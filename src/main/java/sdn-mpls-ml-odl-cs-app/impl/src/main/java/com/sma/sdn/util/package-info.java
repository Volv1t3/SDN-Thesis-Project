/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

/**
 * <p>Shared utilities for secure XML parsing, XPath extraction, escaping, and
 * time-bounded retry with exponential delays and jitter.</p>
 * <p>XML serializers and deserializers use {@link com.sma.sdn.util.XmlSupport};
 * controller services use {@link com.sma.sdn.util.RetryPolicy} for polling.</p>
 */
package com.sma.sdn.util;
