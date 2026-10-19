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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sma.sdn.model.PacketFeatures;
import com.sma.sdn.observability.StructuredLogger;
import java.nio.charset.StandardCharsets;

/**
 * <p>Serializes the four numeric {@link PacketFeatures} values into the nested JSON request contract accepted by the
 * external classifier. The resulting document contains one {@code packet_features} object with {@code eth_type},
 * {@code ip_proto}, {@code src_port}, and {@code dst_port} fields.</p>
 *
 * <p>{@link com.sma.sdn.classification.ClassificationService} invokes this converter only after a cache miss and passes
 * its output unchanged to {@link com.sma.sdn.http.ClassifierRestClient}. Numeric range and protocol/port consistency are
 * established when {@link PacketFeatures} is constructed; this serializer does not repeat those checks. It neither
 * sends the request nor selects a cache key.</p>
 */
public final class ClassificationRequestJsonSerializer {
    /**
     * <p>Logs the serialized feature values and UTF-8 payload size without owning transport behavior.</p>
     */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(ClassificationRequestJsonSerializer.class);

    /**
     * <p>Builds the request object tree and renders it as JSON.</p>
     */
    private final ObjectMapper mapper;

    /**
     * <p>Creates a request serializer backed by the supplied Jackson mapper. The reference is retained without a
     * null check; a missing mapper is detected only when a request is serialized.</p>
     *
     * <p>The constructor performs these steps:</p>
     * <ol>
     *   <li>Receives the shared {@link ObjectMapper} from the application provider.</li>
     *   <li>Retains it for request-tree creation and string serialization.</li>
     * </ol>
     *
     * <p>Construction does not create a JSON document; serialization occurs for each cache-miss request.</p>
     *
     * @param mapper Jackson mapper used to construct and render classifier requests
     */
    public ClassificationRequestJsonSerializer(final ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * <p>Converts packet features into the classifier's nested JSON request body. The four values are JSON numbers,
     * and no ingress identity, cache key, or policy is included. The mapper determines the rendered formatting.</p>
     *
     * <p>The serialization performs these steps:</p>
     * <ol>
     *   <li>Creates a root JSON object and adds its nested {@code packet_features} object.</li>
     *   <li>Writes EtherType, IP protocol, source port, and destination port under the classifier's snake-case field
     *       names.</li>
     *   <li>Uses {@link ObjectMapper#writeValueAsString(Object)} to render the tree.</li>
     *   <li>Logs the feature values and UTF-8 payload size, then returns the JSON string.</li>
     *   <li>If rendering or subsequent logging fails, wraps the cause in {@link IllegalArgumentException}; failures
     *       while creating or populating the tree occur before that handler and propagate directly.</li>
     * </ol>
     *
     * <p>The classification service supplies the returned string to the classifier HTTP client without modifying it.</p>
     *
     * @param features validated packet features to encode in the classifier request
     * @return JSON document containing the nested {@code packet_features} object
     * @throws IllegalArgumentException if rendering or subsequent logging throws an exception
     * @throws NullPointerException if {@code features} or the configured mapper is {@code null}
     */
    public String serialize(final PacketFeatures features) {
        final ObjectNode root = mapper.createObjectNode();
        final ObjectNode packetFeatures = root.putObject("packet_features");
        packetFeatures.put("eth_type", features.ethType());
        packetFeatures.put("ip_proto", features.ipProto());
        packetFeatures.put("src_port", features.srcPort());
        packetFeatures.put("dst_port", features.dstPort());
        try {
            final String json = mapper.writeValueAsString(root);
            LOG.trace("classification_request_serialized", "serialize",
                    "Se serializo la estructura de caracteristicas para el clasificador",
                    StructuredLogger.fields("eth_type", features.ethType(), "ip_protocol", features.ipProto(),
                            "source_port", features.srcPort(), "destination_port", features.dstPort(),
                            "serialized_bytes", json.getBytes(StandardCharsets.UTF_8).length));
            return json;
        } catch (Exception e) {
            throw new IllegalArgumentException("No fue posible serializar la solicitud al clasificador", e);
        }
    }
}
