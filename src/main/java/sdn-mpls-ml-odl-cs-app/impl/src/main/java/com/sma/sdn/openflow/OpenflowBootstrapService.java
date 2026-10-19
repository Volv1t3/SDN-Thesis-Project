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

package com.sma.sdn.openflow;

import com.sma.sdn.config.AppConfig;
import com.sma.sdn.metrics.SdnMplsMlMetrics;
import com.sma.sdn.observability.StructuredLogger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <p>Coordinates access-switch discovery, registry publication, flow installation and
 * verification during controller startup. Readiness is set only after verification, or
 * immediately when bootstrap is disabled.</p>
 */
public final class OpenflowBootstrapService {
    /** <p>Logs bootstrap decisions and readiness.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(OpenflowBootstrapService.class);
    /** <p>Ethernet type matched by ARP access flows.</p> */
    private static final int ARP_ETHERNET_TYPE = 2054;
    /** <p>Ethernet type matched by IPv4 access flows.</p> */
    private static final int IPV4_ETHERNET_TYPE = 2048;
    /** <p>Provides switch ports, flow settings and bootstrap enablement.</p> */
    private final AppConfig config;
    /** <p>Discovers the configured access switches.</p> */
    private final OpenflowInventoryService inventoryService;
    /** <p>Publishes discovered dynamic switch identities.</p> */
    private final OpenflowSwitchRegistry switchRegistry;
    /** <p>Installs each bootstrap flow through RESTCONF.</p> */
    private final OpenflowFlowProvisioningService provisioningService;
    /** <p>Verifies installed flows in configuration and operational state.</p> */
    private final OpenflowBootstrapVerifier verifier;
    /** <p>Reports readiness and the last successful bootstrap time.</p> */
    private final SdnMplsMlMetrics metrics;
    /** <p>Thread-visible readiness of the latest initialization cycle.</p> */
    private final AtomicBoolean ready = new AtomicBoolean();

    /**
     * <p>Creates a bootstrap coordinator with initially false readiness.</p>
     *
     * <p>Construction proceeds as follows:</p>
     * <ol>
     *   <li>Require and store configuration, inventory, registry, provisioning, verifier and metrics collaborators.</li>
     * </ol>
     *
     * @param config OpenFlow configuration
     * @param inventoryService switch discovery service
     * @param switchRegistry dynamic switch registry
     * @param provisioningService flow installation service
     * @param verifier configuration and operational verifier
     * @param metrics metrics collector
     * @throws NullPointerException if any collaborator is null
     */
    public OpenflowBootstrapService(
            final AppConfig config,
            final OpenflowInventoryService inventoryService,
            final OpenflowSwitchRegistry switchRegistry,
            final OpenflowFlowProvisioningService provisioningService,
            final OpenflowBootstrapVerifier verifier,
            final SdnMplsMlMetrics metrics) {
        this.config = Objects.requireNonNull(config, "config");
        this.inventoryService = Objects.requireNonNull(inventoryService, "inventoryService");
        this.switchRegistry = Objects.requireNonNull(switchRegistry, "switchRegistry");
        this.provisioningService = Objects.requireNonNull(provisioningService, "provisioningService");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /**
     * <p>Runs one bootstrap cycle, publishing readiness after all discovered switches'
     * flows are installed and verified. Disabled bootstrap is marked ready without installation.</p>
     *
     * <p>The cycle proceeds as follows:</p>
     * <ol>
     *   <li>Clear readiness and return ready immediately when bootstrap is disabled.</li>
     *   <li>Warn if default-drop installation was requested; this implementation does not install it.</li>
     *   <li>Discover switches and replace the registry before programming flows.</li>
     *   <li>Install four access flows per switch and verify each switch; reject unsuccessful results.</li>
     *   <li>Publish readiness and success timestamp, or clear readiness and rethrow a runtime failure.</li>
     * </ol>
     *
     * @throws IllegalStateException if an installation or verification fails
     * @throws IllegalArgumentException if inventory validation fails
     */
    public void initialize() {
        ready.set(false);
        metrics.set("sma_openflow_bootstrap_ready", 0L);
        if (!config.openflowBootstrapEnabled()) {
            /*This is a contingency only mechanis, as in reality it is imperative that we deploy the implementation with
            * boostrapping to true unless we want to configure the table behaviors by hand*/
            ready.set(true);
            metrics.set("sma_openflow_bootstrap_ready", 1L);
            LOG.warn(
                    "openflow_bootstrap_disabled",
                    "initialize",
                    "El bootstrap OpenFlow esta deshabilitado; no se instalaran ni verificaran flujos de acceso.",
                    java.util.Map.of(),
                    null);
            return;
        }
        /*This is also a contingency because the installation of a default drop mechanic is not recommended for production. This would
        * mean that traffic is severely impeded until a corresponding tunnel were to be created and rules be implemented, adding
        * unnecessary strain on the network*/
        if (config.openflowInstallDefaultDrop()) {
            LOG.warn(
                    "openflow_default_drop_not_installed",
                    "initialize",
                    "Se solicito el flujo drop predeterminado, pero esta primera implementacion conserva "
                            + "el table-miss de ODL.",
                    StructuredLogger.fields("configured_priority", config.openflowDefaultDropPriority()),
                    null);
        }
        try {
            /*We use the discover switches function to perform an initial collection of all nodes registered in the openflow inventory and use the deserialization
            * mechanisms implemented and used by the corresponding inventory service to populate the switch registry
            * with the updated and parsed switches */
            final List<OpenflowSwitchRecord> switches = inventoryService.discoverSwitches();
            switchRegistry.replace(switches);
            long cookieBase = 9_100_000L;

            /*In the initialitzation phase, in order toa llow for the proceessing of packets, we are going to insert
            * specific rules defined for each of the records in the switches that are registered in the corresponding
            * registry associated with the openflow topology. For each of these there is a specific set of Flow Definitions
            * that need to be installed */
            for (OpenflowSwitchRecord switchRecord : switches) {
                // Generate said list of flows
                final List<OpenflowFlowDefinition> flows = definitionsFor(switchRecord, cookieBase);
                cookieBase += 100L;

                /*For each of the created flows we use the Openflow Provisioning Service to install said flows througb
                * Restconf operations*/
                for (OpenflowFlowDefinition flow : flows) {
                    final OpenflowFlowInstallResult result = provisioningService.installFlow(switchRecord, flow);
                    if (!result.success()) {
                        throw new IllegalStateException("No fue posible instalar el flujo " + result.flowId()
                                + "; HTTP " + result.statusCode() + "; " + result.detail());
                    }
                }
                /*After injection we attempt to verify the injected flows in the controller's registered switches */
                verifier.verify(switchRecord, flows);
            }

            /*if it all works then we mark readiness to true and finish the operation. If anything raises an exception in processing, this
            * method is going to raise the exception tot he init method in the provider class and will cause another readiness attempt
            * to be scheduled*/
            ready.set(true);
            metrics.set("sma_openflow_bootstrap_ready", 1L);
            metrics.set("sma_openflow_bootstrap_last_success_timestamp", Instant.now().getEpochSecond());
            LOG.info(
                    "openflow_bootstrap_ready",
                    "initialize",
                    "El bootstrap OpenFlow completo la instalacion y verificacion de los flujos de acceso.",
                    StructuredLogger.fields("switch_count", switches.size(), "flow_count", switches.size() * 4));
        } catch (RuntimeException e) {
            ready.set(false);
            metrics.set("sma_openflow_bootstrap_ready", 0L);
            throw e;
        }
    }

    /**
     * <p>Reports whether the latest bootstrap cycle is ready.</p>
     * <p>The lookup has one step:</p>
     * <ol><li>Read the atomic readiness flag.</li></ol>
     *
     * @return true if flows were verified or bootstrap is disabled
     */
    public boolean isReady() {
        return ready.get();
    }

    /**
     * <p>Constructs the four directional ARP and IPv4 rules for a discovered switch.</p>
     *
     * <p>Rule construction proceeds as follows:</p>
     * <ol>
     *   <li>Choose configured host and core port names and require both connectors.</li>
     *   <li>Create ARP and IPv4 rules in both directions with deterministic IDs and cookies.</li>
     *   <li>Enable a controller copy only for host-to-core IPv4 and return an immutable list.</li>
     * </ol>
     *
     * @param switchRecord discovered switch and connectors
     * @param cookieBase base cookie assigned to this switch
     * @return four deterministic flow definitions
     * @throws IllegalStateException if a required connector is missing
     */
    private List<OpenflowFlowDefinition> definitionsFor(
            final OpenflowSwitchRecord switchRecord, final long cookieBase) {

        /*Based n prior knowledge of the network we implement a mechanism to identify the required
        * core and host facing ports that are used on either switch*/
        final boolean echo = "ECHO".equals(switchRecord.logicalName());
        final String hostName = echo ? config.ovsEchoHostPortName() : config.ovsFoxtrotHostPortName();
        final String coreName = echo ? config.ovsEchoCorePortName() : config.ovsFoxtrotCorePortName();

        /*Here we go ahead and filter the corresponding record for the switch we are configuring at the time, through the hostName of the
        * switch to host port. This should be found if the topology is correctly implemented and no link has been suddenly
        * set to link down. This filtering will provide the Connector record only in contrast to the Switch recrd we are using */
        final OpenflowConnectorRecord host = requireConnector(switchRecord, hostName);

        /*This operation is repeated for either side of the network*/
        final OpenflowConnectorRecord core = requireConnector(switchRecord, coreName);


        final String prefix = "sma-bootstrap-" + switchRecord.logicalName().toLowerCase() + "-";

        /*Based on the prefix boostrap that we associate with the corresponding switches we creat the flows through an
        * internal function that creates a corresponding OpenflowFlowDefinition DTO*/
        final List<OpenflowFlowDefinition> flows = new ArrayList<>(4);
        flows.add(flow(prefix + "arp-host-to-core", config.openflowArpPriority(), cookieBase + 1,
                ARP_ETHERNET_TYPE, host, core, false));
        flows.add(flow(prefix + "arp-core-to-host", config.openflowArpPriority(), cookieBase + 2,
                ARP_ETHERNET_TYPE, core, host, false));
        flows.add(flow(prefix + "ipv4-host-to-core", config.openflowIpv4Priority(), cookieBase + 3,
                IPV4_ETHERNET_TYPE, host, core, true));
        flows.add(flow(prefix + "ipv4-core-to-host", config.openflowIpv4Priority(), cookieBase + 4,
                IPV4_ETHERNET_TYPE, core, host, false));
        return List.copyOf(flows);
    }

    /**
     * <p>Creates a flow definition using the full ingress ID and numeric output port.</p>
     * <p>Construction has one step:</p>
     * <ol><li>Combine configured table and supplied flow and connector fields.</li></ol>
     *
     * @param flowId stable flow identifier
     * @param priority configured priority
     * @param cookie flow cookie
     * @param ethernetType Ethernet type as a decimal integer
     * @param input ingress connector
     * @param output egress connector
     * @param copyToController whether to copy matching packets to the controller
     * @return validated flow definition
     */
    private OpenflowFlowDefinition flow(
            final String flowId,
            final int priority,
            final long cookie,
            final int ethernetType,
            final OpenflowConnectorRecord input,
            final OpenflowConnectorRecord output,
            final boolean copyToController) {
        return new OpenflowFlowDefinition(
                flowId,
                config.openflowTableId(),
                priority,
                cookie,
                ethernetType,
                input.connectorId(),
                input.name(),
                output.portNumber(),
                output.name(),
                copyToController);
    }

    /**
     * <p>Retrieves a required connector from the discovered switch.</p>
     * <p>The lookup proceeds as follows:</p>
     * <ol><li>Look up the connector by name and reject an absent result.</li>
     * <li>Return the discovered connector.</li></ol>
     *
     * @param switchRecord switch containing the connector
     * @param name exact connector name
     * @return matching connector
     * @throws IllegalStateException if the connector is absent
     */
    private static OpenflowConnectorRecord requireConnector(
            final OpenflowSwitchRecord switchRecord, final String name) {
        final OpenflowConnectorRecord connector = switchRecord.connectorsByName().get(name);
        if (connector == null) {
            throw new IllegalStateException("Falta el conector " + name
                    + " en el registro del conmutador " + switchRecord.logicalName());
        }
        return connector;
    }
}
