/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.topology;

import java.time.Instant;

/** Immutable externally observable state of the BGP-LS refresh lifecycle. */
public record TopologyRefreshStatus(
        Instant lastSuccessfulRefresh,
        Instant lastRefreshAttempt,
        Instant freshUntil,
        String lastFailure,
        boolean fresh,
        boolean refreshInProgress,
        long successfulRefreshCount,
        long failedRefreshCount) {
}
