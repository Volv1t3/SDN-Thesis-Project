/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.policy;

import com.sma.sdn.model.PacketFeatures;
import com.sma.sdn.model.ServiceKey;
import java.util.Map;
import java.util.Optional;

/**
 * <p>Derives a service key from packet protocol and ports, preferring a recognized
 * service port so typical request and reply packets resolve to the same service.</p>
 * <p>{@link PairPolicyConsensusService} also uses the port-to-class mapping to
 * resolve conflicting directional classifications.</p>
 */
public final class ServiceKeyResolver {
    /** <p>Recognized transport service ports and their expected classification names.</p> */
    private static final Map<Integer, String> EXPECTED_CLASSES = Map.of(
            20, "FTP", 21, "FTP", 22, "SSH", 53, "DNS", 80, "HTTP", 123, "NTP", 443, "HTTP", 8080, "HTTP");

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
     * <p>Finds the expected class for a key's canonical port, if recognized.</p>
     * <p>The lookup proceeds as follows:</p>
     * <ol><li>Look up the canonical port in the static service map and wrap the
     * result, which may be absent, in an optional.</li></ol>
     * @param serviceKey service identity whose port is checked
     * @return expected class, or an empty optional for an unrecognized port
     */
    public Optional<String> expectedClassFor(final ServiceKey serviceKey) {
        return Optional.ofNullable(EXPECTED_CLASSES.get(serviceKey.canonicalServicePort()));
    }

    /**
     * <p>Checks whether a port has an expected class mapping.</p>
     * <p>The lookup proceeds as follows:</p>
     * <ol><li>Test membership in the recognized service-port map.</li></ol>
     * @param port transport port to examine
     * @return whether the port is recognized
     */
    public boolean isKnownServicePort(final int port) {
        return EXPECTED_CLASSES.containsKey(port);
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
