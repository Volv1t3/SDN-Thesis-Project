/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.serialization.xml;

import com.sma.sdn.model.BgpLsTopologyNode;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.util.XmlSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Define la clase {@code BgpLsTopologyXmlDeserializer} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class BgpLsTopologyXmlDeserializer {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(BgpLsTopologyXmlDeserializer.class);
    private static final Pattern GRAPH_NODE_ID = Pattern.compile("(?:^|[?&])router=(\\d+)(?:$|&)");

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
    public List<BgpLsTopologyNode> deserialize(final String xml) {
        final Document document = XmlSupport.parse(xml);
        final String topologyId = XmlSupport.string(document, "//*[local-name()='topology-id'][1]");
        final NodeList nodes = XmlSupport.nodes(document, "//*[local-name()='node']");
        final List<BgpLsTopologyNode> result = new ArrayList<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            final Node node = nodes.item(index);
            final String nodeId = XmlSupport.string(node, "./*[local-name()='node-id'][1]");
            final String routerId = XmlSupport.string(node,
                    ".//*[local-name()='igp-node-attributes']/*[local-name()='router-id'][1]");
            final String teRouterId = XmlSupport.string(node, ".//*[local-name()='te-router-id-ipv4'][1]");
            if (nodeId == null || routerId == null) {
                continue;
            }
            result.add(new BgpLsTopologyNode(topologyId, nodeId, routerId, teRouterId, graphNodeId(nodeId)));
        }
        final List<BgpLsTopologyNode> snapshot = List.copyOf(result);
        LOG.debug("bgp_ls_topology_deserialized", "deserialize",
                "Se deserializo la estructura de nodos de la topologia BGP-LS",
                StructuredLogger.fields("topology_id", topologyId, "xml_node_count", nodes.getLength(),
                        "usable_node_count", snapshot.size()));
        return snapshot;
    }

    /**
     * Ejecuta la operacion {@code graphNodeId} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param nodeId valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private static long graphNodeId(final String nodeId) {
        final Matcher matcher = GRAPH_NODE_ID.matcher(nodeId);
        if (!matcher.find()) {
            throw new IllegalArgumentException(
                    "El node-id de BGP-LS no contiene router=<numero>: " + nodeId);
        }
        return Long.parseLong(matcher.group(1));
    }
}
