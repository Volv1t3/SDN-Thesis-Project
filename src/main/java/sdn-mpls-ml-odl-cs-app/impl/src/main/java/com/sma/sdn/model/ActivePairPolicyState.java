/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

import java.time.Instant;
import java.util.Map;

/**
 * <p>Records the active policy for a tunnel pair, its lifetime, generation, and directional LSP application outcomes. The active policy registry stores this state and the policy coordinator consults it when refreshing or replacing a policy.</p>
 *
 * @param pairKey managed tunnel-pair identifier
 * @param serviceKey normalized identity of the traffic service
 * @param className classification class name
 * @param profileName traffic-policy profile name
 * @param dscp policy differentiated services code point
 * @param mplsTc policy MPLS traffic-class value
 * @param requestedBandwidthKbps requested bandwidth in kilobits per second
 * @param requestedBandwidthBase64 PCEP bandwidth encoding
 * @param setupPriority logical controller-side setup priority
 * @param holdPriority logical controller-side hold priority
 * @param policySchemaVersion policy schema version
 * @param policyHash policy fingerprint
 * @param generation active-policy generation
 * @param installedAt policy installation time
 * @param lastRefreshedAt latest policy refresh time
 * @param expiresAt expiration time
 * @param lspApplications direction-keyed LSP application results
 */
public record ActivePairPolicyState(
        String pairKey,
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
        long generation,
        Instant installedAt,
        Instant lastRefreshedAt,
        Instant expiresAt,
        Map<String, DirectionalLspApplicationRecord> lspApplications) {
    /**
     * <p>Replace a null application map with an empty map; otherwise retain an immutable copy.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Replace a null application map with an empty map; otherwise retain an immutable copy.</li>
     * </ol>
     *
     * @param pairKey managed tunnel-pair identifier
     * @param serviceKey normalized identity of the traffic service
     * @param className classification class name
     * @param profileName traffic-policy profile name
     * @param dscp policy differentiated services code point
     * @param mplsTc policy MPLS traffic-class value
     * @param requestedBandwidthKbps requested bandwidth in kilobits per second
     * @param requestedBandwidthBase64 PCEP bandwidth encoding
     * @param setupPriority logical controller-side setup priority
     * @param holdPriority logical controller-side hold priority
     * @param policySchemaVersion policy schema version
     * @param policyHash policy fingerprint
     * @param generation active-policy generation
     * @param installedAt policy installation time
     * @param lastRefreshedAt latest policy refresh time
     * @param expiresAt expiration time
     * @param lspApplications direction-keyed LSP application results
     * @throws NullPointerException if the supplied map contains a null key or value
     */
    public ActivePairPolicyState {
        lspApplications = lspApplications == null ? Map.of() : Map.copyOf(lspApplications);
    }
}
