"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370

Archivo que define la identidad de un proceso worker dentro del sistema, esta identidad es estable a lo largo de la vida
del proceso y se usa para fines de observabilidad y monitoreo del sistema.

Este id se basa no solo en el PID del proceso correspondiente en el contenedor, sino que tambien se basa en el IDde instancia si este
se provee, asi como en un identificador UUIDv4 junto con el PID, formando un ID fuerte para la identificacion de procesos

"""

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
    """
    Identifica una instancia de despliegue y uno de sus workers. En este caso la instancia se basa en un id proveido
    a toda la aplicacion en base de una variable de entorno, por ejemplo SDN-API-01, y junto a esta un worker se identifica
    por un id creado, el PID del proceso y el nombre del proceso. En este caso si no se tiene una instance_id esta se deriva del
    HOSTNAME del contenedor o desde el socketname
    """
    service: str
    instance_id: str
    worker_id: str
    worker_pid: int
    process_name: str
    started_at_unix_seconds: float


_identity: ProcessIdentity | None = None
"""
Este lock sirve para proteger la inicializacion de la identidad del proceso contra concurrencia 
durante la inicializacion de la aplicacion, dado que inicializa un singleton de ProcessIdentity para todo
el worker de uvicorn (instancia de la API)
"""
_identity_lock = Lock()


def initialize_process_identity(*, service: str, configured_instance_id: str | None) -> ProcessIdentity | None:
    """
    Inicializa la identidad del proceso. Esta funcion es thread-safe y se puede llamar multiples veces,
    pero solo se ejecutara una vez por proceso. Se asegura de que el proceso tenga una identidad unica
    dentro del sistema de despliegue.

    Args:
        service: nombre del servicio que se esta iniciando.
        configured_instance_id: identificador de instancia opcional.

    Returns:
        ProcessIdentity: identidad del proceso inicializado.
    """

    global _identity
    #? Lock para modificacion de la identidad
    with _identity_lock:
        if _identity is not None:
            #? Si el singleton ya fue configurado retornamos early
            return _identity

        #? Obtenemos el instancia ID si existe de la aplicacion o si no lo derivamos
        instance_id = configured_instance_id.strip() if configured_instance_id is not None else ""
        if not instance_id:
            instance_id = os.getenv("HOSTNAME", "").strip() or socket.gethostname() or "unknown-instance"

        #? Generamos la identidad del worker actual
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
    """
    Devuelve la identidad del worker cuando ya fue inicializada.
    """
    return _identity


def _reset_process_identity_for_tests() -> None:
    """
    Restablece el singleton de identidad; solo debe usarse desde pruebas.
    """
    global _identity
    with _identity_lock:
        _identity = None
