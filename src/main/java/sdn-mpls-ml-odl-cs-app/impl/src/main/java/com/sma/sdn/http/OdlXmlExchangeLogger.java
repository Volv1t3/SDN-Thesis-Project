/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.http;

import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.model.OdlXmlBodyLogLevel;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Registra de forma estructurada los intercambios XML realizados contra las APIs RESTCONF y RESTS de OpenDaylight.
 *
 * <p>Esta clase concentra la evidencia de transporte necesaria para comparar la solicitud XML enviada con la
 * respuesta XML recibida. DEBUG conserva el cuerpo XML para diagnostico e INFO solo conserva un resumen de
 * transporte para la traza operativa. Solo registra los encabezados de negociacion; nunca expone Authorization.</p>
 */
final class OdlXmlExchangeLogger {
    private OdlXmlExchangeLogger() {
        // Clase utilitaria sin estado.
    }

    /**
     * Registra el inicio de una solicitud XML dirigida a OpenDaylight.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Obtiene los encabezados Accept y Content-Type declarados en la solicitud.</li>
     *   <li>Incluye el cuerpo XML completo o indica explicitamente que la solicitud no posee cuerpo.</li>
     *   <li>Emite el cuerpo XML solo en DEBUG y un resumen de transporte en INFO.</li>
     * </ol>
     *
     * @param logger logger estructurado propietario del cliente HTTP
     * @param transport tipo de API ODL, RESTCONF o RESTS
     * @param event identificador estable del evento
     * @param operation operacion que realiza la solicitud
     * @param request solicitud HTTP preparada
     * @param requestBody cuerpo XML enviado o {@code null} cuando no existe
     */
    static void requestStarted(
            final StructuredLogger logger,
            final String transport,
            final String event,
            final String operation,
            final HttpRequest request,
            final String requestBody,
            final OdlXmlBodyLogLevel bodyLogLevel) {
        final Map<String, Object> debugMetadata = StructuredLogger.fields(
                "transport", transport,
                "method", request.method(),
                "endpoint", request.uri(),
                "accept", header(request, "Accept"),
                "content_type", header(request, "Content-Type"),
                "request_body_present", requestBody != null,
                "request_body_bytes", byteLength(requestBody),
                "request_body", requestBody);
        final Map<String, Object> infoMetadata = StructuredLogger.fields(
                "transport", transport,
                "method", request.method(),
                "endpoint", request.uri(),
                "accept", header(request, "Accept"),
                "content_type", header(request, "Content-Type"),
                "request_body_present", requestBody != null,
                "request_body_bytes", byteLength(requestBody));
        logDebugAndInfo(logger, event, operation, "Se inicio una solicitud XML contra OpenDaylight.",
                debugMetadata, infoMetadata, bodyLogLevel);
    }

    /**
     * Registra la respuesta XML producida por OpenDaylight para una solicitud previa.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Lee el codigo HTTP y el encabezado Content-Type de la respuesta.</li>
     *   <li>Incluye el cuerpo XML completo y su tamano en bytes solo en DEBUG.</li>
     *   <li>Emite en INFO el estado, la duracion y el tamano de la respuesta.</li>
     * </ol>
     *
     * @param logger logger estructurado propietario del cliente HTTP
     * @param transport tipo de API ODL, RESTCONF o RESTS
     * @param event identificador estable del evento
     * @param operation operacion que recibe la respuesta
     * @param request solicitud HTTP que origino la respuesta
     * @param response respuesta HTTP recibida
     * @param durationMillis duracion total de la solicitud en milisegundos
     */
    static void responseReceived(
            final StructuredLogger logger,
            final String transport,
            final String event,
            final String operation,
            final HttpRequest request,
            final HttpResponse<String> response,
            final long durationMillis,
            final OdlXmlBodyLogLevel bodyLogLevel) {
        final String responseBody = response.body();
        final Map<String, Object> debugMetadata = StructuredLogger.fields(
                "transport", transport,
                "method", request.method(),
                "endpoint", request.uri(),
                "request_accept", header(request, "Accept"),
                "request_content_type", header(request, "Content-Type"),
                "status_code", response.statusCode(),
                "response_content_type", response.headers().firstValue("Content-Type").orElse(null),
                "response_body_bytes", byteLength(responseBody),
                "response_body", responseBody,
                "duration_ms", durationMillis);
        final Map<String, Object> infoMetadata = StructuredLogger.fields(
                "transport", transport,
                "method", request.method(),
                "endpoint", request.uri(),
                "request_accept", header(request, "Accept"),
                "request_content_type", header(request, "Content-Type"),
                "status_code", response.statusCode(),
                "response_content_type", response.headers().firstValue("Content-Type").orElse(null),
                "response_body_bytes", byteLength(responseBody),
                "duration_ms", durationMillis);
        logDebugAndInfo(logger, event, operation, "Se recibio una respuesta XML de OpenDaylight.",
                debugMetadata, infoMetadata, bodyLogLevel);
    }

    /**
     * Emite una entrada detallada en DEBUG y su resumen seguro en INFO.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Conserva el identificador de evento, la operacion y el mensaje recibidos.</li>
     *   <li>Emite la metadata detallada, incluido el cuerpo XML, en DEBUG.</li>
     *   <li>Emite la metadata resumida en INFO.</li>
     * </ol>
     *
     * @param logger logger estructurado que recibe los eventos
     * @param event identificador estable del evento
     * @param operation operacion asociada
     * @param message descripcion formal del evento
     * @param debugMetadata metadata detallada del intercambio
     * @param infoMetadata metadata resumida del intercambio
     * @param bodyLogLevel nivel configurado para cuerpos XML
     */
    private static void logDebugAndInfo(
            final StructuredLogger logger,
            final String event,
            final String operation,
            final String message,
            final Map<String, Object> debugMetadata,
            final Map<String, Object> infoMetadata,
            final OdlXmlBodyLogLevel bodyLogLevel) {
        if (bodyLogLevel == OdlXmlBodyLogLevel.DEBUG) {
            logger.debug(event, operation, message, debugMetadata);
        } else if (bodyLogLevel == OdlXmlBodyLogLevel.TRACE) {
            logger.trace(event, operation, message, debugMetadata);
        }
        logger.info(event, operation, message, infoMetadata);
    }

    /**
     * Obtiene un encabezado no sensible de una solicitud HTTP.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Busca el encabezado solicitado sin exponer otros encabezados.</li>
     *   <li>Convierte la ausencia del valor en {@code null}.</li>
     *   <li>Devuelve el valor para incluirlo en metadata estructurada.</li>
     * </ol>
     *
     * @param request solicitud que contiene los encabezados
     * @param name nombre del encabezado permitido
     * @return valor del encabezado o {@code null} cuando esta ausente
     */
    private static String header(final HttpRequest request, final String name) {
        return request.headers().firstValue(name).orElse(null);
    }

    /**
     * Calcula el tamano UTF-8 de un cuerpo textual sin fallar cuando esta ausente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Devuelve cero cuando el cuerpo no existe.</li>
     *   <li>Codifica el cuerpo existente mediante UTF-8.</li>
     *   <li>Devuelve el numero de bytes codificados.</li>
     * </ol>
     *
     * @param body cuerpo textual opcional
     * @return tamano UTF-8 del cuerpo o cero
     */
    private static int byteLength(final String body) {
        return body == null ? 0 : body.getBytes(StandardCharsets.UTF_8).length;
    }
}
