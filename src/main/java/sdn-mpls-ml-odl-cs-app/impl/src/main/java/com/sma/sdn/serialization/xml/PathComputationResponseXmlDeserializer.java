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

import com.sma.sdn.model.PathComputationResponse;
import com.sma.sdn.model.PathHop;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.util.XmlSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * <p>Converts an OpenDaylight path-computation XML response into its status, TE metric,
 * and ordered path hops. {@link com.sma.sdn.path.PathComputationService} consumes the
 * result when interpreting computed paths for subsequent route handling.</p>
 */
public final class PathComputationResponseXmlDeserializer {
    /** <p>Path-computation status values recognized in the response.</p> */
    private static final Set<String> VALID_STATUSES = Set.of(
            "idle", "in-progress", "active", "completed", "failed", "no-path", "no-source",
            "no-destination", "equal-endpoints");
    /** <p>Logger for parsed path status, hop count, and TE metric.</p> */
    private static final StructuredLogger LOG =
            StructuredLogger.getLogger(PathComputationResponseXmlDeserializer.class);
    /**
     * <p>Parses a response, rejecting unknown or missing statuses and retaining only path
     * descriptions with a remote IPv4 address. A missing metric becomes zero.</p>
     *
     * <p>Deserialization proceeds as follows:</p>
     * <ol>
     *   <li>Parse the XML and check the first status against the recognized values.</li>
     *   <li>Read the optional TE metric and traverse path-description elements in document order.</li>
     *   <li>Create a hop for each entry with a remote address, retaining its optional local address.</li>
     *   <li>Build the response with an immutable hop list, log its details, and return it.</li>
     * </ol>
     *
     * @param xml path-computation response XML
     * @return parsed status, ordered usable hops, and computed TE metric
     * @throws IllegalArgumentException if XML processing fails, the status is invalid,
     *         or the metric cannot be parsed as an integer
     */
    public PathComputationResponse deserialize(final String xml) {
        final Document document = XmlSupport.parse(xml);
        /*In this case the first validation done is upon the status of the path calculation, expecting for it to be
        * completed */
        final String status = XmlSupport.string(document, "//*[local-name()='status'][1]");
        if (status == null || !VALID_STATUSES.contains(status)) {
            throw new IllegalArgumentException(
                    "La respuesta de calculo de camino tiene un estado invalido: " + status);
        }

        /*Always 1 as we are not using it*/
        final Integer metric = XmlSupport.integer(document, "//*[local-name()='computed-te-metric'][1]");

        /*here we are selecting from the XML response body the corresponding node list whose identifier is <path-description>
        * and creating a list out of them for processing*/
        final NodeList pathDescriptions = XmlSupport.nodes(document, "//*[local-name()='path-description']");
        final List<PathHop> hops = new ArrayList<>();
        /*For each of the description elements we create a DTO for the PathHop that it represents based on the remove IPv4
        * address which represets the interface f the next hope where we arrive from, and the local ipv4 address which represets
        * the router's own interface before making that jump*/
        for (int index = 0; index < pathDescriptions.getLength(); index++) {
            final Node node = pathDescriptions.item(index);
            final String remoteIpv4 = XmlSupport.string(node, "./*[local-name()='remote-ipv4'][1]");
            final String localIpv4 = XmlSupport.string(node, "./*[local-name()='ipv4'][1]");
            if (remoteIpv4 != null) {
                hops.add(new PathHop(localIpv4, remoteIpv4));
            }
        }
        /*For returning this operation properly we construct a nested DTO comprised of the hops as an immutable list (DTO), and
        * the corresponding status and metric value*/
        final PathComputationResponse response =
                new PathComputationResponse(status, List.copyOf(hops), metric == null ? 0 : metric);
        LOG.debug("path_computation_response_deserialized", "deserialize",
                "Se deserializo la respuesta XML del calculo de camino",
                StructuredLogger.fields("status", response.status(), "hop_count", response.pathDescriptions().size(),
                        "computed_te_metric", response.computedTeMetric()));
        return response;
    }
}
