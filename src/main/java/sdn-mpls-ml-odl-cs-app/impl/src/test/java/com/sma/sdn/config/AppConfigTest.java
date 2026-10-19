/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AppConfigTest {
    @Test
    void retiredEnvironmentKeysDoNotChangeTheConfiguration() {
        final AppConfig defaults = AppConfig.from(Map.of());
        final AppConfig withRetiredKeys = AppConfig.from(Map.of(
                "SMA_HEADEND_TO_TAILEND_INGRESS", "invalid",
                "SMA_TAILEND_TO_HEADEND_INGRESS", "invalid",
                "SMA_TUNNEL_UPDATE_SCOPE", "invalid",
                "SMA_TUNNEL_OPERATION_JOURNAL_MAX_ENTRIES", "invalid",
                "SMA_LSP_APPLICATION_REAPPLY_ON_BANDWIDTH_MISMATCH", "invalid",
                "SMA_LSP_APPLICATION_REAPPLY_ON_ERO_MISMATCH", "invalid",
                "SMA_LSP_APPLICATION_REAPPLY_ON_PRIORITY_MISMATCH", "invalid"));

        assertEquals(defaults, withRetiredKeys);
    }

    @Test
    void formerPairPolicyModeOverridesCannotChangeBehavior() {
        final AppConfig defaults = AppConfig.from(Map.of());
        final AppConfig overridden = AppConfig.from(Map.ofEntries(
                Map.entry("SMA_PAIR_CONSENSUS_ENABLED", "false"),
                Map.entry("SMA_PAIR_CONSENSUS_REQUIRE_BOTH_DIRECTIONS", "false"),
                Map.entry("SMA_PAIR_CONSENSUS_SINGLE_SIDE_PROVISIONAL_ENABLED", "true"),
                Map.entry("SMA_PAIR_CONSENSUS_EQUAL_PRIORITY_ACTION", "CLASS_ORDER"),
                Map.entry("SMA_PAIR_POLICY_PREEMPTION_MODE", "DISABLED"),
                Map.entry("SMA_PAIR_POLICY_LSP_APPLICATION_SCOPE", "OBSERVED_DIRECTION"),
                Map.entry("SMA_LSP_APPLICATION_REQUIRE_ALL_DIRECTIONS", "false")));
        assertEquals(defaults, overridden);
    }
}
