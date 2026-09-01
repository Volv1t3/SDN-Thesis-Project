/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

import java.time.Instant;
import java.util.Optional;

/** Holds the most recent usable evidence from the left and right sides of a pair. */
public record PairConsensusBucket(
        String pairKey,
        ServiceKey serviceKey,
        Optional<DirectionalPolicyEvidence> leftEvidence,
        Optional<DirectionalPolicyEvidence> rightEvidence,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt) {
    public PairConsensusBucket {
        leftEvidence = leftEvidence == null ? Optional.empty() : leftEvidence;
        rightEvidence = rightEvidence == null ? Optional.empty() : rightEvidence;
    }
}
