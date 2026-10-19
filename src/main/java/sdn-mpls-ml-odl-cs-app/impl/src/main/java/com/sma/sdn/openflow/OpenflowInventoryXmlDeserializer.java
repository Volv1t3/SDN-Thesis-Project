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

import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.util.XmlSupport;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * <p>Parses OpenDaylight inventory XML into switch records selected by configured
 * management addresses. {@link OpenflowInventoryService} supplies profiles and uses
 * the resulting validated connector indexes for bootstrap.</p>
 */
public final class OpenflowInventoryXmlDeserializer {
    /** <p>Logs the number of inventory nodes and resolved switches.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(OpenflowInventoryXmlDeserializer.class);

    /**
     * <p>Resolves each requested profile to one OpenFlow node and validates its required
     * host and core connectors. Nodes that do not match a profile are ignored.</p>
     *
     * <p>Deserialization proceeds as follows:</p>
     * <ol>
     *   <li>Parse XML and select nodes with IDs beginning {@code openflow:} and matching management IPs.</li>
     *   <li>Reject duplicate matching IPs and parse each switch's connectors.</li>
     *   <li>Require every profile to resolve and return immutable results in profile iteration order.</li>
     * </ol>
     *
     * @param xml inventory XML received from RESTCONF
     * @param profiles required switch profiles keyed by management address
     * @return immutable list of resolved and validated switches
     * @throws IllegalArgumentException if required inventory data is missing, duplicated or unusable
     */
    public List<OpenflowSwitchRecord> deserialize(
            final String xml, final Map<String, OpenflowBootstrapProfile> profiles) {

        /*First we need to understand that the openflow node inventory is massive, beause it returns both nodes, flow tables
        * flows and connectors and so we use XML parsing to tackle directly the highest elemnets of type node*/
        final Document document = XmlSupport.parse(xml);

        /*These node elements contain the information for each of the openflwo nodes that are registered, within them
        * they contain many flow and table elements that need to be ignored. But this allows us to filter to specific
        * node elements instead of the whole document*/
        final NodeList nodes = XmlSupport.nodes(document, "//*[local-name()='node']");

        /*For each of these node elements,we need to perform an operation to extract its id, which is stored within an
        * <id> block associated with each node. This is the identifier that we know is going to come in the packet in notification
        * for the node the packet came in, we need to associate with to the corresponding logical name through something else
        * though...*/
        final Map<String, OpenflowSwitchRecord> byManagementIp = new LinkedHashMap<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            final Node node = nodes.item(index);
            /*Extracting the id, if it is missig we just skip that node*/
            final String nodeId = XmlSupport.string(node, "./*[local-name()='id'][1]");
            if (nodeId == null || !nodeId.startsWith("openflow:")) {
                continue;
            }
            /*This is the interesting part, the ip-address is a high level element defined at the very bottom
            * of the table elements that describes the IP address used to connect to the switch for its openflow management network.
            * This IP address is the main identifier that we used as we mapped it to the expected IP address of the profile
            * for that switch. This allows us to determine if the openflow id is for the ECHO or FOXTROT switch*/
            final String managementIp = XmlSupport.string(node, "./*[local-name()='ip-address'][1]");

            /*This is important, what we are doing here is filtering the expected profiles for the boostrapping process, i.e.
            * we are filtering the profiles that we might have registered to only the ones that us e the management IPs or the
            * IPs associatd with the switches we know, allowing us to filter the profiles that need to be validated against
            * these */
            final OpenflowBootstrapProfile profile = profiles.get(managementIp);
            if (profile == null) {
                continue;
            }
            /*In this case if the managementIP is already registered then we raise an exception as we had a duplicated management
            * IP which wouldnt work*/
            if (byManagementIp.containsKey(managementIp)) {
                throw new IllegalArgumentException(
                        "Se descubrieron varios nodos OpenFlow con la IP de gestion " + managementIp);
            }
            /*If no duplication problems arise we can register this information on the internal list of OpenflowSwitchRecords which
            * are going to be stored from the parsed content. While we certainly can filter out the deserialization, the information
            * here corresponds to ALL possible switches registered*/
            byManagementIp.put(managementIp, parseSwitch(node, nodeId, managementIp, profile));
        }
        final List<OpenflowSwitchRecord> result = new ArrayList<>();
        for (OpenflowBootstrapProfile profile : profiles.values()) {
            final OpenflowSwitchRecord switchRecord = byManagementIp.get(profile.managementIp());
            if (switchRecord == null) {
                throw new IllegalArgumentException("No se encontro el conmutador OpenFlow "
                        + profile.logicalName() + " con IP de gestion " + profile.managementIp());
            }
            result.add(switchRecord);
        }
        LOG.debug(
                "openflow_inventory_deserialized",
                "deserialize",
                "Se deserializo y valido el inventario OpenFlow requerido.",
                StructuredLogger.fields(
                        "inventory_node_count", nodes.getLength(), "resolved_switch_count", result.size()));
        return List.copyOf(result);
    }

    /**
     * <p>Builds connector indexes for a switch and checks that both required ports are usable.</p>
     *
     * <p>Switch parsing proceeds as follows:</p>
     * <ol>
     *   <li>Read direct connector children, skipping LOCAL connectors.</li>
     *   <li>Require identity, port and state fields, default a missing hardware address to empty, and reject duplicate indexes.</li>
     *   <li>Require the configured host and core ports to be live and not link-down.</li>
     *   <li>Encode the node ID for URI use and construct the immutable switch record.</li>
     * </ol>
     *
     * @param node inventory XML node
     * @param nodeId unencoded OpenFlow node ID
     * @param managementIp management address of the node
     * @param profile profile defining required connector names
     * @return validated switch and connector indexes
     * @throws IllegalArgumentException if connector data is missing, duplicated or unusable
     */
    private OpenflowSwitchRecord parseSwitch(
            final Node node,
            final String nodeId,
            final String managementIp,
            final OpenflowBootstrapProfile profile) {
        /*Internally, each of the records for a switch will contain  a mapping between a parameter of its connectors
        * onto the corresponding connector record which can be used for quickly filtering data and connectors, for example
        * when filtering connector based information for the entry of a packet in notification directly based on its name or id
        * instead of having to parse the pcket in and perform a complicated process.*/
        final Map<String, OpenflowConnectorRecord> byName = new HashMap<>();
        final Map<String, OpenflowConnectorRecord> byId = new HashMap<>();
        final Map<Integer, OpenflowConnectorRecord> byPort = new HashMap<>();

        /*This parses a coresponding node and using its management IP and its nodeId it filters down the node
        * to extract the elements of node-connector which are represented as inner nodes of a <node> tag*/
        final NodeList connectors = XmlSupport.nodes(node, "./*[local-name()='node-connector']");
        for (int index = 0; index < connectors.getLength(); index++) {
            final Node connectorNode = connectors.item(index);
            /*Extract the connectors name and id*/
            final String connectorId = XmlSupport.string(connectorNode, "./*[local-name()='id'][1]");
            final String name = XmlSupport.string(connectorNode, "./*[local-name()='name'][1]");
            /*We ignore the local connector because it is always down and it never receives traffic*/
            if ("LOCAL".equalsIgnoreCase(name)
                    || connectorId != null && connectorId.toUpperCase().endsWith(":LOCAL")) {
                continue;
            }
            /*We extract the port number, which could be 1, 2 or others increasing as more links are created upon the device*/
            final Integer portNumber = XmlSupport.integer(connectorNode, "./*[local-name()='port-number'][1]");

            final String hardwareAddress = XmlSupport.string(
                    connectorNode, "./*[local-name()='hardware-address'][1]");
            /*Whether or not the connector is up or not */
            final Boolean live = XmlSupport.bool(
                    connectorNode, ".//*[local-name()='state']/*[local-name()='live'][1]");
            final Boolean linkDown = XmlSupport.bool(
                    connectorNode, ".//*[local-name()='state']/*[local-name()='link-down'][1]");
            /*Validating the state to be non null after parsing*/
            if (connectorId == null || name == null || portNumber == null || live == null || linkDown == null) {
                throw new IllegalArgumentException("El nodo " + nodeId
                        + " contiene un conector no local con identidad, puerto o estado incompleto");
            }
            /*We create a corresponding OpenflowConnectorRecord based on the parameters that were passed , and the parameters taht were identified.
            * We use the putUnique function to create the corresponding lists before we store the connector in the maps
            * created earlier*/
            final OpenflowConnectorRecord connector = new OpenflowConnectorRecord(
                    connectorId, name, portNumber, hardwareAddress == null ? "" : hardwareAddress, live, linkDown);
            /*Store the connector in its list by its identifier*/

            putUnique(byName, name, connector, "nombre");
            putUnique(byId, connectorId, connector, "identificador");
            putUnique(byPort, portNumber, connector, "numero de puerto");
        }

        /*After procesing we go ahead and determine if the required connectors are present. To do this we look up on the byName
        * lists created that the correspoding host port name and core port name from the boostrapping profile is within the
        * connectors map and is usable, i.e. is live and not link down*/

        /*The profile variable comes froma filtering done in the deserialize function over the profiles map coming from the Inventory Service
        * which represents the set of profiles that are expected to be found for the switches in the network, containing management IPs, and logical
        * names for it, and its interfaces.*/
        requireUsableConnector(profile, byName, profile.hostPortName());
        requireUsableConnector(profile, byName, profile.corePortName());


        /*This finally returns a Switch Record, with the profile associated with the information obtained from the deserialization, as well as
        * the corresponding connector maps for easy filtering*/
        return new OpenflowSwitchRecord(
                profile.logicalName(),
                managementIp,
                nodeId,
                URLEncoder.encode(nodeId, StandardCharsets.UTF_8).replace("+", "%20"),
                byName,
                byId,
                byPort);
    }

    /**
     * <p>Adds one connector to a temporary index, rejecting duplicate keys.</p>
     * <p>The insertion has one step:</p>
     * <ol><li>Insert if absent or throw with the key description.</li></ol>
     *
     * @param <K> index key type
     * @param index mutable connector index
     * @param key inventory identity to insert
     * @param connector connector associated with the identity
     * @param keyDescription description used in the duplicate error
     * @throws IllegalArgumentException if the key is already present
     */
    private static <K> void putUnique(
            final Map<K, OpenflowConnectorRecord> index,
            final K key,
            final OpenflowConnectorRecord connector,
            final String keyDescription) {
        if (index.putIfAbsent(key, connector) != null) {
            throw new IllegalArgumentException("Se encontro un conector OpenFlow duplicado por " + keyDescription
                    + ": " + key);
        }
    }

    /**
     * <p>Requires a named connector to exist, be live and not be link-down.</p>
     * <p>Validation proceeds as follows:</p>
     * <ol><li>Look up the required connector and reject a missing entry.</li>
     * <li>Reject a connector reported not live or link-down.</li></ol>
     *
     * @param profile profile used in diagnostic messages
     * @param connectors connectors indexed by name
     * @param connectorName required connector name
     * @throws IllegalArgumentException if the connector is absent or not usable
     */
    private static void requireUsableConnector(
            final OpenflowBootstrapProfile profile,
            final Map<String, OpenflowConnectorRecord> connectors,
            final String connectorName) {
        final OpenflowConnectorRecord connector = connectors.get(connectorName);
        if (connector == null) {
            throw new IllegalArgumentException("Falta el conector " + connectorName
                    + " en el conmutador " + profile.logicalName());
        }
        if (!connector.live() || connector.linkDown()) {
            throw new IllegalArgumentException("El conector " + connectorName
                    + " del conmutador " + profile.logicalName() + " no esta operativo");
        }
    }
}
