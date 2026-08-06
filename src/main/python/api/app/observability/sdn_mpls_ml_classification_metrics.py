"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370

Archivo de definicion de las metricas de Prometheus orientadas al registro de detalles de:
- Clasificaciones realizadas, fallidas, etc.
- Clasificaciones de clases y sus totales
- Tiempo de inferencia 
- Clasificaciones totales por clase o fallback

Esta seccion depende de dos diferentes componentes:

AnyIO's CapacityLimiter: configurado en sdn_mpls_ml_dependencies.py, corresponde a la cantidad TOTAL de hilos workers que
AnyIO tiene la capacidad de mantener en ejecucion paralela. El sistema se configura con 5 hilos, la misma cantidad de Classifier
Instances que pueden haber una ClassifierPool. En este caso, si se acaban estos workers, entonces requests a la
funcion run_sync bloquean hasta que haya un nuevo hilo disponible, lo que permite controlar
la cantidad de recursos usados por la API

AnyIO/s to_thread.run_sync: definida como una funcion que emite un worker thread para ejecutar una funcion syncrona
dentro de un hilo de trabajo, se usa para manejar la inferencia en un solo hilo secundario al event loop y secundario a
la corutina que maneja la llamada a la API.


Esta clase define las metricas y ademas permite realizar una inferencia asincrona usada por la implementacion de las 
Classifier pools para realizar inferencia fuera del loop principal de la aplicacino en hilos secundarios asyncronos 
en donde se registra los detalles de la inferencia y su tiempo de ejecucion

Notes:
    La clase define solo la parte de inferencia estructurada con AnyIO, la seccion de inferencia y modelo se encarga de
    definir la Pool de trabajadores y el metodo de adquisicion y retorno asincrono, asi como la inferencia asincrona
"""

from __future__ import annotations
import asyncio
import time
from dataclasses import dataclass, field
from typing import Any
from anyio import to_thread
from prometheus_client import Counter, Gauge, Histogram
from app.sdn_mpls_ml_exceptions import ModelOutputInvalidError



CLASSIFICATION_REQUESTS_TOTAL = Counter(
    "sdn_mpls_ml_api_obs_classification_requests_total",
    "Total de solicitudes de clasificacion por resultado.",
    ["classification_mode", "outcome"],
)
CLASSIFICATION_IN_PROGRESS = Gauge(
    "sdn_mpls_ml_api_obs_classification_in_progress",
    "Solicitudes de clasificacion actualmente en progreso.",
    ["classification_mode"],
    multiprocess_mode="livesum",
)
CLASSIFICATION_DURATION_SECONDS = Histogram(
    "sdn_mpls_ml_api_obs_classification_duration_seconds",
    "Duracion total de solicitudes de clasificacion.",
    ["classification_mode", "outcome"],
    buckets=(0.0005, 0.001, 0.0025, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0),
)
INFERENCE_DURATION_SECONDS = Histogram(
    "sdn_mpls_ml_api_obs_inference_duration_seconds",
    "Duracion exclusiva de la inferencia.",
    ["classification_mode", "outcome"],
    buckets=(0.0001, 0.00025, 0.0005, 0.001, 0.0025, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0),
)
CLASSIFICATION_RESULTS_TOTAL = Counter(
    "sdn_mpls_ml_api_obs_classification_results_total",
    "Total de predicciones validas por clase.",
    ["classification_mode", "class_name"],
)
PREDICTION_CONFIDENCE = Histogram(
    "sdn_mpls_ml_api_obs_prediction_confidence",
    "Distribucion de confianza de predicciones.",
    ["classification_mode", "class_name"],
    buckets=(0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 0.95, 0.99, 1.0),
)
POLICY_SELECTIONS_TOTAL = Counter(
    "sdn_mpls_ml_api_obs_policy_selections_total",
    "Total de perfiles de politica seleccionados.",
    ["profile_name", "fallback"],
)
POLICY_FALLBACKS_TOTAL = Counter(
    "sdn_mpls_ml_api_obs_policy_fallbacks_total",
    "Total de fallbacks de politica aplicados.",
    ["reason", "predicted_class"],
)
REQUEST_ERRORS_TOTAL = Counter(
    "sdn_mpls_ml_api_obs_request_errors_total",
    "Total de errores HTTP controlados.",
    ["error_code", "component"],
)
REQUEST_BODY_REJECTIONS_TOTAL = Counter(
    "sdn_mpls_ml_api_obs_request_body_rejections_total",
    "Total de rechazos por validacion de cuerpo HTTP.",
    ["reason"],
)


@dataclass(slots=True)
class ClassificationObservation:
    """
    Clase usada para registrar observaciones sobre una solicitud de clasificacion dentro de los objetos
    de la API. Esta clase se utiliza para marcar el estado de una inferencia realizada mediante el endpoint
    de inferencia. La idea es que el endpoint de inferencia utiliza el metodo execute_instrumented_inference y
    usa un Observer y un hilo externo para marcar el proceso de prediccion y la duracion de la inferencia
    """

    classification_mode: str
    enabled: bool = True
    started_at: float = field(default_factory=time.perf_counter)
    outcome: str = "internal_error"
    completed: bool = False

    def mark_outcome(self, outcome: str) -> None:
        """
        Permite marcar dentro del Observer el estado final de una inferencia
        :param outcome: estado final de la inferencia
        :return: None
        """
        self.outcome = outcome

    def finish(self) -> None:
        """
        Funcion que marca el final de una inferencia realizada y que permite guardar la informacion
        de la clasificacion final en terminos del tiempo total y el total de clasificaciones
        :return:
        """
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
    """
    Funcion que se usa para ejecutar una inferencia instrumentada, es decir que recolecta la informacion acerca de
    la inferencia realizada, su estado y tiempo de duracion dentro de un worker thread de AnyIO.
    :param classifier: instancia de un clasificador desde la Pool configurada en AppServices
    :param packet_features: diccionario con las caracteristicas del paquete a clasificar
    :param classification_mode: modo de clasificacion de las settings de AppServices
    :param limiter: Limitador de hilos de inferencia de las settings de AppServices
    :param enabled: si las metricas de duracion de clasificacion estan activadas
    :param run_sync: funcion usada para ejecutar el thread secundario de inferencia
    :return: resultado de la inferencia o excepcion en caso de fallo
    en la inferencia o en el procesamiento del resultado
    """

    started_at = time.perf_counter()
    outcome = "failed"
    try:
        #? Esta seccion utiliza la base to_thread.run_sync, una funcion de la libreria de AnyIO que se encarga de
        #? generar un worker thread por fuera del hilo secundario de la corutina que maneja la request HTTP. En este
        #? caso, internamente dentro de este worker se ejecuta sincronamente el metodo predict del clasificador (de
        #? la instancia de una pool que fue adquirida desde la corutina que maneja la REST call que desplego este
        #? proceso) y lo ejecuta en un solo hilo del CPU por detras sin bloquear el CPU
        result = await run_sync(
            classifier.predict, #* Corresponde al metodo a ejecutar
            packet_features, #* Correspoonde a las features de inferencia
            limiter=limiter #*Corresponde al limitador de inferencia CapacityLimiter que controla la cantidad de hilos
            #* del propio AnyIO que pueden estar activos, es decir, esto controla si tenemos un hilo para inferencia,
            #* no solo si tenemos un objeto Classifier pero si el sistema tiene capacidad de procesamiento
            #* libre
        )
        outcome = "success"
        return result
    except ModelOutputInvalidError:
        outcome = "output_invalid"
        raise
    except asyncio.CancelledError:
        outcome = "cancelled"
        raise
    finally:
        #? Si las metricas estan activadas entonces registramos la duracion de la inferencia
        if enabled:
            INFERENCE_DURATION_SECONDS.labels(
                classification_mode=classification_mode, outcome=outcome
            ).observe(time.perf_counter() - started_at)


def record_request_error(*, error_code: str, component: str | None, enabled: bool) -> None:
    """
    Incrementa un error controlado una sola vez en su capa productora.

    Args:
        error_code: codigo de error.
        component: componente productor.
        enabled: si las metricas estan activadas.
    """

    if enabled:
        REQUEST_ERRORS_TOTAL.labels(error_code=error_code, component=component or "unknown").inc()


def record_request_body_rejection(*, reason: str, enabled: bool) -> None:
    """
    Incrementa un rechazo de cuerpo con una razon acotada.

    Args:
        reason: razon del rechazo.
        enabled: si las metricas estan activadas.
    """

    if enabled:
        REQUEST_BODY_REJECTIONS_TOTAL.labels(reason=reason).inc()
