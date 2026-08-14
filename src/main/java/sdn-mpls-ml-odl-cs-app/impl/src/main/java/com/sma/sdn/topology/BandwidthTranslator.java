/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.topology;

import com.sma.sdn.observability.StructuredLogger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

/**
 * Define la clase {@code BandwidthTranslator} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class BandwidthTranslator {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(BandwidthTranslator.class);
    /**
     * Ejecuta la operacion {@code BandwidthTranslator} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     */
    private BandwidthTranslator() {
    }

    /**
     * Ejecuta la operacion {@code kbpsToBytesPerSecond} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param kbps valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public static long kbpsToBytesPerSecond(final long kbps) {
        if (kbps < 0L) {
            throw new IllegalArgumentException("El ancho de banda en kbps no puede ser negativo");
        }
        final long bytesPerSecond = kbps * 1000L / 8L;
        LOG.trace("bandwidth_units_translated", "kbpsToBytesPerSecond",
                "Se convirtio el ancho de banda a bytes por segundo",
                StructuredLogger.fields("kbps", kbps, "bytes_per_second", bytesPerSecond));
        return bytesPerSecond;
    }

    /**
     * Codifica un ancho de banda en el formato binario {@code string($byte)} requerido por PCEP.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Convierte los kilobits por segundo a bytes por segundo.</li>
     *   <li>Representa el resultado como IEEE-754 float32 en orden de red.</li>
     *   <li>Codifica los cuatro bytes mediante Base64.</li>
     * </ol>
     *
     * @param kbps ancho de banda solicitado en kilobits por segundo
     * @return representacion Base64 del valor float32 de bytes por segundo
     * @throws IllegalArgumentException si el ancho de banda es negativo
     */
    public static String kbpsToPcepBandwidthBase64Float32(final long kbps) {
        final float bytesPerSecond = (float) kbpsToBytesPerSecond(kbps);
        final byte[] bytes = ByteBuffer.allocate(Float.BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .putFloat(bytesPerSecond)
                .array();
        final String encoded = Base64.getEncoder().encodeToString(bytes);
        LOG.debug("bandwidth_encoded_for_pcep", "kbpsToPcepBandwidthBase64Float32",
                "Se codifico el ancho de banda en el formato binario requerido por PCEP",
                StructuredLogger.fields("kbps", kbps, "bytes_per_second_float32", bytesPerSecond,
                        "encoded_length", encoded.length()));
        return encoded;
    }

    /**
     * Ejecuta la operacion {@code bytesPerSecondAsXmlFloat} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param bytesPerSecond valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public static String bytesPerSecondAsXmlFloat(final long bytesPerSecond) {
        return bytesPerSecond + ".0";
    }
}
