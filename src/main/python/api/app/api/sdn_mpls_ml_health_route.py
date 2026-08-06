"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370

Expone endpoints de liveness, readiness y raiz del servicio.

Notas:
- La correlacion HTTP de estos endpoints viaja en el header `X-Request-ID`.
- Las respuestas 503 de readiness reutilizan la misma correlacion en `request_id`.
"""

from fastapi import APIRouter, Request, status
from fastapi.responses import JSONResponse

from app.sdn_mpls_ml_request_context import get_request_id
from app.schemas.sdn_mpls_ml_health_validation_models import LivenessResponse, ReadyFailureResponse, ReadySuccessResponse, RootResponse

router = APIRouter()


@router.get("/", response_model=RootResponse)
def root(request: Request) -> RootResponse:
    """
    Devuelve informacion basica del proceso HTTP activo.

    Pasos:
    - Obtiene settings validados si ya existen.
    - Usa settings crudos como respaldo durante inicializacion fallida.
    - Publica nombre, version y ruta de documentacion.

    Args:
        request: solicitud FastAPI con acceso al estado global.

    Returns:
        RootResponse: informacion basica del servicio.

    Notes:
        La correlacion de esta solicitud solo viaja en el header `X-Request-ID`.
    """

    services = request.app.state.services
    settings = services.settings
    raw_settings = services.raw_settings

    #? Retorna el estado del servicio y la version de la aplicacion, usando settings validados si existen, o crudos como respaldo.
    return RootResponse(
        service=settings.app_name if settings is not None else raw_settings.app_name,
        version=settings.app_version if settings is not None else raw_settings.app_version,
        status="running",
        documentation="/docs",
    )


@router.get("/health/live", response_model=LivenessResponse)
def live() -> LivenessResponse:
    """
    Responde si el proceso HTTP sigue vivo.

    Pasos:
    - Omite toda validacion de modelo y politica.
    - Devuelve un payload minimo de liveness.

    Returns:
        LivenessResponse: respuesta con estado `alive`.

    Notes:
        La correlacion de esta solicitud solo viaja en el header `X-Request-ID`.
    """

    return LivenessResponse(status="alive")


@router.get("/health/ready", response_model=ReadySuccessResponse | ReadyFailureResponse)
def ready(request: Request):
    """
    Devuelve el estado de readiness cacheado del proceso.

    Pasos:
    - Informa `initializing` mientras el startup no termina.
    - Informa `not_ready` con diagnostico si alguna etapa fallo.
    - Informa `ready` cuando las cinco validaciones completan.

    Args:
        request: solicitud FastAPI con acceso a servicios compartidos.

    Returns:
        ReadySuccessResponse | JSONResponse: resultado de readiness ya cacheado.

    Notes:
        En estados 503 el cuerpo incluye `request_id` para alinear con `X-Request-ID`.

        En estado listo la correlacion solo viaja en el header de transporte.
    """

    services = request.app.state.services
    readiness = services.readiness
    request_id = get_request_id(request)
    #? Si no se ha completado la incializacion retornamos este estado
    if not readiness.initialization_completed:
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content=ReadyFailureResponse(
                request_id=request_id,
                status="initializing",
                ready=False,
                classification_mode=readiness.classification_mode,
            ).model_dump(),
        )
    #? Si hemos pasado la inicialiacion y no esta listo retornamos el error y el estado actual
    if not readiness.ready:
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content=ReadyFailureResponse(
                request_id=request_id,
                status="not_ready",
                ready=False,
                classification_mode=readiness.classification_mode,
                error={
                    "code": readiness.error_code,
                    "message": readiness.error_message,
                    "component": readiness.error_component,
                    "failed_stage": readiness.failed_stage,
                    "failed_check": readiness.failed_check,
                    "retryable": readiness.retryable,
                },
            ).model_dump(),
        )
    #? Si todo pasa entonces retornamos el estado correcto y la informacion completa del estado actual de la aplicacion
    return ReadySuccessResponse(
        status="ready",
        ready=True,
        classification_mode=readiness.classification_mode,
        model_loaded=readiness.model_loaded,
        metadata_loaded=readiness.metadata_loaded,
        policy_loaded=readiness.policy_loaded,
        synthetic_inference_passed=readiness.synthetic_inference_passed,
        model_name=readiness.model_name,
        model_schema_version=readiness.model_schema_version,
        feature_count=readiness.feature_count,
        class_count=readiness.class_count,
        classifier_pool_ready=services.classifier_pool is not None,
        classifier_pool_size=services.classifier_pool.capacity if services.classifier_pool is not None else None,
        validated_at_utc=readiness.completed_at_utc,
    )
