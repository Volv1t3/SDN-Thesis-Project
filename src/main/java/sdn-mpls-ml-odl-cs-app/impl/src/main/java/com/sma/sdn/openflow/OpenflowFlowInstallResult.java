/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package com.sma.sdn.openflow;

/**
 * <p>Reports the final outcome of a RESTCONF flow installation, including whether a
 * failure remains retryable. {@link OpenflowFlowProvisioningService} returns this value
 * to its bootstrap callers.</p>
 *
 * @param flowId identifier of the flow being installed
 * @param success whether the installation was accepted
 * @param retryable whether the failure permits a subsequent attempt
 * @param statusCode last observed HTTP status, or zero if no response was received
 * @param detail diagnostic description of the outcome
 */
public record OpenflowFlowInstallResult(
        String flowId, boolean success, boolean retryable, int statusCode, String detail) {
}
