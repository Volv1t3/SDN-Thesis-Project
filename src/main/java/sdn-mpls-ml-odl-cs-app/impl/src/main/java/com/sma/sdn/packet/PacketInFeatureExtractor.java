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

package com.sma.sdn.packet;

import com.sma.sdn.config.AppConfig;
import com.sma.sdn.model.FlowDirection;
import com.sma.sdn.model.PacketClassificationContext;
import com.sma.sdn.model.PacketFeatures;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.openflow.OpenflowConnectorRecord;
import com.sma.sdn.openflow.OpenflowSwitchRecord;
import com.sma.sdn.openflow.OpenflowSwitchRegistry;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yangtools.binding.BindingInstanceIdentifier;
import org.opendaylight.yangtools.binding.KeyStep;

/**
 * <p>Extracts Ethernet, IPv4, and transport features from a PacketIn payload and
 * resolves its typed ingress against the OpenFlow inventory. Incomplete payloads
 * and unknown ingress identities are rejected without producing a context.</p>
 *
 * <p>The workflow consumes the resulting {@link PacketClassificationContext};
 * {@link AppConfig} determines its direction from the resolved logical names.</p>
 */
public final class PacketInFeatureExtractor {
    /** <p>Records feature extraction and rejected ingress diagnostics.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(PacketInFeatureExtractor.class);
    /** <p>EtherType identifying an IPv4 payload.</p> */
    private static final int ETH_TYPE_IPV4 = 0x0800;
    /** <p>EtherType identifying an 802.1Q VLAN tag.</p> */
    private static final int ETH_TYPE_8021Q = 0x8100;
    /** <p>EtherType identifying a QinQ VLAN tag.</p> */
    private static final int ETH_TYPE_QINQ = 0x88a8;
    /** <p>Ingress-name mappings used to determine packet direction.</p> */
    private final AppConfig config;
    /** <p>Current OpenFlow inventory used to resolve node and connector identities.</p> */
    private final OpenflowSwitchRegistry switchRegistry;

    /**
     * <p>Creates an extractor backed by ingress configuration and the OpenFlow inventory.</p>
     *
     * <p>Construction proceeds as follows:</p>
     * <ol>
     *   <li>Requires both dependencies to be non-null.</li>
     *   <li>Stores them for inventory lookup and direction resolution.</li>
     * </ol>
     *
     * @param config configuration mapping logical ingress names to directions
     * @param switchRegistry registry of OpenFlow switches and connectors
     * @throws NullPointerException if either dependency is null
     */
    public PacketInFeatureExtractor(final AppConfig config, final OpenflowSwitchRegistry switchRegistry) {
        this.config = Objects.requireNonNull(config, "config");
        /*This is the same instance that is passed into the OpenFlowBoostrapping Serivce that under the hood
        * the corresponding Boostrapping service takes and fills up with the switches that were detected by its
        * internal discovery service, so at this point, the PacketIn featur extractor sees a complete topology, reported through
        * a Snapshot object which contains the identified switches mapped to their records by their name, management IPs
        * or ids. Internally this in turn contains the OpenFlowSwitchRecord that contains a mapping between a switch, its management IP,
        * its logical name, its ID, and maps of its connector to OpenFlowConnectorRecord definitions organized in maps
        * by name, by id and port number. This information points towards the connector defining the description of the port,
        * its logical name, its hardware address and its corresponding port ID for identifiction with the expected incoming
        * traffic ports*/
        this.switchRegistry = Objects.requireNonNull(switchRegistry, "switchRegistry");
    }

    /**
     * <p>Extracts a timestamped classification context when the Ethernet frame and
     * its typed ingress can be resolved. Unsupported or incomplete network headers
     * may still produce a context with zero-valued protocol or port features.</p>
     *
     * <p>Extraction proceeds as follows:</p>
     * <ol>
     *   <li>Rejects a missing payload or an Ethernet frame shorter than 14 bytes.</li>
     *   <li>Extracts typed node and connector keys and resolves both in the inventory;
     *       returns empty if either identity is unavailable.</li>
     *   <li>Parses the payload, resolves direction from logical ingress names, logs
     *       the features, and constructs the context with the current time.</li>
     * </ol>
     *
     * <p>The workflow uses the context for classification and policy decisions.</p>
     *
     * @param notification OpenDaylight PacketIn notification
     * @return the extracted context, or empty for a short payload or unresolved ingress
     * @throws NullPointerException if notification is null
     */
    public Optional<PacketClassificationContext> extract(final PacketReceived notification) {

        /*Length base filtering to avoid non IPV4 Ethernet frames or incomplete frames*/
        final byte[] payload = notification.getPayload();
        if (payload == null || payload.length < 14) {
            LOG.debug("packet_features_rejected", "extract",
                    "La notificacion no contiene una trama Ethernet completa",
                    StructuredLogger.fields("payload_present", payload != null,
                            "payload_length", payload == null ? 0 : payload.length));
            return Optional.empty();
        }

        /*Resolves the corresponding ingress identity based on the notification ingress. These ingress present two fields
        * corresponding to the switch id and the connector id. This internal function performs a walk over the registered
        * keyed steps within the notificationRef which is a yang based model that contains the connector ID reeference and the
        * node ID reference, it walks the connector reference for these two and returns an optional of these*/
        final Optional<IngressIdentity> ingressIdentity = ingressIdentity(notification.getIngress());
        if (ingressIdentity.isEmpty()) {
            LOG.warn("packet_ingress_identity_rejected", "extract",
                    "La notificacion PacketIn no contiene una identidad tipada de nodo y conector OpenFlow.",
                    StructuredLogger.fields("raw_ingress", String.valueOf(notification.getIngress())), null);
            return Optional.empty();
        }

        // Simple translation layer for an optional, we are extracting the inner value or throwing an exception if nothin
        // is present. Basically a NoSuchElementException
        final IngressIdentity ingress = ingressIdentity.orElseThrow();

        /*
         * here is where the interesting thing comes in, when we translate the connector and node IDs from the packet In
         * notification, we need to figure out to what of our topologically extracted nodes this is akin to. Given that we use
         * our switches to determine the direction of the tunnel and also its connectors to determine the packet in filtering
         * we need these quickly
         *
         * For finding the node, i.e. the switch that we want we use the corresponding Switch Registry which was updated by
         * the OpenFlowTopologySerrvice through the Boostrapper and that contains a map that based on the registered openflow node
         * ID returns a corresponding OpenflowSwitchRecord. We use its method to find the same connector as determined by
         * the packet in notificaiton and we do the same to find the ingress connector, using a composite key formed by
         * first filtering on the node ID and then the connector ID
         */
        final Optional<OpenflowSwitchRecord> switchRecord =
                switchRegistry
                        .findByNodeId(ingress.nodeId());
        final Optional<OpenflowConnectorRecord> connectorRecord =
                switchRegistry
                        .findConnectorById(ingress.nodeId(), ingress.connectorId());

        /*Here we validate if we know the incoming switch, since we filter to the ones that form our policies, i.e. ECHO and
        * FOXTROT, we know that any other packet incoming from a different topology will return null and therefore be dropped
        */
        if (switchRecord.isEmpty() || connectorRecord.isEmpty()) {
            LOG.debug("packet_ingress_inventory_unresolved", "extract",
                    "La identidad tipada de ingreso no esta disponible en la instantanea actual del inventario OpenFlow.",
                    StructuredLogger.fields(
                            "ingress_openflow_node_id", ingress.nodeId(),
                            "ingress_connector_id", ingress.connectorId(),
                            "switch_registered", switchRecord.isPresent(),
                            "connector_registered", connectorRecord.isPresent()));
            return Optional.empty();
        }

        /*Before we can resolve the actual direction and provide the context for the ML workflow services next steps we need to
        * know what the payload of the packet is, which will be done internally to this class through a arser built to extract
        * the corresponding PacketIn fields and return a DTO used for immutable data access on other components*/
        final ParsedPacket parsed = parse(payload);

        /*Internally we now deterine the logical name coming from the switch and the connector id as these are used to
        * resolve the directional flow of traffic within the network*/
        final String switchName = switchRecord.orElseThrow().logicalName();
        final String connectorName = connectorRecord.orElseThrow().name();
        final FlowDirection direction = resolveDirection(switchName, connectorName);
        LOG.debug("packet_features_extracted", "extract",
                "Se extrajeron las caracteristicas seguras de la notificacion PacketIn",
                StructuredLogger.fields("payload_length", payload.length,
                        "ingress_openflow_node_id", ingress.nodeId(),
                        "ingress_connector_id", ingress.connectorId(),
                        "ingress_switch", switchName,
                        "ingress_connector", connectorName, "eth_type", parsed.ethType(),
                        "ip_protocol", parsed.ipProto(), "source_port", parsed.srcPort(),
                        "destination_port", parsed.dstPort(), "direction", direction));
        /*At this point we have created a parsing of the complete context of the packet, having extracted its features
        * from the corresponding Parsed Packet, as well as the ingress information and logical names for identification, including
        * the direction*/
        return Optional.of(new PacketClassificationContext(
                ingress.nodeId(), /*Identified ingress from the parsed connector and node IDs*/
                ingress.connectorId(), /*Identiifed connector from the parsed ingress identitiy*/
                switchName, /*logical names of the switch and connector from which the packet came in*/
                connectorName,
                new PacketFeatures( /*Translation into an external facing DTO using the parsed features into a
                directly serializable structure*/
                        parsed.ethType(),
                        parsed.ipProto(),
                        parsed.srcPort(),
                        parsed.dstPort()),
                direction,
                Instant.now()));
    }

    /**
     * <p>Decodes feature fields from a frame whose Ethernet header has already been checked.</p>
     *
     * <p>Parsing proceeds as follows:</p>
     * <ol>
     *   <li>Reads the EtherType and skips complete 802.1Q or QinQ tags.</li>
     *   <li>Returns zero protocol and ports for non-IPv4 or incomplete IPv4 headers.</li>
     *   <li>Reads the protocol and, for TCP or UDP with zero fragment offset and four
     *       available transport bytes, reads both ports; otherwise returns zero ports.</li>
     * </ol>
     *
     * @param payload frame bytes with at least a complete Ethernet header
     * @return parsed EtherType, protocol, and source and destination ports
     */
    private ParsedPacket parse(final byte[] payload) {
        // Ethernet starts with two 6-byte MAC addresses; bytes 12-13 identify the payload type.
        // unsignedShort combines the two bytes in network (big-endian) order.
        int cursor = 12;
        int ethType = unsignedShort(payload, cursor);
        cursor += 2;
        // A VLAN tag places a 2-byte control field and another 2-byte EtherType here.
        // Follow each complete 802.1Q/QinQ tag, including stacked tags, to the inner type.
        while ((ethType == ETH_TYPE_8021Q || ethType == ETH_TYPE_QINQ) && payload.length >= cursor + 4) {
            cursor += 2;
            ethType = unsignedShort(payload, cursor);
            cursor += 2;
        }
        // IPv4 requires at least 20 header bytes; unknown or incomplete fields remain zero.
        if (ethType != ETH_TYPE_IPV4 || payload.length < cursor + 20) {
            return new ParsedPacket(ethType, 0, 0, 0);
        }

        // The first IPv4 byte contains version in its upper four bits and IHL in its lower four.
        // Mask with 0x0f to keep IHL, then convert its 4-byte units to a byte count.
        final int ihlBytes = (payload[cursor] & 0x0f) * 4;
        if (ihlBytes < 20 || payload.length < cursor + ihlBytes) {
            return new ParsedPacket(ethType, 0, 0, 0);
        }
        // Protocol is byte 9 of the IPv4 header; & 0xff converts Java's signed byte to 0-255.
        final int ipProto = payload[cursor + 9] & 0xff;
        // Bytes 6-7 combine 3 flag bits and a 13-bit fragment offset. Mask away the flags.
        // A zero offset includes unfragmented packets and first fragments, even if MF is set.
        final int flagsFragmentOffset = unsignedShort(payload, cursor + 6);
        final boolean fragmented = (flagsFragmentOffset & 0x1fff) != 0;
        // IHL accounts for IPv4 options, so the transport header begins after the full IP header.
        final int l4 = cursor + ihlBytes;
        // TCP (6) and UDP (17) begin with two network-order 16-bit ports. Non-initial
        // fragments do not start with that header, and truncated headers cannot provide both ports.
        if (!fragmented && (ipProto == 6 || ipProto == 17) && payload.length >= l4 + 4) {
            return new ParsedPacket(ethType, ipProto, unsignedShort(payload, l4), unsignedShort(payload, l4 + 2));
        }
        return new ParsedPacket(ethType, ipProto, 0, 0);
    }

    /**
     * <p>Resolves a packet direction from the logical switch and connector names.</p>
     *
     * <p>Resolution proceeds as follows:</p>
     * <ol>
     *   <li>Delegates the supplied names to the configured classification ingress mapping.</li>
     *   <li>Returns the configured direction or its unknown-direction result.</li>
     * </ol>
     *
     * @param switchName resolved logical switch name
     * @param connectorName resolved logical connector name
     * @return direction selected by the configuration
     */
    private FlowDirection resolveDirection(final String switchName, final String connectorName) {
        return config.resolveClassificationIngress(switchName, connectorName);
    }

    /**
     * <p>Reads a network-byte-order unsigned 16-bit integer from the payload.</p>
     *
     * <p>Decoding proceeds as follows:</p>
     * <ol>
     *   <li>Masks both signed Java bytes to their unsigned values.</li>
     *   <li>Shifts the first byte and combines it with the second.</li>
     * </ol>
     *
     * @param payload bytes containing the value
     * @param offset index of its first byte
     * @return integer in the range 0 to 65535
     * @throws IndexOutOfBoundsException if either byte lies outside the array
     */
    private static int unsignedShort(final byte[] payload, final int offset) {
        return ((payload[offset] & 0xff) << 8) | (payload[offset + 1] & 0xff);
    }

    /**
     * <p>Extracts the typed YANG node and connector keys from a PacketIn ingress reference.</p>
     *
     * <p>Extraction proceeds as follows:</p>
     * <ol>
     *   <li>Returns empty when the reference or its identifier is absent.</li>
     *   <li>Traverses keyed identifier steps, retaining node and connector key values.</li>
     *   <li>Returns an identity only when both values were found.</li>
     * </ol>
     *
     * @param ingress ODL ingress reference, possibly null
     * @return both typed identifiers, or empty if either is absent
     */
    private static Optional<IngressIdentity> ingressIdentity(final NodeConnectorRef ingress) {
        if (ingress == null || ingress.getValue() == null) {
            return Optional.empty();
        }
        String nodeId = null;
        String connectorId = null;

        /*In this case, basedon the incomming reference, we know that the Yang model defines a container called
        * nodes whcih internally contains a node-id leaf alongside a list of connectors defined as a list keyed
        * by conenctor-id. This is the information that we need to parse here. In the case o a PacketIn notifcation
        * e know that the input has one node id and a nested node connector id*/
        for (BindingInstanceIdentifier.Step step : ingress.getValue().steps()) {
            if (!(step instanceof KeyStep<?, ?> keyStep)) {
                continue;
            }
            /*This mechanism evaluates if the corresponding KeyStep identified fromm the parsing of the ingress
            * corresponds to a NodeKey which contains internally the ID of the node, this is a translation between
            * the node leaf into a Java object */
            if (keyStep.key() instanceof NodeKey nodeKey) {
                nodeId = nodeKey.getId().getValue();
            }
            /*Otherwise, we can check if the NodeConnectorKey has been identified for the step. Since we are stepping through
            * the entire keyed steps in the object for the node Connector ref, we are going to get the node id
            * and then a sequence of node connector keys identifiying the id of each connector
            * attacked to a node. In this case we ignore any other step or information as we need, for now these two */
            else if (keyStep.key() instanceof NodeConnectorKey connectorKey) {
                connectorId = connectorKey.getId().getValue();
            }
        }
        if (nodeId == null || connectorId == null) {
            return Optional.empty();
        }

        /*If we were able to process the two corresponding keys, that is the nodeID and the connectorID then we
        * return the optional out of them.*/
        return Optional.of(new IngressIdentity(nodeId, connectorId));
    }

    /**
     * <p>Holds the parsed Ethernet and IPv4 features without retaining the packet payload.</p>
     *
     * @param ethType decoded Ethernet type
     * @param ipProto IPv4 protocol or zero when unavailable
     * @param srcPort source port or zero when unavailable
     * @param dstPort destination port or zero when unavailable
     */
    private record ParsedPacket(int ethType, int ipProto, int srcPort, int dstPort) {
    }

    /**
     * <p>Holds OpenFlow node and connector identifiers extracted from the typed YANG reference.</p>
     *
     * @param nodeId complete OpenFlow node identifier
     * @param connectorId complete OpenFlow connector identifier
     */
    private record IngressIdentity(String nodeId, /*Openflow Switch ID*/
                                   String connectorId /*Openflow Connector ID */) {
    }
}
