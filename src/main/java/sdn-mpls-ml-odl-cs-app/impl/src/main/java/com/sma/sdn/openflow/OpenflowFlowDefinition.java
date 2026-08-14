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
 * Describe una regla OpenFlow de bootstrap con coincidencia de ingreso y una salida opcional al controlador.
 *
 * @param flowId identificador persistente de la regla
 * @param tableId tabla OpenFlow
 * @param priority prioridad de coincidencia
 * @param cookie cookie determinista
 * @param ethernetType tipo Ethernet decimal
 * @param inputConnectorId identificador completo del puerto de ingreso
 * @param inputConnectorName nombre legible del puerto de ingreso
 * @param outputPortNumber numero del puerto de salida
 * @param outputConnectorName nombre legible del puerto de salida
 * @param copyToController indica si debe enviarse primero una copia completa al controlador
 */
public record OpenflowFlowDefinition(
        String flowId,
        int tableId,
        int priority,
        long cookie,
        int ethernetType,
        String inputConnectorId,
        String inputConnectorName,
        int outputPortNumber,
        String outputConnectorName,
        boolean copyToController) {

    /**
     * Valida los campos que se insertaran en la URI y en el documento XML del flujo.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Exige identificadores y nombres no nulos.</li>
     *   <li>Rechaza valores numericos negativos.</li>
     *   <li>Conserva la decision de copia al controlador.</li>
     * </ol>
     *
     * @throws NullPointerException si falta una cadena obligatoria
     * @throws IllegalArgumentException si un valor numerico es negativo
     */
    public OpenflowFlowDefinition {
        Objects.requireNonNull(flowId, "flowId");
        Objects.requireNonNull(inputConnectorId, "inputConnectorId");
        Objects.requireNonNull(inputConnectorName, "inputConnectorName");
        Objects.requireNonNull(outputConnectorName, "outputConnectorName");
        if (tableId < 0 || priority < 0 || cookie < 0 || ethernetType < 0 || outputPortNumber < 0) {
            throw new IllegalArgumentException("Los valores numericos del flujo no pueden ser negativos");
        }
    }
}
