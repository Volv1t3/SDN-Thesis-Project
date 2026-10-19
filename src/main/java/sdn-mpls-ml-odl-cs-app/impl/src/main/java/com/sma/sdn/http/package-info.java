/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

/**
 * <p>Contains the controller's blocking HTTP transport adapters and defensive OpenDaylight response classifiers.
 * {@link com.sma.sdn.http.OdlRestconfDataClient} accesses topology and OpenFlow data resources,
 * {@link com.sma.sdn.http.OdlOperationsClient} invokes XML RPC operations, and
 * {@link com.sma.sdn.http.ClassifierRestClient} calls the external JSON classification API.</p>
 *
 * <p>The package deliberately separates transport from interpretation. Clients preserve raw HTTP responses for
 * topology, path, tunnel, and OpenFlow services, while outcome classifiers map operation-specific statuses and body
 * conditions into {@link com.sma.sdn.model.OdlCallOutcome} values used for retry and failure decisions.</p>
 */
package com.sma.sdn.http;
