/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.serialization.xml;

import com.sma.sdn.model.OpenFlowSuppressionIntent;
import com.sma.sdn.util.XmlSupport;

/** Serializa flujos temporales que reenvian servicios ya decididos sin copiar paquetes al controlador. */
public final class OpenFlowSuppressionFlowXmlSerializer {
    /** Construye el XML RESTCONF de una regla de supresion IPv4 especifica de servicio. */
    public String serialize(final OpenFlowSuppressionIntent intent) {
        final String transportMatch = transportMatch(intent);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<flow xmlns=\"urn:opendaylight:flow:inventory\">"
                + "<id>" + XmlSupport.escape(intent.flowId()) + "</id>"
                + "<flow-name>" + XmlSupport.escape(intent.flowId()) + "</flow-name>"
                + "<table_id>" + intent.tableId() + "</table_id>"
                + "<priority>" + intent.priority() + "</priority>"
                + "<cookie>" + intent.cookie() + "</cookie>"
                + "<idle-timeout>" + intent.idleTimeoutSeconds() + "</idle-timeout>"
                + "<hard-timeout>" + intent.hardTimeoutSeconds() + "</hard-timeout>"
                + "<flags>SEND_FLOW_REM</flags>"
                + "<match><in-port>" + XmlSupport.escape(intent.ingressConnectorId()) + "</in-port>"
                + "<ethernet-match><ethernet-type><type>" + intent.ethType()
                + "</type></ethernet-type></ethernet-match>"
                + "<ip-match><ip-protocol>" + intent.ipProtocol() + "</ip-protocol></ip-match>"
                + transportMatch
                + "</match><instructions><instruction><order>0</order><apply-actions>"
                + "<action><order>0</order><output-action><output-node-connector>"
                + XmlSupport.escape(intent.outputConnectorId())
                + "</output-node-connector><max-length>0</max-length></output-action></action>"
                + "</apply-actions></instruction></instructions></flow>";
    }

    private static String transportMatch(final OpenFlowSuppressionIntent intent) {
        if (intent.tcpDestinationPort() != null) {
            return "<tcp-match><tcp-destination-port>" + intent.tcpDestinationPort()
                    + "</tcp-destination-port></tcp-match>";
        }
        if (intent.udpDestinationPort() != null) {
            return "<udp-match><udp-destination-port>" + intent.udpDestinationPort()
                    + "</udp-destination-port></udp-match>";
        }
        return "";
    }
}
