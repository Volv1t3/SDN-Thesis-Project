"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370

Main principal encargado de construir toda la API y de encender el sistema con los Middlewares necesarios y
las rutas toleradas por el sistema

Pasos:
- Configura logging durante el lifespan.
- Inicializa dependencias compartidas una sola vez por proceso.
- Registra middleware y handlers de error estructurados.

Notas:
- La correlacion HTTP se genera en middleware antes de validaciones y endpoints.
- Los handlers centralizados reutilizan esa correlacion en cuerpos de error y logs.
"""

from __future__ import annotations

import json
import logging
from contextlib import asynccontextmanager

#! Imports desde Starlette y FastAPI usados internamente por la aplicacion
from fastapi.encoders import jsonable_encoder
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from starlette.exceptions import HTTPException as StarletteHTTPException
from starlette.middleware import Middleware
from starlette.status import (
    HTTP_400_BAD_REQUEST,
    HTTP_404_NOT_FOUND,
    HTTP_413_REQUEST_ENTITY_TOO_LARGE,
    HTTP_422_UNPROCESSABLE_ENTITY,
    HTTP_500_INTERNAL_SERVER_ERROR,
)

#! Imports de la aplicacion
from app.api.health import router as health_router
from app.api.inference import router as inference_router
from app.config import DEFAULT_APP_NAME, DEFAULT_APP_VERSION, get_raw_settings, get_safe_log_level
from app.dependencies import build_services
from app.observability.identity import initialize_process_identity
from app.observability.classification_metrics import record_request_error
from app.observability.metrics_route import router as metrics_router
from app.sdn_mpls_ml_exceptions import (
    AppError,
    InferenceCapacityExceededError,
    InvalidContentLengthError,
    InvalidJsonError,
    ModelInferenceFailedError,
    ModelNotReadyError,
    ModelOutputInvalidError,
    PolicyMappingFailedError,
    RequestTooLargeError,
    RequestValidationAppError,
)
from app.sdn_mpls_ml_http_responses import build_error_response
from app.sdn_mpls_ml_logging_config import configure_logging
from app.sdn_mpls_ml_messages import Messages
from app.middleware import CorrelationIdMiddleware, RequestSizeLimitMiddleware

logger = logging.getLogger(__name__)


#! Definimos codigos de errores y de componentes
HTTP_NOT_FOUND_CODE = "HTTP_NOT_FOUND"
HTTP_NOT_FOUND_COMPONENT = "http_routing"
HTTP_NOT_FOUND_STAGE = "request_routing"
HTTP_NOT_FOUND_CHECK = "route_resolution"
HTTP_NOT_FOUND_RETRYABLE = False

INTERNAL_ERROR_CODE = "INTERNAL_ERROR"
INTERNAL_ERROR_MESSAGE = Messages.INTERNAL_ERROR
INTERNAL_ERROR_COMPONENT = "application"
INTERNAL_ERROR_STAGE = "request_processing"
INTERNAL_ERROR_CHECK = "unhandled_exception"
INTERNAL_ERROR_RETRYABLE = True


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Funcion encargada de generar y cargar los recursos compartidos que requiere FastAPI
    durante todo el ciclo de vida de la aplicacion (por esto lifespan), dado que carga los
    recursos del sistema como configuraciones, y servicios durante el startup de la aplicacion (lo
    que permite a la API validar settings, artefactos y el modelo) y luego usarlos dentro de toda la aplicacion.

    En este caso el codigo antes del statement de yield es el que se utiliza durante la inicializacion del entorno, y
    el codigo que podria estar luego al finalizar. En este caso dado que no cargamos informacion ni conexiones con servicios
    externos, no guardamos esta informacion antes del yield, pero mantenemos la informacion viva durante la ejecucion.

    Pasos:
    - Carga settings crudos desde entorno.
    - Configura logging con el nivel seguro derivado.
    - Construye los servicios y el estado de readiness cacheado.

    Args:
        app: aplicacion FastAPI en construccion.

    Returns:
        AsyncIterator[None]: contexto asincrono del lifespan.
    """

    raw_settings = get_raw_settings()
    identity = initialize_process_identity(
        service=raw_settings.app_name or DEFAULT_APP_NAME,
        configured_instance_id=raw_settings.instance_id,
    )
    app.state.process_identity = identity
    configure_logging(get_safe_log_level(raw_settings.log_level), raw_settings)
    app.state.services = build_services(raw_settings=raw_settings, process_identity=identity)
    if app.state.services.settings is not None:
        logger.info(
            (
                Messages.PROMETHEUS_METRICS_ENABLED
                if app.state.services.settings.enable_prometheus_metrics
                else Messages.PROMETHEUS_METRICS_DISABLED
            ),
            extra={
                "service": app.state.services.settings.app_name,
                "event": (
                    "prometheus_metrics_enabled"
                    if app.state.services.settings.enable_prometheus_metrics
                    else "prometheus_metrics_disabled"
                ),
            },
        )
    yield


"""
OJO: Aqui definimos el middleware de la aplicacion como instancias de las clases CorrelationMiddleware (encargada de
configurar para cada request, inclusive antes de pasar por una revision de tamano) un ID que permite seguir el flujo de la request
dentro de todo el sistema (util para la salida a prometheus y logs). Seguido a este tenemos el Request size limit, que aunque 
desde Java no deberia de presentar un problema, nos permite defender a la API en caso de uso indebido.
"""
middleware = [
    Middleware(CorrelationIdMiddleware),
    Middleware(RequestSizeLimitMiddleware),
]


"""
Aqui definimos la aplicacion en general
"""
app = FastAPI(
    title="SDN-MPLS-ML Tech Demonstrator Python ML API",
    description="""
    Santiago Arellano 00328370 
    Sistema de clasificacion de trafico y definicion de politicas para paquetes nuevos en el sistema general del 
    SDN-MPLS-ML Tech Demonstrator
    """,
    version=DEFAULT_APP_VERSION,
    lifespan=lifespan,
    middleware=middleware,
)
app.include_router(health_router)
app.include_router(inference_router)
app.include_router(metrics_router)


def _error_response(
    status_code: int,
    code: str,
    message: str,
    request_id: str | None = None,
    *,
    component: str | None = None,
    failed_stage: str | None = None,
    failed_check: str | None = None,
    retryable: bool | None = None,
    details=None,
):
    """
    Funcion helper que permite redirigir respuestas a errores en la API al handler especifico internamente
    mediante los helpers definidos en sdn_mpls_ml_http_responses.py

    Pasos:
    - Arma el cuerpo principal con `ErrorResponse`. (esto se realiza dentro de la funcion interna)
    - Adjunta detalles opcionales de validacion.
    - Devuelve un `JSONResponse` con el codigo apropiado. (retorno de la funcion interna)

    Args:
        status_code: codigo HTTP a emitir.
        code: codigo de error de la aplicacion.
        message: mensaje publico del error.
        request_id: correlacion opcional de la solicitud.
        component: componente implicado en el error.
        failed_stage: etapa logica fallida, si aplica.
        failed_check: verificacion puntual fallida, si aplica.
        retryable: indicador de reintento, si aplica.
        details: informacion adicional serializable.

    Returns:
        JSONResponse: respuesta HTTP final.

    Notes:
    - Cuando `request_id` existe debe coincidir con el header `X-Request-ID`.
    """

    return build_error_response(
        status_code,
        code,
        message,
        request_id=request_id,
        component=component,
        failed_stage=failed_stage,
        failed_check=failed_check,
        retryable=retryable,
        details=details,
    )


def _request_id_from_request(request: Request) -> str | None:
    """
    Obtiene el request ID desde el contexto del request si existe. Esta funcion hace uso del
    estado de la request y de este obtiene el request id que se registra a traves del Middleware
    CorrelationMiddleware.


    Args:
        request: solicitud HTTP activa.

    Returns:
        str | None: identificador de correlacion o `None`.

    Notas:
    - El valor debe haber sido inicializado por `CorrelationIdMiddleware`.
    """

    return getattr(request.state, "request_id", None)


def _service_name_from_request(request: Request) -> str | None:
    """
    Deriva el nombre del servicio desde el estado compartido de la app. Esto extra el nombre de la aplicacion
    registrado en la configuracion inicial o en los defaults de la aplicacion

    Args:
        request: solicitud HTTP activa.

    Returns:
        str | None: nombre del servicio o `None` si no esta disponible.
    """

    services = getattr(request.app.state, "services", None)

    if services is None:
        return None

    settings = services.settings
    if settings is not None:
        return settings.app_name
    return services.raw_settings.app_name


def _prometheus_metrics_enabled(request: Request) -> bool:
    """Indica si esta habilitada la exportacion para el worker actual."""

    services = getattr(request.app.state, "services", None)
    settings = getattr(services, "settings", None)
    return settings is not None and settings.enable_prometheus_metrics


def _log_request_event(
    request: Request,
    *,
    level: int,
    event: str,
    code: str,
    request_id: str | None,
    component: str | None = None,
    failed_stage: str | None = None,
    failed_check: str | None = None,
    retryable: bool | None = None,
    http_status_code: int | None = None,
    extra_fields: dict[str, object] | None = None,
) -> None:
    """
    Registra un evento estructurado asociado a una solicitud HTTP. Este es un log de un servicio que asocia una request
    con su id de correlacion y con sus componentes y errores posibles.

    Pasos:
    - Obtiene el nombre del servicio desde el estado compartido.
    - Adjunta request ID y metadatos de error relevantes.
    - Emite el evento con el nivel indicado.

    Args:
        request: solicitud HTTP activa.
        level: nivel numerico del logger.
        event: nombre estructurado del evento.
        code: codigo funcional del error o condicion.
        request_id: correlacion del request actual.
        component: componente implicado cuando aplica.
        failed_stage: etapa logica asociada cuando aplica.
        failed_check: chequeo puntual asociado cuando aplica.
        retryable: bandera de reintento cuando aplica.
        http_status_code: codigo HTTP asociado cuando aplica.

    Notes:
    - Estos eventos deben compartir el mismo `request_id` que la respuesta HTTP asociada.
    """

    payload = {
        "service": _service_name_from_request(request),
        "event": event,
        "request_id": request_id,
        "component": component,
        "failed_stage": failed_stage,
        "failed_check": failed_check,
        "retryable": retryable,
        "error_code": code,
        "http_status_code": http_status_code,
    }
    if extra_fields:
        payload.update(extra_fields)

    logger.log(level, _request_event_message(event), extra=payload)


def _request_event_message(event: str) -> str:
    """
    Resuelve el texto humano asociado a un evento HTTP estructurado. Este sistema nos permite resolver parcialmente los
    mensajes de errores mas comunes dentro de la aplicacion a este nivel en base a un evento proporcionado que tiene su
    identificador en ingles, y a traves de este buscamos el evento en Messages

    Args:
        event: identificador maquina del evento.

    Returns:
        str: mensaje humano usado en logging.
    """

    event_messages = {
        "model_not_ready": Messages.MODEL_NOT_READY_LOG,
        "model_inference_failed": Messages.MODEL_INFERENCE_FAILED_LOG,
        "model_output_invalid": Messages.MODEL_OUTPUT_INVALID_LOG,
        "policy_mapping_failed": Messages.POLICY_MAPPING_FAILED_LOG,
        "inference_capacity_exceeded": Messages.INFERENCE_CAPACITY_EXCEEDED_LOG,
        "invalid_content_length": Messages.INVALID_CONTENT_LENGTH_LOG,
        "invalid_json_received": Messages.INVALID_JSON_RECEIVED,
        "request_validation_failed": Messages.REQUEST_VALIDATION_LOG,
        "request_too_large": Messages.REQUEST_TOO_LARGE_LOG,
        "http_request_not_found": Messages.HTTP_REQUEST_NOT_FOUND,
        "http_request_failed": Messages.HTTP_REQUEST_FAILED,
        "unhandled_exception": Messages.UNHANDLED_EXCEPTION,
    }
    return event_messages.get(event, event)


def _contains_invalid_json_error(errors: list[dict[str, object]]) -> bool:
    """
    Determina si FastAPI reporto un fallo de parseo JSON en validacion.

    Pasos:
    - Recorre la lista normalizada de errores de `RequestValidationError`.
    - Busca entradas con tipo `json_invalid`.
    - Devuelve una bandera para ramificar el contrato HTTP final.

    Args:
        errors: lista serializable de errores producidos por FastAPI.

    Returns:
        bool: `True` cuando la solicitud fallo al parsear JSON.

    Notes:
    - FastAPI encapsula estos fallos dentro de `RequestValidationError`,
      por lo que no siempre llegan al handler dedicado de `json.JSONDecodeError`.
    """

    return any(error.get("type") == "json_invalid" for error in errors)


@app.exception_handler(AppError)
async def handle_app_error(request: Request, exc: AppError):
    """
    Convierte errores de dominio en respuestas HTTP estructuradas. Esto nos permite notificar mediante HTTP que ha existido
    un error a nivel de la aplicacion registrado mediante la Excepcio de tipo AppError.

    Pasos:
    - Reutiliza el `request_id` ya presente en la excepcion cuando existe.
    - Usa la correlacion del request como respaldo si la excepcion no la trae.
    - Emite logs request-scoped para errores relevantes.

    Notas:
    - El cuerpo de error debe quedar alineado con `X-Request-ID`.
    """

    request_id = getattr(exc, "request_id", None) or _request_id_from_request(request)
    record_request_error(
        error_code=exc.code,
        component=exc.component,
        enabled=_prometheus_metrics_enabled(request),
    )

    # ? En esta seccion, en base al error de la excepcion que podemos recibir, registramos los detalles del error y el
    # ? codigo con sju mensaje lo que nos permite notificar al usuario final del error real y el componente del error
    if exc.code == ModelNotReadyError.code:
        _log_request_event(
            request,
            level=logging.WARNING,
            event="model_not_ready",
            code=exc.code,
            request_id=request_id,
            component=exc.component,
            failed_stage=exc.failed_stage,
            failed_check=exc.failed_check,
            retryable=exc.retryable,
        )
    elif exc.code == ModelInferenceFailedError.code:
        _log_request_event(
            request,
            level=logging.ERROR,
            event="model_inference_failed",
            code=exc.code,
            request_id=request_id,
            component=exc.component,
            failed_stage=exc.failed_stage,
            failed_check=exc.failed_check,
            retryable=exc.retryable,
        )
    elif exc.code == ModelOutputInvalidError.code:
        _log_request_event(
            request,
            level=logging.ERROR,
            event="model_output_invalid",
            code=exc.code,
            request_id=request_id,
            component=exc.component,
            failed_stage=exc.failed_stage,
            failed_check=exc.failed_check,
            retryable=exc.retryable,
        )
    elif exc.code == PolicyMappingFailedError.code:
        _log_request_event(
            request,
            level=logging.ERROR,
            event="policy_mapping_failed",
            code=exc.code,
            request_id=request_id,
            component=exc.component,
            failed_stage=exc.failed_stage,
            failed_check=exc.failed_check,
            retryable=exc.retryable,
        )
    elif exc.code == InferenceCapacityExceededError.code:
        services = getattr(request.app.state, "services", None)
        classifier_pool = getattr(services, "classifier_pool", None)
        _log_request_event(
            request,
            level=logging.WARNING,
            event="inference_capacity_exceeded",
            code=exc.code,
            request_id=request_id,
            component=exc.component,
            failed_stage=exc.failed_stage,
            failed_check=exc.failed_check,
            retryable=exc.retryable,
            http_status_code=exc.status_code,
            extra_fields={
                "pool_capacity": classifier_pool.capacity if classifier_pool is not None else None,
                "pool_available": classifier_pool.available
                if classifier_pool is not None
                else None,
                "pool_borrowed": classifier_pool.borrowed if classifier_pool is not None else None,
            },
        )
    elif exc.code == InvalidContentLengthError.code:
        _log_request_event(
            request,
            level=logging.WARNING,
            event="invalid_content_length",
            code=exc.code,
            request_id=request_id,
            component=exc.component,
            failed_stage=exc.failed_stage,
            failed_check=exc.failed_check,
            retryable=exc.retryable,
        )
    return _error_response(
        exc.status_code,
        exc.code,
        exc.message,
        request_id=request_id,
        component=exc.component,
        failed_stage=exc.failed_stage,
        failed_check=exc.failed_check,
        retryable=exc.retryable,
    )


@app.exception_handler(RequestValidationError)
async def handle_request_validation(request: Request, exc: RequestValidationError):
    """
    Normaliza errores de validacion de FastAPI al contrato de la API. Este sistema nos permite registrar un evento en el
    log de la aplicacion de un problema de un posible error de validacion de una request (un error de Pydantic por ejemplo) y luego
    retornar una respuesta de error al usuario.

    Pasos:
    - Lee el `request_id` del contexto ya inicializado por middleware.
    - Detecta si FastAPI reporto un parseo JSON invalido como `json_invalid`.
    - Emite el evento estructurado que corresponda al tipo real del fallo.
    - Devuelve el payload uniforme apropiado con detalles serializables.
    """

    # ? Obtenemos la request ID desde la request y el header asociado
    request_id = _request_id_from_request(request)

    # ? Convertimos el error de validacion en una string de JSON econded text para poder enviar el contenido completo del error
    # ? de validacion que viene de un sistema interno sea de FastAPI o de Pydantic sin transcribirlo directamente a la respuesta
    # ? de la API
    errors = jsonable_encoder(exc.errors())
    if _contains_invalid_json_error(errors):
        record_request_error(
            error_code=InvalidJsonError.code,
            component=InvalidJsonError.component,
            enabled=_prometheus_metrics_enabled(request),
        )
        _log_request_event(
            request,
            level=logging.WARNING,
            event="invalid_json_received",
            code=InvalidJsonError.code,
            request_id=request_id,
            component=InvalidJsonError.component,
            failed_stage=InvalidJsonError.failed_stage,
            failed_check=InvalidJsonError.failed_check,
            retryable=InvalidJsonError.retryable,
        )
        return _error_response(
            HTTP_400_BAD_REQUEST,
            InvalidJsonError.code,
            InvalidJsonError.message,
            request_id=request_id,
            component=InvalidJsonError.component,
            failed_stage=InvalidJsonError.failed_stage,
            failed_check=InvalidJsonError.failed_check,
            retryable=InvalidJsonError.retryable,
            details=errors,
        )
    # ? Si no es un error del JSON ingresado para la request enviamos el error de validacion directamente
    record_request_error(
        error_code=RequestValidationAppError.code,
        component=RequestValidationAppError.component,
        enabled=_prometheus_metrics_enabled(request),
    )
    _log_request_event(
        request,
        level=logging.WARNING,
        event="request_validation_failed",
        code=RequestValidationAppError.code,
        request_id=request_id,
        component=RequestValidationAppError.component,
        failed_stage=RequestValidationAppError.failed_stage,
        failed_check=RequestValidationAppError.failed_check,
        retryable=RequestValidationAppError.retryable,
    )
    return _error_response(
        HTTP_422_UNPROCESSABLE_ENTITY,
        RequestValidationAppError.code,
        RequestValidationAppError.message,
        request_id=request_id,
        component=RequestValidationAppError.component,
        failed_stage=RequestValidationAppError.failed_stage,
        failed_check=RequestValidationAppError.failed_check,
        retryable=RequestValidationAppError.retryable,
        details=errors,
    )


@app.exception_handler(json.JSONDecodeError)
async def handle_json_error(request: Request, exc: json.JSONDecodeError):
    """
    Convierte errores de JSON invalido en un payload tipado y registra tanto el log de estos eventos como una respuesta
    HTTP.

    Pasos:
    - Reutiliza la correlacion del request ya generada por middleware.
    - Devuelve el payload uniforme de error sin perder `request_id`.
    """

    del exc
    request_id = _request_id_from_request(request)
    record_request_error(
        error_code=InvalidJsonError.code,
        component=InvalidJsonError.component,
        enabled=_prometheus_metrics_enabled(request),
    )
    _log_request_event(
        request,
        level=logging.WARNING,
        event="invalid_json_received",
        code=InvalidJsonError.code,
        request_id=request_id,
        component=InvalidJsonError.component,
        failed_stage=InvalidJsonError.failed_stage,
        failed_check=InvalidJsonError.failed_check,
        retryable=InvalidJsonError.retryable,
    )
    return _error_response(
        HTTP_400_BAD_REQUEST,
        InvalidJsonError.code,
        InvalidJsonError.message,
        request_id=request_id,
        component=InvalidJsonError.component,
        failed_stage=InvalidJsonError.failed_stage,
        failed_check=InvalidJsonError.failed_check,
        retryable=InvalidJsonError.retryable,
    )


@app.exception_handler(StarletteHTTPException)
async def handle_http_exception(request: Request, exc: StarletteHTTPException):
    """Normaliza excepciones HTTP genericas del framework.

    Pasos:
    - Reutiliza el `request_id` del request actual.
    - Conserva el contrato especial de `413 REQUEST_TOO_LARGE`.
    - Devuelve un payload uniforme para el resto de errores HTTP.
    """

    if exc.status_code == HTTP_413_REQUEST_ENTITY_TOO_LARGE:
        request_id = _request_id_from_request(request)
        record_request_error(
            error_code=RequestTooLargeError.code,
            component=RequestTooLargeError.component,
            enabled=_prometheus_metrics_enabled(request),
        )
        _log_request_event(
            request,
            level=logging.WARNING,
            event="request_too_large",
            code=RequestTooLargeError.code,
            request_id=request_id,
            component=RequestTooLargeError.component,
            failed_stage=RequestTooLargeError.failed_stage,
            failed_check=RequestTooLargeError.failed_check,
            retryable=RequestTooLargeError.retryable,
            http_status_code=exc.status_code,
        )
        return _error_response(
            exc.status_code,
            RequestTooLargeError.code,
            RequestTooLargeError.message,
            request_id=request_id,
            component=RequestTooLargeError.component,
            failed_stage=RequestTooLargeError.failed_stage,
            failed_check=RequestTooLargeError.failed_check,
            retryable=RequestTooLargeError.retryable,
        )

    request_id = _request_id_from_request(request)
    if exc.status_code == HTTP_404_NOT_FOUND:
        record_request_error(
            error_code=HTTP_NOT_FOUND_CODE,
            component=HTTP_NOT_FOUND_COMPONENT,
            enabled=_prometheus_metrics_enabled(request),
        )
        _log_request_event(
            request,
            level=logging.WARNING,
            event="http_request_not_found",
            code=HTTP_NOT_FOUND_CODE,
            request_id=request_id,
            component=HTTP_NOT_FOUND_COMPONENT,
            failed_stage=HTTP_NOT_FOUND_STAGE,
            failed_check=HTTP_NOT_FOUND_CHECK,
            retryable=HTTP_NOT_FOUND_RETRYABLE,
            http_status_code=exc.status_code,
        )
        return _error_response(
            exc.status_code,
            HTTP_NOT_FOUND_CODE,
            Messages.HTTP_NOT_FOUND,
            request_id=request_id,
            component=HTTP_NOT_FOUND_COMPONENT,
            failed_stage=HTTP_NOT_FOUND_STAGE,
            failed_check=HTTP_NOT_FOUND_CHECK,
            retryable=HTTP_NOT_FOUND_RETRYABLE,
        )

    record_request_error(
        error_code="HTTP_ERROR", component="unknown", enabled=_prometheus_metrics_enabled(request)
    )
    _log_request_event(
        request,
        level=logging.WARNING,
        event="http_request_failed",
        code="HTTP_ERROR",
        request_id=request_id,
        http_status_code=exc.status_code,
    )
    return _error_response(
        exc.status_code, "HTTP_ERROR", Messages.HTTP_REQUEST_FAILED, request_id=request_id
    )


@app.exception_handler(Exception)
async def handle_unhandled_exception(request: Request, exc: Exception):
    """Normaliza fallos no controlados al contrato interno de error.

    Pasos:
    - Reutiliza la correlacion ya creada por middleware.
    - Emite un evento estructurado sin incluir payloads ni trazas.
    - Devuelve un `500 INTERNAL_ERROR` uniforme y reintentable.

    Argumentos:
    - request: solicitud HTTP activa.
    - exc: excepcion no controlada capturada por FastAPI.

    Retorna:
    - JSONResponse: respuesta uniforme de error interno.

    Notas:
    - El nivel `ERROR` solo registra metadatos acotados del fallo.
    - No se expone contenido del request ni stack trace en esta ruta normal.
    """

    del exc
    request_id = _request_id_from_request(request)
    record_request_error(
        error_code=INTERNAL_ERROR_CODE,
        component=INTERNAL_ERROR_COMPONENT,
        enabled=_prometheus_metrics_enabled(request),
    )
    _log_request_event(
        request,
        level=logging.ERROR,
        event="unhandled_exception",
        code=INTERNAL_ERROR_CODE,
        request_id=request_id,
        component=INTERNAL_ERROR_COMPONENT,
        failed_stage=INTERNAL_ERROR_STAGE,
        failed_check=INTERNAL_ERROR_CHECK,
        retryable=INTERNAL_ERROR_RETRYABLE,
        http_status_code=HTTP_500_INTERNAL_SERVER_ERROR,
    )
    return _error_response(
        HTTP_500_INTERNAL_SERVER_ERROR,
        INTERNAL_ERROR_CODE,
        INTERNAL_ERROR_MESSAGE,
        request_id=request_id,
        component=INTERNAL_ERROR_COMPONENT,
        failed_stage=INTERNAL_ERROR_STAGE,
        failed_check=INTERNAL_ERROR_CHECK,
        retryable=INTERNAL_ERROR_RETRYABLE,
    )
