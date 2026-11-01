# Application Construction — External Clients, Protocol Payloads, and Serialization

<show-structure for="chapter" depth="3"/>

The controller separates transport from protocol interpretation. <code>OdlRestconfDataClient</code> reads RESTCONF data resources such as network topology, PCEP topology, and OpenFlow inventory. <code>OdlOperationsClient</code> posts XML inputs to RESTS operations for constrained-path calculation and <code>update-lsp</code>. Both clients use the provider's shared blocking <code>HttpClient</code>, configured timeout, and Basic authentication, but they return the raw <code>HttpResponse&lt;String&gt;</code> whenever a caller must apply operation-specific status and body semantics.

The page covers that client and serialization boundary. [Topology Discovery and Path Computation](Java-Controller-Side-Application-—-Application-Construction-Topology-Discovery-and-Path-Computation.md#constrained-path-calculation-and-ero-construction) owns BGP-LS validation, calculated-path caching, and ERO derivation. [Delegated LSP Lifecycle and Tunnel Application](Java-Controller-Side-Application-—-Application-Construction-Delegated-LSP-Lifecycle-and-Tunnel-Application.md#update-submission-and-pcep-convergence) owns the managed-LSP lifecycle and application result. Those topics define why a read or update is needed. This one defines what crosses the OpenDaylight boundary and how it is interpreted.

## RESTCONF data reads and RESTS operation posts {id="restconf-data-reads-and-rests-operation-posts"}

The data client creates authenticated XML-oriented GET and PUT requests. Its topology reads select operational state with <code>content=nonconfig</code>, while the PCEP topology read uses <code>content=all</code> because the caller needs configured and reported LSP information. The client does not accept a status on the caller's behalf. A topology service receives the response and decides whether its body can become a new registry snapshot. This preserves the difference between a reachable endpoint and usable controller state.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/http/OdlRestconfDataClient.java" include-lines="103-176"/>

<code-block lang="mermaid">
flowchart LR
    Discovery["TopologyDiscoveryService"] --> Data["OdlRestconfDataClient"]
    Refresh["TopologyRefreshService"] --> Data
    Lsp["DelegatedLspService"] --> Data
    Data -->|"GET topology or inventory"| ODL["OpenDaylight RESTCONF data"]
    ODL -->|"raw HttpResponse String"| Data
    Data --> Discovery
    Data --> Refresh
    Data --> Lsp
    Discovery --> Bgp["BGP-LS parser and registry"]
    Lsp --> Pcep["PCEP parser and LSP registry"]
</code-block>

RESTS operations use a separate client because an RPC input is not a data-store resource. The operations client posts <code>application/xml</code> to <code>/path-computation:get-constrained-path</code> or <code>/network-topology-pcep:update-lsp</code>, retains non-success HTTP responses, and converts only I/O failure or interruption into <code>IllegalStateException</code>. An interrupted sender restores the thread interrupt flag before it fails, so callers cannot confuse cancellation with a normal HTTP outcome.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/http/OdlOperationsClient.java" include-lines="89-191"/>

<code-block lang="mermaid">
sequenceDiagram
    participant Service as Path or delegated-LSP service
    participant Serializer as XML serializer
    participant Client as OdlOperationsClient
    participant ODL as OpenDaylight RESTS
    participant Classifier as outcome classifier or deserializer
    Service->>Serializer: serialize typed request
    Serializer-->>Service: XML input
    Service->>Client: computeConstrainedPath or updateLsp
    Client->>ODL: authenticated timed POST
    ODL-->>Client: HTTP status and XML body
    Client-->>Service: raw HttpResponse String
    Service->>Classifier: classify or deserialize
</code-block>

## Typed values at protocol payload boundaries {id="typed-values-at-protocol-payload-boundaries"}

Serialization is deliberately outside topology and tunnel decision logic. A path request receives a <code>CalculatedPathRequest</code> with graph-node identifiers, byte-per-second bandwidth, class type, and an allowed algorithm. Its serializer validates those values, escapes text fields, and produces the RPC input with an IPv4 address family. The matching response deserializer accepts only recognized calculation statuses, preserves path-description order, and retains remote IPv4 hops for later ERO construction. It does not decide whether the returned status is acceptable for caching. That remains with path computation.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/serialization/xml/PathComputationRequestXmlSerializer.java" include-lines="40-110"/>

<code-block lang="mermaid">
flowchart TD
    Constraints["PathConstraints"] --> Request["CalculatedPathRequest"]
    Nodes["BGP-LS graph node IDs"] --> Request
    Request --> RequestXml["PathComputationRequestXmlSerializer"]
    RequestXml --> Rpc["path-computation:get-constrained-path XML"]
    Rpc --> ODL["OpenDaylight RESTS"]
    ODL --> ResponseXml["path response XML"]
    ResponseXml --> ResponseParser["PathComputationResponseXmlDeserializer"]
    ResponseParser --> Hops["ordered PathHop values"]
    Hops --> Ero["ERO translation in path service"]
</code-block>

An LSP update starts with the current delegated record and a calculated ERO. The serializer rejects a missing PCC node, LSP name, PCEP topology identifier, positive PLSP identifier, encoded bandwidth, or ERO before any XML is sent. It then renders the PCEP topology reference, RSVP-TE arguments, delegated and administrative LSP flags, encoded bandwidth, and the ordered ERO subobjects. Because serializer input is typed, no packet callback builds XML fragments directly.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/serialization/xml/UpdateLspRequestXmlSerializer.java" include-lines="44-127"/>

<code-block lang="mermaid">
sequenceDiagram
    participant App as DirectionalLspApplicationService
    participant Lsp as DelegatedLspService
    participant Registry as DelegatedLspRegistry
    participant Serializer as UpdateLspRequestXmlSerializer
    participant RPC as update-lsp RESTS operation
    App->>Lsp: updateDelegatedLsp(direction, path, constraints)
    Lsp->>Registry: require current delegated record
    alt ERO and bandwidth already match
        Lsp-->>App: successful no-change result
    else different desired state
        Lsp->>Serializer: serialize(UpdateLspRequest)
        Serializer-->>Lsp: PCEP update XML
        Lsp->>RPC: POST update-lsp
        RPC-->>Lsp: immediate HTTP and XML result
    end
</code-block>

The response boundary is intentionally asymmetric. A path response becomes a typed status and ordered hops. An <code>update-lsp</code> response is only immediate evidence. A successful body may contain a PCEP error, and <code>no-ack</code> is a provisional success rather than convergence. The response deserializer gives a structured PCEP error precedence over a simple <code>failure</code> leaf, while the later PCEP topology read establishes whether the requested ERO was actually reported.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/serialization/xml/UpdateLspResponseXmlDeserializer.java" include-lines="33-92"/>

## Outcome classification, retry scope, and convergence {id="outcome-classification-retry-scope-and-convergence"}

The controller uses outcomes to distinguish a response that is usable now from one that should terminate work or remain uncertain. Topology and constrained-path classifiers confirm only HTTP 200 with a nonblank body. They classify authorization failures and other client statuses as hard failures. Server errors, status zero, and missing or blank bodies are ambiguous. The owning topology or path service applies its attempt budget. These retries do not occur in either transport client, preventing a generic HTTP retry from hiding an operation-specific safety rule.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/http/TopologyDiscoveryOutcomeClassifier.java" include-lines="43-65"/>

The <code>update-lsp</code> classifier instead considers HTTP 200 provisional. A blank response or one without a failure element remains provisional success. A <code>no-ack</code> response records the dedicated flag, another failure value is hard failure, and server or status-zero results are ambiguous. In all provisional cases, delegated-LSP application polls the PCEP topology rather than treating the original POST as proof of control-plane convergence.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/http/UpdateLspOutcomeClassifier.java" include-lines="70-108"/>

<code-block lang="mermaid">
flowchart TD
    Response["HTTP response and optional XML"] --> Kind{"operation"}
    Kind -->|"topology or path"| Read{"200 and nonblank body?"}
    Read -->|yes| Confirmed["CONFIRMED_SUCCESS"]
    Read -->|"401, 403, other client status"| Hard["HARD_FAILURE"]
    Read -->|"5xx, 0, blank or absent body"| Ambiguous["AMBIGUOUS"]
    Kind -->|"update-lsp"| Update{"HTTP 200?"}
    Update -->|no 5xx or 0| Ambiguous
    Update -->|"no, other status"| Hard
    Update -->|yes| Failure{"failure element"}
    Failure -->|"none or blank"| Provisional["PROVISIONAL_SUCCESS"]
    Failure -->|"no-ack"| NoAck["PROVISIONAL_SUCCESS with noAck"]
    Failure -->|"other value"| Hard
</code-block>

Once an update is accepted, <code>DelegatedLspService</code> polls by refreshing PCEP topology until the active ERO equals the requested ERO. A failed refresh is a recoverable empty poll. <code>RetryPolicy</code> doubles its delay up to the configured maximum, randomizes each wait by configured jitter, and preserves the most recent runtime failure as the exhaustion cause. The wait is synchronous and can pass the nominal deadline because a delay is not clipped. A bandwidth mismatch is logged after ERO convergence and does not turn that ERO confirmation into failure.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/tunnel/DelegatedLspService.java" include-lines="347-420"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/util/RetryPolicy.java" include-lines="75-125"/>

<code-block lang="mermaid">
stateDiagram-v2
    [*] --> DesiredStateCompared
    DesiredStateCompared --> Skipped : ERO and bandwidth match
    DesiredStateCompared --> Posted : desired state differs
    Posted --> Rejected : transport or hard failure
    Posted --> AwaitingPcep : provisional immediate result
    AwaitingPcep --> AwaitingPcep : refresh empty or fails, backoff and jitter
    AwaitingPcep --> Confirmed : reported ERO matches
    AwaitingPcep --> Unconfirmed : retry deadline exhausted
    Confirmed --> BandwidthWarning : bandwidth differs
    Confirmed --> [*]
    BandwidthWarning --> [*]
    Skipped --> [*]
    Rejected --> [*]
    Unconfirmed --> [*]
</code-block>

## Exchange evidence without credential disclosure {id="exchange-evidence-without-credential-disclosure"}

Both transport clients record request start and response receipt around every synchronous exchange. The normal information record contains transport label, method, endpoint, negotiated XML headers, body presence and byte counts, status, response content type, and monotonic duration. Complete request or response XML is added only when <code>OdlXmlBodyLogLevel</code> is <code>DEBUG</code> or <code>TRACE</code>. The logger reads only <code>Accept</code> and <code>Content-Type</code>, so the Basic <code>Authorization</code> header is not included in its metadata.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/http/OdlXmlExchangeLogger.java" include-lines="65-92"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/http/OdlXmlExchangeLogger.java" include-lines="117-192"/>

<code-block lang="mermaid">
flowchart LR
    Request["XML request"] --> Start["requestStarted"]
    Start --> InfoStart["info: metadata without body"]
    Start --> DetailStart{"DEBUG or TRACE?"}
    DetailStart -->|yes| BodyStart["diagnostic metadata with XML body"]
    ODL["OpenDaylight response"] --> Received["responseReceived"]
    Received --> InfoResponse["info: status, bytes, duration"]
    Received --> DetailResponse{"DEBUG or TRACE?"}
    DetailResponse -->|yes| BodyResponse["diagnostic metadata with XML body"]
    Start -. "Authorization never read" .-> Protected["credential excluded"]
    Received -. "Authorization never read" .-> Protected
</code-block>

These events explain the exchange without changing its decision authority. The topology and path topics remain responsible for their own retry and cache rules. The delegated-LSP topic remains responsible for accepting a two-phase result only after PCEP reports the requested ERO. [Operational State, Metrics, and Structured Logging](Java-Controller-Side-Application-—-Application-Construction-Operational-State-Metrics-and-Structured-Logging.md#structured-events-and-correlation-context) documents the broader event model and correlation context used alongside this transport evidence.
