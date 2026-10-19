/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Classifies the outcome of an OpenDaylight operation for its caller.</p>
 */
public enum OdlCallOutcomeType {
    /** <p>The operation has a confirmed successful outcome.</p> */
    CONFIRMED_SUCCESS,
    /** <p>The operation indicates success without full confirmation.</p> */
    PROVISIONAL_SUCCESS,
    /** <p>The response cannot establish a definitive outcome.</p> */
    AMBIGUOUS,
    /** <p>The operation failed in a way classified as non-recoverable.</p> */
    HARD_FAILURE
}
