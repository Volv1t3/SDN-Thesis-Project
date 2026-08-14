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
 * Representa el estado operativo de un {@code reported-lsp} extraido directamente de la topologia PCEP.
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
     * Normaliza las colecciones del estado reportado para mantener el modelo inmutable.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Reemplaza una ERO nula por una lista vacia.</li>
     *   <li>Crea una copia inmutable de los subobjetos recibidos.</li>
     * </ol>
     */
    public PcepReportedLspSnapshot {
        ero = ero == null ? List.of() : List.copyOf(ero);
    }
}
