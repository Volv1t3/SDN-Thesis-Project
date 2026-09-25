# SDN-MPLS-ML Java ODL Controller Patch Specification

## Delegated Tunnel `update-lsp` Workflow

## 1. Purpose

Patch the Java ODL controller implementation so it no longer creates, discovers, patches, or removes PCEP auto-PCC tunnels.

The controller must now assume that the Cisco XRv headend routers already own valid delegated RSVP-TE tunnel interfaces:

```text
LSR1 → LSR4: tunnel-te110 / sma-lsr1-lsr4-delegated
LSR4 → LSR1: tunnel-te410 / sma-lsr4-lsr1-delegated
```

The Java application’s responsibility is now reduced to:

```text
PacketIn
→ classify packet
→ map class to path constraints
→ compute constrained path
→ translate path to ERO
→ read delegated LSP state from pcep-topology
→ extract PLSP ID
→ call update-lsp
→ validate reported-lsp state
```

The application must no longer use:

```text
network-topology-pcep:add-lsp
network-topology-pcep:remove-lsp
Cisco XR tunnel-heads discovery
Cisco XR Loopback0 tunnel interface patching
```

---

## 2. Superseded Workflow

Remove this old lifecycle from the implementation:

```text
add-lsp
→ accept no-ack
→ poll Cisco XR tunnel-heads
→ discover generated tunnel-teXYZ
→ patch ipv4 unnumbered Loopback0
→ validate auto-PCC tunnel
→ later update-lsp
```

This was required only for dynamically created auto-PCC tunnels. It is no longer the target architecture.

The new validated model is:

```text
XR preconfigures tunnel shell
XR delegates tunnel to ODL
ODL reads the delegated LSP from pcep-topology
ODL updates the same LSP with update-lsp
```

---

## 3. Required Precondition

Before the Java application starts packet-driven control, the PCEP topology must expose both delegated tunnels as `reported-lsp` entries.

Required forward tunnel:

```text
PCC node: pcc://10.100.10.1
LSP name: sma-lsr1-lsr4-delegated
Tunnel ID: 110
PLSP ID: discovered from pcep-topology; current lab value is 111
Source RID: 11.11.11.11
Destination RID: 14.14.14.14
```

Required reverse tunnel:

```text
PCC node: pcc://10.100.40.1
LSP name: sma-lsr4-lsr1-delegated
Tunnel ID: 410
PLSP ID: discovered from pcep-topology; current lab value is 411
Source RID: 14.14.14.14
Destination RID: 11.11.11.11
```

The PLSP IDs must not be hardcoded. They must be discovered at runtime from:

```http
GET http://172.21.121.100:8182/restconf/data/network-topology:network-topology/topology=pcep-topology?content=all
```

---

## 4. Updated Environment Variables

### 4.1 Keep Existing Core Variables

```text
ODL_RESTCONF_DATA_BASE_URL=http://172.21.121.100:8182/restconf/data
ODL_RESTS_OPERATIONS_BASE_URL=http://172.21.121.100:8181/rests/operations

ODL_USERNAME=admin
ODL_PASSWORD=admin

ODL_BGPLS_TOPOLOGY_ID=sma-bgp-linkstate-topology
ODL_PCEP_TOPOLOGY_ID=pcep-topology
ODL_PATH_COMPUTATION_GRAPH_NAME=ted://sma-bgp-linkstate-topology

SMA_HEADEND_RID=11.11.11.11
SMA_TAILEND_RID=14.14.14.14

SMA_HEADEND_PCC_NODE=pcc://10.100.10.1
SMA_TAILEND_PCC_NODE=pcc://10.100.40.1
```

### 4.2 Add Delegated LSP Variables

```text
SMA_FORWARD_LSP_NAME=sma-lsr1-lsr4-delegated
SMA_REVERSE_LSP_NAME=sma-lsr4-lsr1-delegated

SMA_FORWARD_TUNNEL_INTERFACE=tunnel-te110
SMA_REVERSE_TUNNEL_INTERFACE=tunnel-te410

SMA_FORWARD_DIRECTION_KEY=lsr1_to_lsr4
SMA_REVERSE_DIRECTION_KEY=lsr4_to_lsr1
```

### 4.3 Remove or Deprecate Auto-PCC Variables

Remove or deprecate:

```text
TUNNEL_DISCOVERY_TIMEOUT_SECONDS
TUNNEL_DISCOVERY_INITIAL_DELAY_MS
TUNNEL_DISCOVERY_MAX_DELAY_MS
SMA_HEADEND_NETCONF_NODE
SMA_TAILEND_NETCONF_NODE
TUNNEL_CREATION_MODE
```

`TUNNEL_CREATION_MODE` may remain for compatibility, but the only valid production behavior is now:

```text
DELEGATED_TUNNEL_UPDATE
```

Recommended enum replacement:

```java
enum TunnelControlMode {
    DELEGATED_TUNNEL_UPDATE
}
```

If a legacy enum remains, disable any code path that invokes `add-lsp`, `remove-lsp`, tunnel-head polling, or Loopback0 patching.

---

## 5. Endpoint Inventory

### 5.1 Read BGP-LS Topology

Purpose:

Resolve router IDs into graph node IDs for constrained path computation.

```http
GET {ODL_RESTCONF_DATA_BASE_URL}/network-topology:network-topology/topology={ODL_BGPLS_TOPOLOGY_ID}?content=nonconfig
```

Concrete lab endpoint:

```http
GET http://172.21.121.100:8182/restconf/data/network-topology:network-topology/topology=sma-bgp-linkstate-topology?content=nonconfig
```

Still required.

---

### 5.2 Compute Constrained Path

Purpose:

Compute the path for the active classification policy.

```http
POST {ODL_RESTS_OPERATIONS_BASE_URL}/path-computation:get-constrained-path
```

Concrete lab endpoint:

```http
POST http://172.21.121.100:8181/rests/operations/path-computation:get-constrained-path
```

Still required.

---

### 5.3 Read PCEP Topology

Purpose:

Discover delegated LSPs and extract PLSP IDs.

```http
GET {ODL_RESTCONF_DATA_BASE_URL}/network-topology:network-topology/topology={ODL_PCEP_TOPOLOGY_ID}?content=all
```

Concrete lab endpoint:

```http
GET http://172.21.121.100:8182/restconf/data/network-topology:network-topology/topology=pcep-topology?content=all
```

This replaces the old Cisco XR tunnel-heads endpoint.

---

### 5.4 Update Delegated LSP

Purpose:

Push the new ERO and bandwidth into the existing delegated tunnel.

```http
POST {ODL_RESTS_OPERATIONS_BASE_URL}/network-topology-pcep:update-lsp
```

Concrete lab endpoint:

```http
POST http://172.21.121.100:8181/rests/operations/network-topology-pcep:update-lsp
```

This is now the only PCEP LSP mutation endpoint used by the application.

---

## 6. Endpoints to Remove from the Active Workflow

Remove all active calls to:

```http
POST /network-topology-pcep:add-lsp
POST /network-topology-pcep:remove-lsp
GET  /topology=topology-netconf/node=.../Cisco-IOS-XR-mpls-te-oper:mpls-te/p2p-p2mp-tunnel/tunnel-heads
PUT  /topology=topology-netconf/node=.../Cisco-IOS-XR-ifmgr-cfg:interface-configurations/interface-configuration=act,tunnel-teXYZ
```

The application should no longer need mounted XR NETCONF tunnel operational data for tunnel lifecycle management.

NETCONF mount usage may still exist elsewhere in the project for independent device configuration, but it is not part of the LSP update workflow.

---

## 7. Revised Data Models

### 7.1 DelegatedLspRecord

Replace the old auto-PCC `TunnelRecord` with:

```java
public record DelegatedLspRecord(
    String directionKey,
    String pccNode,
    String lspName,
    String tunnelInterfaceName,
    String sourceRouterId,
    String destinationRouterId,
    long plspId,
    long tunnelId,
    long lspId,
    boolean delegated,
    boolean administrativeUp,
    String operationalState,
    List<EroSubobject> activeEro,
    String reportedBandwidthBase64,
    Instant discoveredAt,
    Instant updatedAt
) {}
```

Required fields:

```text
directionKey
pccNode
lspName
sourceRouterId
destinationRouterId
plspId
tunnelId
activeEro
```

The application must fail startup if the configured LSP names are not visible in `pcep-topology`.

---

### 7.2 DelegatedLspRegistry

Replace `TunnelRegistry` with:

```java
public final class DelegatedLspRegistry
```

Required internal maps:

```java
private final Map<String, DelegatedLspRecord> byDirectionKey;
private final Map<String, DelegatedLspRecord> byLspName;
private final Map<String, DelegatedLspRecord> byPccNodeAndName;
```

Required methods:

```java
void replaceAll(Collection<DelegatedLspRecord> records);

Optional<DelegatedLspRecord> findByDirectionKey(String directionKey);

Optional<DelegatedLspRecord> findByLspName(String lspName);

DelegatedLspRecord requireByDirectionKey(String directionKey);

void updateAfterSuccessfulUpdate(
    String directionKey,
    List<EroSubobject> newEro,
    String bandwidthBase64,
    Instant updatedAt
);
```

Validity condition:

```text
record exists
AND plspId > 0
AND delegated == true
AND administrativeUp == true
AND operationalState == up
```

---

### 7.3 PcepReportedLspSnapshot

Add a parser model for `pcep-topology`:

```java
public record PcepReportedLspSnapshot(
    String pccNode,
    String name,
    long plspId,
    long tunnelId,
    long lspId,
    String sourceRouterId,
    String destinationRouterId,
    boolean delegate,
    boolean administrative,
    String operational,
    List<EroSubobject> ero,
    String bandwidthBase64
) {}
```

Extraction paths:

```text
/topology/node/node-id
/topology/node/path-computation-client/reported-lsp/name
/topology/node/path-computation-client/reported-lsp/path/lsp/plsp-id
/topology/node/path-computation-client/reported-lsp/path/lsp/tlvs/lsp-identifiers/tunnel-id
/topology/node/path-computation-client/reported-lsp/path/lsp/tlvs/lsp-identifiers/lsp-id
/topology/node/path-computation-client/reported-lsp/path/lsp/tlvs/lsp-identifiers/ipv4/ipv4-tunnel-sender-address
/topology/node/path-computation-client/reported-lsp/path/lsp/tlvs/lsp-identifiers/ipv4/ipv4-tunnel-endpoint-address
/topology/node/path-computation-client/reported-lsp/path/lsp/lsp-flags/delegate
/topology/node/path-computation-client/reported-lsp/path/lsp/lsp-flags/administrative
/topology/node/path-computation-client/reported-lsp/path/lsp/lsp-flags/operational
/topology/node/path-computation-client/reported-lsp/path/ero/subobject/ip-prefix/ip-prefix
/topology/node/path-computation-client/reported-lsp/path/bandwidth/bandwidth
```

The XML parser must be namespace-aware.

---

## 8. Revised Serializers and Deserializers

### 8.1 Remove These Active Serializers

Delete or deprecate from the active workflow:

```text
AddLspRequestXmlSerializer
AddLspResponseXmlDeserializer
TunnelHeadsResponseXmlDeserializer
IfmgrTunnelUnnumberedLoopback0XmlSerializer
SymbolicTunnelNameFactory
```

### 8.2 Keep These Serializers

Keep:

```text
ClassificationRequestJsonSerializer
ClassificationResponseJsonDeserializer
PathComputationRequestXmlSerializer
PathComputationResponseXmlDeserializer
EroXmlSerializer
UpdateLspRequestXmlSerializer
UpdateLspResponseXmlDeserializer
BgpLsTopologyXmlDeserializer
NetworkTopologyListXmlDeserializer
```

Add:

```text
PcepTopologyXmlDeserializer
PcepReportedLspDeserializer
```

---

## 9. Bandwidth Encoding Requirement

The `update-lsp` bandwidth field is encoded as `string($byte)` in this runtime.

Zero bandwidth appears as:

```xml
<bandwidth>AAAAAA==</bandwidth>
```

The application must encode requested bandwidth as an IEEE-754 32-bit float in bytes per second, then Base64 encode the four bytes.

Conversion:

```java
long bytesPerSecond = requestedBandwidthKbps * 1000L / 8L;
```

Encoding:

```java
float bandwidthFloat = (float) bytesPerSecond;
byte[] bytes = ByteBuffer
    .allocate(4)
    .order(ByteOrder.BIG_ENDIAN)
    .putFloat(bandwidthFloat)
    .array();

String bandwidthBase64 = Base64.getEncoder().encodeToString(bytes);
```

Example:

```text
80 kbps
→ 10,000 bytes/sec
→ float32 bytes: 46 1C 40 00
→ Base64: RhxAAA==
```

So the XML field becomes:

```xml
<bandwidth>RhxAAA==</bandwidth>
```

---

## 10. Updated `update-lsp` Request XML

### 10.1 Forward Direction: LSR1 → LSR4

Runtime values:

```text
node = pcc://10.100.10.1
name = sma-lsr1-lsr4-delegated
plsp-id = discovered from pcep-topology; current lab value is 111
```

ERO from LSR1 → LSR3 → LSR4:

```text
10.0.12.2/32
10.0.22.2/32
14.14.14.14/32
```

Template:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<input xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
    <node>pcc://10.100.10.1</node>
    <name>sma-lsr1-lsr4-delegated</name>

    <arguments>
        <path-setup-type>
            <pst>rsvp-te</pst>
        </path-setup-type>

        <lsp>
            <plsp-id>111</plsp-id>
            <lsp-flags>
                <delegate>true</delegate>
                <administrative>true</administrative>
            </lsp-flags>
        </lsp>

        <bandwidth>
            <processing-rule>false</processing-rule>
            <bandwidth>RhxAAA==</bandwidth>
            <ignore>false</ignore>
        </bandwidth>

        <ero>
            <processing-rule>false</processing-rule>
            <subobject>
                <loose>false</loose>
                <ip-prefix>
                    <ip-prefix>10.0.12.2/32</ip-prefix>
                </ip-prefix>
            </subobject>
            <subobject>
                <loose>false</loose>
                <ip-prefix>
                    <ip-prefix>10.0.22.2/32</ip-prefix>
                </ip-prefix>
            </subobject>
            <subobject>
                <loose>false</loose>
                <ip-prefix>
                    <ip-prefix>14.14.14.14/32</ip-prefix>
                </ip-prefix>
            </subobject>
            <ignore>false</ignore>
        </ero>
    </arguments>

    <network-topology-ref xmlns:nt="urn:TBD:params:xml:ns:yang:network-topology">/nt:network-topology/nt:topology[nt:topology-id="pcep-topology"]</network-topology-ref>
</input>
```

Implementation note:

The serializer must not hardcode `111`. It must insert the current PLSP ID from `DelegatedLspRegistry`.

---

### 10.2 Reverse Direction: LSR4 → LSR1

Runtime values:

```text
node = pcc://10.100.40.1
name = sma-lsr4-lsr1-delegated
plsp-id = discovered from pcep-topology; current lab value is 411
```

ERO from LSR4 → LSR3 → LSR1:

```text
10.0.22.1/32
10.0.12.1/32
11.11.11.11/32
```

Template:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<input xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
    <node>pcc://10.100.40.1</node>
    <name>sma-lsr4-lsr1-delegated</name>

    <arguments>
        <path-setup-type>
            <pst>rsvp-te</pst>
        </path-setup-type>

        <lsp>
            <plsp-id>411</plsp-id>
            <lsp-flags>
                <delegate>true</delegate>
                <administrative>true</administrative>
            </lsp-flags>
        </lsp>

        <bandwidth>
            <processing-rule>false</processing-rule>
            <bandwidth>RhxAAA==</bandwidth>
            <ignore>false</ignore>
        </bandwidth>

        <ero>
            <processing-rule>false</processing-rule>
            <subobject>
                <loose>false</loose>
                <ip-prefix>
                    <ip-prefix>10.0.22.1/32</ip-prefix>
                </ip-prefix>
            </subobject>
            <subobject>
                <loose>false</loose>
                <ip-prefix>
                    <ip-prefix>10.0.12.1/32</ip-prefix>
                </ip-prefix>
            </subobject>
            <subobject>
                <loose>false</loose>
                <ip-prefix>
                    <ip-prefix>11.11.11.11/32</ip-prefix>
                </ip-prefix>
            </subobject>
            <ignore>false</ignore>
        </ero>
    </arguments>

    <network-topology-ref xmlns:nt="urn:TBD:params:xml:ns:yang:network-topology">/nt:network-topology/nt:topology[nt:topology-id="pcep-topology"]</network-topology-ref>
</input>
```

Implementation note:

The serializer must not hardcode `411`. It must insert the current PLSP ID from `DelegatedLspRegistry`.

---

## 11. Revised Service Responsibilities

### 11.1 Remove TunnelLifecycleService

The old `TunnelLifecycleService` should be removed or renamed because the application no longer owns the full tunnel lifecycle.

Remove responsibilities:

```text
ensureTunnelReady
add-lsp
tunnel-head polling
Loopback0 patch
auto-PCC tunnel validation
remove-lsp
```

---

### 11.2 Add DelegatedLspService

New class:

```java
public final class DelegatedLspService
```

Dependencies:

```text
OdlRestconfDataClient
OdlOperationsClient
PcepTopologyXmlDeserializer
UpdateLspRequestXmlSerializer
UpdateLspResponseXmlDeserializer
DelegatedLspRegistry
RetryPolicy
```

Required methods:

```java
void initialize();

DelegatedLspRecord refreshDirection(String directionKey);

DelegatedLspRecord requireDelegatedLsp(String directionKey);

UpdateLspResult updateDelegatedLsp(
    TunnelDirection direction,
    CalculatedPath path,
    PathConstraints constraints
);
```

Startup flow:

```text
1. GET pcep-topology?content=all.
2. Parse reported-lsp entries.
3. Match configured forward LSP name.
4. Match configured reverse LSP name.
5. Extract PLSP ID, tunnel ID, LSP ID, source, destination, active ERO, bandwidth.
6. Validate both LSPs are delegated and operational up.
7. Populate DelegatedLspRegistry.
8. Fail startup if either LSP is missing.
```

Update flow:

```text
1. Resolve DelegatedLspRecord by directionKey.
2. Encode requested bandwidth as Base64 float32.
3. Build update-lsp XML with:
     node
     name
     plsp-id
     bandwidth
     ERO
     network-topology-ref
4. POST update-lsp.
5. Require HTTP 200 and no hard failure.
6. Refresh pcep-topology.
7. Verify reported-lsp ERO and bandwidth changed or remained consistent.
8. Update DelegatedLspRegistry.
```

---

### 11.3 Update PathComputationService

Keep this service mostly unchanged.

It must still:

```text
resolve source router ID to graph node ID
resolve destination router ID to graph node ID
call path-computation:get-constrained-path
parse path-description
convert remote-ipv4 hops into ERO
append destination router ID /32
cache short-lived path results
```

---

### 11.4 Update SdnMplsMlWorkflowService

Old workflow:

```text
if no tunnel:
  add-lsp + discover + patch
else:
  update-lsp
```

New workflow:

```text
1. Extract PacketIn context.
2. Ignore PacketIn if ingress is not eligible.
3. Classify or get cached classification.
4. Resolve direction.
5. Compute or get cached constrained path.
6. Load delegated LSP for direction.
7. If computed ERO and bandwidth equal active reported state:
     skip update-lsp.
8. Else:
     call update-lsp.
9. Refresh pcep-topology and update registry.
10. Log result.
```

No tunnel creation branch exists anymore.

---

## 12. Revised Startup Sequence

At startup:

```text
1. Load environment variables.
2. Initialize HTTP clients.
3. Validate topology IDs.
4. Read BGP-LS topology.
5. Build BgpLsNodeRegistry.
6. Validate headend and tailend router IDs.
7. Read PCEP topology:
     GET /network-topology:network-topology/topology=pcep-topology?content=all
8. Parse reported-lsp entries.
9. Validate forward delegated LSP:
     pcc://10.100.10.1 / sma-lsr1-lsr4-delegated
10. Validate reverse delegated LSP:
     pcc://10.100.40.1 / sma-lsr4-lsr1-delegated
11. Store PLSP IDs in DelegatedLspRegistry.
12. Start PacketReceived listener.
```

Do not start PacketIn processing if either delegated LSP is missing.

---

## 13. Revised Packet Processing Sequence

```text
PacketReceived
→ PacketInEligibilityFilter
→ PacketInFeatureExtractor
→ DirectionRegistry
→ ClassificationService
→ PathComputationService
→ DelegatedLspService.updateDelegatedLsp
→ PCEP topology refresh
→ metrics/logging
```

Detailed sequence:

```text
1. Receive PacketReceived.
2. Verify ingress switch/port is eligible:
     host-golf
     host-hotel
3. Extract:
     eth_type
     ip_proto
     src_port
     dst_port
4. Resolve direction:
     host-golf  → lsr1_to_lsr4
     host-hotel → lsr4_to_lsr1
5. Classify packet or retrieve cached classification.
6. Convert requested bandwidth:
     kbps → bytes/sec → float32 Base64
7. Compute constrained path:
     source graph node
     destination graph node
     bandwidth bytes/sec
     class-type 0
     algorithm cspf
8. Translate path to ERO.
9. Load delegated LSP record for direction.
10. If active ERO and bandwidth already match:
      skip update.
11. Otherwise:
      POST update-lsp.
12. Refresh pcep-topology.
13. Confirm reported LSP remains delegated and operational up.
```

---

## 14. Revised Error Handling

### 14.1 Missing Delegated LSP

If either configured LSP name is missing from `pcep-topology`:

```text
fail startup
```

Error message must include:

```text
expected PCC node
expected LSP name
actual reported LSP names discovered
```

---

### 14.2 Missing PLSP ID

If a reported LSP exists but has no valid `plsp-id`:

```text
fail startup
```

Do not attempt `update-lsp` without PLSP ID.

---

### 14.3 LSP Not Delegated

If `delegate != true`:

```text
fail startup
```

or mark direction disabled.

Do not attempt updates against non-delegated LSPs.

---

### 14.4 Path Computation Failure

If path computation status is not `completed`:

```text
do not call update-lsp
log workflow failure
```

---

### 14.5 Update-LSP Failure

Hard failure:

```text
HTTP non-2xx
failure=unsent
failure other than no-ack
PCEP error object present
```

Acceptable provisional response:

```text
HTTP 200 with empty output
HTTP 200 with no failure
HTTP 200 with failure=no-ack, only if later topology refresh confirms update
```

After every update attempt, refresh `pcep-topology`.

---

## 15. Revised Metrics

Remove:

```text
sma_add_lsp_request_total
sma_add_lsp_no_ack_total
sma_tunnel_discovery_retry_total
sma_tunnel_loopback_patch_total
```

Add:

```text
sma_pcep_topology_refresh_total
sma_pcep_topology_refresh_failure_total
sma_delegated_lsp_discovered_total
sma_delegated_lsp_missing_total
sma_update_lsp_request_total
sma_update_lsp_success_total
sma_update_lsp_failure_total
sma_update_lsp_skipped_no_change_total
```

Keep:

```text
sma_packet_in_total
sma_classification_cache_hit_total
sma_classification_cache_miss_total
sma_classifier_request_total
sma_classifier_request_failure_total
sma_path_computation_request_total
sma_path_computation_cache_hit_total
```

Recommended labels:

```text
direction
lsp_name
pcc_node
class_name
profile_name
source_router
destination_router
```

---

## 16. Revised Acceptance Criteria

### 16.1 PCEP Topology Discovery

Pass when:

```text
Application reads pcep-topology.
Application finds:
  pcc://10.100.10.1 / sma-lsr1-lsr4-delegated / plsp-id > 0
  pcc://10.100.40.1 / sma-lsr4-lsr1-delegated / plsp-id > 0
Application confirms both are delegated and operational up.
```

---

### 16.2 Forward Path Update

Pass when:

```text
Path computation returns:
  10.0.12.2
  10.0.22.2

Application sends update-lsp for:
  node=pcc://10.100.10.1
  name=sma-lsr1-lsr4-delegated
  plsp-id=runtime discovered value

XR reports tunnel-te110 path:
  10.0.12.2
  10.0.22.2
  14.14.14.14
```

---

### 16.3 Reverse Path Update

Pass when:

```text
Path computation returns:
  10.0.22.1
  10.0.12.1

Application sends update-lsp for:
  node=pcc://10.100.40.1
  name=sma-lsr4-lsr1-delegated
  plsp-id=runtime discovered value

XR reports tunnel-te410 path:
  10.0.22.1
  10.0.12.1
  11.11.11.11
```

---

### 16.4 No Tunnel Proliferation

Pass when:

```text
Application never calls add-lsp.
Application never calls remove-lsp.
Application never creates tunnel-te30x auto-PCC tunnels.
Only tunnel-te110 and tunnel-te410 are updated.
```

---

## 17. Coding Agent Patch Tasks

Implement in this order:

```text
1. Remove active use of add-lsp, remove-lsp, tunnel-head polling, and Loopback0 patching.

2. Add new environment variables:
   SMA_FORWARD_LSP_NAME
   SMA_REVERSE_LSP_NAME
   SMA_FORWARD_TUNNEL_INTERFACE
   SMA_REVERSE_TUNNEL_INTERFACE

3. Add DelegatedLspRecord.

4. Add DelegatedLspRegistry.

5. Add PcepTopologyXmlDeserializer.

6. Add PcepReportedLspSnapshot parser.

7. Update OdlRestconfDataClient:
   add getPcepTopologyXml(content=all).

8. Update OdlOperationsClient:
   keep updateLsp.
   remove or deprecate addLsp and removeLsp from active services.

9. Patch BandwidthTranslator:
   add kbpsToPcepBandwidthBase64Float32().

10. Patch UpdateLspRequestXmlSerializer:
    require plspId.
    require Base64 bandwidth.
    include strict ERO.
    include network-topology-ref.

11. Replace TunnelLifecycleService with DelegatedLspService.

12. Patch SdnMplsMlWorkflowService:
    remove tunnel creation branch.
    always use delegated LSP update branch.

13. Patch metrics.

14. Patch unit tests.

15. Run integration test:
    pcep-topology discovery
    LSR1→LSR4 update
    LSR4→LSR1 update
```

---

## 18. Final Implementation Principle

The controller no longer manages tunnel existence.

The controller manages only the path and bandwidth of pre-existing delegated RSVP-TE tunnels.

Final runtime model:

```text
XR:
  owns tunnel-te110 and tunnel-te410
  owns tunnel source, destination, RSVP-TE signalling, and interface validity

ODL/PCEP:
  owns delegated LSP path updates through update-lsp

Java app:
  classifies traffic
  computes constrained path
  extracts PLSP ID from pcep-topology
  sends update-lsp

Python ML API:
  maps PacketIn features to class and path policy
```

The app must never assume:

```text
PLSP ID is static
add-lsp-created tunnels are manageable
tunnel-heads are needed for normal operation
Loopback0 patching is part of the Java workflow
```

The app must always discover:

```text
router-id → graph node ID
lsp name → reported-lsp
reported-lsp → PLSP ID
path computation result → ERO
policy bandwidth → Base64 PCEP bandwidth object
```

Final control loop:

```text
PacketIn
→ classification
→ constrained path computation
→ pcep-topology delegated LSP lookup
→ update-lsp
→ pcep-topology refresh
→ done
```
