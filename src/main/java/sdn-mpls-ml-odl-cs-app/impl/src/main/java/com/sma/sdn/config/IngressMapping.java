/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.config;

import java.util.Locale;

/**
 * Define el registro {@code IngressMapping} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public record IngressMapping(String switchNameToken, String connectorNameToken) {
    /**
     * Ejecuta la operacion {@code parse} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param value valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    public static IngressMapping parse(final String value) {
        final String[] fields = value.split("\\|");
        if (fields.length != 2) {
            throw new IllegalArgumentException(
                    "El mapeo de ingreso debe utilizar el formato switch|connector: " + value);
        }
        return new IngressMapping(fields[0].trim(), fields[1].trim());
    }

    /**
     * Ejecuta la operacion {@code matches} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param switchName valor requerido para ejecutar esta operacion
     *
     * @param connectorName valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public boolean matches(final String switchName, final String connectorName) {
        return containsIgnoreCase(switchName, switchNameToken) && containsIgnoreCase(connectorName, connectorNameToken);
    }

    /**
     * Ejecuta la operacion {@code containsIgnoreCase} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param value valor requerido para ejecutar esta operacion
     *
     * @param token valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private static boolean containsIgnoreCase(final String value, final String token) {
        if (token == null || token.isBlank()) {
            return true;
        }
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
    }
}
