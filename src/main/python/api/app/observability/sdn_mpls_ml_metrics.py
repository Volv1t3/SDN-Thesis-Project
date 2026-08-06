"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370

Archivo que define las metricas bases de observabilidad de la aplicacion en lo que corresponde a:
- Metricas basicas de estado del sistema (metricas del sistema como estado de raeadiness y el tiempo de inicializacion o
posibles errores)
- Metricas basicas de estado del pool de clasificadores (corresponde al estado, distribucion y participacion de los workers
de las pools de clasificadores, sean estos deterministicos o basados en el modelo general)
- Metricas basicas de estado del worker (corresponde a la identidad de los workers y el tiempo de inicializacion de los workers
de uvicorn)

La informacion registrada corresponde a metricas expuestas para Prometheus que pueden ser ingeridas por Grafana.
"""

from __future__ import annotations
from dataclasses import dataclass
from prometheus_client import Counter, Gauge, Histogram
#? Este import de ProcessIdentity
from app.observability.sdn_mpls_ml_identity import ProcessIdentity


WORKER_INFO = Gauge(
    "sdn_mpls_ml_api_obs_worker_info",
    "Identidad de cada worker activo del servicio.",
    ["instance_id", "worker_id", "classification_mode"],
    multiprocess_mode="liveall")
WORKER_START_TIME_SECONDS = Gauge(
    "sdn_mpls_ml_api_obs_worker_start_time_seconds",
    "Tiempo Unix de inicio de cada worker activo.",
    ["instance_id", "worker_id"],
    multiprocess_mode="liveall")
READINESS = Gauge(
    "sdn_mpls_ml_api_obs_readiness",
    "Estado de readiness del servicio.",
    ["classification_mode"],
    multiprocess_mode="livemin")
STARTUP_VALIDATION_DURATION_SECONDS = Histogram(
    "sdn_mpls_ml_api_obs_startup_validation_duration_seconds",
    "Duracion de la validacion completa de startup.",
    ["classification_mode", "outcome"],
    buckets=(0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0, 30.0))
STARTUP_FAILURES_TOTAL = Counter(
    "sdn_mpls_ml_api_obs_startup_failures_total",
    "Total de fallos controlados durante startup.",
    ["failed_stage", "error_code"])
CLASSIFIER_POOL_CAPACITY = Gauge(
    "sdn_mpls_ml_api_obs_classifier_pool_capacity",
    "Cantidad total de clasificadores en pools activos.",
    ["classification_mode"],
    multiprocess_mode="livesum")
CLASSIFIER_POOL_AVAILABLE = Gauge("sdn_mpls_ml_api_obs_classifier_pool_available",
                                  "Cantidad de clasificadores "
                                  "disponibles en pools activos.",
                                  ["classification_mode"],
                                  multiprocess_mode="livesum")
CLASSIFIER_POOL_BORROWED = Gauge("sdn_mpls_ml_api_obs_classifier_pool_borrowed",
                                 "Cantidad de clasificadores prestados en pools activos.",
                                 ["classification_mode"],
                                 multiprocess_mode="livesum")
CLASSIFIER_POOL_WAIT_SECONDS = Histogram("sdn_mpls_ml_api_obs_classifier_pool_wait_seconds",
                                         "Tiempo de espera para adquirir un clasificador.",
                                         ["classification_mode", "outcome"],
                                         buckets=(0.0001, 0.00025, 0.0005, 0.001, 0.0025, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0))
CLASSIFIER_POOL_TIMEOUTS_TOTAL = Counter("sdn_mpls_ml_api_obs_classifier_pool_timeouts_total",
                                         "Total de timeouts al adquirir clasificadores.",
                                         ["classification_mode"])


@dataclass(slots=True)
class BaselineMetrics:
    """
    Publica las metricas base con el modo de clasificacion acotado.
    """

    classification_mode: str

    def initialize_worker(self, identity: ProcessIdentity) -> None:
        """
        Configura las metricas correspondientes a la informacion de los workers activos y el tiempo de inicio dentro de
        la data de Prometheus en este worker para su reporte en /metrics
        :param identity: Identidad del Proceso actual
        :return: None
        """
        WORKER_INFO.labels(
            instance_id=identity.instance_id,
            worker_id=identity.worker_id,
            classification_mode=self.classification_mode).set(1)
        WORKER_START_TIME_SECONDS.labels(
            instance_id=identity.instance_id,
            worker_id=identity.worker_id).set(identity.started_at_unix_seconds)

    def set_readiness(self, ready: bool) -> None:
        """
        Configura el estado de readiness del servicio en funcion del modo de clasificacion. La informacion se carga
        desde el sistema de sdn_mpls_ml_dependencies.py que valida la configuracion final de todos los recursos de la API
        :param ready: si el sistema esta listo o no
        :return: None
        """
        READINESS.labels(
            classification_mode=self.classification_mode).set(1 if ready else 0)

    def set_pool_state(self, *, capacity: int, available: int, borrowed: int) -> None:
        """
        Configura el estado y la informacion actual actualizada del sistema de clasificadores en base a la informacion
        obtenida del servicio de clasificadores (pool de modelos o clasificadores deterministas) y los expone a
        Prometheus
        :param capacity: cuantos workers hay en el pool en total
        :param available: cuantos workers estan disponibles en el pool
        :param borrowed: cuantos workers estan siendo utilizados en el pool
        :return: None
        """
        CLASSIFIER_POOL_CAPACITY.labels(
            classification_mode=self.classification_mode).set(capacity)
        CLASSIFIER_POOL_AVAILABLE.labels(
            classification_mode=self.classification_mode).set(available)
        CLASSIFIER_POOL_BORROWED.labels(
            classification_mode=self.classification_mode).set(borrowed)

    def set_state(self, *, capacity: int, available: int, borrowed: int) -> None:
        """
        Implementa el contrato del observador del pool.
        Este metodo es invocado por el pool cuando cambia su estado interno.
        :param capacity: cuantos workers hay en el pool en total
        :param available: cuantos workers estan disponibles en el pool
        :param borrowed: cuantos workers estan siendo utilizados en el pool
        :return: None
        """
        self.set_pool_state(capacity=capacity, available=available, borrowed=borrowed)

    def observe_wait(self, *, duration_seconds: float, outcome: str) -> None:
        """
        Registra la duracion de las solicitudes de adquisicion de un clasificador, este metodo es usado en el apartado
        privado de la configuracion del pool
        :param duration_seconds: duracion de la solicitud en segundos
        :param outcome: resultado de la solicitud
        :return: None
        """
        CLASSIFIER_POOL_WAIT_SECONDS.labels(
            classification_mode=self.classification_mode,
            outcome=outcome).observe(duration_seconds)

    def record_timeout(self) -> None:
        """
        Registra un timeout en la adquisicion de un clasificador
        :return: None
        """
        CLASSIFIER_POOL_TIMEOUTS_TOTAL.labels(
            classification_mode=self.classification_mode).inc()
