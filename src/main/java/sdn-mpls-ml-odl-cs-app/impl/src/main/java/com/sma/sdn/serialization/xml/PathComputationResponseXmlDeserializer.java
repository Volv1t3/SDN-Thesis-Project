/*
 * Copyright (c) 2026 Santiago Arellano and others.
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
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Define la clase {@code PathComputationResponseXmlDeserializer} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class PathComputationResponseXmlDeserializer {
    private static final StructuredLogger LOG =
            StructuredLogger.getLogger(PathComputationResponseXmlDeserializer.class);
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
    public PathComputationResponse deserialize(final String xml) {
        final Document document = XmlSupport.parse(xml);
        final String status = XmlSupport.string(document, "//*[local-name()='status'][1]");
        final Integer metric = XmlSupport.integer(document, "//*[local-name()='computed-te-metric'][1]");
        final NodeList pathDescriptions = XmlSupport.nodes(document, "//*[local-name()='path-description']");
        final List<PathHop> hops = new ArrayList<>();
        for (int index = 0; index < pathDescriptions.getLength(); index++) {
            final Node node = pathDescriptions.item(index);
            final String remoteIpv4 = XmlSupport.string(node, "./*[local-name()='remote-ipv4'][1]");
            final String localIpv4 = XmlSupport.string(node, "./*[local-name()='ipv4'][1]");
            if (remoteIpv4 != null) {
                hops.add(new PathHop(localIpv4, remoteIpv4));
            }
        }
        final PathComputationResponse response =
                new PathComputationResponse(status, List.copyOf(hops), metric == null ? 0 : metric);
        LOG.debug("path_computation_response_deserialized", "deserialize",
                "Se deserializo la respuesta XML del calculo de camino",
                StructuredLogger.fields("status", response.status(), "hop_count", response.pathDescriptions().size(),
                        "computed_te_metric", response.computedTeMetric()));
        return response;
    }
}
