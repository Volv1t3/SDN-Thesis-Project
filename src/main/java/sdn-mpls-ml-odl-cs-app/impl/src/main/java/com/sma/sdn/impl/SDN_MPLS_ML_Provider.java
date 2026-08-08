/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.impl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main lifecycle entry point for the SDN-MPLS-ML OpenDaylight controller-side
 * application.
 *
 * <p>This provider is created and destroyed by the OSGi Blueprint container
 * when the Karaf feature is installed or removed.
 *
 * <p>Current milestone:
 * <ul>
 *   <li>Validate that the bundle is loaded by Karaf.</li>
 *   <li>Validate that Blueprint starts the application.</li>
 *   <li>Validate that startup and shutdown logs are emitted.</li>
 * </ul>
 *
 * <p>Future responsibilities:
 * <ul>
 *   <li>Register an OpenFlow PacketIn listener.</li>
 *   <li>Extract first-packet ML features.</li>
 *   <li>Call the Python FastAPI inference service.</li>
 *   <li>Install reactive OpenFlow rules.</li>
 *   <li>Trigger MPLS/PCE policy actions.</li>
 * </ul>
 */
public final class SDN_MPLS_ML_Provider implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(SDN_MPLS_ML_Provider.class);

    /**
     * Called by Blueprint when the application bundle is activated.
     */
    public void init() {
        LOG.info("SDN-MPLS-ML controller application started");
    }

    /**
     * Called by Blueprint when the application bundle is stopped or removed.
     */
    @Override
    public void close() {
        LOG.info("SDN-MPLS-ML controller application stopped");
    }
}