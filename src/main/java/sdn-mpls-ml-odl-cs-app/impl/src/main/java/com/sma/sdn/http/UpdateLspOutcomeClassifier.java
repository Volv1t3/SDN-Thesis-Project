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
import com.sma.sdn.util.XmlSupport;
import java.net.http.HttpResponse;

/**
 * Define la clase {@code UpdateLspOutcomeClassifier} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class UpdateLspOutcomeClassifier {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(UpdateLspOutcomeClassifier.class);
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
        return classify(response.statusCode(), response.body());
    }

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
     * @param statusCode valor requerido para ejecutar esta operacion
     *
     * @param body valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
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
     * Ejecuta la operacion {@code ambiguous} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param reason valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public OdlCallOutcome ambiguous(final String reason) {
        final OdlCallOutcome outcome = new OdlCallOutcome(OdlCallOutcomeType.AMBIGUOUS, 0, reason, false);
        LOG.debug("update_lsp_ambiguous_outcome_created", "ambiguous",
                "Se construyo un resultado ambiguo para update-lsp",
                StructuredLogger.fields("reason_present", reason != null && !reason.isBlank()));
        return outcome;
    }

    /**
     * Ejecuta la operacion {@code failure} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param body valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private static String failure(final String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        return XmlSupport.string(XmlSupport.parse(body), "//*[local-name()='failure'][1]");
    }
}
