/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

import java.time.Instant;
import java.util.Map;

/** Current owner of a service pair and its last directional LSP applications. */
public record ActivePairPolicyState(
        String pairKey,
        ServiceKey serviceKey,
        String className,
        String profileName,
        int dscp,
        int mplsTc,
        int requestedBandwidthKbps,
        String requestedBandwidthBase64,
        int setupPriority,
        int holdPriority,
        String policySchemaVersion,
        String policyHash,
        long generation,
        Instant installedAt,
        Instant lastRefreshedAt,
        Instant expiresAt,
        Map<String, DirectionalLspApplicationRecord> lspApplications) {
    public ActivePairPolicyState {
        lspApplications = lspApplications == null ? Map.of() : Map.copyOf(lspApplications);
    }
}
