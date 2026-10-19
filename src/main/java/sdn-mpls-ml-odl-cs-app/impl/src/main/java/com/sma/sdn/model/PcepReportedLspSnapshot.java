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

import java.util.List;

/**
 * <p>Represents one LSP as reported in PCEP topology data. The PCEP deserializer produces snapshots that the delegated LSP service associates with configured directions.</p>
 *
 * <p>This is a reported view, not yet a managed directional record. The topology deserializer
 * supplies the containing PCC identity; the delegated LSP service matches the PCC and name against
 * configuration and checks the endpoints. It constructs a {@link DelegatedLspRecord},
 * checks the resulting record's operational flags, and then replaces the registry.</p>
 *
 * @param pccNode PCEP client node identifier
 * @param name reported LSP name
 * @param plspId PCEP LSP identifier
 * @param tunnelId reported tunnel identifier
 * @param lspId reported LSP identifier
 * @param sourceRouterId source router identifier
 * @param destinationRouterId destination router identifier
 * @param delegate reported delegation flag
 * @param administrative reported administrative flag
 * @param operational reported operational state
 * @param ero reported explicit route
 * @param bandwidthBase64 PCEP bandwidth encoding
 */
public record PcepReportedLspSnapshot(
        String pccNode,
        String name,
        long plspId,
        long tunnelId,
        long lspId,
        String sourceRouterId,
        String destinationRouterId,
        boolean delegate,
        boolean administrative,
        String operational,
        List<EroSubobject> ero,
        String bandwidthBase64) {

    /**
     * <p>Replace a null ERO with an empty list; otherwise retain an immutable copy.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Replace a null ERO with an empty list; otherwise retain an immutable copy.</li>
     * </ol>
     *
     * @param pccNode PCEP client node identifier
     * @param name reported LSP name
     * @param plspId PCEP LSP identifier
     * @param tunnelId reported tunnel identifier
     * @param lspId reported LSP identifier
     * @param sourceRouterId source router identifier
     * @param destinationRouterId destination router identifier
     * @param delegate reported delegation flag
     * @param administrative reported administrative flag
     * @param operational reported operational state
     * @param ero reported explicit route
     * @param bandwidthBase64 PCEP bandwidth encoding
     * @throws NullPointerException if the supplied ERO contains a null element
     */
    public PcepReportedLspSnapshot {
        ero = ero == null ? List.of() : List.copyOf(ero);
    }
}
