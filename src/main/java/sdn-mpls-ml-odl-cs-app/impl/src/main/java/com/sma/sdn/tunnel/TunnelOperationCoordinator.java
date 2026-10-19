/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.tunnel;

import com.sma.sdn.config.AppConfig;
import com.sma.sdn.metrics.SdnMplsMlMetrics;
import com.sma.sdn.model.CalculatedPath;
import com.sma.sdn.model.ClassificationResult;
import com.sma.sdn.model.DelegatedLspRecord;
import com.sma.sdn.model.EroSubobject;
import com.sma.sdn.model.PacketClassificationContext;
import com.sma.sdn.model.TunnelIntentKey;
import com.sma.sdn.model.TunnelOperationRecord;
import com.sma.sdn.model.TunnelDirection;
import com.sma.sdn.model.WorkflowContext;
import com.sma.sdn.openflow.OpenFlowSuppressionService;
import com.sma.sdn.path.PathComputationService;
import com.sma.sdn.registry.TunnelOperationRegistry;
import com.sma.sdn.topology.BandwidthTranslator;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>Coordinates a directional LSP operation using an intent registry and optionally installs
 * an OpenFlow suppression flow. It reuses recent intents, compares reported EROs and records
 * the lifecycle of any required delegated-LSP update.</p>
 *
 * <p>This class is separate from the pair-policy workflow's
 * {@link DirectionalLspApplicationService}; construction here does not imply that the
 * provider currently invokes this coordinator.</p>
 */
public final class TunnelOperationCoordinator {
    /** <p>Supplies intent lifetimes and the per-direction lock timeout.</p> */
    private final AppConfig config;
    /** <p>Computes or retrieves the constrained path.</p> */
    private final PathComputationService pathComputationService;
    /** <p>Refreshes and updates the delegated LSP.</p> */
    private final DelegatedLspService delegatedLspService;
    /** <p>Stores pending, completed and reusable operation intents.</p> */
    private final TunnelOperationRegistry operationRegistry;
    /** <p>Builds and installs eligible packet suppression flows.</p> */
    private final OpenFlowSuppressionService suppressionService;
    /** <p>Collects tunnel-operation outcome counters.</p> */
    private final SdnMplsMlMetrics metrics;
    /** <p>Serializes non-reused operations by direction.</p> */
    private final ConcurrentHashMap<String, ReentrantLock> locksByDirection = new ConcurrentHashMap<>();

    /**
     * <p>Creates an intent-based tunnel operation coordinator.</p>
     * <p>The constructor performs these steps:</p>
     * <ol>
     *   <li>Stores configuration and the path and delegated-LSP services.</li>
     *   <li>Stores the operation registry, suppression service and metrics collector.</li>
     * </ol>
     * @param config intent lifetime and lock configuration
     * @param pathComputationService constrained path service
     * @param delegatedLspService PCEP state and update service
     * @param operationRegistry operation intent store
     * @param suppressionService OpenFlow suppression service
     * @param metrics operation metrics collector
     */
    public TunnelOperationCoordinator(
            final AppConfig config,
            final PathComputationService pathComputationService,
            final DelegatedLspService delegatedLspService,
            final TunnelOperationRegistry operationRegistry,
            final OpenFlowSuppressionService suppressionService,
            final SdnMplsMlMetrics metrics) {
        this.config = config;
        this.pathComputationService = pathComputationService;
        this.delegatedLspService = delegatedLspService;
        this.operationRegistry = operationRegistry;
        this.suppressionService = suppressionService;
        this.metrics = metrics;
    }

    /**
     * <p>Processes one directional classified intent, reusing a recent result or updating
     * the delegated LSP. An already matching ERO is skipped even if bandwidth differs;
     * the bandwidth comparison is retained in the operation record.</p>
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Computes the path and intent key, then checks for an unexpired reusable operation.</li>
     *   <li>For a miss, acquires a direction lock within the configured timeout and repeats the check.</li>
     *   <li>Refreshes PCEP state and skips an update if the reported ERO matches.</li>
     *   <li>Otherwise marks the intent pending, updates the LSP, and records accepted and confirmed state.</li>
     *   <li>Installs suppression when an intent is available; records update failures and releases the lock.</li>
     * </ol>
     * <p>The registry's reuse window and the direction lock limit redundant work. Failures
     * during the pending update are recorded and rethrown; an interrupted lock wait restores
     * the thread interrupt flag.</p>
     * @param workflowContext workflow identifier and packet sequence
     * @param packetContext ingress and packet data for suppression
     * @param direction target tunnel direction
     * @param classification classification and policy constraints
     * @return reused, already matching or completed operation record
     * @throws IllegalStateException if lock acquisition times out or is interrupted
     * @throws RuntimeException if path computation, PCEP operations, registry or suppression fails
     */
    public TunnelOperationRecord processDirection(
            final WorkflowContext workflowContext,
            final PacketClassificationContext packetContext,
            final TunnelDirection direction,
            final ClassificationResult classification) {
        final CalculatedPath path = pathComputationService.computeOrGetCached(
                direction, classification.policy().pathConstraints());
        final TunnelIntentKey key = intentKey(direction, classification, path);
        final Instant now = Instant.now();
        final Optional<TunnelOperationRecord> recent = operationRegistry.findRecentUsableIntent(key, now);
        if (recent.isPresent()) {
            final TunnelOperationRecord skipped = operationRegistry.markSkippedRecentIntent(
                    key, recent.orElseThrow(), workflowContext.workflowId(), workflowContext.packetSequence(), now);
            installSuppression(packetContext, classification, workflowContext);
            metrics.increment("sma_tunnel_operation_recent_intent_skip_total");
            return skipped;
        }

        final ReentrantLock lock = locksByDirection.computeIfAbsent(
                direction.directionKey(), ignored -> new ReentrantLock());
        try {
            if (!lock.tryLock(config.tunnelOperationLockTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Se agoto el bloqueo de la direccion " + direction.directionKey());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("La espera del bloqueo de tunel fue interrumpida", e);
        }
        try {
            final Optional<TunnelOperationRecord> afterLock = operationRegistry.findRecentUsableIntent(
                    key, Instant.now());
            if (afterLock.isPresent()) {
                final TunnelOperationRecord skipped = operationRegistry.markSkippedRecentIntent(
                        key, afterLock.orElseThrow(), workflowContext.workflowId(), workflowContext.packetSequence(),
                        Instant.now());
                installSuppression(packetContext, classification, workflowContext);
                metrics.increment("sma_tunnel_operation_recent_intent_skip_total");
                return skipped;
            }

            final DelegatedLspRecord current = delegatedLspService.refreshDirection(direction.directionKey());
            final boolean eroMatches = current.activeEro().equals(path.eroSubobjects());
            final boolean bandwidthMatches = key.bandwidthBase64().equals(current.reportedBandwidthBase64());
            if (eroMatches) {
                final TunnelOperationRecord skipped = operationRegistry.markSkippedAlreadyMatching(
                        key, workflowContext.workflowId(), workflowContext.packetSequence(), path.eroSubobjects(),
                        bandwidthMatches, Instant.now(), config.tunnelIntentTtl());
                installSuppression(packetContext, classification, workflowContext);
                metrics.increment("sma_tunnel_operation_already_matching_skip_total");
                return skipped;
            }

            operationRegistry.markPending(
                    key, workflowContext.workflowId(), workflowContext.packetSequence(), direction, classification,
                    path.eroSubobjects(), Instant.now(), config.tunnelPendingTtl());
            try {
                final var result = delegatedLspService.updateDelegatedLsp(
                        direction, path, classification.policy().pathConstraints());
                operationRegistry.markAccepted(key, result.httpStatus(), Instant.now(), config.tunnelIntentTtl());
                final DelegatedLspRecord confirmed = delegatedLspService.requireDelegatedLsp(direction.directionKey());
                final TunnelOperationRecord completed = operationRegistry.markConfirmed(
                        key, result.httpStatus(), confirmed.activeEro().equals(path.eroSubobjects()),
                        key.bandwidthBase64().equals(confirmed.reportedBandwidthBase64()), Instant.now(),
                        config.tunnelIntentTtl());
                installSuppression(packetContext, classification, workflowContext);
                metrics.increment("sma_tunnel_operation_success_total");
                return completed;
            } catch (RuntimeException e) {
                operationRegistry.markFailed(key, 0, e.getMessage(), Instant.now());
                metrics.increment("sma_tunnel_operation_failure_total");
                throw e;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * <p>Formats an ordered ERO as a deterministic intent-key component.</p>
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Formats each subobject's loose flag and IP prefix in route order.</li>
     *   <li>Joins the entries with vertical bars.</li>
     * </ol>
     * @param subobjects ordered route entries
     * @return route fingerprint, or an empty string for an empty route
     */
    static String eroFingerprint(final List<EroSubobject> subobjects) {
        final StringJoiner joiner = new StringJoiner("|");
        for (EroSubobject subobject : subobjects) {
            joiner.add("loose=" + subobject.loose() + ":" + subobject.ipPrefix());
        }
        return joiner.toString();
    }

    /**
     * <p>Constructs the key used to identify an equivalent directional tunnel intent.</p>
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Requires the registered delegated LSP for the direction.</li>
     *   <li>Combines its identity with classification, encoded bandwidth, route and path constraints.</li>
     * </ol>
     * @param direction selected tunnel direction
     * @param classification classification and policy
     * @param path computed route and algorithm information
     * @return key for operation reuse
     * @throws RuntimeException if the LSP lookup or bandwidth conversion fails
     */
    private TunnelIntentKey intentKey(
            final TunnelDirection direction,
            final ClassificationResult classification,
            final CalculatedPath path) {
        final DelegatedLspRecord lsp = delegatedLspService.requireDelegatedLsp(direction.directionKey());
        return new TunnelIntentKey(
                direction.directionKey(), lsp.pccNode(), lsp.lspName(), classification.policy().profileName(),
                classification.className(), BandwidthTranslator.kbpsToPcepBandwidthBase64Float32(
                        classification.policy().pathConstraints().requestedBandwidthKbps()),
                eroFingerprint(path.eroSubobjects()), classification.policy().pathConstraints().setupPriority(),
                classification.policy().pathConstraints().holdPriority(), path.algorithm(), path.classType());
    }

    /**
     * <p>Installs a suppression flow only when the suppression service produces an intent.</p>
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Requests a suppression intent from packet and classification data.</li>
     *   <li>Installs the intent with workflow correlation if one is present.</li>
     * </ol>
     * @param packetContext packet features and ingress
     * @param classification classification and policy
     * @param workflowContext originating workflow identity
     * @throws RuntimeException if suppression construction or installation fails
     */
    private void installSuppression(
            final PacketClassificationContext packetContext,
            final ClassificationResult classification,
            final WorkflowContext workflowContext) {
        suppressionService.buildSuppressionIntent(packetContext, classification)
                .ifPresent(intent -> suppressionService.installSuppressionFlow(intent, workflowContext));
    }
}
