# Application Construction — Operational State, Metrics, and Structured Logging

<show-structure for="chapter" depth="3"/>

The controller publishes three complementary views of its runtime. <code>ControllerOperationalStatePublisher</code> writes a YANG binding into the MD-SAL operational datastore, <code>SdnMplsMlMetrics</code> retains Prometheus metric series, and <code>StructuredLogger</code> emits JSON events through SLF4J. The provider owns their composition and calls publication after material lifecycle transitions and after a non-null PacketIn workflow. This page describes those representations without redefining the packet decisions owned by [PacketIn Classification Workflow](Java-Controller-Side-Application-—-Application-Construction-PacketIn-Classification-Workflow.md) or the pair-policy state transitions owned by [Pair Policy Consensus and Preemption](Java-Controller-Side-Application-—-Application-Construction-Pair-Policy-Consensus-and-Preemption.md).

## Operational state is rebuilt from live suppliers {id="operational-state-live-suppliers"}

The provider constructs one publisher with the controller registries, the topology-refresh service, the shared metrics registry, and suppliers for the atomic readiness flag, closed flag, and packet counter. It passes suppliers rather than a state object created during initialization, so each invocation observes current values at the time it builds the binding. The published model is the read-only <code>controller-state</code> container from <path>csa.yang</path>; the RESTCONF projections and request examples are maintained in [CSA operational-state requests](ODL-RESTCONF-—-CSA-Operational-State-Requests.md).

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/impl/SDN_MPLS_ML_Provider.java" include-lines="397-415"/>

<code-block lang="mermaid">
flowchart LR
    Provider["SDN_MPLS_ML_Provider"] --> Publisher["ControllerOperationalStatePublisher"]
    Provider --> Ready["AtomicBoolean controlPlaneReady"]
    Provider --> Closed["AtomicBoolean closed"]
    Provider --> Packets["AtomicLong packetInCounter"]
    Registries["Topology, cache, LSP, policy, and inventory registries"] --> Publisher
    Refresh["TopologyRefreshService"] --> Publisher
    Ready --> Publisher
    Closed --> Publisher
    Packets --> Publisher
    Publisher --> Yang["MD-SAL OPERATIONAL controller-state"]
</code-block>

The publisher obtains independent snapshots for classification caches, calculated paths, delegated LSPs, evidence buckets, active policies, BGP-LS nodes, and OpenFlow inventory. It then derives cache counts, serializes the configured tunnel pair and directions, and assigns a new generation time. The result is intentionally a best-effort cross-registry view: a concurrent writer can change one registry after another registry has already been copied. It is therefore suitable for observing current controller state, not for asserting that every entry belonged to one atomic transaction.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/operational/ControllerOperationalStatePublisher.java" include-lines="244-292"/>

<code-block lang="mermaid">
sequenceDiagram
    participant Owner as Provider or service
    participant Publisher as ControllerOperationalStatePublisher
    participant Registries as Runtime registries
    participant Broker as DataBroker
    Owner->>Publisher: publish()
    Publisher->>Registries: read separate snapshots and status
    Publisher->>Publisher: build ControllerState and update gauges
    Publisher->>Broker: put(OPERATIONAL, controller-state, state)
    Publisher->>Broker: commit()
    Publisher-->>Owner: submission returned
    Note over Publisher,Broker: Commit completion is not awaited by the publisher.
</code-block>

## Publication outcomes and registry gauges {id="publication-outcomes-and-registry-gauges"}

Each publication creates a write-only transaction, replaces the root state at the operational path, submits the commit, and increments a success counter. A synchronous <code>RuntimeException</code> increments a failure series with <code>reason=runtime_exception</code> and produces a structured warning, allowing the control path to continue. Submission success is not evidence that the asynchronous datastore commit has completed successfully, because this class neither waits for nor observes the returned completion.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/operational/ControllerOperationalStatePublisher.java" include-lines="205-224"/>

The same snapshots supply the registry gauges. Cache, calculated-path, delegated-LSP, evidence, active-policy, inventory, and BGP-LS counts are recorded before the YANG object is returned. The fixed tunnel-pair and direction gauges reflect the configured single managed pair and its two directions, rather than a discovery result. This reuse prevents the state count and the corresponding gauge from being computed from different copies of the same registry during one publication.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/operational/ControllerOperationalStatePublisher.java" include-lines="351-372"/>

<code-block lang="mermaid">
flowchart TD
    Snapshots["Separate registry snapshots"] --> State["ControllerState YANG binding"]
    Snapshots --> Gauges["sma_registry_* gauges"]
    State --> Transaction["MD-SAL write transaction"]
    Transaction --> Submitted{"submit completed synchronously?"}
    Submitted -->|yes| Success["publish_success_total incremented"]
    Submitted -->|RuntimeException| Failure["publish_failure_total{reason=runtime_exception}"]
    Failure --> Event["structured WARN event"]
</code-block>

## Metric registry and scrape endpoint {id="metric-registry-and-scrape-endpoint"}

<code>SdnMplsMlMetrics</code> stores counters in <code>ConcurrentHashMap</code>-indexed <code>LongAdder</code> instances, gauges as replacement values, and histograms in per-series concurrent accumulators. Metric metadata defines the families that Prometheus exposition traverses. Counter and gauge writers can retain an unregistered name, but an exposition only emits predefined metadata families. Histogram observation instead rejects an unknown or non-histogram family before creating the series.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/metrics/SdnMplsMlMetrics.java" include-lines="31-49"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/metrics/SdnMplsMlMetrics.java" include-lines="66-81"/>

The provider registers <code>PrometheusMetricsServlet</code> at <code>/csa/metrics</code> with the same registry used by control-plane services. The servlet obtains freshly rendered UTF-8 text for every GET and returns status 200 with Prometheus text exposition media type. If Karaf cannot register the servlet because the alias already exists or servlet construction fails, initialization translates that platform failure to <code>IllegalStateException</code>. On shutdown the provider unregisters the retained alias after publishing the closed, not-ready state.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/impl/SDN_MPLS_ML_Provider.java" include-lines="789-803"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/metrics/PrometheusMetricsServlet.java" include-lines="58-64"/>

<code-block lang="mermaid">
sequenceDiagram
    participant Service as Controller service
    participant Registry as SdnMplsMlMetrics
    participant Http as PrometheusMetricsServlet
    participant Scraper as Prometheus scraper
    Service->>Registry: increment, setGauge, or observeHistogram
    Scraper->>Http: GET /csa/metrics
    Http->>Registry: renderPrometheusText()
    Registry-->>Http: HELP, TYPE, and current samples
    Http-->>Scraper: 200 text/plain; version=0.0.4; charset=utf-8
</code-block>

## Snapshot semantics under concurrent measurement {id="metrics-snapshot-semantics"}

Metric updates do not require a global registry lock. A compatibility snapshot copies currently visible unlabeled counter sums and then gauge values into an immutable map. Histogram rendering similarly reads individual adders and copies bucket boundaries. Both mechanisms can observe values from different instants while writers continue, so neither is a global transaction. This tradeoff lets controller callbacks record telemetry without blocking on a scrape, while the resulting samples remain an operational observation rather than a serializable cut of all measurements.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/metrics/SdnMplsMlMetrics.java" include-lines="202-228"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/metrics/SdnMplsMlMetrics.java" include-lines="746-756"/>

<code-block lang="mermaid">
flowchart LR
    UpdateA["counter increment"] --> Adders["LongAdder series"]
    UpdateB["gauge assignment"] --> Gauges["ConcurrentHashMap"]
    UpdateC["histogram observation"] --> Buckets["per-series adders"]
    Scrape["render or snapshot"] --> Adders
    Scrape --> Gauges
    Scrape --> Buckets
    Scrape --> View["non-atomic current view"]
</code-block>

## Structured events and correlation context {id="structured-events-and-correlation-context"}

<code>StructuredLogger</code> builds a JSON payload only when the selected SLF4J level is enabled. Its payload contains event identity, logger and component names, timestamp, thread, operation, message, optional metadata, a copy of the current MDC context, and optional exception type, message, and stack trace. If JSON serialization itself fails, the facade emits a fixed minimal logging-failure payload instead of letting the logging path obscure the original controller operation.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/observability/StructuredLogger.java" include-lines="249-282"/>

<code>LogContext</code> scopes MDC entries to the current thread. Opening a context copies the existing values, overlays non-null fields, and replaces the MDC. Closing the same scope restores the captured map exactly once. The provider opens a context containing a generated <code>workflow_id</code> and a monotonic <code>packet_sequence</code> around a non-null PacketIn, so downstream events on that synchronous workflow inherit the correlation fields without every method accepting them as parameters. A context does not by itself transfer MDC to work started on another thread.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/observability/LogContext.java" include-lines="30-98"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/impl/SDN_MPLS_ML_Provider.java" include-lines="641-691"/>

<code-block lang="mermaid">
sequenceDiagram
    participant MD as MD-SAL notification thread
    participant Provider as Provider
    participant MDC as SLF4J MDC
    participant Workflow as Packet workflow
    participant Log as StructuredLogger
    MD->>Provider: onNotification(PacketReceived)
    Provider->>MDC: open(workflow_id, packet_sequence)
    Provider->>Workflow: handlePacket(notification)
    Workflow->>Log: emit event
    Log->>MDC: getCopyOfContextMap()
    Log-->>Workflow: JSON event through SLF4J
    Provider->>MDC: restore prior context on scope close
    Provider->>Provider: publishOperationalState() in finally
</code-block>

## Lifecycle visibility and shutdown limits {id="lifecycle-visibility-and-shutdown-limits"}

The provider publishes an initial state after listener registration, publishes after a successful readiness transition, publishes the not-ready state after a failed readiness attempt, and publishes after each non-null PacketIn even when its workflow raises a runtime exception. Closing first sets <code>closed=true</code> and <code>controlPlaneReady=false</code>, then publishes while the publisher and its suppliers remain available. It subsequently requests executor shutdown, removes the notification registration, closes topology refresh, and unregisters the metrics servlet.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/impl/SDN_MPLS_ML_Provider.java" include-lines="552-578"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/impl/SDN_MPLS_ML_Provider.java" include-lines="720-743"/>

The atomic flags make later readiness and admission checks see shutdown, but <code>close()</code> does not wait for an in-flight PacketIn, HTTP request, or readiness cycle and does not make their already-started remote effects disappear. Likewise, publication catches only synchronous runtime exceptions during transaction construction and submission. The ownership of workflow error containment remains at the notification boundary, while topology freshness and retry state are explained in [Topology Discovery and Path Computation](Java-Controller-Side-Application-—-Application-Construction-Topology-Discovery-and-Path-Computation.md).

<code-block lang="mermaid">
stateDiagram-v2
    [*] --> Assembled
    Assembled --> ListenerRegistered: init registers PacketReceived listener
    ListenerRegistered --> WaitingForReadiness: initial state published
    WaitingForReadiness --> Ready: discovery, LSP, and OpenFlow checks succeed
    WaitingForReadiness --> WaitingForReadiness: attempt fails and retry is scheduled
    Ready --> Closing: close requested
    WaitingForReadiness --> Closing: close requested
    Closing --> ClosedStatePublished: closed=true and ready=false
    ClosedStatePublished --> ResourcesReleased: executor, listener, refresh, and metrics alias released
    ResourcesReleased --> [*]
</code-block>

## Metric-series mutation and exposition {id="metric-series-mutation-and-exposition"}

<code>SdnMplsMlMetrics</code> retains three independent concurrent maps. Counters use <code>LongAdder</code>, gauges replace a <code>Double</code>, and histograms retain a per-series accumulator with bucket adders, a count adder, and a sum adder. Counter and gauge updates normalize labels into an immutable, key-sorted <code>MetricKey</code> without checking registration. Histogram updates differ because they resolve predefined metadata first and reject unknown or non-histogram names. Consequently, an internal caller can store an unregistered counter or gauge that will not appear in Prometheus exposition, while an invalid histogram call fails at observation time.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/metrics/SdnMplsMlMetrics.java" include-lines="45-90"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/metrics/SdnMplsMlMetrics.java" include-lines="176-200"/>

The exporter iterates only the fixed metadata catalog. It emits HELP and TYPE declarations for every registered family, including families with no stored samples, and sorts stored series before rendering. Histogram exposition converts each stored first-matching-bucket count into Prometheus's cumulative bucket sequence, then writes the <code>+Inf</code> bucket, sum, and count. Label keys are sorted, blank values become <code>unknown</code>, and long values are truncated before they become series keys. Those rules give equivalent label maps one stable identity and bound a major source of label-cardinality growth.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/metrics/SdnMplsMlMetrics.java" include-lines="241-324"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/metrics/SdnMplsMlMetrics.java" include-lines="358-414"/>

<code-block lang="mermaid">
flowchart TD
    Update[service reports measurement] --> Key[sort and normalize labels into MetricKey]
    Key --> Kind{metric operation}
    Kind -- counter --> Counter[ConcurrentHashMap and LongAdder]
    Kind -- gauge --> Gauge[ConcurrentHashMap replaces Double]
    Kind -- histogram --> Metadata{registered histogram?}
    Metadata -- no --> Reject[IllegalArgumentException]
    Metadata -- yes --> Histogram[HistogramState bucket, count, sum adders]
    Counter --> Scrape[render registered metadata families]
    Gauge --> Scrape
    Histogram --> Scrape
    Scrape --> Text[Prometheus text exposition]
</code-block>

## JSON event construction and thread-local correlation {id="json-event-construction-and-thread-local-correlation"}

<code>StructuredLogger</code> does not pass arbitrary messages directly to SLF4J. Each level method first checks the backend level, then constructs JSON only when that level is enabled. The payload includes a timestamp, level, logger name, thread name, fixed service name, event, component, operation, message, optional metadata, and optional exception details. The logger creates a class-specific facade, so the component field identifies the class that emitted the event instead of the class that happened to assemble global observability state.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/observability/StructuredLogger.java" include-lines="74-200"/>

<code>LogContext.open()</code> captures the current SLF4J MDC map, overlays non-null correlation entries, and restores the captured map exactly once when its <code>AutoCloseable</code> scope closes. PCEP updates use this pattern to attach LSP name, PCC node, and PLSP ID to all events emitted in their lexical scope. Because MDC is thread-local, the scope correlates events on the executing thread only. The code does not automatically transfer that context to another executor or a later asynchronous callback.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/observability/LogContext.java" include-lines="20-97"/>

If Jackson cannot serialize metadata or an exception object, <code>StructuredLogger</code> returns a fixed minimal JSON failure event rather than letting logging obscure the original control path. This fallback does not preserve the original event's metadata or stack trace. It preserves a detectable logging failure with the controller service name, while callers continue to own the underlying application exception.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/observability/StructuredLogger.java" include-lines="248-303"/>

<code-block lang="mermaid">
sequenceDiagram
    participant Service as controller service
    participant Scope as LogContext
    participant MDC as SLF4J MDC on current thread
    participant Logger as StructuredLogger
    participant Backend as SLF4J backend
    Service->>Scope: open(correlation values)
    Scope->>MDC: capture, merge, replace
    Service->>Logger: info or warn(event, metadata)
    Logger->>Logger: read MDC and build JSON envelope
    Logger->>Backend: emit JSON when level enabled
    Service->>Scope: close()
    Scope->>MDC: restore captured map once
</code-block>
