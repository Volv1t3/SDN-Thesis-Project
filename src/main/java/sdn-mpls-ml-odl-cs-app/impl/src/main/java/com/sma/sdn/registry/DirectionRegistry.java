/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.registry;

import com.sma.sdn.config.AppConfig;
import com.sma.sdn.model.FlowDirection;
import com.sma.sdn.model.PacketClassificationContext;
import com.sma.sdn.model.TunnelDirection;
import com.sma.sdn.model.TunnelUpdateScope;
import com.sma.sdn.observability.StructuredLogger;
import java.util.List;

/**
 * Define la clase {@code DirectionRegistry} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class DirectionRegistry {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(DirectionRegistry.class);
    private final AppConfig config;

    /**
     * Ejecuta la operacion {@code DirectionRegistry} dentro del componente correspondiente.
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
    public DirectionRegistry(final AppConfig config) {
        this.config = config;
    }

    /**
     * Ejecuta la operacion {@code resolve} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param context valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    public FlowDirection resolve(final PacketClassificationContext context) {
        final FlowDirection resolved;
        if (context.direction() != FlowDirection.UNKNOWN) {
            resolved = context.direction();
        } else {
            resolved = config.resolveClassificationIngress(
                    context.ingressSwitchName(), context.ingressConnectorName());
        }
        LOG.debug("flow_direction_resolved", "resolve",
                "Se resolvio la direccion logica del flujo",
                StructuredLogger.fields("ingress_switch", context.ingressSwitchName(),
                        "ingress_connector", context.ingressConnectorName(), "direction", resolved));
        return resolved;
    }

    /**
     * Ejecuta la operacion {@code requireTunnelDirection} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param direction valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    public TunnelDirection requireTunnelDirection(final FlowDirection direction) {
        return switch (direction) {
            case HEADEND_TO_TAILEND -> config.headendToTailend();
            case TAILEND_TO_HEADEND -> config.tailendToHeadend();
            case UNKNOWN -> throw new IllegalStateException(
                    "No fue posible resolver la direccion del tunel desde la notificacion PacketReceived");
        };
    }

    /**
     * Devuelve las dos direcciones de LSP que deben recalcularse para una clasificacion de ingreso autorizada.
     * La direccion observada se conserva en primer lugar para reducir la latencia del trafico que activo el flujo,
     * seguida por la direccion opuesta con su PCC, PLSP ID y destino propios.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Resuelve la direccion asociada al conector de ingreso.</li>
     *   <li>Selecciona la direccion inversa configurada.</li>
     *   <li>Devuelve ambas direcciones en un orden determinista.</li>
     * </ol>
     *
     * @param ingressDirection direccion resuelta desde el PacketIn
     * @return direcciones de ida y retorno que deben procesarse
     * @throws IllegalStateException si la direccion de ingreso es desconocida
     */
    public List<TunnelDirection> requireBidirectionalTunnelDirections(final FlowDirection ingressDirection) {
        return switch (ingressDirection) {
            case HEADEND_TO_TAILEND -> List.of(config.headendToTailend(), config.tailendToHeadend());
            case TAILEND_TO_HEADEND -> List.of(config.tailendToHeadend(), config.headendToTailend());
            case UNKNOWN -> throw new IllegalStateException(
                    "No fue posible resolver las direcciones de tunel desde el PacketReceived");
        };
    }

    /** Resuelve las direcciones permitidas por el alcance configurado para el PacketIn. */
    public List<TunnelDirection> requireTunnelDirectionsForScope(
            final FlowDirection ingressDirection, final TunnelUpdateScope updateScope) {
        return switch (updateScope) {
            case OBSERVED_DIRECTION -> List.of(requireTunnelDirection(ingressDirection));
            case BIDIRECTIONAL_PAIR -> requireBidirectionalTunnelDirections(ingressDirection);
        };
    }
}
