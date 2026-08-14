/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.path;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sma.sdn.model.EroSubobject;
import com.sma.sdn.model.PathComputationResponse;
import com.sma.sdn.model.PathHop;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Define la clase {@code CalculatedPathToEroTranslatorTest} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
class CalculatedPathToEroTranslatorTest {
    /**
     * Ejecuta la operacion {@code translatesRemoteHopsAndAppendsDestinationLoopback} dentro del
     * componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     */
    @Test
    void translatesRemoteHopsAndAppendsDestinationLoopback() {
        final PathComputationResponse response = new PathComputationResponse(
                "completed",
                List.of(new PathHop("10.0.12.1", "10.0.12.2"), new PathHop("10.0.22.1", "10.0.22.2")),
                2);

        final List<EroSubobject> ero = new CalculatedPathToEroTranslator().translate(response, "14.14.14.14");

        assertEquals(List.of(
                new EroSubobject(false, "10.0.12.2/32"),
                new EroSubobject(false, "10.0.22.2/32"),
                new EroSubobject(false, "14.14.14.14/32")), ero);
    }
}
