/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.serialization.xml;

import com.sma.sdn.model.EroSubobject;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.util.XmlSupport;
import java.util.List;

/**
 * Define la clase {@code EroXmlSerializer} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class EroXmlSerializer {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(EroXmlSerializer.class);
    /**
     * Construye la representacion serializada requerida por el endpoint consumidor.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param subobjects valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    public String serialize(final List<EroSubobject> subobjects) {
        final StringBuilder builder = new StringBuilder();
        for (EroSubobject subobject : subobjects) {
            builder.append("            <subobject>\n")
                    .append("                <loose>").append(subobject.loose()).append("</loose>\n")
                    .append("                <ip-prefix>\n")
                    .append("                    <ip-prefix>")
                    .append(XmlSupport.escape(subobject.ipPrefix()))
                    .append("</ip-prefix>\n")
                    .append("                </ip-prefix>\n")
                    .append("            </subobject>\n");
        }
        final String xml = builder.toString();
        LOG.trace("ero_serialized", "serialize",
                "Se serializaron los subobjetos de la ruta explicita",
                StructuredLogger.fields("subobject_count", subobjects.size(), "serialized_characters", xml.length()));
        return xml;
    }
}
