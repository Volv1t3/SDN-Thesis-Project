/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sma.sdn.config.AppConfig;
import com.sma.sdn.metrics.SdnMplsMlMetrics;
import com.sma.sdn.model.DirectionalLspApplicationRecord;
import com.sma.sdn.model.DirectionalPolicyEvidence;
import com.sma.sdn.model.PacketFeatures;
import com.sma.sdn.model.PairConsensusStatus;
import com.sma.sdn.model.PolicyPreemptionDecisionType;
import com.sma.sdn.model.ServiceKey;
import com.sma.sdn.model.TunnelDirection;
import com.sma.sdn.model.WorkflowContext;
import com.sma.sdn.registry.ActivePairPolicyRegistry;
import com.sma.sdn.registry.DirectionalClassificationEvidenceRegistry;
import com.sma.sdn.registry.TunnelPairRegistry;
import com.sma.sdn.tunnel.DirectionalLspApplicationService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tests pair-policy direction selection and active-state acceptance without contacting PCEP. */
class PairPolicyCoordinatorTest {
    private static final String PAIR_KEY = "lsr1_lsr4";
    private static final ServiceKey SERVICE_KEY = new ServiceKey(2048, 6, 22,
            "eth_type=2048|ip_proto=6|service_port=22");
    private static final WorkflowContext CONTEXT = new WorkflowContext("test-workflow", 1L);

    @Test
    void admittedPolicyAppliesToBothDirectionsBeforeInstallation() {
        final Harness harness = harness();
        when(harness.lspService().applyPolicyToDirection(any(), any(), any()))
                .thenAnswer(invocation -> record(invocation.getArgument(1), "UPDATED"));

        assertTrue(harness.coordinator().handleEvidence(evidence("LEFT", "SSH", 2, 1), CONTEXT)
                .candidate().isEmpty());
        verifyNoInteractions(harness.lspService());

        final var decision = harness.coordinator().handleEvidence(evidence("RIGHT", "SSH", 2, 1), CONTEXT);
        assertEquals(2, decision.applications().size());
        assertEquals(2, decision.activePolicy().orElseThrow().lspApplications().size());
        verify(harness.lspService()).applyPolicyToDirection(any(), eq(harness.config().headendToTailend()), eq(CONTEXT));
        verify(harness.lspService()).applyPolicyToDirection(any(), eq(harness.config().tailendToHeadend()), eq(CONTEXT));
    }

    @Test
    void secondDirectionFailureDoesNotInstallCandidateAsActive() {
        final Harness harness = harness();
        when(harness.lspService().applyPolicyToDirection(any(), any(), any()))
                .thenAnswer(invocation -> {
                    final TunnelDirection direction = invocation.getArgument(1);
                    return record(direction, direction.equals(harness.config().tailendToHeadend())
                            ? "FAILED_UPDATE" : "UPDATED");
                });

        harness.coordinator().handleEvidence(evidence("LEFT", "SSH", 2, 1), CONTEXT);
        assertThrows(IllegalStateException.class,
                () -> harness.coordinator().handleEvidence(evidence("RIGHT", "SSH", 2, 1), CONTEXT));
        assertTrue(harness.activeRegistry().findActive(PAIR_KEY, Instant.now()).isEmpty());
        verify(harness.lspService()).applyPolicyToDirection(any(), eq(harness.config().headendToTailend()), eq(CONTEXT));
        verify(harness.lspService()).applyPolicyToDirection(any(), eq(harness.config().tailendToHeadend()), eq(CONTEXT));
    }

    @Test
    void firstDirectionFailureDoesNotInstallCandidateAsActive() {
        final Harness harness = harness();
        when(harness.lspService().applyPolicyToDirection(any(), any(), any()))
                .thenAnswer(invocation -> {
                    final TunnelDirection direction = invocation.getArgument(1);
                    return record(direction, direction.equals(harness.config().headendToTailend())
                            ? "FAILED_UPDATE" : "UPDATED");
                });

        harness.coordinator().handleEvidence(evidence("LEFT", "SSH", 2, 1), CONTEXT);
        assertThrows(IllegalStateException.class,
                () -> harness.coordinator().handleEvidence(evidence("RIGHT", "SSH", 2, 1), CONTEXT));
        assertTrue(harness.activeRegistry().findActive(PAIR_KEY, Instant.now()).isEmpty());
        verify(harness.lspService()).applyPolicyToDirection(any(), eq(harness.config().headendToTailend()), eq(CONTEXT));
        verify(harness.lspService()).applyPolicyToDirection(any(), eq(harness.config().tailendToHeadend()), eq(CONTEXT));
    }

    @Test
    void weakerConsensusWinnerDoesNotTouchEitherDirectionOrRefreshActiveTtl() {
        final Harness harness = harness();
        when(harness.lspService().applyPolicyToDirection(any(), any(), any()))
                .thenAnswer(invocation -> record(invocation.getArgument(1), "UPDATED"));
        harness.coordinator().handleEvidence(evidence("LEFT", "HTTP", 6, 5), CONTEXT);
        harness.coordinator().handleEvidence(evidence("RIGHT", "HTTP", 6, 5), CONTEXT);
        harness.coordinator().handleEvidence(evidence("LEFT", "FTP", 6, 6), CONTEXT);
        final var before = harness.activeRegistry().findActive(PAIR_KEY, Instant.now()).orElseThrow();
        clearInvocations(harness.lspService());

        final var decision = harness.coordinator().handleEvidence(evidence("RIGHT", "FTP", 6, 6), CONTEXT);
        assertEquals(PolicyPreemptionDecisionType.ACTIVE_POLICY_RETAINED_WEAKER_INCOMING,
                decision.preemptionDecision());
        assertEquals(before, harness.activeRegistry().findActive(PAIR_KEY, Instant.now()).orElseThrow());
        verifyNoInteractions(harness.lspService());
    }

    @Test
    void matchingActiveHashRefreshesWithoutAnotherLspApplication() {
        final Harness harness = harness();
        when(harness.lspService().applyPolicyToDirection(any(), any(), any()))
                .thenAnswer(invocation -> record(invocation.getArgument(1), "UPDATED"));
        harness.coordinator().handleEvidence(evidence("LEFT", "SSH", 2, 1), CONTEXT);
        harness.coordinator().handleEvidence(evidence("RIGHT", "SSH", 2, 1), CONTEXT);
        final var before = harness.activeRegistry().findActive(PAIR_KEY, Instant.now()).orElseThrow();
        clearInvocations(harness.lspService());

        final var decision = harness.coordinator().handleEvidence(evidence("LEFT", "SSH", 2, 1), CONTEXT);
        final var after = harness.activeRegistry().findActive(PAIR_KEY, Instant.now()).orElseThrow();
        assertEquals(PolicyPreemptionDecisionType.SAME_POLICY_REFRESH, decision.preemptionDecision());
        assertEquals(before.generation(), after.generation());
        assertTrue(!after.lastRefreshedAt().isBefore(before.lastRefreshedAt()));
        verifyNoInteractions(harness.lspService());
    }

    @Test
    void preservingMatchingActiveHashDoesNotCountAsUnresolved() {
        final Harness harness = harness();
        when(harness.lspService().applyPolicyToDirection(any(), any(), any()))
                .thenAnswer(invocation -> record(invocation.getArgument(1), "UPDATED"));
        harness.coordinator().handleEvidence(evidence("LEFT", "HTTP", 6, 5), CONTEXT);
        harness.coordinator().handleEvidence(evidence("RIGHT", "HTTP", 6, 5), CONTEXT);
        clearInvocations(harness.lspService());

        final var decision = harness.coordinator().handleEvidence(evidence("LEFT", "OTHER", 6, 5), CONTEXT);
        assertEquals(PairConsensusStatus.CONSENSUS_CONFLICT_CURRENT_POLICY_PRESERVED, decision.consensusStatus());
        assertEquals(0L, harness.metrics().value("sma_pair_consensus_unresolved_total"));
        verifyNoInteractions(harness.lspService());
    }

    private static Harness harness() {
        final AppConfig config = AppConfig.from(Map.of(
                "SMA_PAIR_CONSENSUS_ENABLED", "false",
                "SMA_PAIR_CONSENSUS_REQUIRE_BOTH_DIRECTIONS", "false",
                "SMA_PAIR_CONSENSUS_SINGLE_SIDE_PROVISIONAL_ENABLED", "true",
                "SMA_PAIR_POLICY_PREEMPTION_MODE", "DISABLED",
                "SMA_PAIR_POLICY_LSP_APPLICATION_SCOPE", "OBSERVED_DIRECTION_ONLY",
                "SMA_LSP_APPLICATION_REQUIRE_ALL_DIRECTIONS", "false"));
        final TunnelPairRegistry pairRegistry = new TunnelPairRegistry(config);
        final SdnMplsMlMetrics metrics = new SdnMplsMlMetrics();
        final ActivePairPolicyRegistry activeRegistry = new ActivePairPolicyRegistry();
        final DirectionalLspApplicationService lspService = mock(DirectionalLspApplicationService.class);
        final PairPolicyCoordinator coordinator = new PairPolicyCoordinator(config, pairRegistry,
                new DirectionalClassificationEvidenceRegistry(pairRegistry), new PairPolicyConsensusService(),
                activeRegistry, new PolicyPreemptionEvaluator(), lspService, metrics);
        return new Harness(config, coordinator, activeRegistry, lspService, metrics);
    }

    private static DirectionalPolicyEvidence evidence(final String side, final String className,
            final int setup, final int hold) {
        final Instant observedAt = Instant.now();
        final String direction = "LEFT".equals(side) ? "lsr1_to_lsr4" : "lsr4_to_lsr1";
        final String switchName = "LEFT".equals(side) ? "ECHO" : "FOXTROT";
        return new DirectionalPolicyEvidence(PAIR_KEY, direction, switchName, "host",
                new PacketFeatures(2048, 6, 22, 0), SERVICE_KEY, className, className.toLowerCase() + "_policy",
                0, 0, 10_000, "SZiWgA==", setup, hold, "v1", className + "-hash",
                observedAt, observedAt.plusSeconds(60));
    }

    private static DirectionalLspApplicationRecord record(final TunnelDirection direction, final String status) {
        return new DirectionalLspApplicationRecord(UUID.randomUUID(), CONTEXT.workflowId(),
                CONTEXT.packetSequence(), PAIR_KEY, direction.directionKey(), "SSH-hash", "desired-hash",
                "delegated-lsp", direction.source().pccNode(), 1L, status, 200, true, true, true,
                Instant.now(), Instant.now());
    }

    private record Harness(AppConfig config, PairPolicyCoordinator coordinator,
            ActivePairPolicyRegistry activeRegistry, DirectionalLspApplicationService lspService,
            SdnMplsMlMetrics metrics) {
    }
}
