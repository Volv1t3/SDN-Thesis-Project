# SDN-MPLS-ML Controller Idempotency, Directional Workflow, and OpenFlow Suppression Flow Specification

## 1. Purpose

This document defines the required changes for the next coding pass of the SDN-MPLS-ML Controller Service Application (CSA). The goal is to prevent continuous PacketIn events from repeatedly triggering classification, path computation, and PCEP `update-lsp` operations for traffic that has already been processed.

The current controller behavior proves that PacketIn extraction, direction resolution, classification, BGP-LS path computation, PCEP topology parsing, and delegated LSP discovery are mostly operational. However, the workflow can still be triggered repeatedly by ICMP request/reply traffic because the baseline OpenFlow IPv4 host-facing rules intentionally copy every host-to-core IPv4 packet to the controller.

This creates three risks:

1. **Control-plane pollution**: repeated ICMP packets keep generating PacketIn events even after the tunnel decision has already been made.
2. **Duplicated work**: repeated packets may call the classifier, CSPF path-computation RPC, PCEP topology refresh, and `update-lsp` more often than necessary.
3. **Directional race conditions**: a left-to-right packet can update both `lsr1_to_lsr4` and `lsr4_to_lsr1`, while the right-to-left reply can also update both directions shortly after. These workflows may race, recompute based on slightly different TE state, and produce unnecessary tunnel churn.

This specification introduces four coordinated mechanisms:

1. **Observed-direction-only tunnel processing by default**.
2. **A tunnel operation / intent registry for idempotent update-lsp behavior**.
3. **Per-direction locks to prevent concurrent updates to the same delegated LSP**.
4. **Temporary higher-priority OpenFlow suppression flows to stop repeated PacketIns for already-classified traffic.**

---

## 2. Current Behavior Summary

### 2.1 Existing Registries

The current registry package already groups in-memory records used for cache, correlation, and idempotency-related controller state.

Existing classes include:

- `ClassificationRegistrar`
- `CalculatedPathRegistry`
- `DelegatedLspRegistry`
- `BgpLsNodeRegistry`
- `DirectionRegistry`

The current classes provide the following behavior.

### 2.1.1 `ClassificationRegistrar`

`ClassificationRegistrar` maintains in-memory classification records using two indexes:

- exact packet-feature classification per ingress switch
- service-class classification per ingress switch

It supports:

- `find(PacketClassificationContext context)`
- `put(PacketClassificationContext context, ClassificationResult result)`
- `expireOldEntries()`
- `clear()`

This cache avoids repeated ML classifier calls when equivalent packets arrive within the classification result TTL.

### 2.1.2 `CalculatedPathRegistry`

`CalculatedPathRegistry` stores calculated paths by `CalculatedPathKey`.

It supports:

- `findValid(CalculatedPathKey key)`
- `put(CalculatedPathKey key, CalculatedPath path)`
- `expireOldEntries()`
- `clear()`

The cache key should remain directional and constraint-specific. A path from `lsr1_to_lsr4` is not the same as a path from `lsr4_to_lsr1`.

Expected key fields:

```java
record CalculatedPathKey(
    long sourceGraphNodeId,
    long destinationGraphNodeId,
    long bandwidthBytesPerSecond,
    int classType,
    String algorithm
) {}
```

### 2.1.3 `DelegatedLspRegistry`

`DelegatedLspRegistry` maintains the last known state of delegated PCEP LSPs using indexes by:

- direction key
- LSP symbolic name
- PCC node + LSP name

It supports:

- `replaceAll(Collection<DelegatedLspRecord> records)`
- `findByDirectionKey(String directionKey)`
- `findByLspName(String lspName)`
- `requireByDirectionKey(String directionKey)`
- `updateAfterSuccessfulUpdate(...)`

This registry represents the last known or confirmed state of delegated LSPs. It should not be used as a complete operation journal.

### 2.1.4 `DirectionRegistry`

`DirectionRegistry` resolves packet direction from the extracted `PacketClassificationContext`.

The current class also exposes:

```java
public List<TunnelDirection> requireBidirectionalTunnelDirections(final FlowDirection ingressDirection)
```

This returns the observed direction first and the opposite direction second.

This behavior is useful for explicit bidirectional-pair updates, but it should not remain the default because it allows request and reply PacketIn workflows to duplicate each other.

---

## 3. Required Final Behavior

### 3.1 Default tunnel update scope

The default behavior must change from:

```text
PacketIn from either host side
→ update observed direction
→ update opposite direction
```

to:

```text
PacketIn from Echo host-golf
→ process only lsr1_to_lsr4

PacketIn from Foxtrot host-hotel
→ process only lsr4_to_lsr1
```

The opposite direction should be processed when traffic is actually observed from the opposite host-facing switch.

### 3.2 Optional bidirectional mode

Bidirectional pair updates may remain available as a configuration option, but they must not be the default.

Supported modes:

```text
OBSERVED_DIRECTION
BIDIRECTIONAL_PAIR
```

Default:

```text
OBSERVED_DIRECTION
```

### 3.3 Idempotency

Before sending `update-lsp`, the application must answer:

```text
Have we recently requested or confirmed this exact desired tunnel state?
```

If the answer is yes, the controller must skip the `update-lsp` operation.

A desired tunnel state is identified by:

- direction key
- LSP name
- PCC node
- classification profile name
- requested bandwidth
- requested ERO fingerprint
- setup/hold priority if used in future serializers
- update scope
- algorithm / class type if relevant to the computed path

### 3.4 Concurrency control

Only one workflow may update the same tunnel direction at a time.

A second workflow attempting to update the same direction must either:

1. wait for the existing operation to finish, or
2. skip if the desired intent is already pending or recently accepted.

The minimum required lock granularity is:

```text
direction_key
```

Example locks:

```text
lsr1_to_lsr4
lsr4_to_lsr1
```

A future strict bidirectional mode may use a pair-level lock:

```text
lsr1_lsr4_pair
```

### 3.5 OpenFlow suppression

After a packet class has been classified and the required tunnel operation has either:

- succeeded,
- been accepted by ODL,
- been confirmed as already valid, or
- been skipped due to a recent identical intent,

the controller should install a temporary higher-priority OpenFlow flow on the ingress OVS switch.

This flow must forward matching packets locally without copying them to the controller.

Baseline IPv4 host-to-core flow:

```text
priority=200, ip, in_port=host-side, actions=CONTROLLER:65535, output:core-side
```

Temporary suppression flow:

```text
priority=250, ip + protocol/service match, in_port=host-side, actions=output:core-side
```

Because `250 > 200`, matching traffic will bypass the controller until the suppression flow expires.

---

## 4. Non-Goals

This task must not introduce the following changes:

1. It must not replace the existing PCEP delegated-LSP architecture.
2. It must not reintroduce `add-lsp` or `remove-lsp`.
3. It must not require VRF, VLAN, PBTS, DS-TE, SR-TE, or XR policy-based forwarding.
4. It must not remove the baseline ODL table-miss flow.
5. It must not disable the baseline IPv4 copy-to-controller rule permanently.
6. It must not treat PCEP reported bandwidth `AAAAAA==` as a fatal failure when XR confirms the bandwidth was applied.
7. It must not convert the ML classifier API to XML. The classifier remains JSON.

---

## 5. Configuration Requirements

Add the following configuration fields to `AppConfig` or the equivalent configuration model.

### 5.1 Tunnel update scope

```text
SMA_TUNNEL_UPDATE_SCOPE=OBSERVED_DIRECTION
```

Allowed values:

```text
OBSERVED_DIRECTION
BIDIRECTIONAL_PAIR
```

Default:

```text
OBSERVED_DIRECTION
```

Behavior:

- `OBSERVED_DIRECTION`: process only the tunnel direction resolved from the PacketIn ingress.
- `BIDIRECTIONAL_PAIR`: process observed direction first, then the opposite direction.

### 5.2 Tunnel intent TTL

```text
SMA_TUNNEL_INTENT_TTL_SECONDS=30
```

Default:

```text
30
```

Purpose:

- Defines how long an accepted or confirmed tunnel intent suppresses repeated `update-lsp` operations for the same desired state.

### 5.3 Pending operation TTL

```text
SMA_TUNNEL_PENDING_TTL_SECONDS=10
```

Default:

```text
10
```

Purpose:

- Prevents a burst of packets from spawning duplicate concurrent updates while the first update is still running.

### 5.4 Operation journal size

```text
SMA_TUNNEL_OPERATION_JOURNAL_MAX_ENTRIES=500
```

Default:

```text
500
```

Purpose:

- Keeps a bounded in-memory audit trail of recent tunnel decisions.

### 5.5 Per-direction locking timeout

```text
SMA_TUNNEL_OPERATION_LOCK_TIMEOUT_MS=5000
```

Default:

```text
5000
```

Purpose:

- Prevents PacketIn listener threads from blocking indefinitely.

### 5.6 OpenFlow suppression toggle

```text
SMA_OPENFLOW_SUPPRESSION_ENABLED=true
```

Default:

```text
true
```

Purpose:

- Enables or disables temporary service-specific OpenFlow suppression flows.

### 5.7 OpenFlow suppression timeouts

```text
SMA_OPENFLOW_SUPPRESSION_IDLE_TIMEOUT_SECONDS=10
SMA_OPENFLOW_SUPPRESSION_HARD_TIMEOUT_SECONDS=60
```

Defaults:

```text
idle_timeout = 10
hard_timeout = 60
```

Purpose:

- `idle_timeout`: remove suppression flow after no matching packets are observed.
- `hard_timeout`: remove suppression flow after a maximum lifetime even if traffic continues.

### 5.8 OpenFlow suppression priority

```text
SMA_OPENFLOW_SUPPRESSION_PRIORITY=250
```

Default:

```text
250
```

Constraint:

- Must be greater than baseline IPv4 host-to-core flow priority `200`.
- Must be lower than ARP priority `300`.

### 5.9 OpenFlow cookie base

```text
SMA_OPENFLOW_SUPPRESSION_COOKIE_BASE=0x8ADC00
```

Default:

```text
0x8ADC00
```

Purpose:

- Differentiates temporary controller-suppression rules from baseline bootstrap rules.

### 5.10 OpenFlow RESTCONF base

```text
SMA_ODL_RESTCONF_DATA_BASE_URL=http://127.0.0.1:8182/restconf/data
```

Purpose:

- Base URL for OpenFlow inventory and flow programming calls.

### 5.11 XML logging level

```text
SMA_ODL_XML_BODY_LOG_LEVEL=DEBUG
```

Allowed values:

```text
OFF
DEBUG
TRACE
```

Constraint:

- Raw XML bodies should not be logged at `INFO` by default.

---

## 6. Required Class Changes

### 6.1 `DirectionRegistry`

Add a new method:

```java
public List<TunnelDirection> requireTunnelDirectionsForScope(
        FlowDirection ingressDirection,
        TunnelUpdateScope updateScope)
```

Required behavior:

```java
switch (updateScope) {
    case OBSERVED_DIRECTION:
        return List.of(requireTunnelDirection(ingressDirection));

    case BIDIRECTIONAL_PAIR:
        return requireBidirectionalTunnelDirections(ingressDirection);

    default:
        throw new IllegalStateException("Unsupported tunnel update scope: " + updateScope);
}
```

Keep `requireBidirectionalTunnelDirections` for compatibility, but the workflow must stop calling it directly unless the configured mode is `BIDIRECTIONAL_PAIR`.

### 6.2 New enum: `TunnelUpdateScope`

Package:

```text
com.sma.sdn.model
```

Definition:

```java
public enum TunnelUpdateScope {
    OBSERVED_DIRECTION,
    BIDIRECTIONAL_PAIR
}
```

### 6.3 New model: `TunnelIntentKey`

Package:

```text
com.sma.sdn.model
```

Definition:

```java
public record TunnelIntentKey(
        String directionKey,
        String pccNode,
        String lspName,
        String profileName,
        String className,
        String bandwidthBase64,
        String eroFingerprint,
        int setupPriority,
        int holdPriority,
        String algorithm,
        int classType) {
}
```

Notes:

- `eroFingerprint` must be deterministic.
- Recommended format:

```text
loose=false:10.0.14.2/32|loose=false:14.14.14.14/32
```

### 6.4 New model: `TunnelOperationStatus`

Package:

```text
com.sma.sdn.model
```

Definition:

```java
public enum TunnelOperationStatus {
    PENDING,
    ACCEPTED,
    CONFIRMED,
    SKIPPED_RECENT_INTENT,
    SKIPPED_ALREADY_MATCHING,
    FAILED,
    FAILED_HARD,
    ACCEPTED_PCEP_BANDWIDTH_UNCONFIRMED
}
```

### 6.5 New model: `TunnelOperationRecord`

Package:

```text
com.sma.sdn.model
```

Definition:

```java
public record TunnelOperationRecord(
        String operationId,
        String workflowId,
        long packetSequence,
        String directionKey,
        String pccNode,
        String lspName,
        String profileName,
        String className,
        String bandwidthBase64,
        List<EroSubobject> requestedEro,
        TunnelOperationStatus status,
        Integer updateLspHttpStatus,
        boolean pcepEroConfirmed,
        boolean pcepBandwidthConfirmed,
        String failureReason,
        Instant startedAt,
        Instant acceptedAt,
        Instant completedAt,
        Instant expiresAt) {
}
```

### 6.6 New registry: `TunnelOperationRegistry`

Package:

```text
com.sma.sdn.registry
```

Responsibilities:

1. Track pending tunnel operations.
2. Track recently accepted or confirmed tunnel intents.
3. Keep a bounded audit journal.
4. Support idempotency checks before expensive work.
5. Support final operation status updates.
6. Expire old records.

Required methods:

```java
public synchronized Optional<TunnelOperationRecord> findRecentUsableIntent(
        TunnelIntentKey key,
        Instant now);

public synchronized TunnelOperationRecord markPending(
        TunnelIntentKey key,
        String workflowId,
        long packetSequence,
        TunnelDirection direction,
        ClassificationResult classification,
        List<EroSubobject> requestedEro,
        Instant now,
        Duration pendingTtl);

public synchronized void markAccepted(
        TunnelIntentKey key,
        int httpStatus,
        Instant now,
        Duration intentTtl);

public synchronized void markConfirmed(
        TunnelIntentKey key,
        int httpStatus,
        boolean pcepEroConfirmed,
        boolean pcepBandwidthConfirmed,
        Instant now,
        Duration intentTtl);

public synchronized void markSkippedRecentIntent(
        TunnelIntentKey key,
        TunnelOperationRecord reusedRecord,
        String workflowId,
        long packetSequence,
        Instant now);

public synchronized void markFailed(
        TunnelIntentKey key,
        int httpStatus,
        String failureReason,
        Instant now);

public synchronized List<TunnelOperationRecord> recentJournalSnapshot();

public synchronized void expireOldEntries();
```

Required indexes:

```text
Map<TunnelIntentKey, TunnelOperationRecord> latestByIntent
Map<String, TunnelOperationRecord> latestByDirection
Deque<TunnelOperationRecord> journal
```

Required log events:

```text
tunnel_operation_recent_intent_hit
tunnel_operation_recent_intent_miss
tunnel_operation_marked_pending
tunnel_operation_marked_accepted
tunnel_operation_marked_confirmed
tunnel_operation_marked_failed
tunnel_operation_marked_skipped
tunnel_operation_journal_trimmed
```

### 6.7 New coordinator: `TunnelOperationCoordinator`

Package:

```text
com.sma.sdn.tunnel
```

Responsibilities:

1. Enforce per-direction locking.
2. Double-check recent intent before and after acquiring a lock.
3. Call path computation only when the intent cannot be skipped.
4. Call `DelegatedLspService.updateDelegatedLsp` only when needed.
5. Ask OpenFlow suppression service to install temporary suppression flows after a valid decision.

Required methods:

```java
public TunnelOperationRecord processDirection(
        WorkflowContext workflowContext,
        PacketClassificationContext packetContext,
        TunnelDirection direction,
        ClassificationResult classification);
```

Internal flow:

```text
1. Build initial operation context.
2. Compute or retrieve path.
3. Build ERO fingerprint.
4. Build TunnelIntentKey.
5. Check TunnelOperationRegistry for recent usable intent.
6. If hit:
     mark skipped
     install OpenFlow suppression flow
     return SKIPPED_RECENT_INTENT
7. Acquire direction lock.
8. Check registry again.
9. Refresh or read current DelegatedLspRecord.
10. If current ERO already matches desired ERO and the policy allows bandwidth readback mismatch:
     mark skipped already matching or confirmed
     install OpenFlow suppression flow
     return
11. Mark pending.
12. Send update-lsp.
13. Classify HTTP result:
     200/201/204 = accepted
     4xx/5xx = failed
14. Refresh PCEP topology if configured.
15. Mark confirmed or accepted-with-warning.
16. Install OpenFlow suppression flow.
17. Return final operation record.
18. Release lock.
```

### 6.8 New service: `OpenFlowSuppressionService`

Package:

```text
com.sma.sdn.openflow
```

Responsibilities:

1. Build service-specific temporary OpenFlow flow XML.
2. Program the flow with RESTCONF `PUT`.
3. Use the discovered OpenFlow switch and connector IDs.
4. Avoid programming suppression for unsupported traffic.
5. Use short idle and hard timeouts.
6. Log success/failure without failing the main tunnel workflow unless strict mode is enabled.

Required methods:

```java
public Optional<OpenFlowSuppressionIntent> buildSuppressionIntent(
        PacketClassificationContext packetContext,
        ClassificationResult classification);

public OpenFlowProgrammingResult installSuppressionFlow(
        OpenFlowSuppressionIntent intent,
        WorkflowContext workflowContext);
```

Required behavior:

- For ICMP, suppress by `eth_type=2048`, `ip_proto=1`, and ingress host connector.
- For TCP, suppress by `eth_type=2048`, `ip_proto=6`, and canonical service port when available.
- For UDP, suppress by `eth_type=2048`, `ip_proto=17`, and canonical service port when available.
- Do not install suppression for ARP.
- Do not install suppression for IPv6.
- Do not install broad "all IPv4" suppression unless explicitly configured.

### 6.9 New model: `OpenFlowSuppressionIntent`

Package:

```text
com.sma.sdn.model
```

Definition:

```java
public record OpenFlowSuppressionIntent(
        String flowId,
        String nodeId,
        String encodedNodeId,
        int tableId,
        int priority,
        long cookie,
        int idleTimeoutSeconds,
        int hardTimeoutSeconds,
        String ingressConnectorId,
        String outputConnectorId,
        int ethType,
        int ipProtocol,
        Integer tcpDestinationPort,
        Integer udpDestinationPort,
        String classificationClassName,
        String profileName) {
}
```

### 6.10 New serializer: `OpenFlowSuppressionFlowXmlSerializer`

Package:

```text
com.sma.sdn.serialization.xml
```

Responsibilities:

1. Serialize `OpenFlowSuppressionIntent` into ODL flow XML.
2. Use namespace-aware XML generation.
3. Avoid raw string concatenation for untrusted values.
4. Preserve URI flow ID and body `<id>` equality.
5. Include idle/hard timeouts.
6. Include `SEND_FLOW_REM` flags only if already accepted by the current ODL/OpenFlowPlugin environment.

---

## 7. Workflow Changes

### 7.1 Existing high-level workflow

Current:

```text
PacketIn
→ Feature extraction
→ Direction resolution
→ Classification or cache lookup
→ Determine update_direction_keys
→ For each direction:
     compute or retrieve path
     read delegated LSP state
     compare desired state
     update-lsp
```

### 7.2 Required high-level workflow

New default:

```text
PacketIn
→ Feature extraction
→ Reject unsupported non-IPv4 / ARP / IPv6
→ Direction resolution
→ Classification or cache lookup
→ Resolve update scope
→ OBSERVED_DIRECTION:
     process only ingress-resolved tunnel direction
→ BIDIRECTIONAL_PAIR:
     process observed direction first, opposite direction second
→ For each selected direction:
     TunnelOperationCoordinator.processDirection(...)
→ Install OpenFlow suppression flow after accepted/skipped/confirmed decision
→ Emit final workflow summary
```

### 7.3 Required final workflow log

A successful or suppressed workflow should end with:

```text
packet_workflow_completed
```

Required metadata:

```json
{
  "workflow_id": "...",
  "packet_sequence": 15,
  "ingress_switch": "ECHO",
  "ingress_connector": "host-golf",
  "observed_direction": "HEADEND_TO_TAILEND",
  "update_scope": "OBSERVED_DIRECTION",
  "processed_direction_keys": ["lsr1_to_lsr4"],
  "classification_class": "ICMP",
  "profile_name": "icmp_tunnel_policy",
  "operation_statuses": ["ACCEPTED"],
  "suppression_flow_installed": true
}
```

A repeated packet within TTL should produce:

```text
packet_workflow_completed
operation_statuses = ["SKIPPED_RECENT_INTENT"]
suppression_flow_installed = true
```

---

## 8. Idempotency Requirements

### 8.1 Recent intent hit

A recent intent hit occurs when:

```text
same direction key
same LSP name
same PCC node
same profile
same class
same bandwidth
same ERO fingerprint
same algorithm/class type
record not expired
record status in:
  ACCEPTED
  CONFIRMED
  SKIPPED_ALREADY_MATCHING
  ACCEPTED_PCEP_BANDWIDTH_UNCONFIRMED
  PENDING
```

If the current matching record is `PENDING`, the new workflow must not start another update. It may return:

```text
SKIPPED_RECENT_INTENT
```

or:

```text
SKIPPED_PENDING_OPERATION
```

if that status is added.

### 8.2 Failed intents

Recent failed intents should not suppress future operations indefinitely.

Recommended behavior:

```text
FAILED / FAILED_HARD:
  retain in journal for audit
  do not count as usable intent after failure cooldown expires
```

Optional cooldown:

```text
SMA_TUNNEL_FAILED_INTENT_COOLDOWN_SECONDS=5
```

### 8.3 Already matching state

If the current delegated LSP state already has the desired ERO, then the workflow may skip `update-lsp`.

Because ODL PCEP readback may report bandwidth as `AAAAAA==` even when XR shows the requested bandwidth, the comparison must be split:

```text
pcep_ero_matches
pcep_bandwidth_matches
```

Recommended decision:

```text
ERO matches, bandwidth differs:
  do not necessarily fail
  status = ACCEPTED_PCEP_BANDWIDTH_UNCONFIRMED or SKIPPED_ALREADY_MATCHING
  log warning
```

---

## 9. Path Cache Requirements

### 9.1 Directional key

The path cache must remain directional.

Forward:

```text
sourceGraphNodeId = 185273099
destinationGraphNodeId = 235802126
```

Reverse:

```text
sourceGraphNodeId = 235802126
destinationGraphNodeId = 185273099
```

These are separate entries.

### 9.2 Required logs

Add explicit hit/miss logs.

```text
calculated_path_cache_hit
calculated_path_cache_miss
calculated_path_cache_miss_reason
```

Required metadata:

```json
{
  "direction_key": "lsr1_to_lsr4",
  "source_graph_node_id": 185273099,
  "destination_graph_node_id": 235802126,
  "bandwidth_bytes_per_second": 1250000,
  "algorithm": "cspf",
  "class_type": 0,
  "registry_size": 1
}
```

---

## 10. OpenFlow Strategy

### 10.1 Baseline flow model

The baseline edge OVS rules remain:

1. ARP host to core
2. ARP core to host
3. IPv4 host to core with controller copy
4. IPv4 core to host without controller copy
5. ODL table-miss to controller

Example OVS operational shape:

```text
priority=300,arp,in_port="host-golf" actions=output:"core-lsr1"
priority=300,arp,in_port="core-lsr1" actions=output:"host-golf"
priority=200,ip,in_port="host-golf" actions=CONTROLLER:65535,output:"core-lsr1"
priority=200,ip,in_port="core-lsr1" actions=output:"host-golf"
priority=0 actions=CONTROLLER:65535
```

### 10.2 Suppression flow model

After a control-plane decision, install a more specific temporary flow:

```text
priority=250,icmp,in_port="host-golf" actions=output:"core-lsr1"
```

or:

```text
priority=250,tcp,tp_dst=80,in_port="host-golf" actions=output:"core-lsr1"
```

or:

```text
priority=250,udp,tp_dst=53,in_port="host-golf" actions=output:"core-lsr1"
```

This prevents repeated PacketIns while allowing the rule to expire automatically.

### 10.3 Switch-specific parameters

For ECHO / PE1:

```text
switch_name = ECHO
node_id = discovered OpenFlow node ID for sma-ovs-pe1-echo
host_connector_name = host-golf
core_connector_name = core-lsr1
host_connector_id = discovered, e.g. openflow:<node>:1
core_connector_id = discovered, e.g. openflow:<node>:2
```

For FOXTROT / PE2:

```text
switch_name = FOXTROT
node_id = discovered OpenFlow node ID for sma-ovs-pe2-foxtrot
host_connector_name = host-hotel
core_connector_name = core-lsr4
host_connector_id = discovered, e.g. openflow:<node>:1
core_connector_id = discovered, e.g. openflow:<node>:2
```

Do not hardcode OpenFlow node IDs.

---

## 11. OpenFlow RESTCONF Endpoint

### 11.1 Flow programming endpoint

```http
PUT {SMA_ODL_RESTCONF_DATA_BASE_URL}/opendaylight-inventory:nodes/node={encodedNodeId}/flow-node-inventory:table=0/flow={flowId}
```

Example:

```http
PUT http://127.0.0.1:8182/restconf/data/opendaylight-inventory:nodes/node=openflow%3A19794577922119/flow-node-inventory:table=0/flow=sma-suppress-echo-icmp-host-golf
```

Headers:

```http
Accept: application/xml
Content-Type: application/xml
Authorization: Basic ...
```

### 11.2 Verification endpoint

```http
GET {SMA_ODL_RESTCONF_DATA_BASE_URL}/opendaylight-inventory:nodes/node={encodedNodeId}/flow-node-inventory:table=0/flow={flowId}?content=config
```

### 11.3 Operational table endpoint

```http
GET {SMA_ODL_RESTCONF_DATA_BASE_URL}/opendaylight-inventory:nodes/node={encodedNodeId}/flow-node-inventory:table=0?content=nonconfig
```

---

## 12. OpenFlow XML Body Templates

The following XML bodies are defined as templates. Placeholder values are written as `${PLACEHOLDER}`.

General parameter table:

| Placeholder | Meaning |
|---|---|
| `${FLOW_ID}` | Flow ID in URI and body. Must match the RESTCONF path `flow={flowId}`. |
| `${TABLE_ID}` | OpenFlow table ID. Use `0`. |
| `${PRIORITY}` | Flow priority. Baseline ARP `300`, baseline IPv4 `200`, suppression `250`. |
| `${COOKIE}` | Numeric cookie value. |
| `${IDLE_TIMEOUT}` | Idle timeout seconds. Baseline usually `0`; suppression usually `10`. |
| `${HARD_TIMEOUT}` | Hard timeout seconds. Baseline usually `0`; suppression usually `60`. |
| `${INGRESS_CONNECTOR_ID}` | Full OpenFlow connector ID used in match, e.g. `openflow:19794577922119:1`. |
| `${OUTPUT_CONNECTOR_ID}` | Full OpenFlow connector ID used in output action. |
| `${ETH_TYPE}` | Ethernet type as decimal. IPv4 `2048`, ARP `2054`. |
| `${IP_PROTOCOL}` | IPv4 protocol. ICMP `1`, TCP `6`, UDP `17`. |
| `${TCP_DST_PORT}` | TCP destination port. |
| `${UDP_DST_PORT}` | UDP destination port. |

### 12.1 Baseline ARP host-to-core flow

Purpose:

- Forward ARP from host side to core side.
- Must not copy ARP to controller.

Recommended priority:

```text
300
```

Timeouts:

```text
idle_timeout = 0
hard_timeout = 0
```

XML:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<flow xmlns="urn:opendaylight:flow:inventory">
    <id>${FLOW_ID}</id>
    <flow-name>${FLOW_ID}</flow-name>
    <table_id>0</table_id>
    <priority>300</priority>
    <cookie>${COOKIE}</cookie>
    <idle-timeout>0</idle-timeout>
    <hard-timeout>0</hard-timeout>
    <flags>SEND_FLOW_REM</flags>
    <match>
        <in-port>${INGRESS_CONNECTOR_ID}</in-port>
        <ethernet-match>
            <ethernet-type>
                <type>2054</type>
            </ethernet-type>
        </ethernet-match>
    </match>
    <instructions>
        <instruction>
            <order>0</order>
            <apply-actions>
                <action>
                    <order>0</order>
                    <output-action>
                        <output-node-connector>${OUTPUT_CONNECTOR_ID}</output-node-connector>
                        <max-length>65535</max-length>
                    </output-action>
                </action>
            </apply-actions>
        </instruction>
    </instructions>
</flow>
```

Example ECHO parameters:

```text
FLOW_ID=sma-bootstrap-echo-arp-host-to-core
INGRESS_CONNECTOR_ID=${ECHO_HOST_CONNECTOR_ID}
OUTPUT_CONNECTOR_ID=${ECHO_CORE_CONNECTOR_ID}
```

Example FOXTROT parameters:

```text
FLOW_ID=sma-bootstrap-foxtrot-arp-host-to-core
INGRESS_CONNECTOR_ID=${FOXTROT_HOST_CONNECTOR_ID}
OUTPUT_CONNECTOR_ID=${FOXTROT_CORE_CONNECTOR_ID}
```

### 12.2 Baseline ARP core-to-host flow

Purpose:

- Forward ARP from core side to host side.
- Must not copy ARP to controller.

XML body is identical to section 12.1 with reversed ports.

Example ECHO parameters:

```text
FLOW_ID=sma-bootstrap-echo-arp-core-to-host
INGRESS_CONNECTOR_ID=${ECHO_CORE_CONNECTOR_ID}
OUTPUT_CONNECTOR_ID=${ECHO_HOST_CONNECTOR_ID}
```

Example FOXTROT parameters:

```text
FLOW_ID=sma-bootstrap-foxtrot-arp-core-to-host
INGRESS_CONNECTOR_ID=${FOXTROT_CORE_CONNECTOR_ID}
OUTPUT_CONNECTOR_ID=${FOXTROT_HOST_CONNECTOR_ID}
```

### 12.3 Baseline IPv4 host-to-core PacketIn-copy flow

Purpose:

- Forward IPv4 packets from host side to core side.
- Also copy the packet to the controller for first-packet classification.

Recommended priority:

```text
200
```

Timeouts:

```text
idle_timeout = 0
hard_timeout = 0
```

XML:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<flow xmlns="urn:opendaylight:flow:inventory">
    <id>${FLOW_ID}</id>
    <flow-name>${FLOW_ID}</flow-name>
    <table_id>0</table_id>
    <priority>200</priority>
    <cookie>${COOKIE}</cookie>
    <idle-timeout>0</idle-timeout>
    <hard-timeout>0</hard-timeout>
    <flags>SEND_FLOW_REM</flags>
    <match>
        <in-port>${INGRESS_CONNECTOR_ID}</in-port>
        <ethernet-match>
            <ethernet-type>
                <type>2048</type>
            </ethernet-type>
        </ethernet-match>
    </match>
    <instructions>
        <instruction>
            <order>0</order>
            <apply-actions>
                <action>
                    <order>0</order>
                    <output-action>
                        <output-node-connector>CONTROLLER</output-node-connector>
                        <max-length>65535</max-length>
                    </output-action>
                </action>
                <action>
                    <order>1</order>
                    <output-action>
                        <output-node-connector>${OUTPUT_CONNECTOR_ID}</output-node-connector>
                        <max-length>65535</max-length>
                    </output-action>
                </action>
            </apply-actions>
        </instruction>
    </instructions>
</flow>
```

Example ECHO parameters:

```text
FLOW_ID=sma-bootstrap-echo-ipv4-host-to-core-controller-copy
INGRESS_CONNECTOR_ID=${ECHO_HOST_CONNECTOR_ID}
OUTPUT_CONNECTOR_ID=${ECHO_CORE_CONNECTOR_ID}
```

Example FOXTROT parameters:

```text
FLOW_ID=sma-bootstrap-foxtrot-ipv4-host-to-core-controller-copy
INGRESS_CONNECTOR_ID=${FOXTROT_HOST_CONNECTOR_ID}
OUTPUT_CONNECTOR_ID=${FOXTROT_CORE_CONNECTOR_ID}
```

### 12.4 Baseline IPv4 core-to-host forwarding flow

Purpose:

- Forward IPv4 packets from core side to host side.
- Do not copy these packets to the controller.

Recommended priority:

```text
200
```

XML:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<flow xmlns="urn:opendaylight:flow:inventory">
    <id>${FLOW_ID}</id>
    <flow-name>${FLOW_ID}</flow-name>
    <table_id>0</table_id>
    <priority>200</priority>
    <cookie>${COOKIE}</cookie>
    <idle-timeout>0</idle-timeout>
    <hard-timeout>0</hard-timeout>
    <flags>SEND_FLOW_REM</flags>
    <match>
        <in-port>${INGRESS_CONNECTOR_ID}</in-port>
        <ethernet-match>
            <ethernet-type>
                <type>2048</type>
            </ethernet-type>
        </ethernet-match>
    </match>
    <instructions>
        <instruction>
            <order>0</order>
            <apply-actions>
                <action>
                    <order>0</order>
                    <output-action>
                        <output-node-connector>${OUTPUT_CONNECTOR_ID}</output-node-connector>
                        <max-length>65535</max-length>
                    </output-action>
                </action>
            </apply-actions>
        </instruction>
    </instructions>
</flow>
```

Example ECHO parameters:

```text
FLOW_ID=sma-bootstrap-echo-ipv4-core-to-host
INGRESS_CONNECTOR_ID=${ECHO_CORE_CONNECTOR_ID}
OUTPUT_CONNECTOR_ID=${ECHO_HOST_CONNECTOR_ID}
```

Example FOXTROT parameters:

```text
FLOW_ID=sma-bootstrap-foxtrot-ipv4-core-to-host
INGRESS_CONNECTOR_ID=${FOXTROT_CORE_CONNECTOR_ID}
OUTPUT_CONNECTOR_ID=${FOXTROT_HOST_CONNECTOR_ID}
```

---

## 13. Temporary OpenFlow Suppression Flow XML Templates

### 13.1 ICMP suppression flow

Purpose:

- Suppress repeated PacketIns for ICMP traffic after the first ICMP packet has already been classified and processed.

Match:

```text
in_port = host connector
eth_type = IPv4 / 2048
ip_proto = ICMP / 1
```

Action:

```text
output to core connector only
```

Recommended priority:

```text
250
```

Recommended timeouts:

```text
idle_timeout = 10
hard_timeout = 60
```

XML:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<flow xmlns="urn:opendaylight:flow:inventory">
    <id>${FLOW_ID}</id>
    <flow-name>${FLOW_ID}</flow-name>
    <table_id>0</table_id>
    <priority>${PRIORITY}</priority>
    <cookie>${COOKIE}</cookie>
    <idle-timeout>${IDLE_TIMEOUT}</idle-timeout>
    <hard-timeout>${HARD_TIMEOUT}</hard-timeout>
    <flags>SEND_FLOW_REM</flags>
    <match>
        <in-port>${INGRESS_CONNECTOR_ID}</in-port>
        <ethernet-match>
            <ethernet-type>
                <type>2048</type>
            </ethernet-type>
        </ethernet-match>
        <ip-match>
            <ip-protocol>1</ip-protocol>
        </ip-match>
    </match>
    <instructions>
        <instruction>
            <order>0</order>
            <apply-actions>
                <action>
                    <order>0</order>
                    <output-action>
                        <output-node-connector>${OUTPUT_CONNECTOR_ID}</output-node-connector>
                        <max-length>65535</max-length>
                    </output-action>
                </action>
            </apply-actions>
        </instruction>
    </instructions>
</flow>
```

Example ECHO ICMP suppression:

```text
FLOW_ID=sma-suppress-echo-icmp-host-golf
PRIORITY=250
COOKIE=9092097
IDLE_TIMEOUT=10
HARD_TIMEOUT=60
INGRESS_CONNECTOR_ID=${ECHO_HOST_CONNECTOR_ID}
OUTPUT_CONNECTOR_ID=${ECHO_CORE_CONNECTOR_ID}
```

Example FOXTROT ICMP suppression:

```text
FLOW_ID=sma-suppress-foxtrot-icmp-host-hotel
PRIORITY=250
COOKIE=9092098
IDLE_TIMEOUT=10
HARD_TIMEOUT=60
INGRESS_CONNECTOR_ID=${FOXTROT_HOST_CONNECTOR_ID}
OUTPUT_CONNECTOR_ID=${FOXTROT_CORE_CONNECTOR_ID}
```

### 13.2 TCP destination-port suppression flow

Purpose:

- Suppress repeated PacketIns for TCP service traffic such as HTTP or SSH after classification and tunnel processing.

Match:

```text
in_port = host connector
eth_type = IPv4 / 2048
ip_proto = TCP / 6
tcp_dst = service port
```

XML:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<flow xmlns="urn:opendaylight:flow:inventory">
    <id>${FLOW_ID}</id>
    <flow-name>${FLOW_ID}</flow-name>
    <table_id>0</table_id>
    <priority>${PRIORITY}</priority>
    <cookie>${COOKIE}</cookie>
    <idle-timeout>${IDLE_TIMEOUT}</idle-timeout>
    <hard-timeout>${HARD_TIMEOUT}</hard-timeout>
    <flags>SEND_FLOW_REM</flags>
    <match>
        <in-port>${INGRESS_CONNECTOR_ID}</in-port>
        <ethernet-match>
            <ethernet-type>
                <type>2048</type>
            </ethernet-type>
        </ethernet-match>
        <ip-match>
            <ip-protocol>6</ip-protocol>
        </ip-match>
        <tcp-match>
            <tcp-destination-port>${TCP_DST_PORT}</tcp-destination-port>
        </tcp-match>
    </match>
    <instructions>
        <instruction>
            <order>0</order>
            <apply-actions>
                <action>
                    <order>0</order>
                    <output-action>
                        <output-node-connector>${OUTPUT_CONNECTOR_ID}</output-node-connector>
                        <max-length>65535</max-length>
                    </output-action>
                </action>
            </apply-actions>
        </instruction>
    </instructions>
</flow>
```

Example HTTP suppression on ECHO:

```text
FLOW_ID=sma-suppress-echo-tcp-dst-80-host-golf
PRIORITY=250
COOKIE=9092100
IDLE_TIMEOUT=10
HARD_TIMEOUT=60
INGRESS_CONNECTOR_ID=${ECHO_HOST_CONNECTOR_ID}
OUTPUT_CONNECTOR_ID=${ECHO_CORE_CONNECTOR_ID}
TCP_DST_PORT=80
```

Example SSH suppression on FOXTROT:

```text
FLOW_ID=sma-suppress-foxtrot-tcp-dst-22-host-hotel
PRIORITY=250
COOKIE=9092101
IDLE_TIMEOUT=10
HARD_TIMEOUT=60
INGRESS_CONNECTOR_ID=${FOXTROT_HOST_CONNECTOR_ID}
OUTPUT_CONNECTOR_ID=${FOXTROT_CORE_CONNECTOR_ID}
TCP_DST_PORT=22
```

### 13.3 UDP destination-port suppression flow

Purpose:

- Suppress repeated PacketIns for UDP service traffic such as DNS or NTP after classification and tunnel processing.

Match:

```text
in_port = host connector
eth_type = IPv4 / 2048
ip_proto = UDP / 17
udp_dst = service port
```

XML:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<flow xmlns="urn:opendaylight:flow:inventory">
    <id>${FLOW_ID}</id>
    <flow-name>${FLOW_ID}</flow-name>
    <table_id>0</table_id>
    <priority>${PRIORITY}</priority>
    <cookie>${COOKIE}</cookie>
    <idle-timeout>${IDLE_TIMEOUT}</idle-timeout>
    <hard-timeout>${HARD_TIMEOUT}</hard-timeout>
    <flags>SEND_FLOW_REM</flags>
    <match>
        <in-port>${INGRESS_CONNECTOR_ID}</in-port>
        <ethernet-match>
            <ethernet-type>
                <type>2048</type>
            </ethernet-type>
        </ethernet-match>
        <ip-match>
            <ip-protocol>17</ip-protocol>
        </ip-match>
        <udp-match>
            <udp-destination-port>${UDP_DST_PORT}</udp-destination-port>
        </udp-match>
    </match>
    <instructions>
        <instruction>
            <order>0</order>
            <apply-actions>
                <action>
                    <order>0</order>
                    <output-action>
                        <output-node-connector>${OUTPUT_CONNECTOR_ID}</output-node-connector>
                        <max-length>65535</max-length>
                    </output-action>
                </action>
            </apply-actions>
        </instruction>
    </instructions>
</flow>
```

Example DNS suppression on ECHO:

```text
FLOW_ID=sma-suppress-echo-udp-dst-53-host-golf
PRIORITY=250
COOKIE=9092110
IDLE_TIMEOUT=10
HARD_TIMEOUT=60
INGRESS_CONNECTOR_ID=${ECHO_HOST_CONNECTOR_ID}
OUTPUT_CONNECTOR_ID=${ECHO_CORE_CONNECTOR_ID}
UDP_DST_PORT=53
```

Example NTP suppression on FOXTROT:

```text
FLOW_ID=sma-suppress-foxtrot-udp-dst-123-host-hotel
PRIORITY=250
COOKIE=9092111
IDLE_TIMEOUT=10
HARD_TIMEOUT=60
INGRESS_CONNECTOR_ID=${FOXTROT_HOST_CONNECTOR_ID}
OUTPUT_CONNECTOR_ID=${FOXTROT_CORE_CONNECTOR_ID}
UDP_DST_PORT=123
```

### 13.4 Optional source-port suppression

Some reply traffic may use the well-known port as source port instead of destination port. The default design should suppress host-originated service requests by destination port. If reply-direction host-originated traffic is expected to classify by source port, add optional source-port templates.

TCP source-port match:

```xml
<tcp-match>
    <tcp-source-port>${TCP_SRC_PORT}</tcp-source-port>
</tcp-match>
```

UDP source-port match:

```xml
<udp-match>
    <udp-source-port>${UDP_SRC_PORT}</udp-source-port>
</udp-match>
```

This should be enabled only if the classifier and service-key logic require it.

---

## 14. Flow ID and Cookie Requirements

### 14.1 Flow ID scheme

Baseline flow IDs:

```text
sma-bootstrap-{switch}-arp-host-to-core
sma-bootstrap-{switch}-arp-core-to-host
sma-bootstrap-{switch}-ipv4-host-to-core-controller-copy
sma-bootstrap-{switch}-ipv4-core-to-host
```

Suppression flow IDs:

```text
sma-suppress-{switch}-icmp-{hostConnectorName}
sma-suppress-{switch}-tcp-dst-{port}-{hostConnectorName}
sma-suppress-{switch}-udp-dst-{port}-{hostConnectorName}
```

Examples:

```text
sma-suppress-echo-icmp-host-golf
sma-suppress-foxtrot-icmp-host-hotel
sma-suppress-echo-tcp-dst-80-host-golf
sma-suppress-foxtrot-udp-dst-53-host-hotel
```

### 14.2 Cookie scheme

Use deterministic cookies to simplify OVS and ODL diagnostics.

Recommended scheme:

```text
0x8ADBxx = baseline bootstrap flows
0x8ADCxx = temporary suppression flows
```

Example mapping:

```text
0x8ADB45 baseline echo ARP host→core
0x8ADB46 baseline echo ARP core→host
0x8ADB47 baseline echo IPv4 host→core controller copy
0x8ADB48 baseline echo IPv4 core→host

0x8ADC01 suppression echo ICMP
0x8ADC02 suppression foxtrot ICMP
0x8ADC10 suppression echo TCP
0x8ADC20 suppression echo UDP
```

If ODL XML serializer requires decimal cookies, convert hexadecimal to decimal before serialization.

---

## 15. OpenFlow Suppression Installation Timing

Suppression must be installed after one of the following outcomes:

```text
ACCEPTED
CONFIRMED
SKIPPED_RECENT_INTENT
SKIPPED_ALREADY_MATCHING
ACCEPTED_PCEP_BANDWIDTH_UNCONFIRMED
```

Suppression must not be installed after:

```text
FAILED
FAILED_HARD
UNKNOWN_DIRECTION
UNSUPPORTED_PACKET
```

Recommended order for a first packet:

```text
1. PacketIn arrives through baseline IPv4 controller-copy rule.
2. Controller classifies packet.
3. Controller computes or retrieves path.
4. Controller sends or skips update-lsp.
5. Controller installs temporary suppression flow.
6. Future matching packets bypass controller until timeout.
```

---

## 16. Error Handling Requirements

### 16.1 OpenFlow suppression failure

OpenFlow suppression failure must not fail the main tunnel workflow by default.

Default behavior:

```text
log warning
return tunnel operation result
do not throw from PacketReceived listener
```

Optional strict mode:

```text
SMA_OPENFLOW_SUPPRESSION_STRICT=false
```

If strict is true, suppression failure may mark the workflow as warning or failed depending on implementation.

### 16.2 RESTCONF 400 for flow PUT

If ODL returns HTTP 400 for flow programming:

1. Log endpoint, flow ID, status, and response body.
2. Try connector ID fallback only if configured.

Fallback setting:

```text
SMA_OPENFLOW_CONNECTOR_ID_FORMAT=FULL
```

Allowed values:

```text
FULL
NUMERIC
AUTO
```

Behavior:

- `FULL`: use `openflow:<node>:<port>`
- `NUMERIC`: use only port number, e.g. `1`
- `AUTO`: try full first, numeric second on 400

### 16.3 Duplicate flow ID

If the same suppression flow ID already exists, `PUT` should replace it idempotently.

This is desired.

### 16.4 Flow removal

Do not explicitly remove suppression flows during normal operation.

Use:

```text
idle_timeout
hard_timeout
SEND_FLOW_REM
```

The controller may listen for removal notifications later, but that is optional.

---

## 17. Logging Requirements

### 17.1 Required operation-level events

```text
tunnel_operation_started
tunnel_operation_lock_wait_started
tunnel_operation_lock_acquired
tunnel_operation_recent_intent_hit
tunnel_operation_recent_intent_miss
tunnel_operation_pending_recorded
tunnel_operation_update_skipped
tunnel_operation_update_lsp_started
tunnel_operation_update_lsp_accepted
tunnel_operation_update_lsp_failed
tunnel_operation_completed
tunnel_operation_lock_released
```

### 17.2 Required OpenFlow suppression events

```text
openflow_suppression_intent_built
openflow_suppression_skipped_unsupported
openflow_suppression_flow_serialized
openflow_suppression_flow_put_started
openflow_suppression_flow_put_completed
openflow_suppression_flow_put_failed
```

### 17.3 Required final PacketIn workflow events

```text
packet_workflow_completed
packet_workflow_completed_with_warnings
packet_workflow_failed
```

### 17.4 Raw XML logging

Raw XML request/response bodies should be logged only at `DEBUG` or `TRACE`.

At `INFO`, log only:

```text
endpoint
method
status code
duration
request body bytes
response body bytes
operation id
workflow id
direction key
```

---

## 18. Metrics Requirements

Expose or log counters for:

```text
classification_cache_hits_total
classification_cache_misses_total
path_cache_hits_total
path_cache_misses_total
tunnel_intent_hits_total
tunnel_intent_misses_total
tunnel_update_lsp_requests_total
tunnel_update_lsp_skipped_total
tunnel_update_lsp_failures_total
openflow_suppression_installs_total
openflow_suppression_failures_total
packet_workflows_completed_total
packet_workflows_failed_total
```

Recommended labels:

```text
direction_key
class_name
profile_name
ingress_switch
ingress_connector
status
```

---

## 19. Acceptance Criteria

### 19.1 Directional processing

Given a PacketIn from:

```text
ECHO / host-golf
```

the default workflow must process only:

```text
lsr1_to_lsr4
```

Given a PacketIn from:

```text
FOXTROT / host-hotel
```

the default workflow must process only:

```text
lsr4_to_lsr1
```

### 19.2 Bidirectional mode

If:

```text
SMA_TUNNEL_UPDATE_SCOPE=BIDIRECTIONAL_PAIR
```

then the workflow may process both directions, observed direction first.

### 19.3 Path cache

A second equivalent workflow within the path TTL must log:

```text
calculated_path_cache_hit
```

and must not call:

```text
POST /rests/operations/path-computation:get-constrained-path
```

for the same directional `CalculatedPathKey`.

### 19.4 Tunnel intent cache

A second equivalent workflow within `SMA_TUNNEL_INTENT_TTL_SECONDS` must log:

```text
tunnel_operation_recent_intent_hit
```

and must not call:

```text
POST /rests/operations/network-topology-pcep:update-lsp
```

for the same `TunnelIntentKey`.

### 19.5 OpenFlow suppression

After the first successful ICMP workflow from Golf, ECHO must show a temporary suppression flow similar to:

```text
priority=250,icmp,in_port="host-golf" actions=output:"core-lsr1"
```

After the first successful ICMP workflow from Hotel, FOXTROT must show:

```text
priority=250,icmp,in_port="host-hotel" actions=output:"core-lsr4"
```

### 19.6 PacketIn reduction

During a continuous ping test:

```text
ping 192.168.20.10
```

the first ICMP request may generate PacketIn and controller work. Subsequent ICMP packets within suppression TTL should increment the suppression flow counter and should not trigger new classifier/path/update-lsp work.

### 19.7 No fatal failure on suppression failure

If suppression flow programming fails but tunnel processing succeeds or is skipped as already valid, the PacketIn workflow should complete with warning, not fatal failure.

---

## 20. Manual Validation Commands

### 20.1 Check ECHO flows

```bash
docker exec -it clab-sdn-mpls-ai-techdemonstrator-baseline-sma-ovs-pe1-echo \
  ovs-ofctl -O OpenFlow13 dump-flows sma-ovs-pe1-echo
```

Expected after first ICMP decision:

```text
priority=250,icmp,in_port="host-golf" actions=output:"core-lsr1"
priority=200,ip,in_port="host-golf" actions=CONTROLLER:65535,output:"core-lsr1"
```

The priority `250` flow should receive subsequent packets.

### 20.2 Check FOXTROT flows

```bash
docker exec -it clab-sdn-mpls-ai-techdemonstrator-baseline-sma-ovs-pe2-foxtrot \
  ovs-ofctl -O OpenFlow13 dump-flows sma-ovs-pe2-foxtrot
```

Expected after first reverse ICMP decision:

```text
priority=250,icmp,in_port="host-hotel" actions=output:"core-lsr4"
priority=200,ip,in_port="host-hotel" actions=CONTROLLER:65535,output:"core-lsr4"
```

### 20.3 Check suppression expiry

Wait longer than:

```text
SMA_OPENFLOW_SUPPRESSION_HARD_TIMEOUT_SECONDS
```

Then run:

```bash
ovs-ofctl -O OpenFlow13 dump-flows <bridge>
```

The suppression flow should be gone or have reset due to reinstallation.

### 20.4 Check logs

Expected first packet:

```text
classification_completed
calculated_path_cache_miss
path_computation_completed
tunnel_operation_recent_intent_miss
delegated_lsp_update_started
openflow_suppression_flow_put_completed
packet_workflow_completed
```

Expected repeated packet within TTL:

```text
classification_registry_exact_hit or classification_registry_lookup_completed with service_hit=true
calculated_path_cache_hit or skipped before path computation due to recent intent
tunnel_operation_recent_intent_hit
openflow_suppression_flow_put_completed or skipped because same flow exists
packet_workflow_completed
```

---

## 21. Implementation Order

The coding agent should implement the changes in this order:

1. Add `TunnelUpdateScope`.
2. Add configuration parsing for `SMA_TUNNEL_UPDATE_SCOPE`.
3. Modify `DirectionRegistry` to resolve selected tunnel directions based on scope.
4. Change default workflow to `OBSERVED_DIRECTION`.
5. Add explicit path cache hit/miss logs.
6. Add `TunnelIntentKey`.
7. Add `TunnelOperationStatus`.
8. Add `TunnelOperationRecord`.
9. Add `TunnelOperationRegistry`.
10. Add per-direction locks in `TunnelOperationCoordinator`.
11. Move direction processing logic into `TunnelOperationCoordinator`.
12. Add double-check idempotency before and after acquiring the lock.
13. Add `OpenFlowSuppressionIntent`.
14. Add `OpenFlowSuppressionFlowXmlSerializer`.
15. Add `OpenFlowSuppressionService`.
16. Install ICMP suppression flows first.
17. Add TCP/UDP suppression flows.
18. Add metrics.
19. Add serializer tests for XML flow bodies.
20. Add integration validation logs.

---

## 22. Unit Test Requirements

### 22.1 Direction scope tests

Test:

```text
OBSERVED_DIRECTION + HEADEND_TO_TAILEND → [lsr1_to_lsr4]
OBSERVED_DIRECTION + TAILEND_TO_HEADEND → [lsr4_to_lsr1]
BIDIRECTIONAL_PAIR + HEADEND_TO_TAILEND → [lsr1_to_lsr4, lsr4_to_lsr1]
BIDIRECTIONAL_PAIR + TAILEND_TO_HEADEND → [lsr4_to_lsr1, lsr1_to_lsr4]
UNKNOWN → exception
```

### 22.2 Tunnel operation registry tests

Test:

```text
same intent within TTL → hit
same intent after TTL → miss
different ERO → miss
different bandwidth → miss
pending intent suppresses duplicate
failed intent does not suppress after cooldown
journal is bounded
```

### 22.3 OpenFlow XML serializer tests

Validate ICMP XML contains:

```text
<priority>250</priority>
<idle-timeout>10</idle-timeout>
<hard-timeout>60</hard-timeout>
<in-port>${hostConnectorId}</in-port>
<type>2048</type>
<ip-protocol>1</ip-protocol>
<output-node-connector>${coreConnectorId}</output-node-connector>
```

Validate TCP XML contains:

```text
<ip-protocol>6</ip-protocol>
<tcp-destination-port>80</tcp-destination-port>
```

Validate UDP XML contains:

```text
<ip-protocol>17</ip-protocol>
<udp-destination-port>53</udp-destination-port>
```

### 22.4 Idempotency workflow tests

Simulate two workflows with the same PacketIn class and direction:

```text
Workflow 1 → update-lsp attempted
Workflow 2 within TTL → update-lsp skipped
```

Simulate opposite direction:

```text
Workflow 1 ECHO host-golf → lsr1_to_lsr4 only
Workflow 2 FOXTROT host-hotel → lsr4_to_lsr1 only
```

---

## 23. Design Rationale

### 23.1 Why observed-direction-only should be default

A PacketIn from Golf represents traffic entering the MPLS domain through LSR1 toward LSR4. It should therefore configure the `lsr1_to_lsr4` delegated LSP.

A PacketIn from Hotel represents traffic entering through LSR4 toward LSR1. It should configure the `lsr4_to_lsr1` delegated LSP.

This separates concerns and avoids having request and reply packets redundantly updating both tunnels.

### 23.2 Why path cache is not enough

Path caching prevents repeated CSPF requests for the same directional path key. It does not prevent repeated `update-lsp` requests for the same already-computed and already-applied tunnel state.

Therefore, a separate tunnel operation / intent registry is required.

### 23.3 Why OpenFlow suppression is required

Even with perfect internal caching, every IPv4 host-to-core packet still reaches the controller because the baseline rule explicitly copies IPv4 packets to the controller.

Temporary suppression flows reduce PacketIn volume at the switch, which is the correct place to prevent repeated control-plane activation.

### 23.4 Why suppression flows must expire

The demonstrator must remain adaptive. If suppression flows never expired, the controller would stop observing traffic class changes.

Short timeouts provide a balance:

```text
enough time to avoid controller pollution
short enough to allow periodic reclassification and adaptation
```

---

## 24. Final Expected Result

After this task is implemented, the controller should behave as follows:

```text
First ICMP request from Golf:
  classify ICMP
  process lsr1_to_lsr4 only
  compute or retrieve path
  update or skip delegated LSP
  install ECHO ICMP suppression flow

Repeated ICMP requests from Golf within TTL:
  forwarded by ECHO suppression flow
  no PacketIn
  no classifier call
  no path computation
  no update-lsp

First ICMP reply/request from Hotel:
  classify ICMP
  process lsr4_to_lsr1 only
  compute or retrieve path
  update or skip delegated LSP
  install FOXTROT ICMP suppression flow

Repeated ICMP packets from Hotel within TTL:
  forwarded by FOXTROT suppression flow
  no PacketIn
  no duplicate control-plane work
```

The controller must still keep enough records to audit what happened:

```text
classification registry
calculated path registry
delegated LSP registry
tunnel operation registry
OpenFlow suppression install logs
```

This provides deterministic tunnel behavior, reduces controller load, prevents request/reply race conditions, and preserves adaptive reclassification through timeout-based suppression flow expiry.
