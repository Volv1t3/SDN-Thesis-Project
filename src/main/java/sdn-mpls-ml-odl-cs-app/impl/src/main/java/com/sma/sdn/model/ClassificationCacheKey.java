/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Identifies an exact classification cache entry by ingress switch, connector, and packet
 * feature tuple. {@link com.sma.sdn.registry.ClassificationRegistrar} constructs this key for
 * both insertion and lookup before attempting the broader {@link ServiceClassCacheKey}.</p>
 *
 * @param ingressSwitchName logical ingress switch name
 * @param ingressConnectorName ingress connector name
 * @param ethType Ethernet type of the packet
 * @param ipProto IP protocol number
 * @param srcPort source transport port
 * @param dstPort destination transport port
 */
public record ClassificationCacheKey(
        String ingressSwitchName,
        String ingressConnectorName,
        int ethType,
        int ipProto,
        int srcPort,
        int dstPort) {
}
