# Application Construction — Environment Variables

The Python inference API reads its application configuration from the process environment and, when present, an `.env` file. `RawSettings` owns the application-managed variables. A small number of runtime variables, including `HOSTNAME` and `PROMETHEUS_MULTIPROC_DIR`, are intentionally read directly by the components that need them.

<show-structure for="chapter" depth="2"/>

The startup sequence matters when interpreting this reference. FastAPI enters `lifespan()`, reads `RawSettings`, initializes process identity, configures the root logger, and then validates the settings while building the shared service container. A setting may therefore be active even when its normalized copy in `ValidatedSettings` is not read after validation.

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_main.py" include-lines="106-120"/>

## Environment-variable reference {id="python-api-environment-reference"}

The table is a compact deployment reference. The sections that follow trace each variable to its source-level consumers and describe the applicable validation or fallback behavior.

| Variable | Default | Primary consumer | Purpose |
|---|---:|---|---|
| `APP_NAME` | `sma-ml-api-kilo` | lifespan, service state, HTTP and log metadata | Identifies the API service. |
| `APP_VERSION` | `0.1.0` | root endpoint | Reports the application version. |
| `HOST` | `0.0.0.0` | Docker Uvicorn command | Selects the bind interface. |
| `PORT` | `8000` | Docker, health check, startup validation | Selects and validates the HTTP port. |
| `LOG_LEVEL` | `INFO` | root logger | Selects the process-wide logging threshold. |
| `LOG_DIRECTORY` | `/logs` | rotating file handler | Selects the directory for JSON log files. |
| `LOG_FILENAME` | `sdn_mpls_ml_api.log` | rotating file handler | Selects the active log filename. |
| `LOG_FILE_MAX_BYTES` | `10485760` | rotating file handler | Sets the rotation threshold in bytes. |
| `LOG_FILE_BACKUP_COUNT` | `5` | rotating file handler | Sets the retained rotated-file count. |
| `ENABLE_PROMETHEUS_METRICS` | `true` | service initialization and `/metrics` | Enables application metric publication. |
| `INSTANCE_ID` | unset | process identity | Names the deployment instance in logs and metrics. |
| `PROMETHEUS_MULTIPROC_DIR` | unset | metrics router | Enables a Prometheus multiprocess registry. |
| `HOSTNAME` | runtime supplied | process identity | Fallback instance identifier. |
| `CLASSIFICATION_MODE` | `MODEL` | startup and inference route | Selects model or deterministic classification. |
| `MODEL_DIR` | `/models` | artifact validation and predictor | Locates model artifacts. |
| `CONFIG_DIR` | `/configs` | policy and rules loading | Locates configuration artifacts. |
| `MODEL_FILENAME` | `sdn_mpls_ml_model.json` | model artifact validation | Names the XGBoost model artifact. |
| `MODEL_METADATA_FILENAME` | `sdn_mpls_ml_model_meta.json` | metadata validation | Names the model metadata artifact. |
| `POLICY_FILENAME` | `sdn_mpls_ml_traffic_class_to_policy_mapping.json` | policy validation | Names the traffic-policy mapping. |
| `DETERMINISTIC_RULE_FILENAME` | `sdn_mpls_ml_traffic_class_deterministic_rules.json` | deterministic startup | Names deterministic classifier rules. |
| `ENABLE_POLICY_MAPPING` | `true` | `PolicyMapper` construction | Enables the controller-facing policy enrichment contract. |
| `MIN_POLICY_CONFIDENCE` | `0.75` | `PolicyMapper` construction | Selects the default policy below the threshold. |
| `MIN_TUNNEL_BANDWIDTH_KBPS` | `10000` | policy validation | Sets the lower supported bandwidth bound. |
| `MAX_TUNNEL_BANDWIDTH_KBPS` | `100000` | policy validation | Sets the upper supported bandwidth bound. |
| `CLASSIFIER_POOL_SIZE` | `5` | classifier pool and limiter | Sets inference capacity. |
| `REQUEST_TIMEOUT_SECONDS` | `10` | pool acquisition | Bounds wait time for classifier capacity. |
| `MAX_REQUEST_BODY_BYTES` | `16384` | request-size middleware | Limits incoming request bodies. |
| `PROBABILITY_TOLERANCE` | `0.001` | model predictor | Bounds acceptable probability-sum error. |

## Configuration source and normalization {id="python-api-environment-source"}

`RawSettings` declares the application-managed variables with Pydantic aliases. `get_raw_settings()` caches one raw object per process. The startup builder validates and normalizes these values into `ValidatedSettings`, then stores that validated object in `AppServices` after the configuration preflight succeeds.

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_config.py" include-lines="73-140"/>

The Docker entrypoint reads `HOST` and `PORT` independently of Pydantic. Consequently, removing either from `RawSettings` would not remove it from deployment configuration. It would only remove the Python-side representation and, for `PORT`, the structured readiness validation.

<code-block lang="docker" src="../../src/main/python/api/Dockerfile" include-lines="30-33"/>

## Service identity and network binding {id="python-api-environment-service-network"}

### `APP_NAME` {id="python-api-env-app-name"}

`APP_NAME` is required after trimming. `lifespan()` uses it to name the process identity, and the service builder carries the validated value into startup logs, middleware metadata, and the root endpoint response. When the raw value is empty during identity initialization, the startup code falls back to `DEFAULT_APP_NAME`, but validation subsequently marks the service as not ready.

The effective service name is therefore both an HTTP-facing identifier and a structured-observability field. It is not merely a documentation label.

### `APP_VERSION` {id="python-api-env-app-version"}

`APP_VERSION` is also required after trimming. The root route returns the validated version when configuration succeeded and uses the raw value while reporting an initialization failure. FastAPI itself receives `DEFAULT_APP_VERSION` as its static application metadata version when the application object is created.

### `HOST` {id="python-api-env-host"}

`HOST` defaults to `0.0.0.0`. The shell command in the Docker image expands it into Uvicorn's `--host` argument. The Python validation path only strips it when constructing `ValidatedSettings`; no later application component reads that normalized field.

Changing `HOST` changes listener binding at process launch. It does not change a host value returned by an API endpoint because the HTTP response models do not expose it.

### `PORT` {id="python-api-env-port"}

`PORT` defaults to `8000`. Docker expands it for Uvicorn and reuses it in the image health check. The Python startup preflight separately parses it as an integer and rejects values outside `1` through `65535` with a structured readiness failure.

This dual use is deliberate operational defense. The container launcher determines the actual listener, while the application can report an invalid configured port through its readiness state.

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_dependencies.py" include-lines="565-600"/>

## Logging variables {id="python-api-environment-logging"}

The logging settings are consumed before `ValidatedSettings` is constructed. `lifespan()` passes the raw object to `configure_logging()`, which installs console and rotating-file handlers on the root logger. Named application loggers propagate to that root logger under normal Python logging behavior.

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_logging_config.py" include-lines="124-173"/>

### `LOG_LEVEL` {id="python-api-env-log-level"}

`LOG_LEVEL` accepts `CRITICAL`, `ERROR`, `WARNING`, `INFO`, or `DEBUG`. Startup first passes the value through `get_safe_log_level()` so that logger initialization has a safe fallback. The later configuration preflight enforces the supported-level set and marks readiness as failed for an unsupported value.

The raw value configures the root logger before the full service build. Its normalized `ValidatedSettings.log_level` copy currently has no later reader, but the environment variable itself is an active logging control and must remain.

### `LOG_DIRECTORY` {id="python-api-env-log-directory"}

`LOG_DIRECTORY` defaults to `/logs`. `configure_logging()` creates the directory when needed and uses it as the parent of the rotating log file. A deployment must ensure that this location is writable by the API process.

The supplied `.env.example` uses `./logs` for local execution. Container deployments can retain `/logs` when the corresponding writable mount is available.

### `LOG_FILENAME` {id="python-api-env-log-filename"}

`LOG_FILENAME` defaults to `sdn_mpls_ml_api.log`. It is the basename supplied to `RotatingFileHandler` after `LOG_DIRECTORY` is resolved. The application does not derive a filename from `APP_NAME` or `INSTANCE_ID`.

### `LOG_FILE_MAX_BYTES` {id="python-api-env-log-file-max-bytes"}

`LOG_FILE_MAX_BYTES` defaults to `10485760`, which is 10 MiB. Pydantic requires a positive integer, and the value becomes the handler's `maxBytes` argument. Reaching the threshold rotates the active file.

### `LOG_FILE_BACKUP_COUNT` {id="python-api-env-log-file-backup-count"}

`LOG_FILE_BACKUP_COUNT` defaults to `5`. Pydantic accepts zero or a positive integer. The value becomes `RotatingFileHandler.backupCount`, which controls how many rotated files the handler retains.

## Observability variables {id="python-api-environment-observability"}

### `ENABLE_PROMETHEUS_METRICS` {id="python-api-env-enable-prometheus-metrics"}

`ENABLE_PROMETHEUS_METRICS` is parsed as a strict boolean during the configuration preflight. When false, the service removes its baseline metrics publisher, and the literal `/metrics` route returns `404 Not Found`. The route does not read a configurable path.

<code-block lang="python" src="../../src/main/python/api/app/api/sdn_mpls_ml_metrics_route.py" include-lines="35-56"/>

### `INSTANCE_ID` {id="python-api-env-instance-id"}

`INSTANCE_ID` is read in `lifespan()` before configuration preflight. It initializes the singleton `ProcessIdentity`, which is retained in `app.state`, attached to every JSON log record by `ProcessIdentityFilter`, and exported as a label on worker metrics.

It is not a field in the JSON bodies of the root, readiness, or classification endpoints. Its public observability surface is structured logs and Prometheus samples.

<code-block lang="python" src="../../src/main/python/api/app/observability/sdn_mpls_ml_identity.py" include-lines="49-84"/>

### `PROMETHEUS_MULTIPROC_DIR` {id="python-api-env-prometheus-multiproc-dir"}

`PROMETHEUS_MULTIPROC_DIR` is intentionally outside `RawSettings`. The metrics route reads it directly for each scrape. When it is unset or blank, `/metrics` uses the default Prometheus registry. When present, the route creates a registry and attaches `MultiProcessCollector` to the configured directory.

The setting is meaningful only for a deployment that shares Prometheus multiprocess metric files across workers. The current Docker command starts one Uvicorn worker.

<code-block lang="python" src="../../src/main/python/api/app/api/sdn_mpls_ml_metrics_route.py" include-lines="19-32"/>

### `HOSTNAME` {id="python-api-env-hostname"}

`HOSTNAME` is another direct runtime variable. It is only consulted when `INSTANCE_ID` is absent or blank. The identity initializer then uses the container hostname, then the local socket hostname, and finally `unknown-instance` as a last fallback.

This fallback keeps worker metrics and JSON log records identifiable without requiring an explicit deployment-provided instance identifier.

## Classification mode and artifact locations {id="python-api-environment-classification-artifacts"}

### `CLASSIFICATION_MODE` {id="python-api-env-classification-mode"}

`CLASSIFICATION_MODE` defaults to `MODEL` and accepts `MODEL` or `DETERMINISTIC_TEST`. Validation converts the configured string to `ClassificationMode`. The service builder chooses model artifacts and model-backed inference for `MODEL`, or deterministic rules for `DETERMINISTIC_TEST`. The inference route publishes the selected mode in classification responses.

### `MODEL_DIR` {id="python-api-env-model-dir"}

`MODEL_DIR` defaults to `/models`. It is required in `MODEL` mode and becomes the base directory for both the XGBoost model and its metadata file. Artifact validation combines it with `MODEL_FILENAME` and `MODEL_METADATA_FILENAME`.

### `CONFIG_DIR` {id="python-api-env-config-dir"}

`CONFIG_DIR` defaults to `/configs` and is always required. It is the base directory for the traffic-policy mapping and, in deterministic mode, the deterministic-rules artifact.

### `MODEL_FILENAME` {id="python-api-env-model-filename"}

`MODEL_FILENAME` defaults to `sdn_mpls_ml_model.json`. It is required only in `MODEL` mode and is joined with `MODEL_DIR` during artifact validation and predictor creation.

### `MODEL_METADATA_FILENAME` {id="python-api-env-model-metadata-filename"}

`MODEL_METADATA_FILENAME` defaults to `sdn_mpls_ml_model_meta.json`. It is required only in `MODEL` mode and is joined with `MODEL_DIR` to load and validate model metadata before inference becomes ready.

### `POLICY_FILENAME` {id="python-api-env-policy-filename"}

`POLICY_FILENAME` defaults to `sdn_mpls_ml_traffic_class_to_policy_mapping.json`. It is required in both classification modes because startup validates the complete policy map even when policy mapping is disabled for inference-time selection. The deterministic classifier only selects a traffic class from protocol and port rules. The shared `PolicyMapper` then loads this file and supplies the corresponding DSCP, MPLS TC, requested bandwidth, setup priority, and hold priority.

The bundled policy file defines the default profile as best effort with DSCP `0`, MPLS TC `0`, bandwidth `0`, and setup and hold priorities `7`. Class-specific values are also defined here. For example, `HTTP` uses MPLS TC `0`, bandwidth `25000`, setup priority `6`, and hold priority `5`, while `STREAMING` uses MPLS TC `4`, bandwidth `75000`, setup priority `1`, and hold priority `0`.

<code-block lang="json" src="../../src/main/python/api/configs/sdn_mpls_ml_traffic_class_to_policy_mapping.json"/>

### `DETERMINISTIC_RULE_FILENAME` {id="python-api-env-deterministic-rule-filename"}

`DETERMINISTIC_RULE_FILENAME` defaults to `sdn_mpls_ml_traffic_class_deterministic_rules.json`. It is required in `DETERMINISTIC_TEST` mode and is joined with `CONFIG_DIR` while startup loads deterministic classifier rules.

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_dependencies.py" include-lines="912-965"/>

## Policy variables {id="python-api-environment-policy"}

### `ENABLE_POLICY_MAPPING` {id="python-api-env-enable-policy-mapping"}

`ENABLE_POLICY_MAPPING` is parsed as a strict boolean and defaults to `true`. When true, startup constructs `PolicyMapper` from the validated policy file. Both model-backed and deterministic predictions then receive a complete policy object from the same shared mapping file.

<warning>
Keep <code>ENABLE_POLICY_MAPPING=true</code> in production. Disabling it causes both modes to return <code>policy: null</code>, which removes DSCP, MPLS TC, bandwidth, and priority data required by downstream controller components. Set it to <code>false</code> only in isolated tests that intentionally exercise classification without policy enrichment.
</warning>

### `MIN_POLICY_CONFIDENCE` {id="python-api-env-min-policy-confidence"}

`MIN_POLICY_CONFIDENCE` defaults to `0.75` and must be a floating-point number from `0.0` through `1.0`, inclusive. The value is passed to `PolicyMapper`. A prediction with confidence below the threshold receives the policy file's `default_profile`, with `policy_fallback: true` and `policy_fallback_reason: "confidence_below_threshold"` in the response.

The deterministic classifier emits a confidence of `1.0`, so it uses the policy for its selected class under every valid threshold. Its separate class-selection fallback is `STREAMING`, which is enriched with the `STREAMING` policy rather than the policy file's default profile.

### `MIN_TUNNEL_BANDWIDTH_KBPS` {id="python-api-env-min-tunnel-bandwidth"}

`MIN_TUNNEL_BANDWIDTH_KBPS` defaults to `10000`. Startup parses it as an integer and requires a value from `1` through `100000`. It is passed to complete-policy validation as the lower bound for non-default policy bandwidth requests.

### `MAX_TUNNEL_BANDWIDTH_KBPS` {id="python-api-env-max-tunnel-bandwidth"}

`MAX_TUNNEL_BANDWIDTH_KBPS` defaults to `100000`. It must also be from `1` through `100000`, and it must not be lower than `MIN_TUNNEL_BANDWIDTH_KBPS`. Complete-policy validation receives it as the upper bound.

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_dependencies.py" include-lines="660-725"/>

## Inference-capacity and request variables {id="python-api-environment-inference"}

### `CLASSIFIER_POOL_SIZE` {id="python-api-env-classifier-pool-size"}

`CLASSIFIER_POOL_SIZE` defaults to `5` and must be an integer from `1` through `32`. Startup uses it to create one classifier instance per pool slot and an AnyIO `CapacityLimiter` with the same capacity. It therefore bounds both the pool size and the number of concurrent inference operations.

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_dependencies.py" include-lines="1265-1305"/>

### `REQUEST_TIMEOUT_SECONDS` {id="python-api-env-request-timeout-seconds"}

`REQUEST_TIMEOUT_SECONDS` defaults to `10` and must be positive. The inference route and classifier-pool helper use the normalized value as the bounded wait time while acquiring a classifier. Exhausting capacity past this duration produces the typed inference-capacity failure path.

### `MAX_REQUEST_BODY_BYTES` {id="python-api-env-max-request-body-bytes"}

`MAX_REQUEST_BODY_BYTES` defaults to `16384` and must be positive. Service initialization stores the value as `AppServices.request_size_limit_bytes`. The request-size middleware reads that shared state before schema parsing and rejects oversized input.

### `PROBABILITY_TOLERANCE` {id="python-api-env-probability-tolerance"}

`PROBABILITY_TOLERANCE` defaults to `0.001` and must be positive. Startup passes it to the model predictor. The predictor uses it when validating that model-output probabilities sum to one within the allowed numerical tolerance.

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_dependencies.py" include-lines="630-690"/>

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_dependencies.py" include-lines="1514-1518"/>

## Fixed metrics route and retired setting {id="python-api-environment-fixed-metrics-route"}

The metrics endpoint is always `/metrics`. `METRICS_PATH` is not an accepted application setting and does not appear in `RawSettings`, `ValidatedSettings`, or startup validation. The regression test preserves that contract even if an external process defines an unused `METRICS_PATH` environment variable.

<code-block lang="python" src="../../src/main/python/api/tests/test_validation.py" include-lines="103-110"/>

## Deployment example {id="python-api-environment-example"}

The repository's `.env.example` provides non-secret development defaults. It is a template, not evidence that the configured paths are mounted or that the referenced model artifacts exist. Production deployments should set the artifact, configuration, logging, and Prometheus multiprocess paths for their own filesystem layout.

<code-block lang="properties" src="../../src/main/python/api/.env.example"/>
