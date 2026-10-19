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
 * <p>Provides blocking, authenticated access to the OpenDaylight RESTCONF data store for network-topology and
 * OpenFlow inventory resources. The client constructs XML-oriented GET and PUT requests, records each exchange, and
 * returns raw HTTP responses when callers need operation-specific classification.</p>
 *
 * <p>Topology discovery, PCEP reconciliation, inventory discovery, and OpenFlow provisioning services share this
 * client. Transport failures become {@link IllegalStateException}; HTTP error statuses remain available to callers
 * except in the convenience methods whose names end in {@code Xml}, which require a 2xx response.</p>
 */
public final class OdlRestconfDataClient {
    /**
     * <p>Records RESTCONF request summaries, optional XML bodies, responses, durations, and transport failures.</p>
     */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(OdlRestconfDataClient.class);

    /**
     * <p>Sends blocking RESTCONF requests using the HTTP transport shared by the provider.</p>
     */
    private final HttpClient httpClient;

    /**
     * <p>Holds the normalized RESTCONF data base URI without a trailing slash.</p>
     */
    private final URI baseUrl;

    /**
     * <p>Specifies the timeout attached to every RESTCONF request.</p>
     */
    private final Duration timeout;

    /**
     * <p>Controls whether complete OpenDaylight XML bodies are logged at debug, trace, or neither level.</p>
     */
    private final com.sma.sdn.model.OdlXmlBodyLogLevel xmlBodyLogLevel;

    /**
     * <p>Stores the precomputed HTTP Basic Authorization header for OpenDaylight requests.</p>
     */
    private final String authorization;

    /**
     * <p>Creates a RESTCONF data client from a shared transport and application configuration. Construction performs
     * no network exchange.</p>
     *
     * <p>The constructor performs these steps:</p>
     * <ol>
     *   <li>Retains the shared {@link HttpClient}.</li>
     *   <li>Reads and normalizes the RESTCONF data base URI.</li>
     *   <li>Captures the request timeout and XML body logging level.</li>
     *   <li>Encodes the configured OpenDaylight credentials into a reusable Basic authorization value.</li>
     * </ol>
     *
     * <p>The provider shares the same HTTP transport with the operations and classifier clients.</p>
     *
     * @param httpClient HTTP transport used for synchronous RESTCONF requests
     * @param config application configuration containing endpoint, timeout, logging, and credentials
     * @throws NullPointerException if {@code config} or a required credential/configuration value is {@code null}
     */
    public OdlRestconfDataClient(final HttpClient httpClient, final AppConfig config) {
        this.httpClient = httpClient;
        this.baseUrl = stripTrailingSlash(config.odlRestconfDataBaseUrl());
        this.timeout = config.httpRequestTimeout();
        /*This is a logging value that is additional for RESTCONF operations that send out information that is not logged elsewhere.
        * These requests carry XML bodies that are long and require additional logging in DEBUG mode, hence this value*/
        this.xmlBodyLogLevel = config.odlXmlBodyLogLevel();
        this.authorization = basic(config.odlUsername(), config.odlPassword());
    }


    /**
     * <p>Reads the operational network-topology container while preserving the complete HTTP response.</p>
     *
     * <p>The read performs these steps:</p>
     * <ol>
     *   <li>Builds an authenticated GET for {@code network-topology:network-topology}.</li>
     *   <li>Adds {@code content=nonconfig} so only operational topology state is requested.</li>
     *   <li>Sends the request and returns its raw response.</li>
     * </ol>
     *
     * <p>{@link com.sma.sdn.topology.TopologyDiscoveryService} classifies this response before deserializing topology
     * identifiers.</p>
     *
     * @return raw RESTCONF response containing the operational topology list
     * @throws IllegalStateException if transport fails or the thread is interrupted
     */
    public HttpResponse<String> getNetworkTopologyList() {
        return send(get("/network-topology:network-topology?content=nonconfig"));
    }

    /**
     * <p>Reads one operational BGP-LS topology while preserving its HTTP status for defensive classification.</p>
     *
     * <p>The read performs these steps:</p>
     * <ol>
     *   <li>Percent-encodes {@code topologyId} as a RESTCONF list-key segment.</li>
     *   <li>Builds a GET for that topology with {@code content=nonconfig}.</li>
     *   <li>Sends the request and returns its raw response.</li>
     * </ol>
     *
     * <p>Topology discovery and refresh services classify the response before updating their registries.</p>
     *
     * @param topologyId BGP-LS topology list key to encode into the URI
     * @return raw RESTCONF response for the selected BGP-LS topology
     * @throws IllegalStateException if transport fails or the thread is interrupted
     * @throws NullPointerException if {@code topologyId} is {@code null}
     */
    public HttpResponse<String> getBgpLsTopology(final String topologyId) {
        return send(get("/network-topology:network-topology/topology="
                + encodePath(topologyId) + "?content=nonconfig"));
    }

    /**
     * <p>Reads the complete PCEP topology used to discover delegated LSPs reported by PCC nodes.</p>
     *
     * <p>The read performs these steps:</p>
     * <ol>
     *   <li>Percent-encodes the topology identifier as a RESTCONF list-key segment.</li>
     *   <li>Requests both configured and operational state with {@code content=all}.</li>
     *   <li>Sends the GET and returns its complete HTTP response.</li>
     * </ol>
     *
     * <p>{@link com.sma.sdn.tunnel.DelegatedLspService} deserializes successful bodies to refresh delegated LSP state.</p>
     *
     * @param topologyId PCEP topology list key
     * @return raw RESTCONF response containing the PCEP topology XML
     * @throws IllegalStateException if transport fails or the thread is interrupted
     * @throws NullPointerException if {@code topologyId} is {@code null}
     */
    public HttpResponse<String> getPcepTopology(final String topologyId) {
        return send(get("/network-topology:network-topology/topology="
                + encodePath(topologyId) + "?content=all"));
    }

    /**
     * <p>Reads the complete operational OpenFlow node inventory published by the OpenFlow plugin.</p>
     *
     * <p>The read performs these steps:</p>
     * <ol>
     *   <li>Builds the inventory resource path with {@code content=nonconfig}.</li>
     *   <li>Creates and sends an authenticated GET accepting XML.</li>
     *   <li>Returns the response without hiding its HTTP status.</li>
     * </ol>
     *
     * <p>{@link com.sma.sdn.openflow.OpenflowInventoryService} validates and deserializes this response.</p>
     *
     * @return raw RESTCONF response containing the operational OpenFlow inventory
     * @throws IllegalStateException if transport fails or the thread is interrupted
     */
    public HttpResponse<String> getOpenflowInventory() {
        return send(get("/opendaylight-inventory:nodes?content=nonconfig"));
    }

    /**
     * <p>Writes a complete flow definition to OpenDaylight's configured data store with RESTCONF {@code PUT}.</p>
     *
     * <p>The write performs these steps:</p>
     * <ol>
     *   <li>Builds the node and table resource path from the already encoded node identifier and numeric table ID.</li>
     *   <li>Percent-encodes the flow identifier and appends it as the flow list key.</li>
     *   <li>Builds an authenticated, timed PUT accepting and sending XML.</li>
     *   <li>Sends the exact supplied XML body and returns the raw response for caller-side classification.</li>
     * </ol>
     *
     * <p>OpenFlow provisioning and suppression services use this method, then verify configured or operational state.</p>
     *
     * @param encodedNodeId node identifier already encoded for use as a RESTCONF path segment
     * @param tableId destination OpenFlow table number
     * @param flowId stable flow identifier to encode as a path segment
     * @param xml complete OpenFlow flow definition
     * @return raw HTTP response produced by OpenDaylight
     * @throws IllegalStateException if transport fails or the thread is interrupted
     * @throws NullPointerException if {@code flowId}, {@code xml}, or a required transport setting is {@code null}
     * @throws IllegalArgumentException if the resulting endpoint or timeout is invalid
     */
    public HttpResponse<String> putOpenflowFlow(
            final String encodedNodeId, final int tableId, final String flowId, final String xml) {
        final String path = openflowTablePath(encodedNodeId, tableId) + "/flow=" + encodePath(flowId);
        HttpRequest openflowRequest = HttpRequest.newBuilder(endpoint(path))
                .timeout(timeout)
                .header("Accept", "application/xml")
                .header("Content-Type", "application/xml")
                .header("Authorization", authorization)
                .PUT(HttpRequest.BodyPublishers.ofString(xml, StandardCharsets.UTF_8))
                .build();
        return send(openflowRequest, xml);
    }

    /**
     * <p>Reads one flow from the configured data store so a caller can confirm that a preceding PUT was persisted.</p>
     *
     * <p>The verification read performs these steps:</p>
     * <ol>
     *   <li>Builds the exact node and table resource path.</li>
     *   <li>Encodes and appends the flow identifier.</li>
     *   <li>Adds {@code content=config} to exclude operational state, sends the GET, and returns the response.</li>
     * </ol>
     *
     * <p>{@link com.sma.sdn.openflow.OpenflowBootstrapVerifier} checks the returned status and body for presence.</p>
     *
     * @param encodedNodeId node identifier already encoded for RESTCONF
     * @param tableId table containing the flow
     * @param flowId flow identifier to encode into the URI
     * @return raw RESTCONF response for the configured flow resource
     * @throws IllegalStateException if transport fails or the thread is interrupted
     * @throws NullPointerException if {@code flowId} is {@code null}
     */
    public HttpResponse<String> getOpenflowFlowConfig(
            final String encodedNodeId, final int tableId, final String flowId) {
        return send(
                get(openflowTablePath(encodedNodeId, tableId)
                + "/flow=" + encodePath(flowId) + "?content=config"));
    }

    /**
     * <p>Reads a table's operational state so callers can verify that configured flows reached the switch.</p>
     *
     * <p>The verification read performs these steps:</p>
     * <ol>
     *   <li>Builds the table path for the discovered node and table number.</li>
     *   <li>Adds {@code content=nonconfig} to request operational state only.</li>
     *   <li>Sends the GET and returns the response for expected-flow inspection.</li>
     * </ol>
     *
     * <p>{@link com.sma.sdn.openflow.OpenflowBootstrapVerifier} consumes the response during bootstrap verification.</p>
     *
     * @param encodedNodeId node identifier already encoded for RESTCONF
     * @param tableId OpenFlow table number to inspect
     * @return raw RESTCONF response containing operational table state
     * @throws IllegalStateException if transport fails or the thread is interrupted
     */
    public HttpResponse<String> getOpenflowTableOperational(final String encodedNodeId, final int tableId) {
        return send(get(openflowTablePath(encodedNodeId, tableId) + "?content=nonconfig"));
    }

    /**
     * <p>Builds the common RESTCONF path for an OpenFlow table without re-encoding the supplied node identifier.</p>
     *
     * <p>The path construction performs these steps:</p>
     * <ol>
     *   <li>Appends the pre-encoded node identifier to the inventory node list path.</li>
     *   <li>Appends the decimal table identifier using the flow-node-inventory table list.</li>
     *   <li>Returns a relative path ready for a flow key or content query.</li>
     * </ol>
     *
     * @param encodedNodeId node identifier already encoded as a path segment
     * @param tableId OpenFlow table number
     * @return relative RESTCONF path for the selected table
     */
    private static String openflowTablePath(final String encodedNodeId, final int tableId) {
        return "/opendaylight-inventory:nodes/node=" + encodedNodeId
                + "/flow-node-inventory:table=" + tableId;
    }

    /**
     * <p>Builds an authenticated XML-oriented RESTCONF {@code GET} request for a relative path and query.</p>
     *
     * <p>The request construction performs these steps:</p>
     * <ol>
     *   <li>Resolves {@code pathAndQuery} by textual concatenation with the normalized base URI.</li>
     *   <li>Applies the captured request timeout.</li>
     *   <li>Adds XML Accept and stored Basic Authorization headers.</li>
     *   <li>Builds and returns the GET request without sending it.</li>
     * </ol>
     *
     * @param pathAndQuery relative RESTCONF resource path with any query string
     * @return prepared authenticated GET request
     * @throws NullPointerException if a required request setting is {@code null}
     * @throws IllegalArgumentException if the resulting endpoint or timeout is invalid
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
     * <p>Sends a prepared RESTCONF request that has no request body.</p>
     *
     * <p>The adapter performs these steps:</p>
     * <ol>
     *   <li>Accepts the prepared request.</li>
     *   <li>Delegates to {@link #send(HttpRequest, String)} with a {@code null} body for accurate logging.</li>
     *   <li>Returns the complete HTTP response.</li>
     * </ol>
     *
     * @param request prepared body-free RESTCONF request
     * @return raw HTTP response including non-success statuses
     * @throws IllegalStateException if transport fails or the thread is interrupted
     * @throws NullPointerException if {@code request} or the HTTP client is {@code null}
     */
    private HttpResponse<String> send(final HttpRequest request) {
        return send(request, null);
    }

    /**
     * <p>Sends one prepared RESTCONF request synchronously and records its XML exchange. HTTP error statuses are
     * returned normally; only transport failure and interruption are converted to exceptions.</p>
     *
     * <p>The exchange performs these steps:</p>
     * <ol>
     *   <li>Captures a monotonic start value and records request metadata through {@link OdlXmlExchangeLogger}.</li>
     *   <li>Sends the request synchronously and decodes its body as UTF-8.</li>
     *   <li>Records response metadata and elapsed milliseconds before returning the response.</li>
     *   <li>On I/O failure, records the failure and throws {@link IllegalStateException} with the cause.</li>
     *   <li>On interruption, restores the interrupt flag, records the event, and throws {@link IllegalStateException}.</li>
     * </ol>
     *
     * @param request prepared RESTCONF request
     * @param requestBody XML body sent with the request, or {@code null} for body-free requests
     * @return raw string response produced by OpenDaylight
     * @throws IllegalStateException if RESTCONF transport fails or the sending thread is interrupted
     * @throws NullPointerException if {@code request} or the HTTP client is {@code null}
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
     * <p>Calculates elapsed whole milliseconds from a monotonic start value.</p>
     *
     * <p>The calculation performs these steps:</p>
     * <ol>
     *   <li>Reads the current {@link System#nanoTime()} value.</li>
     *   <li>Subtracts the supplied start value.</li>
     *   <li>Converts the nanosecond difference to whole milliseconds.</li>
     * </ol>
     *
     * <p>RESTCONF exchange logs use the result and are unaffected by wall-clock adjustments.</p>
     *
     * @param startedAt monotonic value captured immediately before transport
     * @return elapsed whole milliseconds
     */
    private static long elapsedMillis(final long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    /**
     * <p>Creates a RESTCONF endpoint URI by appending a relative path and query to the normalized base URI.</p>
     *
     * <p>The endpoint construction performs these steps:</p>
     * <ol>
     *   <li>Converts the base URI to text.</li>
     *   <li>Appends {@code pathAndQuery} exactly as supplied.</li>
     *   <li>Parses and returns the combined URI.</li>
     * </ol>
     *
     * @param pathAndQuery relative path beginning with a slash and any query string
     * @return absolute RESTCONF endpoint URI
     * @throws IllegalArgumentException if the combined text is not a valid URI
     */
    private URI endpoint(final String pathAndQuery) {
        return URI.create(baseUrl.toString() + pathAndQuery);
    }

    /**
     * <p>Normalizes a RESTCONF base URI by removing one trailing slash when present.</p>
     *
     * <p>The normalization performs these steps:</p>
     * <ol>
     *   <li>Converts the URI to text.</li>
     *   <li>Removes the final character only when it is a slash.</li>
     *   <li>Creates and returns a URI from the normalized value.</li>
     * </ol>
     *
     * <p>This prevents double slashes when resource paths are appended.</p>
     *
     * @param uri RESTCONF data base URI
     * @return equivalent URI without one trailing slash
     * @throws NullPointerException if {@code uri} is {@code null}
     * @throws IllegalArgumentException if the normalized value is not a valid URI
     */
    private static URI stripTrailingSlash(final URI uri) {
        final String value = uri.toString();
        return URI.create(value.endsWith("/") ? value.substring(0, value.length() - 1) : value);
    }

    /**
     * <p>Builds an HTTP Basic authorization value from configured OpenDaylight credentials.</p>
     *
     * <p>The encoding performs these steps:</p>
     * <ol>
     *   <li>Concatenates the username, a colon, and the password.</li>
     *   <li>Encodes the token as UTF-8 bytes and then Base64.</li>
     *   <li>Prefixes the result with the {@code Basic} scheme.</li>
     * </ol>
     *
     * <p>The value is attached to requests but omitted from exchange log metadata.</p>
     *
     * @param username OpenDaylight username
     * @param password OpenDaylight password
     * @return complete HTTP Basic Authorization header value
     */
    private static String basic(final String username, final String password) {
        final String token = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * <p>Percent-encodes one RESTCONF list-key value as a UTF-8 path segment.</p>
     *
     * <p>The encoding performs these steps:</p>
     * <ol>
     *   <li>Applies {@link URLEncoder#encode(String, java.nio.charset.Charset)} with UTF-8.</li>
     *   <li>Replaces form-encoding plus signs with {@code %20} so spaces remain URI path encoding.</li>
     *   <li>Returns the encoded segment for insertion into a RESTCONF resource path.</li>
     * </ol>
     *
     * @param value topology or flow list-key value to encode
     * @return percent-encoded UTF-8 path segment
     * @throws NullPointerException if {@code value} is {@code null}
     */
    private static String encodePath(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
