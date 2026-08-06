"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370

Aplica validaciones de entrada dependientes del modo de clasificacion.

"""

from __future__ import annotations

from app.sdn_mpls_ml_config import MODEL_SUPPORTED_ETHERTYPES, ClassificationMode
from app.sdn_mpls_ml_exceptions import ModelEtherTypeUnsupportedError


def validate_packet_for_classification_mode(
    *,
    classification_mode: ClassificationMode,
    eth_type: int,
    request_id: str,
) -> None:
    """
    Valida un paquete segun el modo de clasificacion activo.

    Pasos:
    - Omite la restriccion de EtherType en modo deterministico.
    - Exige un EtherType soportado por el modelo en modo `MODEL`.

    Args:
        classification_mode: modo activo de clasificacion.
        eth_type: EtherType recibido en la solicitud.
        request_id: correlacion de la solicitud para errores tipados.

    Returns:
        None.

    Raises:
        ModelEtherTypeUnsupportedError: si el modo `MODEL` recibe un EtherType no soportado.
    """

    if classification_mode is ClassificationMode.DETERMINISTIC_TEST:
        return

    if eth_type not in MODEL_SUPPORTED_ETHERTYPES:
        raise ModelEtherTypeUnsupportedError(request_id=request_id)
