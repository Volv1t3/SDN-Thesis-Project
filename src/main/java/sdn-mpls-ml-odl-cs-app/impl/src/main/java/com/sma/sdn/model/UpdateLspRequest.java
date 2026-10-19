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
 * <p>Carries the PCC, LSP identity, bandwidth, explicit route, and topology identifier for an update-lsp request. The delegated LSP service supplies it to the XML serializer.</p>
 *
 * @param pccNode PCEP client node identifier
 * @param lspName symbolic LSP name
 * @param plspId PCEP LSP identifier
 * @param bandwidthBase64 PCEP bandwidth encoding
 * @param eroSubobjects ero subobjects
 * @param pcepTopologyId PCEP topology identifier
 */
public record UpdateLspRequest(
        String pccNode, /*String pcc:// representation of a pcep node*/
        String lspName, /*LSP to be updated based on its signaled name*/
        long plspId, /*PLSPID which is used in the pcep:update-lsp subsystem from ODL as opposed to the lsp id from the router*/
        String bandwidthBase64, /*encoded representation of the corresponding assigned bandwidth for the path*/
        List<EroSubobject> eroSubobjects, /*ero subojects already configured and created based on a path calculation previosuly translated*/
        String pcepTopologyId /*PCEP topology identifier which is always the pcep-topology defined in the project*/) {
}
