"""Prueba el contrato HTTP principal de los endpoints de clasificacion.

Pasos:
- Verifica metadata expuesta por `/api/v1/model`.
- Ejerce clasificaciones correctas e incorrectas.
- Confirma ausencia de endpoints no soportados.
"""

import logging
import time
from concurrent.futures import ThreadPoolExecutor
from threading import Event, Lock
from uuid import UUID

from anyio import CapacityLimiter
import pytest

from app.api import inference as inference_api
from app.model.pool import ClassifierPool
from app.model.predictor import PredictionResult
from app.sdn_mpls_ml_exceptions import ModelInferenceFailedError, ModelOutputInvalidError
from app.sdn_mpls_ml_main import app


def test_model_endpoint(client):
    """Verifica que el endpoint de modelo exponga metadata utilizable."""

    response = client.get("/api/v1/model")
    assert response.status_code == 200
    assert response.json()["model_name"] == "sdnflow_xgboost_first_packet"
    assert response.headers["X-Request-ID"]


def test_classify_valid_request(client):
    """Confirma que una solicitud valida devuelve prediccion y probabilidades."""

    response = client.post(
        "/api/v1/classify",
        json={
            "packet_features": {
                "eth_type": 2048,
                "ip_proto": 6,
                "src_port": 51514,
                "dst_port": 443,
            }
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["request_id"] == response.headers["X-Request-ID"]
    UUID(body["request_id"])
    assert body["prediction"]["class_name"] == "STREAMING"
    assert "processing_time_ms" in body
    assert "orchestration" not in body
    assert set(body["probabilities"]) == {"DNS", "FTP", "HTTP", "ICMP", "NTP", "SSH", "STREAMING"}


def test_classify_returns_classifier_to_pool_after_success(client):
    """La instancia prestada vuelve al pool al terminar una clasificacion exitosa."""

    pool = client.app.state.services.classifier_pool
    assert pool is not None

    response = client.post(
        "/api/v1/classify",
        json={
            "packet_features": {
                "eth_type": 2048,
                "ip_proto": 6,
                "src_port": 51514,
                "dst_port": 443,
            }
        },
    )

    assert response.status_code == 200
    assert pool.available == pool.capacity
    assert pool.borrowed == 0


@pytest.mark.parametrize(
    ("raised_error", "expected_code"),
    [
        (ModelInferenceFailedError(), "MODEL_INFERENCE_FAILED"),
        (ModelOutputInvalidError(), "MODEL_OUTPUT_INVALID"),
    ],
)
def test_classify_returns_classifier_to_pool_after_typed_prediction_failure(
    client,
    monkeypatch,
    raised_error,
    expected_code,
):
    """Los errores de inferencia tipados no dejan una instancia prestada."""

    pool = client.app.state.services.classifier_pool
    assert pool is not None

    async def raise_from_worker(*_args, **_kwargs):
        raise raised_error

    monkeypatch.setattr(inference_api.to_thread, "run_sync", raise_from_worker)
    response = client.post(
        "/api/v1/classify",
        json={
            "packet_features": {
                "eth_type": 2048,
                "ip_proto": 6,
                "src_port": 51514,
                "dst_port": 443,
            }
        },
    )

    assert response.status_code == 500
    assert response.json()["error"]["code"] == expected_code
    assert pool.available == pool.capacity
    assert pool.borrowed == 0


def test_classify_not_ready_returns_structured_503(client):
    """Verifica que un estado no listo bloquee clasificacion con diagnostico."""

    client.app.state.services.readiness.ready = False
    client.app.state.services.readiness.initialization_completed = True
    client.app.state.services.readiness.error_component = "inference_service"
    client.app.state.services.readiness.failed_stage = "runtime_model_compatibility"
    client.app.state.services.readiness.failed_check = "synthetic_inference"
    client.app.state.services.readiness.retryable = False
    response = client.post(
        "/api/v1/classify",
        json={
            "packet_features": {
                "eth_type": 2048,
                "ip_proto": 6,
                "src_port": 51514,
                "dst_port": 443,
            },
        },
    )
    assert response.status_code == 503
    body = response.json()
    assert body["request_id"] == response.headers["X-Request-ID"]
    UUID(body["request_id"])
    assert body["error"]["code"] == "MODEL_NOT_READY"
    assert body["error"]["failed_stage"] == "runtime_model_compatibility"
    assert body["error"]["component"] == "inference_service"


def test_classify_invalid_request(client):
    """Comprueba que los errores de schema se normalicen a 422 estructurado."""

    response = client.post(
        "/api/v1/classify",
        json={
            "packet_features": {
                "eth_type": 2048,
                "ip_proto": 1,
                "src_port": 51514,
                "dst_port": 0,
            }
        },
    )
    assert response.status_code == 422
    body = response.json()
    assert body["error"]["code"] == "REQUEST_VALIDATION_FAILED"
    assert body["error"]["message"] == "El cuerpo de la solicitud no cumple con el contrato requerido."
    assert body["error"]["component"] == "request_validation"
    assert body["error"]["failed_stage"] == "request_schema_validation"
    assert body["error"]["failed_check"] == "pydantic_schema"
    assert body["error"]["retryable"] is False
    assert body["request_id"] == response.headers["X-Request-ID"]
    UUID(body["request_id"])


def test_classify_invalid_json_returns_structured_400(client):
    """Comprueba que JSON malformado se distinga de un fallo de schema.

    Pasos:
    - Envia un cuerpo JSON truncado al endpoint de clasificacion.
    - Verifica que la API responda `400 INVALID_JSON`.
    - Confirma que la correlacion HTTP se preserve en header y cuerpo.
    """

    response = client.post(
        "/api/v1/classify",
        content='{"packet_features": ',
        headers={"Content-Type": "application/json"},
    )
    assert response.status_code == 400
    body = response.json()
    assert body["error"]["code"] == "INVALID_JSON"
    assert body["error"]["component"] == "request_validation"
    assert body["error"]["failed_stage"] == "request_body_validation"
    assert body["error"]["failed_check"] == "json_parse"
    assert body["error"]["retryable"] is False
    assert body["request_id"] == response.headers["X-Request-ID"]
    UUID(body["request_id"])


def test_request_id_is_not_accepted_in_body(client):
    """Comprueba que el cuerpo no permita fijar el request ID autoritativo."""

    client_supplied_id = "d996cd46-04e2-47aa-8715-83daa215c65e"
    response = client.post(
        "/api/v1/classify",
        json={
            "request_id": client_supplied_id,
            "packet_features": {
                "eth_type": 2048,
                "ip_proto": 17,
                "src_port": 53000,
                "dst_port": 53,
            },
        },
    )
    assert response.status_code == 422
    body = response.json()
    assert body["request_id"] == response.headers["X-Request-ID"]
    assert body["request_id"] != client_supplied_id
    UUID(body["request_id"])


def test_request_too_large_returns_correlated_413(client):
    """Verifica que el rechazo por tamano preserve correlacion y contrato HTTP."""

    response = client.post(
        "/api/v1/classify",
        json={
            "packet_features": {
                "eth_type": 2048,
                "ip_proto": 17,
                "src_port": 53000,
                "dst_port": 53,
            },
            "padding": "x" * 20000,
        },
    )
    assert response.status_code == 413
    body = response.json()
    assert body["error"]["code"] == "REQUEST_TOO_LARGE"
    assert body["request_id"] == response.headers["X-Request-ID"]
    UUID(body["request_id"])


def test_openapi_generated(client):
    """Valida que la especificacion OpenAPI siga disponible."""

    response = client.get("/openapi.json")
    assert response.status_code == 200
    assert "paths" in response.json()


def test_classify_sixth_request_waits_until_pool_capacity_is_released(client):
    """Confirma que una sexta solicitud espere hasta liberar una instancia."""

    services = client.app.state.services

    class BlockingClassifier:
        def __init__(self, ready_event: Event, release_event: Event, counter: dict[str, int], lock: Lock) -> None:
            self._ready_event = ready_event
            self._release_event = release_event
            self._counter = counter
            self._lock = lock

        def predict(self, _packet_features: dict[str, int]) -> PredictionResult:
            with self._lock:
                self._counter["value"] += 1
                if self._counter["value"] == 5:
                    self._ready_event.set()
            self._release_event.wait(timeout=2.0)
            return PredictionResult(
                class_id=6,
                class_name="STREAMING",
                confidence=1.0,
                probabilities={
                    "DNS": 0.0,
                    "FTP": 0.0,
                    "HTTP": 0.0,
                    "ICMP": 0.0,
                    "NTP": 0.0,
                    "SSH": 0.0,
                    "STREAMING": 1.0,
                },
            )

    ready_event = Event()
    release_event = Event()
    counter = {"value": 0}
    lock = Lock()
    services.classifier_pool = ClassifierPool(
        [BlockingClassifier(ready_event, release_event, counter, lock) for _ in range(5)]
    )
    services.inference_thread_limiter = CapacityLimiter(5)
    services.settings.request_timeout_seconds = 1
    payload = {
        "packet_features": {
            "eth_type": 2048,
            "ip_proto": 6,
            "src_port": 51514,
            "dst_port": 443,
        }
    }

    with ThreadPoolExecutor(max_workers=6) as executor:
        initial_futures = [executor.submit(client.post, "/api/v1/classify", json=payload) for _ in range(5)]
        assert ready_event.wait(timeout=2.0)
        sixth_future = executor.submit(client.post, "/api/v1/classify", json=payload)
        time.sleep(0.05)
        assert sixth_future.done() is False
        release_event.set()
        assert sixth_future.result(timeout=2.0).status_code == 200
        for future in initial_futures:
            assert future.result(timeout=2.0).status_code == 200


def test_classify_returns_503_when_pool_capacity_timeout_is_exceeded(client, caplog):
    """Verifica la respuesta estructurada cuando el pool no entrega capacidad a tiempo."""

    services = client.app.state.services

    class BlockingClassifier:
        def __init__(self, ready_event: Event, release_event: Event, counter: dict[str, int], lock: Lock) -> None:
            self._ready_event = ready_event
            self._release_event = release_event
            self._counter = counter
            self._lock = lock

        def predict(self, _packet_features: dict[str, int]) -> PredictionResult:
            with self._lock:
                self._counter["value"] += 1
                if self._counter["value"] == 5:
                    self._ready_event.set()
            self._release_event.wait(timeout=2.0)
            return PredictionResult(
                class_id=6,
                class_name="STREAMING",
                confidence=1.0,
                probabilities={
                    "DNS": 0.0,
                    "FTP": 0.0,
                    "HTTP": 0.0,
                    "ICMP": 0.0,
                    "NTP": 0.0,
                    "SSH": 0.0,
                    "STREAMING": 1.0,
                },
            )

    ready_event = Event()
    release_event = Event()
    counter = {"value": 0}
    lock = Lock()
    services.classifier_pool = ClassifierPool(
        [BlockingClassifier(ready_event, release_event, counter, lock) for _ in range(5)]
    )
    services.inference_thread_limiter = CapacityLimiter(5)
    services.settings.request_timeout_seconds = 0.05
    payload = {
        "packet_features": {
            "eth_type": 2048,
            "ip_proto": 6,
            "src_port": 51514,
            "dst_port": 443,
        }
    }

    caplog.set_level(logging.WARNING, logger="app.sdn_mpls_ml_main")
    with ThreadPoolExecutor(max_workers=6) as executor:
        initial_futures = [executor.submit(client.post, "/api/v1/classify", json=payload) for _ in range(5)]
        assert ready_event.wait(timeout=2.0)
        response = executor.submit(client.post, "/api/v1/classify", json=payload).result(timeout=2.0)
        release_event.set()
        for future in initial_futures:
            assert future.result(timeout=2.0).status_code == 200

    assert response.status_code == 503
    body = response.json()
    assert body["request_id"] == response.headers["X-Request-ID"]
    assert body["error"]["code"] == "INFERENCE_CAPACITY_EXCEEDED"
    assert body["error"]["component"] == "inference_capacity"
    assert body["error"]["failed_stage"] == "classifier_acquisition"
    assert body["error"]["failed_check"] == "classifier_available_before_timeout"
    assert body["error"]["retryable"] is True
    capacity_events = [
        record
        for record in caplog.records
        if getattr(record, "event", None) == "inference_capacity_exceeded"
    ]
    assert len(capacity_events) == 1
    capacity_event = capacity_events[0]
    assert capacity_event.request_id == response.headers["X-Request-ID"]
    assert capacity_event.error_code == "INFERENCE_CAPACITY_EXCEEDED"
    assert capacity_event.pool_capacity == 5
    assert capacity_event.pool_available == 0
    assert capacity_event.pool_borrowed == 5


def test_provisioning_endpoint_is_not_registered(client):
    """Confirma que la API no expone el endpoint de provisionamiento deferido."""

    response = client.post("/api/v1/classify-and-provision")
    assert response.status_code == 404
    body = response.json()
    assert body["error"]["code"] == "HTTP_NOT_FOUND"
    assert body["error"]["component"] == "http_routing"
    assert body["error"]["failed_stage"] == "request_routing"
    assert body["error"]["failed_check"] == "route_resolution"
    assert body["error"]["retryable"] is False
    assert body["request_id"] == response.headers["X-Request-ID"]
    assert "/api/v1/classify-and-provision" not in client.get("/openapi.json").json()["paths"]
