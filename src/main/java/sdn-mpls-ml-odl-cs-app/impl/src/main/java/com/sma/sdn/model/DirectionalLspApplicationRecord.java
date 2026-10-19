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

/**
 * <p>Records one directional application of a selected pair policy, including its workflow identity, update outcome, and PCEP confirmation flags. The pair-policy coordinator retains these results with the active policy.</p>
 *
 * @param operationId operation identifier
 * @param workflowId workflow correlation identifier
 * @param packetSequence packet notification sequence
 * @param pairKey managed tunnel-pair identifier
 * @param directionKey managed tunnel-direction identifier
 * @param policyHash policy fingerprint
 * @param desiredLspStateHash fingerprint of the desired LSP state
 * @param lspName symbolic LSP name
 * @param pccNode PCEP client node identifier
 * @param plspId PCEP LSP identifier
 * @param status operation or response status
 * @param updateLspHttpStatus HTTP status of the update-lsp operation, when available
 * @param updateLspSent whether an update-lsp request was sent
 * @param pcepEroConfirmed whether the requested ERO was confirmed
 * @param pcepBandwidthConfirmed whether requested bandwidth was confirmed
 * @param startedAt operation start time
 * @param completedAt operation completion time
 */
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
