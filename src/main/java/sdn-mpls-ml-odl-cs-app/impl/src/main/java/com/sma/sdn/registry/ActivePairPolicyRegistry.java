/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.registry;

import com.sma.sdn.model.ActivePairPolicyState;
import com.sma.sdn.model.DirectionalLspApplicationRecord;
import com.sma.sdn.model.PairPolicyCandidate;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Stores exactly one live policy owner per tunnel pair. */
public final class ActivePairPolicyRegistry {
    private final Map<String, ActivePairPolicyState> activeByPair = new ConcurrentHashMap<>();

    public synchronized Optional<ActivePairPolicyState> findActive(final String pairKey, final Instant now) {
        expireIfNeeded(pairKey, now);
        return Optional.ofNullable(activeByPair.get(pairKey));
    }

    public Optional<ActivePairPolicyState> findIncludingExpired(final String pairKey) {
        return Optional.ofNullable(activeByPair.get(pairKey));
    }

    public synchronized ActivePairPolicyState refresh(final String pairKey, final Instant now, final Duration ttl) {
        final ActivePairPolicyState current = activeByPair.get(pairKey);
        if (current == null) {
            throw new IllegalStateException("No hay politica activa para refrescar: " + pairKey);
        }
        final ActivePairPolicyState refreshed = new ActivePairPolicyState(current.pairKey(), current.serviceKey(),
                current.className(), current.profileName(), current.dscp(), current.mplsTc(), current.requestedBandwidthKbps(),
                current.requestedBandwidthBase64(), current.setupPriority(), current.holdPriority(), current.policySchemaVersion(),
                current.policyHash(), current.generation(), current.installedAt(), now, now.plus(ttl), current.lspApplications());
        activeByPair.put(pairKey, refreshed);
        return refreshed;
    }

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

    public synchronized void expireIfNeeded(final String pairKey, final Instant now) {
        final ActivePairPolicyState current = activeByPair.get(pairKey);
        if (current != null && !current.expiresAt().isAfter(now)) {
            activeByPair.remove(pairKey);
        }
    }

    public synchronized void expireOldEntries(final Instant now) {
        activeByPair.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    public Map<String, ActivePairPolicyState> snapshot() {
        return Map.copyOf(new LinkedHashMap<>(activeByPair));
    }

    /** Returns the number of unexpired active pair policies. */
    public synchronized int size() {
        expireOldEntries(Instant.now());
        return activeByPair.size();
    }
}
