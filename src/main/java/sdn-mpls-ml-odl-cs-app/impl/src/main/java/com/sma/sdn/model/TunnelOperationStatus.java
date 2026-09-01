/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/** Representa el resultado persistido de una decision sobre un LSP delegado. */
public enum TunnelOperationStatus {
    PENDING,
    ACCEPTED,
    CONFIRMED,
    SKIPPED_RECENT_INTENT,
    SKIPPED_ALREADY_MATCHING,
    FAILED,
    FAILED_HARD,
    ACCEPTED_PCEP_BANDWIDTH_UNCONFIRMED
}
