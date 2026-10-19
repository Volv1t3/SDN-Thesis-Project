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
package com.sma.sdn.openflow;

import java.util.Map;
import java.util.Objects;

/**
 * <p>Represents a discovered switch and immutable connector indexes by name, identifier
 * and port number. {@link OpenflowSwitchRegistry} publishes these records for bootstrap
 * and packet-ingress resolution.</p>
 *
 * @param logicalName stable application switch name
 * @param managementIp address used to discover the switch
 * @param nodeId unencoded OpenFlow node identifier
 * @param encodedNodeId node identifier encoded for use in a URI
 * @param connectorsByName connectors indexed by OVS name
 * @param connectorsById connectors indexed by full identifier
 * @param connectorsByPortNumber connectors indexed by OpenFlow port number
 */
public record OpenflowSwitchRecord(
        String logicalName,
        String managementIp,
        String nodeId,
        String encodedNodeId,
        Map<String, OpenflowConnectorRecord> connectorsByName, /*map of the internal associations of a connect
        by its name to its data*/
        Map<String, OpenflowConnectorRecord> connectorsById, /*map of the connectors assocition by its ID to a record*/
        Map<Integer, OpenflowConnectorRecord> connectorsByPortNumber /*similar association by port number, i.e port 1 or 2 on a switch to the corresponding
        reecord*/) {

    /**
     * <p>Validates identity components and defensively copies each connector index.</p>
     *
     * <p>The constructor prepares the snapshot as follows:</p>
     * <ol>
     *   <li>Require non-null switch names, addresses and node identifiers.</li>
     *   <li>Copy each index into an unmodifiable map, rejecting null entries.</li>
     * </ol>
     *
     * @param logicalName stable application switch name
     * @param managementIp management address
     * @param nodeId unencoded OpenFlow node identifier
     * @param encodedNodeId URI-encoded node identifier
     * @param connectorsByName connectors keyed by OVS name
     * @param connectorsById connectors keyed by full identifier
     * @param connectorsByPortNumber connectors keyed by port number
     * @throws NullPointerException if a required component or map entry is null
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
