# Application Construction — PacketIn Classification Workflow

<show-structure for="chapter" depth="3"/>

The PacketIn Classification Workflow converts an OpenFlow Plugin <code>PacketReceived</code> notification into a
time-bounded directional policy observation. Its owner is <code>SdnMplsMlWorkflowService</code>, which admits only
packets that arrive after the controller reaches readiness, carry an IPv4 frame, map to a configured ingress
direction, and can use a fresh topology. For an admitted packet, the workflow extracts four classifier features,
reuses or requests a classification, constructs directional evidence from the returned traffic policy, and delegates
that evidence to <code>PairPolicyCoordinator</code>. The coordinator owns consensus, preemption, and LSP application,
so this workflow does not directly calculate a path or invoke a PCEP update.

The parent [Application Construction](Java-Controller-Side-Application-—-Application-Construction.md#packet-notification-admission)
topic explains how the provider registers the MD-SAL listener, assigns packet correlation identifiers, contains
notification-boundary exceptions, and publishes operational state after processing. This topic follows the internal
data and decision path after that provider delegates a non-null notification to the workflow. The classifier request
and response are part of the Python API contract described by [Application Construction — Inference Module](Python-Inference-API-—-Application-Construction-Inference.md#inference-request-models)
and [Application Construction — Model Runtime](Python-Inference-API-—-Application-Construction-Model-Runtime.md#packet-validation-and-route).

## Workflow boundary and collaborators {id="packetin-workflow-boundary"}

The workflow is assembled by the provider with explicit collaborators rather than creating clients or registries
during notification handling. <code>PacketInFeatureExtractor</code> owns payload decoding and inventory-backed ingress
resolution. <code>DirectionRegistry</code> translates an ingress direction into a configured tunnel direction.
<code>ClassificationService</code> owns cache lookup and the remote inference exchange. The service-key resolver,
tunnel-pair registry, hash service, and configuration provide the identity, policy representation, and lifetime
needed to form evidence. The workflow then transfers the completed observation and the current correlation context to
the pair-policy coordinator.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/SdnMplsMlWorkflowService.java" include-lines="39-119"/>

<code-block lang="mermaid">
flowchart TD
    Packet["PacketReceived"] --> Workflow["SdnMplsMlWorkflowService"]
    Workflow --> Extractor["PacketInFeatureExtractor"]
    Extractor --> Context["PacketClassificationContext"]
    Workflow --> Directions["DirectionRegistry"]
    Workflow --> Classifier["ClassificationService"]
    Classifier --> Cache["ClassificationRegistrar"]
    Classifier --> Api["Python classification API"]
    Workflow --> ServiceKey["ServiceKeyResolver"]
    Workflow --> PairRegistry["TunnelPairRegistry"]
    Workflow --> Hasher["PairPolicyHashService"]
    Workflow --> Evidence["DirectionalPolicyEvidence"]
    Evidence --> Coordinator["PairPolicyCoordinator"]
    Coordinator --> Downstream["consensus, preemption, and LSP application"]
</code-block>

The readiness and freshness callbacks are supplied by the provider rather than owned by the workflow. The first
callback reads the provider's control-plane state. The second calls the topology refresh service through the supplied
function, allowing it to refresh or reject stale topology without exposing refresh lifecycle ownership to the
workflow. This makes admission a live decision for each notification, even though the workflow object itself is
created once during provider initialization.

| Collaborator | Input from the workflow | Result consumed by the workflow | Responsibility retained by the collaborator |
|---|---|---|---|
| <code>PacketInFeatureExtractor</code> | <code>PacketReceived</code> | optional packet context | YANG ingress traversal, inventory lookup, frame parsing |
| <code>DirectionRegistry</code> | packet context | logical and tunnel direction | ingress mapping and configured direction lookup |
| <code>ClassificationService</code> | packet context | classification plus cache-hit flag | cache selection, HTTP exchange, response conversion |
| <code>ClassificationRegistrar</code> | context and classification | cached result or miss | synchronized expiry, exact-flow and service-class views |
| <code>PairPolicyCoordinator</code> | directional evidence and workflow context | consensus and application decision | pair locking, consensus, preemption, LSP application |
| <code>SdnMplsMlMetrics</code> | outcome labels and durations | no business result | counters and histograms for the control path |

## Notification admission before classification {id="notification-admission"}

The workflow increments <code>sma_packet_in_total</code> before any guard. It then treats readiness as the first
authorization condition. When the control plane is not ready, it increments not-ready counters and returns without
extracting features. This protects the external classifier and the policy path from packets that arrive while the
provider is still discovering BGP-LS topology, validating delegated LSPs, or verifying OpenFlow bootstrap state.

After readiness, feature extraction may return empty when the Ethernet frame is too short, the typed ingress reference
is incomplete, or the OpenFlow inventory cannot resolve the indicated switch and connector. The workflow then accepts
only EtherType <code>2048</code>. EtherType <code>2054</code> is recorded as ARP, while every other absent or
unsupported context is recorded as an unsupported EtherType. These branches are normal no-action outcomes, not
classifier failures.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/SdnMplsMlWorkflowService.java" include-lines="139-194"/>

<code-block lang="mermaid">
flowchart TD
    Start["PacketReceived delegated by provider"] --> Count["increment sma_packet_in_total"]
    Count --> Ready{"controlPlaneReady?"}
    Ready -- no --> NotReady["increment not-ready counters and return"]
    Ready -- yes --> Extract["extract PacketClassificationContext"]
    Extract --> Context{"context present?"}
    Context -- no --> Unsupported["record unsupported EtherType and return"]
    Context -- yes --> IPv4{"EtherType equals 2048?"}
    IPv4 -- no, 2054 --> Arp["record ARP and return"]
    IPv4 -- no, other --> Unsupported
    IPv4 -- yes --> Resolve["resolve FlowDirection"]
    Resolve --> Known{"direction known?"}
    Known -- no --> Unknown["record unknown ingress and return"]
    Known -- yes --> Fresh{"topologyUsable?"}
    Fresh -- no --> Stale["record topology_stale and return"]
    Fresh -- yes --> Eligible["create tunnel direction and continue"]
</code-block>

A known logical direction is still insufficient if the BGP-LS view is stale. The workflow records
<code>topology_stale</code> before returning when its topology callback is false. Only then does
<code>requireTunnelDirection</code> translate the logical direction into the configured source, destination, LSP, and
direction-key representation. The result becomes the directional identity carried by the later evidence record.

### Ingress extraction and inventory identity {id="ingress-extraction"}

<code>PacketInFeatureExtractor</code> does not trust the notification's textual ingress representation as a sufficient
application identity. It traverses the typed <code>NodeConnectorRef</code> to obtain a node key and connector key, then
resolves both IDs in the current <code>OpenflowSwitchRegistry</code>. The registry supplies the configured logical
switch and connector names that the direction mapping uses. A notification from an unknown switch or connector cannot
be classified because it cannot be associated with any managed ingress.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/packet/PacketInFeatureExtractor.java" include-lines="101-196"/>

<code-block lang="mermaid">
sequenceDiagram
    participant OF as OpenFlow Plugin
    participant Extractor as PacketInFeatureExtractor
    participant Ref as NodeConnectorRef
    participant Inventory as OpenflowSwitchRegistry
    participant Config as AppConfig
    participant Context as PacketClassificationContext
    OF->>Extractor: PacketReceived
    Extractor->>Extractor: require Ethernet frame of at least 14 bytes
    Extractor->>Ref: traverse typed ingress keys
    Ref-->>Extractor: nodeId and connectorId
    Extractor->>Inventory: findByNodeId(nodeId)
    Extractor->>Inventory: findConnectorById(nodeId, connectorId)
    alt unresolved inventory identity
        Extractor-->>OF: Optional.empty()
    else resolved logical identity
        Extractor->>Extractor: parse Ethernet and IPv4 fields
        Extractor->>Config: resolveClassificationIngress(switch, connector)
        Extractor->>Context: create immutable context
        Context-->>Extractor: context
    end
</code-block>

The resulting <code>PacketClassificationContext</code> keeps source identity and decoded features together. Its
<code>receivedAt</code> value is later used as the preferred observation time for directional evidence. The context
also carries a first direction result from configuration. The workflow resolves the direction again through
<code>DirectionRegistry</code>, which preserves an already known context direction and only reconsults configuration
when the context reports <code>UNKNOWN</code>.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/model/PacketClassificationContext.java" include-lines="16-37"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/registry/DirectionRegistry.java" include-lines="61-105"/>

### Ethernet, VLAN, IPv4, and transport fields {id="frame-parsing"}

The extractor reads the Ethernet type at bytes twelve and thirteen. If the frame contains one or more complete
802.1Q or QinQ headers, it advances through each tag to the inner EtherType. For a non-IPv4 frame, a short IPv4
header, an invalid Internet Header Length, a non-initial fragment, or a payload without four transport-header bytes,
the parser returns a valid feature tuple with zero protocol and ports where those values are unavailable. The workflow
later applies the stricter IPv4 eligibility rule.

For an unfragmented IPv4 TCP or UDP packet with a complete transport header, the parser obtains the IP protocol and
both transport ports in network byte order. It deliberately does not infer ports from later IPv4 fragments because
those fragments do not begin with a transport header. This retains a precise distinction between a frame whose
protocol is known and a frame that supplies usable TCP or UDP port fields.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/packet/PacketInFeatureExtractor.java" include-lines="213-250"/>

<code-block lang="mermaid">
flowchart TD
    Ethernet["Ethernet header, bytes 12 and 13"] --> Type["read EtherType"]
    Type --> Vlan{"802.1Q or QinQ?"}
    Vlan -- yes --> Skip["skip tag control field and read inner type"]
    Skip --> Vlan
    Vlan -- no --> IPv4{"inner type is IPv4?"}
    IPv4 -- no --> Basic["return EtherType, protocol 0, ports 0"]
    IPv4 -- yes --> Header{"complete IPv4 header and valid IHL?"}
    Header -- no --> Basic
    Header -- yes --> Protocol["read IP protocol and fragment offset"]
    Protocol --> Transport{"TCP or UDP, initial fragment, four L4 bytes?"}
    Transport -- yes --> Ports["read source and destination ports"]
    Transport -- no --> ZeroPorts["return protocol with ports 0"]
</code-block>

<code>PacketFeatures</code> is the validated four-value classifier input. Its compact constructor rejects values
outside the unsigned widths of Ethernet type, IP protocol, and transport ports. It also requires zero ports for
protocols other than TCP and UDP. The extractor's parser naturally produces values within those ranges, but the record
preserves this contract at every construction site, including future callers that do not originate in frame parsing.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/model/PacketFeatures.java" include-lines="11-49"/>

<code-block lang="mermaid">
classDiagram
    class PacketClassificationContext {
        +String ingressOpenflowNodeId
        +String ingressConnectorId
        +String ingressSwitchName
        +String ingressConnectorName
        +PacketFeatures packetFeatures
        +FlowDirection direction
        +Instant receivedAt
    }
    class PacketFeatures {
        +int ethType
        +int ipProto
        +int srcPort
        +int dstPort
    }
    class FlowDirection {
        HEADEND_TO_TAILEND
        TAILEND_TO_HEADEND
        UNKNOWN
    }
    PacketClassificationContext --> PacketFeatures : contains
    PacketClassificationContext --> FlowDirection : contains
</code-block>

## Cache-aware classification {id="cache-aware-classification"}

Classification is a two-stage lookup. <code>ClassificationService</code> asks the registrar for a reusable result
before it serializes a request or makes an HTTP call. The registrar first expires stale entries using one current-time
snapshot, then checks an exact-flow cache view. An exact key contains ingress switch, ingress connector, EtherType,
IP protocol, source port, and destination port. If the exact key misses, the registrar checks a broader service-class
view keyed by ingress switch, EtherType, IP protocol, and a canonical service port. The classification service returns
a <code>ClassificationLookupResult</code> whose boolean flag lets the workflow distinguish reuse from fresh inference.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/classification/ClassificationService.java" include-lines="138-165"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/registry/ClassificationRegistrar.java" include-lines="113-151"/>

<code-block lang="mermaid">
flowchart TD
    Context["PacketClassificationContext"] --> Expire["expireOldEntries()"]
    Expire --> Exact["ClassificationCacheKey"]
    Exact --> ExactHit{"exact entry present?"}
    ExactHit -- yes --> Reuse["ClassificationLookupResult(cacheHit=true)"]
    ExactHit -- no --> Service["ServiceClassCacheKey"]
    Service --> ServiceHit{"service entry present?"}
    ServiceHit -- yes --> Reuse
    ServiceHit -- no --> Miss["fresh classifier request"]
</code-block>

The two cache keys serve different purposes. The exact key protects ingress specificity, so an observation at one
connector is not reused merely because the same switch received a similar feature tuple elsewhere. The service key
permits broader reuse for the same switch and canonical service port after that exact lookup misses. Its canonical
port selector prefers a destination port from one through 1023, then a source port in that range, and otherwise keeps
the destination port. This cache-specific rule should not be confused with the separate service-key normalization used
for pair-policy evidence.

| Cache view | Key fields | Lookup order | Reuse consequence |
|---|---|---|---|
| Exact flow | switch, connector, EtherType, protocol, source port, destination port | first | retains ingress-connector specificity |
| Service class | switch, EtherType, protocol, canonical service port | after exact miss | reuses a classification within an ingress switch's service class |

A fresh classification is written to both views. Each registrar operation is synchronized, and every lookup performs an
expiration sweep before exposing a result. Expired values are removed from both maps, and the metrics collector receives
an eviction counter for each expired exact or service record. The operational-state publisher reads immutable snapshots
from these same cache views through the provider-owned registry.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/registry/ClassificationRegistrar.java" include-lines="174-230"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/registry/ClassificationRegistrar.java" include-lines="337-360"/>

<code-block lang="mermaid">
stateDiagram-v2
    [*] --> Absent
    Absent --> ExactCached : successful classifier response
    Absent --> ServiceCached : successful classifier response
    ExactCached --> ExactCached : exact lookup before expiry
    ServiceCached --> ServiceCached : service lookup after exact miss
    ExactCached --> Expired : expiresAt precedes sweep time
    ServiceCached --> Expired : expiresAt precedes sweep time
    Expired --> Absent : remove entry
    ExactCached --> ExactCached : replace by fresh result
    ServiceCached --> ServiceCached : replace by fresh result
</code-block>

### Classifier request exchange {id="classifier-request-exchange"}

A cache miss increments both the miss counter and the external-request counter. The request serializer constructs a
JSON document containing only <code>packet_features</code> with <code>eth_type</code>, <code>ip_proto</code>,
<code>src_port</code>, and <code>dst_port</code>. It does not send ingress identity, cache keys, policy fields, or
controller topology. That focused body matches the remote inference boundary, whose request validation is owned by the
Python API rather than by the Java serializer.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/serialization/json/ClassificationRequestJsonSerializer.java" include-lines="81-99"/>

<code>ClassifierRestClient</code> constructs a blocking HTTP/1.1 <code>POST</code> using the configured endpoint and
timeout. It declares JSON in both <code>Accept</code> and <code>Content-Type</code>, sends the UTF-8 body, and returns
the complete response without deciding whether an HTTP status is acceptable. That separation lets
<code>ClassificationService</code> apply one status, metrics, and deserialization policy for every response. I/O
failures and interruptions become <code>IllegalStateException</code>, and an interrupted thread has its interrupt flag
restored before the exception propagates.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/http/ClassifierRestClient.java" include-lines="106-148"/>

<code-block lang="mermaid">
sequenceDiagram
    participant Workflow
    participant Service as ClassificationService
    participant Serializer as ClassificationRequestJsonSerializer
    participant Client as ClassifierRestClient
    participant API as Python inference API
    participant Parser as ClassificationResponseJsonDeserializer
    participant Cache as ClassificationRegistrar
    Workflow->>Service: classifyOrGetCached(context)
    Service->>Cache: find(context)
    alt cache hit
        Cache-->>Service: ClassificationResult
        Service-->>Workflow: lookup(cacheHit=true)
    else cache miss
        Service->>Serializer: serialize(packetFeatures)
        Serializer-->>Service: request JSON
        Service->>Client: classify(JSON)
        Client->>API: HTTP/1.1 POST application/json
        API-->>Client: HTTP response
        Client-->>Service: status and body
        Service->>Parser: deserialize(body)
        Parser-->>Service: ClassificationResult with expiry
        Service->>Cache: put(context, result)
        Service-->>Workflow: lookup(cacheHit=false)
    end
</code-block>

The classification service distinguishes transport, non-2xx status, deserialization, and success measurements. A
transport error records the <code>transport_error</code> histogram outcome. A non-2xx response records
<code>http_error</code> and is rejected. A deserialization failure records <code>deserialization_error</code>. None
of these paths writes to the cache. A valid result records a success duration, logs a fallback-policy warning when
the response marks one, writes the result to both cache views, and returns it as a fresh lookup.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/classification/ClassificationService.java" include-lines="204-318"/>

## Response conversion and policy-bearing models {id="response-conversion-and-models"}

The response deserializer is the Java-side validation boundary for a successful classifier body. It requires
<code>prediction</code>, <code>policy</code>, and <code>policy.path_constraints</code> nodes. Within those objects it
requires a nonblank textual <code>prediction.class_name</code>, a numeric
<code>prediction.confidence</code>, and a numeric <code>requested_bandwidth_kbps</code>. It assigns the current time
to <code>cachedAt</code> and derives <code>expiresAt</code> by adding the configured classification-cache TTL. The
result is therefore locally cacheable even though the remote response does not carry a cache expiration timestamp.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/serialization/json/ClassificationResponseJsonDeserializer.java" include-lines="110-148"/>

The deserializer supplies defaults for optional request and model identifiers, class ID, policy markings, priorities,
fallback fields, processing time, and nonnumeric probability members. It validates required presence and selected
scalar types, but the Java model records themselves do not range-check DSCP, MPLS traffic class, priorities, confidence,
or requested bandwidth. The classifier API's stricter request and response behaviours are documented in the linked
Python topics. The Java conversion nevertheless ensures that the workflow never receives a result without the
classification name, confidence, policy path-constraints object, or requested bandwidth it needs to create evidence.

<code-block lang="mermaid">
flowchart TD
    Response["2xx JSON response"] --> Root{"prediction, policy, and path_constraints present?"}
    Root -- no --> Reject["IllegalArgumentException, no cache write"]
    Root -- yes --> Scalars{"class_name text, confidence numeric, bandwidth numeric?"}
    Scalars -- no --> Reject
    Scalars -- yes --> Result["ClassificationResult"]
    Result --> Policy["TrafficPolicy and PathConstraints"]
    Result --> Expiry["cachedAt and expiresAt"]
    Policy --> Store["store in exact and service cache views"]
    Expiry --> Store
</code-block>

<code>ClassificationResult</code> combines classifier identity, class output, probabilities, policy, reported
processing duration, and local cache timestamps. <code>TrafficPolicy</code> carries profile name, DSCP, MPLS traffic
class, path constraints, and fallback metadata. <code>PathConstraints</code> supplies requested bandwidth together
with logical setup and hold priorities. The workflow consumes these values without selecting a path itself. It copies
the markings and priorities into evidence, transforms bandwidth to the PCEP representation, and leaves later
application to the pair-policy and tunnel services.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/model/ClassificationResult.java" include-lines="14-42"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/model/TrafficPolicy.java" include-lines="11-30"/>

<code-block lang="mermaid">
classDiagram
    class ClassificationLookupResult {
        +ClassificationResult classification
        +boolean cacheHit
    }
    class ClassificationResult {
        +String requestId
        +String modelName
        +String className
        +double confidence
        +Map probabilities
        +TrafficPolicy policy
        +Instant cachedAt
        +Instant expiresAt
    }
    class TrafficPolicy {
        +String profileName
        +int dscp
        +int mplsTc
        +PathConstraints pathConstraints
        +boolean policyFallback
    }
    class PathConstraints {
        +long requestedBandwidthKbps
        +int setupPriority
        +int holdPriority
    }
    ClassificationLookupResult --> ClassificationResult : contains
    ClassificationResult --> TrafficPolicy : contains
    TrafficPolicy --> PathConstraints : contains
</code-block>

## Directional evidence construction {id="directional-evidence-construction"}

The workflow starts its control-cycle timer only after admission and direction resolution succeed. It adds the
configured direction key to the active logging context, requests a cache-aware classification, and obtains a
<code>WorkflowContext</code> that preserves the provider-created workflow identifier and packet sequence for
downstream records. A cache hit records the <code>cached_hit</code> cycle outcome before the coordinator evaluates the
evidence. A cache miss records its final outcome after coordination so that the measurement can distinguish pending,
deferred, failed, and successful control cycles.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/SdnMplsMlWorkflowService.java" include-lines="197-266"/>

The evidence builder resolves a <code>ServiceKey</code> and a managed pair from the observed tunnel direction. The
service-key resolver uses a fixed known-service-port set to normalize common request and reply traffic, including DNS, HTTP,
HTTPS, and other recognized ports. ICMP uses a zero port and omits it from the normalized representation.
For an unrecognized non-ICMP service, the resolver prefers a positive destination port and otherwise the source port.
This service identity groups opposite-direction observations for consensus. It is intentionally different from the
registrar's cache key because policy consensus and classification reuse answer different questions.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/policy/ServiceKeyResolver.java" include-lines="17-80"/>

<code-block lang="mermaid">
flowchart TD
    Features["PacketFeatures"] --> Resolver["ServiceKeyResolver"]
    Resolver --> Icmp{"ICMP?"}
    Icmp -- yes --> IcmpKey["EtherType and protocol key"]
    Icmp -- no --> Known{"known destination or source port?"}
    Known -- yes --> KnownKey["select recognized service port"]
    Known -- no --> OtherKey["destination port, otherwise source"]
    IcmpKey --> ServiceKey["normalized ServiceKey"]
    KnownKey --> ServiceKey
    OtherKey --> ServiceKey
    ServiceKey --> Buckets["pair and service consensus bucket"]
</code-block>

The workflow takes the packet context's received time when present and uses the current time only as a fallback. It
looks up the managed pair from the resolved direction, converts requested kilobits per second into the PCEP Base64
float32 representation, and creates directional evidence whose expiry is the observation time plus
<code>pairConsensusEvidenceTtl</code>. The resulting evidence identifies the policy that one side observed. It does
not assert that a pair policy was accepted or that an LSP update has occurred.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/SdnMplsMlWorkflowService.java" include-lines="284-313"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/model/DirectionalPolicyEvidence.java" include-lines="13-62"/>

### Canonical evidence hash and immutable construction {id="canonical-evidence-hash"}

The evidence hash is a lowercase hexadecimal SHA-256 digest of a UTF-8 canonical string. The string begins with the
configured hash version and then appends, in a fixed named-field order, <code>pair_key</code>, the normalized
<code>service_key</code>, <code>class_name</code>, <code>profile_name</code>, <code>dscp</code>,
<code>mpls_tc</code>, requested bandwidth in kilobits per second, the PCEP Base64 bandwidth representation,
<code>setup_priority</code>, <code>hold_priority</code>, and <code>policy_schema_version</code>. The hash deliberately
does not include ingress identity, packet feature tuple, observation time, expiry, direction key, or the
<code>policyHash</code> field itself. Two observations can therefore have the same policy hash when they identify the
same pair-and-service policy, even when they were received on different sides or at different times.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/policy/PairPolicyHashService.java" include-lines="47-154"/>

The workflow creates two records because <code>DirectionalPolicyEvidence</code> is immutable and the hasher accepts a
complete evidence object. The first, partial record contains every field that participates in canonicalization and an
empty <code>policyHash</code>. <code>hashDirectionalEvidence(partial)</code> derives the digest from those policy
fields, so it cannot accidentally incorporate a hash value that does not yet exist. The workflow then constructs a
second record by copying the partial record unchanged and replacing only <code>policyHash</code> with the computed
digest. This two-step construction is a value-construction pattern, not a second classification or a second policy
decision. It makes the hash input explicit while preserving an immutable evidence DTO at every stage.

<code-block lang="mermaid">
flowchart TD
    Context["packet context and receivedAt"] --> Partial["partial DirectionalPolicyEvidence policyHash is empty"]
    Policy["classification policy fields"] --> Partial
    Pair["pair key and normalized service key"] --> Partial
    Partial --> Canonical["fixed canonical policy string"]
    Canonical --> Digest["SHA-256 of UTF-8 bytes\nlowercase hexadecimal"]
    Partial --> Final["copy every partial field"]
    Digest --> Final["set policyHash"]
    Final --> Registry["record evidence"]
</code-block>

### Pair-and-service bucket retention {id="pair-service-bucket-retention"}

The pair-policy coordinator does not block while it waits for the opposite direction. It uses a per-pair
<code>ReentrantLock</code> only to serialize one immediate record-and-evaluate operation for that pair. Inside the
lock, <code>DirectionalClassificationEvidenceRegistry</code> stores the evidence in a
<code>PairConsensusBucket</code> indexed by <code>pairKey|serviceKey.normalizedValue()</code>. The registry maps the
ingress switch to the managed pair's <code>LEFT</code> or <code>RIGHT</code> side, replaces the observation for that
side, and preserves any evidence already stored for the opposite side. The bucket is the application-level state that
persists across separate PacketIn callbacks.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/registry/DirectionalClassificationEvidenceRegistry.java" include-lines="76-92"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/model/PairConsensusBucket.java" include-lines="14-52"/>

The registry expires a whole bucket when its <code>expiresAt</code> is no longer later than the time supplied for a
record or lookup. A bucket expiration is the later of its two evidence expirations. Consequently, one side can remain
stored in a live bucket after its own evidence has expired, but the consensus service filters both sides against the
current decision time before it chooses a candidate. This separation lets the registry retain the bucket's history
until the latest validity bound while ensuring that expired directional evidence is never treated as usable consensus
input.

<code-block lang="mermaid">
sequenceDiagram
    participant Left as PacketIn from LEFT switch
    participant Coordinator as PairPolicyCoordinator
    participant Registry as directional evidence registry
    participant Bucket as PairConsensusBucket
    participant Consensus as PairPolicyConsensusService
    participant Right as later PacketIn from RIGHT switch
    Left->>Coordinator: handleEvidence(left evidence)
    Coordinator->>Registry: recordEvidence(left evidence)
    Registry->>Bucket: store left slot, right remains empty
    Registry-->>Coordinator: updated bucket
    Coordinator->>Consensus: evaluate(bucket, active policy, now)
    Consensus-->>Coordinator: PENDING_ONE_SIDE, no candidate
    Coordinator-->>Left: immediate pending decision
    Right->>Coordinator: handleEvidence(right evidence)
    Coordinator->>Registry: recordEvidence(right evidence)
    Registry->>Bucket: preserve left slot and store right slot
    Registry-->>Coordinator: updated bucket
    Coordinator->>Consensus: evaluate(bucket, active policy, now)
    Consensus-->>Coordinator: match, conflict selection, or no candidate
</code-block>

The coordinator evaluates the returned bucket in the same call that records evidence. The consensus service first
filters each slot by <code>expiresAt().isAfter(now)</code>. If either usable slot is absent, it returns
<code>PENDING_ONE_SIDE</code> with no candidate. If both are usable and their policy hashes match, it creates a
candidate from that policy. If hashes differ, it compares setup priority and then hold priority, preferring the lower
value. An equal-priority conflict either preserves an active policy whose hash matches one side or returns an
unresolved decision. The later PacketIn is therefore what completes the wait. There is no sleeping thread, condition
variable, polling loop, or scheduled consensus task in this path.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/policy/PairPolicyConsensusService.java" include-lines="36-81"/>

<code-block lang="mermaid">
stateDiagram-v2
    [*] --> Classified
    Classified --> Identified : resolve ServiceKey and tunnel pair
    Identified --> Encoded : translate bandwidth and copy policy fields
    Encoded --> Hashed : canonical policy hash
    Hashed --> Recorded : submit to PairPolicyCoordinator
    Recorded --> Pending : opposite direction absent
    Recorded --> Candidate : consensus selects policy
    Pending --> Expired : evidence TTL elapses
    Candidate --> Expired : evidence TTL elapses
</code-block>

## Transfer to pair-policy coordination {id="pair-policy-transfer"}

<code>PairPolicyCoordinator.handleEvidence</code> is the workflow's final synchronous call. It acquires the lock for
the evidence pair key, records the observation, obtains active policy state, and performs the immediate consensus
evaluation before releasing that lock in a <code>finally</code> block. A missing candidate returns to the workflow
without waiting for another notification. The workflow labels <code>PENDING_ONE_SIDE</code> as
<code>pending_consensus</code> and any other no-candidate result as <code>deferred</code>. If a candidate exists, the
workflow checks returned directional-application statuses for a <code>FAILED</code> prefix and labels the cycle as
failed, deferred, or successful. The application and preemption rules that follow candidate selection remain owned by
the pair-policy coordination mechanism.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/policy/PairPolicyCoordinator.java" include-lines="114-189"/>

A runtime failure in extraction, cache lookup, serialization, transport, response conversion, evidence construction, or
coordination reaches the workflow's catch block. If no outcome has already been recorded, that block emits a failed
control-cycle counter and histogram observation, then rethrows. The provider's notification boundary contains that
exception and publishes state, as described in the parent construction topic. The workflow therefore preserves both a
local control-cycle measurement and the provider-level rule that one failed packet does not end MD-SAL notification
delivery.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/SdnMplsMlWorkflowService.java" include-lines="217-265"/>

<code-block lang="mermaid">
sequenceDiagram
    participant Workflow
    participant Evidence as DirectionalPolicyEvidence
    participant Coordinator as PairPolicyCoordinator
    participant Provider as SDN_MPLS_ML_Provider
    participant State as operational publisher
    Workflow->>Evidence: build and hash
    Workflow->>Coordinator: handleEvidence(evidence, workflowContext)
    alt no candidate
        Coordinator-->>Workflow: pending or deferred decision
        Workflow->>Workflow: record control-cycle outcome
    else candidate with applications
        Coordinator-->>Workflow: decision and application records
        Workflow->>Workflow: record success, failed, or deferred
    else runtime failure
        Coordinator-->>Workflow: RuntimeException
        Workflow->>Workflow: record failed when unreported
        Workflow-->>Provider: propagate RuntimeException
        Provider->>State: publish in finally
    end
</code-block>

## Control-cycle observability {id="control-cycle-observability"}

The workflow records a counter named <code>sma_control_cycle_total</code> with <code>outcome</code> and
<code>class_name</code> labels. When a monotonic start time exists, it also records
<code>sma_control_cycle_duration_seconds</code> using the same labels. Admission failures before the timer starts
remain visible through their dedicated packet counters or the topology-stale outcome. The classifier service adds
cache-hit, cache-miss, request, failure, and round-trip-duration metrics, which separate an inference problem from a
decision problem that occurs after a valid result is available.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/SdnMplsMlWorkflowService.java" collapsible="true"/>

<code-block lang="mermaid">
flowchart TD
    Admission["admission result"] --> PacketMetrics["packet and ignore counters"]
    Classification["cache or external classification"] --> ClassMetrics["cache, request, failure, round-trip metrics"]
    Decision["coordinator decision"] --> Cycle["sma_control_cycle_total"]
    Cycle --> Labels["outcome and class_name labels"]
    Cycle --> Duration["sma_control_cycle_duration_seconds when timer started"]
    PacketMetrics --> Scrape["/csa/metrics"]
    ClassMetrics --> Scrape
    Duration --> Scrape
</code-block>

The metrics do not turn a classification into a controller action. They show where the workflow stopped and whether a
fresh classifier exchange occurred, while the state publisher provides the broader controller projection after the
provider finishes notification handling. Together, the two views distinguish packets rejected before inference,
reused classifications, remote classifier failures, evidence awaiting the opposite direction, and completed
pair-policy decisions.
