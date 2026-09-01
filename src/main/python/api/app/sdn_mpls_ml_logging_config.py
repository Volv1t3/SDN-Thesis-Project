"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370

Archivo que defina la configuracion del formateo JSON estructurado usado por la aplicacion en los eventos de logging
que se realizan a consola.

Pasos:
- Define un formatter que serializa campos comunes y extra.
- Inicializa el root logger con un handler estandarizado.
"""

import json
import logging
from datetime import datetime, timezone
from logging.handlers import RotatingFileHandler
from pathlib import Path

from app.sdn_mpls_ml_config import RawSettings
from app.observability.sdn_mpls_ml_identity import get_process_identity


class ProcessIdentityFilter(logging.Filter):
    """Adjunta la identidad estable del worker a cada evento estructurado."""

    def filter(self, record: logging.LogRecord) -> bool:
        identity = get_process_identity()
        if identity is not None:
            record.instance_id = identity.instance_id
            record.worker_id = identity.worker_id
            record.worker_pid = identity.worker_pid
            record.process_name = identity.process_name
        return True


class JsonFormatter(logging.Formatter):
    """
    Formatea registros como objetos JSON compactos.

    Pasos:
    - Construye un payload base con timestamp, nivel y logger.
    - Copia campos extra conocidos cuando estan presentes.
    - Conserva metadatos de fallback cuando una politica cae al perfil por defecto.
    - Conserva codigos HTTP acotados cuando el fallo proviene del enrutamiento.
    - Devuelve una cadena JSON lista para stdout o stderr.

    Args:
        record: registro de logging a serializar.

    Returns:
        str: representacion JSON del evento.
    """

    def format(self, record: logging.LogRecord) -> str:
        """
        Serializa un registro de logging a JSON estructurado.

        Pasos:
        - Construye un payload base con tiempo, nivel y mensaje.
        - Copia campos extra conocidos cuando existen en el registro.
        - Devuelve una cadena JSON ASCII lista para salida estandar.

        Args:
            record: registro de logging recibido por el formatter.

        Returns:
            str: evento serializado en JSON.
        """

        payload = {
            "timestamp": datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z"),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }
        if hasattr(record, "service"):
            payload["service"] = record.service
        if hasattr(record, "event"):
            payload["event"] = record.event
        if hasattr(record, "context"):
            payload["context"] = record.context
        if hasattr(record, "metadata"):
            payload["metadata"] = record.metadata
        for key in (
            "instance_id",
            "worker_id",
            "worker_pid",
            "process_name",
            "request_id",
            "model_name",
            "model_dir",
            "resolved_model_dir",
            "config_dir",
            "resolved_config_dir",
            "current_workdir",
            "model_filename",
            "model_metadata_filename",
            "policy_filename",
            "deterministic_rule_filename",
            "predicted_class",
            "confidence",
            "fallback_reason",
            "pool_capacity",
            "pool_available",
            "pool_borrowed",
            "instance_index",
            "pool_size",
            "queue_wait_ms",
            "processing_time_ms",
            "classification_mode",
            "http_status_code",
            "failed_stage",
            "component",
            "error_code",
            "failed_check",
            "retryable",
            "validation_duration_ms",
        ):
            if hasattr(record, key):
                payload[key] = getattr(record, key)
        return json.dumps(payload, ensure_ascii=True)


def configure_logging(level: str, settings: RawSettings) -> None:
    """
    Inicializa la configuracion global de logging estructurado. Ademas de generar la configuracion base de un logger para
    la consola del contenedor o del proceso, tambien genera un registro en un ssitema de archivos, sea del contenedor o del host,
    para guardar archivos rotativos de logs.

    Usamos el mecanismo de RotatingFileHandler que es un sistema Builtin de Python que permite registrar
    logs hacia un archivo. En este caso, a diferencia de un solo archivo que cree infinitamente, el sistema se configura
    con un set de archivos rotativos, entonces cada vez que un archivo de logs este por llenarse, este cambia a un
    archivo diferente para no sobrecargar un mismo archivo de logs. Este archivo se rota o cambia de nombre para marcar el paso que
    se dio y se crea otro archivo de logs para continuar el registro.

    Pasos:
    - Crea handlers de consola y archivo con `JsonFormatter`.
    - Crea el directorio configurado cuando no existe.
    - Limpia handlers previos del root logger.
    - Aplica el nivel solicitado sobre el logger raiz.

    Args:
        level: nivel de log ya normalizado o seguro.
        settings: configuracion de logging leida desde variables de entorno.

    Returns:
        None.
    """

    #? Configuracion de logging de consola
    formatter = JsonFormatter()
    console_handler = logging.StreamHandler()
    console_handler.addFilter(ProcessIdentityFilter())
    console_handler.setFormatter(formatter)

    #? Configuracion de logging hacia el sistema de archivo usando RotatingFileHandler
    log_directory = Path(settings.log_directory)
    log_directory.mkdir(parents=True, exist_ok=True)
    file_handler = RotatingFileHandler(
        log_directory / settings.log_filename,
        maxBytes=settings.log_file_max_bytes,
        backupCount=settings.log_file_backup_count,
        encoding="utf-8",
    )
    file_handler.addFilter(ProcessIdentityFilter())
    file_handler.setFormatter(formatter)

    #? Registro del logger de consola y archivo
    root_logger = logging.getLogger()
    root_logger.handlers.clear()
    root_logger.addHandler(console_handler)
    root_logger.addHandler(file_handler)
    root_logger.setLevel(level.upper())
