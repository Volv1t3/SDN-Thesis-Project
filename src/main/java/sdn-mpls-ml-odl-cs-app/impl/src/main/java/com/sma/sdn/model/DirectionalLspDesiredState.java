/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

import java.util.List;

/** Exact desired state for one directional delegated LSP. */
public record DirectionalLspDesiredState(
        String pairKey,
        String directionKey,
        String pccNode,
        String lspName,
        String tunnelInterfaceName,
        long plspId,
        long tunnelId,
        int requestedBandwidthKbps,
        String requestedBandwidthBase64,
        int setupPriority,
        int holdPriority,
        List<EroSubobject> desiredEro,
        String desiredEroFingerprint,
        String desiredLspStateHash) {
    public DirectionalLspDesiredState {
        desiredEro = desiredEro == null ? List.of() : List.copyOf(desiredEro);
    }
}
