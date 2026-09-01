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

/** Verifies stronger RSVP-TE priority preempts while weaker and equal policies do not. */
class PolicyPreemptionEvaluatorTest {
    private static final Instant NOW = Instant.parse("2026-08-23T03:05:49Z");
    private static final ServiceKey KEY = new ServiceKey(2048, 1, 0, "eth_type=2048|ip_proto=1");

    @Test
    void strongerIncomingPriorityPreemptsActivePolicy() {
        assertEquals(PolicyPreemptionDecisionType.INCOMING_PRIORITY_PREEMPTS,
                new PolicyPreemptionEvaluator().evaluate(active("ICMP", "icmp", 4, 4), candidate("SSH", "ssh", 3, 3), NOW).type());
    }

    @Test
    void weakerIncomingPriorityIsRetainedWithoutRefresh() {
        assertEquals(PolicyPreemptionDecisionType.ACTIVE_POLICY_RETAINED_WEAKER_INCOMING,
                new PolicyPreemptionEvaluator().evaluate(active("SSH", "ssh", 3, 3), candidate("ICMP", "icmp", 4, 4), NOW).type());
    }

    @Test
    void equalPriorityDifferentPolicyIsDeferred() {
        assertEquals(PolicyPreemptionDecisionType.ACTIVE_POLICY_RETAINED_EQUAL_PRIORITY_DIFFERENT_POLICY,
                new PolicyPreemptionEvaluator().evaluate(active("SSH", "ssh", 3, 3), candidate("STREAMING", "stream", 3, 3), NOW).type());
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
