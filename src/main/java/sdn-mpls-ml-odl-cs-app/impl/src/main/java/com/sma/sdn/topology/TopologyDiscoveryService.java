/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.topology;

import com.sma.sdn.config.AppConfig;
import com.sma.sdn.http.OdlRestconfDataClient;
import com.sma.sdn.http.TopologyDiscoveryOutcomeClassifier;
import com.sma.sdn.metrics.SdnMplsMlMetrics;
import com.sma.sdn.model.BgpLsTopologyNode;
import com.sma.sdn.model.OdlCallOutcome;
import com.sma.sdn.model.OdlCallOutcomeType;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.registry.BgpLsNodeRegistry;
import com.sma.sdn.serialization.xml.BgpLsTopologyXmlDeserializer;
import com.sma.sdn.serialization.xml.NetworkTopologyListXmlDeserializer;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;

/**
 * Define la clase {@code TopologyDiscoveryService} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class TopologyDiscoveryService {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(TopologyDiscoveryService.class);
    private final AppConfig config;
    private final OdlRestconfDataClient client;
    private final NetworkTopologyListXmlDeserializer topologyListDeserializer;
    private final BgpLsTopologyXmlDeserializer bgpLsTopologyDeserializer;
    private final BgpLsNodeRegistry registry;
    private final TopologyDiscoveryOutcomeClassifier outcomeClassifier;
    private final SdnMplsMlMetrics metrics;

    /**
     * Ejecuta la operacion {@code TopologyDiscoveryService} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param config valor requerido para ejecutar esta operacion
     *
     * @param client valor requerido para ejecutar esta operacion
     *
     * @param topologyListDeserializer valor requerido para ejecutar esta operacion
     *
     * @param bgpLsTopologyDeserializer valor requerido para ejecutar esta operacion
     *
     * @param registry valor requerido para ejecutar esta operacion
     *
     * @param outcomeClassifier valor requerido para ejecutar esta operacion
     *
     * @param metrics valor requerido para ejecutar esta operacion
     */
    public TopologyDiscoveryService(
            final AppConfig config,
            final OdlRestconfDataClient client,
            final NetworkTopologyListXmlDeserializer topologyListDeserializer,
            final BgpLsTopologyXmlDeserializer bgpLsTopologyDeserializer,
            final BgpLsNodeRegistry registry,
            final TopologyDiscoveryOutcomeClassifier outcomeClassifier,
            final SdnMplsMlMetrics metrics) {
        this.config = config;
        this.client = client;
        this.topologyListDeserializer = topologyListDeserializer;
        this.bgpLsTopologyDeserializer = bgpLsTopologyDeserializer;
        this.registry = registry;
        this.outcomeClassifier = outcomeClassifier;
        this.metrics = metrics;
    }

    /**
     * Ejecuta el descubrimiento inicial obligatorio de topologias y nodos BGP-LS.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    public void initialize() {
        LOG.info(
                "topology_discovery_started",
                "initialize",
                "Se inicio el descubrimiento obligatorio de topologias ODL.",
                StructuredLogger.fields(
                        "bgpls_topology_id", config.bgplsTopologyId(),
                        "pcep_topology_id", config.pcepTopologyId()));
        final HttpResponse<String> topologyListResponse = readWithAttempts(
                client::getNetworkTopologyList,
                "lista network-topology");
        final Set<String> topologyIds = topologyListDeserializer.deserialize(topologyListResponse.body());
        LOG.debug(
                "topology_identifiers_discovered",
                "initialize",
                "Se deserializaron los identificadores de topologia disponibles.",
                StructuredLogger.fields("topology_ids", topologyIds, "topology_count", topologyIds.size()));
        requireTopology(topologyIds, config.bgplsTopologyId(), metrics);
        requireTopology(topologyIds, config.pcepTopologyId(), metrics);

        final HttpResponse<String> bgplsResponse = readWithAttempts(
                () -> client.getBgpLsTopology(config.bgplsTopologyId()),
                "topologia BGP-LS");
        final List<BgpLsTopologyNode> nodes = bgpLsTopologyDeserializer.deserialize(bgplsResponse.body());
        registry.replaceAll(nodes);
        final long headendGraphId = resolveRequiredRouterId(config.headend().routerId());
        final long tailendGraphId = resolveRequiredRouterId(config.tailend().routerId());
        metrics.increment("sma_odl_topology_discovery_success_total");
        LOG.info(
                "topology_discovery_completed",
                "initialize",
                "Se resolvieron los identificadores de grafo BGP-LS requeridos.",
                StructuredLogger.fields(
                        "node_count", nodes.size(),
                        "headend_router_id", config.headend().routerId(),
                        "headend_graph_node_id", headendGraphId,
                        "tailend_router_id", config.tailend().routerId(),
                        "tailend_graph_node_id", tailendGraphId));
    }

    /**
     * Ejecuta la operacion {@code resolveRequiredRouterId} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param routerId valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    private long resolveRequiredRouterId(final String routerId) {
        try {
            return registry.resolveGraphNodeIdByRouterId(routerId);
        } catch (RuntimeException e) {
            metrics.increment("sma_odl_bgpls_resolution_failure_total");
            throw e;
        }
    }

    /**
     * Ejecuta la operacion {@code readWithAttempts} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param supplier valor requerido para ejecutar esta operacion
     *
     * @param operationName valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    private HttpResponse<String> readWithAttempts(
            final java.util.function.Supplier<HttpResponse<String>> supplier,
            final String operationName) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= config.topologyDiscoveryMaxAttempts(); attempt++) {
            try {
                final HttpResponse<String> response = supplier.get();
                final OdlCallOutcome outcome = outcomeClassifier.classify(response);
                LOG.debug(
                        "topology_discovery_attempt_classified",
                        "readWithAttempts",
                        "Se clasifico un intento de lectura de topologia.",
                        StructuredLogger.fields(
                                "operation_name", operationName,
                                "attempt", attempt,
                                "outcome", outcome.type(),
                                "http_status", outcome.httpStatus(),
                                "failure_reason", outcome.failureReason()));
                if (outcome.type() == OdlCallOutcomeType.CONFIRMED_SUCCESS) {
                    return response;
                }
                if (outcome.type() == OdlCallOutcomeType.HARD_FAILURE) {
                    metrics.increment("sma_odl_topology_discovery_failure_total");
                    throw new IllegalStateException(operationName + " fallo: " + outcome.failureReason());
                }
            } catch (RuntimeException e) {
                if (e.getMessage() != null && e.getMessage().startsWith(operationName + " fallo:")) {
                    throw e;
                }
                lastFailure = e;
                LOG.warn(
                        "topology_discovery_attempt_failed",
                        "readWithAttempts",
                        "Fallo un intento recuperable de lectura de topologia.",
                        StructuredLogger.fields("operation_name", operationName, "attempt", attempt),
                        e);
            }
        }
        metrics.increment("sma_odl_topology_discovery_failure_total");
        throw new IllegalStateException("No fue posible leer " + operationName, lastFailure);
    }

    /**
     * Ejecuta la operacion {@code requireTopology} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param topologyIds valor requerido para ejecutar esta operacion
     *
     * @param requiredTopologyId valor requerido para ejecutar esta operacion
     *
     * @param metrics valor requerido para ejecutar esta operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    private static void requireTopology(
            final Set<String> topologyIds,
            final String requiredTopologyId,
            final SdnMplsMlMetrics metrics) {
        if (!topologyIds.contains(requiredTopologyId)) {
            metrics.increment("sma_odl_topology_discovery_failure_total");
            throw new IllegalStateException("No esta disponible la topologia obligatoria: " + requiredTopologyId);
        }
    }
}
