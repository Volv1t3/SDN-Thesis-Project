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

/** Creates stable hashes from canonical readable policy and desired LSP state fields. */
public final class PairPolicyHashService {
    private final int hashVersion;

    public PairPolicyHashService(final int hashVersion) {
        this.hashVersion = hashVersion;
    }

    public String hashDirectionalEvidence(final DirectionalPolicyEvidence evidence) {
        return sha256(canonicalEvidenceString(evidence));
    }

    public String hashPolicyCandidate(final PairPolicyCandidate candidate) {
        return sha256(canonicalPolicyString(candidate));
    }

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

    public String canonicalEvidenceString(final DirectionalPolicyEvidence evidence) {
        return canonicalPolicy(evidence.pairKey(), evidence.serviceKey().normalizedValue(), evidence.className(),
                evidence.profileName(), evidence.dscp(), evidence.mplsTc(), evidence.requestedBandwidthKbps(),
                evidence.requestedBandwidthBase64(), evidence.setupPriority(), evidence.holdPriority(),
                evidence.policySchemaVersion());
    }

    public String canonicalPolicyString(final PairPolicyCandidate candidate) {
        return canonicalPolicy(candidate.pairKey(), candidate.serviceKey().normalizedValue(), candidate.className(),
                candidate.profileName(), candidate.dscp(), candidate.mplsTc(), candidate.requestedBandwidthKbps(),
                candidate.requestedBandwidthBase64(), candidate.setupPriority(), candidate.holdPriority(),
                candidate.policySchemaVersion());
    }

    private String canonicalPolicy(final String pairKey, final String serviceKey, final String className,
            final String profileName, final int dscp, final int mplsTc, final int bandwidth, final String bandwidthBase64,
            final int setup, final int hold, final String schemaVersion) {
        return "hash_version=" + hashVersion + "|pair_key=" + pairKey + "|service_key=" + serviceKey
                + "|class_name=" + className + "|profile_name=" + profileName + "|dscp=" + dscp
                + "|mpls_tc=" + mplsTc + "|requested_bandwidth_kbps=" + bandwidth
                + "|requested_bandwidth_base64=" + bandwidthBase64 + "|setup_priority=" + setup
                + "|hold_priority=" + hold + "|policy_schema_version=" + schemaVersion;
    }

    private static String sha256(final String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
