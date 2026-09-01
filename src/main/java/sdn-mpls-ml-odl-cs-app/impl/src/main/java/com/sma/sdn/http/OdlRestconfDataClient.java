/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.http;

import com.sma.sdn.config.AppConfig;
import com.sma.sdn.observability.StructuredLogger;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Define la clase {@code OdlRestconfDataClient} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class OdlRestconfDataClient {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(OdlRestconfDataClient.class);
    private final HttpClient httpClient;
    private final URI baseUrl;
    private final Duration timeout;
    private final com.sma.sdn.model.OdlXmlBodyLogLevel xmlBodyLogLevel;
    private final String authorization;

    /**
     * Ejecuta la operacion {@code OdlRestconfDataClient} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param httpClient valor requerido para ejecutar esta operacion
     *
     * @param config valor requerido para ejecutar esta operacion
     */
    public OdlRestconfDataClient(final HttpClient httpClient, final AppConfig config) {
        this.httpClient = httpClient;
        this.baseUrl = stripTrailingSlash(config.odlRestconfDataBaseUrl());
        this.timeout = config.httpRequestTimeout();
        this.xmlBodyLogLevel = config.odlXmlBodyLogLevel();
        this.authorization = basic(config.odlUsername(), config.odlPassword());
    }

    /**
     * Ejecuta la operacion {@code getNetworkTopologyListXml} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public String getNetworkTopologyListXml() {
        return requireSuccess(getNetworkTopologyList());
    }

    /**
     * Ejecuta la operacion {@code getNetworkTopologyList} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public HttpResponse<String> getNetworkTopologyList() {
        return send(get("/network-topology:network-topology?content=nonconfig"));
    }

    /**
     * Ejecuta la operacion {@code getBgpLsTopologyXml} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param topologyId valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public String getBgpLsTopologyXml(final String topologyId) {
        return requireSuccess(getBgpLsTopology(topologyId));
    }

    /**
     * Ejecuta la operacion {@code getBgpLsTopology} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param topologyId valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public HttpResponse<String> getBgpLsTopology(final String topologyId) {
        return send(get("/network-topology:network-topology/topology="
                + encodePath(topologyId) + "?content=nonconfig"));
    }

    /**
     * Lee la topologia PCEP completa para descubrir los LSP delegados reportados por los PCC.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Codifica el identificador de topologia como segmento RESTCONF.</li>
     *   <li>Solicita datos configurados y operativos mediante {@code content=all}.</li>
     *   <li>Devuelve la respuesta HTTP para su validacion y deserializacion.</li>
     * </ol>
     *
     * @param topologyId identificador de la topologia PCEP
     * @return respuesta HTTP con la topologia PCEP en XML
     */
    public HttpResponse<String> getPcepTopology(final String topologyId) {
        return send(get("/network-topology:network-topology/topology="
                + encodePath(topologyId) + "?content=all"));
    }

    /**
     * Lee el XML de la topologia PCEP y exige que ODL responda satisfactoriamente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Ejecuta la solicitud RESTCONF de topologia PCEP.</li>
     *   <li>Comprueba el codigo de estado HTTP.</li>
     *   <li>Devuelve el cuerpo XML validado.</li>
     * </ol>
     *
     * @param topologyId identificador de la topologia PCEP
     * @return cuerpo XML de la topologia PCEP
     * @throws IllegalStateException si ODL devuelve un estado no exitoso
     */
    public String getPcepTopologyXml(final String topologyId) {
        return requireSuccess(getPcepTopology(topologyId));
    }

    /**
     * Lee el inventario operativo completo de nodos OpenFlow publicado por OpenFlow Plugin.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Construye la ruta del inventario con {@code content=nonconfig}.</li>
     *   <li>Ejecuta una solicitud autenticada que acepta XML.</li>
     *   <li>Devuelve la respuesta sin ocultar su codigo HTTP.</li>
     * </ol>
     *
     * @return respuesta RESTCONF con el inventario operativo
     */
    public HttpResponse<String> getOpenflowInventory() {
        return send(get("/opendaylight-inventory:nodes?content=nonconfig"));
    }

    /**
     * Escribe una definicion de flujo en el almacen configurado de OpenDaylight mediante RESTCONF PUT.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Codifica solamente el identificador del nodo y el identificador del flujo como segmentos de URI.</li>
     *   <li>Conserva sin modificar los identificadores de conectores presentes en el cuerpo XML.</li>
     *   <li>Ejecuta el PUT autenticado y devuelve su resultado para clasificacion.</li>
     * </ol>
     *
     * @param encodedNodeId identificador de nodo ya codificado para RESTCONF
     * @param tableId tabla OpenFlow de destino
     * @param flowId identificador estable del flujo
     * @param xml definicion XML completa del flujo
     * @return respuesta producida por OpenDaylight
     */
    public HttpResponse<String> putOpenflowFlow(
            final String encodedNodeId, final int tableId, final String flowId, final String xml) {
        final String path = openflowTablePath(encodedNodeId, tableId) + "/flow=" + encodePath(flowId);
        return send(HttpRequest.newBuilder(endpoint(path))
                .timeout(timeout)
                .header("Accept", "application/xml")
                .header("Content-Type", "application/xml")
                .header("Authorization", authorization)
                .PUT(HttpRequest.BodyPublishers.ofString(xml, StandardCharsets.UTF_8))
                .build(), xml);
    }

    /**
     * Consulta un flujo individual en el almacen configurado para confirmar que el PUT fue persistido.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Construye la ruta exacta de nodo, tabla y flujo.</li>
     *   <li>Agrega {@code content=config} para excluir estado operativo.</li>
     *   <li>Devuelve la respuesta HTTP para que el verificador determine presencia.</li>
     * </ol>
     *
     * @param encodedNodeId identificador de nodo codificado
     * @param tableId tabla que contiene el flujo
     * @param flowId identificador del flujo
     * @return respuesta RESTCONF de verificacion configurada
     */
    public HttpResponse<String> getOpenflowFlowConfig(
            final String encodedNodeId, final int tableId, final String flowId) {
        return send(get(openflowTablePath(encodedNodeId, tableId)
                + "/flow=" + encodePath(flowId) + "?content=config"));
    }

    /**
     * Consulta el estado operativo de una tabla para verificar la propagacion de flujos al conmutador.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Construye la ruta de la tabla asociada al nodo descubierto.</li>
     *   <li>Solicita exclusivamente contenido no configurado.</li>
     *   <li>Devuelve el XML operativo para buscar los identificadores esperados.</li>
     * </ol>
     *
     * @param encodedNodeId identificador de nodo codificado
     * @param tableId tabla OpenFlow consultada
     * @return respuesta RESTCONF con el estado operativo de la tabla
     */
    public HttpResponse<String> getOpenflowTableOperational(final String encodedNodeId, final int tableId) {
        return send(get(openflowTablePath(encodedNodeId, tableId) + "?content=nonconfig"));
    }

    /**
     * Construye la ruta RESTCONF comun de una tabla OpenFlow sin volver a codificar el identificador del nodo.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Agrega el identificador de nodo previamente codificado al recurso de inventario.</li>
     *   <li>Agrega el identificador decimal de tabla.</li>
     *   <li>Devuelve una ruta relativa lista para incorporar flujo o consulta.</li>
     * </ol>
     *
     * @param encodedNodeId identificador de nodo ya codificado
     * @param tableId identificador de tabla OpenFlow
     * @return ruta relativa de la tabla
     */
    private static String openflowTablePath(final String encodedNodeId, final int tableId) {
        return "/opendaylight-inventory:nodes/node=" + encodedNodeId
                + "/flow-node-inventory:table=" + tableId;
    }

    /**
     * Ejecuta la operacion {@code get} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param pathAndQuery valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private HttpRequest get(final String pathAndQuery) {
        return HttpRequest.newBuilder(endpoint(pathAndQuery))
                .timeout(timeout)
                .header("Accept", "application/xml")
                .header("Authorization", authorization)
                .GET()
                .build();
    }

    /**
     * Ejecuta la operacion {@code requireSuccess} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param response valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    private String requireSuccess(final HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("La solicitud de datos RESTCONF de ODL fallo: HTTP "
                    + response.statusCode());
        }
        return response.body();
    }

    /**
     * Ejecuta la operacion {@code send} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param request valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    private HttpResponse<String> send(final HttpRequest request) {
        return send(request, null);
    }

    /**
     * Ejecuta una solicitud RESTCONF y registra su intercambio XML completo.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Registra el metodo, los encabezados de negociacion y el cuerpo XML de solicitud.</li>
     *   <li>Ejecuta la solicitud autenticada contra OpenDaylight.</li>
     *   <li>Registra la respuesta XML completa, su estado HTTP y su duracion.</li>
     * </ol>
     *
     * @param request solicitud RESTCONF preparada
     * @param requestBody cuerpo XML enviado o {@code null} cuando no existe
     * @return respuesta textual producida por OpenDaylight
     * @throws IllegalStateException si la comunicacion RESTCONF falla o es interrumpida
     */
    private HttpResponse<String> send(final HttpRequest request, final String requestBody) {
        final long startedAt = System.nanoTime();
        OdlXmlExchangeLogger.requestStarted(
                LOG, "RESTCONF", "odl_data_request_started", "send", request, requestBody, xmlBodyLogLevel);
        try {
            final HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            OdlXmlExchangeLogger.responseReceived(
                    LOG, "RESTCONF", "odl_data_response_received", "send", request, response,
                    elapsedMillis(startedAt), xmlBodyLogLevel);
            return response;
        } catch (IOException e) {
            LOG.error("odl_data_request_failed", "send",
                    "La solicitud RESTCONF contra ODL fallo",
                    StructuredLogger.fields("method", request.method(), "endpoint", request.uri(),
                            "duration_ms", elapsedMillis(startedAt)), e);
            throw new IllegalStateException("La solicitud de datos RESTCONF de ODL fallo", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("odl_data_request_interrupted", "send",
                    "La solicitud RESTCONF contra ODL fue interrumpida",
                    StructuredLogger.fields("method", request.method(), "endpoint", request.uri(),
                            "duration_ms", elapsedMillis(startedAt)), e);
            throw new IllegalStateException("La solicitud de datos RESTCONF de ODL fue interrumpida", e);
        }
    }

    /**
     * Calcula los milisegundos monotonicamente transcurridos desde el instante de inicio indicado.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Lee el reloj monotono actual de la JVM.</li>
     *   <li>Resta el valor capturado al iniciar la operacion.</li>
     *   <li>Convierte la diferencia de nanosegundos a milisegundos.</li>
     * </ol>
     *
     * @param startedAt lectura de {@link System#nanoTime()} tomada al iniciar la operacion
     * @return duracion transcurrida en milisegundos
     */
    private static long elapsedMillis(final long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    /**
     * Ejecuta la operacion {@code endpoint} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param pathAndQuery valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private URI endpoint(final String pathAndQuery) {
        return URI.create(baseUrl.toString() + pathAndQuery);
    }

    /**
     * Ejecuta la operacion {@code stripTrailingSlash} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param uri valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private static URI stripTrailingSlash(final URI uri) {
        final String value = uri.toString();
        return URI.create(value.endsWith("/") ? value.substring(0, value.length() - 1) : value);
    }

    /**
     * Ejecuta la operacion {@code basic} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param username valor requerido para ejecutar esta operacion
     *
     * @param password valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private static String basic(final String username, final String password) {
        final String token = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Ejecuta la operacion {@code encodePath} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param value valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    private static String encodePath(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
