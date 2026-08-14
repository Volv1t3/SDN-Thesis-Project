/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package com.sma.sdn.openflow;

import com.sma.sdn.config.AppConfig;
import com.sma.sdn.metrics.SdnMplsMlMetrics;
import com.sma.sdn.observability.StructuredLogger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orquesta el descubrimiento, registro, instalacion y verificacion del plano de acceso OpenFlow.
 */
public final class OpenflowBootstrapService {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(OpenflowBootstrapService.class);
    private static final int ARP_ETHERNET_TYPE = 2054;
    private static final int IPV4_ETHERNET_TYPE = 2048;
    private final AppConfig config;
    private final OpenflowInventoryService inventoryService;
    private final OpenflowSwitchRegistry switchRegistry;
    private final OpenflowFlowProvisioningService provisioningService;
    private final OpenflowBootstrapVerifier verifier;
    private final SdnMplsMlMetrics metrics;
    private final AtomicBoolean ready = new AtomicBoolean();

    /**
     * Crea el coordinador de bootstrap con dependencias separadas para descubrimiento, escritura y verificacion.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida y conserva la configuracion.</li>
     *   <li>Valida los servicios de inventario, registro, provisionamiento y verificacion.</li>
     *   <li>Inicializa el estado como no disponible hasta completar un ciclo.</li>
     * </ol>
     *
     * @param config configuracion OpenFlow
     * @param inventoryService servicio de descubrimiento
     * @param switchRegistry registro dinamico de identidades
     * @param provisioningService servicio de instalacion de flujos
     * @param verifier verificador configurado y operativo
     * @param metrics registro de metricas
     */
    public OpenflowBootstrapService(
            final AppConfig config,
            final OpenflowInventoryService inventoryService,
            final OpenflowSwitchRegistry switchRegistry,
            final OpenflowFlowProvisioningService provisioningService,
            final OpenflowBootstrapVerifier verifier,
            final SdnMplsMlMetrics metrics) {
        this.config = Objects.requireNonNull(config, "config");
        this.inventoryService = Objects.requireNonNull(inventoryService, "inventoryService");
        this.switchRegistry = Objects.requireNonNull(switchRegistry, "switchRegistry");
        this.provisioningService = Objects.requireNonNull(provisioningService, "provisioningService");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /**
     * Ejecuta un ciclo completo y solo publica disponibilidad despues de verificar los ocho flujos requeridos.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Marca temporalmente el bootstrap como no disponible.</li>
     *   <li>Descubre ECHO y FOXTROT por sus IP de gestion y actualiza el registro.</li>
     *   <li>Construye cuatro reglas deterministas para cada conmutador.</li>
     *   <li>Instala cada regla y rechaza cualquier resultado no exitoso.</li>
     *   <li>Confirma persistencia configurada y propagacion operativa.</li>
     *   <li>Publica disponibilidad y la marca temporal del ultimo exito.</li>
     * </ol>
     *
     * @throws IllegalStateException si una instalacion o verificacion no concluye correctamente
     * @throws IllegalArgumentException si el inventario no satisface la configuracion
     */
    public void initialize() {
        ready.set(false);
        metrics.set("sma_openflow_bootstrap_ready", 0L);
        if (!config.openflowBootstrapEnabled()) {
            ready.set(true);
            metrics.set("sma_openflow_bootstrap_ready", 1L);
            LOG.warn(
                    "openflow_bootstrap_disabled",
                    "initialize",
                    "El bootstrap OpenFlow esta deshabilitado; no se instalaran ni verificaran flujos de acceso.",
                    java.util.Map.of(),
                    null);
            return;
        }
        if (config.openflowInstallDefaultDrop()) {
            LOG.warn(
                    "openflow_default_drop_not_installed",
                    "initialize",
                    "Se solicito el flujo drop predeterminado, pero esta primera implementacion conserva "
                            + "el table-miss de ODL.",
                    StructuredLogger.fields("configured_priority", config.openflowDefaultDropPriority()),
                    null);
        }
        try {
            final List<OpenflowSwitchRecord> switches = inventoryService.discoverSwitches();
            switchRegistry.replace(switches);
            long cookieBase = 9_100_000L;
            for (OpenflowSwitchRecord switchRecord : switches) {
                final List<OpenflowFlowDefinition> flows = definitionsFor(switchRecord, cookieBase);
                cookieBase += 100L;
                for (OpenflowFlowDefinition flow : flows) {
                    final OpenflowFlowInstallResult result = provisioningService.installFlow(switchRecord, flow);
                    if (!result.success()) {
                        throw new IllegalStateException("No fue posible instalar el flujo " + result.flowId()
                                + "; HTTP " + result.statusCode() + "; " + result.detail());
                    }
                }
                verifier.verify(switchRecord, flows);
            }
            ready.set(true);
            metrics.set("sma_openflow_bootstrap_ready", 1L);
            metrics.set("sma_openflow_bootstrap_last_success_timestamp", Instant.now().getEpochSecond());
            LOG.info(
                    "openflow_bootstrap_ready",
                    "initialize",
                    "El bootstrap OpenFlow completo la instalacion y verificacion de los flujos de acceso.",
                    StructuredLogger.fields("switch_count", switches.size(), "flow_count", switches.size() * 4));
        } catch (RuntimeException e) {
            ready.set(false);
            metrics.set("sma_openflow_bootstrap_ready", 0L);
            throw e;
        }
    }

    /**
     * Informa si el ultimo ciclo de bootstrap concluyo satisfactoriamente.
     *
     * @return {@code true} cuando los flujos fueron verificados o el subsistema esta deshabilitado
     */
    public boolean isReady() {
        return ready.get();
    }

    /**
     * Construye las cuatro reglas bidireccionales de ARP e IPv4 para un conmutador resuelto.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Resuelve los nombres de host y nucleo segun el conmutador logico.</li>
     *   <li>Obtiene identificadores completos para las coincidencias de ingreso.</li>
     *   <li>Obtiene numeros de puerto para las acciones de salida.</li>
     *   <li>Activa la copia al controlador solamente para IPv4 originado por el host.</li>
     * </ol>
     *
     * @param switchRecord conmutador descubierto
     * @param cookieBase valor base reservado para sus reglas
     * @return cuatro definiciones deterministas
     */
    private List<OpenflowFlowDefinition> definitionsFor(
            final OpenflowSwitchRecord switchRecord, final long cookieBase) {
        final boolean echo = "ECHO".equals(switchRecord.logicalName());
        final String hostName = echo ? config.ovsEchoHostPortName() : config.ovsFoxtrotHostPortName();
        final String coreName = echo ? config.ovsEchoCorePortName() : config.ovsFoxtrotCorePortName();
        final OpenflowConnectorRecord host = requireConnector(switchRecord, hostName);
        final OpenflowConnectorRecord core = requireConnector(switchRecord, coreName);
        final String prefix = "sma-bootstrap-" + switchRecord.logicalName().toLowerCase() + "-";
        final List<OpenflowFlowDefinition> flows = new ArrayList<>(4);
        flows.add(flow(prefix + "arp-host-to-core", config.openflowArpPriority(), cookieBase + 1,
                ARP_ETHERNET_TYPE, host, core, false));
        flows.add(flow(prefix + "arp-core-to-host", config.openflowArpPriority(), cookieBase + 2,
                ARP_ETHERNET_TYPE, core, host, false));
        flows.add(flow(prefix + "ipv4-host-to-core", config.openflowIpv4Priority(), cookieBase + 3,
                IPV4_ETHERNET_TYPE, host, core, true));
        flows.add(flow(prefix + "ipv4-core-to-host", config.openflowIpv4Priority(), cookieBase + 4,
                IPV4_ETHERNET_TYPE, core, host, false));
        return List.copyOf(flows);
    }

    /**
     * Crea una definicion usando identificador completo para ingreso y numero de puerto para salida.
     *
     * @param flowId identificador estable
     * @param priority prioridad configurada
     * @param cookie cookie determinista
     * @param ethernetType tipo Ethernet decimal
     * @param input conector de ingreso
     * @param output conector de salida
     * @param copyToController indica si se genera PacketIn sin interrumpir el reenvio
     * @return definicion de flujo validada
     */
    private OpenflowFlowDefinition flow(
            final String flowId,
            final int priority,
            final long cookie,
            final int ethernetType,
            final OpenflowConnectorRecord input,
            final OpenflowConnectorRecord output,
            final boolean copyToController) {
        return new OpenflowFlowDefinition(
                flowId,
                config.openflowTableId(),
                priority,
                cookie,
                ethernetType,
                input.connectorId(),
                input.name(),
                output.portNumber(),
                output.name(),
                copyToController);
    }

    /**
     * Recupera un conector previamente validado y protege el ciclo frente a una instantanea inconsistente.
     *
     * @param switchRecord conmutador que debe contener el conector
     * @param name nombre exacto del conector
     * @return conector encontrado
     * @throws IllegalStateException si el conector desaparecio del registro
     */
    private static OpenflowConnectorRecord requireConnector(
            final OpenflowSwitchRecord switchRecord, final String name) {
        final OpenflowConnectorRecord connector = switchRecord.connectorsByName().get(name);
        if (connector == null) {
            throw new IllegalStateException("Falta el conector " + name
                    + " en el registro del conmutador " + switchRecord.logicalName());
        }
        return connector;
    }
}
