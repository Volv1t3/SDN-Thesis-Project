"""Expone endpoints de metadata y clasificacion de trafico.

Pasos:
- Consulta el estado de readiness antes de clasificar.
- Aplica validaciones dependientes del modo activo.
- Ejecuta inferencia o simulacion y resuelve la politica asociada.

Notas:
- La correlacion de solicitud llega desde middleware por `request.state.request_id`.
- El cuerpo de clasificacion ya no acepta `request_id` enviado por el cliente.
"""

from __future__ import annotations

import logging
import time

from fastapi import APIRouter, Request

from app.exceptions import ModelEtherTypeUnsupportedError, ModelNotReadyError, PolicyMappingFailedError
from app.messages import Messages
from app.model.input_validation import validate_packet_for_classification_mode
from app.request_context import get_request_id
from app.schemas.inference import (
    ClassifyRequest,
    ClassifyResponse,
    ModelClassInfo,
    ModelInfoResponse,
    PolicyResponse,
    PredictionBody,
)

router = APIRouter(prefix="/api/v1")
logger = logging.getLogger(__name__)


@router.get("/model", response_model=ModelInfoResponse)
def model_info(request: Request) -> ModelInfoResponse:
    """Devuelve metadata del modelo solo cuando el servicio esta listo.

    Pasos:
    - Verifica readiness cacheado.
    - Rechaza estados no listos con error estructurado.
    - Serializa el contrato del modelo cargado.

    Argumentos:
    - request: solicitud FastAPI con acceso a servicios compartidos.

    Retorna:
    - ModelInfoResponse: metadata publica del modelo activo.

    Notas:
    - La correlacion de la solicitud viaja en el header `X-Request-ID`.

    Excepciones:
    - ModelNotReadyError: si el servicio aun no esta listo.
    """

    services = request.app.state.services
    if not services.readiness.ready or services.model_metadata is None:
        raise ModelNotReadyError(
            component=services.readiness.error_component or "inference_service",
            failed_stage=services.readiness.failed_stage,
            failed_check=services.readiness.failed_check,
            retryable=services.readiness.retryable,
        )
    metadata = services.model_metadata
    return ModelInfoResponse(
        model_name=metadata.model_name,
        target_name=metadata.target_name,
        schema_version=metadata.schema_version,
        feature_order=metadata.feature_order,
        classes=[ModelClassInfo(id=class_id, name=name) for class_id, name in metadata.classes],
    )


@router.post("/classify", response_model=ClassifyResponse)
def classify(payload: ClassifyRequest, request: Request) -> ClassifyResponse:
    """Clasifica un paquete y devuelve prediccion, probabilidades y politica.

    Pasos:
    - Obtiene el identificador de correlacion ya generado por middleware.
    - Valida readiness antes de tocar el clasificador.
    - Aplica reglas de EtherType dependientes del modo.
    - Ejecuta prediccion y mapea la clase a una politica.
    - Registra un evento estructurado con la latencia final.

    Argumentos:
    - payload: cuerpo validado de la solicitud sin `request_id` de cliente.
    - request: solicitud FastAPI con acceso a servicios compartidos.

    Retorna:
    - ClassifyResponse: resultado completo de la clasificacion.

    Notas:
    - `request_id` del cuerpo de respuesta es autoritativo y generado por el servidor.
    - El mismo valor debe coincidir con `X-Request-ID` y con los logs de la solicitud.
    - Cuando se aplica fallback de politica por confianza baja, la API emite
      un evento informativo adicional antes del evento final de clasificacion.

    Excepciones:
    - ModelNotReadyError: si el servicio aun no esta listo.
    - ModelEtherTypeUnsupportedError: si el modo `MODEL` recibe un EtherType no soportado.
    - PolicyMappingFailedError: si la politica no puede resolverse.
    """

    started = time.perf_counter()
    services = request.app.state.services
    request_id = get_request_id(request)
    if not services.readiness.ready or services.classifier is None:
        raise ModelNotReadyError(
            component=services.readiness.error_component or "inference_service",
            failed_stage=services.readiness.failed_stage,
            failed_check=services.readiness.failed_check,
            retryable=services.readiness.retryable,
            request_id=request_id,
        )

    try:
        validate_packet_for_classification_mode(
            classification_mode=services.settings.classification_mode,
            eth_type=payload.packet_features.eth_type,
            request_id=request_id,
        )
    except ModelEtherTypeUnsupportedError as exc:
        logger.warning(
            Messages.CLASSIFICATION_REJECTED,
            extra={
                "service": services.settings.app_name,
                "event": "classification_rejected",
                "request_id": request_id,
                "classification_mode": services.settings.classification_mode.response_value,
                "error_code": exc.code,
                "component": exc.component,
                "failed_stage": exc.failed_stage,
                "failed_check": exc.failed_check,
                "retryable": exc.retryable,
            },
        )
        raise

    prediction = services.classifier.predict(payload.packet_features.model_dump())

    policy_response = None
    if services.policy_mapper is not None:
        try:
            # La resolucion de politica se ejecuta despues de la prediccion para
            # poder decidir si aplica la clase especifica o el perfil por defecto.
            selected_policy, fallback, fallback_reason = services.policy_mapper.resolve(
                predicted_class=prediction.class_name,
                confidence=prediction.confidence,
            )
        except Exception as exc:
            raise PolicyMappingFailedError() from exc
        policy_response = PolicyResponse(
            **selected_policy.model_dump(),
            policy_fallback=fallback,
            policy_fallback_reason=fallback_reason,
        )
        if fallback:
            logger.info(
                Messages.POLICY_FALLBACK_APPLIED,
                extra={
                    "service": services.settings.app_name,
                    "event": "policy_fallback_applied",
                    "request_id": request_id,
                    "component": "policy_mapper",
                    "fallback_reason": fallback_reason,
                    "predicted_class": prediction.class_name,
                    "confidence": prediction.confidence,
                },
            )

    processing_time_ms = round((time.perf_counter() - started) * 1000, 3)
    logger.info(
        Messages.CLASSIFICATION_COMPLETED,
        extra={
            "service": services.settings.app_name,
            "event": "classification_completed",
            "request_id": request_id,
            "model_name": services.model_metadata.model_name if services.model_metadata is not None else None,
            "predicted_class": prediction.class_name,
            "confidence": prediction.confidence,
            "processing_time_ms": processing_time_ms,
        },
    )

    return ClassifyResponse(
        request_id=request_id,
        model_name=services.model_metadata.model_name if services.model_metadata is not None else "deterministic_test",
        prediction=PredictionBody(
            class_id=prediction.class_id,
            class_name=prediction.class_name,
            confidence=prediction.confidence,
        ),
        probabilities=prediction.probabilities,
        policy=policy_response,
        processing_time_ms=processing_time_ms,
    )
