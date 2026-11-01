# Application Construction — OpenFlow Bootstrap and Packet Suppression

<show-structure for="chapter" depth="3"/>

OpenFlow bootstrap gives the controller a verified access-plane baseline before the Java application admits packet-driven control work. <code>OpenflowBootstrapService</code> discovers the two configured access switches from OpenDaylight operational inventory, publishes their dynamic identities, installs deterministic ARP and IPv4 flows, and confirms that those flows are visible in configuration and operational state. Its readiness flag is one prerequisite for the provider-wide <code>controlPlaneReady</code> gate described in [Application Construction](Java-Controller-Side-Application-—-Application-Construction.md#readiness-cycle-and-recovery). The packet workflow then uses the same published inventory identity when it admits a <code>PacketReceived</code> notification, as described in [Application Construction — PacketIn Classification Workflow](Java-Controller-Side-Application-—-Application-Construction-PacketIn-Classification-Workflow.md#notification-admission).

This page distinguishes that implemented baseline from <code>OpenFlowSuppressionService</code>. The latter can construct and submit a temporary, service-specific suppression flow, but the provider does not construct or call it and the current workflow logs <code>suppression_flow_enabled</code> as <code>false</code>. Therefore, suppression is a source-present capability rather than a behavior currently activated by the PacketIn path.

## Bootstrap in the provider readiness cycle {id="openflow-bootstrap-readiness-cycle"}

The provider registers its PacketIn listener before asynchronous readiness completes, then schedules a single-thread readiness cycle. Within that cycle, topology discovery precedes delegated-LSP validation, which in turn precedes OpenFlow bootstrap. Only after bootstrap reports ready does the provider start topology refresh and publish <code>controlPlaneReady=true</code>. A notification can consequently reach the listener during startup, but the workflow rejects it before feature extraction and classification while the readiness supplier is false.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/impl/SDN_MPLS_ML_Provider.java" include-lines="525-566"/>

<code-block lang="mermaid">
sequenceDiagram
    participant Blueprint as Blueprint lifecycle
    participant Provider as SDN_MPLS_ML_Provider
    participant Topology as TopologyDiscoveryService
    participant Lsp as DelegatedLspService
    participant Bootstrap as OpenflowBootstrapService
    participant Workflow as PacketIn workflow
    Blueprint->>Provider: init()
    Provider->>Provider: register PacketReceived listener
    Provider->>Provider: schedule readiness attempt
    Provider->>Topology: initialize()
    Provider->>Lsp: initialize()
    Provider->>Bootstrap: initialize()
    Bootstrap-->>Provider: verified ready
    Provider->>Provider: controlPlaneReady = true
    Workflow->>Provider: read readiness supplier
    Provider-->>Workflow: true permits admission
</code-block>

The provider creates one shared <code>OpenflowSwitchRegistry</code> and injects it into both the bootstrap service and <code>PacketInFeatureExtractor</code>. This is a temporal contract: discovery must publish a coherent snapshot before PacketIn extraction can resolve a notification's node and connector identifiers. The registry is not an accumulation of independently mutable maps. It builds all indexes, rejects duplicated switch or connector identities, and publishes immutable copies through one volatile snapshot assignment. Readers therefore see either the earlier complete snapshot or the replacement complete snapshot, never an index assembled halfway through discovery.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/openflow/OpenflowSwitchRegistry.java" include-lines="20-66"/>

<code-block lang="mermaid">
flowchart LR
    Inventory["OpenDaylight operational inventory"] --> Discovery["OpenflowInventoryService"]
    Discovery --> Records["OpenflowSwitchRecord list"]
    Records --> Replace["OpenflowSwitchRegistry.replace"]
    Replace --> Snapshot["volatile immutable Snapshot"]
    Snapshot --> Bootstrap["OpenflowBootstrapService"]
    Snapshot --> Extractor["PacketInFeatureExtractor"]
    Extractor --> Admission["PacketIn admission and direction resolution"]
</code-block>

## Inventory identity resolution {id="openflow-inventory-identity-resolution"}

<code>OpenflowInventoryService</code> creates profiles for the logical ECHO and FOXTROT switches from configured management addresses and configured host- and core-facing port names. It reads inventory through <code>OdlRestconfDataClient</code>, accepts only HTTP 200, 201, or 204, and delegates XML interpretation to <code>OpenflowInventoryXmlDeserializer</code>. The deserializer is responsible for validating inventory against those profiles. A failed read or invalid profile relationship increments the discovery-failure metric and escapes as a runtime failure, so the provider's enclosing readiness cycle clears controller readiness and schedules recovery.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/openflow/OpenflowInventoryService.java" include-lines="96-129"/>

The resulting record carries a logical name, management address, dynamic node ID, URI-encoded node ID, and connector indexes. Bootstrap uses configured connector names rather than assuming a fixed datapath ID, while the later PacketIn extractor can resolve the full node and connector identities reported by OpenFlow. This separation permits the runtime inventory to choose the OpenFlow identity without weakening the application's explicit topology expectations.

<code-block lang="mermaid">
flowchart TD
    Get["GET operational inventory"] --> Status{"200, 201, or 204?"}
    Status -- no --> Failure["throw and count discovery failure"]
    Status -- yes --> Parse["deserialize against ECHO and FOXTROT profiles"]
    Parse --> Profiles{"both profiles and connectors valid?"}
    Profiles -- no --> Failure
    Profiles -- yes --> Records["OpenflowSwitchRecord values"]
    Records --> Publish["replace registry snapshot"]
</code-block>

## Deterministic access-plane rules {id="openflow-deterministic-access-rules"}

For every discovered switch, bootstrap requires the configured host and core connectors, creates four immutable flow definitions, and assigns a switch-specific cookie range. The rule set is directional: ARP is forwarded between host and core in both directions, while IPv4 is likewise forwarded in both directions. Only host-to-core IPv4 sets <code>copyToController</code>, which preserves forwarding while asking the switch to deliver a controller copy. The opposite IPv4 direction and both ARP rules do not request a controller copy.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/openflow/OpenflowBootstrapService.java" include-lines="199-265"/>

<code-block lang="mermaid">
flowchart LR
    Host["host-facing connector"] -->|"ARP, no controller copy"| Core["core-facing connector"]
    Core -->|"ARP, no controller copy"| Host
    Host -->|"IPv4, forward + controller copy"| Core
    Core -->|"IPv4, no controller copy"| Host
    Host -->|"PacketIn copy for eligible IPv4"| Controller["OpenFlow Plugin and PacketIn listener"]
</code-block>

The coordinator clears its local readiness metric at the beginning of every cycle. With bootstrap enabled, it discovers and publishes the inventory snapshot, installs each rule, verifies the switch, and only then marks itself ready and records a successful timestamp. If installation, verification, profile validation, or connector lookup fails, the catch block leaves readiness false and rethrows. The provider, rather than the bootstrap service, owns scheduling the next whole control-plane attempt. When bootstrap is disabled, the service deliberately reports ready without installing or verifying access flows. A configured default-drop request only produces a warning in this implementation: it retains the OpenDaylight table-miss behavior instead of installing that default-drop flow.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/openflow/OpenflowBootstrapService.java" include-lines="97-169"/>

## RESTCONF installation and propagation verification {id="openflow-installation-and-verification"}

<code>OpenflowFlowProvisioningService</code> serializes each definition once and submits it with the discovered switch's encoded node ID, table ID, and flow ID. HTTP 200, 201, and 204 complete the installation. HTTP 409 and 503, together with transport-side runtime failures, are retryable up to the configured attempt limit with bounded, jittered backoff. Other unsuccessful HTTP statuses return a permanent failure result immediately. An interruption while waiting restores the thread interrupt flag and becomes an <code>IllegalStateException</code>, which aborts the bootstrap cycle.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/openflow/OpenflowFlowProvisioningService.java" include-lines="92-151"/>

Provisioning acceptance is not considered data-plane confirmation. <code>OpenflowBootstrapVerifier</code> first reads each individual configured flow and requires its exact ID in the response. It then uses <code>RetryPolicy.retryUntilTrue</code> to poll the operational table until every expected ID is present. A missing configured flow fails immediately. An unavailable operational table or an absent flow returns false to the polling policy and becomes a readiness failure when that deadline is exhausted.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/openflow/OpenflowBootstrapVerifier.java" include-lines="66-135"/>

<code-block lang="mermaid">
sequenceDiagram
    participant Boot as OpenflowBootstrapService
    participant Put as OpenflowFlowProvisioningService
    participant ODL as ODL RESTCONF
    participant Verify as OpenflowBootstrapVerifier
    Boot->>Put: installFlow(definition)
    Put->>ODL: PUT flow configuration
    alt retryable 409, 503, or transport failure
        Put->>Put: bounded jittered retry
    end
    Put-->>Boot: successful installation result
    Boot->>Verify: verify(switch, four flows)
    Verify->>ODL: GET each configured flow
    Verify->>ODL: poll operational table
    ODL-->>Verify: every expected flow ID present
    Verify-->>Boot: verification completes
</code-block>

## Admission, readiness, and concurrent failure boundaries {id="openflow-admission-concurrency-failure"}

The provider's readiness executor has one thread, so readiness cycles do not overlap. It performs explicit <code>closed</code> checks between remote phases, but the source does not claim that those checks serialize shutdown with an already-started remote operation. During a runtime failure, the provider sets <code>controlPlaneReady</code> false, publishes operational state, calculates a jittered delay, and schedules the next attempt if it remains open. The bootstrap flag is atomic, and the switch registry snapshot is volatile, which gives packet processing thread-visible readiness and inventory state without making a PacketIn callback wait for bootstrap.

<code-block lang="mermaid">
stateDiagram-v2
    [*] --> NotReady
    NotReady --> Discovering: readiness attempt
    Discovering --> Programming: inventory snapshot published
    Programming --> Verifying: four flows per switch accepted
    Verifying --> Ready: config and operational IDs confirmed
    Discovering --> NotReady: discovery failure
    Programming --> NotReady: installation or connector failure
    Verifying --> NotReady: validation timeout or missing flow
    Ready --> NotReady: later failed readiness attempt or shutdown
</code-block>

At the packet boundary, <code>SdnMplsMlWorkflowService</code> checks the provider-supplied readiness callback before extracting packet features. It counts and returns for a not-ready controller. After readiness, it separately rejects missing context, non-IPv4 traffic, an unknown ingress direction, and stale topology. Consequently, a discovered registry does not alone authorize classification. It is necessary for ingress interpretation, while the provider-wide gate establishes that topology, delegated LSPs, and verified access flows have all completed their initial readiness work.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/SdnMplsMlWorkflowService.java" include-lines="139-195"/>

## Suppression capability and current integration boundary {id="openflow-suppression-integration-boundary"}

<code>OpenFlowSuppressionService</code> is designed to build a temporary IPv4 intent only when suppression is enabled and the packet matches ICMP, TCP with a positive destination port, or UDP with a positive destination port. It resolves the ingress switch by its dynamic node ID, requires both the ingress connector and the configured core-facing connector, then derives a deterministic suppression flow ID and cookie. It returns an empty optional for disabled suppression, non-IPv4 traffic, unsupported protocols, unresolved switches, or unresolved connectors. Building the intent itself does not submit RESTCONF.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/openflow/OpenFlowSuppressionService.java" include-lines="89-140"/>

If an integration calls <code>installSuppressionFlow</code>, that method performs one PUT and classifies 200, 201, and 204 as success. Unlike bootstrap provisioning, it does not retry. A transport exception is converted into an unsuccessful <code>OpenFlowProgrammingResult</code> with status zero, so this method's documented contract does not itself turn a suppression write into a tunnel failure. That behavior must not be conflated with the active workflow: no production Java source calls either suppression method, and the present workflow records <code>suppression_flow_enabled</code> as false after a policy decision.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/openflow/OpenFlowSuppressionService.java" include-lines="153-184"/>

<code-block lang="mermaid">
flowchart TD
    Packet["admitted IPv4 PacketIn"] --> Match{"ICMP or TCP/UDP with positive destination port?"}
    Match -- no --> Empty["no suppression intent"]
    Match -- yes --> Resolve["resolve ingress switch and connectors"]
    Resolve --> Connectors{"ingress and core connector present?"}
    Connectors -- no --> Empty
    Connectors -- yes --> Intent["OpenFlowSuppressionIntent"]
    Intent -. "available service API, not called by current workflow" .-> Put["one RESTCONF PUT if integrated"]
    Put --> Result["OpenFlowProgrammingResult"]
</code-block>

The boundary is important for interpreting packet behavior. The bootstrap IPv4 host-to-core flow supplies the controller copy that makes classification possible. The current workflow's admission and pair-policy stages decide whether a packet can influence control-plane work, but they do not install a temporary suppression rule. A later integration could use the same registry and packet context to invoke the suppression service, yet it would need to preserve the existing distinction between a best-effort suppression result and the policy and delegated-LSP outcomes owned by the [PacketIn workflow](Java-Controller-Side-Application-—-Application-Construction-PacketIn-Classification-Workflow.md#directional-evidence-construction).

## Bootstrap class contracts and evidence boundary {id="openflow-bootstrap-class-contracts-and-evidence-boundary"}

<code>OpenflowBootstrapService</code> coordinates the lifecycle, but it does not own every guarantee needed by readiness. <code>OpenflowInventoryService</code> owns profile-based conversion from operational inventory into dynamic switch records. <code>OpenflowSwitchRegistry</code> owns publication of those records. It first builds indexes for logical name, management address, node ID, and connector ID, rejects duplicates while those temporary maps are mutable, and assigns one immutable <code>Snapshot</code> to a volatile field. A reader of the registry therefore observes a complete old snapshot or a complete new snapshot. The volatile assignment does not coordinate a reader with a later bootstrap failure, which is why provider readiness remains a separate admission gate.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/openflow/OpenflowSwitchRegistry.java" include-lines="24-66"/>

<code-block lang="mermaid">
flowchart LR
    InventoryService[OpenflowInventoryService] --> Parsed[profile-validated records]
    Parsed --> Registry[OpenflowSwitchRegistry]
    Registry --> Build[temporary four-index map set]
    Build --> Reject{duplicate switch or connector identity?}
    Reject -- yes --> Previous[retain prior snapshot]
    Reject -- no --> Publish[one volatile immutable Snapshot assignment]
    Publish --> Bootstrap[OpenflowBootstrapService]
    Publish --> PacketIn[PacketInFeatureExtractor]
    Bootstrap --> Readiness[provider readiness gate]
</code-block>

<code>OpenflowFlowProvisioningService</code> owns retry classification rather than delegating it to bootstrap. It serializes the definition once, then retries only HTTP 409, HTTP 503, and transport exceptions. A nonretryable response becomes a permanent unsuccessful result. Each retry waits with jittered exponential backoff, and interruption restores the thread interrupt flag before failing the lifecycle. Bootstrap responds uniformly to all unsuccessful results by leaving readiness false, whereas the provisioning result preserves whether that failure was permanent or caused by exhausted retryable attempts.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/openflow/OpenflowFlowProvisioningService.java" include-lines="92-179"/>

The verifier establishes controller-side propagation evidence in two stages. It requires each exact flow ID from the configuration endpoint, then polls the operational table until all expected IDs appear. That proves OpenDaylight exposes the expected flow identifiers in both datastore views. It does not measure packets, inspect switch counters, or prove an individual packet followed the action list. The controller copy used for PacketIn is therefore an intended and verified flow definition whose per-packet effect is observed at the later notification boundary.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/openflow/OpenflowBootstrapVerifier.java" include-lines="66-135"/>

<code-block lang="mermaid">
sequenceDiagram
    participant Bootstrap as OpenflowBootstrapService
    participant Provisioning as OpenflowFlowProvisioningService
    participant Config as RESTCONF configuration
    participant Verifier as OpenflowBootstrapVerifier
    participant Operational as RESTCONF operational table
    Bootstrap->>Provisioning: install four definitions
    Provisioning->>Config: PUT each serialized definition
    Provisioning-->>Bootstrap: classified result
    Bootstrap->>Verifier: verify(switch, definitions)
    Verifier->>Config: require every exact flow ID
    loop propagation deadline
        Verifier->>Operational: read table and test all IDs
    end
    Verifier-->>Bootstrap: operational IDs present
    Bootstrap-->>Bootstrap: ready=true
</code-block>
