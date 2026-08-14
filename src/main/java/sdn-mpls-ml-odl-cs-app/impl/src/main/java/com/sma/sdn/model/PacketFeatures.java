/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

/**
 * Define el registro {@code PacketFeatures} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad concreta del flujo de control, de los 
 * modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public record PacketFeatures(int ethType, int ipProto, int srcPort, int dstPort) {
}
