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
import com.sma.sdn.util.XmlSupport;
import java.net.http.HttpResponse;

/**
 * <p>Classifies immediate OpenDaylight {@code update-lsp} results while preserving the operation's provisional
 * semantics. HTTP 200 is provisional success unless the XML contains a failure other than {@code no-ack}; server or
 * transport-equivalent status values are ambiguous, and remaining non-success statuses are hard failures.</p>
 *
 * <p>The classifier uses {@link XmlSupport} only when a nonblank response body is available. Its
 * {@link OdlCallOutcome} retains the failure reason and a dedicated no-ack flag so tunnel orchestration can distinguish
 * an acknowledged error from an update that still requires operational-state verification.</p>
 */
public final class UpdateLspOutcomeClassifier {
    /**
     * <p>Records update-LSP classifications, including provisional and no-ack decisions.</p>
     */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(UpdateLspOutcomeClassifier.class);

    /**
     * <p>Classifies an HTTP response by forwarding its status and body to
     * {@link #classify(int, String)}.</p>
     *
     * <p>The adapter performs these steps:</p>
     * <ol>
     *   <li>Reads the HTTP status from {@code response}.</li>
     *   <li>Reads the string response body without modifying it.</li>
     *   <li>Delegates both values to the status-and-body overload and returns its outcome.</li>
     * </ol>
     *
     * @param response update-LSP HTTP response to classify
     * @return defensive outcome derived from the response status and XML body
     * @throws NullPointerException if {@code response} is {@code null}
     * @throws IllegalArgumentException if a nonblank HTTP 200 body is not parseable XML
     */
    public OdlCallOutcome classify(final HttpResponse<String> response) {
        return classify(response.statusCode(), response.body());
    }

    /**
     * <p>Classifies an update-LSP status and optional XML result. A successful HTTP exchange is intentionally
     * provisional because persistence and PCC acknowledgement must be confirmed separately.</p>
     *
     * <p>The classification performs these steps:</p>
     * <ol>
     *   <li>For HTTP 200, extracts the first namespace-independent {@code failure} element from the XML body.</li>
     *   <li>Returns provisional success when no failure is present.</li>
     *   <li>Returns provisional success with {@link OdlCallOutcome#noAck()} set when the failure is {@code no-ack}.</li>
     *   <li>Maps any other HTTP 200 failure value to hard failure.</li>
     *   <li>Maps status zero and server errors to ambiguous outcomes and all other statuses to hard failure.</li>
     *   <li>Records the classification, failure presence, and no-ack flag at trace level.</li>
     * </ol>
     *
     * <p>The resulting outcome can direct a caller toward operational verification, immediate rejection, or
     * reconciliation after an indeterminate exchange.</p>
     *
     * @param statusCode HTTP status returned by OpenDaylight, or zero for an indeterminate transport result
     * @param body optional update-LSP XML response body
     * @return defensive update-LSP outcome retaining any extracted failure reason
     * @throws IllegalArgumentException if {@code statusCode} is 200 and a nonblank body cannot be parsed as XML
     */
    public OdlCallOutcome classify(final int statusCode, final String body) {
        final OdlCallOutcome outcome;
        if (statusCode == 200) {
            final String failure = failure(body);
            if (failure == null || failure.isBlank()) {
                outcome = new OdlCallOutcome(OdlCallOutcomeType.PROVISIONAL_SUCCESS, statusCode, null, false);
            } else if ("no-ack".equalsIgnoreCase(failure)) {
                outcome = new OdlCallOutcome(OdlCallOutcomeType.PROVISIONAL_SUCCESS, statusCode, failure, true);
            } else {
                outcome = new OdlCallOutcome(OdlCallOutcomeType.HARD_FAILURE, statusCode, failure, false);
            }
        } else if (statusCode >= 500 || statusCode == 0) {
            outcome = new OdlCallOutcome(OdlCallOutcomeType.AMBIGUOUS, statusCode, "HTTP " + statusCode, false);
        } else {
            outcome = new OdlCallOutcome(OdlCallOutcomeType.HARD_FAILURE, statusCode, "HTTP " + statusCode, false);
        }
        LOG.trace("update_lsp_outcome_classified", "classify",
                "Se clasifico defensivamente la respuesta de update-lsp",
                StructuredLogger.fields("status_code", statusCode, "outcome", outcome.type(),
                        "no_ack", outcome.noAck(),
                        "failure_present", outcome.failureReason() != null));
        return outcome;
    }

    /**
     * <p>Extracts the first update-LSP failure value from an optional XML response body.</p>
     *
     * <p>The extraction performs these steps:</p>
     * <ol>
     *   <li>Returns {@code null} immediately when the body is absent or blank.</li>
     *   <li>Parses the body through the securely configured {@link XmlSupport#parse(String)} helper.</li>
     *   <li>Evaluates a namespace-independent XPath for the first {@code failure} element and returns its text.</li>
     * </ol>
     *
     * <p>{@link #classify(int, String)} uses the extracted value to separate provisional success from hard failure.</p>
     *
     * @param body optional update-LSP XML body
     * @return first failure value, an empty string when the element is absent, or {@code null} for no body
     * @throws IllegalArgumentException if the nonblank body cannot be parsed or the XPath cannot be evaluated
     */
    private static String failure(final String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        return XmlSupport.string(XmlSupport.parse(body), "//*[local-name()='failure'][1]");
    }
}
