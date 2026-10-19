/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Identifies a service through Ethernet type, IP protocol, canonical port, and a normalized value. The service-key resolver constructs it for grouping directional evidence in consensus.</p>
 *
 * @param ethType Ethernet type
 * @param ipProtocol IP protocol number
 * @param canonicalServicePort canonical service port
 * @param normalizedValue normalized service-key value
 */
public record ServiceKey(int ethType, int ipProtocol, int canonicalServicePort, String normalizedValue) {
}
