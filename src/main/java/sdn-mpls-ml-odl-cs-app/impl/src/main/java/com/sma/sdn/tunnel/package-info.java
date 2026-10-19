/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

/**
 * <p>Services for observing and updating delegated PCEP LSPs and applying directional
 * policy decisions to their paths and bandwidth.</p>
 *
 * <p>The pair-policy coordinator uses the directional application service, which relies
 * on the delegated-LSP service and path computation. A separate intent-based coordinator
 * integrates tunnel-operation reuse and optional OpenFlow suppression.</p>
 */
package com.sma.sdn.tunnel;
