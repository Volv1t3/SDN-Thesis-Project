/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.serialization.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sma.sdn.model.PcepReportedLspSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifica la extraccion de LSP reportados desde XML PCEP con espacios de nombres.
 */
class PcepTopologyXmlDeserializerTest {
    /**
     * Comprueba que la identidad delegada y su estado operativo se extraen sin depender de prefijos XML.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Define una topologia PCEP representativa con un LSP reportado.</li>
     *   <li>Deserializa el documento con el analizador consciente de espacios de nombres.</li>
     *   <li>Valida PLSP ID, identificadores RSVP-TE, banderas, ERO y ancho de banda.</li>
     * </ol>
     */
    @Test
    void extractsDelegatedReportedLsp() {
        final String xml = """
                <topology xmlns="urn:TBD:params:xml:ns:yang:network-topology"
                          xmlns:pcep="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                    <topology-id>pcep-topology</topology-id>
                    <node>
                        <node-id>pcc://10.100.10.1</node-id>
                        <pcep:path-computation-client>
                            <pcep:reported-lsp>
                                <pcep:name>sma-lsr1-lsr4-delegated</pcep:name>
                                <pcep:path>
                                    <pcep:lsp>
                                        <pcep:plsp-id>111</pcep:plsp-id>
                                        <pcep:tlvs>
                                            <pcep:lsp-identifiers>
                                                <pcep:tunnel-id>110</pcep:tunnel-id>
                                                <pcep:lsp-id>7</pcep:lsp-id>
                                                <pcep:ipv4>
                                                    <pcep:ipv4-tunnel-sender-address>
                                                        11.11.11.11
                                                    </pcep:ipv4-tunnel-sender-address>
                                                    <pcep:ipv4-tunnel-endpoint-address>
                                                        14.14.14.14
                                                    </pcep:ipv4-tunnel-endpoint-address>
                                                </pcep:ipv4>
                                            </pcep:lsp-identifiers>
                                        </pcep:tlvs>
                                        <pcep:lsp-flags>
                                            <pcep:delegate>true</pcep:delegate>
                                            <pcep:administrative>true</pcep:administrative>
                                            <pcep:operational>up</pcep:operational>
                                        </pcep:lsp-flags>
                                    </pcep:lsp>
                                    <pcep:ero>
                                        <pcep:subobject>
                                            <pcep:loose>false</pcep:loose>
                                            <pcep:ip-prefix>
                                                <pcep:ip-prefix>10.0.12.2/32</pcep:ip-prefix>
                                            </pcep:ip-prefix>
                                        </pcep:subobject>
                                        <pcep:subobject>
                                            <pcep:loose>false</pcep:loose>
                                            <pcep:ip-prefix>
                                                <pcep:ip-prefix>14.14.14.14/32</pcep:ip-prefix>
                                            </pcep:ip-prefix>
                                        </pcep:subobject>
                                    </pcep:ero>
                                    <pcep:bandwidth>
                                        <pcep:bandwidth>RhxAAA==</pcep:bandwidth>
                                    </pcep:bandwidth>
                                </pcep:path>
                            </pcep:reported-lsp>
                        </pcep:path-computation-client>
                    </node>
                </topology>
                """;

        final List<PcepReportedLspSnapshot> snapshots = new PcepTopologyXmlDeserializer(
                new PcepReportedLspDeserializer()).deserialize(xml);
        final PcepReportedLspSnapshot snapshot = snapshots.getFirst();

        assertEquals(1, snapshots.size());
        assertEquals("pcc://10.100.10.1", snapshot.pccNode());
        assertEquals("sma-lsr1-lsr4-delegated", snapshot.name());
        assertEquals(111L, snapshot.plspId());
        assertEquals(110L, snapshot.tunnelId());
        assertEquals(7L, snapshot.lspId());
        assertTrue(snapshot.delegate());
        assertTrue(snapshot.administrative());
        assertEquals("up", snapshot.operational());
        assertEquals("10.0.12.2/32", snapshot.ero().getFirst().ipPrefix());
        assertEquals("RhxAAA==", snapshot.bandwidthBase64());
    }
}
