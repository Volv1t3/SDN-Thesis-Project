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

package com.sma.sdn.model;

/**
 * <p>Pairs a classification with the source of the lookup. The classification service returns
 * this record to the packet workflow after either a registrar hit or an external inference.</p>
 *
 * @param classification cached or newly inferred classification
 * @param cacheHit whether the registrar supplied the result without a new inference request
 */
public record ClassificationLookupResult(ClassificationResult classification, boolean cacheHit) {
}
