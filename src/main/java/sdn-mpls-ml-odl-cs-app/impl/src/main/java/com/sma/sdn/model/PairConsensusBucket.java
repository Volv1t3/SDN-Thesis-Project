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

/**
 * <p>Groups recent directional evidence for a tunnel pair and service. The directional evidence registry maintains buckets consumed by the pair-policy consensus service.</p>
 *
 * @param pairKey managed tunnel-pair identifier
 * @param serviceKey normalized identity of the traffic service
 * @param leftEvidence optional evidence from the pair's left side
 * @param rightEvidence optional evidence from the pair's right side
 * @param createdAt bucket creation time
 * @param updatedAt most recent update time
 * @param expiresAt expiration time
 */
public record PairConsensusBucket(
        String pairKey,
        ServiceKey serviceKey,
        Optional<DirectionalPolicyEvidence> leftEvidence,
        Optional<DirectionalPolicyEvidence> rightEvidence,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt) {
    /**
     * <p>Replace null directional evidence optionals with empty optionals.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Replace null directional evidence optionals with empty optionals.</li>
     * </ol>
     *
     * @param pairKey managed tunnel-pair identifier
     * @param serviceKey normalized identity of the traffic service
     * @param leftEvidence optional evidence from the pair's left side
     * @param rightEvidence optional evidence from the pair's right side
     * @param createdAt bucket creation time
     * @param updatedAt most recent update time
     * @param expiresAt expiration time
     */
    public PairConsensusBucket {
        leftEvidence = leftEvidence == null ? Optional.empty() : leftEvidence;
        rightEvidence = rightEvidence == null ? Optional.empty() : rightEvidence;
    }
}
