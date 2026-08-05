"""Genera y propaga un identificador de correlacion por solicitud.

Pasos:
- Crea un UUID del lado del servidor para cada request HTTP.
- Guarda el identificador en `scope["state"]` para handlers y middlewares.
- Anade el mismo valor al header `X-Request-ID` en toda respuesta HTTP.

Notas:
- Este identificador es la correlacion autoritativa del transporte HTTP.
- Los clientes no pueden sustituirlo enviando `request_id` en el cuerpo.
- La implementacion usa ASGI puro para conservar el header incluso cuando
  una excepcion termina convertida en respuesta de error aguas abajo.
"""

from __future__ import annotations

import logging
import uuid

from starlette.types import ASGIApp, Message, Receive, Scope, Send

from app.http_responses import build_error_response
from app.messages import Messages


CORRELATION_ID_HEADER = "X-Request-ID"
_CORRELATION_ID_HEADER_LOWER = CORRELATION_ID_HEADER.lower().encode("ascii")
INTERNAL_ERROR_CODE = "INTERNAL_ERROR"
INTERNAL_ERROR_MESSAGE = Messages.INTERNAL_ERROR
INTERNAL_ERROR_COMPONENT = "application"
INTERNAL_ERROR_STAGE = "request_processing"
INTERNAL_ERROR_CHECK = "unhandled_exception"
INTERNAL_ERROR_RETRYABLE = True

logger = logging.getLogger(__name__)


class CorrelationIdMiddleware:
    """Inicializa la correlacion de transporte para cada solicitud.

    Pasos:
    - Genera un UUID nuevo para la solicitud entrante.
    - Lo guarda en `scope["state"]["request_id"]`.
    - Intercepta el inicio de la respuesta y le agrega el header de correlacion.

        Notas:
        - La misma correlacion debe aparecer en headers, logs y cuerpos que expongan `request_id`.
        - Este middleware debe envolver al resto de middlewares de aplicacion.
        """

    def __init__(self, app: ASGIApp) -> None:
        """Guarda la aplicacion ASGI envuelta.

        Argumentos:
        - app: siguiente aplicacion ASGI de la cadena.
        """

        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        """Procesa una solicitud HTTP y le adjunta correlacion.

        Pasos:
        - Omite trafico no HTTP.
        - Inicializa `request_id` en el estado ASGI del request.
        - Envuelve `send` para insertar `X-Request-ID` en `http.response.start`.

        Argumentos:
        - scope: alcance ASGI entrante.
        - receive: canal ASGI de lectura.
        - send: canal ASGI de escritura.

        Retorna:
        - None.

        Notas:
        - La correlacion se crea antes de que corran validaciones, handlers o endpoints.
        - El header se inyecta aunque la respuesta haya sido generada por un handler
          de error o por una capa inferior del framework.
        - Si una excepcion no fue normalizada aguas abajo, este middleware devuelve
          el `500 INTERNAL_ERROR` correlacionado como ultima barrera segura.
        """

        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        request_id = str(uuid.uuid4())
        state = scope.setdefault("state", {})
        state["request_id"] = request_id

        async def send_with_correlation(message: Message) -> None:
            if message["type"] == "http.response.start":
                headers = list(message.get("headers", []))
                if not any(header_name.lower() == _CORRELATION_ID_HEADER_LOWER for header_name, _ in headers):
                    headers.append((_CORRELATION_ID_HEADER_LOWER, request_id.encode("ascii")))
                message = {**message, "headers": headers}
            await send(message)

        try:
            await self.app(scope, receive, send_with_correlation)
        except Exception:
            logger.error(
                Messages.UNHANDLED_EXCEPTION,
                extra={
                    "service": _service_name_from_scope(scope),
                    "event": "unhandled_exception",
                    "request_id": request_id,
                    "component": INTERNAL_ERROR_COMPONENT,
                    "failed_stage": INTERNAL_ERROR_STAGE,
                    "failed_check": INTERNAL_ERROR_CHECK,
                    "retryable": INTERNAL_ERROR_RETRYABLE,
                    "error_code": INTERNAL_ERROR_CODE,
                    "http_status_code": 500,
                },
            )
            await build_error_response(
                500,
                INTERNAL_ERROR_CODE,
                INTERNAL_ERROR_MESSAGE,
                request_id=request_id,
                component=INTERNAL_ERROR_COMPONENT,
                failed_stage=INTERNAL_ERROR_STAGE,
                failed_check=INTERNAL_ERROR_CHECK,
                retryable=INTERNAL_ERROR_RETRYABLE,
            )(scope, receive, send_with_correlation)


def _service_name_from_scope(scope: Scope) -> str | None:
    """Deriva el nombre del servicio desde el estado compartido ASGI.

    Pasos:
    - Busca la aplicacion FastAPI en el alcance actual.
    - Lee los servicios construidos durante el lifespan.
    - Devuelve el nombre configurado cuando esta disponible.

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
