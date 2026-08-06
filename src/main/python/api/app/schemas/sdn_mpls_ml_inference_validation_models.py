"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370

Define los contratos HTTP para clasificacion y metadata del modelo. Estos modelos son Objetos de Pydantic que
permiten validar las solicitudes de los clientes y su contenido asi como la salida de las respuesta de las requests
en el contexto de los parametros internos requeridos, su tipo de dato y posicion en el JSON de respuesta

Pasos:
- Valida la forma basica de las caracteristicas de paquete.
- Modela respuestas de prediccion, probabilidades y politica.

Notas:
- Las restricciones dependientes del modo se validan fuera de este modulo. Esto se valida en sdn_mpls_ml_dependencies.py
- La correlacion del request se resuelve en middleware y no en el payload. Esto se valida en
sdn_mpls_ml_correlation_middleware.py
"""

from __future__ import annotations
from pydantic import ConfigDict, Field, StrictInt, model_validator
from app.sdn_mpls_ml_messages import Messages
from .sdn_mpls_ml_baseline_validation_models import StrictBaseModel


class PacketFeatures(StrictBaseModel):
    """
    Clase que representa el modelo base de Pydantic para definir las PacketFeatures que debe recibir una request
    desde un cliente y que deben ser estrictas para la ejecucion de una clasificacion de un paquete

    Pasos:
    - Aplica limites enteros estrictos sobre EtherType, protocolo y puertos.
    - Rechaza combinaciones de puertos invalidas para protocolos no TCP/UDP.

    Raises:
        ValueError: si un protocolo no TCP/UDP incluye puertos distintos de cero.
    """

    model_config = ConfigDict(extra="forbid", strict=True)

    eth_type: StrictInt = Field(ge=0, le=65535)
    ip_proto: StrictInt = Field(ge=0, le=255)
    src_port: StrictInt = Field(ge=0, le=65535)
    dst_port: StrictInt = Field(ge=0, le=65535)

    @model_validator(mode="after")
    def validate_protocol_rules(self) -> "PacketFeatures":
        """
        Aplica validaciones cruzadas sobre protocolo y puertos. Esto se realiza despues de la validacion base de
        Pydantic, es decir luego de que se han evaluado los tipos bases de Pydantic como StrictInt, se aplica la
        validacion adicional de los protocolos IP

        Pasos:
        - Revisa si `ip_proto` pertenece a TCP o UDP.
        - Exige puertos en cero cuando el protocolo no usa ese concepto.

        Returns:
            PacketFeatures: la misma instancia validada.

        Raises:
            ValueError: si la combinacion protocolo-puertos es incompatible.
        """

        if self.ip_proto not in {6, 17} and (self.src_port != 0 or self.dst_port != 0):
            raise ValueError(Messages.NON_TCP_UDP_ZERO_PORTS)
        return self


class ClassifyRequest(StrictBaseModel):
    """
    Modela el contenido de una request para clasificacion recibida por la API. EL sistema debe de \
    garantizar que una deserializacion del contenido JSON tenga en su forma real la estructura definida
    en esta clase, es decir, que tenga un modelo de PacketFeatures dentro. Aqui no revisamos si tiene una request id
    valida el contenido de la misma dado que eso no viene del lado del cliente y se crea en el servidor

    Pasos:
    - Agrupa las caracteristicas del paquete bajo `packet_features`.
    - Deja la correlacion del request fuera del payload funcional.

    Notes:
        El cliente no debe enviar `request_id` en este cuerpo.

        `extra="forbid"` hace que cualquier `request_id` enviado produzca `422`.
    """

    packet_features: PacketFeatures


class PredictionBody(StrictBaseModel):
    """
    Modela parte de la respuesta de una clasificacion de paquetes que contiene la clase final determinada con su
    id, nombre y confianza en la clasificacion. Este modelo de Pydantic es parcial dado que el modelo final de una
    respuesta de clasificacion contiene tanto esta informacion, como el policy definido, las probabilidades de las otras
    clases, etc.
    """
    class_id: int
    class_name: str
    confidence: float


class PathConstraints(StrictBaseModel):
    """
    Modelo de Pydantic que representa parte de las paths constraints que se retornan en una respuesta de
    clasificacion. Esta parte representa el bandwidth y la configuracion de setup y hold priorities que requiere
    Cisco, esto no es toda la respuesta, dado que tambien requiere de la Policy Response que es otra parte del modelo
    """

    requested_bandwidth_kbps: int
    setup_priority: int
    hold_priority: int


class PolicyResponse(StrictBaseModel):
    """
    Describe la politica final devuelta al consumidor. Este es un modelo de pydantic que junto con
    PathConstraints determina todo el modelo de datos concreto que debe ser usado.
    """

    profile_name: str
    dscp: int
    mpls_tc: int
    path_constraints: PathConstraints
    policy_fallback: bool = False
    policy_fallback_reason: str | None = None


class ClassifyResponse(StrictBaseModel):
    """
    Modela la respuesta completa del endpoint de clasificacion.

    Notas:
    - `request_id` es siempre generado por el servidor.
    - `request_id` debe coincidir con el header `X-Request-ID`.
    """

    request_id: str
    model_name: str
    prediction: PredictionBody
    probabilities: dict[str, float]
    policy: PolicyResponse | None = None
    processing_time_ms: float


class ModelClassInfo(StrictBaseModel):
    """
    Representa un par id-nombre de una clase del modelo.
    """

    id: int
    name: str


class ModelInfoResponse(StrictBaseModel):
    """
    Modela la respuesta del endpoint de informacion del modelo.
    """

    model_name: str
    target_name: str
    schema_version: str
    feature_order: list[str]
    classes: list[ModelClassInfo]
