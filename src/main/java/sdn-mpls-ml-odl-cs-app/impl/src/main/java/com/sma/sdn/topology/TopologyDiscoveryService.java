/*
 * Copyright (c) 2026 Santiago Arellano 00328370.
 *
 * Universidad San Francisco de Quito
 * SDN-MPLS-ML Tech Demostrator Thesis Project
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
 * <p>Performs mandatory startup discovery of the configured BGP-LS and PCEP topologies through RESTCONF. It
 * resolves required endpoint router IDs in the shared BGP-LS registry, which path computation subsequently
 * uses.</p>
 */
public final class TopologyDiscoveryService {
    /** <p>Logger for initial topology discovery and read attempts.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(TopologyDiscoveryService.class);
    /** <p>Configuration for topology IDs, endpoint routers, and attempt limits.</p> */
    private final AppConfig config;
    /** <p>RESTCONF client used to read topology data.</p> */
    private final OdlRestconfDataClient client;
    /** <p>Deserializer for advertised network topology identifiers.</p> */
    private final NetworkTopologyListXmlDeserializer topologyListDeserializer;
    /** <p>Deserializer for BGP-LS topology node records.</p> */
    private final BgpLsTopologyXmlDeserializer bgpLsTopologyDeserializer;
    /** <p>Shared BGP-LS router and graph node registry.</p> */
    private final BgpLsNodeRegistry registry;
    /** <p>Classifier that determines whether an HTTP topology response is usable.</p> */
    private final TopologyDiscoveryOutcomeClassifier outcomeClassifier;
    /** <p>Collector for discovery success and failure counters.</p> */
    private final SdnMplsMlMetrics metrics;

    /**
     * <p>Construct the startup discovery service with its supplied collaborators.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     * <li>Retain the configuration, RESTCONF client, deserializers, registry, response classifier, and metrics
     * collector.</li>
     * </ol>
     *
     * @param config topology and retry configuration
     * @param client RESTCONF topology data client
     * @param topologyListDeserializer parser of topology IDs
     * @param bgpLsTopologyDeserializer parser of BGP-LS nodes
     * @param registry destination for discovered nodes
     * @param outcomeClassifier classifier for HTTP responses
     * @param metrics discovery metrics collector
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
     * <p>Discover required topology IDs and populate the BGP-LS node registry for startup.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     * <li>Read and parse the network topology list with bounded attempts, then require both configured topology
     * IDs.</li>
     *   <li>Read and parse the BGP-LS topology and replace the shared registry.</li>
     *   <li>Resolve configured headend and tailend router IDs, record success, and log their graph IDs.</li>
     * </ol>
     *
     * @throws IllegalStateException if a required topology or router is missing or a read fails
     * @throws RuntimeException if a collaborator fails during retrieval or parsing
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

        /*If we are here we know that the pollling process for the topology has passed and therefore we can deserialize
        * the body sent from it to extract the nodes, and links from it. This doest not only poll the corresponding BGPLS topology or the PCEPTolopogy
        * rather it polls ALL of the topologies that are registered in the ssytem, so from here we need to filter to the
        * ones the application needs */
        final Set<String> topologyIds = topologyListDeserializer.deserialize(topologyListResponse.body());
        LOG.debug(
                "topology_identifiers_discovered",
                "initialize",
                "Se deserializaron los identificadores de topologia disponibles.",
                StructuredLogger.fields("topology_ids", topologyIds, "topology_count", topologyIds.size()));

        /*This is the filtering step that looks within the topologies that were discovered to determine if the required
        * set of topologies is within here*/
        requireTopology(topologyIds, config.bgplsTopologyId(), metrics);
        requireTopology(topologyIds, config.pcepTopologyId(), metrics);

        // Install the discovered node snapshot before resolving the configured path endpoints. At this point
        /* we know that the topologies exist and if we got here then both bgpls and pcep are registered in the system, i.e. the
        * peering and sessions for BGPLS and PCEP have been established. From here we can poll the state of the BGPLS topology
        * directly which is the importnt step here*/
        final HttpResponse<String> bgplsResponse = readWithAttempts(
                () -> client.getBgpLsTopology(config.bgplsTopologyId()),
                "topologia BGP-LS");

        /*If we got here this means we finally got a response after our attempts and we can nowuse the corresponding deserializer
        * to break down the XML returned by the operation and and extract the node representation of the network*/
        final List<BgpLsTopologyNode> nodes = bgpLsTopologyDeserializer.deserialize(bgplsResponse.body());
        registry.replaceAll(nodes);

        /*These two results are not important, but what matters is that theyu do not throw and pass as these are assertions
        * that the headend and tail end configured routers from the environment are present*/
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
     * <p>Resolve a required endpoint router to its graph node ID and count failures.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Ask the shared registry for the graph ID.</li>
     *   <li>On a runtime failure, increment the resolution failure counter and rethrow it.</li>
     * </ol>
     *
     * @param routerId endpoint router ID
     * @return corresponding graph node ID
     * @throws RuntimeException if registry resolution fails
     */
    private long resolveRequiredRouterId(final String routerId) {
        /*This function goes ahead and call the corresponding underlying registry which was updated during initialization
        * to find if it contains the required routerID upon its routerID based view. This result returns the graph nod eID which would be
        * the graph topology vertex ID used for path calculation that here is discareded*/
        try {
            return registry.resolveGraphNodeIdByRouterId(routerId);
        } catch (RuntimeException e) {
            metrics.increment("sma_odl_bgpls_resolution_failure_total");
            throw e;
        }
    }

    /**
     * <p>Read one topology resource using a bounded number of immediate attempts. Confirmed success returns the
     * response; hard failures terminate early, while recoverable outcomes or runtime exceptions consume
     * attempts.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Call the supplied read operation and classify each HTTP response.</li>
     *   <li>Return a confirmed response or count and throw on a hard failure.</li>
     *   <li>Log and retain recoverable runtime failures, then retry until the configured limit.</li>
     *   <li>Count failure and throw after exhaustion, using the last caught exception as the cause if one exists.</li>
     * </ol>
     *
     * @param supplier RESTCONF read operation to attempt
     * @param operationName descriptive operation name used in diagnostics
     * @return response classified as confirmed success
     * @throws IllegalStateException on a classified hard failure or exhausted attempts
     */
    private HttpResponse<String> readWithAttempts(
            final java.util.function.Supplier<HttpResponse<String>> supplier,
            final String operationName) {
        RuntimeException lastFailure = null;
        // Each iteration performs a new read; this bounded loop does not add a delay between attempts.
        for (int attempt = 1; attempt <= config.topologyDiscoveryMaxAttempts(); attempt++) {
            try {
                /*Over each iteration of the configured max amount the system performs a call to its supplier, i.e. it performs a
                * specific call to the corresponding function that is passed, like an HTTPResponse call in this case to attempt
                * to get the response from the system. IF this causes an exception we retry, if the outcome is not successful
                * then we also retry*/
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
                    /*If classification isa success i.e. 200 with body we return*/
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
     * <p>Require a configured topology ID to be present in the discovered topology list.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Check the supplied set for the required ID.</li>
     *   <li>On absence, increment the discovery failure counter and throw.</li>
     * </ol>
     *
     * @param topologyIds discovered topology identifiers
     * @param requiredTopologyId configured identifier that must exist
     * @param metrics collector for the discovery failure counter
     * @throws IllegalStateException if the required ID is absent
     */
    private static void requireTopology(
            final Set<String> topologyIds,
            final String requiredTopologyId,
            final SdnMplsMlMetrics metrics) {

        /*This function looks within a set of strings for the presence of a string representing a topology tht we want, if it is no
        * it throws illegal state which is what stops all processing during the readinessAttempt approach in the init() section
        * of the provider class*/
        if (!topologyIds.contains(requiredTopologyId)) {
            metrics.increment("sma_odl_topology_discovery_failure_total");
            throw new IllegalStateException("No esta disponible la topologia obligatoria: " + requiredTopologyId);
        }
    }
}
