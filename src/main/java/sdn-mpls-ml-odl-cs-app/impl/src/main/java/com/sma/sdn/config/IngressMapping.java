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

package com.sma.sdn.config;

import java.util.Locale;

/**
 * <p>Immutable pair of case-insensitive substring tokens for recognizing a logical OpenFlow switch and connector.
 * A blank token acts as a wildcard for its corresponding value.</p>
 *
 * <p>{@link AppConfig#from(java.util.Map)} parses and retains one mapping for each traffic direction. The current
 * {@link AppConfig#resolveClassificationIngress(String, String)} implementation uses exact configured host-port names;
 * callers that use this record directly can apply its token-based {@link #matches(String, String)} semantics.</p>
 *
 * @param switchNameToken substring expected in a logical switch name, or blank to match every switch name
 * @param connectorNameToken substring expected in a connector name, or blank to match every connector name
 */
public record IngressMapping(
        /** <p>Case-insensitive substring used to recognize a logical switch name.</p> */
        String switchNameToken,
        /** <p>Case-insensitive substring used to recognize a connector name.</p> */
        String connectorNameToken) {
    /**
     * <p>Parses a pipe-delimited switch and connector token pair. Surrounding whitespace on both tokens is removed.</p>
     *
     * <p>The parser performs the following steps:</p>
     * <ol>
     *   <li>Split the supplied value at each {@code |} delimiter.</li>
     *   <li>Require exactly two retained fields so malformed or multiply delimited mappings are rejected.</li>
     *   <li>Trim both fields and construct the immutable mapping.</li>
     * </ol>
     *
     * <p>{@link AppConfig#from(java.util.Map)} uses this parser for both directional ingress settings.</p>
     *
     * @param value mapping text in {@code switch|connector} form
     * @return a mapping containing the two trimmed tokens
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if splitting does not produce exactly two retained fields
     */
    public static IngressMapping parse(final String value) {
        final String[] fields = value.split("\\|");
        if (fields.length != 2) {
            throw new IllegalArgumentException(
                    "El mapeo de ingreso debe utilizar el formato switch|connector: " + value);
        }
        return new IngressMapping(fields[0].trim(), fields[1].trim());
    }

    /**
     * <p>Tests whether both configured tokens occur in their corresponding candidate names without regard to case.</p>
     *
     * <p>The matcher performs the following steps:</p>
     * <ol>
     *   <li>Match {@code switchName} against {@link #switchNameToken()}.</li>
     *   <li>If the switch matches, match {@code connectorName} against {@link #connectorNameToken()}.</li>
     *   <li>Return {@code true} only when both token checks succeed.</li>
     * </ol>
     *
     * <p>Each check delegates wildcard and locale-independent comparison behavior to the private helper.</p>
     *
     * @param switchName candidate logical switch name, which may be {@code null}
     * @param connectorName candidate connector name, which may be {@code null}
     * @return {@code true} when both configured tokens match their candidates
     */
    public boolean matches(final String switchName, final String connectorName) {
        return containsIgnoreCase(switchName, switchNameToken) && containsIgnoreCase(connectorName, connectorNameToken);
    }

    /**
     * <p>Performs one wildcard-aware, case-insensitive substring comparison.</p>
     *
     * <p>The helper performs the following steps:</p>
     * <ol>
     *   <li>Treat a {@code null} or blank token as an unconditional wildcard match.</li>
     *   <li>Reject a {@code null} candidate when the token is non-blank.</li>
     *   <li>Lower-case both strings with {@link Locale#ROOT} and test substring containment.</li>
     * </ol>
     *
     * @param value candidate text, which may be {@code null}
     * @param token substring to locate, or {@code null} or blank for a wildcard
     * @return {@code true} when the token is a wildcard or occurs in the candidate without regard to case
     */
    private static boolean containsIgnoreCase(final String value, final String token) {
        if (token == null || token.isBlank()) {
            return true;
        }
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
    }
}
