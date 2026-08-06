"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370

Archivo que centraliza la funcion general utilizada para construir una respuesta de error a una llamda a la API basada en
el esquema definido en schemas/sdn_mpls_ml_baseline_validation_models.py que determina los campos concretos a contener, incluyendo el campo de request ID,
y  los campos de detalles adicionales para luego retornar la respuuesta armada completamente

Pasos:
- Construye payloads de error uniformes para rutas y middleware.
- Reutiliza los schemas compartidos de error de la API.
"""

from __future__ import annotations
from fastapi.responses import JSONResponse
from app.schemas.sdn_mpls_ml_baseline_validation_models import ErrorBody, ErrorResponse


def build_error_response(
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
) -> JSONResponse:
    """
    Construye una respuesta de error uniforme para la API basandose en el schema
    ErrorResponse definido en ./schemas/sdn_mpls_ml_baseline_validation_models.py.

    Pasos:
    - Arma el cuerpo principal con `ErrorResponse`.
    - Adjunta detalles opcionales de validacion.
    - Devuelve un `JSONResponse` con el codigo apropiado.

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

    Notas:
    - Cuando `request_id` existe debe coincidir con el header `X-Request-ID`.
    """

    payload = ErrorResponse(
        request_id=request_id,
        error=ErrorBody(
            code=code,
            message=message,
            component=component,
            failed_stage=failed_stage,
            failed_check=failed_check,
            retryable=retryable,
        ),
    ).model_dump()
    if details is not None:
        payload["details"] = details
    return JSONResponse(status_code=status_code, content=payload)
