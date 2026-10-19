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

/**
 * <p>Renders an {@link OpenFlowSuppressionIntent} as an OpenDaylight flow-inventory
 * XML document. {@link com.sma.sdn.openflow.OpenFlowSuppressionService} uses the result
 * to install a temporary forwarding rule for a previously decided service.</p>
 */
public final class OpenFlowSuppressionFlowXmlSerializer {
    /**
     * <p>Builds a flow matching the intent's ingress, Ethernet and IP fields and optional
     * transport destination port, with an output action and flow-removal notification.</p>
     *
     * <p>Serialization proceeds as follows:</p>
     * <ol>
     *   <li>Select a TCP or UDP destination-port match when one is present.</li>
     *   <li>Write the flow identity, table, priority, cookie, timeouts, and matches,
     *       escaping text values for XML.</li>
     *   <li>Append the output action for the intent's connector and return the document.</li>
     * </ol>
     *
     * @param intent suppression rule fields and forwarding connector
     * @return OpenDaylight flow-inventory XML document
     * @throws NullPointerException if the intent is null
     */
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

    /**
     * <p>Creates the optional transport destination-port match, preferring TCP if both
     * destination ports are present.</p>
     *
     * <p>Selection proceeds as follows:</p>
     * <ol>
     *   <li>Return a TCP match if a TCP destination port is set.</li>
     *   <li>Otherwise return a UDP match if set, or an empty fragment.</li>
     * </ol>
     *
     * @param intent suppression intent containing the optional ports
     * @return transport-match XML fragment, or an empty string
     * @throws NullPointerException if the intent is null
     */
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
