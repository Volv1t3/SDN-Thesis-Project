"""Metricas base y recorder de observabilidad del servicio."""

from __future__ import annotations

from dataclasses import dataclass

from prometheus_client import Counter, Gauge, Histogram

from app.observability.identity import ProcessIdentity


WORKER_INFO = Gauge("sdnflow_worker_info", "Identidad de cada worker activo del servicio.", ["instance_id", "worker_id", "classification_mode"], multiprocess_mode="liveall")
WORKER_START_TIME_SECONDS = Gauge("sdnflow_worker_start_time_seconds", "Tiempo Unix de inicio de cada worker activo.", ["instance_id", "worker_id"], multiprocess_mode="liveall")
READINESS = Gauge("sdnflow_readiness", "Estado de readiness del servicio.", ["classification_mode"], multiprocess_mode="livemin")
STARTUP_VALIDATION_DURATION_SECONDS = Histogram("sdnflow_startup_validation_duration_seconds", "Duracion de la validacion completa de startup.", ["classification_mode", "outcome"], buckets=(0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0, 30.0))
STARTUP_FAILURES_TOTAL = Counter("sdnflow_startup_failures_total", "Total de fallos controlados durante startup.", ["failed_stage", "error_code"])
CLASSIFIER_POOL_CAPACITY = Gauge("sdnflow_classifier_pool_capacity", "Cantidad total de clasificadores en pools activos.", ["classification_mode"], multiprocess_mode="livesum")
CLASSIFIER_POOL_AVAILABLE = Gauge("sdnflow_classifier_pool_available", "Cantidad de clasificadores disponibles en pools activos.", ["classification_mode"], multiprocess_mode="livesum")
CLASSIFIER_POOL_BORROWED = Gauge("sdnflow_classifier_pool_borrowed", "Cantidad de clasificadores prestados en pools activos.", ["classification_mode"], multiprocess_mode="livesum")
CLASSIFIER_POOL_WAIT_SECONDS = Histogram("sdnflow_classifier_pool_wait_seconds", "Tiempo de espera para adquirir un clasificador.", ["classification_mode", "outcome"], buckets=(0.0001, 0.00025, 0.0005, 0.001, 0.0025, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0))
CLASSIFIER_POOL_TIMEOUTS_TOTAL = Counter("sdnflow_classifier_pool_timeouts_total", "Total de timeouts al adquirir clasificadores.", ["classification_mode"])


@dataclass(slots=True)
class BaselineMetrics:
    """Publica las metricas base con el modo de clasificacion acotado."""

    classification_mode: str

    def initialize_worker(self, identity: ProcessIdentity) -> None:
        WORKER_INFO.labels(instance_id=identity.instance_id, worker_id=identity.worker_id, classification_mode=self.classification_mode).set(1)
        WORKER_START_TIME_SECONDS.labels(instance_id=identity.instance_id, worker_id=identity.worker_id).set(identity.started_at_unix_seconds)

    def set_readiness(self, ready: bool) -> None:
        READINESS.labels(classification_mode=self.classification_mode).set(1 if ready else 0)

    def set_pool_state(self, *, capacity: int, available: int, borrowed: int) -> None:
        CLASSIFIER_POOL_CAPACITY.labels(classification_mode=self.classification_mode).set(capacity)
        CLASSIFIER_POOL_AVAILABLE.labels(classification_mode=self.classification_mode).set(available)
        CLASSIFIER_POOL_BORROWED.labels(classification_mode=self.classification_mode).set(borrowed)

    def set_state(self, *, capacity: int, available: int, borrowed: int) -> None:
        """Implementa el contrato del observador del pool."""

        self.set_pool_state(capacity=capacity, available=available, borrowed=borrowed)

    def observe_wait(self, *, duration_seconds: float, outcome: str) -> None:
        CLASSIFIER_POOL_WAIT_SECONDS.labels(classification_mode=self.classification_mode, outcome=outcome).observe(duration_seconds)

    def record_timeout(self) -> None:
        CLASSIFIER_POOL_TIMEOUTS_TOTAL.labels(classification_mode=self.classification_mode).inc()
