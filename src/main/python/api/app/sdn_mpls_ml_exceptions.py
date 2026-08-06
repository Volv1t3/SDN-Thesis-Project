"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370

Archivo que define los errores y excepciones definidas para la API como extensiones de las clases de Exception de Python
que corresponde a clases de error especificas usadas para manejar tanto eventos de logging de errores y respuestas de API
basadas en los campos de error que estas tienen.

Pasos:
- Declara un contrato base para errores de aplicacion.
- Provee variantes concretas para validacion, readiness e inferencia.

Notas:
- Los errores se transforman en respuestas HTTP en `app.main`.
"""

from __future__ import annotations

from dataclasses import dataclass
from app.sdn_mpls_ml_messages import Messages


@dataclass(slots=True)
class ErrorDetail:
    """Representa el detalle serializable de un error. Esta parte del sistema representa otro Record que contiene la informacion
    de los detalles de un error, es decir el codigo, el mensaje correcto legible, el componente que fallo, y si el error
    puede ser reintantable como un error en clasifiacion.

    Pasos:
    - Conserva codigo y mensaje legibles por maquina y operador.
    - Adjunta metadatos opcionales de componente y chequeo.
    """

    code: str
    message: str
    component: str | None = None
    failed_stage: str | None = None
    failed_check: str | None = None
    retryable: bool | None = None


class AppError(Exception):
    """
    Base comun para errores funcionales de la API. La clase contiene los mismos campos que su contraparte de tipo Record, pero
    la idea es que esta al ser una excepcion real esta puede ser lanzada por el cuerpo de la aplicacion y ser interceptada
    y usada por servicios de logging y respuestas htttp definidas en sdn_mpls_ml_main.py. Ademas, al presentar la forma
    de serializar este objeto a un error de tipo ErrorDetail (Record) tiene la capacidad de enviar su informacion en un
    objeto sencillo para su serializacion.

    Pasos:
    - Define un codigo y mensaje por defecto.
    - Permite adjuntar contexto estructurado de depuracion segura.
    - Expone un helper para convertir el error a `ErrorDetail`.

    Notas:
    - `request_id` representa la correlacion HTTP generada por el servidor.
    """

    status_code: int = 500
    code: str = "INTERNAL_ERROR"
    message: str = Messages.INTERNAL_ERROR
    component: str | None = None
    failed_stage: str | None = None
    failed_check: str | None = None
    retryable: bool | None = None

    def __init__(
        self,
        message: str | None = None,
        *,
        component: str | None = None,
        failed_stage: str | None = None,
        failed_check: str | None = None,
        retryable: bool | None = None,
        request_id: str | None = None,
    ) -> None:
        """
        Inicializa un error de aplicacion con metadatos opcionales.

        Args:
            message: mensaje final del error o `None` para usar el predeterminado.
            component: componente logico asociado al fallo.
            failed_stage: etapa del flujo donde ocurrio el error.
            failed_check: nombre del chequeo que fallo.
            retryable: indica si el error es reintentable.
            request_id: correlacion opcional de la solicitud generada por el servidor.
        """

        super().__init__(message or self.message)
        self.message = message or self.message
        self.component = component or self.component
        self.failed_stage = failed_stage or self.failed_stage
        self.failed_check = failed_check or self.failed_check
        self.retryable = retryable if retryable is not None else self.retryable
        self.request_id = request_id

    def to_error(self) -> ErrorDetail:
        """
        Convierte la excepcion al contrato serializable de error.

        Args:
            self: la instancia de la excepcion.

        Returns:
            ErrorDetail: estructura lista para respuestas HTTP.
        """

        return ErrorDetail(
            code=self.code,
            message=self.message,
            component=self.component,
            failed_stage=self.failed_stage,
            failed_check=self.failed_check,
            retryable=self.retryable,
        )


class InvalidJsonError(AppError):
    """
    Clase que indica que el cuerpo HTTP no pudo parsearse como JSON valido, resultado de una validacion sea de FastAPI o un error
    de Pydantic. Internamente define los mismos campos que AppError pero los configura con los parametros default para este error
    """
    status_code = 400
    code = "INVALID_JSON"
    message = Messages.INVALID_JSON
    component = "request_validation"
    failed_stage = "request_body_validation"
    failed_check = "json_parse"
    retryable = False


class InvalidContentLengthError(AppError):
    """
    Indica que `Content-Length` no es interpretable como valor valido. Esto puede ser por no tener un parametro de header
    correcto, o una longitud por encima del limite maximo de la aplicacion
    """
    status_code = 400
    code = "INVALID_CONTENT_LENGTH"
    message = Messages.INVALID_CONTENT_LENGTH
    component = "request_validation"
    failed_stage = "request_body_validation"
    failed_check = "content_length_header"
    retryable = False


class RequestTooLargeError(AppError):
    """
    Indica que el cuerpo HTTP excede el limite configurado. Esto puede darse sea por una validacion del header comparado con el
    contexto real del paquete, o sin header contra el limite del servidor
    """
    status_code = 413
    code = "REQUEST_TOO_LARGE"
    message = Messages.REQUEST_TOO_LARGE
    component = "request_validation"
    failed_stage = "request_body_validation"
    failed_check = "maximum_body_size"
    retryable = False


class RequestValidationAppError(AppError):
    """
    Indica que el schema de la solicitud fallo validacion. Esto se da por validaciones de
    Pydantic o por validaciones internas
    """
    status_code = 422
    code = "REQUEST_VALIDATION_FAILED"
    message = Messages.REQUEST_VALIDATION_FAILED
    component = "request_validation"
    failed_stage = "request_schema_validation"
    failed_check = "pydantic_schema"
    retryable = False


class ModelEtherTypeUnsupportedError(AppError):
    """
    Indica que el modelo solo acepta un EtherType soportado. Esto se valida principalmente a la hora de recibir
    una request de clasificacion, dado que el modelo esta entrenado para recibir el parametro con el valor de 2048 o IPv4
    """
    status_code = 422
    code = "MODEL_ETHERTYPE_UNSUPPORTED"
    message = Messages.MODEL_ETHERTYPE_UNSUPPORTED
    component = "request_validation"
    failed_stage = "model_input_validation"
    failed_check = "model_supported_eth_type"
    retryable = False


class ModelNotReadyError(AppError):
    """
    Indica que la API sigue viva pero no esta lista para clasificar.
    """
    status_code = 503
    code = "MODEL_NOT_READY"
    message = Messages.MODEL_NOT_READY
    component = "inference_service"
    failed_stage = "service_readiness"
    failed_check = "service_ready"
    retryable = True


class ModelInferenceFailedError(AppError):
    """
    Indica que la inferencia no pudo ejecutarse correctamente.

    Notas:
    - El caller externo aun debe preferir el comportamiento de fallback
      antes que reintentar inmediatamente el mismo paquete.
    """
    status_code = 500
    code = "MODEL_INFERENCE_FAILED"
    message = Messages.MODEL_INFERENCE_FAILED
    component = "inference_runtime"
    failed_stage = "request_inference"
    failed_check = "model_predict"
    retryable = True


class InferenceCapacityExceededError(AppError):
    """Indica que no hubo capacidad de inferencia disponible a tiempo."""

    status_code = 503
    code = "INFERENCE_CAPACITY_EXCEEDED"
    message = Messages.INFERENCE_CAPACITY_EXCEEDED
    component = "inference_capacity"
    failed_stage = "classifier_acquisition"
    failed_check = "classifier_available_before_timeout"
    retryable = True


class ModelOutputInvalidError(AppError):
    """
    Indica que la salida del clasificador no cumple el contrato esperado.
    """
    status_code = 500
    code = "MODEL_OUTPUT_INVALID"
    message = Messages.MODEL_OUTPUT_INVALID
    component = "inference_runtime"
    failed_stage = "model_output_validation"
    failed_check = "prediction_output_contract"
    retryable = False


class PolicyMappingFailedError(AppError):
    """
    Indica que la politica no pudo resolverse tras la clasificacion.

    Pasos:
    - Representa un fallo interno al traducir una clase predicha a politica.
    - Expone una clasificacion estable para respuestas y logs estructurados.
    - Evita que cada llamador tenga que repetir el mismo diagnostico base.
    """
    status_code = 500
    code = "POLICY_MAPPING_FAILED"
    message = Messages.POLICY_MAPPING_FAILED
    component = "policy_mapper"
    failed_stage = "policy_resolution"
    failed_check = "class_policy_resolution"
    retryable = False
