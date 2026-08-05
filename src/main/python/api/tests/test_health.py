"""Prueba los endpoints de liveness y readiness cacheada.

Pasos:
- Verifica liveness independiente del estado del modelo.
- Comprueba respuestas ready, initializing y not_ready.
- Confirma que readiness repetida no reejecuta inferencia.
"""

from app.main import app
from app.middleware import CorrelationIdMiddleware, RequestSizeLimitMiddleware
from app.readiness import ReadinessState


def test_liveness(client):
    """Verifica que liveness responda siempre con `alive`."""

    response = client.get("/health/live")
    assert response.status_code == 200
    assert response.json() == {"status": "alive"}
    assert response.headers["X-Request-ID"]


def test_readiness_loaded(client):
    """Comprueba la respuesta de readiness cuando startup fue exitoso."""

    response = client.get("/health/ready")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ready"
    assert body["ready"] is True
    assert body["classification_mode"] == "model"
    assert body["model_loaded"] is True
    assert body["metadata_loaded"] is True
    assert body["policy_loaded"] is True
    assert body["synthetic_inference_passed"] is True
    assert body["class_count"] == 7
    assert response.headers["X-Request-ID"]


def test_readiness_initializing_response(client):
    """Verifica el payload devuelto mientras el servicio sigue inicializando."""

    client.app.state.services.readiness = ReadinessState.initializing("model")
    response = client.get("/health/ready")
    assert response.status_code == 503
    body = response.json()
    assert body == {
        "request_id": response.headers["X-Request-ID"],
        "status": "initializing",
        "ready": False,
        "classification_mode": "model",
        "error": None,
    }
    assert response.headers["X-Request-ID"]


def test_readiness_not_loaded(monkeypatch, config_dir_path, policy_filename, deterministic_rule_filename):
    """Confirma que readiness falle si el artefacto del modelo no existe."""

    from fastapi.testclient import TestClient

    from app.config import get_raw_settings
    from app.main import app

    monkeypatch.setenv("CLASSIFICATION_MODE", "MODEL")
    monkeypatch.setenv("MODEL_DIR", "/nonexistent")
    monkeypatch.setenv("CONFIG_DIR", config_dir_path)
    monkeypatch.setenv("POLICY_FILENAME", policy_filename)
    monkeypatch.setenv("DETERMINISTIC_RULE_FILENAME", deterministic_rule_filename)
    get_raw_settings.cache_clear()
    with TestClient(app) as client:
        response = client.get("/health/ready")
        assert response.status_code == 503
        body = response.json()
        assert body["status"] == "not_ready"
        assert body["error"]["code"] == "MODEL_FILE_NOT_FOUND"
        assert body["error"]["failed_stage"] == "artifact_checks"
        assert body["request_id"] == response.headers["X-Request-ID"]
    get_raw_settings.cache_clear()


def test_repeated_readiness_calls_do_not_rerun_inference(client, dummy_booster_class):
    """Asegura que readiness use el cache y no dispare inferencia repetida."""

    assert dummy_booster_class.predict_call_count == 1
    response_one = client.get("/health/ready")
    response_two = client.get("/health/ready")
    assert response_one.status_code == 200
    assert response_two.status_code == 200
    assert dummy_booster_class.predict_call_count == 1
    assert response_one.headers["X-Request-ID"] != response_two.headers["X-Request-ID"]


def test_user_middleware_order_places_correlation_first():
    """Bloquea el orden requerido del stack de middlewares HTTP."""

    assert [middleware.cls for middleware in app.user_middleware] == [
        CorrelationIdMiddleware,
        RequestSizeLimitMiddleware,
    ]
