/*
 * Copyright (c) 2026 Santiago Arellano 00328370.
 *
 * Universidad San Francisco de Quito
 * SDN-MPLS-ML Tech Demostrator Thesis Project
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * <p>Represents one explicit-route subobject and its loose-hop flag. Path translation and PCEP serializers use these values to describe a delegated LSP route.</p>
 *
 * @param loose whether the subobject permits a loose hop
 * @param ipPrefix IP prefix identifying the route subobject
 */
public record EroSubobject(boolean loose, String ipPrefix) {
}
