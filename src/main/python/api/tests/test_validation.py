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

from app.config import (
    DEFAULT_CLASSIFIER_POOL_SIZE,
    DEFAULT_DETERMINISTIC_RULE_FILENAME,
    DEFAULT_LOG_DIRECTORY,
    DEFAULT_LOG_FILE_BACKUP_COUNT,
    DEFAULT_LOG_FILE_MAX_BYTES,
    DEFAULT_LOG_FILENAME,
    DEFAULT_POLICY_FILENAME,
    RawSettings,
    get_raw_settings,
)
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


def test_artifact_filenames_use_renamed_defaults_and_environment_overrides(monkeypatch):
    """Los nombres de artefactos usan los nuevos defaults y admiten overrides."""

    monkeypatch.delenv("POLICY_FILENAME", raising=False)
    monkeypatch.delenv("DETERMINISTIC_RULE_FILENAME", raising=False)
    defaults = RawSettings()
    assert defaults.policy_filename == DEFAULT_POLICY_FILENAME
    assert defaults.deterministic_rule_filename == DEFAULT_DETERMINISTIC_RULE_FILENAME

    monkeypatch.setenv("POLICY_FILENAME", "custom-policy.json")
    monkeypatch.setenv("DETERMINISTIC_RULE_FILENAME", "custom-rules.json")
    overrides = RawSettings()
    assert overrides.policy_filename == "custom-policy.json"
    assert overrides.deterministic_rule_filename == "custom-rules.json"


def test_logging_settings_use_environment_overrides(monkeypatch):
    """La configuracion de logs se carga exclusivamente desde app.config."""

    for variable in ("LOG_DIRECTORY", "LOG_FILENAME", "LOG_FILE_MAX_BYTES", "LOG_FILE_BACKUP_COUNT"):
        monkeypatch.delenv(variable, raising=False)
    defaults = RawSettings()
    assert defaults.log_directory == DEFAULT_LOG_DIRECTORY
    assert defaults.log_filename == DEFAULT_LOG_FILENAME
    assert defaults.log_file_max_bytes == DEFAULT_LOG_FILE_MAX_BYTES
    assert defaults.log_file_backup_count == DEFAULT_LOG_FILE_BACKUP_COUNT

    monkeypatch.setenv("LOG_DIRECTORY", "/tmp/application-logs")
    monkeypatch.setenv("LOG_FILENAME", "custom.log")
    monkeypatch.setenv("LOG_FILE_MAX_BYTES", "2048")
    monkeypatch.setenv("LOG_FILE_BACKUP_COUNT", "2")
    overrides = RawSettings()
    assert overrides.log_directory == "/tmp/application-logs"
    assert overrides.log_filename == "custom.log"
    assert overrides.log_file_max_bytes == 2048
    assert overrides.log_file_backup_count == 2


def test_classifier_pool_settings_use_environment_overrides(monkeypatch):
    """El tamano del pool se define desde configuracion centralizada."""

    monkeypatch.delenv("CLASSIFIER_POOL_SIZE", raising=False)
    defaults = RawSettings()
    assert defaults.classifier_pool_size == DEFAULT_CLASSIFIER_POOL_SIZE

    monkeypatch.setenv("CLASSIFIER_POOL_SIZE", "7")
    overrides = RawSettings()
    assert overrides.classifier_pool_size == "7"


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
    assert services.classifier_pool is not None
    assert services.classifier_pool.capacity == 5
    assert services.inference_thread_limiter is not None
    assert services.policy_mapper.default_policy.path_constraints.requested_bandwidth_kbps == 0
    get_raw_settings.cache_clear()


def test_invalid_classifier_pool_size_fails_readiness(
    monkeypatch,
    model_dir,
    config_dir_path,
    policy_filename,
    deterministic_rule_filename,
    dummy_booster_class,
):
    """Comprueba que el pool size fuera de rango falle en preflight."""

    _patch_dummy_booster(monkeypatch, dummy_booster_class)
    _configure_model_env(
        monkeypatch,
        model_dir,
        Path(config_dir_path) / policy_filename,
        str(Path(config_dir_path) / deterministic_rule_filename),
    )
    monkeypatch.setenv("CLASSIFIER_POOL_SIZE", "0")
    get_raw_settings.cache_clear()
    services = build_services()
    assert services.readiness.error_code == "CONFIGURATION_INVALID"
    assert services.readiness.failed_check == "classifier_pool_size_range"
    get_raw_settings.cache_clear()


def test_partial_classifier_pool_initialization_is_not_published(
    monkeypatch,
    model_dir,
    config_dir_path,
    policy_filename,
    deterministic_rule_filename,
    dummy_booster_class,
):
    """Verifica que un fallo intermedio no publique un pool parcial."""

    from app import dependencies as dependency_module
    from app.model.predictor import PredictionResult

    _patch_dummy_booster(monkeypatch, dummy_booster_class)
    _configure_model_env(
        monkeypatch,
        model_dir,
        Path(config_dir_path) / policy_filename,
        str(Path(config_dir_path) / deterministic_rule_filename),
    )
    get_raw_settings.cache_clear()

    class StubClassifier:
        def predict(self, _packet_features: dict[str, int]) -> PredictionResult:
            return PredictionResult(
                class_id=0,
                class_name="DNS",
                confidence=1.0,
                probabilities={
                    "DNS": 1.0,
                    "FTP": 0.0,
                    "HTTP": 0.0,
                    "ICMP": 0.0,
                    "NTP": 0.0,
                    "SSH": 0.0,
                    "STREAMING": 0.0,
                },
            )

    build_count = {"value": 0}

    def failing_builder(*args, **kwargs):
        del args, kwargs
        build_count["value"] += 1
        if build_count["value"] == 4:
            raise dependency_module._runtime_error(
                code="MODEL_LOAD_FAILED",
                message="controlled-test-failure",
                failed_check="booster_load_model",
                retryable=False,
            )
        return StubClassifier()

    monkeypatch.setattr(dependency_module, "_build_single_classifier", failing_builder)
    services = build_services()
    assert services.readiness.ready is False
    assert services.classifier_pool is None
    assert services.inference_thread_limiter is None
    assert services.readiness.failed_stage == "runtime_model_compatibility"
    get_raw_settings.cache_clear()
