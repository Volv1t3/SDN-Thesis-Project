# Java Controller-Side Application Documentation Pack

This document describes the OpenDaylight controller-side implementation under `src/main/java/sdn-mpls-ml-odl-cs-app/impl`. It is intended as a Writerside-ready reference and companion to the Mermaid source files in this directory.

## Diagram Index

| Diagram | Source file | Scope |
|---|---|---|
| Lifecycle and readiness sequence | `docs/java-controller/lifecycle-and-readiness-sequence.mmd` | OSGi provider startup, service construction, metrics servlet registration, BGP-LS/PCEP/OpenFlow readiness, retry scheduling, and operational state publication. |
| Packet workflow sequence | `docs/java-controller/packet-workflow-sequence.mmd` | End-to-end PacketReceived handling, classification, consensus, path computation, delegated LSP update, metrics, and RESTCONF state publication. |
| Class diagrams | `docs/java-controller/class-diagrams.mmd` | Explicit class, record, enum, registry, serializer, service, metrics, and utility map for the implementation. |
| Component diagram | `docs/java-controller/component-diagram.mmd` | Package-level components, provided interfaces, internal dependencies, and external OpenDaylight/Python/Prometheus integrations. |
| Domain model | `docs/java-controller/domain-model.mmd` | Core records and state objects shared by packet, classification, policy, path, tunnel, topology, OpenFlow, and observability flows. |
| Method and process reference | `docs/java-controller/internal-processes-and-methods.md` | Writerside-ready method tables for lifecycle, workflow, HTTP, serialization, topology, policy, tunnel, OpenFlow, registries, metrics, and utilities. |
| BGP-LS topology refresh | `docs/java-controller/topology-refresh-process.mmd` | Periodic and on-demand BGP-LS refresh, TTL expiry detection, single-flight refresh deduplication, and freshness metrics. |
| OpenFlow bootstrap | `docs/java-controller/openflow-bootstrap-process.mmd` | OpenFlow inventory discovery, bootstrap flow generation, RESTCONF PUT, config validation, and operational polling. |
| Pair policy consensus | `docs/java-controller/pair-policy-consensus-process.mmd` | Directional evidence recording, pair consensus, priority conflict handling, preemption, and active policy replacement. |
| Delegated LSP update | `docs/java-controller/delegated-lsp-update-process.mmd` | Directional LSP application, path computation, PCEP update-lsp, response classification, and convergence polling. |
| Operational state publication | `docs/java-controller/operational-state-publication-process.mmd` | Registry snapshots projected into the `csa:controller-state` operational datastore tree. |
| Metrics exposition | `docs/java-controller/metrics-exposition-process.mmd` | Metric mutation inside services and Prometheus text rendering at `/csa/metrics`. |

## Application Role

The Java application is an OpenDaylight bundle that listens for OpenFlow `PacketReceived` notifications, extracts safe packet features, asks the Python classifier for traffic class and policy, computes constrained paths with ODL path-computation RPCs, and updates preexisting delegated RSVP-TE LSPs through PCEP `update-lsp`. It also publishes internal runtime state into the MD-SAL operational datastore under `csa:controller-state` and exposes custom Prometheus text metrics at `/csa/metrics`.

## External Interfaces

| Interface | Direction | Implementation | Purpose |
|---|---|---|---|
| OpenFlow `PacketReceived` notifications | Inbound | `SDN_MPLS_ML_Provider.onNotification` | Trigger packet classification and control-cycle processing. |
| Python classifier API | Outbound | `ClassifierRestClient.classify` via `ClassificationService` | POST packet features to `/api/v1/classify` and receive classification plus policy. |
| ODL RESTCONF data | Outbound | `OdlRestconfDataClient` | Read network topology, BGP-LS topology, PCEP topology, OpenFlow inventory, OpenFlow table state, and write OpenFlow flow config. |
| ODL RESTS operations | Outbound | `OdlOperationsClient` | Invoke `path-computation:get-constrained-path` and `network-topology-pcep:update-lsp`. |
| MD-SAL operational datastore | Outbound/public state | `ControllerOperationalStatePublisher` and `csa.yang` | Publish read-only controller state for RESTCONF clients. |
| Prometheus metrics | Inbound scrape | `PrometheusMetricsServlet` and `SdnMplsMlMetrics` | Serve metrics at `/csa/metrics`. |

## Public RESTCONF State

The YANG model `impl/src/main/yang/csa.yang` defines a read-only operational tree rooted at:

```text
/restconf/data/csa:controller-state
```

| Path | Meaning |
|---|---|
| `/restconf/data/csa:controller-state` | Complete CSA operational snapshot. |
| `/restconf/data/csa:controller-state/control-plane` | Readiness, closed flag, topology TTL, refresh state, refresh counters, and BGP-LS freshness timestamps. |
| `/restconf/data/csa:controller-state/cache-state` | Aggregate counts for classification, path, evidence, active policy, delegated LSP, and OpenFlow caches. |
| `/restconf/data/csa:controller-state/policy-state` | Consensus/preemption/LSP application settings currently active from `AppConfig`. |
| `/restconf/data/csa:controller-state/bgp-ls-node` | All cached BGP-LS nodes; key is `router-id` if retrieving one item. |
| `/restconf/data/csa:controller-state/openflow-switch` | Cached OpenFlow switches and nested connector state. |
| `/restconf/data/csa:controller-state/classification-exact-cache-entry` | Exact packet-feature classification cache. |
| `/restconf/data/csa:controller-state/classification-service-cache-entry` | Service-normalized classification cache. |
| `/restconf/data/csa:controller-state/calculated-path-cache-entry` | Calculated CSPF paths and EROs. |
| `/restconf/data/csa:controller-state/delegated-lsp-entry` | Discovered delegated PCEP LSPs by direction. |
| `/restconf/data/csa:controller-state/directional-policy-evidence-entry` | Per-direction evidence currently held for pair consensus. |
| `/restconf/data/csa:controller-state/active-pair-policy-entry` | Active pair policies and their TTL/application children. |
| `/restconf/data/csa:controller-state/directional-lsp-application-entry` | Last known directional LSP application records attached to active policies. |
| `/restconf/data/csa:controller-state/tunnel-pair-entry` | Known tunnel pair definition. |
| `/restconf/data/csa:controller-state/tunnel-direction-entry` | Known directional tunnel endpoints. |

## Main Processes

| Process | Trigger | Main classes | Result |
|---|---|---|---|
| Bundle initialization | OSGi Blueprint calls `init()` | `SDN_MPLS_ML_Provider`, `AppConfig`, HTTP clients, registries, serializers, services | Services are constructed, `/csa/metrics` is registered, PacketReceived listener is registered, initial operational state is published. |
| Control-plane readiness | Scheduled by `scheduleReadinessAttempt` | `TopologyDiscoveryService`, `TopologyRefreshService`, `DelegatedLspService`, `OpenflowBootstrapService` | BGP-LS nodes, PCEP delegated LSPs, and OpenFlow access flows are discovered/validated; `controlPlaneReady` becomes true. |
| Periodic BGP-LS refresh | `TopologyRefreshService.start()` scheduled executor | `TopologyRefreshService`, `OdlRestconfDataClient`, `BgpLsTopologyXmlDeserializer`, `BgpLsNodeRegistry` | BGP-LS cache is refreshed every topology TTL interval. |
| On-demand BGP-LS refresh | Packet workflow sees stale topology | `TopologyRefreshService.ensureFresh()` | One synchronous single-flight RESTCONF refresh is attempted before PacketIn processing is allowed to continue. |
| Packet control cycle | OpenFlow `PacketReceived` | `SDN_MPLS_ML_Provider`, `SdnMplsMlWorkflowService`, all downstream services | Packet is classified and may lead to delegated LSP update. |
| Classification | Packet workflow cache miss | `ClassificationService`, `ClassifierRestClient`, JSON serializers, `ClassificationRegistrar` | Python classifier result is cached in exact and service caches. |
| Pair policy consensus | New directional evidence | `PairPolicyCoordinator`, `DirectionalClassificationEvidenceRegistry`, `PairPolicyConsensusService` | Candidate pair policy is selected, deferred, or blocked pending more evidence. |
| Policy preemption | Candidate with existing active policy | `PolicyPreemptionEvaluator`, `ActivePairPolicyRegistry` | Active policy is retained, refreshed, replaced due expiration, or preempted by priority. |
| Directional LSP application | Candidate policy selected | `DirectionalLspApplicationService`, `PathComputationService`, `DelegatedLspService` | Path is computed/cached, current LSP is compared, update-lsp is sent if required, result is recorded. |
| Operational state publication | Provider lifecycle and packet finally paths | `ControllerOperationalStatePublisher` | Complete safe runtime snapshot replaces `csa:controller-state`. |
| Metrics scrape | HTTP GET `/csa/metrics` | `PrometheusMetricsServlet`, `SdnMplsMlMetrics` | Prometheus text exposition is returned. |

## Package Reference

| Package | Provides | Main collaborators |
|---|---|---|
| `com.sma.sdn.impl` | OSGi lifecycle, listener registration, service graph creation, readiness retry loop, metrics servlet registration. | All application packages, ODL MD-SAL services, OSGi `HttpService`. |
| `com.sma.sdn` | Top-level packet workflow orchestration. | Packet extraction, classification, direction/pair registries, policy coordinator, topology refresh, metrics. |
| `com.sma.sdn.config` | Immutable runtime configuration from environment. | Provider, registries, OpenFlow bootstrap, topology/path/tunnel services. |
| `com.sma.sdn.http` | Java HTTP clients for classifier, RESTCONF data, RESTS operations, and response classifiers. | Classification, topology, OpenFlow, path, tunnel services. |
| `com.sma.sdn.packet` | PacketIn feature extraction and ingress mapping. | OpenFlow inventory registry, `PacketReceived`, `AppConfig`. |
| `com.sma.sdn.classification` | Classification cache lookup and Python classifier invocation. | `ClassificationRegistrar`, `ClassifierRestClient`, JSON serializers, metrics. |
| `com.sma.sdn.policy` | Service-key normalization, evidence hashing, pair consensus, preemption, pair-policy coordination. | Evidence/active/pair registries, directional LSP application. |
| `com.sma.sdn.path` | CSPF path computation, path cache, ERO translation. | BGP-LS registry, ODL operations, XML serializers/deserializers. |
| `com.sma.sdn.tunnel` | Delegated LSP discovery, state comparison, update-lsp, directional application. | PCEP topology reads, ODL operations, retry policy, path computation. |
| `com.sma.sdn.topology` | Initial BGP-LS discovery, TTL-based refresh, bandwidth encoding. | RESTCONF data client, BGP-LS registry, metrics, operational publisher callback. |
| `com.sma.sdn.openflow` | OpenFlow inventory discovery, switch/connector registry, bootstrap flow installation and verification. | RESTCONF data client, OpenFlow XML serializers, retry policy. |
| `com.sma.sdn.registry` | Thread-safe in-memory state stores and TTL evictions. | Workflow, publisher, metrics. |
| `com.sma.sdn.serialization.json` | Classifier request/response JSON adapters. | Classification service, Jackson. |
| `com.sma.sdn.serialization.xml` | ODL XML serializers/deserializers for BGP-LS, PCEP, path computation, update-lsp, OpenFlow suppression. | HTTP clients and service layers. |
| `com.sma.sdn.operational` | MD-SAL operational datastore projection. | All registries, `csa.yang`, DataBroker. |
| `com.sma.sdn.metrics` | In-memory Prometheus registry and servlet. | All services that record metrics, OSGi HTTP service. |
| `com.sma.sdn.observability` | Structured logging and scoped log context. | Every process path. |
| `com.sma.sdn.util` | XML helper functions and retry policy. | Serializers/deserializers and LSP/OpenFlow verification. |
| `com.sma.sdn.model` | Records/enums for packets, policies, paths, LSPs, OpenFlow, outcomes, caches, and workflow state. | All packages. |

## Class Reference

| Class / record / enum | Package | Responsibility |
|---|---|---|
| `SDN_MPLS_ML_Provider` | `impl` | Owns OSGi lifecycle, constructs service graph, handles PacketReceived notifications, publishes state, and closes resources. |
| `SdnMplsMlWorkflowService` | root | Coordinates one PacketIn workflow from feature extraction through policy decision and LSP application. |
| `AppConfig` | `config` | Immutable configuration contract loaded from environment. |
| `IngressMapping` | `config` | Parses and matches configured ingress switch/connector tokens. |
| `TunnelCreationMode` | `config` | Supported tunnel operation mode enum. Current provider requires `DELEGATED_TUNNEL_UPDATE`. |
| `ClassifierRestClient` | `http` | POSTs classifier JSON to the Python API. |
| `OdlRestconfDataClient` | `http` | Performs ODL RESTCONF data GET/PUT calls. |
| `OdlOperationsClient` | `http` | Performs ODL RESTS operation POST calls. |
| `OdlXmlExchangeLogger` | `http` | Logs XML request/response exchanges with configured body verbosity. |
| `PathComputationOutcomeClassifier` | `http` | Classifies path-computation HTTP/XML responses. |
| `TopologyDiscoveryOutcomeClassifier` | `http` | Classifies topology discovery responses. |
| `UpdateLspOutcomeClassifier` | `http` | Classifies update-lsp immediate responses as success, failure, provisional, or ambiguous. |
| `HttpClientFactory` | `http` | Creates Java `HttpClient` with request timeout policy. |
| `ClassificationService` | `classification` | Reuses classification caches or invokes the classifier API and records results. |
| `PacketInFeatureExtractor` | `packet` | Extracts payload length, ingress switch/connector, EtherType, IP protocol, ports, and direction hints from PacketIn. |
| `TopologyDiscoveryService` | `topology` | Performs mandatory initial network topology and BGP-LS discovery. |
| `TopologyRefreshService` | `topology` | Maintains BGP-LS freshness via periodic and on-demand refresh. |
| `TopologyRefreshStatus` | `topology` | Snapshot of BGP-LS refresh timestamps, freshness, counters, and failure reason. |
| `BandwidthTranslator` | `topology` | Converts Kbps to bytes/sec and PCEP float32 Base64 bandwidth. |
| `PathComputationService` | `path` | Resolves BGP-LS graph node IDs, caches CSPF paths, invokes ODL path computation, translates responses to ERO. |
| `CalculatedPathToEroTranslator` | `path` | Converts path-computation hop descriptions into strict ERO subobjects ending at destination router ID. |
| `DelegatedLspService` | `tunnel` | Discovers PCEP delegated LSPs, compares active state, sends update-lsp, and confirms convergence. |
| `DirectionalLspApplicationService` | `tunnel` | Applies a selected pair policy to one tunnel direction with per-direction locking. |
| `TunnelOperationCoordinator` | `tunnel` | Legacy/idempotent tunnel operation coordination around recent intents and status journal. |
| `PairPolicyCoordinator` | `policy` | Serializes pair-level evidence recording, consensus, preemption, and LSP application. |
| `PairPolicyConsensusService` | `policy` | Evaluates left/right directional evidence and selects/blocks/defer candidates. |
| `PolicyPreemptionEvaluator` | `policy` | Decides whether incoming candidate refreshes, replaces, preempts, or is retained against active policy. |
| `PairPolicyHashService` | `policy` | Builds stable hashes for evidence, policy candidates, and desired LSP state. |
| `ServiceKeyResolver` | `policy` | Normalizes packet features into service keys and expected service classes. |
| `ControllerOperationalStatePublisher` | `operational` | Projects registries/config/runtime status into the MD-SAL operational `csa:controller-state` tree. |
| `OpenflowBootstrapService` | `openflow` | Discovers OpenFlow inventory and installs/verifies required bootstrap flows. |
| `OpenflowInventoryService` | `openflow` | Reads OpenFlow inventory XML. |
| `OpenflowInventoryXmlDeserializer` | `openflow` | Parses ODL inventory into switch/connector records. |
| `OpenflowSwitchRegistry` | `openflow` | Stores resolved OpenFlow switches and connector lookups. |
| `OpenflowFlowProvisioningService` | `openflow` | Installs configured OpenFlow flow entries with RESTCONF PUT. |
| `OpenflowBootstrapVerifier` | `openflow` | Polls operational table state to confirm bootstrap flows exist. |
| `OpenflowFlowXmlSerializer` | `openflow` | Serializes OpenFlow flow definitions to ODL flow XML. |
| `OpenFlowSuppressionService` | `openflow` | Builds and installs suppression intent flows where enabled. |
| `ClassificationRequestJsonSerializer` | `serialization.json` | Builds the Python classifier request body from safe packet features. |
| `ClassificationResponseJsonDeserializer` | `serialization.json` | Validates classifier responses, extracts prediction/policy/probabilities, and computes cache expiry. |
| `NetworkTopologyListXmlDeserializer` | `serialization.xml` | Parses available ODL topology IDs from RESTCONF topology-list XML. |
| `BgpLsTopologyXmlDeserializer` | `serialization.xml` | Parses BGP-LS router IDs and graph node IDs from topology XML. |
| `PathComputationRequestXmlSerializer` | `serialization.xml` | Serializes constrained-path RPC input XML. |
| `PathComputationResponseXmlDeserializer` | `serialization.xml` | Parses constrained-path RPC output status, hops, and TE metric. |
| `EroXmlSerializer` | `serialization.xml` | Serializes strict/loose ERO subobjects for PCEP update-lsp requests. |
| `PcepTopologyXmlDeserializer` | `serialization.xml` | Parses complete PCEP topology snapshots into reported LSP records. |
| `PcepReportedLspDeserializer` | `serialization.xml` | Parses one reported LSP, including PLSP ID, tunnel IDs, flags, ERO, LSPA, and bandwidth. |
| `UpdateLspRequestXmlSerializer` | `serialization.xml` | Serializes PCEP update-lsp RPC input XML, including compact YANG instance identifiers. |
| `UpdateLspResponseXmlDeserializer` | `serialization.xml` | Converts immediate update-lsp HTTP/XML responses into `UpdateLspResult`. |
| `OpenFlowSuppressionFlowXmlSerializer` | `serialization.xml` | Serializes suppression flow XML. |
| `SdnMplsMlMetrics` | `metrics` | Stores counters/gauges/histograms and renders Prometheus text. |
| `PrometheusMetricsServlet` | `metrics` | OSGi servlet that serves `/csa/metrics`. |
| `StructuredLogger` | `observability` | Emits structured events with component/operation/message/metadata fields. |
| `LogContext` | `observability` | Thread-local contextual fields such as workflow ID, packet sequence, direction, LSP, and PLSP ID. |
| `RetryPolicy` | `util` | Repeats reads until an optional value or boolean condition is confirmed, with delay, timeout, and jitter. |
| `XmlSupport` | `util` | Safe DOM parsing, XPath helpers, type extraction, element checks, and XML escaping. |

## Model Record And Enum Reference

| Model | Kind | Role |
|---|---|---|
| `PacketFeatures` | record | Normalized packet feature tuple used for classifier requests, service keys, and cache keys. |
| `PacketClassificationContext` | record | PacketIn-derived context containing ingress, direction, features, and receive timestamp. |
| `WorkflowContext` | record | Workflow ID and packet sequence correlation extracted from logging MDC for asynchronous/control-cycle records. |
| `ClassificationCacheKey` | record | Exact classification cache key with ingress connector and raw packet features. |
| `ServiceClassCacheKey` | record | Service-level classification cache key using canonical service port. |
| `ClassificationLookupResult` | record | Classification result plus cache-hit flag. |
| `ClassificationResult` | record | Classifier response, probabilities, policy, processing time, cache timestamps, and TTL. |
| `TrafficPolicy` | record | Selected policy profile, DSCP, MPLS TC, fallback state, and path constraints. |
| `PathConstraints` | record | Requested bandwidth and RSVP-TE setup/hold priority. |
| `ServiceKey` | record | Canonical service identity derived from packet features. |
| `DirectionalPolicyEvidence` | record | Per-direction evidence used by pair consensus. |
| `PairConsensusBucket` | record | Left/right evidence bucket for one pair/service key. |
| `PairConsensusDecision` | record | Consensus status, selected candidate, and conflict reason. |
| `PairConsensusStatus` | enum | Pending, match, conflict, priority, service-key, and provisional consensus outcomes. |
| `PairConsensusEqualPriorityAction` | enum | Behavior for equal-priority policy conflicts. |
| `PairPolicyCandidate` | record | Pair-level policy candidate selected from directional evidence. |
| `PairPolicyDecision` | record | Final coordinator decision with candidate, active policy, preemption, and LSP applications. |
| `PolicyPreemptionDecision` | record | Preemption decision and reason. |
| `PolicyPreemptionDecisionType` | enum | Same-policy refresh, retention, priority preemption, and expired replacement outcomes. |
| `ActivePairPolicyState` | record | Installed pair policy state, generation, TTL, and child LSP application records. |
| `PairPolicyLspApplicationScope` | enum | Whether selected policies apply to the observed direction or bidirectional pair. |
| `DirectionalLspDesiredState` | record | Desired one-direction PCEP LSP state and stable hash. |
| `DirectionalLspApplicationRecord` | record | Result of applying a pair policy to one directional LSP. |
| `TunnelEndpoint` | record | Logical router endpoint with router ID and PCC node ID. |
| `TunnelDirection` | record | Direction key plus source and destination endpoints. |
| `TunnelPairDefinition` | record | Managed pair with forward and reverse tunnel directions. |
| `TunnelIntentKey` | record | Legacy idempotency key for tunnel operations. |
| `TunnelOperationRecord` | record | Legacy tunnel operation journal entry. |
| `TunnelOperationStatus` | enum | Pending, accepted, confirmed, skipped, and failed operation states. |
| `TunnelUpdateScope` | enum | Tunnel update scope selection. |
| `BgpLsTopologyNode` | record | BGP-LS router and graph-node mapping. |
| `CalculatedPathKey` | record | Cache key for constrained path calculation. |
| `CalculatedPathRequest` | record | ODL path-computation RPC input model. |
| `PathComputationResponse` | record | Parsed path-computation output and completion helper. |
| `PathHop` | record | Local/remote IPv4 hop from path-computation output. |
| `EroSubobject` | record | Strict/loose ERO IP-prefix subobject. |
| `CalculatedPath` | record | Cached path, hops, ERO, metric, and TTL. |
| `PcepReportedLspSnapshot` | record | Parsed reported LSP snapshot from PCEP topology. |
| `DelegatedLspRecord` | record | Validated delegated LSP identity and active operational state. |
| `UpdateLspRequest` | record | PCEP update-lsp RPC input model. |
| `UpdateLspResult` | record | Parsed immediate update-lsp result. |
| `OdlCallOutcome` | record | Classified ODL HTTP/XML outcome. |
| `OdlCallOutcomeType` | enum | Confirmed success, provisional/ambiguous, and failure categories. |
| `OdlXmlBodyLogLevel` | enum | Controls whether XML bodies are logged. |
| `OpenflowSwitchRecord` | record | Resolved OpenFlow switch identity and connector map. |
| `OpenflowConnectorRecord` | record | Resolved OpenFlow connector identity and liveness/link flags. |
| `OpenflowFlowDefinition` | record | Bootstrap OpenFlow flow definition. |
| `OpenflowBootstrapProfile` | record | Bootstrap flow profile used during OpenFlow initialization. |
| `OpenflowFlowInstallResult` | record | Result of installing one OpenFlow flow. |
| `OpenFlowSuppressionIntent` | record | Suppression-flow intent model. |
| `OpenFlowProgrammingResult` | record | Suppression-flow programming result. |
| `FlowDirection` | enum | Logical packet/tunnel direction, including unknown ingress. |

## Registry Reference

| Registry | Keys | Values | TTL / eviction behavior |
|---|---|---|---|
| `BgpLsNodeRegistry` | Router ID | `BgpLsTopologyNode` | Replaced atomically by topology discovery/refresh; freshness is tracked in `TopologyRefreshService`. |
| `ClassificationRegistrar` | `ClassificationCacheKey` and `ServiceClassCacheKey` | `ClassificationResult` | `find()` expires old exact/service entries before returning; evictions increment classification eviction metrics. |
| `CalculatedPathRegistry` | `CalculatedPathKey` | `CalculatedPath` | `findValid()` and `expireOldEntries()` remove expired paths and update eviction metrics. |
| `DelegatedLspRegistry` | Direction key and LSP name indexes | `DelegatedLspRecord` | Replaced atomically from PCEP topology snapshots; updated after successful update-lsp. |
| `DirectionalClassificationEvidenceRegistry` | Pair key and service key bucket | `PairConsensusBucket` | `expireOldEvidence(now)` removes expired evidence and increments directional evidence eviction metrics. |
| `ActivePairPolicyRegistry` | Pair key | `ActivePairPolicyState` | Active policies expire by idle TTL and can be refreshed/replaced. |
| `DirectionRegistry` | Configured `FlowDirection` | `TunnelDirection` | Static mapping derived from `AppConfig`. |
| `TunnelPairRegistry` | Single pair and direction keys | `TunnelPairDefinition` / `TunnelDirection` | Static mapping derived from `AppConfig`. |
| `TunnelOperationRegistry` | `TunnelIntentKey` plus journal | `TunnelOperationRecord` | Legacy recent-intent registry with pending/usable TTL and bounded journal. |
| `OpenflowSwitchRegistry` | Logical switch name, node ID, connector ID | `OpenflowSwitchRecord` / `OpenflowConnectorRecord` | Replaced during OpenFlow inventory bootstrap. |

## Metrics Summary

| Family | Examples | Meaning |
|---|---|---|
| Packet ingress counters | `sma_packet_in_total`, `packet_ignored_arp`, `packet_ignored_unknown_ingress` | PacketReceived flow admission and discard reasons. |
| Classifier counters/histograms | `sma_classifier_request_total`, `sma_classifier_failure_total`, `sma_classifier_round_trip_duration_seconds` | Python classifier round trip and error tracking. |
| Classification cache | `sma_classification_cache_hit_total`, `sma_classification_cache_miss_total`, `sma_registry_classification_exact_entries` | Classification cache efficiency and size. |
| Path computation | `sma_path_cache_hit_total`, `sma_path_computation_success_total`, `sma_path_computation_duration_seconds` | CSPF cache/use and ODL operation performance. |
| BGP-LS topology TTL | `sma_bgpls_topology_ttl_expired_total`, `sma_bgpls_topology_refresh_on_demand_total`, `sma_bgpls_topology_fresh` | Topology freshness and refresh behavior. |
| PCEP/update-lsp | `sma_update_lsp_request_total`, `sma_update_lsp_success_total`, `sma_update_lsp_failure_total`, `sma_update_lsp_duration_seconds` | Delegated LSP update outcomes and duration. |
| LSP application | `sma_lsp_application_ero_confirmed_total`, `sma_lsp_application_bandwidth_confirmed_total`, `sma_lsp_application_failed_total` | Operational confirmation of requested tunnel state. |
| Pair consensus/preemption | `sma_pair_consensus_decision_total`, `sma_pair_policy_preempted_by_priority_total`, `sma_pair_policy_retained_equal_priority_total` | Policy model decisions and conflict resolution. |
| Control cycle | `sma_control_cycle_total`, `sma_control_cycle_duration_seconds` | End-to-end PacketIn-to-decision/update cycle outcomes. |
| Operational state publication | `sma_controller_operational_state_publish_success_total`, `sma_controller_operational_state_publish_failure_total` | MD-SAL state publication health. |
| OpenFlow bootstrap | `sma_openflow_inventory_discovery_success_total`, `sma_openflow_flow_install_success_total`, `sma_openflow_bootstrap_ready` | Inventory, access-flow install, and verification health. |

## TTL and Repolling Behavior

| Data | Owner | Freshness rule | Repoll trigger |
|---|---|---|---|
| BGP-LS topology | `TopologyRefreshService` plus `BgpLsNodeRegistry` | Fresh while `lastSuccessfulRefresh + topologyCacheTtl` is after `Instant.now()`. | Periodic scheduled refresh after startup and synchronous `ensureFresh()` when a PacketIn arrives after TTL expiry. |
| Classification entries | `ClassificationRegistrar` | `ClassificationResult.expiresAt()` from classifier response TTL. | Expired entries are evicted on lookup, causing a Python classifier request. |
| Calculated paths | `CalculatedPathRegistry` | `CalculatedPath.expiresAt()` from `pathCacheTtl`. | Expired entries are evicted on lookup, causing a new path-computation RPC. |
| Directional evidence | `DirectionalClassificationEvidenceRegistry` | Evidence/bucket expiry based on `pairConsensusEvidenceTtl`. | New evidence recording and explicit sweeps remove expired items. |
| Active pair policy | `ActivePairPolicyRegistry` | `activePairPolicyIdleTtl` refreshed on same-policy activity. | New candidate processing expires or replaces old policies. |
| PCEP delegated LSP state | `DelegatedLspService` | Always refreshed from PCEP topology before compare/update paths. | `refreshDirection()`, `activeStateMatches()`, update confirmation retry loop. |
| OpenFlow inventory | `OpenflowBootstrapService` | Resolved during bootstrap and stored in `OpenflowSwitchRegistry`. | Startup/bootstrap retry; not currently periodic in the main workflow. |

## Test Coverage Map

| Test file | Covered behavior |
|---|---|
| `UpdateLspOutcomeClassifierTest` | update-lsp response classification. |
| `UpdateLspRequestXmlSerializerTest` | update-lsp XML serialization, including compact `network-topology-ref`. |
| `PcepTopologyXmlDeserializerTest` | PCEP topology and reported LSP parsing. |
| `BgpLsTopologyXmlDeserializerTest` | BGP-LS topology parsing. |
| `OpenFlowSuppressionFlowXmlSerializerTest` | Suppression flow XML serialization. |
| `CalculatedPathToEroTranslatorTest` | Path response to strict ERO translation. |
| `BandwidthTranslatorTest` | Bandwidth conversion and PCEP Base64 encoding. |
| `DelegatedLspRegistryTest` | Delegated LSP registry indexing and validation. |
| `DirectionRegistryTest` | Ingress direction and tunnel direction resolution. |
| `TunnelOperationRegistryTest` | Legacy tunnel operation journal/idempotency behavior. |
| `PairPolicyConsensusServiceTest` | Pair evidence consensus outcomes. |
| `PolicyPreemptionEvaluatorTest` | Active policy preemption and retention decisions. |
| `StructuredLoggerTest` | Structured log field handling. |
