"""Ejercita la ruta completa de clasificacion con un modelo XGBoost real.

Pasos:
- Configura la app para usar los artefactos reales del proyecto.
- Ejecuta solicitudes validas, invalidas y de rechazo por EtherType.
- Confirma integracion completa entre prediccion, readiness y politica.
"""

from __future__ import annotations

import logging
from pathlib import Path
from unittest.mock import Mock
from uuid import UUID

import pytest

from app.model.sdn_mpls_ml_metadata import EXPECTED_MODEL_NAME
from fastapi.testclient import TestClient

from app.sdn_mpls_ml_config import get_raw_settings
from app.sdn_mpls_ml_main import app


PROJECT_ROOT = Path(__file__).resolve().parents[1]
REAL_MODEL_DIR = PROJECT_ROOT / "models"


@pytest.fixture
def real_model_client(monkeypatch, config_dir_path, policy_filename, deterministic_rule_filename):
    """Crea un cliente FastAPI apuntando al modelo real del repositorio.

    Pasos:
    - Configura variables de entorno para modo `MODEL`.
    - Limpia la cache de settings antes y despues del cliente.
    - Devuelve un `TestClient` listo para pruebas de integracion.
    """

    monkeypatch.setenv("CLASSIFICATION_MODE", "MODEL")
    monkeypatch.setenv("MODEL_DIR", str(REAL_MODEL_DIR))
    monkeypatch.setenv("CONFIG_DIR", config_dir_path)
    monkeypatch.setenv("MODEL_FILENAME", "sdn_mpls_ml_model.json")
    monkeypatch.setenv("MODEL_METADATA_FILENAME", "sdn_mpls_ml_model_meta.json")
    monkeypatch.setenv("POLICY_FILENAME", policy_filename)
    monkeypatch.setenv("DETERMINISTIC_RULE_FILENAME", deterministic_rule_filename)
    monkeypatch.setenv("MIN_TUNNEL_BANDWIDTH_KBPS", "10000")
    monkeypatch.setenv("MAX_TUNNEL_BANDWIDTH_KBPS", "100000")
    get_raw_settings.cache_clear()
    with TestClient(app) as test_client:
        yield test_client
    get_raw_settings.cache_clear()


def test_real_model_classify_dns_returns_policy_mapping(real_model_client):
    """Verifica la ruta completa para trafico DNS con politica resuelta."""

    response = real_model_client.post(
        "/api/v1/classify",
        json={
            "packet_features": {
                "eth_type": 2048,
                "ip_proto": 17,
                "src_port": 53000,
                "dst_port": 53,
            }
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["request_id"] == response.headers["X-Request-ID"]
    UUID(body["request_id"])
    assert body["model_name"] == EXPECTED_MODEL_NAME
    assert body["prediction"]["class_id"] == 0
    assert body["prediction"]["class_name"] == "DNS"
    assert body["policy"]["profile_name"] == "dns_tunnel_policy"
    assert body["policy"]["dscp"] == 18
    assert body["policy"]["mpls_tc"] == 2
    assert body["policy"]["path_constraints"]["requested_bandwidth_kbps"] == 10000
    assert body["policy"]["policy_fallback"] is False
    assert body["policy"]["policy_fallback_reason"] is None


def test_real_model_classify_returns_full_response_shape(real_model_client):
    """Comprueba que la respuesta completa incluya probabilidades y politica."""

    response = real_model_client.post(
        "/api/v1/classify",
        json={
            "packet_features": {
                "eth_type": 2048,
                "ip_proto": 6,
                "src_port": 49152,
                "dst_port": 443,
            },
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["request_id"] == response.headers["X-Request-ID"]
    UUID(body["request_id"])
    assert body["model_name"] == EXPECTED_MODEL_NAME
    assert body["prediction"]["class_id"] == 2
    assert body["prediction"]["class_name"] == "HTTP"
    assert body["prediction"]["confidence"] == pytest.approx(0.9999786615371704, abs=1e-6)
    assert set(body["probabilities"]) == {"DNS", "FTP", "HTTP", "ICMP", "NTP", "SSH", "STREAMING"}
    assert sum(body["probabilities"].values()) == pytest.approx(1.0, abs=0.001)
    assert body["policy"] == {
        "profile_name": "http_tunnel_policy",
        "dscp": 0,
        "mpls_tc": 0,
        "path_constraints": {
            "requested_bandwidth_kbps": 25000,
            "setup_priority": 5,
            "hold_priority": 5,
        },
        "policy_fallback": False,
        "policy_fallback_reason": None,
    }
    assert body["processing_time_ms"] >= 0.0


def test_real_model_classify_rejects_invalid_protocol_port_combination(real_model_client):
    """Verifica que combinaciones invalidas sigan fallando en validacion HTTP."""

    response = real_model_client.post(
        "/api/v1/classify",
        json={
            "packet_features": {
                "eth_type": 2048,
                "ip_proto": 1,
                "src_port": 443,
                "dst_port": 0,
            }
        },
    )
    assert response.status_code == 422
    body = response.json()
    assert body["error"]["code"] == "REQUEST_VALIDATION_FAILED"
    assert body["request_id"] == response.headers["X-Request-ID"]
    assert "details" in body


def test_model_mode_rejects_non_ipv4_ethertype(real_model_client):
    """Confirma que el modo `MODEL` rechace un EtherType no IPv4."""

    response = real_model_client.post(
        "/api/v1/classify",
        json={
            "packet_features": {
                "eth_type": 34525,
                "ip_proto": 6,
                "src_port": 49152,
                "dst_port": 443,
            }
        },
    )
    assert response.status_code == 422
    body = response.json()
    print(body)
    assert body["request_id"] == response.headers["X-Request-ID"]
    UUID(body["request_id"])
    assert body["error"]["code"] == "MODEL_ETHERTYPE_UNSUPPORTED"
    assert body["error"]["component"] == "request_validation"
    assert body["error"]["failed_stage"] == "model_input_validation"
    assert body["error"]["failed_check"] == "model_supported_eth_type"
    assert body["error"]["retryable"] is False


def test_unsupported_ethertype_does_not_change_readiness(real_model_client):
    """Asegura que un rechazo de solicitud no degrade la readiness cacheada."""

    rejected = real_model_client.post(
        "/api/v1/classify",
        json={
            "packet_features": {
                "eth_type": 34525,
                "ip_proto": 6,
                "src_port": 49152,
                "dst_port": 443,
            }
        },
    )
    assert rejected.status_code == 422

    readiness = real_model_client.get("/health/ready")
    assert readiness.status_code == 200
    assert readiness.json()["ready"] is True
    assert rejected.json()["request_id"] == rejected.headers["X-Request-ID"]


def test_model_mode_rejection_skips_inference_and_logs_warning(real_model_client, caplog):
    """Comprueba que el rechazo por EtherType evite inferencia y registre warning."""

    acquire_spy = Mock(wraps=real_model_client.app.state.services.classifier_pool.acquire)
    real_model_client.app.state.services.classifier_pool.acquire = acquire_spy

    with caplog.at_level(logging.WARNING, logger="app.api.inference"):
        response = real_model_client.post(
            "/api/v1/classify",
            json={
                "packet_features": {
                    "eth_type": 34525,
                    "ip_proto": 6,
                    "src_port": 49152,
                    "dst_port": 443,
                }
            },
        )

    assert response.status_code == 422
    acquire_spy.assert_not_called()
    warning_records = [
        record for record in caplog.records if getattr(record, "event", None) == "classification_rejected"
    ]
    assert len(warning_records) == 1
    record = warning_records[0]
    assert record.levelname == "WARNING"
    assert record.error_code == "MODEL_ETHERTYPE_UNSUPPORTED"
    assert record.component == "request_validation"
    assert record.failed_stage == "model_input_validation"
    assert record.failed_check == "model_supported_eth_type"
    assert record.retryable is False
    assert record.classification_mode == "model"
    assert response.json()["request_id"] == response.headers["X-Request-ID"]


def test_real_model_classify_returns_503_when_startup_model_artifact_is_missing(
    monkeypatch,
    config_dir_path,
    policy_filename,
    deterministic_rule_filename,
):
    """Verifica que una ausencia de artefacto se refleje como `MODEL_NOT_READY`."""

    monkeypatch.setenv("CLASSIFICATION_MODE", "MODEL")
    monkeypatch.setenv("MODEL_DIR", str(REAL_MODEL_DIR))
    monkeypatch.setenv("CONFIG_DIR", config_dir_path)
    monkeypatch.setenv("MODEL_FILENAME", "missing-model.json")
    monkeypatch.setenv("MODEL_METADATA_FILENAME", "sdn_mpls_ml_model_meta.json")
    monkeypatch.setenv("POLICY_FILENAME", policy_filename)
    monkeypatch.setenv("DETERMINISTIC_RULE_FILENAME", deterministic_rule_filename)
    monkeypatch.setenv("MIN_TUNNEL_BANDWIDTH_KBPS", "10000")
    monkeypatch.setenv("MAX_TUNNEL_BANDWIDTH_KBPS", "100000")
    get_raw_settings.cache_clear()
    with TestClient(app) as test_client:
        response = test_client.post(
            "/api/v1/classify",
            json={
                "packet_features": {
                    "eth_type": 2048,
                    "ip_proto": 17,
                    "src_port": 53000,
                    "dst_port": 53,
                }
            },
        )
        assert response.status_code == 503
        body = response.json()
        assert body["request_id"] == response.headers["X-Request-ID"]
        assert body["error"]["code"] == "MODEL_NOT_READY"
        assert body["error"]["failed_stage"] == "artifact_checks"
        assert body["error"]["component"] == "model"
    get_raw_settings.cache_clear()
