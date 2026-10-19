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
 * <p>Maintains the freshness of the shared BGP-LS registry after startup discovery. A scheduled
 * task and packet-triggered checks may request refreshes; a lock and in-progress flag prevent
 * overlapping refresh work. Failures before registry replacement retain the previous snapshot.</p>
 */
public final class TopologyRefreshService implements AutoCloseable {
    /** <p>Logger for refresh scheduling, freshness, and outcomes.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(TopologyRefreshService.class);
    /** <p>Configuration containing topology ID and cache TTL.</p> */
    private final AppConfig config;
    /** <p>RESTCONF client used to retrieve BGP-LS topology.</p> */
    private final OdlRestconfDataClient client;
    /** <p>Parser converting topology XML into BGP-LS nodes.</p> */
    private final BgpLsTopologyXmlDeserializer deserializer;
    /** <p>Shared destination for validated topology snapshots.</p> */
    private final BgpLsNodeRegistry registry;
    /** <p>Classifier for RESTCONF response outcomes.</p> */
    private final TopologyDiscoveryOutcomeClassifier outcomeClassifier;
    /** <p>Collector for refresh counters and freshness gauges.</p> */
    private final SdnMplsMlMetrics metrics;
    /** <p>Callback invoked when observable refresh state changes.</p> */
    private final Runnable stateChanged;
    /** <p>Single-thread scheduler for periodic refresh.</p> */
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    /** <p>Monitor guarding refresh admission and the in-progress check.</p> */
    private final Object refreshLock = new Object();
    /** <p>Atomic flag used to schedule the periodic task only once.</p> */
    private final AtomicBoolean started = new AtomicBoolean();
    /** <p>Time of the most recent successful discovery or refresh.</p> */
    private volatile Instant lastSuccessfulRefresh = Instant.EPOCH;
    /** <p>Time at which the latest discovery or refresh attempt began.</p> */
    private volatile Instant lastRefreshAttempt = Instant.EPOCH;
    /** <p>Description of the last refresh failure, or an empty string after success.</p> */
    private volatile String lastFailure = "Topology has not been discovered yet";
    /** <p>Visible indicator that a refresh has been admitted.</p> */
    private volatile boolean refreshInProgress;
    /** <p>Count of successful initial discovery and refresh operations.</p> */
    private volatile long successfulRefreshCount;
    /** <p>Count of failed refresh operations.</p> */
    private volatile long failedRefreshCount;
    /** <p>Flag suppressing repeated expiration counters until the next success.</p> */
    private volatile boolean ttlExpiredRecorded;

    /**
     * <p>Construct the topology refresh service from supplied collaborators.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Retain the configuration, client, parser, registry, classifier, metrics, and state-change callback.</li>
     * </ol>
     *
     * @param config topology ID and freshness settings
     * @param client RESTCONF topology data client
     * @param deserializer BGP-LS topology parser
     * @param registry destination for validated nodes
     * @param outcomeClassifier HTTP response classifier
     * @param metrics refresh metrics collector
     * @param stateChanged callback for operational state publication
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
        this.stateChanged = stateChanged; /*This receives the Publish operational State which means that whenever
        there is a change upon the corresponding gauges or the state of the topology there will be a new published operationa;
        state that will be stored to be retrieeved through RESTCONF*/
    }

    /**
     * <p>Schedule periodic topology refresh once using a fixed delay based on the topology TTL.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Atomically claim the start flag, returning if already started.</li>
     * <li>Schedule the refresh task with the TTL in milliseconds, clamped to at least one millisecond, and log the
     * interval.</li>
     * </ol>
     *
     * @throws java.util.concurrent.RejectedExecutionException if the executor cannot accept the task
     */
    public void start() {
        /*In the event of a duplicated call onto the start mechanism, we compare the state of the atomic started variable
        * through an atomic compare and set and return to avoid reprocessing, this is a kind of locking mechanism taht does
        * not allow the state to be used by two different start threads*/
        if (!started.compareAndSet(false, true)) {
            return;
        }
        final long intervalMillis = Math.max(1L, config.topologyCacheTtl().toMillis());

        /*We internally schedule a thread that will be executed at a regular interval to refresh the topology using the
        refresh safely function*/
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

    /**
     * <p>Record successful startup discovery as a fresh topology snapshot.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     * <li>Set the success and attempt timestamps to the current time, clear failure and expiry state, and
     * increment the success count.</li>
     *   <li>Update gauges and invoke the state-change callback.</li>
     * </ol>
     */
    public void markInitialDiscoverySuccessful() {
        /*Local updates*/
        this.lastSuccessfulRefresh = Instant.now();
        this.lastRefreshAttempt = lastSuccessfulRefresh;
        this.lastFailure = "";
        this.ttlExpiredRecorded = false;
        this.successfulRefreshCount++;

        /*Increment the corresponding metrics endpoint gauges based on the values calculated here */
        this.updateFreshnessGauges();

        /*Perform the received callback for the state-changed operation which in turn boils down to a new published state
        * to the data stores of MDSAL such that it is queryable through RESTCONF*/
        this.stateChanged.run();
    }

    /**
     * <p>Determine whether the last successful discovery or refresh has reached its configured TTL.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Compare the success timestamp plus TTL with the current time.</li>
     *   <li>Log and return the resulting stale flag.</li>
     * </ol>
     *
     * @return true if the freshness deadline is not after the current time
     */
    public boolean staleBeyondThreshold() {
        /*In this case, the operation we are trying to perform here is a boolean operation to determine if the expiration
        * ttl for the cache has been exceeded by virtue of the value from the last refresh and the ttl being before the current
        * instant. if this happens (due to the negation) then we know we need to perform a topology refresh before anything else*/
        final boolean stale = !this.lastSuccessfulRefresh
                .plus(config.topologyCacheTtl())
                .isAfter(Instant.now());
        LOG.trace(
                "topology_staleness_checked",
                "staleBeyondThreshold",
                "Se comprobo la vigencia del ultimo registro BGP-LS.",
                StructuredLogger.fields("stale", stale, "last_successful_refresh", lastSuccessfulRefresh));
        return stale;
    }

    /**
     * <p>Check freshness before packet processing and request a synchronous refresh if the cache has expired.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Return true after updating gauges when the topology is fresh.</li>
     *   <li>Otherwise record expiration, increment the on-demand counter, and invoke refresh.</li>
     * </ol>
     *
     * @return true if already fresh or the requested refresh succeeds; false when refresh fails or another refresh
     * is in progress
     */
    public boolean ensureFresh() {
        /** This operation is a life cycle aware process that allows for higher level mechanisms like the
         * {@link com.sma.sdn.SdnMplsMlWorkflowService} service for example to review if the current topology is still fresh before performing
         * an operation like packet pcoressing*/
        if (!staleBeyondThreshold())  { //In the event that the staleness has not been reached we update the gauges but return
            // true diretly
            updateFreshnessGauges();
            return true;
        }

        /*If not we are going to record the expiration and return the boolean result of the refresh operation*/
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

    /**
     * <p>Build an observable refresh status from the current volatile fields. The fields are read separately and
     * are not an atomic snapshot under concurrent updates.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Calculate the freshness deadline and update gauges.</li>
     *   <li>Read timestamps, failure, freshness, in-progress flag, and counters into a status record.</li>
     * </ol>
     *
     * @return current observed refresh status
     */
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
     * <p>Run one periodic refresh attempt from the scheduler.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Invoke the refresh operation with the periodic trigger and discard its boolean result.</li>
     * </ol>
     */
    private void refreshSafely() {
        refresh("periodic");
    }

    /**
     * <p>Attempt a BGP-LS refresh while preventing overlapping refresh operations. A non-successful response or
     * runtime failure during retrieval and validation retains the previous registry.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     * <li>Under the admission lock, reject an in-progress refresh or skip a now-fresh on-demand request; otherwise
     * mark the attempt in progress and publish state.</li>
     *   <li>Fetch and classify the response, returning failure for an unconfirmed outcome.</li>
     * <li>Parse nodes into a temporary registry and require both configured endpoint routers before replacing the
     * shared registry.</li>
     *   <li>Update success state and metrics, or record a runtime failure and return false.</li>
     *   <li>Clear the in-progress flag, update gauges, and invoke the state-change callback in the finally block.</li>
     * </ol>
     *
     * @param trigger reason for the attempt; {@code periodic} bypasses the on-demand freshness recheck
     * @return true on successful refresh or an already-fresh on-demand check; false on rejection or refresh failure
     */
    private boolean refresh(final String trigger) {
        // Admit one refresh at a time and recheck on-demand freshness after acquiring the lock.
        synchronized (refreshLock) {
            if (refreshInProgress) {
                /*In the admission lock, if there is alreayd a refresh in progress from another thread or from an on demand
                * refresh then any other process is ignored and returns immediately false.*/
                metrics.increment("sma_bgpls_topology_refresh_deduplicated_total");
                return false;
            }
            /*In the event that the trigger is not periodic, i.e. on demand for example like could be from an ensure fresh call, then if
            * the data is not stale then we simply update the gauges and return true, indicating that the refresh operation
            * has been done. Otherwise we signal a change of state*/
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

            /*Request and classification of the outcome of a BGPLS get topology call*/
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


            /*If the request did not fail we deserailize the nodes like thwe do for the initial topology discovery for BGP
            * LS and we validate the data that has been gathered before we replace the registry*/
            final List<BgpLsTopologyNode> nodes = deserializer.deserialize(response.body());
            // Validate both required endpoints against a temporary snapshot before replacing shared state.
            final BgpLsNodeRegistry candidate = new BgpLsNodeRegistry();
            candidate.replaceAll(nodes);
            candidate.resolveGraphNodeIdByRouterId(config.headend().routerId());
            candidate.resolveGraphNodeIdByRouterId(config.tailend().routerId());

            /*Upon validating that the graph node IDs are registered we can update the internal state */
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

    /**
     * <p>Create a bounded description for a refresh exception.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Use the exception message or its simple class name if no message exists.</li>
     *   <li>Return at most the first 512 characters.</li>
     * </ol>
     *
     * @param error refresh exception
     * @return bounded diagnostic text
     */
    private static String message(final RuntimeException error) {
        final String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return message.length() <= 512 ? message : message.substring(0, 512);
    }

    /**
     * <p>Record a TTL expiration once until a subsequent successful refresh resets the flag.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Check the expiration flag.</li>
     *   <li>Set it and increment the TTL expiration counter if it was not set.</li>
     * </ol>
     */
    private void recordTtlExpiredOnce() {
        if (!ttlExpiredRecorded) {
            ttlExpiredRecorded = true;
            metrics.increment("sma_bgpls_topology_ttl_expired_total");
        }
    }

    /**
     * <p>Publish the current freshness and refresh timing values to metrics.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Calculate the freshness deadline from the last success and configured TTL.</li>
     *   <li>Set freshness, in-progress, success time, attempt time, and deadline gauges.</li>
     * </ol>
     */
    private void updateFreshnessGauges() {
        final Instant freshUntil = lastSuccessfulRefresh.plus(config.topologyCacheTtl());
        metrics.setGauge("sma_bgpls_topology_fresh", freshUntil.isAfter(Instant.now()) ? 1L : 0L);
        metrics.setGauge("sma_bgpls_topology_refresh_in_progress", refreshInProgress ? 1L : 0L);
        metrics.setGauge("sma_bgpls_topology_last_success_epoch_seconds", lastSuccessfulRefresh.getEpochSecond());
        metrics.setGauge("sma_bgpls_topology_last_attempt_epoch_seconds", lastRefreshAttempt.getEpochSecond());
        metrics.setGauge("sma_bgpls_topology_fresh_until_epoch_seconds", freshUntil.getEpochSecond());
    }

    /**
     * <p>Request shutdown of the periodic refresh executor.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Call {@code shutdownNow()} on the executor.</li>
     *   <li>Log the last successful refresh time.</li>
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
