/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

import java.util.Objects;

/** Identifica de forma determinista el estado deseado de un LSP delegado. */
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
