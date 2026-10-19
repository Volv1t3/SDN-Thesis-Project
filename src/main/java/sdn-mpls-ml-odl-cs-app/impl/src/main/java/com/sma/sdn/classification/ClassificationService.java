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
 * <p>Resolves a packet classification from the local registry or the external inference API.
 * A cache hit returns the stored result without an HTTP request. On a miss, the service
 * serializes the packet features, calls the classifier, validates its response, and stores
 * the resulting policy for later packets. The returned {@link ClassificationLookupResult}
 * identifies which path was taken.</p>
 *
 * <p>{@link com.sma.sdn.SdnMplsMlWorkflowService} invokes this service after it has
 * extracted a supported packet and resolved its direction. The service delegates cache
 * expiration enforcement and lookup semantics to {@link ClassificationRegistrar}, HTTP transport to
 * {@link ClassifierRestClient}, expiry assignment and JSON conversion to the request and response serializers,
 * and outcome measurements to {@link SdnMplsMlMetrics}. It does not apply the policy or
 * update an LSP.</p>
 */
public final class ClassificationService {

    /**
     * <p>Logs classification lookups, classifier exchanges, fallback policies, and failures
     * under this service's class name.</p>
     */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(ClassificationService.class);

    /**
     * <p>Stores unexpired packet and service-class classifications for later lookups.</p>
     */
    private final ClassificationRegistrar registrar;

    /**
     * <p>Sends cache-miss requests to the external classifier and returns its HTTP response.</p>
     */
    private final ClassifierRestClient client;

    /**
     * <p>Converts packet features into the JSON request body expected by the classifier.</p>
     */
    private final ClassificationRequestJsonSerializer requestSerializer;

    /**
     * <p>Validates classifier response JSON and converts it into a classification with an expiry.</p>
     */
    private final ClassificationResponseJsonDeserializer responseDeserializer;

    /**
     * <p>Records cache outcomes, classifier requests and failures, and classifier-processing duration.</p>
     */
    private final SdnMplsMlMetrics metrics;

    /**
     * <p>Creates a classification service from the registry, transport, JSON converters, and
     * metrics collector that it will use for every lookup. Construction stores these
     * collaborators, it performs no cache lookup or network request.</p>
     *
     * <p>The constructor performs these steps:</p>
     * <ol>
     *   <li>Retains the {@link ClassificationRegistrar} used to find and store classifications.</li>
     *   <li>Retains the {@link ClassifierRestClient} and JSON converters used when the
     *       registry has no valid entry.</li>
     *   <li>Retains {@link SdnMplsMlMetrics} for cache, request, failure, and latency measurements.</li>
     * </ol>
     *
     * <p>The caller supplies and owns these collaborators; this service does not create or
     * close the HTTP client or registry.</p>
     *
     * @param registrar registry used for valid classification lookups and writes
     * @param client client that sends JSON classification requests to the inference API
     * @param requestSerializer converter from packet features to the API request JSON
     * @param responseDeserializer converter from response JSON to a {@link ClassificationResult}
     * @param metrics collector for classification counters and request duration
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
     * <p>Looks up a classification for the packet context and calls the external classifier
     * only when the registry cannot supply a valid result. The returned cache-hit flag
     * allows the workflow to distinguish reuse from a new inference request.</p>
     *
     * <p>The lookup proceeds as follows:</p>
     * <ol>
     *   <li>Logs the packet's Ethernet type, IP protocol, and transport ports for the lookup.</li>
     *   <li>Asks {@link ClassificationRegistrar#find(PacketClassificationContext)} for an
     *       unexpired exact or service-class entry. On a hit, records a cache-hit metric
     *       and wraps the stored result with {@code cacheHit=true}.</li>
     *   <li>On a miss, delegates to {@link #classify(PacketClassificationContext)} to
     *       request, validate, and cache a fresh classification.</li>
     * </ol>
     *
     * <p>The registry owns expiration and key selection. The caller, normally
     * {@link com.sma.sdn.SdnMplsMlWorkflowService}, uses the classification and policy
     * carried by the result in the subsequent pair-policy decision.</p>
     *
     * @param context packet features and ingress identity used for lookup and inference
     * @return the stored or newly inferred classification and its cache-hit flag
     *
     * @throws IllegalStateException if the classifier transport fails or the API returns a non-2xx status
     * @throws IllegalArgumentException if request serialization or response conversion fails
     * @throws NullPointerException if {@code context}, its packet features, or a configured collaborator is
     *         {@code null}
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
        // The registrar tries the exact key first, then the broader service-class key.
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
                    // Either kind of cache hit avoids an external inference request.
                    return new ClassificationLookupResult(result, true);
                })
                /* If the cache lookup fails we attempt to classify the incoming packet */
                .orElseGet(() -> classify(context));
    }

    /**
     * <p>Obtains a fresh classification from the external inference API after a cache miss.
     * A successful response becomes a {@link ClassificationResult} with the expiry
     * assigned by the response deserializer, and is stored in the local registry.
     * Transport, HTTP status, and deserialization failures are measured separately
     * and propagated without caching a result.</p>
     *
     * <p>The request proceeds as follows:</p>
     * <ol>
     *   <li>Counts the cache miss and request, then serializes
     *       {@code context.packetFeatures()} with
     *       {@link ClassificationRequestJsonSerializer}.</li>
     *   <li>Calls {@link ClassifierRestClient#classify(String)} and measures classifier-processing
     *       time. A transport exception is logged, counted, and rethrown.</li>
     *   <li>Rejects any non-2xx HTTP response with an {@link IllegalStateException},
     *       recording the HTTP failure and its duration.</li>
     *   <li>Converts the response body through
     *       {@link ClassificationResponseJsonDeserializer#deserialize(String)}. A
     *       conversion failure is counted and rethrown.</li>
     *   <li>Records successful latency, warns if the returned policy is a fallback,
     *       stores the result with {@link ClassificationRegistrar#put(PacketClassificationContext,
     *       ClassificationResult)}, and returns it with {@code cacheHit=false}.</li>
     * </ol>
     *
     * <p>The HTTP client owns transport details, the deserializer validates the API
     * payload and assigns its time-to-live, and the registrar supplies subsequent
     * cache hits. This method does not interpret the policy for routing.</p>
     *
     * @param context packet context whose features are sent to the inference API
     * @return the newly inferred classification with {@code cacheHit=false}
     *
     * @throws IllegalStateException if the HTTP request fails or the API returns a non-2xx status
     * @throws IllegalArgumentException if the request cannot be serialized, the HTTP request settings are invalid, or
     *         the API response cannot be converted to a valid classification
     * @throws NullPointerException if {@code context}, its packet features, or a configured collaborator is
     *         {@code null}
     */
    private ClassificationLookupResult classify(final PacketClassificationContext context) {
        /*Step 1: incrementing the corresponding metric counters for Prometheus*/
        metrics.increment("sma_classification_cache_miss_total");
        metrics.increment("sma_classifier_request_total");

        /*Serialize the packet features into a representation suitable for the python classifier service through a purpose
        * built serializer class*/
        final String body = requestSerializer.serialize(context.packetFeatures());

        LOG.debug(
                "classifier_request_prepared",
                "classify",
                "Se preparo la solicitud segura para el clasificador externo.",
                StructuredLogger.fields(
                        "request_body_bytes", body.getBytes(StandardCharsets.UTF_8).length,
                        "request_body", body));

        /*Structure a response object for the HTTP request done through the classification client service*/
        final HttpResponse<String> response;
        final Instant startedAt = Instant.now();
        final long roundTripStartedAt = System.nanoTime();
        try {
            /*This executes an HTTP operation through the ClassificationRestClient that under the hood uses
            * an HTTPRequest object to perform the operation*/
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
        /* If the response it outside of the 200 code range then we reject it, this could be an error on the API end,
        * on the network, or on the Java Application like we saw initially*/
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
        /*In order to classify a packet we need to transform the returned body into a proper ClassifictionResult
        * object which is done through a purpose-built deserializer for Classification JSON Responses*/
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

        /* Once we receive a valid response, we store the result for future use in the registrar. This creates a series of records.
        *  1. Exact Record by Switch, that corresponds to a mapping between the Switch Name (foxtrot or echo) and the exact
        *  combination of parameters that identified the incoming packet
        *  2. Exact Record by Switch and ServiceKey, that corresponds to a mapping between the switch name and the
        *  canonical service key built by the canonical service port identified from the packet using either its destination
        *  or source ports. This is the representation of a service like DNS or HTTP in the registrar directly through a
        *  record that relates the Switch Name, the ip proto and eth type (2048 and either 6 or 17 for TCP or UDP) and the
        *  canonical service port.
        * */
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

        // This returns the expected Record object with the result, containing the context alongside the features,
        // the final class, the traffic policy, and the map of probabilities
        return new ClassificationLookupResult(result, false);
    }

    /**
     * <p>Measures elapsed time against a monotonic start value for the classifier
     * histogram. The value is independent of wall-clock adjustments.</p>
     *
     * <p>The calculation proceeds as follows:</p>
     * <ol>
     *   <li>Reads the current {@link System#nanoTime()} value.</li>
     *   <li>Subtracts {@code startedAt} and converts nanoseconds to seconds.</li>
     * </ol>
     *
     * <p>{@link SdnMplsMlMetrics} receives the resulting duration under the
     * success or failure outcome recorded by {@code classify}.</p>
     *
     * @param startedAt monotonic start value captured before the HTTP call
     * @return elapsed duration in seconds
     */
    private static double elapsedSeconds(final long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000_000.0d;
    }
}
