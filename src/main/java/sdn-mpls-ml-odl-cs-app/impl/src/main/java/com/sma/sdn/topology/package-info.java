/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

/**
 * <p>Discovers and refreshes the controller's BGP-LS topology, tracks its freshness, and converts
 * bandwidth values for path-computation and PCEP operations. Discovery and refresh populate the
 * shared BGP-LS registry used by path calculation and operational state publication.</p>
 */
package com.sma.sdn.topology;
