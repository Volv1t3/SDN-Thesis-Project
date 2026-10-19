/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Identifies a tunnel endpoint by logical name, router ID, and PCC node. Configured endpoints are assembled into directional tunnel definitions.</p>
 *
 * @param logicalName configured logical endpoint name
 * @param routerId router identifier
 * @param pccNode PCEP client node identifier
 */
public record TunnelEndpoint(String logicalName, String routerId, String pccNode) {
}
