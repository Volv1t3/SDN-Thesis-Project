/*
 * Copyright (c) 2026 Santiago Arellano and others.
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
 * Programa flujos OpenFlow mediante RESTCONF PUT y limita internamente los reintentos de fallos transitorios.
 */
public final class OpenflowFlowProvisioningService {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(OpenflowFlowProvisioningService.class);
    private final OdlRestconfDataClient dataClient;
    private final OpenflowFlowXmlSerializer serializer;
    private final SdnMplsMlMetrics metrics;
    private final int maxAttempts;
    private final long initialDelayMs;
    private final long maxDelayMs;
    private final int jitterPercent;

    /**
     * Crea el provisionador con los limites de reintento compartidos por las llamadas protegidas a ODL.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Conserva el cliente RESTCONF y el serializador XML.</li>
     *   <li>Normaliza el numero maximo de intentos a un minimo de uno.</li>
     *   <li>Conserva las demoras y el jitter para respuestas transitorias.</li>
     * </ol>
     *
     * @param config configuracion de reintentos
     * @param dataClient cliente RESTCONF autenticado
     * @param serializer serializador de flujos
     * @param metrics registro de metricas
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
     * Instala una regla, reintenta respuestas 409, 503 y fallos de transporte, y clasifica el resultado final.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Serializa la regla sin codificar los identificadores contenidos en el XML.</li>
     *   <li>Ejecuta PUT usando el identificador de nodo previamente codificado.</li>
     *   <li>Acepta HTTP 200, 201 y 204 como exito.</li>
     *   <li>Reintenta HTTP 409, HTTP 503 y excepciones de transporte con espera exponencial.</li>
     *   <li>Devuelve inmediatamente errores permanentes como HTTP 400 o 404.</li>
     * </ol>
     *
     * @param switchRecord conmutador de destino
     * @param flowDefinition regla que sera instalada
     * @return clasificacion final del intento de instalacion
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
     * Espera antes del siguiente PUT y conserva la interrupcion del hilo como un fallo controlado.
     *
     * @param flowId identificador del flujo reintentado
     * @param attempt intento que acaba de fallar
     * @param baseDelayMs demora base antes de aplicar jitter
     * @param statusCode ultimo codigo HTTP, o cero sin respuesta
     * @param failure ultima excepcion de transporte, si existio
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
     * Duplica una demora sin desbordar el intervalo representable por {@code long}.
     *
     * @param value demora actual
     * @return demora duplicada o {@link Long#MAX_VALUE}
     */
    private static long safeDouble(final long value) {
        return value > Long.MAX_VALUE / 2L ? Long.MAX_VALUE : value * 2L;
    }

    /**
     * Reconoce los codigos RESTCONF que confirman persistencia satisfactoria.
     *
     * @param statusCode codigo HTTP recibido
     * @return {@code true} para HTTP 200, 201 o 204
     */
    private static boolean isSuccess(final int statusCode) {
        return statusCode == 200 || statusCode == 201 || statusCode == 204;
    }

    /**
     * Reconoce conflictos y falta temporal de servicio como condiciones recuperables.
     *
     * @param statusCode codigo HTTP recibido
     * @return {@code true} para HTTP 409 o 503
     */
    private static boolean isRetryable(final int statusCode) {
        return statusCode == 409 || statusCode == 503;
    }
}
