/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

import java.time.Instant;
import java.util.Map;

/**
 * Define el registro {@code ClassificationResult} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public record ClassificationResult(
        String requestId,
        String modelName,
        int classId,
        String className,
        double confidence,
        Map<String, Double> probabilities,
        TrafficPolicy policy,
        double processingTimeMs,
        Instant cachedAt,
        Instant expiresAt) {
}
