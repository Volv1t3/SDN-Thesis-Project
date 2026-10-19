/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.registry;

import com.sma.sdn.metrics.SdnMplsMlMetrics;
import com.sma.sdn.model.DirectionalPolicyEvidence;
import com.sma.sdn.model.PairConsensusBucket;
import com.sma.sdn.model.ServiceKey;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>Stores the most recently recorded classification evidence on each side of a
 * tunnel pair, grouped by pair and normalized service key. A bucket remains present
 * until the later of its two evidence expiration times; individual expired sides are
 * not removed independently.</p>
 * <p>Pair sides are resolved by {@link TunnelPairRegistry}; policy consensus consumes
 * the resulting buckets, and optional metrics count expired bucket evictions.</p>
 */
public final class DirectionalClassificationEvidenceRegistry {
    /** <p>Resolves ingress switches to the sides of a managed pair.</p> */
    private final TunnelPairRegistry pairRegistry;
    /** <p>Consensus buckets indexed by pair and normalized service key.</p> */
    private final Map<String, PairConsensusBucket> buckets = new ConcurrentHashMap<>();
    /** <p>Optional collector for expired-bucket eviction counters.</p> */
    private final SdnMplsMlMetrics metrics;

    /**
     * <p>Creates an evidence registry without eviction metrics.</p>
     * <p>Construction proceeds as follows:</p>
     * <ol><li>Delegates to the metrics-aware constructor with a null collector.</li></ol>
     * @param pairRegistry resolver for managed pair sides
     */
    public DirectionalClassificationEvidenceRegistry(final TunnelPairRegistry pairRegistry) {
        this(pairRegistry, null);
    }

    /**
     * <p>Creates an evidence registry with an optional metrics collector.</p>
     * <p>Construction proceeds as follows:</p>
     * <ol><li>Stores the supplied side resolver and collector for later operations.</li></ol>
     * @param pairRegistry resolver for managed pair sides
     * @param metrics collector for eviction counts, or null to disable them
     */
    public DirectionalClassificationEvidenceRegistry(
            final TunnelPairRegistry pairRegistry,
            final SdnMplsMlMetrics metrics) {
        this.pairRegistry = pairRegistry;
        this.metrics = metrics;
    }

    /**
     * <p>Records evidence on its ingress side, retaining the opposite side if a bucket
     * already exists. Expiration is evaluated against the evidence observation time.</p>
     * <p>The update proceeds as follows:</p>
     * <ol>
     *   <li>Evicts buckets whose expiration is not later than the observation time.</li>
     *   <li>Resolves the pair and switch side, then replaces that side while preserving
     *       the other side and the existing creation time, when present.</li>
     *   <li>Sets the bucket expiration to the later side expiration, stores it and returns it.</li>
     * </ol>
     * @param evidence classification observation to store
     * @return the updated consensus bucket
     * @throws IllegalArgumentException if its pair or switch is unmanaged
     * @throws NullPointerException if evidence or a required collaborator is null
     */
    public synchronized PairConsensusBucket recordEvidence(final DirectionalPolicyEvidence evidence) {
        expireOldEvidence(evidence.observedAt());
        final String key = key(evidence.pairKey(), evidence.serviceKey());
        final PairConsensusBucket previous = buckets.get(key);
        final boolean left = "LEFT".equals(
                pairRegistry.sideForSwitch(evidence.pairKey(), evidence.ingressSwitchName()));
        final Optional<DirectionalPolicyEvidence> leftEvidence = left ? Optional.of(evidence)
                : previous == null ? Optional.empty() : previous.leftEvidence();
        final Optional<DirectionalPolicyEvidence> rightEvidence = left
                ? previous == null ? Optional.empty() : previous.rightEvidence() : Optional.of(evidence);
        final Instant created = previous == null ? evidence.observedAt() : previous.createdAt();
        final Instant expires = latestExpiry(leftEvidence, rightEvidence);
        final PairConsensusBucket bucket = new PairConsensusBucket(evidence.pairKey(), evidence.serviceKey(),
                leftEvidence, rightEvidence, created, evidence.observedAt(), expires);
        buckets.put(key, bucket);
        return bucket;
    }

    /**
     * <p>Finds a bucket after expiring all buckets at the supplied instant.</p>
     * <p>The lookup proceeds as follows:</p>
     * <ol><li>Evicts expired buckets and looks up the pair and normalized service key.</li></ol>
     * @param pairKey pair identifier
     * @param serviceKey service identity used to form the bucket key
     * @param now expiration cutoff
     * @return the bucket if present after expiration
     * @throws NullPointerException if a required argument is null
     */
    public synchronized Optional<PairConsensusBucket> findBucket(
            final String pairKey, final ServiceKey serviceKey, final Instant now) {
        expireOldEvidence(now);
        return Optional.ofNullable(buckets.get(key(pairKey, serviceKey)));
    }

    /**
     * <p>Removes whole buckets whose latest evidence expiration has been reached.</p>
     * <p>The expiration proceeds as follows:</p>
     * <ol><li>Removes matching buckets and records one eviction metric per removed bucket.</li></ol>
     * @param now expiration cutoff, inclusive
     * @throws NullPointerException if now is null and a bucket is examined
     */
    public synchronized void expireOldEvidence(final Instant now) {
        final int previousSize = buckets.size();
        buckets.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        recordEvictions(previousSize - buckets.size());
    }

    /**
     * <p>Returns an immutable view copy after removing buckets expired at the current time.</p>
     * <p>The snapshot proceeds as follows:</p>
     * <ol><li>Expires buckets using the current clock and copies the remaining map.</li></ol>
     * @return immutable mapping of composite keys to live buckets
     */
    public synchronized Map<String, PairConsensusBucket> snapshot() {
        expireOldEvidence(Instant.now());
        return Map.copyOf(new LinkedHashMap<>(buckets));
    }

    /**
     * <p>Counts buckets that remain after expiration at the current time.</p>
     * <p>The count proceeds as follows:</p>
     * <ol><li>Evicts expired buckets and returns the remaining map size.</li></ol>
     * @return number of unexpired buckets
     */
    public synchronized int size() {
        expireOldEvidence(Instant.now());
        return buckets.size();
    }

    /**
     * <p>Chooses the later expiration of the two evidence slots.</p>
     * <p>The comparison proceeds as follows:</p>
     * <ol><li>Uses the epoch for absent slots and returns the later resulting instant.</li></ol>
     * @param left optional left-side evidence
     * @param right optional right-side evidence
     * @return later evidence expiration, or the epoch if both are absent
     */
    private static Instant latestExpiry(final Optional<DirectionalPolicyEvidence> left,
            final Optional<DirectionalPolicyEvidence> right) {
        final Instant leftExpiry = left.map(DirectionalPolicyEvidence::expiresAt).orElse(Instant.EPOCH);
        final Instant rightExpiry = right.map(DirectionalPolicyEvidence::expiresAt).orElse(Instant.EPOCH);
        return leftExpiry.isAfter(rightExpiry) ? leftExpiry : rightExpiry;
    }

    /**
     * <p>Builds the composite index for a pair and service.</p>
     * <p>The construction proceeds as follows:</p>
     * <ol><li>Joins the pair key and normalized service value with a vertical bar.</li></ol>
     * @param pairKey pair identifier
     * @param serviceKey service identity
     * @return composite bucket key
     * @throws NullPointerException if serviceKey is null
     */
    private static String key(final String pairKey, final ServiceKey serviceKey) {
        return pairKey + "|" + serviceKey.normalizedValue();
    }

    /**
     * <p>Counts expired buckets when metrics are enabled.</p>
     * <p>The recording proceeds as follows:</p>
     * <ol><li>Returns if no collector is configured; otherwise increments once per eviction.</li></ol>
     * @param count number of removed buckets
     */
    private void recordEvictions(final int count) {
        if (metrics == null) {
            return;
        }
        for (int index = 0; index < count; index++) {
            metrics.increment("sma_registry_directional_evidence_expired_evictions_total");
        }
    }
}
