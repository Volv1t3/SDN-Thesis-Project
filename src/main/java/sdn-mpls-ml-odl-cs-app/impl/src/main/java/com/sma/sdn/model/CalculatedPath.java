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

import java.time.Instant;
import java.util.List;

/**
 * <p>Represents a calculated path and its cache lifetime. The path computation service builds it from an OpenDaylight response, and the calculated-path registry retains it for reuse.</p>
 *
 * @param graphName path-computation graph name
 * @param sourceGraphNodeId source graph node identifier
 * @param destinationGraphNodeId destination graph node identifier
 * @param bandwidthBytesPerSecond requested bandwidth in bytes per second
 * @param classType path-computation class type
 * @param algorithm path-computation algorithm
 * @param pathHops path hops
 * @param eroSubobjects ero subobjects
 * @param computedTeMetric reported traffic-engineering metric
 * @param calculatedAt path calculation time
 * @param expiresAt expiration time
 */
public record CalculatedPath(
        String graphName, /*Most of the time this ODL_PATH_COMPUTATION_GRAPH_NAME is the same value as here in a single topology application*/
        long sourceGraphNodeId, /*equivalent to the NodeID in INT for 11.11.11.11 in the topology here*/
        long destinationGraphNodeId, /*same but for the tail end lsr4*/
        long bandwidthBytesPerSecond, /*converted bytes from the kbps that I send over to bps */
        int classType, /*integer 0-6 representing the clas*/
        String algorithm, /*most of the time CSPF*/
        List<PathHop> pathHops, /*Corresponding Path Hops that are direct translations from the  Output Object from the Controller
        The object returns a list of path-description elements which contain pairs of remove ipv4 addresses, the ones used to
        form the next hop chain for the ERO object, and ipv4 addresses that represent the pair address for the local interface
        as seen from the hop to the next. So for a link on subnet 10.0.30.n/30 if we are standing on 11.11.11.11 we would see
        the ipv4 address to be 10.0.31.1 and the remote to be 10.0.30.2 both /30 */
        List<EroSubobject> eroSubobjects, /*This is the translated version of the path hops determined by the calculation
        through the get-constrained-path endpoint and its result is stored as an ERO that can be reused in further LSP update
        processes. Each of these EroSubobjects contain directions on whether the object is loose or not and the ipprofexi that
        identifies the next hop interface where we have to arrive, so for our previous example it would be loose= false and
        ip-prefix = 10.0.30.2 for the second hop and finally loose=false ip-prefix= 14.14.14.14 for the last jump, indicating the
        router ID where we have to arrive at*/
        int computedTeMetric, /*Defaults to 1 as we are not managing this directly*/
        Instant calculatedAt,
        Instant expiresAt) {
}
