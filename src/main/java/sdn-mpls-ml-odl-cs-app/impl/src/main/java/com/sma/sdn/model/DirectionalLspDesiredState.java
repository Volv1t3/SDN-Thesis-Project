/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

import java.util.List;

/**
 * <p>Describes the desired route, bandwidth, priorities, and identifiers for one directional LSP. The directional application service constructs it and uses its fingerprint and hash to describe the intended state.</p>
 *
 * @param pairKey managed tunnel-pair identifier
 * @param directionKey managed tunnel-direction identifier
 * @param pccNode PCEP client node identifier
 * @param lspName symbolic LSP name
 * @param tunnelInterfaceName associated tunnel interface name
 * @param plspId PCEP LSP identifier
 * @param tunnelId reported tunnel identifier
 * @param requestedBandwidthKbps requested bandwidth in kilobits per second
 * @param requestedBandwidthBase64 PCEP bandwidth encoding
 * @param setupPriority LSP setup priority
 * @param holdPriority LSP holding priority
 * @param desiredEro desired explicit route
 * @param desiredEroFingerprint fingerprint of the desired explicit route
 * @param desiredLspStateHash fingerprint of the desired LSP state
 */
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
    /**
     * <p>Replace a null desired ERO with an empty list; otherwise retain an immutable copy.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Replace a null desired ERO with an empty list; otherwise retain an immutable copy.</li>
     * </ol>
     *
     * @param pairKey managed tunnel-pair identifier
     * @param directionKey managed tunnel-direction identifier
     * @param pccNode PCEP client node identifier
     * @param lspName symbolic LSP name
     * @param tunnelInterfaceName associated tunnel interface name
     * @param plspId PCEP LSP identifier
     * @param tunnelId reported tunnel identifier
     * @param requestedBandwidthKbps requested bandwidth in kilobits per second
     * @param requestedBandwidthBase64 PCEP bandwidth encoding
     * @param setupPriority LSP setup priority
     * @param holdPriority LSP holding priority
     * @param desiredEro desired explicit route
     * @param desiredEroFingerprint fingerprint of the desired explicit route
     * @param desiredLspStateHash fingerprint of the desired LSP state
     * @throws NullPointerException if the supplied desired ERO contains a null element
     */
    public DirectionalLspDesiredState {
        desiredEro = desiredEro == null ? List.of() : List.copyOf(desiredEro);
    }
}
