/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.serialization.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sma.sdn.model.ClassificationResult;
import com.sma.sdn.model.PathConstraints;
import com.sma.sdn.model.TrafficPolicy;
import com.sma.sdn.observability.StructuredLogger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Define la clase {@code ClassificationResponseJsonDeserializer} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class ClassificationResponseJsonDeserializer {
    private static final StructuredLogger LOG =
            StructuredLogger.getLogger(ClassificationResponseJsonDeserializer.class);
    private final ObjectMapper mapper;
    private final Duration ttl;

    /**
     * Ejecuta la operacion {@code ClassificationResponseJsonDeserializer} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param mapper valor requerido para ejecutar esta operacion
     *
     * @param ttl valor requerido para ejecutar esta operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    public ClassificationResponseJsonDeserializer(final ObjectMapper mapper, final Duration ttl) {
        this.mapper = mapper;
        this.ttl = ttl;
    }

    /**
     * Convierte una respuesta serializada en el modelo de dominio correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param json valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    public ClassificationResult deserialize(final String json) {
        try {
            final JsonNode root = mapper.readTree(json);
            final JsonNode prediction = required(root, "prediction");
            final JsonNode policy = required(root, "policy");
            final JsonNode constraints = required(policy, "path_constraints");
            final Instant now = Instant.now();
            final ClassificationResult result = new ClassificationResult(
                    text(root, "request_id", ""),
                    text(root, "model_name", ""),
                    integer(prediction, "class_id", -1),
                    requiredText(prediction, "class_name"),
                    requiredDouble(prediction, "confidence"),
                    probabilities(root.path("probabilities")),
                    new TrafficPolicy(
                            text(policy, "profile_name", ""),
                            integer(policy, "dscp", 0),
                            integer(policy, "mpls_tc", 0),
                            new PathConstraints(
                                    requiredLong(constraints, "requested_bandwidth_kbps"),
                                    integer(constraints, "setup_priority", 7),
                                    integer(constraints, "hold_priority", 7)),
                            bool(policy, "policy_fallback", false),
                            text(policy, "policy_fallback_reason", "")),
                    doubleValue(root, "processing_time_ms", 0.0d),
                    now,
                    now.plus(ttl));
            LOG.debug("classification_response_deserialized", "deserialize",
                    "Se deserializo y valido la respuesta del clasificador",
                    StructuredLogger.fields("serialized_bytes", json.getBytes(StandardCharsets.UTF_8).length,
                            "request_id", result.requestId(), "model_name", result.modelName(),
                            "class_name", result.className(), "confidence", result.confidence(),
                            "probability_count", result.probabilities().size(), "expires_at", result.expiresAt()));
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("No fue posible deserializar la respuesta del clasificador", e);
        }
    }

    /**
     * Ejecuta la operacion {@code required} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param node valor requerido para ejecutar esta operacion
     *
     * @param fieldName valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    private static JsonNode required(final JsonNode node, final String fieldName) {
        final JsonNode child = node.get(fieldName);
        if (child == null || child.isNull()) {
            throw new IllegalArgumentException(
                    "La respuesta del clasificador no contiene el campo obligatorio: " + fieldName);
        }
        return child;
    }

    /**
     * Ejecuta la operacion {@code requiredText} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param node valor requerido para ejecutar esta operacion
     *
     * @param fieldName valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    private static String requiredText(final JsonNode node, final String fieldName) {
        final JsonNode child = required(node, fieldName);
        if (!child.isTextual() || child.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "La respuesta del clasificador contiene un campo de texto invalido: " + fieldName);
        }
        return child.asText();
    }

    /**
     * Ejecuta la operacion {@code requiredDouble} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param node valor requerido para ejecutar esta operacion
     *
     * @param fieldName valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    private static double requiredDouble(final JsonNode node, final String fieldName) {
        final JsonNode child = required(node, fieldName);
        if (!child.isNumber()) {
            throw new IllegalArgumentException(
                    "La respuesta del clasificador contiene un campo numerico invalido: " + fieldName);
        }
        return child.asDouble();
    }

    /**
     * Ejecuta la operacion {@code requiredLong} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param node valor requerido para ejecutar esta operacion
     *
     * @param fieldName valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    private static long requiredLong(final JsonNode node, final String fieldName) {
        final JsonNode child = required(node, fieldName);
        if (!child.isNumber()) {
            throw new IllegalArgumentException(
                    "La respuesta del clasificador contiene un campo numerico invalido: " + fieldName);
        }
        return child.asLong();
    }

    /**
     * Ejecuta la operacion {@code text} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param node valor requerido para ejecutar esta operacion
     *
     * @param fieldName valor requerido para ejecutar esta operacion
     *
     * @param defaultValue valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private static String text(final JsonNode node, final String fieldName, final String defaultValue) {
        final JsonNode child = node.get(fieldName);
        return child == null || child.isNull() ? defaultValue : child.asText(defaultValue);
    }

    /**
     * Ejecuta la operacion {@code integer} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param node valor requerido para ejecutar esta operacion
     *
     * @param fieldName valor requerido para ejecutar esta operacion
     *
     * @param defaultValue valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private static int integer(final JsonNode node, final String fieldName, final int defaultValue) {
        final JsonNode child = node.get(fieldName);
        return child == null || child.isNull() ? defaultValue : child.asInt(defaultValue);
    }

    /**
     * Ejecuta la operacion {@code doubleValue} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param node valor requerido para ejecutar esta operacion
     *
     * @param fieldName valor requerido para ejecutar esta operacion
     *
     * @param defaultValue valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private static double doubleValue(final JsonNode node, final String fieldName, final double defaultValue) {
        final JsonNode child = node.get(fieldName);
        return child == null || child.isNull() ? defaultValue : child.asDouble(defaultValue);
    }

    /**
     * Ejecuta la operacion {@code bool} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param node valor requerido para ejecutar esta operacion
     *
     * @param fieldName valor requerido para ejecutar esta operacion
     *
     * @param defaultValue valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private static boolean bool(final JsonNode node, final String fieldName, final boolean defaultValue) {
        final JsonNode child = node.get(fieldName);
        return child == null || child.isNull() ? defaultValue : child.asBoolean(defaultValue);
    }

    /**
     * Ejecuta la operacion {@code probabilities} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param node valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private static Map<String, Double> probabilities(final JsonNode node) {
        final Map<String, Double> values = new HashMap<>();
        if (node == null || !node.isObject()) {
            return values;
        }
        final Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> entry = fields.next();
            if (entry.getValue().isNumber()) {
                values.put(entry.getKey(), entry.getValue().asDouble());
            }
        }
        return Map.copyOf(values);
    }
}
