/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.model;

import java.time.Instant;
import java.util.Map;

/**
 * <p>Holds a classifier response, its traffic policy, and the local cache lifetime. The response
 * deserializer constructs this record; the classification registrar retains it for reuse and the
 * packet workflow consumes its policy when creating directional evidence.</p>
 *
 * @param requestId request identifier returned by the classifier
 * @param modelName name of the model that produced the result
 * @param classId numeric identifier of the selected class
 * @param className name of the selected class
 * @param confidence confidence reported for the selected class
 * @param probabilities classifier-reported class probabilities
 * @param policy traffic policy associated with the classification
 * @param processingTimeMs classifier-reported processing duration in milliseconds
 * @param cachedAt time assigned when the response was converted for caching
 * @param expiresAt time after which the registrar considers the result expired
 */
public record ClassificationResult(
        String requestId, /*Request ID associated with the ML API, this is assigned by it*/
        String modelName, /*Model that performed the classification*/
        int classId, /*0-6 classification classes ID*/
        String className, /*string repreentatio normalized to the model's expectd outputs*/
        double confidence, /*confidence of the current classification class*/
        Map<String, Double> probabilities, /*probabilities associated with all other classes and should add up in total to 1*/
        TrafficPolicy policy, /*Traffic policy returned by the classifer and translated to a record in the CSA. This contains the
        mpls_tc, dspc code, bandwidth, hold and setup priorities*/
        double processingTimeMs, /*Reported processing time form the API*/
        Instant cachedAt,
        Instant expiresAt) {
}
