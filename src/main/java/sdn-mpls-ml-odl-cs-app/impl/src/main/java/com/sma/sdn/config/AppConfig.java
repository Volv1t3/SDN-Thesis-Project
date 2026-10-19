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

package com.sma.sdn.config;

import com.sma.sdn.model.FlowDirection;
import com.sma.sdn.model.OdlXmlBodyLogLevel;
import com.sma.sdn.model.PairConsensusEqualPriorityAction;
import com.sma.sdn.model.PairPolicyLspApplicationScope;
import com.sma.sdn.model.TunnelDirection;
import com.sma.sdn.model.TunnelEndpoint;
import com.sma.sdn.observability.StructuredLogger;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * <p>Immutable runtime configuration for the SDN-MPLS-ML controller bundle. The record collects the endpoints,
 * credentials, topology identifiers, tunnel identities, cache lifetimes, OpenFlow settings, retry parameters, and
 * pair-policy controls required by the application services.</p>
 *
 * <p>{@link #fromEnvironment()} is the production entry point used by the provider. The resulting instance is shared
 * with HTTP clients, topology and path services, OpenFlow services, registries, tunnel coordination, and operational
 * state publication so that those collaborators use one validated configuration snapshot.</p>
 *
 * @param tunnelCreationMode strategy for applying tunnel changes
 * @param odlRestconfDataBaseUrl RESTCONF data-store base URI
 * @param odlRestsOperationsBaseUrl RESTS operations base URI
 * @param odlUsername OpenDaylight HTTP Basic user name
 * @param odlPassword OpenDaylight HTTP Basic password
 * @param classifierEndpoint fully resolved classifier API endpoint
 * @param bgplsTopologyId BGP-LS topology identifier
 * @param pcepTopologyId PCEP topology identifier
 * @param pathComputationGraphName graph name supplied to path computation
 * @param headend endpoint that originates the forward direction
 * @param tailend endpoint that terminates the forward direction
 * @param headendToTailend forward tunnel direction
 * @param tailendToHeadend reverse tunnel direction
 * @param forwardLspName delegated forward LSP name
 * @param reverseLspName delegated reverse LSP name
 * @param forwardTunnelInterface forward router tunnel-interface name
 * @param reverseTunnelInterface reverse router tunnel-interface name
 * @param classificationCacheTtl classifier-result cache lifetime
 * @param pathCacheTtl calculated-path cache lifetime
 * @param topologyCacheTtl topology freshness interval
 * @param operationalValidationTimeout operational validation timeout
 * @param httpRequestTimeout outbound HTTP request timeout
 * @param odlRetryInitialDelayMs initial OpenDaylight retry delay in milliseconds
 * @param odlRetryMaxDelayMs maximum OpenDaylight retry delay in milliseconds
 * @param odlRetryJitterPercent percentage of random retry-delay jitter
 * @param topologyDiscoveryMaxAttempts maximum topology discovery attempts
 * @param openflowBootstrapEnabled whether baseline OpenFlow provisioning is enabled
 * @param openflowTableId OpenFlow table used for controller-installed rules
 * @param ovsEchoManagementIp ECHO management IP
 * @param ovsEchoHostPortName ECHO host-facing port name
 * @param ovsEchoCorePortName ECHO core-facing port name
 * @param ovsFoxtrotManagementIp FOXTROT management IP
 * @param ovsFoxtrotHostPortName FOXTROT host-facing port name
 * @param ovsFoxtrotCorePortName FOXTROT core-facing port name
 * @param openflowArpPriority priority of baseline ARP rules
 * @param openflowIpv4Priority priority of baseline IPv4 rules
 * @param openflowInstallDefaultDrop whether bootstrap installs a default-drop rule
 * @param openflowDefaultDropPriority priority of the optional default-drop rule
 * @param tunnelIntentTtl accepted tunnel-intent lifetime
 * @param tunnelPendingTtl pending tunnel-operation lifetime
 * @param tunnelOperationLockTimeout per-tunnel lock-acquisition timeout
 * @param openflowSuppressionEnabled whether temporary suppression rules are enabled
 * @param openflowSuppressionIdleTimeoutSeconds suppression-rule idle timeout in seconds
 * @param openflowSuppressionHardTimeoutSeconds suppression-rule hard timeout in seconds
 * @param openflowSuppressionPriority suppression-rule priority
 * @param openflowSuppressionCookieBase base used to derive suppression cookies
 * @param odlXmlBodyLogLevel logging threshold for OpenDaylight XML bodies
 * @param pairConsensusEnabled whether pair-consensus coordination is enabled
 * @param pairConsensusRequireBothDirections whether consensus requires both traffic directions
 * @param pairConsensusEvidenceTtl pair-consensus evidence lifetime
 * @param pairConsensusSingleSideProvisionalEnabled whether one-sided evidence may be provisional
 * @param pairConsensusEqualPriorityAction equal-priority consensus resolution
 * @param activePairPolicyIdleTtl idle lifetime of an active pair policy
 * @param activePairPolicySweeperEnabled whether active-policy expiration sweeps are enabled
 * @param activePairPolicySweeperInterval interval between active-policy expiration sweeps
 * @param pairPolicyPriorityPreemptionEnabled whether higher-priority policies may preempt
 * @param pairPolicyHashVersion version included in pair-policy hashes
 * @param pairPolicyLspApplicationScope directions receiving an admitted pair policy
 * @param lspApplicationRequireAllDirections whether all selected directions must succeed
 */
public record AppConfig(

        /**
         <p>Supported strategy for applying tunnel changes.</p>
         */
        TunnelCreationMode tunnelCreationMode,
        /** <p>RESTCONF data-store base URI used by read and write data clients.</p> */
        URI odlRestconfDataBaseUrl,
        /** <p>RESTS operations base URI used to invoke controller RPCs.</p> */
        URI odlRestsOperationsBaseUrl,
        /** <p>User name sent in HTTP Basic authentication to OpenDaylight.</p> */
        String odlUsername,
        /** <p>Password sent in HTTP Basic authentication to OpenDaylight.</p> */
        String odlPassword,
        /** <p>Fully resolved classifier API endpoint.</p> */
        URI classifierEndpoint,
        /** <p>Identifier of the BGP-LS network topology.</p> */
        String bgplsTopologyId,
        /** <p>Identifier of the PCEP network topology.</p> */
        String pcepTopologyId,
        /** <p>Graph name supplied to path-computation requests.</p> */
        String pathComputationGraphName,
        /** <p>Endpoint that originates the configured forward direction.</p> */
        TunnelEndpoint headend,
        /** <p>Endpoint that terminates the configured forward direction.</p> */
        TunnelEndpoint tailend,
        /** <p>Forward tunnel direction from the headend to the tailend.</p> */
        TunnelDirection headendToTailend,
        /** <p>Reverse tunnel direction from the tailend to the headend.</p> */
        TunnelDirection tailendToHeadend,
        /** <p>Configured delegated LSP name for the forward direction.</p> */
        String forwardLspName,
        /** <p>Configured delegated LSP name for the reverse direction.</p> */
        String reverseLspName,
        /** <p>Router tunnel-interface name associated with the forward LSP.</p> */
        String forwardTunnelInterface,
        /** <p>Router tunnel-interface name associated with the reverse LSP.</p> */
        String reverseTunnelInterface,
        /** <p>Lifetime assigned to cached classifier results.</p> */
        Duration classificationCacheTtl,
        /** <p>Lifetime assigned to cached calculated paths.</p> */
        Duration pathCacheTtl,
        /** <p>Freshness interval for the discovered topology snapshot.</p> */
        Duration topologyCacheTtl,
        /** <p>Maximum interval allowed for operational validation.</p> */
        Duration operationalValidationTimeout,
        /** <p>Timeout applied to outbound HTTP requests.</p> */
        Duration httpRequestTimeout,
        /** <p>Initial delay, in milliseconds, for OpenDaylight retries.</p> */
        long odlRetryInitialDelayMs,
        /** <p>Maximum delay, in milliseconds, between OpenDaylight retries.</p> */
        long odlRetryMaxDelayMs,
        /** <p>Percentage of random jitter applied to retry delays.</p> */
        int odlRetryJitterPercent,
        /** <p>Maximum number of topology discovery attempts.</p> */
        int topologyDiscoveryMaxAttempts,
        /** <p>Whether startup provisions the baseline OpenFlow rules.</p> */
        boolean openflowBootstrapEnabled,
        /** <p>OpenFlow table used for bootstrap and suppression rules.</p> */
        int openflowTableId,
        /** <p>Management IP used to identify the ECHO Open vSwitch.</p> */
        String ovsEchoManagementIp,
        /** <p>Configured ECHO port name facing its attached host.</p> */
        String ovsEchoHostPortName,
        /** <p>Configured ECHO port name facing the MPLS core.</p> */
        String ovsEchoCorePortName,
        /** <p>Management IP used to identify the FOXTROT Open vSwitch.</p> */
        String ovsFoxtrotManagementIp,
        /** <p>Configured FOXTROT port name facing its attached host.</p> */
        String ovsFoxtrotHostPortName,
        /** <p>Configured FOXTROT port name facing the MPLS core.</p> */
        String ovsFoxtrotCorePortName,
        /** <p>Priority assigned to baseline ARP forwarding rules.</p> */
        int openflowArpPriority,
        /** <p>Priority assigned to baseline IPv4 forwarding rules.</p> */
        int openflowIpv4Priority,
        /** <p>Whether bootstrap installs a table-miss drop rule.</p> */
        boolean openflowInstallDefaultDrop,
        /** <p>Priority assigned to the optional default-drop rule.</p> */
        int openflowDefaultDropPriority,
        /** <p>Lifetime assigned to accepted tunnel intents.</p> */
        Duration tunnelIntentTtl,
        /** <p>Lifetime assigned to pending tunnel operations.</p> */
        Duration tunnelPendingTtl,
        /** <p>Maximum time spent waiting for a per-tunnel operation lock.</p> */
        Duration tunnelOperationLockTimeout,
        /** <p>Whether temporary OpenFlow suppression rules may be installed.</p> */
        boolean openflowSuppressionEnabled,
        /** <p>Idle timeout, in seconds, for suppression rules.</p> */
        int openflowSuppressionIdleTimeoutSeconds,
        /** <p>Hard timeout, in seconds, for suppression rules.</p> */
        int openflowSuppressionHardTimeoutSeconds,
        /** <p>Priority assigned to suppression rules.</p> */
        int openflowSuppressionPriority,
        /** <p>Base cookie from which deterministic suppression cookies are derived.</p> */
        long openflowSuppressionCookieBase,
        /** <p>Logging threshold for OpenDaylight XML bodies.</p> */
        OdlXmlBodyLogLevel odlXmlBodyLogLevel,
        /** <p>Whether bidirectional classification evidence is coordinated as a pair.</p> */
        boolean pairConsensusEnabled,
        /** <p>Whether pair consensus requires evidence from both traffic directions.</p> */
        boolean pairConsensusRequireBothDirections,
        /** <p>Validity interval for pair-consensus evidence.</p> */
        Duration pairConsensusEvidenceTtl,
        /** <p>Whether one-sided evidence can provisionally select a pair policy.</p> */
        boolean pairConsensusSingleSideProvisionalEnabled,
        /** <p>Resolution applied when equal-priority evidence proposes different policies.</p> */
        PairConsensusEqualPriorityAction pairConsensusEqualPriorityAction,
        /** <p>Idle lifetime after which an active pair policy expires.</p> */
        Duration activePairPolicyIdleTtl,
        /** <p>Whether the active-pair-policy expiration sweep is enabled.</p> */
        boolean activePairPolicySweeperEnabled,
        /** <p>Interval between active-pair-policy expiration sweeps.</p> */
        Duration activePairPolicySweeperInterval,
        /** <p>Whether a higher-priority candidate may preempt the active pair policy.</p> */
        boolean pairPolicyPriorityPreemptionEnabled,
        /** <p>Version incorporated into deterministic pair-policy hashes.</p> */
        int pairPolicyHashVersion,
        /** <p>Directions to which an admitted pair policy is applied.</p> */
        PairPolicyLspApplicationScope pairPolicyLspApplicationScope,
        /** <p>Whether applying a pair policy succeeds only when all selected directions succeed.</p> */
        boolean lspApplicationRequireAllDirections) {

    /** <p>Structured event logger for configuration loading.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(AppConfig.class);
    /** <p>Fallback RESTCONF data-store base URL.</p> */
    private static final String DEFAULT_DATA_URL = "http://172.21.121.100:8182/restconf/data";
    /** <p>Fallback RESTS operations base URL.</p> */
    private static final String DEFAULT_OPERATIONS_URL = "http://172.21.121.100:8181/rests/operations";
    /** <p>Fallback HTTP base URL for the classifier service.</p> */
    private static final String DEFAULT_CLASSIFIER_BASE_URL = "http://127.0.0.1:33761";
    /** <p>Fallback classifier path appended to the configured classifier base URL.</p> */
    private static final String DEFAULT_CLASSIFIER_PATH = "/api/v1/classify";

    /**
     * <p>Validates a complete configuration snapshot before the record becomes available to application services.
     * Required reference values must be present, time-to-live values must be positive, and OpenFlow,
     * suppression, and pair-policy numeric settings must satisfy their local ranges.</p>
     *
     * <p>Validation proceeds in the following order:</p>
     * <ol>
     *   <li>Require every reference-valued setting consumed without a null fallback by downstream services.</li>
     *   <li>Reject negative OpenFlow table identifiers or priorities.</li>
     *   <li>Require positive cache, tunnel-operation, consensus, and active-policy durations.</li>
     *   <li>Require a positive policy-hash version and non-negative suppression settings.</li>
     * </ol>
     *
     * <p>The validated record is subsequently injected into the provider's clients and services as their shared,
     * immutable runtime contract.</p>
     *
     * @param tunnelCreationMode strategy for applying tunnel changes
     * @param odlRestconfDataBaseUrl RESTCONF data-store base URI
     * @param odlRestsOperationsBaseUrl RESTS operations base URI
     * @param odlUsername OpenDaylight HTTP Basic user name
     * @param odlPassword OpenDaylight HTTP Basic password
     * @param classifierEndpoint fully resolved classifier API endpoint
     * @param bgplsTopologyId BGP-LS topology identifier
     * @param pcepTopologyId PCEP topology identifier
     * @param pathComputationGraphName graph name supplied to path computation
     * @param headend endpoint that originates the forward direction
     * @param tailend endpoint that terminates the forward direction
     * @param headendToTailend forward tunnel direction
     * @param tailendToHeadend reverse tunnel direction
     * @param forwardLspName delegated forward LSP name
     * @param reverseLspName delegated reverse LSP name
     * @param forwardTunnelInterface forward router tunnel-interface name
     * @param reverseTunnelInterface reverse router tunnel-interface name
     * @param classificationCacheTtl classifier-result cache lifetime
     * @param pathCacheTtl calculated-path cache lifetime
     * @param topologyCacheTtl topology freshness interval
     * @param operationalValidationTimeout operational validation timeout
     * @param httpRequestTimeout outbound HTTP request timeout
     * @param odlRetryInitialDelayMs initial OpenDaylight retry delay in milliseconds
     * @param odlRetryMaxDelayMs maximum OpenDaylight retry delay in milliseconds
     * @param odlRetryJitterPercent percentage of random retry-delay jitter
     * @param topologyDiscoveryMaxAttempts maximum topology discovery attempts
     * @param openflowBootstrapEnabled whether baseline OpenFlow provisioning is enabled
     * @param openflowTableId OpenFlow table used for controller-installed rules
     * @param ovsEchoManagementIp ECHO management IP
     * @param ovsEchoHostPortName ECHO host-facing port name
     * @param ovsEchoCorePortName ECHO core-facing port name
     * @param ovsFoxtrotManagementIp FOXTROT management IP
     * @param ovsFoxtrotHostPortName FOXTROT host-facing port name
     * @param ovsFoxtrotCorePortName FOXTROT core-facing port name
     * @param openflowArpPriority priority of baseline ARP rules
     * @param openflowIpv4Priority priority of baseline IPv4 rules
     * @param openflowInstallDefaultDrop whether bootstrap installs a default-drop rule
     * @param openflowDefaultDropPriority priority of the optional default-drop rule
     * @param tunnelIntentTtl accepted tunnel-intent lifetime
     * @param tunnelPendingTtl pending tunnel-operation lifetime
     * @param tunnelOperationLockTimeout per-tunnel lock-acquisition timeout
     * @param openflowSuppressionEnabled whether temporary suppression rules are enabled
     * @param openflowSuppressionIdleTimeoutSeconds suppression-rule idle timeout in seconds
     * @param openflowSuppressionHardTimeoutSeconds suppression-rule hard timeout in seconds
     * @param openflowSuppressionPriority suppression-rule priority
     * @param openflowSuppressionCookieBase base used to derive suppression cookies
     * @param odlXmlBodyLogLevel logging threshold for OpenDaylight XML bodies
     * @param pairConsensusEnabled whether pair-consensus coordination is enabled
     * @param pairConsensusRequireBothDirections whether consensus requires both traffic directions
     * @param pairConsensusEvidenceTtl pair-consensus evidence lifetime
     * @param pairConsensusSingleSideProvisionalEnabled whether one-sided evidence may be provisional
     * @param pairConsensusEqualPriorityAction equal-priority consensus resolution
     * @param activePairPolicyIdleTtl idle lifetime of an active pair policy
     * @param activePairPolicySweeperEnabled whether active-policy expiration sweeps are enabled
     * @param activePairPolicySweeperInterval interval between active-policy expiration sweeps
     * @param pairPolicyPriorityPreemptionEnabled whether higher-priority policies may preempt
     * @param pairPolicyHashVersion version included in pair-policy hashes
     * @param pairPolicyLspApplicationScope directions receiving an admitted pair policy
     * @param lspApplicationRequireAllDirections whether all selected directions must succeed
     *
     * @throws NullPointerException if a required reference-valued setting is {@code null}
     * @throws IllegalArgumentException if a validated numeric or duration setting is outside its accepted range
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
        if (openflowSuppressionIdleTimeoutSeconds < 0
                || openflowSuppressionHardTimeoutSeconds < 0 || openflowSuppressionPriority < 0
                || openflowSuppressionCookieBase < 0L) {
            throw new IllegalArgumentException("La configuracion de supresion no es valida");
        }
        if (pairConsensusEvidenceTtl.isNegative() || pairConsensusEvidenceTtl.isZero()
                || activePairPolicyIdleTtl.isNegative() || activePairPolicyIdleTtl.isZero()
                || activePairPolicySweeperInterval.isNegative() || activePairPolicySweeperInterval.isZero()
                || pairPolicyHashVersion <= 0) {
            throw new IllegalArgumentException("La configuracion de consenso y politica de par no es valida");
        }
    }

    /**
     * <p>Resolves the classification direction for the two host-facing OpenFlow ingress points. Matching is exact and
     * case-sensitive: the logical switch must be {@code ECHO} or {@code FOXTROT}, and the connector must equal the
     * corresponding configured host-port name.</p>
     *
     * <p>The method evaluates ingress in the following order:</p>
     * <ol>
     *   <li>Match {@code ECHO} and {@link #ovsEchoHostPortName()} to the headend-to-tailend direction.</li>
     *   <li>Match {@code FOXTROT} and {@link #ovsFoxtrotHostPortName()} to the tailend-to-headend direction.</li>
     *   <li>Return {@link FlowDirection#UNKNOWN} when neither authorized host-facing ingress pair matches.</li>
     * </ol>
     *
     * <p>{@code DirectionRegistry} and {@code PacketInFeatureExtractor} consume this result when the incoming context
     * does not already identify a direction.</p>
     *
     * @param switchName logical OpenFlow switch name, which may be {@code null}
     * @param connectorName resolved connector name, which may be {@code null}
     * @return the direction associated with the authorized host-facing port, or {@link FlowDirection#UNKNOWN}
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
     * <p>Loads the process environment into a validated configuration snapshot.</p>
     *
     * <p>The method performs the following steps:</p>
     * <ol>
     *   <li>Obtain the current process environment through {@link System#getenv()}.</li>
     *   <li>Delegate parsing, default selection, validation, and safe configuration logging to {@link #from(Map)}.</li>
     * </ol>
     *
     * <p>The provider calls this method during bundle initialization before constructing HTTP clients and services.</p>
     *
     * @return a validated configuration populated from the current process environment
     * @throws IllegalArgumentException if an environment value cannot be parsed or violates validation constraints
     */
    public static AppConfig fromEnvironment() {
        return from(System.getenv());
    }

    /**
     * <p>Builds a configuration snapshot from a caller-supplied environment map. Missing or blank entries use the
     * defaults declared by this loader; present entries are trimmed and converted to their target types.</p>
     *
     * <p>The loader performs the following steps:</p>
     * <ol>
     *   <li>Parse the tunnel mode and construct the RESTCONF, RESTS, and classifier URIs.</li>
     *   <li>Resolve topology identifiers, endpoint router/PCC identities, and both {@link TunnelDirection} values.</li>
     *   <li>Parse cache and operation durations, retry controls, and OpenFlow settings.</li>
     *   <li>Parse tunnel, suppression, consensus, preemption, and LSP-application policy settings.</li>
     *   <li>Construct the record, thereby running compact-constructor validation.</li>
     *   <li>Log non-secret configuration metadata and return the validated snapshot.</li>
     * </ol>
     *
     * <p>This overload supports deterministic tests and configuration injection; {@link #fromEnvironment()} supplies
     * it with the real process environment in production.</p>
     *
     * @param env environment-style key/value map used as the configuration source
     * @return a validated configuration snapshot containing defaults for missing or blank entries
     * @throws NullPointerException if {@code env} is {@code null} or a required constructed value is {@code null}
     * @throws IllegalArgumentException if an enum, URI, number, boolean, duration, or validated range
     *         is invalid
     */
    public static AppConfig from(final Map<String, String> env) {

        /*The preemption models, and required parameters for it are not overriden in the environment variables and therefore use
        * the variables defined as defaults here*/
        final TunnelCreationMode mode = parseMode(value(
                env, "TUNNEL_CREATION_MODE", "DELEGATED_TUNNEL_UPDATE"));
        /*Section dedicated to resolving clasifier endpoints*/
        final URI dataUrl = uri(value(env, "SMA_ODL_RESTCONF_DATA_BASE_URL",
                value(env, "ODL_RESTCONF_DATA_BASE_URL", DEFAULT_DATA_URL)));
        final URI operationsUrl = uri(value(env, "ODL_RESTS_OPERATIONS_BASE_URL", DEFAULT_OPERATIONS_URL));
        final String classifierBase = stripTrailingSlash(
                value(env, "CLASSIFIER_BASE_URL", DEFAULT_CLASSIFIER_BASE_URL));
        final String classifierPath = value(env, "CLASSIFIER_CLASSIFY_PATH", DEFAULT_CLASSIFIER_PATH);
        final URI classifierEndpoint = uri(classifierBase + ensureLeadingSlash(classifierPath));


        /*Section focused on reoslving bgpls pcep and graph names. Since these use the configuration RESTCONF calls
        * configured since testing and validation the default values always correspond to the ones used in the environment*/
        final String bgplsTopologyId = value(env, "ODL_BGPLS_TOPOLOGY_ID", "sma-bgp-linkstate-topology");
        final String pcepTopologyId = value(env, "ODL_PCEP_TOPOLOGY_ID", "pcep-topology");
        final String graphName = value(env, "ODL_PATH_COMPUTATION_GRAPH_NAME", "ted://sma-bgp-linkstate-topology");


        /*This resolves the two tunnel directions. We establish these to determine directly the tunnel directions that the
        * process has to modify*/
        final EndpointPair endpointPair = endpointPair(env);

        /*The endpoint pair gives us the lsr1 and lsr4 headend and tail end as only the routers. This next section creates the
        * specific directionality keys, the TunnelDirection records are important to variuos systems including the directionality records.
        * These Records are used to represent the tunnel directions of the system head end to tail end and viceversa internally. Which can be
        * used to map policies applied on either direction during the consensus mechanism*/
        final TunnelDirection forward = new TunnelDirection(
                value(env, "SMA_FORWARD_DIRECTION_KEY", "lsr1_to_lsr4"),
                endpointPair.headend(),
                endpointPair.tailend());
        final TunnelDirection reverse = new TunnelDirection(
                value(env, "SMA_REVERSE_DIRECTION_KEY", "lsr4_to_lsr1"),
                endpointPair.tailend(),
                endpointPair.headend());

        // Each trailing comment names the record component receiving that positional argument.
        // Environment keys and fallback values remain visible in the corresponding expressions;
        // variables above are resolved before this call, and the compact constructor validates the result.
        final AppConfig config = new AppConfig(
                mode, // tunnelCreationMode: TUNNEL_CREATION_MODE, parsed above
                dataUrl, // odlRestconfDataBaseUrl: SMA_ODL_RESTCONF_DATA_BASE_URL, then ODL_RESTCONF_DATA_BASE_URL
                operationsUrl, // odlRestsOperationsBaseUrl: ODL_RESTS_OPERATIONS_BASE_URL, parsed above
                value(env, "ODL_USERNAME", "admin"), // odlUsername
                value(env, "ODL_PASSWORD", "admin"), // odlPassword
                classifierEndpoint, // classifierEndpoint: CLASSIFIER_BASE_URL and CLASSIFIER_CLASSIFY_PATH
                bgplsTopologyId, // bgplsTopologyId: ODL_BGPLS_TOPOLOGY_ID, resolved above
                pcepTopologyId, // pcepTopologyId: ODL_PCEP_TOPOLOGY_ID, resolved above
                graphName, // pathComputationGraphName: ODL_PATH_COMPUTATION_GRAPH_NAME, resolved above
                endpointPair.headend(), // headend: SMA_HEADEND_RID and SMA_HEADEND_PCC_NODE
                endpointPair.tailend(), // tailend: SMA_TAILEND_RID and SMA_TAILEND_PCC_NODE
                forward, // headendToTailend: SMA_FORWARD_DIRECTION_KEY and endpointPair
                reverse, // tailendToHeadend: SMA_REVERSE_DIRECTION_KEY and endpointPair
                value(env, "SMA_FORWARD_LSP_NAME", "sma-lsr1-lsr4-delegated"), // forwardLspName
                value(env, "SMA_REVERSE_LSP_NAME", "sma-lsr4-lsr1-delegated"), // reverseLspName
                value(env, "SMA_FORWARD_TUNNEL_INTERFACE", "tunnel-te110"), // forwardTunnelInterface
                value(env, "SMA_REVERSE_TUNNEL_INTERFACE", "tunnel-te410"), // reverseTunnelInterface
                seconds(env, "CLASSIFICATION_CACHE_TTL_SECONDS", 3600), // classificationCacheTtl
                seconds(env, "PATH_CACHE_TTL_SECONDS", 60), // pathCacheTtl
                seconds(env, "TOPOLOGY_CACHE_TTL_SECONDS", 300), // topologyCacheTtl
                seconds(env, "ODL_OPERATIONAL_VALIDATION_TIMEOUT_SECONDS", 120), // operationalValidationTimeout
                seconds(env, "HTTP_REQUEST_TIMEOUT_SECONDS", 10), // httpRequestTimeout
                longValue(env, "ODL_RETRY_INITIAL_DELAY_MS", 500), // odlRetryInitialDelayMs
                longValue(env, "ODL_RETRY_MAX_DELAY_MS", 5000), // odlRetryMaxDelayMs
                (int) longValue(env, "ODL_RETRY_JITTER_PERCENT", 20), // odlRetryJitterPercent
                (int) longValue(env, "ODL_TOPOLOGY_DISCOVERY_MAX_ATTEMPTS", 5), // topologyDiscoveryMaxAttempts
                booleanValue(env, "SMA_OPENFLOW_BOOTSTRAP_ENABLED", true), // openflowBootstrapEnabled
                (int) longValue(env, "SMA_OPENFLOW_TABLE_ID", 0), // openflowTableId
                value(env, "SMA_OVS_ECHO_MGMT_IP", "172.21.121.15"), // ovsEchoManagementIp
                value(env, "SMA_OVS_ECHO_HOST_PORT_NAME", "host-golf"), // ovsEchoHostPortName
                value(env, "SMA_OVS_ECHO_CORE_PORT_NAME", "core-lsr1"), // ovsEchoCorePortName
                value(env, "SMA_OVS_FOXTROT_MGMT_IP", "172.21.121.16"), // ovsFoxtrotManagementIp
                value(env, "SMA_OVS_FOXTROT_HOST_PORT_NAME", "host-hotel"), // ovsFoxtrotHostPortName
                value(env, "SMA_OVS_FOXTROT_CORE_PORT_NAME", "core-lsr4"), // ovsFoxtrotCorePortName
                (int) longValue(env, "SMA_OPENFLOW_ARP_PRIORITY", 300), // openflowArpPriority
                (int) longValue(env, "SMA_OPENFLOW_IPV4_PRIORITY", 200), // openflowIpv4Priority
                booleanValue(env, "SMA_OPENFLOW_INSTALL_DEFAULT_DROP", false), // openflowInstallDefaultDrop
                (int) longValue(env, "SMA_OPENFLOW_DEFAULT_DROP_PRIORITY", 0), // openflowDefaultDropPriority
                seconds(env, "SMA_TUNNEL_INTENT_TTL_SECONDS", 30), // tunnelIntentTtl
                seconds(env, "SMA_TUNNEL_PENDING_TTL_SECONDS", 10), // tunnelPendingTtl
                Duration.ofMillis(longValue(env, "SMA_TUNNEL_OPERATION_LOCK_TIMEOUT_MS", 5000)), // tunnelOperationLockTimeout
                booleanValue(env, "SMA_OPENFLOW_SUPPRESSION_ENABLED", false), // openflowSuppressionEnabled
                (int) longValue(env, "SMA_OPENFLOW_SUPPRESSION_IDLE_TIMEOUT_SECONDS", 10), // openflowSuppressionIdleTimeoutSeconds
                (int) longValue(env, "SMA_OPENFLOW_SUPPRESSION_HARD_TIMEOUT_SECONDS", 60), // openflowSuppressionHardTimeoutSeconds
                (int) longValue(env, "SMA_OPENFLOW_SUPPRESSION_PRIORITY", 250), // openflowSuppressionPriority
                longValue(env, "SMA_OPENFLOW_SUPPRESSION_COOKIE_BASE", 0x8ADC00L), // openflowSuppressionCookieBase
                parseOdlXmlBodyLogLevel(value(env, "SMA_ODL_XML_BODY_LOG_LEVEL", "DEBUG")), // odlXmlBodyLogLevel
                booleanValue(env, "SMA_PAIR_CONSENSUS_ENABLED", true), // pairConsensusEnabled
                booleanValue(env, "SMA_PAIR_CONSENSUS_REQUIRE_BOTH_DIRECTIONS", true), // pairConsensusRequireBothDirections
                seconds(env, "SMA_PAIR_CONSENSUS_EVIDENCE_TTL_SECONDS", 10), // pairConsensusEvidenceTtl
                booleanValue(env, "SMA_PAIR_CONSENSUS_SINGLE_SIDE_PROVISIONAL_ENABLED", false), // pairConsensusSingleSideProvisionalEnabled
                parsePairConsensusEqualPriorityAction(value(env,
                        "SMA_PAIR_CONSENSUS_EQUAL_PRIORITY_ACTION", "KEEP_CURRENT_OR_DEFER")), // pairConsensusEqualPriorityAction
                seconds(env, "SMA_ACTIVE_PAIR_POLICY_IDLE_TTL_SECONDS", 60), // activePairPolicyIdleTtl
                booleanValue(env, "SMA_ACTIVE_PAIR_POLICY_SWEEPER_ENABLED", true), // activePairPolicySweeperEnabled
                seconds(env, "SMA_ACTIVE_PAIR_POLICY_SWEEPER_INTERVAL_SECONDS", 15), // activePairPolicySweeperInterval
                "PRIORITY_PREEMPT".equalsIgnoreCase(value(env, "SMA_PAIR_POLICY_PREEMPTION_MODE", "PRIORITY_PREEMPT")), // pairPolicyPriorityPreemptionEnabled
                (int) longValue(env, "SMA_PAIR_POLICY_HASH_VERSION", 1), // pairPolicyHashVersion
                parsePairPolicyLspApplicationScope(value(env,
                        "SMA_PAIR_POLICY_LSP_APPLICATION_SCOPE", "BIDIRECTIONAL_PAIR")), // pairPolicyLspApplicationScope
                booleanValue(env, "SMA_LSP_APPLICATION_REQUIRE_ALL_DIRECTIONS", true)); // lspApplicationRequireAllDirections
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
     * <p>Constructs the fixed logical headend and tailend with configurable router IDs and PCEP node identifiers.</p>
     *
     * <p>The helper performs the following steps:</p>
     * <ol>
     *   <li>Create the {@code lsr1} headend using {@code SMA_HEADEND_RID} and {@code SMA_HEADEND_PCC_NODE}.</li>
     *   <li>Create the {@code lsr4} tailend using {@code SMA_TAILEND_RID} and {@code SMA_TAILEND_PCC_NODE}.</li>
     *   <li>Return both endpoints in a named {@link EndpointPair}.</li>
     * </ol>
     *
     * <p>{@link #from(Map)} uses the pair to build forward and reverse tunnel directions.</p>
     *
     * @param env environment-style values from which endpoint identifiers are read
     * @return the configured logical headend and tailend
     * @throws NullPointerException if {@code env} is {@code null}
     */
    private static EndpointPair endpointPair(final Map<String, String> env) {

        /*These two are fixed logical records that are created based on the topology. The default keys match the configured
        * parameters fo the network but the environment variables can be overriden at deployment time to configure different
        * router ids*/

        /*One note about adaptability I noticed: these two systems hard code the logical names of the endpoints, we should
        * look into this.*/
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
     * <p>Parses the configured tunnel-creation mode using locale-independent case normalization.</p>
     *
     * <p>The helper performs the following steps:</p>
     * <ol>
     *   <li>Trim the supplied text and convert it to upper case with {@link Locale#ROOT}.</li>
     *   <li>Resolve the normalized text with {@link TunnelCreationMode#valueOf(String)}.</li>
     *   <li>Wrap an unsupported value with an error that names {@code TUNNEL_CREATION_MODE}.</li>
     * </ol>
     *
     * @param rawValue textual tunnel-creation mode
     * @return the matching tunnel-creation mode
     * @throws NullPointerException if {@code rawValue} is {@code null}
     * @throws IllegalArgumentException if the normalized value does not name a supported mode
     */
    private static TunnelCreationMode parseMode(final String rawValue) {
        /*This mechanism is used to parse the incoming text, while the validation detects that it should be in delegated mode,
        * if it is in any other mode then it ignores this value and assumes delegation mode*/
        try {
            return TunnelCreationMode.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El valor de TUNNEL_CREATION_MODE no es reconocido: " + rawValue, e);
        }
    }

    /**
     * <p>Parses the logging threshold applied to OpenDaylight XML request and response bodies.</p>
     *
     * <p>The helper performs the following steps:</p>
     * <ol>
     *   <li>Trim and upper-case the supplied text with {@link Locale#ROOT}.</li>
     *   <li>Resolve the normalized text with {@link OdlXmlBodyLogLevel#valueOf(String)}.</li>
     *   <li>Wrap an unsupported value with an error that names {@code SMA_ODL_XML_BODY_LOG_LEVEL}.</li>
     * </ol>
     *
     * @param rawValue textual XML-body log level
     * @return the matching XML-body log level
     * @throws NullPointerException if {@code rawValue} is {@code null}
     * @throws IllegalArgumentException if the normalized value does not name a supported level
     */
    private static OdlXmlBodyLogLevel parseOdlXmlBodyLogLevel(final String rawValue) {
        /*Similar to the other cases, it is used directly to LOG the XML body in DEBUG mode, hiding sensitive information
        * but allowing the application in DEBUG mode to also see the DEBUG of the requests for validation*/
        try {
            return OdlXmlBodyLogLevel.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El valor de SMA_ODL_XML_BODY_LOG_LEVEL no es reconocido: "
                    + rawValue, e);
        }
    }

    /**
     * <p>Parses the action used when pair-consensus evidence has equal priority.</p>
     *
     * <p>The helper performs the following steps:</p>
     * <ol>
     *   <li>Trim and upper-case the supplied text with {@link Locale#ROOT}.</li>
     *   <li>Resolve the text with {@link PairConsensusEqualPriorityAction#valueOf(String)}.</li>
     *   <li>Wrap an unsupported value with an error naming its environment key.</li>
     * </ol>
     *
     * @param rawValue textual equal-priority action
     * @return the matching equal-priority action
     * @throws NullPointerException if {@code rawValue} is {@code null}
     * @throws IllegalArgumentException if the normalized value does not name a supported action
     */
    private static PairConsensusEqualPriorityAction parsePairConsensusEqualPriorityAction(final String rawValue) {
        try {
            return PairConsensusEqualPriorityAction.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El valor de SMA_PAIR_CONSENSUS_EQUAL_PRIORITY_ACTION no es reconocido: "
                    + rawValue, e);
        }
    }

    /**
     * <p>Parses the configured direction scope for applying an admitted pair policy to LSPs.</p>
     *
     * <p>The helper performs the following steps:</p>
     * <ol>
     *   <li>Trim and upper-case the supplied text with {@link Locale#ROOT}.</li>
     *   <li>Resolve the text with {@link PairPolicyLspApplicationScope#valueOf(String)}.</li>
     *   <li>Wrap an unsupported value with an error naming its environment key.</li>
     * </ol>
     *
     * @param rawValue textual LSP-application scope
     * @return the matching pair-policy LSP-application scope
     * @throws NullPointerException if {@code rawValue} is {@code null}
     * @throws IllegalArgumentException if the normalized value does not name a supported scope
     */
    private static PairPolicyLspApplicationScope parsePairPolicyLspApplicationScope(final String rawValue) {
        try {
            return PairPolicyLspApplicationScope.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El valor de SMA_PAIR_POLICY_LSP_APPLICATION_SCOPE no es reconocido: "
                    + rawValue, e);
        }
    }

    /**
     * <p>Reads a trimmed non-blank configuration value or selects its default.</p>
     *
     * <p>The helper performs the following steps:</p>
     * <ol>
     *   <li>Look up {@code key} in the supplied environment map.</li>
     *   <li>Return {@code defaultValue} when the entry is absent or blank.</li>
     *   <li>Otherwise trim surrounding whitespace and return the configured value.</li>
     * </ol>
     *
     * <p>String, URI, enum, and endpoint loaders use this helper to apply consistent blank-value semantics.</p>
     *
     * @param env environment-style configuration map
     * @param key configuration key to read
     * @param defaultValue value returned when the key is absent or blank
     * @return the trimmed configured value or {@code defaultValue}
     * @throws NullPointerException if {@code env} is {@code null}
     */
    private static String value(final Map<String, String> env, final String key, final String defaultValue) {
        final String value = env.get(key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    /**
     * <p>Loads an integral seconds value and represents it as a {@link Duration}.</p>
     *
     * <p>The helper performs the following steps:</p>
     * <ol>
     *   <li>Delegate decimal or hexadecimal integer parsing and default selection to
     *       {@link #longValue(Map, String, long)}.</li>
     *   <li>Create a duration containing the parsed number of seconds.</li>
     * </ol>
     *
     * @param env environment-style configuration map
     * @param key configuration key containing a seconds value
     * @param defaultValue seconds used when the entry is absent or blank
     * @return a duration containing the configured or default number of seconds
     * @throws NullPointerException if {@code env} is {@code null}
     * @throws NumberFormatException if the configured value is not a valid integer
     */
    private static Duration seconds(final Map<String, String> env, final String key, final long defaultValue) {
        return Duration.ofSeconds(longValue(env, key, defaultValue));
    }

    /**
     * <p>Loads a signed long value, accepting decimal text and hexadecimal text prefixed with {@code 0x}.</p>
     *
     * <p>The helper performs the following steps:</p>
     * <ol>
     *   <li>Look up the requested key and return the default when the entry is absent or blank.</li>
     *   <li>Trim the configured text.</li>
     *   <li>Use {@link Long#decode(String)} for hexadecimal prefixes; otherwise use
     *       {@link Long#parseLong(String)}.</li>
     * </ol>
     *
     * <p>The loader uses hexadecimal support for values such as the suppression-cookie base.</p>
     *
     * @param env environment-style configuration map
     * @param key configuration key containing the integer
     * @param defaultValue value returned when the entry is absent or blank
     * @return the parsed or default long value
     * @throws NullPointerException if {@code env} is {@code null}
     * @throws NumberFormatException if the configured value is not a supported long representation
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
     * <p>Parses a strict boolean setting so that ambiguous text cannot silently disable or enable behavior.</p>
     *
     * <p>The helper performs the following steps:</p>
     * <ol>
     *   <li>Return the default when the entry is absent or blank.</li>
     *   <li>Accept trimmed {@code true} or {@code false} text without regard to case.</li>
     *   <li>Reject every other representation with an error that identifies the configuration key.</li>
     * </ol>
     *
     * @param env environment-style configuration map
     * @param key configuration key containing the boolean
     * @param defaultValue value returned when the entry is absent or blank
     * @return the parsed or default boolean value
     * @throws NullPointerException if {@code env} is {@code null}
     * @throws IllegalArgumentException if the entry is neither {@code true} nor {@code false}
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
     * <p>Converts configuration text to a URI without performing network access.</p>
     *
     * <p>The helper performs the following steps:</p>
     * <ol>
     *   <li>Pass the supplied text to {@link URI#create(String)}.</li>
     *   <li>Return the resulting immutable URI.</li>
     * </ol>
     *
     * @param rawValue URI text to parse
     * @return the parsed URI
     * @throws NullPointerException if {@code rawValue} is {@code null}
     * @throws IllegalArgumentException if {@code rawValue} violates URI syntax
     */
    private static URI uri(final String rawValue) {
        return URI.create(rawValue);
    }

    /**
     * <p>Normalizes a classifier path so it can be appended to a slash-free base URL.</p>
     *
     * <p>The helper performs the following steps:</p>
     * <ol>
     *   <li>Return the value unchanged when it already starts with {@code /}.</li>
     *   <li>Otherwise prepend a single {@code /}.</li>
     * </ol>
     *
     * @param value classifier path to normalize
     * @return the path with a leading slash
     * @throws NullPointerException if {@code value} is {@code null}
     */
    private static String ensureLeadingSlash(final String value) {
        return value.startsWith("/") ? value : "/" + value;
    }

    /**
     * <p>Removes at most one trailing slash from a classifier base URL.</p>
     *
     * <p>The helper performs the following steps:</p>
     * <ol>
     *   <li>Detect whether the value ends with {@code /}.</li>
     *   <li>Remove that final character when present; otherwise return the original value.</li>
     * </ol>
     *
     * <p>{@link #from(Map)} combines this result with {@link #ensureLeadingSlash(String)} to form one endpoint URI.</p>
     *
     * @param value classifier base URL text to normalize
     * @return the value without one trailing slash
     * @throws NullPointerException if {@code value} is {@code null}
     */
    private static String stripTrailingSlash(final String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /**
     * <p>Names the two endpoints produced together while loading configuration, avoiding positional arrays or maps.</p>
     *
     * <p>The pair is an intermediate value used only while {@link #from(Map)} constructs both tunnel directions.</p>
     *
     * @param headend endpoint that originates the forward direction
     * @param tailend endpoint that terminates the forward direction
     */
    private record EndpointPair(
            /** <p>Endpoint that originates the forward direction.</p> */
            TunnelEndpoint headend,
            /** <p>Endpoint that terminates the forward direction.</p> */
            TunnelEndpoint tailend) {
    }
}
