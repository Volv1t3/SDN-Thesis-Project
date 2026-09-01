/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.openflow;

import com.sma.sdn.config.AppConfig;
import com.sma.sdn.http.OdlRestconfDataClient;
import com.sma.sdn.metrics.SdnMplsMlMetrics;
import com.sma.sdn.model.ClassificationResult;
import com.sma.sdn.model.OpenFlowProgrammingResult;
import com.sma.sdn.model.OpenFlowSuppressionIntent;
import com.sma.sdn.model.PacketClassificationContext;
import com.sma.sdn.model.WorkflowContext;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.serialization.xml.OpenFlowSuppressionFlowXmlSerializer;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Instala flujos temporales de mayor prioridad para suprimir PacketIn repetidos por servicio. */
public final class OpenFlowSuppressionService {
    private static final int ETH_TYPE_IPV4 = 2048;
    private static final int IP_PROTO_ICMP = 1;
    private static final int IP_PROTO_TCP = 6;
    private static final int IP_PROTO_UDP = 17;
    private static final StructuredLogger LOG = StructuredLogger.getLogger(OpenFlowSuppressionService.class);
    private final AppConfig config;
    private final OpenflowSwitchRegistry switchRegistry;
    private final OdlRestconfDataClient dataClient;
    private final OpenFlowSuppressionFlowXmlSerializer serializer;
    private final SdnMplsMlMetrics metrics;

    public OpenFlowSuppressionService(
            final AppConfig config,
            final OpenflowSwitchRegistry switchRegistry,
            final OdlRestconfDataClient dataClient,
            final OpenFlowSuppressionFlowXmlSerializer serializer,
            final SdnMplsMlMetrics metrics) {
        this.config = Objects.requireNonNull(config, "config");
        this.switchRegistry = Objects.requireNonNull(switchRegistry, "switchRegistry");
        this.dataClient = Objects.requireNonNull(dataClient, "dataClient");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /** Construye una regla ICMP, TCP o UDP especifica para el conector de ingreso descubierto. */
    public Optional<OpenFlowSuppressionIntent> buildSuppressionIntent(
            final PacketClassificationContext packetContext, final ClassificationResult classification) {
        if (!config.openflowSuppressionEnabled() || packetContext.packetFeatures().ethType() != ETH_TYPE_IPV4) {
            return Optional.empty();
        }
        final int protocol = packetContext.packetFeatures().ipProto();
        final Integer destinationPort;
        final String service;
        if (protocol == IP_PROTO_ICMP) {
            destinationPort = null;
            service = "icmp";
        } else if (protocol == IP_PROTO_TCP && packetContext.packetFeatures().dstPort() > 0) {
            destinationPort = packetContext.packetFeatures().dstPort();
            service = "tcp-" + destinationPort;
        } else if (protocol == IP_PROTO_UDP && packetContext.packetFeatures().dstPort() > 0) {
            destinationPort = packetContext.packetFeatures().dstPort();
            service = "udp-" + destinationPort;
        } else {
            LOG.debug("openflow_suppression_unsupported", "buildSuppressionIntent",
                    "No se instalara supresion para un protocolo sin coincidencia de servicio segura.",
                    StructuredLogger.fields("ip_protocol", protocol,
                            "destination_port", packetContext.packetFeatures().dstPort()));
            return Optional.empty();
        }

        final OpenflowSwitchRecord switchRecord = switchRegistry.findByNodeId(packetContext.ingressOpenflowNodeId())
                .orElse(null);
        if (switchRecord == null) {
            return Optional.empty();
        }
        final String corePortName = corePortName(packetContext.ingressSwitchName());
        final OpenflowConnectorRecord output = switchRecord.connectorsByName().get(corePortName);
        if (output == null || !switchRecord.connectorsById().containsKey(packetContext.ingressConnectorId())) {
            LOG.warn("openflow_suppression_connectors_unresolved", "buildSuppressionIntent",
                    "No se encontro el conector de salida o ingreso para instalar la supresion.",
                    StructuredLogger.fields("switch_name", switchRecord.logicalName(), "core_port_name", corePortName,
                            "ingress_connector_id", packetContext.ingressConnectorId()), null);
            return Optional.empty();
        }
        final String flowId = "sma-suppress-" + switchRecord.logicalName().toLowerCase(Locale.ROOT)
                + "-" + service + "-" + packetContext.ingressConnectorName().replaceAll("[^a-zA-Z0-9-]", "-");
        final long cookie = config.openflowSuppressionCookieBase()
                + Math.floorMod(flowId.hashCode(), 65_536);
        return Optional.of(new OpenFlowSuppressionIntent(
                flowId, switchRecord.nodeId(), switchRecord.encodedNodeId(), config.openflowTableId(),
                config.openflowSuppressionPriority(), cookie, config.openflowSuppressionIdleTimeoutSeconds(),
                config.openflowSuppressionHardTimeoutSeconds(), packetContext.ingressConnectorId(),
                output.connectorId(),
                ETH_TYPE_IPV4, protocol, protocol == IP_PROTO_TCP ? destinationPort : null,
                protocol == IP_PROTO_UDP ? destinationPort : null, classification.className(),
                classification.policy().profileName()));
    }

    /** Programa la regla de supresion y devuelve el resultado sin convertir su fallo en un error de tunel. */
    public OpenFlowProgrammingResult installSuppressionFlow(
            final OpenFlowSuppressionIntent intent, final WorkflowContext workflowContext) {
        metrics.increment("sma_openflow_suppression_install_attempts_total");
        try {
            final HttpResponse<String> response = dataClient.putOpenflowFlow(
                    intent.encodedNodeId(), intent.tableId(), intent.flowId(), serializer.serialize(intent));
            final boolean installed = response.statusCode() == 200 || response.statusCode() == 201
                    || response.statusCode() == 204;
            if (installed) {
                metrics.increment("sma_openflow_suppression_install_success_total");
                LOG.info("openflow_suppression_flow_put_completed", "installSuppressionFlow",
                        "Se instalo un flujo temporal de supresion OpenFlow.",
                        StructuredLogger.fields("workflow_id", workflowContext.workflowId(),
                                "packet_sequence", workflowContext.packetSequence(), "flow_id", intent.flowId(),
                                "node_id", intent.nodeId(), "priority", intent.priority(),
                                "ip_protocol", intent.ipProtocol(), "status_code", response.statusCode()));
            } else {
                metrics.increment("sma_openflow_suppression_install_failure_total");
                LOG.warn("openflow_suppression_flow_put_failed", "installSuppressionFlow",
                        "ODL rechazo la instalacion del flujo temporal de supresion.",
                        StructuredLogger.fields("flow_id", intent.flowId(), "node_id", intent.nodeId(),
                                "status_code", response.statusCode()), null);
            }
            return new OpenFlowProgrammingResult(intent.flowId(), installed, response.statusCode(),
                    installed ? "ODL acepto el flujo de supresion" : "ODL rechazo el flujo de supresion");
        } catch (RuntimeException e) {
            metrics.increment("sma_openflow_suppression_install_failure_total");
            LOG.warn("openflow_suppression_flow_put_failed", "installSuppressionFlow",
                    "Fallo el transporte de la instalacion del flujo temporal de supresion.",
                    StructuredLogger.fields("flow_id", intent.flowId(), "node_id", intent.nodeId()), e);
            return new OpenFlowProgrammingResult(intent.flowId(), false, 0, e.getMessage());
        }
    }

    private String corePortName(final String switchName) {
        return switch (switchName) {
            case "ECHO" -> config.ovsEchoCorePortName();
            case "FOXTROT" -> config.ovsFoxtrotCorePortName();
            default -> throw new IllegalArgumentException("Conmutador de ingreso no soportado: " + switchName);
        };
    }
}
