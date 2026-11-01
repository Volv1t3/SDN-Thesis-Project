# Application Construction — Delegated LSP Lifecycle and Tunnel Application

<show-structure for="chapter" depth="3"/>

The delegated-LSP lifecycle translates an accepted pair-policy candidate into two independently observable PCEP tunnel outcomes. <code>DirectionalLspApplicationService</code> owns one direction at a time. It computes or retrieves a constrained path, refreshes the reported PCEP state, constructs the desired LSP state, and either records a converged skip or requests an update through <code>DelegatedLspService</code>. The [Application Construction — Pair Policy Consensus and Preemption](Java-Controller-Side-Application-—-Application-Construction-Pair-Policy-Consensus-and-Preemption.md#coordinator-decision-sequence) topic owns the surrounding pair-level decision and installs active policy state only after both directional records are non-failing.

The [Application Construction — Topology Discovery and Path Computation](Java-Controller-Side-Application-—-Application-Construction-Topology-Discovery-and-Path-Computation.md#topology-path-handoff-to-lsp-application) topic owns BGP-LS refresh, constrained-path calculation, and the cached ERO. This page begins when that path is handed to a direction. It distinguishes the PCEP topology snapshot, the local delegated-LSP registry, the immediate <code>update-lsp</code> response, and the later reported ERO that confirms convergence.

## PCEP discovery and validated directional records {id="pcep-discovery-and-validated-directional-records"}

Provider readiness requires the two configured delegated LSPs to be present before packet-driven policy application can succeed. <code>DelegatedLspService.initialize()</code> reads the configured PCEP topology, builds records for the forward and reverse configured directions, replaces the registry only after both records validate, then requires both directions from the new snapshot. The PCEP topology may contain many PCC reports, but the application retains only the records whose PCC node and symbolic LSP name match the configured directions.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/tunnel/DelegatedLspService.java" include-lines="116-157"/>

<code-block lang="mermaid">
sequenceDiagram
    participant Provider
    participant Service as DelegatedLspService
    participant RESTCONF as PCEP topology endpoint
    participant Parser as PcepTopologyXmlDeserializer
    participant Registry as DelegatedLspRegistry
    Provider->>Service: initialize()
    Service->>RESTCONF: getPcepTopology(configured ID)
    RESTCONF-->>Service: HTTP response
    Service->>Parser: deserialize reported snapshots
    Service->>Service: validate forward and reverse configured LSPs
    Service->>Registry: replaceAll(validated records)
    Service->>Registry: require both directions
    Registry-->>Provider: startup precondition established
</code-block>

The service reads the whole configured PCEP topology during startup and during a directional refresh. It rejects non-2xx responses, parses successful bodies into <code>PcepReportedLspSnapshot</code> values, and rethrows a retrieval or parsing failure after incrementing the refresh-failure metric. A refresh is therefore a new reported-state observation, not a lookup in a stale local cache.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/tunnel/DelegatedLspService.java" include-lines="171-193"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/tunnel/DelegatedLspService.java" include-lines="437-483"/>

A reported snapshot becomes a <code>DelegatedLspRecord</code> only if it matches the configured source PCC and LSP name, reports the configured source and destination router IDs, and is updateable. Updateability requires a positive PLSP ID, delegation, administrative enablement, and an operational state of <code>up</code>. The construction checks both configured directions before calling <code>replaceAll</code>, preventing one newly observed direction from replacing a prior complete registry with a half-validated snapshot.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/tunnel/DelegatedLspService.java" include-lines="498-529"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/tunnel/DelegatedLspService.java" include-lines="549-610"/>

<code-block lang="mermaid">
flowchart TD
    Snapshots[PCEP reported LSP snapshots] --> Select[select PCC node and configured LSP name]
    Select --> Endpoints{reported endpoints match direction?}
    Endpoints -- no --> RejectEndpoint[reject snapshot]
    Endpoints -- yes --> Updateable{positive PLSP ID, delegated, admin up, operational up?}
    Updateable -- no --> RejectState[reject snapshot]
    Updateable -- yes --> ForwardReverse{both configured directions validated?}
    ForwardReverse -- no --> KeepPrevious[do not replace registry]
    ForwardReverse -- yes --> Replace[replace all registry indexes]
</code-block>

## Registry identity and updateability {id="delegated-lsp-registry-identity-and-updateability"}

<code>DelegatedLspRegistry</code> owns the latest validated local view. It indexes every retained immutable record by direction key, symbolic LSP name, and the composite PCC-node/name key. The replacement method constructs temporary indexes before mutating any existing map. Because the method is synchronized, readers cannot observe one index updated while another is still stale. The registry itself does not read PCEP. <code>DelegatedLspService</code> performs reads and supplies complete, validated replacements.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/registry/DelegatedLspRegistry.java" include-lines="43-77"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/registry/DelegatedLspRegistry.java" include-lines="148-210"/>

<code-block lang="mermaid">
classDiagram
    class DelegatedLspRecord {
        +directionKey
        +pccNode
        +lspName
        +plspId
        +activeEro
        +reportedBandwidthBase64
        +isValidForUpdate()
    }
    class DelegatedLspRegistry {
        +replaceAll(records)
        +requireByDirectionKey(directionKey)
        +updateAfterSuccessfulUpdate(...)
    }
    class DelegatedLspService {
        +initialize()
        +refreshDirection(directionKey)
        +updateDelegatedLsp(direction,path,constraints)
    }
    DelegatedLspService --> DelegatedLspRegistry : replaces and requires
    DelegatedLspRegistry --> DelegatedLspRecord : indexes immutable records
</code-block>

The record includes the discovered PLSP identifier, tunnel and LSP identifiers, active ERO, reported bandwidth encoding, and timestamps as well as the configuration-facing direction identity. After PCEP convergence, the registry updates only the locally recorded ERO, bandwidth, and update timestamp while preserving the reported identity and state fields. That local replacement records the confirmed state used by later application calls. It does not claim that the application has changed an arbitrary external LSP without PCEP confirmation.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/model/DelegatedLspRecord.java" include-lines="17-102"/>

## Per-direction application and desired state {id="per-direction-application-and-desired-state"}

<code>DirectionalLspApplicationService</code> keeps a <code>ReentrantLock</code> per direction. It holds that lock while it obtains a path, refreshes PCEP state, evaluates convergence, and, when required, performs the delegated-LSP update. This serialization is narrower than the pair lock held by the policy coordinator. The pair lock prevents same-pair policy decisions from interleaving, while the direction lock protects a specific LSP direction when it is reached through the application service.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/tunnel/DirectionalLspApplicationService.java" include-lines="97-155"/>

<code-block lang="mermaid">
sequenceDiagram
    participant Coordinator as PairPolicyCoordinator
    participant App as DirectionalLspApplicationService
    participant Path as PathComputationService
    participant PCEP as DelegatedLspService
    participant Registry as DelegatedLspRegistry
    Coordinator->>App: applyPolicyToDirection(candidate, direction)
    App->>App: acquire direction lock
    App->>Path: computeOrGetCached(direction, constraints)
    Path-->>App: CalculatedPath and ERO
    App->>PCEP: refreshDirection(direction key)
    PCEP->>Registry: replace validated snapshot
    Registry-->>App: current DelegatedLspRecord
    App->>App: compare desired ERO and bandwidth
    App-->>Coordinator: skip or application record
</code-block>

The desired state binds the candidate policy to a concrete delegated-LSP identity. It includes the pair key, direction, PCC node, LSP name, tunnel interface, PLSP and tunnel IDs, requested bandwidth, priorities, ordered ERO, and a deterministic ERO fingerprint. The service constructs a partial record, hashes it through <code>PairPolicyHashService</code>, and returns a second immutable record containing the desired-state hash. The application record later carries that hash together with workflow and policy identifiers, so an operational reader can distinguish the selected policy hash from the resulting direction-specific LSP state hash.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/tunnel/DirectionalLspApplicationService.java" include-lines="172-245"/>

<code-block lang="mermaid">
flowchart LR
    Candidate[PairPolicyCandidate] --> Constraints[PathConstraints]
    Candidate --> Identity[delegated LSP identity]
    ERO[ordered ERO] --> Fingerprint[ERO fingerprint]
    Constraints --> Desired[DirectionalLspDesiredState]
    Identity --> Desired
    Fingerprint --> Desired
    Desired --> Hash[PairPolicyHashService]
    Hash --> Record[DirectionalLspApplicationRecord]
</code-block>

When the freshly reported valid LSP has both the desired ERO and the requested encoded bandwidth, the application service does not transmit an update. It returns <code>SKIPPED_ALREADY_CONVERGED</code> with both confirmation flags true. When the state differs, it invokes the delegated-LSP service, reads the locally confirmed record afterwards, and records ERO and bandwidth confirmation separately. A PCEP-confirmed ERO with a different reported bandwidth is recorded as <code>ACCEPTED_PCEP_BANDWIDTH_UNCONFIRMED</code>, rather than being misrepresented as full convergence.

<code-block lang="mermaid">
stateDiagram-v2
    [*] --> Locked
    Locked --> PathReady : cached or calculated path
    PathReady --> CurrentReported : PCEP refresh completes
    CurrentReported --> Converged : ERO and bandwidth match
    CurrentReported --> UpdateRequired : desired state differs
    Converged --> Skipped : non-sent record
    UpdateRequired --> UpdateSubmitted : update-lsp invoked
    UpdateSubmitted --> Confirmed : desired ERO observed
    Confirmed --> BandwidthUnconfirmed : ERO matches, bandwidth differs
    Confirmed --> Applied : ERO and bandwidth match
    PathReady --> Failed : computation or refresh failure
    UpdateSubmitted --> Failed : update or confirmation failure
    Skipped --> [*]
    Applied --> [*]
    BandwidthUnconfirmed --> [*]
    Failed --> [*]
</code-block>

## Update submission and PCEP convergence {id="update-submission-and-pcep-convergence"}

<code>DelegatedLspService.updateDelegatedLsp()</code> is synchronized for the service instance. It first obtains an updateable record, encodes the requested bandwidth, and compares the existing ERO and encoded bandwidth with the desired values. Matching state returns a successful no-change result with HTTP status zero. Otherwise, the service constructs an <code>UpdateLspRequest</code> from the discovered PCC node, LSP name, runtime PLSP ID, encoded bandwidth, desired ERO, and configured PCEP topology ID. The protocol-payload topic describes the XML serialization and response classification in detail.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/tunnel/DelegatedLspService.java" include-lines="236-297"/>

An accepted immediate response does not establish convergence. The service uses <code>RetryPolicy.retryUntilPresent</code> to refresh PCEP state repeatedly until the reported ERO equals the requested ERO. Refresh failures inside that polling callback become unsuccessful polling attempts. On success, the service writes the confirmed ERO and reported bandwidth into the registry, records ERO verification and convergence duration, and returns the original response status. A bandwidth mismatch produces a warning but does not make an ERO-confirmed update fail, which is why the directional application record exposes two separate confirmation flags.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/tunnel/DelegatedLspService.java" include-lines="315-421"/>

<code-block lang="mermaid">
sequenceDiagram
    participant App as DirectionalLspApplicationService
    participant Service as DelegatedLspService
    participant Ops as OdlOperationsClient
    participant Retry as RetryPolicy
    participant Data as PCEP topology RESTCONF
    participant Registry as DelegatedLspRegistry
    App->>Service: updateDelegatedLsp(direction,path,constraints)
    Service->>Service: stateMatches(current, ERO, bandwidth)
    alt matching state
        Service-->>App: successful no-change result
    else different state
        Service->>Ops: updateLsp(serialized request)
        Ops-->>Service: immediate classified response
        Service->>Retry: retryUntilPresent(refresh and compare ERO)
        loop until ERO appears or budget ends
            Retry->>Data: read PCEP topology
            Data-->>Retry: reported LSP snapshot
        end
        Retry-->>Service: confirmed record or failure
        Service->>Registry: updateAfterSuccessfulUpdate(...)
        Service-->>App: accepted update result
    end
</code-block>

Transport failure, response-deserialization failure, a rejected initial response, and exhausted confirmation polling are runtime failures. The transport and rejected-response paths invoke a defensive PCEP refresh before rethrowing. The directional application service catches many runtime failures and returns a <code>FAILED_UPDATE_LSP_VALIDATION</code> application record, although its fallback lookup can itself throw if no updateable LSP record exists. The pair-policy coordinator consequently refuses active-state installation when any returned directional status starts with <code>FAILED</code>, while an exception that escapes reaches the upstream workflow as a failed application path.

<code-block lang="mermaid">
flowchart TD
    Start[updateDelegatedLsp] --> Match{existing state matches ERO and bandwidth?}
    Match -- yes --> Skip[return success, HTTP status 0]
    Match -- no --> Send[serialize and send update-lsp]
    Send --> Immediate{accepted immediate response?}
    Immediate -- no --> Refresh[defensive PCEP refresh] --> Throw[record failure and throw]
    Immediate -- yes --> Poll[refresh PCEP and compare reported ERO]
    Poll --> Seen{desired ERO observed?}
    Seen -- no, attempts remain --> Poll
    Seen -- no, exhausted --> FailConfirm[record timeout and throw]
    Seen -- yes --> Persist[update local registry]
    Persist --> Outcome{reported bandwidth matches?}
    Outcome -- yes --> Full[ERO and bandwidth confirmed]
    Outcome -- no --> Partial[ERO confirmed, bandwidth unconfirmed]
</code-block>

## Lifecycle boundary and optional intent coordinator {id="lifecycle-boundary-and-optional-intent-coordinator"}

The live pair-policy path uses <code>DirectionalLspApplicationService</code>, not <code>TunnelOperationCoordinator</code>, to apply a selected candidate. The latter is an intent-registry and suppression-flow coordinator with a similar directional PCEP operation, but its own source explicitly states that construction does not establish provider invocation. Its operation registry can reuse recent intents, serialize with a bounded lock wait, and install OpenFlow suppression after a reused, matching, or completed operation. The [Application Construction — OpenFlow Bootstrap and Packet Suppression](Java-Controller-Side-Application-—-Application-Construction-OpenFlow-Bootstrap-and-Packet-Suppression.md) topic owns that suppression mechanism.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/tunnel/TunnelOperationCoordinator.java" include-lines="25-42"/>

<code-block lang="mermaid">
flowchart LR
    Consensus[PairPolicyCoordinator] --> Live[DirectionalLspApplicationService]
    Live --> PCEP[DelegatedLspService]
    PCEP --> Active[active pair-policy decision]
    Optional[TunnelOperationCoordinator] --> Intent[TunnelOperationRegistry]
    Optional --> Suppression[OpenFlowSuppressionService]
    Note["constructed separately, not evidence of provider invocation"] -.-> Optional
</code-block>

The lifecycle is conservative at two levels. A direction cannot update until PCEP discovery provides an updateable configured record, and a pair cannot become active until its two directional applications complete without a failed status. The local registry records the last validated or confirmed view, while PCEP topology remains the authority refreshed before each directional comparison and during convergence polling.

## Class-level serialization and recovery scope {id="delegated-lsp-class-level-serialization-and-recovery"}

The lifecycle contains two serialization scopes that serve different purposes. <code>DirectionalLspApplicationService</code> stores a <code>ReentrantLock</code> for each direction, so separate forward and reverse calls can compute paths and refresh their reports independently. <code>DelegatedLspService.updateDelegatedLsp()</code>, however, is <code>synchronized</code> on the service instance. Once either direction enters the actual update operation, the other direction cannot simultaneously construct, transmit, or confirm a PCEP update through that shared service. The pair-policy coordinator additionally holds its pair lock across both directional applications. The normal active path is therefore serialized more strongly than the per-direction map alone suggests.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/tunnel/DirectionalLspApplicationService.java" include-lines="97-155"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/tunnel/DelegatedLspService.java" include-lines="236-310"/>

<code-block lang="mermaid">
sequenceDiagram
    participant Pair as PairPolicyCoordinator pair lock
    participant Forward as forward direction lock
    participant Reverse as reverse direction lock
    participant Service as synchronized DelegatedLspService
    Pair->>Forward: apply forward direction
    Forward->>Service: updateDelegatedLsp when required
    Note over Service: one update body at a time
    Service-->>Forward: response and convergence result
    Pair->>Reverse: apply reverse direction
    Reverse->>Service: updateDelegatedLsp when required
    Service-->>Reverse: response and convergence result
</code-block>

The recovery behavior is intentionally observational rather than compensating. A transport failure or rejected immediate response calls <code>refreshAfterFailedAttempt</code> before the service rethrows, allowing the registry to reflect a state that may have changed despite an uncertain request outcome. The service does not issue a compensating PCEP request, roll back a previous directional change, or treat that refresh as confirmation. Confirmation polling is the only retry loop in this class, and it retries reads until the reported ERO appears. When that loop expires, the request fails even if the immediate operation response had been accepted.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/tunnel/DelegatedLspService.java" include-lines="335-421"/>

<code-block lang="mermaid">
stateDiagram-v2
    [*] --> CurrentRecord
    CurrentRecord --> NoChange : ERO and bandwidth already match
    CurrentRecord --> Submitted : update-lsp accepted immediately
    Submitted --> Observing : refresh PCEP through retry policy
    Observing --> Confirmed : reported ERO matches requested ERO
    Observing --> ConfirmationFailed : polling budget exhausted
    CurrentRecord --> RefreshAfterFailure : transport or rejected response
    RefreshAfterFailure --> Failed : refreshed observation does not confirm request
    Confirmed --> RegistryUpdated
    NoChange --> [*]
    RegistryUpdated --> [*]
    ConfirmationFailed --> [*]
    Failed --> [*]
</code-block>

The source-present <code>TunnelOperationCoordinator</code> and <code>TunnelOperationRegistry</code> define a separate intent lifecycle that is useful for interpreting the model layer but is not currently invoked by the provider's pair-policy flow. That registry has its own synchronized working indexes and bounded journal. It can mark an intent pending, accepted, confirmed, skipped, or failed. A hard failure removes the intent from the reusable index, ensuring that a failed intent does not suppress a later attempt. This separation prevents an operational reader from mistaking the documented intent journal for the active pair-policy registry or a production call path.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/registry/TunnelOperationRegistry.java" include-lines="68-115"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/registry/TunnelOperationRegistry.java" include-lines="237-253"/>

