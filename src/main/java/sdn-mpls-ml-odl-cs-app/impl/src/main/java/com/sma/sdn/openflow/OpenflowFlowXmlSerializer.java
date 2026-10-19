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

import com.sma.sdn.util.XmlSupport;

/**
 * <p>Serializes an {@link OpenflowFlowDefinition} into the OpenDaylight flow inventory
 * XML used by {@link OpenflowFlowProvisioningService} for RESTCONF PUT requests.</p>
 */
public final class OpenflowFlowXmlSerializer {

    /**
     * <p>Builds a permanent flow matching an ingress connector and Ethernet type, with
     * an optional controller copy followed by output to the configured numeric port.</p>
     *
     * <p>The serialization proceeds as follows:</p>
     * <ol>
     *   <li>Add a controller output action with maximum length 65535 when requested.</li>
     *   <li>Add the numeric output-port action after the optional controller action.</li>
     *   <li>Write the flow identity, timeouts, match and ordered actions, escaping text values.</li>
     * </ol>
     *
     * @param definition flow fields and controller-copy choice
     * @return complete XML document for a RESTCONF PUT
     */
    public String serialize(final OpenflowFlowDefinition definition) {
        final StringBuilder actions = new StringBuilder();
        int order = 0;
        if (definition.copyToController()) {
            appendOutputAction(actions, order++, "CONTROLLER", 65535);
        }
        appendOutputAction(actions, order, Integer.toString(definition.outputPortNumber()), 0);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<flow xmlns=\"urn:opendaylight:flow:inventory\">"
                + "<id>" + XmlSupport.escape(definition.flowId()) + "</id>"
                + "<table_id>" + definition.tableId() + "</table_id>"
                + "<priority>" + definition.priority() + "</priority>"
                + "<cookie>" + definition.cookie() + "</cookie>"
                + "<idle-timeout>0</idle-timeout><hard-timeout>0</hard-timeout>"
                + "<flags>SEND_FLOW_REM</flags>"
                + "<match><in-port>" + XmlSupport.escape(definition.inputConnectorId()) + "</in-port>"
                + "<ethernet-match><ethernet-type><type>" + definition.ethernetType()
                + "</type></ethernet-type></ethernet-match></match>"
                + "<instructions><instruction><order>0</order><apply-actions>"
                + actions
                + "</apply-actions></instruction></instructions></flow>";
    }

    /**
     * <p>Appends one output-action element to the accumulating XML.</p>
     *
     * <p>The helper writes the action as follows:</p>
     * <ol>
     *   <li>Write the action order and escaped output connector.</li>
     *   <li>Write the maximum packet length and close the action element.</li>
     * </ol>
     *
     * @param target mutable XML action accumulator
     * @param order execution order of the action
     * @param outputConnector numeric destination or {@code CONTROLLER}
     * @param maxLength maximum packet length for the output action
     */
    private static void appendOutputAction(
            final StringBuilder target, final int order, final String outputConnector, final int maxLength) {
        target.append("<action><order>").append(order).append("</order><output-action>")
                .append("<output-node-connector>").append(XmlSupport.escape(outputConnector))
                .append("</output-node-connector><max-length>").append(maxLength)
                .append("</max-length></output-action></action>");
    }
}
