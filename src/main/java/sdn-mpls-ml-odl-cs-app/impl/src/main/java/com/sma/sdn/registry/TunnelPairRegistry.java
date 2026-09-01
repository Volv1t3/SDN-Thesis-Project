/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.registry;

import com.sma.sdn.config.AppConfig;
import com.sma.sdn.model.TunnelDirection;
import com.sma.sdn.model.TunnelPairDefinition;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Normalizes either configured direction into the same stable policy pair. */
public final class TunnelPairRegistry {
    private final TunnelPairDefinition pair;

    public TunnelPairRegistry(final AppConfig config) {
        Objects.requireNonNull(config, "config");
        this.pair = new TunnelPairDefinition(
                "lsr1_lsr4", config.headendToTailend(), config.tailendToHeadend(),
                config.headend().routerId(), config.tailend().routerId(), "ECHO", "FOXTROT");
    }

    public TunnelPairDefinition requirePairForDirection(final String directionKey) {
        if (pair.forwardDirection().directionKey().equals(directionKey)
                || pair.reverseDirection().directionKey().equals(directionKey)) {
            return pair;
        }
        throw new IllegalArgumentException("Direccion no administrada por ningun par: " + directionKey);
    }

    public List<TunnelDirection> requireManagedDirections(final String pairKey) {
        requirePair(pairKey);
        return List.of(pair.forwardDirection(), pair.reverseDirection());
    }

    public String normalizePairKey(final TunnelDirection direction) {
        return requirePairForDirection(direction.directionKey()).pairKey();
    }

    /** Returns the immutable managed pair definition for operational exposure. */
    public TunnelPairDefinition snapshot() {
        return pair;
    }

    public String sideForSwitch(final String pairKey, final String ingressSwitchName) {
        requirePair(pairKey);
        final String switchName = ingressSwitchName == null ? "" : ingressSwitchName.toUpperCase(Locale.ROOT);
        if (pair.leftSwitchName().equals(switchName)) {
            return "LEFT";
        }
        if (pair.rightSwitchName().equals(switchName)) {
            return "RIGHT";
        }
        throw new IllegalArgumentException("Switch no pertenece al par " + pairKey + ": " + ingressSwitchName);
    }

    private void requirePair(final String pairKey) {
        if (!pair.pairKey().equals(pairKey)) {
            throw new IllegalArgumentException("Par no administrado: " + pairKey);
        }
    }
}
