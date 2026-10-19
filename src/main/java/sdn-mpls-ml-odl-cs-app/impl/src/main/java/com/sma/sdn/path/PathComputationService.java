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

package com.sma.sdn.path;

import com.sma.sdn.config.AppConfig;
import com.sma.sdn.http.OdlOperationsClient;
import com.sma.sdn.http.PathComputationOutcomeClassifier;
import com.sma.sdn.metrics.SdnMplsMlMetrics;
import com.sma.sdn.model.*;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.registry.BgpLsNodeRegistry;
import com.sma.sdn.registry.CalculatedPathRegistry;
import com.sma.sdn.serialization.xml.PathComputationRequestXmlSerializer;
import com.sma.sdn.serialization.xml.PathComputationResponseXmlDeserializer;
import com.sma.sdn.topology.BandwidthTranslator;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;

/**
 * <p>Resolves directional CSPF requests against the BGP-LS node registry, reuses valid
 * calculated paths, or invokes OpenDaylight and caches a newly calculated path and ERO.</p>
 *
 * <p>The service coordinates {@link CalculatedPathRegistry}, the ODL operations client,
 * XML serializers and {@link CalculatedPathToEroTranslator}; it records cache and RPC
 * outcomes through {@link SdnMplsMlMetrics}.</p>
 */
public final class PathComputationService {
    /** <p>Records cache decisions and path-computation outcomes.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(PathComputationService.class);
    /** <p>Class type used for laboratory CSPF requests.</p> */
    private static final int LAB_CLASS_TYPE = 0;
    /** <p>Algorithm selected for laboratory constrained path requests.</p> */
    private static final String LAB_ALGORITHM = "cspf";
    /** <p>Supplies graph name, retry limit and path cache lifetime.</p> */
    private final AppConfig config;
    /** <p>Resolves router identities into BGP-LS graph node identifiers.</p> */
    private final BgpLsNodeRegistry nodeRegistry;
    /** <p>Stores calculated paths indexed by their directional constraints.</p> */
    private final CalculatedPathRegistry pathRegistry;
    /** <p>Invokes the OpenDaylight constrained-path operation.</p> */
    private final OdlOperationsClient operationsClient;
    /** <p>Builds the XML request for the path-computation operation.</p> */
    private final PathComputationRequestXmlSerializer requestSerializer;
    /** <p>Reads the computed path from a successful XML response.</p> */
    private final PathComputationResponseXmlDeserializer responseDeserializer;
    /** <p>Converts computed hops into PCEP explicit-route subobjects.</p> */
    private final CalculatedPathToEroTranslator eroTranslator;
    /** <p>Distinguishes confirmed, hard-failure and unconfirmed ODL responses.</p> */
    private final PathComputationOutcomeClassifier outcomeClassifier;
    /** <p>Records cache, computation and duration metrics.</p> */
    private final SdnMplsMlMetrics metrics;

    /**
     * <p>Stores the collaborators used for cache lookup, remote computation and metrics.</p>
     *
     * <p>Construction performs the following step:</p>
     * <ol>
     *   <li>Assign each supplied collaborator without additional validation.</li>
     * </ol>
     *
     * @param config graph, retry and cache settings
     * @param nodeRegistry BGP-LS router-to-node resolver
     * @param pathRegistry calculated path cache
     * @param operationsClient OpenDaylight operations client
     * @param requestSerializer request XML serializer
     * @param responseDeserializer response XML deserializer
     * @param eroTranslator computed-hop to ERO translator
     * @param outcomeClassifier HTTP response classifier
     * @param metrics application metrics collector
     */
    public PathComputationService(
            final AppConfig config,
            final BgpLsNodeRegistry nodeRegistry,
            final CalculatedPathRegistry pathRegistry,
            final OdlOperationsClient operationsClient,
            final PathComputationRequestXmlSerializer requestSerializer,
            final PathComputationResponseXmlDeserializer responseDeserializer,
            final CalculatedPathToEroTranslator eroTranslator,
            final PathComputationOutcomeClassifier outcomeClassifier,
            final SdnMplsMlMetrics metrics) {
        this.config = config;
        this.nodeRegistry = nodeRegistry;
        this.pathRegistry = pathRegistry;
        this.operationsClient = operationsClient;
        this.requestSerializer = requestSerializer;
        this.responseDeserializer = responseDeserializer;
        this.eroTranslator = eroTranslator;
        this.outcomeClassifier = outcomeClassifier;
        this.metrics = metrics;
    }

    /**
     * <p>Returns a valid cached path for the requested direction and bandwidth, or
     * computes and caches a new CSPF path when none is valid.</p>
     *
     * <p>The lookup proceeds as follows:</p>
     * <ol>
     *   <li>Convert requested bandwidth and resolve both router IDs to graph node IDs.</li>
     *   <li>Build the directional cache key with the laboratory class type and CSPF algorithm.</li>
     *   <li>Record and return a valid cache hit, or invoke {@link #compute} on a miss.</li>
     * </ol>
     *
     * @param direction source and destination tunnel endpoints
     * @param constraints requested path constraints, including bandwidth
     * @return valid cached or newly computed path
     * @throws IllegalStateException if a graph node cannot be resolved or computation fails
     */
    public CalculatedPath computeOrGetCached(final TunnelDirection direction, final PathConstraints constraints) {

        /*As we have established before, the bandwidth requirements for the application are not the same as the ones that
        * are stored within the main classification service API, as the ODL controller requires bytes per second bandwidth
        * values whereas for convenience the values used in th rest of the project correspond to kilo bits per second*/
        final long bandwidthBytesPerSecond = BandwidthTranslator.kbpsToBytesPerSecond(
                constraints.requestedBandwidthKbps());

        /*Using the topology service which should've already fed into the BgpLsNodeRegistry, we use the node registry to translate
        * from the knowledge of router ids that we have from the tunnel direction down to a graph vertex id for calculation*/
        final long sourceGraphNodeId = nodeRegistry
                .resolveGraphNodeIdByRouterId(direction.source().routerId());
        final long destinationGraphNodeId = nodeRegistry
                .resolveGraphNodeIdByRouterId(direction.destination().routerId());

        /*
        * We create a DTO used for transmitting the calculated path key and therefore the information required for
        * calculating the path and sending the requeszt between this component and the OdlRests client*/
        final CalculatedPathKey key = new CalculatedPathKey(
                sourceGraphNodeId,
                destinationGraphNodeId,
                bandwidthBytesPerSecond,
                LAB_CLASS_TYPE,
                LAB_ALGORITHM);
        LOG.debug(
                "path_lookup_started",
                "computeOrGetCached",
                "Se resolvieron los nodos BGP-LS y se inicio la busqueda del camino.",
                StructuredLogger.fields(
                        "source_router_id", direction.source().routerId(),
                        "destination_router_id", direction.destination().routerId(),
                        "source_graph_node_id", sourceGraphNodeId,
                        "destination_graph_node_id", destinationGraphNodeId,
                        "bandwidth_bytes_per_second", bandwidthBytesPerSecond,
                        "algorithm", LAB_ALGORITHM));

        /*We attempt to find, simi.lar to the ClassificationService, first a key based mapping between the request values
        * and an already calculated path. If we find it we return it directly, this would be a CalculatedPath object*/
        return pathRegistry.findValid(key)
                .map(path -> {
                    metrics.increment("sma_path_cache_hit_total");
                    LOG.debug(
                            "calculated_path_cache_hit",
                            "computeOrGetCached",
                            "Se reutilizo un camino calculado vigente desde cache.",
                            StructuredLogger.fields(
                                    "ero", path.eroSubobjects(),
                                    "computed_te_metric", path.computedTeMetric(),
                                    "expires_at", path.expiresAt()));
                    return path; /*This object contains the calculated Path directly contaiing the ERO and the Nodes List
                    from the original calculation*/
                })
                /*If there is no knowledge for that key we call the underlying computation mechanism*/
                .orElseGet(() -> compute(direction, key));
    }

    /**
     * <p>Requests and caches a path for a cache miss, translating its hops into an ERO.
     * An incomplete response is rejected and failure durations are recorded.</p>
     *
     * <p>The computation proceeds as follows:</p>
     * <ol>
     *   <li>Record the miss and construct an ODL request from the graph and cache key.</li>
     *   <li>Call the retry helper and deserialize its confirmed response; record failures.</li>
     *   <li>Reject an incomplete computation, otherwise translate the hops to an ERO.</li>
     *   <li>Store the path with the configured expiration and record successful duration.</li>
     * </ol>
     *
     * @param direction tunnel direction supplying the ERO destination
     * @param key graph endpoints and constraints for the request and cache entry
     * @return newly computed and cached path
     * @throws IllegalStateException if ODL rejects the request, retries are exhausted, or the result is incomplete
     */
    private CalculatedPath compute(final TunnelDirection direction, final CalculatedPathKey key) {
        metrics.increment("sma_path_cache_miss_total");
        LOG.debug(
                "calculated_path_cache_miss",
                "compute",
                "No existe un camino vigente para la clave direccional solicitada.",
                StructuredLogger.fields(
                        "source_graph_node_id", key.sourceGraphNodeId(),
                        "destination_graph_node_id", key.destinationGraphNodeId(),
                        "bandwidth_bytes_per_second", key.bandwidthBytesPerSecond(),
                        "algorithm", key.algorithm(),
                        "class_type", key.classType()));
        LOG.debug(
                "calculated_path_cache_miss_reason",
                "compute",
                "El registro no contiene una entrada vigente para la clave solicitada.",
                StructuredLogger.fields("reason", "MISSING_OR_EXPIRED", "registry_size", pathRegistry.size()));
        metrics.increment("sma_path_computation_request_total");
        final long startedAt = System.nanoTime();
        /*Translate the corresponding key that was created in the computeOrGetCached method into the DTO object for the
        * calculated path request which contains additional atrtibutes like the graph name from which to perform the calculation used
        * in the request body for the get-constrainted-path operation*/
        final CalculatedPathRequest request = new CalculatedPathRequest(
                config.pathComputationGraphName(),
                key.sourceGraphNodeId(),
                key.destinationGraphNodeId(),
                key.bandwidthBytesPerSecond(),
                key.classType(),
                key.algorithm());
        LOG.debug(
                "path_computation_requested",
                "compute",
                "Se construyo la solicitud de calculo de camino restringido.",
                StructuredLogger.fields(
                        "graph_name", request.graphName(),
                        "source_graph_node_id", request.sourceGraphNodeId(),
                        "destination_graph_node_id", request.destinationGraphNodeId(),
                        "bandwidth_bytes_per_second", request.bandwidthBytesPerSecond(),
                        "class_type", request.classType(),
                        "algorithm", request.algorithm()));
        final HttpResponse<String> response;
        final PathComputationResponse parsed;
        try {
            /*Attempt to perform a computation using a compute with retry abstraction that allows for executing
            * the RPC operation request a series of times without an added delay, executing, awaiting and retrying if the result is an error
            * or an exception*/
            response = computeWithRetry(request);

            /**
             * <p>Deserialize the accepted response into path descriptions represented by
             * {@link com.sma.sdn.model.PathHop} records. The ERO translator later derives subobjects from the
             * response for storage in the {@link CalculatedPath}.</p>
             */
            parsed = responseDeserializer.deserialize(response.body());
        } catch (RuntimeException e) {
            metrics.observeHistogram("sma_path_computation_duration_seconds",
                    Map.of("outcome", pathOutcome(e)), elapsedSeconds(startedAt));
            throw e;
        }
        if (!parsed.completed()) {
            metrics.increment("sma_path_computation_failure_total");
            metrics.observeHistogram("sma_path_computation_duration_seconds",
                    Map.of("outcome", "invalid_response"), elapsedSeconds(startedAt));
            throw new IllegalStateException("El calculo de camino no finalizo: " + parsed.status());
        }
        final Instant now = Instant.now();

        /*The final calculated path corresponds to a DTO comprised of the graph name for the calculation, the CalculatedPathKey
        * identifier DTO containing the source, destination and algorithm used as well as the ERO DTO and the Path Description
        * List of Path Hops coming from the PathComputationResponse DTO. */
        final CalculatedPath path = new CalculatedPath(
                config.pathComputationGraphName(),
                key.sourceGraphNodeId(),
                key.destinationGraphNodeId(),
                key.bandwidthBytesPerSecond(),
                key.classType(),
                key.algorithm(),
                parsed.pathDescriptions(), /*From the PathComputationResponse, internally a List<PathHop>(s)*/
                eroTranslator.translate(parsed, direction.destination().routerId()), /*Represented as a List<EroSubobject>
                objects within the CalculatedPath DTO*/
                parsed.computedTeMetric(),
                now,
                now.plus(config.pathCacheTtl()));

        /*We register the corresponding computed path based on the CalculatedPathKey which in turn contains the directionality
        * imbued through graph vertex IDs*/
        pathRegistry.put(key, path);
        metrics.increment("sma_path_computation_success_total");
        metrics.observeHistogram("sma_path_computation_duration_seconds",
                Map.of("outcome", "success"), elapsedSeconds(startedAt));
        LOG.info(
                "path_computation_completed",
                "compute",
                "Se calculo y registro un camino restringido.",
                StructuredLogger.fields(
                        "status", parsed.status(),
                        "hop_count", parsed.pathDescriptions().size(),
                        "ero", path.eroSubobjects(),
                        "computed_te_metric", path.computedTeMetric(),
                        "expires_at", path.expiresAt()));
        return path;
    }

    /**
     * <p>Invokes the constrained-path RPC until a confirmed response is received or
     * the configured attempt budget is exhausted. Attempts have no intentional delay.</p>
     *
     * <p>The request proceeds as follows:</p>
     * <ol>
     *   <li>Serialize and invoke the RPC on each attempt, retaining runtime failures for retry.</li>
     *   <li>Classify responses: return confirmed success or immediately reject hard failure.</li>
     *   <li>Retry unconfirmed responses; on exhaustion, record failure and throw with the last cause.</li>
     * </ol>
     *
     * @param request constrained-path RPC parameters
     * @return confirmed HTTP response for deserialization
     * @throws IllegalStateException if a hard failure occurs or the attempt budget is exhausted
     */
    private HttpResponse<String> computeWithRetry(final CalculatedPathRequest request) {
        RuntimeException lastFailure = null;
        String lastOutcome = "retry_exhausted";

        /*Attempt to perform the same call to the OdlRests operations client for a given
        * amount of max attempts, five in the default case, or as configured in the environment variables of the application.
        * This in turn attempts to gather a response through the RPC operation towards the BGPCEP module, specifically the
        * algorithm module that implements the CSPF algorithms.*/
        for (int attempt = 1; attempt <= config.topologyDiscoveryMaxAttempts(); attempt++) {
            LOG.debug(
                    "path_computation_attempt_started",
                    "computeWithRetry",
                    "Se inicio un intento de calculo de camino.",
                    StructuredLogger.fields(
                            "attempt", attempt,
                            "maximum_attempts", config.topologyDiscoveryMaxAttempts()));
            final HttpResponse<String> response;
            try {
                response = operationsClient.computeConstrainedPath(requestSerializer.serialize(request));
            } catch (RuntimeException e) {
                lastFailure = e;
                lastOutcome = "transport_error";
                LOG.warn(
                        "path_computation_attempt_transport_failure",
                        "computeWithRetry",
                        "Fallo el transporte durante un intento de calculo de camino.",
                        StructuredLogger.fields("attempt", attempt),
                        e);
                continue;
            }
            /*If a response was received and no exceptionw as raised then we can attempt to classify the response,
            * using said result to know if we can return, continue or exit immediately due to a hard failure*/
            final OdlCallOutcome outcome = outcomeClassifier.classify(response);
            LOG.debug(
                    "path_computation_attempt_classified",
                    "computeWithRetry",
                    "Se clasifico la respuesta del intento de calculo de camino.",
                    StructuredLogger.fields(
                            "attempt", attempt,
                            "http_status", response.statusCode(),
                            "outcome", outcome.type(),
                            "failure_reason", outcome.failureReason()));
            if (outcome.type() == OdlCallOutcomeType.CONFIRMED_SUCCESS) {
                /*If operation returned something akin to a 200 with a body we return*/
                return response;
            }
            if (outcome.type() == OdlCallOutcomeType.HARD_FAILURE) {
                metrics.increment("sma_path_computation_failure_total");
                throw new PathComputationFailedException("El calculo de camino fallo: " + outcome.failureReason(),
                        "hard_failure", null);
            }
            lastOutcome = "unconfirmed_response";
            lastFailure = new IllegalStateException(
                    "El resultado del calculo de camino es ambiguo: " + outcome.failureReason());
        }
        /*If for some reason we were unable to calculate a path we simply raise an exception specific to this application
        * about the computing of the path being unable to result in some explicit path.*/
        metrics.increment("sma_path_computation_failure_total");
        throw new PathComputationFailedException(
                "El calculo de camino agoto el presupuesto de reintentos", lastOutcome, lastFailure);
    }

    /**
     * <p>Measures elapsed time using the monotonic nanosecond clock.</p>
     * <p>The calculation has one step:</p>
     * <ol><li>Subtract the start value and convert nanoseconds to seconds.</li></ol>
     * @param startedAt start value from {@link System#nanoTime()}
     * @return elapsed seconds
     */
    private static double elapsedSeconds(final long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000_000.0d;
    }

    /**
     * <p>Chooses the histogram outcome label for a failed computation.</p>
     * <p>The selection has one step:</p>
     * <ol><li>Use the typed retry outcome when available, or {@code invalid_response} otherwise.</li></ol>
     * @param failure computation or deserialization failure
     * @return outcome label for the duration histogram
     */
    private static String pathOutcome(final RuntimeException failure) {
        return failure instanceof PathComputationFailedException typed ? typed.outcome() : "invalid_response";
    }

    /** <p>Associates a retry failure with its metrics outcome label.</p> */
    private static final class PathComputationFailedException extends IllegalStateException {
        /** <p>Serialization version for the exception type.</p> */
        private static final long serialVersionUID = 1L;
        /** <p>Outcome label recorded by the path computation duration histogram.</p> */
        private final String outcome;

        /**
         * <p>Constructs a failure with its metrics label and optional cause.</p>
         * <p>Construction proceeds as follows:</p>
         * <ol><li>Initialize the parent exception and retain the outcome label.</li></ol>
         * @param message failure description
         * @param outcome metrics label
         * @param cause underlying failure, if any
         */
        PathComputationFailedException(final String message, final String outcome, final Throwable cause) {
            super(message, cause);
            this.outcome = outcome;
        }

        /**
         * <p>Returns the label describing this computation failure.</p>
         * <p>The accessor has one step:</p>
         * <ol><li>Return the stored outcome.</li></ol>
         * @return metrics outcome label
         */
        String outcome() {
            return outcome;
        }
    }
}
