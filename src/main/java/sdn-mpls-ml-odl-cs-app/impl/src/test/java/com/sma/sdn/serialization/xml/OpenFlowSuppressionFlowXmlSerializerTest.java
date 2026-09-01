/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.serialization.xml;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sma.sdn.model.OpenFlowSuppressionIntent;
import org.junit.jupiter.api.Test;

/** Verifica el contrato XML de los flujos temporales de supresion OpenFlow. */
class OpenFlowSuppressionFlowXmlSerializerTest {
    private final OpenFlowSuppressionFlowXmlSerializer serializer = new OpenFlowSuppressionFlowXmlSerializer();

    @Test
    void serializesIcmpWithTimeoutsAndConnectorIds() {
        final String xml = serializer.serialize(intent(1, null, null));

        assertTrue(xml.contains("<priority>250</priority>"));
        assertTrue(xml.contains("<idle-timeout>10</idle-timeout>"));
        assertTrue(xml.contains("<hard-timeout>60</hard-timeout>"));
        assertTrue(xml.contains("<in-port>openflow:10:1</in-port>"));
        assertTrue(xml.contains("<type>2048</type>"));
        assertTrue(xml.contains("<ip-protocol>1</ip-protocol>"));
        assertTrue(xml.contains("<output-node-connector>openflow:10:2</output-node-connector>"));
    }

    @Test
    void serializesTcpAndUdpDestinationPorts() {
        final String tcpXml = serializer.serialize(intent(6, 80, null));
        final String udpXml = serializer.serialize(intent(17, null, 53));

        assertTrue(tcpXml.contains("<ip-protocol>6</ip-protocol>"));
        assertTrue(tcpXml.contains("<tcp-destination-port>80</tcp-destination-port>"));
        assertTrue(udpXml.contains("<ip-protocol>17</ip-protocol>"));
        assertTrue(udpXml.contains("<udp-destination-port>53</udp-destination-port>"));
    }

    private static OpenFlowSuppressionIntent intent(
            final int protocol, final Integer tcpDestinationPort, final Integer udpDestinationPort) {
        return new OpenFlowSuppressionIntent("sma-suppress-echo", "openflow:10", "openflow%3A10", 0, 250,
                0x8ADC01L, 10, 60, "openflow:10:1", "openflow:10:2", 2048, protocol, tcpDestinationPort,
                udpDestinationPort, "ICMP", "icmp_tunnel_policy");
    }
}
