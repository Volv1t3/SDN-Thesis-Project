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

/** Thread-safe, TTL-bound storage of left/right classification evidence. */
public final class DirectionalClassificationEvidenceRegistry {
    private final TunnelPairRegistry pairRegistry;
    private final Map<String, PairConsensusBucket> buckets = new ConcurrentHashMap<>();
    private final SdnMplsMlMetrics metrics;

    public DirectionalClassificationEvidenceRegistry(final TunnelPairRegistry pairRegistry) {
        this(pairRegistry, null);
    }

    public DirectionalClassificationEvidenceRegistry(
            final TunnelPairRegistry pairRegistry,
            final SdnMplsMlMetrics metrics) {
        this.pairRegistry = pairRegistry;
        this.metrics = metrics;
    }

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

    public synchronized Optional<PairConsensusBucket> findBucket(
            final String pairKey, final ServiceKey serviceKey, final Instant now) {
        expireOldEvidence(now);
        return Optional.ofNullable(buckets.get(key(pairKey, serviceKey)));
    }

    public synchronized void expireOldEvidence(final Instant now) {
        final int previousSize = buckets.size();
        buckets.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        recordEvictions(previousSize - buckets.size());
    }

    public synchronized Map<String, PairConsensusBucket> snapshot() {
        expireOldEvidence(Instant.now());
        return Map.copyOf(new LinkedHashMap<>(buckets));
    }

    /** Returns the number of unexpired directional-evidence buckets. */
    public synchronized int size() {
        expireOldEvidence(Instant.now());
        return buckets.size();
    }

    private static Instant latestExpiry(final Optional<DirectionalPolicyEvidence> left,
            final Optional<DirectionalPolicyEvidence> right) {
        final Instant leftExpiry = left.map(DirectionalPolicyEvidence::expiresAt).orElse(Instant.EPOCH);
        final Instant rightExpiry = right.map(DirectionalPolicyEvidence::expiresAt).orElse(Instant.EPOCH);
        return leftExpiry.isAfter(rightExpiry) ? leftExpiry : rightExpiry;
    }

    private static String key(final String pairKey, final ServiceKey serviceKey) {
        return pairKey + "|" + serviceKey.normalizedValue();
    }

    private void recordEvictions(final int count) {
        if (metrics == null) {
            return;
        }
        for (int index = 0; index < count; index++) {
            metrics.increment("sma_registry_directional_evidence_expired_evictions_total");
        }
    }
}
