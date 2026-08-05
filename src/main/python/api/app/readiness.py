"""Modela el estado de readiness cacheado y errores de startup.

Pasos:
- Provee un helper para timestamps UTC estandarizados.
- Define la excepcion tipada para fallos de validacion de startup.
- Define el estado mutable que leen endpoints y servicios.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone


def utc_now_iso() -> str:
    """Genera una marca de tiempo UTC en formato ISO compacto.

    Pasos:
    - Obtiene la hora actual en UTC.
    - Formatea milisegundos y reemplaza el offset por `Z`.

    Retorna:
    - str: timestamp UTC serializable.
    """

    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


@dataclass(slots=True)
class StartupValidationError(Exception):
    """Representa un fallo controlado durante la validacion de startup.

    Pasos:
    - Conserva codigo, mensaje y componente del fallo.
    - Transporta etapa, chequeo y semantica de reintento.
    """

    code: str
    message: str
    component: str
    failed_stage: str
    failed_check: str | None
    retryable: bool

    def __str__(self) -> str:
        """Devuelve el mensaje de error como representacion principal.

        Retorna:
        - str: mensaje humano del fallo.
        """

        return self.message


@dataclass(slots=True)
class ReadinessState:
    """Mantiene el estado de readiness cacheado del proceso.

    Pasos:
    - Guarda banderas de inicializacion y resultado final.
    - Conserva diagnostico estructurado del primer fallo encontrado.
    - Expone resumen util para endpoints de salud.
    """

    initialization_completed: bool
    ready: bool
    classification_mode: str
    completed_at_utc: str | None
    model_loaded: bool
    metadata_loaded: bool
    policy_loaded: bool
    synthetic_inference_passed: bool
    failed_stage: str | None
    error_code: str | None
    error_message: str | None
    error_component: str | None
    failed_check: str | None
    retryable: bool | None
    model_name: str | None = None
    model_schema_version: str | None = None
    feature_count: int | None = None
    class_count: int | None = None

    @classmethod
    def initializing(cls, classification_mode: str) -> "ReadinessState":
        """Construye el estado inicial antes del startup completo.

        Argumentos:
        - classification_mode: modo de clasificacion conocido hasta ese momento.

        Retorna:
        - ReadinessState: estado no inicializado y no listo.
        """

        return cls(
            initialization_completed=False,
            ready=False,
            classification_mode=classification_mode,
            completed_at_utc=None,
            model_loaded=False,
            metadata_loaded=False,
            policy_loaded=False,
            synthetic_inference_passed=False,
            failed_stage=None,
            error_code=None,
            error_message=None,
            error_component=None,
            failed_check=None,
            retryable=None,
        )

    def mark_failed(self, error: StartupValidationError) -> None:
        """Marca el estado como no listo tras un fallo controlado.

        Pasos:
        - Cierra la inicializacion como completada.
        - Copia al estado el diagnostico estructurado del error.

        Argumentos:
        - error: fallo tipado ocurrido en startup.

        Retorna:
        - None.
        """

        self.initialization_completed = True
        self.ready = False
        self.completed_at_utc = utc_now_iso()
        self.failed_stage = error.failed_stage
        self.error_code = error.code
        self.error_message = error.message
        self.error_component = error.component
        self.failed_check = error.failed_check
        self.retryable = error.retryable

    def mark_ready(
        self,
        *,
        model_name: str | None,
        model_schema_version: str | None,
        feature_count: int | None,
        class_count: int | None,
    ) -> None:
        """Marca el estado como listo y limpia errores previos.

        Pasos:
        - Declara completa la inicializacion.
        - Limpia metadatos de error.
        - Registra el resumen validado del modelo activo.

        Argumentos:
        - model_name: nombre del modelo listo o `None`.
        - model_schema_version: version del schema del modelo o `None`.
        - feature_count: cantidad de features validadas.
        - class_count: cantidad de clases validadas.
        """

        self.initialization_completed = True
        self.ready = True
        self.completed_at_utc = utc_now_iso()
        self.failed_stage = None
        self.error_code = None
        self.error_message = None
        self.error_component = None
        self.failed_check = None
        self.retryable = None
        self.model_name = model_name
        self.model_schema_version = model_schema_version
        self.feature_count = feature_count
        self.class_count = class_count

    @property
    def status(self) -> str:
        """Deriva el estado textual de readiness para respuestas HTTP.

        Retorna:
        - str: `initializing`, `ready` o `not_ready`.
        """

        if not self.initialization_completed:
            return "initializing"
        if self.ready:
            return "ready"
        return "not_ready"
