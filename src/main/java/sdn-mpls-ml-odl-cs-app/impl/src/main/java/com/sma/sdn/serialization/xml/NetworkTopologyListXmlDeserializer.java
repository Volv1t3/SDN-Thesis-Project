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

import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.util.XmlSupport;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * <p>Extracts the available topology identifiers from an OpenDaylight network-topology
 * response. {@link com.sma.sdn.topology.TopologyDiscoveryService} uses the identifiers
 * when discovering the configured control-plane topologies.</p>
 */
public final class NetworkTopologyListXmlDeserializer {
    /** <p>Logger for the distinct discovered topology identifiers.</p> */
    private static final StructuredLogger LOG =
            StructuredLogger.getLogger(NetworkTopologyListXmlDeserializer.class);
    /**
     * <p>Reads all nonblank topology IDs from an XML document, removing duplicates.</p>
     *
     * <p>Extraction proceeds as follows:</p>
     * <ol>
     *   <li>Parse the document and select topology-id elements irrespective of namespace.</li>
     *   <li>Trim nonblank text values and add them to a set.</li>
     *   <li>Create an immutable set, log its contents and count, and return it.</li>
     * </ol>
     *
     * @param xml network-topology listing XML
     * @return immutable set of trimmed, distinct topology identifiers
     * @throws IllegalArgumentException if XML parsing or XPath evaluation fails
     */
    public Set<String> deserialize(final String xml) {
        final Document document = XmlSupport.parse(xml);
        final NodeList topologyIds = XmlSupport.nodes(document, "//*[local-name()='topology-id']");
        final Set<String> values = new HashSet<>();
        for (int index = 0; index < topologyIds.getLength(); index++) {
            final String value = topologyIds.item(index).getTextContent();
            if (value != null && !value.isBlank()) {
                values.add(value.trim());
            }
        }
        final Set<String> topologyIdsSnapshot = Set.copyOf(values);
        LOG.debug("network_topology_list_deserialized", "deserialize",
                "Se deserializo la lista de topologias disponibles en ODL",
                StructuredLogger.fields("topology_count", topologyIdsSnapshot.size(),
                        "topology_ids", topologyIdsSnapshot));
        return topologyIdsSnapshot;
    }
}
