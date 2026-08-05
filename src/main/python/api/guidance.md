# SDNFlow XGBoost Inference API — Implementation Specification

## 1. Purpose

Implement a containerized Python inference service that exposes a versioned FastAPI API, validates first-packet header features, loads a runtime-mounted DMLC XGBoost multiclass model, constructs metadata-ordered input vectors, decodes predictions, and returns traffic category, confidence, complete class probabilities, and an optional editable traffic policy.

The service is a **classification and decision-layer component only**. It does not configure OpenDaylight, Cisco XRv, OVS, MPLS tunnels, routes, or traffic steering. It has no controller configuration, controller endpoints, controller adapters, or controller-related environment variables.

## 2. Technology Requirements

Use Python 3.12 or another explicitly pinned supported Python release, FastAPI, Uvicorn, Pydantic v2, the official DMLC `xgboost` package, NumPy, Pytest, HTTPX or FastAPI `TestClient`, and Docker. Load the model once at FastAPI lifespan startup and retain it in application state.

## 3. Required Scope

Implement FastAPI request and response schemas, model metadata loading, native XGBoost model loading, input-vector construction, probability extraction, class-ID decoding, model/metadata consistency validation, health and readiness endpoints, structured logging, exception handling, Docker artifacts, centralized environment configuration, unit/API tests, and a Containerlab-compatible read-only model mount example.

Do not implement controller authentication or calls, PCEP/NETCONF/RESTCONF operations, tunnel creation, route injection, OVS flows, packet rewriting, databases, message queues, model training, model downloads, model hot reload, or an endpoint that provisions network resources.

## 4. Model Contract

Expected model identity:

```json
{"model_name":"sdnflow_xgboost_first_packet","target_name":"Category"}
```

Required feature order, sourced from metadata rather than request key order:

```json
["eth_type", "ip_proto", "src_port", "dst_port"]
```

Required class mapping:

```json
{
  "class_to_id":{"DNS":0,"FTP":1,"HTTP":2,"ICMP":3,"NTP":4,"SSH":5,"STREAMING":6},
  "id_to_class":{"0":"DNS","1":"FTP","2":"HTTP","3":"ICMP","4":"NTP","5":"SSH","6":"STREAMING"}
}
```

The model must provide seven finite multiclass probabilities, preferably through `multi:softprob`. The predicted class is `argmax(probabilities)` and confidence equals the selected probability. Reject incompatible outputs, including single class-ID outputs, NaN, infinity, wrong class counts, and probability sums beyond the configured tolerance.

## 5. Runtime Files and Startup Validation

The Docker image must not contain trained artifacts. Mount these read-only files at runtime, by default under `/models`:

```text
/models/model.json
/models/model_meta.json
```

`model_meta.json` must include `schema_version`, `model_name`, `target_name`, `model_format`, `feature_order`, `feature_types`, `class_to_id`, and `id_to_class`; see `models-example/model_meta.json` for the fixed contract.

On startup, verify the files exist, parse UTF-8 JSON metadata, validate the exact four supported features, validate bidirectional unique contiguous class IDs 0–6, load the native XGBoost `Booster`, and run a synthetic inference self-test. Readiness is true only after every check succeeds.

## 6. Project Structure

```text
app/
  api/            health and inference routes
  model/          metadata, loader, predictor
  policy/         editable policy models and mapper
  schemas/        HTTP schemas
  config.py
  dependencies.py
tests/
models-example/
requirements*.txt
Dockerfile
.dockerignore
.env.example
README.md
pyproject.toml
```

There is no `app/orchestration` package.

## 7. Configuration

Read environment variables through one centralized settings object. Supported settings are:

| Variable | Default | Purpose |
| --- | --- | --- |
| `APP_NAME` | `sdnflow-inference-api` | Service name |
| `APP_VERSION` | `1.0.0` | API version |
| `HOST` | `0.0.0.0` | Bind address |
| `PORT` | `8000` | HTTP port |
| `LOG_LEVEL` | `INFO` | Logging level |
| `MODEL_DIR` | `/models` | Mounted model directory |
| `CONFIG_DIR` | `/configs` | Mounted configuration directory |
| `MODEL_FILENAME` | `model.json` | Model artifact name |
| `MODEL_METADATA_FILENAME` | `model_meta.json` | Metadata artifact name |
| `POLICY_FILENAME` | `default_policy.json` | Traffic policy mapping file name |
| `DETERMINISTIC_RULE_FILENAME` | `deterministic_rules.json` | Deterministic simulator rule file name |
| `ENABLE_POLICY_MAPPING` | `true` | Include policy result |
| `REQUEST_TIMEOUT_SECONDS` | `10` | Reserved application timeout setting |
| `MAX_REQUEST_BODY_BYTES` | `16384` | Request size limit |
| `PROBABILITY_TOLERANCE` | `0.001` | Probability sum tolerance |
| `MIN_POLICY_CONFIDENCE` | unset | Optional policy fallback threshold |

`ENABLE_ORCHESTRATION`, `ORCHESTRATION_BACKEND`, and all `ODL_*` variables are unsupported and must not be defined.

## 8. API Endpoints

- `GET /` returns service, version, running status, and `/docs`.
- `GET /health/live` returns `200 {"status":"alive"}` without requiring the model.
- `GET /health/ready` returns model readiness details, or `503 MODEL_NOT_READY`.
- `GET /api/v1/model` returns safe metadata: identity, schema version, feature order, and classes.
- `POST /api/v1/classify` runs classification only.

`POST /api/v1/classify-and-provision` is not registered. The API must never claim to have provisioned a network resource.

A successful classification response has this shape:

```json
{
  "request_id":"uuid",
  "model_name":"sdnflow_xgboost_first_packet",
  "prediction":{"class_id":6,"class_name":"STREAMING","confidence":0.931742},
  "probabilities":{"DNS":0.002104,"FTP":0.001281,"HTTP":0.049332,"ICMP":0.000137,"NTP":0.000221,"SSH":0.015183,"STREAMING":0.931742},
  "policy":{"profile_name":"streaming_default","dscp":34,"mpls_tc":4,"path_constraints":{"requested_bandwidth_kbps":10000,"setup_priority":3,"hold_priority":3},"policy_fallback":false,"policy_fallback_reason":null},
  "processing_time_ms":1.82
}
```

There is no `orchestration` response object.

## 9. Input Validation and Prediction

`packet_features` accepts only strict integers: `eth_type` 0–65535, `ip_proto` 0–255, and `src_port`/`dst_port` 0–65535. Reject booleans, floats, numeric strings, and unknown fields. For protocols other than TCP (6) and UDP (17), both ports must be zero.

Construct vectors through metadata lookup:

```python
feature_vector = [packet_features[feature_name] for feature_name in metadata.feature_order]
```

Use a `(1, 4)` `float32` NumPy matrix. The predictor must validate output count, finiteness, and probability sum, decode class IDs through metadata, and return all class probabilities.

## 10. Policy Mapping

Policy mapping is a layer after classification, never part of the predictor. Read policies from editable JSON. Validate DSCP 0–63, MPLS TC 0–7, nonnegative bandwidth, priorities 0–7, and exact model-class policy names. If `MIN_POLICY_CONFIDENCE` applies, preserve the predicted class and return the default policy with `policy_fallback` and `policy_fallback_reason`.

## 11. Errors, Logging, and Security

Use a consistent error envelope. Required codes are `INVALID_JSON` (400), `REQUEST_TOO_LARGE` (413), `REQUEST_VALIDATION_FAILED` (422), `MODEL_INFERENCE_FAILED` (500), `MODEL_OUTPUT_INVALID` (500), `POLICY_MAPPING_FAILED` (500), and `MODEL_NOT_READY` (503). Do not expose stack traces, paths, credentials, model contents, or arbitrary request payloads.

Write structured logs to standard output with service, event, request ID, model name, predicted class, confidence, and duration. Log only validated numeric features at DEBUG if required.

Run the container as a non-root user, reject oversized/unknown request data, use read-only model mounts, avoid shell execution and dynamic imports, and load native XGBoost JSON or UBJSON rather than pickle.

## 12. Docker, Containerlab, and Tests

Use a pinned Python slim base image, pinned dependencies, a non-root user, `/models` without model artifacts copied into the image, `/configs` for mounted policy and deterministic rule files, one Uvicorn worker, and a liveness health check. Containerlab must mount `./model-artifacts:/models:ro`, mount `./configs:/configs:ro`, and configure only supported model/policy/application variables.

Tests must cover metadata validation, strict packet validation, exact feature ordering, argmax/confidence/probabilities, invalid predictor output, health/readiness, model metadata, valid and invalid classification, generated request ID, timing, OpenAPI generation, and confirmation that `/api/v1/classify-and-provision` is absent.

## 13. Acceptance Criteria

The image builds without a trained model; mounted artifacts load successfully; invalid or missing artifacts leave the service unready; strict validation and metadata feature ordering work; valid requests return one of seven classes, class ID, confidence, all probabilities, and optional policy; policy mapping is editable and isolated from inference; the service has no orchestration package, ODL settings, or provisioning endpoint; tests pass; the container is non-root; the model mount supports read-only Containerlab use; and FastAPI OpenAPI docs are available.

## 14. Required Architectural Separation

```text
HTTP validation
      ↓
Feature-vector construction
      ↓
XGBoost predictor
      ↓
Class decoding
      ↓
Policy mapping
```

The predictor must not make network calls, access controller configuration, select tunnels, inject routes, or install flows.
