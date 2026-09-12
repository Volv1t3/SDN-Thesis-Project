# Python API Documentation Pack

This document describes the `src/main/python/api` service as it exists in the repository. It is intended as a Writerside-ready reference for the Python classifier API used by the SDN MPLS controller-side application.

## Diagram Index

| Diagram | Source file | Purpose |
|---|---|---|
| Full classification request sequence | `docs/python-api/sequence-full-classification-request.mmd` | End-to-end flow from HTTP arrival through middleware, schema validation, classifier execution, policy mapping, metrics, and response handling. |
| Application class diagrams | `docs/python-api/class-diagrams.mmd` | Explicit class-level map for configuration, startup, middleware, schemas, model runtime, policy, observability, logging, and errors. |
| Per-class diagram snippets | `docs/python-api/per-class-diagrams.md` | Writerside-ready Mermaid snippets for each concrete class, dataclass, Pydantic model, enum, protocol, and internal sentinel. |
| Module component diagram | `docs/python-api/component-diagrams.mmd` | Module-level interfaces, internal dependencies, and external integrations. |
| Per-module component snippets | `docs/python-api/module-component-diagrams.md` | Writerside-ready Mermaid snippets for each module/package and its provided interfaces. |
| Domain model diagram | `docs/python-api/domain-model.mmd` | Conceptual domain model connecting requests, settings, readiness, classifiers, predictions, policies, errors, and metrics. |
| Process and method reference | `docs/python-api/process-method-reference.md` | Full tabular documentation for request processing, startup validation, middleware branches, helpers, artifacts, and tests. |

## Runtime Summary

The Python API is a FastAPI application initialized in `app.sdn_mpls_ml_main`. The service loads environment settings, validates model and policy artifacts, builds a classifier pool, runs a synthetic startup inference, exposes health/readiness/model/classification endpoints, and exports Prometheus metrics from the same HTTP service.

The primary classification path is:

1. `CorrelationIdMiddleware.__call__()` creates a server-owned UUID request ID and injects it into `scope.state`.
2. `RequestSizeLimitMiddleware.__call__()` validates `Content-Length`, buffers the body, enforces `max_request_body_bytes`, and replays the validated body to FastAPI.
3. FastAPI parses JSON and validates `ClassifyRequest` and `PacketFeatures`.
4. `classify(payload, request)` verifies readiness and mode-specific input constraints.
5. `ClassifierPool.acquire()` leases one classifier instance under a timeout.
6. `execute_instrumented_inference()` runs the synchronous classifier in an AnyIO worker thread.
7. Either `Predictor.predict()` invokes XGBoost or `DeterministicClassifier.predict()` applies deterministic rules.
8. `PolicyMapper.resolve()` maps the predicted class to a traffic policy when policy mapping is enabled.
9. Metrics are recorded, the classifier is returned to the pool, and `ClassifyResponse` is returned.

## HTTP Interfaces

| Endpoint | Method | Module / function | Request model | Response model | Behavior |
|---|---:|---|---|---|---|
| `/` | `GET` | `app.api.sdn_mpls_ml_health_route.root` | None | `RootResponse` | Returns service identity, version, status, and documentation endpoint. |
| `/health/live` | `GET` | `app.api.sdn_mpls_ml_health_route.live` | None | `LivenessResponse` | Confirms the HTTP process is alive. |
| `/health/ready` | `GET` | `app.api.sdn_mpls_ml_health_route.ready` | None | `ReadySuccessResponse` or `ReadyFailureResponse` | Returns cached startup readiness. Non-ready states return HTTP 503 with structured error details. |
| `/api/v1/model` | `GET` | `app.api.sdn_mpls_ml_inference_route.model_info` | None | `ModelInfoResponse` | Returns active model metadata and class labels. Fails with `ModelNotReadyError` when readiness or metadata is missing. |
| `/api/v1/classify` | `POST` | `app.api.sdn_mpls_ml_inference_route.classify` | `ClassifyRequest` | `ClassifyResponse` | Validates packet features, runs model or deterministic classification, resolves policy, and records metrics. |
| `/metrics` | `GET` | `app.api.sdn_mpls_ml_metrics_route.metrics` | None | Prometheus text exposition | Emits Prometheus metrics when `ENABLE_PROMETHEUS_METRICS=true`; returns 404 when disabled. Hidden from OpenAPI. |

## Request Validation Contract

| Model | Module | Fields | Validation |
|---|---|---|---|
| `StrictBaseModel` | `app.schemas.sdn_mpls_ml_baseline_validation_models` | Common Pydantic configuration | Forbids unknown fields and allows population by field name. |
| `PacketFeatures` | `app.schemas.sdn_mpls_ml_inference_validation_models` | `eth_type`, `ip_proto`, `src_port`, `dst_port` | Unsigned bounds for Ethernet type, IP protocol, and ports. Non-TCP/UDP protocols must keep both ports at `0`. |
| `ClassifyRequest` | `app.schemas.sdn_mpls_ml_inference_validation_models` | `packet_features` | Rejects extra fields, including caller-provided request IDs. |
| `PredictionBody` | `app.schemas.sdn_mpls_ml_inference_validation_models` | `class_id`, `class_name`, `confidence` | Serializes classifier output. |
| `PathConstraints` | `app.schemas.sdn_mpls_ml_inference_validation_models` | `requested_bandwidth_kbps`, `setup_priority`, `hold_priority` | HTTP response shape for selected policy constraints. |
| `PolicyResponse` | `app.schemas.sdn_mpls_ml_inference_validation_models` | `profile_name`, `dscp`, `mpls_tc`, `path_constraints`, fallback metadata | Serializes the selected or fallback policy returned to the caller. |
| `ClassifyResponse` | `app.schemas.sdn_mpls_ml_inference_validation_models` | `request_id`, `model_name`, `prediction`, `probabilities`, `policy`, `processing_time_ms` | Final classification response. |

## Startup and Readiness

| Step | Function | Input | Output / side effect |
|---|---|---|---|
| Raw settings load | `get_raw_settings()` | Environment variables and optional `.env` | Cached `RawSettings`. |
| Safe logging level | `get_safe_log_level(raw_log_level)` | Raw log-level string | Supported level or `INFO`. |
| Process identity | `initialize_process_identity(service, configured_instance_id)` | Service name and optional instance ID | Stable per-worker `ProcessIdentity`. |
| Logging setup | `configure_logging(level, settings)` | Safe level and raw settings | JSON console/file logging with rotating file handler. |
| Initial service container | `create_initial_services(raw_settings)` | Optional raw settings | `AppServices` with initializing readiness. |
| Full service build | `build_services(raw_settings, process_identity)` | Raw settings and identity | Fully initialized `AppServices`. |
| Validation pipeline | `initialize_services(services, process_identity)` | Mutable `AppServices` | Validates settings, artifacts, schemas, runtime, policy map, pool, and readiness. |
| Failure capture | `ReadinessState.mark_failed(error)` | `StartupValidationError` | Caches structured startup failure. |
| Success capture | `ReadinessState.mark_ready(...)` | Model and schema summary | Marks the API ready and clears previous startup errors. |

## Classification Execution Details

| Function / class | Role | Success path | Failure path |
|---|---|---|---|
| `classify(payload, request)` | Primary endpoint handler | Runs readiness, mode validation, pool acquisition, inference, policy mapping, and response serialization. | Raises typed `AppError` subclasses for readiness, capacity, unsupported input, model output, inference, and policy failures. |
| `validate_packet_for_classification_mode(classification_mode, eth_type, request_id)` | Mode-specific input guard | Allows all EtherTypes in deterministic test mode and IPv4 EtherType `2048` in model mode. | Raises `ModelEtherTypeUnsupportedError` in model mode for unsupported EtherTypes. |
| `ClassifierPool.acquire(timeout_seconds)` | Async classifier lease manager | Returns one classifier and guarantees release in `finally`. | Raises `InferenceCapacityExceededError` when the queue cannot provide a classifier before timeout. |
| `execute_instrumented_inference(...)` | Async wrapper for synchronous prediction | Runs `classifier.predict(packet_features)` in AnyIO worker thread and records inference duration. | Preserves cancellation, output-invalid errors, and generic inference failures with metric outcomes. |
| `Predictor.predict(packet_features)` | XGBoost-backed classifier | Builds feature matrix, invokes booster, validates probability vector, returns `PredictionResult`. | Raises model errors on DMatrix conversion, booster runtime failure, invalid shape, invalid probability count, non-finite values, out-of-range values, or probability-sum mismatch. |
| `DeterministicClassifier.predict(packet_features)` | Deterministic test classifier | Applies protocol and port rules, then returns one-hot `PredictionResult`. | Relies on validated deterministic rule file; unknown traffic falls back to `STREAMING`. |
| `PolicyMapper.resolve(predicted_class, confidence)` | Policy selection | Returns class policy unless confidence threshold forces fallback. | Missing class policy becomes `PolicyMappingFailedError` at the endpoint layer. |

## Class Reference

| Class | Module | Responsibility | Key collaborators |
|---|---|---|---|
| `ClassificationMode` | `app.sdn_mpls_ml_config` | Enum for `MODEL` and `DETERMINISTIC_TEST` runtime modes. | `ValidatedSettings`, startup validation, input validation. |
| `RawSettings` | `app.sdn_mpls_ml_config` | Environment-derived configuration with defaults and aliases. | `_validate_settings`, `get_raw_settings`, `configure_logging`. |
| `ValidatedSettings` | `app.sdn_mpls_ml_config` | Semantically validated runtime configuration. | `AppServices`, artifact validation, classifier pool construction. |
| `ArtifactPaths` | `app.sdn_mpls_ml_dependencies` | Resolved model, metadata, policy, and deterministic rule paths. | `_validate_artifacts`, `_validate_schemas`. |
| `AppServices` | `app.sdn_mpls_ml_dependencies` | Mutable service container stored on `app.state.services`. | Routes, readiness, classifier pool, policy mapper, metrics. |
| `StartupValidationError` | `app.sdn_mpls_ml_readiness` | Structured startup failure record. | `ReadinessState.mark_failed`, startup helper functions. |
| `ReadinessState` | `app.sdn_mpls_ml_readiness` | Cached readiness and startup diagnostic state. | Health route, inference route, startup validation. |
| `ErrorDetail` | `app.sdn_mpls_ml_exceptions` | Serializable internal error detail. | `AppError.to_error`. |
| `AppError` | `app.sdn_mpls_ml_exceptions` | Base class for controlled functional errors. | FastAPI exception handlers, `build_error_response`. |
| `InvalidJsonError` | `app.sdn_mpls_ml_exceptions` | Invalid JSON body classification. | Request validation handlers. |
| `InvalidContentLengthError` | `app.sdn_mpls_ml_exceptions` | Invalid `Content-Length` header classification. | Request size middleware. |
| `RequestTooLargeError` | `app.sdn_mpls_ml_exceptions` | Body size limit violation. | Request size middleware. |
| `RequestValidationAppError` | `app.sdn_mpls_ml_exceptions` | Pydantic schema validation failure classification. | `handle_request_validation`. |
| `ModelEtherTypeUnsupportedError` | `app.sdn_mpls_ml_exceptions` | Unsupported EtherType in model mode. | `validate_packet_for_classification_mode`. |
| `ModelNotReadyError` | `app.sdn_mpls_ml_exceptions` | Service not ready to classify. | Health/model/classify endpoints. |
| `ModelInferenceFailedError` | `app.sdn_mpls_ml_exceptions` | Classifier runtime failure. | `Predictor`, endpoint error handling. |
| `InferenceCapacityExceededError` | `app.sdn_mpls_ml_exceptions` | Classifier pool acquisition timeout. | `ClassifierPool.acquire`. |
| `ModelOutputInvalidError` | `app.sdn_mpls_ml_exceptions` | Invalid classifier probability output. | `Predictor._normalize_output`, inference metrics. |
| `PolicyMappingFailedError` | `app.sdn_mpls_ml_exceptions` | Predicted class cannot be translated to policy. | `PolicyMapper`, classify endpoint. |
| `CorrelationIdMiddleware` | `app.middleware.sdn_mpls_ml_correlation_middleware` | Generates and propagates server-owned request IDs. | `get_request_id`, JSON logs, error responses. |
| `RequestSizeLimitMiddleware` | `app.middleware.sdn_mpls_ml_request_size_validation_middleware` | Enforces request-body size and replays accepted bodies. | `build_error_response`, request body rejection metrics. |
| `_InvalidContentLengthSentinel` | `app.middleware.sdn_mpls_ml_request_size_validation_middleware` | Internal sentinel for malformed `Content-Length`. | `_parse_content_length`. |
| `StrictBaseModel` | `app.schemas.sdn_mpls_ml_baseline_validation_models` | Shared strict Pydantic base. | All HTTP schema models. |
| `ErrorBody` | `app.schemas.sdn_mpls_ml_baseline_validation_models` | Uniform error payload body. | `ErrorResponse`, `build_error_response`. |
| `ErrorResponse` | `app.schemas.sdn_mpls_ml_baseline_validation_models` | Uniform error response envelope. | Middleware and exception handlers. |
| `RootResponse` | `app.schemas.sdn_mpls_ml_health_validation_models` | Root endpoint response. | `root`. |
| `LivenessResponse` | `app.schemas.sdn_mpls_ml_health_validation_models` | Liveness endpoint response. | `live`. |
| `ReadinessError` | `app.schemas.sdn_mpls_ml_health_validation_models` | Structured readiness error. | `ReadyFailureResponse`. |
| `ReadySuccessResponse` | `app.schemas.sdn_mpls_ml_health_validation_models` | Ready state response. | `ready`. |
| `ReadyFailureResponse` | `app.schemas.sdn_mpls_ml_health_validation_models` | Non-ready readiness response. | `ready`. |
| `PacketFeatures` | `app.schemas.sdn_mpls_ml_inference_validation_models` | Packet feature input contract. | `ClassifyRequest`, classifier runtime. |
| `ClassifyRequest` | `app.schemas.sdn_mpls_ml_inference_validation_models` | Classification request envelope. | `classify`. |
| `PredictionBody` | `app.schemas.sdn_mpls_ml_inference_validation_models` | Prediction response body. | `ClassifyResponse`. |
| `PathConstraints` | `app.schemas.sdn_mpls_ml_inference_validation_models` | HTTP representation of policy path constraints. | `PolicyResponse`. |
| `PolicyResponse` | `app.schemas.sdn_mpls_ml_inference_validation_models` | HTTP representation of selected policy. | `ClassifyResponse`, `PolicyMapper`. |
| `ClassifyResponse` | `app.schemas.sdn_mpls_ml_inference_validation_models` | Successful classification response. | `classify`. |
| `ModelClassInfo` | `app.schemas.sdn_mpls_ml_inference_validation_models` | Model class ID/name pair. | `ModelInfoResponse`. |
| `ModelInfoResponse` | `app.schemas.sdn_mpls_ml_inference_validation_models` | Model metadata response. | `model_info`. |
| `ClassifierPoolObserver` | `app.model.sdn_mpls_ml_classifier_pool` | Protocol for pool metrics callbacks. | `BaselineMetrics`. |
| `ClassifierPool` | `app.model.sdn_mpls_ml_classifier_pool` | Async bounded pool of classifier instances. | Inference endpoint, `BaselineMetrics`. |
| `PredictionResult` | `app.model.sdn_mpls_ml_model_predictor` | Internal classifier result record. | `Predictor`, `DeterministicClassifier`, endpoint response. |
| `Predictor` | `app.model.sdn_mpls_ml_model_predictor` | XGBoost model adapter and output validator. | XGBoost Booster, `ModelMetadata`. |
| `DeterministicClassifier` | `app.model.sdn_mpls_ml_deterministic_predictor` | Rule-based classifier for deterministic test mode. | `DeterministicRuleFile`. |
| `ModelMetadata` | `app.model.sdn_mpls_ml_metadata` | Model artifact contract and class map validation. | Startup validation, `Predictor`, `/api/v1/model`. |
| `DeterministicRuleFile` | `app.model.sdn_mpls_ml_deterministic_rules` | Deterministic classifier rule contract. | Startup validation, `DeterministicClassifier`. |
| `TrafficClassifier` | `app.model.sdn_mpls_ml_protocols` | Runtime protocol implemented by model and deterministic classifiers. | `ClassifierPool`. |
| `PathConstraints` | `app.policy.sdn_mpls_ml_policy_validation_models` | Policy-file path constraint contract. | `TrafficPolicy`. |
| `TrafficPolicy` | `app.policy.sdn_mpls_ml_policy_validation_models` | Policy-file traffic class policy. | `PolicyFile`, `PolicyMapper`. |
| `PolicyFile` | `app.policy.sdn_mpls_ml_policy_validation_models` | Full traffic-class-to-policy mapping contract. | Startup validation, `PolicyMapper`. |
| `PolicyMapper` | `app.policy.sdn_mpls_ml_policy_mapper` | Resolves prediction class/confidence to `TrafficPolicy`. | `PolicyFile`, classify endpoint. |
| `ProcessIdentity` | `app.observability.sdn_mpls_ml_identity` | Stable worker identity for logs and metrics labels. | `BaselineMetrics`, `ProcessIdentityFilter`. |
| `BaselineMetrics` | `app.observability.sdn_mpls_ml_metrics` | Publishes startup, readiness, worker, and classifier-pool metrics. | Startup pipeline, `ClassifierPool`. |
| `ClassificationObservation` | `app.observability.sdn_mpls_ml_classification_metrics` | Per-request classification timing and outcome recorder. | `classify`. |
| `ProcessIdentityFilter` | `app.sdn_mpls_ml_logging_config` | Adds worker identity fields to log records. | `JsonFormatter`, logging handlers. |
| `JsonFormatter` | `app.sdn_mpls_ml_logging_config` | Serializes structured JSON log events. | Console and rotating file handlers. |
| `Messages` | `app.sdn_mpls_ml_messages` | Centralized human-readable messages and reusable message helpers. | Errors, startup validation, logs. |

## Function Reference

| Function | Module | Responsibility |
|---|---|---|
| `lifespan(app)` | `app.sdn_mpls_ml_main` | Initializes process identity, logging, services, readiness, and metrics during FastAPI startup. |
| `_error_response(...)` | `app.sdn_mpls_ml_main` | Builds normalized HTTP errors for exception handlers. |
| `_request_id_from_request(request)` | `app.sdn_mpls_ml_main` | Safely extracts request ID for error handlers. |
| `_service_name_from_request(request)` | `app.sdn_mpls_ml_main` | Extracts configured service name from request state. |
| `_prometheus_metrics_enabled(request)` | `app.sdn_mpls_ml_main` | Determines whether Prometheus metrics are enabled. |
| `_log_request_event(...)` | `app.sdn_mpls_ml_main` | Emits structured request/error events. |
| `_request_event_message(event)` | `app.sdn_mpls_ml_main` | Maps request event codes to human-readable log messages. |
| `_contains_invalid_json_error(errors)` | `app.sdn_mpls_ml_main` | Detects JSON parsing failures inside FastAPI validation errors. |
| `_validation_body_metadata(body)` | `app.sdn_mpls_ml_main` | Produces safe metadata for invalid request bodies. |
| `_validation_error_summary(errors)` | `app.sdn_mpls_ml_main` | Reduces Pydantic errors to safe summary fields. |
| `handle_app_error(request, exc)` | `app.sdn_mpls_ml_main` | Converts controlled `AppError` exceptions to structured HTTP responses. |
| `handle_request_validation(request, exc)` | `app.sdn_mpls_ml_main` | Converts FastAPI/Pydantic validation errors into `INVALID_JSON` or `REQUEST_VALIDATION_FAILED`. |
| `handle_json_error(request, exc)` | `app.sdn_mpls_ml_main` | Converts raw JSON decode errors to `INVALID_JSON`. |
| `handle_http_exception(request, exc)` | `app.sdn_mpls_ml_main` | Normalizes Starlette HTTP errors, including 404. |
| `handle_unhandled_exception(request, exc)` | `app.sdn_mpls_ml_main` | Catches unexpected exceptions and emits `INTERNAL_ERROR`. |
| `get_raw_settings()` | `app.sdn_mpls_ml_config` | Loads and caches raw environment settings. |
| `get_safe_log_level(raw_log_level)` | `app.sdn_mpls_ml_config` | Normalizes log level with safe fallback. |
| `build_error_response(...)` | `app.sdn_mpls_ml_http_responses` | Constructs uniform `ErrorResponse` JSON payloads. |
| `get_request_id(request)` | `app.sdn_mpls_ml_request_context` | Retrieves the request ID generated by correlation middleware. |
| `utc_now_iso()` | `app.sdn_mpls_ml_readiness` | Creates compact UTC timestamps for readiness state. |
| `create_initial_services(raw_settings)` | `app.sdn_mpls_ml_dependencies` | Creates the initial `AppServices` container. |
| `build_services(raw_settings, process_identity)` | `app.sdn_mpls_ml_dependencies` | Creates and initializes `AppServices`. |
| `initialize_services(services, process_identity)` | `app.sdn_mpls_ml_dependencies` | Executes startup validation and runtime construction. |
| `_validate_settings(raw)` | `app.sdn_mpls_ml_dependencies` | Converts raw string settings to validated typed settings. |
| `_validate_artifacts(settings)` | `app.sdn_mpls_ml_dependencies` | Resolves and validates artifact paths. |
| `_validate_artifact(...)` | `app.sdn_mpls_ml_dependencies` | Checks a single artifact exists, is readable, non-empty, and UTF-8 readable. |
| `_validate_schemas(settings, artifacts, readiness)` | `app.sdn_mpls_ml_dependencies` | Loads and validates model metadata, policy mapping, and deterministic rules. |
| `_load_metadata_for_startup(path)` | `app.sdn_mpls_ml_dependencies` | Loads model metadata JSON into `ModelMetadata`. |
| `_load_policy_for_startup(path)` | `app.sdn_mpls_ml_dependencies` | Loads policy JSON into `PolicyFile`. |
| `_load_deterministic_rules_for_startup(path)` | `app.sdn_mpls_ml_dependencies` | Loads deterministic rule JSON into `DeterministicRuleFile`. |
| `_build_classifier_pool(...)` | `app.sdn_mpls_ml_dependencies` | Builds classifier instances and runs startup self-tests. |
| `_build_single_classifier(...)` | `app.sdn_mpls_ml_dependencies` | Creates one XGBoost `Predictor` or `DeterministicClassifier`. |
| `_load_predictor(settings, metadata)` | `app.sdn_mpls_ml_dependencies` | Loads XGBoost runtime and model artifact, then verifies objective and class count. |
| `_run_synthetic_self_test(result)` | `app.sdn_mpls_ml_dependencies` | Validates startup inference output. |
| `_validate_complete_policy_map(...)` | `app.sdn_mpls_ml_dependencies` | Ensures the policy file covers every known traffic class. |
| `_validate_default_profile(policy)` | `app.sdn_mpls_ml_dependencies` | Ensures best-effort fallback bandwidth remains zero. |
| `_validate_policy_serialization(policy)` | `app.sdn_mpls_ml_dependencies` | Ensures policy objects can serialize after validation. |
| `_validate_tunnel_bandwidth(...)` | `app.sdn_mpls_ml_dependencies` | Checks requested policy bandwidth against configured demonstrator bounds. |
| `_parse_int(...)`, `_parse_float(...)`, `_parse_bool(...)` | `app.sdn_mpls_ml_dependencies` | Typed parsing helpers for environment configuration. |
| `_startup_error(...)`, `_artifact_error(...)`, `_metadata_error(...)`, `_runtime_error(...)`, `_policy_error(...)` | `app.sdn_mpls_ml_dependencies` | Structured startup error factories. |
| `_first_error_loc(exc)` | `app.sdn_mpls_ml_dependencies` | Extracts the first Pydantic validation location for diagnostics. |
| `root(request)` | `app.api.sdn_mpls_ml_health_route` | Root metadata endpoint. |
| `live()` | `app.api.sdn_mpls_ml_health_route` | Liveness endpoint. |
| `ready(request)` | `app.api.sdn_mpls_ml_health_route` | Readiness endpoint backed by cached `ReadinessState`. |
| `model_info(request)` | `app.api.sdn_mpls_ml_inference_route` | Model metadata endpoint. |
| `classify(payload, request)` | `app.api.sdn_mpls_ml_inference_route` | Main classification endpoint. |
| `_registry_for_scrape()` | `app.api.sdn_mpls_ml_metrics_route` | Selects default or multiprocess Prometheus registry. |
| `metrics(request)` | `app.api.sdn_mpls_ml_metrics_route` | Prometheus text exposition endpoint. |
| `_service_name_from_scope(scope)` | `app.middleware.sdn_mpls_ml_correlation_middleware` | Derives service name for middleware logging. |
| `_record_internal_error(scope)` | `app.middleware.sdn_mpls_ml_correlation_middleware` | Records internal error metric from middleware. |
| `_middleware_log_extra(scope, request_id, event, metadata)` | `app.middleware.sdn_mpls_ml_correlation_middleware` | Builds structured middleware log extras. |
| `_request_metadata(scope)` | `app.middleware.sdn_mpls_ml_correlation_middleware` | Extracts safe request metadata from ASGI scope. |
| `_selected_headers(headers)` | `app.middleware.sdn_mpls_ml_correlation_middleware` | Extracts selected safe headers for logs. |
| `_request_size_limit_from_scope(scope)` | `app.middleware.sdn_mpls_ml_request_size_validation_middleware` | Gets active request-body limit. |
| `_request_id_from_scope(scope)` | `app.middleware.sdn_mpls_ml_request_size_validation_middleware` | Reads request ID from ASGI scope state. |
| `_record_rejection(scope, reason)` | `app.middleware.sdn_mpls_ml_request_size_validation_middleware` | Records request-body rejection metrics. |
| `_parse_content_length(scope)` | `app.middleware.sdn_mpls_ml_request_size_validation_middleware` | Parses `Content-Length` or returns sentinel. |
| `_build_replay_receive(validated_body, scope, request_id)` | `app.middleware.sdn_mpls_ml_request_size_validation_middleware` | Builds an ASGI receive callable that replays validated request body. |
| `_log_middleware_event(...)` | `app.middleware.sdn_mpls_ml_request_size_validation_middleware` | Emits structured size-middleware logs. |
| `_selected_request_headers(scope)` | `app.middleware.sdn_mpls_ml_request_size_validation_middleware` | Extracts selected safe request headers for logs. |
| `validate_packet_for_classification_mode(...)` | `app.model.sdn_mpls_ml_input_validation` | Enforces model-mode IPv4 EtherType support. |
| `_load_xgboost_module()` | `app.model.sdn_mpls_ml_model_predictor` | Lazily imports XGBoost. |
| `load_policy_file(path)` | `app.policy.sdn_mpls_ml_policy_mapper` | Loads and validates policy mapping JSON. |
| `initialize_process_identity(...)` | `app.observability.sdn_mpls_ml_identity` | Initializes stable per-worker identity. |
| `get_process_identity()` | `app.observability.sdn_mpls_ml_identity` | Returns initialized worker identity. |
| `_reset_process_identity_for_tests()` | `app.observability.sdn_mpls_ml_identity` | Test-only reset helper for process identity. |
| `execute_instrumented_inference(...)` | `app.observability.sdn_mpls_ml_classification_metrics` | Runs prediction in a worker thread and records inference duration. |
| `record_request_error(...)` | `app.observability.sdn_mpls_ml_classification_metrics` | Increments controlled request error metrics. |
| `record_request_body_rejection(...)` | `app.observability.sdn_mpls_ml_classification_metrics` | Increments request body rejection metrics. |

## Component Responsibilities

| Component | Provides | Consumes | External dependencies |
|---|---|---|---|
| `app.sdn_mpls_ml_main` | FastAPI app, lifespan, exception handlers | Routes, middleware, dependencies, messages, metrics | FastAPI, Starlette |
| `app.api` | HTTP endpoints | Schemas, AppServices, request context, metrics | FastAPI response system |
| `app.middleware` | Correlation ID and body-size validation | Error response builder, metrics, messages | ASGI contract |
| `app.schemas` | Strict request/response/error contracts | Pydantic validators | Pydantic |
| `app.sdn_mpls_ml_dependencies` | Startup validation and service graph construction | Settings, readiness, artifacts, model, policy, metrics | Filesystem, XGBoost, AnyIO |
| `app.model` | Classifier pool, classifier protocol, model adapter, deterministic adapter, metadata contracts | Exceptions, settings, deterministic rule files | NumPy, XGBoost |
| `app.policy` | Policy-file validation and class-to-policy resolution | Policy JSON, traffic class names | Pydantic, JSON filesystem |
| `app.observability` | Worker identity and Prometheus metrics | Startup and request lifecycle events | `prometheus_client` |
| `configs` | Policy and deterministic rule input artifacts | Startup schema validation | Mounted filesystem |
| `models` | XGBoost model and metadata artifacts | Startup runtime validation and `Predictor` | Mounted filesystem |
| `tests` | Regression coverage for API, middleware, metrics, readiness, model, policy validation | Application package | pytest, TestClient |

## Prometheus Metrics

| Metric | Type | Labels | Producer |
|---|---|---|---|
| `sdn_mpls_ml_api_obs_worker_info` | Gauge | `instance_id`, `worker_id`, `classification_mode` | `BaselineMetrics.initialize_worker` |
| `sdn_mpls_ml_api_obs_worker_start_time_seconds` | Gauge | `instance_id`, `worker_id` | `BaselineMetrics.initialize_worker` |
| `sdn_mpls_ml_api_obs_readiness` | Gauge | `classification_mode` | `BaselineMetrics.set_readiness` |
| `sdn_mpls_ml_api_obs_startup_validation_duration_seconds` | Histogram | `classification_mode`, `outcome` | Startup validation pipeline |
| `sdn_mpls_ml_api_obs_startup_failures_total` | Counter | `failed_stage`, `error_code` | Startup validation failure path |
| `sdn_mpls_ml_api_obs_classifier_pool_capacity` | Gauge | `classification_mode` | `BaselineMetrics.set_pool_state` |
| `sdn_mpls_ml_api_obs_classifier_pool_available` | Gauge | `classification_mode` | `BaselineMetrics.set_pool_state` |
| `sdn_mpls_ml_api_obs_classifier_pool_borrowed` | Gauge | `classification_mode` | `BaselineMetrics.set_pool_state` |
| `sdn_mpls_ml_api_obs_classifier_pool_wait_seconds` | Histogram | `classification_mode`, `outcome` | `ClassifierPool.acquire` through `BaselineMetrics.observe_wait` |
| `sdn_mpls_ml_api_obs_classifier_pool_timeouts_total` | Counter | `classification_mode` | `ClassifierPool.acquire` through `BaselineMetrics.record_timeout` |
| `sdn_mpls_ml_api_obs_classification_requests_total` | Counter | `classification_mode`, `outcome` | `ClassificationObservation.finish` |
| `sdn_mpls_ml_api_obs_classification_in_progress` | Gauge | `classification_mode` | `classify` endpoint |
| `sdn_mpls_ml_api_obs_classification_duration_seconds` | Histogram | `classification_mode`, `outcome` | `ClassificationObservation.finish` |
| `sdn_mpls_ml_api_obs_inference_duration_seconds` | Histogram | `classification_mode`, `outcome` | `execute_instrumented_inference` |
| `sdn_mpls_ml_api_obs_classification_results_total` | Counter | `classification_mode`, `class_name` | Successful `classify` path |
| `sdn_mpls_ml_api_obs_prediction_confidence` | Histogram | `classification_mode`, `class_name` | Successful `classify` path |
| `sdn_mpls_ml_api_obs_policy_selections_total` | Counter | `profile_name`, `fallback` | Policy mapping path |
| `sdn_mpls_ml_api_obs_policy_fallbacks_total` | Counter | `reason`, `predicted_class` | Policy fallback path |
| `sdn_mpls_ml_api_obs_request_errors_total` | Counter | `error_code`, `component` | Exception handlers and middleware internal error path |
| `sdn_mpls_ml_api_obs_request_body_rejections_total` | Counter | `reason` | Request size middleware |

## Artifact Contracts

| Artifact | Default path pieces | Validated by | Runtime use |
|---|---|---|---|
| XGBoost model | `MODEL_DIR/sdn_mpls_ml_model.json` | `_validate_artifact`, `_load_predictor` | `Predictor` loads the booster in `MODEL` mode. |
| Model metadata | `MODEL_DIR/sdn_mpls_ml_model_meta.json` | `ModelMetadata.validate_contract` | Defines feature order, expected class IDs, and model response metadata. |
| Policy mapping | `CONFIG_DIR/sdn_mpls_ml_traffic_class_to_policy_mapping.json` | `PolicyFile`, `_validate_complete_policy_map` | `PolicyMapper` maps predicted classes to DSCP, MPLS TC, and path constraints. |
| Deterministic rules | `CONFIG_DIR/sdn_mpls_ml_traffic_class_deterministic_rules.json` | `DeterministicRuleFile.validate_contract` | `DeterministicClassifier` maps ports/protocols to classes in deterministic test mode. |

## Domain Vocabulary

| Term | Meaning |
|---|---|
| Packet features | Four numeric fields: Ethernet type, IP protocol, source port, destination port. |
| Classification mode | Runtime mode selecting XGBoost model inference or deterministic rule inference. |
| Classifier pool | Bounded async lease pool that prevents unlimited concurrent classifier access. |
| Prediction result | Internal class ID, class name, confidence, and probability map. |
| Policy mapping | Translation from predicted traffic class to DSCP/MPLS/path constraint profile. |
| Readiness | Cached result of startup validation, artifact loading, schema checks, runtime checks, and synthetic inference. |
| Request ID | Server-generated UUID used for logs, response headers, and response bodies. |
| Structured error | Uniform `ErrorResponse` with stable code, message, component, failed stage/check, and retryability. |
| Baseline metrics | Worker, startup, readiness, and classifier-pool metrics. |
| Classification metrics | Request, duration, result, confidence, policy, body rejection, and error metrics. |

## Test Coverage Map

| Test file | Focus |
|---|---|
| `tests/test_classify_api.py` | Model endpoint, valid classification, invalid input, request ID policy, size rejection, capacity timeout, endpoint registration. |
| `tests/test_health.py` | Liveness, readiness states, readiness caching, middleware order. |
| `tests/test_validation.py` | Environment settings, artifact validation, policy coverage, bandwidth bounds, partial pool initialization. |
| `tests/test_metadata.py` | Model metadata contract validation and invalid metadata cases. |
| `tests/test_predictor.py` | Feature vector order, prediction argmax, probability validation. |
| `tests/test_model_input_validation.py` | EtherType validation behavior by classification mode. |
| `tests/test_deterministic_mode.py` | Deterministic mode readiness and known/unknown classification. |
| `tests/test_deterministic_test_integration.py` | Deterministic endpoint behavior, response shape, protocol validation, non-IPv4 handling, missing rules. |
| `tests/test_real_model_integration.py` | Real model classification, response shape, invalid protocol/port combination, EtherType rejection. |
| `tests/test_request_size_middleware.py` | Declared and actual body-size enforcement, invalid content length, replay to Pydantic. |
| `tests/test_error_event_matrix.py` | Structured error response and log event matrix. |
| `tests/test_logging_config.py` | JSON logging to console and rotating file handler. |
| `tests/observability/test_metrics_route.py` | Prometheus route availability and OpenAPI visibility. |
| `tests/observability/test_classification_metrics.py` | Classification, rejection, fallback, and request-size metrics. |
| `tests/observability/test_pool_metrics.py` | Pool state, wait, and timeout metric callbacks. |
| `tests/observability/test_process_identity.py` | Stable process identity behavior. |
| `tests/model/test_classifier_pool.py` | Pool initialization, acquire/release invariants, exception/cancellation release, timeout, no duplicate leases. |
| `tests/conftest.py` | Shared model metadata, dummy booster, config fixtures, and TestClient setup. |

## Writerside Usage Notes

The `.mmd` files are intentionally stored as plain Mermaid source. In Writerside, include them as code blocks or use the diagram integration supported by your Writerside build profile. The Markdown tables in this file are standalone and can be split into topic pages later without requiring changes to the source diagrams.
