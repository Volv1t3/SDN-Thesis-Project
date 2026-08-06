"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370

Implementa el clasificador deterministico basado en reglas de red.
"""

from __future__ import annotations

from app.model.sdn_mpls_ml_model_predictor import PredictionResult
from app.model.sdn_mpls_ml_deterministic_rules import DeterministicRuleFile
from app.model.sdn_mpls_ml_metadata import EXPECTED_CLASS_TO_ID


EXPECTED_PROTOCOL_CLASS = "ICMP"


class DeterministicClassifier:
    """
    Clasifica paquetes usando protocolo IP y mapas de puertos configurados.
    """

    def __init__(self, rules: DeterministicRuleFile) -> None:
        self._rules = rules

    def predict(self, packet_features: dict[str, int]) -> PredictionResult:
        """
        AplicaDevuelve una prediccion one-hot para las reglas aplicables al paquete.

        Args:
            packet_features: caracteristicas de paquete a clasificar.

        Returns:
            PredictionResult: resultado con clase, confianza y probabilidades.

        """

        class_name = self._classify(packet_features)
        return PredictionResult(
            class_id=EXPECTED_CLASS_TO_ID[class_name],
            class_name=class_name,
            confidence=1.0,
            probabilities={
                expected_class: 1.0 if expected_class == class_name else 0.0
                for expected_class in EXPECTED_CLASS_TO_ID
            },
        )

    def _classify(self, packet_features: dict[str, int]) -> str:
        """
        Clasifica un paquete segun sus caracteristicas y las reglas deterministas
        :param packet_features: diccionario con las caracteristicas del paquete a clasificar
        :return: nombre de la clase a la que pertenece el paquete segun las reglas
        deterministicas
        """

        if packet_features["ip_proto"] == self._rules.icmp_ip_protocol:
            return EXPECTED_PROTOCOL_CLASS

        dst_port = packet_features["dst_port"]
        src_port = packet_features["src_port"]
        if str(dst_port) in self._rules.destination_port_class_map:
            return self._rules.destination_port_class_map[str(dst_port)]
        if str(src_port) in self._rules.source_port_class_map:
            return self._rules.source_port_class_map[str(src_port)]
        if dst_port > self._rules.well_known_port_threshold:
            return self._rules.streaming_class_name
        if src_port > self._rules.well_known_port_threshold:
            return self._rules.streaming_class_name
        return self._rules.streaming_class_name
