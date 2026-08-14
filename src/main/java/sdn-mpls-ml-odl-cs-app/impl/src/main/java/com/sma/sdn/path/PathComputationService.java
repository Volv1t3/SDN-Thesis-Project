/*
 * Copyright (c) 2026 Santiago Arellano and others.
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
import com.sma.sdn.model.CalculatedPath;
import com.sma.sdn.model.CalculatedPathKey;
import com.sma.sdn.model.CalculatedPathRequest;
import com.sma.sdn.model.PathComputationResponse;
import com.sma.sdn.model.PathConstraints;
import com.sma.sdn.model.OdlCallOutcome;
import com.sma.sdn.model.OdlCallOutcomeType;
import com.sma.sdn.model.TunnelDirection;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.registry.BgpLsNodeRegistry;
import com.sma.sdn.registry.CalculatedPathRegistry;
import com.sma.sdn.serialization.xml.PathComputationRequestXmlSerializer;
import com.sma.sdn.serialization.xml.PathComputationResponseXmlDeserializer;
import com.sma.sdn.topology.BandwidthTranslator;
import java.net.http.HttpResponse;
import java.time.Instant;

/**
 * Define la clase {@code PathComputationService} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class PathComputationService {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(PathComputationService.class);
    private static final int LAB_CLASS_TYPE = 0;
    private static final String LAB_ALGORITHM = "cspf";
    private final AppConfig config;
    private final BgpLsNodeRegistry nodeRegistry;
    private final CalculatedPathRegistry pathRegistry;
    private final OdlOperationsClient operationsClient;
    private final PathComputationRequestXmlSerializer requestSerializer;
    private final PathComputationResponseXmlDeserializer responseDeserializer;
    private final CalculatedPathToEroTranslator eroTranslator;
    private final PathComputationOutcomeClassifier outcomeClassifier;
    private final SdnMplsMlMetrics metrics;

    /**
     * Ejecuta la operacion {@code PathComputationService} dentro del componente correspondiente.
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
     * @param nodeRegistry valor requerido para ejecutar esta operacion
     *
     * @param pathRegistry valor requerido para ejecutar esta operacion
     *
     * @param operationsClient valor requerido para ejecutar esta operacion
     *
     * @param requestSerializer valor requerido para ejecutar esta operacion
     *
     * @param responseDeserializer valor requerido para ejecutar esta operacion
     *
     * @param eroTranslator valor requerido para ejecutar esta operacion
     *
     * @param outcomeClassifier valor requerido para ejecutar esta operacion
     *
     * @param metrics valor requerido para ejecutar esta operacion
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
     * Obtiene una ruta calculada desde cache o solicita una nueva ruta CSPF a ODL.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param direction valor requerido para ejecutar esta operacion
     *
     * @param constraints valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    public CalculatedPath computeOrGetCached(final TunnelDirection direction, final PathConstraints constraints) {
        final long bandwidthBytesPerSecond = BandwidthTranslator.kbpsToBytesPerSecond(
                constraints.requestedBandwidthKbps());
        final long sourceGraphNodeId = nodeRegistry.resolveGraphNodeIdByRouterId(direction.source().routerId());
        final long destinationGraphNodeId = nodeRegistry.resolveGraphNodeIdByRouterId(
                direction.destination().routerId());
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
        return pathRegistry.findValid(key)
                .map(path -> {
                    metrics.increment("sma_path_cache_hit_total");
                    LOG.debug(
                            "path_cache_hit",
                            "computeOrGetCached",
                            "Se reutilizo un camino calculado vigente desde cache.",
                            StructuredLogger.fields(
                                    "ero", path.eroSubobjects(),
                                    "computed_te_metric", path.computedTeMetric(),
                                    "expires_at", path.expiresAt()));
                    return path;
                })
                .orElseGet(() -> compute(direction, key));
    }

    /**
     * Ejecuta la operacion {@code compute} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param direction valor requerido para ejecutar esta operacion
     *
     * @param key valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    private CalculatedPath compute(final TunnelDirection direction, final CalculatedPathKey key) {
        metrics.increment("sma_path_cache_miss_total");
        metrics.increment("sma_path_computation_request_total");
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
        final HttpResponse<String> response = computeWithRetry(request);
        final PathComputationResponse parsed = responseDeserializer.deserialize(response.body());
        if (!parsed.completed()) {
            metrics.increment("sma_path_computation_failure_total");
            throw new IllegalStateException("El calculo de camino no finalizo: " + parsed.status());
        }
        final Instant now = Instant.now();
        final CalculatedPath path = new CalculatedPath(
                config.pathComputationGraphName(),
                key.sourceGraphNodeId(),
                key.destinationGraphNodeId(),
                key.bandwidthBytesPerSecond(),
                key.classType(),
                key.algorithm(),
                parsed.pathDescriptions(),
                eroTranslator.translate(parsed, direction.destination().routerId()),
                parsed.computedTeMetric(),
                now,
                now.plus(config.pathCacheTtl()));
        pathRegistry.put(key, path);
        metrics.increment("sma_path_computation_success_total");
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
     * Ejecuta la operacion {@code computeWithRetry} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param request valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    private HttpResponse<String> computeWithRetry(final CalculatedPathRequest request) {
        RuntimeException lastFailure = null;
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
                LOG.warn(
                        "path_computation_attempt_transport_failure",
                        "computeWithRetry",
                        "Fallo el transporte durante un intento de calculo de camino.",
                        StructuredLogger.fields("attempt", attempt),
                        e);
                continue;
            }
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
                return response;
            }
            if (outcome.type() == OdlCallOutcomeType.HARD_FAILURE) {
                metrics.increment("sma_path_computation_failure_total");
                throw new IllegalStateException("El calculo de camino fallo: " + outcome.failureReason());
            }
            lastFailure = new IllegalStateException(
                    "El resultado del calculo de camino es ambiguo: " + outcome.failureReason());
        }
        metrics.increment("sma_path_computation_failure_total");
        throw new IllegalStateException("El calculo de camino agoto el presupuesto de reintentos", lastFailure);
    }
}
