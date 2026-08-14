/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.serialization.xml;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sma.sdn.model.EroSubobject;
import com.sma.sdn.model.UpdateLspRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifica el contrato XML obligatorio para actualizar LSP RSVP-TE delegados.
 */
class UpdateLspRequestXmlSerializerTest {
    /**
     * Comprueba que la solicitud contiene PLSP ID, ancho de banda binario, ERO estricta y referencia PCEP.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Construye una solicitud con valores representativos del laboratorio.</li>
     *   <li>Serializa el modelo mediante el componente productivo.</li>
     *   <li>Comprueba los elementos obligatorios del contrato ODL.</li>
     * </ol>
     */
    @Test
    void includesDelegatedLspIdentityBandwidthAndStrictEro() {
        final String xml = new UpdateLspRequestXmlSerializer(new EroXmlSerializer()).serialize(request(111L));

        assertTrue(xml.contains("<name>sma-lsr1-lsr4-delegated</name>"));
        assertTrue(xml.contains("<plsp-id>111</plsp-id>"));
        assertTrue(xml.contains("<pst>rsvp-te</pst>"));
        assertTrue(xml.contains("<bandwidth>RhxAAA==</bandwidth>"));
        assertTrue(xml.contains("<processing-rule>false</processing-rule>"));
        assertTrue(xml.contains("<ip-prefix>14.14.14.14/32</ip-prefix>"));
        assertTrue(xml.contains("nt:topology-id=\"pcep-topology\""));
    }

    /**
     * Comprueba que nunca se serializa una actualizacion sin PLSP ID descubierto.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Construye una solicitud con PLSP ID invalido.</li>
     *   <li>Invoca el serializador.</li>
     *   <li>Exige el rechazo inmediato antes de cualquier llamada HTTP.</li>
     * </ol>
     */
    @Test
    void rejectsMissingPlspId() {
        final UpdateLspRequestXmlSerializer serializer = new UpdateLspRequestXmlSerializer(new EroXmlSerializer());
        assertThrows(IllegalArgumentException.class, () -> serializer.serialize(request(0L)));
    }

    private static UpdateLspRequest request(final long plspId) {
        return new UpdateLspRequest(
                "pcc://10.100.10.1",
                "sma-lsr1-lsr4-delegated",
                plspId,
                "RhxAAA==",
                List.of(new EroSubobject(false, "14.14.14.14/32")),
                "pcep-topology");
    }
}
