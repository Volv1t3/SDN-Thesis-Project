# Java Controller Internal Processes And Method Reference

This reference complements `java-controller-documentation.md` with method-level process notes for the Java controller-side application. It focuses on operational behavior rather than Javadoc syntax.

## Secondary Diagram Index

| Diagram | Source file | Process |
|---|---|---|
| BGP-LS topology refresh | `docs/java-controller/topology-refresh-process.mmd` | Periodic and on-demand topology refresh, TTL enforcement, single-flight deduplication, and freshness metrics. |
| OpenFlow bootstrap | `docs/java-controller/openflow-bootstrap-process.mmd` | Inventory discovery, deterministic flow generation, RESTCONF PUT, config validation, and operational polling. |
| Pair policy consensus | `docs/java-controller/pair-policy-consensus-process.mmd` | Evidence recording, consensus status, preemption, retention, refresh, and active policy replacement. |
| Delegated LSP update | `docs/java-controller/delegated-lsp-update-process.mmd` | Directional path computation, PCEP state comparison, update-lsp serialization, response classification, and convergence polling. |
| Operational state publication | `docs/java-controller/operational-state-publication-process.mmd` | Registry snapshot projection into `csa:controller-state` and RESTCONF visibility. |
| Metrics exposition | `docs/java-controller/metrics-exposition-process.mmd` | In-memory metric mutation and `/csa/metrics` Prometheus rendering. |

## Lifecycle Methods

| Method | Class | Process detail | State and metrics |
|---|---|---|---|
| `init()` | `SDN_MPLS_ML_Provider` | Loads `AppConfig`, creates HTTP clients, registries, serializers, service objects, operational publisher, workflow service, metrics servlet, and PacketReceived listener. | Registers `/csa/metrics`; publishes initial `csa:controller-state`; schedules readiness attempt. |
| `scheduleReadinessAttempt(config, attempt, delayMs)` | `SDN_MPLS_ML_Provider` | Submits delayed readiness work to a single-thread scheduled executor unless provider is closed. | Logs rejected scheduling unless shutdown is in progress. |
| `runReadinessAttempt(config, attempt, previousDelayMs)` | `SDN_MPLS_ML_Provider` | Runs BGP-LS discovery, marks initial topology success, validates delegated LSPs, bootstraps OpenFlow, starts topology refresh, then marks control plane ready. | On failure clears readiness, republishes state, and schedules exponential jittered retry. |
| `nextReadinessDelay(config, previousDelayMs)` | `SDN_MPLS_ML_Provider` | Computes bounded exponential backoff for readiness retries. | Uses `odlRetryInitialDelayMs` and `odlRetryMaxDelayMs`. |
| `onNotification(notification)` | `SDN_MPLS_ML_Provider` | Assigns packet sequence and workflow ID, logs raw notification snapshot, delegates to `SdnMplsMlWorkflowService.handlePacket`. | Always calls `publishOperationalState()` in `finally`. |
| `close()` | `SDN_MPLS_ML_Provider` | Marks provider closed, clears readiness, shuts down readiness executor, unregisters notification listener, closes topology refresh, and unregisters metrics servlet. | Publishes closed state before shutdown. |
| `registerMetricsServlet(metrics)` | `SDN_MPLS_ML_Provider` | Registers the metrics servlet with OSGi HTTP service at a fixed alias. | Throws if `/csa/metrics` cannot be registered. |

## Packet Workflow Methods

| Method | Class | Process detail | State and metrics |
|---|---|---|---|
| `handlePacket(notification)` | `SdnMplsMlWorkflowService` | Admission gate for one PacketIn. Rejects when control plane is not ready, packet is absent/non-IPv4, ingress is unknown, or topology cannot be refreshed. Otherwise classifies, builds directional evidence, invokes pair policy coordinator, and records a control-cycle outcome. | Mutates classification, evidence, active policy, path, and LSP registries through downstream services. Increments packet, ignored, cache, workflow, and control-cycle metrics. |
| `buildEvidence(context, direction, classification)` | `SdnMplsMlWorkflowService` | Converts a classification and ingress direction into `DirectionalPolicyEvidence`, including service key, policy fields, PCEP bandwidth encoding, evidence TTL, and stable hash. | Produces the consensus input object. |
| `recordControlCycle(outcome, className, startedAt)` | `SdnMplsMlWorkflowService` | Records E2E workflow counter and duration when timing was started. | Updates `sma_control_cycle_total` and `sma_control_cycle_duration_seconds`. |

## Configuration Methods

| Method | Class | Process detail |
|---|---|---|
| `fromEnvironment()` | `AppConfig` | Builds configuration from `System.getenv()`. |
| `from(env)` | `AppConfig` | Parses RESTCONF/RESTS/classifier URLs, topology IDs, tunnel endpoints, LSP names, cache TTLs, retry policy, OpenFlow options, consensus options, and LSP application options. |
| `resolveClassificationIngress(switchName, connectorName)` | `AppConfig` | Returns `HEADEND_TO_TAILEND` for ECHO host ingress, `TAILEND_TO_HEADEND` for FOXTROT host ingress, and `UNKNOWN` otherwise. |
| `parse(value)` | `IngressMapping` | Parses configured `switch:connector` style values into a matchable record. |
| `matches(switchName, connectorName)` | `IngressMapping` | Checks whether a discovered ingress matches the configured mapping tokens. |

## HTTP Client Methods

| Method | Class | Endpoint family | Process detail |
|---|---|---|---|
| `create(requestTimeout)` | `HttpClientFactory` | Java runtime | Creates a configured Java `HttpClient`. |
| `classify(jsonBody)` | `ClassifierRestClient` | Python `/api/v1/classify` | Sends JSON classification request and logs status/body according to safe structured logging. |
| `getNetworkTopologyList()` / `getNetworkTopologyListXml()` | `OdlRestconfDataClient` | RESTCONF data | Reads the ODL topology list. |
| `getBgpLsTopology(topologyId)` / `getBgpLsTopologyXml(topologyId)` | `OdlRestconfDataClient` | RESTCONF data | Reads BGP-LS topology XML. |
| `getPcepTopology(topologyId)` / `getPcepTopologyXml(topologyId)` | `OdlRestconfDataClient` | RESTCONF data | Reads PCEP topology with reported/configured LSP state. |
| `getOpenflowInventory()` | `OdlRestconfDataClient` | RESTCONF data | Reads OpenFlow inventory. |
| `putOpenflowFlow(encodedNodeId, tableId, flowId, xmlBody)` | `OdlRestconfDataClient` | RESTCONF config data | Writes one OpenFlow flow resource. |
| `getOpenflowFlowConfig(encodedNodeId, tableId, flowId)` | `OdlRestconfDataClient` | RESTCONF config data | Reads one configured flow to verify persistence. |
| `getOpenflowTableOperational(encodedNodeId, tableId)` | `OdlRestconfDataClient` | RESTCONF operational data | Reads table operational state to verify switch propagation. |
| `computeConstrainedPath(xmlBody)` | `OdlOperationsClient` | RESTS operations | Invokes `path-computation:get-constrained-path`. |
| `updateLsp(xmlBody)` | `OdlOperationsClient` | RESTS operations | Invokes `network-topology-pcep:update-lsp`. |
| `classify(response)` | `PathComputationOutcomeClassifier` | Response classifier | Maps path-computation response status/body to `OdlCallOutcome`. |
| `classify(response)` | `TopologyDiscoveryOutcomeClassifier` | Response classifier | Maps topology response status/body to `OdlCallOutcome`. |
| `classify(response)` / `classify(statusCode, body)` | `UpdateLspOutcomeClassifier` | Response classifier | Maps update-lsp response into confirmed/provisional/hard-failure outcome. |
| `ambiguous(reason)` | `UpdateLspOutcomeClassifier` | Response classifier | Creates an ambiguous update-lsp outcome when transport semantics cannot prove failure or success. |

## Classification And Serialization Methods

| Method | Class | Process detail |
|---|---|---|
| `classifyOrGetCached(context)` | `ClassificationService` | Looks up exact/service classification caches first, then calls Python classifier on miss. |
| `classify(context)` | `ClassificationService` | Serializes packet features, POSTs to classifier, checks HTTP status, deserializes response, records classifier round-trip histogram, stores result in both caches. |
| `serialize(features)` | `ClassificationRequestJsonSerializer` | Emits Python API request JSON with `packet_features.eth_type`, `ip_proto`, `src_port`, and `dst_port`. |
| `deserialize(json)` | `ClassificationResponseJsonDeserializer` | Validates classifier response contract, prediction, probabilities, policy, processing time, and computes result expiry from configured TTL. |

## Serialization Adapter Methods

These adapters form the explicit boundary between typed Java state and ODL/Python wire formats. They are intentionally documented separately from service orchestration because malformed scalar fields, namespaces, or response contracts usually fail here first.

| Method | Class | Wire format | Process detail |
|---|---|---|---|
| `serialize(features)` | `ClassificationRequestJsonSerializer` | JSON | Builds the classifier request payload from the normalized packet-feature tuple. |
| `deserialize(json)` | `ClassificationResponseJsonDeserializer` | JSON | Validates the Python response, rejects missing/invalid prediction or policy fields, records probabilities, and applies configured classification TTL. |
| `deserialize(xml)` | `NetworkTopologyListXmlDeserializer` | XML | Reads topology IDs from ODL network-topology output so startup can verify the configured BGP-LS topology exists. |
| `deserialize(xml)` | `BgpLsTopologyXmlDeserializer` | XML | Extracts BGP-LS router IDs and graph node IDs and returns `BgpLsTopologyNode` records for registry replacement. |
| `serialize(request)` | `PathComputationRequestXmlSerializer` | XML | Builds `path-computation:get-constrained-path` input, including graph name, graph node IDs, bandwidth, class type, address family, and algorithm. |
| `deserialize(xml)` | `PathComputationResponseXmlDeserializer` | XML | Parses path-computation output status, local/remote hop fields, and computed TE metric. |
| `serialize(subobjects)` | `EroXmlSerializer` | XML fragment | Emits ERO subobjects with strict/loose flag and IP prefix text. |
| `deserialize(xml)` | `PcepTopologyXmlDeserializer` | XML | Walks the PCEP topology, delegates each reported LSP to `PcepReportedLspDeserializer`, and returns all snapshots. |
| `deserialize(pccNode, reportedLsp)` | `PcepReportedLspDeserializer` | XML DOM node | Extracts reported LSP identity, tunnel identifiers, delegate/admin/operational flags, ERO, LSPA, and reported bandwidth. |
| `serialize(request)` | `UpdateLspRequestXmlSerializer` | XML | Builds `network-topology-pcep:update-lsp` input with compact `network-topology-ref`, PLSP ID, bandwidth, ERO, name, and node. |
| `deserialize(response)` | `UpdateLspResponseXmlDeserializer` | HTTP/XML | Classifies immediate update-lsp status/body into `UpdateLspResult` success, provisional success, hard failure, and failure reason. |
| `serialize(intent)` | `OpenFlowSuppressionFlowXmlSerializer` | XML | Emits legacy suppression-flow XML from a suppression intent. |
| `serialize(flowDefinition)` | `OpenflowFlowXmlSerializer` | XML | Emits bootstrap OpenFlow flow XML for RESTCONF PUT under a switch table. |

## Packet And Direction Methods

| Method | Class | Process detail |
|---|---|---|
| `extract(notification)` | `PacketInFeatureExtractor` | Extracts ingress node/connector from PacketReceived, resolves logical OpenFlow switch/connector, parses Ethernet/IP/TCP/UDP/ICMP fields from payload, and returns `PacketClassificationContext`. |
| `resolve(context)` | `DirectionRegistry` | Converts extracted logical switch/connector into `FlowDirection`. |
| `requireTunnelDirection(direction)` | `DirectionRegistry` | Returns configured `TunnelDirection` or throws for unsupported direction. |
| `requireBidirectionalTunnelDirections(ingressDirection)` | `DirectionRegistry` | Returns forward and reverse tunnel directions for paired processing. |
| `requireTunnelDirectionsForScope(scope, ingressDirection)` | `DirectionRegistry` | Selects one direction or both directions according to configured update scope. |
| `requirePairForDirection(directionKey)` | `TunnelPairRegistry` | Returns the single configured tunnel pair containing the direction. |
| `requireManagedDirections(pairKey)` | `TunnelPairRegistry` | Returns both configured pair directions. |
| `normalizePairKey(direction)` | `TunnelPairRegistry` | Normalizes a direction into the configured pair key. |
| `sideForSwitch(pairKey, ingressSwitchName)` | `TunnelPairRegistry` | Maps ingress switch to left/right side for evidence bucket placement. |

## Policy Consensus Methods

| Method | Class | Process detail | Metrics |
|---|---|---|---|
| `resolve(features)` | `ServiceKeyResolver` | Picks canonical service port and normalized service key from packet features. | None directly. |
| `expectedClassFor(serviceKey)` | `ServiceKeyResolver` | Returns expected traffic class for known service ports. | None directly. |
| `hashDirectionalEvidence(evidence)` | `PairPolicyHashService` | Hashes canonical evidence string with configured version. | Used by operational state and preemption identity. |
| `hashPolicyCandidate(candidate)` | `PairPolicyHashService` | Hashes pair policy candidate fields. | Used to detect same/changed policy. |
| `hashDesiredLspState(state)` | `PairPolicyHashService` | Hashes desired directional LSP state. | Used in `DirectionalLspApplicationRecord`. |
| `evaluate(bucket, activeBefore, now)` | `PairPolicyConsensusService` | Evaluates whether directional evidence matches, conflicts, times out into provisional single-side state, or remains pending. | Coordinator records decision labels. |
| `evaluate(active, candidate, now)` | `PolicyPreemptionEvaluator` | Determines same-policy refresh, expired replacement, incoming priority preemption, stronger active retention, or equal-priority retention. | Coordinator records retained/preempted/replaced counters. |
| `handleEvidence(evidence, workflowContext)` | `PairPolicyCoordinator` | Locks per pair, stores evidence, evaluates consensus, evaluates preemption, applies candidate to LSPs when allowed, installs/refreshed active policy, returns `PairPolicyDecision`. | Consensus, preemption, deferred, active, and failure counters. |

## Path Computation Methods

| Method | Class | Process detail | State and metrics |
|---|---|---|---|
| `computeOrGetCached(direction, constraints)` | `PathComputationService` | Converts requested bandwidth to bytes/sec, resolves source/destination graph node IDs from BGP-LS registry, checks calculated-path cache, and computes on miss. | Path cache hit/miss metrics. |
| `compute(direction, key)` | `PathComputationService` | Builds `CalculatedPathRequest`, invokes ODL path-computation with retry/outcome classification, deserializes response, translates to ERO, stores `CalculatedPath`. | Path request/success/failure counters and duration histogram. |
| `translate(response, destinationRouterId)` | `CalculatedPathToEroTranslator` | Converts path descriptions into strict ERO subobjects and appends final router ID. | Throws when response cannot produce a valid ERO. |
| `serialize(request)` | `PathComputationRequestXmlSerializer` | Emits XML input for `path-computation:get-constrained-path`. |
| `deserialize(xml)` | `PathComputationResponseXmlDeserializer` | Parses status, path descriptions, and computed TE metric. |

## Topology Methods

| Method | Class | Process detail | State and metrics |
|---|---|---|---|
| `initialize()` | `TopologyDiscoveryService` | Reads topology list and BGP-LS topology, validates expected topology exists, parses nodes, and ensures configured endpoints resolve. | Populates `BgpLsNodeRegistry`; increments discovery success/failure metrics. |
| `start()` | `TopologyRefreshService` | Starts fixed-delay BGP-LS refresh using topology TTL as interval. | Emits `topology_refresh_scheduled`. |
| `markInitialDiscoverySuccessful()` | `TopologyRefreshService` | Seeds freshness timestamps after mandatory startup discovery. | Updates freshness gauges and publishes state. |
| `staleBeyondThreshold()` | `TopologyRefreshService` | Checks `lastSuccessfulRefresh + topologyCacheTtl <= now`. | Used by workflow admission and status. |
| `ensureFresh()` | `TopologyRefreshService` | Returns true if fresh; otherwise records TTL expiry, starts an on-demand refresh, and returns whether it succeeded. | `sma_bgpls_topology_ttl_expired_total`, `sma_bgpls_topology_refresh_on_demand_total`, freshness gauges. |
| `status()` | `TopologyRefreshService` | Builds `TopologyRefreshStatus` for operational state. | Also updates freshness gauges. |
| `close()` | `TopologyRefreshService` | Stops refresh executor. | Logs final last-successful-refresh timestamp. |
| `kbpsToBytesPerSecond(kbps)` | `BandwidthTranslator` | Converts policy bandwidth to path-computation bandwidth. |
| `kbpsToPcepBandwidthBase64Float32(kbps)` | `BandwidthTranslator` | Converts policy bandwidth to PCEP float32 Base64. |
| `bytesPerSecondAsXmlFloat(bytesPerSecond)` | `BandwidthTranslator` | Formats bandwidth for XML fields that need float representation. |

## Delegated LSP Methods

| Method | Class | Process detail | State and metrics |
|---|---|---|---|
| `initialize()` | `DelegatedLspService` | Reads PCEP topology, validates both configured delegated LSPs, and replaces registry. | Increments PCEP topology/read and delegated LSP metrics. |
| `refreshDirection(directionKey)` | `DelegatedLspService` | Reads full PCEP topology, revalidates both configured LSPs, replaces registry, and returns one direction. | Keeps LSP registry aligned to ODL operational state. |
| `requireDelegatedLsp(directionKey)` | `DelegatedLspService` | Returns current valid LSP by direction or throws. | Used before update and intent-key construction. |
| `activeStateMatches(direction, path, constraints)` | `DelegatedLspService` | Refreshes PCEP, encodes bandwidth, and compares requested ERO/bandwidth against active state. | Determines whether update-lsp can be skipped. |
| `updateDelegatedLsp(direction, path, constraints)` | `DelegatedLspService` | Builds `UpdateLspRequest`, serializes XML, POSTs update-lsp, classifies response, handles failures, then polls PCEP until requested ERO appears. | Update request/success/failure, update duration, path verification, convergence duration. |
| `applyPolicyToDirection(candidate, direction, workflowContext)` | `DirectionalLspApplicationService` | Locks per direction, computes path, refreshes current LSP, hashes desired state, skips if converged, otherwise calls `updateDelegatedLsp` and records result. | LSP update sent/skipped, ERO confirmed, bandwidth confirmed/unconfirmed, failure counters. |
| `processDirection(...)` | `TunnelOperationCoordinator` | Legacy idempotent flow that computes path, checks recent intent journal, locks direction, updates LSP, and installs optional OpenFlow suppression. | Tunnel operation success/failure/skip metrics. |

## OpenFlow Methods

| Method | Class | Process detail | State and metrics |
|---|---|---|---|
| `initialize()` | `OpenflowBootstrapService` | Discovers switches, writes four deterministic ARP/IPv4 flows per switch, verifies config and operational state, then marks bootstrap ready. | `sma_openflow_bootstrap_ready`, install/discovery metrics. |
| `definitionsFor(switchRecord, cookieBase)` | `OpenflowBootstrapService` | Builds ARP host-to-core, ARP core-to-host, IPv4 host-to-core with controller copy, and IPv4 core-to-host flows. | Flow IDs are deterministic and used by verification. |
| `installFlow(switchRecord, flowDefinition)` | `OpenflowFlowProvisioningService` | Serializes and PUTs one flow, retrying HTTP 409/503 and transport failures. | Install attempt/success/failure counters. |
| `verify(switchRecord, flows)` | `OpenflowBootstrapVerifier` | Checks each configured flow exists and polls operational table until all flow IDs are present. | Fails readiness attempt if config/operational state cannot be confirmed. |
| `discoverSwitches()` | `OpenflowInventoryService` | Reads OpenFlow inventory and delegates parsing/resolution. | Inventory discovery success/failure metrics. |
| `deserialize(xml, echoIp, foxtrotIp)` | `OpenflowInventoryXmlDeserializer` | Resolves configured switches by management IP and extracts connectors. | Produces switch records for registry. |
| `replace(switches)` | `OpenflowSwitchRegistry` | Atomically replaces switch maps by logical name and node ID. | Used by packet ingress resolution and operational publisher. |
| `findConnector(...)` / `findConnectorById(...)` | `OpenflowSwitchRegistry` | Finds resolved connector records by logical switch/name or node/connector ID. | Used by PacketIn extractor. |
| `buildSuppressionIntent(...)` | `OpenFlowSuppressionService` | Creates a legacy suppression-flow intent only when ingress metadata and configuration support it. | Produces an optional intent for old tunnel operation flows. |
| `installSuppressionFlow(intent)` | `OpenFlowSuppressionService` | Serializes and writes a suppression OpenFlow entry using RESTCONF config data. | Returns `OpenFlowProgrammingResult` for legacy operation records. |

## Registry Methods

| Registry method | Class | Behavior |
|---|---|---|
| `replaceAll(nodes)` | `BgpLsNodeRegistry` | Rebuilds router ID and graph-node indexes from parsed BGP-LS nodes. |
| `resolveGraphNodeIdByRouterId(routerId)` | `BgpLsNodeRegistry` | Returns graph-node ID or increments BGP-LS resolution failure before throwing. |
| `find(context)` | `ClassificationRegistrar` | Expires old entries, checks exact cache, then service cache. |
| `put(context, result)` | `ClassificationRegistrar` | Stores exact and service-level classification entries. |
| `expireOldEntries()` | `ClassificationRegistrar` | Removes expired exact/service entries. |
| `findValid(key)` | `CalculatedPathRegistry` | Expires old entries and returns only non-expired paths. |
| `put(key, path)` | `CalculatedPathRegistry` | Stores path and updates path registry gauge. |
| `replaceAll(records)` | `DelegatedLspRegistry` | Replaces direction and name indexes from PCEP snapshots. |
| `updateAfterSuccessfulUpdate(...)` | `DelegatedLspRegistry` | Updates active ERO and bandwidth after confirmed update-lsp. |
| `recordEvidence(evidence)` | `DirectionalClassificationEvidenceRegistry` | Adds evidence to the correct pair/service bucket and side. |
| `expireOldEvidence(now)` | `DirectionalClassificationEvidenceRegistry` | Removes expired buckets/evidence. |
| `findActive(pairKey, now)` | `ActivePairPolicyRegistry` | Returns active policy only if not expired. |
| `refresh(pairKey, now, ttl)` | `ActivePairPolicyRegistry` | Extends the idle TTL for same-policy activity. |
| `installOrReplace(candidate, children, now, ttl)` | `ActivePairPolicyRegistry` | Installs new active state and increments generation. |
| `findRecentUsableIntent(key, now)` | `TunnelOperationRegistry` | Finds reusable recent tunnel operation intent. |
| `markPending/markAccepted/markConfirmed/markFailed(...)` | `TunnelOperationRegistry` | Maintains the legacy tunnel-operation lifecycle journal. |

## Operational State Publication Methods

| Method | Class | Process detail |
|---|---|---|
| `publish()` | `ControllerOperationalStatePublisher` | Creates a write-only transaction, builds a full `ControllerState`, puts it into OPERATIONAL datastore, and commits. |
| `buildState()` | `ControllerOperationalStatePublisher` | Snapshots every registry, transforms domain records into generated YANG binding builders, updates registry gauges, and builds `ControllerState`. |
| `buildPolicyState()` | `ControllerOperationalStatePublisher` | Projects consensus, preemption, TTL, and LSP application config into operational state. |
| `buildExactClassifications(...)` | `ControllerOperationalStatePublisher` | Converts exact classification cache entries into YANG list entries. |
| `buildServiceClassifications(...)` | `ControllerOperationalStatePublisher` | Converts service classification cache entries into YANG list entries. |
| `buildCalculatedPaths(...)` | `ControllerOperationalStatePublisher` | Converts calculated path cache entries into YANG list entries with hops and EROs. |
| `buildDelegatedLsps(...)` | `ControllerOperationalStatePublisher` | Converts delegated LSP registry entries into YANG list entries. |
| `buildEvidence(...)` | `ControllerOperationalStatePublisher` | Converts pair consensus buckets into left/right directional evidence entries. |
| `buildActivePolicies(...)` | `ControllerOperationalStatePublisher` | Converts active pair policies into YANG list entries. |
| `buildApplications(...)` | `ControllerOperationalStatePublisher` | Flattens child directional LSP applications into a YANG list. |
| `buildTunnelPairs()` / `buildTunnelDirections()` | `ControllerOperationalStatePublisher` | Publishes static tunnel pair and direction definitions. |

## Metrics Methods

| Method | Class | Process detail |
|---|---|---|
| `increment(name)` / `incrementCounter(name)` | `SdnMplsMlMetrics` | Compatibility helpers for unlabeled counters. |
| `incrementCounter(name, labels)` | `SdnMplsMlMetrics` | Normalizes labels, creates counter state if missing, and increments it. |
| `set(name, value)` / `setGauge(...)` | `SdnMplsMlMetrics` | Updates unlabeled or labeled gauge values. |
| `observeHistogram(name, labels, seconds)` | `SdnMplsMlMetrics` | Validates metric type, creates histogram state, and records bucket/count/sum data. |
| `snapshot()` | `SdnMplsMlMetrics` | Returns legacy unlabeled counter/gauge snapshot. |
| `renderPrometheusText()` | `SdnMplsMlMetrics` | Renders HELP/TYPE metadata and all counter, gauge, and histogram samples. |
| `doGet(request, response)` | `PrometheusMetricsServlet` | Returns Prometheus text from `SdnMplsMlMetrics`. |

## Observability And Utility Methods

| Method | Class | Process detail |
|---|---|---|
| `getLogger(type)` | `StructuredLogger` | Wraps an SLF4J logger with structured event helpers. |
| `trace/debug/info/warn/error(...)` | `StructuredLogger` | Emits structured log events with event, operation, message, context, metadata, and optional exception. |
| `fields(entries...)` | `StructuredLogger` | Builds a safe ordered metadata map for logging calls. |
| `open(values)` | `LogContext` | Adds scoped thread-local fields to downstream logs. |
| `close()` | `LogContext` | Restores previous scoped log context. |
| `current()` | `WorkflowContext` | Reads `workflow_id` and `packet_sequence` from MDC, falling back to a generated UUID and sequence `0` when no scope is present. |
| `retryUntilPresent(supplier, failureMessage)` | `RetryPolicy` | Polls until an optional value appears or timeout expires. |
| `retryUntilTrue(supplier, failureMessage)` | `RetryPolicy` | Polls until a condition becomes true or timeout expires. |
| `jitter(delayMs, jitterPercent)` | `RetryPolicy` | Applies bounded jitter to retry delay. |
| `parse(xml)` | `XmlSupport` | Builds a namespace-aware DOM document with safe parser settings. |
| `nodes/string/integer/longValue/bool(...)` | `XmlSupport` | XPath helpers for DOM extraction. |
| `elementNamed(node, localName)` | `XmlSupport` | Checks DOM element local name. |
| `escape(value)` | `XmlSupport` | Escapes XML text. |
