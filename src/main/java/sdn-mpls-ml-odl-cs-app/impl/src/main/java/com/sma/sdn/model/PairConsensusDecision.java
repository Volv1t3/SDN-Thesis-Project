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

/**
 * <p>Captures the outcome of evaluating directional evidence, including a possible policy candidate and a conflict-resolution reason. The pair-policy coordinator consumes this decision before considering preemption.</p>
 *
 * @param pairKey managed tunnel-pair identifier
 * @param serviceKey normalized identity of the traffic service
 * @param consensusStatus consensus outcome
 * @param selectedCandidate optional candidate selected by consensus
 * @param leftEvidence optional evidence from the pair's left side
 * @param rightEvidence optional evidence from the pair's right side
 * @param conflictResolutionReason explanation of the consensus result
 * @param decidedAt decision time
 */
public record PairConsensusDecision(
        String pairKey,
        ServiceKey serviceKey,
        PairConsensusStatus consensusStatus,
        Optional<PairPolicyCandidate> selectedCandidate,
        Optional<DirectionalPolicyEvidence> leftEvidence,
        Optional<DirectionalPolicyEvidence> rightEvidence,
        String conflictResolutionReason,
        Instant decidedAt) {
    /**
     * <p>Replace null candidate and directional evidence optionals with empty optionals.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Replace null candidate and directional evidence optionals with empty optionals.</li>
     * </ol>
     *
     * @param pairKey managed tunnel-pair identifier
     * @param serviceKey normalized identity of the traffic service
     * @param consensusStatus consensus outcome
     * @param selectedCandidate optional candidate selected by consensus
     * @param leftEvidence optional evidence from the pair's left side
     * @param rightEvidence optional evidence from the pair's right side
     * @param conflictResolutionReason explanation of the consensus result
     * @param decidedAt decision time
     */
    public PairConsensusDecision {
        selectedCandidate = selectedCandidate == null ? Optional.empty() : selectedCandidate;
        leftEvidence = leftEvidence == null ? Optional.empty() : leftEvidence;
        rightEvidence = rightEvidence == null ? Optional.empty() : rightEvidence;
    }
}
