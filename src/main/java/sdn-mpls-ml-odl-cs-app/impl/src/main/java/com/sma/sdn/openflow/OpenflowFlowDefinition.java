/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package com.sma.sdn.openflow;

import java.util.Objects;

/**
 * <p>Describes an OpenFlow rule matching an ingress connector and Ethernet type,
 * optionally copying traffic to the controller before forwarding it to an output port.
 * {@link OpenflowFlowXmlSerializer} converts the definition into a RESTCONF payload.</p>
 *
 * @param flowId persistent flow identifier
 * @param tableId OpenFlow table number
 * @param priority match priority
 * @param cookie flow cookie
 * @param ethernetType Ethernet type as a decimal integer
 * @param inputConnectorId full ingress connector identifier
 * @param inputConnectorName human-readable ingress port name
 * @param outputPortNumber numeric output port
 * @param outputConnectorName human-readable output port name
 * @param copyToController whether to output a copy to the controller before forwarding
 */
public record OpenflowFlowDefinition(
        String flowId,
        int tableId,
        int priority,
        long cookie,
        int ethernetType,
        String inputConnectorId,
        String inputConnectorName,
        int outputPortNumber,
        String outputConnectorName,
        boolean copyToController) {

    /**
     * <p>Validates required strings and rejects negative numeric fields.</p>
     *
     * <p>The validation performs these checks:</p>
     * <ol>
     *   <li>Require non-null identifiers and connector names.</li>
     *   <li>Reject negative table, priority, cookie, Ethernet type and output port values.</li>
     * </ol>
     *
     * @param flowId flow identifier
     * @param tableId OpenFlow table number
     * @param priority match priority
     * @param cookie flow cookie
     * @param ethernetType Ethernet type
     * @param inputConnectorId full ingress connector identifier
     * @param inputConnectorName ingress port name
     * @param outputPortNumber output port number
     * @param outputConnectorName output port name
     * @param copyToController whether to copy matching traffic to the controller
     * @throws NullPointerException if a required string is null
     * @throws IllegalArgumentException if a numeric field is negative
     */
    public OpenflowFlowDefinition {
        Objects.requireNonNull(flowId, "flowId");
        Objects.requireNonNull(inputConnectorId, "inputConnectorId");
        Objects.requireNonNull(inputConnectorName, "inputConnectorName");
        Objects.requireNonNull(outputConnectorName, "outputConnectorName");
        if (tableId < 0 || priority < 0 || cookie < 0 || ethernetType < 0 || outputPortNumber < 0) {
            throw new IllegalArgumentException("Los valores numericos del flujo no pueden ser negativos");
        }
    }
}
