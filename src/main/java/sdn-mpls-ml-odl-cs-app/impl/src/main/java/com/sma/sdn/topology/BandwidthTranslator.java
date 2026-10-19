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
 * <p>Converts application bandwidth values into the byte-rate representations used by controller path computations
 * and PCEP LSP updates. The PCEP representation encodes a network-order IEEE-754 float in Base64.</p>
 */
public final class BandwidthTranslator {
    /** <p>Logger for bandwidth conversion and encoding.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(BandwidthTranslator.class);
    /**
     * <p>Prevents instantiation of this static conversion utility.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Leave construction inaccessible to callers.</li>
     * </ol>
     */
    private BandwidthTranslator() {
    }

    /**
     * <p>Convert decimal kilobits per second to integral bytes per second.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Reject negative input.</li>
     * <li>Multiply by 1000, divide by eight using integer arithmetic, log, and return the result. The
     * multiplication is not overflow-checked.</li>
     * </ol>
     *
     * @param kbps bandwidth in decimal kilobits per second
     * @return bytes per second after integer conversion
     * @throws IllegalArgumentException if the bandwidth is negative
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
     * <p>Encode bandwidth as the Base64 network-order float32 byte rate used by PCEP.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Convert kilobits to integral bytes per second.</li>
     *   <li>Cast the result to float and write four bytes in big-endian order.</li>
     *   <li>Base64-encode the bytes, log, and return the string.</li>
     * </ol>
     *
     * @param kbps bandwidth in decimal kilobits per second
     * @return Base64-encoded float32 bytes-per-second value
     * @throws IllegalArgumentException if the bandwidth is negative
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
     * <p>Format an integer byte rate with a decimal suffix for XML.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Append {@code .0} to the supplied integer and return it.</li>
     * </ol>
     *
     * @param bytesPerSecond byte rate to format
     * @return decimal string with a fractional zero
     */
    public static String bytesPerSecondAsXmlFloat(final long bytesPerSecond) {
        return bytesPerSecond + ".0";
    }
}
