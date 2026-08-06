"""Define los contratos HTTP para endpoints de salud y readiness.

Pasos:
- Modela la respuesta raiz del servicio.
- Modela respuestas de liveness y readiness exitosas o fallidas.

Notas:
- Estos modelos reflejan el estado cacheado de la aplicacion.
- La correlacion de transporte viaja en el header `X-Request-ID`.
"""

from .common import StrictBaseModel


class RootResponse(StrictBaseModel):
    """Describe la respuesta del endpoint raiz.

    Pasos:
    - Expone nombre y version del servicio.
    - Indica estado general y ruta de documentacion.
    """

    service: str
    version: str
    status: str
    documentation: str


class LivenessResponse(StrictBaseModel):
    """Representa la confirmacion minima de vida del proceso HTTP.

    Pasos:
    - Serializa un estado simple de disponibilidad del proceso.
    """

    status: str


class ReadinessError(StrictBaseModel):
    """Modela el error estructurado devuelto por readiness.

    Pasos:
    - Conserva codigo y mensaje de diagnostico.
    - Incluye metadatos de etapa y chequeo cuando existen.
    """

    code: str
    message: str
    component: str | None = None
    failed_stage: str | None = None
    failed_check: str | None = None
    retryable: bool | None = None


class ReadySuccessResponse(StrictBaseModel):
    """Modela una respuesta de readiness satisfactoria.

    Pasos:
    - Expone el estado listo y el modo de clasificacion activo.
    - Publica banderas de validacion y resumen del modelo cargado.
    """

    status: str
    ready: bool
    classification_mode: str
    model_loaded: bool
    metadata_loaded: bool
    policy_loaded: bool
    synthetic_inference_passed: bool
    model_name: str | None = None
    model_schema_version: str | None = None
    feature_count: int | None = None
    class_count: int | None = None
    classifier_pool_ready: bool | None = None
    classifier_pool_size: int | None = None
    validated_at_utc: str | None = None


class ReadyFailureResponse(StrictBaseModel):
    """Modela una respuesta de readiness no disponible.

    Pasos:
    - Publica el estado no listo o inicializando.
    - Adjunta un error estructurado cuando la validacion ya fallo.

    Notas:
    - Incluye `request_id` para alinear el cuerpo con `X-Request-ID`.
    """

    request_id: str | None = None
    status: str
    ready: bool
    classification_mode: str
    error: ReadinessError | None = None
