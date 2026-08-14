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
 * Conserva la identidad configurada y el ultimo estado operativo conocido de un LSP RSVP-TE delegado.
 */
public record DelegatedLspRecord(
        String directionKey,
        String pccNode,
        String lspName,
        String tunnelInterfaceName,
        String sourceRouterId,
        String destinationRouterId,
        long plspId,
        long tunnelId,
        long lspId,
        boolean delegated,
        boolean administrativeUp,
        String operationalState,
        List<EroSubobject> activeEro,
        String reportedBandwidthBase64,
        Instant discoveredAt,
        Instant updatedAt) {

    /**
     * Protege la ERO almacenada contra modificaciones externas posteriores.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Reemplaza una ERO nula por una lista vacia.</li>
     *   <li>Almacena una copia inmutable del orden de subobjetos.</li>
     * </ol>
     */
    public DelegatedLspRecord {
        activeEro = activeEro == null ? List.of() : List.copyOf(activeEro);
    }

    /**
     * Determina si el estado reportado permite enviar una actualizacion PCEP segura.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Comprueba que existe un PLSP ID positivo.</li>
     *   <li>Verifica las banderas de delegacion y administracion.</li>
     *   <li>Exige que el estado operativo sea {@code up}.</li>
     * </ol>
     *
     * @return {@code true} cuando el LSP puede ser actualizado
     */
    public boolean isValidForUpdate() {
        return plspId > 0L
                && delegated
                && administrativeUp
                && "up".equalsIgnoreCase(operationalState);
    }
}
