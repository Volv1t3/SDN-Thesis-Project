"""Configura el formateo JSON estructurado usado por la aplicacion.

Pasos:
- Define un formatter que serializa campos comunes y extra.
- Inicializa el root logger con un handler estandarizado.
"""

import json
import logging
from datetime import datetime, timezone


class JsonFormatter(logging.Formatter):
    """Formatea registros como objetos JSON compactos.

    Pasos:
    - Construye un payload base con timestamp, nivel y logger.
    - Copia campos extra conocidos cuando estan presentes.
    - Conserva metadatos de fallback cuando una politica cae al perfil por defecto.
    - Conserva codigos HTTP acotados cuando el fallo proviene del enrutamiento.
    - Devuelve una cadena JSON lista para stdout o stderr.

    Argumentos:
    - record: registro de logging a serializar.

    Retorna:
    - str: representacion JSON del evento.
    """

    def format(self, record: logging.LogRecord) -> str:
        """Serializa un registro de logging a JSON estructurado.

        Pasos:
        - Construye un payload base con tiempo, nivel y mensaje.
        - Copia campos extra conocidos cuando existen en el registro.
        - Devuelve una cadena JSON ASCII lista para salida estandar.

        Argumentos:
        - record: registro de logging recibido por el formatter.

        Retorna:
        - str: evento serializado en JSON.
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
        for key in (
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


def configure_logging(level: str) -> None:
    """Inicializa la configuracion global de logging estructurado.

    Pasos:
    - Crea un handler de stream con `JsonFormatter`.
    - Limpia handlers previos del root logger.
    - Aplica el nivel solicitado sobre el logger raiz.

    Argumentos:
    - level: nivel de log ya normalizado o seguro.

    Retorna:
    - None.
    """

    handler = logging.StreamHandler()
    handler.setFormatter(JsonFormatter())
    root_logger = logging.getLogger()
    root_logger.handlers.clear()
    root_logger.addHandler(handler)
    root_logger.setLevel(level.upper())
