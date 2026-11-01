# Java Controller Side Application — Application Construction

<show-structure for="chapter" depth="3"/>

The Java Controller Side Application is an OpenDaylight bundle that turns eligible OpenFlow packet notifications 
into directional evidence and, only when an actionable pair policy exists, updates pre-existing delegated PCEP LSPs. 
Its composition root is <code>SDN_MPLS_ML_Provider</code>. The provider constructs the shared transport, registries, 
serializers, clients, metrics, readiness machinery, and operational publisher once, then gives each runtime service 
only the collaborators it requires. This establishes one controller-owned view of topology, LSP ownership, 
classification results, and policy state without placing every control decision in the OSGi lifecycle class.

Two paths coexist but are not equivalent. The lifecycle path begins when Blueprint injects OpenDaylight services and 
invokes <code>init()</code>. It builds the object graph, subscribes to <code>PacketReceived</code>, and begins a 
background readiness cycle. The data path begins with an OpenFlow notification. It can observe and account for a 
packet immediately, but it cannot classify traffic or influence an LSP until BGP-LS topology discovery, 
delegated-LSP validation, and OpenFlow bootstrap have all succeeded. Listener registration is, therefore, not an 
assertion that the control plane is ready.

## OSGi construction boundary {id="osgi-construction-boundary"}

Karaf creates the provider through the bundle Blueprint descriptor. The descriptor resolves the `MD-SAL` notification 
service, the `MD-SAL` data broker, and the `OSGi HTTP` service, passes them to one provider bean, requests <code>init
()</code> after construction, and invokes <code>close()</code> at bundle destruction. This is the explicit boundary 
between platform-managed services and the application-owned graph.

<code-block lang="xml" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/resources/OSGI-INF/blueprint/impl-blueprint.xml" include-lines="11-29"/>

The constructor retains only non-null platform dependencies. It does not open a controller connection, register a 
listener, or schedule a worker. A temporarily unavailable RESTCONF endpoint, BGP-LS topology, or PCEP LSP is 
consequently handled by the later readiness cycle rather than being misrepresented as a failed dependency injection.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/impl/SDN_MPLS_ML_Provider.java" include-lines="127-165"/>

<code-block lang="mermaid">
sequenceDiagram
    participant Karaf as Karaf and Blueprint
    participant NS as NotificationService
    participant DB as DataBroker
    participant HTTP as HttpService
    participant Provider as SDN_MPLS_ML_Provider
    Karaf->>NS: resolve reference
    Karaf->>DB: resolve reference
    Karaf->>HTTP: resolve reference
    Karaf->>Provider: construct(NS, DB, HTTP)
    Karaf->>Provider: init()
    Provider-->>Karaf: control paths begin
    Karaf->>Provider: close() on bundle destruction
</code-block>

The injected services have separate duties. <code>NotificationService</code> delivers packet notifications. 
<code>DataBroker</code> receives the YANG operational view. <code>HttpService</code> exposes Prometheus metrics at 
<code>/csa/metrics</code>. The RESTCONF shape of the controller's operational state is covered by [CSA 
operational-state requests](ODL-RESTCONF-—-CSA-Operational-State-Requests.md). The present topic explains how the 
provider supplies that projection with current runtime values.

<code-block lang="mermaid">
flowchart LR
    Blueprint["OSGi Blueprint"] --> Provider["SDN_MPLS_ML_Provider"]
    Notifications["NotificationService"] --> Provider
    Broker["DataBroker"] --> Provider
    Http["HttpService"] --> Provider
    Provider --> Listener["PacketReceived listener"]
    Provider --> State["YANG operational-state publisher"]
    Provider --> Metrics["Prometheus servlet at /csa/metrics"]
</code-block>

## Provider-owned runtime graph {id="provider-runtime-graph"}

Initialization obtains one <code>AppConfig</code> and enforces <code>DELEGATED_TUNNEL_UPDATE</code>. This is a 
runtime constraint. The application updates configured LSPs delegated to the controller and does not create a 
general-purpose tunnel lifecycle. The same configuration gives all assembled components their endpoint locations, 
topology and LSP identifiers, classifier details, retry limits, cache controls, and freshness thresholds.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/impl/SDN_MPLS_ML_Provider.java" include-lines="232-282"/>

One Java <code>HttpClient</code> is shared by the RESTCONF data client, RESTS operations client, and classifier 
client. Shared transport creates consistent request timeout behaviour, while explicit clients preserve protocol 
responsibility. The data client reads topology and inventory. The operations client invokes controller operations, 
including PCEP LSP updates. The classifier client invokes the Python inference service. XML and JSON serializers are 
injected into their consuming services, so domain logic exchanges typed values rather than assembling protocol 
payloads inside a packet callback.

<code-block lang="mermaid">
flowchart TB
    Config["AppConfig.fromEnvironment()"] --> Gate{"DELEGATED_TUNNEL_UPDATE?"}
    Gate -- no --> Failure["initialization fails"]
    Gate -- yes --> Client["shared HttpClient"]
    Client --> Data["OdlRestconfDataClient"]
    Client --> Operations["OdlOperationsClient"]
    Client --> Classifier["ClassifierRestClient"]
    Data --> Reads["BGP-LS and OpenFlow reads"]
    Operations --> Pcep["PCEP operations"]
    Classifier --> Ml["Python classification API"]
</code-block>

The assembly order encodes durable dependencies. Initial BGP-LS discovery and periodic refresh share one node 
registry. Path computation consumes that registry and writes calculated paths. Classification retains results in a 
registrar. Delegated-LSP discovery fills an LSP registry. Pair-policy coordination combines directional evidence, 
consensus, preemption, and directional LSP application. No registry is recreated for an individual PacketIn event.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/impl/SDN_MPLS_ML_Provider.java" include-lines="289-395"/>

<code-block lang="mermaid">
flowchart TD
    Data["RESTCONF data client"] --> Discovery["TopologyDiscoveryService"]
    Discovery --> Nodes["BgpLsNodeRegistry"]
    Data --> Inventory["OpenflowInventoryService"]
    Nodes --> Path["PathComputationService"]
    Operations["RESTS operations client"] --> Path
    Data --> Delegated["DelegatedLspService"]
    Operations --> Delegated
    Delegated --> LspRegistry["DelegatedLspRegistry"]
    Classifier["ClassifierRestClient"] --> Classification["ClassificationService"]
    Classification --> Results["ClassificationRegistrar"]
    Classification --> Evidence["directional evidence"]
    Evidence --> Pair["PairPolicyCoordinator"]
    Path --> Application["DirectionalLspApplicationService"]
    Pair --> Application
    Application --> Delegated
</code-block>

### Live state and supplier boundaries {id="live-state-and-suppliers"}

The operational publisher receives registries and suppliers instead of a snapshot produced during initialization. 
Each publication reads the current readiness flag, closed flag, packet counter, and registry values. The `Packet-In` 
workflow receives an equivalent admission pair. One supplier reads readiness. The other asks the topology refresh 
service to ensure fresh BGP-LS data for that notification. A successful startup cycle is therefore not treated as 
indefinite permission to apply later control decisions.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/impl/SDN_MPLS_ML_Provider.java" include-lines="400-443"/>

<code-block lang="mermaid">
classDiagram
    class SDN_MPLS_ML_Provider {
        +init()
        +onNotification(PacketReceived)
        +close()
        -controlPlaneReady: AtomicBoolean
        -closed: AtomicBoolean
        -packetInCounter: AtomicLong
    }
    class ControllerOperationalStatePublisher {
        +publish()
    }
    class SdnMplsMlWorkflowService {
        +handlePacket(PacketReceived)
    }
    class TopologyRefreshService {
        +ensureFresh()
        +start()
        +close()
    }
    SDN_MPLS_ML_Provider --> ControllerOperationalStatePublisher : constructs with suppliers
    SDN_MPLS_ML_Provider --> SdnMplsMlWorkflowService : constructs with suppliers
    SdnMplsMlWorkflowService --> TopologyRefreshService : freshness callback
    ControllerOperationalStatePublisher --> SDN_MPLS_ML_Provider : reads current state
</code-block>

## Readiness cycle and recovery {id="readiness-cycle-and-recovery"}

The provider registers the `Packet-In` listener before remote readiness, publishes an initial operational state, and 
schedules the first readiness attempt with zero delay. This order prevents silent loss of notifications while the 
bundle starts. It does not admit packet-driven changes. The workflow consults <code>controlPlaneReady</code> for 
every notification, so early packets are observed and counted without reaching classification, consensus, path 
computation, or PCEP update operations.

The provider owns a single-thread daemon executor for readiness attempts. The attempt first initializes BGP-LS 
topology discovery, next discovers and verifies delegated LSPs, and finally provisions and verifies OpenFlow 
baseline access rules. Only after all phases succeed does it start periodic topology refresh, set 
<code>controlPlaneReady</code>, and publish its operational state. That order prevents a path computation from using no 
topology, an application from targeting an unverified LSP, or packet processing from relying on incomplete ingress 
mappings.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/impl/SDN_MPLS_ML_Provider.java" include-lines="474-585"/>

<code-block lang="mermaid">
sequenceDiagram
    participant Provider
    participant Executor as readiness executor
    participant Topology as TopologyDiscoveryService
    participant Lsp as DelegatedLspService
    participant Bootstrap as OpenflowBootstrapService
    participant Refresh as TopologyRefreshService
    participant State as operational publisher
    Provider->>Executor: schedule attempt 1, delay 0
    Executor->>Topology: initialize()
    Topology-->>Executor: validated BGP-LS registry
    Executor->>Lsp: initialize()
    Lsp-->>Executor: delegated LSPs confirmed
    Executor->>Bootstrap: initialize()
    Bootstrap-->>Executor: inventory and rule verification
    Executor->>Bootstrap: isReady()
    Bootstrap-->>Executor: true
    Executor->>Refresh: start()
    Executor->>Provider: controlPlaneReady.set(true)
    Executor->>State: publish()
</code-block>

A <code>RuntimeException</code> in any readiness phase is contained by the executor path. The provider clears 
readiness and publishes that condition before it considers a retry. If it is still open, it derives a bounded 
exponential delay, adds configured jitter, and schedules another attempt. This prevents a transient remote 
dependency failure from silently terminating recovery.

<code-block lang="mermaid">
stateDiagram-v2
    [*] --> Constructing
    Constructing --> ListenerRegistered : init registers listener
    ListenerRegistered --> Discovering : attempt scheduled
    Discovering --> LspValidation : topology valid
    LspValidation --> OpenFlowBootstrap : LSPs updateable
    OpenFlowBootstrap --> Ready : rules verified
    Discovering --> NotReady : runtime failure
    LspValidation --> NotReady : runtime failure
    OpenFlowBootstrap --> NotReady : runtime failure
    NotReady --> Discovering : backoff and jitter
    Constructing --> Closed : destruction
    ListenerRegistered --> Closed : destruction
    Discovering --> Closed : shutdown observed
    NotReady --> Closed : shutdown observed
    Ready --> Closed : destruction
</code-block>

<code-block lang="mermaid">
flowchart TD
    Start["readiness attempt"] --> Closed{"closed?"}
    Closed -- yes --> Stop["return"]
    Closed -- no --> Bgp["initialize BGP-LS topology"]
    Bgp --> Lsp["initialize delegated LSPs"]
    Lsp --> OpenFlow["bootstrap and verify OpenFlow"]
    OpenFlow --> Confirm{"bootstrap ready?"}
    Confirm -- yes --> Refresh["start periodic refresh"]
    Refresh --> Ready["set readiness and publish state"]
    Confirm -- no --> Failed["clear readiness and publish state"]
    Bgp -. exception .-> Failed
    Lsp -. exception .-> Failed
    OpenFlow -. exception .-> Failed
    Failed --> RetryAllowed{"closed?"}
    RetryAllowed -- yes --> Stop
    RetryAllowed -- no --> Retry["backoff, jitter, schedule retry"]
</code-block>

The <code>closed</code> checks lie between the major remote calls. They make shutdown visible before a later phase 
begins, but they are not a lifecycle lock. A remote read, LSP operation, OpenFlow call, or notification that has 
already passed its preceding check can finish while shutdown begins. The runtime promises controlled admission and 
best-effort cancellation rather than a false guarantee that asynchronous remote effects are mutually exclusive with 
bundle destruction.

## Packet notification admission and delegation {id="packet-notification-admission"}

MD-SAL invokes <code>onNotification</code> with <code>PacketReceived</code>. The provider uses that method as a 
correlation and fault-containment boundary. A null notification is logged and discarded. A non-null notification 
receives a monotonically increasing packet sequence and a <code>LogContext</code> containing a generated workflow 
identifier. The provider delegates control decisions to <code>SdnMplsMlWorkflowService</code>, contains a runtime 
exception so one packet cannot terminate subsequent MD-SAL delivery, and publishes its current state in the 
<code>finally</code> block.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/impl/SDN_MPLS_ML_Provider.java" include-lines="649-692"/>

The workflow has an admission sequence before it reaches the classifier. It increments packet metrics, rejects work 
when the control plane is not ready, extracts a classification context, accepts IPv4 only, resolves the configured 
flow direction, and checks topology freshness. An unsupported EtherType, unknown ingress, stale topology, or 
not-ready controller is a controlled no-action result. No evidence is formed with an unknown direction or a topology 
that has exceeded the configured freshness threshold.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/SdnMplsMlWorkflowService.java" include-lines="139-219"/>

<code-block lang="mermaid">
flowchart TD
    Packet["PacketReceived"] --> Count["increment packet metrics"]
    Count --> Ready{"control plane ready?"}
    Ready -- no --> IgnoreReady["record no-action outcome"]
    Ready -- yes --> Extract["extract classification context"]
    Extract --> IPv4{"IPv4 context?"}
    IPv4 -- no --> IgnoreType["discard unsupported or ARP packet"]
    IPv4 -- yes --> Direction["resolve FlowDirection"]
    Direction --> Known{"configured ingress?"}
    Known -- no --> IgnoreIngress["discard unknown ingress"]
    Known -- yes --> Fresh{"topology fresh?"}
    Fresh -- no --> IgnoreTopology["record topology_stale"]
    Fresh -- yes --> Classify["classify or use cached result"]
    Classify --> Evidence["build directional evidence"]
    Evidence --> Consensus["PairPolicyCoordinator.handleEvidence"]
</code-block>

When admission succeeds, the workflow reuses a cached classification or calls the Python classifier. It creates 
directional evidence with a normalized service identity and configured tunnel pair, requested bandwidth, and an 
expiry instant. The pair coordinator decides whether the available directional evidence is sufficient for an 
actionable policy. A one-sided consensus result is deliberately not an LSP update. When a candidate is applicable, 
directional application calculates a route and asks the delegated-LSP service to update the already validated PCEP LSP.

<code-block lang="mermaid">
sequenceDiagram
    participant OF as OpenFlow Plugin
    participant Provider
    participant Workflow
    participant Features as PacketInFeatureExtractor
    participant ML as ClassificationService
    participant Pair as PairPolicyCoordinator
    participant Path as PathComputationService
    participant LSP as DelegatedLspService
    participant State as operational publisher
    OF->>Provider: PacketReceived
    Provider->>Workflow: handlePacket(notification)
    Workflow->>Workflow: readiness and topology admission
    Workflow->>Features: extract(notification)
    Features-->>Workflow: PacketClassificationContext
    Workflow->>ML: classifyOrGetCached(context)
    ML-->>Workflow: ClassificationLookupResult
    Workflow->>Pair: handleEvidence(evidence, context)
    alt pending or deferred
        Pair-->>Workflow: no candidate
    else actionable candidate
        Pair->>Path: compute path
        Path-->>Pair: calculated ERO
        Pair->>LSP: update delegated LSP
        LSP-->>Pair: application outcome
    end
    Workflow-->>Provider: return or runtime failure
    Provider->>State: publish()
</code-block>

## State, metrics, and lifecycle closure {id="state-metrics-and-closure"}

The operational publisher is constructed with the same registries that topology discovery, classification, path 
computation, delegated-LSP management, OpenFlow bootstrap, and pair-policy coordination update. It publishes after 
listener registration, after readiness success or failure, and after each non-null notification. Its view is 
consequently a projection of current process state, not a startup record. The same metrics instance is passed to 
application services and registered through the OSGi servlet, so a Prometheus scrape describes the counters used by 
the live control path.

<code-block lang="mermaid">
flowchart LR
    Topology["topology and refresh registries"] --> Publisher["ControllerOperationalStatePublisher"]
    Classification["classification registrar"] --> Publisher
    Paths["calculated-path registry"] --> Publisher
    Lsps["delegated-LSP registry"] --> Publisher
    OpenFlow["switch registry"] --> Publisher
    Policy["evidence and active-policy registries"] --> Publisher
    Flags["ready, closed, packet counter suppliers"] --> Publisher
    Publisher --> Broker["MD-SAL operational datastore"]
    Metrics["shared SdnMplsMlMetrics"] --> Servlet["PrometheusMetricsServlet"]
    Servlet --> Endpoint["GET /csa/metrics"]
</code-block>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/impl/SDN_MPLS_ML_Provider.java" include-lines="761-801"/>

At destruction, <code>close()</code> first makes closure and non-readiness visible while its state publisher still 
exists. It then requests executor cancellation, closes the packet listener registration, stops topology refresh, and 
unregisters the metrics servlet when it was successfully registered. Null guards permit partial initialization to 
close safely. The method does not join a notification, remote HTTP request, or readiness phase already in progress.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/impl/SDN_MPLS_ML_Provider.java" include-lines="720-744"/>

<code-block lang="mermaid">
stateDiagram-v2
    [*] --> Running
    Running --> Closing : Blueprint destroy-method
    Closing --> StatePublished : closed=true and ready=false
    StatePublished --> ExecutorStopped : shutdownNow()
    ExecutorStopped --> ListenerReleased : registration.close()
    ListenerReleased --> RefreshStopped : topologyRefreshService.close()
    RefreshStopped --> MetricsRemoved : unregister /csa/metrics
    MetricsRemoved --> Closed
</code-block>

## End-to-end control-plane relationship {id="end-to-end-control-plane-relationship"}

The complete relationship joins construction and packet processing without treating them as one event. Construction 
establishes durable collaborators. Readiness fills and verifies state required for safe action. Packet processing 
emits evidence only while that state is usable. Consensus selects whether a pair policy can proceed. Directional 
application computes and applies an LSP change only for a valid candidate. Structured logs, metrics, and operational 
state expose each material transition.

<code-block lang="mermaid">
flowchart TB
    OSGi["Blueprint injects OSGi services"] --> Assemble["provider assembles clients, registries, and services"]
    Assemble --> Subscribe["subscribe to PacketReceived"]
    Subscribe --> Readiness["BGP-LS, delegated PCEP, OpenFlow readiness"]
    Readiness --> Ready{"control plane ready?"}
    Packet["OpenFlow PacketReceived"] --> Gate{"ready and topology fresh?"}
    Ready -- yes --> Gate
    Ready -- no --> Retry["bounded retry with jitter"]
    Retry --> Readiness
    Gate -- no --> Observe["metrics, logs, operational state"]
    Gate -- yes --> Classification["feature extraction and classification"]
    Classification --> Evidence["directional evidence"]
    Evidence --> Pair["pair-policy consensus and preemption"]
    Pair --> Candidate{"actionable candidate?"}
    Candidate -- no --> Observe
    Candidate -- yes --> Compute["path computation"]
    Compute --> Update["delegated PCEP LSP update"]
    Update --> Observe
</code-block>

The provider owns the shared safety conditions without assuming that a packet is automatically sufficient to alter the
traffic-engineering state. Its readiness and freshness gates prevent individual services from constructing 
conflicting views of topology, policy, or LSP ownership. Those gates stay visible through the closing state, which 
preserves the separation between observed traffic and permitted controller action.
