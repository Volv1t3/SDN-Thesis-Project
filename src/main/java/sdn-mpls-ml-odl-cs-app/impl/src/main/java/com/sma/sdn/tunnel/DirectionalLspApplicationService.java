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
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>Applies a selected pair-policy candidate to one delegated LSP direction. It computes
 * the requested path, compares it and the encoded bandwidth with the latest PCEP report,
 * and either skips an already converged LSP or requests an update and reports confirmation.</p>
 *
 * <p>The pair-policy coordinator consumes the returned application record. Per-direction
 * locks serialize this service's concurrent applications for the same direction, while
 * {@link DelegatedLspService} performs the actual PCEP update and confirmation.</p>
 */
public final class DirectionalLspApplicationService {
    /** <p>Computes or retrieves paths satisfying the selected policy constraints.</p> */
    private final PathComputationService pathComputationService;
    /** <p>Refreshes and updates the delegated LSP reported by PCEP.</p> */
    private final DelegatedLspService delegatedLspService;
    /** <p>Hashes desired LSP state for correlation in application records.</p> */
    private final PairPolicyHashService hashService;
    /** <p>Records application outcomes and confirmation metrics.</p> */
    private final SdnMplsMlMetrics metrics;
    /** <p>Locks keyed by direction to serialize applications within this service.</p> */
    private final ConcurrentHashMap<String, ReentrantLock> locksByDirection = new ConcurrentHashMap<>();

    /**
     * <p>Creates a directional application service using the supplied collaborators.</p>
     * <p>The constructor performs these steps:</p>
     * <ol>
     *   <li>Stores the path and delegated-LSP services for later computation and updates.</li>
     *   <li>Stores the desired-state hash service and metrics collector.</li>
     * </ol>
     * @param pathComputationService path cache and computation service
     * @param delegatedLspService service that refreshes and updates delegated LSPs
     * @param hashService service used to hash the desired state
     * @param metrics collector for update and confirmation counters
     */
    public DirectionalLspApplicationService(final PathComputationService pathComputationService,
            final DelegatedLspService delegatedLspService, final PairPolicyHashService hashService,
            final SdnMplsMlMetrics metrics) {
        this.pathComputationService = pathComputationService;
        this.delegatedLspService = delegatedLspService;
        this.hashService = hashService;
        this.metrics = metrics;
    }

    /**
     * <p>Applies a policy to one direction, serializing concurrent calls for that direction.
     * A valid LSP already matching both ERO and bandwidth is skipped; otherwise the service
     * invokes the update service and reports the ERO and bandwidth observed afterwards.
     * The update service may itself skip the RPC if its registered state already matches.</p>
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Acquires the direction lock and computes or retrieves a path for the candidate constraints.</li>
     *   <li>Refreshes PCEP state, translates the bandwidth and constructs the hashed desired state.</li>
     *   <li>Returns a converged skip record when both requested values already match a valid LSP.</li>
     *   <li>Otherwise invokes {@link DelegatedLspService#updateDelegatedLsp} and compares the
     *       subsequently registered ERO and bandwidth, recording confirmation metrics.</li>
     *   <li>On a runtime failure, records failure metrics and constructs a failure record using the
     *       currently registered LSP; releases the direction lock in all cases.</li>
     * </ol>
     * <p>Failure handling itself requires a registered LSP and can propagate an exception if
     * that lookup fails. The returned record is consumed by the pair-policy coordinator.</p>
     * @param candidate selected policy and constraints
     * @param direction LSP direction to update
     * @param workflowContext packet workflow identity for correlation
     * @return an application, converged-skip, or failure record
     * @throws RuntimeException if obtaining the lock or constructing the fallback record fails
     */
    public DirectionalLspApplicationRecord applyPolicyToDirection(final PairPolicyCandidate candidate,
            final TunnelDirection direction, final WorkflowContext workflowContext) {
        final ReentrantLock lock = locksByDirection.computeIfAbsent(
                direction.directionKey(), ignored -> new ReentrantLock());
        lock.lock();
        final Instant started = Instant.now();
        try {
            final PathConstraints constraints = new PathConstraints(candidate.requestedBandwidthKbps(),
                    candidate.setupPriority(), candidate.holdPriority());
            final CalculatedPath path = pathComputationService.computeOrGetCached(direction, constraints);
            final DelegatedLspRecord current = delegatedLspService.refreshDirection(direction.directionKey());
            final String bandwidth = BandwidthTranslator.kbpsToPcepBandwidthBase64Float32(
                    candidate.requestedBandwidthKbps());
            final DirectionalLspDesiredState desired = desiredState(
                    candidate, direction, current, path.eroSubobjects(), bandwidth);
            final boolean converged = current.isValidForUpdate() && current.activeEro().equals(path.eroSubobjects())
                    && bandwidth.equals(current.reportedBandwidthBase64());
            if (converged) {
                metrics.increment("sma_lsp_update_skipped_converged_total");
                metrics.incrementCounter("sma_lsp_application_ero_confirmed_total",
                        Map.of("direction", direction.directionKey()));
                metrics.incrementCounter("sma_lsp_application_bandwidth_confirmed_total",
                        Map.of("direction", direction.directionKey()));
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
            if (eroConfirmed) {
                metrics.incrementCounter("sma_lsp_application_ero_confirmed_total",
                        Map.of("direction", direction.directionKey()));
            }
            final String bandwidthMetric = bandwidthConfirmed
                    ? "sma_lsp_application_bandwidth_confirmed_total"
                    : "sma_lsp_application_bandwidth_unconfirmed_total";
            metrics.incrementCounter(bandwidthMetric, Map.of("direction", direction.directionKey()));
            final String status = bandwidthConfirmed ? "UPDATE_SENT_ACCEPTED" : "ACCEPTED_PCEP_BANDWIDTH_UNCONFIRMED";
            return record(candidate, direction, workflowContext, desired, confirmed, status, result.httpStatus(),
                    true, eroConfirmed, bandwidthConfirmed, started);
        } catch (RuntimeException failure) {
            metrics.increment("sma_pair_policy_apply_failure_total");
            metrics.incrementCounter("sma_lsp_application_failed_total",
                    Map.of("stage", failureStage(failure), "direction", direction.directionKey()));
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

    /**
     * <p>Builds the requested directional LSP state and its stable policy-service hash.</p>
     * <p>The construction proceeds as follows:</p>
     * <ol>
     *   <li>Fingerprints the ordered ERO and combines it with policy constraints and LSP identity.</li>
     *   <li>Hashes the partial state and returns a copy containing the resulting hash.</li>
     * </ol>
     * @param candidate selected pair policy
     * @param direction direction receiving the policy
     * @param current current delegated LSP identity
     * @param ero ordered desired route
     * @param bandwidth requested PCEP bandwidth encoding
     * @return desired state including its ERO fingerprint and hash
     */
    private DirectionalLspDesiredState desiredState(
            final PairPolicyCandidate candidate,
            final TunnelDirection direction,
            final DelegatedLspRecord current,
            final List<EroSubobject> ero,
            final String bandwidth) {
        final String fingerprint = eroFingerprint(ero);
        final DirectionalLspDesiredState partial = new DirectionalLspDesiredState(
                candidate.pairKey(), direction.directionKey(), current.pccNode(), current.lspName(),
                current.tunnelInterfaceName(), current.plspId(), current.tunnelId(),
                candidate.requestedBandwidthKbps(), bandwidth, candidate.setupPriority(),
                candidate.holdPriority(), ero, fingerprint, "");
        return new DirectionalLspDesiredState(
                partial.pairKey(), partial.directionKey(), partial.pccNode(), partial.lspName(),
                partial.tunnelInterfaceName(), partial.plspId(), partial.tunnelId(),
                partial.requestedBandwidthKbps(), partial.requestedBandwidthBase64(),
                partial.setupPriority(), partial.holdPriority(), partial.desiredEro(),
                partial.desiredEroFingerprint(), hashService.hashDesiredLspState(partial));
    }

    /**
     * <p>Creates an application audit record associated with the originating packet workflow.</p>
     * <p>The construction proceeds as follows:</p>
     * <ol>
     *   <li>Generates a record identifier and copies workflow, policy, desired-state and LSP identifiers.</li>
     *   <li>Stores the supplied outcome flags and timestamps the end of the operation.</li>
     * </ol>
     * @param candidate selected pair policy
     * @param direction affected direction
     * @param workflowContext originating workflow identity
     * @param desired desired LSP state
     * @param current LSP whose identity is recorded
     * @param status application outcome label
     * @param httpStatus update response status, or null when unavailable
     * @param sent whether an update was sent
     * @param eroConfirmed whether the desired ERO was observed
     * @param bandwidthConfirmed whether the desired bandwidth was observed
     * @param started operation start time
     * @return completed application record
     */
    private static DirectionalLspApplicationRecord record(
            final PairPolicyCandidate candidate,
            final TunnelDirection direction,
            final WorkflowContext workflowContext,
            final DirectionalLspDesiredState desired,
            final DelegatedLspRecord current,
            final String status,
            final Integer httpStatus,
            final boolean sent,
            final boolean eroConfirmed,
            final boolean bandwidthConfirmed,
            final Instant started) {
        return new DirectionalLspApplicationRecord(UUID.randomUUID(), workflowContext.workflowId(),
                workflowContext.packetSequence(), candidate.pairKey(), direction.directionKey(), candidate.policyHash(),
                desired.desiredLspStateHash(), current.lspName(), current.pccNode(), current.plspId(), status,
                httpStatus, sent, eroConfirmed, bandwidthConfirmed, started, Instant.now());
    }

    /**
     * <p>Creates a deterministic textual fingerprint of an ordered ERO.</p>
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Formats each subobject's loose flag and IP prefix in route order.</li>
     *   <li>Joins the formatted values with vertical bars.</li>
     * </ol>
     * @param ero ordered route subobjects
     * @return fingerprint, or an empty string for an empty route
     */
    private static String eroFingerprint(final List<EroSubobject> ero) {
        final StringJoiner joiner = new StringJoiner("|");
        for (EroSubobject subobject : ero) {
            joiner.add("loose=" + subobject.loose() + ":" + subobject.ipPrefix());
        }
        return joiner.toString();
    }

    /**
     * <p>Maps an exception message to a coarse failure-stage metric label.</p>
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Lowercases the message, substituting an empty string when absent.</li>
     *   <li>Checks the configured English and Spanish fragments in order and falls back to unknown.</li>
     * </ol>
     * @param failure exception being classified
     * @return best-effort stage label, not a definitive diagnosis
     */
    private static String failureStage(final RuntimeException failure) {
        final String message = failure.getMessage() == null ? "" : failure.getMessage().toLowerCase();
        if (message.contains("camino") || message.contains("path")) {
            return "path_computation";
        }
        if (message.contains("topologia pcep") || message.contains("delegado")) {
            return "pcep_refresh";
        }
        if (message.contains("update-lsp")) {
            return "update_lsp";
        }
        if (message.contains("confirm")) {
            return "confirmation";
        }
        if (message.contains("valid")) {
            return "validation";
        }
        return "unknown";
    }
}
