# Application Construction — Observability

<show-structure for="chapter" depth="3"/>

The observability package records process, startup, pool, classification, inference, and controlled-request-failure
evidence for the Python Inference API. It is deliberately an instrumentation boundary. It publishes Prometheus
families when the application enables them, but it does not decide routing, validate a request body, load a model, or
choose a traffic policy. Those responsibilities remain with the construction, middleware, inference, and model-runtime
components linked throughout this topic.

The package is composed of `__init__.py`, process identity, baseline metrics, and classification metrics. Its metric
objects are module-level Prometheus collectors. The small recorder classes and helper functions add labels and values
only at the source-backed points where the application has a final state to report. The fixed `GET /metrics` route
serializes the resulting registry for a scraper, but is not itself a producer of application measurements.

## Scope and construction boundary {id="observability-scope"}

`app.observability.__init__` contains only the package documentation anchor. It exports no registration function and
creates no collectors. Importing the concrete modules defines the collectors, while application construction creates a
`BaselineMetrics` recorder only after validated settings allow Prometheus metrics. The following relationship keeps
configuration and runtime composition outside the observability package.

<code-block lang="mermaid">
flowchart TD
    Main["app.sdn_mpls_ml_main"] --> Dependencies["build_services"]
    Dependencies --> Baseline["BaselineMetrics"]
    Main --> Identity["initialize_process_identity"]
    Baseline --> Pool["ClassifierPool observer"]
    Inference["classification route"] --> Classification["ClassificationObservation"]
    Middleware["body-size middleware"] --> Rejections["record request rejection"]
    Baseline --> Registry["Prometheus collectors"]
    Classification --> Registry
    Rejections --> Registry
    Registry --> Scrape["fixed GET /metrics"]
</code-block>

The [Application Construction](Python-Inference-API-—-Application-Construction.md#construction-boundary) topic
describes the composition root, lifespan, router mounting, and state container. This page narrows that wider
construction story to the identity and collector objects created at those boundaries. It does not repeat the general
startup validation narrative.

The [Environment Variables](Python-Inference-API-—-Application-Construction-Environment-Variables.md) topic
documents configuration inputs. In observability terms, the relevant validated switch is
`enable_prometheus_metrics`, which gates metric writes in the producers and changes the scrape route's behavior. The
package does not parse that setting directly.

## Process identity lifecycle {id="process-identity-lifecycle"}

`ProcessIdentity` is an immutable, slotted data class. It carries the service name, deployment instance identifier,
worker UUID, process ID, multiprocessing process name, and Unix start time. The identity identifies one worker within
an application instance. It is not an HTTP request identifier and does not replace the request-correlation mechanism.

<code-block lang="python" src="../../src/main/python/api/app/observability/sdn_mpls_ml_identity.py" include-lines="24-46"/>

`initialize_process_identity` protects the process-local singleton with a thread lock. A later call returns the
existing identity. On the first call, a non-empty configured instance identifier is trimmed and used. Otherwise, the
function uses `HOSTNAME`, then `socket.gethostname()`, then `unknown-instance`. It generates a new UUID for the worker,
reads the current PID and process name, and records the current Unix timestamp.

<code-block lang="python" src="../../src/main/python/api/app/observability/sdn_mpls_ml_identity.py" include-lines="49-84"/>

<code-block lang="mermaid">
sequenceDiagram
    participant Life as lifespan
    participant Init as initialize_process_identity
    participant Lock as identity lock
    participant Store as process singleton
    Life->>Init: service, configured instance ID
    Init->>Lock: enter
    alt identity already exists
        Lock-->>Init: existing ProcessIdentity
    else first initialization
        Init->>Init: resolve instance ID and create worker UUID
        Init->>Store: save immutable identity
        Store-->>Init: ProcessIdentity
    end
    Init-->>Life: ProcessIdentity
</code-block>

The construction lifespan initializes identity before logging configuration and service construction. That ordering is
shown in the [Application Construction lifecycle](Python-Inference-API-—-Application-Construction.md#lifespan-source-path).
Observability uses the identity later to label worker information and worker start time. It does not attach this
identity to a `ClassifyResponse` or to an error body.

`get_process_identity` returns the singleton or `None` before initialization. `_reset_process_identity_for_tests`
clears it under the same lock. The latter is test support, not a production lifecycle transition.

<code-block lang="python" src="../../src/main/python/api/app/observability/sdn_mpls_ml_identity.py" include-lines="87-100"/>

## Baseline metrics and startup evidence {id="baseline-metrics"}

The baseline module declares collectors for worker identity, worker start time, readiness, startup validation duration,
startup failures, and classifier-pool state. These are distinct Prometheus metric families rather than fields on one
status object. Gauges model current worker, readiness, and pool state. The histogram records durations, and counters
accumulate controlled startup failures and pool timeouts.

<code-block lang="python" src="../../src/main/python/api/app/observability/sdn_mpls_ml_metrics.py" include-lines="23-67"/>

| Metric family | Type | Labels | Meaning |
|---|---|---|---|
| `sdn_mpls_ml_api_obs_worker_info` | Gauge | `instance_id`, `worker_id`, `classification_mode` | A live worker identity sample set to one. |
| `sdn_mpls_ml_api_obs_worker_start_time_seconds` | Gauge | `instance_id`, `worker_id` | The worker's recorded Unix start time. |
| `sdn_mpls_ml_api_obs_readiness` | Gauge | `classification_mode` | One when the recorder is told the service is ready, otherwise zero. |
| `sdn_mpls_ml_api_obs_startup_validation_duration_seconds` | Histogram | `classification_mode`, `outcome` | Duration of complete startup validation by outcome. |
| `sdn_mpls_ml_api_obs_startup_failures_total` | Counter | `failed_stage`, `error_code` | Controlled startup failures grouped by their reported stage and code. |
| `sdn_mpls_ml_api_obs_classifier_pool_capacity` | Gauge | `classification_mode` | Total managed classifier capacity. |
| `sdn_mpls_ml_api_obs_classifier_pool_available` | Gauge | `classification_mode` | Currently idle classifiers. |
| `sdn_mpls_ml_api_obs_classifier_pool_borrowed` | Gauge | `classification_mode` | Classifiers currently leased from the queue. |
| `sdn_mpls_ml_api_obs_classifier_pool_wait_seconds` | Histogram | `classification_mode`, `outcome` | Duration of a pool acquisition attempt. |
| `sdn_mpls_ml_api_obs_classifier_pool_timeouts_total` | Counter | `classification_mode` | Pool acquisition timeouts. |

`BaselineMetrics.initialize_worker` writes the identity labels and start timestamp. `set_readiness` converts the
boolean state to zero or one. `set_pool_state` writes capacity, availability, and borrowed values as one consistent
observer update. These methods do not obtain a classifier, read model artifacts, or trigger a readiness probe.

<code-block lang="python" src="../../src/main/python/api/app/observability/sdn_mpls_ml_metrics.py" include-lines="70-129"/>

Startup construction passes the process identity to `initialize_services`. The service initializer invokes baseline
recording when a recorder exists. This link is specific to evidence publication. The startup checks, failure caching,
and `AppServices` ownership are documented in
[Application Construction](Python-Inference-API-—-Application-Construction.md#startup-validation).

<code-block lang="mermaid">
flowchart TD
    Identity["ProcessIdentity"] --> Worker["initialize_worker"]
    Settings["validated classification mode"] --> Baseline["BaselineMetrics"]
    Baseline --> Worker
    Startup["initialize_services"] --> Duration["startup duration observation"]
    Startup --> Ready["set_readiness true or false"]
    Failure["StartupValidationError"] --> FailureCounter["startup failures counter"]
    Ready --> ReadinessGauge["readiness gauge"]
</code-block>

The metric labels have deliberately bounded meanings. `classification_mode` distinguishes the configured mode, not an
individual model artifact. `instance_id` identifies deployment context. `worker_id` identifies the generated worker
identity. `failed_stage` and `error_code` are the structured startup values supplied by the startup path. No request
body, packet-feature value, or client-provided identifier becomes a baseline metric label.

## Classifier-pool observer interaction {id="classifier-pool-observer"}

The generic classifier pool accepts an optional `ClassifierPoolObserver`. Its protocol has exactly three reporting
operations: observe an acquisition wait, publish a state tuple, and record a timeout. This keeps the pool independent
of Prometheus types. `BaselineMetrics` satisfies the protocol through `observe_wait`, `set_state`, and
`record_timeout`.

<code-block lang="python" src="../../src/main/python/api/app/model/sdn_mpls_ml_classifier_pool.py" include-lines="31-46"/>

The pool stores its capacity, optional observer, and an asyncio queue. It publishes initial state after placing every
classifier in the queue. Its public `available` count is queue size. `borrowed` is capacity minus queue size. The
observer receives those values but does not own pool concurrency or enforce the queue invariant.

<code-block lang="python" src="../../src/main/python/api/app/model/sdn_mpls_ml_classifier_pool.py" include-lines="62-107"/>

<code-block lang="mermaid">
sequenceDiagram
    participant Build as service construction
    participant Pool as ClassifierPool
    participant Observer as BaselineMetrics
    participant Queue as asyncio queue
    Build->>Pool: construct classifiers and observer
    Pool->>Queue: enqueue every classifier
    Pool->>Observer: set_state(capacity, available, borrowed)
    Note over Pool,Observer: Initial state is published after queue setup
    Pool->>Queue: acquire with timeout
    Pool->>Observer: observe_wait(duration, success or timeout)
    alt timeout
        Pool->>Observer: record_timeout()
    else lease and release
        Pool->>Observer: set_state after each change
    end
</code-block>

During `acquire`, the pool measures elapsed time with `time.perf_counter`. A successful queue acquisition produces a
`success` wait outcome, removes one classifier, and publishes the new state. A timeout records the `timeout` wait
outcome, increments the timeout counter through the observer, and raises `InferenceCapacityExceededError`. The
context manager returns a leased classifier to the queue and publishes state in its final path.

<code-block lang="python" src="../../src/main/python/api/app/model/sdn_mpls_ml_classifier_pool.py" include-lines="109-209"/>

The [Model Runtime](Python-Inference-API-—-Application-Construction-Model-Runtime.md) topic explains classifier
construction and prediction implementation. The [Inference](Python-Inference-API-—-Application-Construction-Inference.md)
topic explains how a route leases the pool. This topic only establishes the observer contract and the pool state and
wait measurements emitted at that boundary.

`BaselineMetrics.observe_wait` maps the pool outcome and configured classification mode to the wait histogram.
`record_timeout` increments the timeout counter. Both operations are passive recording. They do not change queue
capacity, release a classifier, or translate the raised capacity error into HTTP.

<code-block lang="python" src="../../src/main/python/api/app/observability/sdn_mpls_ml_metrics.py" include-lines="120-149"/>

## Classification observation lifecycle {id="classification-observation"}

Classification metrics live in `sdn_mpls_ml_classification_metrics.py`. The declared families cover request outcome
and duration, active work, inference duration, valid results, confidence, policy selection and fallback, controlled
request errors, and rejected request bodies. The module deliberately separates classification-level outcome from
inference-only outcome because a request includes readiness, pool, policy, and response work beyond prediction.

<code-block lang="python" src="../../src/main/python/api/app/observability/sdn_mpls_ml_classification_metrics.py" include-lines="44-97"/>

| Metric family | Type | Labels | Producer meaning |
|---|---|---|---|
| `sdn_mpls_ml_api_obs_classification_requests_total` | Counter | `classification_mode`, `outcome` | One completed classification observation. |
| `sdn_mpls_ml_api_obs_classification_in_progress` | Gauge | `classification_mode` | Active classifications while the route owns the request. |
| `sdn_mpls_ml_api_obs_classification_duration_seconds` | Histogram | `classification_mode`, `outcome` | Total classification observation duration. |
| `sdn_mpls_ml_api_obs_inference_duration_seconds` | Histogram | `classification_mode`, `outcome` | Time spent in instrumented prediction execution. |
| `sdn_mpls_ml_api_obs_classification_results_total` | Counter | `classification_mode`, `class_name` | Valid predictions by resolved class. |
| `sdn_mpls_ml_api_obs_prediction_confidence` | Histogram | `classification_mode`, `class_name` | Distribution of returned prediction confidence. |
| `sdn_mpls_ml_api_obs_policy_selections_total` | Counter | `profile_name`, `fallback` | Policy-profile selection and whether it was fallback. |
| `sdn_mpls_ml_api_obs_policy_fallbacks_total` | Counter | `reason`, `predicted_class` | Fallbacks grouped by source-provided reason and prediction. |
| `sdn_mpls_ml_api_obs_request_errors_total` | Counter | `error_code`, `component` | A controlled request error at its producing layer. |
| `sdn_mpls_ml_api_obs_request_body_rejections_total` | Counter | `reason` | A body-size middleware rejection with bounded reason. |

`ClassificationObservation` starts with an `internal_error` outcome and records once. A route or surrounding path can
set the final outcome with `mark_outcome`. `finish` is idempotent. When enabled, it increments the request counter
and observes total elapsed time. When disabled, it only marks completion and emits no Prometheus sample.

<code-block lang="python" src="../../src/main/python/api/app/observability/sdn_mpls_ml_classification_metrics.py" include-lines="100-140"/>

<code-block lang="mermaid">
stateDiagram-v2
    [*] --> Started: ClassificationObservation created
    Started --> OutcomeMarked: mark_outcome
    Started --> Finished: finish with default internal_error
    OutcomeMarked --> Finished: finish
    Finished --> Finished: repeated finish has no effect
    Finished --> [*]
</code-block>

The [Inference route boundary](Python-Inference-API-—-Application-Construction-Inference.md) owns HTTP request
validation, classifier leasing, policy resolution, and response construction. Its observability-specific role is to
create the observation, update the active-work gauge, mark final outcomes, and finish exactly once through its control
flow. The route remains the authoritative source of the outcome vocabulary it supplies.

<code-block lang="python" src="../../src/main/python/api/app/api/sdn_mpls_ml_inference_route.py" include-lines="97-222"/>

The classification gauge is decremented in the route's final path when metrics are enabled. The `finally` also calls
`observation.finish`, so a normal result and a controlled exception both reach the recording lifecycle. This does not
mean every request has valid prediction or policy-result labels. Those metric families are written only after the
corresponding route steps have produced their values.

## Instrumented inference {id="instrumented-inference"}

`execute_instrumented_inference` is the narrow measurement wrapper around `classifier.predict`. It starts a
high-resolution timer, runs the synchronous prediction through the supplied `run_sync` function with the supplied
AnyIO limiter, and returns the classifier's result on success. The limiter remains a runtime-concurrency mechanism.
The helper records timing but does not construct or configure that limiter.

<code-block lang="python" src="../../src/main/python/api/app/observability/sdn_mpls_ml_classification_metrics.py" include-lines="143-195"/>

The inference outcome begins as `failed`. A returned result changes it to `success`. `ModelOutputInvalidError` changes
it to `output_invalid` and is re-raised. `asyncio.CancelledError` changes it to `cancelled` and is also re-raised. In
the final block, an enabled recorder observes the inference duration with the classification mode and final outcome.

<code-block lang="mermaid">
sequenceDiagram
    participant Route as classification route
    participant Pool as leased classifier
    participant Helper as execute_instrumented_inference
    participant AnyIO as to_thread.run_sync
    participant Metric as inference histogram
    Route->>Helper: classifier, features, mode, limiter
    Helper->>AnyIO: classifier.predict(features)
    alt result returned
        AnyIO-->>Helper: prediction
        Helper->>Metric: observe(success duration)
        Helper-->>Route: prediction
    else invalid output
        AnyIO-->>Helper: ModelOutputInvalidError
        Helper->>Metric: observe(output_invalid duration)
        Helper-->>Route: re-raise
    else cancellation or other failure
        AnyIO-->>Helper: exception
        Helper->>Metric: observe(cancelled or failed duration)
        Helper-->>Route: re-raise
    end
</code-block>

The [Model Runtime](Python-Inference-API-—-Application-Construction-Model-Runtime.md) page is the backlink for
predictor contracts and model-output validation. The [Inference](Python-Inference-API-—-Application-Construction-Inference.md)
page is the backlink for the route's limiter and lease sequence. The helper is documented here only as the source of
the inference-duration measurement and outcome labels.

## Controlled request-error producers {id="controlled-request-errors"}

`record_request_error` increments `request_errors_total` only when its producer passes `enabled=True`. It maps an
absent component to `unknown`. `record_request_body_rejection` uses the same gate for the body-rejection counter. The
helpers do not create HTTP responses, log errors, or infer an error code.

<code-block lang="python" src="../../src/main/python/api/app/observability/sdn_mpls_ml_classification_metrics.py" include-lines="197-221"/>

The central application error handlers are producers for controlled application and framework error paths. They call
the helper after their own error classification determines an error code and component. The handler's responsibilities
for status selection, error response schema, structured logging, and request correlation remain documented under
[Application Construction error handling](Python-Inference-API-—-Application-Construction.md#error-boundary).

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_main.py" include-lines="412-528"/>

Request-validation and JSON-decoding paths also derive their own structured error information before recording. This
is important for metric ownership: the producer that knows the final controlled error increments the counter. A
later layer does not increment the same error merely because it serializes a response.

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_main.py" include-lines="530-655"/>

<code-block lang="mermaid">
flowchart TD
    RouteError["AppError handler"] --> ErrorMetric["record_request_error"]
    Validation["request validation handler"] --> ErrorMetric
    Json["JSON decoder handler"] --> ErrorMetric
    Size["body-size middleware"] --> BodyMetric["record_request_body_rejection"]
    Size --> ErrorMetric
    ErrorMetric --> Counter["request errors counter"]
    BodyMetric --> RejectionCounter["body rejections counter"]
</code-block>

The [Middleware](Python-Inference-API-—-Application-Construction-Middleware.md) page explains ASGI ordering,
correlation, byte buffering, and response short-circuiting. At its observability boundary, the request-size middleware
records the bounded reasons `invalid_content_length`, `declared_size_exceeded`, and `actual_size_exceeded`. It records
both a rejection and its controlled error before returning its own error response, because the request never reaches
the central application handler.

<code-block lang="python" src="../../src/main/python/api/app/middleware/sdn_mpls_ml_request_size_validation_middleware.py" include-lines="109-176"/>

<code-block lang="python" src="../../src/main/python/api/app/middleware/sdn_mpls_ml_request_size_validation_middleware.py" include-lines="237-260"/>

`_record_rejection` derives the enablement flag from the application services and validated settings. It then invokes
the body-rejection helper and the general controlled-error helper. The middleware intentionally does not call the
central handler for that same condition, which avoids a duplicate error-counter increment.

<code-block lang="python" src="../../src/main/python/api/app/middleware/sdn_mpls_ml_request_size_validation_middleware.py" include-lines="396-423"/>

The error metric's `error_code` and `component` labels identify the controlled error classification supplied by the
producer. The body metric's `reason` label records the constrained body-inspection reason. They are not free-form
message labels, and no raw body content or request ID is attached to these metrics.

## Metric production and scrape exposure {id="metric-production-and-scrape"}

Metric production and metric exposition occur at different boundaries. Baseline, pool, classification, inference,
and error producers write collectors during process or request execution. The scrape route later obtains a registry and
uses `generate_latest` to return Prometheus exposition bytes. A successful scrape does not cause an inference, replay
a request body, or recompute readiness.

<code-block lang="mermaid">
flowchart LR
    Startup["startup recorder"] --> Collectors["Prometheus collectors"]
    Pool["pool observer"] --> Collectors
    Classify["classification lifecycle"] --> Collectors
    Errors["controlled error producers"] --> Collectors
    Scraper["Prometheus scraper"] --> Endpoint["GET /metrics"]
    Endpoint --> Registry["default or multiprocess registry"]
    Registry --> Collectors
    Endpoint --> Exposition["CONTENT_TYPE_LATEST bytes"]
</code-block>

The scrape route is a fixed, schema-excluded `GET /metrics` router operation. It returns 404 only when application
services and validated settings both exist and metrics are disabled. Otherwise it returns the current registry's
Prometheus exposition with `CONTENT_TYPE_LATEST`. This route-level behavior is linked from the
[Application Construction metrics router](Python-Inference-API-—-Application-Construction.md#metrics-router),
without repeating that router's mount details here.

<code-block lang="python" src="../../src/main/python/api/app/api/sdn_mpls_ml_metrics_route.py" include-lines="35-56"/>

`_registry_for_scrape` reads `PROMETHEUS_MULTIPROC_DIR`. When that environment variable is empty, it returns the
default `REGISTRY`. When it is present, it creates a `CollectorRegistry` that supports collectors without names and
attaches `multiprocess.MultiProcessCollector` using that directory. The function selects the registry for exposition.
It does not enable or disable individual application metric writes.

<code-block lang="python" src="../../src/main/python/api/app/api/sdn_mpls_ml_metrics_route.py" include-lines="19-32"/>

The fixed endpoint therefore has two separate conditions. The validated application setting controls whether the
route returns its intentionally unavailable 404 response. The process environment controls whether a scrape uses the
default registry or a multiprocess collector registry. Collector declarations that use live multiprocess modes remain
part of their own metric-family definitions.

## Outcome ownership and label discipline {id="outcome-ownership"}

The classification route determines the classification observation outcome at each branch where it has sufficient
information. Before readiness is established, it marks `not_ready`. Unsupported EtherType input in model mode is
marked `rejected`. A pool lease timeout becomes `capacity_timeout`. Controlled prediction failures and invalid output
become `inference_failed` and `output_invalid` respectively. Policy resolution failures become `policy_failed`. The
successful return path marks `success` before its final observation is completed.

<code-block lang="python" src="../../src/main/python/api/app/api/sdn_mpls_ml_inference_route.py" include-lines="128-227"/>

This outcome vocabulary belongs to classification-request accounting. It should not be conflated with the inference
histogram vocabulary. The instrumentation helper only knows whether the threaded prediction returned successfully,
raised invalid output, was cancelled, or failed before return. It cannot know whether a later policy mapping succeeds.
Conversely, the route can mark a classification policy failure without retroactively changing the inference duration
sample already recorded by the helper.

<code-block lang="mermaid">
flowchart TD
    Begin["classification observation"] --> Ready{"ready dependencies?"}
    Ready -- no --> NotReady["outcome: not_ready"]
    Ready -- yes --> Mode{"mode accepts packet?"}
    Mode -- no --> Rejected["outcome: rejected"]
    Mode -- yes --> Lease{"pool lease"}
    Lease -- timeout --> Capacity["outcome: capacity_timeout"]
    Lease -- leased --> Predict{"instrumented prediction"}
    Predict -- model failure --> InferenceFailed["outcome: inference_failed"]
    Predict -- invalid output --> OutputInvalid["outcome: output_invalid"]
    Predict -- result --> Policy{"policy resolution"}
    Policy -- failure --> PolicyFailed["outcome: policy_failed"]
    Policy -- resolved or absent --> Success["outcome: success"]
</code-block>

After a prediction is available, the route increments the result counter and records confidence using the prediction's
class name. If a policy mapper exists, it records the selected profile and string form of the fallback boolean. A true
fallback additionally records the fallback reason, defaulting to `unknown` only when the resolver does not provide a
reason. These writes are conditional on the same route-local enablement flag.

<code-block lang="python" src="../../src/main/python/api/app/api/sdn_mpls_ml_inference_route.py" include-lines="229-261"/>

The label values are intentionally tied to values already produced by the route or startup configuration. The
classification mode is read from validated settings when available and falls back to the cached readiness mode before
that point. Class name comes from a valid prediction result. Profile name, fallback state, and fallback reason come
from policy resolution. The producers do not derive labels from arbitrary packet-feature keys or unbounded client text.

The baseline recorder itself is also created conditionally. During service construction, `_create_baseline_metrics`
recognizes the false-like raw values `false`, `0`, `no`, and `off` after lowercasing and trimming. It returns `None`
for those values. Otherwise it constructs a recorder with the normalized response form of the raw classification mode,
which permits startup failures to be recorded before validated settings are available.

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_dependencies.py" include-lines="463-480"/>

This early recorder creation is a construction concern, not an assertion that the process has reached ready state.
Readiness becomes a metric value only when startup initialization publishes the relevant state. The actual readiness
HTTP response, its status code, and cached failure details remain the responsibility of the health route described in
[Application Construction](Python-Inference-API-—-Application-Construction.md#health-router).

## Error metrics versus structured logs {id="error-metrics-and-logs"}

Metrics and structured logs share useful identifiers, but they serve different representations. An error metric groups
events by bounded error code and component. The application log helper attaches request ID, failed stage, failed
check, retryability, HTTP status, and optional bounded extra fields to a structured event. The metric is aggregate
evidence. The log event keeps a per-request correlation context.

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_main.py" include-lines="268-329"/>

<code-block lang="mermaid">
sequenceDiagram
    participant Producer as error producer
    participant Counter as request errors counter
    participant Logger as structured logger
    participant Response as error response
    Producer->>Counter: code and component labels
    Producer->>Logger: request ID and failure metadata
    Producer->>Response: normalized HTTP body
    Note over Counter,Response: One condition has aggregate, correlated, and caller-facing evidence
</code-block>

The Starlette HTTP exception handler records a specific request-too-large error for 413, an HTTP-not-found error for
404, and an `HTTP_ERROR` with `unknown` component for other framework HTTP errors. The unhandled-exception handler
records `INTERNAL_ERROR` with application component. These values are fixed by the central application module, rather
than discovered from exception-message text.

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_main.py" include-lines="656-739"/>

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_main.py" include-lines="742-790"/>

This distinction matters at the middleware boundary. The size middleware produces its own short-circuit response and
writes the two counters before it returns. A later generic 413 handler represents a separate framework error path.
The documentation therefore does not claim that every 413 has a body-rejection reason label. Only the size-middleware
producer supplies the bounded request-body rejection reasons documented above.

## Prometheus family semantics {id="prometheus-family-semantics"}

Prometheus client metric types communicate how values should be read. Counters represent monotonic event totals within
the collector process model. Gauges represent the latest value written by the application, including zero or one for
readiness. Histograms retain observations in their configured buckets and expose count and sum series according to the
Prometheus client. The package chooses these types at declaration time and producers use the matching increment, set,
or observe operation.

The duration histograms use explicit source-defined buckets. Startup validation uses buckets from 0.01 through 30.0
seconds. Pool waits use fine-grained buckets from 0.0001 through 10.0 seconds. Classification duration uses 0.0005
through 10.0 seconds, and inference duration uses 0.0001 through 1.0 seconds. Confidence uses buckets from 0.0 to
1.0. These bucket boundaries are declarations, not service-level latency guarantees.

The `liveall`, `livemin`, and `livesum` multiprocess modes appear on selected gauges. Worker identity and start time
use `liveall`. Readiness uses `livemin`. Pool and classification-in-progress state uses `livesum`. These are
collector-definition details that affect multiprocess exposition. They do not alter classifier-pool operations or
turn the fixed route into a worker coordinator.

No metric in this package adds the server-generated `request_id` as a label. Doing so would create a distinct label
series for every request. The request ID is instead carried by response and structured-log boundaries described in
[Middleware](Python-Inference-API-—-Application-Construction-Middleware.md#authoritative-request-id) and
[Application Construction](Python-Inference-API-—-Application-Construction.md#request-id).


