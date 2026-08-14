/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package com.sma.sdn.openflow;

import java.util.Objects;

/**
 * Representa un conector OpenFlow descubierto junto con su identidad, numero de puerto y estado operativo.
 *
 * @param connectorId identificador completo publicado por el inventario
 * @param name nombre de interfaz asignado por OVS
 * @param portNumber numero de puerto OpenFlow
 * @param hardwareAddress direccion fisica publicada, o cadena vacia si no existe
 * @param live indica que el conector esta operativo
 * @param linkDown indica que el enlace fue reportado como caido
 */
public record OpenflowConnectorRecord(
        String connectorId,
        String name,
        int portNumber,
        String hardwareAddress,
        boolean live,
        boolean linkDown) {

    /**
     * Valida la identidad y el numero de puerto del conector antes de incorporarlo al registro.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Exige identificador, nombre y direccion fisica no nulos.</li>
     *   <li>Rechaza numeros de puerto negativos.</li>
     *   <li>Conserva el estado operativo recibido desde ODL.</li>
     * </ol>
     *
     * @throws NullPointerException si una cadena obligatoria es nula
     * @throws IllegalArgumentException si el numero de puerto es negativo
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
