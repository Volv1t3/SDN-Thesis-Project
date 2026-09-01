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
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final Runnable stateChanged;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final Object refreshLock = new Object();
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile Instant lastSuccessfulRefresh = Instant.EPOCH;
    private volatile Instant lastRefreshAttempt = Instant.EPOCH;
    private volatile String lastFailure = "Topology has not been discovered yet";
    private volatile boolean refreshInProgress;
    private volatile long successfulRefreshCount;
    private volatile long failedRefreshCount;
    private volatile boolean ttlExpiredRecorded;

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
            final SdnMplsMlMetrics metrics,
            final Runnable stateChanged) {
        this.config = config;
        this.client = client;
        this.deserializer = deserializer;
        this.registry = registry;
        this.outcomeClassifier = outcomeClassifier;
        this.metrics = metrics;
        this.stateChanged = stateChanged;
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
        if (!started.compareAndSet(false, true)) {
            return;
        }
        final long intervalMillis = Math.max(1L, config.topologyCacheTtl().toMillis());
        executor.scheduleWithFixedDelay(
                this::refreshSafely,
                intervalMillis,
                intervalMillis,
                TimeUnit.MILLISECONDS);
        LOG.info(
                "topology_refresh_scheduled",
                "start",
                "Se programo la actualizacion periodica de la topologia BGP-LS.",
                StructuredLogger.fields("interval_millis", intervalMillis));
    }

    /** Records that the mandatory startup discovery produced the currently registered topology. */
    public void markInitialDiscoverySuccessful() {
        lastSuccessfulRefresh = Instant.now();
        lastRefreshAttempt = lastSuccessfulRefresh;
        lastFailure = "";
        ttlExpiredRecorded = false;
        successfulRefreshCount++;
        updateFreshnessGauges();
        stateChanged.run();
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
        final boolean stale = !lastSuccessfulRefresh.plus(config.topologyCacheTtl()).isAfter(Instant.now());
        LOG.trace(
                "topology_staleness_checked",
                "staleBeyondThreshold",
                "Se comprobo la vigencia del ultimo registro BGP-LS.",
                StructuredLogger.fields("stale", stale, "last_successful_refresh", lastSuccessfulRefresh));
        return stale;
    }

    /**
     * Ensures that a PacketIn never uses BGP-LS state that has reached its configured TTL.
     * A stale entry triggers one synchronous, single-flight RESTCONF refresh before processing is rejected.
     *
     * @return {@code true} when the registry is fresh or a refresh succeeds
     */
    public boolean ensureFresh() {
        if (!staleBeyondThreshold()) {
            updateFreshnessGauges();
            return true;
        }
        recordTtlExpiredOnce();
        metrics.incrementCounter("sma_bgpls_topology_refresh_on_demand_total",
                Map.of("reason", "stale_before_packet"));
        LOG.info(
                "topology_refresh_on_demand_started",
                "ensureFresh",
                "La topologia BGP-LS vencio; se solicitara una actualizacion antes de procesar el paquete.",
                StructuredLogger.fields("last_successful_refresh", lastSuccessfulRefresh));
        return refresh("on_demand_stale");
    }

    /** Returns a consistent snapshot used by the operational RESTCONF state publisher. */
    public TopologyRefreshStatus status() {
        final Instant freshUntil = lastSuccessfulRefresh.plus(config.topologyCacheTtl());
        updateFreshnessGauges();
        return new TopologyRefreshStatus(
                lastSuccessfulRefresh,
                lastRefreshAttempt,
                freshUntil,
                lastFailure,
                !staleBeyondThreshold(),
                refreshInProgress,
                successfulRefreshCount,
                failedRefreshCount);
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
        refresh("periodic");
    }

    private boolean refresh(final String trigger) {
        synchronized (refreshLock) {
            if (refreshInProgress) {
                metrics.increment("sma_bgpls_topology_refresh_deduplicated_total");
                return false;
            }
            if (!"periodic".equals(trigger) && !staleBeyondThreshold()) {
                updateFreshnessGauges();
                return true;
            }
            refreshInProgress = true;
            lastRefreshAttempt = Instant.now();
            updateFreshnessGauges();
            stateChanged.run();
        }
        try (LogContext ignored = LogContext.open(Map.of("refresh_id", UUID.randomUUID().toString()))) {
            LOG.debug(
                    "topology_refresh_started",
                    "refresh",
                    "Se inicio una actualizacion de BGP-LS.",
                    StructuredLogger.fields("topology_id", config.bgplsTopologyId(), "trigger", trigger));
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
                lastFailure = outcome.failureReason() == null
                        ? "Unconfirmed topology response" : outcome.failureReason();
                failedRefreshCount++;
                return false;
            }

            final List<BgpLsTopologyNode> nodes = deserializer.deserialize(response.body());
            final BgpLsNodeRegistry candidate = new BgpLsNodeRegistry();
            candidate.replaceAll(nodes);
            candidate.resolveGraphNodeIdByRouterId(config.headend().routerId());
            candidate.resolveGraphNodeIdByRouterId(config.tailend().routerId());
            registry.replaceAll(nodes);
            lastSuccessfulRefresh = Instant.now();
            lastFailure = "";
            ttlExpiredRecorded = false;
            successfulRefreshCount++;
            metrics.increment("sma_odl_topology_discovery_success_total");
            updateFreshnessGauges();
            LOG.info(
                    "topology_refresh_completed",
                    "refreshSafely",
                    "Se actualizo correctamente el registro BGP-LS.",
                    StructuredLogger.fields(
                            "node_count", nodes.size(),
                            "refreshed_at", lastSuccessfulRefresh));
            return true;
        } catch (RuntimeException e) {
            metrics.increment("sma_odl_topology_discovery_failure_total");
            lastFailure = message(e);
            failedRefreshCount++;
            updateFreshnessGauges();
            LOG.warn(
                    "topology_refresh_failed",
                    "refreshSafely",
                    "Fallo la actualizacion BGP-LS y se conservo el ultimo registro valido.",
                    StructuredLogger.fields("last_successful_refresh", lastSuccessfulRefresh),
                    e);
            return false;
        } finally {
            refreshInProgress = false;
            updateFreshnessGauges();
            stateChanged.run();
        }
    }

    private static String message(final RuntimeException error) {
        final String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return message.length() <= 512 ? message : message.substring(0, 512);
    }

    private void recordTtlExpiredOnce() {
        if (!ttlExpiredRecorded) {
            ttlExpiredRecorded = true;
            metrics.increment("sma_bgpls_topology_ttl_expired_total");
        }
    }

    private void updateFreshnessGauges() {
        final Instant freshUntil = lastSuccessfulRefresh.plus(config.topologyCacheTtl());
        metrics.setGauge("sma_bgpls_topology_fresh", freshUntil.isAfter(Instant.now()) ? 1L : 0L);
        metrics.setGauge("sma_bgpls_topology_refresh_in_progress", refreshInProgress ? 1L : 0L);
        metrics.setGauge("sma_bgpls_topology_last_success_epoch_seconds", lastSuccessfulRefresh.getEpochSecond());
        metrics.setGauge("sma_bgpls_topology_last_attempt_epoch_seconds", lastRefreshAttempt.getEpochSecond());
        metrics.setGauge("sma_bgpls_topology_fresh_until_epoch_seconds", freshUntil.getEpochSecond());
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
