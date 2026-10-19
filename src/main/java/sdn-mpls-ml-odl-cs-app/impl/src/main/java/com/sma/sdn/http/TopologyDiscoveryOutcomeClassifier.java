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
 * <p>Converts topology-discovery HTTP responses into the controller's defensive {@link OdlCallOutcome} model. It
 * requires HTTP 200 and a nonblank XML body for confirmed success, distinguishes authorization failures from retryable
 * uncertainty, and leaves server-side or body-absence failures ambiguous.</p>
 *
 * <p>{@link com.sma.sdn.topology.TopologyDiscoveryService} and
 * {@link com.sma.sdn.topology.TopologyRefreshService} use these outcomes to decide whether to accept, reject, or retry
 * RESTCONF topology reads.</p>
 */
public final class TopologyDiscoveryOutcomeClassifier {
    /**
     * <p>Records each topology response classification and whether a usable body was present.</p>
     */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(TopologyDiscoveryOutcomeClassifier.class);

    /**
     * <p>Classifies a topology response without deserializing its XML body. A blank body prevents confirmed success
     * even when OpenDaylight returned HTTP 200.</p>
     *
     * <p>The classification performs these steps:</p>
     * <ol>
     *   <li>Reads the response status code.</li>
     *   <li>Returns confirmed success for HTTP 200 with a nonblank body.</li>
     *   <li>Maps HTTP 401 and 403 to hard failure because the request is not authorized.</li>
     *   <li>Maps server errors, status zero, and absent or blank bodies to an ambiguous outcome.</li>
     *   <li>Maps remaining statuses to hard failure and emits a trace record of the decision.</li>
     * </ol>
     *
     * <p>Topology services deserialize the response body only after this classifier reports confirmed success.</p>
     *
     * @param response RESTCONF topology response to classify
     * @return defensive outcome used by topology discovery and refresh retry logic
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
        LOG.trace("topology_discovery_outcome_classified", "classify",
                "Se clasifico defensivamente la respuesta de descubrimiento topologico",
                StructuredLogger.fields("status_code", statusCode, "outcome", outcome.type(),
                        "body_present", response.body() != null && !response.body().isBlank()));
        return outcome;
    }
}
