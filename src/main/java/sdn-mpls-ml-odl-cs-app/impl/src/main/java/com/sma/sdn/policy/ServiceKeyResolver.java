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

/** Produces the same service key for request and reply transport packets. */
public final class ServiceKeyResolver {
    private static final Map<Integer, String> EXPECTED_CLASSES = Map.of(
            20, "FTP", 21, "FTP", 22, "SSH", 53, "DNS", 80, "HTTP", 123, "NTP", 443, "HTTP", 8080, "HTTP");

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

    public Optional<String> expectedClassFor(final ServiceKey serviceKey) {
        return Optional.ofNullable(EXPECTED_CLASSES.get(serviceKey.canonicalServicePort()));
    }

    public boolean isKnownServicePort(final int port) {
        return EXPECTED_CLASSES.containsKey(port);
    }

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
