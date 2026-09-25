# SDN-MPLS-ML Controller Pair-Policy Consensus, Priority Preemption, and No OpenFlow Suppression Specification

## 1. Purpose

This document defines the final controller-side modification for the SDN-MPLS-ML technology demonstrator after aligning two design decisions:

1. Runtime OpenFlow suppression flows are removed.
2. Pair-level policy ownership is selected through bidirectional classification consensus, followed by setup/hold-priority preemption.

The controller shall keep receiving IPv4 `PacketIn` notifications from the edge OpenFlow switches through the existing bootstrap rules. It shall not install temporary service-specific suppression flows. Repeated packets shall be controlled through:

1. Directional classification caching.
2. Per-switch classification evidence records.
3. Pair-level classification consensus.
4. Pair-level active policy ownership.
5. Canonical policy hashing.
6. Setup/hold-priority preemption.
7. Directional delegated-LSP application records.
8. Pair-level and direction-level locking.
9. Lazy state expiration and optional registry cleanup.

The final model does **not** use OpenFlow suppression flow TTL as the policy lifecycle mechanism. Since suppression flows are removed, the active policy can be refreshed directly by repeated `PacketIn` notifications for the same consensus-selected policy.

---

## 2. Final Design Decision

The final design removes runtime OpenFlow suppression flows and replaces them with a controller-side policy lifecycle.

The controller shall retain only the OpenFlow bootstrap rules that provide baseline forwarding and PacketIn copy behavior. It shall not install temporary ICMP, SSH, HTTP, DNS, FTP, NTP, or STREAMING suppression rules.

The controller shall not apply a pair-level active policy solely from one directional classification unless an explicit provisional mode is enabled. The preferred final model is:

```text
Directional PacketIn classification
  -> per-switch directional evidence storage
  -> pair-level consensus
  -> selected pair policy
  -> priority preemption against current active pair policy
  -> directional LSP application
```

### 2.1 Core Rule

A pair policy becomes active only after the controller has selected a consensus policy for the service pair.

```text
classification remains directional
policy ownership is pair-level
LSP application remains directional
OpenFlow bootstrap remains unchanged
OpenFlow suppression is removed
```

### 2.2 Why Consensus Is Required

A request packet and its reply may expose ports in opposite positions.

Example:

```text
Golf -> Hotel SSH request:
  src_port = 56792
  dst_port = 22

Hotel -> Golf SSH reply:
  src_port = 22
  dst_port = 56792
```

The classifier should usually identify both as SSH if either port remains `22`. However, a weak model may misclassify the reply if it overweights the destination port or sees an ephemeral destination port. Therefore, the controller must not blindly allow one directional classification to overwrite the pair-level policy.

The pair consensus model stores both directional classifications and decides the pair policy from the combined evidence.

---

## 3. Existing Baseline OpenFlow Behavior to Preserve

The existing `OpenflowBootstrapService` behavior remains valid and shall be preserved.

For each discovered OpenFlow edge switch, the bootstrap service creates four deterministic baseline flows:

1. ARP host-to-core.
2. ARP core-to-host.
3. IPv4 host-to-core with copy-to-controller enabled.
4. IPv4 core-to-host without copy-to-controller.

Only IPv4 traffic entering from the host-facing port is copied to the controller. The packet is still forwarded to the core-facing port, so PacketIn processing does not interrupt forwarding.

### 3.1 Required Baseline Rule Semantics

| Rule | Match | Action | Controller Copy |
|---|---|---|---|
| ARP host-to-core | `eth_type=0x0806`, `in_port=host` | output core | no |
| ARP core-to-host | `eth_type=0x0806`, `in_port=core` | output host | no |
| IPv4 host-to-core | `eth_type=0x0800`, `in_port=host` | controller + output core | yes |
| IPv4 core-to-host | `eth_type=0x0800`, `in_port=core` | output host | no |

### 3.2 Required Baseline Rule Naming

```text
sma-bootstrap-echo-arp-host-to-core
sma-bootstrap-echo-arp-core-to-host
sma-bootstrap-echo-ipv4-host-to-core
sma-bootstrap-echo-ipv4-core-to-host

sma-bootstrap-foxtrot-arp-host-to-core
sma-bootstrap-foxtrot-arp-core-to-host
sma-bootstrap-foxtrot-ipv4-host-to-core
sma-bootstrap-foxtrot-ipv4-core-to-host
```

### 3.3 Baseline XML Flow Body Templates

The controller shall continue using RESTCONF XML for baseline OpenFlow flow installation.

#### 3.3.1 ARP host-to-core template

```xml
<?xml version="1.0" encoding="UTF-8"?>
<flow xmlns="urn:opendaylight:flow:inventory">
  <id>${FLOW_ID}</id>
  <flow-name>${FLOW_ID}</flow-name>
  <table_id>${TABLE_ID}</table_id>
  <priority>${ARP_PRIORITY}</priority>
  <cookie>${COOKIE}</cookie>
  <idle-timeout>0</idle-timeout>
  <hard-timeout>0</hard-timeout>
  <flags>SEND_FLOW_REM</flags>
  <match>
    <in-port>${HOST_CONNECTOR_ID}</in-port>
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
            <output-node-connector>${CORE_PORT_NUMBER}</output-node-connector>
            <max-length>0</max-length>
          </output-action>
        </action>
      </apply-actions>
    </instruction>
  </instructions>
</flow>
```

#### 3.3.2 ARP core-to-host template

```xml
<?xml version="1.0" encoding="UTF-8"?>
<flow xmlns="urn:opendaylight:flow:inventory">
  <id>${FLOW_ID}</id>
  <flow-name>${FLOW_ID}</flow-name>
  <table_id>${TABLE_ID}</table_id>
  <priority>${ARP_PRIORITY}</priority>
  <cookie>${COOKIE}</cookie>
  <idle-timeout>0</idle-timeout>
  <hard-timeout>0</hard-timeout>
  <flags>SEND_FLOW_REM</flags>
  <match>
    <in-port>${CORE_CONNECTOR_ID}</in-port>
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
            <output-node-connector>${HOST_PORT_NUMBER}</output-node-connector>
            <max-length>0</max-length>
          </output-action>
        </action>
      </apply-actions>
    </instruction>
  </instructions>
</flow>
```

#### 3.3.3 IPv4 host-to-core template with PacketIn copy

```xml
<?xml version="1.0" encoding="UTF-8"?>
<flow xmlns="urn:opendaylight:flow:inventory">
  <id>${FLOW_ID}</id>
  <flow-name>${FLOW_ID}</flow-name>
  <table_id>${TABLE_ID}</table_id>
  <priority>${IPV4_PRIORITY}</priority>
  <cookie>${COOKIE}</cookie>
  <idle-timeout>0</idle-timeout>
  <hard-timeout>0</hard-timeout>
  <flags>SEND_FLOW_REM</flags>
  <match>
    <in-port>${HOST_CONNECTOR_ID}</in-port>
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
            <output-node-connector>${CORE_PORT_NUMBER}</output-node-connector>
            <max-length>0</max-length>
          </output-action>
        </action>
      </apply-actions>
    </instruction>
  </instructions>
</flow>
```

#### 3.3.4 IPv4 core-to-host template without PacketIn copy

```xml
<?xml version="1.0" encoding="UTF-8"?>
<flow xmlns="urn:opendaylight:flow:inventory">
  <id>${FLOW_ID}</id>
  <flow-name>${FLOW_ID}</flow-name>
  <table_id>${TABLE_ID}</table_id>
  <priority>${IPV4_PRIORITY}</priority>
  <cookie>${COOKIE}</cookie>
  <idle-timeout>0</idle-timeout>
  <hard-timeout>0</hard-timeout>
  <flags>SEND_FLOW_REM</flags>
  <match>
    <in-port>${CORE_CONNECTOR_ID}</in-port>
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
            <output-node-connector>${HOST_PORT_NUMBER}</output-node-connector>
            <max-length>0</max-length>
          </output-action>
        </action>
      </apply-actions>
    </instruction>
  </instructions>
</flow>
```

### 3.4 Suppression Flow Removal Requirement

The following runtime suppression flows shall not be installed anymore:

```text
sma-suppress-echo-icmp-host-golf
sma-suppress-foxtrot-icmp-host-hotel
sma-suppress-echo-tcp-dst-22-host-golf
sma-suppress-foxtrot-tcp-src-22-host-hotel
sma-suppress-echo-tcp-dst-80-host-golf
sma-suppress-foxtrot-tcp-src-80-host-hotel
sma-suppress-echo-udp-dst-53-host-golf
sma-suppress-foxtrot-udp-src-53-host-hotel
```

No service-specific suppression flow shall be created as part of classification, consensus, policy activation, policy refresh, policy preemption, LSP update, or workflow completion.

---

## 4. Policy Mapping Fields Used by the Controller

The classifier response includes a policy object derived from the traffic-class-to-policy mapping.

The controller shall retain these fields in every directional evidence record, consensus record, and active policy state:

```text
class_name
profile_name
dscp
mpls_tc
requested_bandwidth_kbps
requested_bandwidth_base64
setup_priority
hold_priority
```

The default profile is `best_effort_no_tunnel_required`, with DSCP `0`, MPLS TC `0`, requested bandwidth `0 kbps`, setup priority `7`, and hold priority `7`.

The class policies are:

| Class | Profile | DSCP | MPLS TC | Bandwidth kbps | Setup Priority | Hold Priority |
|---|---|---:|---:|---:|---:|---:|
| DNS | `dns_tunnel_policy` | 18 | 2 | 10000 | 4 | 4 |
| FTP | `ftp_tunnel_policy` | 10 | 1 | 25000 | 6 | 6 |
| HTTP | `http_tunnel_policy` | 0 | 0 | 25000 | 5 | 5 |
| ICMP | `icmp_tunnel_policy` | 16 | 2 | 10000 | 4 | 4 |
| NTP | `ntp_tunneL_policy` | 16 | 2 | 10000 | 4 | 4 |
| SSH | `ssh_tunnel_policy` | 26 | 3 | 10000 | 3 | 3 |
| STREAMING | `streaming_tunnel_policy` | 34 | 4 | 75000 | 3 | 3 |

### 4.1 Field Interpretation

DSCP and MPLS TC are retained as part of the readable policy state and hash. They must not be used alone as identity or preemption fields because some values can repeat across classes.

Setup priority and hold priority are the primary preemption fields. Lower numeric values represent stronger RSVP-TE priority.

### 4.2 Current Mapping Caveat

In the current mapping, SSH and STREAMING both use setup priority `3` and hold priority `3`. Therefore, priority alone cannot distinguish SSH from STREAMING in a conflict. For this reason, the consensus service also requires service-key and well-known-port conflict handling.

---

## 5. Conceptual Model

The implementation shall separate the following concepts.

### 5.1 Directional Classification Evidence

A directional evidence record is the result of classifying one PacketIn from one ingress switch and one observed direction.

Examples:

```text
ECHO / host-golf / lsr1_to_lsr4 / SSH
FOXTROT / host-hotel / lsr4_to_lsr1 / SSH
```

Directional evidence is stored per side. It is not itself the active pair policy.

### 5.2 Pair-Level Classification Consensus

A pair-level consensus record combines the directional evidence observed on both sides of the same service pair.

For pair key `lsr1_lsr4`, a full consensus may contain:

```text
left evidence:  ECHO    / lsr1_to_lsr4 / SSH
right evidence: FOXTROT / lsr4_to_lsr1 / SSH
selected policy: SSH
```

If the two directional policy hashes match, the consensus is direct. If they differ, the consensus service resolves the conflict using service-key and priority rules.

### 5.3 Pair-Level Active Policy

A pair-level active policy is the selected consensus policy that currently owns the LSR1-LSR4 service pair.

There shall be only one active pair policy per pair key at a time.

Example:

```text
pair_key = lsr1_lsr4
active_policy = SSH / ssh_tunnel_policy
```

The active pair policy is not directional. It represents the shared service behavior across both directions of the LSR1-LSR4 pair.

### 5.4 Directional LSP Applications

LSP updates remain directional because PCEP and RSVP-TE tunnels are directional.

For pair key `lsr1_lsr4`, the managed child LSP directions are:

```text
lsr1_to_lsr4 -> sma-lsr1-lsr4-delegated / tunnel-te110
lsr4_to_lsr1 -> sma-lsr4-lsr1-delegated / tunnel-te410
```

A pair policy activation may produce one or more directional LSP application records.

### 5.5 Packet Direction

Packet direction is still used to identify ingress, resolve the pair, and determine the observed direction.

However, the active policy hash shall not include `direction_key`. The same class in the reverse direction should match the same pair-level policy.

### 5.6 Policy Hash

The policy hash identifies the semantic policy owner. It is not a replacement for readable state. Every hash shall be stored alongside a complete readable object.

### 5.7 Desired LSP State Hash

A separate directional desired LSP state hash may be used for child LSP update idempotency.

This hash identifies the exact directional LSP state to apply and may include direction, ERO, bandwidth, priorities, LSP name, and PLSP ID.

---

## 6. Consensus Model

### 6.1 Evidence Collection

Every supported IPv4 PacketIn shall be classified or resolved from classification cache.

The result shall be written as directional evidence before any pair-level policy decision is made.

```text
PacketIn from ECHO
  -> classify
  -> store ECHO-side evidence

PacketIn from FOXTROT
  -> classify
  -> store FOXTROT-side evidence
```

### 6.2 Consensus Readiness

A consensus bucket is ready when it has usable evidence from both sides of the pair for the same canonical service key within the evidence TTL.

Default final mode:

```text
SMA_PAIR_CONSENSUS_REQUIRE_BOTH_DIRECTIONS=true
```

If only one side has evidence, the controller shall record `PENDING_ONE_SIDE` and shall not apply a pair policy unless provisional mode is explicitly enabled.

Optional test mode:

```text
SMA_PAIR_CONSENSUS_SINGLE_SIDE_PROVISIONAL_ENABLED=false
```

### 6.3 Canonical Service Key

The service key normalizes request and reply packets into one service identity.

For TCP/UDP, the canonical service port should be chosen in this order:

```text
1. destination port if destination port is well-known or configured as a known service port
2. source port if source port is well-known or configured as a known service port
3. destination port as fallback
4. source port as fallback
```

Example:

```text
src=56792 dst=22 -> canonical_service_port=22
src=22 dst=56792 -> canonical_service_port=22
```

For ICMP:

```text
service_key = eth_type=2048|ip_proto=1
```

The canonical service key helps correlate bidirectional traffic even when request and reply ports are reversed.

### 6.4 Direct Consensus

If both directional evidence hashes match:

```text
left_evidence.policy_hash == right_evidence.policy_hash
```

Then:

```text
consensus_status = CONSENSUS_MATCH
selected_policy = either side
```

The selected policy is then passed to the pair-policy preemption stage.

### 6.5 Conflict Consensus

If both directional evidence hashes differ:

```text
left_evidence.policy_hash != right_evidence.policy_hash
```

Then the controller shall resolve the conflict in this order:

1. Service-key correction.
2. Setup priority comparison.
3. Hold priority comparison.
4. Existing active policy preservation if one side matches the current active policy.
5. Deterministic unresolved handling.

### 6.6 Service-Key Correction

If both sides share the same canonical well-known service port, and one evidence class is the configured expected class for that service port, that class shall be preferred.

Example:

```text
left evidence:
  src=56792 dst=22 class=SSH

right evidence:
  src=22 dst=56792 class=STREAMING

canonical_service_port=22
expected class for port 22=SSH

selected_policy=SSH
consensus_status=CONSENSUS_CONFLICT_SERVICE_KEY_SELECTED
```

This rule prevents a misclassified SSH reply from forcing a STREAMING pair policy.

### 6.7 Priority Conflict Resolution

If service-key correction cannot resolve the conflict, setup and hold priorities are used.

Incoming conflict winner selection:

```text
candidate_a.setup_priority < candidate_b.setup_priority -> candidate_a wins
candidate_a.setup_priority > candidate_b.setup_priority -> candidate_b wins
```

If setup priority ties:

```text
candidate_a.hold_priority < candidate_b.hold_priority -> candidate_a wins
candidate_a.hold_priority > candidate_b.hold_priority -> candidate_b wins
```

Lower numeric values are stronger.

### 6.8 Equal-Priority Conflict

If both candidates have equal setup and hold priorities and the policy hashes differ:

1. If the current active pair policy matches one of the candidates, retain the current active policy.
2. If no active policy matches, do not install either policy by default.
3. Log `CONSENSUS_CONFLICT_EQUAL_PRIORITY_UNRESOLVED`.

Optional deterministic tie-breaker may be enabled only for testing:

```text
SMA_PAIR_CONSENSUS_EQUAL_PRIORITY_ACTION=KEEP_CURRENT_OR_DEFER
```

Alternative non-default:

```text
SMA_PAIR_CONSENSUS_EQUAL_PRIORITY_ACTION=CLASS_ORDER
```

### 6.9 Consensus Output

The consensus service shall return a selected `PairPolicyCandidate` only when the decision is actionable.

Actionable statuses:

```text
CONSENSUS_MATCH
CONSENSUS_CONFLICT_SERVICE_KEY_SELECTED
CONSENSUS_CONFLICT_PRIORITY_SELECTED
CONSENSUS_TIMEOUT_SINGLE_SIDE_PROVISIONAL
```

Non-actionable statuses:

```text
PENDING_ONE_SIDE
CONSENSUS_CONFLICT_EQUAL_PRIORITY_UNRESOLVED
CONSENSUS_CONFLICT_CURRENT_POLICY_PRESERVED
```

---

## 7. Required Data Models

### 7.1 `TunnelPairDefinition`

```java
public record TunnelPairDefinition(
    String pairKey,
    TunnelDirection forwardDirection,
    TunnelDirection reverseDirection,
    String leftRouterId,
    String rightRouterId,
    String leftSwitchName,
    String rightSwitchName
) {}
```

Example:

```text
pair_key = lsr1_lsr4
forward_direction = lsr1_to_lsr4
reverse_direction = lsr4_to_lsr1
left_router_id = 11.11.11.11
right_router_id = 14.14.14.14
left_switch_name = ECHO
right_switch_name = FOXTROT
```

### 7.2 `ServiceKey`

```java
public record ServiceKey(
    int ethType,
    int ipProtocol,
    int canonicalServicePort,
    String normalizedValue
) {}
```

Examples:

```text
eth_type=2048|ip_proto=1
eth_type=2048|ip_proto=6|service_port=22
eth_type=2048|ip_proto=17|service_port=53
```

### 7.3 `DirectionalPolicyEvidence`

```java
public record DirectionalPolicyEvidence(
    String pairKey,
    String directionKey,
    String ingressSwitchName,
    String ingressConnectorName,
    PacketFeatures packetFeatures,
    ServiceKey serviceKey,
    String className,
    String profileName,
    int dscp,
    int mplsTc,
    int requestedBandwidthKbps,
    String requestedBandwidthBase64,
    int setupPriority,
    int holdPriority,
    String policySchemaVersion,
    String policyHash,
    Instant observedAt,
    Instant expiresAt
) {}
```

### 7.4 `PairConsensusBucket`

```java
public record PairConsensusBucket(
    String pairKey,
    ServiceKey serviceKey,
    Optional<DirectionalPolicyEvidence> leftEvidence,
    Optional<DirectionalPolicyEvidence> rightEvidence,
    Instant createdAt,
    Instant updatedAt,
    Instant expiresAt
) {}
```

### 7.5 `PairConsensusDecision`

```java
public record PairConsensusDecision(
    String pairKey,
    ServiceKey serviceKey,
    String consensusStatus,
    Optional<PairPolicyCandidate> selectedCandidate,
    Optional<DirectionalPolicyEvidence> leftEvidence,
    Optional<DirectionalPolicyEvidence> rightEvidence,
    String conflictResolutionReason,
    Instant decidedAt
) {}
```

### 7.6 `PairPolicyCandidate`

A candidate policy is the selected consensus policy that may be applied or compared against the active policy.

```java
public record PairPolicyCandidate(
    String pairKey,
    ServiceKey serviceKey,
    String selectedFromDirectionKey,
    String selectedFromIngressSwitchName,
    String className,
    String profileName,
    int dscp,
    int mplsTc,
    int requestedBandwidthKbps,
    String requestedBandwidthBase64,
    int setupPriority,
    int holdPriority,
    String policySchemaVersion,
    String policyHash,
    String consensusStatus,
    Instant selectedAt
) {}
```

### 7.7 `ActivePairPolicyState`

```java
public record ActivePairPolicyState(
    String pairKey,
    ServiceKey serviceKey,
    String className,
    String profileName,
    int dscp,
    int mplsTc,
    int requestedBandwidthKbps,
    String requestedBandwidthBase64,
    int setupPriority,
    int holdPriority,
    String policySchemaVersion,
    String policyHash,
    long generation,
    Instant installedAt,
    Instant lastRefreshedAt,
    Instant expiresAt,
    Map<String, DirectionalLspApplicationRecord> lspApplications
) {}
```

### 7.8 `DirectionalLspDesiredState`

```java
public record DirectionalLspDesiredState(
    String pairKey,
    String directionKey,
    String pccNode,
    String lspName,
    String tunnelInterfaceName,
    long plspId,
    long tunnelId,
    int requestedBandwidthKbps,
    String requestedBandwidthBase64,
    int setupPriority,
    int holdPriority,
    List<EroSubobject> desiredEro,
    String desiredEroFingerprint,
    String desiredLspStateHash
) {}
```

### 7.9 `DirectionalLspApplicationRecord`

```java
public record DirectionalLspApplicationRecord(
    UUID operationId,
    UUID workflowId,
    long packetSequence,
    String pairKey,
    String directionKey,
    String policyHash,
    String desiredLspStateHash,
    String lspName,
    String pccNode,
    long plspId,
    String status,
    Integer updateLspHttpStatus,
    boolean updateLspSent,
    boolean pcepEroConfirmed,
    boolean pcepBandwidthConfirmed,
    Instant startedAt,
    Instant completedAt
) {}
```

---

## 8. Required New and Modified Classes

### 8.1 `TunnelPairRegistry`

Responsibility:

- Normalize incoming directions into a stable pair key.
- Return all managed child tunnel directions for a pair.
- Resolve left/right switch ownership for consensus buckets.

Required methods:

```java
TunnelPairDefinition requirePairForDirection(String directionKey);
List<TunnelDirection> requireManagedDirections(String pairKey);
String normalizePairKey(TunnelDirection direction);
String sideForSwitch(String pairKey, String ingressSwitchName); // LEFT or RIGHT
```

### 8.2 `ServiceKeyResolver`

Responsibility:

- Build a canonical service key from packet features.
- Normalize request and reply traffic into the same service identity.
- Provide service-port expected-class hints.

Required methods:

```java
ServiceKey resolve(PacketFeatures features);
Optional<String> expectedClassFor(ServiceKey serviceKey);
boolean isKnownServicePort(int port);
```

Configured service-port hints:

```text
20,21 -> FTP
22 -> SSH
53 -> DNS
80,8080,443 -> HTTP
123 -> NTP
```

### 8.3 `PairPolicyHashService`

Responsibility:

- Produce deterministic canonical policy hashes.
- Produce deterministic directional LSP state hashes.
- Store readable canonical strings in DEBUG logs when troubleshooting is enabled.

Required methods:

```java
String hashDirectionalEvidence(DirectionalPolicyEvidence evidence);
String hashPolicyCandidate(PairPolicyCandidate candidate);
String hashActivePolicyFields(...);
String hashDesiredLspState(DirectionalLspDesiredState desiredState);
String canonicalEvidenceString(DirectionalPolicyEvidence evidence);
String canonicalPolicyString(PairPolicyCandidate candidate);
```

Canonical policy fields:

```text
pair_key
service_key
class_name
profile_name
dscp
mpls_tc
requested_bandwidth_kbps
requested_bandwidth_base64
setup_priority
hold_priority
policy_schema_version
hash_version
```

Canonical policy hash must not include:

```text
direction_key
workflow_id
packet_sequence
timestamp
computed_ero
PCEP reported state
OpenFlow node ID
```

### 8.4 `DirectionalClassificationEvidenceRegistry`

Responsibility:

- Store directional classification evidence per pair and service key.
- Keep separate left-side and right-side evidence.
- Expire stale evidence.
- Provide a consensus bucket view.

Required methods:

```java
PairConsensusBucket recordEvidence(DirectionalPolicyEvidence evidence);
Optional<PairConsensusBucket> findBucket(String pairKey, ServiceKey serviceKey, Instant now);
void expireOldEvidence(Instant now);
Map<String, PairConsensusBucket> snapshot();
```

### 8.5 `PairPolicyConsensusService`

Responsibility:

- Determine whether pair evidence is ready.
- Compare left and right policy hashes.
- Resolve conflicts using service-key and priority rules.
- Return an actionable or non-actionable consensus decision.

Required method:

```java
PairConsensusDecision evaluate(
    PairConsensusBucket bucket,
    Optional<ActivePairPolicyState> currentActivePolicy,
    Instant now
);
```

Required decision enum:

```java
public enum PairConsensusStatus {
    PENDING_ONE_SIDE,
    CONSENSUS_MATCH,
    CONSENSUS_CONFLICT_SERVICE_KEY_SELECTED,
    CONSENSUS_CONFLICT_PRIORITY_SELECTED,
    CONSENSUS_CONFLICT_CURRENT_POLICY_PRESERVED,
    CONSENSUS_CONFLICT_EQUAL_PRIORITY_UNRESOLVED,
    CONSENSUS_TIMEOUT_SINGLE_SIDE_PROVISIONAL
}
```

### 8.6 `ActivePairPolicyRegistry`

Responsibility:

- Store one active pair policy per pair key.
- Refresh TTL for same-policy consensus decisions.
- Replace expired or preempted policies.
- Provide thread-safe access.

Required methods:

```java
Optional<ActivePairPolicyState> findActive(String pairKey, Instant now);
Optional<ActivePairPolicyState> findIncludingExpired(String pairKey);
ActivePairPolicyState refresh(String pairKey, Instant now, Duration ttl);
ActivePairPolicyState installOrReplace(PairPolicyCandidate candidate, Map<String, DirectionalLspApplicationRecord> children, Instant now, Duration ttl);
void expireIfNeeded(String pairKey, Instant now);
void expireOldEntries(Instant now);
Map<String, ActivePairPolicyState> snapshot();
```

### 8.7 `PolicyPreemptionEvaluator`

Responsibility:

- Decide whether a consensus-selected policy may replace an active policy.
- Implement priority-based preemption after consensus, not before consensus.

Required method:

```java
PolicyPreemptionDecision evaluate(
    ActivePairPolicyState active,
    PairPolicyCandidate incoming,
    Instant now
);
```

Decision enum:

```java
public enum PolicyPreemptionDecisionType {
    SAME_POLICY_REFRESH,
    ACTIVE_EXPIRED_REPLACE,
    INCOMING_PRIORITY_PREEMPTS,
    ACTIVE_POLICY_RETAINED_WEAKER_INCOMING,
    ACTIVE_POLICY_RETAINED_EQUAL_PRIORITY_DIFFERENT_POLICY
}
```

### 8.8 `PairPolicyCoordinator`

Responsibility:

- Own the high-level pair-policy workflow after a packet is classified.
- Acquire pair-level locks.
- Record directional evidence.
- Call consensus.
- Call preemption only for actionable consensus decisions.
- Avoid repeated LSP updates for same active policy.
- Delegate LSP path computation and update to directional services.

Required method:

```java
PairPolicyDecision handleEvidence(
    DirectionalPolicyEvidence evidence,
    PacketWorkflowContext workflowContext
);
```

### 8.9 `DirectionalLspApplicationService`

Responsibility:

- Apply a consensus-selected pair policy to each directional delegated LSP.
- Compute direction-specific paths only when a policy is newly applied, replaced, or preempted.
- Never skip an LSP update based only on ERO equality.

Required method:

```java
DirectionalLspApplicationRecord applyPolicyToDirection(
    PairPolicyCandidate candidate,
    TunnelDirection direction,
    PacketWorkflowContext workflowContext
);
```

### 8.10 Classes to Remove or Disable

The following runtime suppression components shall be removed, disabled, or left unused:

```text
OpenFlowSuppressionService
OpenFlowSuppressionLeaseRegistry
OpenFlowSuppressionFlowSerializer
OpenFlowSuppressionPolicy
SuppressionFlowInstaller
SuppressionFlowVerifier
FlowRemoved listener for suppression lifecycle
```

If these classes already exist, they shall not be called by the PacketIn workflow.

---

## 9. Policy Hashing Rules

### 9.1 Why Hashes Are Used

Hashes are used to quickly determine whether two directional evidence records or an incoming consensus-selected policy are semantically identical to another policy.

The hash is not used as the only source of truth. A complete readable policy object must always be stored alongside the hash.

### 9.2 Directional Evidence Hash

Directional evidence hash fields:

```text
pair_key
service_key
class_name
profile_name
dscp
mpls_tc
requested_bandwidth_kbps
requested_bandwidth_base64
setup_priority
hold_priority
policy_schema_version
hash_version
```

The evidence hash must not include direction. Direction is stored in the readable record, but excluding direction allows the same class on the reverse side to produce the same pair-level hash.

### 9.3 Same Directional Evidence Hash

If both directional evidence hashes match:

```text
left.policy_hash == right.policy_hash
```

Then the consensus service selects that policy directly.

### 9.4 Different Directional Evidence Hash

If both directional evidence hashes differ:

```text
left.policy_hash != right.policy_hash
```

Then:

1. The controller shall compare readable fields.
2. The controller shall evaluate service-key correction.
3. The controller shall evaluate setup and hold priorities.
4. The controller shall not use ERO equality as the consensus decision.

### 9.5 Active Policy Hash

If the consensus-selected policy hash equals the active pair policy hash:

```text
incoming.policy_hash == active.policy_hash
```

Then:

1. The controller shall not call path computation.
2. The controller shall not send `update-lsp`.
3. The controller shall refresh the active policy TTL.
4. The controller shall log `pair_policy_refreshed_same_hash`.

### 9.6 Directional LSP State Hash

The directional desired LSP state hash is separate from the pair policy hash.

It may include:

```text
pair_key
direction_key
pcc_node
lsp_name
plsp_id
tunnel_id
requested_bandwidth_base64
setup_priority
hold_priority
desired_ero_fingerprint
algorithm
class_type
policy_hash
```

This hash is used to prevent duplicate `update-lsp` calls inside the same policy application cycle. It does not determine pair-policy ownership or consensus.

---

## 10. TTL and Refresh Model

Since runtime OpenFlow suppression is removed, same-class traffic continues generating PacketIn notifications through the baseline IPv4 host-to-core bootstrap flow.

Therefore, the controller can implement LRU-like refresh directly from PacketIn traffic.

### 10.1 Evidence TTL

Directional evidence shall expire so that stale request/reply classifications are not combined much later.

Default:

```text
SMA_PAIR_CONSENSUS_EVIDENCE_TTL_SECONDS=10
```

A consensus bucket is valid only if both directional evidence records are within this TTL.

### 10.2 Active Policy TTL

Default:

```text
SMA_ACTIVE_PAIR_POLICY_IDLE_TTL_SECONDS=60
```

When a policy is installed:

```text
expires_at = now + 60 seconds
```

When the same consensus-selected policy is observed again:

```text
expires_at = now + 60 seconds
last_refreshed_at = now
```

When a different policy arrives and does not preempt:

```text
expires_at is not modified
last_refreshed_at is not modified
```

### 10.3 TTL Is Not the Preemption Mechanism

The final model does not rely on suppression-rule TTL to determine preemption.

Priority preemption is based on setup and hold priorities after consensus selection.

TTL is used only to remove stale active state. If a policy expires, the pair has no active owner; the next actionable consensus decision may install a new policy. This is state cleanup, not priority preemption.

### 10.4 Expiration Strategy

The controller does not need one sleeping thread per policy.

Expiration shall be handled through:

1. Lazy expiration during PacketIn handling.
2. Optional periodic cleanup using a single `ScheduledExecutorService`.

Recommended optional cleanup interval:

```text
SMA_ACTIVE_PAIR_POLICY_SWEEPER_INTERVAL_SECONDS=15
```

The sweeper only removes expired registry records. It does not poll OpenFlow, because suppression flows are no longer used.

---

## 11. Priority Preemption Rules

Preemption shall be enabled in the final design.

Configuration:

```text
SMA_PAIR_POLICY_PREEMPTION_MODE=PRIORITY_PREEMPT
```

Preemption is evaluated only after the consensus service has produced an actionable selected policy.

### 11.1 Priority Interpretation

Lower numeric RSVP-TE priority values are stronger.

Example:

```text
setup_priority=3 is stronger than setup_priority=4
hold_priority=3 is stronger than hold_priority=4
```

### 11.2 Preemption Decision

A consensus-selected incoming policy preempts the active policy if:

```text
incoming.setup_priority < active.setup_priority
```

or:

```text
incoming.setup_priority == active.setup_priority
AND incoming.hold_priority < active.hold_priority
```

### 11.3 Same Priority Different Policy

If the incoming policy hash differs but setup and hold priorities are equal:

```text
incoming.setup_priority == active.setup_priority
incoming.hold_priority == active.hold_priority
incoming.policy_hash != active.policy_hash
```

Then the active policy remains in control until it expires or until a stronger policy arrives.

The incoming policy is deferred, not applied.

### 11.4 Weaker Incoming Policy

If the incoming policy is weaker:

```text
incoming.setup_priority > active.setup_priority
```

or:

```text
incoming.setup_priority == active.setup_priority
AND incoming.hold_priority > active.hold_priority
```

Then the active policy remains in control.

The incoming policy shall not refresh the active policy TTL.

### 11.5 Expired Active Policy

If the active policy has expired, the incoming consensus-selected policy may replace it regardless of priority.

### 11.6 Same Policy

If the incoming policy hash equals the active policy hash, it refreshes the TTL regardless of priority because it is the same active owner.

---

## 12. Final PacketIn Workflow

### 12.1 High-Level Workflow

```text
1. PacketIn received.
2. Extract packet features.
3. Ignore unsupported packets such as ARP, IPv6, or malformed payloads.
4. Resolve logical direction from ingress switch and connector.
5. Normalize direction to pair key.
6. Resolve canonical service key.
7. Classify packet or get cached classification.
8. Build directional policy evidence.
9. Compute directional evidence policy hash.
10. Acquire pair-level lock.
11. Store directional evidence in the consensus registry.
12. Evaluate pair consensus.
13. If consensus is pending or unresolved, stop without LSP work.
14. If consensus selected a policy, read active pair policy.
15. If no active policy exists, apply selected policy.
16. If active policy hash matches, refresh TTL and skip LSP work.
17. If active policy hash differs, evaluate setup/hold-priority preemption.
18. If selected policy cannot preempt, defer and skip LSP work.
19. If selected policy can preempt or active policy expired, apply selected policy.
20. Release pair-level lock.
21. Log final workflow decision.
```

### 12.2 One-Side Evidence

```text
PacketIn -> classification -> evidence stored -> missing reverse evidence
Result: PENDING_ONE_SIDE
```

Required actions:

1. Store directional evidence.
2. Do not compute path.
3. Do not send `update-lsp`.
4. Log pending consensus state.

### 12.3 Consensus Match

```text
Echo evidence hash == Foxtrot evidence hash
Result: CONSENSUS_MATCH
```

Required actions:

1. Build selected pair policy.
2. Compare against active pair policy.
3. Apply, refresh, or preempt according to active policy state.

### 12.4 Consensus Conflict

```text
Echo evidence hash != Foxtrot evidence hash
Result: CONSENSUS_CONFLICT_*
```

Required actions:

1. Resolve by service-key correction when possible.
2. Resolve by setup/hold priority when possible.
3. Retain current active policy if equal-priority conflict is unresolved and current active policy matches one side.
4. Otherwise defer.

### 12.5 No Active Policy

```text
Consensus selected candidate -> no active policy
Result: APPLY_NEW_CONSENSUS_POLICY
```

Required actions:

1. Compute paths for managed directions.
2. Build directional desired LSP states.
3. Send `update-lsp` where required.
4. Register active pair policy only after successful application criteria are met.
5. Set TTL to `now + SMA_ACTIVE_PAIR_POLICY_IDLE_TTL_SECONDS`.

### 12.6 Same Active Policy

```text
Consensus selected candidate hash == active hash
Result: SAME_POLICY_REFRESH
```

Required actions:

1. Refresh active policy TTL.
2. Do not compute path.
3. Do not send `update-lsp`.
4. Do not modify OpenFlow rules.

### 12.7 Different Policy, Weaker or Equal Priority

```text
Consensus selected candidate hash != active hash
Incoming does not preempt
Result: DEFERRED_ACTIVE_POLICY_RETAINED
```

Required actions:

1. Do not refresh active policy TTL.
2. Do not compute path for incoming policy.
3. Do not send `update-lsp`.
4. Log incoming class, active class, priorities, consensus reason, and active expiration.

### 12.8 Different Policy, Stronger Priority

```text
Consensus selected candidate hash != active hash
Incoming priority is stronger
Result: PREEMPTED_BY_PRIORITY
```

Required actions:

1. Compute paths for managed directions.
2. Send `update-lsp` where required.
3. Replace active pair policy after successful application criteria are met.
4. Set new TTL to `now + SMA_ACTIVE_PAIR_POLICY_IDLE_TTL_SECONDS`.

### 12.9 Expired Active Policy

```text
Consensus selected candidate -> active policy expired
Result: REPLACED_EXPIRED_POLICY
```

Required actions:

1. Treat selected candidate as the new owner.
2. Compute paths and update LSPs as required.
3. Replace active policy state.

---

## 13. Directional LSP Application Rules

### 13.1 Managed Directions

For pair key `lsr1_lsr4`, the default managed directions shall be:

```text
lsr1_to_lsr4
lsr4_to_lsr1
```

Configuration:

```text
SMA_PAIR_POLICY_LSP_APPLICATION_SCOPE=BIDIRECTIONAL_PAIR
```

Alternative test mode:

```text
SMA_PAIR_POLICY_LSP_APPLICATION_SCOPE=OBSERVED_DIRECTION_ONLY
```

The recommended final thesis mode is `BIDIRECTIONAL_PAIR` because the active policy is pair-level and consensus-based.

### 13.2 Update Requirement

The controller shall send `update-lsp` when any of the following are true:

1. Desired bandwidth differs from PCEP-reported bandwidth.
2. Desired ERO differs from PCEP-reported ERO.
3. Desired setup or hold priority differs from the intended LSPA priority, if those fields are included in the update payload and available in PCEP state.
4. No recent successful directional LSP application exists for the same desired LSP state hash.

The controller shall not skip `update-lsp` merely because the ERO matches.

### 13.3 Already Converged Rule

The controller may skip `update-lsp` for a direction only if:

```text
current_ero == desired_ero
AND current_bandwidth_base64 == desired_bandwidth_base64
AND current_lsp is delegated
AND current_lsp is administrative up
AND current_lsp is operational up
```

If ERO matches but bandwidth is `AAAAAA==` while desired bandwidth is non-zero, the update is required.

### 13.4 Directional Application Statuses

Required statuses:

```text
UPDATE_REQUIRED_NO_CURRENT_STATE
UPDATE_REQUIRED_BANDWIDTH_MISMATCH
UPDATE_REQUIRED_PATH_MISMATCH
UPDATE_REQUIRED_PRIORITY_MISMATCH
UPDATE_SENT_ACCEPTED
SKIPPED_ALREADY_CONVERGED
FAILED_UPDATE_LSP_HTTP
FAILED_UPDATE_LSP_VALIDATION
ACCEPTED_PCEP_BANDWIDTH_UNCONFIRMED
```

`ACCEPTED_PCEP_BANDWIDTH_UNCONFIRMED` may only be used after an actual `update-lsp` request was sent and accepted by OpenDaylight.

---

## 14. Pair-Level Locking

Pair-level locking is required because the active policy and consensus state are pair-level.

Lock key:

```text
pair_key
```

Example:

```text
lock(lsr1_lsr4)
```

All evidence storage, consensus evaluation, preemption evaluation, active state replacement, and directional LSP application scheduling shall be protected by the pair lock.

The controller may still use direction-level locks inside the directional LSP application service to avoid overlapping updates to the same delegated LSP.

Recommended lock ordering:

```text
1. pair lock
2. direction lock lsr1_to_lsr4
3. direction lock lsr4_to_lsr1
```

When both direction locks are required, acquire them in deterministic lexical order to avoid deadlock.

---

## 15. Configuration Requirements

### 15.1 OpenFlow Bootstrap

```text
SMA_OPENFLOW_BOOTSTRAP_ENABLED=true
SMA_OPENFLOW_INSTALL_DEFAULT_DROP=false
SMA_OPENFLOW_TABLE_ID=0
SMA_OPENFLOW_ARP_PRIORITY=300
SMA_OPENFLOW_IPV4_PRIORITY=200
```

### 15.2 OpenFlow Suppression Removal

```text
SMA_OPENFLOW_SUPPRESSION_ENABLED=false
```

These settings shall be removed or ignored:

```text
SMA_OPENFLOW_SUPPRESSION_IDLE_TIMEOUT_SECONDS
SMA_OPENFLOW_SUPPRESSION_HARD_TIMEOUT_SECONDS
SMA_OPENFLOW_SUPPRESSION_PRIORITY
SMA_OPENFLOW_SUPPRESSION_COOKIE_BASE
SMA_OPENFLOW_SUPPRESSION_VERIFY_OPERATIONAL
```

### 15.3 Pair Consensus

```text
SMA_PAIR_CONSENSUS_ENABLED=true
SMA_PAIR_CONSENSUS_REQUIRE_BOTH_DIRECTIONS=true
SMA_PAIR_CONSENSUS_EVIDENCE_TTL_SECONDS=10
SMA_PAIR_CONSENSUS_SINGLE_SIDE_PROVISIONAL_ENABLED=false
SMA_PAIR_CONSENSUS_EQUAL_PRIORITY_ACTION=KEEP_CURRENT_OR_DEFER
SMA_PAIR_CONSENSUS_CONFLICT_MODE=SERVICE_KEY_THEN_PRIORITY
```

### 15.4 Active Pair Policy

```text
SMA_ACTIVE_PAIR_POLICY_IDLE_TTL_SECONDS=60
SMA_ACTIVE_PAIR_POLICY_SWEEPER_ENABLED=true
SMA_ACTIVE_PAIR_POLICY_SWEEPER_INTERVAL_SECONDS=15
SMA_PAIR_POLICY_PREEMPTION_MODE=PRIORITY_PREEMPT
SMA_PAIR_POLICY_HASH_VERSION=1
SMA_PAIR_POLICY_LSP_APPLICATION_SCOPE=BIDIRECTIONAL_PAIR
```

### 15.5 LSP Application

```text
SMA_LSP_APPLICATION_REQUIRE_ALL_DIRECTIONS=true
SMA_LSP_APPLICATION_REAPPLY_ON_BANDWIDTH_MISMATCH=true
SMA_LSP_APPLICATION_REAPPLY_ON_ERO_MISMATCH=true
SMA_LSP_APPLICATION_REAPPLY_ON_PRIORITY_MISMATCH=true
```

### 15.6 Caching

```text
SMA_CLASSIFICATION_CACHE_TTL_SECONDS=3600
SMA_CALCULATED_PATH_CACHE_TTL_SECONDS=60
```

The classification cache may live longer than the active pair policy. The active pair policy determines current tunnel ownership; the classification cache only avoids repeated ML API calls.

---

## 16. Required Observability Events

### 16.1 Directional Evidence Events

```text
directional_policy_evidence_built
directional_policy_evidence_hash_computed
directional_policy_evidence_recorded
directional_policy_evidence_expired
```

### 16.2 Pair Consensus Events

```text
pair_consensus_bucket_created
pair_consensus_pending_one_side
pair_consensus_match
pair_consensus_conflict_detected
pair_consensus_service_key_selected
pair_consensus_priority_selected
pair_consensus_equal_priority_unresolved
pair_consensus_current_policy_preserved
pair_consensus_candidate_selected
```

### 16.3 Pair Policy Events

```text
pair_policy_candidate_built
pair_policy_lookup_started
pair_policy_not_found
pair_policy_same_hash_refresh
pair_policy_different_hash_detected
pair_policy_preemption_evaluated
pair_policy_deferred_active_retained
pair_policy_preempted_by_priority
pair_policy_expired_replaced
pair_policy_installed
pair_policy_registry_expired
```

### 16.4 Directional LSP Events

```text
directional_lsp_application_started
directional_lsp_desired_state_built
directional_lsp_state_hash_computed
directional_lsp_update_required
directional_lsp_update_skipped_converged
directional_lsp_update_sent
directional_lsp_update_accepted
directional_lsp_update_failed
directional_lsp_application_completed
```

### 16.5 Final Workflow Events

```text
packet_workflow_completed
packet_workflow_deferred
packet_workflow_pending_consensus
packet_workflow_failed
```

Final workflow metadata shall include:

```text
workflow_id
packet_sequence
pair_key
service_key
observed_direction_key
ingress_switch
ingress_connector
class_name
profile_name
directional_evidence_hash
left_evidence_hash
right_evidence_hash
consensus_status
consensus_selected_class
incoming_policy_hash
active_policy_hash_before
preemption_decision
active_policy_expires_at
processed_direction_keys
lsp_application_statuses
```

---

## 17. Metrics Requirements

Required metrics:

```text
sma_directional_evidence_recorded_total
sma_pair_consensus_pending_total
sma_pair_consensus_match_total
sma_pair_consensus_conflict_total
sma_pair_consensus_service_key_selected_total
sma_pair_consensus_priority_selected_total
sma_pair_consensus_unresolved_total
sma_pair_policy_active_total
sma_pair_policy_refresh_total
sma_pair_policy_preempt_total
sma_pair_policy_deferred_total
sma_pair_policy_expired_total
sma_pair_policy_apply_failure_total
sma_lsp_update_sent_total
sma_lsp_update_skipped_converged_total
sma_lsp_update_bandwidth_mismatch_total
sma_packet_workflow_deferred_total
sma_packet_workflow_pending_consensus_total
```

---

## 18. Removal Impact

Removing runtime OpenFlow suppression means PacketIn volume will be higher than in the suppression design. This is acceptable for the thesis demonstrator because:

1. Baseline forwarding remains uninterrupted.
2. Classification cache prevents repeated ML API calls.
3. Pair consensus prevents one-direction misclassification from immediately owning the pair policy.
4. Same-policy hash matching prevents repeated path computation and LSP update.
5. Setup/hold-priority preemption provides deterministic policy replacement.
6. Pair-level locking prevents direction race conditions.
7. The implementation no longer needs operational OpenFlow polling or FlowRemoved lifecycle logic.

---

## 19. Acceptance Criteria

### 19.1 First SSH Request Evidence

Given no active policy:

```text
SSH-like PacketIn from ECHO host-golf
src_port = 56792
dst_port = 22
```

Expected:

```text
classification = SSH
pair_key = lsr1_lsr4
service_key = tcp:22
evidence stored for ECHO/LEFT
consensus_status = PENDING_ONE_SIDE
no path computation
no update-lsp
```

### 19.2 SSH Reply Evidence With Correct Classification

Given left-side SSH evidence already exists:

```text
SSH-like PacketIn from FOXTROT host-hotel
src_port = 22
dst_port = 56792
classification = SSH
```

Expected:

```text
right evidence stored
left hash == right hash
consensus_status = CONSENSUS_MATCH
selected policy = SSH
pair policy installed if no active policy exists
directional LSP applications dispatched for lsr1_to_lsr4 and lsr4_to_lsr1
```

### 19.3 SSH Reply Misclassified as STREAMING

Given left-side SSH evidence already exists:

```text
PacketIn from FOXTROT host-hotel
src_port = 22
dst_port = 56792
classification = STREAMING
```

Expected:

```text
right evidence stored
left hash != right hash
service_key = tcp:22
expected class for service key = SSH
consensus_status = CONSENSUS_CONFLICT_SERVICE_KEY_SELECTED
selected policy = SSH
STREAMING does not overwrite SSH solely because it appeared on the return side
```

### 19.4 ICMP Consensus

Given ICMP PacketIns arrive from both ECHO and FOXTROT:

```text
eth_type = 2048
ip_proto = 1
```

Expected:

```text
service_key = icmp
left evidence class = ICMP
right evidence class = ICMP
consensus_status = CONSENSUS_MATCH
selected policy = ICMP
```

### 19.5 Repeated Same Consensus Within TTL

Given active ICMP policy:

```text
new ICMP consensus selected
```

Expected:

```text
incoming policy hash == active policy hash
active policy TTL refreshed
no path computation
no update-lsp
```

### 19.6 SSH Consensus During Active ICMP

Given active ICMP policy with setup/hold `4/4`:

```text
SSH consensus selected
```

Expected:

```text
incoming setup/hold = 3/3
incoming priority stronger
ICMP policy preempted
SSH policy installed
LSP update sent if required
active policy becomes SSH
```

### 19.7 ICMP Consensus During Active SSH

Given active SSH policy with setup/hold `3/3`:

```text
ICMP consensus selected
```

Expected:

```text
incoming setup/hold = 4/4
incoming priority weaker
SSH policy retained
SSH TTL not refreshed by ICMP
no path computation for ICMP
no update-lsp for ICMP
```

### 19.8 Equal Priority Different Policy

Given active SSH policy with setup/hold `3/3`:

```text
STREAMING consensus selected
```

Current mapping expected:

```text
STREAMING setup/hold = 3/3
hash differs
priority equal
current SSH policy retained until expiry unless deterministic tie-breaker is explicitly enabled
```

### 19.9 Expired Policy Replacement

Given an expired active policy and an actionable consensus decision:

```text
Any tunneled class consensus selected
```

Expected:

```text
expired policy removed or replaced
selected consensus policy becomes active
LSP update sent if required
```

### 19.10 ERO Match but Bandwidth Mismatch

Given PCEP reports:

```text
current ERO == desired ERO
current bandwidth = AAAAAA==
desired bandwidth != AAAAAA==
```

Expected:

```text
update-lsp is sent
operation is not skipped
```

---

## 20. Implementation Order

1. Disable/remove runtime OpenFlow suppression workflow calls.
2. Preserve OpenFlow bootstrap unchanged.
3. Add `TunnelPairRegistry` and pair-key normalization.
4. Add `ServiceKeyResolver`.
5. Add `PairPolicyHashService` updates for directionless policy/evidence hashing.
6. Add `DirectionalClassificationEvidenceRegistry`.
7. Add `PairPolicyConsensusService`.
8. Add `ActivePairPolicyRegistry`.
9. Add `PolicyPreemptionEvaluator`.
10. Add `PairPolicyCoordinator`.
11. Refactor workflow service to record evidence and call pair consensus after classification.
12. Refactor LSP update logic into directional child application service.
13. Ensure `update-lsp` is not skipped on ERO-only match.
14. Add structured logs and metrics.
15. Add tests for SSH request/reply, SSH reply misclassified as STREAMING, ICMP, equal-priority conflicts, stronger-priority preemption, expiration, and bandwidth mismatch.

---

## 21. Final Summary

The final implementation removes runtime OpenFlow suppression rules and replaces them with controller-side pair-policy consensus and priority preemption.

The controller shall keep receiving IPv4 PacketIn notifications through the baseline OpenFlow host-to-core rule. It shall classify each supported PacketIn or resolve it from cache, store directional evidence per switch, and build pair-level consensus from both sides of the service pair.

The active policy is pair-level. Directionality applies only to evidence origin, ingress context, and child LSP updates. The same traffic class observed from either side contributes to the same pair-level policy decision.

Preemption happens only after consensus selects a candidate policy. The preemption decision uses setup priority first and hold priority second. DSCP and MPLS TC remain part of the readable policy state and hash, but they are not sufficient by themselves for preemption.

The controller shall never treat ERO equality alone as proof that the desired tunnel policy is active. Bandwidth, priority, policy identity, consensus status, and operation history must be considered explicitly.
