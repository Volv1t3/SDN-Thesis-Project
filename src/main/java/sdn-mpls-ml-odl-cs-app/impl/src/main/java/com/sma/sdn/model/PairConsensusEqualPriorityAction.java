/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/** Safe default defers equal-priority policy conflicts; class order is test-only. */
public enum PairConsensusEqualPriorityAction {
    KEEP_CURRENT_OR_DEFER,
    CLASS_ORDER
}
