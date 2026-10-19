/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Describes the classified outcome of an OpenDaylight HTTP operation. Operation-specific outcome classifiers construct it so callers can distinguish confirmation, uncertainty, and failure.</p>
 *
 * @param type outcome or decision type
 * @param httpStatus HTTP response status
 * @param failureReason diagnostic failure reason, when available
 * @param noAck whether the operation lacks an acknowledgement
 */
public record OdlCallOutcome(
        OdlCallOutcomeType type,
        int httpStatus,
        String failureReason,
        boolean noAck) {

}
