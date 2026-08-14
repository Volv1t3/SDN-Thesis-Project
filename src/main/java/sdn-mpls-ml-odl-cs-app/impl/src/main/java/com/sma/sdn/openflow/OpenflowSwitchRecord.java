/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package com.sma.sdn.openflow;

import java.util.Map;
import java.util.Objects;

/**
 * Conserva la identidad dinamica de un conmutador OVS y sus conectores indexados para consultas deterministas.
 *
 * @param logicalName nombre estable usado por la aplicacion
 * @param managementIp direccion de gestion usada para descubrir el nodo
 * @param nodeId identificador OpenFlow sin codificar
 * @param encodedNodeId identificador OpenFlow codificado para URI
 * @param connectorsByName conectores indexados por nombre OVS
 * @param connectorsById conectores indexados por identificador completo
 * @param connectorsByPortNumber conectores indexados por numero OpenFlow
 */
public record OpenflowSwitchRecord(
        String logicalName,
        String managementIp,
        String nodeId,
        String encodedNodeId,
        Map<String, OpenflowConnectorRecord> connectorsByName,
        Map<String, OpenflowConnectorRecord> connectorsById,
        Map<Integer, OpenflowConnectorRecord> connectorsByPortNumber) {

    /**
     * Valida la identidad del conmutador y convierte todos los indices en instantaneas inmutables.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Exige nombres, direcciones e identificadores no nulos.</li>
     *   <li>Copia cada indice para impedir modificaciones externas.</li>
     *   <li>Publica un registro coherente para el ciclo de bootstrap.</li>
     * </ol>
     *
     * @throws NullPointerException si falta un componente obligatorio
     */
    public OpenflowSwitchRecord {
        Objects.requireNonNull(logicalName, "logicalName");
        Objects.requireNonNull(managementIp, "managementIp");
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(encodedNodeId, "encodedNodeId");
        connectorsByName = Map.copyOf(connectorsByName);
        connectorsById = Map.copyOf(connectorsById);
        connectorsByPortNumber = Map.copyOf(connectorsByPortNumber);
    }
}
