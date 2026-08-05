"""Centraliza helpers de contexto asociados a la solicitud actual.

Pasos:
- Lee metadatos colocados por middleware de transporte.
- Expone accesos tipados para endpoints y handlers.

Notas:
- El request ID vive en la capa de transporte y no en los payloads funcionales.
"""

from __future__ import annotations

from fastapi import Request

from app.messages import Messages


def get_request_id(request: Request) -> str:
    """Obtiene el identificador de correlacion de la solicitud actual.

    Pasos:
    - Lee `request.state.request_id`.
    - Falla explicitamente si el middleware no lo inicializo.

    Argumentos:
    - request: solicitud HTTP actual.

    Retorna:
    - str: identificador de correlacion del servidor.

    Notas:
    - El valor fue generado por `CorrelationIdMiddleware`.
    - Los endpoints deben reutilizar este valor en respuestas y logs.

    Excepciones:
    - RuntimeError: si el middleware de correlacion no corrio.
    """

    request_id = getattr(request.state, "request_id", None)
    if request_id is None:
        raise RuntimeError(Messages.REQUEST_ID_NOT_INITIALIZED)
    return request_id
