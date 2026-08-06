"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370

Archivo que define todo el contenido del middleware que maneja el Request Size tanto su validacion como control en
las respuestas y llamadas a la API

Pasos:
- Rechaza tempranamente `Content-Length` invalido o demasiado grande.
- Lee el stream ASGI y cuenta bytes reales recibidos.
- Reproduce el cuerpo validado al resto de la aplicacion cuando es seguro.
"""

from __future__ import annotations

import logging
from collections.abc import Awaitable, Callable

from starlette.responses import JSONResponse
from starlette.types import ASGIApp, Message, Receive, Scope, Send

from app.observability.sdn_mpls_ml_classification_metrics import (
    record_request_body_rejection,
    record_request_error,
)
from app.sdn_mpls_ml_exceptions import InvalidContentLengthError, RequestTooLargeError
from app.sdn_mpls_ml_http_responses import build_error_response
from app.sdn_mpls_ml_messages import Messages


logger = logging.getLogger(__name__)


class RequestSizeLimitMiddleware:
    """
    Clase implementada que define un hard limit al tamano de las requests realizadas. Esta es una medida de seguridad
    configurada para detener el analisis de requests con contenido muy largo, no declarado y superior a un limite
    tolerado en la aplicacion, o con problemas de cnocordancia entre headers y datos

    La clase se encarga de medir tanto el header como el contenido real en forma de stream y registra si los bytes
    leidos superan un limite establecido en settings. Ademas, se encarga de comunicar el paquete una vez ha revisado
    la longitud de su contenido y los envia de nuevo hacia las rutas finales para que sean respondidas por la API

    Notas:
    - Se ejecuta despues del middleware de correlacion para preservar `X-Request-ID`.
    - Usa un middleware ASGI puro para inspeccionar el canal `receive`.
    """

    def __init__(self, app: ASGIApp) -> None:
        """
        Guarda la aplicacion ASGI envuelta. Esta clase recibe especificamente la ASGI app, es decir la app configurada
        de la API luego de haber sido modificada o configurada por el MIddleware de correlacion.

        Esta aplicacion es un alias que describe un objeto que implementa la interfaz ASGI y provee tres
        diferentes parametros.
        - scope: Es un diccionario que permite obtener la aplicacion, el estado de la aplicacion y los metodos,
        - receive: Es un canal async que permite recibir mensajes como http.request para ser usados dentro del
        Middleware
        - send: Es un canal async que permite enviar mensajes como http.response para ser usados dentro del middleware
    .

        Argumentos:
        - app: siguiente aplicacion ASGI de la cadena.
        """

        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        """
        Este es el metodo general del Middleware que se registra como el callable original donde se receptan las
        llamadas HTTP hacia la API y se validan antes de permitir que procedan hacia los endpoints de clasificacion.

        Pasos:
        - Omite trafico no HTTP.
        - Rechaza `Content-Length` invalido o mayor al limite.
        - Lee y acumula los chunks del cuerpo hasta el fin o el exceso.
        - Reinyecta el cuerpo validado a la app envuelta.

        Args:
            scope: alcance ASGI de la solicitud.
            receive: canal de recepcion ASGI original.
            send: canal de envio ASGI original.
        """

        #? Evita trafico http dado que no debemos interactura con otros tipos como websockets
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        #? Definimos el tamano maximo de bytes para la request, el id de la request y el header delcarado en base a
        #? funciones adicionaels que leen el scope de la llamada http entrante
        max_body_bytes = _request_size_limit_from_scope(scope)
        request_id = _request_id_from_scope(scope)
        declared_length = _parse_content_length(scope)

        #? 1. Si el contenido declara una longitud que no es correcta, habra retornado un sentinel que es un marcado
        #? de una clase interna que se puede usar para marcar un error de validacion.
        if declared_length is _INVALID_CONTENT_LENGTH:
            #? 1.1 Si el contenido declara una longitud invalida, se registra el rechazo y se loguea el evento de advertencia
            _record_rejection(
                scope, reason="invalid_content_length", error=InvalidContentLengthError
            )
            #? 1.2 Creamos un log del evento de error
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

            #? 1.3 Construimos la respuesta de error basada en el error de content length. En este caso, aplicamos el
            #? patron de short circuit de ASGI, en donde construimos una respuesta rapida, sin llegar a los endpoints
            #? de la aplicacion, que sera retornada directamente al caller y donde terminara el procesamiento de la
            #? llamada a la API
            response: JSONResponse =  build_error_response(
                InvalidContentLengthError.status_code,
                InvalidContentLengthError.code,
                InvalidContentLengthError.message,
                request_id=request_id,
                component=InvalidContentLengthError.component,
                failed_stage=InvalidContentLengthError.failed_stage,
                failed_check=InvalidContentLengthError.failed_check,
                retryable=InvalidContentLengthError.retryable,
            )
            await response(scope, receive, send)
            #? Cortamos la cadena de procesamiento para no llamar al endpoint
            return

        #? 2. Si tenemos una declared length y es superior al limite tolerado entonces igual registramos el mismo
        #? proceso de short circuit y enviamos una respuesta rapidamente sin llegar a los endpoints
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

        #? 3. Si llegamos aqui no tenemos ni header con error ni valor declarado por lo que empezamos a leer
        #? la request en su totalidad para evaluar mientras llega si supera la cantidad de bytes tolerados
        #? por la aplicacion
        buffered_messages: list[Message] = []
        received_bytes = 0

        #? Iternamos indefinidamente mediante un loop para recibir los mensajes de una request que pueden venir en
        #? fragmentos
        while True:

           #? Rceptamos un fragmento del mensaje o cada mensaje que llega
            message = await receive()
            buffered_messages.append(message)

            #? Si no es HTTP.request entonces lo evitamos
            if message["type"] != "http.request":
                continue

            #? Obtenemos todos los bytes del body y los acumulamos. Si este acumulado supera el limite definido en las
            #? reglas de la aplicacion entonces se lanza el short circuit y se devuelve una respuesta rapida
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

            #? Este bloque de continuacion del loop solo sucede cuando tenemos mas partes de un body en un mismo
            #? mensaje en donde tendriamos que esperar para las siguientes partes antes de terminr.
            if not message.get("more_body", False):
                break

        #? 4. Si llegamos aqui entonces ya tenemos el cuerpo completo y validado, por lo que lo reenviamos a la app
        #? envuelta, pero esta vez con el cuerpo ya validado y sin riesgo de DoS
        replay_receive = _build_replay_receive(buffered_messages)

        #? Realizamos una llamada a la aplicacion ASGI tope que seria la aplicacion real con los endpoints reales a
        #? donde enviamos los mensajes validados con el nuevo receive
        await self.app(scope, replay_receive, send)


class _InvalidContentLengthSentinel:
    """Marca interna para diferenciar un header invalido de uno ausente."""


_INVALID_CONTENT_LENGTH = _InvalidContentLengthSentinel()


def _request_size_limit_from_scope(scope: Scope) -> int:
    """
    Obtiene el limite de tamano configurado desde el estado de la app. Dado que la aplicacion se configura con un
    state en donde se registra la configuracion validada. En este caso, la capa de Middleware de este documento se
    define tercera luego de CorrelationMiddleware y luego de todo el paso de lifespan hasta el statement yield que
    determina el estado total de la aplicacion. En este contexto, el limite de tamano ya se ha configurado y validado
    con la informacion definida en sdn_mpls_ml_dependencies.py y las variables de entorno cargadas

    Args:
        scope: alcance ASGI actual.

    Returns:
        int: limite maximo de bytes permitido para el cuerpo.
    """

    app = scope["app"]
    #? Extraemos de los servicios de la aplicacion el request_size_limit_bytes que corresponde a una configuracion
    #? guardada en toda la aplicacion
    return app.state.services.request_size_limit_bytes


def _request_id_from_scope(scope: Scope) -> str | None:
    """
    Lee el request ID desde `scope['state']` si ya fue inicializado. Como este middleware se ejecuta luego del lifespan
    y luego de que la configuracion de CorrelationMiddleware haya sido ejecutada para una request, este ID ya fue
    configurado en el state de la request

    Args:
        scope: alcance ASGI actual. El scope es u diccionario que contiene informacion de la request, que contiene
        toda su informacion y es gestionado por Uvicorn, es decir, por el servidor ASGI que recibe la request y que la
        envia a la API que creamos.

        Es importante notar que este objeto es compartido por todos los Middlewares y por las rutas finales,
        dado que es un objeto mutable compartido manejado por el servidor ASGI interno, en nuestro caso Uvicorn

    Returns:
        str | None: correlacion del request o `None`.
    """

    #? Extraemos el request_id de los parametros del state
    state = scope.get("state", {})
    return state.get("request_id")


def _service_name_from_scope(scope: Scope) -> str | None:
    """
    Deriva el nombre del servicio desde el estado compartido ASGI.

    Args:
        scope: alcance ASGI actual.

    Returns:
        str | None: nombre del servicio o `None`.
    """

    #? Extrae del scope que recibe el middleware de toda la aplicacion el objeto app, en donde se registran los
    #? servicios y la configuracion de la aplicacion
    app = scope.get("app")
    if app is None:
        return None
    #? Obtenemos los servicios desde lifespan
    services = getattr(app.state, "services", None)
    if services is None:
        return None
    #? Obtenemos las settings desde AppState
    settings = services.settings
    if settings is not None:
        return settings.app_name
    #? Obtenemos el nombre rapidamente de la app
    return services.raw_settings.app_name


def _record_rejection(
    scope: Scope,
    *,
    reason: str,
    error: type[InvalidContentLengthError] | type[RequestTooLargeError],
) -> None:
    """
    Publica el rechazo del middleware sin duplicar el handler HTTP central.

    Args:
        scope: alcance ASGI actual.
        reason: motivo del rechazo.
        error: tipo de error para metricas.

    Notes:
        No se usa `record_request_error` directamente para evitar duplicar el handler HTTP central.
    """

    #? Extrameos en general los servicios, el estado y las settings para validar si tenemos habilitado el registro
    #? de resultados en prometheus
    services = getattr(scope.get("app"), "state", None)
    settings = getattr(getattr(services, "services", None), "settings", None)

    #? Si esta habilitado entonces marcamos como true y desplegamos registro en prometheus
    enabled = settings is not None and settings.enable_prometheus_metrics
    record_request_body_rejection(reason=reason, enabled=enabled)
    record_request_error(error_code=error.code, component=error.component, enabled=enabled)


def _parse_content_length(scope: Scope) -> int | _InvalidContentLengthSentinel | None:
    """

    Parsea el header `Content-Length` cuando existe. Si este no existe, entonces el mecanismo retorna None
    directamente y el sistem sabe que tiene que hacer una validacion directa del contenido que llega hasta que
    sobrepase el limite definido por la API, si existe el header entonces se retorna este valor

    Pasos:
    - Busca el header en la lista ASGI normalizada.
    - Convierte el valor a entero.
    - Marca valores no numericos o negativos como invalidos.

    Args:
        scope: alcance ASGI actual.

    Returns:
        int | _InvalidContentLengthSentinel | None: longitud declarada, invalida o ausente.
    """

    #? Iteramos sobre todos los headers
    for key, value in scope.get("headers", []):
        #? Si encontramos el header de content-length entonces intentamos leerlo como un entero
        if key == b"content-length":
            try:
                parsed = int(value.decode("ascii").strip())
            except (UnicodeDecodeError, ValueError):
                return _INVALID_CONTENT_LENGTH
            if parsed < 0:
                return _INVALID_CONTENT_LENGTH

            #? Retornamos el valor parseado como entero si paso todas las validaciones anteriores
            return parsed
    #? Si no encontramos el header entonces retornamos none
    return None


def _build_replay_receive(messages: list[Message]) -> Callable[[], Awaitable[Message]]:
    """
    Construye un `receive` que reproduce mensajes ASGI ya bufferizados. En este caso, dado que la aplicacion,
    espedcificamente este modulo de Middlware recibio todos los mensajes internamente, el sistema tiene que regenerar
    el comportamiento de la cola asincrona de mensajes de receive para que la siguiente capa, en nuestro caso la
    aplicacion, pueda iterar sobre esos mensajes nuevamente y responder a los mensajes que estan esperando en la cola de la API.

    Para realizar esto, dado que ya consumimos toda la cola con nuestro await receive() (dado que el contrato de una
    app ASGI establece que el receive() es una cola de elementos asincrona) entonces nosotros ya usamos todos los
    datos y no queda mas que generar un generador asincrono nuevo para la siguiente capa.

    Args:
        messages: mensajes recibidos y validados previamente. Estos mensajes se usaran para alimentar a un generador
        asincrono (callable en si) que se transforma en una cola asincrona para la siguienmte capa

    Returns:
        Callable[[], Awaitable[Message]]: funcion `receive` para la app interna.
    """

    pending_messages = list(messages)

    async def replay_receive() -> Message:
        """
        Reproduce un mensaje ASGI previamente bufferizado. Estos mensajes son los que proveyeron de nuestra capa de
        Middleware cuando leimos la cantidad de bytes que venian, cada mensaje que llegaba lo leiamos y si tenia mas
        partes seguiamos. Como ya leimos todo tenemos que volver a lanzar el receive y para esto generamos este iterador

        Pasos:
        - Devuelve mensajes pendientes en el mismo orden original.
        - Emite `http.disconnect` cuando ya no quedan mensajes.

        Returns:
            Message: siguiente mensaje ASGI disponible para la app interna.
        """

        #? Como esta nested function toma el scope exterior tambien, usamos ese scope para jalar el objeto de los
        #? mensajes y enviarlos uno por uno hasta enviar http.disconnect cuando no hay mas mensajes
        if pending_messages:
            return pending_messages.pop(0)
        return {"type": "http.disconnect"}

    #? Retornamos la funcion anidada como un callable que sera usado por la app interna
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

    Args:
        scope: alcance ASGI actual.
        level: nivel numerico del logger.
        event: nombre estructurado del evento.
        code: codigo funcional asociado.
        request_id: correlacion del request actual.
        component: componente implicado cuando aplica.
        failed_stage: etapa logica asociada cuando aplica.
        failed_check: chequeo puntual asociado cuando aplica.
        retryable: bandera de reintento cuando aplica.
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
