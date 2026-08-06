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

import asyncio
import logging
import time

from anyio import to_thread
from fastapi import APIRouter, Request

from app.sdn_mpls_ml_exceptions import (
    AppError,
    InferenceCapacityExceededError,
    ModelEtherTypeUnsupportedError,
    ModelInferenceFailedError,
    ModelNotReadyError,
    ModelOutputInvalidError,
    PolicyMappingFailedError,
)
from app.observability.classification_metrics import (
    CLASSIFICATION_IN_PROGRESS,
    CLASSIFICATION_RESULTS_TOTAL,
    POLICY_FALLBACKS_TOTAL,
    POLICY_SELECTIONS_TOTAL,
    PREDICTION_CONFIDENCE,
    ClassificationObservation,
    execute_instrumented_inference,
)
from app.sdn_mpls_ml_messages import Messages
from app.model.input_validation import validate_packet_for_classification_mode
from app.sdn_mpls_ml_request_context import get_request_id
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
async def classify(payload: ClassifyRequest, request: Request) -> ClassifyResponse:
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

    services = request.app.state.services
    request_id = get_request_id(request)
    settings = services.settings
    classification_mode = (
        settings.classification_mode.response_value
        if settings
        else services.readiness.classification_mode
    )
    metrics_enabled = settings is not None and settings.enable_prometheus_metrics
    observation = ClassificationObservation(
        classification_mode=classification_mode, enabled=metrics_enabled
    )
    if metrics_enabled:
        CLASSIFICATION_IN_PROGRESS.labels(classification_mode=classification_mode).inc()

    try:
        if (
            not services.readiness.ready
            or services.classifier_pool is None
            or services.inference_thread_limiter is None
            or settings is None
        ):
            observation.mark_outcome("not_ready")
            raise ModelNotReadyError(
                component=services.readiness.error_component or "inference_service",
                failed_stage=services.readiness.failed_stage,
                failed_check=services.readiness.failed_check,
                retryable=services.readiness.retryable,
                request_id=request_id,
            )

        try:
            validate_packet_for_classification_mode(
                classification_mode=settings.classification_mode,
                eth_type=payload.packet_features.eth_type,
                request_id=request_id,
            )
        except ModelEtherTypeUnsupportedError as exc:
            observation.mark_outcome("rejected")
            logger.warning(
                Messages.CLASSIFICATION_REJECTED,
                extra={
                    "service": settings.app_name,
                    "event": "classification_rejected",
                    "request_id": request_id,
                    "classification_mode": classification_mode,
                    "error_code": exc.code,
                    "component": exc.component,
                    "failed_stage": exc.failed_stage,
                    "failed_check": exc.failed_check,
                    "retryable": exc.retryable,
                },
            )
            raise

        packet_features = payload.packet_features.model_dump()
        queue_wait_started = time.perf_counter()
        try:
            async with services.classifier_pool.acquire(
                timeout_seconds=settings.request_timeout_seconds
            ) as classifier:
                queue_wait_ms = round((time.perf_counter() - queue_wait_started) * 1000, 3)
                try:
                    prediction = await execute_instrumented_inference(
                        classifier=classifier,
                        packet_features=packet_features,
                        classification_mode=classification_mode,
                        limiter=services.inference_thread_limiter,
                        enabled=metrics_enabled,
                        run_sync=to_thread.run_sync,
                    )
                except AppError as exc:
                    exc.request_id = request_id
                    raise
                pool_capacity = services.classifier_pool.capacity
                pool_available = services.classifier_pool.available
                pool_borrowed = services.classifier_pool.borrowed
        except InferenceCapacityExceededError as exc:
            observation.mark_outcome("capacity_timeout")
            raise InferenceCapacityExceededError(
                request_id=request_id,
                component=exc.component,
                failed_stage=exc.failed_stage,
                failed_check=exc.failed_check,
                retryable=exc.retryable,
            ) from exc
        except ModelInferenceFailedError:
            observation.mark_outcome("inference_failed")
            raise
        except ModelOutputInvalidError:
            observation.mark_outcome("output_invalid")
            raise

        if metrics_enabled:
            CLASSIFICATION_RESULTS_TOTAL.labels(
                classification_mode=classification_mode, class_name=prediction.class_name
            ).inc()
            PREDICTION_CONFIDENCE.labels(
                classification_mode=classification_mode, class_name=prediction.class_name
            ).observe(prediction.confidence)

        policy_response = None
        if services.policy_mapper is not None:
            try:
                selected_policy, fallback, fallback_reason = services.policy_mapper.resolve(
                    predicted_class=prediction.class_name,
                    confidence=prediction.confidence,
                )
            except Exception as exc:
                observation.mark_outcome("policy_failed")
                raise PolicyMappingFailedError() from exc
            policy_response = PolicyResponse(
                **selected_policy.model_dump(),
                policy_fallback=fallback,
                policy_fallback_reason=fallback_reason,
            )
            if metrics_enabled:
                POLICY_SELECTIONS_TOTAL.labels(
                    profile_name=selected_policy.profile_name, fallback=str(fallback).lower()
                ).inc()
                if fallback:
                    POLICY_FALLBACKS_TOTAL.labels(
                        reason=fallback_reason or "unknown", predicted_class=prediction.class_name
                    ).inc()
            if fallback:
                logger.info(
                    Messages.POLICY_FALLBACK_APPLIED,
                    extra={
                        "service": settings.app_name,
                        "event": "policy_fallback_applied",
                        "request_id": request_id,
                        "component": "policy_mapper",
                        "fallback_reason": fallback_reason,
                        "predicted_class": prediction.class_name,
                        "confidence": prediction.confidence,
                        "pool_capacity": pool_capacity,
                        "pool_available": pool_available,
                        "pool_borrowed": pool_borrowed,
                        "queue_wait_ms": queue_wait_ms,
                    },
                )

        processing_time_ms = round((time.perf_counter() - observation.started_at) * 1000, 3)
        logger.info(
            Messages.CLASSIFICATION_COMPLETED,
            extra={
                "service": settings.app_name,
                "event": "classification_completed",
                "request_id": request_id,
                "model_name": services.model_metadata.model_name
                if services.model_metadata is not None
                else None,
                "predicted_class": prediction.class_name,
                "confidence": prediction.confidence,
                "pool_capacity": pool_capacity,
                "pool_available": pool_available,
                "pool_borrowed": pool_borrowed,
                "queue_wait_ms": queue_wait_ms,
                "processing_time_ms": processing_time_ms,
            },
        )
        observation.mark_outcome("success")
        return ClassifyResponse(
            request_id=request_id,
            model_name=services.model_metadata.model_name
            if services.model_metadata is not None
            else "deterministic_test",
            prediction=PredictionBody(
                class_id=prediction.class_id,
                class_name=prediction.class_name,
                confidence=prediction.confidence,
            ),
            probabilities=prediction.probabilities,
            policy=policy_response,
            processing_time_ms=processing_time_ms,
        )
    except asyncio.CancelledError:
        observation.mark_outcome("cancelled")
        raise
    finally:
        observation.finish()
        if metrics_enabled:
            CLASSIFICATION_IN_PROGRESS.labels(classification_mode=classification_mode).dec()
