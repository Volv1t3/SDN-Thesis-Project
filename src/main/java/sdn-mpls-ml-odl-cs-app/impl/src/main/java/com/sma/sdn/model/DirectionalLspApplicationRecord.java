/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

import java.time.Instant;
import java.util.UUID;

/** Auditable result of applying a pair policy to one delegated LSP direction. */
public record DirectionalLspApplicationRecord(
        UUID operationId,
        String workflowId,
        long packetSequence,
        String pairKey,
        String directionKey,
        String policyHash,
        String desiredLspStateHash,
        String lspName,
        String pccNode,
        long plspId,
        String status,
        Integer updateLspHttpStatus,
        boolean updateLspSent,
        boolean pcepEroConfirmed,
        boolean pcepBandwidthConfirmed,
        Instant startedAt,
        Instant completedAt) {
}
