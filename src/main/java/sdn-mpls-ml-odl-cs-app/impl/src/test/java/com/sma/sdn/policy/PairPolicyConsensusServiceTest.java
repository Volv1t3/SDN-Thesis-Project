/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sma.sdn.model.ActivePairPolicyState;
import com.sma.sdn.model.DirectionalPolicyEvidence;
import com.sma.sdn.model.PairConsensusBucket;
import com.sma.sdn.model.PairConsensusStatus;
import com.sma.sdn.model.PacketFeatures;
import com.sma.sdn.model.ServiceKey;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Covers fixed two-sided consensus independently from PCEP and OpenFlow transports. */
class PairPolicyConsensusServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-23T03:05:49Z");
    private static final ServiceKey KEY = new ServiceKey(2048, 6, 22,
            "eth_type=2048|ip_proto=6|service_port=22");
    private final PairPolicyConsensusService service = new PairPolicyConsensusService();

    @Test
    void normalizesSshRequestAndReplyToTheSameServiceKey() {
        final ServiceKeyResolver resolver = new ServiceKeyResolver();
        assertEquals(resolver.resolve(new PacketFeatures(2048, 6, 56_792, 22)),
                resolver.resolve(new PacketFeatures(2048, 6, 22, 56_792)));
        assertTrue(resolver.isKnownServicePort(8080));
    }

    @Test
    void matchingFreshHashesSelectCandidate() {
        final var decision = service.evaluate(bucket(evidence("LEFT", "SSH", 2, 1, NOW.plusSeconds(10)),
                evidence("RIGHT", "SSH", 2, 1, NOW.plusSeconds(10))), Optional.empty(), NOW);
        assertEquals(PairConsensusStatus.CONSENSUS_MATCH, decision.consensusStatus());
        assertEquals("SSH", decision.selectedCandidate().orElseThrow().className());
    }

    @Test
    void servicePortNeverOverridesStrongerMlPolicy() {
        final var decision = service.evaluate(bucket(evidence("LEFT", "SSH", 2, 1, NOW.plusSeconds(10)),
                evidence("RIGHT", "STREAMING", 1, 0, NOW.plusSeconds(10))), Optional.empty(), NOW);
        assertEquals(PairConsensusStatus.CONSENSUS_CONFLICT_PRIORITY_SELECTED, decision.consensusStatus());
        assertEquals("STREAMING", decision.selectedCandidate().orElseThrow().className());
    }

    @Test
    void holdPriorityBreaksSetupTie() {
        final var decision = service.evaluate(bucket(evidence("LEFT", "FTP", 6, 6, NOW.plusSeconds(10)),
                evidence("RIGHT", "HTTP", 6, 5, NOW.plusSeconds(10))), Optional.empty(), NOW);
        assertEquals(PairConsensusStatus.CONSENSUS_CONFLICT_PRIORITY_SELECTED, decision.consensusStatus());
        assertEquals("HTTP", decision.selectedCandidate().orElseThrow().className());
    }

    @Test
    void defersDifferentPoliciesWithEqualPriorityWithoutAnActiveMatch() {
        final var decision = service.evaluate(bucket(evidence("LEFT", "SSH", 2, 1, NOW.plusSeconds(10)),
                evidence("RIGHT", "OTHER", 2, 1, NOW.plusSeconds(10))), Optional.empty(), NOW);
        assertEquals(PairConsensusStatus.CONSENSUS_CONFLICT_EQUAL_PRIORITY_UNRESOLVED, decision.consensusStatus());
        assertTrue(decision.selectedCandidate().isEmpty());
    }

    @Test
    void retainsMatchingActiveHashOnEqualPriorityConflict() {
        final var left = evidence("LEFT", "SSH", 2, 1, NOW.plusSeconds(10));
        final var right = evidence("RIGHT", "OTHER", 2, 1, NOW.plusSeconds(10));
        final var decision = service.evaluate(bucket(left, right), Optional.of(active(left)), NOW);
        assertEquals(PairConsensusStatus.CONSENSUS_CONFLICT_CURRENT_POLICY_PRESERVED, decision.consensusStatus());
        assertTrue(decision.selectedCandidate().isEmpty());
    }

    @Test
    void expiredLeftIsAbsentEvenWhenBucketAndRightRemainLive() {
        final var decision = service.evaluate(bucket(evidence("LEFT", "SSH", 2, 1, NOW.minusSeconds(1)),
                evidence("RIGHT", "SSH", 2, 1, NOW.plusSeconds(10))), Optional.empty(), NOW);
        assertEquals(PairConsensusStatus.PENDING_ONE_SIDE, decision.consensusStatus());
        assertTrue(decision.leftEvidence().isEmpty());
        assertTrue(decision.selectedCandidate().isEmpty());
    }

    @Test
    void expiredRightIsAbsentEvenWhenLeftRemainsLive() {
        final var decision = service.evaluate(bucket(evidence("LEFT", "SSH", 2, 1, NOW.plusSeconds(10)),
                evidence("RIGHT", "SSH", 2, 1, NOW.minusSeconds(1))), Optional.empty(), NOW);
        assertEquals(PairConsensusStatus.PENDING_ONE_SIDE, decision.consensusStatus());
        assertTrue(decision.rightEvidence().isEmpty());
    }

    @Test
    void evidenceExpiringAtDecisionTimeIsNotFresh() {
        final var decision = service.evaluate(bucket(evidence("LEFT", "SSH", 2, 1, NOW),
                evidence("RIGHT", "SSH", 2, 1, NOW)), Optional.empty(), NOW);
        assertEquals(PairConsensusStatus.PENDING_ONE_SIDE, decision.consensusStatus());
        assertTrue(decision.leftEvidence().isEmpty());
        assertTrue(decision.rightEvidence().isEmpty());
    }

    private static PairConsensusBucket bucket(final DirectionalPolicyEvidence left,
            final DirectionalPolicyEvidence right) {
        final Instant latestExpiry = left.expiresAt().isAfter(right.expiresAt())
                ? left.expiresAt() : right.expiresAt();
        return new PairConsensusBucket("lsr1_lsr4", KEY, Optional.of(left), Optional.of(right),
                NOW.minusSeconds(20), NOW, latestExpiry);
    }

    private static DirectionalPolicyEvidence evidence(final String side, final String className,
            final int setup, final int hold, final Instant expiresAt) {
        final String direction = "LEFT".equals(side) ? "lsr1_to_lsr4" : "lsr4_to_lsr1";
        final String switchName = "LEFT".equals(side) ? "ECHO" : "FOXTROT";
        return new DirectionalPolicyEvidence("lsr1_lsr4", direction, switchName, "host",
                new PacketFeatures(2048, 6, 22, 0), KEY, className, className.toLowerCase() + "_policy",
                0, 0, 10_000, "SZiWgA==", setup, hold, "classifier-policy-v1", className + "-hash",
                NOW.minusSeconds(20), expiresAt);
    }

    private static ActivePairPolicyState active(final DirectionalPolicyEvidence evidence) {
        return new ActivePairPolicyState(evidence.pairKey(), evidence.serviceKey(), evidence.className(),
                evidence.profileName(), evidence.dscp(), evidence.mplsTc(), evidence.requestedBandwidthKbps(),
                evidence.requestedBandwidthBase64(), evidence.setupPriority(), evidence.holdPriority(),
                evidence.policySchemaVersion(), evidence.policyHash(), 1L, NOW.minusSeconds(20), NOW,
                NOW.plusSeconds(60), Map.of());
    }
}
