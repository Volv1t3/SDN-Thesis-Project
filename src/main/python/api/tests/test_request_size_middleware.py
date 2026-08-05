"""Prueba la validacion completa de tamano de cuerpo HTTP.

Pasos:
- Ejerce el middleware con llamadas ASGI directas para controlar headers y chunks.
- Verifica validaciones sobre `Content-Length` y bytes reales recibidos.
- Confirma que todos los rechazos preserven la correlacion del request.
"""

from __future__ import annotations

import asyncio
import json
from uuid import UUID


def _invoke_http_app(
    app,
    *,
    method: str,
    path: str,
    headers: list[tuple[bytes, bytes]],
    body_chunks: list[bytes],
) -> tuple[int, dict[str, str], bytes]:
    """Ejecuta la app ASGI con control total sobre headers y cuerpo.

    Pasos:
    - Construye un scope HTTP minimo con estado y app ya iniciada.
    - Reproduce los chunks del cuerpo a traves del canal `receive`.
    - Captura status, headers y cuerpo bruto enviados por la respuesta.

    Argumentos:
    - app: aplicacion ASGI ya inicializada.
    - method: verbo HTTP de la solicitud.
    - path: ruta HTTP a invocar.
    - headers: lista exacta de headers ASGI en bytes.
    - body_chunks: chunks de cuerpo a emitir en orden.

    Retorna:
    - tuple[int, dict[str, str], bytes]: status, headers normalizados y cuerpo completo.
    """

    async def _run() -> tuple[int, dict[str, str], bytes]:
        """Ejecuta la llamada ASGI y captura la respuesta completa.

        Pasos:
        - Mantiene una cola mutable de chunks pendientes.
        - Expone `receive` y `send` compatibles con ASGI.
        - Reconstruye status, headers y cuerpo desde los mensajes emitidos.

        Retorna:
        - tuple[int, dict[str, str], bytes]: status, headers y cuerpo completos.
        """

        sent_messages: list[dict] = []
        pending_chunks = list(body_chunks)

        async def receive():
            """Entrega el siguiente chunk del cuerpo al aplicativo ASGI.

            Pasos:
            - Emite mensajes `http.request` mientras existan chunks pendientes.
            - Emite `http.disconnect` cuando el cuerpo ya se consumio.

            Retorna:
            - dict: mensaje ASGI de recepcion.
            """

            if pending_chunks:
                chunk = pending_chunks.pop(0)
                return {
                    "type": "http.request",
                    "body": chunk,
                    "more_body": bool(pending_chunks),
                }
            return {"type": "http.disconnect"}

        async def send(message):
            """Captura un mensaje de salida emitido por la aplicacion ASGI.

            Argumentos:
            - message: mensaje ASGI de respuesta a registrar.
            """

            sent_messages.append(message)

        scope = {
            "type": "http",
            "asgi": {"version": "3.0"},
            "http_version": "1.1",
            "method": method,
            "scheme": "http",
            "path": path,
            "raw_path": path.encode("ascii"),
            "query_string": b"",
            "root_path": "",
            "headers": headers,
            "client": ("testclient", 50000),
            "server": ("testserver", 80),
            "state": {},
            "app": app,
        }

        await app(scope, receive, send)

        start = next(message for message in sent_messages if message["type"] == "http.response.start")
        body = b"".join(
            message.get("body", b"")
            for message in sent_messages
            if message["type"] == "http.response.body"
        )
        normalized_headers = {
            key.decode("latin1"): value.decode("latin1")
            for key, value in start.get("headers", [])
        }
        return start["status"], normalized_headers, body

    return asyncio.run(_run())


def test_declared_oversized_request_returns_413(client):
    """Verifica el rechazo temprano cuando `Content-Length` ya excede el limite."""

    status_code, headers, body = _invoke_http_app(
        client.app,
        method="POST",
        path="/api/v1/classify",
        headers=[
            (b"host", b"testserver"),
            (b"content-type", b"application/json"),
            (b"content-length", b"20000"),
        ],
        body_chunks=[b"{}"],
    )
    assert status_code == 413
    assert headers["x-request-id"]
    payload = json.loads(body.decode("utf-8"))
    assert payload["error"]["code"] == "REQUEST_TOO_LARGE"
    assert payload["request_id"] == headers["x-request-id"]
    UUID(payload["request_id"])


def test_body_at_exact_limit_is_not_rejected_by_size_middleware(client):
    """Comprueba que el limite exacto no dispare un rechazo por tamano."""

    exact_body = b"x" * 16384
    status_code, headers, body = _invoke_http_app(
        client.app,
        method="POST",
        path="/api/v1/classify",
        headers=[
            (b"host", b"testserver"),
            (b"content-type", b"application/json"),
            (b"content-length", str(len(exact_body)).encode("ascii")),
        ],
        body_chunks=[exact_body],
    )
    assert status_code != 413
    assert headers["x-request-id"]
    payload = json.loads(body.decode("utf-8"))
    assert payload["request_id"] == headers["x-request-id"]


def test_missing_content_length_oversized_body_returns_413(client):
    """Verifica que el tamano real se aplique aunque falte `Content-Length`."""

    status_code, headers, body = _invoke_http_app(
        client.app,
        method="POST",
        path="/api/v1/classify",
        headers=[
            (b"host", b"testserver"),
            (b"content-type", b"application/json"),
        ],
        body_chunks=[b"x" * 10000, b"x" * 7000],
    )
    assert status_code == 413
    payload = json.loads(body.decode("utf-8"))
    assert payload["error"]["code"] == "REQUEST_TOO_LARGE"
    assert payload["request_id"] == headers["x-request-id"]
    UUID(payload["request_id"])


def test_actual_body_over_limit_with_small_declared_length_returns_413(client):
    """Asegura que el tamano real prevalezca sobre un header pequeno."""

    status_code, headers, body = _invoke_http_app(
        client.app,
        method="POST",
        path="/api/v1/classify",
        headers=[
            (b"host", b"testserver"),
            (b"content-type", b"application/json"),
            (b"content-length", b"10"),
        ],
        body_chunks=[b"x" * 10000, b"x" * 7000],
    )
    assert status_code == 413
    payload = json.loads(body.decode("utf-8"))
    assert payload["error"]["code"] == "REQUEST_TOO_LARGE"
    assert payload["request_id"] == headers["x-request-id"]
    UUID(payload["request_id"])


def test_invalid_content_length_returns_controlled_400(client):
    """Comprueba que un `Content-Length` no numerico produzca 400 estructurado."""

    status_code, headers, body = _invoke_http_app(
        client.app,
        method="POST",
        path="/api/v1/classify",
        headers=[
            (b"host", b"testserver"),
            (b"content-type", b"application/json"),
            (b"content-length", b"not-a-number"),
        ],
        body_chunks=[b"{}"],
    )
    assert status_code == 400
    payload = json.loads(body.decode("utf-8"))
    assert payload["error"]["code"] == "INVALID_CONTENT_LENGTH"
    assert payload["request_id"] == headers["x-request-id"]
    UUID(payload["request_id"])


def test_negative_content_length_returns_controlled_400(client):
    """Comprueba que un `Content-Length` negativo produzca 400 estructurado."""

    status_code, headers, body = _invoke_http_app(
        client.app,
        method="POST",
        path="/api/v1/classify",
        headers=[
            (b"host", b"testserver"),
            (b"content-type", b"application/json"),
            (b"content-length", b"-1"),
        ],
        body_chunks=[b"{}"],
    )
    assert status_code == 400
    payload = json.loads(body.decode("utf-8"))
    assert payload["error"]["code"] == "INVALID_CONTENT_LENGTH"
    assert payload["request_id"] == headers["x-request-id"]
    UUID(payload["request_id"])
