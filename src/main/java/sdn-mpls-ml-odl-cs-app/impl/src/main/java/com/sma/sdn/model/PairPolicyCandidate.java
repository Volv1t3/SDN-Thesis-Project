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
 * <p>Represents a policy selected from one direction's evidence during pair-policy consensus.
 * It retains the selected direction and ingress switch alongside the service, policy values,
 * fingerprint, consensus status, and selection time. Selection alone does not establish that
 * the policy has been admitted or applied to any LSP.</p>
 *
 * <p>{@link com.sma.sdn.policy.PairPolicyConsensusService} constructs candidates from
 * {@link DirectionalPolicyEvidence}; {@link com.sma.sdn.policy.PairPolicyCoordinator}
 * evaluates the candidate against the active policy and the configured application scope.</p>
 *
 * @param pairKey managed tunnel-pair identifier
 * @param serviceKey normalized identity of the traffic service
 * @param selectedFromDirectionKey direction from which the policy was selected
 * @param selectedFromIngressSwitchName ingress switch supplying selected evidence
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
 * @param consensusStatus consensus outcome
 * @param selectedAt candidate selection time
 */
public record PairPolicyCandidate(
        String pairKey,
        ServiceKey serviceKey,
        String selectedFromDirectionKey,
        String selectedFromIngressSwitchName,
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
        PairConsensusStatus consensusStatus,
        Instant selectedAt) {
}
