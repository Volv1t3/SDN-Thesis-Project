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

package com.sma.sdn.serialization.xml;

import com.sma.sdn.model.EroSubobject;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.util.XmlSupport;
import java.util.List;

/**
 * <p>Renders ordered explicit-route subobjects as XML fragments. The update-lsp request
 * serializer embeds these fragments in the request sent by the delegated LSP service.</p>
 */
public final class EroXmlSerializer {
    /** <p>Logger for route serialization counts and output length.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(EroXmlSerializer.class);
    /**
     * <p>Builds the XML fragment for the supplied ordered route subobjects.</p>
     *
     * <p>Serialization proceeds as follows:</p>
     * <ol>
     *   <li>Iterate in input order and write each loose flag and escaped IP prefix.</li>
     *   <li>Log the subobject count and fragment length, then return the fragment.</li>
     * </ol>
     *
     * @param subobjects ordered route subobjects to render
     * @return XML fragment containing the route subobjects
     * @throws NullPointerException if the list or an element is null
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
