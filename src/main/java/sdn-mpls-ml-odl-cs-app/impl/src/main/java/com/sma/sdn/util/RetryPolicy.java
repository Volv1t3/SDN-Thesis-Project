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
 * <p>Polls for a present value or true condition until a deadline, sleeping
 * between attempts with an exponentially increased and jittered delay.</p>
 * <p>Suppliers may make controller reads; their runtime failures are retained and
 * retried. Sleeping is synchronous and may extend beyond the deadline.</p>
 */
public final class RetryPolicy {
    /** <p>Structured logger for attempt, delay, success, and exhaustion events.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(RetryPolicy.class);
    /** <p>Base delay in milliseconds before the first retry.</p> */
    private final long initialDelayMs;
    /** <p>Upper bound applied when doubling the base delay.</p> */
    private final long maxDelayMs;
    /** <p>Duration added to the start time to compute the polling deadline.</p> */
    private final Duration timeout;
    /** <p>Percentage used to vary each base delay randomly.</p> */
    private final int jitterPercent;

    /**
     * <p>Creates a retry policy with twenty percent jitter.</p>
     * <p>Construction proceeds as follows:</p>
     * <ol><li>Delegate to the four-argument constructor with jitter set to 20.</li></ol>
     * @param initialDelayMs initial base delay in milliseconds
     * @param maxDelayMs maximum base delay after doubling
     * @param timeout polling window
     */
    public RetryPolicy(final long initialDelayMs, final long maxDelayMs, final Duration timeout) {
        this(initialDelayMs, maxDelayMs, timeout, 20);
    }

    /**
     * <p>Creates a retry policy with explicit delay and jitter settings.</p>
     * <p>Construction proceeds as follows:</p>
     * <ol><li>Store the supplied values without validating them.</li></ol>
     * @param initialDelayMs initial base delay in milliseconds
     * @param maxDelayMs maximum base delay after doubling
     * @param timeout polling window
     * @param jitterPercent percentage used to vary the delay
     */
    public RetryPolicy(
            final long initialDelayMs,
            final long maxDelayMs,
            final Duration timeout,
            final int jitterPercent) {
        this.initialDelayMs = initialDelayMs; /*All come from application defaults*/
        this.maxDelayMs = maxDelayMs; /*Comes from application defaults*/
        this.timeout = timeout; /*Comes from application defaults*/
        this.jitterPercent = jitterPercent; /*Comes from application defaults*/
    }

    /**
     * <p>Polls a supplier until it produces a present value or the deadline is reached.
     * Supplier runtime exceptions are retried and the last one becomes the exhaustion cause.</p>
     * <p>Polling proceeds as follows:</p>
     * <ol><li>Compute the deadline and start with the initial delay.</li>
     * <li>Before each attempt, check the deadline; return a present result immediately.</li>
     * <li>After an empty result or supplier runtime failure, log as appropriate, sleep
     * for a jittered delay and double the base delay up to the configured maximum.</li>
     * <li>On exhaustion, throw with the supplied message and any last supplier failure.</li></ol>
     * <p>Sleep is not clipped to the deadline and interruption aborts the polling operation.</p>
     * @param <T> type of the value being polled
     * @param supplier operation returning an optional value
     * @param failureMessage message used when the deadline expires
     * @return first present supplier value
     * @throws IllegalStateException on exhaustion or interrupted sleep
     * @throws IllegalArgumentException if jitter cannot generate a valid range
     */
    public <T> T retryUntilPresent(final Supplier<Optional<T>> supplier, final String failureMessage) {
        /*Initially we calcualt the max deadline using the timeout we can have on this operation*/
        final Instant deadline = Instant.now().plus(timeout);
        long delay = initialDelayMs;
        int attempt = 0;
        RuntimeException lastFailure = null;

        /*We continue working as long as the time currently is before the deadline. This could be improved with monotonic clocks*/
        while (Instant.now().isBefore(deadline)) {
            attempt++;
            try {
                /*This attempts to perform the get operation of the supplier functional interface provided for this method,
                * this might be, as in the case of the UpdateLspService calls when UpdatingLsp what we are trying to find
                * is that at some time later the optional value, which in that case is the new state is polled and therefore returned.
                * */
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
            /*If the try raised an error on the orElseThrow or before then the exception is caught and the
            * next delay caluclations are performed before a new operation is queued*/
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
     * <p>Polls a Boolean condition until it is true or polling fails.</p>
     * <p>Polling proceeds as follows:</p>
     * <ol><li>Adapt true to a present optional and false to an empty optional, then
     * delegate to {@link #retryUntilPresent}.</li></ol>
     * @param supplier condition evaluated for each attempt
     * @param failureMessage message used on exhaustion
     * @throws IllegalStateException on exhaustion or interrupted sleep
     * @throws IllegalArgumentException if jitter cannot generate a valid range
     */
    public void retryUntilTrue(final Supplier<Boolean> supplier, final String failureMessage) {
        retryUntilPresent(() -> supplier.get() ? Optional.of(Boolean.TRUE) : Optional.empty(), failureMessage);
    }

    /**
     * <p>Randomizes a base delay by a percentage and clamps the result to at least one millisecond.</p>
     * <p>Calculation proceeds as follows:</p>
     * <ol><li>Clamp the percentage to zero or greater and sample a multiplier from
     * the corresponding symmetric range.</li><li>Multiply, round, and clamp the delay.</li></ol>
     * @param delayMs base delay in milliseconds
     * @param jitterPercent requested percentage variation
     * @return jittered delay of at least one millisecond
     * @throws IllegalArgumentException if the random range is empty, including when jitter is zero
     */
    public static long jitter(final long delayMs, final int jitterPercent) {
        final double jitter = Math.max(0, jitterPercent) / 100.0d;
        final double multiplier = ThreadLocalRandom.current().nextDouble(1.0d - jitter, 1.0d + jitter);
        return Math.max(1L, Math.round(delayMs * multiplier));
    }

    /**
     * <p>Blocks the current thread for the requested retry delay.</p>
     * <p>The wait proceeds as follows:</p>
     * <ol><li>Sleep for the delay; on interruption restore the interrupt flag and
     * throw an illegal-state exception with the interruption as cause.</li></ol>
     * @param delayMs delay in milliseconds
     * @throws IllegalStateException if the thread is interrupted while sleeping
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
