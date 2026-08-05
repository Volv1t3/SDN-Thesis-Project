"""Declara los modelos estrictos del archivo de politicas.

Pasos:
- Valida rangos numericos de QoS y prioridades.
- Rechaza campos extra y conversiones silenciosas.
- Expone un contrato tipado para mapeo de politicas.
"""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field, StrictInt, field_validator

from app.messages import Messages


class PathConstraints(BaseModel):
    """Representa las restricciones de camino para una politica.

    Pasos:
    - Valida ancho de banda solicitado.
    - Restringe prioridades RSVP al rango permitido.
    """

    model_config = ConfigDict(extra="forbid", strict=True)

    requested_bandwidth_kbps: StrictInt = Field(ge=0)
    setup_priority: StrictInt = Field(ge=0, le=7)
    hold_priority: StrictInt = Field(ge=0, le=7)


class TrafficPolicy(BaseModel):
    """Representa un perfil de trafico serializable por la API.

    Pasos:
    - Valida nombre de perfil y marcados QoS.
    - Conserva restricciones de camino ya parseadas.
    """

    model_config = ConfigDict(extra="forbid", strict=True)

    profile_name: str
    dscp: StrictInt = Field(ge=0, le=63)
    mpls_tc: StrictInt = Field(ge=0, le=7)
    path_constraints: PathConstraints

    @field_validator("profile_name")
    @classmethod
    def validate_profile_name(cls, value: str) -> str:
        """Asegura que el nombre del perfil no sea vacio.

        Argumentos:
        - value: nombre recibido para el perfil.

        Retorna:
        - str: nombre validado sin alterar.

        Excepciones:
        - ValueError: si el nombre no contiene caracteres utiles.
        """

        if not value.strip():
            raise ValueError(Messages.PROFILE_NAME_REQUIRED)
        return value


class PolicyFile(BaseModel):
    """Representa el archivo raiz de politicas de trafico.

    Pasos:
    - Valida la version del schema.
    - Conserva perfil por defecto y politicas por clase.
    """

    model_config = ConfigDict(extra="forbid", strict=True)

    schema_version: str
    default_profile: TrafficPolicy
    class_policies: dict[str, TrafficPolicy]

    @field_validator("schema_version")
    @classmethod
    def validate_schema_version(cls, value: str) -> str:
        """Verifica la version soportada del schema de politicas.

        Argumentos:
        - value: version declarada en el archivo.

        Retorna:
        - str: la misma version si es valida.

        Excepciones:
        - ValueError: si la version no coincide con `1.0`.
        """

        if value != "1.0":
            raise ValueError(Messages.POLICY_SCHEMA_VERSION)
        return value
