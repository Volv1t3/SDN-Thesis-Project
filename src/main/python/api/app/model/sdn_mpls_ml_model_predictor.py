"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370

Archivo que define el struct organizado de los datos agrupados resultantes de una prediccion de clase de trafico, y
que define la implementacion de la clase TrafficClassifier pero para el modelo real XGBoost
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any
import numpy as np

from app.sdn_mpls_ml_exceptions import ModelInferenceFailedError, ModelOutputInvalidError
from app.sdn_mpls_ml_messages import Messages
from app.model.sdn_mpls_ml_metadata import ModelMetadata


@dataclass(slots=True)
class PredictionResult:
    """
    Record estructurado que define el resultado final de una prediccion de clase de trafico, incluye la clase, su id
    la confianza en la prediccion y la probabilidad en todas las otras clases para validacion externa.
    """

    class_id: int
    class_name: str
    confidence: float
    probabilities: dict[str, float]


def _load_xgboost_module() -> Any:
    """
    Importa XGBoost bajo demanda para desacoplar pruebas y runtime.

    Returns:
        Any: modulo `xgboost` importado.
    """
    import xgboost

    return xgboost


class Predictor:
    """
    Clase que permite ejecutar clasificaciones basdas en un modelo (Booster) de XGBoost y la metadata de este modelo. Esta clase
    es una instancia que en runtime se considera parte de la especializacion de sdn_mpls_ml_protocols.py que le permite
    ser usada en una estructura polimorfica durante la ejecucion del servicio.
    """

    def __init__(self, booster: Any, metadata: ModelMetadata, probability_tolerance: float = 0.001) -> None:
        """
        Inicializa el predictor con booster, metadata y tolerancia.

        Args:
            booster: objeto compatible con la interfaz de prediccion.
            metadata: contrato del modelo para ordenar y decodificar.
            probability_tolerance: tolerancia permitida para la suma de probabilidades.
        """

        self._booster = booster
        self._metadata = metadata
        self._probability_tolerance = probability_tolerance

    def build_feature_matrix(self, packet_features: dict[str, int]) -> np.ndarray:
        """
        Construye una matriz de features con orden fijo.

        Pasos:
        - Recorre el orden de features definido por la metadata.
        - Empaca los valores en una matriz `float32` de una sola fila.

        Args:
            packet_features: mapa de rasgos del paquete.

        Returns:
            np.ndarray: matriz de forma `(1, n_features)`.
        """

        return np.array(
            [[packet_features[feature_name] for feature_name in self._metadata.feature_order]],
            dtype=np.float32,
        )

    def predict(self, packet_features: dict[str, int]) -> PredictionResult:
        """
        Ejecuta una prediccion completa y la normaliza.

        Pasos:
        - Construye la matriz ordenada de entrada.
        - Intenta adaptarla a `DMatrix` cuando XGBoost esta presente.
        - Ejecuta el booster y valida la forma de salida.
        - Decodifica la clase ganadora y el mapa de probabilidades.

        Args:
            packet_features: rasgos de paquete ya validados externamente.

        Returns:
            PredictionResult: prediccion final normalizada.

        Raises:
            ModelInferenceFailedError: si la inferencia falla.
            ModelOutputInvalidError: si la salida no cumple el contrato.
        """

        #? Convertimos las features ingresadas en un arreglo 1x4 de numpy de tipo float32
        #? para enviar los valores al modelo
        feature_matrix = self.build_feature_matrix(packet_features)
        model_input: Any = feature_matrix

        #? Intentamos realizar la carga del modelo y convertir la matrix de numpy a
        #? XGBoost.DMatrix que es una estructura interna de XGBoost optimizada para la prediccion
        #? para que sea memory efficient y rapida de procesar
        try:
            xgboost = _load_xgboost_module()
            model_input = xgboost.DMatrix(feature_matrix)
        except Exception as exc:
            if self._booster.__class__.__module__.startswith("xgboost"):
                raise ModelInferenceFailedError() from exc

        #? Realizamos la prediccion directamente hacia el booster model con el input registrado
        #? en el formato apropiado
        try:
            raw_output = self._booster.predict(model_input)
        except Exception as exc:  # pragma: no cover
            raise ModelInferenceFailedError() from exc

        #? La normalizacion garantiza una unica fila y siete probabilidades validas
        #? antes de decodificar la clase dominante.
        probabilities = self._normalize_output(raw_output)

        #? Obtenemos el id de la clase con mayor probabilidad y obtenemos su nombre
        class_id = int(np.argmax(probabilities))
        class_name = self._metadata.id_to_class[str(class_id)]

        #? Creamos el mapping del resto de las probabilidades para reportar todo
        #? el detalle de la prediccion
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
        """
        Normaliza y valida la salida numerica cruda del booster.

        Pasos:
        - Convierte la salida a `float32`.
        - Aplana una salida bidimensional de una sola fila.
        - Verifica cardinalidad, finitud, rango y suma de probabilidades.

        Args:
            raw_output: salida cruda devuelta por el booster.

        Returns:
            np.ndarray: vector unidimensional de probabilidades validas.

        Raises:
            ModelOutputInvalidError: si la salida no respeta el contrato esperado.
        """

        #? Convierte la salida raw en un arreglo de 1x6 correspondiente a todos los valores de
        #? probabilidades
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
