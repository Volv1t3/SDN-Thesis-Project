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

/** Serves the controller-side application metrics in Prometheus text format. */
public final class PrometheusMetricsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8";

    private final SdnMplsMlMetrics metrics;

    public PrometheusMetricsServlet(final SdnMplsMlMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        final byte[] body = metrics.renderPrometheusText().getBytes(StandardCharsets.UTF_8);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(CONTENT_TYPE);
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }
}
