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
import java.time.Instant;
import java.util.Optional;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;

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
     * @param config valor requerido para ejecutar esta operacion
     */
    public PacketInFeatureExtractor(final AppConfig config) {
        this.config = config;
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

        final ParsedPacket parsed = parse(payload);
        final String ingress = String.valueOf(notification.getIngress());
        final String connector = ingressConnector(ingress);
        final String switchName = ingressSwitch(ingress);
        final FlowDirection direction = resolveDirection(switchName, connector);
        LOG.debug("packet_features_extracted", "extract",
                "Se extrajeron las caracteristicas seguras de la notificacion PacketIn",
                StructuredLogger.fields("payload_length", payload.length, "ingress_switch", switchName,
                        "ingress_connector", connector, "eth_type", parsed.ethType(),
                        "ip_protocol", parsed.ipProto(), "source_port", parsed.srcPort(),
                        "destination_port", parsed.dstPort(), "direction", direction));
        return Optional.of(new PacketClassificationContext(
                switchName,
                connector,
                switchName,
                connector,
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
        if (config.headendToTailendIngress().matches(switchName, connectorName)) {
            return FlowDirection.HEADEND_TO_TAILEND;
        }
        if (config.tailendToHeadendIngress().matches(switchName, connectorName)) {
            return FlowDirection.TAILEND_TO_HEADEND;
        }
        return FlowDirection.UNKNOWN;
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
     * Ejecuta la operacion {@code ingressSwitch} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param ingress valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private static String ingressSwitch(final String ingress) {
        final int connectorSeparator = ingress.lastIndexOf(':');
        if (connectorSeparator > 0) {
            return ingress.substring(0, connectorSeparator);
        }
        return ingress;
    }

    /**
     * Ejecuta la operacion {@code ingressConnector} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param ingress valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private static String ingressConnector(final String ingress) {
        final int connectorSeparator = ingress.lastIndexOf(':');
        if (connectorSeparator > 0 && connectorSeparator + 1 < ingress.length()) {
            return ingress.substring(connectorSeparator + 1);
        }
        return ingress;
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
}
