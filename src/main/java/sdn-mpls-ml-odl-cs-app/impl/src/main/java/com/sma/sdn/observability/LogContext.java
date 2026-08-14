/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.observability;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;

/**
 * Administra un alcance MDC restaurable para correlacionar eventos sin filtrar metadata hacia operaciones posteriores.
 */
public final class LogContext implements AutoCloseable {
    private final Map<String, String> previous;
    private boolean closed;

    /**
     * Captura el contexto vigente e instala temporalmente los valores de correlacion indicados.
     *
     * @param values campos no nulos que deben combinarse con el MDC vigente
     */
    private LogContext(final Map<String, String> values) {
        final Map<String, String> current = MDC.getCopyOfContextMap();
        previous = current == null ? Map.of() : Map.copyOf(current);
        final Map<String, String> merged = new LinkedHashMap<>(previous);
        values.forEach((key, value) -> {
            if (key != null && value != null) {
                merged.put(key, value);
            }
        });
        replace(merged);
    }

    /**
     * Abre un alcance MDC que combina el contexto actual con los valores indicados.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Captura una copia del contexto MDC vigente.</li>
     *   <li>Combina los nuevos campos sin eliminar los existentes.</li>
     *   <li>Instala el contexto combinado hasta cerrar el alcance.</li>
     * </ol>
     *
     * @param values campos de correlacion que deben permanecer dentro del alcance
     * @return contexto que debe cerrarse mediante try-with-resources
     */
    public static LogContext open(final Map<String, String> values) {
        return new LogContext(values == null ? Map.of() : values);
    }

    /**
     * Restaura exactamente el contexto MDC que existia antes de abrir este alcance.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Evita restauraciones repetidas.</li>
     *   <li>Limpia los campos instalados por el alcance.</li>
     *   <li>Repone la copia previa.</li>
     * </ol>
     */
    @Override
    public void close() {
        if (!closed) {
            replace(previous);
            closed = true;
        }
    }

    /**
     * Sustituye completamente el MDC del hilo por el mapa recibido.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Limpia todos los campos vigentes.</li>
     *   <li>Instala cada par de clave y valor del nuevo contexto.</li>
     * </ol>
     *
     * @param values contexto completo que debe quedar instalado
     */
    private static void replace(final Map<String, String> values) {
        MDC.clear();
        values.forEach(MDC::put);
    }
}
