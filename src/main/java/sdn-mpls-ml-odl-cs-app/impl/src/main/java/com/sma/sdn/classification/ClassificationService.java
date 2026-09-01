/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.classification;

import com.sma.sdn.http.ClassifierRestClient;
import com.sma.sdn.metrics.SdnMplsMlMetrics;
import com.sma.sdn.model.ClassificationLookupResult;
import com.sma.sdn.model.ClassificationResult;
import com.sma.sdn.model.PacketClassificationContext;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.registry.ClassificationRegistrar;
import com.sma.sdn.serialization.json.ClassificationRequestJsonSerializer;
import com.sma.sdn.serialization.json.ClassificationResponseJsonDeserializer;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Define la clase {@code ClassificationService} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class ClassificationService {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(ClassificationService.class);
    private final ClassificationRegistrar registrar;
    private final ClassifierRestClient client;
    private final ClassificationRequestJsonSerializer requestSerializer;
    private final ClassificationResponseJsonDeserializer responseDeserializer;
    private final SdnMplsMlMetrics metrics;

    /**
     * Ejecuta la operacion {@code ClassificationService} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param registrar valor requerido para ejecutar esta operacion
     *
     * @param client valor requerido para ejecutar esta operacion
     *
     * @param requestSerializer valor requerido para ejecutar esta operacion
     *
     * @param responseDeserializer valor requerido para ejecutar esta operacion
     *
     * @param metrics valor requerido para ejecutar esta operacion
     */
    public ClassificationService(
            final ClassificationRegistrar registrar,
            final ClassifierRestClient client,
            final ClassificationRequestJsonSerializer requestSerializer,
            final ClassificationResponseJsonDeserializer responseDeserializer,
            final SdnMplsMlMetrics metrics) {
        this.registrar = registrar;
        this.client = client;
        this.requestSerializer = requestSerializer;
        this.responseDeserializer = responseDeserializer;
        this.metrics = metrics;
    }

    /**
     * Obtiene una clasificacion desde cache o invoca el clasificador externo cuando no existe una entrada valida.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param context valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    public ClassificationLookupResult classifyOrGetCached(final PacketClassificationContext context) {
        LOG.debug(
                "classification_lookup_started",
                "classifyOrGetCached",
                "Se inicio la busqueda de una clasificacion vigente.",
                StructuredLogger.fields(
                        "eth_type", context.packetFeatures().ethType(),
                        "ip_protocol", context.packetFeatures().ipProto(),
                        "source_port", context.packetFeatures().srcPort(),
                        "destination_port", context.packetFeatures().dstPort()));
        return registrar.find(context)
                .map(result -> {
                    metrics.increment("sma_classification_cache_hit_total");
                    LOG.debug(
                            "classification_cache_hit",
                            "classifyOrGetCached",
                            "Se reutilizo una clasificacion vigente desde cache.",
                            StructuredLogger.fields(
                                    "class_name", result.className(),
                                    "confidence", result.confidence(),
                                    "request_id", result.requestId()));
                    return new ClassificationLookupResult(result, true);
                })
                .orElseGet(() -> classify(context));
    }

    /**
     * Clasifica una respuesta HTTP de acuerdo con el modelo defensivo de resultados ODL.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param context valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    private ClassificationLookupResult classify(final PacketClassificationContext context) {
        metrics.increment("sma_classification_cache_miss_total");
        metrics.increment("sma_classifier_request_total");
        final String body = requestSerializer.serialize(context.packetFeatures());
        LOG.debug(
                "classifier_request_prepared",
                "classify",
                "Se preparo la solicitud segura para el clasificador externo.",
                StructuredLogger.fields(
                        "request_body_bytes", body.getBytes(StandardCharsets.UTF_8).length,
                        "request_body", body));
        final HttpResponse<String> response;
        final Instant startedAt = Instant.now();
        final long roundTripStartedAt = System.nanoTime();
        try {
            response = client.classify(body);
        } catch (RuntimeException e) {
            metrics.increment("sma_classifier_failure_total");
            metrics.observeHistogram("sma_classifier_round_trip_duration_seconds",
                    Map.of("outcome", "transport_error"), elapsedSeconds(roundTripStartedAt));
            LOG.error(
                    "classifier_request_failed",
                    "classify",
                    "Fallo la comunicacion con el clasificador externo.",
                    StructuredLogger.fields(
                            "duration_ms", Duration.between(startedAt, Instant.now()).toMillis()),
                    e);
            throw e;
        }
        LOG.debug(
                "classifier_response_received",
                "classify",
                "Se recibio la respuesta del clasificador externo.",
                StructuredLogger.fields(
                        "http_status", response.statusCode(),
                        "response_body_bytes", response.body() == null ? 0 : response.body().length(),
                        "response_body", response.body(),
                        "duration_ms", Duration.between(startedAt, Instant.now()).toMillis()));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            metrics.increment("sma_classifier_failure_total");
            metrics.observeHistogram("sma_classifier_round_trip_duration_seconds",
                    Map.of("outcome", "http_error"), elapsedSeconds(roundTripStartedAt));
            LOG.warn(
                    "classifier_response_rejected",
                    "classify",
                    "El clasificador rechazo la solicitud y devolvio un cuerpo de error para diagnostico.",
                    StructuredLogger.fields(
                            "http_status", response.statusCode(),
                            "request_body", body,
                            "error_response_body", response.body()),
                    null);
            throw new IllegalStateException("La solicitud al clasificador fallo: HTTP " + response.statusCode());
        }
        final ClassificationResult result;
        try {
            result = responseDeserializer.deserialize(response.body());
        } catch (RuntimeException e) {
            metrics.increment("sma_classifier_failure_total");
            metrics.observeHistogram("sma_classifier_round_trip_duration_seconds",
                    Map.of("outcome", "deserialization_error"), elapsedSeconds(roundTripStartedAt));
            throw e;
        }
        metrics.observeHistogram("sma_classifier_round_trip_duration_seconds",
                Map.of("outcome", "success"), elapsedSeconds(roundTripStartedAt));
        if (result.policy().policyFallback()) {
            LOG.warn(
                    "classifier_fallback_policy",
                    "classify",
                    "El clasificador devolvio una politica de contingencia.",
                    StructuredLogger.fields(
                            "fallback_reason", result.policy().policyFallbackReason(),
                            "class_name", result.className(),
                            "request_id", result.requestId()),
                    null);
        }
        registrar.put(context, result);
        LOG.info(
                "classification_completed",
                "classify",
                "Se completo y registro la clasificacion del paquete.",
                StructuredLogger.fields(
                        "request_id", result.requestId(),
                        "model_name", result.modelName(),
                        "class_name", result.className(),
                        "confidence", result.confidence(),
                        "profile_name", result.policy().profileName(),
                        "bandwidth_kbps", result.policy().pathConstraints().requestedBandwidthKbps(),
                        "expires_at", result.expiresAt()));
        return new ClassificationLookupResult(result, false);
    }

    private static double elapsedSeconds(final long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000_000.0d;
    }
}
