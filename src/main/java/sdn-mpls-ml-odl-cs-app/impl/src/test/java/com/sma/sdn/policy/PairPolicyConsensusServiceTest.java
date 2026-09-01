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

import com.sma.sdn.model.DirectionalPolicyEvidence;
import com.sma.sdn.model.PairConsensusBucket;
import com.sma.sdn.model.PairConsensusEqualPriorityAction;
import com.sma.sdn.model.PairConsensusStatus;
import com.sma.sdn.model.PacketFeatures;
import com.sma.sdn.model.ServiceKey;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Covers consensus behavior independently from PCEP and OpenFlow transports. */
class PairPolicyConsensusServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-23T03:05:49Z");

    @Test
    void normalizesSshRequestAndReplyToTheSameServiceKey() {
        final ServiceKeyResolver resolver = new ServiceKeyResolver();
        assertEquals(resolver.resolve(new PacketFeatures(2048, 6, 56_792, 22)),
                resolver.resolve(new PacketFeatures(2048, 6, 22, 56_792)));
    }

    @Test
    void selectsExpectedSshClassWhenReplyIsMisclassified() {
        final ServiceKey key = new ServiceKey(2048, 6, 22, "eth_type=2048|ip_proto=6|service_port=22");
        final PairConsensusBucket bucket = new PairConsensusBucket("lsr1_lsr4", key,
                Optional.of(evidence("LEFT", "lsr1_to_lsr4", "SSH", "ssh_tunnel_policy", 3, 3, key)),
                Optional.of(evidence("RIGHT", "lsr4_to_lsr1", "STREAMING", "streaming_tunnel_policy", 3, 3, key)),
                NOW, NOW, NOW.plusSeconds(10));
        final var decision = service().evaluate(bucket, Optional.empty(), NOW);
        assertEquals(PairConsensusStatus.CONSENSUS_CONFLICT_SERVICE_KEY_SELECTED, decision.consensusStatus());
        assertEquals("SSH", decision.selectedCandidate().orElseThrow().className());
    }

    @Test
    void defersDifferentPoliciesWithEqualPriorityWithoutAnActiveMatch() {
        final ServiceKey key = new ServiceKey(2048, 6, 0, "eth_type=2048|ip_proto=6|service_port=0");
        final PairConsensusBucket bucket = new PairConsensusBucket("lsr1_lsr4", key,
                Optional.of(evidence("LEFT", "lsr1_to_lsr4", "SSH", "ssh_tunnel_policy", 3, 3, key)),
                Optional.of(evidence("RIGHT", "lsr4_to_lsr1", "STREAMING", "streaming_tunnel_policy", 3, 3, key)),
                NOW, NOW, NOW.plusSeconds(10));
        final var decision = service().evaluate(bucket, Optional.empty(), NOW);
        assertEquals(PairConsensusStatus.CONSENSUS_CONFLICT_EQUAL_PRIORITY_UNRESOLVED, decision.consensusStatus());
        assertTrue(decision.selectedCandidate().isEmpty());
    }

    private static PairPolicyConsensusService service() {
        return new PairPolicyConsensusService(true, false, PairConsensusEqualPriorityAction.KEEP_CURRENT_OR_DEFER,
                new ServiceKeyResolver());
    }

    private static DirectionalPolicyEvidence evidence(final String side, final String direction, final String className,
            final String profile, final int setup, final int hold, final ServiceKey key) {
        final String switchName = "LEFT".equals(side) ? "ECHO" : "FOXTROT";
        return new DirectionalPolicyEvidence("lsr1_lsr4", direction, switchName, "host", new PacketFeatures(2048, 6, 22, 0),
                key, className, profile, 0, 0, 10_000, "SZiWgA==", setup, hold, "classifier-policy-v1",
                className + "-hash", NOW, NOW.plusSeconds(10));
    }
}
