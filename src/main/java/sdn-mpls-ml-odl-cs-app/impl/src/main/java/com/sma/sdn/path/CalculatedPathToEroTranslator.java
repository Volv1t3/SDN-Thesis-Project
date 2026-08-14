/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.path;

import com.sma.sdn.model.EroSubobject;
import com.sma.sdn.model.PathComputationResponse;
import com.sma.sdn.observability.StructuredLogger;
import java.util.ArrayList;
import java.util.List;

/**
 * Define la clase {@code CalculatedPathToEroTranslator} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class CalculatedPathToEroTranslator {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(CalculatedPathToEroTranslator.class);
    /**
     * Ejecuta la operacion {@code translate} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param response valor requerido para ejecutar esta operacion
     *
     * @param destinationRouterId valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public List<EroSubobject> translate(final PathComputationResponse response, final String destinationRouterId) {
        final List<EroSubobject> subobjects = new ArrayList<>();
        response.pathDescriptions().stream()
                .filter(hop -> hop.remoteIpv4() != null && !hop.remoteIpv4().isBlank())
                .map(hop -> strict(hop.remoteIpv4() + "/32"))
                .forEach(subobjects::add);
        subobjects.add(strict(destinationRouterId + "/32"));
        LOG.debug("calculated_path_translated_to_ero", "translate",
                "El camino calculado fue traducido a una ERO estricta",
                StructuredLogger.fields("path_description_count", response.pathDescriptions().size(),
                        "ero_subobject_count", subobjects.size(),
                        "destination_router_id", destinationRouterId));
        return List.copyOf(subobjects);
    }

    /**
     * Ejecuta la operacion {@code strict} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param ipPrefix valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private static EroSubobject strict(final String ipPrefix) {
        return new EroSubobject(false, ipPrefix);
    }
}
