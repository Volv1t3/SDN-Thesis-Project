"""Prueba la validacion de EtherType dependiente del modo de clasificacion.

Pasos:
- Acepta IPv4 en modo `MODEL`.
- Rechaza EtherType no IPv4 en modo `MODEL`.
- Omite la restriccion especifica del modelo en modo deterministico.
"""

from __future__ import annotations

import pytest

from app.config import ClassificationMode, IPV4_ETHERTYPE
from app.sdn_mpls_ml_exceptions import ModelEtherTypeUnsupportedError
from app.model.input_validation import validate_packet_for_classification_mode


def test_model_mode_accepts_ipv4_ethertype() -> None:
    """Verifica que el modo `MODEL` acepte el EtherType IPv4 soportado."""

    validate_packet_for_classification_mode(
        classification_mode=ClassificationMode.MODEL,
        eth_type=IPV4_ETHERTYPE,
        request_id="request-id",
    )


def test_model_mode_rejects_non_ipv4_ethertype() -> None:
    """Comprueba que el modo `MODEL` rechace EtherTypes no soportados."""

    with pytest.raises(ModelEtherTypeUnsupportedError):
        validate_packet_for_classification_mode(
            classification_mode=ClassificationMode.MODEL,
            eth_type=34525,
            request_id="request-id",
        )


def test_deterministic_mode_accepts_non_ipv4_ethertype() -> None:
    """Confirma que el modo deterministico ignore la restriccion IPv4 del modelo."""

    validate_packet_for_classification_mode(
        classification_mode=ClassificationMode.DETERMINISTIC_TEST,
        eth_type=34525,
        request_id="request-id",
    )
