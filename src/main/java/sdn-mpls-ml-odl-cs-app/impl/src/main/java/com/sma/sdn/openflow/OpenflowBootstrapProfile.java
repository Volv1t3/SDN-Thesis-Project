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
package com.sma.sdn.openflow;

import java.util.Objects;

/**
 * <p>Identifies an OVS switch by management address and names its host-facing and
 * core-facing ports. Inventory discovery uses this stable profile to resolve the
 * dynamic OpenFlow node and connectors needed for bootstrap.</p>
 *
 * @param logicalName application-level switch name
 * @param managementIp expected management address
 * @param hostPortName host-facing OVS port name
 * @param corePortName core-facing OVS port name
 */
public record OpenflowBootstrapProfile(
        String logicalName, /*logical name of the corresponding switch*/
        String managementIp,  /*management IP address of the corresponding switch for identification*/
        String hostPortName, /*switch to host port name for identification*/
        String corePortName /*switch to core name for identification*/) {

    /**
     * <p>Requires the four profile components to be non-null; blank strings are not rejected.</p>
     *
     * <p>The constructor performs these checks:</p>
     * <ol>
     *   <li>Check the logical name and management address.</li>
     *   <li>Check the host-facing and core-facing port names.</li>
     * </ol>
     *
     * @param logicalName application-level switch name
     * @param managementIp expected management address
     * @param hostPortName host-facing OVS port name
     * @param corePortName core-facing OVS port name
     * @throws NullPointerException if any component is null
     */
    public OpenflowBootstrapProfile {
        Objects.requireNonNull(logicalName, "logicalName");
        Objects.requireNonNull(managementIp, "managementIp");
        Objects.requireNonNull(hostPortName, "hostPortName");
        Objects.requireNonNull(corePortName, "corePortName");
    }
}
