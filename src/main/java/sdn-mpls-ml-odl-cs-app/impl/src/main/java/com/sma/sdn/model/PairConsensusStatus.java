/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Identifies the outcome of comparing directional classification evidence for a tunnel
 * pair and service. It distinguishes missing evidence, matching policies, conflict resolution,
 * and unresolved conflicts.</p>
 *
 * <p>{@link com.sma.sdn.policy.PairPolicyConsensusService} assigns these statuses to
 * {@link PairConsensusDecision} and selected {@link PairPolicyCandidate} instances. The
 * {@link com.sma.sdn.policy.PairPolicyCoordinator} consumes the decision and records
 * status-specific metrics; a status alone does not establish that an LSP was updated.</p>
 */
public enum PairConsensusStatus {
    /** <p>Usable evidence from the other direction is missing.</p> */
    PENDING_ONE_SIDE,
    /** <p>Both directions produced matching policy hashes.</p> */
    CONSENSUS_MATCH,
    /** <p>Setup or hold priority selected a candidate.</p> */
    CONSENSUS_CONFLICT_PRIORITY_SELECTED,
    /** <p>A matching active policy is retained during an equal-priority conflict.</p> */
    CONSENSUS_CONFLICT_CURRENT_POLICY_PRESERVED,
    /** <p>Different equal-priority policies could not be resolved.</p> */
    CONSENSUS_CONFLICT_EQUAL_PRIORITY_UNRESOLVED
}
