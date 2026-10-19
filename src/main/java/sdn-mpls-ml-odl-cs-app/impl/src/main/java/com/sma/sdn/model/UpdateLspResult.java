/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Reports whether an update-lsp response is successful, provisional, or a hard failure, together with its diagnostic reason and HTTP status. The update outcome classifier and delegated LSP workflow use this result.</p>
 *
 * @param success whether the response indicates success
 * @param provisionalSuccess whether success is provisional
 * @param hardFailure whether the response is a hard failure
 * @param failureReason diagnostic failure reason, when available
 * @param httpStatus HTTP response status
 */
public record UpdateLspResult(
        boolean success, boolean provisionalSuccess, boolean hardFailure, String failureReason, int httpStatus) {
}
