"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370

Clase encargad de modelar las reglas de los archivos de rutas deterministicas  y su validacion para
su uso dentro de la validacion de dependencias y la clasificacion deterministica
"""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field, StrictInt, field_validator, model_validator

from app.model.sdn_mpls_ml_metadata import EXPECTED_CLASS_TO_ID
from app.sdn_mpls_ml_messages import Messages


EXPECTED_STREAMING_CLASS = "STREAMING"


class DeterministicRuleFile(BaseModel):
    """
    Modela y valida el archivo de reglas del modo deterministico.
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
        """
        Verifica que cada clave represente un puerto valido.

        Args:
            value: mapa de puertos a clases.

        Returns:
            dict[str, str]: el mismo mapa si todas las claves son validas.

        Raises:
            ValueError: si alguna clave no representa un puerto valido.

        """

        for key in value:
            if not key.isdigit():
                raise ValueError(Messages.PORT_MAP_KEYS_DECIMAL)
            if not 0 <= int(key) <= 65535:
                raise ValueError(Messages.PORT_MAP_KEYS_RANGE)
        return value

    @model_validator(mode="after")
    def validate_contract(self) -> "DeterministicRuleFile":
        """
        Comprueba la compatibilidad de las reglas con el contrato de clases.


        Returns:
            DeterministicRuleFile: la misma instancia si es valida.

        Raises:
            ValueError: si alguna clase no es compatible con el contrato.

        """

        if self.schema_version != "1.0":
            raise ValueError(Messages.DETERMINISTIC_RULE_SCHEMA_VERSION)
        if self.streaming_class_name != EXPECTED_STREAMING_CLASS:
            raise ValueError(Messages.DETERMINISTIC_STREAMING_FALLBACK)
        if set(self.destination_port_class_map.values()) - set(EXPECTED_CLASS_TO_ID):
            raise ValueError(Messages.DETERMINISTIC_UNKNOWN_DESTINATION_CLASS)
        if set(self.source_port_class_map.values()) - set(EXPECTED_CLASS_TO_ID):
            raise ValueError(Messages.DETERMINISTIC_UNKNOWN_SOURCE_CLASS)
        if any(name == EXPECTED_STREAMING_CLASS for name in self.destination_port_class_map.values()):
            raise ValueError(Messages.DETERMINISTIC_STREAMING_DIRECT_RULE)
        if any(name == EXPECTED_STREAMING_CLASS for name in self.source_port_class_map.values()):
            raise ValueError(Messages.DETERMINISTIC_STREAMING_DIRECT_RULE)
        return self
