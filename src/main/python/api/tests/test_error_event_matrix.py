"""Ejecuta una matriz unica de casos de error y observabilidad HTTP.

Pasos:
- Dispara una solicitud representativa por cada caso relevante de la API.
- Captura status, cuerpo, header `X-Request-ID` y ultimo log estructurado asociado.
- Imprime un resumen completo por caso para revision manual de la salida actual.

Notas:
- La prueba refleja el comportamiento real actual del servicio, incluso cuando
  un caso no produce log estructurado propio y el valor queda en `None`.
- El `request_id` del cliente nunca se envia en el cuerpo; toda correlacion
  proviene del middleware HTTP del servidor.
"""

from __future__ import annotations

import json
import logging
from contextlib import asynccontextmanager, contextmanager
from dataclasses import replace
from collections.abc import Callable
from typing import Any

from fastapi.testclient import TestClient

from app.api import inference as inference_api
from app.sdn_mpls_ml_exceptions import ModelInferenceFailedError, ModelNotReadyError, ModelOutputInvalidError
from app.model.predictor import PredictionResult
from app.sdn_mpls_ml_main import app


CaseExecutor = Callable[[TestClient], Any]


class InMemoryLogHandler(logging.Handler):
    """Acumula `LogRecord` en memoria para inspeccion puntual por solicitud.

    Pasos:
    - Recibe cada registro emitido por logging.
    - Lo agrega a una lista mutable en el orden recibido.
    - Permite limpiar el buffer entre solicitudes.
    """

    def __init__(self) -> None:
        """Inicializa el buffer vacio del handler."""

        super().__init__()
        self.records: list[logging.LogRecord] = []

    def emit(self, record: logging.LogRecord) -> None:
        """Guarda el registro entrante en memoria.

        Argumentos:
        - record: registro emitido por la aplicacion.
        """

        self.records.append(record)

    def clear(self) -> None:
        """Elimina los registros acumulados para un nuevo caso."""

        self.records.clear()


def _safe_json_body(response) -> dict[str, Any] | None:
    """Intenta decodificar el cuerpo JSON y retorna `None` si no es posible.

    Pasos:
    - Invoca `response.json()` sobre la respuesta capturada.
    - Absorbe errores de parseo cuando el cuerpo no es JSON valido.

    Argumentos:
    - response: respuesta HTTP obtenida con `TestClient`.

    Retorna:
    - dict[str, Any] | None: cuerpo JSON parseado o `None`.
    """

    try:
        body = response.json()
    except ValueError:
        return None
    if isinstance(body, dict):
        return body
    return {"value": body}


def _response_request_id(response_body: dict[str, Any] | None) -> str | None:
    """Extrae el `request_id` del cuerpo JSON cuando esta presente.

    Argumentos:
    - response_body: cuerpo JSON ya parseado o `None`.

    Retorna:
    - str | None: request ID del cuerpo o `None`.
    """

    if response_body is None:
        return None
    value = response_body.get("request_id")
    return value if isinstance(value, str) else None


def _find_structured_log_record(
    records: list[logging.LogRecord],
    *,
    request_id: str | None,
) -> logging.LogRecord | None:
    """Selecciona el ultimo log estructurado de la aplicacion para un request.

    Pasos:
    - Filtra solo loggers del namespace `app.`.
    - Prioriza coincidencia exacta del `request_id` si existe.
    - Si no existe correlacion aplicable, toma el ultimo evento estructurado.

    Argumentos:
    - records: lista de registros capturados por `caplog`.
    - request_id: correlacion HTTP de la respuesta analizada.

    Retorna:
    - logging.LogRecord | None: registro asociado o `None`.
    """

    app_records = [record for record in records if record.name.startswith("app.")]
    if not app_records:
        return None

    if request_id is not None:
        matching = [
            record for record in app_records if getattr(record, "request_id", None) == request_id
        ]
        if matching:
            return matching[-1]

    structured = [record for record in app_records if getattr(record, "event", None) is not None]
    if structured:
        return structured[-1]
    return app_records[-1]


def _record_to_dict(record: logging.LogRecord | None) -> dict[str, Any] | None:
    """Convierte un `LogRecord` estructurado a un diccionario serializable.

    Argumentos:
    - record: registro estructurado o `None`.

    Retorna:
    - dict[str, Any] | None: representacion serializable del registro.
    """

    if record is None:
        return None

    return {
        "logger": record.name,
        "level": record.levelname,
        "message": record.getMessage(),
        "request_id": getattr(record, "request_id", None),
        "event": getattr(record, "event", None),
        "http_status_code": getattr(record, "http_status_code", None),
        "error_code": getattr(record, "error_code", None),
        "component": getattr(record, "component", None),
        "failed_stage": getattr(record, "failed_stage", None),
        "failed_check": getattr(record, "failed_check", None),
        "retryable": getattr(record, "retryable", None),
        "fallback_reason": getattr(record, "fallback_reason", None),
    }


def _case_result(case_name: str, response, records: list[logging.LogRecord]) -> dict[str, Any]:
    """Construye la salida normalizada de un caso ejecutado.

    Pasos:
    - Parsea el cuerpo JSON cuando es posible.
    - Obtiene `X-Request-ID` del header y del cuerpo.
    - Busca el ultimo log estructurado relevante.
    - Calcula si la correlacion coincide entre respuesta y log.

    Argumentos:
    - case_name: nombre humano del caso evaluado.
    - response: respuesta HTTP capturada.
    - records: registros capturados durante la solicitud.

    Retorna:
    - dict[str, Any]: resumen completo del caso.
    """

    response_body = _safe_json_body(response)
    header_request_id = response.headers.get("X-Request-ID")
    body_request_id = _response_request_id(response_body)
    correlation_id = header_request_id or body_request_id
    matching_records = [
        record
        for record in records
        if record.name.startswith("app.") and getattr(record, "request_id", None) == correlation_id
    ]
    record = _find_structured_log_record(records, request_id=correlation_id)
    log_payload = _record_to_dict(record)
    log_request_id = None if log_payload is None else log_payload["request_id"]

    return {
        "case": case_name,
        "status_code": response.status_code,
        "response_headers": {"X-Request-ID": header_request_id},
        "response_body_json": response_body,
        "response_body_text": response.text,
        "log_lines": [_record_to_dict(record_item) for record_item in matching_records],
        "log_line": log_payload,
        "same_request_id_in_response_and_log": (
            None if correlation_id is None or log_request_id is None else correlation_id == log_request_id
        ),
        "current_event": None if log_payload is None else log_payload["event"],
        "current_http_status_code": None if log_payload is None else log_payload["http_status_code"],
        "current_error_code": None if log_payload is None else log_payload["error_code"],
        "current_component": None if log_payload is None else log_payload["component"],
        "current_failed_stage": None if log_payload is None else log_payload["failed_stage"],
        "current_failed_check": None if log_payload is None else log_payload["failed_check"],
        "current_retryable": None if log_payload is None else log_payload["retryable"],
    }


def _print_case_result(result: dict[str, Any]) -> None:
    """Imprime el resumen completo de un caso para revision manual.

    Argumentos:
    - result: salida consolidada del caso ejecutado.
    """

    print(json.dumps(result, indent=2, sort_keys=True))


def _run_case_with_caplog(
    *,
    log_handler: InMemoryLogHandler,
    client: TestClient,
    case_name: str,
    executor: CaseExecutor,
) -> dict[str, Any]:
    """Ejecuta un caso, limpia `caplog` y devuelve su resultado normalizado.

    Pasos:
    - Limpia registros previos para aislar el caso.
    - Ejecuta una unica solicitud HTTP.
    - Normaliza respuesta y log en una sola estructura.
    - Imprime el resultado para inspeccion manual.

    Argumentos:
    - caplog: fixture de pytest para captura de logs.
    - client: cliente HTTP sobre la API.
    - case_name: nombre descriptivo del escenario.
    - executor: funcion que realiza exactamente una solicitud.

    Retorna:
    - dict[str, Any]: resultado del caso ejecutado.
    """

    log_handler.clear()
    response = executor(client)
    result = _case_result(case_name, response, list(log_handler.records))
    _print_case_result(result)
    return result


@contextmanager
def _override_classifier_pool_predict(services, predict_callable):
    """Sustituye temporalmente el clasificador entregado por el pool."""

    class StubClassifier:
        def predict(self, packet_features: dict[str, int]) -> PredictionResult:
            return predict_callable(packet_features)

    original_acquire = services.classifier_pool.acquire

    @asynccontextmanager
    async def acquire(_timeout_seconds: float):
        yield StubClassifier()

    services.classifier_pool.acquire = acquire
    try:
        yield
    finally:
        services.classifier_pool.acquire = original_acquire


def _raise_model_inference_failed(_packet_features: dict[str, int]) -> PredictionResult:
    """Simula un fallo controlado de inferencia."""

    raise ModelInferenceFailedError()


def _raise_model_output_invalid(_packet_features: dict[str, int]) -> PredictionResult:
    """Simula un fallo controlado del contrato de salida."""

    raise ModelOutputInvalidError()


def _raise_runtime_error(_packet_features: dict[str, int]) -> PredictionResult:
    """Simula una excepcion no controlada durante la prediccion."""

    raise RuntimeError("controlled-unhandled-exception")


@contextmanager
def _override_run_sync(raised_exception: Exception):
    """Sustituye temporalmente `to_thread.run_sync` por un fallo controlado."""

    original_run_sync = inference_api.to_thread.run_sync

    async def fake_run_sync(*_args, **_kwargs):
        raise raised_exception

    inference_api.to_thread.run_sync = fake_run_sync
    try:
        yield
    finally:
        inference_api.to_thread.run_sync = original_run_sync


def run_error_event_matrix(
    *,
    log_handler: InMemoryLogHandler,
    client: TestClient,
    non_raising_client: TestClient,
) -> list[dict[str, Any]]:
    """Ejecuta toda la matriz de escenarios de observabilidad.

    Pasos:
    - Construye la lista ordenada de casos solicitados.
    - Ejecuta una solicitud por caso usando el cliente apropiado.
    - Imprime la salida completa de cada caso.

    Argumentos:
    - log_handler: handler en memoria para captura estructurada.
    - client: cliente principal con excepciones del servidor activas.
    - non_raising_client: cliente auxiliar que permite capturar respuestas 500.

    Retorna:
    - list[dict[str, Any]]: resultados en el mismo orden de ejecucion.
    """

    base_payload = {
        "packet_features": {
            "eth_type": 2048,
            "ip_proto": 17,
            "src_port": 53000,
            "dst_port": 53,
        }
    }

    services = client.app.state.services
    results: list[dict[str, Any]] = []

    results.append(
        _run_case_with_caplog(
            log_handler=log_handler,
            client=client,
            case_name="invalid_json",
            executor=lambda active_client: active_client.post(
                "/api/v1/classify",
                content='{"packet_features": ',
                headers={"Content-Type": "application/json"},
            ),
        )
    )
    results.append(
        _run_case_with_caplog(
            log_handler=log_handler,
            client=client,
            case_name="missing_required_field",
            executor=lambda active_client: active_client.post(
                "/api/v1/classify",
                json={
                    "packet_features": {
                        "eth_type": 2048,
                        "ip_proto": 17,
                        "src_port": 53000,
                    }
                },
            ),
        )
    )
    results.append(
        _run_case_with_caplog(
            log_handler=log_handler,
            client=client,
            case_name="extra_field",
            executor=lambda active_client: active_client.post(
                "/api/v1/classify",
                json={
                    "packet_features": {
                        "eth_type": 2048,
                        "ip_proto": 17,
                        "src_port": 53000,
                        "dst_port": 53,
                        "extra_field": "unexpected",
                    }
                },
            ),
        )
    )
    results.append(
        _run_case_with_caplog(
            log_handler=log_handler,
            client=client,
            case_name="wrong_field_type",
            executor=lambda active_client: active_client.post(
                "/api/v1/classify",
                json={
                    "packet_features": {
                        "eth_type": "2048",
                        "ip_proto": 17,
                        "src_port": 53000,
                        "dst_port": 53,
                    }
                },
            ),
        )
    )
    results.append(
        _run_case_with_caplog(
            log_handler=log_handler,
            client=client,
            case_name="unsupported_ethertype_model_mode",
            executor=lambda active_client: active_client.post(
                "/api/v1/classify",
                json={
                    "packet_features": {
                        "eth_type": 34525,
                        "ip_proto": 6,
                        "src_port": 49152,
                        "dst_port": 443,
                    }
                },
            ),
        )
    )
    results.append(
        _run_case_with_caplog(
            log_handler=log_handler,
            client=client,
            case_name="request_body_too_large",
            executor=lambda active_client: active_client.post(
                "/api/v1/classify",
                content=json.dumps({"packet_features": {"eth_type": 2048}, "padding": "x" * 20000}),
                headers={"Content-Type": "application/json"},
            ),
        )
    )

    original_readiness = services.readiness
    try:
        services.readiness = replace(
            original_readiness,
            initialization_completed=True,
            ready=False,
            failed_stage="runtime_model_compatibility",
            error_code="MODEL_LOAD_FAILED",
            error_message="The model failed during startup validation.",
            error_component="model_runtime",
            failed_check="booster_load",
            retryable=True,
        )
        results.append(
            _run_case_with_caplog(
                log_handler=log_handler,
                client=client,
                case_name="api_alive_but_model_not_ready",
                executor=lambda active_client: active_client.post("/api/v1/classify", json=base_payload),
            )
        )
    finally:
        services.readiness = original_readiness

    original_resolve = services.policy_mapper.resolve
    try:
        services.policy_mapper.resolve = lambda *args, **kwargs: (_ for _ in ()).throw(RuntimeError("forced-policy-failure"))
        results.append(
            _run_case_with_caplog(
                log_handler=log_handler,
                client=client,
                case_name="policy_mapping_failure",
                executor=lambda active_client: active_client.post("/api/v1/classify", json=base_payload),
            )
        )
    finally:
        services.policy_mapper.resolve = original_resolve

    try:
        with _override_run_sync(ModelInferenceFailedError()):
            results.append(
                _run_case_with_caplog(
                    log_handler=log_handler,
                    client=client,
                    case_name="model_inference_failure",
                    executor=lambda active_client: active_client.post("/api/v1/classify", json=base_payload),
                )
            )
    finally:
        pass

    try:
        with _override_run_sync(ModelOutputInvalidError()):
            results.append(
                _run_case_with_caplog(
                    log_handler=log_handler,
                    client=client,
                    case_name="invalid_model_output",
                    executor=lambda active_client: active_client.post("/api/v1/classify", json=base_payload),
                )
            )
    finally:
        pass

    original_min_policy_confidence = services.policy_mapper._min_policy_confidence
    try:
        services.policy_mapper._min_policy_confidence = 0.95
        results.append(
            _run_case_with_caplog(
                log_handler=log_handler,
                client=client,
                case_name="low_confidence_policy_fallback",
                executor=lambda active_client: active_client.post("/api/v1/classify", json=base_payload),
            )
        )
    finally:
        services.policy_mapper._min_policy_confidence = original_min_policy_confidence

    results.append(
        _run_case_with_caplog(
            log_handler=log_handler,
            client=client,
            case_name="unknown_route_http_404",
            executor=lambda active_client: active_client.get("/api/v1/does-not-exist"),
        )
    )

    try:
        with _override_run_sync(RuntimeError("controlled-unhandled-exception")):
            results.append(
                _run_case_with_caplog(
                    log_handler=log_handler,
                    client=non_raising_client,
                    case_name="unhandled_internal_exception",
                    executor=lambda active_client: active_client.post("/api/v1/classify", json=base_payload),
                )
            )
    finally:
        pass

    return results


def test_error_event_matrix(client):
    """Ejecuta e imprime la matriz completa de errores y eventos observables.

    Pasos:
    - Crea un cliente auxiliar que no repropaga excepciones 500.
    - Ejecuta la matriz completa de escenarios.
    - Verifica codigos base y correlacion minima esperada.

    Argumentos:
    - client: fixture principal de la API en modo `MODEL`.
    """

    root_logger = logging.getLogger()
    with TestClient(app, raise_server_exceptions=False) as non_raising_client:
        log_handler = InMemoryLogHandler()
        log_handler.setLevel(logging.INFO)
        root_logger.addHandler(log_handler)

        try:
            results = run_error_event_matrix(
                log_handler=log_handler,
                client=client,
                non_raising_client=non_raising_client,
            )
        finally:
            root_logger.removeHandler(log_handler)

    indexed = {result["case"]: result for result in results}

    assert indexed["invalid_json"]["status_code"] == 400
    assert indexed["missing_required_field"]["status_code"] == 422
    assert indexed["extra_field"]["status_code"] == 422
    assert indexed["wrong_field_type"]["status_code"] == 422
    assert indexed["unsupported_ethertype_model_mode"]["status_code"] == 422
    assert indexed["request_body_too_large"]["status_code"] == 413
    assert indexed["api_alive_but_model_not_ready"]["status_code"] == 503
    assert indexed["policy_mapping_failure"]["status_code"] == 500
    assert indexed["model_inference_failure"]["status_code"] == 500
    assert indexed["invalid_model_output"]["status_code"] == 500
    assert indexed["low_confidence_policy_fallback"]["status_code"] == 200
    assert indexed["unknown_route_http_404"]["status_code"] == 404
    assert indexed["unhandled_internal_exception"]["status_code"] == 500

    for result in results:
        assert result["response_headers"]["X-Request-ID"] is not None

        response_body = result["response_body_json"]
        if isinstance(response_body, dict) and "request_id" in response_body:
            assert response_body["request_id"] == result["response_headers"]["X-Request-ID"]

    assert indexed["unhandled_internal_exception"]["response_headers"]["X-Request-ID"] is not None
    assert indexed["unhandled_internal_exception"]["response_body_json"] is not None

    fallback_body = indexed["low_confidence_policy_fallback"]["response_body_json"]
    assert fallback_body is not None
    assert fallback_body["policy"]["policy_fallback"] is True
    assert fallback_body["policy"]["policy_fallback_reason"] == "confidence_below_threshold"
    invalid_json_error = indexed["invalid_json"]["response_body_json"]["error"]
    assert invalid_json_error["component"] == "request_validation"
    assert invalid_json_error["failed_stage"] == "request_body_validation"
    assert invalid_json_error["failed_check"] == "json_parse"
    assert invalid_json_error["retryable"] is False
    assert indexed["invalid_json"]["current_event"] == "invalid_json_received"
    assert indexed["invalid_json"]["current_error_code"] == "INVALID_JSON"
    assert indexed["invalid_json"]["current_component"] == "request_validation"
    assert indexed["invalid_json"]["current_failed_stage"] == "request_body_validation"
    assert indexed["invalid_json"]["current_failed_check"] == "json_parse"
    assert indexed["invalid_json"]["current_retryable"] is False
    for case_name in ("missing_required_field", "extra_field", "wrong_field_type"):
        error_body = indexed[case_name]["response_body_json"]["error"]
        assert error_body["message"] == "El cuerpo de la solicitud no cumple con el contrato requerido."
        assert error_body["component"] == "request_validation"
        assert error_body["failed_stage"] == "request_schema_validation"
        assert error_body["failed_check"] == "pydantic_schema"
        assert error_body["retryable"] is False

        assert indexed[case_name]["current_event"] == "request_validation_failed"
        assert indexed[case_name]["current_error_code"] == "REQUEST_VALIDATION_FAILED"
        assert indexed[case_name]["current_component"] == "request_validation"
        assert indexed[case_name]["current_failed_stage"] == "request_schema_validation"
        assert indexed[case_name]["current_failed_check"] == "pydantic_schema"
        assert indexed[case_name]["current_retryable"] is False

    assert indexed["unsupported_ethertype_model_mode"]["current_component"] == "request_validation"
    assert indexed["unsupported_ethertype_model_mode"]["current_failed_stage"] == "model_input_validation"
    assert indexed["unsupported_ethertype_model_mode"]["current_failed_check"] == "model_supported_eth_type"
    assert indexed["unsupported_ethertype_model_mode"]["current_retryable"] is False
    too_large_error = indexed["request_body_too_large"]["response_body_json"]["error"]
    assert too_large_error["component"] == "request_validation"
    assert too_large_error["failed_stage"] == "request_body_validation"
    assert too_large_error["failed_check"] == "maximum_body_size"
    assert too_large_error["retryable"] is False
    assert indexed["request_body_too_large"]["current_event"] == "request_too_large"
    assert indexed["request_body_too_large"]["current_error_code"] == "REQUEST_TOO_LARGE"
    assert indexed["request_body_too_large"]["current_component"] == "request_validation"
    assert indexed["request_body_too_large"]["current_failed_stage"] == "request_body_validation"
    assert indexed["request_body_too_large"]["current_failed_check"] == "maximum_body_size"
    assert indexed["request_body_too_large"]["current_retryable"] is False
    not_ready_error = indexed["api_alive_but_model_not_ready"]["response_body_json"]["error"]
    assert not_ready_error["component"] == "model_runtime"
    assert not_ready_error["failed_stage"] == "runtime_model_compatibility"
    assert not_ready_error["failed_check"] == "booster_load"
    assert not_ready_error["retryable"] is True
    assert indexed["api_alive_but_model_not_ready"]["current_event"] == "model_not_ready"
    assert indexed["api_alive_but_model_not_ready"]["current_error_code"] == "MODEL_NOT_READY"
    assert indexed["api_alive_but_model_not_ready"]["current_component"] == "model_runtime"
    assert indexed["api_alive_but_model_not_ready"]["current_failed_stage"] == "runtime_model_compatibility"
    assert indexed["api_alive_but_model_not_ready"]["current_failed_check"] == "booster_load"
    assert indexed["api_alive_but_model_not_ready"]["current_retryable"] is True
    policy_error = indexed["policy_mapping_failure"]["response_body_json"]["error"]
    assert policy_error["component"] == "policy_mapper"
    assert policy_error["failed_stage"] == "policy_resolution"
    assert policy_error["failed_check"] == "class_policy_resolution"
    assert policy_error["retryable"] is False

    assert indexed["policy_mapping_failure"]["current_component"] == "policy_mapper"
    assert indexed["policy_mapping_failure"]["current_failed_stage"] == "policy_resolution"
    assert indexed["policy_mapping_failure"]["current_failed_check"] == "class_policy_resolution"
    assert indexed["policy_mapping_failure"]["current_retryable"] is False
    inference_error = indexed["model_inference_failure"]["response_body_json"]["error"]
    assert inference_error["component"] == "inference_runtime"
    assert inference_error["failed_stage"] == "request_inference"
    assert inference_error["failed_check"] == "model_predict"
    assert inference_error["retryable"] is True

    assert indexed["model_inference_failure"]["current_event"] == "model_inference_failed"
    assert indexed["model_inference_failure"]["current_error_code"] == "MODEL_INFERENCE_FAILED"
    assert indexed["model_inference_failure"]["current_component"] == "inference_runtime"
    assert indexed["model_inference_failure"]["current_failed_stage"] == "request_inference"
    assert indexed["model_inference_failure"]["current_failed_check"] == "model_predict"
    assert indexed["model_inference_failure"]["current_retryable"] is True
    output_error = indexed["invalid_model_output"]["response_body_json"]["error"]
    assert output_error["component"] == "inference_runtime"
    assert output_error["failed_stage"] == "model_output_validation"
    assert output_error["failed_check"] == "prediction_output_contract"
    assert output_error["retryable"] is False
    assert indexed["invalid_model_output"]["current_event"] == "model_output_invalid"
    assert indexed["invalid_model_output"]["current_error_code"] == "MODEL_OUTPUT_INVALID"
    assert indexed["invalid_model_output"]["current_component"] == "inference_runtime"
    assert indexed["invalid_model_output"]["current_failed_stage"] == "model_output_validation"
    assert indexed["invalid_model_output"]["current_failed_check"] == "prediction_output_contract"
    assert indexed["invalid_model_output"]["current_retryable"] is False
    fallback_events = [
        log_line["event"]
        for log_line in indexed["low_confidence_policy_fallback"]["log_lines"]
        if log_line is not None
    ]
    assert fallback_events == ["policy_fallback_applied", "classification_completed"]
    fallback_log = indexed["low_confidence_policy_fallback"]["log_lines"][0]
    assert fallback_log["component"] == "policy_mapper"
    assert fallback_log["fallback_reason"] == "confidence_below_threshold"
    not_found_error = indexed["unknown_route_http_404"]["response_body_json"]["error"]
    assert not_found_error["code"] == "HTTP_NOT_FOUND"
    assert not_found_error["component"] == "http_routing"
    assert not_found_error["failed_stage"] == "request_routing"
    assert not_found_error["failed_check"] == "route_resolution"
    assert not_found_error["retryable"] is False

    assert indexed["unknown_route_http_404"]["current_event"] == "http_request_not_found"
    assert indexed["unknown_route_http_404"]["current_http_status_code"] == 404
    assert indexed["unknown_route_http_404"]["current_error_code"] == "HTTP_NOT_FOUND"
    assert indexed["unknown_route_http_404"]["current_component"] == "http_routing"
    assert indexed["unknown_route_http_404"]["current_failed_stage"] == "request_routing"
    assert indexed["unknown_route_http_404"]["current_failed_check"] == "route_resolution"
    assert indexed["unknown_route_http_404"]["current_retryable"] is False

    internal_error = indexed["unhandled_internal_exception"]["response_body_json"]["error"]
    assert internal_error["code"] == "INTERNAL_ERROR"
    assert internal_error["message"] == "Ocurrio un error interno inesperado."
    assert internal_error["component"] == "application"
    assert internal_error["failed_stage"] == "request_processing"
    assert internal_error["failed_check"] == "unhandled_exception"
    assert internal_error["retryable"] is True

    assert indexed["unhandled_internal_exception"]["current_event"] == "unhandled_exception"
    assert indexed["unhandled_internal_exception"]["current_http_status_code"] == 500
    assert indexed["unhandled_internal_exception"]["current_error_code"] == "INTERNAL_ERROR"
    assert indexed["unhandled_internal_exception"]["current_component"] == "application"
    assert indexed["unhandled_internal_exception"]["current_failed_stage"] == "request_processing"
    assert indexed["unhandled_internal_exception"]["current_failed_check"] == "unhandled_exception"
    assert indexed["unhandled_internal_exception"]["current_retryable"] is True


def test_model_not_ready_error_defaults():
    """Verifica los metadatos por defecto de `ModelNotReadyError`.

    Pasos:
    - Instancia el error sin overrides dinamicos.
    - Convierte la excepcion a `ErrorDetail`.
    - Confirma la clasificacion base de readiness no disponible.
    """

    error = ModelNotReadyError()
    detail = error.to_error()

    assert detail.code == "MODEL_NOT_READY"
    assert detail.message == "El servicio de inferencia no esta listo."
    assert detail.component == "inference_service"
    assert detail.failed_stage == "service_readiness"
    assert detail.failed_check == "service_ready"
    assert detail.retryable is True


def test_classify_returns_classifier_to_pool_after_unexpected_prediction_failure(client, monkeypatch):
    """Una excepcion no tipada tampoco deja capacidad del pool retenida."""

    async def raise_from_worker(*_args, **_kwargs):
        raise RuntimeError("controlled-unhandled-exception")

    monkeypatch.setattr(inference_api.to_thread, "run_sync", raise_from_worker)
    with TestClient(app, raise_server_exceptions=False) as non_raising_client:
        pool = non_raising_client.app.state.services.classifier_pool
        assert pool is not None
        response = non_raising_client.post(
            "/api/v1/classify",
            json={
                "packet_features": {
                    "eth_type": 2048,
                    "ip_proto": 6,
                    "src_port": 51514,
                    "dst_port": 443,
                }
            },
        )

        assert response.status_code == 500
        assert response.json()["error"]["code"] == "INTERNAL_ERROR"
        assert pool.available == pool.capacity
        assert pool.borrowed == 0
