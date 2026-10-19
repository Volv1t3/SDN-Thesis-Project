/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Controls the configured verbosity of OpenDaylight XML body logging.</p>
 */
public enum OdlXmlBodyLogLevel {
    /** <p>Do not log XML bodies.</p> */
    OFF,
    /** <p>Log XML bodies at debug level.</p> */
    DEBUG,
    /** <p>Log XML bodies at trace level.</p> */
    TRACE
}
