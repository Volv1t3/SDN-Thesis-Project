/*
 * Copyright (c) 2026 Santiago Arellano 00328370.
 *
 * Universidad San Francisco de Quito
 * SDN-MPLS-ML Tech Demostrator Thesis Project
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

import java.time.Instant;
import java.util.List;

/**
 * <p>Represents the latest locally known state of a managed directional PCEP LSP. The delegated LSP service populates the delegated-LSP registry and requires a valid record before an update.</p>
 *
 * @param directionKey managed tunnel-direction identifier
 * @param pccNode PCEP client node identifier
 * @param lspName symbolic LSP name
 * @param tunnelInterfaceName associated tunnel interface name
 * @param sourceRouterId source router identifier
 * @param destinationRouterId destination router identifier
 * @param plspId PCEP LSP identifier
 * @param tunnelId reported tunnel identifier
 * @param lspId reported LSP identifier
 * @param delegated whether the LSP is delegated
 * @param administrativeUp whether the LSP is administratively enabled
 * @param operationalState reported operational state
 * @param activeEro currently reported explicit route
 * @param reportedBandwidthBase64 reported bandwidth encoding
 * @param discoveredAt time the LSP was discovered
 * @param updatedAt most recent update time
 */
public record DelegatedLspRecord(
        String directionKey, /*This is the string representation of the tunnel direction which is found in the app config
        defined as either lsr1_to_lsr4 or lsr4_to_lsr1 in the implementation*/
        String pccNode, /*String representation f the pcc owner of the lsp tunnel identified through the pcc:// prefix*/
        String lspName, /*Reported lsp name that comes from the signalled name*/
        String tunnelInterfaceName, /*This is the tunnel interface reported by the router*/
        String sourceRouterId, /*router ID of the source */
        String destinationRouterId, /*router ID of the destination*/
        long plspId, /*plsp id reported by the PCC that can be used for update-lsp tasks*/
        long tunnelId, /*tunnel id reported by the PCC from the router configuration*/
        long lspId, /*lsp id reported by the router configuration*/
        boolean delegated, /*if the tunnel is or is not delegated to the PCE*/
        boolean administrativeUp,
        String operationalState,
        List<EroSubobject> activeEro, /*The current ERo desererialized from the reported LSP*/
        String reportedBandwidthBase64, /*Bandwidth represented in base 64 from ODL*/
        Instant discoveredAt,
        Instant updatedAt) {

    /**
     * <p>Replace a null active ERO with an empty list; otherwise retain an immutable copy.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Replace a null active ERO with an empty list; otherwise retain an immutable copy.</li>
     * </ol>
     *
     * @param directionKey managed tunnel-direction identifier
     * @param pccNode PCEP client node identifier
     * @param lspName symbolic LSP name
     * @param tunnelInterfaceName associated tunnel interface name
     * @param sourceRouterId source router identifier
     * @param destinationRouterId destination router identifier
     * @param plspId PCEP LSP identifier
     * @param tunnelId reported tunnel identifier
     * @param lspId reported LSP identifier
     * @param delegated whether the LSP is delegated
     * @param administrativeUp whether the LSP is administratively enabled
     * @param operationalState reported operational state
     * @param activeEro currently reported explicit route
     * @param reportedBandwidthBase64 reported bandwidth encoding
     * @param discoveredAt time the LSP was discovered
     * @param updatedAt most recent update time
     * @throws NullPointerException if the supplied active ERO contains a null element
     */
    public DelegatedLspRecord {
        activeEro = activeEro == null ? List.of() : List.copyOf(activeEro);
    }

    /**
     * <p>Determine whether the reported LSP can be updated.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Require a positive PLSP ID, delegation, administrative enablement, and an operational state equal to {@code up} ignoring case.</li>
     * </ol>
     *
     * @return true when all update prerequisites hold
     */
    public boolean isValidForUpdate() {
        return plspId > 0L
                && delegated
                && administrativeUp
                && "up".equalsIgnoreCase(operationalState);
    }
}
