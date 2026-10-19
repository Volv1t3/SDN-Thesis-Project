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

package com.sma.sdn.serialization.xml;

import com.sma.sdn.model.CalculatedPathRequest;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.util.XmlSupport;
import java.util.Objects;
import java.util.Set;

/**
 * <p>Validates and renders a {@link CalculatedPathRequest} for the OpenDaylight path
 * computation operation. {@link com.sma.sdn.path.PathComputationService} sends the
 * resulting payload and uses {@link PathComputationResponseXmlDeserializer} to interpret
 * the response.</p>
 */
public final class PathComputationRequestXmlSerializer {
    /** <p>Algorithm identifiers accepted in the path computation request.</p> */
    private static final Set<String> SUPPORTED_ALGORITHMS = Set.of("spf", "cspf", "samcra");
    /** <p>Logger for path request details and serialized size.</p> */
    private static final StructuredLogger LOG =
            StructuredLogger.getLogger(PathComputationRequestXmlSerializer.class);
    /**
     * <p>Builds an XML path-computation input with IPv4 address family, graph endpoints,
     * bandwidth, class type, and the selected algorithm.</p>
     *
     * <p>Serialization proceeds as follows:</p>
     * <ol>
     *   <li>Validate the request's graph name, endpoints, bandwidth, and algorithm.</li>
     *   <li>Escape the graph and algorithm strings and insert the request values into the
     *       path computation XML template.</li>
     *   <li>Log request attributes and document length, then return the document.</li>
     * </ol>
     *
     * @param request path computation inputs
     * @return XML input document for the path computation operation
     * @throws NullPointerException if the request or its algorithm is null
     * @throws IllegalArgumentException if the graph name, endpoints, bandwidth, or algorithm is invalid
     */
    public String serialize(final CalculatedPathRequest request) {
        validate(request);
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <input xmlns="urn:opendaylight:params:xml:ns:yang:path:computation">
                    <graph-name>%s</graph-name>
                    <source>%d</source>
                    <destination>%d</destination>
                    <constraints>
                        <bandwidth>%d</bandwidth>
                        <class-type>%d</class-type>
                        <address-family>ipv4</address-family>
                    </constraints>
                    <algorithm>%s</algorithm>
                </input>
                """.formatted(
                XmlSupport.escape(request.graphName()),
                request.sourceGraphNodeId(),
                request.destinationGraphNodeId(),
                request.bandwidthBytesPerSecond(),
                request.classType(),
                XmlSupport.escape(request.algorithm()));
        LOG.trace("path_computation_request_serialized", "serialize",
                "Se serializo la solicitud XML de calculo de camino",
                StructuredLogger.fields("graph_name", request.graphName(),
                        "source_graph_node_id", request.sourceGraphNodeId(),
                        "destination_graph_node_id", request.destinationGraphNodeId(),
                        "bandwidth_bytes_per_second", request.bandwidthBytesPerSecond(),
                        "serialized_characters", xml.length()));
        return xml;
    }

    /**
     * <p>Checks the request fields required by this serializer before XML construction.</p>
     *
     * <p>Validation proceeds as follows:</p>
     * <ol>
     *   <li>Require a nonnull request and a nonblank graph name.</li>
     *   <li>Reject negative source or destination graph IDs and negative bandwidth.</li>
     *   <li>Require the algorithm to be one of the supported identifiers.</li>
     * </ol>
     *
     * @param request path computation input to validate
     * @throws NullPointerException if the request or its algorithm is null
     * @throws IllegalArgumentException if a graph name, endpoint, bandwidth, or algorithm is invalid
     */
    private static void validate(final CalculatedPathRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.graphName() == null || request.graphName().isBlank()) {
            throw new IllegalArgumentException("graphName es obligatorio");
        }
        if (request.sourceGraphNodeId() < 0L || request.destinationGraphNodeId() < 0L) {
            throw new IllegalArgumentException("Los nodos de origen y destino no pueden ser negativos");
        }
        if (request.bandwidthBytesPerSecond() < 0L) {
            throw new IllegalArgumentException("bandwidthBytesPerSecond no puede ser negativo");
        }
        if (!SUPPORTED_ALGORITHMS.contains(request.algorithm())) {
            throw new IllegalArgumentException("El algoritmo de camino no esta permitido: " + request.algorithm());
        }
    }
}
