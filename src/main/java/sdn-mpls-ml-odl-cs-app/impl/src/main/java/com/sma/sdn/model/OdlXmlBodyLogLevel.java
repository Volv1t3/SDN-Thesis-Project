/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Selects whether XML request and response bodies exchanged with OpenDaylight are omitted
 * or logged at debug or trace level. This setting governs body verbosity independently of the
 * other exchange metadata recorded by the HTTP logging component.</p>
 *
 * <p>{@link com.sma.sdn.config.AppConfig} parses the configured value, and
 * {@link com.sma.sdn.http.OdlXmlExchangeLogger} applies it when recording RESTCONF exchanges.
 * XML bodies may contain network configuration and should be logged accordingly.</p>
 */
public enum OdlXmlBodyLogLevel {
    /** <p>Do not log XML bodies.</p> */
    OFF,
    /** <p>Log XML bodies at debug level.</p> */
    DEBUG,
    /** <p>Log XML bodies at trace level.</p> */
    TRACE
}
