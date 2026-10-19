/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * <p>Carries correlation identifiers for a packet workflow and its tunnel operations. The current factory reads logging context values so downstream records can retain the workflow and packet sequence.</p>
 *
 * @param workflowId workflow correlation identifier
 * @param packetSequence packet notification sequence
 */
public record WorkflowContext(String workflowId, long packetSequence) {
    /**
     * <p>Build a workflow context from logging context values.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Read workflow and packet sequence values from MDC.</li>
     *   <li>Use a generated workflow ID for a missing or blank ID and zero for a missing sequence.</li>
     *   <li>If the sequence cannot be parsed, use zero and generate an ID only when the original ID was null.</li>
     * </ol>
     *
     * @return workflow context carrying the resolved identifiers
     */
    public static WorkflowContext current() {
        final String workflowId = MDC.get("workflow_id");
        final String sequence = MDC.get("packet_sequence");
        try {
            return new WorkflowContext(
                    workflowId == null || workflowId.isBlank() ? UUID.randomUUID().toString() : workflowId,
                    sequence == null ? 0L : Long.parseLong(sequence));
        } catch (NumberFormatException e) {
            return new WorkflowContext(workflowId == null ? UUID.randomUUID().toString() : workflowId, 0L);
        }
    }
}
