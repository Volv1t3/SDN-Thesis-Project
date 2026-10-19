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
import com.sma.sdn.http.OdlRestconfDataClient;
import com.sma.sdn.metrics.SdnMplsMlMetrics;
import com.sma.sdn.observability.StructuredLogger;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * <p>Reads OpenDaylight operational inventory and resolves the configured ECHO and
 * FOXTROT access-switch profiles. The returned records are used to populate the
 * {@link OpenflowSwitchRegistry} during bootstrap.</p>
 */
public final class OpenflowInventoryService {
    /** <p>Logs resolved switch and connector identities.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(OpenflowInventoryService.class);
    /** <p>Retrieves the OpenDaylight inventory.</p> */
    private final OdlRestconfDataClient dataClient;
    /** <p>Extracts and validates switches and connectors from inventory XML.</p> */
    private final OpenflowInventoryXmlDeserializer deserializer;
    /** <p>Required switch profiles keyed by their management addresses.</p> */
    private final Map<String, OpenflowBootstrapProfile> profilesByManagementIp;
    /** <p>Records discovery and identity-resolution counts.</p> */
    private final SdnMplsMlMetrics metrics;

    /**
     * <p>Constructs ECHO and FOXTROT profiles from application configuration.</p>
     *
     * <p>Construction proceeds as follows:</p>
     * <ol>
     *   <li>Require the client, deserializer and metrics collector.</li>
     *   <li>Create the two configured profiles and reject duplicate management addresses.</li>
     *   <li>Store an immutable management-address index.</li>
     * </ol>
     *
     * @param config switch addresses and port names
     * @param dataClient authenticated RESTCONF data client
     * @param deserializer inventory XML deserializer
     * @param metrics application metrics collector
     * @throws NullPointerException if a required collaborator or profile field is null
     * @throws IllegalArgumentException if the management addresses are identical
     */
    public OpenflowInventoryService(
            final AppConfig config,
            final OdlRestconfDataClient dataClient,
            final OpenflowInventoryXmlDeserializer deserializer,
            final SdnMplsMlMetrics metrics) {
        this.dataClient = Objects.requireNonNull(dataClient, "dataClient");
        this.deserializer = Objects.requireNonNull(deserializer, "deserializer");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        /*Since we know the edge switches beforehand the application can already use said information to create the profiles
        * that need to be validated within the boostrapping process and deserializatio processes, hoping that internally it can map the
        * corresponding switches ECHO and foxtrot through its configured names for ports and their management IP and use that information
        * to parse the corresponding XML that is returned to process it and find the mappings between logical names and IDS for packet in
        * notifications*/
        final Map<String, OpenflowBootstrapProfile> profiles = new LinkedHashMap<>();

        /*These profiles are crucial and they are formed based on our understanding of the network, we know that two profiles must always be in the network
        * , and if these are not found at all then we are going to be in an Illegal State of the network*/
        addProfile(profiles, new OpenflowBootstrapProfile(
                "ECHO", config.ovsEchoManagementIp(), config.ovsEchoHostPortName(), config.ovsEchoCorePortName()));
        addProfile(profiles, new OpenflowBootstrapProfile(
                "FOXTROT", config.ovsFoxtrotManagementIp(),
                config.ovsFoxtrotHostPortName(), config.ovsFoxtrotCorePortName()));
        profilesByManagementIp = Map.copyOf(profiles);
    }

    /**
     * <p>Reads operational inventory and resolves the configured switch profiles.</p>
     *
     * <p>Discovery proceeds as follows:</p>
     * <ol>
     *   <li>Record an attempt and issue the inventory GET.</li>
     *   <li>Reject unsuccessful statuses and deserialize the response using the configured profiles.</li>
     *   <li>Record success and resolved switch and connector identities, or record and rethrow a runtime failure.</li>
     * </ol>
     *
     * @return discovered ECHO and FOXTROT switch records
     * @throws IllegalStateException if the HTTP response is unsuccessful
     * @throws IllegalArgumentException if the inventory does not satisfy the profiles
     */
    public List<OpenflowSwitchRecord> discoverSwitches() {
        /*This mechanism is called directly within the initialization of the Openflow topologyu in the application. it is used
        * to perform a discovery of the openflow inventory through the OdlRestconf client which performs data based RESTCONF
        * operations against ODL*/
        metrics.increment("sma_openflow_inventory_discovery_attempts_total");
        try {
            /*Attempts to perform the initialization process and get the openflow inventory information. In this case, this operation
            * still has to be done after the BGP-LS topology retrieval has finished in the sequence of initialization, and it
            * therefore always is guaranteed to have the information ready when it gets to it as the time it takes for BGP-LS to become
            * available,the Openflow and OVSDB sessions can start independently at the controller-level before we even pull
            * its data. */
            final HttpResponse<String> response = dataClient.getOpenflowInventory();
            if (!isSuccess(response.statusCode())) {
                throw new IllegalStateException(
                        "La lectura del inventario OpenFlow fallo con HTTP " + response.statusCode());
            }
            /*This performs a classif deserialiazatio of each of the switches read in the body. As we know the topology expects
            * there to be two specific profiles for switches, that being the ECHO and FOXTROT profiles which represent
            * the two edge switches for packet in notifications, what we need to do here is use that information to
            * match them with the interfaces and switch IDS that openflow uses to identify which side of the edge devices a packet
            * in notification is coming from. This is then used for filtering of notifications if the direction is not to be
            * processed.*/
            final List<OpenflowSwitchRecord> switches =
                    deserializer.deserialize(response.body(), profilesByManagementIp);
            metrics.increment("sma_openflow_inventory_discovery_success_total");
            for (OpenflowSwitchRecord switchRecord : switches) {
                metrics.increment("sma_openflow_switch_resolved_total");
                logResolvedSwitch(switchRecord);
            }
            return switches;
        } catch (RuntimeException e) {
            metrics.increment("sma_openflow_inventory_discovery_failure_total");
            throw e;
        }
    }

    /**
     * <p>Adds a profile to the temporary management-address index.</p>
     * <p>The insertion has one step:</p>
     * <ol><li>Insert the profile if absent or reject the duplicate address.</li></ol>
     *
     * @param profiles mutable index under construction
     * @param profile profile to insert
     * @throws IllegalArgumentException if its management address is already indexed
     */
    private static void addProfile(
            final Map<String, OpenflowBootstrapProfile> profiles, final OpenflowBootstrapProfile profile) {
        if (profiles.putIfAbsent(profile.managementIp(), profile) != null) {
            throw new IllegalArgumentException("Las IP de gestion OpenFlow deben ser diferentes: "
                    + profile.managementIp());
        }
    }

    /**
     * <p>Logs a switch and each connector in its name index, incrementing connector metrics.</p>
     * <p>Logging proceeds as follows:</p>
     * <ol><li>Log the switch identity.</li>
     * <li>Record and log each indexed connector and its operational flags.</li></ol>
     *
     * @param switchRecord discovered switch to report
     */
    private void logResolvedSwitch(final OpenflowSwitchRecord switchRecord) {
        LOG.info(
                "openflow_switch_resolved",
                "logResolvedSwitch",
                "Se resolvio un conmutador OpenFlow por su direccion de gestion.",
                StructuredLogger.fields(
                        "logical_name", switchRecord.logicalName(),
                        "management_ip", switchRecord.managementIp(),
                        "node_id", switchRecord.nodeId()));
        for (OpenflowConnectorRecord connector : switchRecord.connectorsByName().values()) {
            metrics.increment("sma_openflow_connector_resolved_total");
            LOG.info(
                    "openflow_connector_resolved",
                    "logResolvedSwitch",
                    "Se resolvio un conector del conmutador OpenFlow.",
                    StructuredLogger.fields(
                            "logical_name", switchRecord.logicalName(),
                            "connector_name", connector.name(),
                            "connector_id", connector.connectorId(),
                            "port_number", connector.portNumber(),
                            "live", connector.live(),
                            "link_down", connector.linkDown()));
        }
    }

    /**
     * <p>Recognizes the statuses accepted for inventory reads.</p>
     * <p>The check has one step:</p>
     * <ol><li>Compare the status with 200, 201 and 204.</li></ol>
     *
     * @param statusCode received HTTP status
     * @return whether the status is 200, 201 or 204
     */
    private static boolean isSuccess(final int statusCode) {
        return statusCode == 200 || statusCode == 201 || statusCode == 204;
    }
}
