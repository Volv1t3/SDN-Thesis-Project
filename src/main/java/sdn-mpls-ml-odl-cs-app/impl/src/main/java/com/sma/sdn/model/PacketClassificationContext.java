/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

import java.time.Instant;

/**
 * <p>Combines extracted packet features with ingress identity, flow direction, and receipt time.
 * The packet workflow passes this context to classification; the registrar uses its ingress and
 * feature fields to construct exact and service-class cache keys.</p>
 *
 * @param ingressOpenflowNodeId OpenFlow node identifier of the ingress switch
 * @param ingressConnectorId OpenFlow identifier of the ingress connector
 * @param ingressSwitchName configured logical name of the ingress switch
 * @param ingressConnectorName configured or resolved ingress connector name
 * @param packetFeatures classifier input features extracted from the packet
 * @param direction flow direction resolved for the packet
 * @param receivedAt time the packet context was recorded
 */
public record PacketClassificationContext(
        String ingressOpenflowNodeId,
        String ingressConnectorId,
        String ingressSwitchName,
        String ingressConnectorName,
        PacketFeatures packetFeatures,
        FlowDirection direction,
        Instant receivedAt) {
}
