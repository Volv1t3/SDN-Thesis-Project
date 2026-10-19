/*
 * Copyright (c) 2026 Santiago Arellano and others.
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
        final byte[] payload = notification.getPayload();
        if (payload == null || payload.length < 14) {
            LOG.debug("packet_features_rejected", "extract",
                    "La notificacion no contiene una trama Ethernet completa",
                    StructuredLogger.fields("payload_present", payload != null,
                            "payload_length", payload == null ? 0 : payload.length));
            return Optional.empty();
        }

        final Optional<IngressIdentity> ingressIdentity = ingressIdentity(notification.getIngress());
        if (ingressIdentity.isEmpty()) {
            LOG.warn("packet_ingress_identity_rejected", "extract",
                    "La notificacion PacketIn no contiene una identidad tipada de nodo y conector OpenFlow.",
                    StructuredLogger.fields("raw_ingress", String.valueOf(notification.getIngress())), null);
            return Optional.empty();
        }

        final IngressIdentity ingress = ingressIdentity.orElseThrow();
        final Optional<OpenflowSwitchRecord> switchRecord = switchRegistry.findByNodeId(ingress.nodeId());
        final Optional<OpenflowConnectorRecord> connectorRecord = switchRegistry.findConnectorById(
                ingress.nodeId(), ingress.connectorId());
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

        final ParsedPacket parsed = parse(payload);
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
        return Optional.of(new PacketClassificationContext(
                ingress.nodeId(),
                ingress.connectorId(),
                switchName,
                connectorName,
                new PacketFeatures(parsed.ethType(), parsed.ipProto(), parsed.srcPort(), parsed.dstPort()),
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
        int cursor = 12;
        int ethType = unsignedShort(payload, cursor);
        cursor += 2;
        while ((ethType == ETH_TYPE_8021Q || ethType == ETH_TYPE_QINQ) && payload.length >= cursor + 4) {
            cursor += 2;
            ethType = unsignedShort(payload, cursor);
            cursor += 2;
        }
        if (ethType != ETH_TYPE_IPV4 || payload.length < cursor + 20) {
            return new ParsedPacket(ethType, 0, 0, 0);
        }

        final int ihlBytes = (payload[cursor] & 0x0f) * 4;
        if (ihlBytes < 20 || payload.length < cursor + ihlBytes) {
            return new ParsedPacket(ethType, 0, 0, 0);
        }
        final int ipProto = payload[cursor + 9] & 0xff;
        final int flagsFragmentOffset = unsignedShort(payload, cursor + 6);
        final boolean fragmented = (flagsFragmentOffset & 0x1fff) != 0;
        final int l4 = cursor + ihlBytes;
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
        for (BindingInstanceIdentifier.Step step : ingress.getValue().steps()) {
            if (!(step instanceof KeyStep<?, ?> keyStep)) {
                continue;
            }
            if (keyStep.key() instanceof NodeKey nodeKey) {
                nodeId = nodeKey.getId().getValue();
            } else if (keyStep.key() instanceof NodeConnectorKey connectorKey) {
                connectorId = connectorKey.getId().getValue();
            }
        }
        if (nodeId == null || connectorId == null) {
            return Optional.empty();
        }
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
    private record IngressIdentity(String nodeId, String connectorId) {
    }
}
