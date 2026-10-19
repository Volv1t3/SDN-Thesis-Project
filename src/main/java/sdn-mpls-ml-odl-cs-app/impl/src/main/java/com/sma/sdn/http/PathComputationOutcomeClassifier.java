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

package com.sma.sdn.http;

import com.sma.sdn.model.OdlCallOutcome;
import com.sma.sdn.model.OdlCallOutcomeType;
import com.sma.sdn.observability.StructuredLogger;
import java.net.http.HttpResponse;

/**
 * <p>Converts an OpenDaylight constrained-path HTTP response into the controller's defensive
 * {@link OdlCallOutcome} model. A nonblank body accompanying HTTP 200 is confirmed success; authentication failures
 * and other client errors are hard failures; server errors, status zero, and absent response bodies remain ambiguous.</p>
 *
 * <p>{@link com.sma.sdn.path.PathComputationService} consumes this classification to return successful responses,
 * stop immediately on hard failure, or retry ambiguous path-computation attempts.</p>
 */
public final class PathComputationOutcomeClassifier {
    /**
     * <p>Records the status, outcome type, and response-body presence for each classification.</p>
     */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(PathComputationOutcomeClassifier.class);

    /**
     * <p>Classifies a constrained-path response without parsing its XML payload. This method treats a blank successful
     * response as ambiguous because callers cannot confirm a usable computed path from the status code alone.</p>
     *
     * <p>The classification performs these steps:</p>
     * <ol>
     *   <li>Reads the response status code.</li>
     *   <li>Returns confirmed success only for HTTP 200 with a nonblank body.</li>
     *   <li>Maps HTTP 401 and 403 to hard failure because retrying cannot repair rejected credentials or access.</li>
     *   <li>Maps server errors, status zero, and missing or blank bodies to an ambiguous outcome eligible for retry.</li>
     *   <li>Maps every other status to hard failure and records the resulting classification at trace level.</li>
     * </ol>
     *
     * <p>The returned failure reason is {@code null} only for confirmed success; all unsuccessful classifications use
     * the HTTP status as their diagnostic reason.</p>
     *
     * @param response constrained-path HTTP response to classify
     * @return defensive outcome used by the path-computation retry policy
     * @throws NullPointerException if {@code response} is {@code null}
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
