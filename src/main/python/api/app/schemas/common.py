"""Define esquemas base y de error reutilizados por la API.

Pasos:
- Establece una base estricta para modelos Pydantic.
- Modela la forma uniforme de errores HTTP serializados.

Notas:
- Los modelos de este modulo se reutilizan en handlers y endpoints.
"""

from pydantic import BaseModel, ConfigDict


class StrictBaseModel(BaseModel):
    """Provee un modelo base estricto para contratos externos.

    Pasos:
    - Deshabilita campos no declarados mediante `extra="forbid"`.
    - Permite popular campos por nombre cuando existe alias.

    Notas:
    - Se usa como base de todos los schemas HTTP del proyecto.
    """

    model_config = ConfigDict(extra="forbid", populate_by_name=True)


class ErrorBody(StrictBaseModel):
    """Representa el bloque `error` de una respuesta fallida.

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
    """Envuelve una respuesta de error con correlacion opcional.

    Pasos:
    - Incluye `request_id` cuando la operacion ya fue correlacionada.
    - Serializa el objeto `ErrorBody` como carga uniforme.

    Notas:
    - En solicitudes HTTP normales la API procura incluir siempre este valor.
    - `request_id` debe coincidir con el header `X-Request-ID` cuando ambos existan.
    """

    request_id: str | None = None
    error: ErrorBody
