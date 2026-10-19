/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Represents the four packet attributes sent to the classifier and used to derive cache keys.
 * The packet extractor constructs these features, the classification service serializes them, and
 * the classification registrar uses them for exact and service-class lookups.</p>
 *
 * @param ethType unsigned 16-bit Ethernet type
 * @param ipProto unsigned 8-bit IP protocol number
 * @param srcPort unsigned 16-bit source port, or zero for protocols other than TCP and UDP
 * @param dstPort unsigned 16-bit destination port, or zero for protocols other than TCP and UDP
 */
public record PacketFeatures(int ethType, int ipProto, int srcPort, int dstPort) {

    /**
     * <p>Validates the numeric ranges and transport-port rules of the classifier request contract.</p>
     *
     * <p>Construction proceeds as follows:</p>
     * <ol>
     *   <li>Reject values outside the unsigned ranges of the Ethernet type, IP protocol, and ports.</li>
     *   <li>Require both ports to be zero when the protocol is neither TCP nor UDP.</li>
     * </ol>
     *
     * @param ethType Ethernet type to validate
     * @param ipProto IP protocol number to validate
     * @param srcPort source transport port to validate
     * @param dstPort destination transport port to validate
     * @throws IllegalArgumentException if a value is out of range or a non-TCP/UDP protocol has nonzero ports
     */
    public PacketFeatures {
        // Reject values outside the widths of the corresponding packet header fields.
        if (ethType < 0 || ethType > 65_535 || ipProto < 0 || ipProto > 255
                || srcPort < 0 || srcPort > 65_535 || dstPort < 0 || dstPort > 65_535) {
            throw new IllegalArgumentException("Las caracteristicas del paquete exceden los rangos admitidos");
        }
        // The classifier contract uses zero ports when TCP or UDP ports are not applicable.
        if (ipProto != 6 && ipProto != 17 && (srcPort != 0 || dstPort != 0)) {
            throw new IllegalArgumentException(
                    "Los protocolos que no son TCP ni UDP deben usar puertos de origen y destino en cero");
        }
    }
}
