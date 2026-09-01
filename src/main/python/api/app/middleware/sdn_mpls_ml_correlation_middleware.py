"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370
Genera y propaga un identificador de correlacion por solicitud.

Archivo que define el middleware `CorrelationIdMiddleware` para la aplicación ASGI. Este middleware se encarga de
generar un identificador único de correlación (UUID) para cada solicitud HTTP entrante y asegurarse de que este
identificador se propague a través de los headers de la respuesta, así como en los logs y cuerpos de respuesta que lo
requieran.

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

from app.sdn_mpls_ml_http_responses import build_error_response
from app.sdn_mpls_ml_messages import Messages
from app.observability.sdn_mpls_ml_classification_metrics import record_request_error


#? Constantes de control de la aplicacion
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
    """
    Clase que define el mecanismo interno de la generacion del header de correllacion interno en las tramas HTTP que
    lleguen a la aplicacion. El mecanismo se implementa mediante un componente ASGI puro, por lo que este Middleware
    recibe la informacion correspondiente de una llamada desde la aplicacion base de Uvicorn que recibe las conexiones
    HTTP

    Notes:
        La misma correlacion debe aparecer en headers, logs y cuerpos que expongan `request_id`.
        Este middleware debe envolver al resto de middlewares de aplicacion.
    """

    def __init__(self, app: ASGIApp) -> None:
        """
        Guarda la aplicacion ASGI envuelta.

        Args:
            app: siguiente aplicacion ASGI de la cadena.
        """

        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        """
        Procesa una solicitud HTTP y le adjunta correlacion.

        Pasos:
        - Omite trafico no HTTP.
        - Inicializa `request_id` en el estado ASGI del request.
        - Envuelve `send` para insertar `X-Request-ID` en `http.response.start`.

        Args:
            scope: alcance ASGI entrante.
            receive: canal ASGI de lectura.
            send: canal ASGI de escritura.

        Returns:
            None.

        Notes:
            La correlacion se crea antes de que corran validaciones, handlers o endpoints.

            El header se inyecta aunque la respuesta haya sido generada por un handler de error o por una capa
            inferior del framework.

            Si una excepcion no fue normalizada aguas abajo, este middleware devuelve
            el `500 INTERNAL_ERROR` correlacionado como ultima barrera segura.
        """

        #? Evitamos cualquier trama enviada a la aplicacion que no sea HTTP
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        #? Generamos el identificador y lo guardamos en el estado de la solicitud
        request_id = str(uuid.uuid4())
        state = scope.setdefault("state", {})
        state["request_id"] = request_id
        logger.debug(
            "Se recibio la solicitud HTTP en la capa de correlacion.",
            extra=_middleware_log_extra(
                scope,
                request_id,
                "correlation_request_received",
                {"asgi_layer": "correlation", **_request_metadata(scope)},
            ),
        )

        async def send_with_correlation(message: Message) -> None:
            """
            Funcion interna orientada a la asociacion del header en la solicitud HTTP que se recepto, y que genera
            una cola send asincrona para mantener el modelo ASGI mediante await send(message)
            """
            if message["type"] == "http.response.start":
                #? Evaluamos la lista de headers, si no esta el header de CORRELATION ID entonces colocamos el header
                #? dentro
                headers = list(message.get("headers", []))
                if not any(
                    header_name.lower() == _CORRELATION_ID_HEADER_LOWER
                    for header_name, _ in headers
                ):
                    headers.append((_CORRELATION_ID_HEADER_LOWER, request_id.encode("ascii")))
                #? Estructuramos el mensaje con el header incluido
                message = {**message, "headers": headers}
                logger.debug(
                    "La capa de correlacion agrego el identificador a la respuesta.",
                    extra=_middleware_log_extra(
                        scope,
                        request_id,
                        "correlation_response_start_forwarded",
                        {
                            "asgi_layer": "correlation",
                            "status_code": message.get("status"),
                            "response_headers": _selected_headers(message.get("headers", [])),
                        },
                    ),
                )
            elif message["type"] == "http.response.body":
                logger.debug(
                    "La capa de correlacion reenvio un fragmento de respuesta.",
                    extra=_middleware_log_extra(
                        scope,
                        request_id,
                        "correlation_response_body_forwarded",
                        {
                            "asgi_layer": "correlation",
                            "body_bytes": len(message.get("body", b"")),
                            "more_body": message.get("more_body", False),
                        },
                    ),
                )
            await send(message)

        try:
            #? Enviamos la informacion del mensaje modificado hacia el Middleware de Request Size enviando el mensaje
            # en la cola asincrona de send
            logger.debug(
                "La capa de correlacion entrego la solicitud a la siguiente capa ASGI.",
                extra=_middleware_log_extra(
                    scope,
                    request_id,
                    "correlation_request_forwarded",
                    {"asgi_layer": "correlation", "next_asgi_layer": "request_size_limit"},
                ),
            )
            await self.app(scope, receive, send_with_correlation)
        except Exception:
            _record_internal_error(scope)
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
    """
    Deriva el nombre del servicio desde el estado compartido ASGI.

    Pasos:
    - Busca la aplicacion FastAPI en el alcance actual.
    - Lee los servicios construidos durante el lifespan.
    - Devuelve el nombre configurado cuando esta disponible.

    Args:
        scope: alcance ASGI actual.

    Returns:
        str | None: nombre del servicio o `None`.
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


def _record_internal_error(scope: Scope) -> None:
    """Cuenta solo los fallos que escaparon los handlers de FastAPI."""

    app = scope.get("app")
    services = getattr(getattr(app, "state", None), "services", None)
    settings = getattr(services, "settings", None)
    record_request_error(
        error_code=INTERNAL_ERROR_CODE,
        component=INTERNAL_ERROR_COMPONENT,
        enabled=settings is not None and settings.enable_prometheus_metrics,
    )


def _middleware_log_extra(scope: Scope, request_id: str, event: str, metadata: dict) -> dict:
    """Construye campos JSON estructurados comunes para trazas ASGI de depuracion."""

    return {
        "service": _service_name_from_scope(scope),
        "event": event,
        "request_id": request_id,
        "component": "http_middleware",
        "metadata": metadata,
    }


def _request_metadata(scope: Scope) -> dict:
    """Devuelve metadata de transporte permitida, sin registrar secretos de headers."""

    return {
        "method": scope.get("method"),
        "path": scope.get("path"),
        "http_version": scope.get("http_version"),
        "request_headers": _selected_headers(scope.get("headers", [])),
    }


def _selected_headers(headers: list[tuple[bytes, bytes]]) -> dict[str, str]:
    """Selecciona solamente los headers que afectan el parseo y transporte del cuerpo."""

    allowed = {b"content-type", b"content-length", b"transfer-encoding", b"accept"}
    return {
        name.decode("latin-1").lower(): value.decode("latin-1")
        for name, value in headers
        if name.lower() in allowed
    }
