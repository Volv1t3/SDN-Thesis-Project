/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.http;

import com.sma.sdn.observability.StructuredLogger;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Define la clase {@code HttpClientFactory} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class HttpClientFactory {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(HttpClientFactory.class);
    /**
     * Ejecuta la operacion {@code HttpClientFactory} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     */
    private HttpClientFactory() {
    }

    /**
     * Ejecuta la operacion {@code create} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param requestTimeout valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public static HttpClient create(final Duration requestTimeout) {
        final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        LOG.info("http_client_created", "create",
                "Se creo el cliente HTTP compartido de la aplicacion",
                StructuredLogger.fields("connect_timeout_ms", requestTimeout.toMillis(),
                        "redirect_policy", client.followRedirects()));
        return client;
    }
}
