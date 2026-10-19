/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.topology;

import java.time.Instant;

/**
 * <p>Immutable externally observable state of the BGP-LS refresh lifecycle, assembled by
 * {@link TopologyRefreshService#status()} from separately read service fields.</p>
 *
 * @param lastSuccessfulRefresh most recent successful discovery or refresh
 * @param lastRefreshAttempt time of the most recent recorded attempt
 * @param freshUntil expiration deadline computed from the last success and configured TTL
 * @param lastFailure last failure description, or an empty string after success
 * @param fresh whether the snapshot is currently within its freshness deadline
 * @param refreshInProgress whether a refresh is currently marked in progress
 * @param successfulRefreshCount number of successful initial discoveries and refreshes
 * @param failedRefreshCount number of failed refreshes
 */
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
