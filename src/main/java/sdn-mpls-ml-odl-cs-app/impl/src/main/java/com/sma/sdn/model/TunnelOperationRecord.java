/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

import java.time.Instant;
import java.util.List;

/**
 * <p>Records a directional tunnel operation, its requested route, lifecycle status, timing, and confirmation information. Tunnel operation coordination and its registry use it for tracking and deduplication.</p>
 *
 * @param operationId operation identifier
 * @param workflowId workflow correlation identifier
 * @param packetSequence packet notification sequence
 * @param directionKey managed tunnel-direction identifier
 * @param pccNode PCEP client node identifier
 * @param lspName symbolic LSP name
 * @param profileName traffic-policy profile name
 * @param className classification class name
 * @param bandwidthBase64 PCEP bandwidth encoding
 * @param requestedEro requested explicit route
 * @param status operation or response status
 * @param updateLspHttpStatus HTTP status of the update-lsp operation, when available
 * @param pcepEroConfirmed whether the requested ERO was confirmed
 * @param pcepBandwidthConfirmed whether requested bandwidth was confirmed
 * @param failureReason diagnostic failure reason, when available
 * @param startedAt operation start time
 * @param acceptedAt operation acceptance time
 * @param completedAt operation completion time
 * @param expiresAt expiration time
 */
public record TunnelOperationRecord(
        String operationId,
        String workflowId,
        long packetSequence,
        String directionKey,
        String pccNode,
        String lspName,
        String profileName,
        String className,
        String bandwidthBase64,
        List<EroSubobject> requestedEro,
        TunnelOperationStatus status,
        Integer updateLspHttpStatus,
        boolean pcepEroConfirmed,
        boolean pcepBandwidthConfirmed,
        String failureReason,
        Instant startedAt,
        Instant acceptedAt,
        Instant completedAt,
        Instant expiresAt) {
    /**
     * <p>Retain an immutable copy of the requested ERO; reject a null list or null elements.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Retain an immutable copy of the requested ERO; reject a null list or null elements.</li>
     * </ol>
     *
     *
     * @param operationId operation identifier
     * @param workflowId workflow correlation identifier
     * @param packetSequence packet notification sequence
     * @param directionKey managed tunnel-direction identifier
     * @param pccNode PCEP client node identifier
     * @param lspName symbolic LSP name
     * @param profileName traffic-policy profile name
     * @param className classification class name
     * @param bandwidthBase64 PCEP bandwidth encoding
     * @param requestedEro requested explicit route
     * @param status operation or response status
     * @param updateLspHttpStatus HTTP status of the update-lsp operation, when available
     * @param pcepEroConfirmed whether the requested ERO was confirmed
     * @param pcepBandwidthConfirmed whether requested bandwidth was confirmed
     * @param failureReason diagnostic failure reason, when available
     * @param startedAt operation start time
     * @param acceptedAt operation acceptance time
     * @param completedAt operation completion time
     * @param expiresAt expiration time
     * @throws NullPointerException if a required value or copied list element is null
     */
    public TunnelOperationRecord {
        requestedEro = List.copyOf(requestedEro);
    }
}
