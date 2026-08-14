/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package com.sma.sdn.openflow;

import com.sma.sdn.util.XmlSupport;

/**
 * Serializa reglas de acceso OpenFlow en el esquema XML aceptado por el inventario de flujos de OpenDaylight.
 */
public final class OpenflowFlowXmlSerializer {

    /**
     * Construye un flujo con coincidencia por conector de ingreso y tipo Ethernet, seguido de sus acciones ordenadas.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Escribe identidad, tabla, prioridad, cookie y tiempos permanentes.</li>
     *   <li>Usa el identificador completo del conector exclusivamente en {@code in-port}.</li>
     *   <li>Agrega una copia al controlador cuando fue solicitada.</li>
     *   <li>Usa el numero de puerto para la accion de salida hacia el acceso.</li>
     * </ol>
     *
     * @param definition definicion validada del flujo
     * @return documento XML listo para RESTCONF PUT
     */
    public String serialize(final OpenflowFlowDefinition definition) {
        final StringBuilder actions = new StringBuilder();
        int order = 0;
        if (definition.copyToController()) {
            appendOutputAction(actions, order++, "CONTROLLER", 65535);
        }
        appendOutputAction(actions, order, Integer.toString(definition.outputPortNumber()), 0);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<flow xmlns=\"urn:opendaylight:flow:inventory\">"
                + "<id>" + XmlSupport.escape(definition.flowId()) + "</id>"
                + "<table_id>" + definition.tableId() + "</table_id>"
                + "<priority>" + definition.priority() + "</priority>"
                + "<cookie>" + definition.cookie() + "</cookie>"
                + "<idle-timeout>0</idle-timeout><hard-timeout>0</hard-timeout>"
                + "<flags>SEND_FLOW_REM</flags>"
                + "<match><in-port>" + XmlSupport.escape(definition.inputConnectorId()) + "</in-port>"
                + "<ethernet-match><ethernet-type><type>" + definition.ethernetType()
                + "</type></ethernet-type></ethernet-match></match>"
                + "<instructions><instruction><order>0</order><apply-actions>"
                + actions
                + "</apply-actions></instruction></instructions></flow>";
    }

    /**
     * Agrega una accion de salida con orden, destino y longitud maxima explicitamente definidos.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Escapa el destino para mantener XML valido.</li>
     *   <li>Escribe el orden de ejecucion de la accion.</li>
     *   <li>Agrega la longitud maxima requerida por OpenFlow Plugin.</li>
     * </ol>
     *
     * @param target acumulador XML de acciones
     * @param order orden secuencial de la accion
     * @param outputConnector destino numerico o {@code CONTROLLER}
     * @param maxLength longitud maxima del paquete enviado
     */
    private static void appendOutputAction(
            final StringBuilder target, final int order, final String outputConnector, final int maxLength) {
        target.append("<action><order>").append(order).append("</order><output-action>")
                .append("<output-node-connector>").append(XmlSupport.escape(outputConnector))
                .append("</output-node-connector><max-length>").append(maxLength)
                .append("</max-length></output-action></action>");
    }
}
