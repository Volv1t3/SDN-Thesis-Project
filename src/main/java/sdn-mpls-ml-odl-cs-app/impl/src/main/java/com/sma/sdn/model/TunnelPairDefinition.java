/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Describes the managed forward and reverse tunnel directions and their associated routers and switches. The tunnel pair registry uses this definition to resolve pair and direction identities.</p>
 *
 * @param pairKey managed tunnel-pair identifier
 * @param forwardDirection forward managed tunnel direction
 * @param reverseDirection reverse managed tunnel direction
 * @param leftRouterId left-side router identifier
 * @param rightRouterId right-side router identifier
 * @param leftSwitchName left-side switch name
 * @param rightSwitchName right-side switch name
 */
public record TunnelPairDefinition(
        String pairKey,
        TunnelDirection forwardDirection,
        TunnelDirection reverseDirection,
        String leftRouterId,
        String rightRouterId,
        String leftSwitchName,
        String rightSwitchName) {
}
