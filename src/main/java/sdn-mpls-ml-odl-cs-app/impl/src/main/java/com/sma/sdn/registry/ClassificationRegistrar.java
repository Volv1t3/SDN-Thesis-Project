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
 * <p>Maintains the controller's in-memory classification cache in two synchronized views. The exact view keys a
 * result by ingress switch, ingress connector, EtherType, IP protocol, and both transport ports; the service view
 * omits the connector and reduces the transport ports to a canonical service port. Each stored classification is
 * therefore available for a precise flow lookup and for reuse by later packets in the same service class.</p>
 *
 * <p>{@link com.sma.sdn.classification.ClassificationService} consults and updates this registrar, while
 * {@link com.sma.sdn.operational.ControllerOperationalStatePublisher} obtains immutable snapshots for the controller's
 * operational datastore. Public state operations are synchronized, remove expired results before exposing them, and
 * optionally report each expired exact or service entry through {@link SdnMplsMlMetrics}.</p>
 */
public final class ClassificationRegistrar {
    /**
     * <p>Writes cache lookup, update, expiration, and clear events under the registrar's class name.</p>
     */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(ClassificationRegistrar.class);

    /**
     * <p>Groups exact-flow classifications by ingress switch before indexing them with the full packet cache key.</p>
     */
    private final Map<String, Map<ClassificationCacheKey, ClassificationResult>> exactBySwitch = new HashMap<>();

    /**
     * <p>Groups reusable service-class classifications by ingress switch and canonical service key.</p>
     */
    private final Map<String, Map<ServiceClassCacheKey, ClassificationResult>> serviceBySwitch = new HashMap<>();

    /**
     * <p>Receives per-entry expiration counters, or is {@code null} when metrics are intentionally disabled.</p>
     */
    private final SdnMplsMlMetrics metrics;

    /**
     * <p>Creates an empty registrar without expiration metrics, as used by callers that do not supply a collector.</p>
     *
     * <p>The constructor performs these steps:</p>
     * <ol>
     *   <li>Delegates to {@link #ClassificationRegistrar(SdnMplsMlMetrics)} with a {@code null} collector.</li>
     *   <li>Leaves both eagerly created cache views empty.</li>
     * </ol>
     *
     * <p>Expiration still removes and logs stale entries; only the metric increments are omitted.</p>
     */
    public ClassificationRegistrar() {
        this(null);
    }

    /**
     * <p>Creates an empty registrar that uses the supplied metrics collector when entries expire.</p>
     *
     * <p>The constructor performs these steps:</p>
     * <ol>
     *   <li>Retains the optional {@link SdnMplsMlMetrics} reference.</li>
     *   <li>Leaves the exact-flow and service-class maps ready to receive classifications.</li>
     * </ol>
     *
     * <p>The production provider supplies its shared metrics collector; tests may pass {@code null} or use the
     * no-argument constructor.</p>
     *
     * @param metrics collector for expiration-eviction counters, or {@code null} to disable those counters
     */
    public ClassificationRegistrar(final SdnMplsMlMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * <p>Returns an unexpired classification for the packet context, preferring the exact-flow view before falling
     * back to the broader service-class view. The lookup may remove expired entries but does not create cache entries.</p>
     *
     * <p>The lookup performs these steps:</p>
     * <ol>
     *   <li>Calls {@link #expireOldEntries()} so neither cache view can return a result whose expiry precedes the
     *       current expiration sweep.</li>
     *   <li>Builds a {@link ClassificationCacheKey} from the ingress switch, ingress connector, and packet features,
     *       then returns that exact result when present.</li>
     *   <li>If the exact lookup misses, builds a {@link ServiceClassCacheKey} from the ingress switch, EtherType, IP
     *       protocol, and canonical service port and returns that result when present.</li>
     *   <li>Logs whether the lookup ended with an exact hit, a service hit, or no result.</li>
     * </ol>
     *
     * <p>{@link com.sma.sdn.classification.ClassificationService#classifyOrGetCached(PacketClassificationContext)}
     * uses an empty result as the signal to invoke the external classifier.</p>
     *
     * @param context ingress identity and packet features from which both lookup keys are derived
     * @return the exact or service-class result, or an empty optional when neither view contains a valid entry
     * @throws NullPointerException if {@code context} or its packet features are {@code null}
     */
    public synchronized Optional<ClassificationResult> find(final PacketClassificationContext context) {

        /*Expire old entries to remove stale records and force reclassification if no entry is alive*/
        expireOldEntries();

        /*Extract corresponding packet features for their use in multiple lookups*/
        final String switchName = context.ingressSwitchName();
        final PacketFeatures features = context.packetFeatures();
        /*Exact lookup based on the packet's arriving interface, this is to avoid looking over to classification peers that
        * might have this in their switches. Notice that if we move to look up at the switch level only, classification can
        * in an edge case been done over the core-lsr1 or core-lsr4 interface and the system would use that classification*/

        /*However, since that classification route is removed, it is possible that the broad classification will also fail
        * and correctly force a recalculation of the classification*/
        final ClassificationResult exact = exactBySwitch
                .getOrDefault(
                        switchName,
                        Map.of())
                .get(exactKey(context));
        if (exact != null) {
            LOG.trace("classification_registry_exact_hit", "find",
                    "Se encontro una clasificacion exacta en el registro",
                    StructuredLogger.fields("ingress_switch", switchName, "registry_size", size()));
            /*If narrow lookup worked this is returned immediately*/
            return Optional.of(exact);
        }
        /*Second broad attempt*/
        final ClassificationResult service = serviceBySwitch
                .getOrDefault(
                        switchName,
                        Map.of())
                .get(serviceKey(switchName, features));
        LOG.trace("classification_registry_lookup_completed", "find",
                "Finalizo la consulta del registro de clasificaciones",
                StructuredLogger.fields("ingress_switch", switchName, "service_hit", service != null,
                        "registry_size", size()));
        /*If this fails we are returning an ofNullable meaning that it can be null when the service is not found, in contrast
        * to the Non Nullable Optional above*/
        return Optional.ofNullable(service);
    }

    /**
     * <p>Stores the same classification in both cache views, replacing any existing values for the derived exact and
     * service-class keys. Expiration is not evaluated during insertion.</p>
     *
     * <p>The update performs these steps:</p>
     * <ol>
     *   <li>Selects or creates the exact-flow map for the ingress switch and writes the result under the full packet
     *       key.</li>
     *   <li>Selects or creates the service-class map for that switch and writes the result under the canonical service
     *       key.</li>
     *   <li>Logs the ingress identity, result expiry, and combined number of exact and service records.</li>
     * </ol>
     *
     * <p>Subsequent calls to {@link #find(PacketClassificationContext)} can reuse either representation until the
     * result expires.</p>
     *
     * @param context ingress identity and packet features used to derive both cache keys
     * @param result classifier result stored under the exact-flow and service-class keys
     * @throws NullPointerException if {@code context}, its packet features, or {@code result} is {@code null}
     */
    public synchronized void put(final PacketClassificationContext context, final ClassificationResult result) {
        final String switchName = context.ingressSwitchName();
        /*Register in the registrar either the exact representation by the switch and connector name alongside the context
        * of the packet received. Or stores the result associated with a canonical port. Either of these can be used in
        * the find mechanism*/
        exactBySwitch
                .computeIfAbsent(
                        switchName,
                        key -> new HashMap<>())
                .put(
                        exactKey(context),
                        result);
        serviceBySwitch
                .computeIfAbsent(
                        switchName,
                        key -> new HashMap<>())
                .put(
                        serviceKey(switchName, context.packetFeatures()), //Here is where we get the notion of the service key
                        // Notice how we are using the switch name and the features, when in the exact we are using the whole
                        // context which includes the ingress port name and the corresponding switch name
                        result);
        LOG.debug("classification_registry_updated", "put",
                "Se almacenaron las clasificaciones exacta y de servicio",
                StructuredLogger.fields("ingress_switch", switchName,
                        "ingress_connector", context.ingressConnectorName(), "expires_at", result.expiresAt(),
                        "registry_size", size()));
    }

    /**
     * <p>Removes results whose {@link ClassificationResult#expiresAt()} value is strictly before a single current-time
     * snapshot. Empty per-switch maps are removed as part of the same sweep.</p>
     *
     * <p>The expiration sweep performs these steps:</p>
     * <ol>
     *   <li>Captures the combined record count and one {@link Instant} used by both cache views.</li>
     *   <li>Removes expired exact-flow entries and then expired service-class entries.</li>
     *   <li>Records one labeled eviction metric per removed record when metrics are enabled.</li>
     *   <li>Logs the combined number removed when the sweep changed the registrar.</li>
     * </ol>
     *
     * <p>This method is also called by lookups, size queries, snapshots, and clearing, so those operations expose only
     * entries surviving their own expiration sweep.</p>
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
     * <p>Returns the combined number of unexpired records across both cache views. Because one classifier result is
     * normally written once to each view, this is a record count rather than a count of distinct classifications.</p>
     *
     * <p>The size query performs these steps:</p>
     * <ol>
     *   <li>Expires stale records from both views.</li>
     *   <li>Sums every per-switch exact map and every per-switch service map.</li>
     * </ol>
     *
     * <p>The count is used for registry event logging and operational diagnostics.</p>
     *
     * @return combined number of exact-flow and service-class records after expiration
     */
    public synchronized int size() {
        expireOldEntries();
        return entryCount();
    }

    /**
     * <p>Returns all unexpired exact-flow entries in one immutable map, preserving the ingress switch inside each
     * {@link ClassificationCacheKey}.</p>
     *
     * <p>The snapshot operation performs these steps:</p>
     * <ol>
     *   <li>Expires stale entries from both cache views.</li>
     *   <li>Copies each per-switch exact-flow map into one temporary map.</li>
     *   <li>Returns an immutable copy that is independent of later registrar mutations.</li>
     * </ol>
     *
     * <p>{@link com.sma.sdn.operational.ControllerOperationalStatePublisher} converts this snapshot into exact-cache
     * operational-state entries.</p>
     *
     * @return immutable snapshot of all unexpired exact-flow classifications
     */
    public synchronized Map<ClassificationCacheKey, ClassificationResult> exactSnapshot() {
        expireOldEntries();
        final Map<ClassificationCacheKey, ClassificationResult> snapshot = new HashMap<>();
        exactBySwitch.values().forEach(snapshot::putAll);
        return Map.copyOf(snapshot);
    }

    /**
     * <p>Returns all unexpired service-class entries in one immutable map, preserving the ingress switch inside each
     * {@link ServiceClassCacheKey}.</p>
     *
     * <p>The snapshot operation performs these steps:</p>
     * <ol>
     *   <li>Expires stale entries from both cache views.</li>
     *   <li>Copies each per-switch service-class map into one temporary map.</li>
     *   <li>Returns an immutable copy that is independent of later registrar mutations.</li>
     * </ol>
     *
     * <p>{@link com.sma.sdn.operational.ControllerOperationalStatePublisher} converts this snapshot into service-cache
     * operational-state entries.</p>
     *
     * @return immutable snapshot of all unexpired service-class classifications
     */
    public synchronized Map<ServiceClassCacheKey, ClassificationResult> serviceSnapshot() {
        expireOldEntries();
        final Map<ServiceClassCacheKey, ClassificationResult> snapshot = new HashMap<>();
        serviceBySwitch.values().forEach(snapshot::putAll);
        return Map.copyOf(snapshot);
    }

    /**
     * <p>Counts records in the two cache views without triggering expiration.</p>
     *
     * <p>The count performs these steps:</p>
     * <ol>
     *   <li>Sums the sizes of all exact-flow maps.</li>
     *   <li>Sums the sizes of all service-class maps and adds that value to the exact count.</li>
     * </ol>
     *
     * <p>Callers that expose the value first run an expiration sweep.</p>
     *
     * @return combined number of records currently held in both views
     */
    private int entryCount() {
        return exactBySwitch.values().stream().mapToInt(Map::size).sum()
                + serviceBySwitch.values().stream().mapToInt(Map::size).sum();
    }

    /**
     * <p>Removes every exact-flow and service-class entry from the registrar.</p>
     *
     * <p>The clear operation performs these steps:</p>
     * <ol>
     *   <li>Calls {@link #size()} to expire stale records and capture the number of remaining records.</li>
     *   <li>Clears both outer maps, including their per-switch maps.</li>
     *   <li>Logs the number of unexpired records removed.</li>
     * </ol>
     *
     * <p>No eviction metric is emitted for records removed explicitly by this operation.</p>
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
     * <p>Selects the transport port used to group packets into a service-class cache key. A privileged destination port
     * takes precedence, followed by a privileged source port; otherwise the destination port is retained.</p>
     *
     * <p>The selection performs these steps:</p>
     * <ol>
     *   <li>Returns the destination port when it is in the inclusive range {@code 1..1023}.</li>
     *   <li>Otherwise returns the source port when it is in that privileged range.</li>
     *   <li>Otherwise returns the destination port, including zero or an ephemeral port.</li>
     * </ol>
     *
     * <p>{@link #serviceKey(String, PacketFeatures)} stores this value in {@link ServiceClassCacheKey}.</p>
     *
     * @param features packet features containing the source and destination transport ports
     * @return canonical port used by service-class cache lookups
     * @throws NullPointerException if {@code features} is {@code null}
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
     * <p>Builds the exact-flow cache key for a packet context.</p>
     *
     * <p>The key construction performs these steps:</p>
     * <ol>
     *   <li>Reads the packet features from the context.</li>
     *   <li>Combines the ingress switch and connector names with EtherType, IP protocol, source port, and destination
     *       port.</li>
     * </ol>
     *
     * <p>The resulting key is used identically by {@link #find(PacketClassificationContext)} and
     * {@link #put(PacketClassificationContext, ClassificationResult)}.</p>
     *
     * @param context packet context supplying the ingress identity and packet feature tuple
     * @return exact-flow cache key derived from the context
     * @throws NullPointerException if {@code context} or its packet features are {@code null}
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
     * <p>Builds the broader service-class key used after an exact-flow cache miss.</p>
     *
     * <p>The key construction performs these steps:</p>
     * <ol>
     *   <li>Reads EtherType and IP protocol from the packet features.</li>
     *   <li>Obtains the port grouping from {@link #canonicalServicePort(PacketFeatures)}.</li>
     *   <li>Combines those values with the ingress switch name.</li>
     * </ol>
     *
     * <p>The key intentionally omits the ingress connector and non-canonical transport port so compatible packets on
     * the same switch can reuse a classification.</p>
     *
     * @param switchName logical ingress-switch name included in the cache key
     * @param features packet features supplying protocol values and transport ports
     * @return service-class cache key derived from the switch and packet features
     * @throws NullPointerException if {@code features} is {@code null}
     */
    private static ServiceClassCacheKey serviceKey(final String switchName, final PacketFeatures features) {
        return new ServiceClassCacheKey(
                switchName,
                features.ethType(),
                features.ipProto(),
                canonicalServicePort(features));
    }

    /**
     * <p>Removes expired values from one cache view and prunes switch maps that become empty.</p>
     *
     * <p>The removal performs these steps:</p>
     * <ol>
     *   <li>Iterates over each per-switch map in the supplied cache view.</li>
     *   <li>Removes entries whose result expiry is strictly before {@code now} and accumulates the number removed.</li>
     *   <li>Removes the outer switch entry when its inner map is empty.</li>
     * </ol>
     *
     * <p>{@link #expireOldEntries()} invokes this helper once for each cache view and labels the resulting eviction
     * metrics separately.</p>
     *
     * @param registry exact-flow or service-class cache view to sweep
     * @param now time snapshot against which result expiry values are compared
     * @param <K> key type stored in the selected cache view
     * @return number of records removed from the supplied view
     * @throws NullPointerException if {@code registry}, {@code now}, or a stored result expiry is {@code null}
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

    /**
     * <p>Records expiration metrics for one cache view when a metrics collector is configured.</p>
     *
     * <p>The metric update performs these steps:</p>
     * <ol>
     *   <li>Returns immediately when this registrar has no metrics collector.</li>
     *   <li>Repeats once per removed entry and increments the expiration counter with the supplied cache-type label.</li>
     * </ol>
     *
     * <p>The per-entry increments preserve the counter API used by {@link SdnMplsMlMetrics}.</p>
     *
     * @param cacheType metric label identifying the exact or service cache view
     * @param count number of expired records to report
     */
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
