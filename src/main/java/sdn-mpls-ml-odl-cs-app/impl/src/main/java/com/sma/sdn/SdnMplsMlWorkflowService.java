/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn;

import com.sma.sdn.classification.ClassificationService;
import com.sma.sdn.metrics.SdnMplsMlMetrics;
import com.sma.sdn.model.CalculatedPath;
import com.sma.sdn.model.ClassificationResult;
import com.sma.sdn.model.DelegatedLspRecord;
import com.sma.sdn.model.FlowDirection;
import com.sma.sdn.model.PacketClassificationContext;
import com.sma.sdn.model.PathConstraints;
import com.sma.sdn.model.TunnelDirection;
import com.sma.sdn.observability.LogContext;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.packet.PacketInFeatureExtractor;
import com.sma.sdn.path.PathComputationService;
import com.sma.sdn.registry.DirectionRegistry;
import com.sma.sdn.tunnel.DelegatedLspService;
import java.util.Objects;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;

/**
 * Coordina PacketIn, clasificacion, calculo de camino y actualizacion de LSP delegados preexistentes.
 */
public final class SdnMplsMlWorkflowService {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(SdnMplsMlWorkflowService.class);

    private final PacketInFeatureExtractor packetInFeatureExtractor;
    private final ClassificationService classificationService;
    private final DirectionRegistry directionRegistry;
    private final PathComputationService pathComputationService;
    private final DelegatedLspService delegatedLspService;
    private final SdnMplsMlMetrics metrics;
    private final BooleanSupplier controlPlaneReady;
    private final BooleanSupplier topologyUsable;

    /**
     * Crea el coordinador del flujo restringido a actualizaciones de LSP delegados.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida los servicios de extraccion, clasificacion, direccion y camino.</li>
     *   <li>Conserva el servicio que opera los LSP delegados.</li>
     *   <li>Registra la comprobacion de vigencia de la topologia BGP-LS.</li>
     * </ol>
     *
     * @param packetInFeatureExtractor extractor de contexto PacketIn
     * @param classificationService servicio de clasificacion y cache
     * @param directionRegistry registro de direcciones elegibles
     * @param pathComputationService servicio de calculo de caminos
     * @param delegatedLspService servicio de actualizacion de LSP delegados
     * @param metrics contadores internos
     * @param controlPlaneReady comprobacion de disponibilidad inicial de BGP-LS y PCEP
     * @param topologyUsable comprobacion de vigencia de BGP-LS
     */
    public SdnMplsMlWorkflowService(
            final PacketInFeatureExtractor packetInFeatureExtractor,
            final ClassificationService classificationService,
            final DirectionRegistry directionRegistry,
            final PathComputationService pathComputationService,
            final DelegatedLspService delegatedLspService,
            final SdnMplsMlMetrics metrics,
            final BooleanSupplier controlPlaneReady,
            final BooleanSupplier topologyUsable) {
        this.packetInFeatureExtractor = Objects.requireNonNull(
                packetInFeatureExtractor, "packetInFeatureExtractor");
        this.classificationService = Objects.requireNonNull(classificationService, "classificationService");
        this.directionRegistry = Objects.requireNonNull(directionRegistry, "directionRegistry");
        this.pathComputationService = Objects.requireNonNull(pathComputationService, "pathComputationService");
        this.delegatedLspService = Objects.requireNonNull(delegatedLspService, "delegatedLspService");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.controlPlaneReady = Objects.requireNonNull(controlPlaneReady, "controlPlaneReady");
        this.topologyUsable = Objects.requireNonNull(topologyUsable, "topologyUsable");
    }

    /**
     * Procesa un PacketIn elegible y modifica solamente el camino o ancho de banda del LSP delegado correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Extrae el contexto y descarta trafico no IPv4 o de conectores no elegibles.</li>
     *   <li>Clasifica el paquete y calcula el camino restringido para su direccion.</li>
     *   <li>Compara la ERO y el ancho de banda con el estado PCEP reportado.</li>
     *   <li>Invoca {@code update-lsp} solo cuando existe una diferencia y confirma el resultado.</li>
     * </ol>
     *
     * @param notification notificacion PacketReceived emitida por OpenFlow Plugin
     */
    public void handlePacket(final PacketReceived notification) {
        LOG.debug(
                "packet_workflow_started",
                "handlePacket",
                "Se inicio el flujo de clasificacion y actualizacion para el paquete.",
                Map.of());
        metrics.increment("sma_packet_in_total");
        if (!controlPlaneReady.getAsBoolean()) {
            metrics.increment("sma_packet_in_control_plane_not_ready_total");
            metrics.increment("packet_ignored_control_plane_not_ready");
            LOG.debug(
                    "packet_workflow_control_plane_not_ready",
                    "handlePacket",
                    "Se omitio el flujo porque el descubrimiento BGP-LS y PCEP aun no ha finalizado.",
                    Map.of());
            return;
        }
        final PacketClassificationContext context = packetInFeatureExtractor.extract(notification).orElse(null);
        if (context == null || context.packetFeatures().ethType() != 2048) {
            if (context != null && context.packetFeatures().ethType() == 2054) {
                metrics.increment("packet_ignored_arp");
            } else {
                metrics.increment("packet_ignored_unsupported_eth_type");
            }
            LOG.debug(
                    "packet_ignored_unsupported",
                    "handlePacket",
                    "Se descarto el paquete porque esta vacio o no utiliza IPv4.",
                    StructuredLogger.fields(
                            "context_present", context != null,
                            "eth_type", context == null ? null : context.packetFeatures().ethType()));
            return;
        }

        final FlowDirection flowDirection = directionRegistry.resolve(context);
        if (flowDirection == FlowDirection.UNKNOWN) {
            metrics.increment("packet_ignored_unknown_ingress");
            LOG.debug(
                    "packet_ignored_direction_unknown",
                    "handlePacket",
                    "Se descarto el paquete porque el conector de ingreso no corresponde a una direccion habilitada.",
                    StructuredLogger.fields(
                            "ingress_node_id", context.ingressOpenflowNodeId(),
                            "ingress_switch", context.ingressSwitchName(),
                            "ingress_connector", context.ingressConnectorName()));
            return;
        }
        if (!topologyUsable.getAsBoolean()) {
            LOG.warn(
                    "packet_workflow_topology_stale",
                    "handlePacket",
                    "Se omitio el flujo porque el registro BGP-LS excedio el umbral de antiguedad.",
                    StructuredLogger.fields("ingress_node_id", context.ingressOpenflowNodeId()),
                    null);
            return;
        }

        final TunnelDirection tunnelDirection = directionRegistry.requireTunnelDirection(flowDirection);
        try (LogContext ignored = LogContext.open(Map.of(
                "direction_key", tunnelDirection.directionKey()))) {
            LOG.debug(
                    "packet_direction_resolved",
                    "handlePacket",
                    "Se resolvio la direccion logica del paquete.",
                    StructuredLogger.fields(
                            "source_router_id", tunnelDirection.source().routerId(),
                            "destination_router_id", tunnelDirection.destination().routerId()));
            final ClassificationResult classification = classificationService.classifyOrGetCached(context);
            final PathConstraints constraints = classification.policy().pathConstraints();
            final CalculatedPath path = pathComputationService.computeOrGetCached(tunnelDirection, constraints);
            final boolean unchanged = delegatedLspService.activeStateMatches(tunnelDirection, path, constraints);
            final String action;
            if (unchanged) {
                action = "skip_no_change";
                metrics.increment("sma_update_lsp_skipped_no_change_total");
            } else {
                delegatedLspService.updateDelegatedLsp(tunnelDirection, path, constraints);
                action = "update_lsp";
            }
            final DelegatedLspRecord lsp = delegatedLspService.requireDelegatedLsp(
                    tunnelDirection.directionKey());

            LOG.info(
                    "packet_workflow_completed",
                    "handlePacket",
                    "Finalizo el flujo del paquete sobre el LSP delegado.",
                    StructuredLogger.fields(
                            "ingress_switch", context.ingressSwitchName(),
                            "ingress_connector", context.ingressConnectorName(),
                            "eth_type", context.packetFeatures().ethType(),
                            "ip_protocol", context.packetFeatures().ipProto(),
                            "source_port", context.packetFeatures().srcPort(),
                            "destination_port", context.packetFeatures().dstPort(),
                            "class_name", classification.className(),
                            "confidence", classification.confidence(),
                            "profile_name", classification.policy().profileName(),
                            "bandwidth_kbps", constraints.requestedBandwidthKbps(),
                            "source_graph_node", path.sourceGraphNodeId(),
                            "destination_graph_node", path.destinationGraphNodeId(),
                            "ero", path.eroSubobjects(),
                            "action", action,
                            "lsp_name", lsp.lspName(),
                            "plsp_id", lsp.plspId(),
                            "tunnel_interface", lsp.tunnelInterfaceName(),
                            "operational_state", lsp.operationalState()));
        }
    }
}
