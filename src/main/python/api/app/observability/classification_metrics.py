"""Metricas Prometheus de clasificacion y errores HTTP controlados."""

from __future__ import annotations

import asyncio
import time
from dataclasses import dataclass, field
from typing import Any

from anyio import to_thread
from prometheus_client import Counter, Gauge, Histogram

from app.sdn_mpls_ml_exceptions import ModelOutputInvalidError


CLASSIFICATION_REQUESTS_TOTAL = Counter(
    "sdnflow_classification_requests_total",
    "Total de solicitudes de clasificacion por resultado.",
    ["classification_mode", "outcome"],
)
CLASSIFICATION_IN_PROGRESS = Gauge(
    "sdnflow_classification_in_progress",
    "Solicitudes de clasificacion actualmente en progreso.",
    ["classification_mode"],
    multiprocess_mode="livesum",
)
CLASSIFICATION_DURATION_SECONDS = Histogram(
    "sdnflow_classification_duration_seconds",
    "Duracion total de solicitudes de clasificacion.",
    ["classification_mode", "outcome"],
    buckets=(0.0005, 0.001, 0.0025, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0),
)
INFERENCE_DURATION_SECONDS = Histogram(
    "sdnflow_inference_duration_seconds",
    "Duracion exclusiva de la inferencia.",
    ["classification_mode", "outcome"],
    buckets=(0.0001, 0.00025, 0.0005, 0.001, 0.0025, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0),
)
CLASSIFICATION_RESULTS_TOTAL = Counter(
    "sdnflow_classification_results_total",
    "Total de predicciones validas por clase.",
    ["classification_mode", "class_name"],
)
PREDICTION_CONFIDENCE = Histogram(
    "sdnflow_prediction_confidence",
    "Distribucion de confianza de predicciones.",
    ["classification_mode", "class_name"],
    buckets=(0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 0.95, 0.99, 1.0),
)
POLICY_SELECTIONS_TOTAL = Counter(
    "sdnflow_policy_selections_total",
    "Total de perfiles de politica seleccionados.",
    ["profile_name", "fallback"],
)
POLICY_FALLBACKS_TOTAL = Counter(
    "sdnflow_policy_fallbacks_total",
    "Total de fallbacks de politica aplicados.",
    ["reason", "predicted_class"],
)
REQUEST_ERRORS_TOTAL = Counter(
    "sdnflow_request_errors_total",
    "Total de errores HTTP controlados.",
    ["error_code", "component"],
)
REQUEST_BODY_REJECTIONS_TOTAL = Counter(
    "sdnflow_request_body_rejections_total",
    "Total de rechazos por validacion de cuerpo HTTP.",
    ["reason"],
)


@dataclass(slots=True)
class ClassificationObservation:
    """Garantiza un resultado y una duracion final por clasificacion."""

    classification_mode: str
    enabled: bool = True
    started_at: float = field(default_factory=time.perf_counter)
    outcome: str = "internal_error"
    completed: bool = False

    def mark_outcome(self, outcome: str) -> None:
        self.outcome = outcome

    def finish(self) -> None:
        if self.completed:
            return
        self.completed = True
        if not self.enabled:
            return
        duration_seconds = time.perf_counter() - self.started_at
        CLASSIFICATION_REQUESTS_TOTAL.labels(
            classification_mode=self.classification_mode, outcome=self.outcome
        ).inc()
        CLASSIFICATION_DURATION_SECONDS.labels(
            classification_mode=self.classification_mode, outcome=self.outcome
        ).observe(duration_seconds)


async def execute_instrumented_inference(
    *,
    classifier: Any,
    packet_features: dict[str, int],
    classification_mode: str,
    limiter: Any,
    enabled: bool,
    run_sync: Any = to_thread.run_sync,
) -> Any:
    """Ejecuta inferencia y mide solo el tiempo de despacho y prediccion."""

    started_at = time.perf_counter()
    outcome = "failed"
    try:
        result = await run_sync(classifier.predict, packet_features, limiter=limiter)
        outcome = "success"
        return result
    except ModelOutputInvalidError:
        outcome = "output_invalid"
        raise
    except asyncio.CancelledError:
        outcome = "cancelled"
        raise
    finally:
        if enabled:
            INFERENCE_DURATION_SECONDS.labels(
                classification_mode=classification_mode, outcome=outcome
            ).observe(time.perf_counter() - started_at)


def record_request_error(*, error_code: str, component: str | None, enabled: bool) -> None:
    """Incrementa un error controlado una sola vez en su capa productora."""

    if enabled:
        REQUEST_ERRORS_TOTAL.labels(error_code=error_code, component=component or "unknown").inc()


def record_request_body_rejection(*, reason: str, enabled: bool) -> None:
    """Incrementa un rechazo de cuerpo con una razon acotada."""

    if enabled:
        REQUEST_BODY_REJECTIONS_TOTAL.labels(reason=reason).inc()
