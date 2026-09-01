/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.registry;

import com.sma.sdn.model.ClassificationResult;
import com.sma.sdn.model.EroSubobject;
import com.sma.sdn.model.TunnelIntentKey;
import com.sma.sdn.model.TunnelOperationRecord;
import com.sma.sdn.model.TunnelOperationStatus;
import com.sma.sdn.model.TunnelDirection;
import com.sma.sdn.observability.StructuredLogger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Mantiene los intentos recientes de LSP para evitar operaciones PCEP duplicadas. */
public final class TunnelOperationRegistry {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(TunnelOperationRegistry.class);
    private final Map<TunnelIntentKey, TunnelOperationRecord> latestByIntent = new LinkedHashMap<>();
    private final Map<String, TunnelOperationRecord> latestByDirection = new LinkedHashMap<>();
    private final Deque<TunnelOperationRecord> journal = new ArrayDeque<>();
    private final int maximumJournalEntries;

    public TunnelOperationRegistry(final int maximumJournalEntries) {
        if (maximumJournalEntries <= 0) {
            throw new IllegalArgumentException("maximumJournalEntries debe ser positivo");
        }
        this.maximumJournalEntries = maximumJournalEntries;
    }

    /** Busca un estado pendiente o exitoso que siga representando el mismo intento deseado. */
    public synchronized Optional<TunnelOperationRecord> findRecentUsableIntent(
            final TunnelIntentKey key, final Instant now) {
        expireOldEntries(now);
        final TunnelOperationRecord record = latestByIntent.get(key);
        if (record != null && isUsable(record) && record.expiresAt().isAfter(now)) {
            LOG.debug("tunnel_operation_recent_intent_hit", "findRecentUsableIntent",
                    "Se encontro un intento reciente utilizable para el LSP delegado.", fields(key, record));
            return Optional.of(record);
        }
        LOG.debug("tunnel_operation_recent_intent_miss", "findRecentUsableIntent",
                "No se encontro un intento reciente utilizable para el LSP delegado.", fields(key, record));
        return Optional.empty();
    }

    /** Registra un intento antes de enviar update-lsp para que paquetes concurrentes puedan reutilizarlo. */
    public synchronized TunnelOperationRecord markPending(
            final TunnelIntentKey key,
            final String workflowId,
            final long packetSequence,
            final TunnelDirection direction,
            final ClassificationResult classification,
            final List<EroSubobject> requestedEro,
            final Instant now,
            final Duration pendingTtl) {
        final TunnelOperationRecord record = new TunnelOperationRecord(
                UUID.randomUUID().toString(), workflowId, packetSequence, direction.directionKey(),
                key.pccNode(), key.lspName(), classification.policy().profileName(), classification.className(),
                key.bandwidthBase64(), requestedEro, TunnelOperationStatus.PENDING, null, false, false,
                null, now, null, null, now.plus(pendingTtl));
        publish(key, record);
        LOG.debug("tunnel_operation_marked_pending", "markPending",
                "Se registro una operacion PCEP pendiente para el LSP delegado.", fields(key, record));
        return record;
    }

    /** Marca una respuesta HTTP aceptada cuando aun no existe confirmacion PCEP completa. */
    public synchronized TunnelOperationRecord markAccepted(
            final TunnelIntentKey key, final int httpStatus, final Instant now, final Duration intentTtl) {
        return replaceStatus(key, TunnelOperationStatus.ACCEPTED, httpStatus, false, false, null, now, intentTtl,
                "tunnel_operation_marked_accepted");
    }

    /** Marca el intento cuando PCEP confirma la ERO y, si es posible, el ancho de banda. */
    public synchronized TunnelOperationRecord markConfirmed(
            final TunnelIntentKey key,
            final int httpStatus,
            final boolean pcepEroConfirmed,
            final boolean pcepBandwidthConfirmed,
            final Instant now,
            final Duration intentTtl) {
        final TunnelOperationStatus status = pcepBandwidthConfirmed
                ? TunnelOperationStatus.CONFIRMED : TunnelOperationStatus.ACCEPTED_PCEP_BANDWIDTH_UNCONFIRMED;
        return replaceStatus(key, status, httpStatus, pcepEroConfirmed, pcepBandwidthConfirmed, null, now, intentTtl,
                "tunnel_operation_marked_confirmed");
    }

    /** Añade al diario la reutilizacion de una decision reciente sin reemplazar el intento utilizable original. */
    public synchronized TunnelOperationRecord markSkippedRecentIntent(
            final TunnelIntentKey key,
            final TunnelOperationRecord reusedRecord,
            final String workflowId,
            final long packetSequence,
            final Instant now) {
        final TunnelOperationRecord skipped = new TunnelOperationRecord(
                UUID.randomUUID().toString(), workflowId, packetSequence, key.directionKey(), key.pccNode(),
                key.lspName(), key.profileName(), key.className(), key.bandwidthBase64(), reusedRecord.requestedEro(),
                TunnelOperationStatus.SKIPPED_RECENT_INTENT, reusedRecord.updateLspHttpStatus(),
                reusedRecord.pcepEroConfirmed(), reusedRecord.pcepBandwidthConfirmed(), null, now,
                reusedRecord.acceptedAt(), now, reusedRecord.expiresAt());
        appendJournal(skipped);
        LOG.debug("tunnel_operation_marked_skipped", "markSkippedRecentIntent",
                "Se omitio una operacion porque un intento equivalente sigue vigente.", fields(key, skipped));
        return skipped;
    }

    /** Registra un estado que ya coincide con la ERO solicitada sin enviar update-lsp. */
    public synchronized TunnelOperationRecord markSkippedAlreadyMatching(
            final TunnelIntentKey key,
            final String workflowId,
            final long packetSequence,
            final List<EroSubobject> requestedEro,
            final boolean pcepBandwidthConfirmed,
            final Instant now,
            final Duration intentTtl) {
        final TunnelOperationStatus status = pcepBandwidthConfirmed
                ? TunnelOperationStatus.SKIPPED_ALREADY_MATCHING
                : TunnelOperationStatus.ACCEPTED_PCEP_BANDWIDTH_UNCONFIRMED;
        final TunnelOperationRecord record = new TunnelOperationRecord(
                UUID.randomUUID().toString(), workflowId, packetSequence, key.directionKey(), key.pccNode(),
                key.lspName(), key.profileName(), key.className(), key.bandwidthBase64(), requestedEro, status,
                null, true, pcepBandwidthConfirmed, null, now, now, now, now.plus(intentTtl));
        publish(key, record);
        LOG.debug("tunnel_operation_marked_confirmed", "markSkippedAlreadyMatching",
                "Se reutilizo el estado PCEP cuya ERO ya coincide con la solicitud.", fields(key, record));
        return record;
    }

    /** Conserva el fallo para auditoria, sin permitir que bloquee un intento posterior. */
    public synchronized TunnelOperationRecord markFailed(
            final TunnelIntentKey key, final int httpStatus, final String failureReason, final Instant now) {
        final TunnelOperationRecord previous = latestByIntent.get(key);
        final TunnelOperationRecord failed = new TunnelOperationRecord(
                previous == null ? UUID.randomUUID().toString() : previous.operationId(),
                previous == null ? "unknown" : previous.workflowId(),
                previous == null ? 0L : previous.packetSequence(), key.directionKey(), key.pccNode(), key.lspName(),
                key.profileName(), key.className(), key.bandwidthBase64(),
                previous == null ? List.of() : previous.requestedEro(), TunnelOperationStatus.FAILED_HARD,
                httpStatus <= 0 ? null : httpStatus, false, false, failureReason,
                previous == null ? now : previous.startedAt(), null, now, now);
        latestByIntent.remove(key);
        latestByDirection.put(key.directionKey(), failed);
        appendJournal(failed);
        LOG.warn("tunnel_operation_marked_failed", "markFailed",
                "La operacion PCEP del LSP delegado fallo y no suprimira nuevos intentos.", fields(key, failed), null);
        return failed;
    }

    /** Devuelve una instantanea ordenada de los eventos conservados para auditoria. */
    public synchronized List<TunnelOperationRecord> recentJournalSnapshot() {
        return List.copyOf(journal);
    }

    /** Expira los indices de trabajo sin eliminar el diario acotado de auditoria. */
    public synchronized void expireOldEntries() {
        expireOldEntries(Instant.now());
    }

    private TunnelOperationRecord replaceStatus(
            final TunnelIntentKey key,
            final TunnelOperationStatus status,
            final int httpStatus,
            final boolean pcepEroConfirmed,
            final boolean pcepBandwidthConfirmed,
            final String failureReason,
            final Instant now,
            final Duration intentTtl,
            final String event) {
        final TunnelOperationRecord previous = latestByIntent.get(key);
        if (previous == null) {
            throw new IllegalStateException("No existe una operacion pendiente para " + key.directionKey());
        }
        final TunnelOperationRecord record = new TunnelOperationRecord(
                previous.operationId(), previous.workflowId(), previous.packetSequence(), previous.directionKey(),
                previous.pccNode(), previous.lspName(), previous.profileName(), previous.className(),
                previous.bandwidthBase64(), previous.requestedEro(), status, httpStatus <= 0 ? null : httpStatus,
                pcepEroConfirmed, pcepBandwidthConfirmed, failureReason, previous.startedAt(), now, now,
                now.plus(intentTtl));
        publish(key, record);
        LOG.debug(event, "replaceStatus", "Se actualizo el estado de una operacion PCEP del LSP delegado.",
                fields(key, record));
        return record;
    }

    private void publish(final TunnelIntentKey key, final TunnelOperationRecord record) {
        latestByIntent.put(key, record);
        latestByDirection.put(record.directionKey(), record);
        appendJournal(record);
    }

    private void appendJournal(final TunnelOperationRecord record) {
        journal.addLast(record);
        if (journal.size() > maximumJournalEntries) {
            journal.removeFirst();
            LOG.debug("tunnel_operation_journal_trimmed", "appendJournal",
                    "Se descarto la entrada mas antigua del diario de operaciones de tunel.",
                    StructuredLogger.fields("maximum_entries", maximumJournalEntries, "journal_size", journal.size()));
        }
    }

    private void expireOldEntries(final Instant now) {
        latestByIntent.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        latestByDirection.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private static boolean isUsable(final TunnelOperationRecord record) {
        return switch (record.status()) {
            case PENDING, ACCEPTED, CONFIRMED, SKIPPED_ALREADY_MATCHING,
                    ACCEPTED_PCEP_BANDWIDTH_UNCONFIRMED -> true;
            case SKIPPED_RECENT_INTENT, FAILED, FAILED_HARD -> false;
        };
    }

    private static Map<String, Object> fields(final TunnelIntentKey key, final TunnelOperationRecord record) {
        return StructuredLogger.fields(
                "direction_key", key.directionKey(), "lsp_name", key.lspName(), "pcc_node", key.pccNode(),
                "profile_name", key.profileName(), "class_name", key.className(),
                "ero_fingerprint", key.eroFingerprint(), "operation_status", record == null ? null : record.status(),
                "operation_id", record == null ? null : record.operationId(),
                "expires_at", record == null ? null : record.expiresAt());
    }
}
