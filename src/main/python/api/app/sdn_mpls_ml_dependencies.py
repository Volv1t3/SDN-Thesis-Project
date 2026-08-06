"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370

Archivo que define la forma en que se define la construccion de todos los servicios adicionales y la validacion de los
componentes del sistema, incluyendo la carga y validacion de configuraciones, modelos, y la inferencia temprana de prueba
para definir si la aplicacion esta lista o no

Pasos:
- Crea el estado inicial de readiness antes de cualquier carga pesada.
- Ejecuta las cinco etapas de validacion obligatoria en orden fijo.
- Conserva clasificadores, metadata y politicas listos para las rutas HTTP.
"""

from __future__ import annotations
import json
import logging
import os
import time
from dataclasses import dataclass
from pathlib import Path

from anyio import CapacityLimiter
import numpy as np
from pydantic import ValidationError


#? Importes de la API
from app.sdn_mpls_ml_config import (
    DEFAULT_MAX_REQUEST_BODY_BYTES,
    MAX_CLASSIFIER_POOL_SIZE,
    MODEL_SUPPORTED_ETHERTYPES,
    SUPPORTED_LOG_LEVELS,
    ClassificationMode,
    RawSettings,
    ValidatedSettings,
    get_raw_settings,
)
from app.sdn_mpls_ml_messages import Messages
from app.model.sdn_mpls_ml_deterministic_predictor import DeterministicClassifier
from app.model.sdn_mpls_ml_deterministic_rules import DeterministicRuleFile
from app.model.sdn_mpls_ml_metadata import EXPECTED_CLASS_TO_ID, EXPECTED_FEATURE_ORDER, ModelMetadata
from app.model.sdn_mpls_ml_classifier_pool import ClassifierPool
from app.model.sdn_mpls_ml_model_predictor import PredictionResult, Predictor
from app.model.sdn_mpls_ml_protocols import TrafficClassifier
from app.observability.sdn_mpls_ml_identity import ProcessIdentity
from app.observability.sdn_mpls_ml_metrics import (
    STARTUP_FAILURES_TOTAL,
    STARTUP_VALIDATION_DURATION_SECONDS,
    BaselineMetrics,
)
from app.policy.sdn_mpls_ml_policy_mapper import PolicyMapper, load_policy_file
from app.policy.sdn_mpls_ml_policy_validation_models import PolicyFile, TrafficPolicy
from app.sdn_mpls_ml_readiness import ReadinessState, StartupValidationError


#? Codigos especificos de los pasos que se realizan en la validacion local, usados para referenciar errores o trabas
#? en logs o en respuestas HTTP
STAGE_CONFIGURATION_PREFLIGHT = "configuration_preflight"
STAGE_ARTIFACT_CHECKS = "artifact_checks"
STAGE_METADATA_POLICY_SCHEMA_VALIDATION = "metadata_and_policy_schema_validation"
STAGE_RUNTIME_MODEL_COMPATIBILITY = "runtime_model_compatibility"
STAGE_COMPLETE_POLICY_MAP_VALIDATION = "complete_policy_map_validation"

#? Paquete de prueba de tipo HTTP
SYNTHETIC_PACKET_FEATURES = {
    "eth_type": 2048,
    "ip_proto": 6,
    "src_port": 49152,
    "dst_port": 443,
}

#? Codigos de errores de carga de modelo que indican problemas de compatibilidad o corrupcion de artefactos
MODEL_LOAD_FAILURE_CODES = {
    "MODEL_LOAD_FAILED",
    "MODEL_FEATURE_COUNT_MISMATCH",
    "MODEL_OBJECTIVE_INCOMPATIBLE",
    "MODEL_CLASS_COUNT_MISMATCH",
}

#? Codigos de errores durante la ejecucion de la clasificacion de prueba
SYNTHETIC_FAILURE_CODES = {
    "MODEL_SELF_TEST_FAILED",
    "MODEL_OUTPUT_INVALID",
    "DETERMINISTIC_RULE_SELF_TEST_FAILED",
}


@dataclass(slots=True)
class ArtifactPaths:
    """
    Agrupa las rutas validadas de artefactos usadas en startup. Este es otro record que se usa para agrupar todos los datos
    en un objeto que puede ser usado rapidamente dentro de las validaciones de artefactos.
    """
    model_path: Path | None
    metadata_path: Path | None
    policy_path: Path
    deterministic_rule_path: Path | None


@dataclass(slots=True)
class AppServices:
    """
    Contiene los servicios y caches compartidos por la aplicacion. Corresponde al servicio general que contiene todos los
    sistemas internos de:
    - Pool de clasificadores que pueden ser de tipo DeterministicClassifier o TrafficClassifier
    - Configuraciones
    - Metadata del modelo
    - Policies de Trafico
    - Limite de hilos para inferencia

    Toda esta informacion es accessible directamente desde sdn_mpls_ml_main.py dado que hace durante la inicializacion de la api.
    SI bien no es un singleton, se constreuye una vez y es valido durante toda la vida de la API como proceso en el contenedor o
    sistema
    """

    raw_settings: RawSettings
    settings: ValidatedSettings | None
    readiness: ReadinessState
    classifier_pool: ClassifierPool[TrafficClassifier] | None
    inference_thread_limiter: CapacityLimiter | None
    model_metadata: ModelMetadata | None
    policy_mapper: PolicyMapper | None
    request_size_limit_bytes: int
    baseline_metrics: BaselineMetrics | None


def create_initial_services(raw_settings: RawSettings | None = None) -> AppServices:
    """
    Crea un contenedor de servicios en estado de inicializacion. En este caso, esta funcion permite configurar inicialmente
    a una instancia de AppServices para ser usada desde la configuracion de la API en lifespan en base unicamente a tener
    RawSettings, es decir la configuracion cargada base del entorno. Esta instancia no configura nada mas que las settings
    dado que requiere de pasar las validaciones de readiness para configurar otros parametros.

    Pasos:
    - Carga settings crudos si no fueron proporcionados.
    - Inicializa readiness en estado `initializing`.
    - Deja vacios classifier, metadata y policy mapper.

    Args:
        raw_settings: configuracion cruda opcional ya cargada.

    Returns:
        AppServices: estructura inicial lista para ser validada.
    """

    raw = raw_settings or get_raw_settings()
    return AppServices(
        raw_settings=raw,
        settings=None,
        readiness=ReadinessState.initializing(_classification_mode_response_value(raw.classification_mode)),
        classifier_pool=None,
        inference_thread_limiter=None,
        model_metadata=None,
        policy_mapper=None,
        request_size_limit_bytes=DEFAULT_MAX_REQUEST_BODY_BYTES,
        #? Genera un objeto base de BaselineMetrics que corresponde a una clase de metricas de Prometheus
        #? para reportar errores de manera temprana
        baseline_metrics=_create_baseline_metrics(raw),
    )


def build_services(
    raw_settings: RawSettings | None = None,
    process_identity: ProcessIdentity | None = None,
) -> AppServices:
    """
    Construye los servicios completos y ejecuta la inicializacion. Este mecanismo permite inicializar los recursos de la aplicacion
    y los servicios que ofrece mediante la inicializacion del estado de readiness mediante la funcion initialize_services().
    Hasta este punto, el sistema solo se ha encargado de crear el objeto de servicios y de llamar a su inicializacion.

    Pasos:
    - Crea la estructura base de servicios.
    - Ejecuta el flujo de startup una sola vez.
    - Devuelve el resultado listo para ser usado por la app.

    Args:
        raw_settings: configuracion cruda opcional.
        process_identity: identidad del proceso opcional.

    Returns:
        AppServices: servicios inicializados y readiness cacheado.
    """

    services = create_initial_services(raw_settings)
    initialize_services(services, process_identity=process_identity)
    return services


def initialize_services(services: AppServices, process_identity: ProcessIdentity | None = None) -> None:
    """
    Ejecuta la cadena completa de validacion de startup. En este caso, el proceso general que realiza corresponde al proceso de
    inicializacion de todos los sistemas de la aplicacion, lo que implica pasar por todas las fases de revision de archivos
    de configuracion, artefactos, esquemas de datos y los modelos, asi como la validacion interna de la capacidad de prediccion
    de los modelos.

    En base a estas pruebas la aplicacion puede marcar sea un error y mantenerse en un estado not ready, o proceder
    a la inicializacion de los servicios y marcarse como ready. En caso de error, se registran eventos estructurados
    y se marca internamente el estado de la aplicacion como not ready con un cache para que no se vuelva a ejecutar
    este servicio.

    Pasos:
    - Registra el inicio del proceso de readiness.
    - Valida settings, artefactos, schemas, runtime y mapa de politicas.
    - Marca readiness como listo o fallido y registra eventos estructurados.

    Args:
        services: contenedor mutable donde se escribira el resultado.
        process_identity: identidad opcional del proceso para inicializar metricas.
    """

    logger = logging.getLogger(__name__)
    started_at = time.perf_counter()
    #? Si existen las baseline metrics, y si existe un identificador de proceso (creado en lifespan)
    #? entonces inicializamos las metricas con la identidad del proceso
    if services.baseline_metrics is not None:
        if process_identity is not None:
            services.baseline_metrics.initialize_worker(process_identity)
        #? Marcamos el estado de readiness como no listo dado que no se ha pasado ninguna validacion todavia
        services.baseline_metrics.set_readiness(False)

    #? Log de estado de inicio de validacion
    logger.info(
        Messages.STARTUP_VALIDATION_STARTED,
        extra={
            "service": services.raw_settings.app_name or "sdnflow-inference-api",
            "event": "startup_validation_started",
            "classification_mode": services.readiness.classification_mode,
        },
    )

    try:
        #! Etapa 1: validar configuracion antes de tocar disco o runtime.
        settings = _validate_settings(services.raw_settings)
        if not settings.enable_prometheus_metrics:
            services.baseline_metrics = None
        elif services.baseline_metrics is not None:
            #? Si tenemos metricas activadas registramos el modo de clasificacion
            services.baseline_metrics.classification_mode = (
                settings.classification_mode.response_value)

        #? Registramos el tamano total de bytes para las requests (para el Middleware de Content Length) y
        #? registramos el modo de clasificacion bajo el struct de ReadinessState en AppServices
        services.settings = settings
        services.request_size_limit_bytes = settings.max_request_body_bytes
        services.readiness.classification_mode = settings.classification_mode.response_value
        logger.info(
            Messages.CONFIGURATION_PREFLIGHT_PASSED,
            extra={
                "service": settings.app_name,
                "event": "configuration_preflight_passed",
                "classification_mode": settings.classification_mode.response_value,
            },
        )

        _log_artifact_configuration(logger, settings)

        #! Etapa 2: comprobar que los artefactos requeridos existen y pueden abrirse.
        #? Si regresamos a este punto de ejecucion no hubo ningun error de validacion en artefactos
        artifacts = _validate_artifacts(settings)
        logger.info(
            Messages.ARTIFACT_VALIDATION_PASSED,
            extra={
                "service": settings.app_name,
                "event": "artifact_validation_passed",
                "classification_mode": settings.classification_mode.response_value,
            },
        )

        #! Etapa 3: validar metadata, politica y reglas deterministicas segun el modo.
        metadata, policy_file, deterministic_rules = _validate_schemas(settings, artifacts, services.readiness)
        #? Guardamos la metadata validada para ser accesible por todos los paquetes durante el lifecycle de la API
        #? mediante services
        services.model_metadata = metadata
        logger.info(
            Messages.METADATA_VALIDATION_PASSED,
            extra={
                "service": settings.app_name,
                "event": "metadata_validation_passed",
                "classification_mode": settings.classification_mode.response_value,
            },
        )

        #! Etapas 4 y 5: construir pool de clasificadores, ejecutar auto-pruebas y validar mapa total.
        #? Con todas las configuraciones validadas, la prueba mas grande es la construccion de los clasificadores y
        #? la prueba de clasificacion para MODEL
        classifier_pool = _build_classifier_pool(
            settings, metadata, deterministic_rules, services.readiness, services.baseline_metrics
        )

        #? Procedemos a validar el archivo de politicas por clase completo
        _validate_complete_policy_map(
            policy_file=policy_file,
            expected_classes=list(EXPECTED_CLASS_TO_ID),
            min_tunnel_bandwidth_kbps=settings.min_tunnel_bandwidth_kbps,
            max_tunnel_bandwidth_kbps=settings.max_tunnel_bandwidth_kbps,
        )
        logger.info(
            Messages.POLICY_MAP_VALIDATION_PASSED,
            extra={
                "service": settings.app_name,
                "event": "policy_map_validation_passed",
                "classification_mode": settings.classification_mode.response_value,
            },
        )

        #? Configuramos la API con los servicios generados, incluyendo el servicio del Policy Mapper que es usado para
        #? luego de una prediccion, asignarle a esa prediccion una politica de trafico, la pool de clasificadores y
        #? un limitador de los hilos que puede usar
        if settings.enable_policy_mapping:
            services.policy_mapper = PolicyMapper(
                policy_file=policy_file,
                min_policy_confidence=settings.min_policy_confidence,
            )

        #? Registramos la Classifier Pool y el Limitador de los hilos de inferencia para los clasificadores
        services.classifier_pool = classifier_pool
        services.inference_thread_limiter = CapacityLimiter(settings.classifier_pool_size)

        #? Registramos en el estado de readiness que la app ha pasado todas las validaciones de datos y esta activa
        services.readiness.mark_ready(
            model_name=metadata.model_name if metadata is not None else None,
            model_schema_version=metadata.schema_version if metadata is not None else None,
            feature_count=len(metadata.feature_order) if metadata is not None else len(EXPECTED_FEATURE_ORDER),
            class_count=len(metadata.class_to_id) if metadata is not None else len(EXPECTED_CLASS_TO_ID),
        )

        #? Registramos en las metricas de Prometheus los detalles configurados de la Pool creada, el modo de
        #? clasificacion y el tiempo de preparacion de todos los checks
        if services.baseline_metrics is not None:
            duration_seconds = time.perf_counter() - started_at
            STARTUP_VALIDATION_DURATION_SECONDS.labels(
                classification_mode=settings.classification_mode.response_value,
                outcome="ready",
            ).observe(duration_seconds)
            services.baseline_metrics.set_readiness(True)
            services.baseline_metrics.set_pool_state(
                capacity=classifier_pool.capacity,
                available=classifier_pool.available,
                borrowed=classifier_pool.borrowed,
            )
        logger.info(
            Messages.SERVICE_READY,
            extra={
                "service": settings.app_name,
                "event": "service_ready",
                "classification_mode": settings.classification_mode.response_value,
                "model_name": metadata.model_name if metadata is not None else None,
                "pool_size": settings.classifier_pool_size,
                "validation_duration_ms": round((time.perf_counter() - started_at) * 1000, 3),
            },
        )
    except StartupValidationError as exc:
        services.readiness.mark_failed(exc)
        if services.baseline_metrics is not None:
            STARTUP_VALIDATION_DURATION_SECONDS.labels(
                classification_mode=services.readiness.classification_mode,
                outcome="failed",
            ).observe(time.perf_counter() - started_at)
            STARTUP_FAILURES_TOTAL.labels(
                failed_stage=exc.failed_stage,
                error_code=exc.code,
            ).inc()
            services.baseline_metrics.set_readiness(False)
        service_name = services.settings.app_name if services.settings is not None else services.raw_settings.app_name
        classification_mode = services.readiness.classification_mode
        logger.error(
            exc.message,
            extra={
                "service": service_name,
                "event": _failure_event(exc),
                "classification_mode": classification_mode,
                "failed_stage": exc.failed_stage,
                "component": exc.component,
                "error_code": exc.code,
                "failed_check": exc.failed_check,
                "retryable": exc.retryable,
            },
        )
        logger.error(
            Messages.SERVICE_NOT_READY,
            extra={
                "service": service_name,
                "event": "service_not_ready",
                "classification_mode": classification_mode,
                "failed_stage": exc.failed_stage,
                "component": exc.component,
                "error_code": exc.code,
                "failed_check": exc.failed_check,
                "retryable": exc.retryable,
                "validation_duration_ms": round((time.perf_counter() - started_at) * 1000, 3),
            },
        )


def _log_artifact_configuration(logger: logging.Logger, settings: ValidatedSettings) -> None:
    """
    Registra el contexto de rutas usado para validar artefactos. Este dato de log registra todas las direcciones de
    archivos, esto no se expone a la API, pero se guarda internamente en el log para validacion futura.

    Pasos:
    - Publica el directorio de trabajo actual del proceso.
    - Muestra directorios configurados y sus rutas resueltas.
    - Expone los nombres de archivo esperados para facilitar depuracion local.

    Args:
        logger: logger estructurado del modulo.
        settings: configuracion validada ya normalizada.
    """

    logger.info(
        Messages.ARTIFACT_CONFIGURATION_RESOLVED,
        extra={
            "service": settings.app_name,
            "event": "artifact_configuration_resolved",
            "classification_mode": settings.classification_mode.response_value,
            "current_workdir": str(Path.cwd()),
            "model_dir": settings.model_dir or None,
            "resolved_model_dir": str(Path(settings.model_dir).resolve(strict=False)) if settings.model_dir else None,
            "config_dir": settings.config_dir,
            "resolved_config_dir": str(Path(settings.config_dir).resolve(strict=False)),
            "model_filename": settings.model_filename or None,
            "model_metadata_filename": settings.model_metadata_filename or None,
            "policy_filename": settings.policy_filename,
            "deterministic_rule_filename": settings.deterministic_rule_filename or None,
        },
    )


def _failure_event(error: StartupValidationError) -> str:
    """Traduce un error de startup al evento estructurado de logging.

    Argumentos:
    - error: error de validacion capturado durante startup.

    Retorna:
    - str: nombre del evento de logging asociado.
    """

    if error.failed_stage == STAGE_CONFIGURATION_PREFLIGHT:
        return "configuration_preflight_failed"
    if error.failed_stage == STAGE_ARTIFACT_CHECKS:
        return "artifact_validation_failed"
    if error.failed_stage == STAGE_METADATA_POLICY_SCHEMA_VALIDATION:
        return "metadata_validation_failed"
    if error.failed_stage == STAGE_COMPLETE_POLICY_MAP_VALIDATION:
        return "policy_map_validation_failed"
    if error.code in SYNTHETIC_FAILURE_CODES:
        return "synthetic_inference_failed"
    return "model_load_failed"


def _classification_mode_response_value(raw_value: str) -> str:
    """Normaliza el modo crudo a la forma expuesta por la API.

    Argumentos:
    - raw_value: valor crudo leido desde entorno.

    Retorna:
    - str: valor normalizado para respuestas de readiness.
    """

    candidate = raw_value.strip().upper()
    try:
        return ClassificationMode(candidate).response_value
    except ValueError:
        return raw_value.strip().lower() or "unknown"


def _create_baseline_metrics(raw: RawSettings) -> BaselineMetrics | None:
    """
    Crea el recorder temprano para incluir fallos de validacion en startup. Esta instancia permite configurar
    rapidamente el estado de la API durante la validacion de startup, dado que prometheus se considera como activado
    cuando el flag esta activo, eso activa el sistema y retorna el objeto correspondiente.

    """

    if raw.enable_prometheus_metrics.strip().lower() in {"false", "0", "no", "off"}:
        return None
    return BaselineMetrics(classification_mode=_classification_mode_response_value(raw.classification_mode))


def _startup_error(
    *,
    code: str,
    message: str,
    component: str,
    failed_stage: str,
    failed_check: str | None,
    retryable: bool,
) -> StartupValidationError:
    """Construye un `StartupValidationError` consistente.

    Args:
        code: codigo de error a exponer.
        message: mensaje publico del error.
        component: componente responsable.
        failed_stage: etapa de readiness fallida.
        failed_check: verificacion puntual fallida.
        retryable: indica si la condicion podria resolverse externamente.

    Returns:
        StartupValidationError: error listo para registrar y cachear.
    """

    return StartupValidationError(
        code=code,
        message=message,
        component=component,
        failed_stage=failed_stage,
        failed_check=failed_check,
        retryable=retryable,
    )


def _validate_settings(raw: RawSettings) -> ValidatedSettings:
    """
    Valida y normaliza la configuracion de arranque, es decir, valida la informacion correspondiente a las
    variables de entorno ingresadas a la API y revisa si se tiene que usar los defaults.

    Pasos:
    - Comprueba campos generales requeridos por la aplicacion.
    - Convierte tipos numericos y booleanos desde variables de entorno.
    - Aplica reglas especificas del modo `MODEL` o `DETERMINISTIC_TEST`.

    Args:
        raw: settings crudos leidos desde entorno.

    Returns:
        ValidatedSettings: configuracion segura para el runtime.

    Raises:
        StartupValidationError: si alguna regla de configuracion falla.
    """

    #? Revisa si existe un nombre ingresado en la aplicacion en la configuracion raw
    app_name = raw.app_name.strip()
    if not app_name:
        raise _startup_error(
            code="CONFIGURATION_INVALID",
            message=Messages.APP_NAME_REQUIRED,
            component="application_settings",
            failed_stage=STAGE_CONFIGURATION_PREFLIGHT,
            failed_check="app_name_nonempty",
            retryable=False,
        )
    app_version = raw.app_version.strip()
    if not app_version:
        raise _startup_error(
            code="CONFIGURATION_INVALID",
            message=Messages.APP_VERSION_REQUIRED,
            component="application_settings",
            failed_stage=STAGE_CONFIGURATION_PREFLIGHT,
            failed_check="app_version_nonempty",
            retryable=False,
        )


    mode_raw = raw.classification_mode.strip().upper()
    try:
        #? Intentamos crear una instancia de ClassifcationMode, si la clase registrada existe entonces
        #? este modo pasa y retorna la instancia, lo que registra en la aplicacion el modo de clasificacion.
        classification_mode = ClassificationMode(mode_raw)
    except ValueError as exc:
        raise _startup_error(
            code="CONFIGURATION_INVALID",
            message=Messages.CLASSIFICATION_MODE_UNSUPPORTED,
            component="application_settings",
            failed_stage=STAGE_CONFIGURATION_PREFLIGHT,
            failed_check="classification_mode_supported",
            retryable=False,
        ) from exc

    #? Validamos puerto de la aplicacion
    port = _parse_int(
        raw.port,
        failed_check="port_range",
        message=Messages.PORT_INVALID,
    )
    if not 1 <= port <= 65535:
        raise _startup_error(
            code="CONFIGURATION_INVALID",
            message=Messages.PORT_RANGE,
            component="application_settings",
            failed_stage=STAGE_CONFIGURATION_PREFLIGHT,
            failed_check="port_range",
            retryable=False,
        )

    #? Validamos el nivel del log
    log_level = raw.log_level.strip().upper()
    if log_level not in SUPPORTED_LOG_LEVELS:
        raise _startup_error(
            code="CONFIGURATION_INVALID",
            message=Messages.LOG_LEVEL_UNSUPPORTED,
            component="application_settings",
            failed_stage=STAGE_CONFIGURATION_PREFLIGHT,
            failed_check="log_level_supported",
            retryable=False,
        )

    #? Validamos la ruta de métricas
    metrics_path = raw.metrics_path.strip()
    if not metrics_path or not metrics_path.startswith("/"):
        raise _startup_error(
            code="CONFIGURATION_INVALID",
            message=Messages.METRICS_PATH_INVALID,
            component="application_settings",
            failed_stage=STAGE_CONFIGURATION_PREFLIGHT,
            failed_check="metrics_path_absolute",
            retryable=False,
        )
    if "?" in metrics_path or "#" in metrics_path:
        raise _startup_error(
            code="CONFIGURATION_INVALID",
            message=Messages.METRICS_PATH_INVALID,
            component="application_settings",
            failed_stage=STAGE_CONFIGURATION_PREFLIGHT,
            failed_check="metrics_path_format",
            retryable=False,
        )
    #? Validamos si se van a exportar las metricas de prometheus
    enable_prometheus_metrics = _parse_bool(
        raw.enable_prometheus_metrics,
        failed_check="enable_prometheus_metrics_boolean",
        message=Messages.ENABLE_PROMETHEUS_METRICS_BOOLEAN,
    )

    ##? Revisa si la carpeta de los archivos de configuracion fue definida
    config_dir = raw.config_dir.strip()
    if not config_dir:
        raise _startup_error(
            code="CONFIGURATION_INVALID",
            message=Messages.CONFIG_DIR_REQUIRED,
            component="application_settings",
            failed_stage=STAGE_CONFIGURATION_PREFLIGHT,
            failed_check="config_dir_configured",
            retryable=False,
        )
    policy_filename = raw.policy_filename.strip()
    if not policy_filename:
        raise _startup_error(
            code="CONFIGURATION_INVALID",
            message=Messages.POLICY_FILENAME_REQUIRED,
            component="application_settings",
            failed_stage=STAGE_CONFIGURATION_PREFLIGHT,
            failed_check="policy_filename_configured",
            retryable=False,
        )
    max_request_body_bytes = _parse_int(
        raw.max_request_body_bytes,
        failed_check="max_request_body_bytes_positive",
        message=Messages.MAX_REQUEST_BODY_BYTES_POSITIVE,
    )
    if max_request_body_bytes <= 0:
        raise _startup_error(
            code="CONFIGURATION_INVALID",
            message=Messages.MAX_REQUEST_BODY_BYTES_POSITIVE,
            component="application_settings",
            failed_stage=STAGE_CONFIGURATION_PREFLIGHT,
            failed_check="max_request_body_bytes_positive",
            retryable=False,
        )

    probability_tolerance = _parse_float(
        raw.probability_tolerance,
        failed_check="probability_tolerance_positive",
        message=Messages.PROBABILITY_TOLERANCE_POSITIVE,
    )
    if probability_tolerance <= 0:
        raise _startup_error(
            code="CONFIGURATION_INVALID",
            message=Messages.PROBABILITY_TOLERANCE_POSITIVE,
            component="application_settings",
            failed_stage=STAGE_CONFIGURATION_PREFLIGHT,
            failed_check="probability_tolerance_positive",
            retryable=False,
        )

    min_policy_confidence = None
    if raw.min_policy_confidence is not None and raw.min_policy_confidence.strip():
        min_policy_confidence = _parse_float(
            raw.min_policy_confidence,
            failed_check="min_policy_confidence_range",
            message=Messages.MIN_POLICY_CONFIDENCE_RANGE,
        )
        if not 0.0 <= min_policy_confidence <= 1.0:
            raise _startup_error(
                code="CONFIGURATION_INVALID",
                message=Messages.MIN_POLICY_CONFIDENCE_RANGE,
                component="application_settings",
                failed_stage=STAGE_CONFIGURATION_PREFLIGHT,
                failed_check="min_policy_confidence_range",
                retryable=False,
            )

    request_timeout_seconds = _parse_int(
        raw.request_timeout_seconds,
        failed_check="request_timeout_positive",
        message=Messages.REQUEST_TIMEOUT_SECONDS_POSITIVE,
    )
    if request_timeout_seconds <= 0:
        raise _startup_error(
            code="CONFIGURATION_INVALID",
            message=Messages.REQUEST_TIMEOUT_SECONDS_POSITIVE,
            component="application_settings",
            failed_stage=STAGE_CONFIGURATION_PREFLIGHT,
            failed_check="request_timeout_positive",
            retryable=False,
        )

    min_tunnel_bandwidth_kbps = _parse_int(
        raw.min_tunnel_bandwidth_kbps,
        failed_check="min_tunnel_bandwidth_range",
        message=Messages.MIN_TUNNEL_BANDWIDTH_RANGE,
    )
    max_tunnel_bandwidth_kbps = _parse_int(
        raw.max_tunnel_bandwidth_kbps,
        failed_check="max_tunnel_bandwidth_range",
        message=Messages.MAX_TUNNEL_BANDWIDTH_RANGE,
    )
    if not 1 <= min_tunnel_bandwidth_kbps <= 100000:
        raise _startup_error(
            code="CONFIGURATION_INVALID",
            message=Messages.MIN_TUNNEL_BANDWIDTH_RANGE,
            component="application_settings",
            failed_stage=STAGE_CONFIGURATION_PREFLIGHT,
            failed_check="min_tunnel_bandwidth_range",
            retryable=False,
        )
    if not 1 <= max_tunnel_bandwidth_kbps <= 100000:
        raise _startup_error(
            code="CONFIGURATION_INVALID",
            message=Messages.MAX_TUNNEL_BANDWIDTH_RANGE,
            component="application_settings",
            failed_stage=STAGE_CONFIGURATION_PREFLIGHT,
            failed_check="max_tunnel_bandwidth_range",
            retryable=False,
        )
    if min_tunnel_bandwidth_kbps > max_tunnel_bandwidth_kbps:
        raise _startup_error(
            code="CONFIGURATION_INVALID",
            message=Messages.TUNNEL_BANDWIDTH_BOUNDS,
            component="application_settings",
            failed_stage=STAGE_CONFIGURATION_PREFLIGHT,
            failed_check="tunnel_bandwidth_bounds",
            retryable=False,
        )

    enable_policy_mapping = _parse_bool(
        raw.enable_policy_mapping,
        failed_check="enable_policy_mapping_boolean",
        message=Messages.ENABLE_POLICY_MAPPING_BOOLEAN,
    )
    classifier_pool_size = _parse_int(
        raw.classifier_pool_size,
        failed_check="classifier_pool_size_range",
        message=Messages.CLASSIFIER_POOL_SIZE_RANGE,
    )
    if not 1 <= classifier_pool_size <= MAX_CLASSIFIER_POOL_SIZE:
        raise _startup_error(
            code="CONFIGURATION_INVALID",
            message=Messages.CLASSIFIER_POOL_SIZE_RANGE,
            component="application_settings",
            failed_stage=STAGE_CONFIGURATION_PREFLIGHT,
            failed_check="classifier_pool_size_range",
            retryable=False,
        )

    model_dir = raw.model_dir.strip()
    model_filename = raw.model_filename.strip()
    model_metadata_filename = raw.model_metadata_filename.strip()
    deterministic_rule_filename = raw.deterministic_rule_filename.strip()
    if classification_mode is ClassificationMode.MODEL:
        if not model_dir:
            raise _startup_error(
                code="CONFIGURATION_INVALID",
                message=Messages.MODEL_DIR_REQUIRED_MODEL_MODE,
                component="application_settings",
                failed_stage=STAGE_CONFIGURATION_PREFLIGHT,
                failed_check="model_dir_configured",
                retryable=False,
            )
        if not model_filename:
            raise _startup_error(
                code="CONFIGURATION_INVALID",
                message=Messages.MODEL_FILENAME_REQUIRED_MODEL_MODE,
                component="application_settings",
                failed_stage=STAGE_CONFIGURATION_PREFLIGHT,
                failed_check="model_filename_configured",
                retryable=False,
            )
        if not model_metadata_filename:
            raise _startup_error(
                code="CONFIGURATION_INVALID",
                message=Messages.MODEL_METADATA_FILENAME_REQUIRED_MODEL_MODE,
                component="application_settings",
                failed_stage=STAGE_CONFIGURATION_PREFLIGHT,
                failed_check="model_metadata_filename_configured",
                retryable=False,
            )
    else:
        if not deterministic_rule_filename:
            raise _startup_error(
                code="CONFIGURATION_INVALID",
                message=Messages.DETERMINISTIC_RULE_FILENAME_REQUIRED,
                component="application_settings",
                failed_stage=STAGE_CONFIGURATION_PREFLIGHT,
                failed_check="deterministic_rule_filename_configured",
                retryable=False,
            )

    #? Finalmente retornamos un objeto de ValidatedSettings que corresponde a los
    #? resultados finales de la configuracion. Raw settings contenia defaults que
    #? ahora se estandarizan a un solo valor, sea el registrado en el ENV o los defaults
    #? pero un solo valor
    return ValidatedSettings(
        app_name=app_name,
        app_version=app_version,
        host=raw.host.strip() or "0.0.0.0",
        port=port,
        log_level=log_level,
        enable_prometheus_metrics=enable_prometheus_metrics,
        metrics_path=metrics_path,
        instance_id=raw.instance_id.strip(),
        model_dir=model_dir,
        config_dir=config_dir,
        model_filename=model_filename,
        model_metadata_filename=model_metadata_filename,
        policy_filename=policy_filename,
        deterministic_rule_filename=deterministic_rule_filename,
        enable_policy_mapping=enable_policy_mapping,
        classifier_pool_size=classifier_pool_size,
        request_timeout_seconds=request_timeout_seconds,
        max_request_body_bytes=max_request_body_bytes,
        probability_tolerance=probability_tolerance,
        min_policy_confidence=min_policy_confidence,
        classification_mode=classification_mode,
        min_tunnel_bandwidth_kbps=min_tunnel_bandwidth_kbps,
        max_tunnel_bandwidth_kbps=max_tunnel_bandwidth_kbps,
    )


def _parse_int(raw_value: str, *, failed_check: str, message: str) -> int:
    """
    Convierte un valor crudo a entero con error de startup tipado.

    Args:
        raw_value: valor ingresado
        failed_check: nombre del chequeo usado en diagnostico.
        message: mensaje publico del error.

    Returns:
        int: valor entero validado.

    Raises:
        StartupValidationError: si el valor no es un entero valido.
    """

    try:
        return int(raw_value.strip())
    except (AttributeError, ValueError) as exc:
        raise _startup_error(
            code="CONFIGURATION_INVALID",
            message=message,
            component="application_settings",
            failed_stage=STAGE_CONFIGURATION_PREFLIGHT,
            failed_check=failed_check,
            retryable=False,
        ) from exc


def _parse_float(raw_value: str, *, failed_check: str, message: str) -> float:
    """
    Convierte un valor crudo a flotante con error de startup tipado.

    Args:
        raw_value: valor ingresado
        failed_check: nombre del chequeo usado en diagnostico.
        message: mensaje publico del error.

    Returns:
        float: valor flotante validado.

    Raises:
        StartupValidationError: si el valor no es un numero valido.
    """

    try:
        return float(raw_value.strip())
    except (AttributeError, ValueError) as exc:
        raise _startup_error(
            code="CONFIGURATION_INVALID",
            message=message,
            component="application_settings",
            failed_stage=STAGE_CONFIGURATION_PREFLIGHT,
            failed_check=failed_check,
            retryable=False,
        ) from exc


def _parse_bool(raw_value: str, *, failed_check: str, message: str) -> bool:
    """
    Convierte una bandera textual a booleano estricto.

    Args:
        raw_value: valor crudo proveniente del entorno.
        failed_check: nombre del chequeo usado en diagnostico.
        message: mensaje publico del error.

    Returns:
        bool: bandera booleana validada.

    Raises:
        StartupValidationError: si el valor no corresponde a un booleano conocido.
    """

    normalized = raw_value.strip().lower()
    if normalized in {"true", "1", "yes", "on"}:
        return True
    if normalized in {"false", "0", "no", "off"}:
        return False
    raise _startup_error(
        code="CONFIGURATION_INVALID",
        message=message,
        component="application_settings",
        failed_stage=STAGE_CONFIGURATION_PREFLIGHT,
        failed_check=failed_check,
        retryable=False,
    )


def _validate_artifacts(settings: ValidatedSettings) -> ArtifactPaths:
    """
    Valida los artefactos requeridos por el modo de clasificacion. Esto corresponde a la direccion del modelo,
    archivo de policies, determinantes sin modelo y la propia metadata del modelo.

    Pasos:
    - Resuelve artefactos de modelo en modo `MODEL`.
    - Resuelve reglas deterministicas en modo simulador.
    - Valida siempre el archivo de politicas.

    Args:
        settings: configuracion validada ya normalizada.

    Returns:
        ArtifactPaths: rutas listas para carga posterior.
    """

    model_path = None
    metadata_path = None
    deterministic_rule_path = None

    #? Si tenemos que clasificar en base a MODEL (modelo cargado) validamos el artifacto del modelo y
    #? la metadata
    if settings.classification_mode is ClassificationMode.MODEL:
        model_path = _validate_artifact(
            path=Path(settings.model_dir) / settings.model_filename,
            configured_filename=settings.model_filename,
            component="model",
            not_found_code="MODEL_FILE_NOT_FOUND",
        )
        metadata_path = _validate_artifact(
            path=Path(settings.model_dir) / settings.model_metadata_filename,
            configured_filename=settings.model_metadata_filename,
            component="model_metadata",
            not_found_code="MODEL_METADATA_FILE_NOT_FOUND",
        )
    else:
        #? Si no clasificamos asi entonces estamos en DETERMINISTIC_TEST y usamos solo un archivo base con mapeo por
        #? puertos
        deterministic_rule_path = _validate_artifact(
            path=Path(settings.config_dir) / settings.deterministic_rule_filename,
            configured_filename=settings.deterministic_rule_filename,
            component="deterministic_rules",
            not_found_code="DETERMINISTIC_RULE_FILE_NOT_FOUND",
        )

    #? El archivo de policy siempre tiene que estar en la app
    policy_path = _validate_artifact(
        path=Path(settings.config_dir) / settings.policy_filename,
        configured_filename=settings.policy_filename,
        component="policy",
        not_found_code="POLICY_FILE_NOT_FOUND",
    )

    return ArtifactPaths(
        model_path=model_path,
        metadata_path=metadata_path,
        policy_path=policy_path,
        deterministic_rule_path=deterministic_rule_path,
    )


def _validate_artifact(
    *,
    path: Path,
    configured_filename: str,
    component: str,
    not_found_code: str,
) -> Path:
    """
    Verifica que un artefacto exista, sea legible y no este vacio. Esto se aplica para model, model metadata, policy
    y deterministic rules.

    Pasos:
    - Resuelve la ruta real.
    - Comprueba existencia, tipo de archivo y permisos de lectura.
    - Verifica que el archivo sea UTF-8 legible y no este vacio.

    Args:
        path: ruta candidata del artefacto.
        configured_filename: nombre configurado a exponer en mensajes.
        component: componente logico del artefacto.
        not_found_code: codigo especifico para ausencia del archivo.

    Returns:
        Path: ruta resuelta y validada.

    Raises:
        StartupValidationError: si el artefacto no cumple el contrato de lectura.
    """

    try:
        #? Intentamos primeramente resolver todo el path configurado para tener
        #? el path completo
        resolved = path.resolve()
    except OSError as exc:
        raise _artifact_error(
            code="ARTIFACT_NOT_READABLE",
            message=Messages.artifact_path_resolution(configured_filename),
            component=component,
            failed_check="path_resolution",
        ) from exc

    #? Si no existe early exist
    if not resolved.exists():
        raise _artifact_error(
            code=not_found_code,
            message=Messages.artifact_not_found(configured_filename),
            component=component,
            failed_check="file_exists",
        )
    if not resolved.is_file():
        raise _artifact_error(
            code="ARTIFACT_NOT_READABLE",
            message=Messages.artifact_not_regular_file(configured_filename),
            component=component,
            failed_check="regular_file",
        )
    #? Si no tenemos acceso, salida rapida
    if not os.access(resolved, os.R_OK):
        raise _artifact_error(
            code="ARTIFACT_NOT_READABLE",
            message=Messages.artifact_not_readable(configured_filename),
            component=component,
            failed_check="readable",
        )
    if resolved.stat().st_size == 0:
        raise _artifact_error(
            code="ARTIFACT_EMPTY",
            message=Messages.artifact_empty(configured_filename),
            component=component,
            failed_check="nonempty",
        )
    try:
        resolved.read_text(encoding="utf-8")
    except UnicodeDecodeError as exc:
        raise _artifact_error(
            code="ARTIFACT_NOT_READABLE",
            message=Messages.artifact_not_utf8(configured_filename),
            component=component,
            failed_check="utf8_readable",
        ) from exc
    except OSError as exc:
        raise _artifact_error(
            code="ARTIFACT_NOT_READABLE",
            message=Messages.artifact_not_readable(configured_filename),
            component=component,
            failed_check="readable",
        ) from exc
    return resolved


def _artifact_error(*, code: str, message: str, component: str, failed_check: str) -> StartupValidationError:
    """
    Funcion encargada de construir un error especifico de tipo startup que se da porque el sistema
    no puede continuar con la validacion o inicializacion porque un componente clave para su funcionamiento
    no puede ser accedido.

    Args:
        code: codigo de error especifico.
        message: mensaje de error para el diagnostico.
        component: componente logico del artefacto.
        failed_check: chequeo que fallo en el proceso de validacion.

    Returns:
        StartupValidationError: error de inicializacion tipado.
    """

    return _startup_error(
        code=code,
        message=message,
        component=component,
        failed_stage=STAGE_ARTIFACT_CHECKS,
        failed_check=failed_check,
        retryable=True,
    )


def _validate_schemas(
    settings: ValidatedSettings,
    artifacts: ArtifactPaths,
    readiness: ReadinessState,
) -> tuple[ModelMetadata | None, PolicyFile, DeterministicRuleFile | None]:
    """Carga y valida los schemas requeridos segun el modo actual.

    Pasos:
    - Carga metadata de modelo o reglas deterministicas.
    - Carga siempre el archivo de politicas.
    - Actualiza banderas parciales de readiness.

    Args:
        settings: configuracion validada.
        artifacts: rutas de artefactos ya verificadas.
        readiness: estado mutable donde se marcan cargas parciales.

    Returns:
        tuple[ModelMetadata | None, PolicyFile, DeterministicRuleFile | None]:
      metadata opcional, politica validada y reglas deterministicas opcionales.
    """

    metadata = None
    deterministic_rules = None
    if settings.classification_mode is ClassificationMode.MODEL:
        #? Si nos enctramos en el modo de MODE (clasificacion por ML) validamos la metadata del modelo
        metadata = _load_metadata_for_startup(artifacts.metadata_path)
        readiness.metadata_loaded = True
    else:
        #? Si estamos en DETERMINISTIC_TEST entonces validamos las reglas cargadas en el sistema
        deterministic_rules = _load_deterministic_rules_for_startup(artifacts.deterministic_rule_path)

    #? Independiente del modo de operacion revisamos las politicas de trafico
    policy_file = _load_policy_for_startup(artifacts.policy_path)
    readiness.policy_loaded = True
    return metadata, policy_file, deterministic_rules


def _load_metadata_for_startup(path: Path | None) -> ModelMetadata:
    """
    Carga metadata del modelo con diagnostico de startup controlado. Con esto validamos que el archivo
    definido como settings del modelo sea valido, exista y sea legible dentro de la aplicacion. Hasta
    este punto no se ha validado contenido hasta pasar al paso dos de la validacion.

    Args:
        path: ruta validada del archivo de metadata.

    Returns:
        ModelMetadata: metadata cargada y validada.

    Raises:
        StartupValidationError: si el JSON o el contrato de metadata fallan.
    """

    assert path is not None

    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise _metadata_error(
            code="MODEL_METADATA_INVALID",
            message=Messages.MODEL_METADATA_INVALID_JSON,
            failed_check="json_parse",
        ) from exc
    try:
        #? Aqui se valida la metadata basada en una validacion con Pydantic para seguir un contrato
        #? de datos especifico
        return ModelMetadata.model_validate(payload)
    except ValidationError as exc:
        message = str(exc)
        code = "MODEL_METADATA_INCOMPATIBLE" if _is_metadata_contract_error(message) else "MODEL_METADATA_INVALID"
        raise _metadata_error(
            code=code,
            message=(
                Messages.MODEL_METADATA_INCOMPATIBLE
                if code == "MODEL_METADATA_INCOMPATIBLE"
                else Messages.MODEL_METADATA_SCHEMA_INVALID
            ),
            failed_check=_first_error_loc(exc),
        ) from exc


def _is_metadata_contract_error(message: str) -> bool:
    """Determina si un mensaje apunta a incompatibilidad de contrato."""

    contract_markers = (
        "model_name",
        "model_format",
        "feature_order",
        "feature_types",
        "class_to_id",
        "id_to_class",
        "schema_version",
    )
    return any(marker in message for marker in contract_markers)


def _metadata_error(*, code: str, message: str, failed_check: str) -> StartupValidationError:
    """Construye un error tipado para fallos de metadata."""

    return _startup_error(
        code=code,
        message=message,
        component="model_metadata",
        failed_stage=STAGE_METADATA_POLICY_SCHEMA_VALIDATION,
        failed_check=failed_check,
        retryable=False,
    )


def _load_policy_for_startup(path: Path) -> PolicyFile:
    """
    Funcion encargada de cargar el archivo de policitas de trafico y definicion de tuneles para su validacion usando
    Pydantic y modelos esquematicos definidos

    Args:
        path: ruta validada del archivo de politicas.

    Returns:
        PolicyFile: politicas cargadas y validadas.

    Raises:
        StartupValidationError: si el JSON o el contrato de politicas fallan.
    """

    try:
        return load_policy_file(path)
    except ValueError as exc:
        message = str(exc)
        code = "POLICY_VERSION_UNSUPPORTED" if "schema_version" in message else "POLICY_SCHEMA_INVALID"
        raise _startup_error(
            code=code,
            message=(
                Messages.POLICY_SCHEMA_INVALID
                if code == "POLICY_SCHEMA_INVALID"
                else Messages.POLICY_VERSION_UNSUPPORTED
            ),
            component="policy",
            failed_stage=STAGE_METADATA_POLICY_SCHEMA_VALIDATION,
            failed_check="schema_validation",
            retryable=False,
        ) from exc


def _load_deterministic_rules_for_startup(path: Path | None) -> DeterministicRuleFile:
    """
    Funcion encargada de cargar las reglas deterministicas desde un archivo de configuracion y validarlas contra
    un schema estandar. Esta funcion es utilizada durante el startup de la aplicacion para validar que
    el archivo de reglas sea correcto y pueda ser usado por el sistema de clasificacion.

    Args:
        path: ruta validada del archivo de reglas deterministicas.

    Returns:
        DeterministicRuleFile: reglas cargadas y validadas.

    Raises:
        StartupValidationError: si el JSON o el contrato de reglas fallan.
    """

    assert path is not None
    try:
        #? Enviamos al modelo a validar directamente con Pydantic
        return DeterministicRuleFile.model_validate(json.loads(path.read_text(encoding="utf-8")))
    except (json.JSONDecodeError, OSError, ValidationError) as exc:
        raise _startup_error(
            code="POLICY_SCHEMA_INVALID",
            message=Messages.DETERMINISTIC_RULE_SCHEMA_INVALID,
            component="deterministic_rules",
            failed_stage=STAGE_METADATA_POLICY_SCHEMA_VALIDATION,
            failed_check="schema_validation",
            retryable=False,
        ) from exc


def _build_classifier_pool(
    settings: ValidatedSettings,
    metadata: ModelMetadata | None,
    deterministic_rules: DeterministicRuleFile | None,
    readiness: ReadinessState,
    observer: BaselineMetrics | None = None,
) -> ClassifierPool[TrafficClassifier]:
    """
    Construye el pool de clasificadores segun el modo configurado.

    Pasos:
    - Inicializa todas las instancias en memoria local antes de publicarlas.
    - Ejecuta una auto-prueba sintetica por instancia.
    - Marca readiness parcial cuando la etapa termina correctamente.

    Args:
        settings: configuracion validada de la app.
        metadata: metadata del modelo cuando aplica.
        deterministic_rules: reglas del simulador cuando aplica.
        readiness: estado mutable para registrar progreso.

    Retorna:
    - ClassifierPool[TrafficClassifier]: pool listo para la ruta `/api/v1/classify`.
    """

    logger = logging.getLogger(__name__)
    logger.info(
        Messages.CLASSIFIER_POOL_INITIALIZATION_STARTED,
        extra={
            "service": settings.app_name,
            "event": "classifier_pool_initialization_started",
            "classification_mode": settings.classification_mode.response_value,
            "pool_size": settings.classifier_pool_size,
        },
    )
    instances: list[TrafficClassifier] = []
    try:
        for instance_index in range(settings.classifier_pool_size):
            #? Construye un clasificador basado en la configuracion, metadata, reglas y
            classifier = _build_single_classifier(settings, metadata, deterministic_rules, readiness, instance_index)

            #? Ejecutamos una prueba sintentica sobre el modelo para validar que se ha cargado el modelo correcto y
            #? con la cantidad de features correctas, si esto falla entonces el modelo no es compatible con la API y se lanza un error
            _run_synthetic_self_test(
                classifier.predict(SYNTHETIC_PACKET_FEATURES) #? La funcion valida una prediccion realizada a traves del
                #? metodo predict de la instancia runtime de TrafficClassifier que fue retornada por el metodo anterior
            )
            #? Si la validacion pasa entonces agregamos la instancia al pool
            instances.append(classifier)
            logger.info(
                Messages.CLASSIFIER_POOL_INSTANCE_READY,
                extra={
                    "service": settings.app_name,
                    "event": "classifier_pool_instance_ready",
                    "classification_mode": settings.classification_mode.response_value,
                    "instance_index": instance_index,
                    "pool_size": settings.classifier_pool_size,
                    "model_name": metadata.model_name if metadata is not None else None,
                },
            )
    except StartupValidationError:
        logger.error(
            Messages.CLASSIFIER_POOL_INITIALIZATION_FAILED,
            extra={
                "service": settings.app_name,
                "event": "classifier_pool_initialization_failed",
                "classification_mode": settings.classification_mode.response_value,
                "pool_size": settings.classifier_pool_size,
                "model_name": metadata.model_name if metadata is not None else None,
            },
        )
        raise

    #? Marcamos que la etapa de validacion paso completamente y retornamos el objeto
    readiness.synthetic_inference_passed = True
    logger.info(
        Messages.CLASSIFIER_POOL_READY,
        extra={
            "service": settings.app_name,
            "event": "classifier_pool_ready",
            "classification_mode": settings.classification_mode.response_value,
            "pool_size": settings.classifier_pool_size,
            "model_name": metadata.model_name if metadata is not None else None,
        },
    )
    #? Dentro de la aplicacion, al generar una Classifier Pool usamos un observer que se usa para registrar las metricas
    #? base de la aplicacion correspondientes al tiempo de solicitud de un clasificador, y la cantidad de
    #? clasificadores usados, libres y totales. Estas actualizaciones se realizan de manera automatica y constante a
    #? respuesta a cambios como la adquisicion o liberacion de instancias
    return ClassifierPool(instances, observer=observer)


def _build_single_classifier(
    settings: ValidatedSettings,
    metadata: ModelMetadata | None,
    deterministic_rules: DeterministicRuleFile | None,
    readiness: ReadinessState,
    instance_index: int,
) -> TrafficClassifier:
    """
    Funcion encargada de construir una instancia real de una entidad de Determinsitic Classifier o Classifier
    dependiendo del modo de operacion de la API a la hora de cargar los datos en la validacion.
    :param settings: settings cargadas y valdidadas
    :param metadata: metdata del modelo
    :param deterministic_rules: reglas deterministicas en el caso de modo DETERMINISTIC TEST
    :param readiness: instancia de ReadinessState para guardar informacion de errores o avances
    :param instance_index: indice de la instancia para el indicador y logs
    :return: instancia configurada
    """

    #? Si el modo de clasificacion es DETERMINISTIC_TEST entonces validamos que tenemos reglas y creamos una instancia
    #? directamente
    if settings.classification_mode is ClassificationMode.DETERMINISTIC_TEST:
        assert deterministic_rules is not None
        return DeterministicClassifier(deterministic_rules)

    #? Si no es este modo entonces iniciamos la carga de un predictor, es decir cargar el modelo y retornarlo
    logger = logging.getLogger(__name__)
    logger.info(
        Messages.MODEL_LOAD_STARTED,
        extra={
            "service": settings.app_name,
            "event": "model_load_started",
            "classification_mode": settings.classification_mode.response_value,
            "instance_index": instance_index,
            "pool_size": settings.classifier_pool_size,
        },
    )
    #? Cargamos el predictor que dependiendo del tipo de ejecucion y los archivos retornara una instancia de las clases
    #? especializadas de TrafficClassifier
    predictor = _load_predictor(settings, metadata)
    readiness.model_loaded = True
    logger.info(
        Messages.MODEL_LOAD_PASSED,
        extra={
            "service": settings.app_name,
            "event": "model_load_passed",
            "classification_mode": settings.classification_mode.response_value,
            "instance_index": instance_index,
            "pool_size": settings.classifier_pool_size,
            "model_name": metadata.model_name if metadata is not None else None,
        },
    )
    #? Validamos que el paquete de prueba sintetico funciona para este modelo y retornamos el modelo
    if SYNTHETIC_PACKET_FEATURES["eth_type"] not in MODEL_SUPPORTED_ETHERTYPES:
        raise _runtime_error(
            code="MODEL_SELF_TEST_CONFIGURATION_INVALID",
            message=Messages.SYNTHETIC_ETHERTYPE_INVALID,
            failed_check="synthetic_eth_type",
            retryable=False,
        )
    return predictor


def _load_predictor(settings: ValidatedSettings, metadata: ModelMetadata | None) -> Predictor:
    """
    Carga el predictor XGBoost y valida su compatibilidad de runtime.

    Pasos:
    - Importa XGBoost y carga el booster desde disco.
    - Verifica cantidad de features del artefacto.
    - Inspecciona configuracion de objetivo y numero de clases cuando existe.

    Args:
        settings: configuracion validada con directorio y nombre del modelo.
        metadata: metadata validada del modelo.

    Returns:
        Predictor: predictor listo para inferencia.

    Raises:
        StartupValidationError: si el runtime o el artefacto son incompatibles.
    """

    #? Verificamos que tengamos la metadata del modelo antes de continuar
    #?
    assert metadata is not None

    try:
        import xgboost
    except Exception as exc:
        raise _runtime_error(
            code="MODEL_LOAD_FAILED",
            message=Messages.XGBOOST_RUNTIME_LOAD_FAILED,
            failed_check="xgboost_import",
            retryable=False,
        ) from exc

    #? Definimos un Booster que es la clase base de un XGClassifier, que nos permite cargar un modelo directamente
    booster = xgboost.Booster()
    model_path = Path(settings.model_dir) / settings.model_filename
    try:
        #? Cargamos el modelo dentro de la instancia del booster
        booster.load_model(model_path)
    except Exception as exc:
        raise _runtime_error(
            code="MODEL_LOAD_FAILED",
            message=Messages.MODEL_ARTIFACT_LOAD_FAILED,
            failed_check="booster_load_model",
            retryable=False,
        ) from exc

    try:
        #? Revisamos la cantidad de features que tiene el modelo cargado, estas deben ser cuatro para cuadrar con
        #? la metadata del modelo
        num_features = booster.num_features()
    except Exception as exc:
        raise _runtime_error(
            code="MODEL_LOAD_FAILED",
            message=Messages.MODEL_FEATURE_COUNT_INSPECTION_FAILED,
            failed_check="booster_num_features",
            retryable=False,
        ) from exc
    if num_features != len(EXPECTED_FEATURE_ORDER):
        raise _runtime_error(
            code="MODEL_FEATURE_COUNT_MISMATCH",
            message=Messages.MODEL_FEATURE_COUNT_MISMATCH,
            failed_check="num_features",
            retryable=False,
        )

    try:
        #? Obtenemos la configuracion del booster para validar que sea compatible con lo esperado, esto valida el tipo
        #? del modelo y la cantidad de clases aprendidas que determinan si el modelo que tenemos es el valido para
        #? esta seccion.
        config = json.loads(booster.save_config())
    except Exception:
        config = None

    if config is not None:
        #? La inspeccion del config no reemplaza la prediccion sintetica, pero
        #? permite fallar temprano cuando el objetivo o el numero de clases son incompatibles.
        objective_name = config.get("learner", {}).get("objective", {}).get("name")
        if objective_name is not None and objective_name != "multi:softprob":
            raise _runtime_error(
                code="MODEL_OBJECTIVE_INCOMPATIBLE",
                message=Messages.MODEL_OBJECTIVE_INCOMPATIBLE,
                failed_check="objective_name",
                retryable=False,
            )
        num_class = config.get("learner", {}).get("learner_model_param", {}).get("num_class")
        if num_class is not None and int(num_class) != len(EXPECTED_CLASS_TO_ID):
            raise _runtime_error(
                code="MODEL_CLASS_COUNT_MISMATCH",
                message=Messages.MODEL_CLASS_COUNT_MISMATCH,
                failed_check="num_class",
                retryable=False,
            )

    return Predictor(
        booster=booster,
        metadata=metadata,
        probability_tolerance=settings.probability_tolerance,
    )


def _runtime_error(*, code: str, message: str, failed_check: str, retryable: bool) -> StartupValidationError:
    """
    Construye un error tipado para la etapa de runtime del modelo.

    Args:
        code: codigo funcional del error.
        message: mensaje legible por humanos.
        failed_check: clave de validacion fallida.
        retryable: bandera de reintento.

    Returns:
        StartupValidationError: error tipado para etapa de runtime.
    """

    return _startup_error(
        code=code,
        message=message,
        component="inference_runtime",
        failed_stage=STAGE_RUNTIME_MODEL_COMPATIBILITY,
        failed_check=failed_check,
        retryable=retryable,
    )


def _run_synthetic_self_test(result: PredictionResult) -> None:
    """
    Verifica que una inferencia sintetica cumpla el contrato probabilistico. Es decir, esta funcion valida que el modelo
    pueda entender que tiene que recibir cuatro parametroxs en un orden especifico y valida la cantidad de resultados
    de probabilidades de un modelo multiclase

    Pasos:
    - Comprueba cardinalidad total de probabilidades.
    - Valida finitud y rango numerico.
    - Verifica que la clase resultante pueda decodificarse.

    Args:
        result: resultado ya normalizado de una auto-prueba.

    Raises:
        StartupValidationError: si la salida sintetica es incompatible.
    """

    #? Validamos la salida completa de la clasificacion
    if len(result.probabilities) != len(EXPECTED_CLASS_TO_ID):
        raise _runtime_error(
            code="MODEL_OUTPUT_INVALID",
            message=Messages.SYNTHETIC_PROBABILITY_COUNT_INVALID,
            failed_check="probability_count",
            retryable=False,
        )
    probability_values = np.array(list(result.probabilities.values()), dtype=np.float32)
    if not np.isfinite(probability_values).all():
        raise _runtime_error(
            code="MODEL_OUTPUT_INVALID",
            message=Messages.SYNTHETIC_PROBABILITY_NONFINITE,
            failed_check="probability_finite",
            retryable=False,
        )
    if (probability_values < 0.0).any() or (probability_values > 1.0).any():
        raise _runtime_error(
            code="MODEL_OUTPUT_INVALID",
            message=Messages.SYNTHETIC_PROBABILITY_BOUNDS,
            failed_check="probability_bounds",
            retryable=False,
        )
    if result.class_id not in EXPECTED_CLASS_TO_ID.values():
        raise _runtime_error(
            code="MODEL_SELF_TEST_FAILED",
            message=Messages.SYNTHETIC_CLASS_ID_INVALID,
            failed_check="class_id_decode",
            retryable=False,
        )
    if result.class_name not in EXPECTED_CLASS_TO_ID:
        raise _runtime_error(
            code="MODEL_SELF_TEST_FAILED",
            message=Messages.SYNTHETIC_CLASS_NAME_INVALID,
            failed_check="class_name_decode",
            retryable=False,
        )


def _validate_complete_policy_map(
    *,
    policy_file: PolicyFile,
    expected_classes: list[str],
    min_tunnel_bandwidth_kbps: int,
    max_tunnel_bandwidth_kbps: int,
) -> None:
    """
    Valida la cobertura total del mapa de politicas por clase.

    Pasos:
    - Rechaza clases desconocidas en el archivo.
    - Rechaza clases obligatorias faltantes.
    - Verifica perfil por defecto y restricciones de ancho de banda por clase.

    Args:
        policy_file: archivo de politicas ya validado.
        expected_classes: clases que deben resolverse obligatoriamente.
        min_tunnel_bandwidth_kbps: minimo demostrador para perfiles de tunel.
        max_tunnel_bandwidth_kbps: maximo demostrador permitido.

    Raises:
        StartupValidationError: si el mapa es incompleto o invalido.
    """

    policy_classes = set(policy_file.class_policies)
    expected_class_set = set(expected_classes)
    unknown_classes = policy_classes - expected_class_set
    #? Retornamos un error si tenemos un set de clases inesperadas dentro de la politica
    if unknown_classes:
        raise _policy_error(
            code="POLICY_CLASS_UNKNOWN",
            message=Messages.POLICY_UNKNOWN_CLASS,
            failed_check="unknown_class",
        )

    missing_classes = expected_class_set - policy_classes
    #? Retornamos un error si tenemos clases faltantes de las esperadas en la aplicacion
    if missing_classes:
        raise _policy_error(
            code="POLICY_MAP_INCOMPLETE",
            message=Messages.POLICY_MISSING_CLASS,
            failed_check="missing_class",
        )

    #? Validamos la existencia y configuracion del tunel por defecto
    _validate_default_profile(policy_file.default_profile)

    #? Validamos para cada clase que la politica exista y sea serializable, y luego el bandwidth del tunel con respecto
    #? a los limites de la aplicacion
    for class_name in expected_classes:
        policy = policy_file.class_policies[class_name]
        #? Validacion de Serializacion
        _validate_policy_serialization(policy)
        #? Validacion de bandwidth del tunel
        _validate_tunnel_bandwidth(
            class_name=class_name,
            policy=policy,
            min_tunnel_bandwidth_kbps=min_tunnel_bandwidth_kbps,
            max_tunnel_bandwidth_kbps=max_tunnel_bandwidth_kbps,
        )


def _validate_default_profile(policy: TrafficPolicy) -> None:
    """
    Funcion usada para validar el perfil por defecto de una politica por defecto que no necesita un tipo de bandwidth
    definido.
    :param policy: Policita de trafico default
    :return: None
    """

    #? Validamos la posible serializacion de una policita para validar el objeto
    _validate_policy_serialization(policy)
    if policy.path_constraints.requested_bandwidth_kbps != 0:
        raise _policy_error(
            code="POLICY_VALUE_INVALID",
            message=Messages.DEFAULT_PROFILE_BANDWIDTH_ZERO,
            failed_check="default_bandwidth_zero",
        )


def _validate_policy_serialization(policy: TrafficPolicy) -> None:
    """
    Confirma que una politica validada siga siendo serializable.

    Args:
        policy: Politica ingresada de tipo TrafficPolicy
    """

    try:
        #? Obtenemos la version serializada en Python Dictionaries del objeto
        policy.model_dump()
    except Exception as exc:
        raise _policy_error(
            code="POLICY_VALUE_INVALID",
            message=Messages.POLICY_SERIALIZATION_FAILED,
            failed_check="policy_serialization",
        ) from exc


def _validate_tunnel_bandwidth(
    *,
    class_name: str,
    policy: TrafficPolicy,
    min_tunnel_bandwidth_kbps: int,
    max_tunnel_bandwidth_kbps: int,
) -> None:
    """
    Valida el ancho de banda de una politica de clase.

    Args:
        class_name: nombre de la clase evaluada.
        policy: politica asociada a la clase.
        min_tunnel_bandwidth_kbps: minimo demostrador permitido.
        max_tunnel_bandwidth_kbps: maximo demostrador permitido.

    Raises:
        StartupValidationError: si el ancho de banda sale del rango permitido.
    """

    #? Obtenemos el bandwidth solitida y lo revisamos comparandolo con el minimmo posible de un tunel y el maximo
    bandwidth = policy.path_constraints.requested_bandwidth_kbps

    if bandwidth < min_tunnel_bandwidth_kbps:
        raise _policy_error(
            code="POLICY_BANDWIDTH_BELOW_MINIMUM",
            message=Messages.policy_bandwidth_below_minimum(class_name),
            failed_check="requested_bandwidth_kbps",
        )
    if bandwidth > max_tunnel_bandwidth_kbps:
        raise _policy_error(
            code="POLICY_BANDWIDTH_ABOVE_MAXIMUM",
            message=Messages.policy_bandwidth_above_maximum(class_name),
            failed_check="requested_bandwidth_kbps",
        )


def _policy_error(*, code: str, message: str, failed_check: str) -> StartupValidationError:
    """
    Funcion usada para registrar un error de StartupValidationError configurado para errores de politicas de trafico
    y mapeo de clases. Esto se usa para validar que el archivo de politicas sea correcto y
    :param code: codigo del error
    :param message: mensaje legible en espanol
    :param failed_check: fase en la que hubo el error
    :return: instancia configurada de `StartupValidationError`
    """

    return _startup_error(
        code=code,
        message=message,
        component="policy",
        failed_stage=STAGE_COMPLETE_POLICY_MAP_VALIDATION,
        failed_check=failed_check,
        retryable=False,
    )


def _first_error_loc(exc: ValidationError) -> str:
    """
    Extrae la primera localizacion de error Pydantic como cadena.

    Args:
        exc: excepcion de validacion capturada.

    Returns:
        str: ruta textual del primer error o un literal por defecto.
    """

    first_error = exc.errors()[0]
    loc = first_error.get("loc", ())
    return ".".join(str(part) for part in loc) or "schema_validation"
