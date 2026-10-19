/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Identifies stages and outcomes of a directional tunnel operation, from a pending or
 * accepted request to confirmation, skips, or failure. It also distinguishes acceptance
 * without confirmed PCEP bandwidth from a fully confirmed result.</p>
 *
 * <p>{@link com.sma.sdn.registry.TunnelOperationRegistry} records these values in
 * {@link TunnelOperationRecord} instances as it tracks intents and their outcomes. A skipped
 * status describes why a new operation was unnecessary rather than an update sent to the LSP.</p>
 */
public enum TunnelOperationStatus {
    /** <p>The operation is pending.</p> */
    PENDING,
    /** <p>The operation has been accepted.</p> */
    ACCEPTED,
    /** <p>The intended state has been confirmed.</p> */
    CONFIRMED,
    /** <p>A recent equivalent intent made another operation unnecessary.</p> */
    SKIPPED_RECENT_INTENT,
    /** <p>The observed LSP already matched the desired state.</p> */
    SKIPPED_ALREADY_MATCHING,
    /** <p>The operation failed.</p> */
    FAILED,
    /** <p>The operation encountered a hard failure.</p> */
    FAILED_HARD,
    /** <p>The update was accepted but PCEP bandwidth was not confirmed.</p> */
    ACCEPTED_PCEP_BANDWIDTH_UNCONFIRMED
}
