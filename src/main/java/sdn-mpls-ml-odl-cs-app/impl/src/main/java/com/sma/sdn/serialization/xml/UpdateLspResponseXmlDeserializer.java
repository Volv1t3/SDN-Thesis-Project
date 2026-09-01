/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.serialization.xml;

import com.sma.sdn.model.UpdateLspResult;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.util.XmlSupport;
import java.net.http.HttpResponse;

/**
 * Define la clase {@code UpdateLspResponseXmlDeserializer} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class UpdateLspResponseXmlDeserializer {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(UpdateLspResponseXmlDeserializer.class);
    /**
     * Convierte una respuesta serializada en el modelo de dominio correspondiente.
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
    public UpdateLspResult deserialize(final HttpResponse<String> response) {
        final UpdateLspResult result;
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            result = new UpdateLspResult(false, false, true, "HTTP " + response.statusCode(), response.statusCode());
        } else {
            final String body = response.body();
            if (body == null || body.isBlank()) {
                result = new UpdateLspResult(true, false, false, null, response.statusCode());
            } else {
                final org.w3c.dom.Document document = XmlSupport.parse(body);
                final String failure = XmlSupport.string(document, "//*[local-name()='failure'][1]");
                final String error = errorDescription(document);
                if (error != null) {
                    result = new UpdateLspResult(false, false, true, "Error PCEP: " + error, response.statusCode());
                } else if (failure == null || failure.isBlank()) {
                    result = new UpdateLspResult(true, false, false, null, response.statusCode());
                } else if ("no-ack".equalsIgnoreCase(failure)) {
                    result = new UpdateLspResult(true, true, false, failure, response.statusCode());
                } else {
                    result = new UpdateLspResult(false, false, true, failure, response.statusCode());
                }
            }
        }
        LOG.debug("update_lsp_response_deserialized", "deserialize",
                "Se deserializo y clasifico la respuesta XML de update-lsp",
                StructuredLogger.fields("status_code", response.statusCode(), "success", result.success(),
                        "provisional_success", result.provisionalSuccess(), "hard_failure", result.hardFailure(),
                        "failure_present", result.failureReason() != null));
        return result;
    }

    /**
     * Extrae el detalle de error de la estructura {@code error/error-object} publicada por update-lsp.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Busca el objeto de error anidado definido por el esquema PCEP.</li>
     *   <li>Lee tipo, valor y el identificador de solicitud faltante cuando existen.</li>
     *   <li>Devuelve una descripcion compacta o {@code null} si ODL no reporto error.</li>
     * </ol>
     *
     * @param document documento XML de salida producido por update-lsp
     * @return descripcion estructurada del error o {@code null}
     */
    private static String errorDescription(final org.w3c.dom.Document document) {
        final String errorType = XmlSupport.string(
                document, "//*[local-name()='error-object']/*[local-name()='type'][1]");
        final String errorValue = XmlSupport.string(
                document, "//*[local-name()='error-object']/*[local-name()='value'][1]");
        final String missingRequest = XmlSupport.string(
                document,
                "//*[local-name()='error-object']//*[local-name()='req-missing']"
                        + "/*[local-name()='request-id'][1]");
        if (errorType == null && errorValue == null && missingRequest == null) {
            return XmlSupport.string(document, "//*[local-name()='error'][not(*)][1]");
        }
        return "type=" + valueOrUnknown(errorType)
                + ", value=" + valueOrUnknown(errorValue)
                + ", request_id=" + valueOrUnknown(missingRequest);
    }

    /**
     * Sustituye valores XML ausentes por una marca estable de diagnostico.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Comprueba si el valor fue publicado por OpenDaylight.</li>
     *   <li>Conserva el texto no vacio recibido.</li>
     *   <li>Devuelve {@code desconocido} cuando el campo esta ausente.</li>
     * </ol>
     *
     * @param value valor XML opcional
     * @return valor recibido o marca de ausencia
     */
    private static String valueOrUnknown(final String value) {
        return value == null ? "desconocido" : value;
    }
}
