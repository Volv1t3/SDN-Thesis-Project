# Python API Per-Module Component Diagram Snippets

Each section contains a standalone Mermaid snippet that can be embedded directly in Writerside.

## Application Main

```mermaid
flowchart LR
    Uvicorn["Uvicorn"] --> App["FastAPI app"]
    App --> Lifespan["lifespan(app)"]
    App --> Middleware["middleware list"]
    App --> Routers["health_router, inference_router, metrics_router"]
    App --> Handlers["exception handlers"]
    Lifespan --> Settings["get_raw_settings"]
    Lifespan --> Identity["initialize_process_identity"]
    Lifespan --> Logging["configure_logging"]
    Lifespan --> Services["build_services"]
    Handlers --> ErrorResponses["build_error_response"]
    Handlers --> RequestMetrics["record_request_error"]
```

## API Routes

```mermaid
flowchart LR
    Health["sdn_mpls_ml_health_route"] --> Root["GET / root()"]
    Health --> Live["GET /health/live live()"]
    Health --> Ready["GET /health/ready ready()"]
    Inference["sdn_mpls_ml_inference_route"] --> ModelInfo["GET /api/v1/model model_info()"]
    Inference --> Classify["POST /api/v1/classify classify()"]
    Metrics["sdn_mpls_ml_metrics_route"] --> Scrape["GET /metrics metrics()"]
    Ready --> ReadinessState["ReadinessState"]
    Classify --> AppServices["AppServices"]
    Classify --> Pool["ClassifierPool"]
    Classify --> PolicyMapper["PolicyMapper"]
    Scrape --> Prometheus["prometheus_client.generate_latest"]
```

## Middleware

```mermaid
flowchart LR
    Caller["HTTP caller"] --> Correlation["CorrelationIdMiddleware"]
    Correlation -->|"adds request_id"| Size["RequestSizeLimitMiddleware"]
    Size -->|"accepted body"| FastAPI["FastAPI routing"]
    Size -->|"invalid length / too large"| ErrorResponse["build_error_response"]
    Correlation -->|"unhandled downstream exception"| InternalError["500 INTERNAL_ERROR"]
    Size --> BodyMetrics["record_request_body_rejection"]
    Size --> ErrorMetrics["record_request_error"]
```

## Schemas

```mermaid
flowchart TB
    StrictBaseModel["StrictBaseModel"] --> ErrorBody["ErrorBody"]
    StrictBaseModel --> ErrorResponse["ErrorResponse"]
    StrictBaseModel --> Health["RootResponse / LivenessResponse / Ready responses"]
    StrictBaseModel --> PacketFeatures["PacketFeatures"]
    PacketFeatures --> ClassifyRequest["ClassifyRequest"]
    PredictionBody["PredictionBody"] --> ClassifyResponse["ClassifyResponse"]
    PolicyResponse["PolicyResponse"] --> ClassifyResponse
    PathConstraints["PathConstraints"] --> PolicyResponse
    ModelClassInfo["ModelClassInfo"] --> ModelInfoResponse["ModelInfoResponse"]
```

## Startup Dependencies

```mermaid
flowchart TB
    Raw["RawSettings"] --> ValidateSettings["_validate_settings"]
    ValidateSettings --> Settings["ValidatedSettings"]
    Settings --> ValidateArtifacts["_validate_artifacts"]
    ValidateArtifacts --> ArtifactPaths["ArtifactPaths"]
    ArtifactPaths --> ValidateSchemas["_validate_schemas"]
    ValidateSchemas --> Metadata["ModelMetadata"]
    ValidateSchemas --> Policy["PolicyFile"]
    ValidateSchemas --> Rules["DeterministicRuleFile"]
    Metadata --> BuildPool["_build_classifier_pool"]
    Rules --> BuildPool
    BuildPool --> Pool["ClassifierPool"]
    Policy --> CompletePolicy["_validate_complete_policy_map"]
    CompletePolicy --> AppServices["AppServices ready"]
    AppServices --> Readiness["ReadinessState.mark_ready"]
```

## Model Runtime

```mermaid
flowchart LR
    Classify["classify()"] --> Pool["ClassifierPool.acquire"]
    Pool --> Protocol["TrafficClassifier protocol"]
    Protocol --> Predictor["Predictor"]
    Protocol --> Deterministic["DeterministicClassifier"]
    Predictor --> XGBoost["xgboost.Booster"]
    Predictor --> Metadata["ModelMetadata"]
    Predictor --> Prediction["PredictionResult"]
    Deterministic --> Rules["DeterministicRuleFile"]
    Deterministic --> Prediction
    Pool --> Observer["ClassifierPoolObserver / BaselineMetrics"]
```

## Policy

```mermaid
flowchart LR
    PolicyJson["policy JSON"] --> Loader["load_policy_file"]
    Loader --> PolicyFile["PolicyFile"]
    PolicyFile --> TrafficPolicy["TrafficPolicy"]
    TrafficPolicy --> PathConstraints["PathConstraints"]
    PolicyFile --> Mapper["PolicyMapper"]
    Mapper -->|"class + confidence"| SelectedPolicy["selected policy"]
    Mapper -->|"low confidence"| DefaultPolicy["default policy fallback"]
```

## Observability

```mermaid
flowchart TB
    IdentityInit["initialize_process_identity"] --> Identity["ProcessIdentity"]
    Identity --> WorkerMetrics["BaselineMetrics.initialize_worker"]
    Baseline["BaselineMetrics"] --> ReadinessGauge["READINESS"]
    Baseline --> PoolGauges["CLASSIFIER_POOL_*"]
    Baseline --> PoolWait["CLASSIFIER_POOL_WAIT_SECONDS"]
    Observation["ClassificationObservation"] --> RequestCounter["CLASSIFICATION_REQUESTS_TOTAL"]
    Observation --> RequestDuration["CLASSIFICATION_DURATION_SECONDS"]
    Inference["execute_instrumented_inference"] --> InferenceDuration["INFERENCE_DURATION_SECONDS"]
    Errors["record_request_error / record_request_body_rejection"] --> ErrorCounters["REQUEST_* counters"]
```

## Logging And Messages

```mermaid
flowchart LR
    Configure["configure_logging"] --> Console["StreamHandler"]
    Configure --> File["RotatingFileHandler"]
    Console --> Formatter["JsonFormatter"]
    File --> Formatter
    Formatter --> IdentityFilter["ProcessIdentityFilter"]
    IdentityFilter --> ProcessIdentity["get_process_identity"]
    Messages["Messages"] --> Exceptions["AppError subclasses"]
    Messages --> Startup["startup validation"]
    Messages --> Middleware["middleware logs"]
```

## Artifacts And Tests

```mermaid
flowchart TB
    Dockerfile["Dockerfile"] --> Runtime["Runtime container"]
    Requirements["requirements.txt"] --> Runtime
    DevRequirements["requirements-dev.txt"] --> Tests["pytest tests"]
    Configs["configs/*.json"] --> Startup["startup validation"]
    Models["models/*.json"] --> Startup
    Tests --> API["API behavior"]
    Tests --> Middleware["middleware behavior"]
    Tests --> Model["model behavior"]
    Tests --> Observability["metrics/logging behavior"]
```
