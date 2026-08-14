/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package com.sma.sdn.openflow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Publica una instantanea atomica de conmutadores OpenFlow mediante sus identidades logicas y dinamicas.
 */
public final class OpenflowSwitchRegistry {
    private volatile Snapshot snapshot = Snapshot.empty();

    /**
     * Sustituye atomicamente el contenido del registro con una nueva instantanea validada.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Construye indices temporales por nombre, IP, nodo y conector.</li>
     *   <li>Rechaza cualquier identidad duplicada.</li>
     *   <li>Publica todos los indices mediante una sola escritura volatil.</li>
     * </ol>
     *
     * @param switches conmutadores descubiertos
     * @throws IllegalArgumentException si existe una identidad duplicada
     */
    public void replace(final List<OpenflowSwitchRecord> switches) {
        final Map<String, OpenflowSwitchRecord> byLogicalName = new HashMap<>();
        final Map<String, OpenflowSwitchRecord> byManagementIp = new HashMap<>();
        final Map<String, OpenflowSwitchRecord> byNodeId = new HashMap<>();
        final Map<String, OpenflowConnectorRecord> connectorsById = new HashMap<>();
        for (OpenflowSwitchRecord switchRecord : switches) {
            unique(byLogicalName, switchRecord.logicalName(), switchRecord);
            unique(byManagementIp, switchRecord.managementIp(), switchRecord);
            unique(byNodeId, switchRecord.nodeId(), switchRecord);
            switchRecord.connectorsById().forEach((connectorId, connector) -> {
                if (connectorsById.putIfAbsent(connectorId, connector) != null) {
                    throw new IllegalArgumentException("Identificador de conector OpenFlow duplicado: " + connectorId);
                }
            });
        }
        snapshot = new Snapshot(
                Map.copyOf(byLogicalName),
                Map.copyOf(byManagementIp),
                Map.copyOf(byNodeId),
                Map.copyOf(connectorsById));
    }

    /** @return conmutador logico ECHO */
    public OpenflowSwitchRecord getEcho() {
        return requireByLogicalName("ECHO");
    }

    /** @return conmutador logico FOXTROT */
    public OpenflowSwitchRecord getFoxtrot() {
        return requireByLogicalName("FOXTROT");
    }

    /**
     * Busca un conmutador por el identificador dinamico informado por ODL.
     *
     * @param nodeId identificador OpenFlow sin codificar
     * @return conmutador asociado, si existe
     */
    public Optional<OpenflowSwitchRecord> findByNodeId(final String nodeId) {
        return Optional.ofNullable(snapshot.byNodeId().get(nodeId));
    }

    /**
     * Busca un conector por nombre dentro de un nodo dinamico especifico.
     *
     * @param nodeId identificador OpenFlow sin codificar
     * @param connectorName nombre OVS del conector
     * @return conector asociado, si existe
     */
    public Optional<OpenflowConnectorRecord> findConnector(
            final String nodeId, final String connectorName) {
        return findByNodeId(nodeId).map(value -> value.connectorsByName().get(connectorName));
    }

    private OpenflowSwitchRecord requireByLogicalName(final String logicalName) {
        final OpenflowSwitchRecord value = snapshot.byLogicalName().get(logicalName);
        if (value == null) {
            throw new IllegalStateException("El conmutador OpenFlow " + logicalName + " no esta registrado");
        }
        return value;
    }

    private static <K> void unique(
            final Map<K, OpenflowSwitchRecord> index, final K key, final OpenflowSwitchRecord value) {
        if (index.putIfAbsent(key, value) != null) {
            throw new IllegalArgumentException("Identidad de conmutador OpenFlow duplicada: " + key);
        }
    }

    private record Snapshot(
            Map<String, OpenflowSwitchRecord> byLogicalName,
            Map<String, OpenflowSwitchRecord> byManagementIp,
            Map<String, OpenflowSwitchRecord> byNodeId,
            Map<String, OpenflowConnectorRecord> connectorsById) {
        private static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of(), Map.of(), Map.of());
        }
    }
}
