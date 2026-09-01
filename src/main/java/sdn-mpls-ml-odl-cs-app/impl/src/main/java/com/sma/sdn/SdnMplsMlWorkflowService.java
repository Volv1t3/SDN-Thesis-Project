/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn;

import com.sma.sdn.classification.ClassificationService;
import com.sma.sdn.config.AppConfig;
import com.sma.sdn.metrics.SdnMplsMlMetrics;
import com.sma.sdn.model.ClassificationLookupResult;
import com.sma.sdn.model.ClassificationResult;
import com.sma.sdn.model.DirectionalPolicyEvidence;
import com.sma.sdn.model.FlowDirection;
import com.sma.sdn.model.PacketClassificationContext;
import com.sma.sdn.model.TunnelDirection;
import com.sma.sdn.model.WorkflowContext;
import com.sma.sdn.observability.LogContext;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.packet.PacketInFeatureExtractor;
import com.sma.sdn.policy.PairPolicyCoordinator;
import com.sma.sdn.policy.PairPolicyHashService;
import com.sma.sdn.policy.ServiceKeyResolver;
import com.sma.sdn.registry.DirectionRegistry;
import com.sma.sdn.registry.TunnelPairRegistry;
import com.sma.sdn.topology.BandwidthTranslator;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;

/**
 * Coordina PacketIn, clasificacion, calculo de camino y actualizacion de LSP delegados preexistentes.
 */
public final class SdnMplsMlWorkflowService {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(SdnMplsMlWorkflowService.class);

    private final PacketInFeatureExtractor packetInFeatureExtractor;
    private final ClassificationService classificationService;
    private final AppConfig config;
    private final DirectionRegistry directionRegistry;
    private final TunnelPairRegistry pairRegistry;
    private final ServiceKeyResolver serviceKeyResolver;
    private final PairPolicyHashService hashService;
    private final PairPolicyCoordinator pairPolicyCoordinator;
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
            final AppConfig config,
            final PacketInFeatureExtractor packetInFeatureExtractor,
            final ClassificationService classificationService,
            final DirectionRegistry directionRegistry,
            final TunnelPairRegistry pairRegistry,
            final ServiceKeyResolver serviceKeyResolver,
            final PairPolicyHashService hashService,
            final PairPolicyCoordinator pairPolicyCoordinator,
            final SdnMplsMlMetrics metrics,
            final BooleanSupplier controlPlaneReady,
            final BooleanSupplier topologyUsable) {
        this.config = Objects.requireNonNull(config, "config");
        this.packetInFeatureExtractor = Objects.requireNonNull(
                packetInFeatureExtractor, "packetInFeatureExtractor");
        this.classificationService = Objects.requireNonNull(classificationService, "classificationService");
        this.directionRegistry = Objects.requireNonNull(directionRegistry, "directionRegistry");
        this.pairRegistry = Objects.requireNonNull(pairRegistry, "pairRegistry");
        this.serviceKeyResolver = Objects.requireNonNull(serviceKeyResolver, "serviceKeyResolver");
        this.hashService = Objects.requireNonNull(hashService, "hashService");
        this.pairPolicyCoordinator = Objects.requireNonNull(pairPolicyCoordinator, "pairPolicyCoordinator");
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
            recordControlCycle("topology_stale", "unknown", 0L);
            LOG.warn(
                    "packet_workflow_topology_stale",
                    "handlePacket",
                    "Se omitio el flujo porque el registro BGP-LS excedio el umbral de antiguedad.",
                    StructuredLogger.fields("ingress_node_id", context.ingressOpenflowNodeId()),
                    null);
            return;
        }

        final TunnelDirection ingressTunnelDirection = directionRegistry.requireTunnelDirection(flowDirection);
        final long controlCycleStartedAt = System.nanoTime();
        boolean controlCycleRecorded = false;
        String controlCycleClassName = "unknown";
        try (LogContext ignored = LogContext.open(Map.of(
                "direction_key", ingressTunnelDirection.directionKey()))) {
            LOG.debug(
                    "packet_direction_resolved",
                    "handlePacket",
                    "Se resolvio la direccion logica del paquete.",
                    StructuredLogger.fields(
                            "source_router_id", ingressTunnelDirection.source().routerId(),
                            "destination_router_id", ingressTunnelDirection.destination().routerId()));
            final ClassificationLookupResult lookup = classificationService.classifyOrGetCached(context);
            final ClassificationResult classification = lookup.classification();
            controlCycleClassName = classification.className();
            if (lookup.cacheHit()) {
                recordControlCycle("cached_hit", classification.className(), controlCycleStartedAt);
                controlCycleRecorded = true;
            }
            final WorkflowContext workflowContext = WorkflowContext.current();
            final DirectionalPolicyEvidence evidence = buildEvidence(context, ingressTunnelDirection, classification);
            final var decision = pairPolicyCoordinator.handleEvidence(evidence, workflowContext);
            if (decision.candidate().isEmpty()) {
                final String outcome = decision.consensusStatus().name().equals("PENDING_ONE_SIDE")
                        ? "pending_consensus" : "deferred";
                if (!lookup.cacheHit()) {
                    recordControlCycle(outcome, classification.className(), controlCycleStartedAt);
                    controlCycleRecorded = true;
                }
                LOG.info("packet_workflow_pending_consensus", "handlePacket",
                        "La evidencia fue registrada, pero aun no existe una politica de par accionable.",
                        StructuredLogger.fields("pair_key", decision.pairKey(),
                                "service_key", decision.serviceKey().normalizedValue(),
                                "consensus_status", decision.consensusStatus(), "observed_direction_key",
                                ingressTunnelDirection.directionKey()));
                return;
            }
            if (!lookup.cacheHit()) {
                final boolean failed = decision.applications().stream()
                        .anyMatch(application -> application.status().startsWith("FAILED"));
                final String outcome = decision.applications().isEmpty() ? "deferred" : failed ? "failed" : "success";
                recordControlCycle(outcome, classification.className(), controlCycleStartedAt);
                controlCycleRecorded = true;
            }
            LOG.info(
                    "packet_workflow_completed",
                    "handlePacket",
                    "Finalizo el flujo del paquete con una decision de politica de par.",
                    StructuredLogger.fields(
                            "ingress_switch", context.ingressSwitchName(),
                            "ingress_connector", context.ingressConnectorName(),
                            "observed_direction", flowDirection,
                            "pair_key", decision.pairKey(),
                            "service_key", decision.serviceKey().normalizedValue(),
                            "consensus_status", decision.consensusStatus(),
                            "preemption_decision", decision.preemptionDecision(),
                            "processed_direction_keys", decision.applications().stream()
                                    .map(application -> application.directionKey()).toList(),
                            "classification_class", classification.className(),
                            "profile_name", classification.policy().profileName(),
                            "lsp_application_statuses", decision.applications().stream()
                                    .map(application -> application.status()).toList(),
                            "suppression_flow_enabled", false));
        } catch (RuntimeException e) {
            if (!controlCycleRecorded) {
                recordControlCycle("failed", controlCycleClassName, controlCycleStartedAt);
            }
            throw e;
        }
    }

    private DirectionalPolicyEvidence buildEvidence(final PacketClassificationContext context,
            final TunnelDirection direction, final ClassificationResult classification) {
        final Instant observedAt = context.receivedAt() == null ? Instant.now() : context.receivedAt();
        final var serviceKey = serviceKeyResolver.resolve(context.packetFeatures());
        final var pair = pairRegistry.requirePairForDirection(direction.directionKey());
        final String bandwidth = BandwidthTranslator.kbpsToPcepBandwidthBase64Float32(
                classification.policy().pathConstraints().requestedBandwidthKbps());
        final DirectionalPolicyEvidence partial = new DirectionalPolicyEvidence(
                pair.pairKey(), direction.directionKey(), context.ingressSwitchName(), context.ingressConnectorName(),
                context.packetFeatures(), serviceKey, classification.className(), classification.policy().profileName(),
                classification.policy().dscp(), classification.policy().mplsTc(),
                (int) classification.policy().pathConstraints().requestedBandwidthKbps(), bandwidth,
                classification.policy().pathConstraints().setupPriority(),
                classification.policy().pathConstraints().holdPriority(), "classifier-policy-v1", "", observedAt,
                observedAt.plus(config.pairConsensusEvidenceTtl()));
        final DirectionalPolicyEvidence evidence = new DirectionalPolicyEvidence(
                partial.pairKey(), partial.directionKey(), partial.ingressSwitchName(),
                partial.ingressConnectorName(), partial.packetFeatures(), partial.serviceKey(),
                partial.className(), partial.profileName(), partial.dscp(), partial.mplsTc(),
                partial.requestedBandwidthKbps(), partial.requestedBandwidthBase64(), partial.setupPriority(),
                partial.holdPriority(), partial.policySchemaVersion(), hashService.hashDirectionalEvidence(partial),
                partial.observedAt(), partial.expiresAt());
        LOG.debug(
                "directional_policy_evidence_built",
                "buildEvidence",
                "Se construyo evidencia direccional para consenso.",
                StructuredLogger.fields("pair_key", evidence.pairKey(),
                        "service_key", evidence.serviceKey().normalizedValue(),
                        "class_name", evidence.className(), "policy_hash", evidence.policyHash()));
        return evidence;
    }

    private void recordControlCycle(final String outcome, final String className, final long startedAt) {
        final Map<String, String> labels = Map.of("outcome", outcome, "class_name", className);
        metrics.incrementCounter("sma_control_cycle_total", labels);
        if (startedAt > 0L) {
            metrics.observeHistogram("sma_control_cycle_duration_seconds", labels,
                    (System.nanoTime() - startedAt) / 1_000_000_000.0d);
        }
    }
}
