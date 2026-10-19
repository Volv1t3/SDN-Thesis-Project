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

package com.sma.sdn.registry;

import com.sma.sdn.config.AppConfig;
import com.sma.sdn.model.FlowDirection;
import com.sma.sdn.model.PacketClassificationContext;
import com.sma.sdn.model.TunnelDirection;
import com.sma.sdn.model.TunnelUpdateScope;
import com.sma.sdn.observability.StructuredLogger;
import java.util.List;

/**
 * <p>Resolves packet ingress into a logical flow direction and maps that direction
 * to the configured tunnel direction or ordered pair of directions.</p>
 * <p>The packet workflow uses this registry alongside {@link AppConfig}; the requested
 * {@link TunnelUpdateScope} determines whether only the observed direction or both
 * configured directions are returned.</p>
 */
public final class DirectionRegistry {
    /** <p>Structured logger for resolved packet directions.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(DirectionRegistry.class);
    /** <p>Configuration supplying ingress mappings and tunnel directions.</p> */
    private final AppConfig config;

    /**
     * <p>Creates a resolver backed by the supplied application configuration.</p>
     * <p>Construction proceeds as follows:</p>
     * <ol>
     *   <li>Stores the configuration for ingress and tunnel direction lookups.</li>
     * </ol>
     * @param config configuration supplying mappings and directions
     */
    public DirectionRegistry(final AppConfig config) {
        this.config = config;
    }

    /**
     * <p>Resolves the logical direction of a packet, preferring a direction already
     * supplied by its classification context.</p>
     * <p>The lookup proceeds as follows:</p>
     * <ol>
     *   <li>Uses the context direction unless it is {@code UNKNOWN}; otherwise asks
     *       {@link AppConfig} to resolve the ingress switch and connector.</li>
     *   <li>Logs and returns the resulting direction, which may remain unknown.</li>
     * </ol>
     * @param context packet ingress identity and optional resolved direction
     * @return resolved logical flow direction
     * @throws NullPointerException if context or the required configuration is null
     */
    public FlowDirection resolve(final PacketClassificationContext context) {
        /*This is attempting to extract the resolved FlowDirection ENUM value that would identify the
        * packet in notification's flow, either from HEADEND_TO_TAILEND or TAILEND_TO_HEADEND, in this case these two are universal
        * for packets that arrive at say the switch to host connector on the left side (which we would consdier the head end) will always
        * map to the HEADEND to TAILEND direction whereas those that arrive from the right's connector willl always be considered to be TAILEND_TO_HEADEND.
        *
        * The reason this logic is the same for any direction or if the flow is started upon the second host is that in the end, the conversation
        * that might be initiated between two hosts will result in both directions being seen and both being classified and the policy pair can indeed form
        * the corresponding correction packet and push lsp updates. If we say for example had only one side, then we could not progress on the classificaiton
        * as the appliation assumes that the network will be able to do this consnensu based on two readings and classfications*/
        final FlowDirection resolved;

        //If the context is not UNKNOWN then we are going to use the direction that was provided one in the context for
        // classification. If not we attempt to resolve the direction on our own using the configuration of the application.,
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
     * <p>Maps a known logical direction to its configured tunnel direction.</p>
     * <p>The selection proceeds as follows:</p>
     * <ol>
     *   <li>Selects the corresponding forward or reverse direction from configuration,
     *       or rejects an unknown direction.</li>
     * </ol>
     * @param direction logical direction to map
     * @return configured tunnel direction
     * @throws IllegalStateException if direction is unknown
     * @throws NullPointerException if direction or the required configuration is null
     */
    public TunnelDirection requireTunnelDirection(final FlowDirection direction) {
        return switch (direction) {
            /*
            * In this case, this implementation is performing a mapping from a corresponding ENUM to the complete
            * TunnelDirection object whcih associates a direction key (lsr1_to_lsr4) or (lsr4_to_lsr1) alongside the
            * Source and destionation TunnelDirection records that create DTOs for the logical name, router ID and pcc node.
            * This is important because the direction key is used to identify the direction in the policy pair and the
            * source and destination are used to identify the endpoints of the tunnel that will be created by the PCEP client.
            */
            case HEADEND_TO_TAILEND -> config.headendToTailend();
            case TAILEND_TO_HEADEND -> config.tailendToHeadend();
            case UNKNOWN -> throw new IllegalStateException(
                    "No fue posible resolver la direccion del tunel desde la notificacion PacketReceived");
        };
    }

    /**
     * <p>Returns both configured tunnel directions, with the observed direction first.</p>
     * <p>The selection proceeds as follows:</p>
     * <ol>
     *   <li>Selects the observed and opposite direction in that order, or rejects
     *       an unknown ingress direction.</li>
     * </ol>
     * @param ingressDirection logical direction resolved from the packet
     * @return immutable list containing observed then opposite tunnel direction
     * @throws IllegalStateException if the ingress direction is unknown
     * @throws NullPointerException if ingressDirection or the required configuration is null
     */
    public List<TunnelDirection> requireBidirectionalTunnelDirections(final FlowDirection ingressDirection) {
        /*Helper method to create a list of the corresponding TunnelDirection records for either pairs depending on
        * the direction of the incoming packet, so for example for a lsr1 to lsr4 paacket it would return the headend and tailend
        * ordered, vs on the reverse it would put first the tail end to head end and then the reverse*/
        return switch (ingressDirection) {
            case HEADEND_TO_TAILEND -> List.of(config.headendToTailend(), config.tailendToHeadend());
            case TAILEND_TO_HEADEND -> List.of(config.tailendToHeadend(), config.headendToTailend());
            case UNKNOWN -> throw new IllegalStateException(
                    "No fue posible resolver las direcciones de tunel desde el PacketReceived");
        };
    }

    /**
     * <p>Selects the tunnel directions allowed by the requested update scope.</p>
     * <p>The selection proceeds as follows:</p>
     * <ol>
     *   <li>For {@code OBSERVED_DIRECTION}, resolves and returns only the ingress direction.</li>
     *   <li>For {@code BIDIRECTIONAL_PAIR}, returns both directions with the observed one first.</li>
     * </ol>
     * @param ingressDirection logical direction resolved from the packet
     * @param updateScope requested direction selection
     * @return immutable list of selected tunnel directions
     * @throws IllegalStateException if the ingress direction is unknown
     * @throws NullPointerException if an argument or the required configuration is null
     */
    public List<TunnelDirection> requireTunnelDirectionsForScope(
            final FlowDirection ingressDirection, final TunnelUpdateScope updateScope) {
        /*This helper is used to transform what is known as a update scope, which is proladed from config that determines
        * whether or not the update from a corresponding packet in notification will only affect that side or both. In the case
        * for BIDIRECIONTAL UPDATE we still attempt to figure out a preemptive policy based on consensus, so we always apply the same
        * policy, but this policy would be applied independently, requiring further processing for the response traffic to figure out if the policy is the
        * same for either side.*/
        return switch (updateScope) {
            case OBSERVED_DIRECTION -> List.of(requireTunnelDirection(ingressDirection));
            case BIDIRECTIONAL_PAIR -> requireBidirectionalTunnelDirections(ingressDirection);
        };
    }
}
