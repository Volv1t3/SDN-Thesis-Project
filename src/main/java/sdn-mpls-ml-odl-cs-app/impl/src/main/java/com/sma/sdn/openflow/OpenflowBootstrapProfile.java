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
 * Define los datos estables usados para resolver un OVS dinamico y sus dos puertos de acceso requeridos.
 *
 * @param logicalName nombre logico del conmutador
 * @param managementIp direccion de gestion esperada
 * @param hostPortName nombre del puerto conectado al host
 * @param corePortName nombre del puerto conectado al enrutador central
 */
public record OpenflowBootstrapProfile(
        String logicalName, String managementIp, String hostPortName, String corePortName) {

    /**
     * Exige que todos los valores necesarios para resolver el conmutador hayan sido configurados.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida el nombre logico.</li>
     *   <li>Valida la direccion de gestion.</li>
     *   <li>Valida los nombres de los puertos de host y nucleo.</li>
     * </ol>
     *
     * @throws NullPointerException si falta un valor obligatorio
     */
    public OpenflowBootstrapProfile {
        Objects.requireNonNull(logicalName, "logicalName");
        Objects.requireNonNull(managementIp, "managementIp");
        Objects.requireNonNull(hostPortName, "hostPortName");
        Objects.requireNonNull(corePortName, "corePortName");
    }
}
