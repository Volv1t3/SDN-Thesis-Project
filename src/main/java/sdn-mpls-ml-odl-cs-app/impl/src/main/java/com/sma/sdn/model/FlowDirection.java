/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Identifies the direction of a packet between the configured headend and tailend, or
 * indicates that its ingress cannot be associated with either direction. The value describes
 * observed packet direction rather than the scope of a subsequent tunnel update.</p>
 *
 * <p>{@link com.sma.sdn.packet.PacketInFeatureExtractor} obtains an ingress direction from
 * {@link com.sma.sdn.config.AppConfig}, while {@link com.sma.sdn.registry.DirectionRegistry}
 * resolves the packet context and maps known directions to tunnel directions. The workflow
 * stops processing when the resolved direction is unknown.</p>
 */
public enum FlowDirection {
    /** <p>Traffic flows from the configured headend toward the tailend.</p> */
    HEADEND_TO_TAILEND,
    /** <p>Traffic flows from the tailend toward the headend.</p> */
    TAILEND_TO_HEADEND,
    /** <p>The packet direction could not be resolved.</p> */
    UNKNOWN
}
