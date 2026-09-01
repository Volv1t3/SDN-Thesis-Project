/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sma.sdn.model.ClassificationResult;
import com.sma.sdn.model.EroSubobject;
import com.sma.sdn.model.PathConstraints;
import com.sma.sdn.model.TrafficPolicy;
import com.sma.sdn.model.TunnelEndpoint;
import com.sma.sdn.model.TunnelIntentKey;
import com.sma.sdn.model.TunnelOperationStatus;
import com.sma.sdn.model.TunnelDirection;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifica la reutilizacion temporal y el limite del diario de operaciones de tunel. */
class TunnelOperationRegistryTest {
    private static final Instant NOW = Instant.parse("2026-08-23T03:05:49Z");

    @Test
    void reusesPendingIntentUntilItsTtlExpires() {
        final TunnelOperationRegistry registry = new TunnelOperationRegistry(5);
        final TunnelIntentKey key = key("ero-forward", "SZiWgA==");
        registry.markPending(
                key, "workflow-1", 1L, direction(), classification(), ero(), NOW, Duration.ofSeconds(10));

        assertTrue(registry.findRecentUsableIntent(key, NOW.plusSeconds(9)).isPresent());
        assertFalse(registry.findRecentUsableIntent(key, NOW.plusSeconds(10)).isPresent());
    }

    @Test
    void differentiatesEroAndBandwidthAndBoundsTheJournal() {
        final TunnelOperationRegistry registry = new TunnelOperationRegistry(2);
        final TunnelIntentKey first = key("ero-forward", "SZiWgA==");
        final TunnelIntentKey differentEro = key("ero-reverse", "SZiWgA==");
        final TunnelIntentKey differentBandwidth = key("ero-forward", "AAAAAA==");
        registry.markPending(
                first, "workflow-1", 1L, direction(), classification(), ero(), NOW, Duration.ofSeconds(10));

        assertFalse(registry.findRecentUsableIntent(differentEro, NOW).isPresent());
        assertFalse(registry.findRecentUsableIntent(differentBandwidth, NOW).isPresent());

        registry.markAccepted(first, 204, NOW.plusSeconds(1), Duration.ofSeconds(10));
        registry.markConfirmed(first, 204, true, true, NOW.plusSeconds(2), Duration.ofSeconds(10));
        assertEquals(2, registry.recentJournalSnapshot().size());
        assertEquals(TunnelOperationStatus.CONFIRMED,
                registry.recentJournalSnapshot().get(1).status());
    }

    private static TunnelIntentKey key(final String fingerprint, final String bandwidth) {
        return new TunnelIntentKey("lsr1_to_lsr4", "pcc://10.100.10.1", "sma-lsr1-lsr4-delegated",
                "icmp_tunnel_policy", "ICMP", bandwidth, fingerprint, 4, 4, "cspf", 0);
    }

    private static TunnelDirection direction() {
        return new TunnelDirection("lsr1_to_lsr4", new TunnelEndpoint("lsr1", "11.11.11.11", "pcc://10.100.10.1"),
                new TunnelEndpoint("lsr4", "14.14.14.14", "pcc://10.100.40.1"));
    }

    private static ClassificationResult classification() {
        return new ClassificationResult("request", "model", 3, "ICMP", 1.0d, Map.of(),
                new TrafficPolicy("icmp_tunnel_policy", 16, 2, new PathConstraints(10_000L, 4, 4), false, null),
                1.0d, NOW, NOW.plusSeconds(30));
    }

    private static List<EroSubobject> ero() {
        return List.of(new EroSubobject(false, "10.0.14.2/32"), new EroSubobject(false, "14.14.14.14/32"));
    }
}
