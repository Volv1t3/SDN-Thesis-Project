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

/** Coordina actualizaciones idempotentes de LSP y la supresion OpenFlow posterior a una decision valida. */
public final class TunnelOperationCoordinator {
    private final AppConfig config;
    private final PathComputationService pathComputationService;
    private final DelegatedLspService delegatedLspService;
    private final TunnelOperationRegistry operationRegistry;
    private final OpenFlowSuppressionService suppressionService;
    private final SdnMplsMlMetrics metrics;
    private final ConcurrentHashMap<String, ReentrantLock> locksByDirection = new ConcurrentHashMap<>();

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

    /** Procesa una direccion bajo un bloqueo exclusivo y reutiliza decisiones recientes equivalentes. */
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

    static String eroFingerprint(final List<EroSubobject> subobjects) {
        final StringJoiner joiner = new StringJoiner("|");
        for (EroSubobject subobject : subobjects) {
            joiner.add("loose=" + subobject.loose() + ":" + subobject.ipPrefix());
        }
        return joiner.toString();
    }

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

    private void installSuppression(
            final PacketClassificationContext packetContext,
            final ClassificationResult classification,
            final WorkflowContext workflowContext) {
        suppressionService.buildSuppressionIntent(packetContext, classification)
                .ifPresent(intent -> suppressionService.installSuppressionFlow(intent, workflowContext));
    }
}
