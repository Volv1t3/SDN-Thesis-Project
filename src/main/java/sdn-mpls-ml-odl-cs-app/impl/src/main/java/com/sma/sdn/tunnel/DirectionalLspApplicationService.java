/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.tunnel;

import com.sma.sdn.metrics.SdnMplsMlMetrics;
import com.sma.sdn.model.CalculatedPath;
import com.sma.sdn.model.DelegatedLspRecord;
import com.sma.sdn.model.DirectionalLspApplicationRecord;
import com.sma.sdn.model.DirectionalLspDesiredState;
import com.sma.sdn.model.EroSubobject;
import com.sma.sdn.model.PairPolicyCandidate;
import com.sma.sdn.model.PathConstraints;
import com.sma.sdn.model.TunnelDirection;
import com.sma.sdn.model.WorkflowContext;
import com.sma.sdn.path.PathComputationService;
import com.sma.sdn.policy.PairPolicyHashService;
import com.sma.sdn.topology.BandwidthTranslator;
import java.time.Instant;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** Applies an already-selected pair policy to one directional delegated LSP. */
public final class DirectionalLspApplicationService {
    private final PathComputationService pathComputationService;
    private final DelegatedLspService delegatedLspService;
    private final PairPolicyHashService hashService;
    private final SdnMplsMlMetrics metrics;
    private final ConcurrentHashMap<String, ReentrantLock> locksByDirection = new ConcurrentHashMap<>();

    public DirectionalLspApplicationService(final PathComputationService pathComputationService,
            final DelegatedLspService delegatedLspService, final PairPolicyHashService hashService,
            final SdnMplsMlMetrics metrics) {
        this.pathComputationService = pathComputationService;
        this.delegatedLspService = delegatedLspService;
        this.hashService = hashService;
        this.metrics = metrics;
    }

    public DirectionalLspApplicationRecord applyPolicyToDirection(final PairPolicyCandidate candidate,
            final TunnelDirection direction, final WorkflowContext workflowContext) {
        final ReentrantLock lock = locksByDirection.computeIfAbsent(direction.directionKey(), ignored -> new ReentrantLock());
        lock.lock();
        final Instant started = Instant.now();
        try {
            final PathConstraints constraints = new PathConstraints(candidate.requestedBandwidthKbps(),
                    candidate.setupPriority(), candidate.holdPriority());
            final CalculatedPath path = pathComputationService.computeOrGetCached(direction, constraints);
            final DelegatedLspRecord current = delegatedLspService.refreshDirection(direction.directionKey());
            final String bandwidth = BandwidthTranslator.kbpsToPcepBandwidthBase64Float32(candidate.requestedBandwidthKbps());
            final DirectionalLspDesiredState desired = desiredState(candidate, direction, current, path.eroSubobjects(), bandwidth);
            final boolean converged = current.isValidForUpdate() && current.activeEro().equals(path.eroSubobjects())
                    && bandwidth.equals(current.reportedBandwidthBase64());
            if (converged) {
                metrics.increment("sma_lsp_update_skipped_converged_total");
                return record(candidate, direction, workflowContext, desired, current, "SKIPPED_ALREADY_CONVERGED", 0,
                        false, true, true, started);
            }
            if (!bandwidth.equals(current.reportedBandwidthBase64())) {
                metrics.increment("sma_lsp_update_bandwidth_mismatch_total");
            }
            metrics.increment("sma_lsp_update_sent_total");
            final var result = delegatedLspService.updateDelegatedLsp(direction, path, constraints);
            final DelegatedLspRecord confirmed = delegatedLspService.requireDelegatedLsp(direction.directionKey());
            final boolean eroConfirmed = confirmed.activeEro().equals(path.eroSubobjects());
            final boolean bandwidthConfirmed = bandwidth.equals(confirmed.reportedBandwidthBase64());
            final String status = bandwidthConfirmed ? "UPDATE_SENT_ACCEPTED" : "ACCEPTED_PCEP_BANDWIDTH_UNCONFIRMED";
            return record(candidate, direction, workflowContext, desired, confirmed, status, result.httpStatus(),
                    true, eroConfirmed, bandwidthConfirmed, started);
        } catch (RuntimeException failure) {
            metrics.increment("sma_pair_policy_apply_failure_total");
            final DelegatedLspRecord current = delegatedLspService.requireDelegatedLsp(direction.directionKey());
            return record(candidate, direction, workflowContext,
                    new DirectionalLspDesiredState(candidate.pairKey(), direction.directionKey(), current.pccNode(),
                            current.lspName(), current.tunnelInterfaceName(), current.plspId(), current.tunnelId(),
                            candidate.requestedBandwidthKbps(), "", candidate.setupPriority(), candidate.holdPriority(),
                            List.of(), "", ""),
                    current, "FAILED_UPDATE_LSP_VALIDATION", null, false, false, false, started);
        } finally {
            lock.unlock();
        }
    }

    private DirectionalLspDesiredState desiredState(final PairPolicyCandidate candidate, final TunnelDirection direction,
            final DelegatedLspRecord current, final List<EroSubobject> ero, final String bandwidth) {
        final String fingerprint = eroFingerprint(ero);
        final DirectionalLspDesiredState partial = new DirectionalLspDesiredState(candidate.pairKey(), direction.directionKey(),
                current.pccNode(), current.lspName(), current.tunnelInterfaceName(), current.plspId(), current.tunnelId(),
                candidate.requestedBandwidthKbps(), bandwidth, candidate.setupPriority(), candidate.holdPriority(), ero,
                fingerprint, "");
        return new DirectionalLspDesiredState(partial.pairKey(), partial.directionKey(), partial.pccNode(), partial.lspName(),
                partial.tunnelInterfaceName(), partial.plspId(), partial.tunnelId(), partial.requestedBandwidthKbps(),
                partial.requestedBandwidthBase64(), partial.setupPriority(), partial.holdPriority(), partial.desiredEro(),
                partial.desiredEroFingerprint(), hashService.hashDesiredLspState(partial));
    }

    private static DirectionalLspApplicationRecord record(final PairPolicyCandidate candidate, final TunnelDirection direction,
            final WorkflowContext workflowContext, final DirectionalLspDesiredState desired, final DelegatedLspRecord current,
            final String status, final Integer httpStatus, final boolean sent, final boolean eroConfirmed,
            final boolean bandwidthConfirmed, final Instant started) {
        return new DirectionalLspApplicationRecord(UUID.randomUUID(), workflowContext.workflowId(),
                workflowContext.packetSequence(), candidate.pairKey(), direction.directionKey(), candidate.policyHash(),
                desired.desiredLspStateHash(), current.lspName(), current.pccNode(), current.plspId(), status, httpStatus,
                sent, eroConfirmed, bandwidthConfirmed, started, Instant.now());
    }

    private static String eroFingerprint(final List<EroSubobject> ero) {
        final StringJoiner joiner = new StringJoiner("|");
        for (EroSubobject subobject : ero) {
            joiner.add("loose=" + subobject.loose() + ":" + subobject.ipPrefix());
        }
        return joiner.toString();
    }
}
