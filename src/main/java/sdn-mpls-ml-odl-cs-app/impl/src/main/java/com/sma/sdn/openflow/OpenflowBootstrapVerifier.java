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
 * <p>Verifies that bootstrap flows appear in RESTCONF configuration and then polls
 * for their propagation to the operational inventory. {@link OpenflowBootstrapService}
 * uses this confirmation before declaring switch bootstrap complete.</p>
 */
public final class OpenflowBootstrapVerifier {
    /** <p>Records pending and completed operational verification.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(OpenflowBootstrapVerifier.class);
    /** <p>Reads configured flows and the operational OpenFlow table.</p> */
    private final OdlRestconfDataClient dataClient;
    /** <p>Controls the deadline and delay for operational-state polling.</p> */
    private final RetryPolicy retryPolicy;

    /**
     * <p>Creates a verifier with the RESTCONF client and polling policy.</p>
     * <p>Construction has one step:</p>
     * <ol><li>Require and store both collaborators.</li></ol>
     *
     * @param dataClient RESTCONF data client
     * @param retryPolicy deadline-based policy for operational propagation
     * @throws NullPointerException if either collaborator is null
     */
    public OpenflowBootstrapVerifier(
            final OdlRestconfDataClient dataClient, final RetryPolicy retryPolicy) {
        this.dataClient = Objects.requireNonNull(dataClient, "dataClient");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
    }

    /**
     * <p>Verifies each flow in configuration once, then polls until all flow IDs appear
     * in the operational table. The caller must supply at least one flow for polling.</p>
     *
     * <p>Verification proceeds as follows:</p>
     * <ol>
     *   <li>Read each configured flow and reject a failed status or missing ID immediately.</li>
     *   <li>Poll the operational table through {@link RetryPolicy} until all IDs are present.</li>
     *   <li>Log successful verification after the polling policy returns.</li>
     * </ol>
     *
     * @param switchRecord switch whose flows are verified
     * @param flows expected flows, with the first flow supplying the operational table ID
     * @throws IllegalStateException if configuration is missing or polling fails to confirm propagation
     */
    public void verify(
            final OpenflowSwitchRecord switchRecord, final List<OpenflowFlowDefinition> flows) {
        /*Initially we attempt to perform a per flow validation, and if we go through them we then perform a
        * poll for the entire set of flows, expecting for them to also return true */
        for (OpenflowFlowDefinition flow : flows) {
            final HttpResponse<String> response = dataClient.getOpenflowFlowConfig(
                    switchRecord.encodedNodeId(), flow.tableId(), flow.flowId());
            if (!isSuccess(response.statusCode()) || !containsFlow(response.body(), flow.flowId())) {
                throw new IllegalStateException("El flujo configurado " + flow.flowId()
                        + " no esta presente despues del PUT; HTTP " + response.statusCode());
            }
        }
        /*Attempts the same operation until the outcome of this becomes true, which means until the corresponding flows have
        * been identified in the table, for the given switch and set of flows*/
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
     * <p>Reads the first flow's operational table and checks that every expected ID is present.</p>
     * <p>The check proceeds as follows:</p>
     * <ol><li>Read the table; log and return false for an unsuccessful status.</li>
     * <li>Check each flow ID, logging and returning false on the first missing one.</li>
     * <li>Return true after all IDs are found.</li></ol>
     *
     * @param switchRecord switch to query
     * @param flows nonempty list of expected flows
     * @return whether the successful response contains every flow ID
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
        /*We validate that in total all the flows are present within the flow table and return the corresponding false if
        * a single one is missing*/
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
     * <p>Checks whether an XML response contains a flow element with the given ID.</p>
     * <p>The lookup proceeds as follows:</p>
     * <ol><li>Return false for null or blank XML.</li>
     * <li>Parse the document and evaluate a local-name XPath for the exact ID.</li></ol>
     *
     * @param xml RESTCONF XML response
     * @param flowId exact flow identifier to find
     * @return whether a matching flow ID is present
     */
    private static boolean containsFlow(final String xml, final String flowId) {
        if (xml == null || xml.isBlank()) {
            return false;
        }
        final Document document = XmlSupport.parse(xml);
        /*We apply another XPath operation that filters the flow instances for at least one where the id corresponds
        * to the created flow id and whose node length is higher than 0, meaning there is associated content*/
        return XmlSupport.nodes(document,
                "//*[local-name()='flow']/*[local-name()='id' and text()='" + flowId + "']")
                .getLength() > 0;
    }

    /**
     * <p>Recognizes the statuses accepted when reading RESTCONF resources.</p>
     * <p>The check has one step:</p>
     * <ol><li>Compare the status with 200, 201 and 204.</li></ol>
     *
     * @param statusCode received HTTP status
     * @return whether the status is 200, 201 or 204
     */
    private static boolean isSuccess(final int statusCode) {
        return statusCode == 200 || statusCode == 201 || statusCode == 204;
    }
}
