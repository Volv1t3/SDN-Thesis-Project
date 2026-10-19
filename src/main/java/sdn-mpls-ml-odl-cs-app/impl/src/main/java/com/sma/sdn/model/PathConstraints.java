/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Groups requested bandwidth and RSVP-TE setup and hold priorities. Classification policies
 * carry these values, and path computation and delegated LSP application use them to construct
 * their respective requests.</p>
 *
 * @param requestedBandwidthKbps requested bandwidth in kilobits per second
 * @param setupPriority setup priority used for path and LSP requests
 * @param holdPriority holding priority used for path and LSP requests
 */
public record PathConstraints(long requestedBandwidthKbps, int setupPriority, int holdPriority) {
}
