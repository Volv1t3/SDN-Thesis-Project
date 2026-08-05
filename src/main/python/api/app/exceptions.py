"""Define errores tipados y serializables usados por la API.

Pasos:
- Declara un contrato base para errores de aplicacion.
- Provee variantes concretas para validacion, readiness e inferencia.

Notas:
- Los errores se transforman en respuestas HTTP en `app.main`.
"""

from __future__ import annotations

from dataclasses import dataclass

from app.messages import Messages


@dataclass(slots=True)
class ErrorDetail:
    """Representa el detalle serializable de un error.

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
    """Base comun para errores funcionales de la API.

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
        """Inicializa un error de aplicacion con metadatos opcionales.

        Argumentos:
        - message: mensaje final del error o `None` para usar el predeterminado.
        - component: componente logico asociado al fallo.
        - failed_stage: etapa del flujo donde ocurrio el error.
        - failed_check: nombre del chequeo que fallo.
        - retryable: indica si el error es reintentable.
        - request_id: correlacion opcional de la solicitud generada por el servidor.
        """

        super().__init__(message or self.message)
        self.message = message or self.message
        self.component = component or self.component
        self.failed_stage = failed_stage or self.failed_stage
        self.failed_check = failed_check or self.failed_check
        self.retryable = retryable if retryable is not None else self.retryable
        self.request_id = request_id

    def to_error(self) -> ErrorDetail:
        """Convierte la excepcion al contrato serializable de error.

        Retorna:
        - ErrorDetail: estructura lista para respuestas HTTP.
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
    """Indica que el cuerpo HTTP no pudo parsearse como JSON valido.

    Pasos:
    - Representa un fallo de parseo previo a la validacion de schema.
    - Conserva metadatos estructurados utiles para logs y respuestas.
    - Permite diferenciar JSON invalido de errores de contrato Pydantic.
    """

    status_code = 400
    code = "INVALID_JSON"
    message = Messages.INVALID_JSON
    component = "request_validation"
    failed_stage = "request_body_validation"
    failed_check = "json_parse"
    retryable = False


class InvalidContentLengthError(AppError):
    """Indica que `Content-Length` no es interpretable como valor valido."""

    status_code = 400
    code = "INVALID_CONTENT_LENGTH"
    message = Messages.INVALID_CONTENT_LENGTH
    component = "request_validation"
    failed_stage = "request_body_validation"
    failed_check = "content_length_header"
    retryable = False


class RequestTooLargeError(AppError):
    """Indica que el cuerpo HTTP excede el limite configurado."""

    status_code = 413
    code = "REQUEST_TOO_LARGE"
    message = Messages.REQUEST_TOO_LARGE
    component = "request_validation"
    failed_stage = "request_body_validation"
    failed_check = "maximum_body_size"
    retryable = False


class RequestValidationAppError(AppError):
    """Indica que el schema de la solicitud fallo validacion.

    Pasos:
    - Representa errores de contrato detectados por Pydantic o FastAPI.
    - Expone una clasificacion comun para fallos de campos requeridos, extras o tipos.
    - Deja el detalle exacto del campo en la lista `details` de la respuesta.
    """

    status_code = 422
    code = "REQUEST_VALIDATION_FAILED"
    message = Messages.REQUEST_VALIDATION_FAILED
    component = "request_validation"
    failed_stage = "request_schema_validation"
    failed_check = "pydantic_schema"
    retryable = False


class ModelEtherTypeUnsupportedError(AppError):
    """Indica que el modelo solo acepta un EtherType soportado."""

    status_code = 422
    code = "MODEL_ETHERTYPE_UNSUPPORTED"
    message = Messages.MODEL_ETHERTYPE_UNSUPPORTED
    component = "request_validation"
    failed_stage = "model_input_validation"
    failed_check = "model_supported_eth_type"
    retryable = False


class ModelNotReadyError(AppError):
    """Indica que la API sigue viva pero no esta lista para clasificar."""

    status_code = 503
    code = "MODEL_NOT_READY"
    message = Messages.MODEL_NOT_READY
    component = "inference_service"
    failed_stage = "service_readiness"
    failed_check = "service_ready"
    retryable = True


class ModelInferenceFailedError(AppError):
    """Indica que la inferencia no pudo ejecutarse correctamente.

    Pasos:
    - Representa un fallo al invocar la etapa de prediccion del modelo.
    - Expone una clasificacion comun para respuesta HTTP y log estructurado.
    - Marca el evento como potencialmente reintentable a nivel tecnico.

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


class ModelOutputInvalidError(AppError):
    """Indica que la salida del clasificador no cumple el contrato esperado."""

    status_code = 500
    code = "MODEL_OUTPUT_INVALID"
    message = Messages.MODEL_OUTPUT_INVALID
    component = "inference_runtime"
    failed_stage = "model_output_validation"
    failed_check = "prediction_output_contract"
    retryable = False


class PolicyMappingFailedError(AppError):
    """Indica que la politica no pudo resolverse tras la clasificacion.

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
