/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sma.sdn.model.ActivePairPolicyState;
import com.sma.sdn.model.PairConsensusStatus;
import com.sma.sdn.model.PairPolicyCandidate;
import com.sma.sdn.model.PolicyPreemptionDecisionType;
import com.sma.sdn.model.ServiceKey;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifies controller-side setup-then-hold priority preemption. */
class PolicyPreemptionEvaluatorTest {
    private static final Instant NOW = Instant.parse("2026-08-23T03:05:49Z");
    private static final ServiceKey KEY = new ServiceKey(2048, 1, 0, "eth_type=2048|ip_proto=1");

    @Test
    void strongerIncomingPriorityPreemptsActivePolicy() {
        assertEquals(PolicyPreemptionDecisionType.INCOMING_PRIORITY_PREEMPTS,
                new PolicyPreemptionEvaluator().evaluate(active("SSH", "ssh", 2, 1),
                        candidate("STREAMING", "stream", 1, 0), NOW).type());
    }

    @Test
    void weakerIncomingPriorityIsRetainedWithoutRefresh() {
        assertEquals(PolicyPreemptionDecisionType.ACTIVE_POLICY_RETAINED_WEAKER_INCOMING,
                new PolicyPreemptionEvaluator().evaluate(active("HTTP", "http", 6, 5),
                        candidate("FTP", "ftp", 6, 6), NOW).type());
    }

    @Test
    void equalPriorityDifferentPolicyIsDeferred() {
        assertEquals(PolicyPreemptionDecisionType.ACTIVE_POLICY_RETAINED_EQUAL_PRIORITY_DIFFERENT_POLICY,
                new PolicyPreemptionEvaluator().evaluate(active("SSH", "ssh", 2, 1),
                        candidate("OTHER", "other", 2, 1), NOW).type());
    }

    @Test
    void lowerHoldPriorityPreemptsWhenSetupIsEqual() {
        assertEquals(PolicyPreemptionDecisionType.INCOMING_PRIORITY_PREEMPTS,
                new PolicyPreemptionEvaluator().evaluate(active("FTP", "ftp", 6, 6),
                        candidate("HTTP", "http", 6, 5), NOW).type());
    }

    @Test
    void matchingHashRefreshesWithoutApplication() {
        assertEquals(PolicyPreemptionDecisionType.SAME_POLICY_REFRESH,
                new PolicyPreemptionEvaluator().evaluate(active("SSH", "same", 2, 1),
                        candidate("SSH", "same", 2, 1), NOW).type());
    }

    @Test
    void expiredActiveCanBeReplaced() {
        final ActivePairPolicyState expired = active("SSH", "ssh", 2, 1);
        final ActivePairPolicyState atBoundary = new ActivePairPolicyState(expired.pairKey(), expired.serviceKey(),
                expired.className(), expired.profileName(), expired.dscp(), expired.mplsTc(),
                expired.requestedBandwidthKbps(), expired.requestedBandwidthBase64(), expired.setupPriority(),
                expired.holdPriority(), expired.policySchemaVersion(), expired.policyHash(), expired.generation(),
                expired.installedAt(), expired.lastRefreshedAt(), NOW, expired.lspApplications());
        assertEquals(PolicyPreemptionDecisionType.ACTIVE_EXPIRED_REPLACE,
                new PolicyPreemptionEvaluator().evaluate(atBoundary,
                        candidate("FTP", "ftp", 6, 6), NOW).type());
    }

    private static ActivePairPolicyState active(final String className, final String hash, final int setup, final int hold) {
        return new ActivePairPolicyState("lsr1_lsr4", KEY, className, className.toLowerCase() + "_policy", 0, 0,
                10_000, "SZiWgA==", setup, hold, "v1", hash, 1L, NOW, NOW, NOW.plusSeconds(60), Map.of());
    }

    private static PairPolicyCandidate candidate(final String className, final String hash, final int setup, final int hold) {
        return new PairPolicyCandidate("lsr1_lsr4", KEY, "lsr1_to_lsr4", "ECHO", className,
                className.toLowerCase() + "_policy", 0, 0, 10_000, "SZiWgA==", setup, hold, "v1", hash,
                PairConsensusStatus.CONSENSUS_MATCH, NOW);
    }
}
