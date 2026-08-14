/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package com.sma.sdn.openflow;

import com.sma.sdn.http.OdlRestconfDataClient;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.util.RetryPolicy;
import com.sma.sdn.util.XmlSupport;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Objects;
import org.w3c.dom.Document;

/**
 * Confirma primero la persistencia configurada y luego la propagacion operativa de los flujos de bootstrap.
 */
public final class OpenflowBootstrapVerifier {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(OpenflowBootstrapVerifier.class);
    private final OdlRestconfDataClient dataClient;
    private final RetryPolicy retryPolicy;

    /**
     * Crea el verificador con el cliente RESTCONF y la politica temporal de validacion operacional.
     *
     * @param dataClient cliente RESTCONF autenticado
     * @param retryPolicy politica de reintentos para propagacion al conmutador
     */
    public OpenflowBootstrapVerifier(
            final OdlRestconfDataClient dataClient, final RetryPolicy retryPolicy) {
        this.dataClient = Objects.requireNonNull(dataClient, "dataClient");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
    }

    /**
     * Exige que cada flujo exista en configuracion y espera hasta que todos aparezcan en estado operativo.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Consulta individualmente cada flujo con {@code content=config}.</li>
     *   <li>Trata una configuracion ausente como fallo duro del intento de disponibilidad.</li>
     *   <li>Consulta la tabla operativa completa y busca todos los identificadores.</li>
     *   <li>Reintenta la propagacion operativa hasta el limite configurado.</li>
     * </ol>
     *
     * @param switchRecord conmutador que contiene las reglas
     * @param flows reglas esperadas
     * @throws IllegalStateException si falta configuracion o no se confirma la propagacion operativa
     */
    public void verify(
            final OpenflowSwitchRecord switchRecord, final List<OpenflowFlowDefinition> flows) {
        for (OpenflowFlowDefinition flow : flows) {
            final HttpResponse<String> response = dataClient.getOpenflowFlowConfig(
                    switchRecord.encodedNodeId(), flow.tableId(), flow.flowId());
            if (!isSuccess(response.statusCode()) || !containsFlow(response.body(), flow.flowId())) {
                throw new IllegalStateException("El flujo configurado " + flow.flowId()
                        + " no esta presente despues del PUT; HTTP " + response.statusCode());
            }
        }
        retryPolicy.retryUntilTrue(
                () -> operationalContainsAll(switchRecord, flows),
                "Los flujos OpenFlow no fueron confirmados en el estado operativo de "
                        + switchRecord.logicalName());
        LOG.info(
                "openflow_bootstrap_flows_verified",
                "verify",
                "Se confirmaron los flujos OpenFlow en configuracion y estado operativo.",
                StructuredLogger.fields(
                        "logical_name", switchRecord.logicalName(),
                        "node_id", switchRecord.nodeId(),
                        "flow_count", flows.size()));
    }

    /**
     * Consulta una tabla operativa y confirma que contiene todos los identificadores esperados.
     *
     * @param switchRecord conmutador consultado
     * @param flows reglas que deben estar presentes
     * @return {@code true} si la respuesta fue exitosa y contiene todas las reglas
     */
    private boolean operationalContainsAll(
            final OpenflowSwitchRecord switchRecord, final List<OpenflowFlowDefinition> flows) {
        final HttpResponse<String> response = dataClient.getOpenflowTableOperational(
                switchRecord.encodedNodeId(), flows.getFirst().tableId());
        if (!isSuccess(response.statusCode())) {
            LOG.warn(
                    "openflow_operational_verification_pending",
                    "operationalContainsAll",
                    "La tabla OpenFlow operativa aun no esta disponible.",
                    StructuredLogger.fields(
                            "logical_name", switchRecord.logicalName(),
                            "status_code", response.statusCode()),
                    null);
            return false;
        }
        for (OpenflowFlowDefinition flow : flows) {
            if (!containsFlow(response.body(), flow.flowId())) {
                LOG.warn(
                        "openflow_operational_flow_pending",
                        "operationalContainsAll",
                        "Un flujo configurado aun no aparece en el estado operativo.",
                        StructuredLogger.fields(
                                "logical_name", switchRecord.logicalName(),
                                "flow_id", flow.flowId()),
                        null);
                return false;
            }
        }
        return true;
    }

    /**
     * Analiza una respuesta XML y busca un elemento {@code id} cuyo texto coincida exactamente con el flujo.
     *
     * @param xml documento RESTCONF recibido
     * @param flowId identificador exacto buscado
     * @return {@code true} cuando el identificador esta presente
     */
    private static boolean containsFlow(final String xml, final String flowId) {
        if (xml == null || xml.isBlank()) {
            return false;
        }
        final Document document = XmlSupport.parse(xml);
        return XmlSupport.nodes(document,
                "//*[local-name()='flow']/*[local-name()='id' and text()='" + flowId + "']")
                .getLength() > 0;
    }

    /**
     * Reconoce las respuestas RESTCONF que pueden contener el recurso solicitado.
     *
     * @param statusCode codigo HTTP recibido
     * @return {@code true} para HTTP 200, 201 o 204
     */
    private static boolean isSuccess(final int statusCode) {
        return statusCode == 200 || statusCode == 201 || statusCode == 204;
    }
}
