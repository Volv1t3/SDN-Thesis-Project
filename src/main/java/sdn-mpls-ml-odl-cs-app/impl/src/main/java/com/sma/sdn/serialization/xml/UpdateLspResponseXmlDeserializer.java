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

package com.sma.sdn.serialization.xml;

import com.sma.sdn.model.UpdateLspResult;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.util.XmlSupport;
import java.net.http.HttpResponse;

/**
 * <p>Classifies the immediate HTTP and XML response to update-lsp. DelegatedLspService consumes the result before polling PCEP for confirmation of the requested route.</p>
 */
public final class UpdateLspResponseXmlDeserializer {
    /** <p>Logger for classified update-lsp outcomes.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(UpdateLspResponseXmlDeserializer.class);

    /**
     * <p>Classify an update-lsp HTTP response as success, provisional success, or hard failure.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Treat non-2xx HTTP status as hard failure and an empty successful body as success.</li>
     *   <li>Parse a nonempty body and give PCEP error details precedence over failure text.</li>
     *   <li>Classify no-ack as provisional success, other failure text as hard failure, and absent failure as success; log and return the result.</li>
     * </ol>
     *
     * @param response HTTP response to update-lsp
     * @return classified update result
     * @throws RuntimeException if XML parsing or XPath evaluation fails
     */
    public UpdateLspResult deserialize(final HttpResponse<String> response) {
        final UpdateLspResult result;
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            result = new UpdateLspResult(false, false, true,
                    "HTTP " + response.statusCode(), response.statusCode());
        } else {
            final String body = response.body();
            if (body == null || body.isBlank()) {
                result = new UpdateLspResult(true,
                        false, false, null, response.statusCode());
            } else {
                final org.w3c.dom.Document document = XmlSupport.parse(body);
                final String failure = XmlSupport.string(document, "//*[local-name()='failure'][1]");
                final String error = errorDescription(document);
                if (error != null) {
                    result = new UpdateLspResult(false, false, true,
                            "Error PCEP: " + error, response.statusCode());
                } else if (failure == null || failure.isBlank()) {
                    result = new UpdateLspResult(true, false, false,
                            null, response.statusCode());
                } else if ("no-ack".equalsIgnoreCase(failure)) {
                    result = new UpdateLspResult(true, true, false,
                            failure, response.statusCode());
                } else {
                    result = new UpdateLspResult(false, false, true,
                            failure, response.statusCode());
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
     * <p>Extract an error description from a parsed update-lsp response.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Read the error-object type, value, and missing request ID.</li>
     *   <li>If none is present return a plain leaf error; otherwise format the fields with a missing-value marker.</li>
     * </ol>
     *
     * @param document parsed response document
     * @return error description or null
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
     * <p>Substitute the diagnostic marker for an absent error field.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Return the supplied value, or {@code desconocido} when it is null.</li>
     * </ol>
     *
     * @param value optional XML value
     * @return original value or missing-value marker
     */
    private static String valueOrUnknown(final String value) {
        return value == null ? "desconocido" : value;
    }
}
