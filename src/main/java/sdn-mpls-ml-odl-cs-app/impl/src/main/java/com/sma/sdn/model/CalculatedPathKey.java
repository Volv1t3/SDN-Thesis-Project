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
 * <p>Identifies a cached path by endpoints, bandwidth, class type, and algorithm. The path computation service uses this key to query the calculated-path registry.</p>
 *
 * @param sourceGraphNodeId source graph node identifier
 * @param destinationGraphNodeId destination graph node identifier
 * @param bandwidthBytesPerSecond requested bandwidth in bytes per second
 * @param classType path-computation class type
 * @param algorithm path-computation algorithm
 */
public record CalculatedPathKey(
        long sourceGraphNodeId, /*Graph vertex ID for the source node*/
        long destinationGraphNodeId, /*Graph vertex ID for the destination node*/
        long bandwidthBytesPerSecond, /*Bandwidth in bytes per second instead of the kbps that is returned from the
        classification service*/
        int classType, /*path computation class type for default path computation*/
        String algorithm /*CSPF default algorithm*/) {
}
