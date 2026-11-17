# Application Construction — Reading Structured Logs

<show-structure for="chapter" depth="2"/>

The Python Inference API writes one JSON event per log line to both its process stream and its rotating log file. The captured API records in `docs/experimentation` show the same envelope that `JsonFormatter` constructs at runtime. Each line can therefore be parsed independently, then grouped by `request_id` when it belongs to an HTTP exchange or by the worker identity when it describes startup and process-level activity. This page describes the stable envelope only. The request, model, policy, and middleware pages remain responsible for the meaning of their individual event names.

## JSON event envelope {id="python-log-json-event-envelope"}

`JsonFormatter` always writes `timestamp`, `level`, `logger`, and `message`. Event producers add semantic identity and detail through `logging`'s `extra` values, while `ProcessIdentityFilter` attaches the identity of the worker that emitted the record. The formatter only serializes an optional field when the `LogRecord` has that attribute, so absence means that the emitter did not attach that category of detail. A present field may still be JSON `null` when an emitter deliberately records an unavailable value.

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_logging_config.py" include-lines="23-121"/>

<code-block lang="json">
{
  "timestamp": "UTC ISO-8601 instant",
  "level": "DEBUG | INFO | WARNING | ERROR | CRITICAL",
  "logger": "emitting Python logger name",
  "message": "human-readable event message",
  "service": "application service name",
  "event": "stable event identifier",
  "metadata": {"event-specific detail": "..."},
  "instance_id": "deployment instance",
  "worker_id": "worker UUID",
  "request_id": "HTTP exchange identifier"
}
</code-block>

| Top-level field | Presence | How to interpret it |
|---|---|---|
| `timestamp` | Always | UTC time assigned by the formatter, with millisecond precision and a `Z` suffix. |
| `level` | Always | Python logging severity selected for the emitted record. |
| `logger` | Always | Dotted module logger name, which identifies the emitting runtime layer. |
| `message` | Always | Human-readable description supplied by the event producer. Use `event` for stable programmatic grouping when it is present. |
| `service` and `event` | Optional | Application service identity and a machine-oriented event identifier. The experimentation logs show these on application and middleware events. |
| `metadata` and `context` | Optional objects | Event-specific nested detail. `metadata` commonly contains bounded diagnostics, while `context` is preserved only when the producer supplies it. Their internal keys vary with the event. |
| `instance_id`, `worker_id`, `worker_pid`, and `process_name` | Added after identity initialization | Stable worker identity injected by `ProcessIdentityFilter`, which distinguishes a process from the request it may be serving. |
| `request_id` | Request-scoped events | Correlation value created or accepted by the correlation middleware and reused by handlers, middleware, and responses for one HTTP exchange. |
| Failure and execution fields | Event-dependent | Fields such as `component`, `error_code`, `failed_stage`, `failed_check`, `retryable`, `http_status_code`, and timing or pool values describe the particular event without changing the common envelope. |

The identity fields and `request_id` answer different questions. `instance_id` and `worker_id` identify the process that wrote a record. `request_id` joins records emitted while one HTTP request crosses correlation middleware, request-size validation, routing, error conversion, and response handling. A startup record can carry worker identity without a request ID, while an HTTP event can use both sets of fields. The request-correlation lifecycle is documented in [Middleware](Python-Inference-API-—-Application-Construction-Middleware.md), and the process identity lifecycle is owned by [Observability](Python-Inference-API-—-Application-Construction-Observability.md#process-identity-lifecycle).

## Level threshold and output locations {id="python-log-level-threshold-and-output-locations"}

`lifespan()` initializes process identity before configuring logging, then passes the normalized `LOG_LEVEL` value and raw logging settings to `configure_logging()`. The API accepts `CRITICAL`, `ERROR`, `WARNING`, `INFO`, and `DEBUG`. An unsupported raw value initially falls back to `INFO` for safe logger initialization, although the later settings preflight still reports the unsupported configuration as a startup validation failure. The threshold is installed on the root logger, so a setting of `INFO` retains `INFO`, `WARNING`, `ERROR`, and `CRITICAL` records while suppressing `DEBUG` records from loggers that propagate normally.

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_main.py" include-lines="106-120"/>

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_config.py" include-lines="23-31"/>

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_config.py" include-lines="189-208"/>

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_logging_config.py" include-lines="150-173"/>

The console handler and `RotatingFileHandler` share the same JSON formatter and identity filter. `LOG_DIRECTORY` and `LOG_FILENAME` select the active file, while `LOG_FILE_MAX_BYTES` and `LOG_FILE_BACKUP_COUNT` control rotation. The exact environment-variable defaults and validation boundary are maintained in [Environment Variables](Python-Inference-API-—-Application-Construction-Environment-Variables.md#python-api-environment-logging). When investigating an HTTP path, filter the recorded JSON lines by `request_id` first, use `event` and `logger` to order the participating layers, and inspect `metadata` only after the shared envelope has identified the correct request and worker.
