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

import com.sma.sdn.model.PcepReportedLspSnapshot;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.util.XmlSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * <p>Parses PCEP topology XML into reported-LSP snapshots. It supplies each containing PCC node ID to PcepReportedLspDeserializer; DelegatedLspService subsequently matches the resulting snapshots to configured directions.</p>
 */
public final class PcepTopologyXmlDeserializer {
    /** <p>Logger for PCEP topology parsing counts.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(PcepTopologyXmlDeserializer.class);
    /** <p>Parser for each individual reported LSP.</p> */
    private final PcepReportedLspDeserializer reportedLspDeserializer;

    /**
     * <p>Construct a topology parser with an individual reported-LSP parser.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Require and retain the reported-LSP parser.</li>
     * </ol>
     *
     * @param reportedLspDeserializer parser for reported LSP entries
     * @throws NullPointerException if the parser is null
     */
    public PcepTopologyXmlDeserializer(final PcepReportedLspDeserializer reportedLspDeserializer) {
        this.reportedLspDeserializer = Objects.requireNonNull(
                reportedLspDeserializer, "reportedLspDeserializer");
    }

    /**
     * <p>Parse all PCC nodes and their directly reported LSP entries.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Parse the XML and select nodes containing a path-computation client using namespace-independent XPath.</li>
     *   <li>Extract each node ID and delegate each reported-lsp element to the individual parser.</li>
     *   <li>Return an immutable list and log the number of PCCs and snapshots.</li>
     * </ol>
     *
     * @param xml PCEP topology XML document
     * @return immutable list of reported LSP snapshots
     * @throws RuntimeException if XML or XPath processing fails
     */
    public List<PcepReportedLspSnapshot> deserialize(final String xml) {
        /*XPath parser from utilities*/
        final Document document = XmlSupport.parse(xml);
        /* This XPath expression extracts all XML elements whose local name
         * is <node> and which contain a direct child element named
         * <path-computation-client>.
         *
         * The first predicate:
         *   [local-name()='node']
         * filters all document elements to retain only <node> elements.
         *
         * The second predicate:
         *   [*[local-name()='path-computation-client']]
         * verifies that the selected <node> element contains at least one
         * child element named <path-computation-client>.
         *
         * The use of local-name() allows the expression to match elements
         * regardless of their XML namespace.
         */
        final NodeList pccNodes = XmlSupport.nodes(
                document,
                "//*[local-name()='node'][*[local-name()='path-computation-client']]");

        /*Internally within each of these pcc Nodes and within each of the path computation elements within them there
        * exists a series of <reported-lsp> blocks taht contain the information about every lsp that is delegated or
        * reported at the very least to the controller. Since each router is conjfigured to delegate their tunnels, plus
        * report them always, the ones we are expecting in the topology are the ones configured at each router*/
        final List<PcepReportedLspSnapshot> snapshots = new ArrayList<>();
        for (int nodeIndex = 0; nodeIndex < pccNodes.getLength(); nodeIndex++) {
            final Node pccNode = pccNodes.item(nodeIndex);
            /*This xtracts extractste node id from the pcc node, each of these high level <node> tags also has an internal
            * <node-id> tag which represents the IP address of the node prefixed with the pcc:// identifiers, indiating that
            * these are pcc enabled nodes or pce enabled nodes*/
            final String nodeId = XmlSupport.string(pccNode, "./*[local-name()='node-id'][1]");
            /*This is another mechanism to extract node lists but this time all of the path computation client elements that
            * have at least one reported lsp which allows us to extract the path computation client block with settings,
            * reported capabililties and reported lsps*/
            final NodeList reportedLsps = XmlSupport.nodes(
                    pccNode,
                    "./*[local-name()='path-computation-client']/*[local-name()='reported-lsp']");

            /**
             * <p>Associate each reported LSP with its PCC node ID and use
             * {@link PcepReportedLspDeserializer} to produce a {@link PcepReportedLspSnapshot}.</p>
             */
            for (int lspIndex = 0; lspIndex < reportedLsps.getLength(); lspIndex++) {
                snapshots.add(reportedLspDeserializer.deserialize(nodeId, reportedLsps.item(lspIndex)));
            }
        }
        /**
         * <p>Return an immutable list containing one {@link PcepReportedLspSnapshot} per
         * processed {@code <reported-lsp>} child of a path-computation client.</p>
         */
        final List<PcepReportedLspSnapshot> snapshot = List.copyOf(snapshots);
        LOG.debug("pcep_topology_deserialized", "deserialize",
                "Se deserializo la estructura de PCC y LSP reportados por PCEP",
                StructuredLogger.fields("pcc_count", pccNodes.getLength(), "reported_lsp_count", snapshot.size()));

        /*Returns the whole list of reported lsps from the nodes the system has access to from the pcep topology*/
        return snapshot;
    }
}
