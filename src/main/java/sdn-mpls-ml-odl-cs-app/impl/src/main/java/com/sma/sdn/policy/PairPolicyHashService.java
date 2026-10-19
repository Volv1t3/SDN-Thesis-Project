/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.policy;

import com.sma.sdn.model.DirectionalLspDesiredState;
import com.sma.sdn.model.DirectionalPolicyEvidence;
import com.sma.sdn.model.PairPolicyCandidate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * <p>Builds versioned canonical strings and SHA-256 hashes for policy evidence,
 * candidates, and directional desired LSP state. Matching evidence and candidate
 * policy fields share the same canonical representation.</p>
 * <p>Consensus and active-policy comparison use policy hashes; directional LSP
 * application can use the separate desired-state hash to identify state changes.</p>
 */
public final class PairPolicyHashService {
    /** <p>Version included in canonical strings to distinguish hash schemas.</p> */
    private final int hashVersion;

    /**
     * <p>Creates a hasher with the supplied canonicalization version.</p>
     * <p>Construction proceeds as follows:</p>
     * <ol><li>Store the version for use in each generated canonical string.</li></ol>
     * @param hashVersion version included in hashed values
     */
    public PairPolicyHashService(final int hashVersion) {
        this.hashVersion = hashVersion;
    }

    /**
     * <p>Hashes the policy fields of a directional observation.</p>
     * <p>Hashing proceeds as follows:</p>
     * <ol><li>Canonicalize the evidence and compute its UTF-8 SHA-256 digest.</li></ol>
     * @param evidence observation containing policy fields
     * @return lowercase hexadecimal digest
     * @throws IllegalStateException if SHA-256 is unavailable
     */
    public String hashDirectionalEvidence(final DirectionalPolicyEvidence evidence) {
        return sha256(canonicalEvidenceString(evidence));
    }

    /**
     * <p>Hashes the policy fields of a consensus candidate.</p>
     * <p>Hashing proceeds as follows:</p>
     * <ol><li>Canonicalize the candidate and compute its UTF-8 SHA-256 digest.</li></ol>
     * @param candidate selected pair policy
     * @return lowercase hexadecimal digest
     * @throws IllegalStateException if SHA-256 is unavailable
     */
    public String hashPolicyCandidate(final PairPolicyCandidate candidate) {
        return sha256(canonicalPolicyString(candidate));
    }

    /**
     * <p>Hashes the intended state of a particular directional LSP.</p>
     * <p>Hashing proceeds as follows:</p>
     * <ol><li>Join the version, pair and LSP identifiers, bandwidth, priorities and
     * ERO fingerprint in a fixed order, then compute the SHA-256 digest.</li></ol>
     * @param state desired directional LSP state
     * @return lowercase hexadecimal digest
     * @throws IllegalStateException if SHA-256 is unavailable
     */
    public String hashDesiredLspState(final DirectionalLspDesiredState state) {
        return sha256("hash_version=" + hashVersion
                + "|pair_key=" + state.pairKey()
                + "|direction_key=" + state.directionKey()
                + "|pcc_node=" + state.pccNode()
                + "|lsp_name=" + state.lspName()
                + "|plsp_id=" + state.plspId()
                + "|tunnel_id=" + state.tunnelId()
                + "|bandwidth=" + state.requestedBandwidthBase64()
                + "|setup=" + state.setupPriority()
                + "|hold=" + state.holdPriority()
                + "|ero=" + state.desiredEroFingerprint());
    }

    /**
     * <p>Produces the shared policy representation for directional evidence.</p>
     * <p>Construction proceeds as follows:</p>
     * <ol><li>Pass the observation's policy fields and normalized service key to the common formatter.</li></ol>
     * @param evidence observation to represent
     * @return versioned canonical policy string
     */
    public String canonicalEvidenceString(final DirectionalPolicyEvidence evidence) {
        return canonicalPolicy(evidence.pairKey(), evidence.serviceKey().normalizedValue(), evidence.className(),
                evidence.profileName(), evidence.dscp(), evidence.mplsTc(), evidence.requestedBandwidthKbps(),
                evidence.requestedBandwidthBase64(), evidence.setupPriority(), evidence.holdPriority(),
                evidence.policySchemaVersion());
    }

    /**
     * <p>Produces the shared policy representation for a candidate.</p>
     * <p>Construction proceeds as follows:</p>
     * <ol><li>Pass candidate policy fields and normalized service key to the common formatter.</li></ol>
     * @param candidate selected policy to represent
     * @return versioned canonical policy string
     */
    public String canonicalPolicyString(final PairPolicyCandidate candidate) {
        return canonicalPolicy(candidate.pairKey(), candidate.serviceKey().normalizedValue(), candidate.className(),
                candidate.profileName(), candidate.dscp(), candidate.mplsTc(), candidate.requestedBandwidthKbps(),
                candidate.requestedBandwidthBase64(), candidate.setupPriority(), candidate.holdPriority(),
                candidate.policySchemaVersion());
    }

    /**
     * <p>Formats policy identity and attributes in a fixed, versioned order.</p>
     * <p>Construction proceeds as follows:</p>
     * <ol><li>Concatenate named fields separated by vertical bars without escaping field values.</li></ol>
     * @param pairKey managed pair identity
     * @param serviceKey normalized service identity
     * @param className classification name
     * @param profileName policy profile name
     * @param dscp requested DSCP value
     * @param mplsTc requested MPLS traffic class
     * @param bandwidth requested bandwidth in kilobits per second
     * @param bandwidthBase64 encoded bandwidth representation
     * @param setup setup priority
     * @param hold hold priority
     * @param schemaVersion policy schema version
     * @return canonical policy representation
     */
    private String canonicalPolicy(final String pairKey, final String serviceKey, final String className,
            final String profileName, final int dscp, final int mplsTc, final int bandwidth, final String bandwidthBase64,
            final int setup, final int hold, final String schemaVersion) {
        return "hash_version=" + hashVersion + "|pair_key=" + pairKey + "|service_key=" + serviceKey
                + "|class_name=" + className + "|profile_name=" + profileName + "|dscp=" + dscp
                + "|mpls_tc=" + mplsTc + "|requested_bandwidth_kbps=" + bandwidth
                + "|requested_bandwidth_base64=" + bandwidthBase64 + "|setup_priority=" + setup
                + "|hold_priority=" + hold + "|policy_schema_version=" + schemaVersion;
    }

    /**
     * <p>Computes a lowercase hexadecimal SHA-256 digest of a UTF-8 string.</p>
     * <p>Digest computation proceeds as follows:</p>
     * <ol><li>Obtain the SHA-256 implementation, digest the UTF-8 bytes and format them as hex.</li>
     * <li>Wrap an unavailable algorithm as an illegal state.</li></ol>
     * @param value string to digest
     * @return lowercase hexadecimal SHA-256 digest
     * @throws IllegalStateException if SHA-256 is unavailable
     */
    private static String sha256(final String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
