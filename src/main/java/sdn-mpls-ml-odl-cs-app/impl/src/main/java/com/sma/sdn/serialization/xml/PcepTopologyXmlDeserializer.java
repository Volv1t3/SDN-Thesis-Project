/*
 * Copyright (c) 2026 Santiago Arellano and others.
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
 * Recorre una topologia PCEP completa y produce una instantanea por cada LSP reportado por sus nodos PCC.
 */
public final class PcepTopologyXmlDeserializer {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(PcepTopologyXmlDeserializer.class);
    private final PcepReportedLspDeserializer reportedLspDeserializer;

    /**
     * Crea el deserializador de topologia con el analizador de entradas reportadas.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida el analizador individual obligatorio.</li>
     *   <li>Lo conserva para procesar cada entrada de la topologia.</li>
     * </ol>
     *
     * @param reportedLspDeserializer deserializador de una entrada {@code reported-lsp}
     */
    public PcepTopologyXmlDeserializer(final PcepReportedLspDeserializer reportedLspDeserializer) {
        this.reportedLspDeserializer = Objects.requireNonNull(
                reportedLspDeserializer, "reportedLspDeserializer");
    }

    /**
     * Analiza XML con espacios de nombres y vincula cada LSP reportado con el nodo PCC que lo contiene.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Construye un documento DOM seguro y consciente de espacios de nombres.</li>
     *   <li>Recorre los nodos de topologia que contienen un cliente de computacion de caminos.</li>
     *   <li>Deserializa todas las entradas {@code reported-lsp} descendientes.</li>
     * </ol>
     *
     * @param xml documento XML de {@code pcep-topology?content=all}
     * @return lista inmutable de LSP reportados
     * @throws IllegalArgumentException si el XML no es valido
     */
    public List<PcepReportedLspSnapshot> deserialize(final String xml) {
        final Document document = XmlSupport.parse(xml);
        final NodeList pccNodes = XmlSupport.nodes(
                document,
                "//*[local-name()='node'][*[local-name()='path-computation-client']]");
        final List<PcepReportedLspSnapshot> snapshots = new ArrayList<>();
        for (int nodeIndex = 0; nodeIndex < pccNodes.getLength(); nodeIndex++) {
            final Node pccNode = pccNodes.item(nodeIndex);
            final String nodeId = XmlSupport.string(pccNode, "./*[local-name()='node-id'][1]");
            final NodeList reportedLsps = XmlSupport.nodes(
                    pccNode,
                    "./*[local-name()='path-computation-client']/*[local-name()='reported-lsp']");
            for (int lspIndex = 0; lspIndex < reportedLsps.getLength(); lspIndex++) {
                snapshots.add(reportedLspDeserializer.deserialize(nodeId, reportedLsps.item(lspIndex)));
            }
        }
        final List<PcepReportedLspSnapshot> snapshot = List.copyOf(snapshots);
        LOG.debug("pcep_topology_deserialized", "deserialize",
                "Se deserializo la estructura de PCC y LSP reportados por PCEP",
                StructuredLogger.fields("pcc_count", pccNodes.getLength(), "reported_lsp_count", snapshot.size()));
        return snapshot;
    }
}
