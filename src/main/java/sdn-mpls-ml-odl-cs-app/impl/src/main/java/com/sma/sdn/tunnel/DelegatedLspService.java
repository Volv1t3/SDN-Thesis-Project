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
 * <p>Discovers and updates existing delegated PCEP LSPs. The topology parser produces reported snapshots; this service
 * matches them to configured forward and reverse directions, validates their endpoints and state, and stores directional
 * records in the registry. Updates poll for ERO convergence; a bandwidth mismatch is logged but does not prevent successful return.</p>
 *
 * <p>{@link DirectionalLspApplicationService} refreshes these records and delegates update-lsp
 * requests here. The service uses {@link PcepTopologyXmlDeserializer} snapshots to rebuild
 * {@link DelegatedLspRegistry} state and {@link RetryPolicy} to poll for a reported route.</p>
 */
public final class DelegatedLspService {
    /** <p>Logger for discovery and update events.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(DelegatedLspService.class);

    /** <p>Configuration of topology and directional LSP identities.</p> */
    private final AppConfig config;
    /** <p>RESTCONF client for PCEP topology reads.</p> */
    private final OdlRestconfDataClient dataClient;
    /** <p>RESTS client for update-lsp operations.</p> */
    private final OdlOperationsClient operationsClient;
    /** <p>Parser for reported PCEP snapshots.</p> */
    private final PcepTopologyXmlDeserializer topologyDeserializer;
    /** <p>Serializer for update-lsp requests.</p> */
    private final UpdateLspRequestXmlSerializer requestSerializer;
    /** <p>Classifier of update-lsp responses.</p> */
    private final UpdateLspResponseXmlDeserializer responseDeserializer;
    /** <p>Registry of validated directional LSPs.</p> */
    private final DelegatedLspRegistry registry;
    /** <p>Policy for polling PCEP convergence.</p> */
    private final RetryPolicy retryPolicy;
    /** <p>Collector for discovery, update, and convergence metrics.</p> */
    private final SdnMplsMlMetrics metrics;

    /**
     * <p>Construct the service with its required collaborators.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Require and retain the configuration, clients, parsers, registry, retry policy, and metrics.</li>
     * </ol>
     *
     * @param config PCEP and directional tunnel configuration
     * @param dataClient PCEP topology data client
     * @param operationsClient update-lsp operations client
     * @param topologyDeserializer PCEP topology parser
     * @param requestSerializer update-lsp serializer
     * @param responseDeserializer update-lsp response classifier
     * @param registry registry of managed LSP records
     * @param retryPolicy convergence polling policy
     * @param metrics metrics collector
     * @throws NullPointerException if a collaborator is null
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
     * <p>Discover both configured delegated LSPs for startup.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Read PCEP snapshots, validate and register the forward and reverse records, and require each directional record.</li>
     * </ol>
     *
     * @throws IllegalStateException if discovery or validation fails
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

        /*First we call to replace from snapshots which performs a call that polls the entire pcep topology and uses
        * the return body to figureotu the lsps that are delegatted per nodes and transform them into the corresponding
        * {@link PcepReportedLspSnapshot} instances. This will only execute in general if the initial BGP-LS topological refresh
        * passed, as it will raise exceptions there if it cannot detect the required nodes in the returned nodes from that
        * call first.*/
        replaceFromSnapshots(readSnapshots());

        /**
         * <p>Require the registered forward and reverse directional LSPs after replacing the
         * registry from the PCC reports. The returned records are not needed here; the checks
         * establish that both configured directions are available for subsequent operations.</p>
         */
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
     * <p>Refresh both configured LSP records from PCEP and return one direction.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Read and validate the topology snapshot, replace both records, and require the requested directional record.</li>
     * </ol>
     *
     * @param directionKey configured direction identifier
     * @return refreshed updateable LSP record
     * @throws IllegalStateException if discovery or validation fails
     */
    public DelegatedLspRecord refreshDirection(final String directionKey) {
        /*This function performs an update upon the registry based on a direction key which is the serialized way of
        * representing the direction from either lsr1_to_lsr4 or lsr4_to_lsr1. Callers including
        * DirectionalLspApplicationService and TunnelOperationCoordinator use this method to refresh both configured
        * LSP records and return the record corresponding to the requested direction identifier.*/
        LOG.debug(
                "delegated_lsp_refresh_started",
                "refreshDirection",
                "Se inicio la actualizacion del estado reportado del LSP delegado.",
                StructuredLogger.fields("direction_key", directionKey));

        /*Whole PCEP Topology refresh and filter down to the specific tunnels that we need to know whih are based on the directions
        * that the application knows about. In this fucntion we are using the updated records to return only the
        * tunnel data for the direction key requesed */
        replaceFromSnapshots(readSnapshots());
        /*Extract the required direction*/
        final DelegatedLspRecord record = registry.requireByDirectionKey(directionKey);
        LOG.debug(
                "delegated_lsp_refresh_completed",
                "refreshDirection",
                "Se actualizo y valido el estado reportado del LSP delegado.",
                lspFields(record));
        return record;
    }

    /**
     * <p>Return an updated record from the registry without a new PCEP read.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Require the record by direction, log its identity and state, and return it.</li>
     * </ol>
     *
     * @param directionKey configured direction identifier
     * @return registered updateable LSP
     * @throws IllegalStateException if the record is missing or not updateable
     */
    public DelegatedLspRecord requireDelegatedLsp(final String directionKey) {
        /*This is a less destructive operation as a whole topology update, only providing the current lsp record for that dirction*/
        final DelegatedLspRecord record = registry.requireByDirectionKey(directionKey);
        LOG.trace(
                "delegated_lsp_required",
                "requireDelegatedLsp",
                "Se obtuvo un LSP delegado valido desde el registro.",
                lspFields(record));
        return record;
    }

    /**
     * <p>Update an existing delegated LSP and poll PCEP until the requested ERO appears. A bandwidth mismatch is logged but does not prevent a successful return. A currently matching record skips the RPC and returns success with HTTP status zero.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Require the current LSP and encode requested bandwidth; skip the operation when the registered state already matches.</li>
     *   <li>Serialize and send update-lsp, then classify its immediate response; transport and rejected-response failures trigger a defensive refresh.</li>
     *   <li>Use the retry policy to refresh PCEP until the reported ERO matches, treating refresh failures as unsuccessful polls.</li>
     *   <li>Record the reported state, metrics, and any bandwidth mismatch, and return the update result.</li>
     * </ol>
     *
     * @param direction configured tunnel direction
     * @param path calculated desired ERO
     * @param constraints requested bandwidth and priorities
     * @return successful no-change or ERO-confirmed update result
     * @throws RuntimeException if transport, response parsing, rejection, or confirmation fails
     */
    public synchronized UpdateLspResult updateDelegatedLsp(
            final TunnelDirection direction, /*Update direction record*/
            final CalculatedPath path, /*Calculated path coming from an outside source already translated*/
            final PathConstraints constraints /*Constraints to be applied into the tunnel*/) {

        /*First we extract the already present state, this can alow us to identify if the update is not going to
        * affect the state and therefore should not be executed. This is a defense against reprocessing*/
        final DelegatedLspRecord current = requireDelegatedLsp(direction.directionKey());
        try (LogContext ignored = LogContext.open(Map.of(
                "lsp_name", current.lspName(),
                "pcc_node", current.pccNode(),
                "plsp_id", Long.toString(current.plspId())))) {
            /*Attempt to perform a translation between the requested bandwidth and the format expected by the PCEP protocol*/
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

            /*As we know if the bandwidth and the path already match then our model for traffic engineering has already
            * been satisfied, therefore, there is no implementation needed.*/
            if (stateMatches(current, path.eroSubobjects(), bandwidth)) {
                metrics.increment("sma_update_lsp_skipped_no_change_total");
                metrics.incrementCounter("sma_lsp_path_verification_total",
                        Map.of("outcome", "match", "direction", direction.directionKey()));
                LOG.debug(
                        "delegated_lsp_update_skipped",
                        "updateDelegatedLsp",
                        "Se omitio update-lsp porque el estado activo ya coincide con la solicitud.",
                        lspFields(current));
                /*This just fakes a proper return value for the update operation.       */
                return new UpdateLspResult(true, false, false, null, 0);
            }

            /* If in the end the state did not match then we need to perform an Update LSP request through the corresponding
            * request object format, which means translating the path that has been calculated into its eroSubojects*/
            final UpdateLspRequest request = new UpdateLspRequest(
                    current.pccNode(), current.lspName(), current.plspId(), bandwidth,
                    path.eroSubobjects(), config.pcepTopologyId());
            metrics.increment("sma_update_lsp_request_total");
            final long updateStartedAt = System.nanoTime(); /*Get the monotonic clock locally for operation timing*/
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
                /**
                 * <p>Serialize the {@link UpdateLspRequest} with {@link UpdateLspRequestXmlSerializer},
                 * then send the resulting XML to the operations client for the update-lsp RPC.</p>
                 */
                response = operationsClient.updateLsp(requestSerializer.serialize(request));
            } catch (RuntimeException updateFailure) {
                metrics.increment("sma_update_lsp_failure_total");
                observeUpdateDuration("transport_error", direction.directionKey(), updateStartedAt);
                LOG.error(
                        "delegated_lsp_update_transport_failed",
                        "updateDelegatedLsp",
                        "Fallo el transporte de la solicitud update-lsp.",
                        lspFields(current),
                        updateFailure);
                /*Upon an update failure we refresh the topology to note if the changes were done, or if the required process
                * needs to be done again depending on the failure type*/
                refreshAfterFailedAttempt(updateFailure);
                throw updateFailure;
            }

            /*Upon a correct result, the initialResult represents the information coming from a deserialization attempt upon the response,
            * again through an specialized deserializer.*/
            final UpdateLspResult initialResult;
            try {
                initialResult = responseDeserializer.deserialize(response);
            } catch (RuntimeException parseFailure) {
                metrics.increment("sma_update_lsp_failure_total");
                observeUpdateDuration("deserialization_error", direction.directionKey(), updateStartedAt);
                throw parseFailure;
            }
            observeUpdateDuration(response.statusCode() >= 200 && response.statusCode() < 300
                    ? "success" : "http_error", direction.directionKey(), updateStartedAt);
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
            if (!initialResult.success() || initialResult.hardFailure()) {
                final IllegalStateException failure = new IllegalStateException(
                        "update-lsp fallo para " + current.lspName() + ": "
                                + (initialResult.failureReason() == null
                                        ? "HTTP " + response.statusCode() : initialResult.failureReason()));
                metrics.increment("sma_update_lsp_failure_total");
                metrics.incrementCounter("sma_lsp_path_verification_total",
                        Map.of("outcome", "unavailable", "direction", direction.directionKey()));
                refreshAfterFailedAttempt(failure);
                throw failure;
            }

            // An accepted RPC is not enough: poll reported PCEP state until the requested ERO appears.
            /*In this case what we attempt to do is to retry until the specific updated lsp state is shown on the reported lsps, the idea is simply
            * to poll the information, refresh the direction specific to the direction we just updated and attempt
            * to find the new state based on a retry policy*/
            final long convergenceStartedAt = System.nanoTime();
            try {
                final DelegatedLspRecord confirmed = retryPolicy.retryUntilPresent(() -> {
                    try {
                        final DelegatedLspRecord refreshed = refreshDirection(direction.directionKey());
                        return refreshed.activeEro().equals(path.eroSubobjects())
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

                /*This updates directly the local state with the confirmed information the tunnel of said direction that we were
                * updating, this is done immediately as to allow the system to reflect the final state of the update lsp process*/
                registry.updateAfterSuccessfulUpdate(
                        direction.directionKey(), confirmed.activeEro(),
                        confirmed.reportedBandwidthBase64(), Instant.now());
                metrics.increment("sma_update_lsp_success_total");
                final boolean eroConfirmed = confirmed.activeEro().equals(path.eroSubobjects());
                metrics.incrementCounter("sma_lsp_path_verification_total",
                        Map.of("outcome", eroConfirmed ? "match" : "mismatch",
                                "direction", direction.directionKey()));
                metrics.observeHistogram("sma_lsp_convergence_duration_seconds",
                        Map.of("outcome", eroConfirmed ? "confirmed" : "mismatch",
                                "direction", direction.directionKey()),
                        elapsedSeconds(convergenceStartedAt));
                // Bandwidth is checked separately and a mismatch does not fail ERO confirmation.
                final boolean bandwidthConfirmed = Objects.equals(
                        confirmed.reportedBandwidthBase64(), bandwidth);
                if (!bandwidthConfirmed) {
                    LOG.warn(
                            "delegated_lsp_update_bandwidth_unconfirmed",
                            "updateDelegatedLsp",
                            "PCEP confirmo la ERO, pero reporto un ancho de banda distinto al solicitado.",
                            StructuredLogger.fields(
                                    "requested_bandwidth_base64", bandwidth,
                                    "reported_bandwidth_base64", confirmed.reportedBandwidthBase64()),
                            null);
                }
                LOG.info(
                        "delegated_lsp_update_confirmed",
                        "updateDelegatedLsp",
                        "La topologia PCEP confirmo la actualizacion del LSP delegado.",
                        StructuredLogger.fields(
                                "active_ero", confirmed.activeEro(),
                                "reported_bandwidth_base64", confirmed.reportedBandwidthBase64(),
                                "provisional_response", initialResult.provisionalSuccess()));
                /*Return the final result upon complete confirmation*/
                return new UpdateLspResult(
                        true, initialResult.provisionalSuccess(), false, null, initialResult.httpStatus());
            } catch (RuntimeException confirmationFailure) {
                metrics.increment("sma_update_lsp_failure_total");
                metrics.incrementCounter("sma_lsp_path_verification_total",
                        Map.of("outcome", "unavailable", "direction", direction.directionKey()));
                metrics.observeHistogram("sma_lsp_convergence_duration_seconds",
                        Map.of("outcome", "timeout", "direction", direction.directionKey()),
                        elapsedSeconds(convergenceStartedAt));
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

    /**
     * <p>Read and parse all reported LSPs in the configured PCEP topology.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Count the refresh, reject non-2xx HTTP responses, and parse the response body.</li>
     *   <li>Log and count runtime failures before rethrowing.</li>
     * </ol>
     *
     * @return parsed reported LSP snapshots
     * @throws RuntimeException if the read or parsing fails
     */
    private List<PcepReportedLspSnapshot> readSnapshots() {

        /*This operation is done at the initialization phase to read and update the reported lsps from all nodes that are
        * pccs in the network. This is done during initialization and in the replace from snapshot mechanism that cleans
        * the records, reads again and polls the current state as reported by the PCCs */
        metrics.increment("sma_pcep_topology_refresh_total");
        LOG.debug(
                "pcep_topology_read_started",
                "readSnapshots",
                "Se inicio la lectura completa de la topologia PCEP.",
                StructuredLogger.fields("topology_id", config.pcepTopologyId()));
        try {
            /*This performs a network call to the pcep topology MDSAL model that is exposed through the OdlRestconf client
            * as these are data access operations and not RPC*/
            final HttpResponse<String> response = dataClient.getPcepTopology(config.pcepTopologyId());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("La lectura de la topologia PCEP fallo: HTTP "
                        + response.statusCode());
            }
            /*This is the important step, if we got a proper response this is going to use the topology deserializer to extraact
            * from the whole topology response which is often huge, the corresponding nod elements that within them have
            * a path computation client block, effectively filtering to those that are actual PCCs at the topology level*/
            final List<PcepReportedLspSnapshot> snapshots = topologyDeserializer.deserialize(response.body());
            LOG.debug(
                    "pcep_topology_read_completed",
                    "readSnapshots",
                    "Se leyeron y deserializaron los LSP reportados por PCEP.",
                    StructuredLogger.fields(
                            "http_status", response.statusCode(),
                            "response_body_bytes", response.body() == null ? 0 : response.body().length(),
                            "reported_lsp_count", snapshots.size()));
            /**
             * <p>Return the {@link PcepReportedLspSnapshot} records parsed from the configured
             * PCEP topology; selection of the configured directional LSPs happens later.</p>
             */
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

    /**
     * <p>Resolve and register both configured LSP directions from a PCEP snapshot.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Collect reported names for diagnostics and validate the forward and reverse configured records.</li>
     *   <li>Replace the registry once both records have been constructed.</li>
     * </ol>
     *
     * @param snapshots reported LSP snapshots
     * @throws IllegalStateException if a configured LSP is missing or invalid
     */
    private void replaceFromSnapshots(final List<PcepReportedLspSnapshot> snapshots) {

        /*This i the process of translating the correspodning reported LSPS from their reported state into the actual
        * DelegatedLSPRecords that are usedby the program*/
        final Set<String> actualNames = snapshots.stream()
                .map(PcepReportedLspSnapshot::name)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // Both configured directions must validate before the registry snapshot is replaced.
        final List<DelegatedLspRecord> records = new ArrayList<>();

        /*In this step we are validating that the snapshots that we know have some kind of record that matches the corresponding
        * tunnels that we are expecting to have in the program, i.e., that the reported lsps match the directionality and router based
        * configuration that we expect.*/
        records.add(requireConfiguredSnapshot(
                snapshots, config.headendToTailend(), config.forwardLspName(),
                config.forwardTunnelInterface(), actualNames));
        records.add(requireConfiguredSnapshot(
                snapshots, config.tailendToHeadend(), config.reverseLspName(),
                config.reverseTunnelInterface(), actualNames));

        /*We clean the register based on the snapshots we gathered and record only those that actually match the
        * tunnels that we need*/
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

    /**
     * <p>Match one direction to a reported PCC and LSP name and produce an updateable registry record.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Select the first matching source PCC and LSP name, counting and rejecting a missing match.</li>
     *   <li>Build a directional record and validate its router endpoints and updateable state.</li>
     * </ol>
     *
     * @param snapshots reported PCEP LSPs
     * @param direction configured source and destination direction
     * @param expectedName configured symbolic LSP name
     * @param tunnelInterface configured tunnel interface name
     * @param actualNames reported names used for diagnostics
     * @return validated directional record
     * @throws IllegalStateException if a matching LSP is absent or invalid
     */
    private DelegatedLspRecord requireConfiguredSnapshot(
            final List<PcepReportedLspSnapshot> snapshots,
            final TunnelDirection direction,
            final String expectedName,
            final String tunnelInterface,
            final Set<String> actualNames) {

        /*This attempts to filter the Reported LSP snapshosts obtained from the initial read snapshosts process and wants to use
        * these values to filter for the tunnel direction that is required by the TunnelDirection reecord that is passed.
        * In the application we need to have two records presenta t all time so this translates these records we are expected for
        * either direction into DelegatedLspRecord which are still immutavble records but that have been validated */
        final PcepReportedLspSnapshot snapshot = snapshots
                .stream()
                /*Filter based on the source node being the TunnelDirection expected source. This filters down to all
                * tunnels flowing from say 11.11.11.11*/
                .filter(value -> direction.source().pccNode().equals(value.pccNode()))
                /*Filter again over the names, searching for the expected name of the tunnel*/
                .filter(value -> expectedName.equals(value.name()))
                .findFirst()
                .orElseThrow(() -> {
                    /*if we cannot find anything, since we are going to take the element out and if it is null
                    * then we are going to throw an illegal state exception given as we would not have the lsp registered*/
                    metrics.increment("sma_delegated_lsp_missing_total");
                    return new IllegalStateException("Falta el LSP delegado: PCC esperado="
                            + direction.source().pccNode() + ", nombre=" + expectedName
                            + ", nombres reportados=" + actualNames);
                });

        /*Creating the record to pass back*/
        final Instant now = Instant.now();
        final DelegatedLspRecord record = new DelegatedLspRecord(
                direction.directionKey(), snapshot.pccNode(),
                snapshot.name(), tunnelInterface,
                snapshot.sourceRouterId(), snapshot.destinationRouterId(),
                snapshot.plspId(), snapshot.tunnelId(),
                snapshot.lspId(), snapshot.delegate(),
                snapshot.administrative(), snapshot.operational(),
                snapshot.ero(), snapshot.bandwidthBase64(),
                now, now);

        /*These are the final validations that we are going to do, if th destination and source do not match the expected
        * direction then we know taht there is an illegal state where the lsp has been configured but that configuration
        * is wrong.*/
        if (!Objects.equals(direction.source().routerId(), record.sourceRouterId())
                || !Objects.equals(direction.destination().routerId(), record.destinationRouterId())) {
            throw new IllegalStateException("Los extremos del LSP delegado no coinciden con la configuracion para "
                    + expectedName + ": reportado=" + record.sourceRouterId() + "->"
                    + record.destinationRouterId() + ", esperado=" + direction.source().routerId()
                    + "->" + direction.destination().routerId());
        }

        /*This validation focuses on having a valid PLSID, and other parameters such as being delegated and operational. If
        * it is not delegated nor operational or any of the parameters failed validation we raise an exception*/
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

    /**
     * <p>Refresh PCEP state after an update transport or rejected-response failure.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Read and register the reported snapshots.</li>
     *   <li>Attach any refresh failure as suppressed on the original failure and log it.</li>
     * </ol>
     *
     * @param originalFailure failure receiving any suppressed refresh exception
     */
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

    /**
     * <p>Build structured log fields for a delegated LSP.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Collect its direction, identifiers, state flags, ERO, and bandwidth into a field map.</li>
     * </ol>
     *
     * @param record LSP record to describe
     * @return structured log fields
     */
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

    /**
     * <p>Compare a reported LSP's ERO and bandwidth against desired values.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Compare the ordered route and use null-safe equality for the encoded bandwidth.</li>
     * </ol>
     *
     * @param record reported LSP record
     * @param ero desired ordered ERO
     * @param bandwidthBase64 desired encoded bandwidth
     * @return true when both values match
     */
    private static boolean stateMatches(
            final DelegatedLspRecord record,
            final List<EroSubobject> ero,
            final String bandwidthBase64) {
        return record.activeEro().equals(ero)
                && Objects.equals(record.reportedBandwidthBase64(), bandwidthBase64);
    }

    /**
     * <p>Record an update duration with outcome and direction labels.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Measure elapsed monotonic time and observe the labeled histogram.</li>
     * </ol>
     *
     * @param outcome update outcome label
     * @param directionKey direction label
     * @param startedAt monotonic start time in nanoseconds
     */
    private void observeUpdateDuration(final String outcome, final String directionKey, final long startedAt) {
        metrics.observeHistogram("sma_update_lsp_duration_seconds",
                Map.of("outcome", outcome, "direction", directionKey), elapsedSeconds(startedAt));
    }

    /**
     * <p>Calculate elapsed seconds from a monotonic start time.</p>
     *
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Subtract the start from the current nanosecond clock and convert to seconds.</li>
     * </ol>
     *
     * @param startedAt monotonic start time in nanoseconds
     * @return elapsed duration in seconds
     */
    private static double elapsedSeconds(final long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000_000.0d;
    }
}
