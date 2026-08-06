"""Define los contratos HTTP para clasificacion y metadata del modelo.

Pasos:
- Valida la forma basica de las caracteristicas de paquete.
- Modela respuestas de prediccion, probabilidades y politica.

Notas:
- Las restricciones dependientes del modo se validan fuera de este modulo.
- La correlacion del request se resuelve en middleware y no en el payload.
"""

from __future__ import annotations

from pydantic import ConfigDict, Field, StrictInt, model_validator

from app.sdn_mpls_ml_messages import Messages
from .common import StrictBaseModel


class PacketFeatures(StrictBaseModel):
    """Representa el conjunto minimo de rasgos de un paquete.

    Pasos:
    - Aplica limites enteros estrictos sobre EtherType, protocolo y puertos.
    - Rechaza combinaciones de puertos invalidas para protocolos no TCP/UDP.

    Excepciones:
    - ValueError: si un protocolo no TCP/UDP incluye puertos distintos de cero.
    """

    model_config = ConfigDict(extra="forbid", strict=True)

    eth_type: StrictInt = Field(ge=0, le=65535)
    ip_proto: StrictInt = Field(ge=0, le=255)
    src_port: StrictInt = Field(ge=0, le=65535)
    dst_port: StrictInt = Field(ge=0, le=65535)

    @model_validator(mode="after")
    def validate_protocol_rules(self) -> "PacketFeatures":
        """Aplica validaciones cruzadas sobre protocolo y puertos.

        Pasos:
        - Revisa si `ip_proto` pertenece a TCP o UDP.
        - Exige puertos en cero cuando el protocolo no usa ese concepto.

        Retorna:
        - PacketFeatures: la misma instancia validada.

        Excepciones:
        - ValueError: si la combinacion protocolo-puertos es incompatible.
        """

        if self.ip_proto not in {6, 17} and (self.src_port != 0 or self.dst_port != 0):
            raise ValueError(Messages.NON_TCP_UDP_ZERO_PORTS)
        return self


class ClassifyRequest(StrictBaseModel):
    """Modela el cuerpo de una solicitud de clasificacion.

    Pasos:
    - Agrupa las caracteristicas del paquete bajo `packet_features`.
    - Deja la correlacion del request fuera del payload funcional.

    Notas:
    - El cliente no debe enviar `request_id` en este cuerpo.
    - `extra="forbid"` hace que cualquier `request_id` enviado produzca `422`.
    """

    packet_features: PacketFeatures


class PredictionBody(StrictBaseModel):
    """Representa el resultado principal de la prediccion.

    Pasos:
    - Expone clase, identificador y confianza seleccionados por el clasificador.
    """

    class_id: int
    class_name: str
    confidence: float


class PathConstraints(StrictBaseModel):
    """Representa restricciones de camino asociadas a una politica.

    Pasos:
    - Expone ancho de banda y prioridades RSVP serializadas.
    """

    requested_bandwidth_kbps: int
    setup_priority: int
    hold_priority: int


class PolicyResponse(StrictBaseModel):
    """Describe la politica final devuelta al consumidor.

    Pasos:
    - Publica nombre de perfil, DSCP y TC MPLS.
    - Adjunta el motivo de fallback cuando se usa la politica por defecto.
    """

    profile_name: str
    dscp: int
    mpls_tc: int
    path_constraints: PathConstraints
    policy_fallback: bool = False
    policy_fallback_reason: str | None = None


class ClassifyResponse(StrictBaseModel):
    """Modela la respuesta completa del endpoint de clasificacion.

    Pasos:
    - Devuelve correlacion, identidad del modelo y prediccion principal.
    - Incluye el mapa completo de probabilidades y la politica resuelta.

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
    """Representa un par id-nombre de una clase del modelo.

    Pasos:
    - Serializa una clase disponible para el endpoint de metadata.
    """

    id: int
    name: str


class ModelInfoResponse(StrictBaseModel):
    """Modela la respuesta del endpoint de informacion del modelo.

    Pasos:
    - Publica identidad, schema y orden de features.
    - Enumera las clases configuradas de salida.
    """

    model_name: str
    target_name: str
    schema_version: str
    feature_order: list[str]
    classes: list[ModelClassInfo]
