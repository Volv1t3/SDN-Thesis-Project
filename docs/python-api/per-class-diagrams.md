# Python API Per-Class Mermaid Diagram Snippets

These snippets are intentionally small and class-specific so they can be pasted into individual Writerside sections.

## Configuration Classes

### ClassificationMode

```mermaid
classDiagram
    class ClassificationMode {
        <<StrEnum>>
        MODEL
        DETERMINISTIC_TEST
        +response_value str
    }
```

### RawSettings

```mermaid
classDiagram
    class RawSettings {
        <<BaseSettings>>
        +app_name str
        +app_version str
        +host str
        +port str
        +log_level str
        +enable_prometheus_metrics str
        +metrics_path str
        +instance_id str
        +model_dir str
        +config_dir str
        +model_filename str
        +model_metadata_filename str
        +policy_filename str
        +deterministic_rule_filename str
        +enable_policy_mapping str
        +classifier_pool_size str
        +request_timeout_seconds str
        +max_request_body_bytes str
        +probability_tolerance str
        +min_policy_confidence str?
        +classification_mode str
    }
```

### ValidatedSettings

```mermaid
classDiagram
    class ValidatedSettings {
        <<dataclass>>
        +app_name str
        +app_version str
        +host str
        +port int
        +log_level str
        +enable_prometheus_metrics bool
        +metrics_path str
        +classification_mode ClassificationMode
        +classifier_pool_size int
        +request_timeout_seconds int
        +max_request_body_bytes int
        +probability_tolerance float
        +min_policy_confidence float?
        +min_tunnel_bandwidth_kbps int
        +max_tunnel_bandwidth_kbps int
    }
    ValidatedSettings --> ClassificationMode
```

## Startup And Readiness Classes

### ArtifactPaths

```mermaid
classDiagram
    class ArtifactPaths {
        <<dataclass>>
        +model_path Path?
        +metadata_path Path?
        +policy_path Path
        +deterministic_rule_path Path?
    }
```

### AppServices

```mermaid
classDiagram
    class AppServices {
        <<dataclass>>
        +raw_settings RawSettings
        +settings ValidatedSettings?
        +readiness ReadinessState
        +classifier_pool ClassifierPool?
        +inference_thread_limiter CapacityLimiter?
        +model_metadata ModelMetadata?
        +policy_mapper PolicyMapper?
        +request_size_limit_bytes int
        +baseline_metrics BaselineMetrics?
    }
    AppServices --> RawSettings
    AppServices --> ValidatedSettings
    AppServices --> ReadinessState
```

### StartupValidationError

```mermaid
classDiagram
    class StartupValidationError {
        <<dataclass Exception>>
        +code str
        +message str
        +component str
        +failed_stage str
        +failed_check str?
        +retryable bool
        +__str__() str
    }
```

### ReadinessState

```mermaid
classDiagram
    class ReadinessState {
        <<dataclass>>
        +initialization_completed bool
        +ready bool
        +classification_mode str
        +completed_at_utc str?
        +model_loaded bool
        +metadata_loaded bool
        +policy_loaded bool
        +synthetic_inference_passed bool
        +model_name str?
        +model_schema_version str?
        +feature_count int?
        +class_count int?
        +initializing(classification_mode) ReadinessState
        +mark_failed(error) None
        +mark_ready(...) None
        +status str
    }
    ReadinessState --> StartupValidationError
```

## Error Classes

### ErrorDetail

```mermaid
classDiagram
    class ErrorDetail {
        <<dataclass>>
        +code str
        +message str
        +component str?
        +failed_stage str?
        +failed_check str?
        +retryable bool?
    }
```

### AppError And Subclasses

```mermaid
classDiagram
    class AppError {
        <<Exception>>
        +status_code int
        +code str
        +message str
        +component str?
        +failed_stage str?
        +failed_check str?
        +retryable bool?
        +request_id str?
        +to_error() ErrorDetail
    }
    class InvalidJsonError
    class InvalidContentLengthError
    class RequestTooLargeError
    class RequestValidationAppError
    class ModelEtherTypeUnsupportedError
    class ModelNotReadyError
    class ModelInferenceFailedError
    class InferenceCapacityExceededError
    class ModelOutputInvalidError
    class PolicyMappingFailedError
    AppError --> ErrorDetail
    AppError <|-- InvalidJsonError
    AppError <|-- InvalidContentLengthError
    AppError <|-- RequestTooLargeError
    AppError <|-- RequestValidationAppError
    AppError <|-- ModelEtherTypeUnsupportedError
    AppError <|-- ModelNotReadyError
    AppError <|-- ModelInferenceFailedError
    AppError <|-- InferenceCapacityExceededError
    AppError <|-- ModelOutputInvalidError
    AppError <|-- PolicyMappingFailedError
```

## Middleware Classes

### CorrelationIdMiddleware

```mermaid
classDiagram
    class CorrelationIdMiddleware {
        +app ASGIApp
        +__init__(app) None
        +__call__(scope, receive, send) None
    }
```

### RequestSizeLimitMiddleware

```mermaid
classDiagram
    class RequestSizeLimitMiddleware {
        +app ASGIApp
        +__init__(app) None
        +__call__(scope, receive, send) None
    }
    class _InvalidContentLengthSentinel
    RequestSizeLimitMiddleware --> _InvalidContentLengthSentinel
```

## HTTP Schema Classes

### Base Error Schemas

```mermaid
classDiagram
    class StrictBaseModel {
        <<Pydantic BaseModel>>
        extra=forbid
        populate_by_name=true
    }
    class ErrorBody {
        +code str
        +message str
        +component str?
        +failed_stage str?
        +failed_check str?
        +retryable bool?
    }
    class ErrorResponse {
        +request_id str?
        +error ErrorBody
    }
    StrictBaseModel <|-- ErrorBody
    StrictBaseModel <|-- ErrorResponse
    ErrorResponse --> ErrorBody
```

### Health Schemas

```mermaid
classDiagram
    class RootResponse {
        +service str
        +version str
        +status str
        +documentation str
    }
    class LivenessResponse {
        +status str
    }
    class ReadinessError {
        +code str
        +message str
        +component str?
        +failed_stage str?
        +failed_check str?
        +retryable bool?
    }
    class ReadySuccessResponse {
        +status str
        +ready bool
        +classification_mode str
        +model_loaded bool
        +metadata_loaded bool
        +policy_loaded bool
        +synthetic_inference_passed bool
    }
    class ReadyFailureResponse {
        +request_id str?
        +status str
        +ready bool
        +classification_mode str
        +error ReadinessError?
    }
    ReadyFailureResponse --> ReadinessError
```

### Inference Schemas

```mermaid
classDiagram
    class PacketFeatures {
        +eth_type int
        +ip_proto int
        +src_port int
        +dst_port int
        +validate_protocol_rules() PacketFeatures
    }
    class ClassifyRequest {
        +packet_features PacketFeatures
    }
    class PredictionBody {
        +class_id int
        +class_name str
        +confidence float
    }
    class PathConstraints {
        +requested_bandwidth_kbps int
        +setup_priority int
        +hold_priority int
    }
    class PolicyResponse {
        +profile_name str
        +dscp int
        +mpls_tc int
        +path_constraints PathConstraints
        +policy_fallback bool
        +policy_fallback_reason str?
    }
    class ClassifyResponse {
        +request_id str
        +model_name str
        +prediction PredictionBody
        +probabilities dict
        +policy PolicyResponse?
        +processing_time_ms float
    }
    class ModelClassInfo {
        +id int
        +name str
    }
    class ModelInfoResponse {
        +model_name str
        +target_name str
        +schema_version str
        +feature_order list
        +classes list
    }
    ClassifyRequest --> PacketFeatures
    PolicyResponse --> PathConstraints
    ClassifyResponse --> PredictionBody
    ClassifyResponse --> PolicyResponse
    ModelInfoResponse --> ModelClassInfo
```

## Model Classes

### ClassifierPoolObserver

```mermaid
classDiagram
    class ClassifierPoolObserver {
        <<Protocol>>
        +observe_wait(duration_seconds, outcome) None
        +set_state(capacity, available, borrowed) None
        +record_timeout() None
    }
```

### ClassifierPool

```mermaid
classDiagram
    class ClassifierPool~ClassifierT~ {
        -_capacity int
        -_available Queue
        -_observer ClassifierPoolObserver?
        +capacity int
        +available int
        +borrowed int
        +acquire(timeout_seconds) AsyncIterator
        -_observe_wait(duration_seconds, outcome) None
        -_publish_state() None
    }
    ClassifierPool --> ClassifierPoolObserver
```

### TrafficClassifier

```mermaid
classDiagram
    class TrafficClassifier {
        <<Protocol>>
        +predict(packet_features) PredictionResult
    }
```

### PredictionResult

```mermaid
classDiagram
    class PredictionResult {
        <<dataclass>>
        +class_id int
        +class_name str
        +confidence float
        +probabilities dict
    }
```

### Predictor

```mermaid
classDiagram
    class Predictor {
        -_booster Any
        -_metadata ModelMetadata
        -_probability_tolerance float
        +build_feature_matrix(packet_features) ndarray
        +predict(packet_features) PredictionResult
        -_normalize_output(raw_output) ndarray
    }
    Predictor --> ModelMetadata
    Predictor --> PredictionResult
```

### DeterministicClassifier

```mermaid
classDiagram
    class DeterministicClassifier {
        -_rules DeterministicRuleFile
        +predict(packet_features) PredictionResult
        -_classify(packet_features) str
    }
    DeterministicClassifier --> DeterministicRuleFile
    DeterministicClassifier --> PredictionResult
```

### DeterministicRuleFile

```mermaid
classDiagram
    class DeterministicRuleFile {
        <<Pydantic BaseModel>>
        +schema_version str
        +well_known_port_threshold int
        +icmp_ip_protocol int
        +destination_port_class_map dict
        +source_port_class_map dict
        +streaming_class_name str
        +validate_port_map_keys(value) dict
        +validate_contract() DeterministicRuleFile
    }
```

### ModelMetadata

```mermaid
classDiagram
    class ModelMetadata {
        <<Pydantic BaseModel>>
        +schema_version str
        +model_name str
        +target_name str
        +model_format str
        +feature_order list
        +feature_types dict
        +class_to_id dict
        +id_to_class dict
        +validate_schema_version(value) str
        +validate_contract() ModelMetadata
        +classes list
    }
```

## Policy Classes

### Policy Artifact Schemas

```mermaid
classDiagram
    class PolicyPathConstraints {
        +requested_bandwidth_kbps int
        +setup_priority int
        +hold_priority int
    }
    class TrafficPolicy {
        +profile_name str
        +dscp int
        +mpls_tc int
        +path_constraints PolicyPathConstraints
        +validate_profile_name(value) str
    }
    class PolicyFile {
        +schema_version str
        +default_profile TrafficPolicy
        +class_policies dict
        +validate_schema_version(value) str
    }
    TrafficPolicy --> PolicyPathConstraints
    PolicyFile --> TrafficPolicy
```

### PolicyMapper

```mermaid
classDiagram
    class PolicyMapper {
        -_policy_file PolicyFile
        -_min_policy_confidence float?
        +resolve(predicted_class, confidence) tuple
        +default_policy TrafficPolicy
    }
    PolicyMapper --> PolicyFile
    PolicyMapper --> TrafficPolicy
```

## Observability Classes

### ProcessIdentity

```mermaid
classDiagram
    class ProcessIdentity {
        <<dataclass frozen>>
        +service str
        +instance_id str
        +worker_id str
        +worker_pid int
        +process_name str
        +started_at_unix_seconds float
    }
```

### BaselineMetrics

```mermaid
classDiagram
    class BaselineMetrics {
        <<dataclass>>
        +classification_mode str
        +initialize_worker(identity) None
        +set_readiness(ready) None
        +set_pool_state(capacity, available, borrowed) None
        +set_state(capacity, available, borrowed) None
        +observe_wait(duration_seconds, outcome) None
        +record_timeout() None
    }
    BaselineMetrics --> ProcessIdentity
```

### ClassificationObservation

```mermaid
classDiagram
    class ClassificationObservation {
        <<dataclass>>
        +classification_mode str
        +enabled bool
        +started_at float
        +outcome str
        +completed bool
        +mark_outcome(outcome) None
        +finish() None
    }
```

## Logging And Message Classes

### ProcessIdentityFilter

```mermaid
classDiagram
    class ProcessIdentityFilter {
        +filter(record) bool
    }
    ProcessIdentityFilter --> ProcessIdentity
```

### JsonFormatter

```mermaid
classDiagram
    class JsonFormatter {
        +format(record) str
    }
```

### Messages

```mermaid
classDiagram
    class Messages {
        <<constants>>
        +artifact_path_resolution(configured_filename) str
        +artifact_not_found(configured_filename) str
        +artifact_not_regular_file(configured_filename) str
        +artifact_not_readable(configured_filename) str
        +artifact_empty(configured_filename) str
        +artifact_not_utf8(configured_filename) str
        +policy_file_validation_failed(details) str
        +unexpected_model_name(actual) str
        +policy_bandwidth_below_minimum(class_name) str
        +policy_bandwidth_above_maximum(class_name) str
    }
```
