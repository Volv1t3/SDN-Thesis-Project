/*
 * Copyright (c) 2026 Santiago Arellano
 * SDN-MPLS-ML Tech Demonstrator
 *
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

/**
 * Contains the CSA's OSGi Blueprint integration boundary.
 *
 * <p>Blueprint creates <code>SDN_MPLS_ML_Provider</code> after the MD-SAL and Karaf HTTP services
 * are available, injects those references, and calls <code>init()</code>. When the bundle stops,
 * Blueprint calls <code>close()</code> to remove the listener, stop deferred work, and release
 * published resources.</p>
 *
 * <p>The provider does not classify traffic or calculate paths directly. It assembles the REST
 * clients, in-memory registries, serializers, and domain services that receive
 * <code>PacketReceived</code>, form bidirectional evidence, and update delegated RSVP-TE LSPs
 * through the controller's PCEP operations.</p>
 */
package com.sma.sdn.impl;
