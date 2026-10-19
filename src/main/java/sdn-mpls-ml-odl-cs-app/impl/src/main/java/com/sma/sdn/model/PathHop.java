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

package com.sma.sdn.model;

/**
 * <p>Represents the local and remote IPv4 addresses of a calculated path segment. Path computation deserialization and ERO translation use the ordered hop data.</p>
 *
 * @param localIpv4 local IPv4 address of the hop
 * @param remoteIpv4 remote IPv4 address of the hop
 */
public record PathHop(String localIpv4, String remoteIpv4) {
}
