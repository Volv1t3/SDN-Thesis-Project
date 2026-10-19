/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Groups the graph name, source and destination graph-node identifiers, requested bandwidth,
 * class type, and algorithm used for an OpenDaylight path-computation request. This record
 * carries request inputs; it does not itself compute a path or validate its components.</p>
 *
 * <p>{@link com.sma.sdn.path.PathComputationService} constructs the request after resolving
 * the graph endpoints and policy parameters. The
 * {@link com.sma.sdn.serialization.xml.PathComputationRequestXmlSerializer} validates and
 * serializes it for the path-computation operation.</p>
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
