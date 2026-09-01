/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/** Pair-policy ownership decision made after a consensus candidate exists. */
public enum PolicyPreemptionDecisionType {
    SAME_POLICY_REFRESH,
    ACTIVE_EXPIRED_REPLACE,
    INCOMING_PRIORITY_PREEMPTS,
    ACTIVE_POLICY_RETAINED_WEAKER_INCOMING,
    ACTIVE_POLICY_RETAINED_EQUAL_PRIORITY_DIFFERENT_POLICY
}
