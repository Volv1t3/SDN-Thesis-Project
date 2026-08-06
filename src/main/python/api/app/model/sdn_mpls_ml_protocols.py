"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370

Esta clase contiene una caracteristica especial que es que permite generar una estructura de
runtime polymorphism en donde las clases de Predictor y las clases de DeterministicClassifier
son consideradas durante el runtime de la aplicacion como parte de las especializaciones de esta
clase general declarada mediante el protocolo TrafficClassifier.

En este caso, typing.Protocol funciona mediante un mecanismo conocido como 'duck typing', que permite que cualquier
clase que implemente los metodos y atributos definidos en el protocolo pueda ser tratada como una instancia de
ese protocolo, sin necesidad de heredar de una clase base concreta. Esto es especialmente util para definir interfaces
y contratos en Python, permitiendo una mayor flexibilidad y extensibilidad en el diseno del software.
"""

from __future__ import annotations

from typing import Protocol
from app.model.sdn_mpls_ml_model_predictor import PredictionResult


class TrafficClassifier(Protocol):
    """
    Interface (Protocol) que define el mecanismo base o contrato base de los predictores de la aplicacion. En este caso
    la definicion corresponde a una funcion predict basada en un diccionario de features de un paquete
    que siempre retorna un PredictionResult de la clase sdn_mpls_ml_model_predictor.py
    """

    def predict(self, packet_features: dict[str, int]) -> PredictionResult:
        """Clasifica un paquete y devuelve el resultado normalizado."""
