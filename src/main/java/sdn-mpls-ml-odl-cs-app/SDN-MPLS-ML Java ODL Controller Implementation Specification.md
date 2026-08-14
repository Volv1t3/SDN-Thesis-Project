# SDN-MPLS-ML Java ODL Controller Implementation Specification

## 1. Objective

Implement the initial Java application scaffolding for the SDN-MPLS-ML controller workflow using the validated **single active PCC tunnel per direction** model.

The application must:

1. Receive `PacketReceived` notifications from ODL/OpenFlow.
2. Extract first-packet flow features.
3. Cache and reuse previous classifications.
4. Call the Python ML classifier only on cache miss.
5. Map classification policy constraints into MPLS-TE path computation input.
6. Discover BGP-LS graph node IDs dynamically from RESTCONF topology data.
7. Compute constrained paths through ODL.
8. Create an auto-PCC tunnel only when the direction has no reusable tunnel.
9. Discover the generated `tunnel-teXYZ` interface from Cisco XR operational data.
10. Patch the discovered tunnel with `ipv4 unnumbered Loopback0`.
11. Cache the symbolic name, tunnel interface, tunnel ID, and PCEP identifiers.
12. Use `update-lsp` for later classifications instead of repeated `add-lsp`.

The validated implementation mode is:

```text
TUNNEL_CREATION_MODE=SINGLE_TUNNEL_PCC
```

The future/reserved mode is:

```text
TUNNEL_CREATION_MODE=MULTI_TUNNEL_PCC
```

`MULTI_TUNNEL_PCC` must be present as a configuration enum but should not be enabled as production behavior yet.

---

## 2. Architecture Decision

### 2.1 Accepted Model

Use one reusable PCC-created tunnel per direction:

```text
LSR1 → LSR4: one active PCC tunnel
LSR4 → LSR1: one active PCC tunnel
```

The first classification for a direction may create and configure the tunnel.

Later classifications should update the existing tunnel path using `update-lsp`.

### 2.2 Rejected Model

Do not implement the following as the primary path:

```text
per-class VLAN
→ per-class VRF
→ cross-VRF route
→ default-VRF Tunnel-TE
```

Reason:

The lab validated that VLAN ingress into the VRF works and that cross-VRF static routes can be installed in the VRF RIB, but XRv did not program usable CEF/FIB forwarding into the default-VRF TE tunnel. Packets reached the `.160` VRF subinterface but never entered the TE tunnel.

---

## 3. Required Environment Variables

## 3.1 Application Mode

```text
TUNNEL_CREATION_MODE=SINGLE_TUNNEL_PCC
```

Allowed values:

```text
SINGLE_TUNNEL_PCC
MULTI_TUNNEL_PCC
```

Default:

```text
SINGLE_TUNNEL_PCC
```

Implementation requirement:

```java
enum TunnelCreationMode {
    SINGLE_TUNNEL_PCC,
    MULTI_TUNNEL_PCC
}
```

If the mode is missing, use `SINGLE_TUNNEL_PCC`.

If the value is unknown, fail application startup.

---

## 3.2 ODL Base URLs

```text
ODL_RESTCONF_DATA_BASE_URL=http://172.21.121.100:8182/restconf/data
ODL_RESTS_OPERATIONS_BASE_URL=http://172.21.121.100:8181/rests/operations
```

Usage:

```text
ODL_RESTCONF_DATA_BASE_URL:
  Used for RESTCONF data reads/writes:
    - network-topology discovery
    - BGP-LS topology discovery
    - NETCONF-mounted Cisco XR tunnel-head polling
    - NETCONF-mounted Cisco XR interface patching

ODL_RESTS_OPERATIONS_BASE_URL:
  Used for RPC operations:
    - path-computation:get-constrained-path
    - network-topology-pcep:add-lsp
    - network-topology-pcep:update-lsp
```

Do not use `/rests/data` for topology discovery.

Do not use `/restconf/data` for RPC operations.

---

## 3.3 ODL Authentication

```text
ODL_USERNAME=admin
ODL_PASSWORD=admin
```

Use HTTP Basic Authentication for all ODL HTTP clients.

---

## 3.4 Classifier Endpoint

```text
CLASSIFIER_BASE_URL=http://127.0.0.1:33761
CLASSIFIER_CLASSIFY_PATH=/api/v1/classify
```

Resolved endpoint:

```text
http://127.0.0.1:33761/api/v1/classify
```

---

## 3.5 Topology Identifiers

```text
ODL_NETCONF_TOPOLOGY_ID=topology-netconf
ODL_BGPLS_TOPOLOGY_ID=sma-bgp-linkstate-topology
ODL_PCEP_TOPOLOGY_ID=pcep-topology
ODL_PATH_COMPUTATION_GRAPH_NAME=ted://sma-bgp-linkstate-topology
```

Important distinction:

```text
RESTCONF topology ID:
  sma-bgp-linkstate-topology

Path computation graph name:
  ted://sma-bgp-linkstate-topology
```

---

## 3.6 Tunnel Endpoint Router IDs

```text
SMA_HEADEND_RID=11.11.11.11
SMA_TAILEND_RID=14.14.14.14
```

Forward path:

```text
source RID = SMA_HEADEND_RID
destination RID = SMA_TAILEND_RID
```

Reverse path:

```text
source RID = SMA_TAILEND_RID
destination RID = SMA_HEADEND_RID
```

The Java app must resolve these router IDs into graph node IDs dynamically from BGP-LS topology data.

---

## 3.7 Tunnel Endpoint NETCONF Node Names

```text
SMA_HEADEND_NETCONF_NODE=sma-xrv-lsr1-alpha
SMA_TAILEND_NETCONF_NODE=sma-xrv-lsr4-delta
```

Forward tunnel creation/discovery uses:

```text
headend NETCONF node = SMA_HEADEND_NETCONF_NODE
```

Reverse tunnel creation/discovery uses:

```text
headend NETCONF node = SMA_TAILEND_NETCONF_NODE
```

---

## 3.8 PCC Node Identifiers

```text
SMA_HEADEND_PCC_NODE=pcc://10.100.10.1
SMA_TAILEND_PCC_NODE=pcc://10.100.40.1
```

Forward PCEP RPC:

```text
node = SMA_HEADEND_PCC_NODE
```

Reverse PCEP RPC:

```text
node = SMA_TAILEND_PCC_NODE
```

---

## 3.9 Optional Compact Endpoint Mapping

The application may alternatively support:

```text
SMA_TUNNEL_ENDPOINTS=sma-xrv-lsr1-alpha|11.11.11.11|pcc://10.100.10.1;sma-xrv-lsr4-delta|14.14.14.14|pcc://10.100.40.1
```

Format:

```text
netconfNodeName|routerId|pccNode
```

Parsed records:

```text
sma-xrv-lsr1-alpha:
  routerId: 11.11.11.11
  pccNode: pcc://10.100.10.1

sma-xrv-lsr4-delta:
  routerId: 14.14.14.14
  pccNode: pcc://10.100.40.1
```

If both compact and individual variables exist, individual variables should take precedence unless explicitly configured otherwise.

---

## 3.10 Cache TTL Configuration

```text
CLASSIFICATION_CACHE_TTL_SECONDS=3600
PATH_CACHE_TTL_SECONDS=60
TOPOLOGY_CACHE_TTL_SECONDS=300
TUNNEL_DISCOVERY_TIMEOUT_SECONDS=120
TUNNEL_DISCOVERY_INITIAL_DELAY_MS=500
TUNNEL_DISCOVERY_MAX_DELAY_MS=5000
HTTP_REQUEST_TIMEOUT_SECONDS=10
```

Recommended defaults:

```text
classification cache: long lived
path cache: short lived
topology cache: medium lived
tunnel registry: persistent in memory for app lifecycle
```

Path cache must expire quickly because TE unreserved bandwidth can change.

---

## 4. Required Java Packages

Recommended package layout:

```text
com.sma.sdn.config
com.sma.sdn.packet
com.sma.sdn.classification
com.sma.sdn.topology
com.sma.sdn.path
com.sma.sdn.tunnel
com.sma.sdn.http
com.sma.sdn.registry
com.sma.sdn.serialization.json
com.sma.sdn.serialization.xml
com.sma.sdn.model
com.sma.sdn.util
```

---

## 5. Core Data Models

## 5.1 PacketFeatures

```java
public record PacketFeatures(
    int ethType,
    int ipProto,
    int srcPort,
    int dstPort
) {}
```

Required behavior:

- `ethType` should be `2048` for IPv4.
- `ipProto` should be protocol number:
  - TCP = `6`
  - UDP = `17`
  - ICMP = `1`
- If no L4 port exists, use `0` or `-1`, but use one convention consistently.

---

## 5.2 PacketClassificationContext

```java
public record PacketClassificationContext(
    String ingressOpenflowNodeId,
    String ingressConnectorId,
    String ingressSwitchName,
    String ingressConnectorName,
    PacketFeatures packetFeatures,
    FlowDirection direction,
    Instant receivedAt
) {}
```

Purpose:

Carries all packet-derived metadata required for classification and direction resolution.

---

## 5.3 FlowDirection

```java
public enum FlowDirection {
    HEADEND_TO_TAILEND,
    TAILEND_TO_HEADEND,
    UNKNOWN
}
```

Resolution rule:

```text
Packet from OVS PE1 / echo / golf side:
  HEADEND_TO_TAILEND

Packet from OVS PE2 / foxtrot / hotel side:
  TAILEND_TO_HEADEND
```

The mapping between OpenFlow node IDs and logical switch names must be configurable or discoverable.

---

## 5.4 ClassificationCacheKey

Use exact first-packet features as the primary cache key:

```java
public record ClassificationCacheKey(
    String ingressSwitchName,
    String ingressConnectorName,
    int ethType,
    int ipProto,
    int srcPort,
    int dstPort
) {}
```

This avoids accidentally reusing classifications across different ingress switches.

---

## 5.5 ServiceClassCacheKey

Use a normalized key as a secondary lookup to reduce duplicate classification:

```java
public record ServiceClassCacheKey(
    String ingressSwitchName,
    int ethType,
    int ipProto,
    int canonicalServicePort
) {}
```

Canonical service port logic:

```java
int canonicalServicePort(PacketFeatures f) {
    if (f.dstPort() > 0 && f.dstPort() <= 1023) {
        return f.dstPort();
    }
    if (f.srcPort() > 0 && f.srcPort() <= 1023) {
        return f.srcPort();
    }
    return f.dstPort();
}
```

Examples:

```text
client → DNS server:
  src_port=53000
  dst_port=53
  canonicalServicePort=53

DNS server → client:
  src_port=53
  dst_port=53000
  canonicalServicePort=53
```

---

## 5.6 ClassificationResult

```java
public record ClassificationResult(
    String requestId,
    String modelName,
    int classId,
    String className,
    double confidence,
    Map<String, Double> probabilities,
    TrafficPolicy policy,
    double processingTimeMs,
    Instant cachedAt,
    Instant expiresAt
) {}
```

---

## 5.7 TrafficPolicy

```java
public record TrafficPolicy(
    String profileName,
    int dscp,
    int mplsTc,
    PathConstraints pathConstraints,
    boolean policyFallback,
    String policyFallbackReason
) {}
```

---

## 5.8 PathConstraints

```java
public record PathConstraints(
    long requestedBandwidthKbps,
    int setupPriority,
    int holdPriority
) {}
```

Bandwidth conversion requirement:

```java
long bandwidthBytesPerSecond = requestedBandwidthKbps * 1000L / 8L;
```

Example:

```text
10000 kbps → 1250000 bytes/second
```

Do not convert to Mbps for the path-computation XML.

---

## 5.9 BgpLsTopologyNode

```java
public record BgpLsTopologyNode(
    String topologyId,
    String nodeId,
    String routerId,
    String teRouterIdIpv4,
    long graphNodeId
) {}
```

Example:

```text
node-id:
  bgpls://Ospf:36/type=node&as=65000&domain=0&area=0&router=185273099

router-id:
  11.11.11.11

graphNodeId:
  185273099
```

---

## 5.10 TunnelEndpoint

```java
public record TunnelEndpoint(
    String logicalName,
    String netconfNodeName,
    String routerId,
    String pccNode
) {}
```

Example:

```java
new TunnelEndpoint(
    "lsr1",
    "sma-xrv-lsr1-alpha",
    "11.11.11.11",
    "pcc://10.100.10.1"
);
```

---

## 5.11 TunnelDirection

```java
public record TunnelDirection(
    String directionKey,
    TunnelEndpoint source,
    TunnelEndpoint destination
) {}
```

Examples:

```text
directionKey=lsr1_to_lsr4
source=sma-xrv-lsr1-alpha / 11.11.11.11 / pcc://10.100.10.1
destination=sma-xrv-lsr4-delta / 14.14.14.14 / pcc://10.100.40.1
```

```text
directionKey=lsr4_to_lsr1
source=sma-xrv-lsr4-delta / 14.14.14.14 / pcc://10.100.40.1
destination=sma-xrv-lsr1-alpha / 11.11.11.11 / pcc://10.100.10.1
```

---

## 5.12 CalculatedPath

```java
public record CalculatedPath(
    String graphName,
    long sourceGraphNodeId,
    long destinationGraphNodeId,
    long bandwidthBytesPerSecond,
    int classType,
    String algorithm,
    List<PathHop> pathHops,
    List<EroSubobject> eroSubobjects,
    int computedTeMetric,
    Instant calculatedAt,
    Instant expiresAt
) {}
```

---

## 5.13 PathHop

```java
public record PathHop(
    String localIpv4,
    String remoteIpv4
) {}
```

From XML:

```xml
<path-description>
    <remote-ipv4>10.0.12.2</remote-ipv4>
    <ipv4>10.0.12.1</ipv4>
</path-description>
```

Map to:

```java
new PathHop("10.0.12.1", "10.0.12.2");
```

---

## 5.14 EroSubobject

```java
public record EroSubobject(
    boolean loose,
    String ipPrefix
) {}
```

Example:

```java
new EroSubobject(false, "10.0.12.2/32");
```

The final destination loopback must be appended:

```text
14.14.14.14/32
```

or reverse:

```text
11.11.11.11/32
```

---

## 5.15 TunnelRecord

```java
public record TunnelRecord(
    String directionKey,
    String symbolicTunnelName,
    String sourceNetconfNodeName,
    String destinationNetconfNodeName,
    String sourceRouterId,
    String destinationRouterId,
    String pccNode,
    String tunnelInterfaceName,
    Integer tunnelId,
    Long plspId,
    String signalledName,
    String operationalState,
    String pathState,
    String signallingState,
    String failReason,
    boolean loopback0Patched,
    boolean usableForUpdate,
    Instant createdAt,
    Instant updatedAt
) {}
```

Minimum required cached fields:

```text
directionKey
symbolicTunnelName
pccNode
tunnelInterfaceName
tunnelId
plspId if available
sourceRouterId
destinationRouterId
loopback0Patched
operational state fields
```

The `plspId` is important for `update-lsp` if the RPC requires it.

---

## 6. Required Registries and Caches

## 6.1 ClassificationRegistrar

Class:

```java
public final class ClassificationRegistrar
```

Responsibilities:

1. Store exact packet classification cache.
2. Store normalized service-port classification cache.
3. Return cached classification before calling ML API.
4. Expire old entries.
5. Keep switch-local maps so PE1 and PE2 classifications do not collide.

Internal structure:

```java
private final Map<String, Map<ClassificationCacheKey, ClassificationResult>> exactBySwitch;
private final Map<String, Map<ServiceClassCacheKey, ClassificationResult>> serviceBySwitch;
```

Required methods:

```java
Optional<ClassificationResult> find(PacketClassificationContext context);

void put(PacketClassificationContext context, ClassificationResult result);

void expireOldEntries();

int size();

void clear();
```

Lookup order:

```text
1. exact key lookup
2. normalized service-class key lookup
3. cache miss
```

On cache miss:

```text
call ClassifierService
then store in both exact and normalized maps
```

---

## 6.2 BgpLsNodeRegistry

Class:

```java
public final class BgpLsNodeRegistry
```

Internal structure:

```java
private final Map<String, BgpLsTopologyNode> byRouterId;
```

Required methods:

```java
void replaceAll(Collection<BgpLsTopologyNode> nodes);

boolean containsRouterId(String routerId);

BgpLsTopologyNode requireByRouterId(String routerId);

long resolveGraphNodeIdByRouterId(String routerId);

Map<String, BgpLsTopologyNode> snapshot();
```

Deduplication rule:

```text
logical key = router-id
stored value = graphNodeId parsed from node-id router=<value>
```

If the same router ID appears multiple times with the same numeric graph node ID, keep one.

If the same router ID appears with conflicting numeric graph node IDs, throw a topology consistency exception.

Expected lab mapping:

```text
11.11.11.11 → 185273099
12.12.12.12 → 202116108
13.13.13.13 → 218959117
14.14.14.14 → 235802126
```

---

## 6.3 CalculatedPathRegistry

Class:

```java
public final class CalculatedPathRegistry
```

Key:

```java
public record CalculatedPathKey(
    long sourceGraphNodeId,
    long destinationGraphNodeId,
    long bandwidthBytesPerSecond,
    int classType,
    String algorithm
) {}
```

Internal structure:

```java
private final Map<CalculatedPathKey, CalculatedPath> paths;
```

Required methods:

```java
Optional<CalculatedPath> findValid(CalculatedPathKey key);

void put(CalculatedPathKey key, CalculatedPath path);

void expireOldEntries();

void clear();
```

TTL:

```text
PATH_CACHE_TTL_SECONDS
```

Default:

```text
60 seconds
```

Reason:

The TE topology may change due to tunnel reservations, topology changes, or RSVP state.

---

## 6.4 TunnelRegistry

Class:

```java
public final class TunnelRegistry
```

Key:

```text
directionKey
```

Examples:

```text
lsr1_to_lsr4
lsr4_to_lsr1
```

Internal structure:

```java
private final Map<String, TunnelRecord> byDirection;
private final Map<String, TunnelRecord> bySymbolicName;
private final Map<String, TunnelRecord> byTunnelInterfaceName;
```

Required methods:

```java
Optional<TunnelRecord> findByDirection(String directionKey);

Optional<TunnelRecord> findBySymbolicName(String symbolicName);

Optional<TunnelRecord> findByTunnelInterfaceName(String tunnelInterfaceName);

void upsert(TunnelRecord record);

boolean hasUsableTunnel(String directionKey);

TunnelRecord requireUsableTunnel(String directionKey);

void markLoopbackPatched(String directionKey);

void updateOperationalState(String directionKey, TunnelOperationalSnapshot snapshot);
```

Tunnel is reusable when:

```text
symbolicTunnelName exists
AND tunnelInterfaceName exists
AND loopback0Patched == true
AND operationalState == operational-up
AND signallingState == connected
AND failReason is absent or non-fatal
```

---

## 6.5 DirectionRegistry

Class:

```java
public final class DirectionRegistry
```

Purpose:

Map ingress OpenFlow node/connector to the tunnel direction.

Example mappings:

```text
sma-ovs-pe1-echo / host-golf → lsr1_to_lsr4
sma-ovs-pe2-foxtrot / host-hotel → lsr4_to_lsr1
```

Required methods:

```java
FlowDirection resolve(PacketClassificationContext context);

TunnelDirection requireTunnelDirection(FlowDirection direction);
```

This can be loaded from environment variables or a static configuration file.

---

## 7. Required HTTP Clients

## 7.1 OdlRestconfDataClient

Class:

```java
public final class OdlRestconfDataClient
```

Base URL:

```text
ODL_RESTCONF_DATA_BASE_URL
```

Responsibilities:

```text
GET topology list
GET BGP-LS topology
GET tunnel-heads from NETCONF mount
PUT tunnel interface Loopback0 patch
```

Required methods:

```java
String getNetworkTopologyListXml();

String getBgpLsTopologyXml(String topologyId);

String getTunnelHeadsXml(String netconfNodeName);

HttpResponse<String> putTunnelUnnumberedLoopback0(String netconfNodeName, String tunnelInterfaceName, String xmlBody);
```

All methods must set:

```http
Accept: application/xml
Authorization: Basic <admin:admin>
```

PUT methods must also set:

```http
Content-Type: application/xml
```

---

## 7.2 OdlOperationsClient

Class:

```java
public final class OdlOperationsClient
```

Base URL:

```text
ODL_RESTS_OPERATIONS_BASE_URL
```

Responsibilities:

```text
POST path-computation:get-constrained-path
POST network-topology-pcep:add-lsp
POST network-topology-pcep:update-lsp
```

Required methods:

```java
HttpResponse<String> computeConstrainedPath(String xmlBody);

HttpResponse<String> addLsp(String xmlBody);

HttpResponse<String> updateLsp(String xmlBody);
```

All methods must set:

```http
Accept: application/xml
Content-Type: application/xml
Authorization: Basic <admin:admin>
```

---

## 7.3 ClassifierRestClient

Class:

```java
public final class ClassifierRestClient
```

Base URL:

```text
CLASSIFIER_BASE_URL
```

Path:

```text
CLASSIFIER_CLASSIFY_PATH
```

Required method:

```java
HttpResponse<String> classify(String jsonBody);
```

Headers:

```http
Accept: application/json
Content-Type: application/json
```

---

## 8. Endpoint Inventory

## 8.1 List Network Topologies

Purpose:

Verify the available topology IDs at startup.

Endpoint:

```http
GET {ODL_RESTCONF_DATA_BASE_URL}/network-topology:network-topology?content=nonconfig
```

Concrete lab endpoint:

```http
GET http://172.21.121.100:8182/restconf/data/network-topology:network-topology?content=nonconfig
```

Expected topology IDs:

```text
flow:1
topology-netconf
sma-bgp-linkstate-topology
```

Deserializer:

```text
NetworkTopologyListXmlDeserializer
```

---

## 8.2 Read BGP-LS / TED Topology

Purpose:

Resolve router IDs to path-computation graph node IDs.

Endpoint:

```http
GET {ODL_RESTCONF_DATA_BASE_URL}/network-topology:network-topology/topology={ODL_BGPLS_TOPOLOGY_ID}?content=nonconfig
```

Concrete lab endpoint:

```http
GET http://172.21.121.100:8182/restconf/data/network-topology:network-topology/topology=sma-bgp-linkstate-topology?content=nonconfig
```

Deserializer:

```text
BgpLsTopologyXmlDeserializer
```

Relevant XML fields:

```text
/topology/topology-id
/topology/node/node-id
/topology/node/igp-node-attributes/router-id
/topology/node/igp-node-attributes/ospf-node-attributes/ted/te-router-id-ipv4
```

Graph node ID extraction:

```text
parse numeric value from node-id query parameter router=<number>
```

Example:

```text
node-id:
  bgpls://Ospf:36/type=node&as=65000&domain=0&area=0&router=185273099

router-id:
  11.11.11.11

resolved graphNodeId:
  185273099
```

---

## 8.3 Classify Packet

Endpoint:

```http
POST {CLASSIFIER_BASE_URL}{CLASSIFIER_CLASSIFY_PATH}
```

Concrete lab endpoint:

```http
POST http://127.0.0.1:33761/api/v1/classify
```

Request serializer:

```text
ClassificationRequestJsonSerializer
```

Response deserializer:

```text
ClassificationResponseJsonDeserializer
```

Request JSON:

```json
{
  "packet_features": {
    "eth_type": 2048,
    "ip_proto": 17,
    "src_port": 53000,
    "dst_port": 53
  }
}
```

Required response fields:

```text
request_id
model_name
prediction.class_id
prediction.class_name
prediction.confidence
probabilities
policy.profile_name
policy.dscp
policy.mpls_tc
policy.path_constraints.requested_bandwidth_kbps
policy.path_constraints.setup_priority
policy.path_constraints.hold_priority
policy.policy_fallback
policy.policy_fallback_reason
processing_time_ms
```

---

## 8.4 Compute Constrained Path

Endpoint:

```http
POST {ODL_RESTS_OPERATIONS_BASE_URL}/path-computation:get-constrained-path
```

Concrete lab endpoint:

```http
POST http://172.21.121.100:8181/rests/operations/path-computation:get-constrained-path
```

Request serializer:

```text
PathComputationRequestXmlSerializer
```

Response deserializer:

```text
PathComputationResponseXmlDeserializer
```

Request XML:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<input xmlns="urn:opendaylight:params:xml:ns:yang:path:computation">
    <graph-name>${graphName}</graph-name>
    <source>${sourceGraphNodeId}</source>
    <destination>${destinationGraphNodeId}</destination>
    <constraints>
        <address-family>ipv4</address-family>
        <bandwidth>${bandwidthBytesPerSecond}</bandwidth>
        <class-type>${classType}</class-type>
    </constraints>
    <algorithm>cspf</algorithm>
</input>
```

Variables:

```text
graphName:
  ODL_PATH_COMPUTATION_GRAPH_NAME
  example: ted://sma-bgp-linkstate-topology

sourceGraphNodeId:
  resolved from BgpLsNodeRegistry

destinationGraphNodeId:
  resolved from BgpLsNodeRegistry

bandwidthBytesPerSecond:
  requested_bandwidth_kbps * 1000 / 8

classType:
  0 for current lab

algorithm:
  cspf
```

Success condition:

```text
HTTP 200
AND <status>completed</status>
AND path-description count > 0
```

Response XML example:

```xml
<output xmlns="urn:opendaylight:params:xml:ns:yang:path:computation">
    <path-description>
        <remote-ipv4>10.0.12.2</remote-ipv4>
        <ipv4>10.0.12.1</ipv4>
    </path-description>
    <path-description>
        <remote-ipv4>10.0.22.2</remote-ipv4>
        <ipv4>10.0.22.1</ipv4>
    </path-description>
    <status>completed</status>
    <computed-te-metric>2</computed-te-metric>
</output>
```

---

## 8.5 Create PCC Tunnel

Endpoint:

```http
POST {ODL_RESTS_OPERATIONS_BASE_URL}/network-topology-pcep:add-lsp
```

Concrete lab endpoint:

```http
POST http://172.21.121.100:8181/rests/operations/network-topology-pcep:add-lsp
```

Request serializer:

```text
AddLspRequestXmlSerializer
```

Response deserializer:

```text
AddLspResponseXmlDeserializer
```

Request XML:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<input xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
    <node>${pccNode}</node>
    <name>${symbolicTunnelName}</name>
    <arguments>
        <path-setup-type>
            <pst>rsvp-te</pst>
        </path-setup-type>

        <lsp>
            <lsp-flags>
                <delegate>true</delegate>
                <administrative>true</administrative>
            </lsp-flags>
        </lsp>

        <bandwidth>
            <bandwidth>${bandwidthBytesPerSecondFloat}</bandwidth>
        </bandwidth>

        <endpoints-obj>
            <ipv4>
                <source-ipv4-address>${sourceRouterId}</source-ipv4-address>
                <destination-ipv4-address>${destinationRouterId}</destination-ipv4-address>
            </ipv4>
        </endpoints-obj>

        <ero>
            ${eroSubobjects}
        </ero>
    </arguments>

    <network-topology-ref xmlns:nt="urn:TBD:params:xml:ns:yang:network-topology">
        /nt:network-topology/nt:topology[nt:topology-id="${pcepTopologyId}"]
    </network-topology-ref>
</input>
```

Success condition:

```text
HTTP 200
AND output has no failure
```

or:

```text
HTTP 200
AND <failure>no-ack</failure>
```

`no-ack` is provisional success in this lab and must trigger tunnel discovery polling, not immediate failure.

---

## 8.6 Discover Generated Auto-PCC Tunnel Interface

Endpoint:

```http
GET {ODL_RESTCONF_DATA_BASE_URL}/network-topology:network-topology/topology={ODL_NETCONF_TOPOLOGY_ID}/node={netconfNodeName}/yang-ext:mount/Cisco-IOS-XR-mpls-te-oper:mpls-te/p2p-p2mp-tunnel/tunnel-heads?content=all
```

Concrete LSR1 endpoint:

```http
GET http://172.21.121.100:8182/restconf/data/network-topology:network-topology/topology=topology-netconf/node=sma-xrv-lsr1-alpha/yang-ext:mount/Cisco-IOS-XR-mpls-te-oper:mpls-te/p2p-p2mp-tunnel/tunnel-heads?content=all
```

Concrete LSR4 endpoint:

```http
GET http://172.21.121.100:8182/restconf/data/network-topology:network-topology/topology=topology-netconf/node=sma-xrv-lsr4-delta/yang-ext:mount/Cisco-IOS-XR-mpls-te-oper:mpls-te/p2p-p2mp-tunnel/tunnel-heads?content=all
```

Deserializer:

```text
TunnelHeadsResponseXmlDeserializer
```

Required extraction fields per tunnel head:

```text
tunnel-name
tunnel-interface-name
tunnel-id
signaled-name
config/signaled-name
auto-pcc/symbolic-name
auto-pcc/plspid or equivalent PCEP ID if present
is-auto-pcc
operational-state
fail-reason
destination-state
signalling-status
path information if present
```

Matching order:

```text
1. auto-pcc/symbolic-name == requested symbolicTunnelName
2. signaled-name == requested symbolicTunnelName
3. config/signaled-name == requested symbolicTunnelName
4. tunnel-interface-name == known tunnelInterfaceName, only after registry exists
```

Success condition for discovery:

```text
HTTP 200
AND matching tunnel-head exists
AND tunnel interface can be extracted
```

Success condition for operational usability:

```text
matching tunnel-head exists
AND operational-state == operational-up
AND signalling status == connected
AND path valid if path field exists
AND fail-reason is absent or non-fatal
```

---

## 8.7 Patch Tunnel Interface with Loopback0

Purpose:

Configure the auto-created tunnel interface with:

```text
ipv4 unnumbered Loopback0
```

Endpoint:

```http
PUT {ODL_RESTCONF_DATA_BASE_URL}/network-topology:network-topology/topology={ODL_NETCONF_TOPOLOGY_ID}/node={netconfNodeName}/yang-ext:mount/Cisco-IOS-XR-ifmgr-cfg:interface-configurations/interface-configuration=act,{tunnelInterfaceName}
```

Concrete example:

```http
PUT http://172.21.121.100:8182/restconf/data/network-topology:network-topology/topology=topology-netconf/node=sma-xrv-lsr1-alpha/yang-ext:mount/Cisco-IOS-XR-ifmgr-cfg:interface-configurations/interface-configuration=act,tunnel-te201
```

Serializer:

```text
IfmgrTunnelUnnumberedLoopback0XmlSerializer
```

Request XML:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<interface-configuration xmlns="http://cisco.com/ns/yang/Cisco-IOS-XR-ifmgr-cfg">
    <active>act</active>
    <interface-name>${tunnelInterfaceName}</interface-name>
    <ipv4-network xmlns="http://cisco.com/ns/yang/Cisco-IOS-XR-ipv4-io-cfg">
        <addresses>
            <unnumbered>Loopback0</unnumbered>
        </addresses>
    </ipv4-network>
</interface-configuration>
```

Success condition:

```text
any HTTP 2xx
```

Expected common success:

```text
204 No Content
```

Retryable failures:

```text
409 conflict
404 data-missing
500 commit failed
timeout
connection reset
```

Reason:

Auto-PCC tunnel interface creation and NETCONF mount visibility are eventually consistent.

---

## 8.8 Update Existing LSP

Endpoint:

```http
POST {ODL_RESTS_OPERATIONS_BASE_URL}/network-topology-pcep:update-lsp
```

Concrete lab endpoint:

```http
POST http://172.21.121.100:8181/rests/operations/network-topology-pcep:update-lsp
```

Serializer:

```text
UpdateLspRequestXmlSerializer
```

Request XML template:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<input xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
    <node>${pccNode}</node>
    <name>${symbolicTunnelName}</name>

    <network-topology-ref xmlns:nt="urn:TBD:params:xml:ns:yang:network-topology">
        /nt:network-topology/nt:topology[nt:topology-id="${pcepTopologyId}"]
    </network-topology-ref>

    <arguments>
        <lsp>
            ${optionalPlspId}
            <lsp-flags>
                <delegate>true</delegate>
                <administrative>true</administrative>
            </lsp-flags>
        </lsp>

        <bandwidth>
            <bandwidth>${bandwidthBytesPerSecondFloat}</bandwidth>
        </bandwidth>

        <ero>
            ${eroSubobjects}
        </ero>
    </arguments>
</input>
```

`optionalPlspId` should be included if discovered and required by the ODL runtime:

```xml
<plsp-id>${plspId}</plsp-id>
```

Implementation rule:

```text
Cache plspId/PCEP ID from tunnel-head discovery if present.
Use symbolicTunnelName as the primary correlation key.
Use plspId in update-lsp when available.
```

Success condition:

```text
HTTP 200
AND no hard failure
```

After update, poll tunnel-heads to confirm the tunnel remains operational.

---

## 9. Serializers

## 9.1 ClassificationRequestJsonSerializer

Input:

```java
PacketFeatures
```

Output:

```json
{
  "packet_features": {
    "eth_type": 2048,
    "ip_proto": 17,
    "src_port": 53000,
    "dst_port": 53
  }
}
```

Requirements:

```text
Use snake_case JSON fields.
Do not include null fields.
Validate eth_type and ip_proto are present.
```

---

## 9.2 PathComputationRequestXmlSerializer

Input:

```java
CalculatedPathRequest
```

Output:

```xml
<input xmlns="urn:opendaylight:params:xml:ns:yang:path:computation">
    ...
</input>
```

Requirements:

```text
Use XML namespace:
  urn:opendaylight:params:xml:ns:yang:path:computation

Use graph-name:
  ted://sma-bgp-linkstate-topology

Use source/destination numeric graph node IDs.

Use bandwidth in bytes per second.

Use class-type 0 for current lab.

Use algorithm cspf.
```

---

## 9.3 AddLspRequestXmlSerializer

Input:

```java
AddLspRequest
```

Required fields:

```text
pccNode
symbolicTunnelName
sourceRouterId
destinationRouterId
bandwidthBytesPerSecond
eroSubobjects
pcepTopologyId
```

Output namespace:

```text
urn:opendaylight:params:xml:ns:yang:topology:pcep
```

Must include:

```text
path-setup-type/pst = rsvp-te
lsp-flags/delegate = true
lsp-flags/administrative = true
bandwidth
endpoints-obj
ero
network-topology-ref
```

---

## 9.4 UpdateLspRequestXmlSerializer

Input:

```java
UpdateLspRequest
```

Required fields:

```text
pccNode
symbolicTunnelName
bandwidthBytesPerSecond
eroSubobjects
pcepTopologyId
```

Optional but recommended field:

```text
plspId
```

Must preserve:

```text
delegate=true
administrative=true
```

---

## 9.5 IfmgrTunnelUnnumberedLoopback0XmlSerializer

Input:

```java
String tunnelInterfaceName
```

Output:

```xml
<interface-configuration xmlns="http://cisco.com/ns/yang/Cisco-IOS-XR-ifmgr-cfg">
    <active>act</active>
    <interface-name>tunnel-te201</interface-name>
    <ipv4-network xmlns="http://cisco.com/ns/yang/Cisco-IOS-XR-ipv4-io-cfg">
        <addresses>
            <unnumbered>Loopback0</unnumbered>
        </addresses>
    </ipv4-network>
</interface-configuration>
```

Validation:

```text
tunnelInterfaceName must match:
  tunnel-te<integer>
```

---

## 9.6 EroXmlSerializer

Input:

```java
List<EroSubobject>
```

Output:

```xml
<subobject>
    <loose>false</loose>
    <ip-prefix>
        <ip-prefix>10.0.12.2/32</ip-prefix>
    </ip-prefix>
</subobject>
```

Rules:

```text
Every ERO hop is strict:
  loose=false

Every IPv4 hop must be encoded as /32.

Append destination router ID as the final /32 hop.
```

---

## 10. Deserializers

## 10.1 NetworkTopologyListXmlDeserializer

Input:

```xml
<network-topology>
    <topology>
        <topology-id>...</topology-id>
    </topology>
</network-topology>
```

Output:

```java
Set<String> topologyIds
```

Required validation:

```text
topology-netconf exists
sma-bgp-linkstate-topology exists
```

---

## 10.2 BgpLsTopologyXmlDeserializer

Input:

```xml
<topology>
    <topology-id>sma-bgp-linkstate-topology</topology-id>
    <node>
        <node-id>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</node-id>
        <igp-node-attributes>
            <router-id>11.11.11.11</router-id>
            <ospf-node-attributes>
                <ted>
                    <te-router-id-ipv4>11.11.11.11</te-router-id-ipv4>
                </ted>
            </ospf-node-attributes>
        </igp-node-attributes>
    </node>
</topology>
```

Output:

```java
List<BgpLsTopologyNode>
```

Extraction rules:

```text
topologyId = topology/topology-id
nodeId = node/node-id
routerId = node/igp-node-attributes/router-id
teRouterIdIpv4 = node/igp-node-attributes/ospf-node-attributes/ted/te-router-id-ipv4
graphNodeId = parse router=<number> from nodeId
```

Must handle XML namespaces properly.

Do not use brittle string splitting for XML traversal, except for parsing the `router=` query parameter from the already-extracted `node-id` string.

---

## 10.3 ClassificationResponseJsonDeserializer

Input:

Classifier JSON response.

Output:

```java
ClassificationResult
```

Required validation:

```text
prediction.class_name exists
prediction.confidence exists
policy.path_constraints.requested_bandwidth_kbps exists
```

If `policy.policy_fallback == true`, keep the classification but log the fallback reason.

---

## 10.4 PathComputationResponseXmlDeserializer

Input:

```xml
<output xmlns="urn:opendaylight:params:xml:ns:yang:path:computation">
    <path-description>
        <remote-ipv4>10.0.12.2</remote-ipv4>
        <ipv4>10.0.12.1</ipv4>
    </path-description>
    <status>completed</status>
    <computed-te-metric>2</computed-te-metric>
</output>
```

Output:

```java
PathComputationResponse
```

Required fields:

```text
status
path-description list
computed-te-metric
```

Success validation:

```text
status == completed
pathDescriptions is not empty
```

---

## 10.5 AddLspResponseXmlDeserializer

Input examples:

```xml
<output xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
    <failure>no-ack</failure>
</output>
```

Output:

```java
AddLspResult
```

Fields:

```java
boolean provisionalSuccess;
boolean hardFailure;
String failureReason;
```

Rules:

```text
HTTP 200 and no failure:
  provisionalSuccess=true
  hardFailure=false

HTTP 200 and failure=no-ack:
  provisionalSuccess=true
  hardFailure=false

HTTP 200 and other failure:
  provisionalSuccess=false
  hardFailure=true

non-2xx:
  provisionalSuccess=false
  hardFailure=true
```

---

## 10.6 TunnelHeadsResponseXmlDeserializer

Input:

Cisco XR `tunnel-heads` XML.

Output:

```java
List<TunnelOperationalSnapshot>
```

Model:

```java
public record TunnelOperationalSnapshot(
    String tunnelName,
    String tunnelInterfaceName,
    Integer tunnelId,
    Long plspId,
    String signalledName,
    String configSignalledName,
    String autoPccSymbolicName,
    Boolean autoPcc,
    String operationalState,
    String failReason,
    String destinationState,
    String signallingStatus,
    List<String> currentPathHops
) {}
```

Matching method:

```java
Optional<TunnelOperationalSnapshot> findBySymbolicName(String requestedName)
```

Matching order:

```text
autoPccSymbolicName
signalledName
configSignalledName
```

Tunnel interface fallback rule:

```text
if tunnelInterfaceName exists:
  use tunnelInterfaceName
else:
  use tunnelName
```

---

## 10.7 UpdateLspResponseXmlDeserializer

Output:

```java
UpdateLspResult
```

Rules:

```text
HTTP 2xx and no hard failure:
  success

HTTP 2xx and no-ack:
  provisional success; poll tunnel-heads

non-2xx:
  failure
```

After every update, poll tunnel-heads.

---

## 11. Translators

## 11.1 CalculatedPathToEroTranslator

Class:

```java
public final class CalculatedPathToEroTranslator
```

Input:

```java
PathComputationResponse
destinationRouterId
```

Algorithm:

```text
1. For each path-description in order:
     read remote-ipv4
     convert to remote-ipv4/32
     add strict ERO subobject

2. Append destinationRouterId/32 as final strict ERO hop.

3. Return List<EroSubobject>.
```

Example input:

```xml
<path-description>
    <remote-ipv4>10.0.12.2</remote-ipv4>
    <ipv4>10.0.12.1</ipv4>
</path-description>
<path-description>
    <remote-ipv4>10.0.22.2</remote-ipv4>
    <ipv4>10.0.22.1</ipv4>
</path-description>
```

Destination:

```text
14.14.14.14
```

Output ERO:

```text
10.0.12.2/32
10.0.22.2/32
14.14.14.14/32
```

---

## 11.2 BandwidthTranslator

Class:

```java
public final class BandwidthTranslator
```

Required method:

```java
long kbpsToBytesPerSecond(long kbps) {
    return kbps * 1000L / 8L;
}
```

Optional method:

```java
String bytesPerSecondAsXmlFloat(long bytesPerSecond) {
    return bytesPerSecond + ".0";
}
```

Example:

```text
10000 kbps
→ 1250000 bytes/sec
→ "1250000.0"
```

---

## 11.3 SymbolicTunnelNameFactory

Class:

```java
public final class SymbolicTunnelNameFactory
```

For `SINGLE_TUNNEL_PCC`, recommended deterministic names:

```text
sma-odl-single-lsr1-lsr4
sma-odl-single-lsr4-lsr1
```

Alternative if first classification should be visible:

```text
sma-odl-single-dns-lsr1-lsr4
```

But warning:

In single-tunnel mode, the tunnel may later serve a different traffic class after `update-lsp`.

Therefore the recommended stable name is direction-based, not class-based:

```text
sma-odl-single-${srcAlias}-${dstAlias}
```

Rules:

```text
lowercase only
hyphen separated
no spaces
stable across app runtime
maximum practical length should stay conservative
```

---

## 12. Services

## 12.1 PacketInFeatureExtractor

Class:

```java
public final class PacketInFeatureExtractor
```

Input:

```java
PacketReceived notification
```

Output:

```java
PacketClassificationContext
```

Responsibilities:

```text
1. Extract ingress node reference.
2. Extract ingress connector reference.
3. Parse Ethernet payload.
4. Extract eth_type.
5. If IPv4, extract ip_proto.
6. If TCP/UDP, extract src_port and dst_port.
7. If ICMP, set ports to 0 or -1.
8. Resolve logical switch name and connector name.
9. Resolve flow direction.
```

Must not throw on non-IPv4 packets. Non-IPv4 may be ignored or classified as unsupported.

---

## 12.2 ClassificationService

Class:

```java
public final class ClassificationService
```

Dependencies:

```text
ClassificationRegistrar
ClassifierRestClient
ClassificationRequestJsonSerializer
ClassificationResponseJsonDeserializer
```

Method:

```java
ClassificationResult classifyOrGetCached(PacketClassificationContext context);
```

Flow:

```text
1. Ask ClassificationRegistrar for cached result.
2. If present and not expired, return it.
3. Serialize JSON request.
4. POST classifier endpoint.
5. Deserialize response.
6. Store result in ClassificationRegistrar.
7. Return result.
```

Failure behavior:

```text
If classifier is unreachable:
  log error
  either drop workflow or use explicit fallback policy if configured

Do not silently invent classification.
```

---

## 12.3 TopologyDiscoveryService

Class:

```java
public final class TopologyDiscoveryService
```

Dependencies:

```text
OdlRestconfDataClient
NetworkTopologyListXmlDeserializer
BgpLsTopologyXmlDeserializer
BgpLsNodeRegistry
```

Startup method:

```java
void initialize();
```

Flow:

```text
1. GET network-topology list.
2. Verify topology-netconf exists.
3. Verify sma-bgp-linkstate-topology exists.
4. GET BGP-LS topology.
5. Parse BGP-LS nodes.
6. Populate BgpLsNodeRegistry.
7. Validate SMA_HEADEND_RID exists.
8. Validate SMA_TAILEND_RID exists.
9. Resolve forward and reverse graph node IDs.
```

Must fail startup if headend or tailend router ID cannot be resolved.

---

## 12.4 PathComputationService

Class:

```java
public final class PathComputationService
```

Dependencies:

```text
BgpLsNodeRegistry
CalculatedPathRegistry
OdlOperationsClient
PathComputationRequestXmlSerializer
PathComputationResponseXmlDeserializer
CalculatedPathToEroTranslator
BandwidthTranslator
```

Method:

```java
CalculatedPath computeOrGetCached(
    TunnelDirection direction,
    PathConstraints constraints
);
```

Flow:

```text
1. Convert requested bandwidth from kbps to bytes/sec.
2. Resolve source graph node ID from source router ID.
3. Resolve destination graph node ID from destination router ID.
4. Build CalculatedPathKey.
5. Check CalculatedPathRegistry.
6. If valid cached path exists, return it.
7. Serialize path computation XML.
8. POST path-computation RPC.
9. Validate status == completed.
10. Translate response to ERO.
11. Cache CalculatedPath.
12. Return CalculatedPath.
```

---

## 12.5 TunnelLifecycleService

Class:

```java
public final class TunnelLifecycleService
```

Dependencies:

```text
TunnelRegistry
OdlOperationsClient
OdlRestconfDataClient
AddLspRequestXmlSerializer
AddLspResponseXmlDeserializer
UpdateLspRequestXmlSerializer
UpdateLspResponseXmlDeserializer
TunnelHeadsResponseXmlDeserializer
IfmgrTunnelUnnumberedLoopback0XmlSerializer
SymbolicTunnelNameFactory
RetryPolicy
```

Primary method:

```java
TunnelRecord ensureTunnelReady(
    TunnelDirection direction,
    ClassificationResult classification,
    CalculatedPath path
);
```

Flow for `SINGLE_TUNNEL_PCC`:

```text
1. Check TunnelRegistry by directionKey.
2. If usable tunnel exists:
     return it.

3. Generate symbolic tunnel name.
4. Serialize add-lsp request.
5. POST add-lsp.
6. Treat no-ack as provisional success.
7. Poll tunnel-heads on source NETCONF node.
8. Match by symbolic/signalled name.
9. Extract tunnelInterfaceName, tunnelId, plspId if present.
10. Store partial TunnelRecord.
11. PUT Loopback0 patch to discovered tunnelInterfaceName.
12. Mark loopback0Patched.
13. Poll tunnel-heads until operational-up/signalling-connected.
14. Store final TunnelRecord.
15. Return TunnelRecord.
```

Method for later updates:

```java
TunnelRecord updateExistingTunnel(
    TunnelDirection direction,
    ClassificationResult classification,
    CalculatedPath path
);
```

Flow:

```text
1. Require usable tunnel from TunnelRegistry.
2. Serialize update-lsp using symbolicTunnelName and plspId if available.
3. POST update-lsp.
4. Poll tunnel-heads.
5. Update operational state in TunnelRegistry.
6. Return updated TunnelRecord.
```

---

## 12.6 SdnMplsMlWorkflowService

Class:

```java
public final class SdnMplsMlWorkflowService
```

Purpose:

Orchestrates PacketIn → Classification → Path → Tunnel Create/Update.

Method:

```java
void handlePacket(PacketReceived notification);
```

Flow:

```text
1. Extract PacketClassificationContext.
2. Ignore unsupported packets if required.
3. Classify or retrieve cached classification.
4. Resolve TunnelDirection from ingress switch/connector.
5. Compute or retrieve constrained path.
6. If TUNNEL_CREATION_MODE == SINGLE_TUNNEL_PCC:
     a. If no usable tunnel exists for direction:
          ensureTunnelReady()
     b. Else:
          updateExistingTunnel() only if policy/path differs from active tunnel state.
7. Log decision.
8. Update metrics.
```

Important optimization:

```text
Do not call update-lsp if the computed ERO and bandwidth match the active cached tunnel state.
```

---

## 13. Retry Policy

Class:

```java
public final class RetryPolicy
```

Configuration:

```text
initialDelayMs = 500
maxDelayMs = 5000
jitter = ±20%
timeoutSeconds = 60–120 depending operation
```

Retryable cases:

```text
HTTP 404 data missing
HTTP 409 conflict
HTTP 500 transient NETCONF commit failure
empty body
connection timeout
no matching tunnel-head yet
tunnel exists but still operational-down with no-source before patch
```

Non-retryable cases:

```text
authentication failure
malformed XML generated by serializer
configured topology ID missing
configured router ID missing
path-computation status != completed
classifier response missing required policy fields
```

---

## 14. Startup Sequence

At application startup:

```text
1. Load environment variables.

2. Validate TUNNEL_CREATION_MODE.

3. Build TunnelEndpoint records:
     headend
     tailend

4. Build TunnelDirection records:
     lsr1_to_lsr4
     lsr4_to_lsr1

5. Initialize HTTP clients.

6. Call:
     GET /network-topology:network-topology?content=nonconfig

7. Validate:
     topology-netconf exists
     sma-bgp-linkstate-topology exists

8. Call:
     GET /network-topology:network-topology/topology=sma-bgp-linkstate-topology?content=nonconfig

9. Build BgpLsNodeRegistry.

10. Validate:
      SMA_HEADEND_RID resolves to graph node ID
      SMA_TAILEND_RID resolves to graph node ID

11. Log resolved graph mapping:
      11.11.11.11 → 185273099
      14.14.14.14 → 235802126

12. Start PacketReceived listener.
```

Do not start packet-driven tunnel operations until topology discovery succeeds.

---

## 15. Packet Processing Sequence

On every `PacketReceived` notification:

```text
1. PacketInFeatureExtractor extracts:
     ingress switch
     ingress connector
     eth_type
     ip_proto
     src_port
     dst_port

2. DirectionRegistry resolves:
     HEADEND_TO_TAILEND
     or
     TAILEND_TO_HEADEND

3. ClassificationService checks ClassificationRegistrar.

4. If cache miss:
     POST classifier API.

5. Extract:
     class_name
     confidence
     requested_bandwidth_kbps
     setup_priority
     hold_priority
     dscp
     mpls_tc

6. PathComputationService computes or retrieves path.

7. TunnelLifecycleService checks TunnelRegistry.

8. If no tunnel:
     add-lsp
     discover tunnel interface
     patch Loopback0
     validate tunnel up

9. If tunnel exists:
     update-lsp only if policy/path changed.

10. Update logs and metrics.
```

---

## 16. Acceptance Criteria

## 16.1 Topology Discovery

Pass when:

```text
Application can retrieve topology list from port 8182 RESTCONF data endpoint.
Application confirms sma-bgp-linkstate-topology exists.
Application resolves:
  11.11.11.11 → 185273099
  14.14.14.14 → 235802126
```

---

## 16.2 Classification Cache

Pass when:

```text
First DNS-like packet causes classifier API call.
Second packet with same effective service key reuses cache.
PE1 and PE2 caches are isolated by ingress switch.
```

---

## 16.3 Path Computation

Pass when:

```text
Given DNS policy requested_bandwidth_kbps=10000,
application sends bandwidth=1250000 to path-computation RPC.

RPC response status is completed.
ERO is generated from remote-ipv4 hops plus final destination router ID.
```

---

## 16.4 Tunnel Creation

Pass when:

```text
First packet for lsr1_to_lsr4 creates tunnel using add-lsp.
Application accepts no-ack as provisional success.
Application discovers generated tunnel-teXYZ by symbolic/signalled name.
Application patches tunnel-teXYZ with Loopback0.
Application validates operational-up/signalling-connected.
TunnelRegistry stores tunnel record.
```

---

## 16.5 Tunnel Reuse

Pass when:

```text
Later packet for same direction does not call add-lsp.
Later packet does not repeat Loopback0 patch.
Later packet uses update-lsp only when path or bandwidth changes.
```

---

## 16.6 Reverse Direction

Pass when:

```text
Packet from opposite OVS side resolves reverse direction.
Application computes path:
  source = graph ID for 14.14.14.14
  destination = graph ID for 11.11.11.11

Application creates or updates reverse PCC tunnel from LSR4 to LSR1.
```

---

## 17. Logging Requirements

Every workflow execution must log:

```text
packet ingress switch
packet ingress connector
eth_type
ip_proto
src_port
dst_port
classification cache hit/miss
classification class_name
classification confidence
policy profile_name
requested bandwidth kbps
converted bandwidth bytes/sec
directionKey
source router ID
destination router ID
source graph node ID
destination graph node ID
path cache hit/miss
computed ERO
tunnel registry hit/miss
symbolic tunnel name
tunnel interface name
add-lsp/update-lsp action
Loopback0 patch action
final tunnel operational state
```

Do not log passwords.

---

## 18. Metrics Requirements

Expose counters/gauges suitable for Prometheus:

```text
sma_packet_in_total
sma_classification_cache_hit_total
sma_classification_cache_miss_total
sma_classifier_request_total
sma_classifier_request_failure_total
sma_path_computation_request_total
sma_path_computation_cache_hit_total
sma_add_lsp_request_total
sma_add_lsp_no_ack_total
sma_update_lsp_request_total
sma_tunnel_discovery_retry_total
sma_tunnel_loopback_patch_total
sma_tunnel_operational_up_total
sma_tunnel_operational_failure_total
```

Recommended labels:

```text
direction
class_name
profile_name
ingress_switch
source_router
destination_router
```

---

## 19. Error Handling Rules

## 19.1 Missing Topology

If topology list does not include `sma-bgp-linkstate-topology`:

```text
fail startup
```

## 19.2 Missing Router ID

If configured `SMA_HEADEND_RID` or `SMA_TAILEND_RID` cannot be resolved:

```text
fail startup
```

## 19.3 Classifier Failure

If classifier is unreachable:

```text
log error
do not create/update tunnel from guessed classification
```

Optional future behavior:

```text
use explicit best-effort fallback policy only if configured
```

## 19.4 Path Computation Failure

If status is not `completed`:

```text
do not call add-lsp or update-lsp
log hard workflow failure
```

## 19.5 Add-LSP no-ack

If response is `no-ack` with HTTP 200:

```text
continue to tunnel discovery
```

## 19.6 Tunnel Discovery Timeout

If no tunnel matching symbolic name is found within timeout:

```text
mark tunnel creation failed
do not patch unknown tunnel
do not assume tunnel ID
```

## 19.7 Loopback Patch Failure

If patch fails within timeout:

```text
mark tunnel not usable
do not update registry as usableForUpdate
```

---

## 20. Coding Agent Task Breakdown

Implement in this order:

```text
1. Configuration loader and environment validation.

2. HTTP clients:
   - OdlRestconfDataClient
   - OdlOperationsClient
   - ClassifierRestClient

3. Data models.

4. XML/JSON serializers:
   - ClassificationRequestJsonSerializer
   - PathComputationRequestXmlSerializer
   - EroXmlSerializer
   - AddLspRequestXmlSerializer
   - UpdateLspRequestXmlSerializer
   - IfmgrTunnelUnnumberedLoopback0XmlSerializer

5. XML/JSON deserializers:
   - NetworkTopologyListXmlDeserializer
   - BgpLsTopologyXmlDeserializer
   - ClassificationResponseJsonDeserializer
   - PathComputationResponseXmlDeserializer
   - AddLspResponseXmlDeserializer
   - UpdateLspResponseXmlDeserializer
   - TunnelHeadsResponseXmlDeserializer

6. Registries:
   - ClassificationRegistrar
   - BgpLsNodeRegistry
   - CalculatedPathRegistry
   - TunnelRegistry
   - DirectionRegistry

7. Translators:
   - BandwidthTranslator
   - CalculatedPathToEroTranslator
   - SymbolicTunnelNameFactory

8. Services:
   - TopologyDiscoveryService
   - ClassificationService
   - PathComputationService
   - TunnelLifecycleService
   - SdnMplsMlWorkflowService

9. PacketReceived listener integration.

10. Logging and metrics.

11. Unit tests for serializers/deserializers.

12. Integration tests against the lab endpoints.
```

---

## 21. Minimum Unit Tests

## 21.1 BgpLsTopologyXmlDeserializer

Input:

```xml
<node-id>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</node-id>
<router-id>11.11.11.11</router-id>
<te-router-id-ipv4>11.11.11.11</te-router-id-ipv4>
```

Expected:

```text
routerId = 11.11.11.11
graphNodeId = 185273099
```

---

## 21.2 BandwidthTranslator

Input:

```text
10000 kbps
```

Expected:

```text
1250000 bytes/sec
```

---

## 21.3 PathComputationResponseXmlDeserializer

Input path:

```text
remote-ipv4 10.0.12.2
remote-ipv4 10.0.22.2
destination 14.14.14.14
```

Expected ERO:

```text
10.0.12.2/32
10.0.22.2/32
14.14.14.14/32
```

---

## 21.4 AddLspResponseXmlDeserializer

Input:

```xml
<failure>no-ack</failure>
```

Expected:

```text
provisionalSuccess = true
hardFailure = false
```

---

## 21.5 TunnelHeadsResponseXmlDeserializer

Input tunnel head with:

```text
auto-pcc/symbolic-name = sma-odl-single-lsr1-lsr4
tunnel-interface-name = tunnel-te201
tunnel-id = 201
```

Expected:

```text
findBySymbolicName("sma-odl-single-lsr1-lsr4") returns tunnel-te201
```

---

## 22. Final Implementation Principle

The Java application must treat ODL/PCEP/NETCONF operations as an eventually consistent workflow, not as immediate single-response transactions.

The correct operational model is:

```text
PacketIn
→ Classification cache or classifier API
→ Path cache or path-computation RPC
→ Tunnel registry lookup
→ add-lsp only if no tunnel exists
→ tunnel-head polling
→ Loopback0 patch
→ final tunnel-head validation
→ update-lsp for future changes
```

The app must never assume:

```text
add-lsp response alone means tunnel is usable
tunnel number is known before polling tunnel-heads
Loopback0 patch is unnecessary for auto-PCC tunnels
hardcoded graph node IDs are stable enough for implementation
```

The app must always discover:

```text
router-id → graphNodeId
symbolic tunnel name → tunnel-teXYZ
tunnel-teXYZ → operational state
```

before treating a tunnel as reusable.