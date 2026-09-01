/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

import java.time.Instant;

/** One side's recent classification evidence for a service pair. */
public record DirectionalPolicyEvidence(
        String pairKey,
        String directionKey,
        String ingressSwitchName,
        String ingressConnectorName,
        PacketFeatures packetFeatures,
        ServiceKey serviceKey,
        String className,
        String profileName,
        int dscp,
        int mplsTc,
        int requestedBandwidthKbps,
        String requestedBandwidthBase64,
        int setupPriority,
        int holdPriority,
        String policySchemaVersion,
        String policyHash,
        Instant observedAt,
        Instant expiresAt) {
}
