/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Carries the graph, endpoint, bandwidth, class, and algorithm inputs used to construct a path-computation request to OpenDaylight.</p>
 *
 * @param graphName path-computation graph name
 * @param sourceGraphNodeId source graph node identifier
 * @param destinationGraphNodeId destination graph node identifier
 * @param bandwidthBytesPerSecond requested bandwidth in bytes per second
 * @param classType path-computation class type
 * @param algorithm path-computation algorithm
 */
public record CalculatedPathRequest(
        String graphName,
        long sourceGraphNodeId,
        long destinationGraphNodeId,
        long bandwidthBytesPerSecond,
        int classType,
        String algorithm) {
}
