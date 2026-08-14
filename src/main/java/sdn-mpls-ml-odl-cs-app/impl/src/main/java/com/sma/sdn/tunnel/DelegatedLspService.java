/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.tunnel;

import com.sma.sdn.config.AppConfig;
import com.sma.sdn.http.OdlOperationsClient;
import com.sma.sdn.http.OdlRestconfDataClient;
import com.sma.sdn.metrics.SdnMplsMlMetrics;
import com.sma.sdn.model.CalculatedPath;
import com.sma.sdn.model.DelegatedLspRecord;
import com.sma.sdn.model.EroSubobject;
import com.sma.sdn.model.PathConstraints;
import com.sma.sdn.model.PcepReportedLspSnapshot;
import com.sma.sdn.model.TunnelDirection;
import com.sma.sdn.model.UpdateLspRequest;
import com.sma.sdn.model.UpdateLspResult;
import com.sma.sdn.observability.LogContext;
import com.sma.sdn.observability.StructuredLogger;
import com.sma.sdn.registry.DelegatedLspRegistry;
import com.sma.sdn.serialization.xml.PcepTopologyXmlDeserializer;
import com.sma.sdn.serialization.xml.UpdateLspRequestXmlSerializer;
import com.sma.sdn.serialization.xml.UpdateLspResponseXmlDeserializer;
import com.sma.sdn.topology.BandwidthTranslator;
import com.sma.sdn.util.RetryPolicy;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Descubre y actualiza exclusivamente LSP RSVP-TE preexistentes que los routers XR delegaron al controlador ODL.
 */
public final class DelegatedLspService {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(DelegatedLspService.class);

    private final AppConfig config;
    private final OdlRestconfDataClient dataClient;
    private final OdlOperationsClient operationsClient;
    private final PcepTopologyXmlDeserializer topologyDeserializer;
    private final UpdateLspRequestXmlSerializer requestSerializer;
    private final UpdateLspResponseXmlDeserializer responseDeserializer;
    private final DelegatedLspRegistry registry;
    private final RetryPolicy retryPolicy;
    private final SdnMplsMlMetrics metrics;

    /**
     * Crea el servicio que administra el estado y las actualizaciones de LSP delegados.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida todas las dependencias de comunicacion, serializacion y registro.</li>
     *   <li>Conserva la politica de reintentos usada para confirmar cambios asincronos.</li>
     *   <li>Prepara el servicio sin ejecutar llamadas hasta {@link #initialize()}.</li>
     * </ol>
     *
     * @param config configuracion de identidades y endpoints ODL
     * @param dataClient cliente RESTCONF para leer la topologia PCEP
     * @param operationsClient cliente RPC para invocar {@code update-lsp}
     * @param topologyDeserializer deserializador de la topologia PCEP
     * @param requestSerializer serializador de solicitudes de actualizacion
     * @param responseDeserializer clasificador de respuestas de actualizacion
     * @param registry registro de LSP delegados
     * @param retryPolicy politica de espera para confirmacion operativa
     * @param metrics contadores internos del flujo
     */
    public DelegatedLspService(
            final AppConfig config,
            final OdlRestconfDataClient dataClient,
            final OdlOperationsClient operationsClient,
            final PcepTopologyXmlDeserializer topologyDeserializer,
            final UpdateLspRequestXmlSerializer requestSerializer,
            final UpdateLspResponseXmlDeserializer responseDeserializer,
            final DelegatedLspRegistry registry,
            final RetryPolicy retryPolicy,
            final SdnMplsMlMetrics metrics) {
        this.config = Objects.requireNonNull(config, "config");
        this.dataClient = Objects.requireNonNull(dataClient, "dataClient");
        this.operationsClient = Objects.requireNonNull(operationsClient, "operationsClient");
        this.topologyDeserializer = Objects.requireNonNull(topologyDeserializer, "topologyDeserializer");
        this.requestSerializer = Objects.requireNonNull(requestSerializer, "requestSerializer");
        this.responseDeserializer = Objects.requireNonNull(responseDeserializer, "responseDeserializer");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /**
     * Descubre ambos LSP configurados y detiene el arranque si cualquiera no esta delegado, activo o identificable.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Lee una unica instantanea completa de {@code pcep-topology}.</li>
     *   <li>Busca las identidades PCC y nombre configuradas para ambas direcciones.</li>
     *   <li>Valida PLSP ID, extremos, delegacion, administracion y estado operativo.</li>
     *   <li>Publica ambos registros de forma atomica.</li>
     * </ol>
     *
     * @throws IllegalStateException si falta o no es utilizable algun LSP requerido
     */
    public void initialize() {
        LOG.info(
                "delegated_lsp_discovery_started",
                "initialize",
                "Se inicio el descubrimiento obligatorio de los LSP delegados.",
                StructuredLogger.fields(
                        "forward_lsp_name", config.forwardLspName(),
                        "reverse_lsp_name", config.reverseLspName(),
                        "pcep_topology_id", config.pcepTopologyId()));
        replaceFromSnapshots(readSnapshots());
        requireDelegatedLsp(config.headendToTailend().directionKey());
        requireDelegatedLsp(config.tailendToHeadend().directionKey());
        LOG.info(
                "delegated_lsp_discovery_completed",
                "initialize",
                "Se descubrieron y validaron ambos LSP delegados.",
                StructuredLogger.fields(
                        "forward_lsp_name", config.forwardLspName(),
                        "reverse_lsp_name", config.reverseLspName()));
    }

    /**
     * Vuelve a leer la topologia PCEP y devuelve el LSP valido de una direccion concreta.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Obtiene y deserializa el estado PCEP completo.</li>
     *   <li>Revalida los dos LSP obligatorios y reemplaza el registro.</li>
     *   <li>Exige el registro correspondiente a la direccion solicitada.</li>
     * </ol>
     *
     * @param directionKey clave configurada de direccion
     * @return estado actualizado del LSP delegado
     * @throws IllegalStateException si la topologia no confirma un estado utilizable
     */
    public DelegatedLspRecord refreshDirection(final String directionKey) {
        LOG.debug(
                "delegated_lsp_refresh_started",
                "refreshDirection",
                "Se inicio la actualizacion del estado reportado del LSP delegado.",
                StructuredLogger.fields("direction_key", directionKey));
        replaceFromSnapshots(readSnapshots());
        final DelegatedLspRecord record = registry.requireByDirectionKey(directionKey);
        LOG.debug(
                "delegated_lsp_refresh_completed",
                "refreshDirection",
                "Se actualizo y valido el estado reportado del LSP delegado.",
                lspFields(record));
        return record;
    }

    /**
     * Obtiene el ultimo LSP delegado valido registrado para una direccion.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Consulta el indice por clave de direccion.</li>
     *   <li>Exige que el estado permita actualizaciones.</li>
     * </ol>
     *
     * @param directionKey clave configurada de direccion
     * @return registro valido del LSP delegado
     * @throws IllegalStateException si el registro falta o no es utilizable
     */
    public DelegatedLspRecord requireDelegatedLsp(final String directionKey) {
        final DelegatedLspRecord record = registry.requireByDirectionKey(directionKey);
        LOG.trace(
                "delegated_lsp_required",
                "requireDelegatedLsp",
                "Se obtuvo un LSP delegado valido desde el registro.",
                lspFields(record));
        return record;
    }

    /**
     * Indica si la ERO y el ancho de banda solicitados ya coinciden con el estado activo reportado por PCEP.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Obtiene el LSP valido de la direccion.</li>
     *   <li>Codifica el ancho de banda solicitado como float32 Base64.</li>
     *   <li>Compara exactamente el orden de la ERO y el valor de ancho de banda.</li>
     * </ol>
     *
     * @param direction direccion logica y extremos del tunel
     * @param path camino calculado con ERO estricta
     * @param constraints restricciones de politica aplicadas
     * @return {@code true} si no es necesario invocar {@code update-lsp}
     */
    public boolean activeStateMatches(
            final TunnelDirection direction,
            final CalculatedPath path,
            final PathConstraints constraints) {
        final DelegatedLspRecord record = requireDelegatedLsp(direction.directionKey());
        final String bandwidth = BandwidthTranslator.kbpsToPcepBandwidthBase64Float32(
                constraints.requestedBandwidthKbps());
        final boolean matches = stateMatches(record, path.eroSubobjects(), bandwidth);
        LOG.debug(
                "delegated_lsp_state_compared",
                "activeStateMatches",
                "Se compararon la ERO y el ancho de banda solicitados con el estado PCEP activo.",
                StructuredLogger.fields(
                        "lsp_name", record.lspName(),
                        "plsp_id", record.plspId(),
                        "state_matches", matches,
                        "requested_ero", path.eroSubobjects(),
                        "active_ero", record.activeEro(),
                        "requested_bandwidth_base64", bandwidth,
                        "reported_bandwidth_base64", record.reportedBandwidthBase64()));
        return matches;
    }

    /**
     * Actualiza un LSP delegado existente y no considera exitosa la operacion hasta confirmarla en PCEP.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Resuelve el PLSP ID actual y codifica el ancho de banda requerido.</li>
     *   <li>Envia exclusivamente la operacion {@code update-lsp} con ERO estricta.</li>
     *   <li>Clasifica la respuesta y rechaza estados HTTP o errores PCEP definitivos.</li>
     *   <li>Refresca la topologia hasta confirmar estado valido, ERO y ancho de banda.</li>
     * </ol>
     *
     * @param direction direccion del LSP delegado
     * @param path camino calculado que contiene la nueva ERO
     * @param constraints restricciones que contienen el ancho de banda solicitado
     * @return resultado confirmado de la actualizacion
     * @throws IllegalStateException si ODL rechaza la solicitud o PCEP no confirma el cambio
     */
    public synchronized UpdateLspResult updateDelegatedLsp(
            final TunnelDirection direction,
            final CalculatedPath path,
            final PathConstraints constraints) {
        final DelegatedLspRecord current = requireDelegatedLsp(direction.directionKey());
        try (LogContext ignored = LogContext.open(Map.of(
                "lsp_name", current.lspName(),
                "pcc_node", current.pccNode(),
                "plsp_id", Long.toString(current.plspId())))) {
            final String bandwidth = BandwidthTranslator.kbpsToPcepBandwidthBase64Float32(
                    constraints.requestedBandwidthKbps());
            LOG.info(
                    "delegated_lsp_update_started",
                    "updateDelegatedLsp",
                    "Se inicio la actualizacion del camino y ancho de banda del LSP delegado.",
                    StructuredLogger.fields(
                            "requested_bandwidth_kbps", constraints.requestedBandwidthKbps(),
                            "requested_bandwidth_base64", bandwidth,
                            "requested_ero", path.eroSubobjects()));
            if (stateMatches(current, path.eroSubobjects(), bandwidth)) {
                metrics.increment("sma_update_lsp_skipped_no_change_total");
                LOG.debug(
                        "delegated_lsp_update_skipped",
                        "updateDelegatedLsp",
                        "Se omitio update-lsp porque el estado activo ya coincide con la solicitud.",
                        lspFields(current));
                return new UpdateLspResult(true, false, false, null);
            }

            final UpdateLspRequest request = new UpdateLspRequest(
                    current.pccNode(), current.lspName(), current.plspId(), bandwidth,
                    path.eroSubobjects(), config.pcepTopologyId());
            metrics.increment("sma_update_lsp_request_total");
            LOG.debug(
                    "delegated_lsp_request_prepared",
                    "updateDelegatedLsp",
                    "Se preparo la estructura update-lsp con el PLSP ID descubierto en tiempo de ejecucion.",
                    StructuredLogger.fields(
                            "pcep_topology_id", request.pcepTopologyId(),
                            "ero_subobject_count", request.eroSubobjects().size(),
                            "bandwidth_base64", request.bandwidthBase64()));

            final HttpResponse<String> response;
            try {
                response = operationsClient.updateLsp(requestSerializer.serialize(request));
            } catch (RuntimeException updateFailure) {
                metrics.increment("sma_update_lsp_failure_total");
                LOG.error(
                        "delegated_lsp_update_transport_failed",
                        "updateDelegatedLsp",
                        "Fallo el transporte de la solicitud update-lsp.",
                        lspFields(current),
                        updateFailure);
                refreshAfterFailedAttempt(updateFailure);
                throw updateFailure;
            }

            final UpdateLspResult initialResult = responseDeserializer.deserialize(response);
            LOG.debug(
                    "delegated_lsp_update_response_classified",
                    "updateDelegatedLsp",
                    "Se clasifico la respuesta inmediata de update-lsp.",
                    StructuredLogger.fields(
                            "http_status", response.statusCode(),
                            "success", initialResult.success(),
                            "provisional_success", initialResult.provisionalSuccess(),
                            "hard_failure", initialResult.hardFailure(),
                            "failure_reason", initialResult.failureReason()));
            if (response.statusCode() != 200 || initialResult.hardFailure()) {
                final IllegalStateException failure = new IllegalStateException(
                        "update-lsp fallo para " + current.lspName() + ": "
                                + (initialResult.failureReason() == null
                                        ? "HTTP " + response.statusCode() : initialResult.failureReason()));
                metrics.increment("sma_update_lsp_failure_total");
                refreshAfterFailedAttempt(failure);
                throw failure;
            }

            try {
                final DelegatedLspRecord confirmed = retryPolicy.retryUntilPresent(() -> {
                    try {
                        final DelegatedLspRecord refreshed = refreshDirection(direction.directionKey());
                        return stateMatches(refreshed, path.eroSubobjects(), bandwidth)
                                ? Optional.of(refreshed) : Optional.empty();
                    } catch (RuntimeException refreshFailure) {
                        LOG.warn(
                                "delegated_lsp_confirmation_refresh_failed",
                                "updateDelegatedLsp",
                                "Fallo una lectura recuperable mientras se confirmaba update-lsp.",
                                lspFields(current),
                                refreshFailure);
                        return Optional.empty();
                    }
                }, "La topologia PCEP no confirmo update-lsp para " + current.lspName());
                registry.updateAfterSuccessfulUpdate(
                        direction.directionKey(), confirmed.activeEro(),
                        confirmed.reportedBandwidthBase64(), Instant.now());
                metrics.increment("sma_update_lsp_success_total");
                LOG.info(
                        "delegated_lsp_update_confirmed",
                        "updateDelegatedLsp",
                        "La topologia PCEP confirmo la actualizacion del LSP delegado.",
                        StructuredLogger.fields(
                                "active_ero", confirmed.activeEro(),
                                "reported_bandwidth_base64", confirmed.reportedBandwidthBase64(),
                                "provisional_response", initialResult.provisionalSuccess()));
                return new UpdateLspResult(true, initialResult.provisionalSuccess(), false, null);
            } catch (RuntimeException confirmationFailure) {
                metrics.increment("sma_update_lsp_failure_total");
                LOG.error(
                        "delegated_lsp_update_not_confirmed",
                        "updateDelegatedLsp",
                        "No fue posible confirmar la actualizacion del LSP delegado.",
                        lspFields(current),
                        confirmationFailure);
                throw confirmationFailure;
            }
        }
    }

    private List<PcepReportedLspSnapshot> readSnapshots() {
        metrics.increment("sma_pcep_topology_refresh_total");
        LOG.debug(
                "pcep_topology_read_started",
                "readSnapshots",
                "Se inicio la lectura completa de la topologia PCEP.",
                StructuredLogger.fields("topology_id", config.pcepTopologyId()));
        try {
            final HttpResponse<String> response = dataClient.getPcepTopology(config.pcepTopologyId());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("La lectura de la topologia PCEP fallo: HTTP "
                        + response.statusCode());
            }
            final List<PcepReportedLspSnapshot> snapshots = topologyDeserializer.deserialize(response.body());
            LOG.debug(
                    "pcep_topology_read_completed",
                    "readSnapshots",
                    "Se leyeron y deserializaron los LSP reportados por PCEP.",
                    StructuredLogger.fields(
                            "http_status", response.statusCode(),
                            "response_body_bytes", response.body() == null ? 0 : response.body().length(),
                            "reported_lsp_count", snapshots.size()));
            return snapshots;
        } catch (RuntimeException failure) {
            metrics.increment("sma_pcep_topology_refresh_failure_total");
            LOG.error(
                    "pcep_topology_read_failed",
                    "readSnapshots",
                    "Fallo la lectura o deserializacion de la topologia PCEP.",
                    StructuredLogger.fields("topology_id", config.pcepTopologyId()),
                    failure);
            throw failure;
        }
    }

    private void replaceFromSnapshots(final List<PcepReportedLspSnapshot> snapshots) {
        final Set<String> actualNames = snapshots.stream()
                .map(PcepReportedLspSnapshot::name)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        final List<DelegatedLspRecord> records = new ArrayList<>();
        records.add(requireConfiguredSnapshot(
                snapshots, config.headendToTailend(), config.forwardLspName(),
                config.forwardTunnelInterface(), actualNames));
        records.add(requireConfiguredSnapshot(
                snapshots, config.tailendToHeadend(), config.reverseLspName(),
                config.reverseTunnelInterface(), actualNames));
        registry.replaceAll(records);
        LOG.debug(
                "delegated_lsp_registry_replaced",
                "replaceFromSnapshots",
                "Se reemplazo atomicamente el registro de LSP delegados.",
                StructuredLogger.fields(
                        "reported_lsp_count", snapshots.size(),
                        "registered_lsp_count", records.size(),
                        "reported_lsp_names", actualNames));
    }

    private DelegatedLspRecord requireConfiguredSnapshot(
            final List<PcepReportedLspSnapshot> snapshots,
            final TunnelDirection direction,
            final String expectedName,
            final String tunnelInterface,
            final Set<String> actualNames) {
        final PcepReportedLspSnapshot snapshot = snapshots.stream()
                .filter(value -> direction.source().pccNode().equals(value.pccNode()))
                .filter(value -> expectedName.equals(value.name()))
                .findFirst()
                .orElseThrow(() -> {
                    metrics.increment("sma_delegated_lsp_missing_total");
                    return new IllegalStateException("Falta el LSP delegado: PCC esperado="
                            + direction.source().pccNode() + ", nombre=" + expectedName
                            + ", nombres reportados=" + actualNames);
                });
        final Instant now = Instant.now();
        final DelegatedLspRecord record = new DelegatedLspRecord(
                direction.directionKey(), snapshot.pccNode(), snapshot.name(), tunnelInterface,
                snapshot.sourceRouterId(), snapshot.destinationRouterId(), snapshot.plspId(),
                snapshot.tunnelId(), snapshot.lspId(), snapshot.delegate(), snapshot.administrative(),
                snapshot.operational(), snapshot.ero(), snapshot.bandwidthBase64(), now, now);
        if (!Objects.equals(direction.source().routerId(), record.sourceRouterId())
                || !Objects.equals(direction.destination().routerId(), record.destinationRouterId())) {
            throw new IllegalStateException("Los extremos del LSP delegado no coinciden con la configuracion para "
                    + expectedName + ": reportado=" + record.sourceRouterId() + "->"
                    + record.destinationRouterId() + ", esperado=" + direction.source().routerId()
                    + "->" + direction.destination().routerId());
        }
        if (!record.isValidForUpdate()) {
            throw new IllegalStateException("El LSP no esta delegado y operativo: PCC="
                    + record.pccNode() + ", nombre=" + record.lspName() + ", plspId=" + record.plspId()
                    + ", delegado=" + record.delegated() + ", administrativo="
                    + record.administrativeUp() + ", operacional=" + record.operationalState());
        }
        metrics.increment("sma_delegated_lsp_discovered_total");
        LOG.debug(
                "delegated_lsp_snapshot_validated",
                "requireConfiguredSnapshot",
                "Se encontro y valido un LSP delegado configurado.",
                lspFields(record));
        return record;
    }

    private void refreshAfterFailedAttempt(final RuntimeException originalFailure) {
        try {
            replaceFromSnapshots(readSnapshots());
        } catch (RuntimeException refreshFailure) {
            originalFailure.addSuppressed(refreshFailure);
            LOG.warn(
                    "failed_attempt_refresh_failed",
                    "refreshAfterFailedAttempt",
                    "Fallo la actualizacion defensiva de PCEP posterior al intento fallido.",
                    java.util.Map.of(),
                    refreshFailure);
        }
    }

    private static Map<String, Object> lspFields(final DelegatedLspRecord record) {
        return StructuredLogger.fields(
                "direction_key", record.directionKey(),
                "pcc_node", record.pccNode(),
                "lsp_name", record.lspName(),
                "tunnel_interface", record.tunnelInterfaceName(),
                "plsp_id", record.plspId(),
                "tunnel_id", record.tunnelId(),
                "lsp_id", record.lspId(),
                "delegated", record.delegated(),
                "administrative_up", record.administrativeUp(),
                "operational_state", record.operationalState(),
                "active_ero", record.activeEro(),
                "reported_bandwidth_base64", record.reportedBandwidthBase64());
    }

    private static boolean stateMatches(
            final DelegatedLspRecord record,
            final List<EroSubobject> ero,
            final String bandwidthBase64) {
        return record.activeEro().equals(ero)
                && Objects.equals(record.reportedBandwidthBase64(), bandwidthBase64);
    }
}
