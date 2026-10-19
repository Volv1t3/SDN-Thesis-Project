/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sma.sdn.SdnMplsMlWorkflowService;
import com.sma.sdn.classification.ClassificationService;
import com.sma.sdn.config.AppConfig;
import com.sma.sdn.config.TunnelCreationMode;
import com.sma.sdn.http.ClassifierRestClient;
import com.sma.sdn.http.HttpClientFactory;
import com.sma.sdn.http.OdlOperationsClient;
import com.sma.sdn.http.OdlRestconfDataClient;
import com.sma.sdn.http.PathComputationOutcomeClassifier;
import com.sma.sdn.http.TopologyDiscoveryOutcomeClassifier;
import com.sma.sdn.metrics.PrometheusMetricsServlet;
import com.sma.sdn.metrics.SdnMplsMlMetrics;
import com.sma.sdn.observability.LogContext;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.openflow.OpenflowBootstrapService;
import com.sma.sdn.openflow.OpenflowBootstrapVerifier;
import com.sma.sdn.openflow.OpenflowFlowProvisioningService;
import com.sma.sdn.openflow.OpenflowFlowXmlSerializer;
import com.sma.sdn.openflow.OpenflowInventoryService;
import com.sma.sdn.openflow.OpenflowInventoryXmlDeserializer;
import com.sma.sdn.openflow.OpenflowSwitchRegistry;
import com.sma.sdn.operational.ControllerOperationalStatePublisher;
import com.sma.sdn.packet.PacketInFeatureExtractor;
import com.sma.sdn.path.CalculatedPathToEroTranslator;
import com.sma.sdn.path.PathComputationService;
import com.sma.sdn.policy.PairPolicyConsensusService;
import com.sma.sdn.policy.PairPolicyCoordinator;
import com.sma.sdn.policy.PairPolicyHashService;
import com.sma.sdn.policy.PolicyPreemptionEvaluator;
import com.sma.sdn.policy.ServiceKeyResolver;
import com.sma.sdn.registry.ActivePairPolicyRegistry;
import com.sma.sdn.registry.BgpLsNodeRegistry;
import com.sma.sdn.registry.CalculatedPathRegistry;
import com.sma.sdn.registry.ClassificationRegistrar;
import com.sma.sdn.registry.DirectionalClassificationEvidenceRegistry;
import com.sma.sdn.registry.DirectionRegistry;
import com.sma.sdn.registry.DelegatedLspRegistry;
import com.sma.sdn.registry.TunnelPairRegistry;
import com.sma.sdn.serialization.json.ClassificationRequestJsonSerializer;
import com.sma.sdn.serialization.json.ClassificationResponseJsonDeserializer;
import com.sma.sdn.serialization.xml.BgpLsTopologyXmlDeserializer;
import com.sma.sdn.serialization.xml.EroXmlSerializer;
import com.sma.sdn.serialization.xml.NetworkTopologyListXmlDeserializer;
import com.sma.sdn.serialization.xml.PcepReportedLspDeserializer;
import com.sma.sdn.serialization.xml.PcepTopologyXmlDeserializer;
import com.sma.sdn.serialization.xml.PathComputationRequestXmlSerializer;
import com.sma.sdn.serialization.xml.PathComputationResponseXmlDeserializer;
import com.sma.sdn.serialization.xml.UpdateLspRequestXmlSerializer;
import com.sma.sdn.serialization.xml.UpdateLspResponseXmlDeserializer;
import com.sma.sdn.topology.TopologyDiscoveryService;
import com.sma.sdn.topology.TopologyRefreshService;
import com.sma.sdn.tunnel.DelegatedLspService;
import com.sma.sdn.tunnel.DirectionalLspApplicationService;
import com.sma.sdn.util.RetryPolicy;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.servlet.ServletException;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.NotificationService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yangtools.concepts.Registration;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

/**
 * Owns the lifecycle and dependency assembly of the SDN-MPLS-ML application in OpenDaylight.
 *
 * <p>Blueprint creates this class from <code>OSGI-INF/blueprint/impl-blueprint.xml</code> after
 * Karaf supplies <code>NotificationService</code>, <code>DataBroker</code>, and
 * <code>HttpService</code>, then invokes <code>init()</code>. That method builds the CSA object
 * graph: RESTCONF and RESTS clients, local registries, BGP-LS topology discovery, OpenFlow
 * bootstrap, delegated PCEP LSP management, ML classification, and pair-policy coordination.
 * These are assembled here rather than exposed as separate OSGi services so that they share one
 * <code>AppConfig</code>, metrics instance, and retry policy.</p>
 *
 * <p>The <code>PacketReceived</code> listener is registered before remote readiness completes.
 * <code>controlPlaneReady</code> is therefore the admission flag used by the packet workflow:
 * packets received during discovery are observed and recorded, but do not trigger LSP updates.
 * A single daemon executor performs readiness attempts with bounded exponential backoff. Once the
 * initial BGP-LS topology, delegated PCEP LSPs, and OpenFlow bootstrap are available, the provider
 * starts periodic topology refresh and marks the control plane ready.</p>
 *
 * <p><strong>Concurrency:</strong> the readiness executor serializes readiness attempts, but
 * notification delivery and <code>close()</code> can run concurrently with that executor. The
 * atomic flags provide cross-thread visibility only; they are not a lifecycle lock and do not
 * cancel an HTTP call, a notification, or a readiness phase already in progress. In particular,
 * shutdown is a best-effort transition: the phase checks <code>closed</code> between major remote
 * operations, yet a phase that has passed a check can still finish concurrently with shutdown.</p>
 *
 * <p><code>ControllerOperationalStatePublisher</code> publishes current registry and flag values
 * after material state changes and after every PacketIn. <code>close()</code> requests shutdown
 * of deferred work, unregisters the listener, stops topology refresh, and removes the Prometheus
 * servlet.</p>
 *
 * <h2>Injected services</h2>
 * <ul>
 *   <li><code>NotificationService</code> registers the MD-SAL
 *       <code>PacketReceived</code> listener.</li>
 *   <li><code>DataBroker</code> publishes the YANG operational state.</li>
 *   <li><code>HttpService</code> exposes Prometheus metrics at <code>/csa/metrics</code>.</li>
 * </ul>
 */
public final class SDN_MPLS_ML_Provider
        implements AutoCloseable, NotificationService.Listener<PacketReceived> {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(SDN_MPLS_ML_Provider.class);

    private final NotificationService notificationService;
    private final DataBroker dataBroker;
    private final HttpService httpService;
    private final AtomicLong packetInCounter = new AtomicLong();
    private final AtomicBoolean controlPlaneReady = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ScheduledExecutorService readinessExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "sma-control-plane-readiness");
        thread.setDaemon(true);
        return thread;
    });
    private Registration packetInRegistration;
    private SdnMplsMlWorkflowService workflowService;
    private TopologyDiscoveryService topologyDiscoveryService;
    private TopologyRefreshService topologyRefreshService;
    private DelegatedLspService delegatedLspService;
    private OpenflowBootstrapService openflowBootstrapService;
    private ControllerOperationalStatePublisher operationalStatePublisher;
    private String metricsServletAlias;

    /**
     * Receives the OSGi services required to operate the provider.
     *
     * <p>Blueprint resolves these arguments from the bundle descriptor. The constructor only
     * validates and retains the references: it does not open connections, register listeners, or
     * call the controller. Deferring those operations to <code>init()</code> keeps transient
     * RESTCONF or PCEP unavailability out of constructor injection and under the retry lifecycle.</p>
     *
     * @param notificationService non-null MD-SAL service used to register the
     *        <code>PacketReceived</code> listener
     * @param dataBroker non-null MD-SAL service used by the operational-state publisher
     * @param httpService non-null Karaf HTTP service used to expose metrics
     * @throws NullPointerException if Blueprint does not supply a required dependency
     */
    public SDN_MPLS_ML_Provider(
            final NotificationService notificationService,
            final DataBroker dataBroker,
            final HttpService httpService) {
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
        this.dataBroker = Objects.requireNonNull(dataBroker, "dataBroker");
        this.httpService = Objects.requireNonNull(httpService, "httpService");
    }

    /**
     * Builds the CSA workflow, registers the PacketIn listener, and begins asynchronous readiness.
     *
     * <p><code>AppConfig.fromEnvironment()</code> supplies the configuration and must select
     * <code>DELEGATED_TUNNEL_UPDATE</code>; this provider only updates LSPs already delegated to
     * the controller. The method creates one <code>HttpClient</code> shared by RESTCONF data,
     * RESTS operations, and the ML classifier. It also creates the in-memory registries, XML/JSON
     * serializers, and domain services that make policy keys, metrics, and time-to-live handling
     * consistent for the lifetime of the bundle.</p>
     *
     * <p>Construction follows dependency direction: discovery and refresh share the BGP-LS registry;
     * path computation depends on that registry and the operations client; directional LSP
     * application combines path computation with PCEP updates; and pair-policy coordination combines
     * evidence, consensus, preemption, and application. The PacketIn workflow receives suppliers
     * for readiness and topology freshness so it can evaluate them for every notification.</p>
     *
     * <p>The listener is registered before discovery begins so that notifications are not missed,
     * while <code>controlPlaneReady</code> remains false until
     * <code>runReadinessAttempt()</code> succeeds. The first readiness attempt is scheduled with
     * zero delay outside the Blueprint lifecycle thread.</p>
     *
     * @throws IllegalStateException if the configured tunnel mode is unsupported or metrics cannot
     *        be published
     * @throws RuntimeException if local construction or mandatory listener registration fails
     */
    public void init() {
        LOG.info(
                "controller_initialization_started",
                "init",
                "Se inicio la inicializacion del proveedor OSGi del controlador.",
                Map.of());

        // Configuration is the common contract for every client and service in the bundle.
        final AppConfig config = AppConfig.fromEnvironment();
        if (config.tunnelCreationMode() != TunnelCreationMode.DELEGATED_TUNNEL_UPDATE) {
            throw new IllegalStateException("Solo se admite DELEGATED_TUNNEL_UPDATE. Modo configurado="
                    + config.tunnelCreationMode());
        }
        LOG.debug(
                "controller_configuration_loaded",
                "init",
                "Se cargo la configuracion operativa sin exponer credenciales.",
                StructuredLogger.fields(
                        "mode", config.tunnelCreationMode(),
                        "restconf_data_base_url", config.odlRestconfDataBaseUrl(),
                        "rests_operations_base_url", config.odlRestsOperationsBaseUrl(),
                        "classifier_endpoint", config.classifierEndpoint(),
                        "bgpls_topology_id", config.bgplsTopologyId(),
                        "pcep_topology_id", config.pcepTopologyId(),
                        "forward_lsp_name", config.forwardLspName(),
                        "reverse_lsp_name", config.reverseLspName()));
        /*
         * A single HTTP client keeps transport behavior and timeout handling consistent across
         * RESTCONF, RESTS, and the classifier API.
         */
        final HttpClient httpClient = HttpClientFactory.create(config.httpRequestTimeout());

        /*
         * RESTCONF reads controller state and inventories. RESTS executes operations that modify
         * PCEP LSPs; keeping the two clients explicit distinguishes data reads from operational RPCs.
         */
        final OdlRestconfDataClient dataClient = new OdlRestconfDataClient(httpClient, config);
        final OdlOperationsClient operationsClient = new OdlOperationsClient(httpClient, config);
        final ClassifierRestClient classifierRestClient = new ClassifierRestClient(httpClient, config);

        // These modules serialize Optional and Instant values used by the classifier JSON contract.
        final ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new Jdk8Module())
                .registerModule(new JavaTimeModule());

        final SdnMplsMlMetrics metrics = new SdnMplsMlMetrics();
        registerMetricsServlet(metrics);

        /*
         * These registries hold the state shared by acquisition and decision-making: topology,
         * calculated paths, classifications, and reported LSPs. Readiness populates them only after
         * the listener is registered.
         */
        final BgpLsNodeRegistry bgpLsNodeRegistry = new BgpLsNodeRegistry();
        final CalculatedPathRegistry calculatedPathRegistry = new CalculatedPathRegistry(metrics);
        final ClassificationRegistrar classificationRegistrar = new ClassificationRegistrar(metrics);
        final DelegatedLspRegistry delegatedLspRegistry = new DelegatedLspRegistry();

        /*
         * Initial discovery and periodic refresh share both the registry and outcome classifier, so
         * the PacketIn freshness check observes the topology validated by either process.
         */
        final TopologyDiscoveryOutcomeClassifier topologyOutcomeClassifier = new TopologyDiscoveryOutcomeClassifier();
        topologyDiscoveryService = new TopologyDiscoveryService(
                config,
                dataClient,
                new NetworkTopologyListXmlDeserializer(),
                new BgpLsTopologyXmlDeserializer(),
                bgpLsNodeRegistry,
                topologyOutcomeClassifier,
                metrics);
        topologyRefreshService = new TopologyRefreshService(
                config,
                dataClient,
                new BgpLsTopologyXmlDeserializer(),
                bgpLsNodeRegistry,
                topologyOutcomeClassifier,
                metrics,
                this::publishOperationalState);

        /*
         * Classification returns a policy, not a route. Directional application combines path
         * computation with PCEP and reuses the ERO serializer in delegated-LSP updates.
         */
        final EroXmlSerializer eroXmlSerializer = new EroXmlSerializer();
        final ClassificationService classificationService = new ClassificationService(
                classificationRegistrar,
                classifierRestClient,
                new ClassificationRequestJsonSerializer(objectMapper),
                new ClassificationResponseJsonDeserializer(objectMapper, config.classificationCacheTtl()),
                metrics);
        final PathComputationService pathComputationService = new PathComputationService(
                config,
                bgpLsNodeRegistry,
                calculatedPathRegistry,
                operationsClient,
                new PathComputationRequestXmlSerializer(),
                new PathComputationResponseXmlDeserializer(),
                new CalculatedPathToEroTranslator(),
                new PathComputationOutcomeClassifier(),
                metrics);
        final RetryPolicy retryPolicy = new RetryPolicy(
                config.odlRetryInitialDelayMs(),
                config.odlRetryMaxDelayMs(),
                config.operationalValidationTimeout(),
                config.odlRetryJitterPercent());

        /*Corresponds to the operational data storage for the openflow switches read from opendaylight through RESTCONF*/
        final OpenflowSwitchRegistry openflowSwitchRegistry = new OpenflowSwitchRegistry();

        /*Thisis the baseline mechanism that allows for the initializacion of the baseline flow tables on either edge devices
        * as they do not know anything aside from the baseline Table Miss behavior whihc pushes traffic to the controller. Aside from that one
        * we append a new rule to duplicate traffic to the controller as well as push it through to always allow for traffic
        * to flow.*/
        openflowBootstrapService = new OpenflowBootstrapService(
                config,
                new OpenflowInventoryService(
                        config,
                        dataClient,
                        new OpenflowInventoryXmlDeserializer(),
                        metrics),
                openflowSwitchRegistry,
                new OpenflowFlowProvisioningService(
                        config,
                        dataClient,
                        new OpenflowFlowXmlSerializer(),
                        metrics),
                new OpenflowBootstrapVerifier(dataClient, retryPolicy),
                metrics);
        delegatedLspService = new DelegatedLspService(
                config,
                dataClient,
                operationsClient,
                new PcepTopologyXmlDeserializer(new PcepReportedLspDeserializer()),
                new UpdateLspRequestXmlSerializer(eroXmlSerializer),
                new UpdateLspResponseXmlDeserializer(),
                delegatedLspRegistry,
                retryPolicy,
                metrics);
        /*
         * The coordinator receives evidence from both directions before selecting a pair policy.
         * ServiceKeyResolver stabilizes TCP, UDP, and ICMP service identity, while the hash
         * distinguishes a new policy from an equivalent refresh.
         */
        final TunnelPairRegistry tunnelPairRegistry = new TunnelPairRegistry(config);
        final ServiceKeyResolver serviceKeyResolver = new ServiceKeyResolver();
        final PairPolicyHashService pairPolicyHashService = new PairPolicyHashService(config.pairPolicyHashVersion());
        final DirectionalClassificationEvidenceRegistry evidenceRegistry =
                new DirectionalClassificationEvidenceRegistry(tunnelPairRegistry, metrics);
        final ActivePairPolicyRegistry activePairPolicyRegistry = new ActivePairPolicyRegistry(metrics);
        final PairPolicyCoordinator pairPolicyCoordinator = new PairPolicyCoordinator(
                config,
                tunnelPairRegistry,
                evidenceRegistry,
                new PairPolicyConsensusService(),
                activePairPolicyRegistry,
                new PolicyPreemptionEvaluator(),
                new DirectionalLspApplicationService(
                        pathComputationService, delegatedLspService, pairPolicyHashService, metrics),
                metrics);
        /*
         * The publisher receives state suppliers rather than snapshots, so its YANG view reads the
         * current counters and registries when invoked from the workflow or refresh process.
         */
        operationalStatePublisher = new ControllerOperationalStatePublisher(
                dataBroker,
                config,
                bgpLsNodeRegistry,
                classificationRegistrar,
                calculatedPathRegistry,
                delegatedLspRegistry,
                openflowSwitchRegistry,
                evidenceRegistry,
                activePairPolicyRegistry,
                tunnelPairRegistry,
                topologyRefreshService,
                metrics,
                controlPlaneReady::get,
                closed::get,
                packetInCounter::get);
        /*
         * Freshness is injected as a supplier, allowing each PacketIn to request an on-demand
         * refresh without coupling the workflow to TopologyRefreshService lifecycle ownership.
         */
        workflowService = new SdnMplsMlWorkflowService(
                config,
                new PacketInFeatureExtractor(config, openflowSwitchRegistry),
                classificationService,
                new DirectionRegistry(config),
                tunnelPairRegistry,
                serviceKeyResolver,
                pairPolicyHashService,
                pairPolicyCoordinator,
                metrics,
                controlPlaneReady::get,
                () -> topologyRefreshService != null && topologyRefreshService.ensureFresh());

        packetInRegistration = notificationService.registerListener(PacketReceived.class, this);
        LOG.info(
                "packet_listener_registered",
                "init",
                "Se registro el listener PacketReceived para actualizar LSP delegados.",
                StructuredLogger.fields(
                        "forward_lsp_name", config.forwardLspName(),
                        "reverse_lsp_name", config.reverseLspName()));
        publishOperationalState();
        scheduleReadinessAttempt(config, 1, 0L);
        LOG.info(
                "control_plane_readiness_scheduled",
                "init",
                "Se programo el descubrimiento asincrono del plano de control.",
                StructuredLogger.fields("initial_delay_ms", 0));
    }

    /**
     * Schedules the next readiness attempt without blocking the Blueprint lifecycle thread.
     *
     * <p>The executor has one thread, so BGP-LS, PCEP, and OpenFlow readiness cycles do not overlap.
     * <code>delayMs</code> may already include jitter and is used only as the delay before invoking
     * <code>runReadinessAttempt()</code>. The initial <code>closed</code> check avoids scheduling
     * new work after shutdown has been observed; it does not make scheduling and shutdown atomic.</p>
     *
     * <p>A <code>RejectedExecutionException</code> is expected when <code>close()</code> shuts down
     * the executor between the check and the scheduling call, and is therefore ignored. If the
     * provider is still open, rejection is logged because no local readiness retry was scheduled.</p>
     *
     * @param config non-null configuration defining retry behavior for the delegated attempt
     * @param attempt positive ordinal used for tracing and for identifying the next attempt
     * @param delayMs non-negative delay, in milliseconds, before the attempt runs
     */
    private void scheduleReadinessAttempt(final AppConfig config, final long attempt, final long delayMs) {
        if (closed.get()) {
            return;
        }
        try {
            readinessExecutor.schedule(
                    () -> runReadinessAttempt(config, attempt, delayMs), delayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            if (!closed.get()) {
                LOG.warn(
                        "control_plane_readiness_schedule_rejected",
                        "scheduleReadinessAttempt",
                        "El ejecutor rechazo inesperadamente un intento de disponibilidad.",
                        StructuredLogger.fields("attempt", attempt, "delay_ms", delayMs),
                        e);
            }
        }
    }

    /**
     * Runs one complete control-plane readiness cycle in the background.
     *
     * <ol>
     *   <li><code>TopologyDiscoveryService</code> reads and validates the BGP-LS topology, filling
     *       the node registry used by path computation.</li>
     *   <li><code>DelegatedLspService</code> discovers existing PCEP LSPs and verifies that the
     *       configured tunnels can be updated as delegated LSPs.</li>
     *   <li><code>OpenflowBootstrapService</code> reads inventory, provisions access flows, and
     *       verifies them before PacketIn processing is admitted.</li>
     * </ol>
     *
     * <p>Only after all three phases succeed does the method start
     * <code>TopologyRefreshService</code> and set <code>controlPlaneReady</code> to true. A
     * <code>RuntimeException</code> from any phase is contained here: the provider is marked not
     * ready, operational state is published, and another cycle is scheduled with bounded
     * exponential backoff and jitter. The exception is deliberately not rethrown from the executor,
     * because that would silently end the recovery loop.</p>
     *
     * <p>The <code>closed</code> checks between phases stop later phases when shutdown was observed
     * after a remote call returns. They do not serialize shutdown with a phase that has already
     * passed its check, and this method does not wait for remote calls to honor interruption.</p>
     *
     * @param config non-null configuration supplying retry limits and jitter
     * @param attempt ordinal of the current cycle, included in structured events
     * @param previousDelayMs delay used before this cycle; it determines the next delay when this
     *        cycle fails
     */
    private void runReadinessAttempt(
            final AppConfig config,
            final long attempt,
            final long previousDelayMs) {
        if (closed.get()) {
            return;
        }
        LOG.info(
                "control_plane_readiness_attempt_started",
                "runReadinessAttempt",
                "Se inicio un intento asincrono de disponibilidad del plano de control.",
                StructuredLogger.fields("attempt", attempt));
        try {
            // Path and LSP validation require a topology snapshot before they can run.
            topologyDiscoveryService.initialize();
            topologyRefreshService.markInitialDiscoverySuccessful();
            if (closed.get()) {
                return;
            }
            // The PCEP read verifies that the target LSPs are delegated and updateable.
            delegatedLspService.initialize();
            if (closed.get()) {
                return;
            }
            // PacketIn ingress mappings depend on a ready OpenFlow inventory.
            openflowBootstrapService.initialize();
            if (closed.get()) {
                return;
            }
            if (!openflowBootstrapService.isReady()) {
                throw new IllegalStateException("El bootstrap OpenFlow no confirmo su disponibilidad");
            }
            // Periodic refresh begins only after a known-valid initial snapshot is available.
            topologyRefreshService.start();
            controlPlaneReady.set(true);
            publishOperationalState();
            LOG.info(
                    "control_plane_ready",
                    "runReadinessAttempt",
                    "El plano de control confirmo BGP-LS, PCEP y los flujos de acceso OpenFlow requeridos.",
                    StructuredLogger.fields("attempt", attempt));
        } catch (RuntimeException e) {
            controlPlaneReady.set(false);
            publishOperationalState();
            if (closed.get()) {
                return;
            }
            final long baseDelayMs = nextReadinessDelay(config, previousDelayMs);
            final long retryDelayMs = RetryPolicy.jitter(baseDelayMs, config.odlRetryJitterPercent());
            LOG.warn(
                    "control_plane_not_ready",
                    "runReadinessAttempt",
                    "El plano de control aun no esta disponible; el descubrimiento sera reintentado.",
                    StructuredLogger.fields(
                            "attempt", attempt,
                            "next_attempt", attempt + 1,
                            "retry_delay_ms", retryDelayMs),
                    e);
            scheduleReadinessAttempt(config, attempt + 1, retryDelayMs);
        }
    }

    /**
     * Calculates the bounded exponential-backoff base delay for the next readiness cycle.
     *
     * <p>The first failure uses <code>odlRetryInitialDelayMs</code>. Later failures double the
     * prior delay, up to <code>odlRetryMaxDelayMs</code>. The multiplication guards against
     * <code>long</code> overflow, so even an anomalous sequence cannot turn the delay negative.
     * Jitter is intentionally not applied here, keeping the calculation deterministic; it is added
     * by <code>runReadinessAttempt()</code> immediately before scheduling work.</p>
     *
     * @param config non-null configuration providing the initial and maximum delays
     * @param previousDelayMs delay used before the cycle that just failed; zero or a negative value
     *        means that no previous delay exists
     * @return non-negative base delay for the next attempt, in milliseconds
     */
    private static long nextReadinessDelay(final AppConfig config, final long previousDelayMs) {
        if (previousDelayMs <= 0L) {
            return config.odlRetryInitialDelayMs();
        }
        final long doubled = previousDelayMs > Long.MAX_VALUE / 2L
                ? Long.MAX_VALUE : previousDelayMs * 2L;
        return Math.min(config.odlRetryMaxDelayMs(), doubled);
    }

    /**
     * Receives an MD-SAL notification and delegates processing to the PacketIn workflow.
     *
     * <p>The notification originates from the subscription registered by <code>init()</code>. A
     * null value is logged and ignored, although standard MD-SAL delivery should not produce one.
     * For a valid notification, the provider increments a monotonic counter and opens a
     * <code>LogContext</code> containing workflow and packet identifiers. Downstream extraction,
     * classification, consensus, and LSP-application events inherit this context without requiring
     * each dependency to receive the identifiers as explicit parameters.</p>
     *
     * <p><code>SdnMplsMlWorkflowService.handlePacket()</code> decides whether to extract features,
     * use a cached classification or call the ML API, and whether sufficient evidence exists to
     * update LSPs. Its <code>RuntimeException</code> instances are logged and contained at this
     * notification boundary so a single packet failure does not terminate MD-SAL delivery. The
     * provider attempts operational-state publication in the <code>finally</code> block in every
     * case. This method does not serialize a notification with shutdown; a notification already in
     * progress may finish while <code>close()</code> is running.</p>
     *
     * @param notification MD-SAL <code>PacketReceived</code> notification; null is tolerated only
     *        for non-standard invocation and produces no workflow side effects
     */
    @Override
    public void onNotification(final PacketReceived notification) {
        if (notification == null) {
            LOG.warn(
                    "packet_notification_null",
                    "onNotification",
                    "Se recibio una notificacion PacketReceived nula y se descarto.",
                    Map.of(),
                    null);
            return;
        }

        // The counter and context correlate one packet's records across all participating modules.
        final long packetSequence = packetInCounter.incrementAndGet();
        try (LogContext ignored = LogContext.open(Map.of(
                "workflow_id", UUID.randomUUID().toString(),
                "packet_sequence", Long.toString(packetSequence)))) {
            LOG.debug(
                    "packet_notification_received",
                    "onNotification",
                    "Se recibio una notificacion PacketReceived y se inicio su procesamiento.",
                    StructuredLogger.fields("packet_sequence", packetSequence));
            LOG.debug(
                    "packet_notification_snapshot",
                    "onNotification",
                    "Se registro la representacion textual completa de la notificacion PacketReceived recibida.",
                    StructuredLogger.fields(
                            "packet_sequence", packetSequence,
                            "notification_type", notification.getClass().getName(),
                            "notification_interface", PacketReceived.class.getName(),
                            "raw_notification", String.valueOf(notification),
                            "raw_ingress", String.valueOf(notification.getIngress())));
            // The workflow owns decisions; this boundary contains failures and publishes state.
            workflowService.handlePacket(notification);
        } catch (RuntimeException e) {
            LOG.error(
                    "packet_workflow_failed",
                    "onNotification",
                    "Fallo el procesamiento de la notificacion PacketReceived.",
                    StructuredLogger.fields("packet_sequence", packetSequence),
                    e);
        } finally {
            publishOperationalState();
        }
    }

    /**
     * Closes the provider and releases resources that outlive an individual method call.
     *
     * <p>Blueprint invokes this method as its <code>destroy-method</code>. It first marks the
     * provider closed and not ready, and publishes that condition while the publisher still exists.
     * It then requests shutdown of the executor, removes the MD-SAL subscription, stops topology
     * refresh, and unregisters the metrics servlet. This ordering prevents new scheduled work from
     * being accepted after shutdown is requested.</p>
     *
     * <p>The method tolerates partial initialization: the listener, refresh service, and servlet
     * alias may not exist if <code>init()</code> failed early. Their references are cleared after
     * release so a later invocation does not repeat those operations. This is not a join operation:
     * it neither waits for an in-flight HTTP call or PacketIn workflow nor establishes mutual
     * exclusion with the readiness executor. The atomic flags make shutdown visible to subsequent
     * checks, but cannot retract a side effect that a concurrently running phase has already begun.</p>
     */
    @Override
    public void close() {
        closed.set(true);
        controlPlaneReady.set(false);
        publishOperationalState();
        // Request cancellation before releasing collaborators that deferred work could otherwise use.
        readinessExecutor.shutdownNow();
        if (packetInRegistration != null) {
            packetInRegistration.close();
            packetInRegistration = null;
        }
        if (topologyRefreshService != null) {
            topologyRefreshService.close();
            topologyRefreshService = null;
        }
        if (metricsServletAlias != null) {
            httpService.unregister(metricsServletAlias);
            metricsServletAlias = null;
        }
        LOG.info(
                "packet_listener_unregistered",
                "close",
                "Se desregistro el listener PacketReceived y se cerraron los servicios auxiliares.",
                StructuredLogger.fields("processed_packet_count", packetInCounter.get()));
    }

    /**
     * Publishes an operational snapshot when the YANG publisher has been assembled.
     *
     * <p>The null guard makes this method usable during partial initialization, error handling, and
     * shutdown. The publisher reads suppliers backed by current registries and flags, so this method
     * creates no local snapshot and does not coordinate PacketIn execution. Exceptions from
     * <code>publish()</code> are intentionally propagated to the caller, which determines whether
     * the failure belongs to a retry path or a notification failure path.</p>
     */
    private void publishOperationalState() {
        if (operationalStatePublisher != null) {
            operationalStatePublisher.publish();
        }
    }

    /**
     * Registers the Prometheus endpoint that exposes the CSA's shared metrics.
     *
     * <p>The servlet retains the same <code>SdnMplsMlMetrics</code> instance used by classification,
     * topology, path, and LSP services, so <code>/csa/metrics</code> exposes a consistent set of
     * counters. The alias is retained so that <code>close()</code> can unregister the servlet.</p>
     *
     * <p><code>NamespaceException</code> means another application already owns the alias, while
     * <code>ServletException</code> means Karaf could not construct the servlet. Both are translated
     * to <code>IllegalStateException</code> because this provider treats missing observability as
     * incomplete initialization.</p>
     *
     * @param metrics non-null instance to which application components record operational results
     * @throws IllegalStateException if the HTTP service cannot register the requested endpoint
     */
    private void registerMetricsServlet(final SdnMplsMlMetrics metrics) {
        try {
            httpService.registerServlet("/csa/metrics", new PrometheusMetricsServlet(metrics), null, null);
            metricsServletAlias = "/csa/metrics";
            LOG.info(
                    "prometheus_metrics_servlet_registered",
                    "registerMetricsServlet",
                    "Se registro el endpoint Prometheus del controlador.",
                    StructuredLogger.fields("path", "/csa/metrics"));
        } catch (NamespaceException | ServletException e) {
            throw new IllegalStateException("No se pudo registrar el endpoint Prometheus", e);
        }
    }
}
