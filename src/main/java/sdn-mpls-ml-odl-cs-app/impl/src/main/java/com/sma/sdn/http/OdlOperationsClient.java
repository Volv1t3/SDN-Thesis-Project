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
import java.util.Base64;

/**
 * Define la clase {@code OdlOperationsClient} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class OdlOperationsClient {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(OdlOperationsClient.class);
    private final HttpClient httpClient;
    private final URI baseUrl;
    private final AppConfig config;
    private final String authorization;

    /**
     * Ejecuta la operacion {@code OdlOperationsClient} dentro del componente correspondiente.
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
    public OdlOperationsClient(final HttpClient httpClient, final AppConfig config) {
        this.httpClient = httpClient;
        this.baseUrl = stripTrailingSlash(config.odlRestsOperationsBaseUrl());
        this.config = config;
        this.authorization = basic(config.odlUsername(), config.odlPassword());
    }

    /**
     * Ejecuta la operacion {@code computeConstrainedPath} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param xmlBody valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    public HttpResponse<String> computeConstrainedPath(final String xmlBody) {
        return post("/path-computation:get-constrained-path", xmlBody);
    }

    /**
     * Ejecuta la operacion {@code updateLsp} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param xmlBody valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    public HttpResponse<String> updateLsp(final String xmlBody) {
        return post("/network-topology-pcep:update-lsp", xmlBody);
    }

    /**
     * Ejecuta la operacion {@code post} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param path valor requerido para ejecutar esta operacion
     *
     * @param xmlBody valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private HttpResponse<String> post(final String path, final String xmlBody) {
        final URI endpoint = URI.create(baseUrl.toString() + path);
        final HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(config.httpRequestTimeout())
                .header("Accept", "application/xml")
                .header("Content-Type", "application/xml")
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofString(xmlBody, StandardCharsets.UTF_8))
                .build();
        final long startedAt = System.nanoTime();
        LOG.debug("odl_operation_request_started", "post",
                "Se inicio una operacion RESTS contra ODL",
                StructuredLogger.fields("method", "POST", "endpoint", endpoint,
                        "request_bytes", xmlBody.getBytes(StandardCharsets.UTF_8).length));
        try {
            final HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            LOG.debug("odl_operation_request_completed", "post",
                    "La operacion RESTS contra ODL finalizo",
                    StructuredLogger.fields("method", "POST", "endpoint", endpoint,
                            "status_code", response.statusCode(),
                            "response_bytes", response.body().getBytes(StandardCharsets.UTF_8).length,
                            "duration_ms", elapsedMillis(startedAt)));
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

    /**
     * Ejecuta la operacion {@code stripTrailingSlash} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param uri valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private static URI stripTrailingSlash(final URI uri) {
        final String value = uri.toString();
        return URI.create(value.endsWith("/") ? value.substring(0, value.length() - 1) : value);
    }

    /**
     * Ejecuta la operacion {@code basic} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param username valor requerido para ejecutar esta operacion
     *
     * @param password valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private static String basic(final String username, final String password) {
        final String token = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }
}
