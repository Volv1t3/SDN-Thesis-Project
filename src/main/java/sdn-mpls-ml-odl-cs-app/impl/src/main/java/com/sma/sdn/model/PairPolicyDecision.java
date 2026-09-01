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

/** Final controller-side result for one PacketIn after pair consensus and preemption. */
public record PairPolicyDecision(
        String pairKey,
        ServiceKey serviceKey,
        PairConsensusStatus consensusStatus,
        Optional<PairPolicyCandidate> candidate,
        PolicyPreemptionDecisionType preemptionDecision,
        Optional<ActivePairPolicyState> activePolicy,
        List<DirectionalLspApplicationRecord> applications,
        String detail) {
    public PairPolicyDecision {
        candidate = candidate == null ? Optional.empty() : candidate;
        activePolicy = activePolicy == null ? Optional.empty() : activePolicy;
        applications = applications == null ? List.of() : List.copyOf(applications);
    }
}
