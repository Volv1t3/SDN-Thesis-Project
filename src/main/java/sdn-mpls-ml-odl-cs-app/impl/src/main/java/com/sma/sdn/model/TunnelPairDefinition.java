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

/**
 * <p>Describes a managed tunnel pair as two ordered directions together with the routers and
 * switches associated with its left and right sides. It holds configured identities; it does
 * not represent live topology, delegated-LSP status, or installed policy.</p>
 *
 * <p>{@link com.sma.sdn.registry.TunnelPairRegistry} constructs the definition from
 * configuration, resolves either direction to the pair, and uses the switch names to map
 * ingress evidence to a side. Pair-policy processing and operational-state publication use
 * that common identity.</p>
 *
 * @param pairKey managed tunnel-pair identifier
 * @param forwardDirection forward managed tunnel direction
 * @param reverseDirection reverse managed tunnel direction
 * @param leftRouterId left-side router identifier
 * @param rightRouterId right-side router identifier
 * @param leftSwitchName left-side switch name
 * @param rightSwitchName right-side switch name
 */
public record TunnelPairDefinition(
        String pairKey, /*lsr1_lsr4 preconfigured key*/
        TunnelDirection forwardDirection, /*The TunnelDirection object that describes the direction key, and endpoint DTOs
        for the forward direction*/
        TunnelDirection reverseDirection, /*same as above bt for the reverse*/
        String leftRouterId, /*preconfigured and environment based left router ID*/
        String rightRouterId, /*preconfigured and environment base right router ID*/
        String leftSwitchName, /*switch names associated with the left router ID*/
        String rightSwitchName /*switch names associated with the right router ID*/){
}
