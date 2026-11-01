/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sma.sdn.classification.ClassificationService;
import com.sma.sdn.config.AppConfig;
import com.sma.sdn.metrics.SdnMplsMlMetrics;
import com.sma.sdn.model.ClassificationLookupResult;
import com.sma.sdn.model.ClassificationResult;
import com.sma.sdn.model.DirectionalLspApplicationRecord;
import com.sma.sdn.model.FlowDirection;
import com.sma.sdn.model.PacketClassificationContext;
import com.sma.sdn.model.PacketFeatures;
import com.sma.sdn.model.PairConsensusStatus;
import com.sma.sdn.model.PairPolicyCandidate;
import com.sma.sdn.model.PairPolicyDecision;
import com.sma.sdn.model.PathConstraints;
import com.sma.sdn.model.ServiceKey;
import com.sma.sdn.model.TrafficPolicy;
import com.sma.sdn.packet.PacketInFeatureExtractor;
import com.sma.sdn.policy.PairPolicyCoordinator;
import com.sma.sdn.policy.PairPolicyHashService;
import com.sma.sdn.policy.ServiceKeyResolver;
import com.sma.sdn.registry.DirectionRegistry;
import com.sma.sdn.registry.TunnelPairRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;

/** Tests terminal control-cycle metrics independently from classification-cache metrics. */
class SdnMplsMlWorkflowServiceTest {
    private static final String CLASS_NAME = "TLS";
    private static final ServiceKey SERVICE_KEY = new ServiceKey(2048, 6, 443,
            "eth_type=2048|ip_proto=6|service_port=443");

    @Test
    void cachedClassificationRecordsPendingConsensusInsteadOfCachedHit() {
        final Harness harness = harness(pendingDecision());

        harness.workflow().handlePacket(mock(PacketReceived.class));

        assertControlCycle(harness.metrics(), "pending_consensus");
        assertNoCachedControlCycle(harness.metrics());
    }

    @Test
    void cachedClassificationRecordsDeferredWhenNoLspApplicationIsNeeded() {
        final Harness harness = harness(deferredDecision());

        harness.workflow().handlePacket(mock(PacketReceived.class));

        assertControlCycle(harness.metrics(), "deferred");
        assertNoCachedControlCycle(harness.metrics());
    }

    @Test
    void cachedClassificationRecordsSuccessWhenEroIsConfirmedButBandwidthIsStillPending() {
        final Harness harness = harness(successWithUnconfirmedBandwidthDecision());

        harness.workflow().handlePacket(mock(PacketReceived.class));

        assertControlCycle(harness.metrics(), "success");
        assertNoCachedControlCycle(harness.metrics());
    }

    @Test
    void cachedClassificationRecordsFailureWhenPolicyApplicationThrows() {
        final Harness harness = harness(null);
        when(harness.coordinator().handleEvidence(any(), any()))
                .thenThrow(new IllegalStateException("PCEP confirmation timed out"));

        assertThrows(IllegalStateException.class, () -> harness.workflow().handlePacket(mock(PacketReceived.class)));

        assertControlCycle(harness.metrics(), "failed");
        assertNoCachedControlCycle(harness.metrics());
    }

    private static Harness harness(final PairPolicyDecision decision) {
        final AppConfig config = AppConfig.from(Map.of());
        final PacketInFeatureExtractor extractor = mock(PacketInFeatureExtractor.class);
        final ClassificationService classificationService = mock(ClassificationService.class);
        final PairPolicyCoordinator coordinator = mock(PairPolicyCoordinator.class);
        final SdnMplsMlMetrics metrics = new SdnMplsMlMetrics();
        final PacketClassificationContext context = new PacketClassificationContext(
                "openflow:1", "openflow:1:1", "ECHO", "host1",
                new PacketFeatures(2048, 6, 52_000, 443), FlowDirection.HEADEND_TO_TAILEND, Instant.now());

        when(extractor.extract(any())).thenReturn(Optional.of(context));
        when(classificationService.classifyOrGetCached(context))
                .thenReturn(new ClassificationLookupResult(classification(), true));
        if (decision != null) {
            when(coordinator.handleEvidence(any(), any())).thenReturn(decision);
        }

        final SdnMplsMlWorkflowService workflow = new SdnMplsMlWorkflowService(
                config, extractor, classificationService, new DirectionRegistry(config), new TunnelPairRegistry(config),
                new ServiceKeyResolver(), new PairPolicyHashService(config.pairPolicyHashVersion()), coordinator,
                metrics, () -> true, () -> true);
        return new Harness(workflow, coordinator, metrics);
    }

    private static ClassificationResult classification() {
        final Instant now = Instant.now();
        return new ClassificationResult("request-1", "model-1", 1, CLASS_NAME, 0.99d,
                Map.of(CLASS_NAME, 0.99d), new TrafficPolicy("tls", 0, 0,
                        new PathConstraints(10_000, 1, 1), false, ""),
                1.0d, now, now.plusSeconds(60));
    }

    private static PairPolicyDecision pendingDecision() {
        return new PairPolicyDecision("lsr1_lsr4", SERVICE_KEY, PairConsensusStatus.PENDING_ONE_SIDE,
                Optional.empty(), null, Optional.empty(), List.of(), "waiting for reverse evidence");
    }

    private static PairPolicyDecision deferredDecision() {
        return new PairPolicyDecision("lsr1_lsr4", SERVICE_KEY, PairConsensusStatus.CONSENSUS_MATCH,
                Optional.of(candidate()), null, Optional.empty(), List.of(), "policy already retained");
    }

    private static PairPolicyDecision successWithUnconfirmedBandwidthDecision() {
        final Instant now = Instant.now();
        final DirectionalLspApplicationRecord application = new DirectionalLspApplicationRecord(
                UUID.randomUUID(), "workflow", 1L, "lsr1_lsr4", "lsr1_to_lsr4", "policy-hash", "desired-hash",
                "delegated-lsp", "pcc://lsr1", 1L, "ACCEPTED_PCEP_BANDWIDTH_UNCONFIRMED", 200,
                true, true, false, now, now);
        return new PairPolicyDecision("lsr1_lsr4", SERVICE_KEY, PairConsensusStatus.CONSENSUS_MATCH,
                Optional.of(candidate()), null, Optional.empty(), List.of(application), "ERO confirmed");
    }

    private static PairPolicyCandidate candidate() {
        return new PairPolicyCandidate("lsr1_lsr4", SERVICE_KEY, "lsr1_to_lsr4", "ECHO", CLASS_NAME, "tls",
                0, 0, 10_000, "SZiWgA==", 1, 1, "v1", "policy-hash", PairConsensusStatus.CONSENSUS_MATCH,
                Instant.now());
    }

    private static void assertControlCycle(final SdnMplsMlMetrics metrics, final String outcome) {
        final String exposition = metrics.renderPrometheusText();
        assertMetricSample(exposition, "sma_control_cycle_total", outcome);
        assertMetricSample(exposition, "sma_control_cycle_duration_seconds_count", outcome);
    }

    private static void assertMetricSample(final String exposition, final String metric, final String outcome) {
        assertTrue(exposition.lines().anyMatch(line -> line.startsWith(metric)
                && line.contains("class_name=\"" + CLASS_NAME + "\"")
                && line.contains("outcome=\"" + outcome + "\"")
                && line.endsWith(" 1")));
    }

    private static void assertNoCachedControlCycle(final SdnMplsMlMetrics metrics) {
        final String exposition = metrics.renderPrometheusText();
        assertFalse(exposition.contains("outcome=\"cached_hit\""));
        assertEquals(0L, metrics.value("sma_classification_cache_hit_total"));
    }

    private record Harness(SdnMplsMlWorkflowService workflow, PairPolicyCoordinator coordinator,
            SdnMplsMlMetrics metrics) {
    }
}
