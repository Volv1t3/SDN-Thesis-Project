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
import com.sma.sdn.registry.BgpLsNodeRegistry;
import com.sma.sdn.registry.CalculatedPathRegistry;
import com.sma.sdn.registry.ClassificationRegistrar;
import com.sma.sdn.registry.DirectionRegistry;
import com.sma.sdn.registry.DelegatedLspRegistry;
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
import com.sma.sdn.policy.PairPolicyCoordinator;
import com.sma.sdn.policy.PairPolicyConsensusService;
import com.sma.sdn.policy.PairPolicyHashService;
import com.sma.sdn.policy.PolicyPreemptionEvaluator;
import com.sma.sdn.policy.ServiceKeyResolver;
import com.sma.sdn.registry.ActivePairPolicyRegistry;
import com.sma.sdn.registry.DirectionalClassificationEvidenceRegistry;
import com.sma.sdn.registry.TunnelPairRegistry;
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
import org.opendaylight.mdsal.binding.api.NotificationService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yangtools.concepts.Registration;

/**
 * Administra el ciclo de vida principal de la aplicacion SDN-MPLS-ML ejecutada dentro del controlador OpenDaylight.
 */
public final class SDN_MPLS_ML_Provider
        implements AutoCloseable, NotificationService.Listener<PacketReceived> {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(SDN_MPLS_ML_Provider.class);

    private final NotificationService notificationService;
    private final DataBroker dataBroker;
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

    /**
     * Ejecuta la operacion {@code SDN_MPLS_ML_Provider} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param notificationService valor requerido para ejecutar esta operacion
     */
    public SDN_MPLS_ML_Provider(
            final NotificationService notificationService,
            final DataBroker dataBroker) {
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
        this.dataBroker = Objects.requireNonNull(dataBroker, "dataBroker");
    }

    /**
     * Construye el flujo, registra el listener PacketReceived e inicia asincronicamente la disponibilidad del plano.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Carga la configuracion y construye servicios sin ejecutar descubrimiento RESTCONF.</li>
     *   <li>Registra el listener PacketReceived antes de iniciar llamadas topologicas.</li>
     *   <li>Entrega el descubrimiento BGP-LS y PCEP al ejecutor asincrono de disponibilidad.</li>
     * </ol>
     *
     * @throws RuntimeException si falla la configuracion local o el registro obligatorio del listener
     */
    public void init() {
        LOG.info(
                "controller_initialization_started",
                "init",
                "Se inicio la inicializacion del proveedor OSGi del controlador.",
                Map.of());
        final AppConfig config = AppConfig.fromEnvironment();
        if (config.tunnelCreationMode() != TunnelCreationMode.DELEGATED_TUNNEL_UPDATE) {
            throw new IllegalStateException("Solo se admite DELEGATED_TUNNEL_UPDATE; modo configurado="
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
        final HttpClient httpClient = HttpClientFactory.create(config.httpRequestTimeout());
        final OdlRestconfDataClient dataClient = new OdlRestconfDataClient(httpClient, config);
        final OdlOperationsClient operationsClient = new OdlOperationsClient(httpClient, config);
        final ClassifierRestClient classifierRestClient = new ClassifierRestClient(httpClient, config);
        final ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new Jdk8Module())
                .registerModule(new JavaTimeModule());
        final SdnMplsMlMetrics metrics = new SdnMplsMlMetrics();
        final BgpLsNodeRegistry bgpLsNodeRegistry = new BgpLsNodeRegistry();
        final CalculatedPathRegistry calculatedPathRegistry = new CalculatedPathRegistry();
        final ClassificationRegistrar classificationRegistrar = new ClassificationRegistrar();
        final DelegatedLspRegistry delegatedLspRegistry = new DelegatedLspRegistry();

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
        final OpenflowSwitchRegistry openflowSwitchRegistry = new OpenflowSwitchRegistry();
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
        final TunnelPairRegistry tunnelPairRegistry = new TunnelPairRegistry(config);
        final ServiceKeyResolver serviceKeyResolver = new ServiceKeyResolver();
        final PairPolicyHashService pairPolicyHashService = new PairPolicyHashService(config.pairPolicyHashVersion());
        final DirectionalClassificationEvidenceRegistry evidenceRegistry =
                new DirectionalClassificationEvidenceRegistry(tunnelPairRegistry);
        final ActivePairPolicyRegistry activePairPolicyRegistry = new ActivePairPolicyRegistry();
        final PairPolicyCoordinator pairPolicyCoordinator = new PairPolicyCoordinator(
                config,
                tunnelPairRegistry,
                evidenceRegistry,
                new PairPolicyConsensusService(config.pairConsensusRequireBothDirections(),
                        config.pairConsensusSingleSideProvisionalEnabled(), config.pairConsensusEqualPriorityAction(),
                        serviceKeyResolver),
                activePairPolicyRegistry,
                new PolicyPreemptionEvaluator(),
                new DirectionalLspApplicationService(pathComputationService, delegatedLspService, pairPolicyHashService, metrics),
                metrics);
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
                topologyRefreshService,
                controlPlaneReady::get,
                closed::get,
                packetInCounter::get);
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
     * Programa un intento de disponibilidad sin bloquear el hilo de ciclo de vida de Blueprint.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Descarta la programacion cuando el proveedor ya esta cerrado.</li>
     *   <li>Entrega el intento al ejecutor dedicado con la demora indicada.</li>
     *   <li>Ignora el rechazo esperado si el cierre ocurre concurrentemente.</li>
     * </ol>
     *
     * @param config configuracion de demoras y jitter
     * @param attempt numero secuencial del intento
     * @param delayMs demora previa al intento en milisegundos
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
     * Descubre BGP-LS, PCEP y OpenFlow en segundo plano sin afectar el arranque del bundle.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Ejecuta el descubrimiento de topologias y nodos BGP-LS.</li>
     *   <li>Descubre y valida los LSP delegados reportados por PCEP.</li>
     *   <li>Instala y verifica los flujos de acceso OpenFlow.</li>
     *   <li>Marca el plano listo e inicia el refresco periodico, o programa un nuevo ciclo con espera exponencial.</li>
     * </ol>
     *
     * @param config configuracion de reintentos del controlador
     * @param attempt numero secuencial del intento actual
     * @param previousDelayMs demora utilizada antes del intento actual
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
            topologyDiscoveryService.initialize();
            topologyRefreshService.markInitialDiscoverySuccessful();
            if (closed.get()) {
                return;
            }
            delegatedLspService.initialize();
            if (closed.get()) {
                return;
            }
            openflowBootstrapService.initialize();
            if (closed.get()) {
                return;
            }
            if (!openflowBootstrapService.isReady()) {
                throw new IllegalStateException("El bootstrap OpenFlow no confirmo su disponibilidad");
            }
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
     * Calcula la espera exponencial limitada para el siguiente ciclo de disponibilidad.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Usa la demora inicial cuando todavia no existio una espera previa.</li>
     *   <li>Duplica de forma segura la demora utilizada por el ciclo anterior.</li>
     *   <li>Limita el resultado al maximo configurado.</li>
     * </ol>
     *
     * @param config configuracion de limites de reintento
     * @param previousDelayMs demora aplicada antes del intento actual
     * @return demora base del siguiente intento en milisegundos
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
     * Ejecuta la operacion {@code onNotification} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param notification valor requerido para ejecutar esta operacion
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
     * Desactiva la disponibilidad, cancela los reintentos y libera el listener y el refresco topologico.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Marca el proveedor como cerrado e impide nuevos flujos PacketIn.</li>
     *   <li>Interrumpe el ejecutor de disponibilidad y desregistra el listener.</li>
     *   <li>Detiene el refresco periodico de topologia.</li>
     * </ol>
     */
    @Override
    public void close() {
        closed.set(true);
        controlPlaneReady.set(false);
        publishOperationalState();
        readinessExecutor.shutdownNow();
        if (packetInRegistration != null) {
            packetInRegistration.close();
            packetInRegistration = null;
        }
        if (topologyRefreshService != null) {
            topologyRefreshService.close();
            topologyRefreshService = null;
        }
        LOG.info(
                "packet_listener_unregistered",
                "close",
                "Se desregistro el listener PacketReceived y se cerraron los servicios auxiliares.",
                StructuredLogger.fields("processed_packet_count", packetInCounter.get()));
    }

    private void publishOperationalState() {
        if (operationalStatePublisher != null) {
            operationalStatePublisher.publish();
        }
    }
}
