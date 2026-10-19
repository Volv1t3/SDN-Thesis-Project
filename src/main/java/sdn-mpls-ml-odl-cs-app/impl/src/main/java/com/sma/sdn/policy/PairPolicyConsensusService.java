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

/**
 * <p>Evaluates directional classification evidence and selects a pair policy
 * candidate when agreement or a configured conflict rule permits it.</p>
 * <p>{@link PairPolicyCoordinator} supplies the evidence bucket and active state;
 * {@link ServiceKeyResolver} provides service-class preferences during conflicts.</p>
 */
public final class PairPolicyConsensusService {
    /** <p>Whether both directional observations are required.</p> */
    private final boolean requireBothDirections;
    /** <p>Whether a single observation can produce a provisional candidate.</p> */
    private final boolean provisionalSingleSideEnabled;
    /** <p>Tie-breaking action for conflicting policies of equal priority.</p> */
    private final PairConsensusEqualPriorityAction equalPriorityAction;
    /** <p>Resolver of expected classes for well-known service ports.</p> */
    private final ServiceKeyResolver serviceKeyResolver;

    /**
     * <p>Creates a consensus evaluator with the supplied selection rules.</p>
     * <p>Construction proceeds as follows:</p>
     * <ol><li>Retain the direction, provisional, tie-breaking and service resolver settings.</li></ol>
     * @param requireBothDirections whether missing opposite evidence blocks selection
     * @param provisionalSingleSideEnabled whether single-side selection is allowed when both are not required
     * @param equalPriorityAction configured action for unresolved priority ties
     * @param serviceKeyResolver lookup of expected classes for service keys
     */
    public PairPolicyConsensusService(final boolean requireBothDirections, final boolean provisionalSingleSideEnabled,
            final PairConsensusEqualPriorityAction equalPriorityAction, final ServiceKeyResolver serviceKeyResolver) {
        this.requireBothDirections = requireBothDirections;
        this.provisionalSingleSideEnabled = provisionalSingleSideEnabled;
        this.equalPriorityAction = equalPriorityAction;
        this.serviceKeyResolver = serviceKeyResolver;
    }

    /**
     * <p>Selects a candidate or returns a non-actionable decision for the current evidence.</p>
     * <p>The evaluation proceeds as follows:</p>
     * <ol>
     *   <li>With a missing side, select a provisional observation only when configured;
     *       otherwise report pending evidence.</li>
     *   <li>When hashes match, select the left observation; on conflict, prefer an
     *       observation matching the expected service class, checking left first.</li>
     *   <li>Otherwise compare setup and hold priority, preferring lower numbers.</li>
     *   <li>On a tie preserve a matching active policy, use configured class ordering,
     *       or return an unresolved conflict without a candidate.</li>
     * </ol>
     * <p>The coordinator decides whether and where to apply a selected candidate.</p>
     * @param bucket pair, service key, and optional directional evidence
     * @param currentActivePolicy active policy, if any, used to resolve ties
     * @param now decision and candidate timestamp
     * @return consensus status, candidate when selected, evidence and reason
     * @throws java.util.NoSuchElementException if provisional selection is enabled but both sides are empty
     */
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

    /**
     * <p>Orders evidence by setup priority and then hold priority.</p>
     * <p>The comparison proceeds as follows:</p>
     * <ol><li>Compare setup values; compare hold values only when setup values match.</li></ol>
     * @param left first observation
     * @param right second observation
     * @return negative, zero, or positive according to the priority ordering
     */
    private static int comparePriority(final DirectionalPolicyEvidence left, final DirectionalPolicyEvidence right) {
        final int setup = Integer.compare(left.setupPriority(), right.setupPriority());
        return setup != 0 ? setup : Integer.compare(left.holdPriority(), right.holdPriority());
    }

    /**
     * <p>Copies selected directional evidence into a pair policy candidate.</p>
     * <p>Construction proceeds as follows:</p>
     * <ol><li>Copy the evidence identity and policy fields and attach the consensus status and time.</li></ol>
     * @param evidence selected observation
     * @param status selection status
     * @param now candidate timestamp
     * @return candidate representing the selected evidence
     */
    private static PairPolicyCandidate candidate(final DirectionalPolicyEvidence evidence,
            final PairConsensusStatus status, final Instant now) {
        return new PairPolicyCandidate(evidence.pairKey(), evidence.serviceKey(), evidence.directionKey(),
                evidence.ingressSwitchName(), evidence.className(), evidence.profileName(), evidence.dscp(), evidence.mplsTc(),
                evidence.requestedBandwidthKbps(), evidence.requestedBandwidthBase64(), evidence.setupPriority(),
                evidence.holdPriority(), evidence.policySchemaVersion(), evidence.policyHash(), status, now);
    }

    /**
     * <p>Packages a consensus result together with the evaluated evidence.</p>
     * <p>Construction proceeds as follows:</p>
     * <ol><li>Copy pair and service identity from the bucket and attach the supplied outcome.</li></ol>
     * @param bucket evaluated pair and service
     * @param status outcome of consensus
     * @param candidate selected policy, if any
     * @param left left-direction evidence, if present
     * @param right right-direction evidence, if present
     * @param reason explanation of the outcome
     * @param now decision timestamp
     * @return consensus decision for the coordinator
     */
    private static PairConsensusDecision decision(final PairConsensusBucket bucket, final PairConsensusStatus status,
            final Optional<PairPolicyCandidate> candidate, final Optional<DirectionalPolicyEvidence> left,
            final Optional<DirectionalPolicyEvidence> right, final String reason, final Instant now) {
        return new PairConsensusDecision(bucket.pairKey(), bucket.serviceKey(), status, candidate, left, right, reason, now);
    }
}
