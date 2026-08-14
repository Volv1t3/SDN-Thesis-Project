/*
 * Copyright (c) 2026 Santiago Arellano and others.
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
 * Define la clase {@code ClassificationRequestJsonSerializer} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class ClassificationRequestJsonSerializer {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(ClassificationRequestJsonSerializer.class);
    private final ObjectMapper mapper;

    /**
     * Ejecuta la operacion {@code ClassificationRequestJsonSerializer} dentro del componente correspondiente.
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
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    public ClassificationRequestJsonSerializer(final ObjectMapper mapper) {
        this.mapper = mapper;
    }

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
     * @param features valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
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
