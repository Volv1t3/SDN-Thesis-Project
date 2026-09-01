/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/** Outcome of combining two directional policy evidence records. */
public enum PairConsensusStatus {
    PENDING_ONE_SIDE,
    CONSENSUS_MATCH,
    CONSENSUS_CONFLICT_SERVICE_KEY_SELECTED,
    CONSENSUS_CONFLICT_PRIORITY_SELECTED,
    CONSENSUS_CONFLICT_CURRENT_POLICY_PRESERVED,
    CONSENSUS_CONFLICT_EQUAL_PRIORITY_UNRESOLVED,
    CONSENSUS_TIMEOUT_SINGLE_SIDE_PROVISIONAL
}
