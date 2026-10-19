/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.observability;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * <p>Emits compact structured JSON events through the SLF4J logger associated with a caller's
 * class. Events include thread and component identity, optional MDC entries, metadata and
 * exception details; non-ASCII characters are escaped by the shared Jackson mapper.</p>
 * <p>{@link LogContext} supplies scoped correlation entries, while controller services and
 * the metrics registry use this facade to produce events through their configured backend.</p>
 */
public final class StructuredLogger {
    /** <p>Service identifier included in every event and serialization fallback.</p> */
    private static final String SERVICE_NAME = "sdn-mpls-ml-controller";
    /** <p>Shared JSON mapper configured once for supported Java types and ASCII output.</p> */
    private static final ObjectMapper MAPPER = createMapper();

    /** <p>SLF4J backend logger associated with the originating class.</p> */
    private final Logger logger;
    /** <p>Simple name of the class represented in the event payload.</p> */
    private final String component;

    /**
     * <p>Creates a structured logging facade for a particular class.</p>
     *
     * <p>Construction proceeds as follows:</p>
     * <ol>
     *   <li>Rejects a null class and obtains its SLF4J logger.</li>
     *   <li>Stores the class's simple name as the structured component.</li>
     * </ol>
     *
     * @param type class that owns the events
     * @throws NullPointerException if type is null
     */
    private StructuredLogger(final Class<?> type) {
        final Class<?> requiredType = Objects.requireNonNull(type, "type");
        logger = LoggerFactory.getLogger(requiredType);
        component = requiredType.getSimpleName();
    }

    /**
     * <p>Creates a structured logger for the supplied class.</p>
     *
     * <p>The factory proceeds as follows:</p>
     * <ol>
     *   <li>Constructs a facade, which validates the class and resolves its SLF4J logger.</li>
     * </ol>
     *
     * @param type class associated with the logger
     * @return structured facade for the class
     * @throws NullPointerException if type is null
     */
    public static StructuredLogger getLogger(final Class<?> type) {
        return new StructuredLogger(type);
    }

    /**
     * <p>Emits a structured TRACE event when that level is enabled.</p>
     *
     * <p>Emission proceeds as follows:</p>
     * <ol>
     *   <li>Checks the backend's TRACE level before constructing the event.</li>
     *   <li>Serializes the event with the current MDC and sends it to SLF4J.</li>
     * </ol>
     *
     * @param event event identifier
     * @param operation originating operation
     * @param message event description
     * @param metadata optional additional event fields
     */
    public void trace(
            final String event,
            final String operation,
            final String message,
            final Map<String, ?> metadata) {
        if (logger.isTraceEnabled()) {
            logger.trace(serialize("TRACE", event, operation, message, metadata, null));
        }
    }

    /**
     * <p>Emits a structured DEBUG event when that level is enabled.</p>
     *
     * <p>Emission proceeds as follows:</p>
     * <ol>
     *   <li>Checks the backend's DEBUG level before constructing the event.</li>
     *   <li>Serializes the event with the current MDC and sends it to SLF4J.</li>
     * </ol>
     *
     * @param event event identifier
     * @param operation originating operation
     * @param message event description
     * @param metadata optional additional event fields
     */
    public void debug(
            final String event,
            final String operation,
            final String message,
            final Map<String, ?> metadata) {
        if (logger.isDebugEnabled()) {
            logger.debug(serialize("DEBUG", event, operation, message, metadata, null));
        }
    }

    /**
     * <p>Emits a structured INFO event when that level is enabled.</p>
     *
     * <p>Emission proceeds as follows:</p>
     * <ol>
     *   <li>Checks the backend's INFO level before constructing the event.</li>
     *   <li>Serializes the event with the current MDC and sends it to SLF4J.</li>
     * </ol>
     *
     * @param event event identifier
     * @param operation originating operation
     * @param message event description
     * @param metadata optional additional event fields
     */
    public void info(
            final String event,
            final String operation,
            final String message,
            final Map<String, ?> metadata) {
        if (logger.isInfoEnabled()) {
            logger.info(serialize("INFO", event, operation, message, metadata, null));
        }
    }

    /**
     * <p>Emits a structured WARN event with optional exception details when enabled.</p>
     *
     * <p>Emission proceeds as follows:</p>
     * <ol>
     *   <li>Checks the backend's WARN level before constructing the event.</li>
     *   <li>Serializes the MDC, metadata and optional exception and sends the JSON to SLF4J.</li>
     * </ol>
     *
     * @param event event identifier
     * @param operation originating operation
     * @param message event description
     * @param metadata optional additional event fields
     * @param failure associated failure, or null
     */
    public void warn(
            final String event,
            final String operation,
            final String message,
            final Map<String, ?> metadata,
            final Throwable failure) {
        if (logger.isWarnEnabled()) {
            logger.warn(serialize("WARN", event, operation, message, metadata, failure));
        }
    }

    /**
     * <p>Emits a structured ERROR event with optional exception details when enabled.</p>
     *
     * <p>Emission proceeds as follows:</p>
     * <ol>
     *   <li>Checks the backend's ERROR level before constructing the event.</li>
     *   <li>Serializes the MDC, metadata and optional exception and sends the JSON to SLF4J.</li>
     * </ol>
     *
     * @param event event identifier
     * @param operation originating operation
     * @param message event description
     * @param metadata optional additional event fields
     * @param failure associated failure, or null
     */
    public void error(
            final String event,
            final String operation,
            final String message,
            final Map<String, ?> metadata,
            final Throwable failure) {
        if (logger.isErrorEnabled()) {
            logger.error(serialize("ERROR", event, operation, message, metadata, failure));
        }
    }

    /**
     * <p>Creates an unmodifiable, insertion-ordered metadata map from alternating keys and values.
     * Duplicate stringified keys retain their last value; null keys become the string {@code null}.</p>
     *
     * <p>Map construction proceeds as follows:</p>
     * <ol>
     *   <li>Rejects an odd number of arguments.</li>
     *   <li>Converts each key with {@link String#valueOf(Object)} and inserts its value.</li>
     *   <li>Returns an unmodifiable view of the newly constructed map.</li>
     * </ol>
     *
     * @param entries alternating keys and values
     * @return unmodifiable metadata map in insertion order
     * @throws IllegalArgumentException if the number of entries is odd
     * @throws NullPointerException if the argument array is null
     */
    public static Map<String, Object> fields(final Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("La metadata debe contener pares de clave y valor");
        }
        final Map<String, Object> fields = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            fields.put(String.valueOf(entries[index]), entries[index + 1]);
        }
        return Collections.unmodifiableMap(fields);
    }

    /**
     * <p>Builds a compact JSON event without sending it to the logging backend. If Jackson
     * serialization fails, returns a fixed minimal logging-failure event instead.</p>
     *
     * <p>Serialization proceeds as follows:</p>
     * <ol>
     *   <li>Builds an ordered payload with timestamp, level, logger, thread and event identity.</li>
     *   <li>Adds nonempty MDC and metadata maps and, when present, exception details.</li>
     *   <li>Serializes the payload with the shared mapper, or returns the fixed fallback on failure.</li>
     * </ol>
     *
     * @param level textual logging level
     * @param event event identifier
     * @param operation originating operation
     * @param message event description
     * @param metadata optional structured fields
     * @param failure associated failure, or null
     * @return compact event JSON or the fixed serialization-failure JSON
     */
    String serialize(
            final String level,
            final String event,
            final String operation,
            final String message,
            final Map<String, ?> metadata,
            final Throwable failure) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().toString());
        payload.put("level", level);
        payload.put("logger", logger.getName());
        payload.put("thread", Thread.currentThread().getName());
        payload.put("service", SERVICE_NAME);
        payload.put("event", event);
        payload.put("component", component);
        payload.put("operation", operation);
        payload.put("message", message);
        final Map<String, String> context = MDC.getCopyOfContextMap();
        if (context != null && !context.isEmpty()) {
            payload.put("context", context);
        }
        if (metadata != null && !metadata.isEmpty()) {
            payload.put("metadata", metadata);
        }
        if (failure != null) {
            payload.put("exception", exception(failure));
        }
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception serializationFailure) {
            return "{\"level\":\"ERROR\",\"service\":\"" + SERVICE_NAME
                    + "\",\"event\":\"logging_serialization_failure\","
                    + "\"message\":\"No fue posible serializar el evento estructurado\"}";
        }
    }

    /**
     * <p>Converts a failure into the structured exception field of an event.</p>
     *
     * <p>Conversion proceeds as follows:</p>
     * <ol>
     *   <li>Captures the stack trace in an in-memory writer.</li>
     *   <li>Returns its class name, message and captured trace in an ordered map.</li>
     * </ol>
     *
     * @param failure exception to represent
     * @return ordered map containing type, message and stack trace
     */
    private static Map<String, Object> exception(final Throwable failure) {
        final StringWriter stackTrace = new StringWriter();
        failure.printStackTrace(new PrintWriter(stackTrace));
        final Map<String, Object> exception = new LinkedHashMap<>();
        exception.put("type", failure.getClass().getName());
        exception.put("message", failure.getMessage());
        exception.put("stack_trace", stackTrace.toString());
        return exception;
    }

    /**
     * <p>Creates the shared Jackson mapper for structured events.</p>
     *
     * <p>Configuration proceeds as follows:</p>
     * <ol>
     *   <li>Registers JDK 8 and Java time modules.</li>
     *   <li>Enables escaping of non-ASCII characters and returns the configured mapper.</li>
     * </ol>
     *
     * @return mapper configured for structured event serialization
     */
    @SuppressWarnings("deprecation")
    private static ObjectMapper createMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());
        mapper.registerModule(new JavaTimeModule());
        mapper.getFactory().configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, true);
        return mapper;
    }
}
