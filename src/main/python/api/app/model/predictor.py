"""Implementa la construccion del vector de entrada y la prediccion.

Pasos:
- Construye matrices NumPy en el orden exacto de features.
- Intenta usar `xgboost.DMatrix` cuando el booster real esta disponible.
- Normaliza y valida la salida probabilistica del clasificador.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import numpy as np

from app.sdn_mpls_ml_exceptions import ModelInferenceFailedError, ModelOutputInvalidError
from app.sdn_mpls_ml_messages import Messages
from app.model.metadata import ModelMetadata


@dataclass(slots=True)
class PredictionResult:
    """Agrupa la salida normalizada de una clasificacion.

    Pasos:
    - Conserva id, nombre y confianza de la clase ganadora.
    - Publica el mapa completo de probabilidades por nombre de clase.
    """

    class_id: int
    class_name: str
    confidence: float
    probabilities: dict[str, float]


def _load_xgboost_module() -> Any:
    """Importa XGBoost bajo demanda para desacoplar pruebas y runtime.

    Retorna:
    - Any: modulo `xgboost` importado.
    """

    import xgboost

    return xgboost


class Predictor:
    """Envuelve un booster y la metadata necesaria para predecir.

    Pasos:
    - Construye la matriz de entrada segun el orden de features.
    - Ejecuta el booster y normaliza su salida.
    - Decodifica la clase ganadora a su nombre logico.
    """

    def __init__(self, booster: Any, metadata: ModelMetadata, probability_tolerance: float = 0.001) -> None:
        """Inicializa el predictor con booster, metadata y tolerancia.

        Argumentos:
        - booster: objeto compatible con la interfaz de prediccion.
        - metadata: contrato del modelo para ordenar y decodificar.
        - probability_tolerance: tolerancia permitida para la suma de probabilidades.
        """

        self._booster = booster
        self._metadata = metadata
        self._probability_tolerance = probability_tolerance

    def build_feature_matrix(self, packet_features: dict[str, int]) -> np.ndarray:
        """Construye una matriz de features con orden fijo.

        Pasos:
        - Recorre el orden de features definido por la metadata.
        - Empaca los valores en una matriz `float32` de una sola fila.

        Argumentos:
        - packet_features: mapa de rasgos del paquete.

        Retorna:
        - np.ndarray: matriz de forma `(1, n_features)`.
        """

        return np.array(
            [[packet_features[feature_name] for feature_name in self._metadata.feature_order]],
            dtype=np.float32,
        )

    def predict(self, packet_features: dict[str, int]) -> PredictionResult:
        """Ejecuta una prediccion completa y la normaliza.

        Pasos:
        - Construye la matriz ordenada de entrada.
        - Intenta adaptarla a `DMatrix` cuando XGBoost esta presente.
        - Ejecuta el booster y valida la forma de salida.
        - Decodifica la clase ganadora y el mapa de probabilidades.

        Argumentos:
        - packet_features: rasgos de paquete ya validados externamente.

        Retorna:
        - PredictionResult: prediccion final normalizada.

        Excepciones:
        - ModelInferenceFailedError: si la inferencia falla.
        - ModelOutputInvalidError: si la salida no cumple el contrato.
        """

        feature_matrix = self.build_feature_matrix(packet_features)
        model_input: Any = feature_matrix
        try:
            xgboost = _load_xgboost_module()
            model_input = xgboost.DMatrix(feature_matrix)
        except Exception as exc:
            if self._booster.__class__.__module__.startswith("xgboost"):
                raise ModelInferenceFailedError() from exc
        try:
            raw_output = self._booster.predict(model_input)
        except Exception as exc:  # pragma: no cover
            raise ModelInferenceFailedError() from exc

        # La normalizacion garantiza una unica fila y siete probabilidades validas
        # antes de decodificar la clase dominante.
        probabilities = self._normalize_output(raw_output)
        class_id = int(np.argmax(probabilities))
        class_name = self._metadata.id_to_class[str(class_id)]
        probability_mapping = {
            self._metadata.id_to_class[str(index)]: float(value)
            for index, value in enumerate(probabilities)
        }
        return PredictionResult(
            class_id=class_id,
            class_name=class_name,
            confidence=float(probabilities[class_id]),
            probabilities=probability_mapping,
        )

    def _normalize_output(self, raw_output: np.ndarray) -> np.ndarray:
        """Normaliza y valida la salida numerica cruda del booster.

        Pasos:
        - Convierte la salida a `float32`.
        - Aplana una salida bidimensional de una sola fila.
        - Verifica cardinalidad, finitud, rango y suma de probabilidades.

        Argumentos:
        - raw_output: salida cruda devuelta por el booster.

        Retorna:
        - np.ndarray: vector unidimensional de probabilidades validas.

        Excepciones:
        - ModelOutputInvalidError: si la salida no respeta el contrato esperado.
        """

        values = np.asarray(raw_output, dtype=np.float32)
        if values.ndim == 2:
            if values.shape[0] != 1:
                raise ModelOutputInvalidError(Messages.MODEL_OUTPUT_MULTIPLE_ROWS)
            values = values[0]
        elif values.ndim != 1:
            raise ModelOutputInvalidError(Messages.MODEL_OUTPUT_SHAPE_UNSUPPORTED)

        if values.shape[0] != len(self._metadata.class_to_id):
            raise ModelOutputInvalidError(Messages.MODEL_OUTPUT_PROBABILITY_COUNT)
        if not np.isfinite(values).all():
            raise ModelOutputInvalidError(Messages.MODEL_OUTPUT_NONFINITE)
        if (values < 0.0).any() or (values > 1.0).any():
            raise ModelOutputInvalidError(Messages.MODEL_OUTPUT_BOUNDS)
        if abs(float(values.sum()) - 1.0) > self._probability_tolerance:
            raise ModelOutputInvalidError(Messages.MODEL_OUTPUT_SUM)
        return values
