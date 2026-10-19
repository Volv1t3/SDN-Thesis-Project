/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Carries the result and reason from comparing an incoming pair-policy candidate with the active policy. The pair-policy coordinator uses it to decide whether the incoming candidate can be installed.</p>
 *
 * @param type outcome or decision type
 * @param reason explanation of the decision
 */
public record PolicyPreemptionDecision(PolicyPreemptionDecisionType type, String reason) {
    /**
     * <p>Determine whether the preemption decision applies the incoming candidate.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Check whether the decision is expired-policy replacement or incoming-priority preemption.</li>
     * </ol>
     *
     * @return true for either incoming-application decision
     */
    public boolean appliesIncoming() {
        return type == PolicyPreemptionDecisionType.ACTIVE_EXPIRED_REPLACE
                || type == PolicyPreemptionDecisionType.INCOMING_PRIORITY_PREEMPTS;
    }
}
