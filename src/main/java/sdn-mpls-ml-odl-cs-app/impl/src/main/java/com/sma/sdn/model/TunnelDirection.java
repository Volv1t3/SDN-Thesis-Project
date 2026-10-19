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
 * <p>Identifies one managed tunnel direction by its key and ordered source and destination
 * endpoints. The direction is a configuration identity, not a snapshot of an LSP or proof
 * that an LSP has been established.</p>
 *
 * <p>{@link AppConfig#from(java.util.Map)} constructs forward and reverse instances with
 * their endpoints swapped. The keys come from {@code SMA_FORWARD_DIRECTION_KEY} and
 * {@code SMA_REVERSE_DIRECTION_KEY}, defaulting to {@code lsr1_to_lsr4} and
 * {@code lsr4_to_lsr1}. The direction and tunnel-pair registries use these instances to
 * associate packet ingress and policy evidence with managed LSP directions.</p>
 *
 * @param directionKey managed tunnel-direction identifier
 * @param source source tunnel endpoint
 * @param destination destination tunnel endpoint
 */
public record TunnelDirection(String directionKey, TunnelEndpoint source, TunnelEndpoint destination) {
}
