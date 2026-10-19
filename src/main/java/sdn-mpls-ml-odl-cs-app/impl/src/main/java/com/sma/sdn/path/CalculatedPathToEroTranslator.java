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

package com.sma.sdn.path;

import com.sma.sdn.model.EroSubobject;
import com.sma.sdn.model.PathComputationResponse;
import com.sma.sdn.observability.StructuredLogger;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>Converts a computed path into strict IPv4 explicit-route subobjects for a PCEP
 * LSP update. Remote hop addresses retain their response order, and the destination router
 * is appended even when it also appears among those hops.</p>
 *
 * <p>The path computation workflow supplies the response; the resulting
 * {@link EroSubobject} list is subsequently used to build an update request.</p>
 */
public final class CalculatedPathToEroTranslator {
    /** <p>Records the number of translated hops and the destination router.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(CalculatedPathToEroTranslator.class);
    /**
     * <p>Builds an immutable strict ERO from the remote IPv4 addresses in a computed
     * path and the supplied destination. Missing or blank remote addresses are skipped;
     * addresses are suffixed with {@code /32} without validation or deduplication.</p>
     *
     * <p>The translation proceeds as follows:</p>
     * <ol>
     *   <li>Select nonblank remote IPv4 addresses in path-description order.</li>
     *   <li>Convert each address and the destination to strict {@code /32} subobjects.</li>
     *   <li>Log the counts and return an immutable copy of the resulting list.</li>
     * </ol>
     *
     * @param response computed path containing the ordered hop descriptions
     * @param destinationRouterId destination appended after the remote hop addresses
     * @return immutable ordered list of strict IPv4 ERO subobjects
     */
    public List<EroSubobject> translate(final PathComputationResponse response, final String destinationRouterId) {
        final List<EroSubobject> subobjects = new ArrayList<>();

        /*From the List<PathHop> we are going to perform functional programming to transform them into strict
        * EroSuboject DTOs before creating the list*/
        response.pathDescriptions()
                        .stream()
                            .filter(hop -> hop.remoteIpv4() != null && !hop.remoteIpv4().isBlank())
                            .map(hop -> strict(hop.remoteIpv4() + "/32"))
                            .forEach(subobjects::add);

        /*After creating the ERO records we add a final strict hop to the router ID of the destination which is mandatory
        * for the EROs used for updating lsps */
        subobjects.add(strict(destinationRouterId + "/32"));
        LOG.debug("calculated_path_translated_to_ero", "translate",
                "El camino calculado fue traducido a una ERO estricta",
                StructuredLogger.fields("path_description_count", response.pathDescriptions().size(),
                        "ero_subobject_count", subobjects.size(),
                        "destination_router_id", destinationRouterId));
        return List.copyOf(subobjects);
    }

    /**
     * <p>Creates an ERO subobject whose loose flag is false.</p>
     *
     * <p>The conversion consists of the following step:</p>
     * <ol>
     *   <li>Construct an {@link EroSubobject} using the supplied prefix and strict flag.</li>
     * </ol>
     *
     * @param ipPrefix prefix to place in the subobject
     * @return strict subobject containing the prefix
     */
    private static EroSubobject strict(final String ipPrefix) {
        return new EroSubobject(false, ipPrefix);
    }
}
