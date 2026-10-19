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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * <p>Publishes a single immutable snapshot of discovered switches and connector indexes.
 * Inventory discovery replaces the snapshot, while bootstrap and packet handling resolve
 * stable names and dynamic OpenFlow identifiers through it.</p>
 */
public final class OpenflowSwitchRegistry {
    /** <p>Current complete set of indexes, atomically published to readers.</p> */
    private volatile Snapshot snapshot = Snapshot.empty();

    /**
     * <p>Atomically replaces all switch and connector indexes after duplicate checks.
     * A failure during construction leaves the previously published snapshot unchanged.</p>
     *
     * <p>The replacement proceeds as follows:</p>
     * <ol>
     *   <li>Build indexes by logical name, management IP, node ID and connector ID.</li>
     *   <li>Reject duplicate identities while building the temporary indexes.</li>
     *   <li>Copy the indexes and publish the snapshot with one volatile assignment.</li>
     * </ol>
     *
     * @param switches discovered switch records
     * @throws IllegalArgumentException if a switch identity or connector ID is duplicated
     */
    public void replace(final List<OpenflowSwitchRecord> switches) {
        /*As with toher operations, this attempts to modify the entirety of the views of data held within the snapshost
        * of t he openflow topology, making it such that in the event of receivinge new records only unique versions
        * processed from the incoming switches come in*/
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
        /*The content of this*/
        snapshot = new Snapshot(
                Map.copyOf(byLogicalName),
                Map.copyOf(byManagementIp),
                Map.copyOf(byNodeId),
                Map.copyOf(connectorsById));
    }

    /**
     * <p>Retrieves the switch registered under the logical name ECHO.</p>
     * <p>The lookup has one step:</p>
     * <ol><li>Delegate the required lookup to the current snapshot.</li></ol>
     * @return the ECHO switch
     * @throws IllegalStateException if ECHO is not registered
     */
    public OpenflowSwitchRecord getEcho() {
        return requireByLogicalName("ECHO");
    }

    /**
     * <p>Retrieves the switch registered under the logical name FOXTROT.</p>
     * <p>The lookup has one step:</p>
     * <ol><li>Delegate the required lookup to the current snapshot.</li></ol>
     * @return the FOXTROT switch
     * @throws IllegalStateException if FOXTROT is not registered
     */
    public OpenflowSwitchRecord getFoxtrot() {
        return requireByLogicalName("FOXTROT");
    }

    /**
     * <p>Finds a switch by its dynamic OpenFlow node identifier.</p>
     * <p>The lookup has one step:</p>
     * <ol><li>Read the current node index and wrap the matching record, if present.</li></ol>
     *
     * @param nodeId unencoded OpenFlow node identifier
     * @return matching switch, or an empty optional
     */
    public Optional<OpenflowSwitchRecord> findByNodeId(final String nodeId) {
        return Optional.ofNullable(snapshot.byNodeId().get(nodeId));
    }

    /**
     * <p>Finds a named connector belonging to a specific OpenFlow node.</p>
     * <p>The lookup proceeds as follows:</p>
     * <ol><li>Find the switch by node ID.</li>
     * <li>Look up its connector by OVS name, returning empty if either is absent.</li></ol>
     *
     * @param nodeId unencoded OpenFlow node identifier
     * @param connectorName OVS connector name
     * @return matching connector, or an empty optional
     */
    public Optional<OpenflowConnectorRecord> findConnector(
            final String nodeId, final String connectorName) {
        return findByNodeId(nodeId).map(value -> value.connectorsByName().get(connectorName));
    }

    /**
     * <p>Finds a connector by the full identifier reported in a PacketIn notification.</p>
     *
     * <p>The lookup proceeds as follows:</p>
     * <ol>
     *   <li>Resolve the node ID to a switch.</li>
     *   <li>Look up the connector within that switch, returning empty if either is absent.</li>
     * </ol>
     *
     * @param nodeId OpenFlow node identifier
     * @param connectorId full connector identifier
     * @return matching connector, or an empty optional
     */
    public Optional<OpenflowConnectorRecord> findConnectorById(
            final String nodeId, final String connectorId) {
        return findByNodeId(nodeId).map(value -> value.connectorsById().get(connectorId));
    }

    /**
     * <p>Reports the number of switches in the current snapshot.</p>
     * <p>The count is obtained as follows:</p>
     * <ol><li>Return the size of the node identifier index.</li></ol>
     * @return number of registered switches
     */
    public int size() {
        return snapshot.byNodeId().size();
    }

    /**
     * <p>Copies the current logical-name index for operational state exposure.</p>
     * <p>The copy is constructed as follows:</p>
     * <ol><li>Create a mutable linked map containing the published entries.</li></ol>
     * @return a detached map of logical names to switches
     */
    public Map<String, OpenflowSwitchRecord> snapshotByLogicalName() {
        return new LinkedHashMap<>(snapshot.byLogicalName());
    }

    /**
     * <p>Retrieves a required switch from the logical-name index.</p>
     * <p>The lookup proceeds as follows:</p>
     * <ol><li>Read the current index and reject a missing entry.</li>
     * <li>Return the matching record.</li></ol>
     * @param logicalName application switch name
     * @return matching switch
     * @throws IllegalStateException if the switch is not registered
     */
    private OpenflowSwitchRecord requireByLogicalName(final String logicalName) {
        final OpenflowSwitchRecord value = snapshot.byLogicalName().get(logicalName);
        if (value == null) {
            throw new IllegalStateException("El conmutador OpenFlow " + logicalName + " no esta registrado");
        }
        return value;
    }

    /**
     * <p>Inserts a switch into a temporary index while rejecting duplicate keys.</p>
     * <p>The insertion has one step:</p>
     * <ol><li>Use {@code putIfAbsent} and fail if the key already exists.</li></ol>
     * @param <K> type of index key
     * @param index index being assembled
     * @param key identity to insert
     * @param value switch associated with the identity
     * @throws IllegalArgumentException if the key is already present
     */
    private static <K> void unique(
            final Map<K, OpenflowSwitchRecord> index, final K key, final OpenflowSwitchRecord value) {
        if (index.putIfAbsent(key, value) != null) {
            throw new IllegalArgumentException("Identidad de conmutador OpenFlow duplicada: " + key);
        }
    }

    /**
     * <p>Groups immutable indexes so replacement publishes them together.</p>
     * @param byLogicalName switches keyed by application name
     * @param byManagementIp switches keyed by management address
     * @param byNodeId switches keyed by OpenFlow node ID
     * @param connectorsById connectors keyed by full identifier
     */
    private record Snapshot(
            Map<String, OpenflowSwitchRecord> byLogicalName,
            Map<String, OpenflowSwitchRecord> byManagementIp,
            Map<String, OpenflowSwitchRecord> byNodeId,
            Map<String, OpenflowConnectorRecord> connectorsById) {
        /**
         * <p>Creates the empty initial registry snapshot.</p>
         * <p>The initialization has one step:</p>
         * <ol><li>Construct a snapshot with four empty immutable maps.</li></ol>
         * @return empty snapshot
         */
        private static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of(), Map.of(), Map.of());
        }
    }
}
