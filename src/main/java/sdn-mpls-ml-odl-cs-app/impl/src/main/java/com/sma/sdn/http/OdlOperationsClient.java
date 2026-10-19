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

import com.sma.sdn.config.AppConfig;
import com.sma.sdn.observability.StructuredLogger;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * <p>Executes blocking XML RPC requests against OpenDaylight's RESTS operations endpoint. It supports constrained-path
 * computation and PCEP update-LSP operations, applying Basic authentication and the configured request timeout while
 * returning each raw HTTP response for domain-specific classification and deserialization.</p>
 *
 * <p>{@link com.sma.sdn.path.PathComputationService} and
 * {@link com.sma.sdn.tunnel.DelegatedLspService} serialize their requests before calling this client. Transport
 * failures become {@link IllegalStateException}; HTTP error statuses remain ordinary responses so those services can
 * apply operation-specific policy.</p>
 */
public final class OdlOperationsClient {
    /**
     * <p>Records RESTS request summaries, optional XML bodies, responses, durations, and transport failures.</p>
     */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(OdlOperationsClient.class);

    /**
     * <p>Sends blocking requests using the HTTP transport shared by the provider.</p>
     */
    private final HttpClient httpClient;

    /**
     * <p>Holds the normalized RESTS operations base URI without a trailing slash.</p>
     */
    private final URI baseUrl;

    /**
     * <p>Supplies request timeouts and XML body logging policy for each exchange.</p>
     */
    private final AppConfig config;

    /**
     * <p>Stores the precomputed HTTP Basic Authorization header for OpenDaylight requests.</p>
     */
    private final String authorization;

    /**
     * <p>Creates a RESTS operations client from a shared transport and application configuration. Construction performs
     * no network exchange.</p>
     *
     * <p>The constructor performs these steps:</p>
     * <ol>
     *   <li>Retains the shared {@link HttpClient}.</li>
     *   <li>Reads and normalizes the configured RESTS operations base URI.</li>
     *   <li>Retains the configuration for request timeout and XML logging policy.</li>
     *   <li>Encodes the configured OpenDaylight credentials into a reusable Basic authorization value.</li>
     * </ol>
     *
     * <p>The provider shares this transport with the RESTCONF data and classifier clients.</p>
     *
     * @param httpClient HTTP transport used for blocking RESTS requests
     * @param config application configuration containing endpoint, timeout, logging, and credentials
     * @throws NullPointerException if {@code config} or a required credential/configuration value is {@code null}
     */
    public OdlOperationsClient(final HttpClient httpClient, final AppConfig config) {
        this.httpClient = httpClient;
        this.baseUrl = stripTrailingSlash(config.odlRestsOperationsBaseUrl());
        this.config = config;
        this.authorization = basic(config.odlUsername(), config.odlPassword());
    }

    /**
     * <p>Invokes the OpenDaylight constrained-path operation with the supplied XML payload.</p>
     *
     * <p>The operation performs these steps:</p>
     * <ol>
     *   <li>Selects the {@code /path-computation:get-constrained-path} RPC path.</li>
     *   <li>Delegates request construction, authenticated transport, and exchange logging to {@link #post(String, String)}.</li>
     *   <li>Returns the raw HTTP response without classifying its status or parsing its XML body.</li>
     * </ol>
     *
     * <p>{@link com.sma.sdn.path.PathComputationService} classifies the response with
     * {@link PathComputationOutcomeClassifier} and deserializes confirmed results.</p>
     *
     * @param xmlBody serialized constrained-path request document
     * @return raw UTF-8 HTTP response from the OpenDaylight operation
     * @throws IllegalStateException if the request cannot be sent or the thread is interrupted
     * @throws NullPointerException if {@code xmlBody}, the HTTP client, or a required request setting is {@code null}
     * @throws IllegalArgumentException if the configured timeout or resulting endpoint is invalid
     */
    public HttpResponse<String> computeConstrainedPath(final String xmlBody) {
        return post("/path-computation:get-constrained-path", xmlBody);
    }

    /**
     * <p>Invokes OpenDaylight's PCEP update-LSP operation with the supplied XML payload.</p>
     *
     * <p>The operation performs these steps:</p>
     * <ol>
     *   <li>Selects the {@code /network-topology-pcep:update-lsp} RPC path.</li>
     *   <li>Delegates authenticated XML request construction and transport to {@link #post(String, String)}.</li>
     *   <li>Returns the raw response so tunnel orchestration can interpret provisional and failure semantics.</li>
     * </ol>
     *
     * <p>{@link com.sma.sdn.tunnel.DelegatedLspService} consumes this response and subsequently verifies operational
     * state when the immediate result is not a hard failure.</p>
     *
     * @param xmlBody serialized update-LSP request document
     * @return raw UTF-8 HTTP response from the OpenDaylight operation
     * @throws IllegalStateException if the request cannot be sent or the thread is interrupted
     * @throws NullPointerException if {@code xmlBody}, the HTTP client, or a required request setting is {@code null}
     * @throws IllegalArgumentException if the configured timeout or resulting endpoint is invalid
     */
    public HttpResponse<String> updateLsp(final String xmlBody) {
        return post("/network-topology-pcep:update-lsp", xmlBody);
    }

    /**
     * <p>Builds and synchronously sends one authenticated XML {@code POST} to a RESTS operation path. HTTP status codes
     * are not rejected here; only construction and transport failures are raised.</p>
     *
     * <p>The exchange performs these steps:</p>
     * <ol>
     *   <li>Appends {@code path} to the normalized operations base URI.</li>
     *   <li>Builds a timed POST request accepting and sending XML with the stored Basic credentials.</li>
     *   <li>Records the request through {@link OdlXmlExchangeLogger} under the RESTS transport label.</li>
     *   <li>Sends the request synchronously and decodes its body as UTF-8.</li>
     *   <li>Records the response and monotonic duration before returning it.</li>
     *   <li>Converts I/O failure to {@link IllegalStateException}; on interruption it first restores the interrupt flag.</li>
     * </ol>
     *
     * @param path RESTS operation path beginning with a slash
     * @param xmlBody XML request document to send as UTF-8
     * @return raw HTTP response including error statuses
     * @throws IllegalStateException if transport fails or the sending thread is interrupted
     * @throws NullPointerException if {@code xmlBody}, the transport, or a required request setting is {@code null}
     * @throws IllegalArgumentException if the endpoint or timeout is invalid
     */
    private HttpResponse<String> post(final String path, final String xmlBody) {

        /*This modifies the path that was sent to add the base url for the rests operation endpoint on port 8181*/
        final URI endpoint = URI.create(baseUrl.toString() + path);
        /*This, as was done in the ClassifierRestClient constructs the corresponding HTTPRequest which is what the
        * other services care about*/
        final HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(config.httpRequestTimeout())
                .header("Accept", "application/xml")
                .header("Content-Type", "application/xml")
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofString(xmlBody, StandardCharsets.UTF_8))
                .build();
        final long startedAt = System.nanoTime();
        OdlXmlExchangeLogger.requestStarted(
                LOG, "RESTS", "odl_operation_request_started", "post", request, xmlBody,
                config.odlXmlBodyLogLevel());
        try {
            /*Executes the actual operation against the ODL controller and waits for a response*/
            final HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            OdlXmlExchangeLogger.responseReceived(
                    LOG, "RESTS", "odl_operation_response_received", "post", request, response,
                    elapsedMillis(startedAt), config.odlXmlBodyLogLevel());
            /*If no error was reported then the response is returned*/
            return response;
        } catch (IOException e) {
            LOG.error("odl_operation_request_failed", "post",
                    "La operacion RESTS contra ODL fallo",
                    StructuredLogger.fields("method", "POST", "endpoint", endpoint,
                            "duration_ms", elapsedMillis(startedAt)), e);
            throw new IllegalStateException("La solicitud de operaciones RESTS de ODL fallo", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("odl_operation_request_interrupted", "post",
                    "La operacion RESTS contra ODL fue interrumpida",
                    StructuredLogger.fields("method", "POST", "endpoint", endpoint,
                            "duration_ms", elapsedMillis(startedAt)), e);
            throw new IllegalStateException("La solicitud de operaciones RESTS de ODL fue interrumpida", e);
        }
    }

    /**
     * <p>Calculates elapsed whole milliseconds from a monotonic start value.</p>
     *
     * <p>The calculation performs these steps:</p>
     * <ol>
     *   <li>Reads the current {@link System#nanoTime()} value.</li>
     *   <li>Subtracts the supplied start value.</li>
     *   <li>Converts the nanosecond difference to whole milliseconds.</li>
     * </ol>
     *
     * <p>RESTS success and failure logs use this value and are unaffected by wall-clock adjustments.</p>
     *
     * @param startedAt monotonic value captured immediately before transport
     * @return elapsed whole milliseconds
     */
    private static long elapsedMillis(final long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    /**
     * <p>Normalizes a base URI by removing one trailing slash when present.</p>
     *
     * <p>The normalization performs these steps:</p>
     * <ol>
     *   <li>Converts the URI to its textual form.</li>
     *   <li>Removes the final character only when that character is a slash.</li>
     *   <li>Creates and returns a URI from the normalized text.</li>
     * </ol>
     *
     * <p>This ensures operation paths can be appended without introducing a double slash.</p>
     *
     * @param uri RESTS base URI to normalize
     * @return equivalent URI without one trailing slash
     * @throws NullPointerException if {@code uri} is {@code null}
     * @throws IllegalArgumentException if the normalized value is not a valid URI
     */
    private static URI stripTrailingSlash(final URI uri) {
        final String value = uri.toString();
        return URI.create(value.endsWith("/") ? value.substring(0, value.length() - 1) : value);
    }

    /**
     * <p>Builds an HTTP Basic authorization value from configured OpenDaylight credentials.</p>
     *
     * <p>The encoding performs these steps:</p>
     * <ol>
     *   <li>Concatenates the username, a colon, and the password.</li>
     *   <li>Encodes that credential token as UTF-8 bytes and then Base64.</li>
     *   <li>Prefixes the encoded token with the {@code Basic} authentication scheme.</li>
     * </ol>
     *
     * <p>The resulting value is attached to requests but is never included in XML exchange metadata.</p>
     *
     * @param username OpenDaylight username
     * @param password OpenDaylight password
     * @return complete HTTP Basic Authorization header value
     */
    private static String basic(final String username, final String password) {
        final String token = username + ":" + password;
        return "Basic " + Base64
                .getEncoder()
                .encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }
}
