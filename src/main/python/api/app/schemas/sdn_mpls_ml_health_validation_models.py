"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370

Define los contratos HTTP para endpoints de salud y readiness.

Notas:
- Estos modelos reflejan el estado cacheado de la aplicacion.
- La correlacion de transporte viaja en el header `X-Request-ID`.
"""

from .sdn_mpls_ml_baseline_validation_models import StrictBaseModel


class RootResponse(StrictBaseModel):
    """
    Describe la respuesta del endpoint root (/).
    """

    service: str
    version: str
    status: str
    documentation: str


class LivenessResponse(StrictBaseModel):
    """
    Representa la confirmacion minima de vida del proceso HTTP.
    """

    status: str


class ReadinessError(StrictBaseModel):
    """
    Modela el error estructurado devuelto por readiness.
    """

    code: str
    message: str
    component: str | None = None
    failed_stage: str | None = None
    failed_check: str | None = None
    retryable: bool | None = None


class ReadySuccessResponse(StrictBaseModel):
    """
    Modela una respuesta de readiness satisfactoria.
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
    """
    Modela una respuesta de readiness no disponible. Esta respuesta contiene internamente un
    cuerpo de ReadinessError que representa toda la informacion de error del estado de readiness de la aplicacion
    que fue cacheado

    Notas:
    - Incluye `request_id` para alinear el cuerpo con `X-Request-ID`.
    """

    request_id: str | None = None
    status: str
    ready: bool
    classification_mode: str
    error: ReadinessError | None = None
