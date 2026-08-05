"""Ejercita la ruta completa de clasificacion en modo deterministico.

Pasos:
- Configura la aplicacion en `DETERMINISTIC_TEST`.
- Verifica respuestas completas, validaciones y fallback de readiness.
- Confirma que la restriccion IPv4 del modelo no aplica en este modo.
"""

from __future__ import annotations

import logging
from uuid import UUID

from fastapi.testclient import TestClient

from app.config import get_raw_settings
from app.main import app


def _configure_deterministic_env(monkeypatch, config_dir_path: str, policy_filename: str, deterministic_rule_filename: str) -> None:
    """Configura variables de entorno para pruebas de modo deterministico."""

    monkeypatch.setenv("CLASSIFICATION_MODE", "DETERMINISTIC_TEST")
    monkeypatch.setenv("CONFIG_DIR", config_dir_path)
    monkeypatch.setenv("POLICY_FILENAME", policy_filename)
    monkeypatch.setenv("DETERMINISTIC_RULE_FILENAME", deterministic_rule_filename)
    monkeypatch.setenv("MIN_TUNNEL_BANDWIDTH_KBPS", "10000")
    monkeypatch.setenv("MAX_TUNNEL_BANDWIDTH_KBPS", "100000")


def test_deterministic_test_classify_dns_returns_policy_mapping(
    monkeypatch,
    config_dir_path,
    policy_filename,
    deterministic_rule_filename,
):
    """Verifica la ruta completa de DNS con politica mapeada en modo simulador."""

    _configure_deterministic_env(monkeypatch, config_dir_path, policy_filename, deterministic_rule_filename)
    get_raw_settings.cache_clear()
    with TestClient(app) as client:
        response = client.post(
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
        assert body["model_name"] == "deterministic_test"
        assert body["prediction"]["class_id"] == 0
        assert body["prediction"]["class_name"] == "DNS"
        assert body["policy"]["profile_name"] == "dns_control"
        assert body["policy"]["dscp"] == 18
        assert body["policy"]["mpls_tc"] == 2
        assert body["policy"]["path_constraints"]["requested_bandwidth_kbps"] == 10000
        assert body["policy"]["policy_fallback"] is False
        assert body["policy"]["policy_fallback_reason"] is None
    get_raw_settings.cache_clear()


def test_deterministic_test_classify_returns_full_response_shape(
    monkeypatch,
    config_dir_path,
    policy_filename,
    deterministic_rule_filename,
):
    """Comprueba la forma completa de respuesta para un trafico HTTP simulado."""

    _configure_deterministic_env(monkeypatch, config_dir_path, policy_filename, deterministic_rule_filename)
    get_raw_settings.cache_clear()
    with TestClient(app) as client:
        response = client.post(
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
        assert body["model_name"] == "deterministic_test"
        assert body["prediction"] == {
            "class_id": 2,
            "class_name": "HTTP",
            "confidence": 1.0,
        }
        assert body["probabilities"] == {
            "DNS": 0.0,
            "FTP": 0.0,
            "HTTP": 1.0,
            "ICMP": 0.0,
            "NTP": 0.0,
            "SSH": 0.0,
            "STREAMING": 0.0,
        }
        assert body["policy"] == {
            "profile_name": "http_standard",
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
    get_raw_settings.cache_clear()


def test_deterministic_test_rejects_invalid_protocol_port_combination(
    monkeypatch,
    config_dir_path,
    policy_filename,
    deterministic_rule_filename,
):
    """Verifica que la validacion HTTP siga activa tambien en modo simulador."""

    _configure_deterministic_env(monkeypatch, config_dir_path, policy_filename, deterministic_rule_filename)
    get_raw_settings.cache_clear()
    with TestClient(app) as client:
        response = client.post(
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
    get_raw_settings.cache_clear()


def test_deterministic_test_accepts_ipv6_ethertype(
    monkeypatch,
    config_dir_path,
    policy_filename,
    deterministic_rule_filename,
):
    """Confirma que el simulador acepte un EtherType IPv6 valido."""

    _configure_deterministic_env(monkeypatch, config_dir_path, policy_filename, deterministic_rule_filename)
    get_raw_settings.cache_clear()
    with TestClient(app) as client:
        response = client.post(
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
        assert response.status_code == 200
        assert response.json()["request_id"] == response.headers["X-Request-ID"]
        assert response.json()["prediction"]["class_name"] == "HTTP"
    get_raw_settings.cache_clear()


def test_deterministic_test_non_ipv4_does_not_log_model_rejection_warning(
    monkeypatch,
    config_dir_path,
    policy_filename,
    deterministic_rule_filename,
    caplog,
):
    """Asegura que el modo deterministico no registre rechazo de modelo por EtherType."""

    _configure_deterministic_env(monkeypatch, config_dir_path, policy_filename, deterministic_rule_filename)
    get_raw_settings.cache_clear()
    with TestClient(app) as client:
        with caplog.at_level(logging.WARNING, logger="app.api.inference"):
            response = client.post(
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
        assert response.status_code == 200
        assert response.json()["request_id"] == response.headers["X-Request-ID"]
        assert not any(getattr(record, "event", None) == "classification_rejected" for record in caplog.records)
    get_raw_settings.cache_clear()


def test_deterministic_test_does_not_enforce_ipv4_only(
    monkeypatch,
    config_dir_path,
    policy_filename,
    deterministic_rule_filename,
):
    """Comprueba que otros EtherTypes validos sigan clasificando en modo simulador."""

    _configure_deterministic_env(monkeypatch, config_dir_path, policy_filename, deterministic_rule_filename)
    get_raw_settings.cache_clear()
    with TestClient(app) as client:
        response = client.post(
            "/api/v1/classify",
            json={
                "packet_features": {
                    "eth_type": 2054,
                    "ip_proto": 6,
                    "src_port": 49152,
                    "dst_port": 22,
                }
            },
        )
        assert response.status_code == 200
        assert response.json()["request_id"] == response.headers["X-Request-ID"]
        assert response.json()["prediction"]["class_name"] == "SSH"
    get_raw_settings.cache_clear()


def test_deterministic_test_rejects_ethertype_above_unsigned_16_bit_range(
    monkeypatch,
    config_dir_path,
    policy_filename,
    deterministic_rule_filename,
):
    """Verifica que la validacion universal de rango siga activa."""

    _configure_deterministic_env(monkeypatch, config_dir_path, policy_filename, deterministic_rule_filename)
    get_raw_settings.cache_clear()
    with TestClient(app) as client:
        response = client.post(
            "/api/v1/classify",
            json={
                "packet_features": {
                    "eth_type": 65536,
                    "ip_proto": 6,
                    "src_port": 49152,
                    "dst_port": 22,
                }
            },
        )
        assert response.status_code == 422
        assert response.json()["request_id"] == response.headers["X-Request-ID"]
        assert response.json()["error"]["code"] == "REQUEST_VALIDATION_FAILED"
    get_raw_settings.cache_clear()


def test_deterministic_test_returns_503_when_rule_artifact_is_missing(
    monkeypatch,
    config_dir_path,
    policy_filename,
):
    """Confirma que la falta de reglas deje al servicio no listo."""

    _configure_deterministic_env(monkeypatch, config_dir_path, policy_filename, "missing-rules.json")
    get_raw_settings.cache_clear()
    with TestClient(app) as client:
        response = client.post(
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
        assert body["error"]["component"] == "deterministic_rules"
    get_raw_settings.cache_clear()
