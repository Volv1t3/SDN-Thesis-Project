/*
 * Copyright (c) 2026 Santiago Arellano 00328370.
 *
 * Universidad San Francisco de Quito
 * SDN-MPLS-ML Tech Demostrator Thesis Project
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
 * <p>Converts a successful classifier JSON body into classification, traffic-policy, and path-constraint
 * domain records. It requires non-null {@code prediction}, {@code policy}, and nested {@code path_constraints}
 * nodes, validates selected required scalar fields, and applies defaults to optional fields. Presence checks do not
 * independently assert that these nodes are objects or enforce semantic ranges on policy values.</p>
 *
 * <p>{@link com.sma.sdn.classification.ClassificationService} calls this converter after accepting a 2xx classifier
 * response. The converter timestamps the result at deserialization time and derives its expiry from the configured
 * time-to-live so {@link com.sma.sdn.registry.ClassificationRegistrar} can later evict the cached result.</p>
 */
public final class ClassificationResponseJsonDeserializer {
    /**
     * <p>Logs validated response identity, classification values, payload size, and the assigned expiry.</p>
     */
    private static final StructuredLogger LOG =
            StructuredLogger.getLogger(ClassificationResponseJsonDeserializer.class);

    /**
     * <p>Parses response JSON into Jackson nodes.</p>
     */
    private final ObjectMapper mapper;

    /**
     * <p>Defines how far the locally assigned cache expiry is offset from deserialization time.</p>
     */
    private final Duration ttl;

    /**
     * <p>Creates a response converter with the provider's Jackson mapper and configured classification-cache lifetime.
     * Both references are retained without null checks or validation of the duration.</p>
     *
     * <p>The constructor performs these steps:</p>
     * <ol>
     *   <li>Retains the supplied {@link ObjectMapper} for response parsing.</li>
     *   <li>Retains the {@link Duration} added to each result's local cache timestamp.</li>
     * </ol>
     *
     * <p>The production provider obtains the lifetime from
     * {@link com.sma.sdn.config.AppConfig#classificationCacheTtl()}.</p>
     *
     * @param mapper Jackson mapper used to parse classifier response bodies
     * @param ttl duration added to deserialization time to assign the cache expiry
     */
    public ClassificationResponseJsonDeserializer(final ObjectMapper mapper, final Duration ttl) {
        this.mapper = mapper;
        this.ttl = ttl;
    }

    /**
     * <p>Parses and validates a classifier response, then returns a cache-ready {@link ClassificationResult}. Required
     * fields are {@code prediction.class_name}, {@code prediction.confidence}, and
     * {@code policy.path_constraints.requested_bandwidth_kbps}; absent optional values receive the defaults encoded by
     * this method. Numeric bandwidth is converted with Jackson's {@code asLong()}, not validated as an integral or
     * non-negative value; confidence and probabilities are not checked for a particular range or total.</p>
     *
     * <p>Missing or JSON-null request and model names, profile name, and fallback reason default to empty strings;
     * class ID defaults to {@code -1}, DSCP and MPLS traffic class to {@code 0}, setup and hold priority to {@code 7},
     * fallback to {@code false}, and processing time to {@code 0.0}. Present optional values are subject to Jackson's
     * coercion and its supplied default. Probability entries are retained only when their values are numeric.</p>
     *
     * <p>The conversion performs these steps:</p>
     * <ol>
     *   <li>Parses the JSON tree and requires the top-level {@code prediction} and {@code policy} nodes plus the policy's
     *       {@code path_constraints} node.</li>
     *   <li>Captures one current {@link Instant} for both {@code cachedAt} and the base of {@code expiresAt}.</li>
     *   <li>Reads optional request and model identifiers and class ID, requires the class name and numeric confidence,
     *       and filters probability members to numeric values.</li>
     *   <li>Builds {@link PathConstraints} and {@link TrafficPolicy} from the required bandwidth and optional policy
     *       values, reads processing time, and assigns expiry as the captured instant plus the configured TTL.</li>
     *   <li>Logs the validated result and returns it.</li>
     *   <li>Rethrows runtime validation failures unchanged and wraps other parsing failures in
     *       {@link IllegalArgumentException}.</li>
     * </ol>
     *
     * <p>The classification service stores the returned result in both registrar cache views and passes its policy to
     * the workflow's pair-policy coordination.</p>
     *
     * @param json UTF-8 classifier response body represented as a Java string
     * @return validated classification with policy, local cache timestamp, and derived expiry
     * @throws IllegalArgumentException if {@code json} is {@code null} or malformed, a required node is absent, or a
     *         required scalar has the wrong type or a blank required text value
     * @throws NullPointerException if the configured mapper, the configured TTL, or the parsed root is {@code null}
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
     * <p>Returns a named child node only when it is present and not JSON {@code null}.</p>
     *
     * <p>The validation performs these steps:</p>
     * <ol>
     *   <li>Reads {@code fieldName} directly from the supplied node.</li>
     *   <li>Rejects an absent or JSON-null child with {@link IllegalArgumentException}.</li>
     *   <li>Returns the existing child without coercing its node type.</li>
     * </ol>
     *
     * <p>Typed required-value helpers perform any additional scalar validation after this presence check.</p>
     *
     * @param node parent JSON node that must contain the child
     * @param fieldName name of the required child field
     * @return present, non-null child node
     * @throws IllegalArgumentException if the child is absent or JSON {@code null}
     * @throws NullPointerException if {@code node} is {@code null}
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
     * <p>Reads a required, non-blank textual field.</p>
     *
     * <p>The text validation performs these steps:</p>
     * <ol>
     *   <li>Obtains the field through {@link #required(JsonNode, String)}.</li>
     *   <li>Rejects non-textual nodes and text containing only whitespace.</li>
     *   <li>Returns the field's text unchanged.</li>
     * </ol>
     *
     * <p>The response conversion uses this helper for {@code prediction.class_name}.</p>
     *
     * @param node parent JSON node containing the required text field
     * @param fieldName name of the required text field
     * @return non-blank text value
     * @throws IllegalArgumentException if the field is absent, null, non-textual, or blank
     * @throws NullPointerException if {@code node} is {@code null}
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
     * <p>Reads a required numeric field as a {@code double}.</p>
     *
     * <p>The numeric validation performs these steps:</p>
     * <ol>
     *   <li>Obtains the field through {@link #required(JsonNode, String)}.</li>
     *   <li>Rejects a child that Jackson does not identify as numeric.</li>
     *   <li>Converts and returns the numeric node as a {@code double}.</li>
     * </ol>
     *
     * <p>The response conversion uses this helper for {@code prediction.confidence}.</p>
     *
     * @param node parent JSON node containing the required numeric field
     * @param fieldName name of the required numeric field
     * @return numeric value converted to {@code double}
     * @throws IllegalArgumentException if the field is absent, null, or non-numeric
     * @throws NullPointerException if {@code node} is {@code null}
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
     * <p>Reads a required numeric field as a {@code long}.</p>
     *
     * <p>The numeric validation performs these steps:</p>
     * <ol>
     *   <li>Obtains the field through {@link #required(JsonNode, String)}.</li>
     *   <li>Rejects a child that Jackson does not identify as numeric.</li>
     *   <li>Converts and returns the numeric node as a {@code long}.</li>
     * </ol>
     *
     * <p>The response conversion uses this helper for {@code requested_bandwidth_kbps}.</p>
     *
     * @param node parent JSON node containing the required numeric field
     * @param fieldName name of the required numeric field
     * @return numeric value converted to {@code long}
     * @throws IllegalArgumentException if the field is absent, null, or non-numeric
     * @throws NullPointerException if {@code node} is {@code null}
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
     * <p>Reads an optional field using Jackson's text coercion and a caller-supplied default.</p>
     *
     * <p>The optional read performs these steps:</p>
     * <ol>
     *   <li>Reads the named child from the parent node.</li>
     *   <li>Returns {@code defaultValue} when the child is absent or JSON {@code null}.</li>
     *   <li>Otherwise returns {@link JsonNode#asText(String)} with the same default.</li>
     * </ol>
     *
     * <p>This supplies optional identifiers, names, and fallback text in the domain result.</p>
     *
     * @param node parent JSON node containing the optional field
     * @param fieldName name of the optional field
     * @param defaultValue value returned for an absent, null, or non-coercible child
     * @return coerced text value or the supplied default
     * @throws NullPointerException if {@code node} is {@code null}
     */
    private static String text(final JsonNode node, final String fieldName, final String defaultValue) {
        final JsonNode child = node.get(fieldName);
        return child == null || child.isNull() ? defaultValue : child.asText(defaultValue);
    }

    /**
     * <p>Reads an optional field using Jackson's integer coercion and a caller-supplied default.</p>
     *
     * <p>The optional read performs these steps:</p>
     * <ol>
     *   <li>Reads the named child from the parent node.</li>
     *   <li>Returns {@code defaultValue} when the child is absent or JSON {@code null}.</li>
     *   <li>Otherwise returns {@link JsonNode#asInt(int)} with the same default.</li>
     * </ol>
     *
     * <p>This supplies optional class, marking, and path-priority integers in the domain result.</p>
     *
     * @param node parent JSON node containing the optional field
     * @param fieldName name of the optional field
     * @param defaultValue value returned for an absent, null, or non-coercible child
     * @return coerced integer value or the supplied default
     * @throws NullPointerException if {@code node} is {@code null}
     */
    private static int integer(final JsonNode node, final String fieldName, final int defaultValue) {
        final JsonNode child = node.get(fieldName);
        return child == null || child.isNull() ? defaultValue : child.asInt(defaultValue);
    }

    /**
     * <p>Reads an optional field using Jackson's double coercion and a caller-supplied default.</p>
     *
     * <p>The optional read performs these steps:</p>
     * <ol>
     *   <li>Reads the named child from the parent node.</li>
     *   <li>Returns {@code defaultValue} when the child is absent or JSON {@code null}.</li>
     *   <li>Otherwise returns {@link JsonNode#asDouble(double)} with the same default.</li>
     * </ol>
     *
     * <p>This supplies the optional classifier processing-time value.</p>
     *
     * @param node parent JSON node containing the optional field
     * @param fieldName name of the optional field
     * @param defaultValue value returned for an absent, null, or non-coercible child
     * @return coerced double value or the supplied default
     * @throws NullPointerException if {@code node} is {@code null}
     */
    private static double doubleValue(final JsonNode node, final String fieldName, final double defaultValue) {
        final JsonNode child = node.get(fieldName);
        return child == null || child.isNull() ? defaultValue : child.asDouble(defaultValue);
    }

    /**
     * <p>Reads an optional field using Jackson's boolean coercion and a caller-supplied default.</p>
     *
     * <p>The optional read performs these steps:</p>
     * <ol>
     *   <li>Reads the named child from the parent node.</li>
     *   <li>Returns {@code defaultValue} when the child is absent or JSON {@code null}.</li>
     *   <li>Otherwise returns {@link JsonNode#asBoolean(boolean)} with the same default.</li>
     * </ol>
     *
     * <p>This supplies the optional policy-fallback flag.</p>
     *
     * @param node parent JSON node containing the optional field
     * @param fieldName name of the optional field
     * @param defaultValue value returned for an absent, null, or non-coercible child
     * @return coerced boolean value or the supplied default
     * @throws NullPointerException if {@code node} is {@code null}
     */
    private static boolean bool(final JsonNode node, final String fieldName, final boolean defaultValue) {
        final JsonNode child = node.get(fieldName);
        return child == null || child.isNull() ? defaultValue : child.asBoolean(defaultValue);
    }

    /**
     * <p>Copies numeric classification probabilities from a JSON object into a map. Missing, null, non-object, and
     * non-numeric members do not cause validation failures.</p>
     *
     * <p>The extraction performs these steps:</p>
     * <ol>
     *   <li>Returns a mutable empty map immediately when the supplied node is {@code null} or not a JSON object.</li>
     *   <li>Iterates through object fields and copies only values Jackson identifies as numeric.</li>
     *   <li>Returns an immutable copy of the collected class-name-to-probability entries.</li>
     * </ol>
     *
     * <p>The extracted map becomes {@link ClassificationResult#probabilities()}.</p>
     *
     * @param node optional {@code probabilities} node from the classifier response
     * @return map of numeric probability fields, empty when the node is absent or not an object
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
