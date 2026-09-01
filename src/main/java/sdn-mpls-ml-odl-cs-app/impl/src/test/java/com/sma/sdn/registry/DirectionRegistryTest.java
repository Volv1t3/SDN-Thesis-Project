/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sma.sdn.config.AppConfig;
import com.sma.sdn.model.FlowDirection;
import com.sma.sdn.model.TunnelUpdateScope;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifica que el alcance de actualizacion no procese direcciones adicionales por defecto. */
class DirectionRegistryTest {
    @Test
    void observedScopeProcessesOnlyTheIngressDirection() {
        final DirectionRegistry registry = registry(TunnelUpdateScope.OBSERVED_DIRECTION);

        assertEquals(List.of("lsr1_to_lsr4"), keys(registry, FlowDirection.HEADEND_TO_TAILEND,
                TunnelUpdateScope.OBSERVED_DIRECTION));
        assertEquals(List.of("lsr4_to_lsr1"), keys(registry, FlowDirection.TAILEND_TO_HEADEND,
                TunnelUpdateScope.OBSERVED_DIRECTION));
    }

    @Test
    void bidirectionalScopeKeepsObservedDirectionFirst() {
        final DirectionRegistry registry = registry(TunnelUpdateScope.BIDIRECTIONAL_PAIR);

        assertEquals(List.of("lsr1_to_lsr4", "lsr4_to_lsr1"), keys(registry,
                FlowDirection.HEADEND_TO_TAILEND, TunnelUpdateScope.BIDIRECTIONAL_PAIR));
        assertEquals(List.of("lsr4_to_lsr1", "lsr1_to_lsr4"), keys(registry,
                FlowDirection.TAILEND_TO_HEADEND, TunnelUpdateScope.BIDIRECTIONAL_PAIR));
        assertThrows(IllegalStateException.class,
                () -> registry.requireTunnelDirectionsForScope(
                        FlowDirection.UNKNOWN, TunnelUpdateScope.OBSERVED_DIRECTION));
    }

    private static DirectionRegistry registry(final TunnelUpdateScope scope) {
        return new DirectionRegistry(AppConfig.from(Map.of("SMA_TUNNEL_UPDATE_SCOPE", scope.name())));
    }

    private static List<String> keys(
            final DirectionRegistry registry, final FlowDirection direction, final TunnelUpdateScope scope) {
        return registry.requireTunnelDirectionsForScope(direction, scope).stream()
                .map(value -> value.directionKey())
                .toList();
    }
}
