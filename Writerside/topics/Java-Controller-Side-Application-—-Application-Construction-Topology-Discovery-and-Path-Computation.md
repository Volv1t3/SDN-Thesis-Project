# Application Construction — Topology Discovery and Path Computation

<show-structure for="chapter" depth="3"/>

Topology discovery establishes the BGP-LS graph view that path computation requires after pair-policy consensus has selected a candidate. Path computation resolves the selected direction's router identifiers into graph-node identifiers, uses that directional identity and requested bandwidth as a cache key, and invokes the OpenDaylight constrained-path operation only on a cache miss. Its result is an explicit route supplied to delegated-LSP application, not an independently installed policy.

[Application Construction — Pair Policy Consensus and Preemption](Java-Controller-Side-Application-—-Application-Construction-Pair-Policy-Consensus-and-Preemption.md#coordinator-decision-sequence) owns the decision to apply a candidate and the later requirement that both directional results be non-failing before it installs active pair-policy state. [Application Construction — PacketIn Classification Workflow](Java-Controller-Side-Application-—-Application-Construction-PacketIn-Classification-Workflow.md#directional-evidence-construction) owns the earlier evidence that supplies bandwidth and priorities. This topic documents the topology and path contract consumed between those stages.

## Startup topology contract {id="startup-topology-contract"}

<code>TopologyDiscoveryService.initialize()</code> is a mandatory provider-startup operation. It reads the full network-topology list through RESTCONF, requires the configured BGP-LS and PCEP topology identifiers, then retrieves the configured BGP-LS topology. Parsed nodes replace the shared <code>BgpLsNodeRegistry</code> snapshot, and both configured endpoint router identifiers must resolve in that replacement. Successful startup therefore proves that configured routers can become graph vertices before a selected direction requests a path.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/topology/TopologyDiscoveryService.java" include-lines="83-155"/>

<code-block lang="mermaid">
sequenceDiagram
    participant Provider
    participant Discovery as TopologyDiscoveryService
    participant RESTCONF
    participant Parser as XML deserializers
    participant Registry as BgpLsNodeRegistry
    Provider->>Discovery: initialize()
    Discovery->>RESTCONF: getNetworkTopologyList()
    RESTCONF-->>Discovery: confirmed response
    Discovery->>Parser: parse topology identifiers
    Discovery->>Discovery: require BGP-LS and PCEP IDs
    Discovery->>RESTCONF: getBgpLsTopology(configured ID)
    RESTCONF-->>Discovery: confirmed response
    Discovery->>Parser: parse BGP-LS nodes
    Discovery->>Registry: replaceAll(nodes)
    Discovery->>Registry: resolve configured endpoint routers
</code-block>

The initial reader has a bounded retry loop. A confirmed response returns immediately, while a classified hard failure increments the discovery-failure counter and terminates the operation. A recoverable response or runtime exception consumes an attempt and is retried without an intentional delay. Exhaustion fails initialization rather than allowing a degraded start with an empty graph registry.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/topology/TopologyDiscoveryService.java" include-lines="178-246"/>

<code-block lang="mermaid">
flowchart TD
    Start[Topology RESTCONF read] --> Classify{response outcome}
    Classify -- confirmed --> Return[use response]
    Classify -- hard failure --> Fail[counter and fail startup]
    Classify -- recoverable --> Budget{attempts remain}
    Budget -- yes --> Start
    Budget -- no --> Exhausted[counter and fail startup]
    Transport[Runtime exception] --> Budget
</code-block>

## Refreshing a usable BGP-LS snapshot {id="topology-refresh-and-freshness"}

Initial success does not make the topology timeless. <code>TopologyRefreshService.ensureFresh()</code> compares the last successful refresh with <code>topologyCacheTtl</code> before packet processing continues. A fresh snapshot returns immediately. A stale one causes an on-demand refresh, while periodic refreshes can also occur. Its admission lock permits only one refresh at a time, so concurrent callers do not race competing snapshots into the registry.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/topology/TopologyRefreshService.java" include-lines="203-245"/>

The refresh path parses returned nodes into a temporary <code>BgpLsNodeRegistry</code> and resolves both configured endpoints there before replacing shared state. If the response is unconfirmed, parsing fails, or either endpoint is absent, the previous registry remains intact. A failed refresh is therefore observable as failed freshness rather than as a partially validated graph.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/topology/TopologyRefreshService.java" include-lines="278-349"/>

<code-block lang="mermaid">
stateDiagram-v2
    [*] --> Fresh
    Fresh --> Fresh : ensureFresh within TTL
    Fresh --> Stale : TTL boundary passes
    Stale --> Refreshing : one caller admitted
    Refreshing --> Fresh : validated snapshot replaces registry
    Refreshing --> Stale : unconfirmed response or validation failure
    Refreshing --> Refreshing : concurrent caller deduplicated
</code-block>

## Directional path identity and cache semantics {id="directional-path-identity-and-cache-semantics"}

<code>DirectionalLspApplicationService</code> constructs <code>PathConstraints</code> from the candidate's requested bandwidth, setup priority, and hold priority. <code>PathComputationService</code> converts bandwidth from kilobits per second to bytes per second, resolves source and destination router IDs through BGP-LS, and constructs <code>CalculatedPathKey</code>. The key contains ordered graph endpoints, converted bandwidth, class type, and algorithm. Direction is therefore represented by endpoint order, so a calculated reverse route cannot be a cache hit for this key.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/path/PathComputationService.java" include-lines="120-177"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/model/CalculatedPathKey.java" include-lines="13-29"/>

<code-block lang="mermaid">
classDiagram
    class PathConstraints {
        +requestedBandwidthKbps
        +setupPriority
        +holdPriority
    }
    class TunnelDirection {
        +source router ID
        +destination router ID
    }
    class CalculatedPathKey {
        +sourceGraphNodeId
        +destinationGraphNodeId
        +bandwidthBytesPerSecond
        +classType
        +algorithm
    }
    class CalculatedPath {
        +pathHops
        +eroSubobjects
        +computedTeMetric
        +expiresAt
    }
    PathConstraints --> CalculatedPathKey : bandwidth contributes
    TunnelDirection --> CalculatedPathKey : ordered endpoints contribute
    CalculatedPathKey --> CalculatedPath : indexes cached result
</code-block>

<code>CalculatedPathRegistry</code> synchronizes access to its mutable map. Lookup first sweeps expired entries, then returns an optional current path. A put replaces the value for exactly one key, and snapshots copy the remaining map. The service therefore shares reusable paths without exposing mutable registry storage to operational readers.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/registry/CalculatedPathRegistry.java" include-lines="55-143"/>

<code-block lang="mermaid">
flowchart LR
    Candidate[Pair policy candidate] --> Constraints[PathConstraints]
    Direction[TunnelDirection] --> Resolve[resolve router IDs through BGP-LS registry]
    Constraints --> Key[CalculatedPathKey]
    Resolve --> Key
    Key --> Lookup{unexpired cache entry?}
    Lookup -- yes --> Cached[CalculatedPath and ERO]
    Lookup -- no --> Rpc[constrained-path RPC]
    Rpc --> Translate[translate hops to ERO]
    Translate --> Store[cache with TTL]
    Store --> Cached
</code-block>

## Constrained-path calculation and ERO construction {id="constrained-path-calculation-and-ero-construction"}

A cache miss records the miss and a path-computation request, then constructs an operation request from the configured graph name and complete cache key. The service retries under its configured attempt budget. A confirmed response is deserialized and must report a completed calculation. Only then are path descriptions converted into ordered ERO subobjects using the direction's destination router identifier. The resulting <code>CalculatedPath</code> stores original hops, derived ERO, traffic-engineering metric, calculation time, and path-cache expiry before it returns to directional application.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/path/PathComputationService.java" include-lines="201-275"/>

<code-block lang="mermaid">
sequenceDiagram
    participant App as DirectionalLspApplicationService
    participant Path as PathComputationService
    participant Cache as CalculatedPathRegistry
    participant ODL as OdlOperationsClient
    participant Parser as response deserializer
    participant ERO as ERO translator
    App->>Path: computeOrGetCached(direction, constraints)
    Path->>Cache: findValid(key)
    alt cache hit
        Cache-->>Path: CalculatedPath
    else cache miss
        Path->>ODL: computeConstrainedPath(request)
        ODL-->>Path: confirmed response
        Path->>Parser: deserialize response
        Path->>ERO: translate completed hops
        Path->>Cache: put(key, path with TTL)
    end
    Path-->>App: CalculatedPath with ERO
</code-block>

Hard operation failures are terminal. Transport errors and unconfirmed responses consume attempts, with no intentional delay. A response that parses but is not marked completed is rejected and never cached. The duration histogram distinguishes successful computation from typed retry outcomes and invalid responses, so cache reuse, usable computation, and failure remain distinct in telemetry.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/path/PathComputationService.java" include-lines="294-371"/>

<code-block lang="mermaid">
flowchart TD
    Request[serialize constrained-path request] --> Call[operations client call]
    Call --> Outcome{confirmed?}
    Outcome -- yes --> Parse[deserialize response]
    Outcome -- hard failure --> Terminal[record failure and throw]
    Outcome -- unconfirmed or transport failure --> Attempts{budget remains?}
    Attempts -- yes --> Call
    Attempts -- no --> Terminal
    Parse --> Complete{completed?}
    Complete -- no --> Invalid[record invalid response and throw]
    Complete -- yes --> ERO[translate ordered ERO]
    ERO --> Cache[cache calculated path]
</code-block>

## Handoff to delegated LSP application {id="topology-path-handoff-to-lsp-application"}

The calculated path is not itself a policy installation. <code>DirectionalLspApplicationService</code> takes a per-direction lock, obtains the cached or newly calculated path, refreshes the current delegated-LSP record, and compares the required ERO and encoded bandwidth with that record. A valid converged LSP produces a non-failing skip record. Otherwise the service requests an update and creates an application record from post-update state. The parent consensus topic uses both directional records to decide whether active state may be installed.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/tunnel/DirectionalLspApplicationService.java" include-lines="88-151"/>

<code-block lang="mermaid">
stateDiagram-v2
    [*] --> ResolvePath
    ResolvePath --> CachedPath : cache hit
    ResolvePath --> ComputedPath : cache miss and completed RPC
    CachedPath --> CompareLsp
    ComputedPath --> CompareLsp
    CompareLsp --> Converged : ERO and bandwidth match valid LSP
    CompareLsp --> UpdateRequested : different desired state
    UpdateRequested --> ApplicationRecord : post-update record
    Converged --> ApplicationRecord : skipped-converged record
    ResolvePath --> Failure : router resolution or calculation fails
    UpdateRequested --> Failure : delegated-LSP failure
</code-block>

Topology errors are not ordinary preemption decisions. Consensus can select an incoming candidate without inspecting BGP-LS state, and preemption can permit it without sending an RPC. Only directional application reaches this graph and constrained-path work. Missing topology, a refresh that cannot produce a validated snapshot, a failed path operation, or an incomplete response prevents that direction from contributing a successful application record.

