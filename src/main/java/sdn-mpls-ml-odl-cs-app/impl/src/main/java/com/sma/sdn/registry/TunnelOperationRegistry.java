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

/**
 * <p>Tracks recent tunnel operation intents and a bounded audit journal so callers can
 * identify reusable in-flight or successful operations and avoid duplicate PCEP updates.</p>
 * <p>{@link com.sma.sdn.tunnel.TunnelOperationCoordinator} records operation transitions;
 * expiration removes working indexes but leaves the bounded journal intact.</p>
 */
public final class TunnelOperationRegistry {
    /** <p>Structured logger for operation transitions and journal trimming.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(TunnelOperationRegistry.class);
    /** <p>Most recent reusable or pending record indexed by intent.</p> */
    private final Map<TunnelIntentKey, TunnelOperationRecord> latestByIntent = new LinkedHashMap<>();
    /** <p>Most recent published record for each direction, including failures.</p> */
    private final Map<String, TunnelOperationRecord> latestByDirection = new LinkedHashMap<>();
    /** <p>Bounded chronological audit trail of operation records.</p> */
    private final Deque<TunnelOperationRecord> journal = new ArrayDeque<>();
    /** <p>Maximum number of events retained in the audit trail.</p> */
    private final int maximumJournalEntries;

    /**
     * <p>Creates an operation registry with a bounded journal.</p>
     * <p>Construction proceeds as follows:</p>
     * <ol><li>Rejects nonpositive limits and stores the accepted capacity.</li></ol>
     * @param maximumJournalEntries positive maximum journal size
     * @throws IllegalArgumentException if the limit is not positive
     */
    public TunnelOperationRegistry(final int maximumJournalEntries) {
        if (maximumJournalEntries <= 0) {
            throw new IllegalArgumentException("maximumJournalEntries debe ser positivo");
        }
        this.maximumJournalEntries = maximumJournalEntries;
    }

    /**
     * <p>Finds an unexpired intent whose status permits reuse.</p>
     * <p>The lookup proceeds as follows:</p>
     * <ol><li>Expires working indexes, checks the intent status and expiration, and logs the result.</li></ol>
     * @param key intent identity
     * @param now inclusive expiration cutoff
     * @return usable record if present
     */
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

    /**
     * <p>Records a pending update intent before its PCEP request is sent.</p>
     * <p>The update proceeds as follows:</p>
     * <ol><li>Creates a uniquely identified pending record with the supplied expiration.</li>
     *   <li>Publishes it to both indexes and the journal, logs it and returns it.</li></ol>
     * @param key intent identity
     * @param workflowId workflow correlation identifier
     * @param packetSequence packet sequence number
     * @param direction target tunnel direction
     * @param classification classification supplying policy and class
     * @param requestedEro requested explicit route
     * @param now attempt start time
     * @param pendingTtl pending-record lifetime
     * @return the pending operation record
     */
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

    /**
     * <p>Records HTTP acceptance without claiming PCEP confirmation.</p>
     * <p>The update proceeds as follows:</p>
     * <ol><li>Replaces the indexed intent with an accepted record and renewed lifetime.</li></ol>
     * @param key existing intent identity
     * @param httpStatus response status, omitted from the record when nonpositive
     * @param now acceptance time
     * @param intentTtl renewed lifetime
     * @return the accepted operation record
     * @throws IllegalStateException if the intent is absent
     */
    public synchronized TunnelOperationRecord markAccepted(
            final TunnelIntentKey key, final int httpStatus, final Instant now, final Duration intentTtl) {
        return replaceStatus(key, TunnelOperationStatus.ACCEPTED, httpStatus, false, false, null, now, intentTtl,
                "tunnel_operation_marked_accepted");
    }

    /**
     * <p>Records PCEP confirmation flags and the accepted HTTP status.</p>
     * <p>The update proceeds as follows:</p>
     * <ol><li>Selects confirmed status when bandwidth is confirmed, otherwise bandwidth-unconfirmed
     *       acceptance, and replaces the existing intent.</li></ol>
     * @param key existing intent identity
     * @param httpStatus response status, omitted when nonpositive
     * @param pcepEroConfirmed whether the ERO was confirmed
     * @param pcepBandwidthConfirmed whether bandwidth was confirmed
     * @param now confirmation time
     * @param intentTtl renewed lifetime
     * @return the updated record
     * @throws IllegalStateException if the intent is absent
     */
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

    /**
     * <p>Records reuse of an existing operation without replacing its indexed intent.</p>
     * <p>The update proceeds as follows:</p>
     * <ol><li>Creates a skipped record using the prior route and confirmation data, appends it
     *       only to the journal, and returns it.</li></ol>
     * @param key reused intent identity
     * @param reusedRecord previously usable record
     * @param workflowId current workflow identifier
     * @param packetSequence current packet sequence
     * @param now time of the reuse decision
     * @return journaled skipped record
     */
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

    /**
     * <p>Records an already matching ERO without sending an update request.</p>
     * <p>The update proceeds as follows:</p>
     * <ol><li>Chooses the status based on bandwidth confirmation, creates a record with ERO
     *       confirmed, then publishes and returns it.</li></ol>
     * @param key intent identity
     * @param workflowId workflow correlation identifier
     * @param packetSequence packet sequence number
     * @param requestedEro matching explicit route
     * @param pcepBandwidthConfirmed whether bandwidth also matches
     * @param now observation time
     * @param intentTtl record lifetime
     * @return published matching-state record
     */
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

    /**
     * <p>Records a hard failure without allowing it to suppress a later attempt.</p>
     * <p>The update proceeds as follows:</p>
     * <ol><li>Copies available prior operation details into a failed record, removes the intent
     *       index, and stores the failure by direction and in the journal.</li></ol>
     * @param key failed intent identity
     * @param httpStatus response status, omitted when nonpositive
     * @param failureReason failure description
     * @param now failure time
     * @return the failed operation record
     */
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

    /**
     * <p>Returns the retained audit records in insertion order.</p>
     * <p>The snapshot proceeds as follows:</p>
     * <ol><li>Copies the journal to an immutable list.</li></ol>
     * @return ordered immutable journal snapshot
     */
    public synchronized List<TunnelOperationRecord> recentJournalSnapshot() {
        return List.copyOf(journal);
    }

    /**
     * <p>Removes expired working-index entries without clearing the audit journal.</p>
     * <p>The expiration proceeds as follows:</p>
     * <ol><li>Uses the current time as the inclusive expiration cutoff.</li></ol>
     */
    public synchronized void expireOldEntries() {
        expireOldEntries(Instant.now());
    }

    /**
     * <p>Replaces an indexed intent with a new status and refreshed expiration.</p>
     * <p>The update proceeds as follows:</p>
     * <ol><li>Requires a prior record, copies its identity and route, sets the new status,
     *       confirmation flags and timestamps, then publishes and logs the replacement.</li></ol>
     * @param key existing intent identity
     * @param status replacement status
     * @param httpStatus response status, omitted when nonpositive
     * @param pcepEroConfirmed ERO confirmation flag
     * @param pcepBandwidthConfirmed bandwidth confirmation flag
     * @param failureReason optional failure description
     * @param now transition time
     * @param intentTtl replacement lifetime
     * @param event structured log event name
     * @return replacement record
     * @throws IllegalStateException if the intent is absent
     */
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

    /**
     * <p>Publishes an operation to the working indexes and audit journal.</p>
     * <p>The publication proceeds as follows:</p>
     * <ol><li>Replaces both indexed records and appends the record to the journal.</li></ol>
     * @param key intent index key
     * @param record operation record to publish
     */
    private void publish(final TunnelIntentKey key, final TunnelOperationRecord record) {
        latestByIntent.put(key, record);
        latestByDirection.put(record.directionKey(), record);
        appendJournal(record);
    }

    /**
     * <p>Adds an operation event to the bounded audit journal.</p>
     * <p>The append proceeds as follows:</p>
     * <ol><li>Appends the record and, if capacity is exceeded, removes and logs the oldest event.</li></ol>
     * @param record operation event to append
     */
    private void appendJournal(final TunnelOperationRecord record) {
        journal.addLast(record);
        if (journal.size() > maximumJournalEntries) {
            journal.removeFirst();
            LOG.debug("tunnel_operation_journal_trimmed", "appendJournal",
                    "Se descarto la entrada mas antigua del diario de operaciones de tunel.",
                    StructuredLogger.fields("maximum_entries", maximumJournalEntries, "journal_size", journal.size()));
        }
    }

    /**
     * <p>Removes expired intent and direction indexes while preserving journal records.</p>
     * <p>The expiration proceeds as follows:</p>
     * <ol><li>Removes entries from both indexes when expiration is at or before now.</li></ol>
     * @param now inclusive expiration cutoff
     */
    private void expireOldEntries(final Instant now) {
        latestByIntent.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        latestByDirection.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    /**
     * <p>Determines whether a record's status is eligible for intent reuse.</p>
     * <p>The check proceeds as follows:</p>
     * <ol><li>Accepts pending, accepted, confirmed and matching statuses; rejects skipped-reuse
     *       and failure statuses.</li></ol>
     * @param record operation record to inspect
     * @return whether its status permits reuse
     */
    private static boolean isUsable(final TunnelOperationRecord record) {
        return switch (record.status()) {
            case PENDING, ACCEPTED, CONFIRMED, SKIPPED_ALREADY_MATCHING,
                    ACCEPTED_PCEP_BANDWIDTH_UNCONFIRMED -> true;
            case SKIPPED_RECENT_INTENT, FAILED, FAILED_HARD -> false;
        };
    }

    /**
     * <p>Builds structured fields identifying an intent and optional operation.</p>
     * <p>The construction proceeds as follows:</p>
     * <ol><li>Combines intent identifiers with nullable record status, identifier and expiration.</li></ol>
     * @param key intent identity
     * @param record optional operation record
     * @return structured log field map
     */
    private static Map<String, Object> fields(final TunnelIntentKey key, final TunnelOperationRecord record) {
        return StructuredLogger.fields(
                "direction_key", key.directionKey(), "lsp_name", key.lspName(), "pcc_node", key.pccNode(),
                "profile_name", key.profileName(), "class_name", key.className(),
                "ero_fingerprint", key.eroFingerprint(), "operation_status", record == null ? null : record.status(),
                "operation_id", record == null ? null : record.operationId(),
                "expires_at", record == null ? null : record.expiresAt());
    }
}
