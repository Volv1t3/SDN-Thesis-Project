/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sma.sdn.model.OdlCallOutcome;
import com.sma.sdn.model.OdlCallOutcomeType;
import org.junit.jupiter.api.Test;

/**
 * Define la clase {@code UpdateLspOutcomeClassifierTest} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
class UpdateLspOutcomeClassifierTest {
    /**
     * Ejecuta la operacion {@code treatsNoAckAsProvisionalSuccess} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     */
    @Test
    void treatsNoAckAsProvisionalSuccess() {
        final OdlCallOutcome outcome = new UpdateLspOutcomeClassifier().classify(200, "<failure>no-ack</failure>");

        assertEquals(OdlCallOutcomeType.PROVISIONAL_SUCCESS, outcome.type());
    }
}
