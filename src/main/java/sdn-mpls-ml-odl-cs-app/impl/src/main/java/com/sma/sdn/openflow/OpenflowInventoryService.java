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
import com.sma.sdn.observability.StructuredLogger;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lee el inventario no configurado de ODL y entrega exclusivamente los conmutadores de acceso requeridos.
 */
public final class OpenflowInventoryService {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(OpenflowInventoryService.class);
    private final OdlRestconfDataClient dataClient;
    private final OpenflowInventoryXmlDeserializer deserializer;
    private final Map<String, OpenflowBootstrapProfile> profilesByManagementIp;
    private final SdnMplsMlMetrics metrics;

    /**
     * Crea el lector de inventario y sus perfiles ECHO y FOXTROT a partir de la configuracion validada.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Conserva el cliente RESTCONF y el deserializador.</li>
     *   <li>Construye perfiles estables para las dos IP de gestion.</li>
     *   <li>Rechaza direcciones duplicadas que impedirian una resolucion univoca.</li>
     * </ol>
     *
     * @param config configuracion de conmutadores y conectores
     * @param dataClient cliente RESTCONF autenticado
     * @param deserializer analizador de inventario XML
     * @param metrics registro de metricas operativas
     */
    public OpenflowInventoryService(
            final AppConfig config,
            final OdlRestconfDataClient dataClient,
            final OpenflowInventoryXmlDeserializer deserializer,
            final SdnMplsMlMetrics metrics) {
        this.dataClient = Objects.requireNonNull(dataClient, "dataClient");
        this.deserializer = Objects.requireNonNull(deserializer, "deserializer");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        final Map<String, OpenflowBootstrapProfile> profiles = new LinkedHashMap<>();
        addProfile(profiles, new OpenflowBootstrapProfile(
                "ECHO", config.ovsEchoManagementIp(), config.ovsEchoHostPortName(), config.ovsEchoCorePortName()));
        addProfile(profiles, new OpenflowBootstrapProfile(
                "FOXTROT", config.ovsFoxtrotManagementIp(),
                config.ovsFoxtrotHostPortName(), config.ovsFoxtrotCorePortName()));
        profilesByManagementIp = Map.copyOf(profiles);
    }

    /**
     * Solicita el inventario operativo, clasifica el resultado HTTP y valida los dos conmutadores requeridos.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Incrementa el contador de intentos y ejecuta el GET de inventario.</li>
     *   <li>Exige un codigo HTTP exitoso.</li>
     *   <li>Deserializa y valida nodos y conectores.</li>
     *   <li>Registra las metricas y resumenes de cada identidad resuelta.</li>
     * </ol>
     *
     * @return conmutadores ECHO y FOXTROT descubiertos
     * @throws IllegalStateException si ODL no entrega una respuesta exitosa
     * @throws IllegalArgumentException si el inventario no satisface los perfiles
     */
    public List<OpenflowSwitchRecord> discoverSwitches() {
        metrics.increment("sma_openflow_inventory_discovery_attempts_total");
        try {
            final HttpResponse<String> response = dataClient.getOpenflowInventory();
            if (!isSuccess(response.statusCode())) {
                throw new IllegalStateException(
                        "La lectura del inventario OpenFlow fallo con HTTP " + response.statusCode());
            }
            final List<OpenflowSwitchRecord> switches =
                    deserializer.deserialize(response.body(), profilesByManagementIp);
            metrics.increment("sma_openflow_inventory_discovery_success_total");
            for (OpenflowSwitchRecord switchRecord : switches) {
                metrics.increment("sma_openflow_switch_resolved_total");
                logResolvedSwitch(switchRecord);
            }
            return switches;
        } catch (RuntimeException e) {
            metrics.increment("sma_openflow_inventory_discovery_failure_total");
            throw e;
        }
    }

    /**
     * Registra un perfil por IP y rechaza dos conmutadores configurados con la misma direccion.
     *
     * @param profiles indice mutable durante la construccion
     * @param profile perfil que sera agregado
     * @throws IllegalArgumentException si la IP ya fue registrada
     */
    private static void addProfile(
            final Map<String, OpenflowBootstrapProfile> profiles, final OpenflowBootstrapProfile profile) {
        if (profiles.putIfAbsent(profile.managementIp(), profile) != null) {
            throw new IllegalArgumentException("Las IP de gestion OpenFlow deben ser diferentes: "
                    + profile.managementIp());
        }
    }

    /**
     * Emite el resumen del conmutador y de cada conector requerido con su estado operacional.
     *
     * @param switchRecord conmutador validado que sera descrito
     */
    private void logResolvedSwitch(final OpenflowSwitchRecord switchRecord) {
        LOG.info(
                "openflow_switch_resolved",
                "logResolvedSwitch",
                "Se resolvio un conmutador OpenFlow por su direccion de gestion.",
                StructuredLogger.fields(
                        "logical_name", switchRecord.logicalName(),
                        "management_ip", switchRecord.managementIp(),
                        "node_id", switchRecord.nodeId()));
        for (OpenflowConnectorRecord connector : switchRecord.connectorsByName().values()) {
            metrics.increment("sma_openflow_connector_resolved_total");
            LOG.info(
                    "openflow_connector_resolved",
                    "logResolvedSwitch",
                    "Se resolvio un conector del conmutador OpenFlow.",
                    StructuredLogger.fields(
                            "logical_name", switchRecord.logicalName(),
                            "connector_name", connector.name(),
                            "connector_id", connector.connectorId(),
                            "port_number", connector.portNumber(),
                            "live", connector.live(),
                            "link_down", connector.linkDown()));
        }
    }

    /**
     * Determina si RESTCONF confirmo una operacion mediante uno de sus codigos exitosos admitidos.
     *
     * @param statusCode codigo HTTP recibido
     * @return {@code true} para 200, 201 o 204
     */
    private static boolean isSuccess(final int statusCode) {
        return statusCode == 200 || statusCode == 201 || statusCode == 204;
    }
}
