/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package com.sma.sdn.openflow;

import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.util.XmlSupport;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Convierte el inventario XML de OpenDaylight en registros OpenFlow resueltos por direccion de gestion.
 */
public final class OpenflowInventoryXmlDeserializer {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(OpenflowInventoryXmlDeserializer.class);

    /**
     * Analiza nodos OpenFlow, selecciona los perfiles esperados y valida sus conectores obligatorios.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Localiza nodos cuyo identificador comienza con {@code openflow:}.</li>
     *   <li>Relaciona la direccion IP del nodo con los perfiles configurados.</li>
     *   <li>Indexa conectores no locales por nombre, identificador y numero de puerto.</li>
     *   <li>Exige que los conectores de host y nucleo existan, esten vivos y no esten caidos.</li>
     * </ol>
     *
     * @param xml inventario XML recibido desde RESTCONF
     * @param profiles perfiles requeridos indexados por direccion de gestion
     * @return conmutadores descubiertos y validados
     * @throws IllegalArgumentException si el XML contiene datos duplicados, incompletos o inconsistentes
     */
    public List<OpenflowSwitchRecord> deserialize(
            final String xml, final Map<String, OpenflowBootstrapProfile> profiles) {
        final Document document = XmlSupport.parse(xml);
        final NodeList nodes = XmlSupport.nodes(document, "//*[local-name()='node']");
        final Map<String, OpenflowSwitchRecord> byManagementIp = new LinkedHashMap<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            final Node node = nodes.item(index);
            final String nodeId = XmlSupport.string(node, "./*[local-name()='id'][1]");
            if (nodeId == null || !nodeId.startsWith("openflow:")) {
                continue;
            }
            final String managementIp = XmlSupport.string(node, "./*[local-name()='ip-address'][1]");
            final OpenflowBootstrapProfile profile = profiles.get(managementIp);
            if (profile == null) {
                continue;
            }
            if (byManagementIp.containsKey(managementIp)) {
                throw new IllegalArgumentException(
                        "Se descubrieron varios nodos OpenFlow con la IP de gestion " + managementIp);
            }
            byManagementIp.put(managementIp, parseSwitch(node, nodeId, managementIp, profile));
        }
        final List<OpenflowSwitchRecord> result = new ArrayList<>();
        for (OpenflowBootstrapProfile profile : profiles.values()) {
            final OpenflowSwitchRecord switchRecord = byManagementIp.get(profile.managementIp());
            if (switchRecord == null) {
                throw new IllegalArgumentException("No se encontro el conmutador OpenFlow "
                        + profile.logicalName() + " con IP de gestion " + profile.managementIp());
            }
            result.add(switchRecord);
        }
        LOG.debug(
                "openflow_inventory_deserialized",
                "deserialize",
                "Se deserializo y valido el inventario OpenFlow requerido.",
                StructuredLogger.fields(
                        "inventory_node_count", nodes.getLength(), "resolved_switch_count", result.size()));
        return List.copyOf(result);
    }

    /**
     * Construye los tres indices de conectores de un nodo y valida los puertos exigidos por su perfil.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Recorre los conectores hijos directos y omite el conector LOCAL.</li>
     *   <li>Rechaza nombres, identificadores o numeros de puerto duplicados.</li>
     *   <li>Resuelve y valida los conectores de host y nucleo.</li>
     *   <li>Codifica solamente el identificador del nodo para su uso posterior en URI.</li>
     * </ol>
     *
     * @param node nodo XML del inventario
     * @param nodeId identificador OpenFlow sin codificar
     * @param managementIp direccion de gestion del nodo
     * @param profile perfil que define los conectores requeridos
     * @return registro completo del conmutador
     */
    private OpenflowSwitchRecord parseSwitch(
            final Node node,
            final String nodeId,
            final String managementIp,
            final OpenflowBootstrapProfile profile) {
        final Map<String, OpenflowConnectorRecord> byName = new HashMap<>();
        final Map<String, OpenflowConnectorRecord> byId = new HashMap<>();
        final Map<Integer, OpenflowConnectorRecord> byPort = new HashMap<>();
        final NodeList connectors = XmlSupport.nodes(node, "./*[local-name()='node-connector']");
        for (int index = 0; index < connectors.getLength(); index++) {
            final Node connectorNode = connectors.item(index);
            final String connectorId = XmlSupport.string(connectorNode, "./*[local-name()='id'][1]");
            final String name = XmlSupport.string(connectorNode, "./*[local-name()='name'][1]");
            if ("LOCAL".equalsIgnoreCase(name)
                    || connectorId != null && connectorId.toUpperCase().endsWith(":LOCAL")) {
                continue;
            }
            final Integer portNumber = XmlSupport.integer(connectorNode, "./*[local-name()='port-number'][1]");
            final String hardwareAddress = XmlSupport.string(
                    connectorNode, "./*[local-name()='hardware-address'][1]");
            final Boolean live = XmlSupport.bool(
                    connectorNode, ".//*[local-name()='state']/*[local-name()='live'][1]");
            final Boolean linkDown = XmlSupport.bool(
                    connectorNode, ".//*[local-name()='state']/*[local-name()='link-down'][1]");
            if (connectorId == null || name == null || portNumber == null || live == null || linkDown == null) {
                throw new IllegalArgumentException("El nodo " + nodeId
                        + " contiene un conector no local con identidad, puerto o estado incompleto");
            }
            final OpenflowConnectorRecord connector = new OpenflowConnectorRecord(
                    connectorId, name, portNumber, hardwareAddress == null ? "" : hardwareAddress, live, linkDown);
            putUnique(byName, name, connector, "nombre");
            putUnique(byId, connectorId, connector, "identificador");
            putUnique(byPort, portNumber, connector, "numero de puerto");
        }
        requireUsableConnector(profile, byName, profile.hostPortName());
        requireUsableConnector(profile, byName, profile.corePortName());
        return new OpenflowSwitchRecord(
                profile.logicalName(),
                managementIp,
                nodeId,
                URLEncoder.encode(nodeId, StandardCharsets.UTF_8).replace("+", "%20"),
                byName,
                byId,
                byPort);
    }

    /**
     * Inserta un conector en un indice y rechaza colisiones que volverian ambiguo el aprovisionamiento.
     *
     * @param index indice que recibe el conector
     * @param key clave obtenida del inventario
     * @param connector conector asociado
     * @param keyDescription descripcion formal de la clave
     * @param <K> tipo de la clave
     * @throws IllegalArgumentException si la clave ya pertenece a otro conector
     */
    private static <K> void putUnique(
            final Map<K, OpenflowConnectorRecord> index,
            final K key,
            final OpenflowConnectorRecord connector,
            final String keyDescription) {
        if (index.putIfAbsent(key, connector) != null) {
            throw new IllegalArgumentException("Se encontro un conector OpenFlow duplicado por " + keyDescription
                    + ": " + key);
        }
    }

    /**
     * Exige que un conector requerido exista y mantenga un enlace operativo aceptable.
     *
     * @param profile perfil del conmutador evaluado
     * @param connectors conectores indexados por nombre
     * @param connectorName nombre obligatorio
     * @throws IllegalArgumentException si el conector falta o fue reportado como no operativo
     */
    private static void requireUsableConnector(
            final OpenflowBootstrapProfile profile,
            final Map<String, OpenflowConnectorRecord> connectors,
            final String connectorName) {
        final OpenflowConnectorRecord connector = connectors.get(connectorName);
        if (connector == null) {
            throw new IllegalArgumentException("Falta el conector " + connectorName
                    + " en el conmutador " + profile.logicalName());
        }
        if (!connector.live() || connector.linkDown()) {
            throw new IllegalArgumentException("El conector " + connectorName
                    + " del conmutador " + profile.logicalName() + " no esta operativo");
        }
    }
}
