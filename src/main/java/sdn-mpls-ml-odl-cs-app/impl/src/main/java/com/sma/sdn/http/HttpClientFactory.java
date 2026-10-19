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
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * <p>Creates the shared JDK HTTP transport used by the controller's OpenDaylight and classifier clients. The factory
 * applies the application's connection timeout and permits normal HTTP redirects while leaving per-request timeouts,
 * authentication, headers, and payload handling to the specialized clients.</p>
 *
 * <p>{@link com.sma.sdn.impl.SDN_MPLS_ML_Provider} invokes this factory during initialization and supplies the returned
 * client to {@link OdlRestconfDataClient}, {@link OdlOperationsClient}, and {@link ClassifierRestClient}.</p>
 */
public final class HttpClientFactory {
    /**
     * <p>Records creation of the shared transport and its effective connection settings.</p>
     */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(HttpClientFactory.class);

    /**
     * <p>Prevents instantiation because this class exposes only the static transport factory.</p>
     *
     * <p>Construction performs these steps:</p>
     * <ol>
     *   <li>Receives no state because all behavior belongs to {@link #create(Duration)}.</li>
     *   <li>Completes privately so callers cannot create meaningless factory instances.</li>
     * </ol>
     */
    private HttpClientFactory() {
    }

    /**
     * <p>Builds a shared {@link HttpClient} whose connection establishment timeout equals the supplied duration and
     * whose redirect policy follows normal HTTP redirects.</p>
     *
     * <p>The factory performs these steps:</p>
     * <ol>
     *   <li>Creates a JDK HTTP client builder and applies {@code requestTimeout} as its connection timeout.</li>
     *   <li>Selects {@link HttpClient.Redirect#NORMAL}, allowing ordinary redirects except HTTPS-to-HTTP downgrades.</li>
     *   <li>Builds the immutable client and records the timeout and effective redirect policy.</li>
     *   <li>Returns the transport for reuse across controller services.</li>
     * </ol>
     *
     * <p>Individual requests still apply their own timeout through the specialized transport clients.</p>
     *
     * @param requestTimeout maximum duration allowed while establishing a connection
     * @return configured HTTP client shared by the controller's remote-service clients
     * @throws IllegalArgumentException if {@code requestTimeout} is zero or negative
     * @throws NullPointerException if {@code requestTimeout} is {@code null}
     */
    public static HttpClient create(final Duration requestTimeout) {

        /*Building the HTTP Client that most internal clients will use for communication, while the servlet with the csa RESTCONF
        * metrics will use the default OSGI provided httpService*/
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
