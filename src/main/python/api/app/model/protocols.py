"""Define contratos comunes para clasificadores del runtime."""

from __future__ import annotations

from typing import Protocol

from app.model.predictor import PredictionResult


class TrafficClassifier(Protocol):
    """Representa cualquier clasificador compatible con la API."""

    def predict(self, packet_features: dict[str, int]) -> PredictionResult:
        """Clasifica un paquete y devuelve el resultado normalizado."""

