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

import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.model.OdlXmlBodyLogLevel;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * <p>Produces structured transport evidence for XML exchanges with OpenDaylight RESTCONF and RESTS APIs. Request and
 * response metadata include method, endpoint, negotiation headers, body sizes, status, and duration.</p>
 *
 * <p>The configured {@link OdlXmlBodyLogLevel} controls whether complete XML bodies are emitted at debug or trace
 * level; an information-level summary is always emitted. Only the {@code Accept} and {@code Content-Type} headers are
 * read, so the Basic Authorization value is not exposed through this utility.</p>
 */
final class OdlXmlExchangeLogger {
    /**
     * <p>Prevents instantiation because exchange logging is provided exclusively through static helpers.</p>
     *
     * <p>Construction performs these steps:</p>
     * <ol>
     *   <li>Receives no state because callers supply their logger and exchange metadata to each method.</li>
     *   <li>Completes privately so no utility instance can be created.</li>
     * </ol>
     */
    private OdlXmlExchangeLogger() {
        // Clase utilitaria sin estado.
    }

    /**
     * <p>Records the start of an XML request to OpenDaylight, separating body-bearing diagnostic metadata from the
     * body-free operational summary.</p>
     *
     * <p>The logging operation performs these steps:</p>
     * <ol>
     *   <li>Builds detailed metadata from method, URI, negotiation headers, body presence, UTF-8 size, and body text.</li>
     *   <li>Builds a summary containing the same transport facts but excluding the body text.</li>
     *   <li>Delegates both maps to {@link #logDebugAndInfo(StructuredLogger, String, String, String, Map, Map,
     *       OdlXmlBodyLogLevel)} so body logging follows the configured level.</li>
     * </ol>
     *
     * <p>{@link OdlRestconfDataClient} and {@link OdlOperationsClient} invoke this immediately before synchronous send.</p>
     *
     * @param logger structured logger owned by the calling transport client
     * @param transport OpenDaylight API label, such as {@code RESTCONF} or {@code RESTS}
     * @param event stable event identifier for the request-start record
     * @param operation calling operation name
     * @param request prepared HTTP request
     * @param requestBody XML payload, or {@code null} for a request without a body
     * @param bodyLogLevel configured level at which complete XML bodies may be emitted
     */
    static void requestStarted(
            final StructuredLogger logger,
            final String transport,
            final String event,
            final String operation,
            final HttpRequest request,
            final String requestBody,
            final OdlXmlBodyLogLevel bodyLogLevel) {
        final Map<String, Object> debugMetadata = StructuredLogger.fields(
                "transport", transport,
                "method", request.method(),
                "endpoint", request.uri(),
                "accept", header(request, "Accept"),
                "content_type", header(request, "Content-Type"),
                "request_body_present", requestBody != null,
                "request_body_bytes", byteLength(requestBody),
                "request_body", requestBody);
        final Map<String, Object> infoMetadata = StructuredLogger.fields(
                "transport", transport,
                "method", request.method(),
                "endpoint", request.uri(),
                "accept", header(request, "Accept"),
                "content_type", header(request, "Content-Type"),
                "request_body_present", requestBody != null,
                "request_body_bytes", byteLength(requestBody));
        logDebugAndInfo(logger, event, operation, "Se inicio una solicitud XML contra OpenDaylight.",
                debugMetadata, infoMetadata, bodyLogLevel);
    }

    /**
     * <p>Records an XML response from OpenDaylight together with the originating request and measured duration.</p>
     *
     * <p>The logging operation performs these steps:</p>
     * <ol>
     *   <li>Reads the response body, status, response content type, and request negotiation headers.</li>
     *   <li>Builds detailed metadata containing the response body and its UTF-8 size.</li>
     *   <li>Builds body-free summary metadata containing status, response size, and duration.</li>
     *   <li>Emits both representations according to the configured body logging level.</li>
     * </ol>
     *
     * <p>Transport clients call this only after
     * {@link java.net.http.HttpClient#send(HttpRequest, HttpResponse.BodyHandler)} returns.</p>
     *
     * @param logger structured logger owned by the calling transport client
     * @param transport OpenDaylight API label, such as {@code RESTCONF} or {@code RESTS}
     * @param event stable event identifier for the response record
     * @param operation calling operation name
     * @param request HTTP request that produced the response
     * @param response received response with a string body
     * @param durationMillis total monotonic exchange duration in milliseconds
     * @param bodyLogLevel configured level at which complete XML bodies may be emitted
     */
    static void responseReceived(
            final StructuredLogger logger,
            final String transport,
            final String event,
            final String operation,
            final HttpRequest request,
            final HttpResponse<String> response,
            final long durationMillis,
            final OdlXmlBodyLogLevel bodyLogLevel) {
        final String responseBody = response.body();
        final Map<String, Object> debugMetadata = StructuredLogger.fields(
                "transport", transport,
                "method", request.method(),
                "endpoint", request.uri(),
                "request_accept", header(request, "Accept"),
                "request_content_type", header(request, "Content-Type"),
                "status_code", response.statusCode(),
                "response_content_type", response.headers().firstValue("Content-Type").orElse(null),
                "response_body_bytes", byteLength(responseBody),
                "response_body", responseBody,
                "duration_ms", durationMillis);
        final Map<String, Object> infoMetadata = StructuredLogger.fields(
                "transport", transport,
                "method", request.method(),
                "endpoint", request.uri(),
                "request_accept", header(request, "Accept"),
                "request_content_type", header(request, "Content-Type"),
                "status_code", response.statusCode(),
                "response_content_type", response.headers().firstValue("Content-Type").orElse(null),
                "response_body_bytes", byteLength(responseBody),
                "duration_ms", durationMillis);
        logDebugAndInfo(logger, event, operation, "Se recibio una respuesta XML de OpenDaylight.",
                debugMetadata, infoMetadata, bodyLogLevel);
    }

    /**
     * <p>Emits optional body-bearing metadata at the configured diagnostic level and always emits a body-free
     * information summary.</p>
     *
     * <p>The emission performs these steps:</p>
     * <ol>
     *   <li>Emits {@code debugMetadata} through {@link StructuredLogger#debug(String, String, String, Map)} when the
     *       configured level is {@link OdlXmlBodyLogLevel#DEBUG}.</li>
     *   <li>Emits the same detailed map at trace level when the configured level is
     *       {@link OdlXmlBodyLogLevel#TRACE}.</li>
     *   <li>Emits {@code infoMetadata} at information level regardless of the body logging setting.</li>
     * </ol>
     *
     * <p>This separation preserves routine transport observability without forcing XML bodies into information logs.</p>
     *
     * @param logger structured logger receiving the records
     * @param event stable event identifier
     * @param operation associated operation name
     * @param message human-readable event description
     * @param debugMetadata detailed exchange metadata including a body
     * @param infoMetadata summarized exchange metadata without a body
     * @param bodyLogLevel configured XML body logging level
     */
    private static void logDebugAndInfo(
            final StructuredLogger logger,
            final String event,
            final String operation,
            final String message,
            final Map<String, Object> debugMetadata,
            final Map<String, Object> infoMetadata,
            final OdlXmlBodyLogLevel bodyLogLevel) {
        /*This prints out the XML body if the debug metadata i.e the required debug level is defined for XML bodies. In this case
        * by default this is set to DEBUG such that the XML body will always be logged alongside the request information
        * (except credentials), for debugging and tracing purposes.*/
        if (bodyLogLevel == OdlXmlBodyLogLevel.DEBUG) {
            logger.debug(event, operation, message, debugMetadata);
        } else if (bodyLogLevel == OdlXmlBodyLogLevel.TRACE) {
            logger.trace(event, operation, message, debugMetadata);
        }
        /*The information metadata about the request and its duration is always outputted*/
        logger.info(event, operation, message, infoMetadata);
    }

    /**
     * <p>Reads one explicitly requested, non-sensitive HTTP request header for structured metadata.</p>
     *
     * <p>The lookup performs these steps:</p>
     * <ol>
     *   <li>Searches the request headers for the supplied name.</li>
     *   <li>Converts an absent optional value to {@code null}.</li>
     *   <li>Returns the value for inclusion in the caller's structured metadata.</li>
     * </ol>
     *
     * <p>Callers pass only negotiation header names and never request the Authorization header.</p>
     *
     * @param request request containing the headers
     * @param name header name to read
     * @return first header value, or {@code null} when absent
     * @throws NullPointerException if {@code request} or {@code name} is {@code null}
     */
    private static String header(final HttpRequest request, final String name) {
        return request.headers().firstValue(name).orElse(null);
    }

    /**
     * <p>Calculates the UTF-8 byte length of an optional textual body.</p>
     *
     * <p>The calculation performs these steps:</p>
     * <ol>
     *   <li>Returns zero when the body is {@code null}.</li>
     *   <li>Encodes a present body using UTF-8.</li>
     *   <li>Returns the number of encoded bytes.</li>
     * </ol>
     *
     * <p>Request and response log records use this value rather than the Java character count.</p>
     *
     * @param body optional textual body
     * @return UTF-8 byte length, or zero for {@code null}
     */
    private static int byteLength(final String body) {
        return body == null ? 0 : body.getBytes(StandardCharsets.UTF_8).length;
    }
}
