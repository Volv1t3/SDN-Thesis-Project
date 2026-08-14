/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.observability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifica el contrato de formato, seguridad textual y estructura de los eventos JSON de la aplicacion.
 */
class StructuredLoggerTest {
    /**
     * Comprueba que el logger genere una sola linea JSON y escape cualquier caracter fuera de ASCII.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Construye un evento con texto Unicode expresado mediante una secuencia Java ASCII.</li>
     *   <li>Serializa metadata que incluye un valor nulo permitido.</li>
     *   <li>Verifica la estructura, el escape Unicode y la ausencia de saltos de linea reales.</li>
     * </ol>
     */
    @Test
    void serializesSingleLineAsciiJson() {
        final StructuredLogger logger = StructuredLogger.getLogger(StructuredLoggerTest.class);
        final String event = logger.serialize(
                "INFO",
                "test_event",
                "serializesSingleLineAsciiJson",
                "Registro para Espa\u00f1a",
                StructuredLogger.fields("nullable", null, "count", 2),
                null);

        assertTrue(event.startsWith("{"));
        assertTrue(event.contains("\"event\":\"test_event\""));
        assertTrue(event.contains("Espa\\u00f1a"));
        assertTrue(event.contains("\"nullable\":null"));
        assertFalse(event.contains("\n"));
    }

    /**
     * Comprueba que una lista impar de argumentos sea rechazada antes de construir metadata ambigua.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Entrega una clave sin su valor correspondiente.</li>
     *   <li>Verifica que la validacion produzca {@link IllegalArgumentException}.</li>
     * </ol>
     */
    @Test
    void rejectsOddMetadataEntries() {
        assertThrows(IllegalArgumentException.class, () -> StructuredLogger.fields("key"));
    }

    /**
     * Comprueba que una excepcion quede incorporada dentro del JSON sin imprimir un bloque multilinea independiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Construye una excepcion controlada.</li>
     *   <li>Serializa el evento con su traza.</li>
     *   <li>Verifica los campos de excepcion y la permanencia del formato de una sola linea.</li>
     * </ol>
     */
    @Test
    void serializesExceptionInsideJson() {
        final StructuredLogger logger = StructuredLogger.getLogger(StructuredLoggerTest.class);
        final String event = logger.serialize(
                "ERROR", "test_failure", "serializesExceptionInsideJson",
                "Se produjo un fallo controlado", Map.of(), new IllegalStateException("fallo"));

        assertTrue(event.contains("\"exception\""));
        assertTrue(event.contains("java.lang.IllegalStateException"));
        assertTrue(event.contains("\\n"));
        assertFalse(event.contains("\n"));
    }
}
