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

package com.sma.sdn.model;

import com.sma.sdn.config.AppConfig;

/**
 * <p>Identifies one managed tunnel direction by its key and source and destination endpoints. Direction and tunnel registries use it when resolving packet direction and LSP operations.</p>
 *
 * @param directionKey managed tunnel-direction identifier
 * @param source source tunnel endpoint
 * @param destination destination tunnel endpoint
 */
public record TunnelDirection(String directionKey, TunnelEndpoint source, TunnelEndpoint destination) {
    /**
     * The implemenaiton for this specific record does not need anything else aside from the declaration of the immutable object.
     * However for clarity, notice that the environment variable <b>SMA_FORWARD_DIRECTION_KEY and SMA_REVERSE_DIRECTION_KEY</b> are used during the
     * topology instatiation to represent the placeholders for the forward and reverse direction keys.
     *
     * These values are used to create the TunnelDirection records for the forward and reverse directions of a tunnel. This is done
     * in the corresponding {@link AppConfig} class. The default values that this direction key can take ergo can be
     * lsr1_to_lsr4 and lsr4_to_lsr1.
     *
     * For the corresponding source and destination endpoints, this is also derived in the {@link AppConfig} class based
     * on the head end and tail end router configuration. Internally each of these contains the name and ip address as
     * well as pcc node which is configured through environment variables in this deployment.
    * */
}
