/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.serialization.xml;

import com.sma.sdn.model.UpdateLspRequest;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.util.XmlSupport;
import java.util.Objects;

/**
 * Serializa una actualizacion estricta de un LSP RSVP-TE previamente delegado al controlador ODL.
 */
public final class UpdateLspRequestXmlSerializer {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(UpdateLspRequestXmlSerializer.class);
    private final EroXmlSerializer eroXmlSerializer;

    /**
     * Crea el serializador con el componente encargado de representar los subobjetos ERO.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida la dependencia obligatoria.</li>
     *   <li>Conserva el serializador para las solicitudes posteriores.</li>
     * </ol>
     *
     * @param eroXmlSerializer serializador de subobjetos ERO
     * @throws NullPointerException si el serializador es nulo
     */
    public UpdateLspRequestXmlSerializer(final EroXmlSerializer eroXmlSerializer) {
        this.eroXmlSerializer = Objects.requireNonNull(eroXmlSerializer, "eroXmlSerializer");
    }

    /**
     * Construye el XML completo de {@code update-lsp} con PLSP ID, ancho de banda, ERO y referencia de topologia.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Exige un PLSP ID positivo y un ancho de banda Base64 no vacio.</li>
     *   <li>Serializa el ERO estricto en el orden calculado.</li>
     *   <li>Agrega las banderas de delegacion y administracion junto con la referencia PCEP.</li>
     * </ol>
     *
     * @param request solicitud tipada de actualizacion del LSP delegado
     * @return documento XML aceptado por {@code network-topology-pcep:update-lsp}
     * @throws IllegalArgumentException si falta el PLSP ID o el ancho de banda
     * @throws NullPointerException si la solicitud es nula
     */
    public String serialize(final UpdateLspRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.plspId() <= 0L) {
            throw new IllegalArgumentException("plspId debe ser mayor que cero");
        }
        if (request.bandwidthBase64() == null || request.bandwidthBase64().isBlank()) {
            throw new IllegalArgumentException("bandwidthBase64 es obligatorio");
        }
        if (request.pccNode() == null || request.pccNode().isBlank()) {
            throw new IllegalArgumentException("pccNode es obligatorio");
        }
        if (request.lspName() == null || request.lspName().isBlank()) {
            throw new IllegalArgumentException("lspName es obligatorio");
        }
        if (request.pcepTopologyId() == null || request.pcepTopologyId().isBlank()) {
            throw new IllegalArgumentException("pcepTopologyId es obligatorio");
        }
        if (request.eroSubobjects() == null || request.eroSubobjects().isEmpty()) {
            throw new IllegalArgumentException("eroSubobjects debe contener al menos un subobjeto");
        }
        final String networkTopologyReference = "/nt:network-topology/nt:topology[nt:topology-id=\""
                + XmlSupport.escape(request.pcepTopologyId()) + "\"]";
        final String networkTopologyReferenceElement =
                "<network-topology-ref xmlns:nt=\"urn:TBD:params:xml:ns:yang:network-topology\">"
                        + networkTopologyReference + "</network-topology-ref>";
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <input xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                    %s
                    <arguments>
                        <path-setup-type>
                            <pst>rsvp-te</pst>
                        </path-setup-type>
                        <metadata/>
                        <lsp>
                            <processing-rule>true</processing-rule>
                            <plsp-id>%d</plsp-id>
                            <lsp-flags>
                                <delegate>true</delegate>
                                <administrative>true</administrative>
                            </lsp-flags>
                        </lsp>
                        <bandwidth>
                            <processing-rule>true</processing-rule>
                            <bandwidth>%s</bandwidth>
                            <ignore>false</ignore>
                        </bandwidth>
                        <ero>
                            <processing-rule>true</processing-rule>
                %s            <ignore>false</ignore>
                        </ero>
                    </arguments>
                    <name>%s</name>
                    <node>%s</node>
                </input>
                """.formatted(
                networkTopologyReferenceElement,
                request.plspId(),
                XmlSupport.escape(request.bandwidthBase64()),
                eroXmlSerializer.serialize(request.eroSubobjects()),
                XmlSupport.escape(request.lspName()),
                XmlSupport.escape(request.pccNode()));
        LOG.debug("update_lsp_request_serialized", "serialize",
                "Se serializo la solicitud XML para actualizar el LSP delegado",
                StructuredLogger.fields("pcc_node", request.pccNode(), "lsp_name", request.lspName(),
                        "plsp_id", request.plspId(), "pcep_topology_id", request.pcepTopologyId(),
                        "ero_subobject_count", request.eroSubobjects().size(),
                        "bandwidth_encoded_length", request.bandwidthBase64().length(),
                        "serialized_characters", xml.length()));
        return xml;
    }
}
