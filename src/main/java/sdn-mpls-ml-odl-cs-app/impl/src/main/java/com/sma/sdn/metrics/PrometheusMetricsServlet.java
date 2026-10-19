/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.metrics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * <p>Serves the shared controller metrics registry in Prometheus text format. The controller
 * provider registers this servlet at {@code /csa/metrics} when its HTTP service is available.</p>
 */
public final class PrometheusMetricsServlet extends HttpServlet {
    /** <p>Serialization identifier required by the servlet superclass.</p> */
    private static final long serialVersionUID = 1L;
    /** <p>Prometheus text exposition media type and UTF-8 charset.</p> */
    private static final String CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8";

    /** <p>Shared registry rendered for each HTTP scrape.</p> */
    private final SdnMplsMlMetrics metrics;

    /**
     * <p>Creates a servlet backed by the provided live metrics registry.</p>
     * <p>Construction proceeds as follows:</p>
     * <ol>
     *   <li>Rejects a null registry.</li>
     *   <li>Stores the registry for subsequent GET requests.</li>
     * </ol>
     * @param metrics registry containing controller measurements
     * @throws NullPointerException if metrics is null
     */
    public PrometheusMetricsServlet(final SdnMplsMlMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /**
     * <p>Writes a fresh UTF-8 Prometheus text snapshot to an HTTP response.</p>
     * <p>The request is handled as follows:</p>
     * <ol>
     *   <li>Renders the registry and encodes the resulting text as UTF-8.</li>
     *   <li>Sets status 200, the exposition content type and the byte length.</li>
     *   <li>Writes the encoded body to the servlet output stream.</li>
     * </ol>
     * @param request incoming GET request; its contents are not inspected
     * @param response destination for the exposition response
     * @throws IOException if obtaining or writing the response stream fails
     */
    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        final byte[] body = metrics.renderPrometheusText().getBytes(StandardCharsets.UTF_8);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(CONTENT_TYPE);
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }
}
