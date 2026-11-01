# Application Construction — Pair Policy Consensus and Preemption

<show-structure for="chapter" depth="3"/>

The Pair Policy Consensus and Preemption mechanism converts directional policy evidence into one of three outcomes:
a non-actionable result, a refresh of an existing active policy, or a candidate that is applied to both managed delegated
LSP directions. <code>PairPolicyCoordinator</code> owns the transition among these outcomes. It records evidence,
evaluates whether the two directions provide usable consensus, compares an accepted candidate with existing active
state, invokes directional application when replacement is permitted, and installs active state only after both
directional applications report success.

The preceding [Application Construction — PacketIn Classification Workflow](Java-Controller-Side-Application-—-Application-Construction-PacketIn-Classification-Workflow.md#directional-evidence-construction)
topic owns the construction and hashing of one <code>DirectionalPolicyEvidence</code> record. It also explains how
one PacketIn records a side and receives an immediate pending decision rather than blocking. This topic begins when
that evidence reaches the coordinator. It focuses on consensus, active-state comparison, concurrency, state retention,
and the conditions that permit or prevent directional application. Path calculation and delegated-LSP protocol updates
are downstream responsibilities and are not redefined here.

## Coordination boundary and serialized decision scope {id="pair-policy-coordination-boundary"}

The coordinator is assembled once by the provider with an evidence registry, consensus service, active-policy
registry, preemption evaluator, directional LSP application service, metrics collector, and tunnel-pair registry. It
does not classify packets and it does not own the lifecycle of topology discovery or PCEP clients. Its job is to keep
the policy decision for one managed pair internally consistent while those other components provide the already
prepared evidence and perform any permitted side effects.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/policy/PairPolicyCoordinator.java" include-lines="39-91"/>

<code-block lang="mermaid">
flowchart LR
    Evidence["DirectionalPolicyEvidence"] --> Coordinator["PairPolicyCoordinator"]
    Coordinator --> EvidenceRegistry["DirectionalClassificationEvidenceRegistry"]
    Coordinator --> Consensus["PairPolicyConsensusService"]
    Coordinator --> ActiveRegistry["ActivePairPolicyRegistry"]
    Coordinator --> Preemption["PolicyPreemptionEvaluator"]
    Coordinator --> Application["DirectionalLspApplicationService"]
    Application --> Paths["path computation"]
    Application --> Lsp["delegated LSP service"]
    Coordinator --> Decision["PairPolicyDecision"]
    Coordinator --> Metrics["SdnMplsMlMetrics"]
</code-block>

A <code>ConcurrentHashMap</code> stores one <code>ReentrantLock</code> per pair key. The coordinator acquires that lock
before it records evidence, reads active state, evaluates consensus, refreshes or replaces a policy, and applies a
candidate. Decisions for the same pair therefore cannot interleave their evidence, preemption, and installation
steps. Different pair keys can proceed concurrently because they resolve to distinct locks. The lock covers the
directional application calls too, so a later observation for the same pair cannot preempt or refresh state while the
current decision is applying its candidate.

<code-block lang="mermaid">
sequenceDiagram
    participant A as PacketIn for pair A
    participant B as Later PacketIn for pair A
    participant C as PacketIn for pair B
    participant Locks as locksByPair
    participant Coordinator
    A->>Locks: acquire lock A
    A->>Coordinator: record, decide, and possibly apply
    B->>Locks: wait for lock A release
    C->>Locks: acquire independent lock B
    C->>Coordinator: record and decide for pair B
    Coordinator-->>A: decision
    A->>Locks: release lock A
    Locks-->>B: lock A acquired
    B->>Coordinator: record and decide after A
</code-block>

The lock is a local serialization mechanism, not a distributed transaction. Directional application can perform
external path and PCEP work while the lock is held. If an application fails after a previous direction already made a
remote change, the coordinator does not roll back that earlier external effect. Instead, it rejects installation of a
new active pair policy and propagates the failure path to the PacketIn workflow. This limitation is explicit in the
application sequence and is essential when interpreting an active-state record.

## Decision records and state ownership {id="decision-records-and-state-ownership"}

The mechanism uses different immutable records for different stages of the decision. A
<code>PairConsensusDecision</code> represents only the comparison of the two directional observations. It may carry a
candidate, both evidence slots, a status, and an explanation. A <code>PairPolicyCandidate</code> copies the selected
observation's policy fields, source direction, hash, and selection time. Candidate selection does not mean that an
active policy exists. <code>PairPolicyDecision</code> is the coordinator's outer result. It adds the preemption type,
optional active state, and zero or more directional application records so the PacketIn workflow can report the final
control-cycle outcome.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/model/PairConsensusDecision.java" include-lines="14-57"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/model/PairPolicyCandidate.java" include-lines="13-56"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/model/PairPolicyDecision.java" include-lines="14-57"/>

<code-block lang="mermaid">
classDiagram
    class DirectionalPolicyEvidence {
        +String pairKey
        +ServiceKey serviceKey
        +String directionKey
        +String policyHash
        +int setupPriority
        +int holdPriority
        +Instant expiresAt
    }
    class PairConsensusBucket {
        +Optional leftEvidence
        +Optional rightEvidence
        +Instant expiresAt
    }
    class PairConsensusDecision {
        +PairConsensusStatus consensusStatus
        +Optional selectedCandidate
        +String conflictResolutionReason
    }
    class PairPolicyCandidate {
        +String selectedFromDirectionKey
        +String policyHash
        +PairConsensusStatus consensusStatus
    }
    class PairPolicyDecision {
        +PolicyPreemptionDecisionType preemptionDecision
        +Optional activePolicy
        +List applications
    }
    DirectionalPolicyEvidence --> PairConsensusBucket : stored by side
    PairConsensusBucket --> PairConsensusDecision : evaluated
    PairConsensusDecision --> PairPolicyCandidate : optional selection
    PairPolicyCandidate --> PairPolicyDecision : considered by coordinator
</code-block>

<code>ActivePairPolicyState</code> is the only record that represents installed pair-level policy. The active registry
stores at most one such state for each pair. It retains the candidate's service and policy identity, controller-side
priorities, hash, generation, installation and refresh times, expiry, and a map of directional application records.
The record copies that map immutably, so a later caller cannot revise the observed LSP application outcome through a
retained mutable map.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/model/ActivePairPolicyState.java" include-lines="14-82"/>

| Record | Owner that creates it | Meaning | Does not establish |
|---|---|---|---|
| <code>DirectionalPolicyEvidence</code> | PacketIn workflow | one side's classified policy observation | two-sided agreement |
| <code>PairConsensusDecision</code> | consensus service | result of comparing usable evidence | preemption or LSP update |
| <code>PairPolicyCandidate</code> | consensus service | policy selected from a usable observation | active-state installation |
| <code>PolicyPreemptionDecision</code> | preemption evaluator | comparison with active state | remote application success |
| <code>ActivePairPolicyState</code> | active registry | installed policy and directional results | rollback of external changes |

## Two-sided consensus {id="two-sided-consensus"}

The directional evidence registry retains evidence by pair and normalized service key, with distinct left and right
slots. It resolves the ingress switch through <code>TunnelPairRegistry</code>, which maps the demonstrator's managed
pair to <code>LEFT</code> and <code>RIGHT</code>. A newly recorded observation replaces only its own slot and preserves
the opposite slot when it exists. The bucket expiration becomes the later of the two slot expirations. This creates a
short-lived application state that connects two independent PacketIn callbacks without blocking either notification
thread.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/registry/DirectionalClassificationEvidenceRegistry.java" include-lines="61-92"/>

The registry's whole-bucket lifetime does not make both slots equally usable. At decision time, the consensus service
filters each slot with <code>expiresAt().isAfter(now)</code>. Evidence expiring exactly at the decision time is not
usable. If either filtered slot is absent, consensus returns <code>PENDING_ONE_SIDE</code> with no candidate. This is
the precise pair-policy gate. The next packet from the missing direction, provided it resolves to the same pair and
normalized service key before the relevant evidence expires, updates the bucket and causes a new immediate
evaluation.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/policy/PairPolicyConsensusService.java" include-lines="36-68"/>

<code-block lang="mermaid">
stateDiagram-v2
    [*] --> Empty
    Empty --> LeftOnly : left PacketIn recorded
    Empty --> RightOnly : right PacketIn recorded
    LeftOnly --> BothUsable : right PacketIn recorded before expiry
    RightOnly --> BothUsable : left PacketIn recorded before expiry
    LeftOnly --> Empty : left evidence expires
    RightOnly --> Empty : right evidence expires
    BothUsable --> Pending : one side expires at evaluation
    BothUsable --> HashMatch : policy hashes equal
    BothUsable --> PriorityComparison : policy hashes differ
    Pending --> LeftOnly : new left evidence
    Pending --> RightOnly : new right evidence
    HashMatch --> [*]
    PriorityComparison --> [*]
</code-block>

The consensus service first compares the directional policy hashes. Equal hashes yield
<code>CONSENSUS_MATCH</code> and a candidate copied from left-side evidence. A hash conflict does not automatically
defer the decision. The service compares setup priority first and hold priority second, where a lower number is
stronger. A strictly stronger observation yields <code>CONSENSUS_CONFLICT_PRIORITY_SELECTED</code> and a candidate
copied from that selected observation. This rule lets evidence for one service agree on the pair-level policy or
resolve a disagreement by the controller-side priority values rather than by the order in which PacketIns arrived.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/policy/PairPolicyConsensusService.java" include-lines="69-123"/>

<code-block lang="mermaid">
flowchart TD
    Bucket["PairConsensusBucket"] --> Fresh{"both directional slots usable?"}
    Fresh -- no --> Pending["PENDING_ONE_SIDE, no candidate"]
    Fresh -- yes --> Hashes{"policy hashes equal?"}
    Hashes -- yes --> Match["CONSENSUS_MATCH"]
    Match --> CopyLeft["copy left evidence to candidate"]
    Hashes -- no --> Setup{"setup priorities differ?"}
    Setup -- yes --> SelectSetup["select lower setup priority"]
    Setup -- no --> Hold{"hold priorities differ?"}
    Hold -- yes --> SelectHold["select lower hold priority"]
    Hold -- no --> Active{"active hash matches either side?"}
    Active -- yes --> Preserve["CURRENT_POLICY_PRESERVED, no candidate"]
    Active -- no --> Unresolved["EQUAL_PRIORITY_UNRESOLVED, no candidate"]
    SelectSetup --> Candidate["CONSENSUS_CONFLICT_PRIORITY_SELECTED candidate"]
    SelectHold --> Candidate
</code-block>

The equal-priority branch provides stability when two different policies have the same controller-side priority. If
the currently active state has the hash of either directional observation, consensus returns
<code>CONSENSUS_CONFLICT_CURRENT_POLICY_PRESERVED</code>. If there is no such active hash, it returns
<code>CONSENSUS_CONFLICT_EQUAL_PRIORITY_UNRESOLVED</code>. In either branch, no candidate reaches preemption. The
tests demonstrate the relevant boundary cases: a matching fresh hash selects a candidate, a stronger setup priority
wins, hold priority resolves a setup tie, and an expired directional observation produces
<code>PENDING_ONE_SIDE</code> even when the other slot remains present.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/test/java/com/sma/sdn/policy/PairPolicyConsensusServiceTest.java" include-lines="35-104"/>

### Consensus status and candidate scope {id="consensus-status-and-candidate-scope"}

<code>PairConsensusStatus</code> describes a comparison result, not an LSP state. Only
<code>CONSENSUS_MATCH</code> and <code>CONSENSUS_CONFLICT_PRIORITY_SELECTED</code> carry a candidate. The candidate
retains the selected direction and ingress switch for auditability, while its policy fields become pair-policy input.
The coordinator may still retain, refresh, or reject the candidate after it compares active state. Treating candidate
selection and application as separate transitions prevents a matching pair of PacketIns from overwriting an active
policy solely because it arrived later.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/model/PairConsensusStatus.java" include-lines="11-32"/>

## Active-policy lifetime and preemption {id="active-policy-lifetime-and-preemption"}

The active registry has one state per pair key and removes expired entries lazily during expiration-aware operations.
<code>findActive(pairKey, now)</code> first removes an entry whose expiry is at or before <code>now</code>. A candidate
is therefore compared only with a currently usable state. <code>snapshot()</code> and <code>size()</code> also sweep
expired entries, while <code>findIncludingExpired()</code> intentionally exposes raw stored state without performing a
sweep. This distinction supports state inspection without allowing an expired policy to participate in the normal
preemption path.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/registry/ActivePairPolicyRegistry.java" include-lines="34-118"/>

A same-hash candidate does not trigger directional application. The coordinator asks the registry to copy the active
state with a new <code>lastRefreshedAt</code> and expiry calculated from
<code>activePairPolicyIdleTtl</code>. The generation and existing directional application records remain unchanged.
When a new candidate replaces a state, the registry increments the generation, sets installation and refresh times to
the replacement time, assigns a new expiry, and stores the newly collected application records. An active policy is
therefore a bounded lease on the latest fully applied pair state.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/registry/ActivePairPolicyRegistry.java" include-lines="59-154"/>

<code-block lang="mermaid">
stateDiagram-v2
    [*] --> Absent
    Absent --> Installed : candidate applied to both directions
    Installed --> Refreshed : same policy hash
    Refreshed --> Refreshed : same policy hash
    Installed --> Replaced : stronger incoming or expiry
    Refreshed --> Replaced : stronger incoming or expiry
    Installed --> Retained : weaker or equal different incoming
    Retained --> Installed : active state remains current
    Installed --> Expired : expiresAt at or before now
    Refreshed --> Expired : expiresAt at or before now
    Expired --> Absent : lazy eviction
    Replaced --> Installed
</code-block>

<code>PolicyPreemptionEvaluator</code> compares only an active policy and a consensus-selected candidate. It first
permits replacement when the active state has expired. It then refreshes when hashes match. When hashes differ, it
compares incoming setup priority to active setup priority and uses hold priority only for a setup tie. Lower values
are stronger. Equal setup and hold values retain the active state when the hashes differ, while a weaker incoming
priority also retains it. The evaluator does not call PCEP or change a registry. It returns a small decision record
whose <code>appliesIncoming()</code> method is true only for expiration replacement and stronger incoming priority.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/policy/PolicyPreemptionEvaluator.java" include-lines="18-62"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/model/PolicyPreemptionDecision.java" include-lines="11-31"/>

| Preemption decision | Condition | Coordinator action | Application records |
|---|---|---|---|
| <code>SAME_POLICY_REFRESH</code> | active and candidate hashes match | refresh active TTL | retained unchanged |
| <code>ACTIVE_EXPIRED_REPLACE</code> | no active state or active state expired | apply candidate to both directions | collected and installed on success |
| <code>INCOMING_PRIORITY_PREEMPTS</code> | candidate has lower setup or lower hold after a setup tie | apply candidate to both directions | collected and installed on success |
| <code>ACTIVE_POLICY_RETAINED_WEAKER_INCOMING</code> | candidate priority is weaker | retain active policy | none |
| <code>ACTIVE_POLICY_RETAINED_EQUAL_PRIORITY_DIFFERENT_POLICY</code> | different hash and equal setup and hold priority | retain active policy | none |

The evaluator tests capture these ordering rules. A lower setup priority preempts, lower hold priority preempts when
setup ties, a weaker policy is retained, equal priorities with a different hash remain deferred, matching hashes
refresh without application, and an active policy at its expiry boundary can be replaced.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/test/java/com/sma/sdn/policy/PolicyPreemptionEvaluatorTest.java" include-lines="25-85"/>

## Coordinator decision sequence {id="coordinator-decision-sequence"}

<code>handleEvidence</code> executes the complete decision sequence under the pair lock. It records incoming evidence,
increments the directional-evidence metric, obtains an unexpired active state, and evaluates consensus. A no-candidate
decision returns immediately with an empty candidate and no application records. A selected candidate records either
a matching-hash or priority-selected consensus metric before the coordinator branches on whether an active state
exists. The active-state branch is where refresh, retention, preemption, and expiry replacement become distinct
runtime effects.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/policy/PairPolicyCoordinator.java" include-lines="114-189"/>

<code-block lang="mermaid">
flowchart TD
    Input["DirectionalPolicyEvidence"] --> Lock["acquire pair lock"]
    Lock --> Record["record evidence in pair and service bucket"]
    Record --> Active["find unexpired active policy"]
    Active --> Consensus["evaluate consensus"]
    Consensus --> Candidate{"candidate selected?"}
    Candidate -- no --> NoCandidate["return pending or deferred decision"]
    Candidate -- yes --> HasActive{"active state present?"}
    HasActive -- no --> Apply["apply candidate to both directions"]
    HasActive -- yes --> Preempt["evaluate preemption"]
    Preempt --> Refresh{"same policy hash?"}
    Refresh -- yes --> Renew["refresh active TTL and return"]
    Refresh -- no --> Incoming{"incoming applies?"}
    Incoming -- no --> Retain["retain active policy and return"]
    Incoming -- yes --> Apply
    Apply --> Install["install active state after both applications succeed"]
    Install --> Result["return decision with applications"]
    NoCandidate --> Unlock["release pair lock"]
    Renew --> Unlock
    Retain --> Unlock
    Result --> Unlock
</code-block>

The coordinator treats a candidate as a pair-level policy even when consensus selected its values from one direction.
It retrieves both managed directions from <code>TunnelPairRegistry</code> and calls
<code>DirectionalLspApplicationService.applyPolicyToDirection</code> once for each. The application service owns path
computation, delegated-LSP refresh, update transmission, and confirmation. The coordinator collects those records and
checks their status strings. If any status begins with <code>FAILED</code>, it throws before installing active state.
The earlier application is not rolled back. If both succeed or report a converged skip without a failed status, the
coordinator indexes the records by direction key, installs the active state, and increments the active-policy metric.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/policy/PairPolicyCoordinator.java" include-lines="191-235"/>

<code-block lang="mermaid">
sequenceDiagram
    participant Coordinator
    participant Pairs as TunnelPairRegistry
    participant Application as DirectionalLspApplicationService
    participant Path as PathComputationService
    participant Lsp as DelegatedLspService
    participant Active as ActivePairPolicyRegistry
    Coordinator->>Pairs: requireManagedDirections(pairKey)
    loop each managed direction
        Coordinator->>Application: applyPolicyToDirection(candidate, direction, context)
        Application->>Path: computeOrGetCached(direction, constraints)
        Application->>Lsp: refresh, compare, and update when needed
        Lsp-->>Application: confirmed or failure record source
        Application-->>Coordinator: DirectionalLspApplicationRecord
    end
    alt any record status starts with FAILED
        Coordinator-->>Coordinator: reject active-state installation
    else both directions non-failed
        Coordinator->>Active: installOrReplace(candidate, records, now, TTL)
        Active-->>Coordinator: ActivePairPolicyState
    end
</code-block>

## Failure boundaries and observability {id="pair-policy-failure-and-observability"}

Consensus and preemption are deterministic local decisions over their supplied records. Their no-candidate branches
return structured decisions rather than exceptions. A pending side, an unresolved equal-priority conflict, an active
policy retained for equal or weaker incoming priority, and a same-policy refresh are all normal outcomes. External
failure begins only when the coordinator chooses to apply an incoming candidate. The directional application service
returns a failure record for many runtime failures, but its own fallback-record lookup can still propagate an
exception. The coordinator releases the pair lock in all cases because its entire operation is wrapped in
<code>try</code>/<code>finally</code>.

The metrics distinguish evidence recording, consensus status, pending consensus, unresolved equal-priority conflicts,
matching consensus, priority-selected consensus, active-policy refresh, deferred retention, expiration replacement,
priority preemption, and active-state installation. These counters reveal which gate stopped an observation without
claiming that a metric proves a remote LSP update. Directional application records remain the state-bearing evidence of
an attempted update, while active state exists only after the coordinator accepts every direction.

<code-block lang="mermaid">
flowchart LR
    Evidence["sma_directional_evidence_recorded_total"] --> Consensus["sma_pair_consensus_decision_total"]
    Consensus --> Pending["pending and unresolved counters"]
    Consensus --> Match["match and priority-selected counters"]
    Match --> Preemption["refresh, retain, expire, or preempt counters"]
    Preemption --> Active["sma_pair_policy_active_total"]
    Application["directional LSP application records"] --> Active
    Active --> State["operational active pair-policy state"]
</code-block>

The resulting design makes policy replacement conservative in two ways. First, it requires usable evidence from both
directions before it produces a candidate. Second, it requires a candidate to survive active-state comparison and
complete both directional applications before it becomes active. These conditions preserve one explicit pair-level
state even though evidence enters through separate packet notifications and application ultimately crosses topology
and PCEP boundaries.
