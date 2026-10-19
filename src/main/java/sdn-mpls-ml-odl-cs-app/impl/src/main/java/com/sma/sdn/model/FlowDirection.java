/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Identifies the packet's resolved direction between configured tunnel endpoints.</p>
 */
public enum FlowDirection {
    /** <p>Traffic flows from the configured headend toward the tailend.</p> */
    HEADEND_TO_TAILEND,
    /** <p>Traffic flows from the tailend toward the headend.</p> */
    TAILEND_TO_HEADEND,
    /** <p>The packet direction could not be resolved.</p> */
    UNKNOWN
}
