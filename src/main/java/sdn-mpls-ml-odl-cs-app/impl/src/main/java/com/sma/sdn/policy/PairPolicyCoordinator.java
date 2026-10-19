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
import com.sma.sdn.model.PairConsensusStatus;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>Coordinates evidence, consensus and active-policy decisions under a per-pair
 * lock. It invokes directional LSP application before installing the resulting
 * records in the active registry.</p>
 * <p>Configuration determines whether consensus and preemption are enabled and
 * whether policy application targets the observed direction or the managed pair.</p>
 */
public final class PairPolicyCoordinator {
    /** <p>Consensus, preemption, application scope and expiration settings.</p> */
    private final AppConfig config;
    /** <p>Managed tunnel-pair and direction lookup.</p> */
    private final TunnelPairRegistry pairRegistry;
    /** <p>Store for observations used to build consensus buckets.</p> */
    private final DirectionalClassificationEvidenceRegistry evidenceRegistry;
    /** <p>Evaluator selecting a candidate from directional evidence.</p> */
    private final PairPolicyConsensusService consensusService;
    /** <p>Store for active pair policies and their child application records.</p> */
    private final ActivePairPolicyRegistry activeRegistry;
    /** <p>Comparator for incoming and active policy priorities.</p> */
    private final PolicyPreemptionEvaluator preemptionEvaluator;
    /** <p>Applies the candidate to each selected tunnel direction.</p> */
    private final DirectionalLspApplicationService lspApplicationService;
    /** <p>Records consensus and policy decision counters.</p> */
    private final SdnMplsMlMetrics metrics;
    /** <p>Per-pair locks serializing evidence and subsequent state changes.</p> */
    private final ConcurrentHashMap<String, ReentrantLock> locksByPair = new ConcurrentHashMap<>();

    /**
     * <p>Creates a coordinator using the supplied registries and services.</p>
     * <p>Construction proceeds as follows:</p>
     * <ol><li>Retain the configuration, collaborators and metrics for subsequent decisions.</li></ol>
     * @param config application and consensus settings
     * @param pairRegistry managed pair lookup
     * @param evidenceRegistry directional evidence store
     * @param consensusService consensus evaluator
     * @param activeRegistry active policy store
     * @param preemptionEvaluator active and incoming policy comparator
     * @param lspApplicationService directional LSP policy application service
     * @param metrics decision counters
     */
    public PairPolicyCoordinator(final AppConfig config, final TunnelPairRegistry pairRegistry,
            final DirectionalClassificationEvidenceRegistry evidenceRegistry,
            final PairPolicyConsensusService consensusService, final ActivePairPolicyRegistry activeRegistry,
            final PolicyPreemptionEvaluator preemptionEvaluator,
            final DirectionalLspApplicationService lspApplicationService,
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

    /**
     * <p>Processes an observation and, when permitted, applies a selected policy.
     * Decisions for the same pair execute serially; distinct pairs use separate locks.</p>
     * <p>The workflow proceeds as follows:</p>
     * <ol>
     *   <li>Acquire the pair lock and return without recording evidence when consensus is disabled.</li>
     *   <li>Record the observation, find the active policy, evaluate consensus and record metrics.</li>
     *   <li>Return pending or unresolved results when no candidate was selected.</li>
     *   <li>For an active policy, refresh an identical hash, retain it when preemption is
     *       disabled or rejected, or apply the candidate when replacement is allowed.</li>
     *   <li>Without an active policy, apply the candidate; return the installed state
     *       and directional application records, then release the lock in all cases.</li>
     * </ol>
     * <p>{@link DirectionalLspApplicationService} may perform external work before
     * the active registry is updated; the lock does not roll back completed operations.</p>
     * @param evidence directional classification and policy observation
     * @param workflowContext packet workflow context passed to LSP application
     * @return consensus and preemption outcome, active state and application records
     * @throws IllegalStateException if required directional application fails or a collaborator rejects state
     */
    public PairPolicyDecision handleEvidence(
            final DirectionalPolicyEvidence evidence,
            final WorkflowContext workflowContext) {
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
            recordConsensus(consensus.consensusStatus());
            if (consensus.selectedCandidate().isEmpty()) {
                final boolean pending = consensus.consensusStatus().name().equals("PENDING_ONE_SIDE");
                metrics.increment(pending ? "sma_pair_consensus_pending_total" : "sma_pair_consensus_unresolved_total");
                metrics.increment(pending ? "sma_packet_workflow_pending_consensus_total"
                        : "sma_packet_workflow_deferred_total");
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
                final PolicyPreemptionDecision preemption = preemptionEvaluator.evaluate(
                        activeBefore.orElseThrow(), candidate, now);
                if (preemption.type() == PolicyPreemptionDecisionType.SAME_POLICY_REFRESH) {
                    final ActivePairPolicyState refreshed = activeRegistry.refresh(candidate.pairKey(), now,
                            config.activePairPolicyIdleTtl());
                    metrics.increment("sma_pair_policy_refresh_total");
                    return new PairPolicyDecision(
                            candidate.pairKey(), candidate.serviceKey(), consensus.consensusStatus(),
                            Optional.of(candidate), preemption.type(), Optional.of(refreshed), List.of(),
                            preemption.reason());
                }
                if (!config.pairPolicyPriorityPreemptionEnabled()) {
                    metrics.increment("sma_pair_policy_deferred_total");
                    metrics.increment("sma_pair_policy_retained_stronger_active_total");
                    return new PairPolicyDecision(
                            candidate.pairKey(), candidate.serviceKey(), consensus.consensusStatus(),
                            Optional.of(candidate), PolicyPreemptionDecisionType.ACTIVE_POLICY_RETAINED_WEAKER_INCOMING,
                            activeBefore, List.of(), "priority preemption is disabled");
                }
                if (!preemption.appliesIncoming()) {
                    metrics.increment("sma_pair_policy_deferred_total");
                    metrics.increment("sma_packet_workflow_deferred_total");
                    if (preemption.type() == PolicyPreemptionDecisionType.ACTIVE_POLICY_RETAINED_WEAKER_INCOMING) {
                        metrics.increment("sma_pair_policy_retained_stronger_active_total");
                    } else if (preemption.type()
                            == PolicyPreemptionDecisionType.ACTIVE_POLICY_RETAINED_EQUAL_PRIORITY_DIFFERENT_POLICY) {
                        metrics.increment("sma_pair_policy_retained_equal_priority_total");
                    }
                    return new PairPolicyDecision(
                            candidate.pairKey(), candidate.serviceKey(), consensus.consensusStatus(),
                            Optional.of(candidate), preemption.type(), activeBefore, List.of(),
                            preemption.reason());
                }
                final ActivePairPolicyState installed = apply(candidate, evidence.directionKey(), workflowContext, now);
                metrics.increment(preemption.type() == PolicyPreemptionDecisionType.INCOMING_PRIORITY_PREEMPTS
                        ? "sma_pair_policy_preempt_total" : "sma_pair_policy_expired_total");
                metrics.increment(preemption.type() == PolicyPreemptionDecisionType.INCOMING_PRIORITY_PREEMPTS
                        ? "sma_pair_policy_preempted_by_priority_total" : "sma_pair_policy_replaced_expired_total");
                return new PairPolicyDecision(
                        candidate.pairKey(), candidate.serviceKey(), consensus.consensusStatus(),
                        Optional.of(candidate), preemption.type(), Optional.of(installed),
                        List.copyOf(installed.lspApplications().values()), preemption.reason());
            }
            final ActivePairPolicyState installed = apply(candidate, evidence.directionKey(), workflowContext, now);
            metrics.increment("sma_pair_policy_replaced_expired_total");
            return new PairPolicyDecision(
                    candidate.pairKey(), candidate.serviceKey(), consensus.consensusStatus(),
                    Optional.of(candidate), PolicyPreemptionDecisionType.ACTIVE_EXPIRED_REPLACE, Optional.of(installed),
                    List.copyOf(installed.lspApplications().values()), "no active pair policy");
        } finally {
            lock.unlock();
        }
    }

    /**
     * <p>Applies a candidate to the configured directions and records the active pair state.</p>
     * <p>Application proceeds as follows:</p>
     * <ol>
     *   <li>Resolve either all managed pair directions or the observed direction.</li>
     *   <li>Apply the candidate to each direction and collect the returned records.</li>
     *   <li>If any status starts with {@code FAILED}, reject installation when all
     *       directions are required; earlier directional operations are not rolled back.</li>
     *   <li>Index records by direction, install the active state with its idle TTL and increment metrics.</li>
     * </ol>
     * @param candidate consensus-selected policy
     * @param observedDirection direction associated with the current evidence
     * @param workflowContext packet context for directional application
     * @param now timestamp used to create the active state
     * @return newly installed active policy state
     * @throws IllegalStateException if a required direction fails or a pair cannot be resolved
     */
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

    /**
     * <p>Records the consensus outcome and selected specialized counters.</p>
     * <p>Metric recording proceeds as follows:</p>
     * <ol><li>Increment the labeled decision counter and the corresponding pending,
     * equal-priority, or provisional counter when applicable.</li></ol>
     * @param status outcome returned by the consensus evaluator
     */
    private void recordConsensus(final PairConsensusStatus status) {
        metrics.incrementCounter("sma_pair_consensus_decision_total", Map.of("decision", status.name()));
        if (status == PairConsensusStatus.PENDING_ONE_SIDE) {
            metrics.increment("sma_pair_consensus_require_both_block_total");
        } else if (status == PairConsensusStatus.CONSENSUS_CONFLICT_EQUAL_PRIORITY_UNRESOLVED) {
            metrics.increment("sma_pair_consensus_equal_priority_total");
        } else if (status == PairConsensusStatus.CONSENSUS_TIMEOUT_SINGLE_SIDE_PROVISIONAL) {
            metrics.increment("sma_pair_consensus_single_side_provisional_total");
        }
    }
}
