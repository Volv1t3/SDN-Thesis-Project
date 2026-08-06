"""Define fixtures compartidos y doubles usados por la suite de pruebas.

Pasos:
- Inserta el proyecto en `sys.path` para imports directos.
- Publica payloads y rutas reutilizables para tests.
- Reemplaza XGBoost por un booster dummy en pruebas aisladas.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
import pytest
from fastapi.testclient import TestClient

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from app.sdn_mpls_ml_config import get_raw_settings  # noqa: E402
from app.sdn_mpls_ml_main import app  # noqa: E402
from app.model.sdn_mpls_ml_metadata import EXPECTED_CLASS_TO_ID, EXPECTED_MODEL_NAME  # noqa: E402


class DummyBooster:
    """Simula el comportamiento minimo de un booster XGBoost.

    Pasos:
    - Expone salida de prediccion configurable a nivel de clase.
    - Cuenta llamadas a carga y prediccion para assertions.
    - Emula metodos inspeccionados por el startup validation.
    """

    predict_output = np.array([[0.1, 0.05, 0.2, 0.1, 0.05, 0.1, 0.4]], dtype=np.float32)
    num_features_value = 4
    objective_name = "multi:softprob"
    num_class_value = 7
    predict_call_count = 0
    load_call_count = 0

    @classmethod
    def reset(cls) -> None:
        """Restablece el estado compartido del booster dummy.

        Pasos:
        - Restaura probabilidades y metadatos por defecto.
        - Reinicia contadores de carga y prediccion.
        """

        cls.predict_output = np.array([[0.1, 0.05, 0.2, 0.1, 0.05, 0.1, 0.4]], dtype=np.float32)
        cls.num_features_value = 4
        cls.objective_name = "multi:softprob"
        cls.num_class_value = 7
        cls.predict_call_count = 0
        cls.load_call_count = 0

    def load_model(self, path):  # pragma: no cover
        """Registra la carga del artefacto dummy."""

        type(self).load_call_count += 1
        self.path = path

    def num_features(self) -> int:
        """Devuelve el numero de features configurado para la prueba."""

        return type(self).num_features_value

    def save_config(self) -> str:
        """Devuelve una configuracion JSON similar a la de XGBoost."""

        return json.dumps(
            {
                "learner": {
                    "objective": {"name": type(self).objective_name},
                    "learner_model_param": {"num_class": str(type(self).num_class_value)},
                    "gradient_booster": {"name": "gbtree"},
                }
            }
        )

    def predict(self, matrix):
        """Devuelve la salida configurada y cuenta la invocacion."""

        type(self).predict_call_count += 1
        return type(self).predict_output


@pytest.fixture
def metadata_payload():
    """Entrega un payload de metadata valido para pruebas locales.

    Retorna:
    - dict[str, object]: metadata compatible con el contrato del modelo.
    """

    return {
        "schema_version": "1.0",
        "model_name": EXPECTED_MODEL_NAME,
        "target_name": "Category",
        "model_format": "xgboost_booster_json",
        "feature_order": ["eth_type", "ip_proto", "src_port", "dst_port"],
        "feature_types": {
            "eth_type": "integer",
            "ip_proto": "integer",
            "src_port": "integer",
            "dst_port": "integer",
        },
        "class_to_id": EXPECTED_CLASS_TO_ID,
        "id_to_class": {str(value): key for key, value in EXPECTED_CLASS_TO_ID.items()},
    }


@pytest.fixture
def model_dir(tmp_path: Path, metadata_payload):
    """Crea un directorio temporal con artefactos de modelo dummy.

    Pasos:
    - Escribe metadata valida en JSON.
    - Escribe un artefacto de modelo placeholder.

    Retorna:
    - Path: directorio temporal listo para ser usado por settings.
    """

    (tmp_path / "model_meta.json").write_text(json.dumps(metadata_payload), encoding="utf-8")
    (tmp_path / "model.json").write_text("{}", encoding="utf-8")
    return tmp_path


@pytest.fixture
def config_dir_path() -> str:
    """Devuelve la ruta del directorio `configs` del proyecto."""

    return str(PROJECT_ROOT / "configs")


@pytest.fixture
def policy_filename() -> str:
    """Devuelve el nombre del archivo de politica base."""

    return "sdn_mpls_ml_traffic_class_to_policy_mapping.json"


@pytest.fixture
def deterministic_rule_filename() -> str:
    """Devuelve el nombre del archivo base de reglas deterministicas."""

    return "sdn_mpls_ml_traffic_class_deterministic_rules.json"


@pytest.fixture
def dummy_booster_class():
    """Expone la clase del booster dummy para monkeypatching."""

    return DummyBooster


@pytest.fixture
def client(monkeypatch, model_dir, config_dir_path, policy_filename, deterministic_rule_filename, dummy_booster_class):
    """Crea un cliente FastAPI configurado en modo `MODEL` con booster dummy.

    Pasos:
    - Configura variables de entorno requeridas por la app.
    - Sustituye `xgboost.Booster` por `DummyBooster`.
    - Limpia la cache de settings antes y despues de la prueba.

    Retorna:
    - Iterator[TestClient]: cliente HTTP listo para ejercitar la API.
    """

    import xgboost

    dummy_booster_class.reset()
    monkeypatch.setenv("CLASSIFICATION_MODE", "MODEL")
    monkeypatch.setenv("MODEL_DIR", str(model_dir))
    monkeypatch.setenv("CONFIG_DIR", config_dir_path)
    monkeypatch.setenv("MODEL_FILENAME", "model.json")
    monkeypatch.setenv("MODEL_METADATA_FILENAME", "model_meta.json")
    monkeypatch.setenv("POLICY_FILENAME", policy_filename)
    monkeypatch.setenv("DETERMINISTIC_RULE_FILENAME", deterministic_rule_filename)
    monkeypatch.setenv("MIN_TUNNEL_BANDWIDTH_KBPS", "10000")
    monkeypatch.setenv("MAX_TUNNEL_BANDWIDTH_KBPS", "100000")
    get_raw_settings.cache_clear()
    monkeypatch.setattr(xgboost, "Booster", dummy_booster_class)
    with TestClient(app) as test_client:
        yield test_client
    get_raw_settings.cache_clear()
