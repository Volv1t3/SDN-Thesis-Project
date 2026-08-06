"""Identidad estable para la vida de un proceso worker."""

from __future__ import annotations

import multiprocessing
import os
import socket
import time
import uuid
from dataclasses import dataclass
from threading import Lock


@dataclass(frozen=True, slots=True)
class ProcessIdentity:
    """Identifica una instancia de despliegue y uno de sus workers."""

    service: str
    instance_id: str
    worker_id: str
    worker_pid: int
    process_name: str
    started_at_unix_seconds: float


_identity: ProcessIdentity | None = None
_identity_lock = Lock()


def initialize_process_identity(*, service: str, configured_instance_id: str | None) -> ProcessIdentity:
    """Inicializa una sola identidad por proceso y devuelve la existente si aplica."""

    global _identity
    with _identity_lock:
        if _identity is not None:
            return _identity

        instance_id = configured_instance_id.strip() if configured_instance_id is not None else ""
        if not instance_id:
            instance_id = os.getenv("HOSTNAME", "").strip() or socket.gethostname() or "unknown-instance"

        _identity = ProcessIdentity(
            service=service,
            instance_id=instance_id,
            worker_id=str(uuid.uuid4()),
            worker_pid=os.getpid(),
            process_name=multiprocessing.current_process().name,
            started_at_unix_seconds=time.time(),
        )
        return _identity


def get_process_identity() -> ProcessIdentity | None:
    """Devuelve la identidad del worker cuando ya fue inicializada."""

    return _identity


def _reset_process_identity_for_tests() -> None:
    """Restablece el singleton de identidad; solo debe usarse desde pruebas."""

    global _identity
    with _identity_lock:
        _identity = None
