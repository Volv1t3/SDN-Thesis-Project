/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.policy;

import com.sma.sdn.model.ActivePairPolicyState;
import com.sma.sdn.model.PairPolicyCandidate;
import com.sma.sdn.model.PolicyPreemptionDecision;
import com.sma.sdn.model.PolicyPreemptionDecisionType;
import java.time.Instant;

/** Applies RSVP-TE setup then hold priority after, never before, consensus. */
public final class PolicyPreemptionEvaluator {
    public PolicyPreemptionDecision evaluate(final ActivePairPolicyState active,
            final PairPolicyCandidate incoming, final Instant now) {
        if (!active.expiresAt().isAfter(now)) {
            return new PolicyPreemptionDecision(PolicyPreemptionDecisionType.ACTIVE_EXPIRED_REPLACE,
                    "active policy TTL expired");
        }
        if (active.policyHash().equals(incoming.policyHash())) {
            return new PolicyPreemptionDecision(PolicyPreemptionDecisionType.SAME_POLICY_REFRESH,
                    "incoming policy hash matches active owner");
        }
        final int setup = Integer.compare(incoming.setupPriority(), active.setupPriority());
        if (setup < 0 || (setup == 0 && incoming.holdPriority() < active.holdPriority())) {
            return new PolicyPreemptionDecision(PolicyPreemptionDecisionType.INCOMING_PRIORITY_PREEMPTS,
                    "incoming setup/hold priority is stronger");
        }
        if (setup == 0 && incoming.holdPriority() == active.holdPriority()) {
            return new PolicyPreemptionDecision(
                    PolicyPreemptionDecisionType.ACTIVE_POLICY_RETAINED_EQUAL_PRIORITY_DIFFERENT_POLICY,
                    "different policy hash has equal setup and hold priority");
        }
        return new PolicyPreemptionDecision(PolicyPreemptionDecisionType.ACTIVE_POLICY_RETAINED_WEAKER_INCOMING,
                "incoming setup/hold priority is weaker");
    }
}
