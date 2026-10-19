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

package com.sma.sdn.openflow;

import com.sma.sdn.config.AppConfig;
import com.sma.sdn.http.OdlRestconfDataClient;
import com.sma.sdn.metrics.SdnMplsMlMetrics;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.util.RetryPolicy;
import java.net.http.HttpResponse;
import java.util.Objects;

/**
 * <p>Installs OpenFlow definitions using RESTCONF PUT, retrying transient HTTP responses
 * and runtime failures with bounded, jittered backoff. Bootstrap callers receive an
 * {@link OpenflowFlowInstallResult} describing the final outcome.</p>
 */
public final class OpenflowFlowProvisioningService {
    /** <p>Records installations and retry decisions.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(OpenflowFlowProvisioningService.class);
    /** <p>Sends flow configuration PUT requests to OpenDaylight.</p> */
    private final OdlRestconfDataClient dataClient;
    /** <p>Builds the flow inventory XML payload.</p> */
    private final OpenflowFlowXmlSerializer serializer;
    /** <p>Records attempt, success and failure counts.</p> */
    private final SdnMplsMlMetrics metrics;
    /** <p>Maximum number of PUT attempts, including the first.</p> */
    private final int maxAttempts;
    /** <p>Base delay before the first retry in milliseconds.</p> */
    private final long initialDelayMs;
    /** <p>Upper bound for the exponentially increasing base delay.</p> */
    private final long maxDelayMs;
    /** <p>Percentage jitter applied to each retry delay.</p> */
    private final int jitterPercent;

    /**
     * <p>Initializes the RESTCONF collaborators and retry configuration.</p>
     *
     * <p>Construction proceeds as follows:</p>
     * <ol>
     *   <li>Require a client, serializer and metrics collector.</li>
     *   <li>Read the topology-discovery attempt limit, clamping it to at least one.</li>
     *   <li>Read the initial and maximum delays and jitter percentage.</li>
     * </ol>
     *
     * @param config retry settings
     * @param dataClient authenticated RESTCONF data client
     * @param serializer flow XML serializer
     * @param metrics application metrics collector
     * @throws NullPointerException if a collaborator or configuration is null
     */
    public OpenflowFlowProvisioningService(
            final AppConfig config,
            final OdlRestconfDataClient dataClient,
            final OpenflowFlowXmlSerializer serializer,
            final SdnMplsMlMetrics metrics) {
        this.dataClient = Objects.requireNonNull(dataClient, "dataClient");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        maxAttempts = Math.max(1, config.topologyDiscoveryMaxAttempts());
        initialDelayMs = config.odlRetryInitialDelayMs();
        maxDelayMs = config.odlRetryMaxDelayMs();
        jitterPercent = config.odlRetryJitterPercent();
    }

    /**
     * <p>Installs a flow and returns a structured result for success, a permanent
     * HTTP failure, or exhaustion of retryable attempts. The XML is serialized once.</p>
     *
     * <p>Installation proceeds as follows:</p>
     * <ol>
     *   <li>Serialize the definition, then PUT it to the switch's encoded node URI.</li>
     *   <li>Return success for HTTP 200, 201 or 204, and fail immediately for other nonretryable statuses.</li>
     *   <li>Retry HTTP 409, 503 and runtime failures, waiting with bounded jittered backoff between attempts.</li>
     *   <li>Record failure and return a retryable result if all attempts are exhausted.</li>
     * </ol>
     *
     * @param switchRecord destination switch and encoded node ID
     * @param flowDefinition rule to serialize and install
     * @return final installation classification and last observed HTTP status
     * @throws IllegalStateException if the retry wait is interrupted
     */
    public OpenflowFlowInstallResult installFlow(
            final OpenflowSwitchRecord switchRecord,
            final OpenflowFlowDefinition flowDefinition) {
        final String xml = serializer.serialize(flowDefinition);
        long delayMs = initialDelayMs;
        RuntimeException lastException = null;
        int lastStatus = 0;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            metrics.increment("sma_openflow_flow_install_attempts_total");
            try {
                final HttpResponse<String> response = dataClient.putOpenflowFlow(
                        switchRecord.encodedNodeId(), flowDefinition.tableId(), flowDefinition.flowId(), xml);
                lastStatus = response.statusCode();
                if (isSuccess(lastStatus)) {
                    metrics.increment("sma_openflow_flow_install_success_total");
                    LOG.info(
                            "openflow_bootstrap_flow_installed",
                            "installFlow",
                            "Se instalo un flujo OpenFlow de bootstrap.",
                            StructuredLogger.fields(
                                    "logical_name", switchRecord.logicalName(),
                                    "flow_id", flowDefinition.flowId(),
                                    "priority", flowDefinition.priority(),
                                    "ethernet_type", flowDefinition.ethernetType(),
                                    "input_connector", flowDefinition.inputConnectorName(),
                                    "output_connector", flowDefinition.outputConnectorName(),
                                    "attempt", attempt,
                                    "status_code", lastStatus));
                    return new OpenflowFlowInstallResult(
                            flowDefinition.flowId(), true, false, lastStatus, "ODL acepto el flujo");
                }
                if (!isRetryable(lastStatus)) {
                    metrics.increment("sma_openflow_flow_install_failure_total");
                    return new OpenflowFlowInstallResult(
                            flowDefinition.flowId(), false, false, lastStatus,
                            "ODL rechazo permanentemente el flujo");
                }
            } catch (RuntimeException e) {
                lastException = e;
            }
            if (attempt < maxAttempts) {
                waitBeforeRetry(flowDefinition.flowId(), attempt, delayMs, lastStatus, lastException);
                delayMs = Math.min(maxDelayMs, safeDouble(delayMs));
            }
        }
        metrics.increment("sma_openflow_flow_install_failure_total");
        final String detail = lastException == null
                ? "Se agotaron los reintentos despues de una respuesta transitoria"
                : "Se agotaron los reintentos por un fallo de transporte: " + lastException.getMessage();
        return new OpenflowFlowInstallResult(flowDefinition.flowId(), false, true, lastStatus, detail);
    }

    /**
     * <p>Waits for a jittered delay before the next PUT, restoring the interrupt flag if interrupted.</p>
     * <p>The wait proceeds as follows:</p>
     * <ol><li>Apply jitter and log the previous attempt and delay.</li>
     * <li>Sleep for that delay, converting interruption to an exception.</li></ol>
     *
     * @param flowId identifier of the flow being retried
     * @param attempt attempt that just failed
     * @param baseDelayMs delay before jitter in milliseconds
     * @param statusCode last observed HTTP status, or zero without a response
     * @param failure most recent runtime exception, if any
     * @throws IllegalStateException if the thread is interrupted during sleep
     */
    private void waitBeforeRetry(
            final String flowId,
            final int attempt,
            final long baseDelayMs,
            final int statusCode,
            final RuntimeException failure) {
        final long delayMs = RetryPolicy.jitter(baseDelayMs, jitterPercent);
        LOG.warn(
                "openflow_flow_install_retry",
                "waitBeforeRetry",
                "La instalacion OpenFlow encontro un fallo transitorio y sera reintentada.",
                StructuredLogger.fields(
                        "flow_id", flowId,
                        "attempt", attempt,
                        "status_code", statusCode,
                        "retry_delay_ms", delayMs),
                failure);
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("La espera de instalacion OpenFlow fue interrumpida", e);
        }
    }

    /**
     * <p>Doubles a delay, saturating at the largest representable long.</p>
     * <p>The calculation has one step:</p>
     * <ol><li>Check for positive overflow before multiplying by two.</li></ol>
     *
     * @param value current delay
     * @return doubled delay or {@link Long#MAX_VALUE} on positive overflow
     */
    private static long safeDouble(final long value) {
        return value > Long.MAX_VALUE / 2L ? Long.MAX_VALUE : value * 2L;
    }

    /**
     * <p>Recognizes HTTP statuses treated as successful flow installation.</p>
     * <p>The check has one step:</p>
     * <ol><li>Compare the status with 200, 201 and 204.</li></ol>
     *
     * @param statusCode received HTTP status
     * @return whether the status is 200, 201 or 204
     */
    private static boolean isSuccess(final int statusCode) {
        return statusCode == 200 || statusCode == 201 || statusCode == 204;
    }

    /**
     * <p>Recognizes the HTTP statuses retried by flow installation.</p>
     * <p>The check has one step:</p>
     * <ol><li>Compare the status with 409 and 503.</li></ol>
     *
     * @param statusCode received HTTP status
     * @return whether the status is 409 or 503
     */
    private static boolean isRetryable(final int statusCode) {
        return statusCode == 409 || statusCode == 503;
    }
}
