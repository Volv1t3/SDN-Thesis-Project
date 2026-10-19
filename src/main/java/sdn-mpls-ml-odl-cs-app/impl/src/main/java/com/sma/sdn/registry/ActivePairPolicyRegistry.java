/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.registry;

import com.sma.sdn.metrics.SdnMplsMlMetrics;
import com.sma.sdn.model.ActivePairPolicyState;
import com.sma.sdn.model.DirectionalLspApplicationRecord;
import com.sma.sdn.model.PairPolicyCandidate;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>Stores at most one policy state per tunnel pair, including its generation,
 * lifetime and directional LSP application records. Expiration is lazy: entries
 * remain in the map until an expiration-aware operation removes them.</p>
 * <p>The pair policy coordinator installs and refreshes these states, and operational
 * publication reads snapshots. Optional metrics count expired-state evictions.</p>
 */
public final class ActivePairPolicyRegistry {
    /** <p>Current policy states indexed by managed pair key.</p> */
    private final Map<String, ActivePairPolicyState> activeByPair = new ConcurrentHashMap<>();
    /** <p>Optional collector for expired-policy eviction counters.</p> */
    private final SdnMplsMlMetrics metrics;

    /**
     * <p>Creates a registry without eviction metrics.</p>
     * <p>Construction proceeds as follows:</p>
     * <ol><li>Delegates to the metrics-aware constructor with a null collector.</li></ol>
     */
    public ActivePairPolicyRegistry() {
        this(null);
    }

    /**
     * <p>Creates a registry with an optional eviction counter.</p>
     * <p>Construction proceeds as follows:</p>
     * <ol><li>Stores the supplied collector for subsequent expiration operations.</li></ol>
     * @param metrics collector, or null to disable eviction counts
     */
    public ActivePairPolicyRegistry(final SdnMplsMlMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * <p>Finds the current policy after checking its expiration against the supplied time.</p>
     * <p>The lookup proceeds as follows:</p>
     * <ol><li>Removes an expired state for the pair, then returns the remaining state if any.</li></ol>
     * @param pairKey pair identifier
     * @param now inclusive expiration cutoff
     * @return unexpired policy state, if present
     * @throws NullPointerException if pairKey is null, or now is null for an existing state
     */
    public synchronized Optional<ActivePairPolicyState> findActive(final String pairKey, final Instant now) {
        expireIfNeeded(pairKey, now);
        return Optional.ofNullable(activeByPair.get(pairKey));
    }

    /**
     * <p>Looks up a stored policy without applying expiration.</p>
     * <p>The lookup proceeds as follows:</p>
     * <ol><li>Reads the concurrent map and wraps the value in an optional.</li></ol>
     * @param pairKey pair identifier
     * @return the stored state, possibly expired, if present
     * @throws NullPointerException if pairKey is null
     */
    public Optional<ActivePairPolicyState> findIncludingExpired(final String pairKey) {
        return Optional.ofNullable(activeByPair.get(pairKey));
    }

    /**
     * <p>Extends the lifetime of a stored policy without changing its generation or
     * directional application records. This method does not first expire the entry.</p>
     * <p>The update proceeds as follows:</p>
     * <ol>
     *   <li>Reads the current state and rejects a missing pair.</li>
     *   <li>Copies its policy and installation data into a replacement with refresh time
     *       {@code now} and expiration {@code now + ttl}, then stores and returns it.</li>
     * </ol>
     * @param pairKey pair identifier
     * @param now refresh time
     * @param ttl lifetime measured from now
     * @return the replacement state
     * @throws IllegalStateException if no state is stored for the pair
     * @throws NullPointerException if a required argument is null
     * @throws java.time.DateTimeException if the resulting instant exceeds the supported range
     * @throws ArithmeticException if time arithmetic overflows
     */
    public synchronized ActivePairPolicyState refresh(final String pairKey, final Instant now, final Duration ttl) {
        final ActivePairPolicyState current = activeByPair.get(pairKey);
        if (current == null) {
            throw new IllegalStateException("No hay politica activa para refrescar: " + pairKey);
        }
        final ActivePairPolicyState refreshed = new ActivePairPolicyState(
                current.pairKey(), current.serviceKey(), current.className(), current.profileName(),
                current.dscp(), current.mplsTc(), current.requestedBandwidthKbps(),
                current.requestedBandwidthBase64(), current.setupPriority(), current.holdPriority(),
                current.policySchemaVersion(), current.policyHash(), current.generation(), current.installedAt(),
                now, now.plus(ttl), current.lspApplications());
        activeByPair.put(pairKey, refreshed);
        return refreshed;
    }

    /**
     * <p>Stores a candidate as the current policy with its directional application results.</p>
     * <p>The installation proceeds as follows:</p>
     * <ol>
     *   <li>Reads any stored predecessor, including one not yet lazily expired, and chooses
     *       generation one or the predecessor's generation plus one.</li>
     *   <li>Copies the candidate and application records into a new state with installation
     *       and refresh times set to now and expiration set to {@code now + ttl}.</li>
     *   <li>Replaces the pair entry and returns the new state.</li>
     * </ol>
     * @param candidate policy to store
     * @param children directional LSP application records
     * @param now installation time
     * @param ttl lifetime measured from now
     * @return newly installed state
     * @throws NullPointerException if a required argument is null
     * @throws java.time.DateTimeException if the resulting instant exceeds the supported range
     * @throws ArithmeticException if time arithmetic overflows
     */
    public synchronized ActivePairPolicyState installOrReplace(final PairPolicyCandidate candidate,
            final Map<String, DirectionalLspApplicationRecord> children, final Instant now, final Duration ttl) {
        final ActivePairPolicyState previous = activeByPair.get(candidate.pairKey());
        final long generation = previous == null ? 1L : previous.generation() + 1L;
        final ActivePairPolicyState installed = new ActivePairPolicyState(candidate.pairKey(), candidate.serviceKey(),
                candidate.className(), candidate.profileName(), candidate.dscp(), candidate.mplsTc(),
                candidate.requestedBandwidthKbps(), candidate.requestedBandwidthBase64(), candidate.setupPriority(),
                candidate.holdPriority(), candidate.policySchemaVersion(), candidate.policyHash(), generation,
                now, now, now.plus(ttl), children);
        activeByPair.put(candidate.pairKey(), installed);
        return installed;
    }

    /**
     * <p>Evicts the selected pair's policy if its expiration has been reached.</p>
     * <p>The expiration proceeds as follows:</p>
     * <ol><li>Checks the state and removes and counts it when expiration is at or before now.</li></ol>
     * @param pairKey pair identifier
     * @param now inclusive expiration cutoff
     * @throws NullPointerException if pairKey is null, or now is null for an existing state
     */
    public synchronized void expireIfNeeded(final String pairKey, final Instant now) {
        final ActivePairPolicyState current = activeByPair.get(pairKey);
        if (current != null && !current.expiresAt().isAfter(now)) {
            activeByPair.remove(pairKey);
            recordEvictions(1);
        }
    }

    /**
     * <p>Evicts all policies whose expiration has been reached.</p>
     * <p>The expiration proceeds as follows:</p>
     * <ol><li>Removes matching states and records one metric increment per removal.</li></ol>
     * @param now inclusive expiration cutoff
     * @throws NullPointerException if now is null and a state is examined
     */
    public synchronized void expireOldEntries(final Instant now) {
        final int previousSize = activeByPair.size();
        activeByPair.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        recordEvictions(previousSize - activeByPair.size());
    }

    /**
     * <p>Returns a copy of the policies that have not expired at the current time.</p>
     * <p>The snapshot proceeds as follows:</p>
     * <ol><li>Expires old entries using the current clock and copies the remaining map.</li></ol>
     * @return immutable mapping from pair keys to policy states
     */
    public synchronized Map<String, ActivePairPolicyState> snapshot() {
        expireOldEntries(Instant.now());
        return Map.copyOf(new LinkedHashMap<>(activeByPair));
    }

    /**
     * <p>Counts policies that remain after expiration at the current time.</p>
     * <p>The count proceeds as follows:</p>
     * <ol><li>Evicts expired entries and returns the remaining map size.</li></ol>
     * @return number of unexpired policies
     */
    public synchronized int size() {
        expireOldEntries(Instant.now());
        return activeByPair.size();
    }

    /**
     * <p>Counts expired policy states when metrics are enabled.</p>
     * <p>The recording proceeds as follows:</p>
     * <ol><li>Returns without a collector; otherwise increments once per eviction.</li></ol>
     * @param count number of removed states
     */
    private void recordEvictions(final int count) {
        if (metrics == null) {
            return;
        }
        for (int index = 0; index < count; index++) {
            metrics.increment("sma_registry_active_pair_policy_expired_evictions_total");
        }
    }
}
