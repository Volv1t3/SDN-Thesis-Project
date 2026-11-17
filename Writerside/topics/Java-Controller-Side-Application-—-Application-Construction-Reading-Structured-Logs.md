# Application Construction — Reading Structured Logs

<show-structure for="chapter" depth="2"/>

The Java Controller Side Application emits one JSON object per line through class-specific SLF4J loggers. The controller recordings in `docs/experimentation` show this compact envelope for PacketIn workflows, topology activity, and controller initialization. A reader can first identify the event and emitting component, then use the `context` object to join records that belong to the same workflow. This page limits itself to the shared event structure and the environment-controlled threshold. The detailed PacketIn, topology, policy, and LSP pages remain the authority for their event sequences.

## JSON event envelope {id="java-log-json-event-envelope"}

`StructuredLogger` creates the common fields in a fixed order before it writes JSON through the configured SLF4J backend. `service` is fixed to `sdn-mpls-ml-controller`, `logger` identifies the class-associated backend logger, and `component` is the simple class name from which the facade was created. `metadata`, `context`, and `exception` are included only when the active event supplies them. The Java records in the experimentation logs therefore retain the same top-level schema even though the nested data is different for classification, topology, and LSP operations.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/observability/StructuredLogger.java" include-lines="248-303"/>

<code-block lang="json">
{
  "timestamp": "UTC ISO-8601 instant",
  "level": "TRACE | DEBUG | INFO | WARN | ERROR",
  "logger": "emitting Java logger name",
  "thread": "current controller thread",
  "service": "sdn-mpls-ml-controller",
  "event": "stable event identifier",
  "component": "emitting class simple name",
  "operation": "emitting method or operation",
  "message": "human-readable event message",
  "context": {"workflow correlation": "..."},
  "metadata": {"event-specific detail": "..."}
}
</code-block>

| Top-level field | Presence | How to interpret it |
|---|---|---|
| `timestamp` | Always | UTC instant created when the structured event is serialized. |
| `level` | Always | Severity passed to the level-specific `StructuredLogger` method. |
| `logger` | Always | Fully qualified SLF4J logger name associated with the emitting class. |
| `thread` | Always | Controller thread that emitted the event. It gives execution context but is not a workflow identifier. |
| `service` | Always | Fixed controller-service identity, `sdn-mpls-ml-controller`. |
| `event` | Always for facade events | Stable event identifier used for machine-oriented grouping. |
| `component` and `operation` | Always for facade events | The emitting class's simple name and the operation supplied by that class. Together they identify the code-level owner of the event. |
| `message` | Always | Human-readable event description. |
| `context` | Optional object | Copy of the current thread's SLF4J MDC correlation map. Packet workflows commonly carry `workflow_id`, `packet_sequence`, and direction data here. |
| `metadata` | Optional object | Event-specific structured details such as selected policy, endpoint, outcome, or duration. Its internal schema depends on `event`. |
| `exception` | Optional object | Failure representation with `type`, `message`, and `stack_trace` when a warning or error call receives a throwable. |

`LogContext` scopes the MDC to the executing thread. Opening a scope copies previous entries, overlays supplied non-null keys, and closing the scope restores the prior map. As a result, `context.workflow_id` and `context.packet_sequence` can join synchronous PacketIn records across participating controller classes without placing correlation fields in every method signature. MDC does not automatically cross an executor boundary, so a matching `thread` is not a substitute for matching a workflow ID. [Operational State, Metrics, and Structured Logging](Java-Controller-Side-Application-—-Application-Construction-Operational-State-Metrics-and-Structured-Logging.md#structured-events-and-correlation-context) documents the lifecycle and thread boundary in detail.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/observability/LogContext.java" include-lines="37-80"/>

## Level threshold and output locations {id="java-log-level-threshold-and-output-locations"}

The controller image entrypoint, rather than `AppConfig`, configures the main structured-event threshold before Karaf starts. It normalizes `SMA_LOG_LEVEL` to uppercase, accepts `TRACE`, `DEBUG`, `INFO`, `WARN`, and `ERROR`, and stops startup with status 64 for any other value. The entrypoint writes a Log4j2 configuration for the `com.sma.sdn` logger namespace, so the threshold applies to controller-side application loggers rather than every unrelated Karaf logger. A setting of `INFO` includes `INFO`, `WARN`, and `ERROR` facade events while omitting `DEBUG` and `TRACE` events whose level checks are not enabled.

<code-block lang="bash" src="../../src/main/java/baseline-odl/sma-odl-entrypoint.sh" include-lines="7-38"/>

<code-block lang="bash" src="../../src/main/java/baseline-odl/sma-odl-entrypoint.sh" include-lines="54-76"/>

The same entrypoint sends JSON messages unmodified to the console and to a rolling file selected by `SMA_LOG_DIRECTORY` and `SMA_LOG_FILENAME`. `SMA_LOG_FILE_MAX_BYTES` and `SMA_LOG_FILE_BACKUP_COUNT` control the rolling-file policy. `SMA_ODL_XML_BODY_LOG_LEVEL` is a separate application setting for XML request and response body logging. It does not replace `SMA_LOG_LEVEL`, which controls the controller's primary `com.sma.sdn` event threshold. The controller environment catalog is maintained in [Main Topology Per-Device Configuration Files and Definitions](Net-Impl-—-Main-Topology-Per-Device-Configuration-Files-And-Definitions.md).

When investigating one controller decision, filter the JSON lines by `context.workflow_id` when it exists, then use `event`, `component`, and `operation` to establish the local order. For events outside a workflow, such as initialization or topology refresh, `event` and `metadata` identify the operation while `context` may be absent. This preserves the distinction between an event's common envelope and the operation-specific evidence nested inside it.
