/*
 * Copyright (c) 2026 Santiago Arellano and others.
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
 * Define la clase {@code ClassifierRestClient} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class ClassifierRestClient {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(ClassifierRestClient.class);
    private final HttpClient httpClient;
    private final AppConfig config;
    private final URI endpoint;

    /**
     * Ejecuta la operacion {@code ClassifierRestClient} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param httpClient valor requerido para ejecutar esta operacion
     *
     * @param config valor requerido para ejecutar esta operacion
     */
    public ClassifierRestClient(final HttpClient httpClient, final AppConfig config) {
        this.httpClient = httpClient;
        this.config = config;
        this.endpoint = config.classifierEndpoint();
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
     * @param jsonBody valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    public HttpResponse<String> classify(final String jsonBody) {
        final HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(config.httpRequestTimeout())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        final long startedAt = System.nanoTime();
        LOG.debug("classifier_http_request_started", "classify",
                "Se inicio la solicitud HTTP al clasificador",
                StructuredLogger.fields("method", "POST", "endpoint", endpoint,
                        "request_bytes", jsonBody.getBytes(StandardCharsets.UTF_8).length));
        try {
            final HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            LOG.debug("classifier_http_request_completed", "classify",
                    "La solicitud HTTP al clasificador finalizo",
                    StructuredLogger.fields("method", "POST", "endpoint", endpoint,
                            "status_code", response.statusCode(),
                            "response_bytes", response.body().getBytes(StandardCharsets.UTF_8).length,
                            "duration_ms", elapsedMillis(startedAt)));
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
     * Calcula los milisegundos monotonicamente transcurridos desde el instante de inicio indicado.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Lee el reloj monotono actual de la JVM.</li>
     *   <li>Resta el valor capturado al iniciar la operacion.</li>
     *   <li>Convierte la diferencia de nanosegundos a milisegundos.</li>
     * </ol>
     *
     * @param startedAt lectura de {@link System#nanoTime()} tomada al iniciar la operacion
     * @return duracion transcurrida en milisegundos
     */
    private static long elapsedMillis(final long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
