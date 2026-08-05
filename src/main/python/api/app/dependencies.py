"""Construye y valida los servicios compartidos del proceso API.

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
from typing import Any

import numpy as np
from pydantic import ValidationError

from app.config import (
    DEFAULT_MAX_REQUEST_BODY_BYTES,
    MODEL_SUPPORTED_ETHERTYPES,
    SUPPORTED_LOG_LEVELS,
    ClassificationMode,
    RawSettings,
    ValidatedSettings,
    get_raw_settings,
)
from app.messages import Messages
from app.model.deterministic import DeterministicClassifier, DeterministicRuleFile, load_deterministic_rules
from app.model.metadata import EXPECTED_CLASS_TO_ID, EXPECTED_FEATURE_ORDER, ModelMetadata
from app.model.predictor import PredictionResult, Predictor
from app.policy.mapper import PolicyMapper, load_policy_file
from app.policy.models import PolicyFile, TrafficPolicy
from app.readiness import ReadinessState, StartupValidationError


STAGE_CONFIGURATION_PREFLIGHT = "configuration_preflight"
STAGE_ARTIFACT_CHECKS = "artifact_checks"
STAGE_METADATA_POLICY_SCHEMA_VALIDATION = "metadata_and_policy_schema_validation"
STAGE_RUNTIME_MODEL_COMPATIBILITY = "runtime_model_compatibility"
STAGE_COMPLETE_POLICY_MAP_VALIDATION = "complete_policy_map_validation"

SYNTHETIC_PACKET_FEATURES = {
    "eth_type": 2048,
    "ip_proto": 6,
    "src_port": 49152,
    "dst_port": 443,
}

MODEL_LOAD_FAILURE_CODES = {
    "MODEL_LOAD_FAILED",
    "MODEL_FEATURE_COUNT_MISMATCH",
    "MODEL_OBJECTIVE_INCOMPATIBLE",
    "MODEL_CLASS_COUNT_MISMATCH",
}
SYNTHETIC_FAILURE_CODES = {
    "MODEL_SELF_TEST_FAILED",
    "MODEL_OUTPUT_INVALID",
    "DETERMINISTIC_RULE_SELF_TEST_FAILED",
}


@dataclass(slots=True)
class ArtifactPaths:
    """Agrupa las rutas validadas de artefactos usadas en startup.

    Pasos:
    - Conserva rutas de modelo y metadata cuando aplica.
    - Conserva rutas de politica y reglas deterministicas.
    """

    model_path: Path | None
    metadata_path: Path | None
    policy_path: Path
    deterministic_rule_path: Path | None


@dataclass(slots=True)
class AppServices:
    """Contiene los servicios y caches compartidos por la aplicacion.

    Pasos:
    - Guarda settings crudos y settings validados.
    - Expone readiness, clasificador y mapper de politicas.
    - Publica el limite de tamano de solicitud ya resuelto.
    """

    raw_settings: RawSettings
    settings: ValidatedSettings | None
    readiness: ReadinessState
    classifier: Any | None
    model_metadata: ModelMetadata | None
    policy_mapper: PolicyMapper | None
    request_size_limit_bytes: int


def create_initial_services(raw_settings: RawSettings | None = None) -> AppServices:
    """Crea un contenedor de servicios en estado de inicializacion.

    Pasos:
    - Carga settings crudos si no fueron proporcionados.
    - Inicializa readiness en estado `initializing`.
    - Deja vacios classifier, metadata y policy mapper.

    Argumentos:
    - raw_settings: configuracion cruda opcional ya cargada.

    Retorna:
    - AppServices: estructura inicial lista para ser validada.
    """

    raw = raw_settings or get_raw_settings()
    return AppServices(
        raw_settings=raw,
        settings=None,
        readiness=ReadinessState.initializing(_classification_mode_response_value(raw.classification_mode)),
        classifier=None,
        model_metadata=None,
        policy_mapper=None,
        request_size_limit_bytes=DEFAULT_MAX_REQUEST_BODY_BYTES,
    )


def build_services(raw_settings: RawSettings | None = None) -> AppServices:
    """Construye los servicios completos y ejecuta la inicializacion.

    Pasos:
    - Crea la estructura base de servicios.
    - Ejecuta el flujo de startup una sola vez.
    - Devuelve el resultado listo para ser usado por la app.

    Argumentos:
    - raw_settings: configuracion cruda opcional.

    Retorna:
    - AppServices: servicios inicializados y readiness cacheado.
    """

    services = create_initial_services(raw_settings)
    initialize_services(services)
    return services


def initialize_services(services: AppServices) -> None:
    """Ejecuta la cadena completa de validacion de startup.

    Pasos:
    - Registra el inicio del proceso de readiness.
    - Valida settings, artefactos, schemas, runtime y mapa de politicas.
    - Marca readiness como listo o fallido y registra eventos estructurados.

    Argumentos:
    - services: contenedor mutable donde se escribira el resultado.
    """

    logger = logging.getLogger(__name__)
    started_at = time.perf_counter()
    logger.info(
        Messages.STARTUP_VALIDATION_STARTED,
        extra={
            "service": services.raw_settings.app_name or "sdnflow-inference-api",
            "event": "startup_validation_started",
            "classification_mode": services.readiness.classification_mode,
        },
    )

    try:
        # Etapa 1: validar configuracion antes de tocar disco o runtime.
        settings = _validate_settings(services.raw_settings)
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

        # Etapa 2: comprobar que los artefactos requeridos existen y pueden abrirse.
        artifacts = _validate_artifacts(settings)
        logger.info(
            Messages.ARTIFACT_VALIDATION_PASSED,
            extra={
                "service": settings.app_name,
                "event": "artifact_validation_passed",
                "classification_mode": settings.classification_mode.response_value,
            },
        )

        # Etapa 3: validar metadata, politica y reglas deterministicas segun el modo.
        metadata, policy_file, deterministic_rules = _validate_schemas(settings, artifacts, services.readiness)
        services.model_metadata = metadata
        logger.info(
            Messages.METADATA_VALIDATION_PASSED,
            extra={
                "service": settings.app_name,
                "event": "metadata_validation_passed",
                "classification_mode": settings.classification_mode.response_value,
            },
        )

        # Etapas 4 y 5: construir clasificador, ejecutar auto-prueba y validar mapa total.
        services.classifier = _build_classifier(settings, metadata, deterministic_rules, services.readiness)
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

        if settings.enable_policy_mapping:
            services.policy_mapper = PolicyMapper(
                policy_file=policy_file,
                min_policy_confidence=settings.min_policy_confidence,
            )

        services.readiness.mark_ready(
            model_name=metadata.model_name if metadata is not None else None,
            model_schema_version=metadata.schema_version if metadata is not None else None,
            feature_count=len(metadata.feature_order) if metadata is not None else len(EXPECTED_FEATURE_ORDER),
            class_count=len(metadata.class_to_id) if metadata is not None else len(EXPECTED_CLASS_TO_ID),
        )
        logger.info(
            Messages.SERVICE_READY,
            extra={
                "service": settings.app_name,
                "event": "service_ready",
                "classification_mode": settings.classification_mode.response_value,
                "model_name": metadata.model_name if metadata is not None else None,
                "validation_duration_ms": round((time.perf_counter() - started_at) * 1000, 3),
            },
        )
    except StartupValidationError as exc:
        services.readiness.mark_failed(exc)
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
    """Registra el contexto de rutas usado para validar artefactos.

    Pasos:
    - Publica el directorio de trabajo actual del proceso.
    - Muestra directorios configurados y sus rutas resueltas.
    - Expone los nombres de archivo esperados para facilitar depuracion local.

    Argumentos:
    - logger: logger estructurado del modulo.
    - settings: configuracion validada ya normalizada.
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

    Argumentos:
    - code: codigo de error a exponer.
    - message: mensaje publico del error.
    - component: componente responsable.
    - failed_stage: etapa de readiness fallida.
    - failed_check: verificacion puntual fallida.
    - retryable: indica si la condicion podria resolverse externamente.

    Retorna:
    - StartupValidationError: error listo para registrar y cachear.
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
    """Valida y normaliza la configuracion de arranque.

    Pasos:
    - Comprueba campos generales requeridos por la aplicacion.
    - Convierte tipos numericos y booleanos desde variables de entorno.
    - Aplica reglas especificas del modo `MODEL` o `DETERMINISTIC_TEST`.

    Argumentos:
    - raw: settings crudos leidos desde entorno.

    Retorna:
    - ValidatedSettings: configuracion segura para el runtime.

    Excepciones:
    - StartupValidationError: si alguna regla de configuracion falla.
    """

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

    return ValidatedSettings(
        app_name=app_name,
        app_version=app_version,
        host=raw.host.strip() or "0.0.0.0",
        port=port,
        log_level=log_level,
        model_dir=model_dir,
        config_dir=config_dir,
        model_filename=model_filename,
        model_metadata_filename=model_metadata_filename,
        policy_filename=policy_filename,
        deterministic_rule_filename=deterministic_rule_filename,
        enable_policy_mapping=enable_policy_mapping,
        request_timeout_seconds=request_timeout_seconds,
        max_request_body_bytes=max_request_body_bytes,
        probability_tolerance=probability_tolerance,
        min_policy_confidence=min_policy_confidence,
        classification_mode=classification_mode,
        min_tunnel_bandwidth_kbps=min_tunnel_bandwidth_kbps,
        max_tunnel_bandwidth_kbps=max_tunnel_bandwidth_kbps,
    )


def _parse_int(raw_value: str, *, failed_check: str, message: str) -> int:
    """Convierte un valor crudo a entero con error de startup tipado."""

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
    """Convierte un valor crudo a flotante con error de startup tipado."""

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
    """Convierte una bandera textual a booleano estricto.

    Argumentos:
    - raw_value: valor crudo proveniente del entorno.
    - failed_check: nombre del chequeo usado en diagnostico.
    - message: mensaje publico del error.

    Retorna:
    - bool: bandera booleana validada.

    Excepciones:
    - StartupValidationError: si el valor no corresponde a un booleano conocido.
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
    """Valida los artefactos requeridos por el modo de clasificacion.

    Pasos:
    - Resuelve artefactos de modelo en modo `MODEL`.
    - Resuelve reglas deterministicas en modo simulador.
    - Valida siempre el archivo de politicas.

    Argumentos:
    - settings: configuracion validada ya normalizada.

    Retorna:
    - ArtifactPaths: rutas listas para carga posterior.
    """

    model_path = None
    metadata_path = None
    deterministic_rule_path = None
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
        deterministic_rule_path = _validate_artifact(
            path=Path(settings.config_dir) / settings.deterministic_rule_filename,
            configured_filename=settings.deterministic_rule_filename,
            component="deterministic_rules",
            not_found_code="DETERMINISTIC_RULE_FILE_NOT_FOUND",
        )

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
    """Verifica que un artefacto exista, sea legible y no este vacio.

    Pasos:
    - Resuelve la ruta real.
    - Comprueba existencia, tipo de archivo y permisos de lectura.
    - Verifica que el archivo sea UTF-8 legible y no este vacio.

    Argumentos:
    - path: ruta candidata del artefacto.
    - configured_filename: nombre configurado a exponer en mensajes.
    - component: componente logico del artefacto.
    - not_found_code: codigo especifico para ausencia del archivo.

    Retorna:
    - Path: ruta resuelta y validada.

    Excepciones:
    - StartupValidationError: si el artefacto no cumple el contrato de lectura.
    """

    try:
        resolved = path.resolve()
    except OSError as exc:
        raise _artifact_error(
            code="ARTIFACT_NOT_READABLE",
            message=Messages.artifact_path_resolution(configured_filename),
            component=component,
            failed_check="path_resolution",
        ) from exc

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
    """Construye un error tipado de validacion de artefactos."""

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

    Argumentos:
    - settings: configuracion validada.
    - artifacts: rutas de artefactos ya verificadas.
    - readiness: estado mutable donde se marcan cargas parciales.

    Retorna:
    - tuple[ModelMetadata | None, PolicyFile, DeterministicRuleFile | None]:
      metadata opcional, politica validada y reglas deterministicas opcionales.
    """

    metadata = None
    deterministic_rules = None
    if settings.classification_mode is ClassificationMode.MODEL:
        metadata = _load_metadata_for_startup(artifacts.metadata_path)
        readiness.metadata_loaded = True
    else:
        deterministic_rules = _load_deterministic_rules_for_startup(artifacts.deterministic_rule_path)
    policy_file = _load_policy_for_startup(artifacts.policy_path)
    readiness.policy_loaded = True
    return metadata, policy_file, deterministic_rules


def _load_metadata_for_startup(path: Path | None) -> ModelMetadata:
    """Carga metadata del modelo con diagnostico de startup controlado.

    Argumentos:
    - path: ruta validada del archivo de metadata.

    Retorna:
    - ModelMetadata: metadata cargada y validada.

    Excepciones:
    - StartupValidationError: si el JSON o el contrato de metadata fallan.
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
    """Carga la politica y traduce sus fallos al contrato de readiness."""

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
    """Carga reglas deterministicas con diagnostico homogeno de startup."""

    assert path is not None
    try:
        return load_deterministic_rules(path)
    except ValueError as exc:
        raise _startup_error(
            code="POLICY_SCHEMA_INVALID",
            message=Messages.DETERMINISTIC_RULE_SCHEMA_INVALID,
            component="deterministic_rules",
            failed_stage=STAGE_METADATA_POLICY_SCHEMA_VALIDATION,
            failed_check="schema_validation",
            retryable=False,
        ) from exc


def _build_classifier(
    settings: ValidatedSettings,
    metadata: ModelMetadata | None,
    deterministic_rules: DeterministicRuleFile | None,
    readiness: ReadinessState,
) -> Any:
    """Construye el clasificador segun el modo configurado.

    Pasos:
    - En modo deterministico, crea el simulador y ejecuta una auto-prueba.
    - En modo modelo, carga el booster real y ejecuta inferencia sintetica.
    - Marca readiness parcial cuando la etapa termina correctamente.

    Argumentos:
    - settings: configuracion validada de la app.
    - metadata: metadata del modelo cuando aplica.
    - deterministic_rules: reglas del simulador cuando aplica.
    - readiness: estado mutable para registrar progreso.

    Retorna:
    - Any: clasificador listo para la ruta `/api/v1/classify`.
    """

    if settings.classification_mode is ClassificationMode.DETERMINISTIC_TEST:
        classifier = DeterministicClassifier(deterministic_rules)
        _run_synthetic_self_test(classifier.predict(SYNTHETIC_PACKET_FEATURES))
        readiness.synthetic_inference_passed = True
        return classifier

    logger = logging.getLogger(__name__)
    logger.info(
        Messages.MODEL_LOAD_STARTED,
        extra={
            "service": settings.app_name,
            "event": "model_load_started",
            "classification_mode": settings.classification_mode.response_value,
        },
    )
    predictor = _load_predictor(settings, metadata)
    readiness.model_loaded = True
    logger.info(
        Messages.MODEL_LOAD_PASSED,
        extra={
            "service": settings.app_name,
            "event": "model_load_passed",
            "classification_mode": settings.classification_mode.response_value,
            "model_name": metadata.model_name if metadata is not None else None,
        },
    )
    if SYNTHETIC_PACKET_FEATURES["eth_type"] not in MODEL_SUPPORTED_ETHERTYPES:
        raise _runtime_error(
            code="MODEL_SELF_TEST_CONFIGURATION_INVALID",
            message=Messages.SYNTHETIC_ETHERTYPE_INVALID,
            failed_check="synthetic_eth_type",
            retryable=False,
        )
    _run_synthetic_self_test(predictor.predict(SYNTHETIC_PACKET_FEATURES))
    readiness.synthetic_inference_passed = True
    logger.info(
        Messages.SYNTHETIC_INFERENCE_PASSED,
        extra={
            "service": settings.app_name,
            "event": "synthetic_inference_passed",
            "classification_mode": settings.classification_mode.response_value,
            "model_name": metadata.model_name if metadata is not None else None,
        },
    )
    return predictor


def _load_predictor(settings: ValidatedSettings, metadata: ModelMetadata | None) -> Predictor:
    """Carga el predictor XGBoost y valida su compatibilidad de runtime.

    Pasos:
    - Importa XGBoost y carga el booster desde disco.
    - Verifica cantidad de features del artefacto.
    - Inspecciona configuracion de objetivo y numero de clases cuando existe.

    Argumentos:
    - settings: configuracion validada con directorio y nombre del modelo.
    - metadata: metadata validada del modelo.

    Retorna:
    - Predictor: predictor listo para inferencia.

    Excepciones:
    - StartupValidationError: si el runtime o el artefacto son incompatibles.
    """

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

    booster = xgboost.Booster()
    model_path = Path(settings.model_dir) / settings.model_filename
    try:
        booster.load_model(model_path)
    except Exception as exc:
        raise _runtime_error(
            code="MODEL_LOAD_FAILED",
            message=Messages.MODEL_ARTIFACT_LOAD_FAILED,
            failed_check="booster_load_model",
            retryable=False,
        ) from exc

    try:
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
        config = json.loads(booster.save_config())
    except Exception:
        config = None

    if config is not None:
        # La inspeccion del config no reemplaza la prediccion sintetica, pero
        # permite fallar temprano cuando el objetivo o el numero de clases son incompatibles.
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
    """Construye un error tipado para la etapa de runtime del modelo."""

    return _startup_error(
        code=code,
        message=message,
        component="inference_runtime",
        failed_stage=STAGE_RUNTIME_MODEL_COMPATIBILITY,
        failed_check=failed_check,
        retryable=retryable,
    )


def _run_synthetic_self_test(result: PredictionResult) -> None:
    """Verifica que una inferencia sintetica cumpla el contrato probabilistico.

    Pasos:
    - Comprueba cardinalidad total de probabilidades.
    - Valida finitud y rango numerico.
    - Verifica que la clase resultante pueda decodificarse.

    Argumentos:
    - result: resultado ya normalizado de una auto-prueba.

    Excepciones:
    - StartupValidationError: si la salida sintetica es incompatible.
    """

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
    """Valida la cobertura total del mapa de politicas por clase.

    Pasos:
    - Rechaza clases desconocidas en el archivo.
    - Rechaza clases obligatorias faltantes.
    - Verifica perfil por defecto y restricciones de ancho de banda por clase.

    Argumentos:
    - policy_file: archivo de politicas ya validado.
    - expected_classes: clases que deben resolverse obligatoriamente.
    - min_tunnel_bandwidth_kbps: minimo demostrador para perfiles de tunel.
    - max_tunnel_bandwidth_kbps: maximo demostrador permitido.

    Excepciones:
    - StartupValidationError: si el mapa es incompleto o invalido.
    """

    policy_classes = set(policy_file.class_policies)
    expected_class_set = set(expected_classes)
    unknown_classes = policy_classes - expected_class_set
    if unknown_classes:
        raise _policy_error(
            code="POLICY_CLASS_UNKNOWN",
            message=Messages.POLICY_UNKNOWN_CLASS,
            failed_check="unknown_class",
        )

    missing_classes = expected_class_set - policy_classes
    if missing_classes:
        raise _policy_error(
            code="POLICY_MAP_INCOMPLETE",
            message=Messages.POLICY_MISSING_CLASS,
            failed_check="missing_class",
        )

    _validate_default_profile(policy_file.default_profile)
    for class_name in expected_classes:
        policy = policy_file.class_policies[class_name]
        _validate_policy_serialization(policy)
        _validate_tunnel_bandwidth(
            class_name=class_name,
            policy=policy,
            min_tunnel_bandwidth_kbps=min_tunnel_bandwidth_kbps,
            max_tunnel_bandwidth_kbps=max_tunnel_bandwidth_kbps,
        )


def _validate_default_profile(policy: TrafficPolicy) -> None:
    """Verifica que el perfil por defecto conserve ancho de banda cero."""

    _validate_policy_serialization(policy)
    if policy.path_constraints.requested_bandwidth_kbps != 0:
        raise _policy_error(
            code="POLICY_VALUE_INVALID",
            message=Messages.DEFAULT_PROFILE_BANDWIDTH_ZERO,
            failed_check="default_bandwidth_zero",
        )


def _validate_policy_serialization(policy: TrafficPolicy) -> None:
    """Confirma que una politica validada siga siendo serializable."""

    try:
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
    """Valida el ancho de banda de una politica de clase.

    Argumentos:
    - class_name: nombre de la clase evaluada.
    - policy: politica asociada a la clase.
    - min_tunnel_bandwidth_kbps: minimo demostrador permitido.
    - max_tunnel_bandwidth_kbps: maximo demostrador permitido.

    Excepciones:
    - StartupValidationError: si el ancho de banda sale del rango permitido.
    """

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
    """Construye un error tipado para la validacion del mapa de politicas."""

    return _startup_error(
        code=code,
        message=message,
        component="policy",
        failed_stage=STAGE_COMPLETE_POLICY_MAP_VALIDATION,
        failed_check=failed_check,
        retryable=False,
    )


def _first_error_loc(exc: ValidationError) -> str:
    """Extrae la primera localizacion de error Pydantic como cadena.

    Argumentos:
    - exc: excepcion de validacion capturada.

    Retorna:
    - str: ruta textual del primer error o un literal por defecto.
    """

    first_error = exc.errors()[0]
    loc = first_error.get("loc", ())
    return ".".join(str(part) for part in loc) or "schema_validation"
