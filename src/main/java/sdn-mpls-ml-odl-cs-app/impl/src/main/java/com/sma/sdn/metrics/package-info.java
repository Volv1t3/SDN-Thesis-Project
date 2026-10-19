/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

/**
 * <p>Maintains controller counters, gauges and histograms and exposes their Prometheus text
 * representation through a servlet. Controller services update the shared
 * {@link com.sma.sdn.metrics.SdnMplsMlMetrics} instance supplied by the provider.</p>
 */
package com.sma.sdn.metrics;
