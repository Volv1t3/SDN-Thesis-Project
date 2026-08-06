"""Prueba la validacion de metadata a traves del flujo de startup.

Pasos:
- Carga metadata valida y diferentes variantes incompatibles.
- Verifica orden de features, clases y version de schema.
- Confirma rechazo de JSON invalido y campos inesperados.
"""

import json

import pytest

from app.sdn_mpls_ml_dependencies import _load_metadata_for_startup
from app.model.sdn_mpls_ml_metadata import EXPECTED_MODEL_NAME
from app.sdn_mpls_ml_readiness import StartupValidationError


def _load_startup_metadata(path):
    """Carga metadata mediante el loader usado por el startup de la aplicacion."""

    return _load_metadata_for_startup(path)


def test_valid_metadata_loads(tmp_path, metadata_payload):
    """Verifica que una metadata correcta pueda cargarse sin error."""

    path = tmp_path / "model_meta.json"
    path.write_text(json.dumps(metadata_payload), encoding="utf-8")
    metadata = _load_startup_metadata(path)
    assert metadata.model_name == EXPECTED_MODEL_NAME


def test_missing_feature_order_fails(tmp_path, metadata_payload):
    """Comprueba que falte `feature_order` invalide la metadata."""

    path = tmp_path / "model_meta.json"
    payload = dict(metadata_payload)
    payload.pop("feature_order")
    path.write_text(json.dumps(payload), encoding="utf-8")
    with pytest.raises(StartupValidationError, match="metadatos"):
        _load_startup_metadata(path)


def test_wrong_feature_order_fails(tmp_path, metadata_payload):
    """Asegura que un orden de features distinto al contrato sea rechazado."""

    path = tmp_path / "model_meta.json"
    payload = dict(metadata_payload)
    payload["feature_order"] = ["ip_proto", "eth_type", "src_port", "dst_port"]
    path.write_text(json.dumps(payload), encoding="utf-8")
    with pytest.raises(StartupValidationError, match="metadatos"):
        _load_startup_metadata(path)


def test_duplicate_class_ids_fail(tmp_path, metadata_payload):
    """Verifica que ids de clase duplicados fallen la validacion."""

    path = tmp_path / "model_meta.json"
    payload = dict(metadata_payload)
    payload["class_to_id"] = dict(payload["class_to_id"])
    payload["class_to_id"]["FTP"] = 0
    path.write_text(json.dumps(payload), encoding="utf-8")
    with pytest.raises(StartupValidationError, match="metadatos"):
        _load_startup_metadata(path)


def test_missing_reverse_mapping_fails(tmp_path, metadata_payload):
    """Comprueba que el mapa inverso incompleto sea rechazado."""

    path = tmp_path / "model_meta.json"
    payload = dict(metadata_payload)
    payload["id_to_class"] = dict(payload["id_to_class"])
    payload["id_to_class"].pop("6")
    path.write_text(json.dumps(payload), encoding="utf-8")
    with pytest.raises(StartupValidationError, match="metadatos"):
        _load_startup_metadata(path)


def test_non_contiguous_class_ids_fail(tmp_path, metadata_payload):
    """Verifica que ids no contiguos rompan el contrato del modelo."""

    path = tmp_path / "model_meta.json"
    payload = dict(metadata_payload)
    payload["class_to_id"] = dict(payload["class_to_id"])
    payload["class_to_id"]["STREAMING"] = 8
    path.write_text(json.dumps(payload), encoding="utf-8")
    with pytest.raises(StartupValidationError, match="metadatos"):
        _load_startup_metadata(path)


def test_unknown_class_fails(tmp_path, metadata_payload):
    """Comprueba que una clase desconocida invalide la metadata."""

    path = tmp_path / "model_meta.json"
    payload = dict(metadata_payload)
    payload["class_to_id"] = dict(payload["class_to_id"])
    payload["class_to_id"]["VIDEO"] = payload["class_to_id"].pop("STREAMING")
    payload["id_to_class"] = {str(value): key for key, value in payload["class_to_id"].items()}
    path.write_text(json.dumps(payload), encoding="utf-8")
    with pytest.raises(StartupValidationError, match="metadatos"):
        _load_startup_metadata(path)


def test_unsupported_schema_version_fails(tmp_path, metadata_payload):
    """Verifica que solo se acepte la version `1.0` del schema."""

    path = tmp_path / "model_meta.json"
    payload = dict(metadata_payload)
    payload["schema_version"] = "2.0"
    path.write_text(json.dumps(payload), encoding="utf-8")
    with pytest.raises(StartupValidationError, match="metadatos"):
        _load_startup_metadata(path)


def test_unsupported_feature_fails(tmp_path, metadata_payload):
    """Asegura que features fuera del contrato sean rechazadas."""

    path = tmp_path / "model_meta.json"
    payload = dict(metadata_payload)
    payload["feature_types"] = dict(payload["feature_types"])
    payload["feature_types"]["ttl"] = "integer"
    path.write_text(json.dumps(payload), encoding="utf-8")
    with pytest.raises(StartupValidationError, match="metadatos"):
        _load_startup_metadata(path)


def test_invalid_json_fails(tmp_path):
    """Comprueba que un JSON malformado produzca un error de startup tipado."""

    path = tmp_path / "model_meta.json"
    path.write_text("{", encoding="utf-8")
    with pytest.raises(StartupValidationError, match="JSON valido"):
        _load_startup_metadata(path)
