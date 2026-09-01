/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

import java.time.Instant;
import java.util.Optional;

/** Result of evaluating a consensus bucket, including non-actionable states. */
public record PairConsensusDecision(
        String pairKey,
        ServiceKey serviceKey,
        PairConsensusStatus consensusStatus,
        Optional<PairPolicyCandidate> selectedCandidate,
        Optional<DirectionalPolicyEvidence> leftEvidence,
        Optional<DirectionalPolicyEvidence> rightEvidence,
        String conflictResolutionReason,
        Instant decidedAt) {
    public PairConsensusDecision {
        selectedCandidate = selectedCandidate == null ? Optional.empty() : selectedCandidate;
        leftEvidence = leftEvidence == null ? Optional.empty() : leftEvidence;
        rightEvidence = rightEvidence == null ? Optional.empty() : rightEvidence;
    }
}
