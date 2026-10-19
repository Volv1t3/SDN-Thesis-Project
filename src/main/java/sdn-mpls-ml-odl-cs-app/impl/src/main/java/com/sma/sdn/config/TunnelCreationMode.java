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

package com.sma.sdn.config;

/**
 * <p>Enumerates the supported mechanism for applying path changes to MPLS tunnels.</p>
 *
 * <p>{@link AppConfig} parses this setting at startup, and the provider rejects initialization unless the selected
 * value is supported by its delegated-LSP workflow.</p>
 */
public enum TunnelCreationMode {
    /** <p>Update an existing PCEP-delegated LSP through the OpenDaylight operations API.</p> */
    DELEGATED_TUNNEL_UPDATE
}
