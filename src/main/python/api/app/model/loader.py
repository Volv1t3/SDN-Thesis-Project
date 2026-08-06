"""Contiene un loader historico para bundle de modelo y predictor.

Pasos:
- Carga metadata y artefacto XGBoost desde un directorio dado.
- Ejecuta una auto-prueba sintetica basica del predictor resultante.

Notas:
- El flujo principal actual de startup vive en `app.dependencies`.
- Este modulo se conserva como helper aislado reutilizable.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np

from app.sdn_mpls_ml_messages import Messages
from app.model.metadata import ModelMetadata, load_metadata
from app.model.predictor import Predictor


@dataclass(slots=True)
class ModelBundle:
    """Agrupa metadata validada y predictor inicializado.

    Pasos:
    - Conserva el contrato del modelo.
    - Expone el predictor listo para clasificacion.
    """

    metadata: ModelMetadata
    predictor: Predictor


def _load_xgboost_module() -> Any:
    """Importa la libreria XGBoost bajo demanda.

    Retorna:
    - Any: modulo importado de XGBoost.
    """

    import xgboost

    return xgboost


def load_model_bundle(model_dir: str, model_filename: str, metadata_filename: str, probability_tolerance: float) -> ModelBundle:
    """Carga un bundle completo de modelo y predictor.

    Pasos:
    - Resuelve rutas de modelo y metadata dentro del directorio indicado.
    - Valida la existencia de ambos artefactos.
    - Carga la metadata y el booster XGBoost.
    - Construye un predictor y ejecuta una auto-prueba sintetica.

    Argumentos:
    - model_dir: directorio donde viven los artefactos del modelo.
    - model_filename: nombre del artefacto del booster.
    - metadata_filename: nombre del archivo de metadata.
    - probability_tolerance: tolerancia para la suma de probabilidades.

    Retorna:
    - ModelBundle: bundle con metadata y predictor listos.

    Excepciones:
    - ValueError: si faltan artefactos o la auto-prueba produce salida invalida.
    """

    model_path = Path(model_dir) / model_filename
    metadata_path = Path(model_dir) / metadata_filename

    if not model_path.exists():
        raise ValueError(Messages.MODEL_FILE_MISSING.format(path=model_path))
    if not metadata_path.exists():
        raise ValueError(Messages.METADATA_FILE_MISSING.format(path=metadata_path))

    metadata = load_metadata(metadata_path)
    xgboost = _load_xgboost_module()
    booster = xgboost.Booster()
    booster.load_model(model_path)
    predictor = Predictor(booster=booster, metadata=metadata, probability_tolerance=probability_tolerance)

    synthetic_input = {
        "eth_type": 2048,
        "ip_proto": 6,
        "src_port": 49152,
        "dst_port": 443,
    }
    result = predictor.predict(synthetic_input)
    if len(result.probabilities) != len(metadata.class_to_id):
        raise ValueError(Messages.MODEL_BUNDLE_SYNTHETIC_PROBABILITY_COUNT)
    if not np.isfinite(np.array(list(result.probabilities.values()), dtype=np.float32)).all():
        raise ValueError(Messages.MODEL_BUNDLE_SYNTHETIC_NONFINITE)

    return ModelBundle(metadata=metadata, predictor=predictor)
