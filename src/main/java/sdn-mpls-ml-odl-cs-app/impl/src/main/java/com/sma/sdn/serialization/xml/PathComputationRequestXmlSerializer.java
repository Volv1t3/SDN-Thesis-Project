/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.serialization.xml;

import com.sma.sdn.model.CalculatedPathRequest;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.util.XmlSupport;

/**
 * Define la clase {@code PathComputationRequestXmlSerializer} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class PathComputationRequestXmlSerializer {
    private static final StructuredLogger LOG =
            StructuredLogger.getLogger(PathComputationRequestXmlSerializer.class);
    /**
     * Construye la representacion serializada requerida por el endpoint consumidor.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param request valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    public String serialize(final CalculatedPathRequest request) {
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <input xmlns="urn:opendaylight:params:xml:ns:yang:path:computation">
                    <graph-name>%s</graph-name>
                    <source>%d</source>
                    <destination>%d</destination>
                    <constraints>
                        <address-family>ipv4</address-family>
                        <bandwidth>%d</bandwidth>
                        <class-type>%d</class-type>
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
}
