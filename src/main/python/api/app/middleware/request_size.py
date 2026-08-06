"""Aplica validacion completa de tamano de cuerpo HTTP.

Pasos:
- Rechaza tempranamente `Content-Length` invalido o demasiado grande.
- Lee el stream ASGI y cuenta bytes reales recibidos.
- Reproduce el cuerpo validado al resto de la aplicacion cuando es seguro.
"""

from __future__ import annotations

import logging
from collections.abc import Awaitable, Callable
from starlette.types import ASGIApp, Message, Receive, Scope, Send

from app.observability.classification_metrics import (
    record_request_body_rejection,
    record_request_error,
)
from app.sdn_mpls_ml_exceptions import InvalidContentLengthError, RequestTooLargeError
from app.sdn_mpls_ml_http_responses import build_error_response
from app.sdn_mpls_ml_messages import Messages


logger = logging.getLogger(__name__)


class RequestSizeLimitMiddleware:
    """Impone un limite duro al cuerpo HTTP declarado y recibido.

    Pasos:
    - Lee el limite cacheado en los servicios de la app.
    - Valida `Content-Length` cuando el cliente lo envia.
    - Cuenta los bytes reales del stream antes de ejecutar rutas o parsing.
    - Reproduce el cuerpo al ASGI interno solo si queda dentro del limite.

    Notas:
    - Se ejecuta despues del middleware de correlacion para preservar `X-Request-ID`.
    - Usa un middleware ASGI puro para inspeccionar el canal `receive`.
    """

    def __init__(self, app: ASGIApp) -> None:
        """Guarda la aplicacion ASGI envuelta.

        Argumentos:
        - app: siguiente aplicacion ASGI de la cadena.
        """

        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        """Procesa una solicitud HTTP y valida el cuerpo completo.

        Pasos:
        - Omite trafico no HTTP.
        - Rechaza `Content-Length` invalido o mayor al limite.
        - Lee y acumula los chunks del cuerpo hasta el fin o el exceso.
        - Reinyecta el cuerpo validado a la app envuelta.

        Argumentos:
        - scope: alcance ASGI de la solicitud.
        - receive: canal de recepcion ASGI original.
        - send: canal de envio ASGI original.
        """

        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        max_body_bytes = _request_size_limit_from_scope(scope)
        request_id = _request_id_from_scope(scope)
        declared_length = _parse_content_length(scope)

        if declared_length is _INVALID_CONTENT_LENGTH:
            _record_rejection(
                scope, reason="invalid_content_length", error=InvalidContentLengthError
            )
            _log_middleware_event(
                scope,
                level=logging.WARNING,
                event="invalid_content_length",
                code=InvalidContentLengthError.code,
                request_id=request_id,
                component=InvalidContentLengthError.component,
                failed_stage=InvalidContentLengthError.failed_stage,
                failed_check=InvalidContentLengthError.failed_check,
                retryable=InvalidContentLengthError.retryable,
            )
            await build_error_response(
                InvalidContentLengthError.status_code,
                InvalidContentLengthError.code,
                InvalidContentLengthError.message,
                request_id=request_id,
                component=InvalidContentLengthError.component,
                failed_stage=InvalidContentLengthError.failed_stage,
                failed_check=InvalidContentLengthError.failed_check,
                retryable=InvalidContentLengthError.retryable,
            )(scope, receive, send)
            return

        if declared_length is not None and declared_length > max_body_bytes:
            _record_rejection(scope, reason="declared_size_exceeded", error=RequestTooLargeError)
            _log_middleware_event(
                scope,
                level=logging.WARNING,
                event="request_too_large",
                code=RequestTooLargeError.code,
                request_id=request_id,
                component=RequestTooLargeError.component,
                failed_stage=RequestTooLargeError.failed_stage,
                failed_check=RequestTooLargeError.failed_check,
                retryable=RequestTooLargeError.retryable,
            )
            await build_error_response(
                RequestTooLargeError.status_code,
                RequestTooLargeError.code,
                RequestTooLargeError.message,
                request_id=request_id,
                component=RequestTooLargeError.component,
                failed_stage=RequestTooLargeError.failed_stage,
                failed_check=RequestTooLargeError.failed_check,
                retryable=RequestTooLargeError.retryable,
            )(scope, receive, send)
            return

        buffered_messages: list[Message] = []
        received_bytes = 0

        while True:
            message = await receive()
            buffered_messages.append(message)

            if message["type"] != "http.request":
                continue

            body = message.get("body", b"")
            received_bytes += len(body)
            if received_bytes > max_body_bytes:
                _record_rejection(scope, reason="actual_size_exceeded", error=RequestTooLargeError)
                _log_middleware_event(
                    scope,
                    level=logging.WARNING,
                    event="request_too_large",
                    code=RequestTooLargeError.code,
                    request_id=request_id,
                    component=RequestTooLargeError.component,
                    failed_stage=RequestTooLargeError.failed_stage,
                    failed_check=RequestTooLargeError.failed_check,
                    retryable=RequestTooLargeError.retryable,
                )
                await build_error_response(
                    RequestTooLargeError.status_code,
                    RequestTooLargeError.code,
                    RequestTooLargeError.message,
                    request_id=request_id,
                    component=RequestTooLargeError.component,
                    failed_stage=RequestTooLargeError.failed_stage,
                    failed_check=RequestTooLargeError.failed_check,
                    retryable=RequestTooLargeError.retryable,
                )(scope, receive, send)
                return

            if not message.get("more_body", False):
                break

        replay_receive = _build_replay_receive(buffered_messages)
        await self.app(scope, replay_receive, send)


class _InvalidContentLengthSentinel:
    """Marca interna para diferenciar un header invalido de uno ausente."""


_INVALID_CONTENT_LENGTH = _InvalidContentLengthSentinel()


def _request_size_limit_from_scope(scope: Scope) -> int:
    """Obtiene el limite de tamano configurado desde el estado de la app.

    Argumentos:
    - scope: alcance ASGI actual.

    Retorna:
    - int: limite maximo de bytes permitido para el cuerpo.
    """

    app = scope["app"]
    return app.state.services.request_size_limit_bytes


def _request_id_from_scope(scope: Scope) -> str | None:
    """Lee el request ID desde `scope['state']` si ya fue inicializado.

    Argumentos:
    - scope: alcance ASGI actual.

    Retorna:
    - str | None: correlacion del request o `None`.
    """

    state = scope.get("state", {})
    return state.get("request_id")


def _service_name_from_scope(scope: Scope) -> str | None:
    """Deriva el nombre del servicio desde el estado compartido ASGI.

    Argumentos:
    - scope: alcance ASGI actual.

    Retorna:
    - str | None: nombre del servicio o `None`.
    """

    app = scope.get("app")
    if app is None:
        return None
    services = getattr(app.state, "services", None)
    if services is None:
        return None
    settings = services.settings
    if settings is not None:
        return settings.app_name
    return services.raw_settings.app_name


def _record_rejection(
    scope: Scope,
    *,
    reason: str,
    error: type[InvalidContentLengthError] | type[RequestTooLargeError],
) -> None:
    """Publica el rechazo del middleware sin duplicar el handler HTTP central."""

    services = getattr(scope.get("app"), "state", None)
    settings = getattr(getattr(services, "services", None), "settings", None)
    enabled = settings is not None and settings.enable_prometheus_metrics
    record_request_body_rejection(reason=reason, enabled=enabled)
    record_request_error(error_code=error.code, component=error.component, enabled=enabled)


def _parse_content_length(scope: Scope) -> int | _InvalidContentLengthSentinel | None:
    """Parsea el header `Content-Length` cuando existe.

    Pasos:
    - Busca el header en la lista ASGI normalizada.
    - Convierte el valor a entero.
    - Marca valores no numericos o negativos como invalidos.

    Argumentos:
    - scope: alcance ASGI actual.

    Retorna:
    - int | _InvalidContentLengthSentinel | None: longitud declarada, invalida o ausente.
    """

    for key, value in scope.get("headers", []):
        if key == b"content-length":
            try:
                parsed = int(value.decode("ascii").strip())
            except (UnicodeDecodeError, ValueError):
                return _INVALID_CONTENT_LENGTH
            if parsed < 0:
                return _INVALID_CONTENT_LENGTH
            return parsed
    return None


def _build_replay_receive(messages: list[Message]) -> Callable[[], Awaitable[Message]]:
    """Construye un `receive` que reproduce mensajes ASGI ya bufferizados.

    Argumentos:
    - messages: mensajes recibidos y validados previamente.

    Retorna:
    - Callable[[], Awaitable[Message]]: funcion `receive` para la app interna.
    """

    pending_messages = list(messages)

    async def replay_receive() -> Message:
        """Reproduce un mensaje ASGI previamente bufferizado.

        Pasos:
        - Devuelve mensajes pendientes en el mismo orden original.
        - Emite `http.disconnect` cuando ya no quedan mensajes.

        Retorna:
        - Message: siguiente mensaje ASGI disponible para la app interna.
        """

        if pending_messages:
            return pending_messages.pop(0)
        return {"type": "http.disconnect"}

    return replay_receive


def _log_middleware_event(
    scope: Scope,
    *,
    level: int,
    event: str,
    code: str,
    request_id: str | None,
    component: str | None = None,
    failed_stage: str | None = None,
    failed_check: str | None = None,
    retryable: bool | None = None,
) -> None:
    """Registra un evento estructurado asociado al middleware de tamano.

    Argumentos:
    - scope: alcance ASGI actual.
    - level: nivel numerico del logger.
    - event: nombre estructurado del evento.
    - code: codigo funcional asociado.
    - request_id: correlacion del request actual.
    - component: componente implicado cuando aplica.
    - failed_stage: etapa logica asociada cuando aplica.
    - failed_check: chequeo puntual asociado cuando aplica.
    - retryable: bandera de reintento cuando aplica.
    """

    event_messages = {
        "invalid_content_length": Messages.INVALID_CONTENT_LENGTH_LOG,
        "request_too_large": Messages.REQUEST_TOO_LARGE_LOG,
    }
    logger.log(
        level,
        event_messages.get(event, event),
        extra={
            "service": _service_name_from_scope(scope),
            "event": event,
            "request_id": request_id,
            "component": component,
            "failed_stage": failed_stage,
            "failed_check": failed_check,
            "retryable": retryable,
            "error_code": code,
        },
    )
