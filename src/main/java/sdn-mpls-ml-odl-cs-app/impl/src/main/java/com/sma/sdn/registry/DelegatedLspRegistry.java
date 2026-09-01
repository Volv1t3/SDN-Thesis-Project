/*
 * Copyright (c) 2026 Santiago Arellano and others.
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
 * Mantiene indices atomicos del ultimo estado conocido de todos los LSP delegados administrados por la aplicacion.
 */
public final class DelegatedLspRegistry {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(DelegatedLspRegistry.class);
    private final Map<String, DelegatedLspRecord> byDirectionKey = new HashMap<>();
    private final Map<String, DelegatedLspRecord> byLspName = new HashMap<>();
    private final Map<String, DelegatedLspRecord> byPccNodeAndName = new HashMap<>();

    /**
     * Sustituye atomica y completamente los indices con una instantanea validada de PCEP.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Construye indices temporales por direccion, nombre e identidad PCC.</li>
     *   <li>Rechaza claves duplicadas para evitar seleccionar un LSP ambiguo.</li>
     *   <li>Reemplaza los tres indices dentro de la misma seccion sincronizada.</li>
     * </ol>
     *
     * @param records registros descubiertos y asociados con su direccion
     * @throws IllegalArgumentException si una clave aparece mas de una vez
     */
    public synchronized void replaceAll(final Collection<DelegatedLspRecord> records) {
        final Map<String, DelegatedLspRecord> directions = new HashMap<>();
        final Map<String, DelegatedLspRecord> names = new HashMap<>();
        final Map<String, DelegatedLspRecord> pccNames = new HashMap<>();
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
     * Busca un LSP delegado por la clave logica de direccion.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Consulta el indice sincronizado por direccion.</li>
     *   <li>Expresa la ausencia mediante {@link Optional}.</li>
     * </ol>
     *
     * @param directionKey clave configurada de direccion
     * @return registro encontrado o un valor vacio
     */
    public synchronized Optional<DelegatedLspRecord> findByDirectionKey(final String directionKey) {
        final Optional<DelegatedLspRecord> record = Optional.ofNullable(byDirectionKey.get(directionKey));
        LOG.trace("delegated_lsp_registry_direction_lookup", "findByDirectionKey",
                "Se consulto un LSP delegado por direccion",
                StructuredLogger.fields("direction_key", directionKey, "found", record.isPresent()));
        return record;
    }

    /**
     * Busca un LSP delegado por su nombre simbolico reportado.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Consulta el indice sincronizado por nombre.</li>
     *   <li>Expresa la ausencia mediante {@link Optional}.</li>
     * </ol>
     *
     * @param lspName nombre simbolico del LSP
     * @return registro encontrado o un valor vacio
     */
    public synchronized Optional<DelegatedLspRecord> findByLspName(final String lspName) {
        final Optional<DelegatedLspRecord> record = Optional.ofNullable(byLspName.get(lspName));
        LOG.trace("delegated_lsp_registry_name_lookup", "findByLspName",
                "Se consulto un LSP delegado por nombre",
                StructuredLogger.fields("lsp_name", lspName, "found", record.isPresent()));
        return record;
    }

    /** Returns the number of currently discovered delegated LSPs. */
    public synchronized int size() {
        return byDirectionKey.size();
    }

    /**
     * Exige un LSP valido y actualizable para la direccion indicada.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Busca el registro por direccion.</li>
     *   <li>Verifica PLSP ID, delegacion, administracion y estado operativo.</li>
     *   <li>Devuelve el registro validado.</li>
     * </ol>
     *
     * @param directionKey clave configurada de direccion
     * @return LSP delegado valido
     * @throws IllegalStateException si falta el registro o su estado no permite actualizaciones
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
     * Actualiza el estado local confirmado despues de una operacion {@code update-lsp} exitosa.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Exige que la direccion ya exista en el registro.</li>
     *   <li>Crea una copia con la nueva ERO, ancho de banda y fecha de actualizacion.</li>
     *   <li>Reemplaza todos los indices mediante la operacion atomica principal.</li>
     * </ol>
     *
     * @param directionKey clave configurada de direccion
     * @param newEro ERO confirmada por PCEP
     * @param bandwidthBase64 ancho de banda confirmado por PCEP
     * @param updatedAt instante de confirmacion
     */
    public synchronized void updateAfterSuccessfulUpdate(
            final String directionKey,
            final List<EroSubobject> newEro,
            final String bandwidthBase64,
            final Instant updatedAt) {
        final DelegatedLspRecord current = requireByDirectionKey(directionKey);
        final DelegatedLspRecord replacement = new DelegatedLspRecord(
                current.directionKey(), current.pccNode(), current.lspName(), current.tunnelInterfaceName(),
                current.sourceRouterId(), current.destinationRouterId(), current.plspId(), current.tunnelId(),
                current.lspId(), current.delegated(), current.administrativeUp(), current.operationalState(),
                newEro, bandwidthBase64, current.discoveredAt(), updatedAt);
        final Map<String, DelegatedLspRecord> records = new HashMap<>(byDirectionKey);
        records.put(directionKey, replacement);
        replaceAll(records.values());
        LOG.info("delegated_lsp_registry_state_updated", "updateAfterSuccessfulUpdate",
                "Se actualizo el estado confirmado de un LSP delegado",
                StructuredLogger.fields("direction_key", directionKey, "lsp_name", replacement.lspName(),
                        "ero_subobject_count", newEro.size(), "updated_at", updatedAt));
    }

    private static String compositeKey(final String pccNode, final String lspName) {
        return pccNode + '\u0000' + lspName;
    }

    private static void putUnique(
            final Map<String, DelegatedLspRecord> map,
            final String key,
            final DelegatedLspRecord record) {
        if (map.putIfAbsent(key, record) != null) {
            throw new IllegalArgumentException("La clave del registro de LSP delegados esta duplicada: " + key);
        }
    }
}
