"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370

Archivo que contiene el modelo de readiness que se utiliza para tener el estado de la aplicacion cacheado con el fin
de evitar multiples calls a la API o al modelo. Readiness en esta aplicacion se define en base a pasar todas las pruebas
de inicio de la app:
1. Validar existencia y carga de artefactos de configuracion
2. Validar existencia y lectura de artefactos generales
3. Valida carga y estructura de los artefactos segun el modo de ejecucion
4. Prueba de funcionamiento del modelo

Si en alguno de estos pasos la aplicacion no pasa, el startup no se detiene, pero la aplicacion entra en un estado en donde
las respuestas de la api son 503 en healthy y lo mismo en ready pero el loop sigue vivo. Para evitar durante operaciones que el
modelo este en un loop de uso del sistema de reaqdiness o health en lugar de tener que ejecutar todo el loop de validacion
completo cada vez que queramos saber si la api esta ready, lo que hacemos es retornar el estado cacheado, y los sistemas
dependientes deberan revisar si las respuesta se transforman en codigos 5XX indicando un error en la API.

- Provee un helper para timestamps UTC estandarizados.
- Define la excepcion tipada para fallos de validacion de startup.
- Define el estado mutable que leen endpoints y servicios.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone


def utc_now_iso() -> str:
    """
    Genera una marca de tiempo UTC en formato ISO compacto.

    Pasos:
    - Obtiene la hora actual en UTC.
    - Formatea milisegundos y reemplaza el offset por `Z`.

    :returns: timestamp UTC serializable.
    """
    return (datetime.now(timezone.utc)
            .isoformat(timespec="milliseconds")
            .replace("+00:00", "Z"))


@dataclass(slots=True)
class StartupValidationError(Exception):
    """
    Clase representativa de un objeto de tipo Record que representa y contiene todos los campos
    correspondientes a un error de Validacion durante el Startup. Esta informacion se registra en las variables
    internas que pueden ser usadas para enviar estos datos dentro de una respuesta a una llamda a la API o para logging.

    En general el valor mas util de la clase para un desarrollador se retorna dentro de su metodo toString() que
    corresponde al mensaje de sdn_mpls_ml_messages.py que contiene los mensajes claros de estos errores.

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
        """
        Devuelve el mensaje de error como representacion principal.

        :returns: mensaje humano del fallo.
        """
        return self.message


@dataclass(slots=True)
class ReadinessState:
    """

    Esta clase corresponde a otro Record que contiene todos los datos internos de la incializacion del sistema,
    junto con los detalles de los archivos de configuracion cargados y datos de un posible error registrado junto con el
    paso en el que se dio el error

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
    def initializing(
            cls, 
            classification_mode: str) -> "ReadinessState":
        """
        Construye el estado inicial antes del startup completo. Esto es como una inicializacion en Java en un constructor,
        solo que en este caso es una configuracion de un objeto mediante un metodo de la clase. En este caso @classmethod 
        nos permite inicializar un objeto externo de manera que al usarlo tenga todos los valores por defecto sin tener que tener 
        atributos de una clase. En un Record de Java esto es equivalente a usar el constructor del Record y que todos los 
        campos se vuelvan inmutables, solo que en Python los parametros si son mutables. 
        
        La idea es que al hacer una dataclass es como tener un struct o un Record con campos mutables, pero no una clase real.

        :arg classification_mode: modo de clasificacion conocido hasta ese momento.

        :returns: estado no inicializado y no listo.
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

    def mark_failed(
            self, 
            error: StartupValidationError) -> None:
        """
        Marca el estado como no listo tras un fallo controlado.

        Pasos:
        - Cierra la inicializacion como completada.
        - Copia al estado el diagnostico estructurado del error.

        :arg error: fallo tipado ocurrido en startup.

        :returns: None
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
        """
        Marca el estado como listo y limpia errores previos.

        Pasos:
        - Declara completa la inicializacion.
        - Limpia metadatos de error.
        - Registra el resumen validado del modelo activo.

        Args:
            model_name: nombre del modelo listo o `None`.
            model_schema_version: version del schema del modelo o `None`.
            feature_count: cantidad de features validadas.
            class_count: cantidad de clases validadas.
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
        """
        Deriva el estado textual de readiness para respuestas HTTP.

        :returns: `initializing`, `ready` o `not_ready`.
        """

        if not self.initialization_completed:
            return "initializing"
        if self.ready:
            return "ready"
        return "not_ready"
