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
 * <p>Describes a discovered OpenFlow connector, including its identifier, OVS name,
 * port number, hardware address and operational flags. Switch inventory records index
 * these values for bootstrap and packet ingress lookup.</p>
 *
 * @param connectorId full identifier published in the inventory
 * @param name OVS interface name
 * @param portNumber nonnegative OpenFlow port number
 * @param hardwareAddress published hardware address, or an empty string when absent
 * @param live reported live state
 * @param linkDown reported link-down state
 */
public record OpenflowConnectorRecord(
        String connectorId, /*openflow:... connector identifier */
        String name, /*logical name that can be associated to a switch, environment variables or the node register*/
        int portNumber, /*port integer identifier*/
        String hardwareAddress, /*MAC address associated with the port*/
        boolean live, /*if the connector is live (online)*/
        boolean linkDown /* if the connector is down (offline)*/) {

    /**
     * <p>Checks connector identity fields and rejects negative port numbers.</p>
     *
     * <p>The constructor validates the record as follows:</p>
     * <ol>
     *   <li>Require non-null identifier, name and hardware address.</li>
     *   <li>Reject negative port numbers; retain the reported operational flags.</li>
     * </ol>
     *
     * @param connectorId full inventory identifier
     * @param name OVS interface name
     * @param portNumber OpenFlow port number
     * @param hardwareAddress connector hardware address
     * @param live reported live state
     * @param linkDown reported link-down state
     * @throws NullPointerException if an identity string is null
     * @throws IllegalArgumentException if the port number is negative
     */
    public OpenflowConnectorRecord {
        Objects.requireNonNull(connectorId, "connectorId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(hardwareAddress, "hardwareAddress");
        if (portNumber < 0) {
            throw new IllegalArgumentException("El numero de puerto OpenFlow no puede ser negativo");
        }
    }
}
