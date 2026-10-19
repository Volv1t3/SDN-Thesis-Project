/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.operational;

import com.sma.sdn.config.AppConfig;
import com.sma.sdn.metrics.SdnMplsMlMetrics;
import com.sma.sdn.model.ActivePairPolicyState;
import com.sma.sdn.model.BgpLsTopologyNode;
import com.sma.sdn.model.CalculatedPath;
import com.sma.sdn.model.CalculatedPathKey;
import com.sma.sdn.model.ClassificationCacheKey;
import com.sma.sdn.model.ClassificationResult;
import com.sma.sdn.model.DelegatedLspRecord;
import com.sma.sdn.model.DirectionalPolicyEvidence;
import com.sma.sdn.model.EroSubobject;
import com.sma.sdn.model.PairConsensusBucket;
import com.sma.sdn.model.ServiceClassCacheKey;
import com.sma.sdn.model.TunnelDirection;
import com.sma.sdn.model.TunnelPairDefinition;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.openflow.OpenflowConnectorRecord;
import com.sma.sdn.openflow.OpenflowSwitchRecord;
import com.sma.sdn.openflow.OpenflowSwitchRegistry;
import com.sma.sdn.registry.ActivePairPolicyRegistry;
import com.sma.sdn.registry.BgpLsNodeRegistry;
import com.sma.sdn.registry.CalculatedPathRegistry;
import com.sma.sdn.registry.ClassificationRegistrar;
import com.sma.sdn.registry.DelegatedLspRegistry;
import com.sma.sdn.registry.DirectionalClassificationEvidenceRegistry;
import com.sma.sdn.registry.TunnelPairRegistry;
import com.sma.sdn.topology.TopologyRefreshService;
import com.sma.sdn.topology.TopologyRefreshStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.ControllerState;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.ControllerStateBuilder;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.ActivePairPolicyEntry;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.ActivePairPolicyEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.ActivePairPolicyEntryKey;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.BgpLsNode;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.BgpLsNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.BgpLsNodeKey;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.CacheStateBuilder;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.CalculatedPathCacheEntry;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.CalculatedPathCacheEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.CalculatedPathCacheEntryKey;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.ClassificationExactCacheEntry;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.ClassificationExactCacheEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.ClassificationExactCacheEntryKey;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.ClassificationServiceCacheEntry;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.ClassificationServiceCacheEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.ClassificationServiceCacheEntryKey;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.ControlPlaneBuilder;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.DelegatedLspEntry;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.DelegatedLspEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.DelegatedLspEntryKey;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.DirectionalLspApplicationEntry;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.DirectionalLspApplicationEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.DirectionalLspApplicationEntryKey;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.DirectionalPolicyEvidenceEntry;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.DirectionalPolicyEvidenceEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.DirectionalPolicyEvidenceEntryKey;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.OpenflowSwitch;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.OpenflowSwitchBuilder;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.OpenflowSwitchKey;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.PolicyStateBuilder;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.TunnelDirectionEntry;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.TunnelDirectionEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.TunnelDirectionEntryKey;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.TunnelPairEntry;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.TunnelPairEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.TunnelPairEntryKey;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.openflow._switch.Connector;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.openflow._switch.ConnectorBuilder;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.openflow._switch.ConnectorKey;
import org.opendaylight.yangtools.binding.DataObjectIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;

/**
 * <p>Builds a controller-state representation from configuration, runtime registries,
 * topology refresh status, and lifecycle suppliers, then submits it to the MD-SAL
 * operational datastore. Registry snapshots are collected separately and therefore
 * do not constitute a single cross-registry atomic snapshot.</p>
 *
 * <p>The provider and services invoke this publisher as state changes. Publishing
 * is best effort: synchronous runtime failures are logged, while commit completion
 * is not awaited or observed by this class.</p>
 */
public final class ControllerOperationalStatePublisher {
    /** <p>Logs synchronous publication failures.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(ControllerOperationalStatePublisher.class);
    /** <p>Binding path for the root operational controller-state object.</p> */
    private static final DataObjectIdentifier<ControllerState> STATE_PATH =
            DataObjectIdentifier.builder(ControllerState.class).build();

    /** <p>Creates write transactions for the operational datastore.</p> */
    private final DataBroker dataBroker;
    /** <p>Provides topology and policy settings for the published state.</p> */
    private final AppConfig config;
    /** <p>Supplies the BGP-LS node snapshot.</p> */
    private final BgpLsNodeRegistry bgpLsNodeRegistry;
    /** <p>Supplies exact and service classification cache snapshots.</p> */
    private final ClassificationRegistrar classificationRegistrar;
    /** <p>Supplies cached path computations.</p> */
    private final CalculatedPathRegistry calculatedPathRegistry;
    /** <p>Supplies delegated LSP records by direction.</p> */
    private final DelegatedLspRegistry delegatedLspRegistry;
    /** <p>Supplies switch and connector inventory records.</p> */
    private final OpenflowSwitchRegistry openflowSwitchRegistry;
    /** <p>Supplies directional policy evidence buckets.</p> */
    private final DirectionalClassificationEvidenceRegistry evidenceRegistry;
    /** <p>Supplies active pair policies and their LSP applications.</p> */
    private final ActivePairPolicyRegistry activePolicyRegistry;
    /** <p>Supplies the configured tunnel pair and directions.</p> */
    private final TunnelPairRegistry tunnelPairRegistry;
    /** <p>Supplies topology freshness and refresh status.</p> */
    private final TopologyRefreshService topologyRefreshService;
    /** <p>Receives publication counters and registry-size gauges.</p> */
    private final SdnMplsMlMetrics metrics;
    /** <p>Reads the provider's current readiness state.</p> */
    private final BooleanSupplier controlPlaneReady;
    /** <p>Reads the provider's current closed state.</p> */
    private final BooleanSupplier closed;
    /** <p>Reads the provider's processed packet count.</p> */
    private final LongSupplier processedPacketCount;

    /**
     * <p>Creates a publisher using the registries, lifecycle suppliers, and datastore
     * owned by the controller application.</p>
     * <p>Construction proceeds as follows:</p>
     * <ol>
     *   <li>Requires each dependency and supplier to be non-null.</li>
     *   <li>Stores them for later snapshot collection and publication.</li>
     * </ol>
     * @param dataBroker operational datastore transaction provider
     * @param config controller configuration
     * @param bgpLsNodeRegistry BGP-LS node registry
     * @param classificationRegistrar classification caches
     * @param calculatedPathRegistry calculated path cache
     * @param delegatedLspRegistry delegated LSP state
     * @param openflowSwitchRegistry switch and connector inventory
     * @param evidenceRegistry directional evidence buckets
     * @param activePolicyRegistry active pair policies
     * @param tunnelPairRegistry tunnel pair and directions
     * @param topologyRefreshService topology refresh status provider
     * @param metrics publication counters and registry gauges
     * @param controlPlaneReady supplier of readiness
     * @param closed supplier of shutdown state
     * @param processedPacketCount supplier of processed packet count
     * @throws NullPointerException if any dependency is null
     */
    public ControllerOperationalStatePublisher(
            final DataBroker dataBroker,
            final AppConfig config,
            final BgpLsNodeRegistry bgpLsNodeRegistry,
            final ClassificationRegistrar classificationRegistrar,
            final CalculatedPathRegistry calculatedPathRegistry,
            final DelegatedLspRegistry delegatedLspRegistry,
            final OpenflowSwitchRegistry openflowSwitchRegistry,
            final DirectionalClassificationEvidenceRegistry evidenceRegistry,
            final ActivePairPolicyRegistry activePolicyRegistry,
            final TunnelPairRegistry tunnelPairRegistry,
            final TopologyRefreshService topologyRefreshService,
            final SdnMplsMlMetrics metrics,
            final BooleanSupplier controlPlaneReady,
            final BooleanSupplier closed,
            final LongSupplier processedPacketCount) {
        this.dataBroker = Objects.requireNonNull(dataBroker, "dataBroker");
        this.config = Objects.requireNonNull(config, "config");
        this.bgpLsNodeRegistry = Objects.requireNonNull(bgpLsNodeRegistry, "bgpLsNodeRegistry");
        this.classificationRegistrar = Objects.requireNonNull(classificationRegistrar, "classificationRegistrar");
        this.calculatedPathRegistry = Objects.requireNonNull(calculatedPathRegistry, "calculatedPathRegistry");
        this.delegatedLspRegistry = Objects.requireNonNull(delegatedLspRegistry, "delegatedLspRegistry");
        this.openflowSwitchRegistry = Objects.requireNonNull(openflowSwitchRegistry, "openflowSwitchRegistry");
        this.evidenceRegistry = Objects.requireNonNull(evidenceRegistry, "evidenceRegistry");
        this.activePolicyRegistry = Objects.requireNonNull(activePolicyRegistry, "activePolicyRegistry");
        this.tunnelPairRegistry = Objects.requireNonNull(tunnelPairRegistry, "tunnelPairRegistry");
        this.topologyRefreshService = Objects.requireNonNull(topologyRefreshService, "topologyRefreshService");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.controlPlaneReady = Objects.requireNonNull(controlPlaneReady, "controlPlaneReady");
        this.closed = Objects.requireNonNull(closed, "closed");
        this.processedPacketCount = Objects.requireNonNull(processedPacketCount, "processedPacketCount");
    }

    /**
     * <p>Submits a replacement controller-state object to the operational datastore.
     * The commit result is not awaited; the success counter records submission,
     * not confirmed asynchronous commit completion.</p>
     * <p>Publication proceeds as follows:</p>
     * <ol>
     *   <li>Creates a write transaction and builds a fresh state object.</li>
     *   <li>Puts the object at the controller-state path, submits the commit,
     *       and increments the success counter.</li>
     *   <li>On a synchronous runtime exception, increments the failure counter and
     *       logs the failure so normal control processing can continue.</li>
     * </ol>
     */
    public void publish() {
        try {
            final var transaction = dataBroker.newWriteOnlyTransaction();
            transaction.put(LogicalDatastoreType.OPERATIONAL, STATE_PATH, buildState());
            transaction.commit();
            metrics.increment("sma_controller_operational_state_publish_success_total");
        } catch (RuntimeException e) {
            metrics.incrementCounter("sma_controller_operational_state_publish_failure_total",
                    Map.of("reason", "runtime_exception"));
            LOG.warn(
                    "controller_operational_state_publish_failed",
                    "publish",
                    "No se pudo publicar el estado operacional CSA; el flujo de control continuara.",
                    Map.of(),
                    e);
        }
    }

    /**
     * <p>Collects current registry snapshots and converts them into a YANG binding
     * object, updating registry gauges along the way.</p>
     * <p>Construction proceeds as follows:</p>
     * <ol>
     *   <li>Reads topology status and separate snapshots of caches and policies.</li>
     *   <li>Builds BGP-LS node and OpenFlow switch and connector maps.</li>
     *   <li>Updates gauges and assembles lifecycle, configuration, cache, policy,
     *       LSP and tunnel entries into the root state.</li>
     * </ol>
     * <p>Snapshots can reflect different moments during concurrent updates.</p>
     * @return assembled operational controller-state binding
     */
    private ControllerState buildState() {
        final TopologyRefreshStatus topology = topologyRefreshService.status();
        final Map<ClassificationCacheKey, ClassificationResult> exactClassifications =
                classificationRegistrar.exactSnapshot();
        final Map<ServiceClassCacheKey, ClassificationResult> serviceClassifications =
                classificationRegistrar.serviceSnapshot();
        final Map<CalculatedPathKey, CalculatedPath> calculatedPaths = calculatedPathRegistry.snapshot();
        final Map<String, DelegatedLspRecord> delegatedLsps = delegatedLspRegistry.snapshotByDirection();
        final Map<String, PairConsensusBucket> evidenceBuckets = evidenceRegistry.snapshot();
        final Map<String, ActivePairPolicyState> activePolicies = activePolicyRegistry.snapshot();
        final Map<BgpLsNodeKey, BgpLsNode> nodes = new LinkedHashMap<>();
        final Map<OpenflowSwitchKey, OpenflowSwitch> openflowSwitches = new LinkedHashMap<>();
        for (BgpLsTopologyNode node : bgpLsNodeRegistry.snapshot().values()) {
            final BgpLsNodeKey key = new BgpLsNodeKey(node.routerId());
            nodes.put(key, new BgpLsNodeBuilder()
                    .setRouterId(node.routerId())
                    .setTopologyId(node.topologyId())
                    .setNodeId(node.nodeId())
                    .setTeRouterIdIpv4(node.teRouterIdIpv4())
                    .setGraphNodeId(Uint64.valueOf(node.graphNodeId()))
                    .build());
        }
        for (OpenflowSwitchRecord switchRecord : openflowSwitchRegistry.snapshotByLogicalName().values()) {
            final Map<ConnectorKey, Connector> connectors = new LinkedHashMap<>();
            for (OpenflowConnectorRecord connectorRecord : switchRecord.connectorsById().values()) {
                final ConnectorKey connectorKey = new ConnectorKey(connectorRecord.connectorId());
                connectors.put(connectorKey, new ConnectorBuilder()
                        .setConnectorId(connectorRecord.connectorId())
                        .setName(connectorRecord.name())
                        .setPortNumber(Uint32.valueOf(connectorRecord.portNumber()))
                        .setHardwareAddress(connectorRecord.hardwareAddress())
                        .setLive(connectorRecord.live())
                        .setLinkDown(connectorRecord.linkDown())
                        .build());
            }
            final OpenflowSwitchKey switchKey = new OpenflowSwitchKey(switchRecord.logicalName());
            openflowSwitches.put(switchKey, new OpenflowSwitchBuilder()
                    .setLogicalName(switchRecord.logicalName())
                    .setManagementIp(switchRecord.managementIp())
                    .setNodeId(switchRecord.nodeId())
                    .setEncodedNodeId(switchRecord.encodedNodeId())
                    .setConnector(connectors)
                    .build());
        }
        updateRegistryGauges(exactClassifications, serviceClassifications, calculatedPaths, delegatedLsps,
                evidenceBuckets, activePolicies, nodes, openflowSwitches);
        return new ControllerStateBuilder()
                .setGeneratedAt(Instant.now().toString())
                .setProcessedPacketCount(Uint64.valueOf(processedPacketCount.getAsLong()))
                .setControlPlane(new ControlPlaneBuilder()
                        .setReady(controlPlaneReady.getAsBoolean())
                        .setClosed(closed.getAsBoolean())
                        .setTopologyId(config.bgplsTopologyId())
                        .setTopologyTtlMillis(Uint64.valueOf(config.topologyCacheTtl().toMillis()))
                        .setTopologyFresh(topology.fresh())
                        .setTopologyRefreshInProgress(topology.refreshInProgress())
                        .setTopologyLastSuccessfulRefresh(topology.lastSuccessfulRefresh().toString())
                        .setTopologyLastRefreshAttempt(topology.lastRefreshAttempt().toString())
                        .setTopologyFreshUntil(topology.freshUntil().toString())
                        .setTopologyLastFailure(topology.lastFailure())
                        .setTopologyRefreshSuccessCount(Uint64.valueOf(topology.successfulRefreshCount()))
                        .setTopologyRefreshFailureCount(Uint64.valueOf(topology.failedRefreshCount()))
                        .build())
                .setCacheState(new CacheStateBuilder()
                        .setClassificationEntryCount(
                                Uint32.valueOf(exactClassifications.size() + serviceClassifications.size()))
                        .setCalculatedPathEntryCount(Uint32.valueOf(calculatedPaths.size()))
                        .setPolicyEvidenceBucketCount(Uint32.valueOf(evidenceBuckets.size()))
                        .setActivePairPolicyCount(Uint32.valueOf(activePolicies.size()))
                        .setDelegatedLspCount(Uint32.valueOf(delegatedLsps.size()))
                        .setOpenflowSwitchCount(Uint32.valueOf(openflowSwitchRegistry.size()))
                        .setClassificationExactEntryCount(Uint32.valueOf(exactClassifications.size()))
                        .setClassificationServiceEntryCount(Uint32.valueOf(serviceClassifications.size()))
                        .setDirectionalEvidenceEntryCount(Uint32.valueOf(evidenceEntryCount(evidenceBuckets)))
                        .setDirectionalLspApplicationCount(Uint32.valueOf(applicationEntryCount(activePolicies)))
                        .build())
                .setPolicyState(buildPolicyState().build())
                .setBgpLsNode(nodes)
                .setOpenflowSwitch(openflowSwitches)
                .setClassificationExactCacheEntry(buildExactClassifications(exactClassifications))
                .setClassificationServiceCacheEntry(buildServiceClassifications(serviceClassifications))
                .setCalculatedPathCacheEntry(buildCalculatedPaths(calculatedPaths))
                .setDelegatedLspEntry(buildDelegatedLsps(delegatedLsps))
                .setDirectionalPolicyEvidenceEntry(buildEvidence(evidenceBuckets))
                .setActivePairPolicyEntry(buildActivePolicies(activePolicies))
                .setDirectionalLspApplicationEntry(buildApplications(activePolicies))
                .setTunnelPairEntry(buildTunnelPairs())
                .setTunnelDirectionEntry(buildTunnelDirections())
                .build();
    }

    /**
     * <p>Updates registry gauges from the snapshots used to build the state.</p>
     * <p>Updates proceed as follows:</p>
     * <ol>
     *   <li>Sets cache, LSP, evidence and policy counts, including nested entry totals.</li>
     *   <li>Sets fixed pair and direction counts and counts inventory and BGP-LS entries.</li>
     * </ol>
     * @param exactClassifications exact classification snapshot
     * @param serviceClassifications service classification snapshot
     * @param calculatedPaths calculated path snapshot
     * @param delegatedLsps LSP snapshot keyed by direction
     * @param evidenceBuckets consensus evidence snapshot
     * @param activePolicies active policy snapshot
     * @param nodes converted BGP-LS nodes
     * @param openflowSwitches converted OpenFlow switches
     */
    private void updateRegistryGauges(
            final Map<ClassificationCacheKey, ClassificationResult> exactClassifications,
            final Map<ServiceClassCacheKey, ClassificationResult> serviceClassifications,
            final Map<CalculatedPathKey, CalculatedPath> calculatedPaths,
            final Map<String, DelegatedLspRecord> delegatedLsps,
            final Map<String, PairConsensusBucket> evidenceBuckets,
            final Map<String, ActivePairPolicyState> activePolicies,
            final Map<BgpLsNodeKey, BgpLsNode> nodes,
            final Map<OpenflowSwitchKey, OpenflowSwitch> openflowSwitches) {
        metrics.setGauge("sma_registry_classification_exact_entries", exactClassifications.size());
        metrics.setGauge("sma_registry_classification_service_entries", serviceClassifications.size());
        metrics.setGauge("sma_registry_calculated_path_entries", calculatedPaths.size());
        metrics.setGauge("sma_registry_delegated_lsp_entries", delegatedLsps.size());
        metrics.setGauge("sma_registry_directional_evidence_buckets", evidenceBuckets.size());
        metrics.setGauge("sma_registry_directional_evidence_entries", evidenceEntryCount(evidenceBuckets));
        metrics.setGauge("sma_registry_active_pair_policy_entries", activePolicies.size());
        metrics.setGauge("sma_registry_directional_lsp_application_entries", applicationEntryCount(activePolicies));
        metrics.setGauge("sma_registry_tunnel_pair_entries", 1L);
        metrics.setGauge("sma_registry_tunnel_direction_entries", 2L);
        metrics.setGauge("sma_registry_openflow_switch_entries", openflowSwitches.size());
        metrics.setGauge("sma_registry_openflow_connector_entries", openflowConnectorCount(openflowSwitches));
        metrics.setGauge("sma_registry_bgpls_node_entries", nodes.size());
    }

    /**
     * <p>Counts connectors across converted OpenFlow switch entries.</p>
     * <p>Counting proceeds as follows:</p>
     * <ol>
     *   <li>Obtains each switch's connector map and skips null maps.</li>
     *   <li>Sums the remaining map sizes.</li>
     * </ol>
     * @param openflowSwitches converted switch entries
     * @return total number of connectors
     */
    private static int openflowConnectorCount(final Map<OpenflowSwitchKey, OpenflowSwitch> openflowSwitches) {
        return openflowSwitches.values().stream()
                .map(OpenflowSwitch::getConnector)
                .filter(Objects::nonNull)
                .mapToInt(Map::size)
                .sum();
    }

    /**
     * <p>Copies configured consensus, policy, LSP application, and cache settings
     * into the operational policy-state builder.</p>
     * <p>Construction proceeds as follows:</p>
     * <ol>
     *   <li>Reads the policy and cache settings from configuration.</li>
     *   <li>Converts numeric durations and values to YANG unsigned types.</li>
     * </ol>
     * @return populated policy-state builder
     */
    private PolicyStateBuilder buildPolicyState() {
        // The YANG view retains these fields for compatibility, but pair-policy modes are fixed invariants.
        return new PolicyStateBuilder()
                .setConsensusEnabled(true)
                .setRequireBothDirections(true)
                .setSingleSideProvisionalEnabled(false)
                .setEqualPriorityAction("KEEP_CURRENT_OR_DEFER")
                .setEvidenceTtlMillis(Uint64.valueOf(config.pairConsensusEvidenceTtl().toMillis()))
                .setActivePolicyIdleTtlMillis(Uint64.valueOf(config.activePairPolicyIdleTtl().toMillis()))
                .setActivePolicySweeperEnabled(config.activePairPolicySweeperEnabled())
                .setActivePolicySweeperIntervalMillis(
                        Uint64.valueOf(config.activePairPolicySweeperInterval().toMillis()))
                .setPriorityPreemptionEnabled(true)
                .setPolicyHashVersion(Uint32.valueOf(config.pairPolicyHashVersion()))
                .setLspApplicationScope("BIDIRECTIONAL_PAIR")
                .setRequireAllLspDirections(true)
                .setClassificationCacheTtlMillis(Uint64.valueOf(config.classificationCacheTtl().toMillis()))
                .setCalculatedPathCacheTtlMillis(Uint64.valueOf(config.pathCacheTtl().toMillis()));
    }

    /**
     * <p>Converts exact classification cache entries into keyed YANG entries.</p>
     * <p>Conversion proceeds as follows:</p>
     * <ol>
     *   <li>Builds an entry ID from ingress and packet-feature fields for each key.</li>
     *   <li>Copies those fields and the classification result into a binding entry.</li>
     * </ol>
     * @param snapshot exact classification cache snapshot
     * @return keyed operational classification entries
     */
    private static Map<ClassificationExactCacheEntryKey, ClassificationExactCacheEntry> buildExactClassifications(
            final Map<ClassificationCacheKey, ClassificationResult> snapshot) {
        final Map<ClassificationExactCacheEntryKey, ClassificationExactCacheEntry> result = new LinkedHashMap<>();
        snapshot.forEach((key, value) -> {
            final String entryId = String.join("|", "exact", key.ingressSwitchName(),
                    key.ingressConnectorName(), Integer.toString(key.ethType()), Integer.toString(key.ipProto()),
                    Integer.toString(key.srcPort()), Integer.toString(key.dstPort()));
            final ClassificationExactCacheEntryKey bindingKey = new ClassificationExactCacheEntryKey(entryId);
            result.put(bindingKey, applyClassificationResult(new ClassificationExactCacheEntryBuilder()
                    .setEntryId(entryId)
                    .setIngressSwitchName(key.ingressSwitchName())
                    .setIngressConnectorName(key.ingressConnectorName())
                    .setEthType(Uint32.valueOf(key.ethType()))
                    .setIpProtocol(Uint32.valueOf(key.ipProto()))
                    .setSourcePort(Uint32.valueOf(key.srcPort()))
                    .setDestinationPort(Uint32.valueOf(key.dstPort())), value).build());
        });
        return result;
    }

    /**
     * <p>Converts service-class classification cache entries into YANG entries.</p>
     * <p>Conversion proceeds as follows:</p>
     * <ol>
     *   <li>Constructs an ID from the ingress, EtherType, protocol and canonical port.</li>
     *   <li>Copies key fields and the classification result into each binding entry.</li>
     * </ol>
     * @param snapshot service classification cache snapshot
     * @return keyed operational service-class entries
     */
    private static Map<ClassificationServiceCacheEntryKey, ClassificationServiceCacheEntry>
            buildServiceClassifications(final Map<ServiceClassCacheKey, ClassificationResult> snapshot) {
        final Map<ClassificationServiceCacheEntryKey, ClassificationServiceCacheEntry> result = new LinkedHashMap<>();
        snapshot.forEach((key, value) -> {
            final String entryId = String.join("|", "service", key.ingressSwitchName(),
                    Integer.toString(key.ethType()), Integer.toString(key.ipProto()),
                    Integer.toString(key.canonicalServicePort()));
            final ClassificationServiceCacheEntryKey bindingKey = new ClassificationServiceCacheEntryKey(entryId);
            result.put(bindingKey, applyClassificationResult(new ClassificationServiceCacheEntryBuilder()
                    .setEntryId(entryId)
                    .setIngressSwitchName(key.ingressSwitchName())
                    .setEthType(Uint32.valueOf(key.ethType()))
                    .setIpProtocol(Uint32.valueOf(key.ipProto()))
                    .setCanonicalServicePort(Uint32.valueOf(key.canonicalServicePort())), value).build());
        });
        return result;
    }

    /**
     * <p>Copies classification, policy, and cache metadata to an exact-entry builder.</p>
     * <p>Conversion proceeds as follows:</p>
     * <ol>
     *   <li>Copies model, class, confidence and probability values.</li>
     *   <li>Copies policy constraints, fallback, processing time and timestamps.</li>
     * </ol>
     * @param builder exact cache entry builder to populate
     * @param value classification result and policy
     * @return the populated builder
     */
    private static ClassificationExactCacheEntryBuilder applyClassificationResult(
            final ClassificationExactCacheEntryBuilder builder, final ClassificationResult value) {
        return builder.setRequestId(value.requestId())
                .setModelName(value.modelName())
                .setClassId(Uint32.valueOf(value.classId()))
                .setClassName(value.className())
                .setConfidence(Double.toString(value.confidence()))
                .setProbability(probabilities(value))
                .setProfileName(value.policy().profileName())
                .setDscp(Uint32.valueOf(value.policy().dscp()))
                .setMplsTc(Uint32.valueOf(value.policy().mplsTc()))
                .setRequestedBandwidthKbps(
                        Uint64.valueOf(value.policy().pathConstraints().requestedBandwidthKbps()))
                .setSetupPriority(Uint32.valueOf(value.policy().pathConstraints().setupPriority()))
                .setHoldPriority(Uint32.valueOf(value.policy().pathConstraints().holdPriority()))
                .setPolicyFallback(value.policy().policyFallback())
                .setPolicyFallbackReason(value.policy().policyFallbackReason())
                .setProcessingTimeMs(Double.toString(value.processingTimeMs()))
                .setCachedAt(value.cachedAt().toString())
                .setExpiresAt(value.expiresAt().toString());
    }

    /**
     * <p>Copies classification, policy, and cache metadata to a service-entry builder.</p>
     * <p>Conversion proceeds as follows:</p>
     * <ol>
     *   <li>Copies model, class, confidence and probability values.</li>
     *   <li>Copies policy constraints, fallback, processing time and timestamps.</li>
     * </ol>
     * @param builder service cache entry builder to populate
     * @param value classification result and policy
     * @return the populated builder
     */
    private static ClassificationServiceCacheEntryBuilder applyClassificationResult(
            final ClassificationServiceCacheEntryBuilder builder, final ClassificationResult value) {
        return builder.setRequestId(value.requestId())
                .setModelName(value.modelName())
                .setClassId(Uint32.valueOf(value.classId()))
                .setClassName(value.className())
                .setConfidence(Double.toString(value.confidence()))
                .setProbability(probabilities(value))
                .setProfileName(value.policy().profileName())
                .setDscp(Uint32.valueOf(value.policy().dscp()))
                .setMplsTc(Uint32.valueOf(value.policy().mplsTc()))
                .setRequestedBandwidthKbps(
                        Uint64.valueOf(value.policy().pathConstraints().requestedBandwidthKbps()))
                .setSetupPriority(Uint32.valueOf(value.policy().pathConstraints().setupPriority()))
                .setHoldPriority(Uint32.valueOf(value.policy().pathConstraints().holdPriority()))
                .setPolicyFallback(value.policy().policyFallback())
                .setPolicyFallbackReason(value.policy().policyFallbackReason())
                .setProcessingTimeMs(Double.toString(value.processingTimeMs()))
                .setCachedAt(value.cachedAt().toString())
                .setExpiresAt(value.expiresAt().toString());
    }

    /**
     * <p>Formats classification probabilities as strings for the operational model.</p>
     * <p>Conversion proceeds as follows:</p>
     * <ol>
     *   <li>Formats each class and probability as a key-value string.</li>
     *   <li>Collects the strings into an immutable set.</li>
     * </ol>
     * @param value result containing class probabilities
     * @return immutable set of formatted probability entries
     */
    private static Set<String> probabilities(final ClassificationResult value) {
        return Set.copyOf(value.probabilities().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList());
    }

    /**
     * <p>Converts cached paths and their keys into operational binding entries.</p>
     * <p>Conversion proceeds as follows:</p>
     * <ol>
     *   <li>Forms each entry ID from the path request key.</li>
     *   <li>Copies constraints, metric, hops, formatted ERO and cache timestamps.</li>
     * </ol>
     * @param snapshot calculated path cache snapshot
     * @return keyed operational path entries
     */
    private static Map<CalculatedPathCacheEntryKey, CalculatedPathCacheEntry> buildCalculatedPaths(
            final Map<CalculatedPathKey, CalculatedPath> snapshot) {
        final Map<CalculatedPathCacheEntryKey, CalculatedPathCacheEntry> result = new LinkedHashMap<>();
        snapshot.forEach((key, value) -> {
            final String entryId = String.join("|", Long.toString(key.sourceGraphNodeId()),
                    Long.toString(key.destinationGraphNodeId()), Long.toString(key.bandwidthBytesPerSecond()),
                    Integer.toString(key.classType()), key.algorithm());
            final CalculatedPathCacheEntryKey bindingKey = new CalculatedPathCacheEntryKey(entryId);
            result.put(bindingKey, new CalculatedPathCacheEntryBuilder()
                    .setEntryId(entryId)
                    .setGraphName(value.graphName())
                    .setSourceGraphNodeId(Uint64.valueOf(key.sourceGraphNodeId()))
                    .setDestinationGraphNodeId(Uint64.valueOf(key.destinationGraphNodeId()))
                    .setBandwidthBytesPerSecond(Uint64.valueOf(key.bandwidthBytesPerSecond()))
                    .setClassType(Uint32.valueOf(key.classType()))
                    .setAlgorithm(key.algorithm())
                    .setComputedTeMetric(Uint32.valueOf(value.computedTeMetric()))
                    .setPathHop(value.pathHops().stream()
                            .map(hop -> hop.localIpv4() + "|" + hop.remoteIpv4()).toList())
                    .setEroSubobject(value.eroSubobjects().stream()
                            .map(ControllerOperationalStatePublisher::ero).toList())
                    .setCalculatedAt(value.calculatedAt().toString())
                    .setExpiresAt(value.expiresAt().toString())
                    .build());
        });
        return result;
    }

    /**
     * <p>Converts delegated LSP records into entries keyed by direction.</p>
     * <p>Conversion proceeds as follows:</p>
     * <ol>
     *   <li>Copies PCC, tunnel, LSP identifiers and reported state.</li>
     *   <li>Formats the active ERO and nullable observation timestamps.</li>
     * </ol>
     * @param snapshot delegated LSP records keyed by direction
     * @return keyed operational delegated-LSP entries
     */
    private static Map<DelegatedLspEntryKey, DelegatedLspEntry> buildDelegatedLsps(
            final Map<String, DelegatedLspRecord> snapshot) {
        final Map<DelegatedLspEntryKey, DelegatedLspEntry> result = new LinkedHashMap<>();
        snapshot.forEach((directionKey, value) -> {
            final DelegatedLspEntryKey bindingKey = new DelegatedLspEntryKey(directionKey);
            result.put(bindingKey, new DelegatedLspEntryBuilder()
                    .setDirectionKey(directionKey)
                    .setPccNode(value.pccNode())
                    .setLspName(value.lspName())
                    .setTunnelInterfaceName(value.tunnelInterfaceName())
                    .setSourceRouterId(value.sourceRouterId())
                    .setDestinationRouterId(value.destinationRouterId())
                    .setPlspId(Uint64.valueOf(value.plspId()))
                    .setTunnelId(Uint64.valueOf(value.tunnelId()))
                    .setLspId(Uint64.valueOf(value.lspId()))
                    .setDelegated(value.delegated())
                    .setAdministrativeUp(value.administrativeUp())
                    .setOperationalState(value.operationalState())
                    .setValidForUpdate(value.isValidForUpdate())
                    .setActiveEroSubobject(value.activeEro().stream()
                            .map(ControllerOperationalStatePublisher::ero).toList())
                    .setReportedBandwidthBase64(value.reportedBandwidthBase64())
                    .setDiscoveredAt(string(value.discoveredAt()))
                    .setUpdatedAt(string(value.updatedAt()))
                    .build());
        });
        return result;
    }

    /**
     * <p>Flattens consensus buckets into directional evidence entries.</p>
     * <p>Conversion proceeds as follows:</p>
     * <ol>
     *   <li>Iterates over each bucket and checks its left and right evidence.</li>
     *   <li>Adds an entry for each present side using {@link #putEvidence}.</li>
     * </ol>
     * @param snapshot evidence buckets keyed by bucket ID
     * @return keyed directional evidence entries
     */
    private static Map<DirectionalPolicyEvidenceEntryKey, DirectionalPolicyEvidenceEntry> buildEvidence(
            final Map<String, PairConsensusBucket> snapshot) {
        final Map<DirectionalPolicyEvidenceEntryKey, DirectionalPolicyEvidenceEntry> result = new LinkedHashMap<>();
        snapshot.forEach((bucketKey, bucket) -> {
            bucket.leftEvidence().ifPresent(evidence -> putEvidence(result, bucketKey, "LEFT", bucket, evidence));
            bucket.rightEvidence().ifPresent(evidence -> putEvidence(result, bucketKey, "RIGHT", bucket, evidence));
        });
        return result;
    }

    /**
     * <p>Adds one directional observation and its bucket metadata to the result.</p>
     * <p>Conversion proceeds as follows:</p>
     * <ol>
     *   <li>Combines bucket key and side into the entry identifier.</li>
     *   <li>Copies packet, service, policy, hash and expiry fields into a binding.</li>
     *   <li>Stores the binding in the supplied result map.</li>
     * </ol>
     * @param result destination map modified by this method
     * @param bucketKey identifier of the containing bucket
     * @param side left or right side label
     * @param bucket consensus bucket supplying timestamps
     * @param evidence directional policy observation
     */
    private static void putEvidence(
            final Map<DirectionalPolicyEvidenceEntryKey, DirectionalPolicyEvidenceEntry> result,
            final String bucketKey,
            final String side,
            final PairConsensusBucket bucket,
            final DirectionalPolicyEvidence evidence) {
        final String entryId = bucketKey + "|" + side;
        final DirectionalPolicyEvidenceEntryKey bindingKey = new DirectionalPolicyEvidenceEntryKey(entryId);
        result.put(bindingKey, new DirectionalPolicyEvidenceEntryBuilder()
                .setEntryId(entryId)
                .setPairKey(evidence.pairKey())
                .setSide(side)
                .setDirectionKey(evidence.directionKey())
                .setIngressSwitchName(evidence.ingressSwitchName())
                .setIngressConnectorName(evidence.ingressConnectorName())
                .setEthType(Uint32.valueOf(evidence.packetFeatures().ethType()))
                .setIpProtocol(Uint32.valueOf(evidence.packetFeatures().ipProto()))
                .setSourcePort(Uint32.valueOf(evidence.packetFeatures().srcPort()))
                .setDestinationPort(Uint32.valueOf(evidence.packetFeatures().dstPort()))
                .setServiceEthType(Uint32.valueOf(evidence.serviceKey().ethType()))
                .setServiceIpProtocol(Uint32.valueOf(evidence.serviceKey().ipProtocol()))
                .setCanonicalServicePort(Uint32.valueOf(evidence.serviceKey().canonicalServicePort()))
                .setServiceKey(evidence.serviceKey().normalizedValue())
                .setClassName(evidence.className())
                .setProfileName(evidence.profileName())
                .setDscp(Uint32.valueOf(evidence.dscp()))
                .setMplsTc(Uint32.valueOf(evidence.mplsTc()))
                .setRequestedBandwidthKbps(Uint64.valueOf(evidence.requestedBandwidthKbps()))
                .setRequestedBandwidthBase64(evidence.requestedBandwidthBase64())
                .setSetupPriority(Uint32.valueOf(evidence.setupPriority()))
                .setHoldPriority(Uint32.valueOf(evidence.holdPriority()))
                .setPolicySchemaVersion(evidence.policySchemaVersion())
                .setPolicyHash(evidence.policyHash())
                .setBucketCreatedAt(bucket.createdAt().toString())
                .setBucketUpdatedAt(bucket.updatedAt().toString())
                .setBucketExpiresAt(bucket.expiresAt().toString())
                .setObservedAt(evidence.observedAt().toString())
                .setExpiresAt(evidence.expiresAt().toString())
                .build());
    }

    /**
     * <p>Converts active pair-policy state to operational entries.</p>
     * <p>Conversion proceeds as follows:</p>
     * <ol>
     *   <li>Uses each pair key as the YANG entry key.</li>
     *   <li>Copies service, policy, generation and lifetime fields.</li>
     * </ol>
     * @param snapshot active policies keyed by pair
     * @return keyed operational policy entries
     */
    private static Map<ActivePairPolicyEntryKey, ActivePairPolicyEntry> buildActivePolicies(
            final Map<String, ActivePairPolicyState> snapshot) {
        final Map<ActivePairPolicyEntryKey, ActivePairPolicyEntry> result = new LinkedHashMap<>();
        snapshot.forEach((pairKey, value) -> {
            final ActivePairPolicyEntryKey bindingKey = new ActivePairPolicyEntryKey(pairKey);
            result.put(bindingKey, new ActivePairPolicyEntryBuilder()
                    .setPairKey(pairKey)
                    .setServiceEthType(Uint32.valueOf(value.serviceKey().ethType()))
                    .setServiceIpProtocol(Uint32.valueOf(value.serviceKey().ipProtocol()))
                    .setCanonicalServicePort(Uint32.valueOf(value.serviceKey().canonicalServicePort()))
                    .setServiceKey(value.serviceKey().normalizedValue())
                    .setClassName(value.className())
                    .setProfileName(value.profileName())
                    .setDscp(Uint32.valueOf(value.dscp()))
                    .setMplsTc(Uint32.valueOf(value.mplsTc()))
                    .setRequestedBandwidthKbps(Uint64.valueOf(value.requestedBandwidthKbps()))
                    .setRequestedBandwidthBase64(value.requestedBandwidthBase64())
                    .setSetupPriority(Uint32.valueOf(value.setupPriority()))
                    .setHoldPriority(Uint32.valueOf(value.holdPriority()))
                    .setPolicySchemaVersion(value.policySchemaVersion())
                    .setPolicyHash(value.policyHash())
                    .setGeneration(Uint64.valueOf(value.generation()))
                    .setInstalledAt(value.installedAt().toString())
                    .setLastRefreshedAt(value.lastRefreshedAt().toString())
                    .setExpiresAt(value.expiresAt().toString())
                    .build());
        });
        return result;
    }

    /**
     * <p>Flattens each active policy's directional LSP application records.</p>
     * <p>Conversion proceeds as follows:</p>
     * <ol>
     *   <li>Forms an entry ID from the pair and direction for each application.</li>
     *   <li>Copies operation identifiers, LSP status, confirmation flags and times.</li>
     *   <li>Sets the HTTP status only when one is available.</li>
     * </ol>
     * @param snapshot active policies containing application records
     * @return keyed directional application entries
     */
    private static Map<DirectionalLspApplicationEntryKey, DirectionalLspApplicationEntry> buildApplications(
            final Map<String, ActivePairPolicyState> snapshot) {
        final Map<DirectionalLspApplicationEntryKey, DirectionalLspApplicationEntry> result = new LinkedHashMap<>();
        snapshot.values().forEach(policy -> policy.lspApplications().values().forEach(application -> {
            final String entryId = policy.pairKey() + "|" + application.directionKey();
            final DirectionalLspApplicationEntryKey bindingKey = new DirectionalLspApplicationEntryKey(entryId);
            final DirectionalLspApplicationEntryBuilder builder = new DirectionalLspApplicationEntryBuilder()
                    .setEntryId(entryId)
                    .setOperationId(application.operationId().toString())
                    .setWorkflowId(application.workflowId())
                    .setPacketSequence(Uint64.valueOf(application.packetSequence()))
                    .setPairKey(application.pairKey())
                    .setDirectionKey(application.directionKey())
                    .setPolicyHash(application.policyHash())
                    .setDesiredLspStateHash(application.desiredLspStateHash())
                    .setLspName(application.lspName())
                    .setPccNode(application.pccNode())
                    .setPlspId(Uint64.valueOf(application.plspId()))
                    .setStatus(application.status())
                    .setUpdateLspSent(application.updateLspSent())
                    .setPcepEroConfirmed(application.pcepEroConfirmed())
                    .setPcepBandwidthConfirmed(application.pcepBandwidthConfirmed())
                    .setStartedAt(string(application.startedAt()))
                    .setCompletedAt(string(application.completedAt()));
            if (application.updateLspHttpStatus() != null) {
                builder.setUpdateLspHttpStatus(Uint32.valueOf(application.updateLspHttpStatus()));
            }
            result.put(bindingKey, builder.build());
        }));
        return result;
    }

    /**
     * <p>Converts the configured tunnel pair into a single operational entry.</p>
     * <p>Conversion proceeds as follows:</p>
     * <ol>
     *   <li>Reads the pair snapshot from the registry.</li>
     *   <li>Copies endpoint identities and both direction keys into a keyed map.</li>
     * </ol>
     * @return map containing the configured pair entry
     */
    private Map<TunnelPairEntryKey, TunnelPairEntry> buildTunnelPairs() {
        final TunnelPairDefinition pair = tunnelPairRegistry.snapshot();
        final TunnelPairEntryKey key = new TunnelPairEntryKey(pair.pairKey());
        return Map.of(key, new TunnelPairEntryBuilder()
                .setPairKey(pair.pairKey())
                .setLeftRouterId(pair.leftRouterId())
                .setRightRouterId(pair.rightRouterId())
                .setLeftSwitchName(pair.leftSwitchName())
                .setRightSwitchName(pair.rightSwitchName())
                .setForwardDirectionKey(pair.forwardDirection().directionKey())
                .setReverseDirectionKey(pair.reverseDirection().directionKey())
                .build());
    }

    /**
     * <p>Converts both directions of the configured pair to operational entries.</p>
     * <p>Conversion proceeds as follows:</p>
     * <ol>
     *   <li>Reads the current pair from the registry.</li>
     *   <li>Adds forward and reverse directions using {@link #putTunnelDirection}.</li>
     * </ol>
     * @return keyed tunnel-direction entries
     */
    private Map<TunnelDirectionEntryKey, TunnelDirectionEntry> buildTunnelDirections() {
        final TunnelPairDefinition pair = tunnelPairRegistry.snapshot();
        final Map<TunnelDirectionEntryKey, TunnelDirectionEntry> result = new LinkedHashMap<>();
        putTunnelDirection(result, pair.pairKey(), pair.forwardDirection());
        putTunnelDirection(result, pair.pairKey(), pair.reverseDirection());
        return result;
    }

    /**
     * <p>Adds one tunnel direction and its endpoint identities to a result map.</p>
     * <p>Conversion proceeds as follows:</p>
     * <ol>
     *   <li>Creates a key from the direction identifier.</li>
     *   <li>Copies the pair, source, and destination fields into the binding.</li>
     * </ol>
     * @param result destination map modified by this method
     * @param pairKey containing tunnel-pair identifier
     * @param direction direction and endpoint identities
     */
    private static void putTunnelDirection(
            final Map<TunnelDirectionEntryKey, TunnelDirectionEntry> result,
            final String pairKey,
            final TunnelDirection direction) {
        final TunnelDirectionEntryKey key = new TunnelDirectionEntryKey(direction.directionKey());
        result.put(key, new TunnelDirectionEntryBuilder()
                .setDirectionKey(direction.directionKey())
                .setPairKey(pairKey)
                .setSourceLogicalName(direction.source().logicalName())
                .setSourceRouterId(direction.source().routerId())
                .setSourcePccNode(direction.source().pccNode())
                .setDestinationLogicalName(direction.destination().logicalName())
                .setDestinationRouterId(direction.destination().routerId())
                .setDestinationPccNode(direction.destination().pccNode())
                .build());
    }

    /**
     * <p>Counts present directional observations across consensus buckets.</p>
     * <p>Counting proceeds as follows:</p>
     * <ol>
     *   <li>Counts the present left and right observation in each bucket.</li>
     *   <li>Sums those counts across buckets.</li>
     * </ol>
     * @param buckets consensus buckets
     * @return total number of present directional evidence entries
     */
    private static int evidenceEntryCount(final Map<String, PairConsensusBucket> buckets) {
        return buckets.values().stream().mapToInt(bucket ->
                (bucket.leftEvidence().isPresent() ? 1 : 0) + (bucket.rightEvidence().isPresent() ? 1 : 0)).sum();
    }

    /**
     * <p>Counts directional application records across active policies.</p>
     * <p>Counting proceeds as follows:</p>
     * <ol>
     *   <li>Reads the application map size for each policy.</li>
     *   <li>Sums those sizes.</li>
     * </ol>
     * @param policies active policy snapshot
     * @return total application-record count
     */
    private static int applicationEntryCount(final Map<String, ActivePairPolicyState> policies) {
        return policies.values().stream().mapToInt(policy -> policy.lspApplications().size()).sum();
    }

    /**
     * <p>Formats an ERO subobject for the operational state representation.</p>
     * <p>Formatting proceeds as follows:</p>
     * <ol>
     *   <li>Chooses the loose or strict label from the subobject flag.</li>
     *   <li>Appends a separator and the IP prefix.</li>
     * </ol>
     * @param subobject ERO subobject to format
     * @return formatted loose-or-strict label and IP prefix
     */
    private static String ero(final EroSubobject subobject) {
        return (subobject.loose() ? "loose" : "strict") + "|" + subobject.ipPrefix();
    }

    /**
     * <p>Converts an optional timestamp to its standard string representation.</p>
     * <p>Conversion proceeds as follows:</p>
     * <ol>
     *   <li>Checks whether the supplied timestamp is null.</li>
     *   <li>Returns null for an absent value or its string representation otherwise.</li>
     * </ol>
     * @param value timestamp, possibly null
     * @return formatted timestamp or null
     */
    private static String string(final Instant value) {
        return value == null ? null : value.toString();
    }
}
