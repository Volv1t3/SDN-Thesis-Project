/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

/**
 * <p>Converts controller models to and from the XML exchanged with OpenDaylight.
 * The package covers topology discovery, PCEP reported LSPs and updates, path computation,
 * explicit routes, and OpenFlow suppression rules.</p>
 *
 * <p>Topology, path, tunnel, and OpenFlow services coordinate the associated HTTP
 * operations; these classes construct or interpret the corresponding payloads.</p>
 */
package com.sma.sdn.serialization.xml;
