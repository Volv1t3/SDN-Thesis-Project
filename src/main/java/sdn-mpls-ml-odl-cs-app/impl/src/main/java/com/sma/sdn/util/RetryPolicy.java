/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.util;

import com.sma.sdn.observability.StructuredLogger;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Define la clase {@code RetryPolicy} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class RetryPolicy {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(RetryPolicy.class);
    private final long initialDelayMs;
    private final long maxDelayMs;
    private final Duration timeout;
    private final int jitterPercent;

    /**
     * Ejecuta la operacion {@code RetryPolicy} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param initialDelayMs valor requerido para ejecutar esta operacion
     *
     * @param maxDelayMs valor requerido para ejecutar esta operacion
     *
     * @param timeout valor requerido para ejecutar esta operacion
     */
    public RetryPolicy(final long initialDelayMs, final long maxDelayMs, final Duration timeout) {
        this(initialDelayMs, maxDelayMs, timeout, 20);
    }

    /**
     * Ejecuta la operacion {@code RetryPolicy} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param initialDelayMs valor requerido para ejecutar esta operacion
     *
     * @param maxDelayMs valor requerido para ejecutar esta operacion
     *
     * @param timeout valor requerido para ejecutar esta operacion
     *
     * @param jitterPercent valor requerido para ejecutar esta operacion
     */
    public RetryPolicy(
            final long initialDelayMs,
            final long maxDelayMs,
            final Duration timeout,
            final int jitterPercent) {
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.timeout = timeout;
        this.jitterPercent = jitterPercent;
    }

    /**
     * Ejecuta la operacion {@code retryUntilPresent} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param supplier valor requerido para ejecutar esta operacion
     *
     * @param failureMessage valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public <T> T retryUntilPresent(final Supplier<Optional<T>> supplier, final String failureMessage) {
        final Instant deadline = Instant.now().plus(timeout);
        long delay = initialDelayMs;
        int attempt = 0;
        RuntimeException lastFailure = null;
        while (Instant.now().isBefore(deadline)) {
            attempt++;
            try {
                final Optional<T> value = supplier.get();
                if (value.isPresent()) {
                    LOG.debug("retry_policy_succeeded", "retryUntilPresent",
                            "La operacion con reintentos finalizo correctamente",
                            StructuredLogger.fields("attempt", attempt, "timeout_ms", timeout.toMillis()));
                    return value.orElseThrow();
                }
            } catch (RuntimeException e) {
                lastFailure = e;
                LOG.warn("retry_policy_attempt_failed", "retryUntilPresent",
                        "Un intento de la operacion fallo y sera reintentado",
                        StructuredLogger.fields("attempt", attempt, "next_delay_base_ms", delay), e);
            }
            final long effectiveDelay = jitter(delay, jitterPercent);
            LOG.trace("retry_policy_waiting", "retryUntilPresent",
                    "La politica espera antes del siguiente intento",
                    StructuredLogger.fields("attempt", attempt, "delay_ms", effectiveDelay));
            sleep(effectiveDelay);
            delay = Math.min(maxDelayMs, delay * 2L);
        }
        LOG.error("retry_policy_exhausted", "retryUntilPresent",
                "La operacion agoto su periodo de reintentos",
                StructuredLogger.fields("attempt_count", attempt, "timeout_ms", timeout.toMillis()), lastFailure);
        if (lastFailure != null) {
            throw new IllegalStateException(failureMessage, lastFailure);
        }
        throw new IllegalStateException(failureMessage);
    }

    /**
     * Ejecuta la operacion {@code retryUntilTrue} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param supplier valor requerido para ejecutar esta operacion
     *
     * @param failureMessage valor requerido para ejecutar esta operacion
     */
    public void retryUntilTrue(final Supplier<Boolean> supplier, final String failureMessage) {
        retryUntilPresent(() -> supplier.get() ? Optional.of(Boolean.TRUE) : Optional.empty(), failureMessage);
    }

    /**
     * Ejecuta la operacion {@code jitter} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param delayMs valor requerido para ejecutar esta operacion
     *
     * @param jitterPercent valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public static long jitter(final long delayMs, final int jitterPercent) {
        final double jitter = Math.max(0, jitterPercent) / 100.0d;
        final double multiplier = ThreadLocalRandom.current().nextDouble(1.0d - jitter, 1.0d + jitter);
        return Math.max(1L, Math.round(delayMs * multiplier));
    }

    /**
     * Ejecuta la operacion {@code sleep} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param delayMs valor requerido para ejecutar esta operacion
     */
    private static void sleep(final long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("La espera entre reintentos fue interrumpida", e);
        }
    }
}
