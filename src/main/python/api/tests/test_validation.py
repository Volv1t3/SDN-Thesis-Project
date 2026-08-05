"""Prueba las validaciones de startup y readiness cacheada.

Pasos:
- Configura entorno para distintos escenarios de arranque.
- Reemplaza XGBoost por un booster dummy cuando conviene.
- Verifica codigos y etapas de error de cada fallo controlado.
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from app.config import get_raw_settings
from app.dependencies import build_services


def _configure_model_env(monkeypatch, model_dir: Path, policy_path: Path, deterministic_rule_path: str) -> None:
    """Configura variables de entorno para pruebas de startup en modo `MODEL`."""

    monkeypatch.setenv("CLASSIFICATION_MODE", "MODEL")
    monkeypatch.setenv("MODEL_DIR", str(model_dir))
    monkeypatch.setenv("CONFIG_DIR", str(policy_path.parent))
    monkeypatch.setenv("MODEL_FILENAME", "model.json")
    monkeypatch.setenv("MODEL_METADATA_FILENAME", "model_meta.json")
    monkeypatch.setenv("POLICY_FILENAME", policy_path.name)
    monkeypatch.setenv("DETERMINISTIC_RULE_FILENAME", Path(deterministic_rule_path).name)
    monkeypatch.setenv("MIN_TUNNEL_BANDWIDTH_KBPS", "10000")
    monkeypatch.setenv("MAX_TUNNEL_BANDWIDTH_KBPS", "100000")


def _patch_dummy_booster(monkeypatch, dummy_booster_class) -> None:
    """Sustituye `xgboost.Booster` por el dummy compartido de pruebas."""

    import xgboost

    dummy_booster_class.reset()
    monkeypatch.setattr(xgboost, "Booster", dummy_booster_class)


def test_invalid_port_fails_readiness(
    monkeypatch,
    model_dir,
    config_dir_path,
    policy_filename,
    deterministic_rule_filename,
    dummy_booster_class,
):
    """Verifica que un puerto invalido deje la app en `not_ready`."""

    _patch_dummy_booster(monkeypatch, dummy_booster_class)
    _configure_model_env(monkeypatch, model_dir, Path(config_dir_path) / policy_filename, str(Path(config_dir_path) / deterministic_rule_filename))
    monkeypatch.setenv("PORT", "70000")
    get_raw_settings.cache_clear()
    services = build_services()
    assert services.readiness.ready is False
    assert services.readiness.error_code == "CONFIGURATION_INVALID"
    assert services.readiness.failed_check == "port_range"
    get_raw_settings.cache_clear()


def test_invalid_log_level_fails_readiness(
    monkeypatch,
    model_dir,
    config_dir_path,
    policy_filename,
    deterministic_rule_filename,
    dummy_booster_class,
):
    """Comprueba que un log level no soportado falle en preflight."""

    _patch_dummy_booster(monkeypatch, dummy_booster_class)
    _configure_model_env(monkeypatch, model_dir, Path(config_dir_path) / policy_filename, str(Path(config_dir_path) / deterministic_rule_filename))
    monkeypatch.setenv("LOG_LEVEL", "TRACE")
    get_raw_settings.cache_clear()
    services = build_services()
    assert services.readiness.error_code == "CONFIGURATION_INVALID"
    assert services.readiness.failed_check == "log_level_supported"
    get_raw_settings.cache_clear()


def test_invalid_confidence_threshold_fails_readiness(
    monkeypatch,
    model_dir,
    config_dir_path,
    policy_filename,
    deterministic_rule_filename,
    dummy_booster_class,
):
    """Asegura que un umbral de confianza invalido rompa la configuracion."""

    _patch_dummy_booster(monkeypatch, dummy_booster_class)
    _configure_model_env(monkeypatch, model_dir, Path(config_dir_path) / policy_filename, str(Path(config_dir_path) / deterministic_rule_filename))
    monkeypatch.setenv("MIN_POLICY_CONFIDENCE", "1.5")
    get_raw_settings.cache_clear()
    services = build_services()
    assert services.readiness.error_code == "CONFIGURATION_INVALID"
    assert services.readiness.failed_check == "min_policy_confidence_range"
    get_raw_settings.cache_clear()


def test_unsupported_classification_mode_fails_readiness(
    monkeypatch,
    model_dir,
    config_dir_path,
    policy_filename,
    deterministic_rule_filename,
    dummy_booster_class,
):
    """Verifica que modos de clasificacion desconocidos sean rechazados."""

    _patch_dummy_booster(monkeypatch, dummy_booster_class)
    _configure_model_env(monkeypatch, model_dir, Path(config_dir_path) / policy_filename, str(Path(config_dir_path) / deterministic_rule_filename))
    monkeypatch.setenv("CLASSIFICATION_MODE", "LEGACY")
    get_raw_settings.cache_clear()
    services = build_services()
    assert services.readiness.error_code == "CONFIGURATION_INVALID"
    assert services.readiness.failed_check == "classification_mode_supported"
    get_raw_settings.cache_clear()


def test_missing_model_filename_in_model_mode_fails_readiness(
    monkeypatch,
    model_dir,
    config_dir_path,
    policy_filename,
    deterministic_rule_filename,
    dummy_booster_class,
):
    """Comprueba que falte `MODEL_FILENAME` en modo `MODEL` falle readiness."""

    _patch_dummy_booster(monkeypatch, dummy_booster_class)
    _configure_model_env(monkeypatch, model_dir, Path(config_dir_path) / policy_filename, str(Path(config_dir_path) / deterministic_rule_filename))
    monkeypatch.setenv("MODEL_FILENAME", "")
    get_raw_settings.cache_clear()
    services = build_services()
    assert services.readiness.error_code == "CONFIGURATION_INVALID"
    assert services.readiness.failed_check == "model_filename_configured"
    get_raw_settings.cache_clear()


def test_missing_policy_class_fails_readiness(
    monkeypatch,
    model_dir,
    metadata_payload,
    tmp_path,
    config_dir_path,
    deterministic_rule_filename,
    dummy_booster_class,
):
    """Verifica que una politica sin todas las clases obligatorias falle."""

    _patch_dummy_booster(monkeypatch, dummy_booster_class)
    policy_path = tmp_path / "policy.json"
    payload = {
        "schema_version": "1.0",
        "default_profile": {
            "profile_name": "best_effort",
            "dscp": 0,
            "mpls_tc": 0,
            "path_constraints": {
                "requested_bandwidth_kbps": 0,
                "setup_priority": 7,
                "hold_priority": 7,
            },
        },
        "class_policies": {
            class_name: {
                "profile_name": class_name.lower(),
                "dscp": 10,
                "mpls_tc": 1,
                "path_constraints": {
                    "requested_bandwidth_kbps": 10000,
                    "setup_priority": 4,
                    "hold_priority": 4,
                },
            }
            for class_name in metadata_payload["class_to_id"]
            if class_name != "STREAMING"
        },
    }
    policy_path.write_text(json.dumps(payload), encoding="utf-8")
    _configure_model_env(monkeypatch, model_dir, policy_path, str(Path(config_dir_path) / deterministic_rule_filename))
    get_raw_settings.cache_clear()
    services = build_services()
    assert services.readiness.error_code == "POLICY_MAP_INCOMPLETE"
    assert services.readiness.failed_stage == "complete_policy_map_validation"
    get_raw_settings.cache_clear()


def test_bandwidth_below_demonstrator_minimum_fails_readiness(
    monkeypatch,
    model_dir,
    tmp_path,
    config_dir_path,
    policy_filename,
    deterministic_rule_filename,
    dummy_booster_class,
):
    """Comprueba que el ancho de banda por debajo del minimo sea rechazado."""

    _patch_dummy_booster(monkeypatch, dummy_booster_class)
    policy_path = tmp_path / "policy.json"
    payload = json.loads((Path(config_dir_path) / policy_filename).read_text(encoding="utf-8"))
    payload["class_policies"]["HTTP"]["path_constraints"]["requested_bandwidth_kbps"] = 9000
    policy_path.write_text(json.dumps(payload), encoding="utf-8")
    _configure_model_env(monkeypatch, model_dir, policy_path, str(Path(config_dir_path) / deterministic_rule_filename))
    get_raw_settings.cache_clear()
    services = build_services()
    assert services.readiness.error_code == "POLICY_BANDWIDTH_BELOW_MINIMUM"
    assert services.readiness.failed_stage == "complete_policy_map_validation"
    get_raw_settings.cache_clear()


def test_default_bandwidth_zero_remains_valid(
    monkeypatch,
    model_dir,
    config_dir_path,
    policy_filename,
    deterministic_rule_filename,
    dummy_booster_class,
):
    """Asegura que el perfil por defecto conserve banda cero valida."""

    _patch_dummy_booster(monkeypatch, dummy_booster_class)
    _configure_model_env(monkeypatch, model_dir, Path(config_dir_path) / policy_filename, str(Path(config_dir_path) / deterministic_rule_filename))
    get_raw_settings.cache_clear()
    services = build_services()
    assert services.readiness.ready is True
    assert services.policy_mapper.default_policy.path_constraints.requested_bandwidth_kbps == 0
    get_raw_settings.cache_clear()
