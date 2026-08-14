/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sma.sdn.model.DelegatedLspRecord;
import com.sma.sdn.model.EroSubobject;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifica los indices y las condiciones de validez del registro de LSP delegados.
 */
class DelegatedLspRegistryTest {
    /**
     * Comprueba que un registro valido puede resolverse por direccion y nombre.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Inserta una instantanea delegada y operativa.</li>
     *   <li>Consulta los dos indices publicos.</li>
     *   <li>Exige el mismo PLSP ID por ambas rutas.</li>
     * </ol>
     */
    @Test
    void indexesUsableDelegatedLsp() {
        final DelegatedLspRegistry registry = new DelegatedLspRegistry();
        registry.replaceAll(List.of(record(true, true, "up", 111L)));

        assertEquals(111L, registry.requireByDirectionKey("lsr1_to_lsr4").plspId());
        assertEquals(111L, registry.findByLspName("sma-lsr1-lsr4-delegated").orElseThrow().plspId());
    }

    /**
     * Comprueba que una entrada no delegada nunca se considera actualizable.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Inserta una entrada operativa sin la bandera de delegacion.</li>
     *   <li>Solicita el registro obligatorio por direccion.</li>
     *   <li>Exige un fallo de consistencia.</li>
     * </ol>
     */
    @Test
    void rejectsNonDelegatedLsp() {
        final DelegatedLspRegistry registry = new DelegatedLspRegistry();
        registry.replaceAll(List.of(record(false, true, "up", 111L)));
        assertThrows(IllegalStateException.class, () -> registry.requireByDirectionKey("lsr1_to_lsr4"));
    }

    private static DelegatedLspRecord record(
            final boolean delegated,
            final boolean administrative,
            final String operational,
            final long plspId) {
        final Instant now = Instant.now();
        return new DelegatedLspRecord(
                "lsr1_to_lsr4", "pcc://10.100.10.1", "sma-lsr1-lsr4-delegated", "tunnel-te110",
                "11.11.11.11", "14.14.14.14", plspId, 110L, 7L, delegated, administrative,
                operational, List.of(new EroSubobject(false, "14.14.14.14/32")), "RhxAAA==", now, now);
    }
}
