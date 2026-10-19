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

import com.sma.sdn.model.DelegatedLspRecord;
import com.sma.sdn.model.EroSubobject;
import com.sma.sdn.observability.StructuredLogger;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * <p>Maintains synchronized indexes of the latest delegated PCEP LSP records by direction, LSP
 * name, and PCC/name identity. Delegated LSP services supply reported state and resolve
 * updateable records; replacement validates temporary indexes before changing shared state.</p>
 *
 * <p>{@link com.sma.sdn.tunnel.DelegatedLspService} converts reported PCEP snapshots into these
 * records after matching and validating both configured directions. A lookup returns the last
 * stored view; a fresh PCEP read is performed by the service, not by this registry.</p>
 */
public final class DelegatedLspRegistry {
    /** <p>Logger for LSP registry updates and lookups.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(DelegatedLspRegistry.class);
    /** <p>LSP records indexed by configured direction.</p> */
    private final Map<String, DelegatedLspRecord> byDirectionKey = new HashMap<>();
    /** <p>LSP records indexed by reported symbolic name.</p> */
    private final Map<String, DelegatedLspRecord> byLspName = new HashMap<>();
    /** <p>LSP records indexed by combined PCC node and LSP name.</p> */
    private final Map<String, DelegatedLspRecord> byPccNodeAndName = new HashMap<>();

    /**
     * <p>Replace all LSP indexes with a validated snapshot.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Build temporary direction, name, and PCC/name indexes, rejecting duplicate keys.</li>
     *   <li>Replace all three indexes after validation and log the resulting records.</li>
     * </ol>
     *
     * @param records discovered LSP records to index
     * @throws IllegalArgumentException if an index key is duplicated
     */
    public synchronized void replaceAll(final Collection<DelegatedLspRecord> records) {
        // Construct all indexes first so a duplicate cannot leave the existing snapshot partially replaced.
        final Map<String, DelegatedLspRecord> directions = new HashMap<>();
        final Map<String, DelegatedLspRecord> names = new HashMap<>();
        final Map<String, DelegatedLspRecord> pccNames = new HashMap<>();
        /*This sections perform a filter upon the records based on the record direction, lsp name and its pccNode and lspName
        * which are the three representations that are maintained in the regfistry*/
        for (DelegatedLspRecord record : records) {
            putUnique(directions, record.directionKey(), record);
            putUnique(names, record.lspName(), record);
            putUnique(pccNames, compositeKey(record.pccNode(), record.lspName()), record);
        }
        byDirectionKey.clear();
        byDirectionKey.putAll(directions);
        byLspName.clear();
        byLspName.putAll(names);
        byPccNodeAndName.clear();
        byPccNodeAndName.putAll(pccNames);
        LOG.info("delegated_lsp_registry_replaced", "replaceAll",
                "El registro de LSP delegados fue reemplazado de forma atomica",
                StructuredLogger.fields("registered_count", byDirectionKey.size(),
                        "direction_keys", byDirectionKey.keySet(), "lsp_names", byLspName.keySet()));
    }

    /**
     * <p>Find an LSP by its configured direction.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Look up the direction index.</li>
     *   <li>Log whether a record was found and return an optional.</li>
     * </ol>
     *
     * @param directionKey configured direction identifier
     * @return matching record or an empty optional
     */
    public synchronized Optional<DelegatedLspRecord> findByDirectionKey(final String directionKey) {
        final Optional<DelegatedLspRecord> record = Optional.ofNullable(byDirectionKey.get(directionKey));
        LOG.trace("delegated_lsp_registry_direction_lookup", "findByDirectionKey",
                "Se consulto un LSP delegado por direccion",
                StructuredLogger.fields("direction_key", directionKey, "found", record.isPresent()));
        return record;
    }

    /**
     * <p>Find an LSP by its reported symbolic name.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Look up the name index.</li>
     *   <li>Log whether a record was found and return an optional.</li>
     * </ol>
     *
     * @param lspName reported LSP name
     * @return matching record or an empty optional
     */
    public synchronized Optional<DelegatedLspRecord> findByLspName(final String lspName) {
        final Optional<DelegatedLspRecord> record = Optional.ofNullable(byLspName.get(lspName));
        LOG.trace("delegated_lsp_registry_name_lookup", "findByLspName",
                "Se consulto un LSP delegado por nombre",
                StructuredLogger.fields("lsp_name", lspName, "found", record.isPresent()));
        return record;
    }

    /**
     * <p>Count the currently indexed directions.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Return the size of the synchronized direction index.</li>
     * </ol>
     *
     * @return number of indexed directions
     */
    public synchronized int size() {
        return byDirectionKey.size();
    }

    /**
     * <p>Return an immutable snapshot of the latest LSP records.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Copy the direction index into an immutable map.</li>
     *   <li>Return the independent snapshot.</li>
     * </ol>
     *
     * @return direction-keyed LSP snapshot
     */
    public synchronized Map<String, DelegatedLspRecord> snapshotByDirection() {
        return Map.copyOf(byDirectionKey);
    }

    /**
     * <p>Resolve a direction to an LSP record that is valid for update.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Look up the direction and reject an absent record.</li>
     *   <li>Check {@link DelegatedLspRecord#isValidForUpdate()} and reject an invalid record.</li>
     *   <li>Return the validated record.</li>
     * </ol>
     *
     * @param directionKey configured direction identifier
     * @return updateable LSP record
     * @throws IllegalStateException if the record is absent or invalid for update
     */
    public synchronized DelegatedLspRecord requireByDirectionKey(final String directionKey) {
        final DelegatedLspRecord record = byDirectionKey.get(directionKey);
        if (record == null) {
            throw new IllegalStateException("No existe un LSP delegado registrado para la direccion " + directionKey);
        }
        if (!record.isValidForUpdate()) {
            throw new IllegalStateException("El LSP delegado no puede actualizarse para la direccion " + directionKey
                    + ": plspId=" + record.plspId() + ", delegado=" + record.delegated()
                    + ", administrativo=" + record.administrativeUp()
                    + ", operativo=" + record.operationalState());
        }
        return record;
    }

    /**
     * <p>Update the locally stored route, bandwidth, and timestamp for a direction after an LSP update.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Require an existing updateable record.</li>
     *   <li>Construct a replacement retaining its identity and other reported fields.</li>
     *   <li>Replace the direction in a copied map, rebuild all indexes, and log the result.</li>
     * </ol>
     *
     * @param directionKey       configured direction identifier
     * @param newEro             replacement explicit route subobjects
     * @param newBandwidthBase64 replacement PCEP bandwidth encoding
     * @param updatedAt          time recorded for the update
     * @throws IllegalStateException    if the current record is absent or invalid for update
     * @throws IllegalArgumentException if rebuilding indexes encounters duplicate keys
     */
    public synchronized void updateAfterSuccessfulUpdate(
            final String directionKey,
            final List<EroSubobject> newEro,
            final String newBandwidthBase64,
            final Instant updatedAt) {
        final DelegatedLspRecord current = requireByDirectionKey(directionKey);
        // Preserve the reported identity and state while replacing the locally recorded update fields.
        final DelegatedLspRecord replacement = new DelegatedLspRecord(
                current.directionKey(), current.pccNode(),
                current.lspName(), current.tunnelInterfaceName(),
                current.sourceRouterId(), current.destinationRouterId(),
                current.plspId(), current.tunnelId(),
                current.lspId(), current.delegated(),
                current.administrativeUp(), current.operationalState(),
                newEro, newBandwidthBase64, current.discoveredAt(), updatedAt);
        final Map<String, DelegatedLspRecord> records = new HashMap<>(byDirectionKey);
        records.put(directionKey, replacement);
        replaceAll(records.values());
        LOG.info("delegated_lsp_registry_state_updated", "updateAfterSuccessfulUpdate",
                "Se actualizo el estado confirmado de un LSP delegado",
                StructuredLogger.fields("direction_key", directionKey, "lsp_name", replacement.lspName(),
                        "ero_subobject_count", newEro.size(), "updated_at", updatedAt));
    }

    /**
     * <p>Construct the internal index key for a PCC and LSP name.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Join the PCC and name with a NUL character separator.</li>
     *   <li>Return the resulting composite key.</li>
     * </ol>
     *
     * @param pccNode PCC node identifier
     * @param lspName LSP symbolic name
     * @return composite index key
     */
    private static String compositeKey(final String pccNode, final String lspName) {
        return pccNode + '\u0000' + lspName;
    }

    /**
     * <p>Insert a record into a temporary index and reject duplicate keys.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Attempt to insert the record only when the key is absent.</li>
     *   <li>Throw an exception if an existing record prevented insertion.</li>
     * </ol>
     *
     * @param map temporary index to populate
     * @param key index key
     * @param record record to insert
     * @throws IllegalArgumentException if the key already exists
     */
    private static void putUnique(
            final Map<String, DelegatedLspRecord> map,
            final String key,
            final DelegatedLspRecord record) {
        if (map.putIfAbsent(key, record) != null) {
            throw new IllegalArgumentException("La clave del registro de LSP delegados esta duplicada: " + key);
        }
    }
}
