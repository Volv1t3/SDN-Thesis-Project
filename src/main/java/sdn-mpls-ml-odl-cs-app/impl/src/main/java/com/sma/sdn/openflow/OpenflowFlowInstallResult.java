/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package com.sma.sdn.openflow;

/**
 * Resume el resultado final de la instalacion RESTCONF de una regla OpenFlow.
 *
 * @param flowId identificador de la regla evaluada
 * @param success indica si ODL acepto la operacion
 * @param retryable indica si el fallo permite un intento posterior
 * @param statusCode codigo HTTP, o cero cuando no se recibio respuesta
 * @param detail descripcion formal del resultado
 */
public record OpenflowFlowInstallResult(
        String flowId, boolean success, boolean retryable, int statusCode, String detail) {
}
