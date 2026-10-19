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

package com.sma.sdn.registry;

import com.sma.sdn.model.BgpLsTopologyNode;
import com.sma.sdn.observability.StructuredLogger;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>Maintains a synchronized router-ID index of the latest BGP-LS topology nodes for discovery
 * and path computation. Replacement validates a temporary index before modifying the current
 * snapshot.</p>
 */
public final class BgpLsNodeRegistry {
    /** <p>Logger for registry updates and lookups.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(BgpLsNodeRegistry.class);
    /** <p>Current BGP-LS nodes indexed by router ID.</p> */
    private final Map<String, BgpLsTopologyNode> byRouterId = new HashMap<>();

    /**
     * <p>Replace the topology snapshot while detecting inconsistent graph node IDs.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Build a temporary index, retaining the first node for each router ID.</li>
     *   <li>Reject a repeated router ID with a different graph ID before changing the current index.</li>
     *   <li>Replace the index and log the input and retained counts.</li>
     * </ol>
     *
     * @param nodes nodes to index
     * @throws IllegalStateException if the same router ID has conflicting graph IDs
     */
    public synchronized void replaceAll(final Collection<BgpLsTopologyNode> nodes) {
        // Validate the complete replacement before exposing any part of it to readers.
        final Map<String, BgpLsTopologyNode> replacement = new HashMap<>();
        for (BgpLsTopologyNode node : nodes) {
            /*On the first iteration of every node that has not been processed this will return null and
            * allo for processing and replacement, for any that ha salready been seen with no issues the
            * putIfAbset will do nothing. The only possible issue that we culd have is if the node's graph Node ID changed
            * from a previous collection, perhaps due to a change in the topology*/
            final BgpLsTopologyNode existing = replacement.get(node.routerId());
            if (existing != null && existing.graphNodeId() != node.graphNodeId()) {
                throw new IllegalStateException("Existen identificadores de nodo de grafo incompatibles para el "
                        + "identificador de enrutador " + node.routerId());
            }
            replacement.putIfAbsent(node.routerId(), node);
        }

        /*
         * Here we are looking to remove the content of the router ID hash map and replace its contents with the
         * newly read list of incoming nodes
        */
        byRouterId.clear();
        byRouterId.putAll(replacement);
        LOG.info("bgp_ls_registry_replaced", "replaceAll",
                "El registro de nodos BGP-LS fue reemplazado",
                StructuredLogger.fields("input_count", nodes.size(), "registered_count", byRouterId.size()));
    }

    /**
     * <p>Check whether a router ID is present in the current snapshot.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Look up the ID in the synchronized index.</li>
     *   <li>Log and return the presence result.</li>
     * </ol>
     *
     * @param routerId router ID to check
     * @return whether the ID is indexed
     */
    public synchronized boolean containsRouterId(final String routerId) {
        final boolean present = byRouterId.containsKey(routerId);
        LOG.trace("bgp_ls_registry_lookup", "containsRouterId",
                "Se consulto la existencia de un identificador de enrutador BGP-LS",
                StructuredLogger.fields("router_id", routerId, "present", present));
        return present;
    }

    /**
     * <p>Resolve a router ID to a topology node.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Read the index and reject an absent node.</li>
     *   <li>Log and return the resolved node.</li>
     * </ol>
     *
     * @param routerId router ID to resolve
     * @return indexed topology node
     * @throws IllegalStateException if the ID is absent
     */
    public synchronized BgpLsTopologyNode requireByRouterId(final String routerId) {
        final BgpLsTopologyNode node = byRouterId.get(routerId);
        if (node == null) {
            throw new IllegalStateException("No se encontro el identificador de enrutador BGP-LS: " + routerId);
        }
        LOG.trace("bgp_ls_registry_entry_resolved", "requireByRouterId",
                "Se resolvio un nodo BGP-LS desde el registro",
                StructuredLogger.fields("router_id", routerId, "graph_node_id", node.graphNodeId()));
        return node;
    }

    /**
     * <p>Resolve a router ID to its graph node identifier for path computation.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Require the topology node for the router.</li>
     *   <li>Return its graph node identifier.</li>
     * </ol>
     *
     * @param routerId router ID to resolve
     * @return graph node ID
     * @throws IllegalStateException if the ID is absent
     */
    public synchronized long resolveGraphNodeIdByRouterId(final String routerId) {
        return requireByRouterId(routerId).graphNodeId();
    }

    /**
     * <p>Return an immutable snapshot of the router ID index.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Copy the current index using {@link Map#copyOf(Map)}.</li>
     *   <li>Return the independent snapshot.</li>
     * </ol>
     *
     * @return immutable router ID to node mapping
     */
    public synchronized Map<String, BgpLsTopologyNode> snapshot() {
        return Map.copyOf(byRouterId);
    }
}
