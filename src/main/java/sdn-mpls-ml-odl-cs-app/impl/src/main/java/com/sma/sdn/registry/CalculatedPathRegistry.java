/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.registry;

import com.sma.sdn.model.CalculatedPath;
import com.sma.sdn.model.CalculatedPathKey;
import com.sma.sdn.observability.StructuredLogger;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Define la clase {@code CalculatedPathRegistry} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class CalculatedPathRegistry {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(CalculatedPathRegistry.class);
    private final Map<CalculatedPathKey, CalculatedPath> paths = new HashMap<>();

    /**
     * Ejecuta la operacion {@code findValid} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param key valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public synchronized Optional<CalculatedPath> findValid(final CalculatedPathKey key) {
        expireOldEntries();
        final Optional<CalculatedPath> result = Optional.ofNullable(paths.get(key));
        LOG.trace("calculated_path_registry_lookup", "findValid",
                "Se consulto el registro de caminos calculados",
                StructuredLogger.fields("cache_key", key, "found", result.isPresent(), "registry_size", paths.size()));
        return result;
    }

    /**
     * Inserta o reemplaza una entrada en el registro en memoria.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param key valor requerido para ejecutar esta operacion
     *
     * @param path valor requerido para ejecutar esta operacion
     */
    public synchronized void put(final CalculatedPathKey key, final CalculatedPath path) {
        final boolean replaced = paths.put(key, path) != null;
        LOG.debug("calculated_path_registry_updated", "put",
                "Se actualizo el registro de caminos calculados",
                StructuredLogger.fields("cache_key", key, "replaced", replaced,
                        "expires_at", path.expiresAt(), "registry_size", paths.size()));
    }

    /**
     * Ejecuta la operacion {@code expireOldEntries} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     */
    public synchronized void expireOldEntries() {
        final int previousSize = paths.size();
        final Instant now = Instant.now();
        final Iterator<Map.Entry<CalculatedPathKey, CalculatedPath>> iterator = paths.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAt().isBefore(now)) {
                iterator.remove();
            }
        }
        final int expiredCount = previousSize - paths.size();
        if (expiredCount > 0) {
            LOG.debug("calculated_path_registry_entries_expired", "expireOldEntries",
                    "Se eliminaron caminos calculados vencidos",
                    StructuredLogger.fields("expired_count", expiredCount, "registry_size", paths.size()));
        }
    }

    /**
     * Elimina todas las entradas mantenidas por el registro.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     */
    public synchronized void clear() {
        final int previousSize = paths.size();
        paths.clear();
        LOG.info("calculated_path_registry_cleared", "clear",
                "Se elimino el contenido del registro de caminos calculados",
                StructuredLogger.fields("removed_count", previousSize));
    }
}
