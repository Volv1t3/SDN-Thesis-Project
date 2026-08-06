"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370

Archivo de configuracion que define los modelos de Pydantic usados tanto en la API en el modelo de Policy Mapper asi
como durante la validacion de dependencias para validar archivos de politicas cargados al sistema.

"""

from __future__ import annotations
from pydantic import BaseModel, ConfigDict, Field, StrictInt, field_validator
from app.sdn_mpls_ml_messages import Messages


class PathConstraints(BaseModel):
    """
    Representa las restricciones de camino para una politica. Este es un modelo de Pydantic orientado a la validacion de
    los archivos de politicas, notese por la definicion de esta clase que la validacion esta orientada
    a los valores cargados dentro de los archivos de configuracion (policy.json por ejemplo) que contiene la informacion
    de los tuneles a crear en base al tipo de trafico
    """

    model_config = ConfigDict(extra="forbid", strict=True)

    requested_bandwidth_kbps: StrictInt = Field(ge=0)
    setup_priority: StrictInt = Field(ge=0, le=7)
    hold_priority: StrictInt = Field(ge=0, le=7)


class TrafficPolicy(BaseModel):
    """
    Representa un perfil de trafico serializable por la API. Esta clase, objeto de Pydantic basado en
    BaseModel es igual que las validaciones definidas en sdn_mpls_ml_config.py usada para validar los datos
    de entrada de las politicas de trafico. Esta parte de la validacion se centra en los valores
    de DSCP, MPLS TC e internamente las restricciones de PathConstraints
    """

    model_config = ConfigDict(extra="forbid", strict=True)

    profile_name: str
    dscp: StrictInt = Field(ge=0, le=63)
    mpls_tc: StrictInt = Field(ge=0, le=7)
    path_constraints: PathConstraints

    @field_validator("profile_name")
    @classmethod
    def validate_profile_name(cls, value: str) -> str:
        """
        Asegura que el nombre del perfil no sea vacio.

        Args:
            value: nombre recibido para el perfil.

        Returns:
            str: nombre validado sin alterar.

        Raises:
            ValueError: si el nombre no contiene caracteres utiles.
        """

        if not value.strip():
            raise ValueError(Messages.PROFILE_NAME_REQUIRED)
        return value


class PolicyFile(BaseModel):
    """
    Representa el archivo raiz de politicas de trafico. Este modelo de Pydantic
    esta orientado a la validaacion del archivo, por lo que contiene internamente
    la configuracion del archivo y suversion, el TrafficPolicy default y las politicas de trafico
    correspondientes a todas las clases registradas
    """

    model_config = ConfigDict(extra="forbid", strict=True)

    schema_version: str
    default_profile: TrafficPolicy
    class_policies: dict[str, TrafficPolicy]

    @field_validator("schema_version")
    @classmethod
    def validate_schema_version(cls, value: str) -> str:
        """Verifica la version soportada del schema de politicas.

        Args:
            value: version declarada en el archivo.

        Returns:
            str: la misma version si es valida.

        Raises:
            ValueError: si la version no coincide con `1.0`.
        """

        if value != "1.0":
            raise ValueError(Messages.POLICY_SCHEMA_VERSION)
        return value
