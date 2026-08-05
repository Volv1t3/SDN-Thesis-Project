"""Centraliza constantes y carga de configuracion de la aplicacion.

Pasos:
- Declara valores por defecto para runtime, artefactos y limites.
- Modela configuracion cruda leida desde variables de entorno.
- Expone un contenedor validado para consumo interno.

Notas:
- Este modulo no ejecuta validaciones de negocio complejas.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum
from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


DEFAULT_APP_NAME = "sdnflow-inference-api"
DEFAULT_APP_VERSION = "1.0.0"
DEFAULT_HOST = "0.0.0.0"
DEFAULT_PORT = "8000"
DEFAULT_LOG_LEVEL = "INFO"
DEFAULT_MODEL_DIR = "/models"
DEFAULT_CONFIG_DIR = "/configs"
DEFAULT_MODEL_FILENAME = "model.json"
DEFAULT_MODEL_METADATA_FILENAME = "model_meta.json"
DEFAULT_POLICY_FILENAME = "default_policy.json"
DEFAULT_DETERMINISTIC_RULE_FILENAME = "deterministic_rules.json"
DEFAULT_REQUEST_TIMEOUT_SECONDS = "10"
DEFAULT_MAX_REQUEST_BODY_BYTES = 16384
DEFAULT_PROBABILITY_TOLERANCE = 0.001
DEFAULT_MIN_TUNNEL_BANDWIDTH_KBPS = "10000"
DEFAULT_MAX_TUNNEL_BANDWIDTH_KBPS = "100000"
IPV4_ETHERTYPE = 2048
MODEL_SUPPORTED_ETHERTYPES = frozenset({IPV4_ETHERTYPE})
SUPPORTED_LOG_LEVELS = {"CRITICAL", "ERROR", "WARNING", "INFO", "DEBUG"}


class ClassificationMode(StrEnum):
    """Enumera los modos de clasificacion soportados.

    Pasos:
    - Define el modo con modelo XGBoost real.
    - Define el modo deterministico usado como simulador.
    """

    MODEL = "MODEL"
    DETERMINISTIC_TEST = "DETERMINISTIC_TEST"

    @property
    def response_value(self) -> str:
        """Convierte el valor del enum al formato de respuesta HTTP.

        Pasos:
        - Toma el valor del enum en mayusculas.
        - Lo normaliza a minusculas para respuestas externas.

        Retorna:
        - str: representacion publica del modo.
        """

        return self.value.lower()


class RawSettings(BaseSettings):
    """Representa variables de entorno sin validacion semantica completa.

    Pasos:
    - Declara cada variable admitida por la API.
    - Conserva los valores como texto cuando la validacion posterior lo requiere.

    Notas:
    - La transformacion a tipos finales ocurre en `app.dependencies`.
    """

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=True,
    )

    app_name: str = Field(default=DEFAULT_APP_NAME, alias="APP_NAME")
    app_version: str = Field(default=DEFAULT_APP_VERSION, alias="APP_VERSION")
    host: str = Field(default=DEFAULT_HOST, alias="HOST")
    port: str = Field(default=DEFAULT_PORT, alias="PORT")
    log_level: str = Field(default=DEFAULT_LOG_LEVEL, alias="LOG_LEVEL")

    model_dir: str = Field(default=DEFAULT_MODEL_DIR, alias="MODEL_DIR")
    config_dir: str = Field(default=DEFAULT_CONFIG_DIR, alias="CONFIG_DIR")
    model_filename: str = Field(default=DEFAULT_MODEL_FILENAME, alias="MODEL_FILENAME")
    model_metadata_filename: str = Field(default=DEFAULT_MODEL_METADATA_FILENAME, alias="MODEL_METADATA_FILENAME")
    policy_filename: str = Field(default=DEFAULT_POLICY_FILENAME, alias="POLICY_FILENAME")
    deterministic_rule_filename: str = Field(
        default=DEFAULT_DETERMINISTIC_RULE_FILENAME,
        alias="DETERMINISTIC_RULE_FILENAME",
    )

    enable_policy_mapping: str = Field(default="true", alias="ENABLE_POLICY_MAPPING")
    request_timeout_seconds: str = Field(default=DEFAULT_REQUEST_TIMEOUT_SECONDS, alias="REQUEST_TIMEOUT_SECONDS")
    max_request_body_bytes: str = Field(default=str(DEFAULT_MAX_REQUEST_BODY_BYTES), alias="MAX_REQUEST_BODY_BYTES")
    probability_tolerance: str = Field(default=str(DEFAULT_PROBABILITY_TOLERANCE), alias="PROBABILITY_TOLERANCE")
    min_policy_confidence: str | None = Field(default=None, alias="MIN_POLICY_CONFIDENCE")
    classification_mode: str = Field(default=ClassificationMode.MODEL.value, alias="CLASSIFICATION_MODE")
    min_tunnel_bandwidth_kbps: str = Field(
        default=DEFAULT_MIN_TUNNEL_BANDWIDTH_KBPS,
        alias="MIN_TUNNEL_BANDWIDTH_KBPS",
    )
    max_tunnel_bandwidth_kbps: str = Field(
        default=DEFAULT_MAX_TUNNEL_BANDWIDTH_KBPS,
        alias="MAX_TUNNEL_BANDWIDTH_KBPS",
    )


@dataclass(slots=True)
class ValidatedSettings:
    """Agrupa configuracion ya parseada y lista para el runtime.

    Pasos:
    - Almacena valores convertidos a tipos concretos.
    - Se usa como contrato interno entre startup y endpoints.
    """

    app_name: str
    app_version: str
    host: str
    port: int
    log_level: str
    model_dir: str
    config_dir: str
    model_filename: str
    model_metadata_filename: str
    policy_filename: str
    deterministic_rule_filename: str
    enable_policy_mapping: bool
    request_timeout_seconds: int
    max_request_body_bytes: int
    probability_tolerance: float
    min_policy_confidence: float | None
    classification_mode: ClassificationMode
    min_tunnel_bandwidth_kbps: int
    max_tunnel_bandwidth_kbps: int


@lru_cache(maxsize=1)
def get_raw_settings() -> RawSettings:
    """Carga y cachea la configuracion cruda desde el entorno.

    Pasos:
    - Instancia `RawSettings` una sola vez por proceso.
    - Reutiliza el resultado en llamadas posteriores.

    Retorna:
    - RawSettings: configuracion cruda cacheada.
    """

    return RawSettings()


def get_safe_log_level(raw_log_level: str) -> str:
    """Normaliza un nivel de log y aplica fallback seguro.

    Pasos:
    - Elimina espacios y transforma el nivel a mayusculas.
    - Devuelve el nivel normalizado si esta soportado.
    - Usa el valor por defecto cuando el nivel no es valido.

    Argumentos:
    - raw_log_level: valor recibido desde configuracion externa.

    Retorna:
    - str: nivel de logging seguro para inicializar el root logger.
    """

    candidate = raw_log_level.strip().upper()
    if candidate in SUPPORTED_LOG_LEVELS:
        return candidate
    return DEFAULT_LOG_LEVEL
