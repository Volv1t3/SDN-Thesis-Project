/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Selects whether a tunnel update is scoped to the observed packet direction or to both
 * directions of a tunnel pair. This type expresses a configured scope; it does not itself
 * compute a path or send an update.</p>
 *
 * <p>{@link com.sma.sdn.config.AppConfig} parses the scope and
 * {@link com.sma.sdn.registry.DirectionRegistry} uses it to select the corresponding
 * {@link TunnelDirection} values. This setting is distinct from pair-policy LSP application
 * scope and should not be treated as evidence that both directions were updated.</p>
 */
public enum TunnelUpdateScope {
    /** <p>Scope the update to the observed direction.</p> */
    OBSERVED_DIRECTION,
    /** <p>Scope the update to both directions of the pair.</p> */
    BIDIRECTIONAL_PAIR
}
