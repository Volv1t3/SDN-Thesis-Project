"""Pruebas de identidad estable de workers."""

from __future__ import annotations

import os
import uuid

from app.observability.sdn_mpls_ml_identity import (
    _reset_process_identity_for_tests,
    initialize_process_identity,
)


def test_process_identity_is_stable_and_uses_configured_instance_id():
    """Una inicializacion repetida conserva la identidad del worker."""

    _reset_process_identity_for_tests()
    try:
        identity = initialize_process_identity(service="test-service", configured_instance_id="instance-1")
        repeated = initialize_process_identity(service="another-service", configured_instance_id="instance-2")

        assert repeated is identity
        assert identity.instance_id == "instance-1"
        assert identity.worker_pid == os.getpid()
        assert identity.process_name
        assert uuid.UUID(identity.worker_id).version == 4
    finally:
        _reset_process_identity_for_tests()
