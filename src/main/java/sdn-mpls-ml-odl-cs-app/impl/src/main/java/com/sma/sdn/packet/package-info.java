/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

/**
 * <p>Extracts classification features and ingress identities from OpenDaylight
 * PacketReceived notifications. The workflow consumes the resulting packet context
 * after the extractor resolves the ingress against the OpenFlow inventory.</p>
 */
package com.sma.sdn.packet;
