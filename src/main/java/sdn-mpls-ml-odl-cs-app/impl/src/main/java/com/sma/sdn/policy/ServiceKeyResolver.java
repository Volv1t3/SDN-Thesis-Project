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

package com.sma.sdn.policy;

import com.sma.sdn.model.PacketFeatures;
import com.sma.sdn.model.ServiceKey;
import java.util.Set;

/**
 * <p>Derives a service key from packet protocol and ports, preferring a recognized
 * service port so typical request and reply packets resolve to the same service.</p>
 * <p>The key only correlates observations; it never supplies a traffic class.</p>
 */
public final class ServiceKeyResolver {
    /** <p>Recognized ports used to normalize request and reply service identity.</p> */
    private static final Set<Integer> KNOWN_SERVICE_PORTS = Set.of(20, 21, 22, 53, 80, 123, 443, 8080);

    /**
     * <p>Builds a normalized service key for the packet features.</p>
     * <p>The key is constructed as follows:</p>
     * <ol>
     *   <li>For ICMP, use port zero and omit the port from the normalized value.</li>
     *   <li>For other protocols, select a canonical port and include it with Ethernet
     *       type and IP protocol in the normalized value.</li>
     * </ol>
     * @param features extracted packet protocol and ports
     * @return service identity and its normalized representation
     */
    public ServiceKey resolve(final PacketFeatures features) {
        final int protocol = features.ipProto();
        if (protocol == 1) {
            return new ServiceKey(features.ethType(), protocol, 0,
                    "eth_type=" + features.ethType() + "|ip_proto=" + protocol);
        }
        final int port = canonicalPort(features.srcPort(), features.dstPort());
        return new ServiceKey(features.ethType(), protocol, port,
                "eth_type=" + features.ethType() + "|ip_proto=" + protocol + "|service_port=" + port);
    }

    /**
     * <p>Checks whether a port is recognized for service-key normalization.</p>
     * <p>The lookup proceeds as follows:</p>
     * <ol><li>Test membership in the recognized service-port set.</li></ol>
     * @param port transport port to examine
     * @return whether the port is recognized
     */
    public boolean isKnownServicePort(final int port) {
        return KNOWN_SERVICE_PORTS.contains(port);
    }

    /**
     * <p>Chooses the service port for a non-ICMP packet.</p>
     * <p>The selection proceeds as follows:</p>
     * <ol>
     *   <li>Prefer a recognized destination port, then a recognized source port.</li>
     *   <li>Otherwise use the destination when positive, or the source.</li>
     * </ol>
     * @param sourcePort packet source port
     * @param destinationPort packet destination port
     * @return selected port; unknown request and reply ports need not match
     */
    private int canonicalPort(final int sourcePort, final int destinationPort) {
        if (isKnownServicePort(destinationPort)) {
            return destinationPort;
        }
        if (isKnownServicePort(sourcePort)) {
            return sourcePort;
        }
        return destinationPort > 0 ? destinationPort : sourcePort;
    }
}
