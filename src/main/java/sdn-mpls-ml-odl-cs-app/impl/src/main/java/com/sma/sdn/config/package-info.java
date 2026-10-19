/*
 * Copyright (c) 2026 Santiago Arellano 00328370.
 *
 * Universidad San Francisco de Quito
 * SDN-MPLS-ML Tech Demostrator Thesis Project
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

/**
 * <p>Loads, normalizes, and validates the runtime configuration shared by the SDN-MPLS-ML controller bundle.</p>
 *
 * <p>The package defines the immutable {@link com.sma.sdn.config.AppConfig} snapshot, its supported tunnel-creation
 * mode, and the optional token-based ingress mapping value. Provider initialization creates one configuration instance
 * and supplies it to HTTP, topology, path-computation, OpenFlow, tunnel, policy, and observability collaborators.</p>
 */
package com.sma.sdn.config;
