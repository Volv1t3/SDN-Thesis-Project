/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Identifies a reusable classification by switch, protocol attributes, and canonical service
 * port. {@link com.sma.sdn.registry.ClassificationRegistrar} uses this key after an exact cache
 * miss; it omits the connector and the noncanonical transport port.</p>
 *
 * @param ingressSwitchName logical ingress switch name
 * @param ethType Ethernet type of the packet
 * @param ipProto IP protocol number
 * @param canonicalServicePort port selected by the registrar's service-port rule
 */
public record ServiceClassCacheKey(
        String ingressSwitchName,
        int ethType,
        int ipProto,
        int canonicalServicePort) {
}
