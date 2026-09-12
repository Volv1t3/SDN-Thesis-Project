# Python API Process And Method Reference

This Writerside-ready reference complements `python-api-documentation.md` with method-level and process-level coverage for the complete `src/main/python/api` tree.

## Runtime Entry Points

| Entry point | Source | Trigger | Process |
|---|---|---|---|
| `uvicorn app.sdn_mpls_ml_main:app` | `Dockerfile` | Container start | Runs the FastAPI application with configured host, port, and one worker by default. |
| `lifespan(app)` | `app/sdn_mpls_ml_main.py` | FastAPI startup | Loads raw settings, initializes process identity, configures JSON logging, builds services, initializes readiness, and logs metrics state. |
| `CorrelationIdMiddleware.__call__` | `app/middleware/sdn_mpls_ml_correlation_middleware.py` | Every HTTP request | Generates authoritative server-side `X-Request-ID`, stores it in ASGI state, wraps `send`, and provides the final internal-error barrier. |
| `RequestSizeLimitMiddleware.__call__` | `app/middleware/sdn_mpls_ml_request_size_validation_middleware.py` | Every HTTP request after correlation | Validates `Content-Length`, enforces configured body-size limits, buffers safe bodies, and replays validated bytes to FastAPI. |
| `root(request)` | `app/api/sdn_mpls_ml_health_route.py` | `GET /` | Returns service name, version, running status, and documentation path. |
| `live()` | `app/api/sdn_mpls_ml_health_route.py` | `GET /health/live` | Returns a minimal process-alive response without model checks. |
| `ready(request)` | `app/api/sdn_mpls_ml_health_route.py` | `GET /health/ready` | Returns cached readiness details or a 503 readiness error with request correlation. |
| `model_info(request)` | `app/api/sdn_mpls_ml_inference_route.py` | `GET /api/v1/model` | Requires readiness and returns model metadata/class map. |
| `classify(payload, request)` | `app/api/sdn_mpls_ml_inference_route.py` | `POST /api/v1/classify` | Validates readiness and mode, leases classifier, runs inference, maps policy, records metrics, and returns classification. |
| `metrics(request)` | `app/api/sdn_mpls_ml_metrics_route.py` | `GET /metrics` | Returns Prometheus exposition when enabled, otherwise 404. |

## Full Classification Request Process

| Step | Class/function | Input | Validation or action | Output |
|---|---|---|---|---|
| 1 | `CorrelationIdMiddleware.__call__` | ASGI `scope`, `receive`, `send` | Skips non-HTTP scopes; generates UUID request ID for HTTP scopes. | `scope["state"]["request_id"]` and eventual `X-Request-ID`. |
| 2 | `send_with_correlation` | ASGI response messages | Adds `X-Request-ID` to `http.response.start` if absent. | Correlated response headers. |
| 3 | `RequestSizeLimitMiddleware.__call__` | ASGI HTTP stream | Reads configured `request_size_limit_bytes` and parses `Content-Length`. | Either early error or buffered body. |
| 4 | `_parse_content_length` | ASGI headers | Rejects non-ASCII, non-integer, or negative values. | Integer length, `None`, or `_INVALID_CONTENT_LENGTH`. |
| 5 | `_record_rejection` | Middleware rejection | Records request body rejection and request error metrics if enabled. | Prometheus counters. |
| 6 | `_build_replay_receive` | Accepted request body bytes | Creates a replacement ASGI receive callable with a single full body message. | FastAPI receives the same validated body. |
| 7 | FastAPI/Pydantic | JSON body | Parses `ClassifyRequest` and nested `PacketFeatures`; rejects unknown fields. | Validated Pydantic object or `RequestValidationError`. |
| 8 | `PacketFeatures.validate_protocol_rules` | Packet features | Requires ports to be zero for non-TCP/non-UDP protocols. | Valid packet features or validation error. |
| 9 | `handle_request_validation` | FastAPI validation error | Distinguishes invalid JSON from schema violations and redacts body metadata. | Structured 400 or 422. |
| 10 | `classify` | Valid request and app state | Checks readiness, pool, AnyIO limiter, and validated settings. | Continues or raises `ModelNotReadyError`. |
| 11 | `validate_packet_for_classification_mode` | Classification mode and EtherType | Allows all EtherTypes in deterministic mode; requires IPv4 EtherType in model mode. | Continues or raises `ModelEtherTypeUnsupportedError`. |
| 12 | `ClassifierPool.acquire` | Timeout seconds | Waits for one classifier from bounded async queue. | Leased classifier or `InferenceCapacityExceededError`. |
| 13 | `execute_instrumented_inference` | Classifier and packet features | Runs `classifier.predict` in AnyIO worker thread with limiter. | `PredictionResult` plus inference-duration metric. |
| 14 | `Predictor.predict` | Packet features | Builds XGBoost input, predicts, validates probability vector, decodes class. | Normalized `PredictionResult`. |
| 15 | `DeterministicClassifier.predict` | Packet features | Applies protocol, destination port, source port, and streaming fallback rules. | One-hot `PredictionResult`. |
| 16 | `PolicyMapper.resolve` | Predicted class and confidence | Uses default policy if confidence is below threshold, otherwise class policy. | `TrafficPolicy`, fallback flag, fallback reason. |
| 17 | `classify` | Prediction and policy | Builds `ClassifyResponse`, logs completion, updates result/confidence/policy metrics. | HTTP 200 response. |
| 18 | `ClassificationObservation.finish` | Outcome and start time | Increments request counter and observes end-to-end classification duration once. | Prometheus metrics. |

## Startup Validation Process

| Stage | Function | Checks | Failure model | Success state |
|---|---|---|---|---|
| Initial container | `create_initial_services` | Raw settings availability and initial readiness mode display. | None; creates initializing state. | `AppServices` with empty runtime dependencies. |
| Build wrapper | `build_services` | Calls initialization exactly once for this process. | Cached in `ReadinessState`. | Fully initialized or not-ready services. |
| Metrics prelude | `initialize_services` | Worker identity, startup timer, readiness gauge false. | Startup metrics still recorded when enabled. | Startup validation begins. |
| Configuration preflight | `_validate_settings` | App name/version, port, log level, metrics path, booleans, timeouts, body size, confidence, mode, bandwidth bounds, pool size, required filenames. | `StartupValidationError` with stage `configuration_preflight`. | `ValidatedSettings`. |
| Artifact checks | `_validate_artifacts` and `_validate_artifact` | Required model, metadata, policy, or deterministic-rule files exist, are regular files, readable, non-empty, and UTF-8 readable. | `StartupValidationError` with stage `artifact_checks`. | `ArtifactPaths`. |
| Schema validation | `_validate_schemas` | Model metadata or deterministic rules plus policy file parse and Pydantic validation. | `StartupValidationError` with stage `metadata_and_policy_schema_validation`. | `ModelMetadata`, `PolicyFile`, optional `DeterministicRuleFile`. |
| Runtime compatibility | `_build_classifier_pool`, `_build_single_classifier`, `_load_predictor` | Builds every classifier, imports XGBoost in model mode, loads booster, verifies feature count/objective/class count, runs synthetic prediction. | `StartupValidationError` with stage `runtime_model_compatibility`. | `ClassifierPool` and `model_loaded` or deterministic classifier instances. |
| Synthetic output | `_run_synthetic_self_test` | Probability cardinality, finite values, range, class ID, and class name. | `MODEL_OUTPUT_INVALID` or `MODEL_SELF_TEST_FAILED`. | `synthetic_inference_passed=True`. |
| Complete policy map | `_validate_complete_policy_map` | Unknown classes, missing classes, default profile bandwidth, serialization, and per-class bandwidth bounds. | `StartupValidationError` with stage `complete_policy_map_validation`. | Policy mapper can be constructed. |
| Ready mark | `ReadinessState.mark_ready` | Startup completed successfully. | Not applicable. | `ready=True`, model summary fields populated. |
| Failure mark | `ReadinessState.mark_failed` | Any controlled startup failure. | Cached not-ready diagnostics. | API remains live but returns not-ready classification responses. |

## Middleware Branch Matrix

| Branch | Trigger | Producer | Response | Metrics |
|---|---|---|---|---|
| Non-HTTP passthrough | `scope["type"] != "http"` | Both middlewares | Delegates unchanged. | None. |
| Correlated success | Any HTTP response from downstream app | `CorrelationIdMiddleware` | Adds `X-Request-ID`. | None directly. |
| Correlation fallback internal error | Unhandled exception escaping downstream | `CorrelationIdMiddleware` | `500 INTERNAL_ERROR`. | `sdn_mpls_ml_api_obs_request_errors_total{error_code="INTERNAL_ERROR",component="application"}`. |
| Invalid content length | Non-integer or negative `Content-Length` | `RequestSizeLimitMiddleware` | `400 INVALID_CONTENT_LENGTH`. | Body rejection and request error counters. |
| Declared body too large | `Content-Length > max_request_body_bytes` | `RequestSizeLimitMiddleware` | `413 REQUEST_TOO_LARGE`. | Body rejection reason `declared_size_exceeded`. |
| Actual body too large | Streamed bytes exceed limit | `RequestSizeLimitMiddleware` | `413 REQUEST_TOO_LARGE`. | Body rejection reason `actual_size_exceeded`. |
| Client disconnect during body read | `http.disconnect` before full body | `RequestSizeLimitMiddleware` | Replays what was buffered and lets FastAPI decide. | Debug log only. |
| Body accepted | Content is within limit | `RequestSizeLimitMiddleware` | Passes to FastAPI through replay receive. | Debug trace only. |

## Exception Handler Matrix

| Handler | Error source | Classification | Response |
|---|---|---|---|
| `handle_app_error` | Any `AppError` subclass | Uses embedded code/component/stage/check/retryability and request correlation. | Structured `ErrorResponse` with subclass status code. |
| `handle_request_validation` | FastAPI/Pydantic validation | `INVALID_JSON` for `json_invalid`, otherwise `REQUEST_VALIDATION_FAILED`. | 400 or 422 with safe validation details. |
| `handle_json_error` | Raw JSON decode error | `INVALID_JSON`. | 400. |
| `handle_http_exception` | Starlette HTTP exception | Special cases 413 and 404, normalizes all other HTTP errors. | Structured `ErrorResponse`. |
| `handle_unhandled_exception` | Any uncaught exception | `INTERNAL_ERROR`. | 500 retryable error. |

## Class And Method Reference

| Class | Module | Key methods/properties | Responsibility |
|---|---|---|---|
| `ClassificationMode` | `app.sdn_mpls_ml_config` | `response_value` | Runtime enum for XGBoost model mode and deterministic test mode. |
| `RawSettings` | `app.sdn_mpls_ml_config` | Pydantic field aliases | Environment-backed raw configuration. |
| `ValidatedSettings` | `app.sdn_mpls_ml_config` | dataclass fields | Typed semantic configuration after startup validation. |
| `ArtifactPaths` | `app.sdn_mpls_ml_dependencies` | dataclass fields | Resolved startup artifact paths. |
| `AppServices` | `app.sdn_mpls_ml_dependencies` | dataclass fields | Process-wide service container stored in `app.state.services`. |
| `StartupValidationError` | `app.sdn_mpls_ml_readiness` | `__str__` | Structured startup failure cached by readiness. |
| `ReadinessState` | `app.sdn_mpls_ml_readiness` | `initializing`, `mark_failed`, `mark_ready`, `status` | Cached readiness result and diagnostics. |
| `ErrorDetail` | `app.sdn_mpls_ml_exceptions` | dataclass fields | Serializable detail for controlled errors. |
| `AppError` | `app.sdn_mpls_ml_exceptions` | `__init__`, `to_error` | Base controlled HTTP/domain exception. |
| `InvalidJsonError` | `app.sdn_mpls_ml_exceptions` | class attributes | 400 invalid JSON error contract. |
| `InvalidContentLengthError` | `app.sdn_mpls_ml_exceptions` | class attributes | 400 invalid `Content-Length` contract. |
| `RequestTooLargeError` | `app.sdn_mpls_ml_exceptions` | class attributes | 413 request body too large contract. |
| `RequestValidationAppError` | `app.sdn_mpls_ml_exceptions` | class attributes | 422 request schema validation contract. |
| `ModelEtherTypeUnsupportedError` | `app.sdn_mpls_ml_exceptions` | class attributes | 422 model-mode EtherType rejection. |
| `ModelNotReadyError` | `app.sdn_mpls_ml_exceptions` | class attributes | 503 inference not ready. |
| `ModelInferenceFailedError` | `app.sdn_mpls_ml_exceptions` | class attributes | 500 predictor runtime failure. |
| `InferenceCapacityExceededError` | `app.sdn_mpls_ml_exceptions` | class attributes | 503 classifier pool wait timeout. |
| `ModelOutputInvalidError` | `app.sdn_mpls_ml_exceptions` | class attributes | 500 invalid model output contract. |
| `PolicyMappingFailedError` | `app.sdn_mpls_ml_exceptions` | class attributes | 500 class-to-policy resolution failure. |
| `CorrelationIdMiddleware` | `app.middleware.sdn_mpls_ml_correlation_middleware` | `__init__`, `__call__` | Request correlation and final error guard. |
| `RequestSizeLimitMiddleware` | `app.middleware.sdn_mpls_ml_request_size_validation_middleware` | `__init__`, `__call__` | Body-size enforcement and replay receive construction. |
| `_InvalidContentLengthSentinel` | `app.middleware.sdn_mpls_ml_request_size_validation_middleware` | marker only | Differentiates invalid length from absent length. |
| `StrictBaseModel` | `app.schemas.sdn_mpls_ml_baseline_validation_models` | Pydantic config | Common strict HTTP schema base. |
| `ErrorBody` | `app.schemas.sdn_mpls_ml_baseline_validation_models` | schema fields | HTTP error body. |
| `ErrorResponse` | `app.schemas.sdn_mpls_ml_baseline_validation_models` | schema fields | HTTP error envelope with optional request ID. |
| `RootResponse` | `app.schemas.sdn_mpls_ml_health_validation_models` | schema fields | Root endpoint payload. |
| `LivenessResponse` | `app.schemas.sdn_mpls_ml_health_validation_models` | schema fields | Liveness payload. |
| `ReadinessError` | `app.schemas.sdn_mpls_ml_health_validation_models` | schema fields | Nested readiness error payload. |
| `ReadySuccessResponse` | `app.schemas.sdn_mpls_ml_health_validation_models` | schema fields | Ready-state payload. |
| `ReadyFailureResponse` | `app.schemas.sdn_mpls_ml_health_validation_models` | schema fields | Initializing/not-ready payload. |
| `PacketFeatures` | `app.schemas.sdn_mpls_ml_inference_validation_models` | `validate_protocol_rules` | Packet feature request contract. |
| `ClassifyRequest` | `app.schemas.sdn_mpls_ml_inference_validation_models` | schema fields | Classification request envelope. |
| `PredictionBody` | `app.schemas.sdn_mpls_ml_inference_validation_models` | schema fields | Prediction response segment. |
| `PathConstraints` | `app.schemas.sdn_mpls_ml_inference_validation_models` | schema fields | HTTP response path constraint segment. |
| `PolicyResponse` | `app.schemas.sdn_mpls_ml_inference_validation_models` | schema fields | HTTP response policy segment. |
| `ClassifyResponse` | `app.schemas.sdn_mpls_ml_inference_validation_models` | schema fields | Successful classification response. |
| `ModelClassInfo` | `app.schemas.sdn_mpls_ml_inference_validation_models` | schema fields | Class metadata item. |
| `ModelInfoResponse` | `app.schemas.sdn_mpls_ml_inference_validation_models` | schema fields | Model metadata endpoint payload. |
| `ClassifierPoolObserver` | `app.model.sdn_mpls_ml_classifier_pool` | `observe_wait`, `set_state`, `record_timeout` | Protocol consumed by classifier pool for metrics. |
| `ClassifierPool` | `app.model.sdn_mpls_ml_classifier_pool` | `capacity`, `available`, `borrowed`, `acquire`, `_observe_wait`, `_publish_state` | Async bounded classifier lease pool. |
| `PredictionResult` | `app.model.sdn_mpls_ml_model_predictor` | dataclass fields | Normalized internal prediction result. |
| `Predictor` | `app.model.sdn_mpls_ml_model_predictor` | `build_feature_matrix`, `predict`, `_normalize_output` | XGBoost classifier adapter. |
| `DeterministicClassifier` | `app.model.sdn_mpls_ml_deterministic_predictor` | `predict`, `_classify` | Rule-based deterministic classifier. |
| `DeterministicRuleFile` | `app.model.sdn_mpls_ml_deterministic_rules` | `validate_port_map_keys`, `validate_contract` | Deterministic rule artifact schema. |
| `ModelMetadata` | `app.model.sdn_mpls_ml_metadata` | `validate_schema_version`, `validate_contract`, `classes` | XGBoost metadata artifact schema. |
| `TrafficClassifier` | `app.model.sdn_mpls_ml_protocols` | `predict` | Runtime protocol implemented by both classifier types. |
| `PathConstraints` | `app.policy.sdn_mpls_ml_policy_validation_models` | schema fields | Policy-file path constraints. |
| `TrafficPolicy` | `app.policy.sdn_mpls_ml_policy_validation_models` | `validate_profile_name` | Policy-file traffic profile. |
| `PolicyFile` | `app.policy.sdn_mpls_ml_policy_validation_models` | `validate_schema_version` | Root policy mapping artifact. |
| `PolicyMapper` | `app.policy.sdn_mpls_ml_policy_mapper` | `resolve`, `default_policy` | Predicted-class to traffic-policy mapper. |
| `ProcessIdentity` | `app.observability.sdn_mpls_ml_identity` | dataclass fields | Stable per-worker identity. |
| `BaselineMetrics` | `app.observability.sdn_mpls_ml_metrics` | `initialize_worker`, `set_readiness`, `set_pool_state`, `set_state`, `observe_wait`, `record_timeout` | Startup, readiness, worker, and pool metric producer. |
| `ClassificationObservation` | `app.observability.sdn_mpls_ml_classification_metrics` | `mark_outcome`, `finish` | Per-request classification metric lifecycle. |
| `ProcessIdentityFilter` | `app.sdn_mpls_ml_logging_config` | `filter` | Adds process identity fields to log records. |
| `JsonFormatter` | `app.sdn_mpls_ml_logging_config` | `format` | Emits structured JSON log records. |
| `Messages` | `app.sdn_mpls_ml_messages` | static message helpers | Central message catalog for errors, logs, and validation messages. |

## Function Reference

| Function | Module | Responsibility |
|---|---|---|
| `lifespan(app)` | `app.sdn_mpls_ml_main` | Initializes process identity, logging, services, readiness, and metrics state during FastAPI startup. |
| `_error_response` | `app.sdn_mpls_ml_main` | Delegates to `build_error_response` for uniform error payloads. |
| `_request_id_from_request` | `app.sdn_mpls_ml_main` | Reads request correlation from `request.state`. |
| `_service_name_from_request` | `app.sdn_mpls_ml_main` | Resolves service name from validated or raw settings. |
| `_prometheus_metrics_enabled` | `app.sdn_mpls_ml_main` | Checks whether metrics are enabled for the current request. |
| `_log_request_event` | `app.sdn_mpls_ml_main` | Emits structured request/error logs with bounded metadata. |
| `_request_event_message` | `app.sdn_mpls_ml_main` | Maps event keys to human-readable log messages. |
| `_contains_invalid_json_error` | `app.sdn_mpls_ml_main` | Detects FastAPI JSON parser failures inside validation errors. |
| `_validation_body_metadata` | `app.sdn_mpls_ml_main` | Produces safe type/size/hash metadata for invalid bodies. |
| `_validation_error_summary` | `app.sdn_mpls_ml_main` | Reduces validation errors to safe type/location/message triples. |
| `handle_app_error` | `app.sdn_mpls_ml_main` | Converts controlled domain exceptions to logged HTTP responses. |
| `handle_request_validation` | `app.sdn_mpls_ml_main` | Converts FastAPI schema/JSON failures to stable API errors. |
| `handle_json_error` | `app.sdn_mpls_ml_main` | Handles raw `json.JSONDecodeError`. |
| `handle_http_exception` | `app.sdn_mpls_ml_main` | Normalizes Starlette HTTP errors. |
| `handle_unhandled_exception` | `app.sdn_mpls_ml_main` | Last FastAPI-level catch for unexpected failures. |
| `get_raw_settings` | `app.sdn_mpls_ml_config` | Loads cached raw environment settings. |
| `get_safe_log_level` | `app.sdn_mpls_ml_config` | Normalizes log level with fallback. |
| `create_initial_services` | `app.sdn_mpls_ml_dependencies` | Creates initial service container and early metrics. |
| `build_services` | `app.sdn_mpls_ml_dependencies` | Creates services and runs startup initialization. |
| `initialize_services` | `app.sdn_mpls_ml_dependencies` | Runs all readiness validation stages and mutates `AppServices`. |
| `_log_artifact_configuration` | `app.sdn_mpls_ml_dependencies` | Logs resolved artifact configuration. |
| `_failure_event` | `app.sdn_mpls_ml_dependencies` | Maps startup failure to structured log event. |
| `_classification_mode_response_value` | `app.sdn_mpls_ml_dependencies` | Converts raw mode string to response-safe value. |
| `_create_baseline_metrics` | `app.sdn_mpls_ml_dependencies` | Creates early metrics recorder unless disabled. |
| `_startup_error` | `app.sdn_mpls_ml_dependencies` | Builds structured startup errors. |
| `_validate_settings` | `app.sdn_mpls_ml_dependencies` | Converts raw environment strings into validated settings. |
| `_parse_int` / `_parse_float` / `_parse_bool` | `app.sdn_mpls_ml_dependencies` | Typed parsing helpers that raise startup errors. |
| `_validate_artifacts` | `app.sdn_mpls_ml_dependencies` | Resolves required artifact set by classification mode. |
| `_validate_artifact` | `app.sdn_mpls_ml_dependencies` | Validates one artifact path. |
| `_artifact_error` | `app.sdn_mpls_ml_dependencies` | Creates artifact-stage startup errors. |
| `_validate_schemas` | `app.sdn_mpls_ml_dependencies` | Loads metadata/rules and policy schemas. |
| `_load_metadata_for_startup` | `app.sdn_mpls_ml_dependencies` | Loads and validates model metadata. |
| `_is_metadata_contract_error` | `app.sdn_mpls_ml_dependencies` | Classifies metadata errors as contract failures. |
| `_metadata_error` | `app.sdn_mpls_ml_dependencies` | Creates metadata-stage startup errors. |
| `_load_policy_for_startup` | `app.sdn_mpls_ml_dependencies` | Loads and validates policy file. |
| `_load_deterministic_rules_for_startup` | `app.sdn_mpls_ml_dependencies` | Loads deterministic classifier rules. |
| `_build_classifier_pool` | `app.sdn_mpls_ml_dependencies` | Builds all classifier instances and synthetic-tests them before publishing. |
| `_build_single_classifier` | `app.sdn_mpls_ml_dependencies` | Creates one deterministic classifier or XGBoost predictor. |
| `_load_predictor` | `app.sdn_mpls_ml_dependencies` | Imports XGBoost, loads booster, and validates runtime compatibility. |
| `_runtime_error` | `app.sdn_mpls_ml_dependencies` | Creates runtime-stage startup errors. |
| `_run_synthetic_self_test` | `app.sdn_mpls_ml_dependencies` | Validates synthetic prediction output. |
| `_validate_complete_policy_map` | `app.sdn_mpls_ml_dependencies` | Confirms policy coverage and bounds for all expected classes. |
| `_validate_default_profile` | `app.sdn_mpls_ml_dependencies` | Requires default policy bandwidth to remain zero. |
| `_validate_policy_serialization` | `app.sdn_mpls_ml_dependencies` | Confirms policy model serialization works after validation. |
| `_validate_tunnel_bandwidth` | `app.sdn_mpls_ml_dependencies` | Enforces configured min/max bandwidth bounds per class. |
| `_policy_error` | `app.sdn_mpls_ml_dependencies` | Creates policy-stage startup errors. |
| `_first_error_loc` | `app.sdn_mpls_ml_dependencies` | Extracts first Pydantic error location for diagnostics. |
| `build_error_response` | `app.sdn_mpls_ml_http_responses` | Builds uniform `ErrorResponse` JSON and status code. |
| `get_request_id` | `app.sdn_mpls_ml_request_context` | Requires and returns middleware-generated request ID. |
| `utc_now_iso` | `app.sdn_mpls_ml_readiness` | Produces UTC readiness timestamps. |
| `_service_name_from_scope` | `app.middleware.sdn_mpls_ml_correlation_middleware` | Resolves service name from ASGI scope. |
| `_record_internal_error` | `app.middleware.sdn_mpls_ml_correlation_middleware` | Records uncaught downstream failure metrics. |
| `_middleware_log_extra` | `app.middleware.sdn_mpls_ml_correlation_middleware` | Builds correlation middleware log metadata. |
| `_request_metadata` | `app.middleware.sdn_mpls_ml_correlation_middleware` | Extracts safe method/path/header metadata. |
| `_selected_headers` | `app.middleware.sdn_mpls_ml_correlation_middleware` | Whitelists transport headers for logs. |
| `_request_size_limit_from_scope` | `app.middleware.sdn_mpls_ml_request_size_validation_middleware` | Reads active body-size limit from `AppServices`. |
| `_request_id_from_scope` | `app.middleware.sdn_mpls_ml_request_size_validation_middleware` | Reads correlation ID from ASGI state. |
| `_service_name_from_scope` | `app.middleware.sdn_mpls_ml_request_size_validation_middleware` | Resolves service name for size middleware logs. |
| `_record_rejection` | `app.middleware.sdn_mpls_ml_request_size_validation_middleware` | Records size/header rejections and request-error metrics. |
| `_parse_content_length` | `app.middleware.sdn_mpls_ml_request_size_validation_middleware` | Parses or rejects `Content-Length`. |
| `_build_replay_receive` | `app.middleware.sdn_mpls_ml_request_size_validation_middleware` | Creates single-body ASGI receive replay. |
| `_log_middleware_event` | `app.middleware.sdn_mpls_ml_request_size_validation_middleware` | Emits structured size middleware logs. |
| `_selected_request_headers` | `app.middleware.sdn_mpls_ml_request_size_validation_middleware` | Whitelists request headers for body diagnostics. |
| `validate_packet_for_classification_mode` | `app.model.sdn_mpls_ml_input_validation` | Enforces model-mode EtherType restrictions. |
| `_load_xgboost_module` | `app.model.sdn_mpls_ml_model_predictor` | Lazily imports XGBoost. |
| `load_policy_file` | `app.policy.sdn_mpls_ml_policy_mapper` | Reads and validates the policy mapping artifact. |
| `initialize_process_identity` | `app.observability.sdn_mpls_ml_identity` | Initializes singleton worker identity safely. |
| `get_process_identity` | `app.observability.sdn_mpls_ml_identity` | Returns initialized worker identity. |
| `_reset_process_identity_for_tests` | `app.observability.sdn_mpls_ml_identity` | Clears identity singleton for tests. |
| `execute_instrumented_inference` | `app.observability.sdn_mpls_ml_classification_metrics` | Runs classifier prediction off-loop and records inference histogram. |
| `record_request_error` | `app.observability.sdn_mpls_ml_classification_metrics` | Increments controlled request-error counter. |
| `record_request_body_rejection` | `app.observability.sdn_mpls_ml_classification_metrics` | Increments request-body rejection counter. |
| `configure_logging` | `app.sdn_mpls_ml_logging_config` | Installs JSON console and rotating file logging handlers. |

## Module Reference

| Module or folder | Provides | Consumes |
|---|---|---|
| `app.sdn_mpls_ml_main` | FastAPI app, middleware registration, routers, lifespan, exception handlers. | Routes, dependencies, logging, identity, metrics, exceptions. |
| `app.api` | Health, readiness, inference, model metadata, and metrics endpoints. | Schemas, `AppServices`, request context, observability. |
| `app.middleware` | Correlation and request-size validation ASGI layers. | Error response builder, messages, request metrics. |
| `app.schemas` | Strict Pydantic request/response/error contracts. | Pydantic and message constants. |
| `app.sdn_mpls_ml_dependencies` | Startup validation and runtime object graph construction. | Settings, artifacts, model, policy, metrics, readiness. |
| `app.model` | Classifier protocol, pool, XGBoost predictor, deterministic classifier, metadata/rule validation, mode input guard. | NumPy, XGBoost, Pydantic, exceptions. |
| `app.policy` | Policy file schema and class-to-policy mapper. | JSON artifact filesystem and Pydantic. |
| `app.observability` | Process identity, baseline metrics, classification metrics. | `prometheus_client`, AnyIO, classifier pool callbacks. |
| `app.sdn_mpls_ml_logging_config` | JSON formatter and rotating file handler setup. | Process identity and raw settings. |
| `app.sdn_mpls_ml_exceptions` | Stable controlled error hierarchy. | Message catalog. |
| `app.sdn_mpls_ml_http_responses` | Uniform HTTP error builder. | Error schemas. |
| `app.sdn_mpls_ml_messages` | Centralized messages and message factories. | Used by nearly all modules. |
| `configs` | Runtime policy mapping and deterministic rule artifacts. | Startup validation and deterministic classifier. |
| `models` | XGBoost model and metadata artifacts. | Startup validation and `Predictor`. |
| `tests` | API, middleware, model, metadata, metrics, and readiness regression coverage. | pytest, TestClient, dummy boosters, fixtures. |

## Artifact And Build Files

| File | Purpose |
|---|---|
| `Dockerfile` | Builds the Python API container, installs dependencies, copies app/config/model artifacts, configures healthcheck, and runs Uvicorn. |
| `requirements.txt` | Runtime dependencies: FastAPI, Uvicorn, Pydantic, XGBoost, NumPy, and Prometheus client. |
| `requirements-dev.txt` | Test and static-analysis dependencies. |
| `pyproject.toml` | pytest path, Ruff line length/target, and mypy baseline settings. |
| `configs/sdn_mpls_ml_traffic_class_to_policy_mapping.json` | Policy mapping for DNS, FTP, HTTP, ICMP, NTP, SSH, STREAMING plus default profile. |
| `configs/sdn_mpls_ml_traffic_class_deterministic_rules.json` | Deterministic mode protocol/port rules and streaming fallback. |
| `models/sdn_mpls_ml_model_meta.json` | Model metadata contract including feature order, class IDs, and training metadata. |
| `models/sdn_mpls_ml_model.json` | XGBoost booster artifact consumed by model mode. |

## Test Coverage Reference

| Test area | Files | Covered behavior |
|---|---|---|
| Classification API | `tests/test_classify_api.py`, `tests/test_real_model_integration.py`, `tests/test_deterministic_test_integration.py`, `tests/test_deterministic_mode.py` | Valid classification, response shape, model readiness, deterministic mode, pool capacity timeout, missing artifacts, and unsupported inputs. |
| Health and readiness | `tests/test_health.py`, `tests/test_validation.py` | Liveness, readiness states, startup validation stages, invalid settings, partial pool initialization, and readiness caching. |
| Middleware | `tests/test_request_size_middleware.py` | Content-Length parsing, declared/actual body-size limits, replay receive, and invalid header handling. |
| Error/log matrix | `tests/test_error_event_matrix.py`, `tests/test_logging_config.py` | Structured error response/log combinations, JSON logging, and classifier return-to-pool after errors. |
| Model contracts | `tests/test_metadata.py`, `tests/test_predictor.py`, `tests/test_model_input_validation.py`, `tests/model/test_classifier_pool.py` | Metadata contract, feature order, probability normalization, EtherType mode guard, pool concurrency, cancellation, and timeout invariants. |
| Observability | `tests/observability/test_metrics_route.py`, `tests/observability/test_classification_metrics.py`, `tests/observability/test_pool_metrics.py`, `tests/observability/test_process_identity.py` | Metrics endpoint, classification metrics, pool metrics, process identity, and rejection counters. |
| Fixtures | `tests/conftest.py` | Shared model metadata, temporary model/config directories, dummy booster, and TestClient setup. |
