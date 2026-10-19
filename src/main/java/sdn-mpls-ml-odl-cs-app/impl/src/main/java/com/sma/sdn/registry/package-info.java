/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

/**
 * <p>Provides in-memory registries for topology, classification, tunnel state and
 * pair-policy coordination. Individual registries define their own expiration,
 * synchronization and snapshot behavior.</p>
 * <p>Services populate and query these stores while processing packets and control-plane
 * updates; operational-state publication consumes selected snapshots and counts.</p>
 */
package com.sma.sdn.registry;
