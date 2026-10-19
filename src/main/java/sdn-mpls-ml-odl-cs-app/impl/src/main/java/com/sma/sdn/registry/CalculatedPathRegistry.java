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
import com.sma.sdn.model.CalculatedPath;
import com.sma.sdn.model.CalculatedPathKey;
import com.sma.sdn.observability.StructuredLogger;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * <p>Caches calculated paths under synchronized path keys. Path computation reuses valid results
 * and operational state consumers obtain immutable snapshots; expiration is performed on reads
 * or by an explicit sweep.</p>
 */
public final class CalculatedPathRegistry {
    /** <p>Logger for cache lookups, updates, expirations, and clears.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(CalculatedPathRegistry.class);
    /** <p>Calculated paths indexed by their computation keys.</p> */
    private final Map<CalculatedPathKey, CalculatedPath> paths = new HashMap<>();
    /** <p>Optional metrics collector for expired path evictions.</p> */
    private final SdnMplsMlMetrics metrics;

    /**
     * <p>Create an empty cache without eviction metrics.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Delegate to the metrics constructor with {@code null}.</li>
     * </ol>
     */
    public CalculatedPathRegistry() {
        this(null);
    }

    /**
     * <p>Create an empty cache with an optional metrics collector.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Retain the supplied collector for expiration reporting.</li>
     * </ol>
     *
     * @param metrics collector for eviction counters, or {@code null}
     */
    public CalculatedPathRegistry(final SdnMplsMlMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * <p>Find a cached path after removing expired entries.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Sweep expired paths using the current time.</li>
     *   <li>Look up the key, log the result and cache size, and return an optional.</li>
     * </ol>
     *
     * @param key path key to look up
     * @return unexpired cached path or an empty optional
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
     * <p>Insert or replace a cached path.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Store the path under the supplied key and determine whether it replaced a value.</li>
     *   <li>Log the key, expiry, replacement status, and cache size.</li>
     * </ol>
     *
     * @param key path computation key
     * @param path calculated path to store
     */
    public synchronized void put(final CalculatedPathKey key, final CalculatedPath path) {
        final boolean replaced = paths.put(key, path) != null;
        LOG.debug("calculated_path_registry_updated", "put",
                "Se actualizo el registro de caminos calculados",
                StructuredLogger.fields("cache_key", key, "replaced", replaced,
                        "expires_at", path.expiresAt(), "registry_size", paths.size()));
    }

    /**
     * <p>Remove paths whose expiration is strictly before the current sweep time.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Capture the current time and remove expired entries while iterating.</li>
     *   <li>Record an eviction per removal if metrics are configured.</li>
     *   <li>Log the number removed when nonzero.</li>
     * </ol>
     */
    public synchronized void expireOldEntries() {
        final int previousSize = paths.size();
        // One time sample ensures every entry in this sweep uses the same expiration boundary.
        final Instant now = Instant.now();
        final Iterator<Map.Entry<CalculatedPathKey, CalculatedPath>> iterator = paths.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAt().isBefore(now)) {
                iterator.remove();
            }
        }
        final int expiredCount = previousSize - paths.size();
        recordEvictions(expiredCount);
        if (expiredCount > 0) {
            LOG.debug("calculated_path_registry_entries_expired", "expireOldEntries",
                    "Se eliminaron caminos calculados vencidos",
                    StructuredLogger.fields("expired_count", expiredCount, "registry_size", paths.size()));
        }
    }

    /**
     * <p>Remove every cached path without recording expiration evictions.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Record the previous map size and clear the cache.</li>
     *   <li>Log the number of removed entries.</li>
     * </ol>
     */
    public synchronized void clear() {
        final int previousSize = paths.size();
        paths.clear();
        LOG.info("calculated_path_registry_cleared", "clear",
                "Se elimino el contenido del registro de caminos calculados",
                StructuredLogger.fields("removed_count", previousSize));
    }

    /**
     * <p>Count cached paths after expiration.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Sweep expired entries.</li>
     *   <li>Return the remaining map size.</li>
     * </ol>
     *
     * @return number of unexpired cached paths
     */
    public synchronized int size() {
        expireOldEntries();
        return paths.size();
    }

    /**
     * <p>Return an immutable snapshot of cached paths after expiration.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Sweep expired entries.</li>
     *   <li>Return an immutable copy of the remaining map.</li>
     * </ol>
     *
     * @return immutable path-keyed snapshot
     */
    public synchronized Map<CalculatedPathKey, CalculatedPath> snapshot() {
        expireOldEntries();
        return Map.copyOf(paths);
    }

    /**
     * <p>Record one expiration metric per removed path when metrics are enabled.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Return if no metrics collector is configured.</li>
     *   <li>Increment the path expiration counter once per removed entry.</li>
     * </ol>
     *
     * @param count number of evicted paths
     */
    private void recordEvictions(final int count) {
        if (metrics == null) {
            return;
        }
        for (int index = 0; index < count; index++) {
            metrics.increment("sma_registry_calculated_path_expired_evictions_total");
        }
    }
}
