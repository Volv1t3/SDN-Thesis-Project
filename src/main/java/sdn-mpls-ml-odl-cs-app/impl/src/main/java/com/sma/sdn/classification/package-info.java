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

/**
 * <p>Resolves packet classifications for the controller workflow through a local
 * registry and an external inference API. The package's {@link
 * com.sma.sdn.classification.ClassificationService} first reuses an unexpired
 * {@link com.sma.sdn.registry.ClassificationRegistrar} entry and otherwise
 * exchanges JSON with the classifier through {@link
 * com.sma.sdn.http.ClassifierRestClient}.</p>
 *
 * <p>{@link com.sma.sdn.SdnMplsMlWorkflowService} consumes the classification
 * and its traffic policy when assembling directional evidence for pair-policy
 * coordination. Transport, JSON conversion, cache storage, and route application
 * remain in their respective packages.</p>
 */
package com.sma.sdn.classification;
