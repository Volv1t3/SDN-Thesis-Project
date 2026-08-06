"""Pruebas para la salida estructurada de logs."""

from __future__ import annotations

import json
import logging
from logging.handlers import RotatingFileHandler
from types import SimpleNamespace

from app import sdn_mpls_ml_logging_config as logging_config


def test_configure_logging_writes_json_events_to_console_and_file(monkeypatch, tmp_path, capsys):
    """El mismo evento estructurado se emite en consola y en el archivo de logs."""

    settings = SimpleNamespace(
        log_directory=str(tmp_path),
        log_filename="test.log",
        log_file_max_bytes=1024,
        log_file_backup_count=1,
    )
    root_logger = logging.getLogger()
    original_handlers = root_logger.handlers[:]
    original_level = root_logger.level

    try:
        logging_config.configure_logging("INFO", settings)
        logging.getLogger("test.logging").info("file logging enabled", extra={"event": "logging_test"})

        console_event = json.loads(capsys.readouterr().err)
        file_event = json.loads((tmp_path / settings.log_filename).read_text(encoding="utf-8"))
        assert console_event["event"] == "logging_test"
        assert file_event["event"] == "logging_test"
        assert any(isinstance(handler, RotatingFileHandler) for handler in root_logger.handlers)
    finally:
        configured_handlers = root_logger.handlers[:]
        root_logger.handlers.clear()
        for handler in configured_handlers:
            handler.close()
        root_logger.handlers.extend(original_handlers)
        root_logger.setLevel(original_level)
