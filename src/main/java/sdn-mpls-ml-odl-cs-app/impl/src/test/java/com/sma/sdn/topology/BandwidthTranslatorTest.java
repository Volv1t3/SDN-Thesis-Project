/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Define la clase {@code BandwidthTranslatorTest} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
class BandwidthTranslatorTest {
    /**
     * Ejecuta la operacion {@code convertsKbpsToBytesPerSecond} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     */
    @Test
    void convertsKbpsToBytesPerSecond() {
        assertEquals(1_250_000L, BandwidthTranslator.kbpsToBytesPerSecond(10_000));
    }

    /**
     * Comprueba el ejemplo normativo de codificacion PCEP para 80 kbps.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Convierte 80 kbps a 10000 bytes por segundo.</li>
     *   <li>Codifica el valor como float32 en orden de red.</li>
     *   <li>Compara el resultado Base64 requerido por ODL.</li>
     * </ol>
     */
    @Test
    void encodesPcepBandwidthAsBase64Float32() {
        assertEquals("RhxAAA==", BandwidthTranslator.kbpsToPcepBandwidthBase64Float32(80));
    }
}
