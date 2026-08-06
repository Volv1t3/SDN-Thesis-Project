"""Prueba la normalizacion y decodificacion del predictor.

Pasos:
- Verifica el orden exacto del vector de entrada.
- Comprueba clase ganadora, confianza y probabilidades.
- Rechaza salidas numericamente incompatibles.
"""

import numpy as np
import pytest

from app.sdn_mpls_ml_exceptions import ModelOutputInvalidError
from app.model.metadata import ModelMetadata
from app.model.predictor import Predictor


class BoosterStub:
    """Stub minimo para controlar la salida cruda del predictor.

    Pasos:
    - Conserva la salida deseada para la prueba.
    - Devuelve esa salida cuando `predict` es invocado.
    """

    def __init__(self, output):
        """Guarda la salida sintetica que devolvera el stub."""

        self.output = output

    def predict(self, matrix):
        """Devuelve la salida configurada sin transformaciones."""

        return self.output


def test_feature_vector_order_exact(metadata_payload):
    """Verifica que el predictor respete el orden de features de la metadata."""

    metadata = ModelMetadata.model_validate(metadata_payload)
    predictor = Predictor(booster=BoosterStub([[0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.4]]), metadata=metadata)
    matrix = predictor.build_feature_matrix({"dst_port": 4, "src_port": 3, "ip_proto": 2, "eth_type": 1})
    assert matrix.tolist() == [[1.0, 2.0, 3.0, 4.0]]


def test_argmax_confidence_and_probabilities(metadata_payload):
    """Comprueba la decodificacion de clase ganadora y probabilidades completas."""

    metadata = ModelMetadata.model_validate(metadata_payload)
    predictor = Predictor(booster=BoosterStub([[0.02, 0.03, 0.04, 0.05, 0.06, 0.1, 0.7]]), metadata=metadata)
    result = predictor.predict({"eth_type": 2048, "ip_proto": 6, "src_port": 12345, "dst_port": 443})
    assert result.class_id == 6
    assert result.class_name == "STREAMING"
    assert result.confidence == pytest.approx(0.7)
    assert len(result.probabilities) == 7
    assert result.probabilities["DNS"] == pytest.approx(0.02)


@pytest.mark.parametrize(
    "output",
    [
        [[np.nan, 0.1, 0.1, 0.1, 0.1, 0.1, 0.4]],
        [[np.inf, 0.1, 0.1, 0.1, 0.1, 0.1, 0.4]],
        [[0.5, 0.5]],
        [[0.2, 0.2, 0.2, 0.2, 0.2, 0.1, 0.1]],
        [[1.2, -0.2, 0.0, 0.0, 0.0, 0.0, 0.0]],
    ],
)
def test_invalid_output_fails(metadata_payload, output):
    """Asegura que salidas incompatibles disparen `ModelOutputInvalidError`."""

    metadata = ModelMetadata.model_validate(metadata_payload)
    predictor = Predictor(booster=BoosterStub(output), metadata=metadata)
    with pytest.raises(ModelOutputInvalidError):
        predictor.predict({"eth_type": 2048, "ip_proto": 6, "src_port": 12345, "dst_port": 443})
