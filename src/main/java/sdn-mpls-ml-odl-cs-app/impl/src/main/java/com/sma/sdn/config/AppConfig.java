/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.config;

import com.sma.sdn.model.FlowDirection;
import com.sma.sdn.model.OdlXmlBodyLogLevel;
import com.sma.sdn.model.PairConsensusEqualPriorityAction;
import com.sma.sdn.model.PairPolicyLspApplicationScope;
import com.sma.sdn.model.TunnelDirection;
import com.sma.sdn.model.TunnelEndpoint;
import com.sma.sdn.model.TunnelUpdateScope;
import com.sma.sdn.observability.StructuredLogger;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Define el registro {@code AppConfig} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public record AppConfig(
        TunnelCreationMode tunnelCreationMode,
        URI odlRestconfDataBaseUrl,
        URI odlRestsOperationsBaseUrl,
        String odlUsername,
        String odlPassword,
        URI classifierEndpoint,
        String bgplsTopologyId,
        String pcepTopologyId,
        String pathComputationGraphName,
        TunnelEndpoint headend,
        TunnelEndpoint tailend,
        TunnelDirection headendToTailend,
        TunnelDirection tailendToHeadend,
        String forwardLspName,
        String reverseLspName,
        String forwardTunnelInterface,
        String reverseTunnelInterface,
        IngressMapping headendToTailendIngress,
        IngressMapping tailendToHeadendIngress,
        Duration classificationCacheTtl,
        Duration pathCacheTtl,
        Duration topologyCacheTtl,
        Duration operationalValidationTimeout,
        Duration httpRequestTimeout,
        long odlRetryInitialDelayMs,
        long odlRetryMaxDelayMs,
        int odlRetryJitterPercent,
        int topologyDiscoveryMaxAttempts,
        boolean openflowBootstrapEnabled,
        int openflowTableId,
        String ovsEchoManagementIp,
        String ovsEchoHostPortName,
        String ovsEchoCorePortName,
        String ovsFoxtrotManagementIp,
        String ovsFoxtrotHostPortName,
        String ovsFoxtrotCorePortName,
        int openflowArpPriority,
        int openflowIpv4Priority,
        boolean openflowInstallDefaultDrop,
        int openflowDefaultDropPriority,
        TunnelUpdateScope tunnelUpdateScope,
        Duration tunnelIntentTtl,
        Duration tunnelPendingTtl,
        int tunnelOperationJournalMaxEntries,
        Duration tunnelOperationLockTimeout,
        boolean openflowSuppressionEnabled,
        int openflowSuppressionIdleTimeoutSeconds,
        int openflowSuppressionHardTimeoutSeconds,
        int openflowSuppressionPriority,
        long openflowSuppressionCookieBase,
        OdlXmlBodyLogLevel odlXmlBodyLogLevel,
        boolean pairConsensusEnabled,
        boolean pairConsensusRequireBothDirections,
        Duration pairConsensusEvidenceTtl,
        boolean pairConsensusSingleSideProvisionalEnabled,
        PairConsensusEqualPriorityAction pairConsensusEqualPriorityAction,
        Duration activePairPolicyIdleTtl,
        boolean activePairPolicySweeperEnabled,
        Duration activePairPolicySweeperInterval,
        boolean pairPolicyPriorityPreemptionEnabled,
        int pairPolicyHashVersion,
        PairPolicyLspApplicationScope pairPolicyLspApplicationScope,
        boolean lspApplicationRequireAllDirections,
        boolean lspApplicationReapplyOnBandwidthMismatch,
        boolean lspApplicationReapplyOnEroMismatch,
        boolean lspApplicationReapplyOnPriorityMismatch) {

    private static final StructuredLogger LOG = StructuredLogger.getLogger(AppConfig.class);
    private static final String DEFAULT_DATA_URL = "http://172.21.121.100:8182/restconf/data";
    private static final String DEFAULT_OPERATIONS_URL = "http://172.21.121.100:8181/rests/operations";
    private static final String DEFAULT_CLASSIFIER_BASE_URL = "http://127.0.0.1:33761";
    private static final String DEFAULT_CLASSIFIER_PATH = "/api/v1/classify";

    /**
     * Construye y valida la configuracion completa usada por el bundle ODL. Este constructor compacto garantiza que los
     * objetos obligatorios existen antes de que se creen clientes HTTP, registros o servicios de ciclo de vida.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Verifica que los objetos y cadenas de configuracion obligatorios no sean nulos.</li>
     *   <li>Conserva los valores escalares ya normalizados por el cargador de entorno.</li>
     *   <li>Permite que el record resultante sea usado como contrato inmutable de ejecucion.</li>
     * </ol>
     *
     * @throws NullPointerException si un valor obligatorio de configuracion es nulo
     */
    public AppConfig {
        Objects.requireNonNull(tunnelCreationMode, "tunnelCreationMode");
        Objects.requireNonNull(odlRestconfDataBaseUrl, "odlRestconfDataBaseUrl");
        Objects.requireNonNull(odlRestsOperationsBaseUrl, "odlRestsOperationsBaseUrl");
        Objects.requireNonNull(odlUsername, "odlUsername");
        Objects.requireNonNull(odlPassword, "odlPassword");
        Objects.requireNonNull(classifierEndpoint, "classifierEndpoint");
        Objects.requireNonNull(bgplsTopologyId, "bgplsTopologyId");
        Objects.requireNonNull(pcepTopologyId, "pcepTopologyId");
        Objects.requireNonNull(pathComputationGraphName, "pathComputationGraphName");
        Objects.requireNonNull(headend, "headend");
        Objects.requireNonNull(tailend, "tailend");
        Objects.requireNonNull(headendToTailend, "headendToTailend");
        Objects.requireNonNull(tailendToHeadend, "tailendToHeadend");
        Objects.requireNonNull(forwardLspName, "forwardLspName");
        Objects.requireNonNull(reverseLspName, "reverseLspName");
        Objects.requireNonNull(forwardTunnelInterface, "forwardTunnelInterface");
        Objects.requireNonNull(reverseTunnelInterface, "reverseTunnelInterface");
        Objects.requireNonNull(classificationCacheTtl, "classificationCacheTtl");
        Objects.requireNonNull(pathCacheTtl, "pathCacheTtl");
        Objects.requireNonNull(topologyCacheTtl, "topologyCacheTtl");
        Objects.requireNonNull(operationalValidationTimeout, "operationalValidationTimeout");
        Objects.requireNonNull(httpRequestTimeout, "httpRequestTimeout");
        Objects.requireNonNull(tunnelUpdateScope, "tunnelUpdateScope");
        Objects.requireNonNull(tunnelIntentTtl, "tunnelIntentTtl");
        Objects.requireNonNull(tunnelPendingTtl, "tunnelPendingTtl");
        Objects.requireNonNull(tunnelOperationLockTimeout, "tunnelOperationLockTimeout");
        Objects.requireNonNull(odlXmlBodyLogLevel, "odlXmlBodyLogLevel");
        Objects.requireNonNull(pairConsensusEvidenceTtl, "pairConsensusEvidenceTtl");
        Objects.requireNonNull(pairConsensusEqualPriorityAction, "pairConsensusEqualPriorityAction");
        Objects.requireNonNull(activePairPolicyIdleTtl, "activePairPolicyIdleTtl");
        Objects.requireNonNull(activePairPolicySweeperInterval, "activePairPolicySweeperInterval");
        Objects.requireNonNull(pairPolicyLspApplicationScope, "pairPolicyLspApplicationScope");
        Objects.requireNonNull(ovsEchoManagementIp, "ovsEchoManagementIp");
        Objects.requireNonNull(ovsEchoHostPortName, "ovsEchoHostPortName");
        Objects.requireNonNull(ovsEchoCorePortName, "ovsEchoCorePortName");
        Objects.requireNonNull(ovsFoxtrotManagementIp, "ovsFoxtrotManagementIp");
        Objects.requireNonNull(ovsFoxtrotHostPortName, "ovsFoxtrotHostPortName");
        Objects.requireNonNull(ovsFoxtrotCorePortName, "ovsFoxtrotCorePortName");
        if (openflowTableId < 0 || openflowArpPriority < 0 || openflowIpv4Priority < 0
                || openflowDefaultDropPriority < 0) {
            throw new IllegalArgumentException("Los identificadores y prioridades OpenFlow no pueden ser negativos");
        }
        if (classificationCacheTtl.isNegative() || classificationCacheTtl.isZero()
                || pathCacheTtl.isNegative() || pathCacheTtl.isZero()
                || topologyCacheTtl.isNegative() || topologyCacheTtl.isZero()) {
            throw new IllegalArgumentException("Los TTL de clasificacion, camino y topologia deben ser positivos");
        }
        if (tunnelIntentTtl.isNegative() || tunnelIntentTtl.isZero()
                || tunnelPendingTtl.isNegative() || tunnelPendingTtl.isZero()
                || tunnelOperationLockTimeout.isNegative() || tunnelOperationLockTimeout.isZero()) {
            throw new IllegalArgumentException("Los TTL y el tiempo de bloqueo deben ser positivos");
        }
        if (tunnelOperationJournalMaxEntries <= 0 || openflowSuppressionIdleTimeoutSeconds < 0
                || openflowSuppressionHardTimeoutSeconds < 0 || openflowSuppressionPriority < 0
                || openflowSuppressionCookieBase < 0L) {
            throw new IllegalArgumentException("La configuracion de operaciones y supresion no es valida");
        }
        if (pairConsensusEvidenceTtl.isNegative() || pairConsensusEvidenceTtl.isZero()
                || activePairPolicyIdleTtl.isNegative() || activePairPolicyIdleTtl.isZero()
                || activePairPolicySweeperInterval.isNegative() || activePairPolicySweeperInterval.isZero()
                || pairPolicyHashVersion <= 0) {
            throw new IllegalArgumentException("La configuracion de consenso y politica de par no es valida");
        }
    }

    /**
     * Resuelve la direccion de clasificacion para los unicos conectores de host autorizados a generar PacketIn.
     * Los nombres logicos son publicados por el registro OpenFlow despues de correlacionar la identidad dinamica
     * del inventario con las IP de gestion configuradas. Los conectores de nucleo no forman parte de esta regla y
     * por tanto nunca habilitan la clasificacion de trafico.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Compara la identidad logica ECHO con su conector de host configurado.</li>
     *   <li>Compara la identidad logica FOXTROT con su conector de host configurado.</li>
     *   <li>Devuelve una direccion desconocida para cualquier otro conmutador o conector.</li>
     * </ol>
     *
     * @param switchName nombre logico resuelto del conmutador OpenFlow
     * @param connectorName nombre OVS resuelto del conector de ingreso
     * @return direccion de tunel asociada al host, o {@code UNKNOWN} cuando no es un ingreso autorizado
     */
    public FlowDirection resolveClassificationIngress(final String switchName, final String connectorName) {
        if ("ECHO".equals(switchName) && ovsEchoHostPortName.equals(connectorName)) {
            return FlowDirection.HEADEND_TO_TAILEND;
        }
        if ("FOXTROT".equals(switchName) && ovsFoxtrotHostPortName.equals(connectorName)) {
            return FlowDirection.TAILEND_TO_HEADEND;
        }
        return FlowDirection.UNKNOWN;
    }

    /**
     * Ejecuta la operacion {@code fromEnvironment} dentro del componente correspondiente.
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
    public static AppConfig fromEnvironment() {
        return from(System.getenv());
    }

    /**
     * Ejecuta la operacion {@code from} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param env valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public static AppConfig from(final Map<String, String> env) {
        final TunnelCreationMode mode = parseMode(value(
                env, "TUNNEL_CREATION_MODE", "DELEGATED_TUNNEL_UPDATE"));
        final URI dataUrl = uri(value(env, "SMA_ODL_RESTCONF_DATA_BASE_URL",
                value(env, "ODL_RESTCONF_DATA_BASE_URL", DEFAULT_DATA_URL)));
        final URI operationsUrl = uri(value(env, "ODL_RESTS_OPERATIONS_BASE_URL", DEFAULT_OPERATIONS_URL));
        final String classifierBase = stripTrailingSlash(
                value(env, "CLASSIFIER_BASE_URL", DEFAULT_CLASSIFIER_BASE_URL));
        final String classifierPath = value(env, "CLASSIFIER_CLASSIFY_PATH", DEFAULT_CLASSIFIER_PATH);
        final URI classifierEndpoint = uri(classifierBase + ensureLeadingSlash(classifierPath));

        final String bgplsTopologyId = value(env, "ODL_BGPLS_TOPOLOGY_ID", "sma-bgp-linkstate-topology");
        final String pcepTopologyId = value(env, "ODL_PCEP_TOPOLOGY_ID", "pcep-topology");
        final String graphName = value(env, "ODL_PATH_COMPUTATION_GRAPH_NAME", "ted://sma-bgp-linkstate-topology");

        final EndpointPair endpointPair = endpointPair(env);
        final TunnelDirection forward = new TunnelDirection(
                value(env, "SMA_FORWARD_DIRECTION_KEY", "lsr1_to_lsr4"),
                endpointPair.headend(),
                endpointPair.tailend());
        final TunnelDirection reverse = new TunnelDirection(
                value(env, "SMA_REVERSE_DIRECTION_KEY", "lsr4_to_lsr1"),
                endpointPair.tailend(),
                endpointPair.headend());

        final AppConfig config = new AppConfig(
                mode,
                dataUrl,
                operationsUrl,
                value(env, "ODL_USERNAME", "admin"),
                value(env, "ODL_PASSWORD", "admin"),
                classifierEndpoint,
                bgplsTopologyId,
                pcepTopologyId,
                graphName,
                endpointPair.headend(),
                endpointPair.tailend(),
                forward,
                reverse,
                value(env, "SMA_FORWARD_LSP_NAME", "sma-lsr1-lsr4-delegated"),
                value(env, "SMA_REVERSE_LSP_NAME", "sma-lsr4-lsr1-delegated"),
                value(env, "SMA_FORWARD_TUNNEL_INTERFACE", "tunnel-te110"),
                value(env, "SMA_REVERSE_TUNNEL_INTERFACE", "tunnel-te410"),
                IngressMapping.parse(value(env, "SMA_HEADEND_TO_TAILEND_INGRESS", "sma-ovs-pe1-echo|host-golf")),
                IngressMapping.parse(value(env, "SMA_TAILEND_TO_HEADEND_INGRESS", "sma-ovs-pe2-foxtrot|host-hotel")),
                seconds(env, "CLASSIFICATION_CACHE_TTL_SECONDS", 3600),
                seconds(env, "PATH_CACHE_TTL_SECONDS", 60),
                seconds(env, "TOPOLOGY_CACHE_TTL_SECONDS", 300),
                seconds(env, "ODL_OPERATIONAL_VALIDATION_TIMEOUT_SECONDS", 120),
                seconds(env, "HTTP_REQUEST_TIMEOUT_SECONDS", 10),
                longValue(env, "ODL_RETRY_INITIAL_DELAY_MS", 500),
                longValue(env, "ODL_RETRY_MAX_DELAY_MS", 5000),
                (int) longValue(env, "ODL_RETRY_JITTER_PERCENT", 20),
                (int) longValue(env, "ODL_TOPOLOGY_DISCOVERY_MAX_ATTEMPTS", 5),
                booleanValue(env, "SMA_OPENFLOW_BOOTSTRAP_ENABLED", true),
                (int) longValue(env, "SMA_OPENFLOW_TABLE_ID", 0),
                value(env, "SMA_OVS_ECHO_MGMT_IP", "172.21.121.15"),
                value(env, "SMA_OVS_ECHO_HOST_PORT_NAME", "host-golf"),
                value(env, "SMA_OVS_ECHO_CORE_PORT_NAME", "core-lsr1"),
                value(env, "SMA_OVS_FOXTROT_MGMT_IP", "172.21.121.16"),
                value(env, "SMA_OVS_FOXTROT_HOST_PORT_NAME", "host-hotel"),
                value(env, "SMA_OVS_FOXTROT_CORE_PORT_NAME", "core-lsr4"),
                (int) longValue(env, "SMA_OPENFLOW_ARP_PRIORITY", 300),
                (int) longValue(env, "SMA_OPENFLOW_IPV4_PRIORITY", 200),
                booleanValue(env, "SMA_OPENFLOW_INSTALL_DEFAULT_DROP", false),
                (int) longValue(env, "SMA_OPENFLOW_DEFAULT_DROP_PRIORITY", 0),
                parseTunnelUpdateScope(value(env, "SMA_TUNNEL_UPDATE_SCOPE", "OBSERVED_DIRECTION")),
                seconds(env, "SMA_TUNNEL_INTENT_TTL_SECONDS", 30),
                seconds(env, "SMA_TUNNEL_PENDING_TTL_SECONDS", 10),
                (int) longValue(env, "SMA_TUNNEL_OPERATION_JOURNAL_MAX_ENTRIES", 500),
                Duration.ofMillis(longValue(env, "SMA_TUNNEL_OPERATION_LOCK_TIMEOUT_MS", 5000)),
                booleanValue(env, "SMA_OPENFLOW_SUPPRESSION_ENABLED", false),
                (int) longValue(env, "SMA_OPENFLOW_SUPPRESSION_IDLE_TIMEOUT_SECONDS", 10),
                (int) longValue(env, "SMA_OPENFLOW_SUPPRESSION_HARD_TIMEOUT_SECONDS", 60),
                (int) longValue(env, "SMA_OPENFLOW_SUPPRESSION_PRIORITY", 250),
                longValue(env, "SMA_OPENFLOW_SUPPRESSION_COOKIE_BASE", 0x8ADC00L),
                parseOdlXmlBodyLogLevel(value(env, "SMA_ODL_XML_BODY_LOG_LEVEL", "DEBUG")),
                booleanValue(env, "SMA_PAIR_CONSENSUS_ENABLED", true),
                booleanValue(env, "SMA_PAIR_CONSENSUS_REQUIRE_BOTH_DIRECTIONS", true),
                seconds(env, "SMA_PAIR_CONSENSUS_EVIDENCE_TTL_SECONDS", 10),
                booleanValue(env, "SMA_PAIR_CONSENSUS_SINGLE_SIDE_PROVISIONAL_ENABLED", false),
                parsePairConsensusEqualPriorityAction(value(env,
                        "SMA_PAIR_CONSENSUS_EQUAL_PRIORITY_ACTION", "KEEP_CURRENT_OR_DEFER")),
                seconds(env, "SMA_ACTIVE_PAIR_POLICY_IDLE_TTL_SECONDS", 60),
                booleanValue(env, "SMA_ACTIVE_PAIR_POLICY_SWEEPER_ENABLED", true),
                seconds(env, "SMA_ACTIVE_PAIR_POLICY_SWEEPER_INTERVAL_SECONDS", 15),
                "PRIORITY_PREEMPT".equalsIgnoreCase(value(env, "SMA_PAIR_POLICY_PREEMPTION_MODE", "PRIORITY_PREEMPT")),
                (int) longValue(env, "SMA_PAIR_POLICY_HASH_VERSION", 1),
                parsePairPolicyLspApplicationScope(value(env,
                        "SMA_PAIR_POLICY_LSP_APPLICATION_SCOPE", "BIDIRECTIONAL_PAIR")),
                booleanValue(env, "SMA_LSP_APPLICATION_REQUIRE_ALL_DIRECTIONS", true),
                booleanValue(env, "SMA_LSP_APPLICATION_REAPPLY_ON_BANDWIDTH_MISMATCH", true),
                booleanValue(env, "SMA_LSP_APPLICATION_REAPPLY_ON_ERO_MISMATCH", true),
                booleanValue(env, "SMA_LSP_APPLICATION_REAPPLY_ON_PRIORITY_MISMATCH", true));
        LOG.info("application_configuration_loaded", "from",
                "Se cargo y valido la configuracion de la aplicacion desde el entorno",
                StructuredLogger.fields("tunnel_creation_mode", config.tunnelCreationMode(),
                        "odl_data_endpoint", config.odlRestconfDataBaseUrl(),
                        "odl_operations_endpoint", config.odlRestsOperationsBaseUrl(),
                        "classifier_endpoint", config.classifierEndpoint(),
                        "bgp_ls_topology_id", config.bgplsTopologyId(),
                        "pcep_topology_id", config.pcepTopologyId(),
                        "path_computation_graph", config.pathComputationGraphName(),
                        "forward_direction_key", config.headendToTailend().directionKey(),
                        "reverse_direction_key", config.tailendToHeadend().directionKey(),
                        "openflow_bootstrap_enabled", config.openflowBootstrapEnabled(),
                        "openflow_table_id", config.openflowTableId(),
                        "tunnel_update_scope", config.tunnelUpdateScope(),
                        "tunnel_intent_ttl_seconds", config.tunnelIntentTtl().toSeconds(),
                        "openflow_suppression_enabled", false,
                        "pair_consensus_require_both_directions", config.pairConsensusRequireBothDirections(),
                        "pair_consensus_evidence_ttl_seconds", config.pairConsensusEvidenceTtl().toSeconds(),
                        "active_pair_policy_idle_ttl_seconds", config.activePairPolicyIdleTtl().toSeconds(),
                        "odl_xml_body_log_level", config.odlXmlBodyLogLevel(),
                        "http_timeout_seconds", config.httpRequestTimeout().toSeconds(),
                        "topology_cache_ttl_seconds", config.topologyCacheTtl().toSeconds()));
        return config;
    }

    /**
     * Ejecuta la operacion {@code endpointPair} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param env valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private static EndpointPair endpointPair(final Map<String, String> env) {
        final TunnelEndpoint headend = new TunnelEndpoint(
                "lsr1",
                value(env, "SMA_HEADEND_RID", "11.11.11.11"),
                value(env, "SMA_HEADEND_PCC_NODE", "pcc://10.100.10.1"));
        final TunnelEndpoint tailend = new TunnelEndpoint(
                "lsr4",
                value(env, "SMA_TAILEND_RID", "14.14.14.14"),
                value(env, "SMA_TAILEND_PCC_NODE", "pcc://10.100.40.1"));
        return new EndpointPair(headend, tailend);
    }

    /**
     * Ejecuta la operacion {@code parseMode} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param rawValue valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    private static TunnelCreationMode parseMode(final String rawValue) {
        try {
            return TunnelCreationMode.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El valor de TUNNEL_CREATION_MODE no es reconocido: " + rawValue, e);
        }
    }

    private static TunnelUpdateScope parseTunnelUpdateScope(final String rawValue) {
        try {
            return TunnelUpdateScope.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El valor de SMA_TUNNEL_UPDATE_SCOPE no es reconocido: "
                    + rawValue, e);
        }
    }

    private static OdlXmlBodyLogLevel parseOdlXmlBodyLogLevel(final String rawValue) {
        try {
            return OdlXmlBodyLogLevel.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El valor de SMA_ODL_XML_BODY_LOG_LEVEL no es reconocido: "
                    + rawValue, e);
        }
    }

    private static PairConsensusEqualPriorityAction parsePairConsensusEqualPriorityAction(final String rawValue) {
        try {
            return PairConsensusEqualPriorityAction.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El valor de SMA_PAIR_CONSENSUS_EQUAL_PRIORITY_ACTION no es reconocido: "
                    + rawValue, e);
        }
    }

    private static PairPolicyLspApplicationScope parsePairPolicyLspApplicationScope(final String rawValue) {
        try {
            return PairPolicyLspApplicationScope.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El valor de SMA_PAIR_POLICY_LSP_APPLICATION_SCOPE no es reconocido: "
                    + rawValue, e);
        }
    }

    /**
     * Ejecuta la operacion {@code value} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param env valor requerido para ejecutar esta operacion
     *
     * @param key valor requerido para ejecutar esta operacion
     *
     * @param defaultValue valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private static String value(final Map<String, String> env, final String key, final String defaultValue) {
        final String value = env.get(key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    /**
     * Ejecuta la operacion {@code seconds} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param env valor requerido para ejecutar esta operacion
     *
     * @param key valor requerido para ejecutar esta operacion
     *
     * @param defaultValue valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private static Duration seconds(final Map<String, String> env, final String key, final long defaultValue) {
        return Duration.ofSeconds(longValue(env, key, defaultValue));
    }

    /**
     * Ejecuta la operacion {@code longValue} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param env valor requerido para ejecutar esta operacion
     *
     * @param key valor requerido para ejecutar esta operacion
     *
     * @param defaultValue valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private static long longValue(final Map<String, String> env, final String key, final long defaultValue) {
        final String value = env.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        final String normalized = value.trim();
        return normalized.startsWith("0x") || normalized.startsWith("0X")
                ? Long.decode(normalized) : Long.parseLong(normalized);
    }

    /**
     * Interpreta una variable booleana y rechaza valores ambiguos para impedir una configuracion
     * silenciosamente erronea.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Obtiene el valor configurado o aplica el valor predeterminado.</li>
     *   <li>Acepta exclusivamente las representaciones {@code true} y {@code false} sin distinguir mayusculas.</li>
     *   <li>Devuelve el valor booleano validado.</li>
     * </ol>
     *
     * @param env variables disponibles para la aplicacion
     * @param key nombre de la variable booleana
     * @param defaultValue valor usado cuando la variable no existe
     * @return valor booleano validado
     * @throws IllegalArgumentException si la variable contiene una representacion no reconocida
     */
    private static boolean booleanValue(
            final Map<String, String> env, final String key, final boolean defaultValue) {
        final String rawValue = env.get(key);
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(rawValue.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(rawValue.trim())) {
            return false;
        }
        throw new IllegalArgumentException("El valor booleano de " + key + " no es reconocido: " + rawValue);
    }

    /**
     * Ejecuta la operacion {@code uri} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param rawValue valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private static URI uri(final String rawValue) {
        return URI.create(rawValue);
    }

    /**
     * Ejecuta la operacion {@code ensureLeadingSlash} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param value valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    private static String ensureLeadingSlash(final String value) {
        return value.startsWith("/") ? value : "/" + value;
    }

    /**
     * Ejecuta la operacion {@code stripTrailingSlash} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param value valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private static String stripTrailingSlash(final String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /**
     * Define el par interno de extremos de tunel usado durante la construccion de la configuracion. Este record evita
     * devolver arreglos posicionales y conserva nombres semanticos para cabecera y cola.
     *
     * @param headend extremo que actua como origen logico del sentido directo
     * @param tailend extremo que actua como destino logico del sentido directo
     */
    private record EndpointPair(TunnelEndpoint headend, TunnelEndpoint tailend) {
    }
}
