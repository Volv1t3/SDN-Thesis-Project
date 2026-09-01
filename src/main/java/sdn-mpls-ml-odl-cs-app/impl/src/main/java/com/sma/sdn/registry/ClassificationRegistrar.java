/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.registry;

import com.sma.sdn.metrics.SdnMplsMlMetrics;
import com.sma.sdn.model.ClassificationCacheKey;
import com.sma.sdn.model.ClassificationResult;
import com.sma.sdn.model.PacketClassificationContext;
import com.sma.sdn.model.PacketFeatures;
import com.sma.sdn.model.ServiceClassCacheKey;
import com.sma.sdn.observability.StructuredLogger;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Define la clase {@code ClassificationRegistrar} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class ClassificationRegistrar {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(ClassificationRegistrar.class);
    private final Map<String, Map<ClassificationCacheKey, ClassificationResult>> exactBySwitch = new HashMap<>();
    private final Map<String, Map<ServiceClassCacheKey, ClassificationResult>> serviceBySwitch = new HashMap<>();
    private final SdnMplsMlMetrics metrics;

    public ClassificationRegistrar() {
        this(null);
    }

    public ClassificationRegistrar(final SdnMplsMlMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Busca una entrada existente en el registro sin crear estado nuevo.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param context valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public synchronized Optional<ClassificationResult> find(final PacketClassificationContext context) {
        expireOldEntries();
        final String switchName = context.ingressSwitchName();
        final PacketFeatures features = context.packetFeatures();
        final ClassificationResult exact = exactBySwitch
                .getOrDefault(switchName, Map.of())
                .get(exactKey(context));
        if (exact != null) {
            LOG.trace("classification_registry_exact_hit", "find",
                    "Se encontro una clasificacion exacta en el registro",
                    StructuredLogger.fields("ingress_switch", switchName, "registry_size", size()));
            return Optional.of(exact);
        }
        final ClassificationResult service = serviceBySwitch
                .getOrDefault(switchName, Map.of())
                .get(serviceKey(switchName, features));
        LOG.trace("classification_registry_lookup_completed", "find",
                "Finalizo la consulta del registro de clasificaciones",
                StructuredLogger.fields("ingress_switch", switchName, "service_hit", service != null,
                        "registry_size", size()));
        return Optional.ofNullable(service);
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
     * @param context valor requerido para ejecutar esta operacion
     *
     * @param result valor requerido para ejecutar esta operacion
     */
    public synchronized void put(final PacketClassificationContext context, final ClassificationResult result) {
        final String switchName = context.ingressSwitchName();
        exactBySwitch.computeIfAbsent(switchName, key -> new HashMap<>()).put(exactKey(context), result);
        serviceBySwitch.computeIfAbsent(switchName, key -> new HashMap<>())
                .put(serviceKey(switchName, context.packetFeatures()), result);
        LOG.debug("classification_registry_updated", "put",
                "Se almacenaron las clasificaciones exacta y de servicio",
                StructuredLogger.fields("ingress_switch", switchName,
                        "ingress_connector", context.ingressConnectorName(), "expires_at", result.expiresAt(),
                        "registry_size", size()));
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
        final int previousSize = entryCount();
        final Instant now = Instant.now();
        final int exactExpired = expire(exactBySwitch, now);
        final int serviceExpired = expire(serviceBySwitch, now);
        recordEvictions("exact", exactExpired);
        recordEvictions("service", serviceExpired);
        final int expiredCount = previousSize - entryCount();
        if (expiredCount > 0) {
            LOG.debug("classification_registry_entries_expired", "expireOldEntries",
                    "Se eliminaron clasificaciones vencidas",
                    StructuredLogger.fields("expired_count", expiredCount, "registry_size", size()));
        }
    }

    /**
     * Ejecuta la operacion {@code size} dentro del componente correspondiente.
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
    public synchronized int size() {
        expireOldEntries();
        return entryCount();
    }

    /** Returns all unexpired exact-match entries as an immutable operational snapshot. */
    public synchronized Map<ClassificationCacheKey, ClassificationResult> exactSnapshot() {
        expireOldEntries();
        final Map<ClassificationCacheKey, ClassificationResult> snapshot = new HashMap<>();
        exactBySwitch.values().forEach(snapshot::putAll);
        return Map.copyOf(snapshot);
    }

    /** Returns all unexpired service-class entries as an immutable operational snapshot. */
    public synchronized Map<ServiceClassCacheKey, ClassificationResult> serviceSnapshot() {
        expireOldEntries();
        final Map<ServiceClassCacheKey, ClassificationResult> snapshot = new HashMap<>();
        serviceBySwitch.values().forEach(snapshot::putAll);
        return Map.copyOf(snapshot);
    }

    private int entryCount() {
        return exactBySwitch.values().stream().mapToInt(Map::size).sum()
                + serviceBySwitch.values().stream().mapToInt(Map::size).sum();
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
        final int previousSize = size();
        exactBySwitch.clear();
        serviceBySwitch.clear();
        LOG.info("classification_registry_cleared", "clear",
                "Se elimino el contenido del registro de clasificaciones",
                StructuredLogger.fields("removed_count", previousSize));
    }

    /**
     * Ejecuta la operacion {@code canonicalServicePort} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param features valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public static int canonicalServicePort(final PacketFeatures features) {
        if (features.dstPort() > 0 && features.dstPort() <= 1023) {
            return features.dstPort();
        }
        if (features.srcPort() > 0 && features.srcPort() <= 1023) {
            return features.srcPort();
        }
        return features.dstPort();
    }

    /**
     * Ejecuta la operacion {@code exactKey} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param context valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private static ClassificationCacheKey exactKey(final PacketClassificationContext context) {
        final PacketFeatures features = context.packetFeatures();
        return new ClassificationCacheKey(
                context.ingressSwitchName(),
                context.ingressConnectorName(),
                features.ethType(),
                features.ipProto(),
                features.srcPort(),
                features.dstPort());
    }

    /**
     * Ejecuta la operacion {@code serviceKey} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param switchName valor requerido para ejecutar esta operacion
     *
     * @param features valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private static ServiceClassCacheKey serviceKey(final String switchName, final PacketFeatures features) {
        return new ServiceClassCacheKey(
                switchName,
                features.ethType(),
                features.ipProto(),
                canonicalServicePort(features));
    }

    /**
     * Ejecuta la operacion {@code expire} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param registry valor requerido para ejecutar esta operacion
     *
     * @param now valor requerido para ejecutar esta operacion
     */
    private static <K> int expire(final Map<String, Map<K, ClassificationResult>> registry, final Instant now) {
        int expired = 0;
        final Iterator<Map.Entry<String, Map<K, ClassificationResult>>> switches = registry.entrySet().iterator();
        while (switches.hasNext()) {
            final Map<K, ClassificationResult> values = switches.next().getValue();
            final int previousSize = values.size();
            values.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
            expired += previousSize - values.size();
            if (values.isEmpty()) {
                switches.remove();
            }
        }
        return expired;
    }

    private void recordEvictions(final String cacheType, final int count) {
        if (metrics == null) {
            return;
        }
        for (int index = 0; index < count; index++) {
            metrics.incrementCounter("sma_registry_classification_expired_evictions_total",
                    Map.of("cache_type", cacheType));
        }
    }
}
