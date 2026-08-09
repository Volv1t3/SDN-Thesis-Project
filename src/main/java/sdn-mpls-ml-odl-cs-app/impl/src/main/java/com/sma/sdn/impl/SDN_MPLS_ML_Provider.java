/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.impl;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.opendaylight.mdsal.binding.api.NotificationService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main lifecycle entry point for the SDN-MPLS-ML OpenDaylight controller-side
 * application.
 *
 * <p>This provider is created and destroyed by the OSGi Blueprint container
 * when the Karaf feature is installed or removed.
 *
 * <p>Current milestone validates the controller-side notification ingestion path
 * by registering an MD-SAL listener for OpenFlow {@code PacketReceived}
 * notifications and logging immediate observable fields.
 */
public final class SDN_MPLS_ML_Provider
        implements AutoCloseable, NotificationService.Listener<PacketReceived> {
    private static final Logger LOG = LoggerFactory.getLogger(SDN_MPLS_ML_Provider.class);

    private final NotificationService notificationService;
    private final AtomicLong packetInCounter = new AtomicLong();
    private Registration packetInRegistration;

    public SDN_MPLS_ML_Provider(final NotificationService notificationService) {
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
    }

    /**
     * Called by Blueprint when the application bundle is activated.
     */
    public void init() {
        packetInRegistration = notificationService.registerListener(PacketReceived.class, this);
        LOG.info("SDN-MPLS-ML PacketReceived listener registered");
    }

    @Override
    public void onNotification(final PacketReceived notification) {
        if (notification == null) {
            LOG.warn("Received null PacketReceived notification");
            return;
        }

        try {
            final long packetNumber = packetInCounter.incrementAndGet();
            final byte[] payload = notification.getPayload();
            final int payloadLength = payload == null ? 0 : payload.length;

            LOG.info(
                    "PacketReceived notification: ingress={}, payloadLength={}, reason={}, tableId={}, flowCookie={}",
                    notification.getIngress(),
                    payloadLength,
                    notification.getPacketInReason(),
                    notification.getTableId(),
                    notification.getFlowCookie()
            );
            LOG.debug("PacketReceived sequence={}", packetNumber);
            LOG.debug("PacketReceived match: {}", notification.getMatch());
            LOG.debug("PacketReceived payload preview: {}", payloadPreview(payload, 64));
            LOG.trace("PacketReceived raw notification: {}", notification);
        } catch (RuntimeException e) {
            LOG.warn("Failed while logging PacketReceived notification", e);
        }
    }

    /**
     * Called by Blueprint when the application bundle is stopped or removed.
     */
    @Override
    public void close() {
        if (packetInRegistration != null) {
            packetInRegistration.close();
            packetInRegistration = null;
        }

        LOG.info("SDN-MPLS-ML PacketReceived listener unregistered");
    }

    private static String payloadPreview(final byte[] payload, final int maxBytes) {
        if (payload == null || payload.length == 0) {
            return "<empty>";
        }

        final int limit = Math.min(payload.length, Math.max(maxBytes, 0));
        final StringBuilder builder = new StringBuilder(limit * 3);

        for (int index = 0; index < limit; index++) {
            if (index > 0) {
                builder.append(' ');
            }

            appendHexByte(builder, payload[index] & 0xff);
        }

        if (payload.length > limit) {
            builder.append(" ...");
        }

        return builder.toString();
    }

    private static void appendHexByte(final StringBuilder builder, final int value) {
        final char[] hex = "0123456789abcdef".toCharArray();
        builder.append(hex[(value >>> 4) & 0x0f]);
        builder.append(hex[value & 0x0f]);
    }
}
