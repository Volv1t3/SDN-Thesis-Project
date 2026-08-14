/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

/**
 * Contiene el punto de entrada OSGi Blueprint del bundle de OpenDaylight.
 *
 * <p>Pasos:
 * <ol>
 *   <li>Agrupa clases con una responsabilidad comun dentro del controlador.</li>
 *   <li>Expone contratos internos usados por los servicios de clasificacion, topologia, ruta o tunel.</li>
 *   <li>Separa la documentacion por dominio para facilitar mantenimiento y auditoria.</li>
 * </ol>
 */
package com.sma.sdn.impl;
