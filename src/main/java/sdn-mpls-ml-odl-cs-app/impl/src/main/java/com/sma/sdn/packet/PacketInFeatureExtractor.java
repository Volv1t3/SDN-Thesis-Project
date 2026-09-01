/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.packet;

import com.sma.sdn.config.AppConfig;
import com.sma.sdn.model.FlowDirection;
import com.sma.sdn.model.PacketClassificationContext;
import com.sma.sdn.model.PacketFeatures;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.openflow.OpenflowConnectorRecord;
import com.sma.sdn.openflow.OpenflowSwitchRecord;
import com.sma.sdn.openflow.OpenflowSwitchRegistry;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yangtools.binding.BindingInstanceIdentifier;
import org.opendaylight.yangtools.binding.KeyStep;

/**
 * Define la clase {@code PacketInFeatureExtractor} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class PacketInFeatureExtractor {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(PacketInFeatureExtractor.class);
    private static final int ETH_TYPE_IPV4 = 0x0800;
    private static final int ETH_TYPE_8021Q = 0x8100;
    private static final int ETH_TYPE_QINQ = 0x88a8;
    private final AppConfig config;
    private final OpenflowSwitchRegistry switchRegistry;

    /**
     * Ejecuta la operacion {@code PacketInFeatureExtractor} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param config configuracion que define las direcciones logicas admitidas
     * @param switchRegistry registro de identidades OpenFlow descubierto desde RESTCONF
     */
    public PacketInFeatureExtractor(final AppConfig config, final OpenflowSwitchRegistry switchRegistry) {
        this.config = Objects.requireNonNull(config, "config");
        this.switchRegistry = Objects.requireNonNull(switchRegistry, "switchRegistry");
    }

    /**
     * Extrae metadatos de ingreso y caracteristicas de primer paquete desde la notificacion OpenFlow.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param notification valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public Optional<PacketClassificationContext> extract(final PacketReceived notification) {
        final byte[] payload = notification.getPayload();
        if (payload == null || payload.length < 14) {
            LOG.debug("packet_features_rejected", "extract",
                    "La notificacion no contiene una trama Ethernet completa",
                    StructuredLogger.fields("payload_present", payload != null,
                            "payload_length", payload == null ? 0 : payload.length));
            return Optional.empty();
        }

        final Optional<IngressIdentity> ingressIdentity = ingressIdentity(notification.getIngress());
        if (ingressIdentity.isEmpty()) {
            LOG.warn("packet_ingress_identity_rejected", "extract",
                    "La notificacion PacketIn no contiene una identidad tipada de nodo y conector OpenFlow.",
                    StructuredLogger.fields("raw_ingress", String.valueOf(notification.getIngress())), null);
            return Optional.empty();
        }

        final IngressIdentity ingress = ingressIdentity.orElseThrow();
        final Optional<OpenflowSwitchRecord> switchRecord = switchRegistry.findByNodeId(ingress.nodeId());
        final Optional<OpenflowConnectorRecord> connectorRecord = switchRegistry.findConnectorById(
                ingress.nodeId(), ingress.connectorId());
        if (switchRecord.isEmpty() || connectorRecord.isEmpty()) {
            LOG.debug("packet_ingress_inventory_unresolved", "extract",
                    "La identidad tipada de ingreso no esta disponible en la instantanea actual del inventario OpenFlow.",
                    StructuredLogger.fields(
                            "ingress_openflow_node_id", ingress.nodeId(),
                            "ingress_connector_id", ingress.connectorId(),
                            "switch_registered", switchRecord.isPresent(),
                            "connector_registered", connectorRecord.isPresent()));
            return Optional.empty();
        }

        final ParsedPacket parsed = parse(payload);
        final String switchName = switchRecord.orElseThrow().logicalName();
        final String connectorName = connectorRecord.orElseThrow().name();
        final FlowDirection direction = resolveDirection(switchName, connectorName);
        LOG.debug("packet_features_extracted", "extract",
                "Se extrajeron las caracteristicas seguras de la notificacion PacketIn",
                StructuredLogger.fields("payload_length", payload.length,
                        "ingress_openflow_node_id", ingress.nodeId(),
                        "ingress_connector_id", ingress.connectorId(),
                        "ingress_switch", switchName,
                        "ingress_connector", connectorName, "eth_type", parsed.ethType(),
                        "ip_protocol", parsed.ipProto(), "source_port", parsed.srcPort(),
                        "destination_port", parsed.dstPort(), "direction", direction));
        return Optional.of(new PacketClassificationContext(
                ingress.nodeId(),
                ingress.connectorId(),
                switchName,
                connectorName,
                new PacketFeatures(parsed.ethType(), parsed.ipProto(), parsed.srcPort(), parsed.dstPort()),
                direction,
                Instant.now()));
    }

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
     * @param payload valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    private ParsedPacket parse(final byte[] payload) {
        int cursor = 12;
        int ethType = unsignedShort(payload, cursor);
        cursor += 2;
        while ((ethType == ETH_TYPE_8021Q || ethType == ETH_TYPE_QINQ) && payload.length >= cursor + 4) {
            cursor += 2;
            ethType = unsignedShort(payload, cursor);
            cursor += 2;
        }
        if (ethType != ETH_TYPE_IPV4 || payload.length < cursor + 20) {
            return new ParsedPacket(ethType, 0, 0, 0);
        }

        final int ihlBytes = (payload[cursor] & 0x0f) * 4;
        if (ihlBytes < 20 || payload.length < cursor + ihlBytes) {
            return new ParsedPacket(ethType, 0, 0, 0);
        }
        final int ipProto = payload[cursor + 9] & 0xff;
        final int flagsFragmentOffset = unsignedShort(payload, cursor + 6);
        final boolean fragmented = (flagsFragmentOffset & 0x1fff) != 0;
        final int l4 = cursor + ihlBytes;
        if (!fragmented && (ipProto == 6 || ipProto == 17) && payload.length >= l4 + 4) {
            return new ParsedPacket(ethType, ipProto, unsignedShort(payload, l4), unsignedShort(payload, l4 + 2));
        }
        return new ParsedPacket(ethType, ipProto, 0, 0);
    }

    /**
     * Ejecuta la operacion {@code resolveDirection} dentro del componente correspondiente.
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
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    private FlowDirection resolveDirection(final String switchName, final String connectorName) {
        return config.resolveClassificationIngress(switchName, connectorName);
    }

    /**
     * Ejecuta la operacion {@code unsignedShort} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param payload valor requerido para ejecutar esta operacion
     *
     * @param offset valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private static int unsignedShort(final byte[] payload, final int offset) {
        return ((payload[offset] & 0xff) << 8) | (payload[offset + 1] & 0xff);
    }

    /**
     * Extrae las claves YANG de nodo y conector desde la referencia tipada de una notificacion PacketIn.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Obtiene el identificador de instancia enlazado en {@code NodeConnectorRef}.</li>
     *   <li>Recorre sus pasos tipados y recupera {@code NodeKey} y {@code NodeConnectorKey}.</li>
     *   <li>Devuelve los valores de las claves sin interpretar la salida diagnostica de {@code toString()}.</li>
     * </ol>
     *
     * @param ingress referencia YANG de ingreso entregada por ODL
     *
     * @return identidad completa de nodo y conector, o vacio si la referencia esta incompleta
     */
    private static Optional<IngressIdentity> ingressIdentity(final NodeConnectorRef ingress) {
        if (ingress == null || ingress.getValue() == null) {
            return Optional.empty();
        }
        String nodeId = null;
        String connectorId = null;
        for (BindingInstanceIdentifier.Step step : ingress.getValue().steps()) {
            if (!(step instanceof KeyStep<?, ?> keyStep)) {
                continue;
            }
            if (keyStep.key() instanceof NodeKey nodeKey) {
                nodeId = nodeKey.getId().getValue();
            } else if (keyStep.key() instanceof NodeConnectorKey connectorKey) {
                connectorId = connectorKey.getId().getValue();
            }
        }
        if (nodeId == null || connectorId == null) {
            return Optional.empty();
        }
        return Optional.of(new IngressIdentity(nodeId, connectorId));
    }

    /**
     * Define el resultado interno del parseo Ethernet e IPv4. Este record transporta unicamente los campos requeridos
     * para clasificacion sin exponer el paquete completo ni datos de payload no necesarios.
     *
     * @param ethType EtherType extraido de la trama
     * @param ipProto protocolo IP extraido del encabezado IPv4
     * @param srcPort puerto de origen de capa 4 o cero cuando no aplica
     * @param dstPort puerto de destino de capa 4 o cero cuando no aplica
     */
    private record ParsedPacket(int ethType, int ipProto, int srcPort, int dstPort) {
    }

    /**
     * Representa las claves OpenFlow obtenidas directamente desde la referencia YANG de ingreso.
     *
     * @param nodeId identificador completo del nodo OpenFlow
     * @param connectorId identificador completo del conector OpenFlow
     */
    private record IngressIdentity(String nodeId, String connectorId) {
    }
}
