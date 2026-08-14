/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.serialization.xml;

import com.sma.sdn.model.EroSubobject;
import com.sma.sdn.model.PcepReportedLspSnapshot;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.util.XmlSupport;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Deserializa una entrada individual {@code reported-lsp} mediante rutas XPath independientes de prefijos XML.
 */
public final class PcepReportedLspDeserializer {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(PcepReportedLspDeserializer.class);
    /**
     * Extrae la identidad, las banderas, la ERO y el ancho de banda de una entrada PCEP reportada.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Lee los identificadores del LSP y de sus extremos IPv4.</li>
     *   <li>Interpreta las banderas de delegacion, administracion y operacion.</li>
     *   <li>Conserva en orden todos los subobjetos IPv4 del ERO activo.</li>
     * </ol>
     *
     * @param pccNode identificador del nodo PCC propietario
     * @param reportedLsp nodo DOM correspondiente a {@code reported-lsp}
     * @return instantanea tipada del LSP reportado
     */
    public PcepReportedLspSnapshot deserialize(final String pccNode, final Node reportedLsp) {
        final Node path = first(reportedLsp, "./*[local-name()='path'][1]");
        final Node scope = path == null ? reportedLsp : path;
        final List<EroSubobject> ero = new ArrayList<>();
        final NodeList subobjects = XmlSupport.nodes(
                scope,
                ".//*[local-name()='ero']/*[local-name()='subobject']");
        for (int index = 0; index < subobjects.getLength(); index++) {
            final Node subobject = subobjects.item(index);
            final String prefix = XmlSupport.string(
                    subobject,
                    "./*[local-name()='ip-prefix']/*[local-name()='ip-prefix'][1]");
            if (prefix != null) {
                ero.add(new EroSubobject(Boolean.TRUE.equals(XmlSupport.bool(
                        subobject, "./*[local-name()='loose'][1]")), prefix));
            }
        }

        final PcepReportedLspSnapshot snapshot = new PcepReportedLspSnapshot(
                pccNode,
                XmlSupport.string(reportedLsp, "./*[local-name()='name'][1]"),
                number(scope, ".//*[local-name()='lsp']/*[local-name()='plsp-id'][1]"),
                number(scope, ".//*[local-name()='lsp-identifiers']/*[local-name()='tunnel-id'][1]"),
                number(scope, ".//*[local-name()='lsp-identifiers']/*[local-name()='lsp-id'][1]"),
                XmlSupport.string(scope, ".//*[local-name()='ipv4-tunnel-sender-address'][1]"),
                XmlSupport.string(scope, ".//*[local-name()='ipv4-tunnel-endpoint-address'][1]"),
                Boolean.TRUE.equals(XmlSupport.bool(
                        scope, ".//*[local-name()='lsp-flags']/*[local-name()='delegate'][1]")),
                Boolean.TRUE.equals(XmlSupport.bool(
                        scope, ".//*[local-name()='lsp-flags']/*[local-name()='administrative'][1]")),
                XmlSupport.string(scope, ".//*[local-name()='lsp-flags']/*[local-name()='operational'][1]"),
                ero,
                XmlSupport.string(
                        scope,
                        "./*[local-name()='bandwidth']/*[local-name()='bandwidth'][1]"));
        LOG.trace("reported_lsp_deserialized", "deserialize",
                "Se deserializo una instantanea individual de LSP reportado",
                StructuredLogger.fields("pcc_node", pccNode, "lsp_name", snapshot.name(),
                        "plsp_id", snapshot.plspId(), "delegated", snapshot.delegate(),
                        "administrative_up", snapshot.administrative(),
                        "operational_state", snapshot.operational(), "ero_subobject_count", ero.size()));
        return snapshot;
    }

    private static long number(final Node node, final String expression) {
        final Long value = XmlSupport.longValue(node, expression);
        return value == null ? 0L : value;
    }

    private static Node first(final Node node, final String expression) {
        final NodeList nodes = XmlSupport.nodes(node, expression);
        return nodes.getLength() == 0 ? null : nodes.item(0);
    }
}
