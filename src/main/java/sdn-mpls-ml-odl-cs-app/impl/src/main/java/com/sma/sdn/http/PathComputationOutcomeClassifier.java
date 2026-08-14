/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.http;

import com.sma.sdn.model.OdlCallOutcome;
import com.sma.sdn.model.OdlCallOutcomeType;
import com.sma.sdn.observability.StructuredLogger;
import java.net.http.HttpResponse;

/**
 * Define la clase {@code PathComputationOutcomeClassifier} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class PathComputationOutcomeClassifier {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(PathComputationOutcomeClassifier.class);
    /**
     * Clasifica una respuesta HTTP de acuerdo con el modelo defensivo de resultados ODL.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param response valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    public OdlCallOutcome classify(final HttpResponse<String> response) {
        final int statusCode = response.statusCode();
        final OdlCallOutcome outcome;
        if (statusCode == 200 && response.body() != null && !response.body().isBlank()) {
            outcome = new OdlCallOutcome(OdlCallOutcomeType.CONFIRMED_SUCCESS, statusCode, null, false);
        } else if (statusCode == 401 || statusCode == 403) {
            outcome = new OdlCallOutcome(OdlCallOutcomeType.HARD_FAILURE, statusCode, "HTTP " + statusCode, false);
        } else if (statusCode >= 500 || statusCode == 0
                || response.body() == null || response.body().isBlank()) {
            outcome = new OdlCallOutcome(OdlCallOutcomeType.AMBIGUOUS, statusCode, "HTTP " + statusCode, false);
        } else {
            outcome = new OdlCallOutcome(OdlCallOutcomeType.HARD_FAILURE, statusCode, "HTTP " + statusCode, false);
        }
        LOG.trace("path_computation_outcome_classified", "classify",
                "Se clasifico defensivamente la respuesta del calculo de camino",
                StructuredLogger.fields("status_code", statusCode, "outcome", outcome.type(),
                        "body_present", response.body() != null && !response.body().isBlank()));
        return outcome;
    }
}
