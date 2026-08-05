"""Implementa el clasificador deterministico usado como simulador.

Pasos:
- Valida el archivo de reglas deterministicas.
- Clasifica paquetes usando protocolo y mapas de puertos.
- Devuelve probabilidades one-hot compatibles con la API.
"""

from __future__ import annotations

import json
from pathlib import Path

from pydantic import BaseModel, ConfigDict, Field, StrictInt, ValidationError, field_validator, model_validator

from app.messages import Messages
from app.model.metadata import EXPECTED_CLASS_TO_ID
from app.model.predictor import PredictionResult


EXPECTED_PROTOCOL_CLASS = "ICMP"
EXPECTED_STREAMING_CLASS = "STREAMING"


class DeterministicRuleFile(BaseModel):
    """Modela el archivo de reglas del modo deterministico.

    Pasos:
    - Valida el schema del archivo JSON.
    - Restringe puertos a claves numericas validas.
    - Verifica clases conocidas y fallback de streaming.
    """

    model_config = ConfigDict(extra="forbid", strict=True)

    schema_version: str
    well_known_port_threshold: StrictInt = Field(ge=1, le=65535)
    icmp_ip_protocol: StrictInt = Field(ge=0, le=255)
    destination_port_class_map: dict[str, str]
    source_port_class_map: dict[str, str] = Field(default_factory=dict)
    streaming_class_name: str

    @field_validator("destination_port_class_map", "source_port_class_map")
    @classmethod
    def validate_port_map_keys(cls, value: dict[str, str]) -> dict[str, str]:
        """Valida que las claves del mapa de puertos sean numericas.

        Argumentos:
        - value: mapa de puertos a nombres de clase.

        Retorna:
        - dict[str, str]: el mismo mapa si todas las claves son validas.

        Excepciones:
        - ValueError: si alguna clave no representa un puerto valido.
        """

        for key in value:
            if not key.isdigit():
                raise ValueError(Messages.PORT_MAP_KEYS_DECIMAL)
            port = int(key)
            if not 0 <= port <= 65535:
                raise ValueError(Messages.PORT_MAP_KEYS_RANGE)
        return value

    @model_validator(mode="after")
    def validate_contract(self) -> "DeterministicRuleFile":
        """Verifica el contrato funcional de las reglas deterministicas.

        Pasos:
        - Comprueba la version del schema.
        - Verifica la clase de fallback y las clases conocidas.
        - Impide usar STREAMING como regla directa de puerto.

        Retorna:
        - DeterministicRuleFile: la misma instancia validada.

        Excepciones:
        - ValueError: si el contrato del archivo es incompatible.
        """

        if self.schema_version != "1.0":
            raise ValueError(Messages.DETERMINISTIC_RULE_SCHEMA_VERSION)
        if self.streaming_class_name != EXPECTED_STREAMING_CLASS:
            raise ValueError(Messages.DETERMINISTIC_STREAMING_FALLBACK)
        if set(self.destination_port_class_map.values()) - set(EXPECTED_CLASS_TO_ID):
            raise ValueError(Messages.DETERMINISTIC_UNKNOWN_DESTINATION_CLASS)
        if set(self.source_port_class_map.values()) - set(EXPECTED_CLASS_TO_ID):
            raise ValueError(Messages.DETERMINISTIC_UNKNOWN_SOURCE_CLASS)
        if any(class_name == EXPECTED_STREAMING_CLASS for class_name in self.destination_port_class_map.values()):
            raise ValueError(Messages.DETERMINISTIC_STREAMING_DIRECT_RULE)
        if any(class_name == EXPECTED_STREAMING_CLASS for class_name in self.source_port_class_map.values()):
            raise ValueError(Messages.DETERMINISTIC_STREAMING_DIRECT_RULE)
        return self


def load_deterministic_rules(path: str | Path) -> DeterministicRuleFile:
    """Carga y valida reglas deterministicas desde JSON.

    Pasos:
    - Lee el archivo como UTF-8.
    - Parsea el contenido como JSON.
    - Valida el payload con `DeterministicRuleFile`.

    Argumentos:
    - path: ruta del archivo de reglas.

    Retorna:
    - DeterministicRuleFile: reglas validadas.

    Excepciones:
    - ValueError: si el archivo no puede leerse o validarse.
    """

    try:
        payload = json.loads(Path(path).read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise ValueError(Messages.DETERMINISTIC_RULE_FILE_INVALID_JSON) from exc
    except OSError as exc:
        raise ValueError(Messages.DETERMINISTIC_RULE_FILE_READ_FAILED) from exc

    try:
        return DeterministicRuleFile.model_validate(payload)
    except ValidationError as exc:
        raise ValueError(Messages.deterministic_rule_validation_failed(str(exc))) from exc


class DeterministicClassifier:
    """Clasifica trafico con reglas deterministicas simples.

    Pasos:
    - Revisa primero protocolo ICMP.
    - Luego evalua mapa de puertos de destino.
    - Luego evalua mapa de puertos de origen.
    - Aplica fallback a STREAMING cuando no hay coincidencias.
    """

    def __init__(self, rules: DeterministicRuleFile) -> None:
        """Guarda las reglas cargadas para clasificacion posterior.

        Argumentos:
        - rules: reglas deterministicas ya validadas.
        """

        self._rules = rules

    def predict(self, packet_features: dict[str, int]) -> PredictionResult:
        """Genera una prediccion one-hot para un paquete dado.

        Pasos:
        - Determina la clase mediante `_classify`.
        - Convierte la clase en probabilidades one-hot.
        - Devuelve el resultado con confianza unitaria.

        Argumentos:
        - packet_features: rasgos del paquete a clasificar.

        Retorna:
        - PredictionResult: prediccion deterministicamente resuelta.
        """

        class_name = self._classify(packet_features)
        class_id = EXPECTED_CLASS_TO_ID[class_name]
        probabilities = {
            expected_class: 1.0 if expected_class == class_name else 0.0
            for expected_class in EXPECTED_CLASS_TO_ID
        }
        return PredictionResult(
            class_id=class_id,
            class_name=class_name,
            confidence=1.0,
            probabilities=probabilities,
        )

    def _classify(self, packet_features: dict[str, int]) -> str:
        """Selecciona una clase usando reglas ordenadas por prioridad.

        Pasos:
        - Prioriza el protocolo ICMP configurado.
        - Consulta primero el puerto de destino.
        - Consulta luego el puerto de origen.
        - Usa STREAMING como fallback final.

        Argumentos:
        - packet_features: rasgos del paquete ya parseados.

        Retorna:
        - str: nombre de clase resultante.
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
