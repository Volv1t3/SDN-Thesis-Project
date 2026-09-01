/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/** Defines the two managed directional LSPs that belong to one policy pair. */
public record TunnelPairDefinition(
        String pairKey,
        TunnelDirection forwardDirection,
        TunnelDirection reverseDirection,
        String leftRouterId,
        String rightRouterId,
        String leftSwitchName,
        String rightSwitchName) {
}
