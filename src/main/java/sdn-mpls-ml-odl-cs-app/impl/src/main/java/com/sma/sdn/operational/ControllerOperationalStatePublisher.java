/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.operational;

import com.sma.sdn.config.AppConfig;
import com.sma.sdn.model.BgpLsTopologyNode;
import com.sma.sdn.openflow.OpenflowConnectorRecord;
import com.sma.sdn.openflow.OpenflowSwitchRecord;
import com.sma.sdn.openflow.OpenflowSwitchRegistry;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.registry.ActivePairPolicyRegistry;
import com.sma.sdn.registry.BgpLsNodeRegistry;
import com.sma.sdn.registry.CalculatedPathRegistry;
import com.sma.sdn.registry.ClassificationRegistrar;
import com.sma.sdn.registry.DelegatedLspRegistry;
import com.sma.sdn.registry.DirectionalClassificationEvidenceRegistry;
import com.sma.sdn.topology.TopologyRefreshService;
import com.sma.sdn.topology.TopologyRefreshStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.ControllerState;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.ControllerStateBuilder;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.BgpLsNode;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.BgpLsNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.BgpLsNodeKey;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.CacheStateBuilder;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.ControlPlaneBuilder;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.OpenflowSwitch;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.OpenflowSwitchBuilder;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.OpenflowSwitchKey;
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
    private final TopologyRefreshService topologyRefreshService;
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
            final TopologyRefreshService topologyRefreshService,
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
        this.topologyRefreshService = Objects.requireNonNull(topologyRefreshService, "topologyRefreshService");
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
        } catch (RuntimeException e) {
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
                        .setClassificationEntryCount(Uint32.valueOf(classificationRegistrar.size()))
                        .setCalculatedPathEntryCount(Uint32.valueOf(calculatedPathRegistry.size()))
                        .setPolicyEvidenceBucketCount(Uint32.valueOf(evidenceRegistry.size()))
                        .setActivePairPolicyCount(Uint32.valueOf(activePolicyRegistry.size()))
                        .setDelegatedLspCount(Uint32.valueOf(delegatedLspRegistry.size()))
                        .setOpenflowSwitchCount(Uint32.valueOf(openflowSwitchRegistry.size()))
                        .build())
                .setBgpLsNode(nodes)
                .setOpenflowSwitch(openflowSwitches)
                .build();
    }
}
