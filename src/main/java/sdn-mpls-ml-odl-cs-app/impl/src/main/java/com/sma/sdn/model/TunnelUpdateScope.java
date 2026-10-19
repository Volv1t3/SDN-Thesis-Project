/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Represents configured scope choices for tunnel updates.</p>
 */
public enum TunnelUpdateScope {
    /** <p>Scope the update to the observed direction.</p> */
    OBSERVED_DIRECTION,
    /** <p>Scope the update to both directions of the pair.</p> */
    BIDIRECTIONAL_PAIR
}
