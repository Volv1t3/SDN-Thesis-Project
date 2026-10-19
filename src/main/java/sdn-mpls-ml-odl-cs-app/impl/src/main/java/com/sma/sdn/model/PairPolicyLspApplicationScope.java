/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Selects which managed LSP directions receive an admitted pair-policy candidate.</p>
 */
public enum PairPolicyLspApplicationScope {
    /** <p>Apply the selected candidate to both managed directions.</p> */
    BIDIRECTIONAL_PAIR,
    /** <p>Apply the selected candidate only to the direction of the triggering evidence.</p> */
    OBSERVED_DIRECTION_ONLY
}
