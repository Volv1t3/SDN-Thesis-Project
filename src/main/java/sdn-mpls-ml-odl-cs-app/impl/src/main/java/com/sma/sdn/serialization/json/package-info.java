/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

/**
 * <p>Provides the JSON boundary between controller domain objects and the external classifier API.
 * {@link com.sma.sdn.serialization.json.ClassificationRequestJsonSerializer} emits the nested packet-feature request,
 * while {@link com.sma.sdn.serialization.json.ClassificationResponseJsonDeserializer} validates required response
 * fields and constructs classification, traffic-policy, and path-constraint records.</p>
 *
 * <p>{@link com.sma.sdn.classification.ClassificationService} coordinates both converters around
 * {@link com.sma.sdn.http.ClassifierRestClient}. The request converter relies on already validated packet features;
 * the response converter assigns local cache timestamps and expiry but does not perform HTTP status handling or cache
 * storage. Required response scalar checks establish presence and type, not policy ranges or probability
 * normalization; optional fields are converted using Jackson coercion and defaults.</p>
 */
package com.sma.sdn.serialization.json;
