"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370
Define esquemas base y de error reutilizados por la API.

Pasos:
- Establece una base estricta para modelos Pydantic.
- Modela la forma uniforme de errores HTTP serializados.

Notas:
- Los modelos de este modulo se reutilizan en handlers y endpoints.
"""

from pydantic import BaseModel, ConfigDict


class StrictBaseModel(BaseModel):
    """
    Provee un modelo de validacion estricto de Pydantic mediante una configuracion de un modelo en donde
    no se tolera campos adicionales y los campos se poblan por nombre. Esto sirve para manejar que las entradas
    de JSON enviadas por clientes a la aplicacion no contengan campos adicionales fuera de los definidos estrictamente
    por los resultados de schemas/sdn_mpls_ml_inference.py

    Pasos:
    - Deshabilita campos no declarados mediante `extra="forbid"`.
    - Permite popular campos por nombre cuando existe alias.

    Notas:
    - Se usa como base de todos los schemas HTTP del proyecto.
    """

    model_config = ConfigDict(extra="forbid", populate_by_name=True)


class ErrorBody(StrictBaseModel):
    """
    Modelo de validacion de Pydantic para los cuerpos de error HTTP. Contiene un codigo de error, un mensaje de alto
    nivel y metadatos opcionales de diagnostico. Esta informacion se usa en las respuestas HTTP dentro de las llamadas
    para registrar errores en donde el cuerpo de cualquier error registrado se transforma
    a un ErrorBody para ser enviado en una ErrorResponse comunicando el error de validacion o el error registrado
    internamente

    Pasos:
    - Conserva el codigo y mensaje de alto nivel.
    - Adjunta metadatos de diagnostico cuando existen.
    """

    code: str
    message: str
    component: str | None = None
    failed_stage: str | None = None
    failed_check: str | None = None
    retryable: bool | None = None


class ErrorResponse(StrictBaseModel):
    """
    Envuelve una respuesta de error con correlacion opcional.

    Pasos:
    - Incluye `request_id` cuando la operacion ya fue correlacionada.
    - Serializa el objeto `ErrorBody` como carga uniforme.

    Notas:
    - En solicitudes HTTP normales la API procura incluir siempre este valor.
    - `request_id` debe coincidir con el header `X-Request-ID` cuando ambos existan.
    """

    request_id: str | None = None
    error: ErrorBody
