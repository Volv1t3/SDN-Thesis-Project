"""Aplica validaciones de entrada dependientes del modo de clasificacion.

Pasos:
- Diferencia reglas exclusivas del modo con modelo real.
- Reutiliza errores tipados para rechazos de compatibilidad.
"""

from __future__ import annotations

from app.config import MODEL_SUPPORTED_ETHERTYPES, ClassificationMode
from app.sdn_mpls_ml_exceptions import ModelEtherTypeUnsupportedError


def validate_packet_for_classification_mode(
    *,
    classification_mode: ClassificationMode,
    eth_type: int,
    request_id: str,
) -> None:
    """Valida un paquete segun el modo de clasificacion activo.

    Pasos:
    - Omite la restriccion de EtherType en modo deterministico.
    - Exige un EtherType soportado por el modelo en modo `MODEL`.

    Argumentos:
    - classification_mode: modo activo de clasificacion.
    - eth_type: EtherType recibido en la solicitud.
    - request_id: correlacion de la solicitud para errores tipados.

    Retorna:
    - None.

    Excepciones:
    - ModelEtherTypeUnsupportedError: si el modo `MODEL` recibe un EtherType no soportado.
    """

    if classification_mode is ClassificationMode.DETERMINISTIC_TEST:
        return

    if eth_type not in MODEL_SUPPORTED_ETHERTYPES:
        raise ModelEtherTypeUnsupportedError(request_id=request_id)
