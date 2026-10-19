/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Groups requested bandwidth with logical setup and hold priorities from a classification
 * policy. The controller compares the priorities during consensus and preemption; the current
 * PCEP update request carries bandwidth and ERO, not these priority values.</p>
 *
 * @param requestedBandwidthKbps requested bandwidth in kilobits per second
 * @param setupPriority logical setup priority used for controller policy ordering
 * @param holdPriority logical hold priority used to break setup-priority ties
 */
public record PathConstraints(long requestedBandwidthKbps, int setupPriority, int holdPriority) {
}
