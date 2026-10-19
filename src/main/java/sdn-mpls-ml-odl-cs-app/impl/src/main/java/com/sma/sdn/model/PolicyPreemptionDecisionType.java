/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Describes how an incoming candidate compares with the active pair policy.</p>
 */
public enum PolicyPreemptionDecisionType {
    /** <p>Refresh the active policy without replacing it.</p> */
    SAME_POLICY_REFRESH,
    /** <p>Replace an expired or absent active policy.</p> */
    ACTIVE_EXPIRED_REPLACE,
    /** <p>Install an incoming policy that has stronger priority.</p> */
    INCOMING_PRIORITY_PREEMPTS,
    /** <p>Retain the stronger active policy.</p> */
    ACTIVE_POLICY_RETAINED_WEAKER_INCOMING,
    /** <p>Retain the active policy when a different incoming policy has equal priority.</p> */
    ACTIVE_POLICY_RETAINED_EQUAL_PRIORITY_DIFFERENT_POLICY
}
