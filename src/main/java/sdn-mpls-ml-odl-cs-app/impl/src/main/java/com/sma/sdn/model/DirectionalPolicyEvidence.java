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
 * <p>Carries one direction's classification, service identity, policy values, and validity interval. The directional evidence registry stores it for evaluation by the pair-policy consensus service.</p>
 *
 * @param pairKey managed tunnel-pair identifier
 * @param directionKey managed tunnel-direction identifier
 * @param ingressSwitchName logical ingress switch name
 * @param ingressConnectorName ingress connector name
 * @param packetFeatures observed packet features
 * @param serviceKey normalized identity of the traffic service
 * @param className classification class name
 * @param profileName traffic-policy profile name
 * @param dscp policy differentiated services code point
 * @param mplsTc policy MPLS traffic-class value
 * @param requestedBandwidthKbps requested bandwidth in kilobits per second
 * @param requestedBandwidthBase64 PCEP bandwidth encoding
 * @param setupPriority LSP setup priority
 * @param holdPriority LSP holding priority
 * @param policySchemaVersion policy schema version
 * @param policyHash policy fingerprint
 * @param observedAt time the evidence was observed
 * @param expiresAt expiration time
 */
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
