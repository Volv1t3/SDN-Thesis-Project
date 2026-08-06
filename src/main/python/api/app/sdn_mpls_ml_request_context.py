"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370

Archivo que centraliza helpers para el manejo del Request Context para mantener un registro de un ID por solicitud
que se puede usar para correlacionar logs y trazas dentro de toda la aplicacion. Inicialmente la API manejaba un ID proporcionado
por el caller, lo que le daba una responsabilidad adicional a los servicios del SDN Controller, y en especifico al subsistema
que hace la llamada a esta API, por esto se implemento un sistema de generacion de UUIDv4 local para que todas las solicitudes
tengan un ID dentro del sistema que unifique a una llamada con todos sus pasos y sus logs.

Este archivo define el helper funcional que permite extraer el ID de una request de tipo Request de FastAPI, que se
encuentra asociado al state de una request y es manejado por el servidor. Este UUID se genera dentro del servidor y se maneja
mediante un Middleware que atrapa a una request y se ejecuta luego de la validacion de tamano de la request.

Notas:
- El request ID vive en la capa de transporte y no en los payloads funcionales.
"""

from __future__ import annotations

from fastapi import Request
from app.sdn_mpls_ml_messages import Messages


def get_request_id(request: Request) -> str:
    """Obtiene el identificador de correlacion de la solicitud actual.

    Pasos:
    - Lee `request.state.request_id`.
    - Falla explicitamente si el middleware no lo inicializo.

    :arg request: solicitud HTTP actual.

    :returns: identificador de correlacion del servidor.

    Notas:
    - El valor fue generado por `CorrelationIdMiddleware`.
    - Los endpoints deben reutilizar este valor en respuestas y logs.

    :raises RuntimeError: si el middleware de correlacion no corrio.
    """

    request_id = getattr(request.state, "request_id", None)

    if request_id is None:
        raise RuntimeError(Messages.REQUEST_ID_NOT_INITIALIZED)
    return request_id
