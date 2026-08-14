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
import com.sma.sdn.observability.LogContext;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.registry.BgpLsNodeRegistry;
import com.sma.sdn.serialization.xml.BgpLsTopologyXmlDeserializer;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Define la clase {@code TopologyRefreshService} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class TopologyRefreshService implements AutoCloseable {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(TopologyRefreshService.class);
    private final AppConfig config;
    private final OdlRestconfDataClient client;
    private final BgpLsTopologyXmlDeserializer deserializer;
    private final BgpLsNodeRegistry registry;
    private final TopologyDiscoveryOutcomeClassifier outcomeClassifier;
    private final SdnMplsMlMetrics metrics;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private volatile Instant lastSuccessfulRefresh = Instant.now();

    /**
     * Ejecuta la operacion {@code TopologyRefreshService} dentro del componente correspondiente.
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
     * @param deserializer valor requerido para ejecutar esta operacion
     *
     * @param registry valor requerido para ejecutar esta operacion
     *
     * @param outcomeClassifier valor requerido para ejecutar esta operacion
     *
     * @param metrics valor requerido para ejecutar esta operacion
     */
    public TopologyRefreshService(
            final AppConfig config,
            final OdlRestconfDataClient client,
            final BgpLsTopologyXmlDeserializer deserializer,
            final BgpLsNodeRegistry registry,
            final TopologyDiscoveryOutcomeClassifier outcomeClassifier,
            final SdnMplsMlMetrics metrics) {
        this.config = config;
        this.client = client;
        this.deserializer = deserializer;
        this.registry = registry;
        this.outcomeClassifier = outcomeClassifier;
        this.metrics = metrics;
    }

    /**
     * Inicia la tarea periodica asociada al servicio.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     */
    public void start() {
        executor.scheduleWithFixedDelay(
                this::refreshSafely,
                config.topologyCacheTtl().toSeconds(),
                config.topologyCacheTtl().toSeconds(),
                TimeUnit.SECONDS);
        LOG.info(
                "topology_refresh_scheduled",
                "start",
                "Se programo la actualizacion periodica de la topologia BGP-LS.",
                StructuredLogger.fields("interval_seconds", config.topologyCacheTtl().toSeconds()));
    }

    /**
     * Ejecuta la operacion {@code staleBeyondThreshold} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public boolean staleBeyondThreshold() {
        final boolean stale = lastSuccessfulRefresh.plus(
                config.topologyCacheTtl().multipliedBy(2)).isBefore(Instant.now());
        LOG.trace(
                "topology_staleness_checked",
                "staleBeyondThreshold",
                "Se comprobo la vigencia del ultimo registro BGP-LS.",
                StructuredLogger.fields("stale", stale, "last_successful_refresh", lastSuccessfulRefresh));
        return stale;
    }

    /**
     * Ejecuta la operacion {@code refreshSafely} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     */
    private void refreshSafely() {
        try (LogContext ignored = LogContext.open(Map.of("refresh_id", UUID.randomUUID().toString()))) {
            LOG.debug(
                    "topology_refresh_started",
                    "refreshSafely",
                    "Se inicio una actualizacion periodica de BGP-LS.",
                    StructuredLogger.fields("topology_id", config.bgplsTopologyId()));
            final HttpResponse<String> response = client.getBgpLsTopology(config.bgplsTopologyId());
            final OdlCallOutcome outcome = outcomeClassifier.classify(response);
            if (outcome.type() != OdlCallOutcomeType.CONFIRMED_SUCCESS) {
                metrics.increment("sma_odl_topology_discovery_failure_total");
                LOG.warn(
                        "topology_refresh_rejected",
                        "refreshSafely",
                        "Se omitio la actualizacion BGP-LS por un resultado no confirmado.",
                        StructuredLogger.fields(
                                "outcome", outcome.type(),
                                "http_status", outcome.httpStatus(),
                                "failure_reason", outcome.failureReason()),
                        null);
                return;
            }

            final List<BgpLsTopologyNode> nodes = deserializer.deserialize(response.body());
            final BgpLsNodeRegistry candidate = new BgpLsNodeRegistry();
            candidate.replaceAll(nodes);
            candidate.resolveGraphNodeIdByRouterId(config.headend().routerId());
            candidate.resolveGraphNodeIdByRouterId(config.tailend().routerId());
            registry.replaceAll(nodes);
            lastSuccessfulRefresh = Instant.now();
            metrics.increment("sma_odl_topology_discovery_success_total");
            LOG.info(
                    "topology_refresh_completed",
                    "refreshSafely",
                    "Se actualizo correctamente el registro BGP-LS.",
                    StructuredLogger.fields(
                            "node_count", nodes.size(),
                            "refreshed_at", lastSuccessfulRefresh));
        } catch (RuntimeException e) {
            metrics.increment("sma_odl_topology_discovery_failure_total");
            LOG.warn(
                    "topology_refresh_failed",
                    "refreshSafely",
                    "Fallo la actualizacion BGP-LS y se conservo el ultimo registro valido.",
                    StructuredLogger.fields("last_successful_refresh", lastSuccessfulRefresh),
                    e);
        }
    }

    /**
     * Libera los recursos registrados y detiene las tareas auxiliares del componente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     */
    @Override
    public void close() {
        executor.shutdownNow();
        LOG.info(
                "topology_refresh_stopped",
                "close",
                "Se detuvo la tarea periodica de actualizacion BGP-LS.",
                StructuredLogger.fields("last_successful_refresh", lastSuccessfulRefresh));
    }
}
