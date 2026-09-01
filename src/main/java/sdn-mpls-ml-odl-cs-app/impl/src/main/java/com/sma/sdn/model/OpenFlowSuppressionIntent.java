/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

import java.util.Objects;

/** Describe un flujo OpenFlow temporal que evita PacketIn repetidos para un servicio. */
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
