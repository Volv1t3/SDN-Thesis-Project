/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/** Canonical, direction-independent identity for a classified service. */
public record ServiceKey(int ethType, int ipProtocol, int canonicalServicePort, String normalizedValue) {
}
