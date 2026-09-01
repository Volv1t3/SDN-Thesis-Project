/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/** Typed result of comparing an incoming candidate to the active pair policy. */
public record PolicyPreemptionDecision(PolicyPreemptionDecisionType type, String reason) {
    public boolean appliesIncoming() {
        return type == PolicyPreemptionDecisionType.ACTIVE_EXPIRED_REPLACE
                || type == PolicyPreemptionDecisionType.INCOMING_PRIORITY_PREEMPTS;
    }
}
