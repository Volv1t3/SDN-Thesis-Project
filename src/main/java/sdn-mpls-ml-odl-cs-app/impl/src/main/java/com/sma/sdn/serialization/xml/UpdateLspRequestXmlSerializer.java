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

import com.sma.sdn.model.UpdateLspRequest;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.util.XmlSupport;
import java.util.Objects;

/**
 * <p>Builds update-lsp XML for an existing delegated RSVP-TE LSP. DelegatedLspService supplies the registry identity, requested bandwidth, and calculated route; EroXmlSerializer renders its ordered route subobjects.</p>
 */
public final class UpdateLspRequestXmlSerializer {
    /** <p>Logger for serialized update-lsp request details.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(UpdateLspRequestXmlSerializer.class);
    /** <p>Serializer for ordered ERO subobjects.</p> */
    private final EroXmlSerializer eroXmlSerializer;

    /**
     * <p>Create a serializer with a required ERO serializer.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Require and retain the route serializer.</li>
     * </ol>
     *
     * @param eroXmlSerializer ERO subobject serializer
     * @throws NullPointerException if the serializer is null
     */
    public UpdateLspRequestXmlSerializer(final EroXmlSerializer eroXmlSerializer) {
        this.eroXmlSerializer = Objects.requireNonNull(eroXmlSerializer, "eroXmlSerializer");
    }

    /**
     * <p>Serialize an update-lsp request with its PCEP topology reference and LSP identity.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Validate the PLSP ID, PCC, LSP name, topology ID, bandwidth, and nonempty ERO.</li>
     *   <li>Render the topology reference, serialize the ERO, escape interpolated values, and build the RSVP-TE XML.</li>
     *   <li>Log request details and return the XML.</li>
     * </ol>
     *
     * @param request typed LSP update request
     * @return update-lsp XML document
     * @throws NullPointerException if the request is null
     * @throws IllegalArgumentException if required request fields are invalid
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
