"""Prueba el comportamiento basico del modo `DETERMINISTIC_TEST`.

Pasos:
- Inicializa la app sin modelo real.
- Verifica readiness correcta en modo deterministico.
- Comprueba clasificacion de puertos conocidos y fallback.
"""

from fastapi.testclient import TestClient

from app.config import get_raw_settings
from app.sdn_mpls_ml_main import app


def test_deterministic_mode_ready_and_classifies_known_port(
    monkeypatch,
    config_dir_path,
    policy_filename,
    deterministic_rule_filename,
):
    """Verifica readiness y clasificacion de un puerto bien conocido."""

    monkeypatch.setenv("CLASSIFICATION_MODE", "DETERMINISTIC_TEST")
    monkeypatch.setenv("CONFIG_DIR", config_dir_path)
    monkeypatch.setenv("POLICY_FILENAME", policy_filename)
    monkeypatch.setenv("DETERMINISTIC_RULE_FILENAME", deterministic_rule_filename)
    monkeypatch.setenv("MIN_TUNNEL_BANDWIDTH_KBPS", "10000")
    monkeypatch.setenv("MAX_TUNNEL_BANDWIDTH_KBPS", "100000")
    get_raw_settings.cache_clear()
    with TestClient(app) as client:
        ready_response = client.get("/health/ready")
        assert ready_response.status_code == 200
        assert ready_response.json()["classification_mode"] == "deterministic_test"

        classify_response = client.post(
            "/api/v1/classify",
            json={
                "packet_features": {
                    "eth_type": 2048,
                    "ip_proto": 6,
                    "src_port": 53000,
                    "dst_port": 53,
                }
            },
        )
        assert classify_response.status_code == 200
        body = classify_response.json()
        assert body["prediction"]["class_name"] == "DNS"
        assert body["policy"]["profile_name"] == "dns_tunnel_policy"
    get_raw_settings.cache_clear()


def test_deterministic_mode_unknown_port_falls_back_to_streaming(
    monkeypatch,
    config_dir_path,
    policy_filename,
    deterministic_rule_filename,
):
    """Comprueba que puertos no reconocidos caigan en la clase STREAMING."""

    monkeypatch.setenv("CLASSIFICATION_MODE", "DETERMINISTIC_TEST")
    monkeypatch.setenv("CONFIG_DIR", config_dir_path)
    monkeypatch.setenv("POLICY_FILENAME", policy_filename)
    monkeypatch.setenv("DETERMINISTIC_RULE_FILENAME", deterministic_rule_filename)
    monkeypatch.setenv("MIN_TUNNEL_BANDWIDTH_KBPS", "10000")
    monkeypatch.setenv("MAX_TUNNEL_BANDWIDTH_KBPS", "100000")
    get_raw_settings.cache_clear()
    with TestClient(app) as client:
        classify_response = client.post(
            "/api/v1/classify",
            json={
                "packet_features": {
                    "eth_type": 2048,
                    "ip_proto": 6,
                    "src_port": 55000,
                    "dst_port": 65000,
                }
            },
        )
        assert classify_response.status_code == 200
        assert classify_response.json()["prediction"]["class_name"] == "STREAMING"
    get_raw_settings.cache_clear()
