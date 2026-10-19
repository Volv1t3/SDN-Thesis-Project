/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Represents the traffic policy carried by a classifier result. The workflow converts its
 * markings and path constraints into directional policy evidence for pair-policy coordination;
 * the fallback fields identify a policy selected as a classifier fallback.</p>
 *
 * @param profileName name of the traffic profile
 * @param dscp differentiated services code point associated with the policy
 * @param mplsTc MPLS traffic-class value associated with the policy
 * @param pathConstraints bandwidth and priority constraints for path and LSP decisions
 * @param policyFallback whether the classifier returned a fallback policy
 * @param policyFallbackReason classifier-provided reason for a fallback, when applicable
 */
public record TrafficPolicy(
        String profileName,
        int dscp,
        int mplsTc,
        PathConstraints pathConstraints,
        boolean policyFallback,
        String policyFallbackReason) {
}
