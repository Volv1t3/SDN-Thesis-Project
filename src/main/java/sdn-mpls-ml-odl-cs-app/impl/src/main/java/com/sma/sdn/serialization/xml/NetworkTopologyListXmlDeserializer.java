/*
 * Copyright (c) 2026 Santiago Arellano and others.
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
 * Define la clase {@code NetworkTopologyListXmlDeserializer} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class NetworkTopologyListXmlDeserializer {
    private static final StructuredLogger LOG =
            StructuredLogger.getLogger(NetworkTopologyListXmlDeserializer.class);
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
     * @param xml valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
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
