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

/** Conserva el resultado auditable de una operacion de tunel. */
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
    public TunnelOperationRecord {
        requestedEro = List.copyOf(requestedEro);
    }
}
