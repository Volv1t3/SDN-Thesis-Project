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
import com.sma.sdn.model.TunnelDirection;
import com.sma.sdn.model.TunnelPairDefinition;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * <p>Defines the single managed tunnel pair and maps either configured direction to its
 * stable pair identity. Its endpoint router identifiers and directions come from
 * {@link AppConfig}, while the pair key and ingress switch names are fixed here.</p>
 * <p>Directional evidence uses the switch names to identify a pair side, and policy
 * coordination and operational state use the same immutable definition.</p>
 */
public final class TunnelPairRegistry {
    /** <p>The immutable definition of the application's managed tunnel pair.</p> */
    private final TunnelPairDefinition pair;

    /**
     * <p>Constructs the managed pair from the configured endpoint directions and router IDs.</p>
     * <p>The construction proceeds as follows:</p>
     * <ol>
     *   <li>Rejects a null configuration.</li>
     *   <li>Creates the fixed {@code lsr1_lsr4} pair with switch names {@code ECHO} and
     *       {@code FOXTROT} and the configured endpoint information.</li>
     * </ol>
     * @param config source of endpoint identifiers and tunnel directions
     * @throws NullPointerException if config is null
     */
    public TunnelPairRegistry(final AppConfig config) {
        Objects.requireNonNull(config, "config");
        /*Within our topology there is a single pair that would need to be parsed, hence why it is created like this. This is
        * another one of those areas where I think we could improve with further generalization.*/
        this.pair = new TunnelPairDefinition(
                "lsr1_lsr4", config.headendToTailend(), config.tailendToHeadend(),
                config.headend().routerId(), config.tailend().routerId(), "ECHO", "FOXTROT");
    }

    /**
     * <p>Finds the managed pair containing the supplied direction key.</p>
     * <p>The lookup proceeds as follows:</p>
     * <ol>
     *   <li>Compares the key against the forward and reverse direction keys.</li>
     *   <li>Returns the pair on a match or rejects an unmanaged key.</li>
     * </ol>
     * @param directionKey configured direction identifier
     * @return the managed pair containing the direction
     * @throws IllegalArgumentException if the key does not identify a managed direction
     */
    public TunnelPairDefinition requirePairForDirection(final String directionKey) {
        /*helper that can allow the system to return the corresponding TunnnelPairDefinition which would contain all
        * of the metadata to the tunnel pair configuration in the topology if the directionKey is known. Since we know that the direction keys areknown all the times
        * and the network is specialized we can know that this method will work*/
        if (pair.forwardDirection().directionKey().equals(directionKey)
                || pair.reverseDirection().directionKey().equals(directionKey)) {
            return pair;
        }
        throw new IllegalArgumentException("Direccion no administrada por ningun par: " + directionKey);
    }

    /**
     * <p>Returns the forward and reverse directions belonging to the requested pair.</p>
     * <p>The lookup proceeds as follows:</p>
     * <ol>
     *   <li>Validates the pair key against the managed definition.</li>
     *   <li>Returns an immutable list with the forward direction first.</li>
     * </ol>
     * @param pairKey managed pair identifier
     * @return the ordered forward and reverse directions
     * @throws IllegalArgumentException if the pair is not managed
     */
    public List<TunnelDirection> requireManagedDirections(final String pairKey) {
        requirePair(pairKey);
        /*This mechanism validates that the pair key that s associated something like lsr1_lsr4 matches the expected
        * keys that the system knows about, if it does not match then it would be impossible to continue with classification
        * or as a matter of fact the process */
        return List.of(pair.forwardDirection(), pair.reverseDirection());
    }

    /**
     * <p>Returns the immutable managed pair definition for operational exposure.</p>
     * <p>The operation proceeds as follows:</p>
     * <ol><li>Returns the definition created during construction.</li></ol>
     * @return the managed pair definition
     */
    public TunnelPairDefinition snapshot() {
        return pair;
    }

    /**
     * <p>Maps an ingress switch to the left or right side of the managed pair.</p>
     * <p>The lookup proceeds as follows:</p>
     * <ol>
     *   <li>Validates the pair key, then uppercases the switch name with the root locale;
     *       a null name is treated as an empty name.</li>
     *   <li>Returns {@code LEFT} or {@code RIGHT} for the configured switch names,
     *       or rejects a name belonging to neither side.</li>
     * </ol>
     * <p>{@link DirectionalClassificationEvidenceRegistry} uses this result to select
     * the evidence slot.</p>
     * @param pairKey managed pair identifier
     * @param ingressSwitchName switch name to classify
     * @return {@code LEFT} or {@code RIGHT}
     * @throws IllegalArgumentException if the pair or switch is not managed
     */
    public String sideForSwitch(final String pairKey, final String ingressSwitchName) {
        requirePair(pairKey);

        /*What this is doing is trying to understand the side that the packet is coming from, specifially which of the
        * switches on either side of the core handled it. This is used for the evidence registration on the Evidence
        * Registry for classification. Notice how it validates that the ingress name if not null then is uppper cased since the
        * switches name would be */
        final String switchName = ingressSwitchName == null ? "" : ingressSwitchName.toUpperCase(Locale.ROOT);
        if (pair.leftSwitchName().equals(switchName)) {
            return "LEFT";
        }
        if (pair.rightSwitchName().equals(switchName)) {
            return "RIGHT";
        }
        throw new IllegalArgumentException("Switch no pertenece al par " + pairKey + ": " + ingressSwitchName);
    }

    /**
     * <p>Rejects a key that does not identify the managed pair.</p>
     * <p>The validation proceeds as follows:</p>
     * <ol><li>Compares the supplied key with the stored pair key and throws on mismatch.</li></ol>
     * @param pairKey pair identifier to validate
     * @throws IllegalArgumentException if the key is not managed
     */
    private void requirePair(final String pairKey) {
        /*This is a gate that would cause the process to throw, all the way up the chain onto the Workflow Service and then up to the
        * Provider for the module and finally into the lgos assa ERROR but will not kill the application. This is used here to gate
        * the pairKey passed in, or the side tht it belongs to depending on the ingress swiwtch name all of which are methods
        * that accept external input and so validate taht the key pair even exists before performing their operation.*/
        if (!pair.pairKey().equals(pairKey)) {
            throw new IllegalArgumentException("Par no administrado: " + pairKey);
        }
    }
}
