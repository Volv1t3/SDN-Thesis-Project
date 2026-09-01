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

/** Publishes the controller's safe runtime snapshot into the MD-SAL operational datastore. */
public final class ControllerOperationalStatePublisher {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(ControllerOperationalStatePublisher.class);
    private static final DataObjectIdentifier<ControllerState> STATE_PATH =
            DataObjectIdentifier.builder(ControllerState.class).build();

    private final DataBroker dataBroker;
    private final AppConfig config;
    private final BgpLsNodeRegistry bgpLsNodeRegistry;
    private final ClassificationRegistrar classificationRegistrar;
    private final CalculatedPathRegistry calculatedPathRegistry;
    private final DelegatedLspRegistry delegatedLspRegistry;
    private final OpenflowSwitchRegistry openflowSwitchRegistry;
    private final DirectionalClassificationEvidenceRegistry evidenceRegistry;
    private final ActivePairPolicyRegistry activePolicyRegistry;
    private final TunnelPairRegistry tunnelPairRegistry;
    private final TopologyRefreshService topologyRefreshService;
    private final SdnMplsMlMetrics metrics;
    private final BooleanSupplier controlPlaneReady;
    private final BooleanSupplier closed;
    private final LongSupplier processedPacketCount;

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

    /** Replaces the complete read-only snapshot atomically from the client's perspective. */
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

    private static int openflowConnectorCount(final Map<OpenflowSwitchKey, OpenflowSwitch> openflowSwitches) {
        return openflowSwitches.values().stream()
                .map(OpenflowSwitch::getConnector)
                .filter(Objects::nonNull)
                .mapToInt(Map::size)
                .sum();
    }

    private PolicyStateBuilder buildPolicyState() {
        return new PolicyStateBuilder()
                .setConsensusEnabled(config.pairConsensusEnabled())
                .setRequireBothDirections(config.pairConsensusRequireBothDirections())
                .setSingleSideProvisionalEnabled(config.pairConsensusSingleSideProvisionalEnabled())
                .setEqualPriorityAction(config.pairConsensusEqualPriorityAction().name())
                .setEvidenceTtlMillis(Uint64.valueOf(config.pairConsensusEvidenceTtl().toMillis()))
                .setActivePolicyIdleTtlMillis(Uint64.valueOf(config.activePairPolicyIdleTtl().toMillis()))
                .setActivePolicySweeperEnabled(config.activePairPolicySweeperEnabled())
                .setActivePolicySweeperIntervalMillis(
                        Uint64.valueOf(config.activePairPolicySweeperInterval().toMillis()))
                .setPriorityPreemptionEnabled(config.pairPolicyPriorityPreemptionEnabled())
                .setPolicyHashVersion(Uint32.valueOf(config.pairPolicyHashVersion()))
                .setLspApplicationScope(config.pairPolicyLspApplicationScope().name())
                .setRequireAllLspDirections(config.lspApplicationRequireAllDirections())
                .setClassificationCacheTtlMillis(Uint64.valueOf(config.classificationCacheTtl().toMillis()))
                .setCalculatedPathCacheTtlMillis(Uint64.valueOf(config.pathCacheTtl().toMillis()));
    }

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

    private static Set<String> probabilities(final ClassificationResult value) {
        return Set.copyOf(value.probabilities().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList());
    }

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

    private static Map<DirectionalPolicyEvidenceEntryKey, DirectionalPolicyEvidenceEntry> buildEvidence(
            final Map<String, PairConsensusBucket> snapshot) {
        final Map<DirectionalPolicyEvidenceEntryKey, DirectionalPolicyEvidenceEntry> result = new LinkedHashMap<>();
        snapshot.forEach((bucketKey, bucket) -> {
            bucket.leftEvidence().ifPresent(evidence -> putEvidence(result, bucketKey, "LEFT", bucket, evidence));
            bucket.rightEvidence().ifPresent(evidence -> putEvidence(result, bucketKey, "RIGHT", bucket, evidence));
        });
        return result;
    }

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

    private Map<TunnelDirectionEntryKey, TunnelDirectionEntry> buildTunnelDirections() {
        final TunnelPairDefinition pair = tunnelPairRegistry.snapshot();
        final Map<TunnelDirectionEntryKey, TunnelDirectionEntry> result = new LinkedHashMap<>();
        putTunnelDirection(result, pair.pairKey(), pair.forwardDirection());
        putTunnelDirection(result, pair.pairKey(), pair.reverseDirection());
        return result;
    }

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

    private static int evidenceEntryCount(final Map<String, PairConsensusBucket> buckets) {
        return buckets.values().stream().mapToInt(bucket ->
                (bucket.leftEvidence().isPresent() ? 1 : 0) + (bucket.rightEvidence().isPresent() ? 1 : 0)).sum();
    }

    private static int applicationEntryCount(final Map<String, ActivePairPolicyState> policies) {
        return policies.values().stream().mapToInt(policy -> policy.lspApplications().size()).sum();
    }

    private static String ero(final EroSubobject subobject) {
        return (subobject.loose() ? "loose" : "strict") + "|" + subobject.ipPrefix();
    }

    private static String string(final Instant value) {
        return value == null ? null : value.toString();
    }
}
