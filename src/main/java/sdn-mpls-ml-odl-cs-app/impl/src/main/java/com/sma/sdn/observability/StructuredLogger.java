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
 * Emite eventos JSON estructurados, compactos y ASCII mediante la infraestructura SLF4J administrada por Karaf.
 */
public final class StructuredLogger {
    private static final String SERVICE_NAME = "sdn-mpls-ml-controller";
    private static final ObjectMapper MAPPER = createMapper();

    private final Logger logger;
    private final String component;

    /**
     * Crea una fachada asociada con una clase concreta de la aplicacion.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Obtiene el logger SLF4J administrado por Pax Logging.</li>
     *   <li>Conserva el nombre simple de la clase como componente estructurado.</li>
     * </ol>
     *
     * @param type clase propietaria de los eventos
     * @throws NullPointerException si la clase es nula
     */
    private StructuredLogger(final Class<?> type) {
        final Class<?> requiredType = Objects.requireNonNull(type, "type");
        logger = LoggerFactory.getLogger(requiredType);
        component = requiredType.getSimpleName();
    }

    /**
     * Construye una fachada de logging estructurado para la clase indicada.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida la clase solicitante.</li>
     *   <li>Crea la fachada con identidad estable del componente.</li>
     * </ol>
     *
     * @param type clase que emitira los eventos
     * @return logger estructurado asociado con la clase
     */
    public static StructuredLogger getLogger(final Class<?> type) {
        return new StructuredLogger(type);
    }

    /**
     * Emite un evento de traza reservado para operaciones primitivas de alta frecuencia.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Comprueba si TRACE esta habilitado.</li>
     *   <li>Construye el evento JSON con contexto y metadata.</li>
     *   <li>Lo entrega a SLF4J como una unica linea.</li>
     * </ol>
     *
     * @param event identificador estable del evento
     * @param operation funcion u operacion que genera el evento
     * @param message descripcion formal en espanol
     * @param metadata campos seguros adicionales
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
     * Emite un evento de depuracion con pasos, estructuras o resultados intermedios.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Comprueba si DEBUG esta habilitado.</li>
     *   <li>Construye el evento JSON con contexto y metadata.</li>
     *   <li>Lo entrega a SLF4J como una unica linea.</li>
     * </ol>
     *
     * @param event identificador estable del evento
     * @param operation funcion u operacion que genera el evento
     * @param message descripcion formal en espanol
     * @param metadata campos seguros adicionales
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
     * Emite un evento informativo para cambios de estado o resultados operativos relevantes.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Comprueba si INFO esta habilitado.</li>
     *   <li>Construye el evento JSON con contexto y metadata.</li>
     *   <li>Lo entrega a SLF4J como una unica linea.</li>
     * </ol>
     *
     * @param event identificador estable del evento
     * @param operation funcion u operacion que genera el evento
     * @param message descripcion formal en espanol
     * @param metadata campos seguros adicionales
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
     * Emite una advertencia recuperable con metadata y una excepcion opcional.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Comprueba si WARN esta habilitado.</li>
     *   <li>Serializa el contexto, la metadata y la excepcion dentro del JSON.</li>
     *   <li>Entrega una sola linea al sistema de logs.</li>
     * </ol>
     *
     * @param event identificador estable del evento
     * @param operation funcion u operacion que genera el evento
     * @param message descripcion formal en espanol
     * @param metadata campos seguros adicionales
     * @param failure excepcion asociada o {@code null}
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
     * Emite un fallo definitivo con metadata y una excepcion opcional.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Comprueba si ERROR esta habilitado.</li>
     *   <li>Serializa el contexto, la metadata y la excepcion dentro del JSON.</li>
     *   <li>Entrega una sola linea al sistema de logs.</li>
     * </ol>
     *
     * @param event identificador estable del evento
     * @param operation funcion u operacion que genera el evento
     * @param message descripcion formal en espanol
     * @param metadata campos seguros adicionales
     * @param failure excepcion asociada o {@code null}
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
     * Crea un mapa ordenado a partir de pares clave y valor para simplificar los eventos de los componentes.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Exige una cantidad par de argumentos.</li>
     *   <li>Convierte cada clave a texto y conserva su valor asociado.</li>
     *   <li>Devuelve una copia inmutable y ordenada.</li>
     * </ol>
     *
     * @param entries secuencia alternada de claves y valores
     * @return mapa inmutable de metadata
     * @throws IllegalArgumentException si la cantidad de argumentos es impar
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
     * Construye la linea JSON completa de un evento sin emitirla al backend de logging.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Agrega identidad temporal, nivel, componente, operacion y mensaje.</li>
     *   <li>Incorpora el contexto MDC, la metadata segura y la excepcion cuando existen.</li>
     *   <li>Serializa el mapa como JSON ASCII o devuelve un evento minimo de contingencia.</li>
     * </ol>
     *
     * @param level nivel textual del evento
     * @param event identificador estable del evento
     * @param operation funcion que origina el evento
     * @param message descripcion formal del evento
     * @param metadata campos estructurados seguros
     * @param failure excepcion asociada o {@code null}
     * @return evento JSON compacto de una sola linea
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
     * Convierte una excepcion y su traza en una estructura serializable dentro del evento JSON.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Captura la traza en memoria sin escribir directamente en la consola.</li>
     *   <li>Extrae el tipo y el mensaje de la excepcion.</li>
     *   <li>Devuelve los tres campos en un mapa ordenado.</li>
     * </ol>
     *
     * @param failure excepcion que debe representarse
     * @return estructura ordenada con tipo, mensaje y traza
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
     * Crea el serializador JSON compartido con soporte de tipos JDK8, fechas Java y escape obligatorio de Unicode.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Registra los modulos de tipos opcionales y temporales.</li>
     *   <li>Activa el escape de caracteres fuera de ASCII.</li>
     *   <li>Devuelve la instancia inmutable de uso compartido.</li>
     * </ol>
     *
     * @return serializador configurado para eventos estructurados
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
