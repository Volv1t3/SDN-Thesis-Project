/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * Define el registro {@code PacketFeatures} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad concreta del flujo de control, de los 
 * modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public record PacketFeatures(int ethType, int ipProto, int srcPort, int dstPort) {

    /**
     * Valida las restricciones numericas y de protocolo que exige el contrato JSON del clasificador Python.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Comprueba los limites sin signo de EtherType, protocolo IP y puertos.</li>
     *   <li>Identifica TCP y UDP como los unicos protocolos que usan puertos de capa cuatro.</li>
     *   <li>Exige puertos en cero para cualquier otro protocolo, incluido ICMP.</li>
     * </ol>
     *
     * @throws IllegalArgumentException si un campo excede el rango del contrato o los puertos no corresponden al
     *     protocolo
     */
    public PacketFeatures {
        if (ethType < 0 || ethType > 65_535 || ipProto < 0 || ipProto > 255
                || srcPort < 0 || srcPort > 65_535 || dstPort < 0 || dstPort > 65_535) {
            throw new IllegalArgumentException("Las caracteristicas del paquete exceden los rangos admitidos");
        }
        if (ipProto != 6 && ipProto != 17 && (srcPort != 0 || dstPort != 0)) {
            throw new IllegalArgumentException(
                    "Los protocolos que no son TCP ni UDP deben usar puertos de origen y destino en cero");
        }
    }
}
