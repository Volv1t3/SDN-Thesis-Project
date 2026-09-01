/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.policy;

import com.sma.sdn.model.ActivePairPolicyState;
import com.sma.sdn.model.DirectionalPolicyEvidence;
import com.sma.sdn.model.PairConsensusBucket;
import com.sma.sdn.model.PairConsensusDecision;
import com.sma.sdn.model.PairConsensusStatus;
import com.sma.sdn.model.PairPolicyCandidate;
import com.sma.sdn.model.PairConsensusEqualPriorityAction;
import java.time.Instant;
import java.util.Optional;

/** Selects an actionable pair candidate only after both directional observations agree or resolve safely. */
public final class PairPolicyConsensusService {
    private final boolean requireBothDirections;
    private final boolean provisionalSingleSideEnabled;
    private final PairConsensusEqualPriorityAction equalPriorityAction;
    private final ServiceKeyResolver serviceKeyResolver;

    public PairPolicyConsensusService(final boolean requireBothDirections, final boolean provisionalSingleSideEnabled,
            final PairConsensusEqualPriorityAction equalPriorityAction, final ServiceKeyResolver serviceKeyResolver) {
        this.requireBothDirections = requireBothDirections;
        this.provisionalSingleSideEnabled = provisionalSingleSideEnabled;
        this.equalPriorityAction = equalPriorityAction;
        this.serviceKeyResolver = serviceKeyResolver;
    }

    public PairConsensusDecision evaluate(final PairConsensusBucket bucket,
            final Optional<ActivePairPolicyState> currentActivePolicy, final Instant now) {
        final Optional<DirectionalPolicyEvidence> left = bucket.leftEvidence();
        final Optional<DirectionalPolicyEvidence> right = bucket.rightEvidence();
        if (left.isEmpty() || right.isEmpty()) {
            if (!requireBothDirections && provisionalSingleSideEnabled) {
                final DirectionalPolicyEvidence selected = left.orElseGet(right::orElseThrow);
                return decision(bucket, PairConsensusStatus.CONSENSUS_TIMEOUT_SINGLE_SIDE_PROVISIONAL,
                        Optional.of(candidate(selected, PairConsensusStatus.CONSENSUS_TIMEOUT_SINGLE_SIDE_PROVISIONAL, now)),
                        left, right, "single-side provisional mode", now);
            }
            return decision(bucket, PairConsensusStatus.PENDING_ONE_SIDE, Optional.empty(), left, right,
                    "missing usable evidence from the opposite side", now);
        }
        final DirectionalPolicyEvidence leftEvidence = left.orElseThrow();
        final DirectionalPolicyEvidence rightEvidence = right.orElseThrow();
        if (leftEvidence.policyHash().equals(rightEvidence.policyHash())) {
            return decision(bucket, PairConsensusStatus.CONSENSUS_MATCH,
                    Optional.of(candidate(leftEvidence, PairConsensusStatus.CONSENSUS_MATCH, now)), left, right,
                    "directional policy hashes match", now);
        }
        final Optional<String> expectedClass = serviceKeyResolver.expectedClassFor(bucket.serviceKey());
        if (expectedClass.isPresent()) {
            if (expectedClass.get().equals(leftEvidence.className())) {
                return decision(bucket, PairConsensusStatus.CONSENSUS_CONFLICT_SERVICE_KEY_SELECTED,
                        Optional.of(candidate(leftEvidence, PairConsensusStatus.CONSENSUS_CONFLICT_SERVICE_KEY_SELECTED, now)),
                        left, right, "well-known service key selected " + expectedClass.get(), now);
            }
            if (expectedClass.get().equals(rightEvidence.className())) {
                return decision(bucket, PairConsensusStatus.CONSENSUS_CONFLICT_SERVICE_KEY_SELECTED,
                        Optional.of(candidate(rightEvidence, PairConsensusStatus.CONSENSUS_CONFLICT_SERVICE_KEY_SELECTED, now)),
                        left, right, "well-known service key selected " + expectedClass.get(), now);
            }
        }
        final int priority = comparePriority(leftEvidence, rightEvidence);
        if (priority != 0) {
            final DirectionalPolicyEvidence selected = priority < 0 ? leftEvidence : rightEvidence;
            return decision(bucket, PairConsensusStatus.CONSENSUS_CONFLICT_PRIORITY_SELECTED,
                    Optional.of(candidate(selected, PairConsensusStatus.CONSENSUS_CONFLICT_PRIORITY_SELECTED, now)),
                    left, right, "setup/hold priority selected " + selected.className(), now);
        }
        if (currentActivePolicy.isPresent() && (currentActivePolicy.orElseThrow().policyHash().equals(leftEvidence.policyHash())
                || currentActivePolicy.orElseThrow().policyHash().equals(rightEvidence.policyHash()))) {
            return decision(bucket, PairConsensusStatus.CONSENSUS_CONFLICT_CURRENT_POLICY_PRESERVED, Optional.empty(),
                    left, right, "equal priority conflict retains current active policy", now);
        }
        if (equalPriorityAction == PairConsensusEqualPriorityAction.CLASS_ORDER) {
            final DirectionalPolicyEvidence selected = leftEvidence.className().compareTo(rightEvidence.className()) <= 0
                    ? leftEvidence : rightEvidence;
            return decision(bucket, PairConsensusStatus.CONSENSUS_CONFLICT_PRIORITY_SELECTED,
                    Optional.of(candidate(selected, PairConsensusStatus.CONSENSUS_CONFLICT_PRIORITY_SELECTED, now)),
                    left, right, "test-only class-order tie break", now);
        }
        return decision(bucket, PairConsensusStatus.CONSENSUS_CONFLICT_EQUAL_PRIORITY_UNRESOLVED, Optional.empty(),
                left, right, "different policies have equal setup and hold priorities", now);
    }

    private static int comparePriority(final DirectionalPolicyEvidence left, final DirectionalPolicyEvidence right) {
        final int setup = Integer.compare(left.setupPriority(), right.setupPriority());
        return setup != 0 ? setup : Integer.compare(left.holdPriority(), right.holdPriority());
    }

    private static PairPolicyCandidate candidate(final DirectionalPolicyEvidence evidence,
            final PairConsensusStatus status, final Instant now) {
        return new PairPolicyCandidate(evidence.pairKey(), evidence.serviceKey(), evidence.directionKey(),
                evidence.ingressSwitchName(), evidence.className(), evidence.profileName(), evidence.dscp(), evidence.mplsTc(),
                evidence.requestedBandwidthKbps(), evidence.requestedBandwidthBase64(), evidence.setupPriority(),
                evidence.holdPriority(), evidence.policySchemaVersion(), evidence.policyHash(), status, now);
    }

    private static PairConsensusDecision decision(final PairConsensusBucket bucket, final PairConsensusStatus status,
            final Optional<PairPolicyCandidate> candidate, final Optional<DirectionalPolicyEvidence> left,
            final Optional<DirectionalPolicyEvidence> right, final String reason, final Instant now) {
        return new PairConsensusDecision(bucket.pairKey(), bucket.serviceKey(), status, candidate, left, right, reason, now);
    }
}
