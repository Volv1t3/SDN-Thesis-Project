/*
 * Copyright (c) 2026 Santiago Arellano and others.
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
 * Define la clase {@code BgpLsNodeRegistry} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class BgpLsNodeRegistry {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(BgpLsNodeRegistry.class);
    private final Map<String, BgpLsTopologyNode> byRouterId = new HashMap<>();

    /**
     * Ejecuta la operacion {@code replaceAll} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param nodes valor requerido para ejecutar esta operacion
     */
    public synchronized void replaceAll(final Collection<BgpLsTopologyNode> nodes) {
        final Map<String, BgpLsTopologyNode> replacement = new HashMap<>();
        for (BgpLsTopologyNode node : nodes) {
            final BgpLsTopologyNode existing = replacement.get(node.routerId());
            if (existing != null && existing.graphNodeId() != node.graphNodeId()) {
                throw new IllegalStateException("Existen identificadores de nodo de grafo incompatibles para el "
                        + "identificador de enrutador " + node.routerId());
            }
            replacement.putIfAbsent(node.routerId(), node);
        }
        byRouterId.clear();
        byRouterId.putAll(replacement);
        LOG.info("bgp_ls_registry_replaced", "replaceAll",
                "El registro de nodos BGP-LS fue reemplazado",
                StructuredLogger.fields("input_count", nodes.size(), "registered_count", byRouterId.size()));
    }

    /**
     * Ejecuta la operacion {@code containsRouterId} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param routerId valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public synchronized boolean containsRouterId(final String routerId) {
        final boolean present = byRouterId.containsKey(routerId);
        LOG.trace("bgp_ls_registry_lookup", "containsRouterId",
                "Se consulto la existencia de un identificador de enrutador BGP-LS",
                StructuredLogger.fields("router_id", routerId, "present", present));
        return present;
    }

    /**
     * Ejecuta la operacion {@code requireByRouterId} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param routerId valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
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
     * Ejecuta la operacion {@code resolveGraphNodeIdByRouterId} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param routerId valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    public synchronized long resolveGraphNodeIdByRouterId(final String routerId) {
        return requireByRouterId(routerId).graphNodeId();
    }

    /**
     * Ejecuta la operacion {@code snapshot} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public synchronized Map<String, BgpLsTopologyNode> snapshot() {
        return Map.copyOf(byRouterId);
    }
}
