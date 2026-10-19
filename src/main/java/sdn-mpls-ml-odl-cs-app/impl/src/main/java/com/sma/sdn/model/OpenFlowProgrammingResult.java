/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Reports an OpenFlow programming attempt, its flow identifier, installation flag, HTTP status, and diagnostic detail to the provisioning workflow.</p>
 *
 * @param flowId OpenFlow flow identifier
 * @param installed whether the flow was installed
 * @param statusCode HTTP status code
 * @param detail diagnostic detail
 */
public record OpenFlowProgrammingResult(String flowId, boolean installed, int statusCode, String detail) {
}
