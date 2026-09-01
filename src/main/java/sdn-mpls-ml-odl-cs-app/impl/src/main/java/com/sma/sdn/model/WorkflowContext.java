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

/** Correlacion estable del flujo PacketIn que solicita una operacion de tunel. */
public record WorkflowContext(String workflowId, long packetSequence) {
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
