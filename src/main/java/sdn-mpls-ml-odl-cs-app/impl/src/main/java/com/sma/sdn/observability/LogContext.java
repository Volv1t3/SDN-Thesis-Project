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
 * <p>Temporarily augments the current thread's SLF4J mapped diagnostic context and restores
 * its earlier entries on close. Packet processing, topology refresh and LSP operations use
 * these scopes to correlate events emitted by {@link StructuredLogger}.</p>
 */
public final class LogContext implements AutoCloseable {
    /** <p>Context entries captured before this scope was opened.</p> */
    private final Map<String, String> previous;
    /** <p>Whether the captured context has already been restored.</p> */
    private boolean closed;

    /**
     * <p>Captures the current thread context and installs a merged correlation context.</p>
     *
     * <p>The scope is initialized as follows:</p>
     * <ol>
     *   <li>Copies the existing MDC entries, treating an absent context as empty.</li>
     *   <li>Overlays non-null keys and values, replacing entries with matching keys.</li>
     *   <li>Replaces the thread's MDC with the merged entries.</li>
     * </ol>
     * @param values correlation entries to merge into the existing context
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
     * <p>Opens a restorable scope that merges the supplied entries into the current MDC.</p>
     *
     * <p>The scope is opened as follows:</p>
     * <ol>
     *   <li>Substitutes an empty map when the argument is null.</li>
     *   <li>Creates a scope which captures and augments the current thread's context.</li>
     * </ol>
     *
     * <p>Close the returned scope on the same thread, normally using try-with-resources.</p>
     * @param values correlation entries, or null for no additions
     * @return scope that restores the previous context when closed
     */
    public static LogContext open(final Map<String, String> values) {
        return new LogContext(values == null ? Map.of() : values);
    }

    /**
     * <p>Restores the captured MDC entries once; subsequent calls do nothing.</p>
     *
     * <p>Restoration proceeds as follows:</p>
     * <ol>
     *   <li>Checks whether this scope has already been closed.</li>
     *   <li>Replaces the current MDC with the captured entries and marks the scope closed.</li>
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
     * <p>Replaces the current thread's MDC entries with the supplied map.</p>
     *
     * <p>Replacement proceeds as follows:</p>
     * <ol>
     *   <li>Clears the current MDC.</li>
     *   <li>Installs each supplied entry through the SLF4J MDC API.</li>
     * </ol>
     *
     * @param values complete set of context entries to install
     */
    private static void replace(final Map<String, String> values) {
        MDC.clear();
        values.forEach(MDC::put);
    }
}
