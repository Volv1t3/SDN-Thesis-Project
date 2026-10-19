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

package com.sma.sdn.serialization.xml;

import com.sma.sdn.model.BgpLsTopologyNode;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.util.XmlSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * <p>Converts an OpenDaylight BGP-LS topology document into node snapshots with router
 * identifiers and numeric graph identifiers. Topology discovery and refresh services use
 * these snapshots to populate and refresh the controller's BGP-LS node state.</p>
 */
public final class BgpLsTopologyXmlDeserializer {
    /** <p>Logger for topology identifiers and node extraction counts.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(BgpLsTopologyXmlDeserializer.class);
    /** <p>Pattern for the numeric router query parameter in a BGP-LS node identifier.</p> */
    private static final Pattern GRAPH_NODE_ID = Pattern.compile("(?:^|[?&])router=(\\d+)(?:$|&)");

    /**
     * <p>Extracts usable BGP-LS nodes from an XML topology, retaining the first topology
     * identifier found and the optional TE router ID for each node.</p>
     *
     * <p>Extraction proceeds as follows:</p>
     * <ol>
     *   <li>Parse the document and locate its first topology ID and all node elements.</li>
     *   <li>Read each node's node ID, IGP router ID, and optional IPv4 TE router ID;
     *       skip entries missing either required ID.</li>
     *   <li>Extract the numeric graph ID from each retained node ID, construct the model,
     *       and log and return an immutable list.</li>
     * </ol>
     *
     * <p>{@link com.sma.sdn.topology.TopologyDiscoveryService} and
     * {@link com.sma.sdn.topology.TopologyRefreshService} consume these snapshots.</p>
     *
     * @param xml BGP-LS topology XML response
     * @return immutable list of nodes with usable node and router identifiers
     * @throws IllegalArgumentException if XML processing fails or a retained node ID lacks
     *         a valid numeric router parameter
     */
    public List<BgpLsTopologyNode> deserialize(final String xml) {
        final Document document = XmlSupport.parse(xml);

        /*This statement filters through the corresponding BGPLS body for the topology id section which determines the name of the
        * topology which should match to the environment variables configured*/
        final String topologyId = XmlSupport.string(document, "//*[local-name()='topology-id'][1]");
        /*This then extracts the nodes section, since we only care about these and not the links per se we are going to
        * extract them from the document first, creating a NodeList of them*/
        final NodeList nodes = XmlSupport.nodes(document, "//*[local-name()='node']");
        final List<BgpLsTopologyNode> result = new ArrayList<>();
        /*We are going to iterate over the entire set of nodes that was extracted and from each extract their node-id
        * their router-id and their te-router-id from which we are going to extract the graphNodeId too*/
        for (int index = 0; index < nodes.getLength(); index++) {
            final Node node = nodes.item(index);
            /*This node id encompases the entirety of the identifier for the node, determining its OSPF, BGPLS, TE and Graph ID
            * variables, we can use this one to extract the corresponding graph node id */
            final String nodeId = XmlSupport.string(node, "./*[local-name()='node-id'][1]");

            /*Internally to the IGP node attreibutes which are reported through BGPLS comes the router id which is in our case always the
            * loopback0 address, so stuff like 11.11.11.11*/
            final String routerId = XmlSupport.string(node,
                    ".//*[local-name()='igp-node-attributes']/*[local-name()='router-id'][1]");

            /*Finally we extract the TE or MPLS TE id that was defined on each router to be advertised to be the same as
            * the loopback0 used for the router ID in ospf, therefore this value comes from the ospf configuration*/
            final String teRouterId = XmlSupport.string(node, ".//*[local-name()='te-router-id-ipv4'][1]");
            if (nodeId == null || routerId == null) {
                continue;
            }

            /*For each of the processed nodes we create the Java side DTO from it using the provided class*/
            result.add(new BgpLsTopologyNode(topologyId, nodeId, routerId, teRouterId, graphNodeId(nodeId)));
        }
        final List<BgpLsTopologyNode> snapshot = List.copyOf(result);
        LOG.debug("bgp_ls_topology_deserialized", "deserialize",
                "Se deserializo la estructura de nodos de la topologia BGP-LS",
                StructuredLogger.fields("topology_id", topologyId, "xml_node_count", nodes.getLength(),
                        "usable_node_count", snapshot.size()));

        /*Return the read only snapshot to the processing service*/
        return snapshot;
    }

    /**
     * <p>Extracts the decimal router parameter used as the numeric graph node ID.</p>
     *
     * <p>Extraction proceeds as follows:</p>
     * <ol>
     *   <li>Match a complete {@code router=<digits>} query parameter.</li>
     *   <li>Reject an absent match or parse its digits as a signed long.</li>
     * </ol>
     *
     * @param nodeId BGP-LS node identifier containing the router parameter
     * @return numeric graph node identifier
     * @throws IllegalArgumentException if the parameter is missing or exceeds the long range
     */
    private static long graphNodeId(final String nodeId) {
        /*This is a small regex expression used to find the graph node id which is registered within the node id statement.*/
        final Matcher matcher = GRAPH_NODE_ID.matcher(nodeId);
        if (!matcher.find()) {
            throw new IllegalArgumentException(
                    "El node-id de BGP-LS no contiene router=<numero>: " + nodeId);
        }
        return Long.parseLong(matcher.group(1));
    }
}
