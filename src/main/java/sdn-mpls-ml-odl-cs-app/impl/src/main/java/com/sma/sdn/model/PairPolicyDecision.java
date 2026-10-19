/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

import java.util.List;
import java.util.Optional;

/**
 * <p>Reports the coordinator's consensus, preemption, active-policy, and directional application results for a packet workflow. A missing candidate or application list can represent a deferred or unchanged decision.</p>
 *
 * @param pairKey managed tunnel-pair identifier
 * @param serviceKey normalized identity of the traffic service
 * @param consensusStatus consensus outcome
 * @param candidate optional candidate selected by consensus
 * @param preemptionDecision preemption decision, when available
 * @param activePolicy optional active pair policy
 * @param applications directional LSP application results
 * @param detail diagnostic detail
 */
public record PairPolicyDecision(
        String pairKey,
        ServiceKey serviceKey,
        PairConsensusStatus consensusStatus,
        Optional<PairPolicyCandidate> candidate,
        PolicyPreemptionDecisionType preemptionDecision,
        Optional<ActivePairPolicyState> activePolicy,
        List<DirectionalLspApplicationRecord> applications,
        String detail) {
    /**
     * <p>Replace null optionals with empty optionals and retain an immutable application list, using an empty list for null.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Replace null optionals with empty optionals and retain an immutable application list, using an empty list for null.</li>
     * </ol>
     *
     * @param pairKey managed tunnel-pair identifier
     * @param serviceKey normalized identity of the traffic service
     * @param consensusStatus consensus outcome
     * @param candidate optional candidate selected by consensus
     * @param preemptionDecision preemption decision, when available
     * @param activePolicy optional active pair policy
     * @param applications directional LSP application results
     * @param detail diagnostic detail
     * @throws NullPointerException if the supplied application list contains a null element
     */
    public PairPolicyDecision {
        candidate = candidate == null ? Optional.empty() : candidate;
        activePolicy = activePolicy == null ? Optional.empty() : activePolicy;
        applications = applications == null ? List.of() : List.copyOf(applications);
    }
}
