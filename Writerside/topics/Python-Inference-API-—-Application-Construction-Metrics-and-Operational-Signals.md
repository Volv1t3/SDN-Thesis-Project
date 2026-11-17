# Application Construction — Metrics and Operational Signals

<show-structure for="chapter" depth="2"/>

The Python inference API publishes Prometheus families through the fixed `GET /metrics` route. The route is intentionally excluded from the OpenAPI schema, and it returns `404 Not Found` only after the application has constructed services whose validated settings disable `ENABLE_PROMETHEUS_METRICS`. When the feature is enabled, the scrape reads either the process-wide Prometheus registry or a registry assembled from `PROMETHEUS_MULTIPROC_DIR`; it does not invoke model inference, reload policy data, or change readiness. The application-construction page owns the route's placement in FastAPI, while [Observability](Python-Inference-API-—-Application-Construction-Observability.md) explains the identity and logging collaboration that makes worker-level samples attributable to a process.

## Scrape contract and collector lifetime {id="python-metrics-scrape-contract-and-collector-lifetime"}

The metrics modules construct their collectors at module import time. `BaselineMetrics` supplies the bounded classification-mode label and publishes worker identity, readiness, startup, and classifier-pool state. `ClassificationObservation` and the inference route then record request outcomes, durations, results, confidence, policy decisions, and controlled failures. This division matters because a failed startup can still increment its failure counter, whereas request-time metrics are emitted only after services have validated settings and the corresponding request path runs.

<code-block lang="python" src="../../src/main/python/api/app/api/sdn_mpls_ml_metrics_route.py" include-lines="19-56"/>

The route creates a separate `CollectorRegistry` only for a configured multiprocess directory. Otherwise it returns the default registry, which contains the import-time collectors listed below. The `Content-Type` is supplied by `prometheus_client.CONTENT_TYPE_LATEST`, so consumers receive Prometheus text exposition rather than a JSON application response.

<code-block lang="mermaid">
sequenceDiagram
    participant P as Prometheus
    participant R as GET /metrics
    participant S as app.state.services
    participant G as Prometheus registry
    P->>R: scrape
    R->>S: inspect validated settings
    alt metrics disabled after validation
        R-->>P: 404 Not Found
    else default registry
        R->>G: generate_latest(REGISTRY)
        G-->>R: exposition bytes
        R-->>P: 200 Prometheus text
    else multiprocess registry
        R->>G: attach MultiProcessCollector
        G-->>R: merged exposition bytes
        R-->>P: 200 Prometheus text
    end
</code-block>

## Metric kinds and labels {id="python-metrics-kinds-and-labels"}

Each row names the family as it is declared in the Python collectors. A `Counter` accumulates occurrences, a `Gauge` represents the latest state or current level, and a `Histogram` emits bucket, sum, and count samples for a distribution. The labels column is the collector's declared series key. `classification_mode` distinguishes the supported inference modes, while `instance_id` and `worker_id` identify the worker that initialized the baseline samples. Values such as `class_name`, `profile_name`, `error_code`, and failure reasons are supplied by the producing path and should be treated as dimensions rather than numeric sample values.

This catalog covers the 20 application-defined collector families. When the route uses the default `REGISTRY`, the installed Prometheus client can also contribute its own Python-runtime and process collectors. Those dependency-provided families are not declared by the API and are absent from a multiprocess scrape that instead builds a fresh registry and attaches `MultiProcessCollector`. They are consequently outside the stable application metric contract documented here.

### Worker, startup, and classifier-pool families {id="python-metrics-baseline-families"}

The baseline families are initialized while `initialize_services()` still owns startup validation. The worker identity and zero readiness state are recorded before validation begins. A successful validation observes the ready duration, sets readiness to one, and publishes the pool state. A controlled startup failure instead records a failed duration, increments the failure series, and leaves readiness at zero. The pool itself drives later capacity, availability, borrowing, wait, and timeout updates through the observer contract documented in [Model Runtime](Python-Inference-API-—-Application-Construction-Model-Runtime.md#metrics-and-invariants).

<code-block lang="python" src="../../src/main/python/api/app/observability/sdn_mpls_ml_metrics.py" include-lines="23-67"/>

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_dependencies.py" include-lines="212-220"/>

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_dependencies.py" include-lines="330-364"/>

| Metric name | Prometheus type | Labels | Operational meaning |
|---|---|---|---|
| `sdn_mpls_ml_api_obs_worker_info` | `Gauge` | `instance_id`, `worker_id`, `classification_mode` | A value of one identifies each active API worker and its configured classification mode. |
| `sdn_mpls_ml_api_obs_worker_start_time_seconds` | `Gauge` | `instance_id`, `worker_id` | Unix timestamp at which the worker process identity was initialized. |
| `sdn_mpls_ml_api_obs_readiness` | `Gauge` | `classification_mode` | Current service readiness, represented as zero before or after failed validation and one after successful startup validation. |
| `sdn_mpls_ml_api_obs_startup_validation_duration_seconds` | `Histogram` | `classification_mode`, `outcome` | Distribution of the complete startup validation duration, separated into `ready` and `failed` outcomes. |
| `sdn_mpls_ml_api_obs_startup_failures_total` | `Counter` | `failed_stage`, `error_code` | Controlled startup validation failures, preserving both the failing phase and the application error code. |
| `sdn_mpls_ml_api_obs_classifier_pool_capacity` | `Gauge` | `classification_mode` | Total classifiers configured in the active pool. |
| `sdn_mpls_ml_api_obs_classifier_pool_available` | `Gauge` | `classification_mode` | Classifiers currently available for acquisition. |
| `sdn_mpls_ml_api_obs_classifier_pool_borrowed` | `Gauge` | `classification_mode` | Classifiers currently leased by request execution. |
| `sdn_mpls_ml_api_obs_classifier_pool_wait_seconds` | `Histogram` | `classification_mode`, `outcome` | Distribution of time spent waiting to acquire a classifier, partitioned by the acquisition outcome. |
| `sdn_mpls_ml_api_obs_classifier_pool_timeouts_total` | `Counter` | `classification_mode` | Acquisitions that timed out because a classifier was not available within the configured wait. |

### Classification, policy, and request-failure families {id="python-metrics-request-families"}

Request-time instrumentation distinguishes the total classification lifecycle from the synchronous classifier invocation that runs in an AnyIO worker thread. The observation object records the classification request counter and end-to-end duration once, even if a caller reaches its finalizer more than once. The helper around `classifier.predict` records only the model invocation duration and categorizes known invalid output and cancellation separately from its default failure outcome. This relationship is why the two duration families should not be interpreted as interchangeable latency measures.

<code-block lang="python" src="../../src/main/python/api/app/observability/sdn_mpls_ml_classification_metrics.py" include-lines="44-97"/>

<code-block lang="python" src="../../src/main/python/api/app/observability/sdn_mpls_ml_classification_metrics.py" include-lines="123-140"/>

<code-block lang="python" src="../../src/main/python/api/app/observability/sdn_mpls_ml_classification_metrics.py" include-lines="165-194"/>

<code-block lang="python" src="../../src/main/python/api/app/api/sdn_mpls_ml_inference_route.py" include-lines="143-145"/>

<code-block lang="python" src="../../src/main/python/api/app/api/sdn_mpls_ml_inference_route.py" include-lines="229-260"/>

<code-block lang="python" src="../../src/main/python/api/app/api/sdn_mpls_ml_inference_route.py" include-lines="319-321"/>

| Metric name | Prometheus type | Labels | Operational meaning |
|---|---|---|---|
| `sdn_mpls_ml_api_obs_classification_requests_total` | `Counter` | `classification_mode`, `outcome` | Completed classification request lifecycles, categorized by the final recorded outcome. |
| `sdn_mpls_ml_api_obs_classification_in_progress` | `Gauge` | `classification_mode` | Requests that have entered classification and have not yet reached the route finalizer. |
| `sdn_mpls_ml_api_obs_classification_duration_seconds` | `Histogram` | `classification_mode`, `outcome` | End-to-end classification-request duration from observation construction to finalization. |
| `sdn_mpls_ml_api_obs_inference_duration_seconds` | `Histogram` | `classification_mode`, `outcome` | Time spent executing `classifier.predict` through the AnyIO thread bridge. |
| `sdn_mpls_ml_api_obs_classification_results_total` | `Counter` | `classification_mode`, `class_name` | Valid model predictions, partitioned by the predicted traffic class. |
| `sdn_mpls_ml_api_obs_prediction_confidence` | `Histogram` | `classification_mode`, `class_name` | Distribution of confidence values associated with valid predictions. |
| `sdn_mpls_ml_api_obs_policy_selections_total` | `Counter` | `profile_name`, `fallback` | Policy profiles selected after a prediction, with the fallback decision recorded as a boolean string. |
| `sdn_mpls_ml_api_obs_policy_fallbacks_total` | `Counter` | `reason`, `predicted_class` | Fallback policy selections, partitioned by the stable fallback reason and predicted class. |
| `sdn_mpls_ml_api_obs_request_errors_total` | `Counter` | `error_code`, `component` | Controlled HTTP application errors emitted by their producing layer. |
| `sdn_mpls_ml_api_obs_request_body_rejections_total` | `Counter` | `reason` | HTTP body rejections attributed to the bounded middleware validation reason. |

<code-block lang="mermaid">
flowchart LR
    Request[Classification request] --> InProgress[Increment in-progress gauge]
    InProgress --> Pool[Acquire classifier]
    Pool --> Predict[execute_instrumented_inference]
    Predict --> Result[Record result and confidence]
    Result --> Policy[Record policy selection or fallback]
    Policy --> Finalize[finish observation and decrement gauge]
    Finalize --> Registry[Prometheus registry]
    Error[Exception handlers and size middleware] --> ErrorCounter[Request error or body rejection counter]
    ErrorCounter --> Registry
</code-block>

The request counters are intentionally conditional on the same validated feature flag as the scrape route. A request can still be rejected or return an application error while metrics are disabled, but no family update occurs in that case. The error and body-rejection counts complement the exception and middleware contracts described in [Middleware](Python-Inference-API-—-Application-Construction-Middleware.md) and [Inference](Python-Inference-API-—-Application-Construction-Inference.md#inference-metrics); they do not replace the correlated JSON error response or structured log event.

## Series interpretation across processes {id="python-metrics-series-interpretation-across-processes"}

The baseline gauges use Prometheus multiprocess aggregation modes that match their operational role. Worker identity and start time use `liveall`, readiness uses `livemin`, and pool and in-progress gauges use `livesum`. Consequently, a scrape from a multiprocess deployment can report every live worker identity, the lowest readiness among workers, and aggregate resource occupancy. Histograms and counters retain their Prometheus-client behavior, while the route selects a multiprocess collector only when the configured directory is nonblank.

The `worker_info` family is an identity marker rather than a numeric measurement. Its sample value is always one, and its labels carry the useful data. Conversely, timestamps, capacity, availability, borrowing, and in-progress values are gauges whose numeric value represents the current state at scrape time. Histograms expose the declared finite buckets together with their `_sum` and `_count` samples, so percentile or rate calculations must use those Prometheus histogram series rather than treating the family name as one scalar.

The policy families connect the API's prediction result to its chosen system behavior. [Policy Mapping](Python-Inference-API-—-Application-Construction-Policy.md) owns the policy-file validation and deterministic mapper decisions. This metrics page records only the request-time evidence that the inference route emits after receiving a selection, which prevents a mapper invocation outside HTTP processing from creating Prometheus side effects.
