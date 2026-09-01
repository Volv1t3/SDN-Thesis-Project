/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.policy;

import com.sma.sdn.config.AppConfig;
import com.sma.sdn.metrics.SdnMplsMlMetrics;
import com.sma.sdn.model.ActivePairPolicyState;
import com.sma.sdn.model.DirectionalLspApplicationRecord;
import com.sma.sdn.model.DirectionalPolicyEvidence;
import com.sma.sdn.model.PairConsensusBucket;
import com.sma.sdn.model.PairConsensusDecision;
import com.sma.sdn.model.PairPolicyCandidate;
import com.sma.sdn.model.PairPolicyDecision;
import com.sma.sdn.model.PairPolicyLspApplicationScope;
import com.sma.sdn.model.PolicyPreemptionDecision;
import com.sma.sdn.model.PolicyPreemptionDecisionType;
import com.sma.sdn.model.TunnelDirection;
import com.sma.sdn.model.WorkflowContext;
import com.sma.sdn.registry.ActivePairPolicyRegistry;
import com.sma.sdn.registry.DirectionalClassificationEvidenceRegistry;
import com.sma.sdn.registry.TunnelPairRegistry;
import com.sma.sdn.tunnel.DirectionalLspApplicationService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** Serializes pair-level evidence, consensus, preemption, and child-LSP application. */
public final class PairPolicyCoordinator {
    private final AppConfig config;
    private final TunnelPairRegistry pairRegistry;
    private final DirectionalClassificationEvidenceRegistry evidenceRegistry;
    private final PairPolicyConsensusService consensusService;
    private final ActivePairPolicyRegistry activeRegistry;
    private final PolicyPreemptionEvaluator preemptionEvaluator;
    private final DirectionalLspApplicationService lspApplicationService;
    private final SdnMplsMlMetrics metrics;
    private final ConcurrentHashMap<String, ReentrantLock> locksByPair = new ConcurrentHashMap<>();

    public PairPolicyCoordinator(final AppConfig config, final TunnelPairRegistry pairRegistry,
            final DirectionalClassificationEvidenceRegistry evidenceRegistry,
            final PairPolicyConsensusService consensusService, final ActivePairPolicyRegistry activeRegistry,
            final PolicyPreemptionEvaluator preemptionEvaluator, final DirectionalLspApplicationService lspApplicationService,
            final SdnMplsMlMetrics metrics) {
        this.config = config;
        this.pairRegistry = pairRegistry;
        this.evidenceRegistry = evidenceRegistry;
        this.consensusService = consensusService;
        this.activeRegistry = activeRegistry;
        this.preemptionEvaluator = preemptionEvaluator;
        this.lspApplicationService = lspApplicationService;
        this.metrics = metrics;
    }

    public PairPolicyDecision handleEvidence(final DirectionalPolicyEvidence evidence, final WorkflowContext workflowContext) {
        final ReentrantLock lock = locksByPair.computeIfAbsent(evidence.pairKey(), ignored -> new ReentrantLock());
        lock.lock();
        try {
            final Instant now = Instant.now();
            if (!config.pairConsensusEnabled()) {
                return new PairPolicyDecision(evidence.pairKey(), evidence.serviceKey(),
                        com.sma.sdn.model.PairConsensusStatus.PENDING_ONE_SIDE, Optional.empty(), null,
                        activeRegistry.findActive(evidence.pairKey(), now), List.of(), "pair consensus is disabled");
            }
            final PairConsensusBucket bucket = evidenceRegistry.recordEvidence(evidence);
            metrics.increment("sma_directional_evidence_recorded_total");
            final Optional<ActivePairPolicyState> activeBefore = activeRegistry.findActive(evidence.pairKey(), now);
            final PairConsensusDecision consensus = consensusService.evaluate(bucket, activeBefore, now);
            if (consensus.selectedCandidate().isEmpty()) {
                final boolean pending = consensus.consensusStatus().name().equals("PENDING_ONE_SIDE");
                metrics.increment(pending ? "sma_pair_consensus_pending_total" : "sma_pair_consensus_unresolved_total");
                metrics.increment(pending ? "sma_packet_workflow_pending_consensus_total" : "sma_packet_workflow_deferred_total");
                return new PairPolicyDecision(evidence.pairKey(), evidence.serviceKey(), consensus.consensusStatus(),
                        Optional.empty(), null, activeBefore, List.of(), consensus.conflictResolutionReason());
            }
            final PairPolicyCandidate candidate = consensus.selectedCandidate().orElseThrow();
            if (consensus.consensusStatus().name().contains("MATCH")) {
                metrics.increment("sma_pair_consensus_match_total");
            } else if (consensus.consensusStatus().name().contains("SERVICE_KEY")) {
                metrics.increment("sma_pair_consensus_service_key_selected_total");
            } else if (consensus.consensusStatus().name().contains("PRIORITY")) {
                metrics.increment("sma_pair_consensus_priority_selected_total");
            }
            if (activeBefore.isPresent()) {
                final PolicyPreemptionDecision preemption = preemptionEvaluator.evaluate(activeBefore.orElseThrow(), candidate, now);
                if (preemption.type() == PolicyPreemptionDecisionType.SAME_POLICY_REFRESH) {
                    final ActivePairPolicyState refreshed = activeRegistry.refresh(candidate.pairKey(), now,
                            config.activePairPolicyIdleTtl());
                    metrics.increment("sma_pair_policy_refresh_total");
                    return new PairPolicyDecision(candidate.pairKey(), candidate.serviceKey(), consensus.consensusStatus(),
                            Optional.of(candidate), preemption.type(), Optional.of(refreshed), List.of(), preemption.reason());
                }
                if (!config.pairPolicyPriorityPreemptionEnabled()) {
                    metrics.increment("sma_pair_policy_deferred_total");
                    return new PairPolicyDecision(candidate.pairKey(), candidate.serviceKey(), consensus.consensusStatus(),
                            Optional.of(candidate), PolicyPreemptionDecisionType.ACTIVE_POLICY_RETAINED_WEAKER_INCOMING,
                            activeBefore, List.of(), "priority preemption is disabled");
                }
                if (!preemption.appliesIncoming()) {
                    metrics.increment("sma_pair_policy_deferred_total");
                    metrics.increment("sma_packet_workflow_deferred_total");
                    return new PairPolicyDecision(candidate.pairKey(), candidate.serviceKey(), consensus.consensusStatus(),
                            Optional.of(candidate), preemption.type(), activeBefore, List.of(), preemption.reason());
                }
                final ActivePairPolicyState installed = apply(candidate, evidence.directionKey(), workflowContext, now);
                metrics.increment(preemption.type() == PolicyPreemptionDecisionType.INCOMING_PRIORITY_PREEMPTS
                        ? "sma_pair_policy_preempt_total" : "sma_pair_policy_expired_total");
                return new PairPolicyDecision(candidate.pairKey(), candidate.serviceKey(), consensus.consensusStatus(),
                        Optional.of(candidate), preemption.type(), Optional.of(installed),
                        List.copyOf(installed.lspApplications().values()), preemption.reason());
            }
            final ActivePairPolicyState installed = apply(candidate, evidence.directionKey(), workflowContext, now);
            return new PairPolicyDecision(candidate.pairKey(), candidate.serviceKey(), consensus.consensusStatus(),
                    Optional.of(candidate), PolicyPreemptionDecisionType.ACTIVE_EXPIRED_REPLACE, Optional.of(installed),
                    List.copyOf(installed.lspApplications().values()), "no active pair policy");
        } finally {
            lock.unlock();
        }
    }

    private ActivePairPolicyState apply(final PairPolicyCandidate candidate, final String observedDirection,
            final WorkflowContext workflowContext, final Instant now) {
        final List<TunnelDirection> directions = config.pairPolicyLspApplicationScope()
                == PairPolicyLspApplicationScope.BIDIRECTIONAL_PAIR
                ? pairRegistry.requireManagedDirections(candidate.pairKey())
                : List.of(pairRegistry.requirePairForDirection(observedDirection).forwardDirection().directionKey()
                        .equals(observedDirection)
                        ? pairRegistry.requirePairForDirection(observedDirection).forwardDirection()
                        : pairRegistry.requirePairForDirection(observedDirection).reverseDirection());
        final List<DirectionalLspApplicationRecord> applications = new ArrayList<>();
        for (TunnelDirection direction : directions) {
            applications.add(lspApplicationService.applyPolicyToDirection(candidate, direction, workflowContext));
        }
        final boolean failed = applications.stream().anyMatch(record -> record.status().startsWith("FAILED"));
        if (failed && config.lspApplicationRequireAllDirections()) {
            throw new IllegalStateException("No se aplico la politica de par porque fallo al menos un LSP delegado");
        }
        final var children = new LinkedHashMap<String, DirectionalLspApplicationRecord>();
        applications.forEach(application -> children.put(application.directionKey(), application));
        final ActivePairPolicyState installed = activeRegistry.installOrReplace(candidate, children, now,
                config.activePairPolicyIdleTtl());
        metrics.increment("sma_pair_policy_active_total");
        return installed;
    }
}
