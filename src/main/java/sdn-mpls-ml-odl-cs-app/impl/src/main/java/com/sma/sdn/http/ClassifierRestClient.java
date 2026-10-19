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

/**
 * <p>Executes the blocking HTTP exchange with the external classification API. Each call sends the caller-provided JSON
 * as an HTTP/1.1 {@code POST}, requests and declares JSON content, applies the configured request timeout, and returns
 * the response without interpreting its status code or body.</p>
 *
 * <p>{@link com.sma.sdn.classification.ClassificationService} owns status validation and response conversion. This
 * client owns request construction and transport failures, converting I/O and interruption into
 * {@link IllegalStateException}. An interrupted call also restores the thread's interrupted status.</p>
 */
public final class ClassifierRestClient {
    /**
     * <p>Logs classifier request bodies, responses, durations, transport failures, and interruptions.</p>
     */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(ClassifierRestClient.class);

    /**
     * <p>Sends the blocking classifier request using the transport shared by the provider.</p>
     */
    private final HttpClient httpClient;

    /**
     * <p>Supplies the timeout applied to each classifier request.</p>
     */
    private final AppConfig config;

    /**
     * <p>Holds the classifier URI captured from the configuration at construction time.</p>
     */
    private final URI endpoint;

    /**
     * <p>Creates a classifier client around an existing HTTP transport and a validated application configuration.
     * Construction performs no network operation.</p>
     *
     * <p>The constructor performs these steps:</p>
     * <ol>
     *   <li>Retains the supplied {@link HttpClient} for synchronous sends.</li>
     *   <li>Retains the {@link AppConfig} used to obtain the request timeout.</li>
     *   <li>Captures {@link AppConfig#classifierEndpoint()} as the destination for all subsequent requests.</li>
     * </ol>
     *
     * <p>The production provider supplies one shared HTTP client to this and the ODL transport clients.</p>
     *
     * @param httpClient HTTP transport used for blocking classifier exchanges
     * @param config application configuration containing the classifier endpoint and request timeout
     * @throws NullPointerException if {@code config} is {@code null}
     */
    public ClassifierRestClient(final HttpClient httpClient, final AppConfig config) {
        /*It is important to note that this HTTP client is coming directly from the OSGI framework as it provides it
        * during initializatino as an injected dependency*/
        this.httpClient = httpClient;
        /*This configuration contains the request parameters and URI*/
        this.config = config;
        /*This is the endpoint that the classifier lives in*/
        this.endpoint = config.classifierEndpoint();
    }

    /**
     * <p>Sends one JSON classification request and returns the complete string response. HTTP error statuses are returned
     * normally so the classification service can apply its own status and metrics policy.</p>
     *
     * <p>The exchange performs these steps:</p>
     * <ol>
     *   <li>Builds an HTTP/1.1 request to the configured endpoint with the configured timeout, JSON {@code Accept} and
     *       {@code Content-Type} headers, and a UTF-8 {@code POST} body.</li>
     *   <li>Captures a monotonic start value and logs the endpoint and encoded request size.</li>
     *   <li>Calls {@link HttpClient#send(HttpRequest, HttpResponse.BodyHandler)} with a UTF-8 string body handler.</li>
     *   <li>On success, logs the status, response body and size, and elapsed duration before returning the response.</li>
     *   <li>On {@link IOException}, logs the transport failure and throws {@link IllegalStateException} with the cause.</li>
     *   <li>On {@link InterruptedException}, restores the interrupt flag, logs the interruption, and throws
     *       {@link IllegalStateException} with the cause.</li>
     * </ol>
     *
     * <p>{@link com.sma.sdn.classification.ClassificationService} subsequently rejects non-2xx statuses and passes a
     * successful body to
     * {@link com.sma.sdn.serialization.json.ClassificationResponseJsonDeserializer#deserialize(String)}.</p>
     *
     * @param jsonBody UTF-8 JSON document to send as the request body
     * @return HTTP response containing the status code, headers, and UTF-8 string body
     * @throws IllegalStateException if the HTTP transport fails or the sending thread is interrupted
     * @throws NullPointerException if {@code jsonBody}, the HTTP client, or a required request setting is {@code null}
     * @throws IllegalArgumentException if the configured timeout is invalid for an HTTP request
     */
    public HttpResponse<String> classify(final String jsonBody) {

        /*This construct the required HTTP 1.11 request given that the ASGI framework for the API is expecting HTTP 1.1*/
        final HttpRequest request = HttpRequest.newBuilder(endpoint)
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(config.httpRequestTimeout())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        final long startedAt = System.nanoTime();
        LOG.debug("classifier_http_request_started", "classify",
                "Se inicio la solicitud HTTP al clasificador",
                StructuredLogger.fields("method", "POST", "endpoint", endpoint,
                        "request_bytes", jsonBody.getBytes(StandardCharsets.UTF_8).length,
                        "request_body", jsonBody));
        try {
            /*Attempts to perform the sequential HTTP request through the managed OSGi dependency*/
            final HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            LOG.debug("classifier_http_request_completed", "classify",
                    "La solicitud HTTP al clasificador finalizo",
                    StructuredLogger.fields("method", "POST", "endpoint", endpoint,
                            "status_code", response.statusCode(),
                            "response_bytes", response.body().getBytes(StandardCharsets.UTF_8).length,
                            "response_body", response.body(),
                            "duration_ms", elapsedMillis(startedAt)));
            /*If no exception was raised we return the result here!!*/
            return response;
        } catch (IOException e) {
            LOG.error("classifier_http_request_failed", "classify",
                    "La solicitud HTTP al clasificador fallo",
                    StructuredLogger.fields("method", "POST", "endpoint", endpoint,
                            "duration_ms", elapsedMillis(startedAt)), e);
            throw new IllegalStateException("La solicitud al clasificador fallo", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("classifier_http_request_interrupted", "classify",
                    "La solicitud HTTP al clasificador fue interrumpida",
                    StructuredLogger.fields("method", "POST", "endpoint", endpoint,
                            "duration_ms", elapsedMillis(startedAt)), e);
            throw new IllegalStateException("La solicitud al clasificador fue interrumpida", e);
        }
    }

    /**
     * <p>Calculates elapsed milliseconds from a monotonic start value.</p>
     *
     * <p>The calculation performs these steps:</p>
     * <ol>
     *   <li>Reads the current {@link System#nanoTime()} value.</li>
     *   <li>Subtracts the supplied start value.</li>
     *   <li>Converts the nanosecond difference to whole milliseconds.</li>
     * </ol>
     *
     * <p>Request completion and failure logs use the returned duration; wall-clock adjustments do not affect it.</p>
     *
     * @param startedAt monotonic value captured immediately before the HTTP exchange
     * @return elapsed whole milliseconds
     */
    private static long elapsedMillis(final long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
