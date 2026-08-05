"""Construye la aplicacion FastAPI y sus manejadores globales.

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

from app.api.health import router as health_router
from app.api.inference import router as inference_router
from app.config import DEFAULT_APP_VERSION, get_raw_settings, get_safe_log_level
from app.dependencies import build_services
from app.exceptions import (
    AppError,
    InvalidContentLengthError,
    InvalidJsonError,
    ModelInferenceFailedError,
    ModelNotReadyError,
    ModelOutputInvalidError,
    PolicyMappingFailedError,
    RequestTooLargeError,
    RequestValidationAppError,
)
from app.http_responses import build_error_response
from app.logging_config import configure_logging
from app.messages import Messages
from app.middleware import CorrelationIdMiddleware, RequestSizeLimitMiddleware

logger = logging.getLogger(__name__)

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
    """Inicializa recursos compartidos durante el ciclo de vida de FastAPI.

    Pasos:
    - Carga settings crudos desde entorno.
    - Configura logging con el nivel seguro derivado.
    - Construye los servicios y el estado de readiness cacheado.

    Argumentos:
    - app: aplicacion FastAPI en construccion.

    Retorna:
    - AsyncIterator[None]: contexto asincrono del lifespan.
    """

    raw_settings = get_raw_settings()
    configure_logging(get_safe_log_level(raw_settings.log_level))
    app.state.services = build_services(raw_settings=raw_settings)
    yield


middleware = [
    Middleware(CorrelationIdMiddleware),
    Middleware(RequestSizeLimitMiddleware),
]

app = FastAPI(
    title="SDNFlow Inference API",
    version=DEFAULT_APP_VERSION,
    lifespan=lifespan,
    middleware=middleware,
)
app.include_router(health_router)
app.include_router(inference_router)


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
    """Construye una respuesta de error uniforme para la API.

    Pasos:
    - Arma el cuerpo principal con `ErrorResponse`.
    - Adjunta detalles opcionales de validacion.
    - Devuelve un `JSONResponse` con el codigo apropiado.

    Argumentos:
    - status_code: codigo HTTP a emitir.
    - code: codigo de error de la aplicacion.
    - message: mensaje publico del error.
    - request_id: correlacion opcional de la solicitud.
    - component: componente implicado en el error.
    - failed_stage: etapa logica fallida, si aplica.
    - failed_check: verificacion puntual fallida, si aplica.
    - retryable: indicador de reintento, si aplica.
    - details: informacion adicional serializable.

    Retorna:
    - JSONResponse: respuesta HTTP final.

    Notas:
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
    """Obtiene el request ID desde el contexto del request si existe.

    Argumentos:
    - request: solicitud HTTP activa.

    Retorna:
    - str | None: identificador de correlacion o `None`.

    Notas:
    - El valor debe haber sido inicializado por `CorrelationIdMiddleware`.
    """

    return getattr(request.state, "request_id", None)


def _service_name_from_request(request: Request) -> str | None:
    """Deriva el nombre del servicio desde el estado compartido de la app.

    Argumentos:
    - request: solicitud HTTP activa.

    Retorna:
    - str | None: nombre del servicio o `None` si no esta disponible.
    """

    services = getattr(request.app.state, "services", None)
    if services is None:
        return None
    settings = services.settings
    if settings is not None:
        return settings.app_name
    return services.raw_settings.app_name


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
) -> None:
    """Registra un evento estructurado asociado a una solicitud HTTP.

    Pasos:
    - Obtiene el nombre del servicio desde el estado compartido.
    - Adjunta request ID y metadatos de error relevantes.
    - Emite el evento con el nivel indicado.

    Argumentos:
    - request: solicitud HTTP activa.
    - level: nivel numerico del logger.
    - event: nombre estructurado del evento.
    - code: codigo funcional del error o condicion.
    - request_id: correlacion del request actual.
    - component: componente implicado cuando aplica.
    - failed_stage: etapa logica asociada cuando aplica.
    - failed_check: chequeo puntual asociado cuando aplica.
    - retryable: bandera de reintento cuando aplica.
    - http_status_code: codigo HTTP asociado cuando aplica.

    Notas:
    - Estos eventos deben compartir el mismo `request_id` que la respuesta HTTP asociada.
    """

    logger.log(
        level,
        _request_event_message(event),
        extra={
            "service": _service_name_from_request(request),
            "event": event,
            "request_id": request_id,
            "component": component,
            "failed_stage": failed_stage,
            "failed_check": failed_check,
            "retryable": retryable,
            "error_code": code,
            "http_status_code": http_status_code,
        },
    )


def _request_event_message(event: str) -> str:
    """Resuelve el texto humano asociado a un evento HTTP estructurado.

    Argumentos:
    - event: identificador maquina del evento.

    Retorna:
    - str: mensaje humano usado en logging.
    """

    event_messages = {
        "model_not_ready": Messages.MODEL_NOT_READY_LOG,
        "model_inference_failed": Messages.MODEL_INFERENCE_FAILED_LOG,
        "model_output_invalid": Messages.MODEL_OUTPUT_INVALID_LOG,
        "policy_mapping_failed": Messages.POLICY_MAPPING_FAILED_LOG,
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
    """Determina si FastAPI reporto un fallo de parseo JSON en validacion.

    Pasos:
    - Recorre la lista normalizada de errores de `RequestValidationError`.
    - Busca entradas con tipo `json_invalid`.
    - Devuelve una bandera para ramificar el contrato HTTP final.

    Argumentos:
    - errors: lista serializable de errores producidos por FastAPI.

    Retorna:
    - bool: `True` cuando la solicitud fallo al parsear JSON.

    Notas:
    - FastAPI encapsula estos fallos dentro de `RequestValidationError`,
      por lo que no siempre llegan al handler dedicado de `json.JSONDecodeError`.
    """

    return any(error.get("type") == "json_invalid" for error in errors)


@app.exception_handler(AppError)
async def handle_app_error(request: Request, exc: AppError):
    """Convierte errores de dominio en respuestas HTTP estructuradas.

    Pasos:
    - Reutiliza el `request_id` ya presente en la excepcion cuando existe.
    - Usa la correlacion del request como respaldo si la excepcion no la trae.
    - Emite logs request-scoped para errores relevantes.

    Notas:
    - El cuerpo de error debe quedar alineado con `X-Request-ID`.
    """

    request_id = getattr(exc, "request_id", None) or _request_id_from_request(request)
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
    """Normaliza errores de validacion de FastAPI al contrato de la API.

    Pasos:
    - Lee el `request_id` del contexto ya inicializado por middleware.
    - Detecta si FastAPI reporto un parseo JSON invalido como `json_invalid`.
    - Emite el evento estructurado que corresponda al tipo real del fallo.
    - Devuelve el payload uniforme apropiado con detalles serializables.
    """

    request_id = _request_id_from_request(request)
    errors = jsonable_encoder(exc.errors())
    if _contains_invalid_json_error(errors):
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
    """Convierte errores de JSON invalido en un payload tipado.

    Pasos:
    - Reutiliza la correlacion del request ya generada por middleware.
    - Devuelve el payload uniforme de error sin perder `request_id`.
    """

    del exc
    request_id = _request_id_from_request(request)
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

    _log_request_event(
        request,
        level=logging.WARNING,
        event="http_request_failed",
        code="HTTP_ERROR",
        request_id=request_id,
        http_status_code=exc.status_code,
    )
    return _error_response(exc.status_code, "HTTP_ERROR", Messages.HTTP_REQUEST_FAILED, request_id=request_id)


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
