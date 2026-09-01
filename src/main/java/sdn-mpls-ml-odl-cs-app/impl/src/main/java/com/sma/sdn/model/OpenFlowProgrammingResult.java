/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/** Resume una programacion RESTCONF de un flujo temporal de supresion. */
public record OpenFlowProgrammingResult(String flowId, boolean installed, int statusCode, String detail) {
}
