/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

import java.util.Objects;

/**
 * <p>Describes the match, output, lifetime, and policy context of a suppression flow. OpenFlow services use this intent to construct and provision a flow for packet traffic.</p>
 *
 * @param flowId OpenFlow flow identifier
 * @param nodeId node identifier
 * @param encodedNodeId encoded node identifier
 * @param tableId OpenFlow table identifier
 * @param priority flow priority
 * @param cookie OpenFlow cookie
 * @param idleTimeoutSeconds idle timeout in seconds
 * @param hardTimeoutSeconds hard timeout in seconds
 * @param ingressConnectorId ingress connector identifier
 * @param outputConnectorId output connector identifier
 * @param ethType Ethernet type
 * @param ipProtocol IP protocol number
 * @param tcpDestinationPort TCP destination port, when applicable
 * @param udpDestinationPort UDP destination port, when applicable
 * @param classificationClassName classification class name
 * @param profileName traffic-policy profile name
 */
public record OpenFlowSuppressionIntent(
        String flowId,
        String nodeId,
        String encodedNodeId,
        int tableId,
        int priority,
        long cookie,
        int idleTimeoutSeconds,
        int hardTimeoutSeconds,
        String ingressConnectorId,
        String outputConnectorId,
        int ethType,
        int ipProtocol,
        Integer tcpDestinationPort,
        Integer udpDestinationPort,
        String classificationClassName,
        String profileName) {
    /**
     * <p>Require the flow, node, connector, class, and profile string identifiers to be non-null.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Require the flow, node, connector, class, and profile string identifiers to be non-null.</li>
     * </ol>
     *
     *
     * @param flowId OpenFlow flow identifier
     * @param nodeId node identifier
     * @param encodedNodeId encoded node identifier
     * @param tableId OpenFlow table identifier
     * @param priority flow priority
     * @param cookie OpenFlow cookie
     * @param idleTimeoutSeconds idle timeout in seconds
     * @param hardTimeoutSeconds hard timeout in seconds
     * @param ingressConnectorId ingress connector identifier
     * @param outputConnectorId output connector identifier
     * @param ethType Ethernet type
     * @param ipProtocol IP protocol number
     * @param tcpDestinationPort TCP destination port, when applicable
     * @param udpDestinationPort UDP destination port, when applicable
     * @param classificationClassName classification class name
     * @param profileName traffic-policy profile name
     * @throws NullPointerException if a required value or copied list element is null
     */
    public OpenFlowSuppressionIntent {
        Objects.requireNonNull(flowId, "flowId");
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(encodedNodeId, "encodedNodeId");
        Objects.requireNonNull(ingressConnectorId, "ingressConnectorId");
        Objects.requireNonNull(outputConnectorId, "outputConnectorId");
        Objects.requireNonNull(classificationClassName, "classificationClassName");
        Objects.requireNonNull(profileName, "profileName");
    }
}
