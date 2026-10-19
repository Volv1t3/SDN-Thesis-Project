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

import com.sma.sdn.model.EroSubobject;
import com.sma.sdn.model.PcepReportedLspSnapshot;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.util.XmlSupport;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * <p>Parses one reported-lsp element into a PcepReportedLspSnapshot. The topology parser supplies the containing PCC ID, and DelegatedLspService later matches and validates the reported snapshot.</p>
 */
public final class PcepReportedLspDeserializer {
    /** <p>Logger for parsed PCEP LSP identity and state.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(PcepReportedLspDeserializer.class);
    /**
     * <p>Extract a reported LSP's identity, endpoint addresses, flags, route, and bandwidth.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Use the first path child as the extraction scope when present, otherwise use the reported LSP itself.</li>
     *   <li>Collect ordered ERO subobjects that have IP prefixes and their loose flags.</li>
     *   <li>Extract identifiers, endpoints, flags, operational state, and bandwidth, then construct and log the snapshot.</li>
     * </ol>
     *
     * @param pccNode containing PCC node identifier
     * @param reportedLsp DOM node for the reported LSP
     * @return snapshot associated with the PCC
     * @throws RuntimeException if XML value or XPath processing fails
     */
    public PcepReportedLspSnapshot deserialize(final String pccNode, final Node reportedLsp) {
        /**
         * <p>Find the first {@code <path>} child of the {@code <reported-lsp>} selected by
         * {@link PcepTopologyXmlDeserializer}; use the reported LSP itself when no path exists.</p>
         */
        final Node path = first(reportedLsp, "./*[local-name()='path'][1]");
        final Node scope = path == null ? reportedLsp : path; /*Validates if the path element is not null and ergo
        marks whether or not it was extract or not*/
        final List<EroSubobject> ero = new ArrayList<>(); /*This will hold the corresponding ERO Subobject DTO representation*/
        final NodeList subobjects = XmlSupport.nodes(
                scope,
                /**
                 * <p>As in {@link PcepTopologyXmlDeserializer}, local-name() ignores namespace
                 * prefixes. Select each {@code <subobject>} child of an {@code <ero>} descendant.</p>
                 */
                ".//*[local-name()='ero']/*[local-name()='subobject']");
        for (int index = 0; index < subobjects.getLength(); index++) {
            final Node subobject = subobjects.item(index);
            /* This function performs deper processing over the nodes that have suboject objects and extracts the ip prefix
            * element which contains the next hop address*/
            final String prefix = XmlSupport.string(
                    subobject,
                    "./*[local-name()='ip-prefix']/*[local-name()='ip-prefix'][1]");
            /*If it has a prefix to construct the Ero Suboject record it determines if the loose attribute is true or false*/
            if (prefix != null) {
                ero.add(new EroSubobject(Boolean.TRUE.equals(XmlSupport.bool(
                        subobject, "./*[local-name()='loose'][1]")), prefix));
            }
        }

        /**
         * <p>Build the snapshot from the reported name, LSP identifiers, endpoint addresses,
         * delegation and state flags, extracted ERO, and bandwidth. These values are consumed
         * by the registry and by subsequent delegated-LSP operations.</p>
         */
        final PcepReportedLspSnapshot snapshot = new PcepReportedLspSnapshot(
                pccNode,
                XmlSupport.string(reportedLsp, "./*[local-name()='name'][1]"), /*Extracts the name of the lsp*/
                number(scope, ".//*[local-name()='lsp']/*[local-name()='plsp-id'][1]"), /*Extacts the plsp id
                which is not the same as the lsp id and that is used to identify the lsp in the update-lsp processes*/

                /*Extracts the corresponding tunnel id which is reported from the underlying router*/
                number(scope, ".//*[local-name()='lsp-identifiers']/*[local-name()='tunnel-id'][1]"),
                /*Extractst the lsp id that is the one created at the router, usually under testing the relationship is
                * that the plsp id is one higher than this*/
                number(scope, ".//*[local-name()='lsp-identifiers']/*[local-name()='lsp-id'][1]"),

                /*This is the sender address of the lsp, i.e the head end IPv4 router ID for the sender and the tail end.
                * These are router IDs always as those are the identifiers that are configured at the underlying tunnels. Here is wher
                * the add-lsp system fails as it does not allow for the assignment of this directly from the function, and
                * Cisco IOX XR complains that without it it cannot acknowledge a tunnel */
                XmlSupport.string(scope, ".//*[local-name()='ipv4-tunnel-sender-address'][1]"),
                XmlSupport.string(scope, ".//*[local-name()='ipv4-tunnel-endpoint-address'][1]"),

                /*Here we are extracting the corresponding operational flags which are associated with the lsp, usually
                * for the ones that we are workign with we should see the delegate flag set to true and operational to up*/
                Boolean.TRUE.equals(XmlSupport.bool(
                        scope, ".//*[local-name()='lsp-flags']/*[local-name()='delegate'][1]")),
                Boolean.TRUE.equals(XmlSupport.bool(
                        scope, ".//*[local-name()='lsp-flags']/*[local-name()='administrative'][1]")),
                XmlSupport.string(scope, ".//*[local-name()='lsp-flags']/*[local-name()='operational'][1]"),
                ero,
                /*This extracts the bandiwdth configuration wich is base 64 encoded.*/
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

    /**
     * <p>Read a numeric value from an XML scope, defaulting missing values to zero.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Evaluate the XPath as a long and return zero if no value is present.</li>
     * </ol>
     *
     * @param node XML scope
     * @param expression XPath expression
     * @return parsed value or zero
     */
    private static long number(final Node node, final String expression) {
        final Long value = XmlSupport.longValue(node, expression);
        return value == null ? 0L : value;
    }

    /**
     * <p>Find the first node selected by an XPath expression.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Evaluate the node list and return its first item or null when empty.</li>
     * </ol>
     *
     * @param node XML scope
     * @param expression XPath selection
     * @return first match or null
     */
    private static Node first(final Node node, final String expression) {
        final NodeList nodes = XmlSupport.nodes(node, expression);
        return nodes.getLength() == 0 ? null : nodes.item(0);
    }
}
