/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

import java.util.Objects;

/**
 * <p>Identifies a desired directional LSP operation by its endpoint, policy, route fingerprint, and path parameters. The tunnel operation registry uses it to recognize repeated intents.</p>
 *
 * @param directionKey managed tunnel-direction identifier
 * @param pccNode PCEP client node identifier
 * @param lspName symbolic LSP name
 * @param profileName traffic-policy profile name
 * @param className classification class name
 * @param bandwidthBase64 PCEP bandwidth encoding
 * @param eroFingerprint explicit-route fingerprint
 * @param setupPriority LSP setup priority
 * @param holdPriority LSP holding priority
 * @param algorithm path-computation algorithm
 * @param classType path-computation class type
 */
public record TunnelIntentKey(
        String directionKey,
        String pccNode,
        String lspName,
        String profileName,
        String className,
        String bandwidthBase64,
        String eroFingerprint,
        int setupPriority,
        int holdPriority,
        String algorithm,
        int classType) {
    /**
     * <p>Require the direction, PCC, LSP, policy, bandwidth, ERO fingerprint, and algorithm strings to be non-null.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Require the direction, PCC, LSP, policy, bandwidth, ERO fingerprint, and algorithm strings to be non-null.</li>
     * </ol>
     *
     *
     * @param directionKey managed tunnel-direction identifier
     * @param pccNode PCEP client node identifier
     * @param lspName symbolic LSP name
     * @param profileName traffic-policy profile name
     * @param className classification class name
     * @param bandwidthBase64 PCEP bandwidth encoding
     * @param eroFingerprint explicit-route fingerprint
     * @param setupPriority LSP setup priority
     * @param holdPriority LSP holding priority
     * @param algorithm path-computation algorithm
     * @param classType path-computation class type
     * @throws NullPointerException if a required value or copied list element is null
     */
    public TunnelIntentKey {
        Objects.requireNonNull(directionKey, "directionKey");
        Objects.requireNonNull(pccNode, "pccNode");
        Objects.requireNonNull(lspName, "lspName");
        Objects.requireNonNull(profileName, "profileName");
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(bandwidthBase64, "bandwidthBase64");
        Objects.requireNonNull(eroFingerprint, "eroFingerprint");
        Objects.requireNonNull(algorithm, "algorithm");
    }
}
