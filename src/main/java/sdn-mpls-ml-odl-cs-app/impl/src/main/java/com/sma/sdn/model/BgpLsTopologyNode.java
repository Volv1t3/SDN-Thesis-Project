/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Represents a discovered BGP-LS node and its router and graph identifiers. Topology discovery and refresh populate the BGP-LS registry with these nodes for path computation.</p>
 *
 * @param topologyId source topology identifier
 * @param nodeId node identifier
 * @param routerId router identifier
 * @param teRouterIdIpv4 traffic-engineering IPv4 router identifier
 * @param graphNodeId graph node identifier
 */
public record BgpLsTopologyNode(
        String topologyId, /*ID of the topology where this came from, the same for this implementation*/
        String nodeId, /*The concrete concatenation of all identifiers for a node as returned by ODL*/
        String routerId, /*The router ID as reported through BGPLS to create the topology based on the device's IGP configuration*/
        String teRouterIdIpv4, /* The configured TE router ID associted with the MPLS TE configuration done at the
        router, effectively determining the ID that will be used for the advertisements of its TED*/
        long graphNodeId /*The graph vertex ID coming from the node ID useful for path calculations*/) {
}
