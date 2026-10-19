/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.metrics;

import com.sma.sdn.observability.StructuredLogger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * <p>Stores labeled counters, gauges and histograms in memory for controller services and
 * renders registered metric families in Prometheus text format. Updates use concurrent maps
 * and adders; a scrape is not an atomic snapshot of all concurrent measurements.</p>
 * <p>The provider shares an instance with services and {@link PrometheusMetricsServlet}.
 * Counter and gauge updates can store unregistered names, but exposition iterates only the
 * predefined metadata; histogram updates require a registered histogram name.</p>
 */
public final class SdnMplsMlMetrics {
    /** <p>Structured logger for metric changes and compatibility snapshots.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(SdnMplsMlMetrics.class);
    /** <p>Finite upper bounds in seconds for ordinary duration histograms.</p> */
    private static final double[] DEFAULT_BUCKETS = {
        0.001, 0.0025, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0, 30.0
    };
    /** <p>Finite upper bounds in seconds for LSP convergence duration.</p> */
    private static final double[] CONVERGENCE_BUCKETS = {
        0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0, 30.0, 60.0
    };
    /** <p>Known metric families, help text, types and histogram boundaries.</p> */
    private static final Map<String, MetricMetadata> METADATA = metadata();

    /** <p>Concurrent counter values indexed by metric name and normalized labels.</p> */
    private final Map<MetricKey, LongAdder> counters = new ConcurrentHashMap<>();
    /** <p>Most recently assigned gauge values indexed by metric name and labels.</p> */
    private final Map<MetricKey, Double> gauges = new ConcurrentHashMap<>();
    /** <p>Histogram accumulators indexed by metric name and normalized labels.</p> */
    private final Map<MetricKey, HistogramState> histograms = new ConcurrentHashMap<>();

    /**
     * <p>Increments an unlabeled counter through the compatibility API.</p>
     * <p>The operation proceeds as follows:</p>
     * <ol><li>Delegates to {@link #incrementCounter(String)}.</li></ol>
     * @param counterName name of the counter to increment
     */
    public void increment(final String counterName) {
        incrementCounter(counterName);
    }

    /**
     * <p>Increments an unlabeled counter.</p>
     * <p>The operation proceeds as follows:</p>
     * <ol><li>Delegates to the labeled overload with an empty label map.</li></ol>
     * @param counterName name of the counter to increment
     */
    public void incrementCounter(final String counterName) {
        incrementCounter(counterName, Map.of());
    }

    /**
     * <p>Increments the counter identified by a name and normalized labels.</p>
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Constructs a normalized metric key and creates a concurrent adder if absent.</li>
     *   <li>Increments the adder and emits a trace event containing its current sum.</li>
     * </ol>
     * @param counterName counter name; registration is not checked here
     * @param labels label names and values to normalize for the series
     * @throws NullPointerException if labels or a label key is null
     */
    public void incrementCounter(final String counterName, final Map<String, String> labels) {
        final MetricKey key = key(counterName, labels);
        final LongAdder counter = counters.computeIfAbsent(key, ignored -> new LongAdder());
        counter.increment();
        LOG.trace("metric_counter_incremented", "incrementCounter",
                "Se incremento un contador operativo",
                StructuredLogger.fields("counter_name", counterName, "labels", key.labels(), "value", counter.sum()));
    }

    /**
     * <p>Returns an unlabeled gauge as a long, or an unlabeled counter when no gauge exists.</p>
     * <p>The lookup proceeds as follows:</p>
     * <ol>
     *   <li>Looks up the unlabeled gauge and converts its double value to a long if present.</li>
     *   <li>Otherwise returns the counter sum, or zero when no counter exists.</li>
     * </ol>
     * @param metricName name of the unlabeled metric
     * @return gauge value converted to long, counter sum, or zero
     */
    public long value(final String metricName) {
        final Double gauge = gauges.get(key(metricName, Map.of()));
        if (gauge != null) {
            return gauge.longValue();
        }
        final LongAdder value = counters.get(key(metricName, Map.of()));
        return value == null ? 0L : value.sum();
    }

    /**
     * <p>Sets an unlabeled gauge through the compatibility API.</p>
     * <p>The operation proceeds as follows:</p>
     * <ol><li>Delegates the value to the long-valued gauge overload.</li></ol>
     * @param metricName gauge name
     * @param value new gauge value
     */
    public void set(final String metricName, final long value) {
        setGauge(metricName, value);
    }

    /**
     * <p>Sets an unlabeled gauge from a long value.</p>
     * <p>The operation proceeds as follows:</p>
     * <ol><li>Converts the value to double and delegates to the double overload.</li></ol>
     * @param metricName gauge name
     * @param value new gauge value
     */
    public void setGauge(final String metricName, final long value) {
        setGauge(metricName, (double) value);
    }

    /**
     * <p>Sets an unlabeled gauge to a double value.</p>
     * <p>The operation proceeds as follows:</p>
     * <ol><li>Delegates to the labeled overload with an empty label map.</li></ol>
     * @param metricName gauge name
     * @param value new gauge value
     */
    public void setGauge(final String metricName, final double value) {
        setGauge(metricName, Map.of(), value);
    }

    /**
     * <p>Replaces the gauge value for the specified name and normalized label set.</p>
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Constructs a normalized key and replaces its current gauge value.</li>
     *   <li>Emits a trace event with the key and assigned value.</li>
     * </ol>
     * @param metricName gauge name; registration is not checked here
     * @param labels label names and values to normalize for the series
     * @param value new gauge value
     * @throws NullPointerException if labels or a label key is null
     */
    public void setGauge(final String metricName, final Map<String, String> labels, final double value) {
        final MetricKey key = key(metricName, labels);
        gauges.put(key, value);
        LOG.trace("metric_gauge_updated", "setGauge",
                "Se actualizo una metrica de estado.",
                StructuredLogger.fields("metric_name", metricName, "labels", key.labels(), "value", value));
    }

    /**
     * <p>Records an unlabeled duration observation in a registered histogram.</p>
     * <p>The operation proceeds as follows:</p>
     * <ol><li>Delegates to the labeled overload with an empty label map.</li></ol>
     * @param metricName registered histogram name
     * @param seconds observed value in seconds
     * @throws IllegalArgumentException if the name is unknown or not a histogram
     */
    public void observeHistogram(final String metricName, final double seconds) {
        observeHistogram(metricName, Map.of(), seconds);
    }

    /**
     * <p>Records an observation in the histogram for the supplied name and labels.</p>
     * <p>The operation proceeds as follows:</p>
     * <ol>
     *   <li>Resolves the predefined metadata and rejects names that are not histograms.</li>
     *   <li>Creates the series if absent and updates its count, sum and first matching bucket.</li>
     *   <li>Emits a trace event containing the supplied observation.</li>
     * </ol>
     * @param metricName registered histogram name
     * @param labels label names and values for the observed series
     * @param seconds observed value in seconds
     * @throws IllegalArgumentException if the name is unknown or not a histogram
     * @throws NullPointerException if labels or a label key is null
     */
    public void observeHistogram(final String metricName, final Map<String, String> labels, final double seconds) {
        final MetricMetadata metadata = metadata(metricName);
        if (metadata.type() != MetricType.HISTOGRAM) {
            throw new IllegalArgumentException("La metrica no es un histograma: " + metricName);
        }
        histograms.computeIfAbsent(key(metricName, labels), ignored -> new HistogramState(metadata.buckets()))
                .observe(seconds);
        LOG.trace("metric_histogram_observed", "observeHistogram",
                "Se registro una observacion de histograma.",
                StructuredLogger.fields("metric_name", metricName, "labels", labels, "seconds", seconds));
    }

    /**
     * <p>Returns a compatibility snapshot of unlabeled counters and gauges as long values.</p>
     * <p>Snapshot construction proceeds as follows:</p>
     * <ol>
     *   <li>Copies unlabeled counter sums and then unlabeled gauge values converted to long.</li>
     *   <li>Returns an immutable copy after logging it; a gauge overwrites a same-name counter.</li>
     * </ol>
     * <p>Concurrent updates can be reflected at different times in the returned map.</p>
     * @return immutable name-to-value snapshot, excluding labeled series and histograms
     */
    public Map<String, Long> snapshot() {
        final Map<String, Long> values = new ConcurrentHashMap<>();
        counters.forEach((key, value) -> {
            if (key.labels().isEmpty()) {
                values.put(key.name(), value.sum());
            }
        });
        gauges.forEach((key, value) -> {
            if (key.labels().isEmpty()) {
                values.put(key.name(), value.longValue());
            }
        });
        final Map<String, Long> snapshot = Map.copyOf(values);
        LOG.debug("metrics_snapshot_created", "snapshot",
                "Se genero una instantanea de las metricas operativas",
                StructuredLogger.fields("counter_count", snapshot.size(), "counters", snapshot));
        return snapshot;
    }

    /**
     * <p>Renders every predefined metric family in Prometheus text exposition format.</p>
     * <p>Rendering proceeds as follows:</p>
     * <ol>
     *   <li>Creates an output buffer and iterates over registered metadata.</li>
     *   <li>Appends help, type and currently stored samples for each family.</li>
     *   <li>Returns the resulting text for the metrics servlet.</li>
     * </ol>
     * @return text exposition including metadata for families without samples
     */
    public String renderPrometheusText() {
        final StringBuilder output = new StringBuilder(8192);
        METADATA.values().forEach(metadata -> appendMetric(output, metadata));
        return output.toString();
    }

    /**
     * <p>Appends one metric family's metadata and samples to an exposition buffer.</p>
     * <p>Rendering proceeds as follows:</p>
     * <ol><li>Writes HELP and TYPE lines.</li><li>Dispatches sample rendering by metric type.</li></ol>
     * @param output destination buffer
     * @param metadata family name, type and help text
     */
    private void appendMetric(final StringBuilder output, final MetricMetadata metadata) {
        output.append("# HELP ").append(metadata.name()).append(' ').append(escapeHelp(metadata.help())).append('\n');
        output.append("# TYPE ").append(metadata.name()).append(' ').append(metadata.type().wireName()).append('\n');
        if (metadata.type() == MetricType.COUNTER) {
            appendCounters(output, metadata.name());
        } else if (metadata.type() == MetricType.GAUGE) {
            appendGauges(output, metadata.name());
        } else {
            appendHistograms(output, metadata.name());
        }
    }

    /**
     * <p>Appends the current series of a counter family in key order.</p>
     * <p>Rendering proceeds as follows:</p>
     * <ol><li>Filters and sorts matching keys.</li><li>Writes each adder's current sum.</li></ol>
     * @param output destination buffer
     * @param name counter family name
     */
    private void appendCounters(final StringBuilder output, final String name) {
        counters.entrySet().stream()
                .filter(entry -> entry.getKey().name().equals(name))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> appendSample(output, name, entry.getKey().labels(),
                        Long.toString(entry.getValue().sum())));
    }

    /**
     * <p>Appends the current series of a gauge family in key order.</p>
     * <p>Rendering proceeds as follows:</p>
     * <ol><li>Filters and sorts matching keys.</li><li>Writes each formatted gauge value.</li></ol>
     * @param output destination buffer
     * @param name gauge family name
     */
    private void appendGauges(final StringBuilder output, final String name) {
        gauges.entrySet().stream()
                .filter(entry -> entry.getKey().name().equals(name))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> appendSample(output, name, entry.getKey().labels(), doubleValue(entry.getValue())));
    }

    /**
     * <p>Appends cumulative buckets, count and sum for each histogram series.</p>
     * <p>Rendering proceeds as follows:</p>
     * <ol>
     *   <li>Filters and sorts the matching histogram series and reads each snapshot.</li>
     *   <li>Accumulates finite bucket counts, adds the infinite bucket, and writes sum and count.</li>
     * </ol>
     * @param output destination buffer
     * @param name histogram family name
     */
    private void appendHistograms(final StringBuilder output, final String name) {
        histograms.entrySet().stream()
                .filter(entry -> entry.getKey().name().equals(name))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    final MetricKey key = entry.getKey();
                    final HistogramSnapshot snapshot = entry.getValue().snapshot();
                    long cumulative = 0L;
                    for (int index = 0; index < snapshot.buckets().length; index++) {
                        cumulative += snapshot.bucketCounts()[index];
                        appendSample(output, name + "_bucket",
                                withLabel(key.labels(), "le", doubleValue(snapshot.buckets()[index])),
                                Long.toString(cumulative));
                    }
                    appendSample(output, name + "_bucket", withLabel(key.labels(), "le", "+Inf"),
                            Long.toString(snapshot.count()));
                    appendSample(output, name + "_sum", key.labels(), doubleValue(snapshot.sum()));
                    appendSample(output, name + "_count", key.labels(), Long.toString(snapshot.count()));
                });
    }

    /**
     * <p>Appends a single Prometheus sample with its escaped label values.</p>
     * <p>Rendering proceeds as follows:</p>
     * <ol><li>Writes the name and any labels.</li><li>Appends the value and a newline.</li></ol>
     * @param output destination buffer
     * @param name sample name
     * @param labels label names and unescaped values
     * @param value formatted sample value
     */
    private static void appendSample(
            final StringBuilder output,
            final String name,
            final Map<String, String> labels,
            final String value) {
        output.append(name);
        if (!labels.isEmpty()) {
            final StringJoiner joiner = new StringJoiner(",", "{", "}");
            labels.forEach((key, labelValue) -> joiner.add(key + "=\"" + escapeLabel(labelValue) + "\""));
            output.append(joiner);
        }
        output.append(' ').append(value).append('\n');
    }

    /**
     * <p>Builds a series key from a metric name and normalized label values.</p>
     * <p>Construction proceeds as follows:</p>
     * <ol><li>Sorts labels by key and normalizes each value.</li><li>Copies the labels into the key.</li></ol>
     * @param name metric name
     * @param labels input label names and values
     * @return metric key with an immutable label map
     * @throws NullPointerException if labels or a label key is null
     */
    private static MetricKey key(final String name, final Map<String, String> labels) {
        final Map<String, String> normalized = new LinkedHashMap<>();
        labels.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> normalized.put(entry.getKey(), normalizeLabel(entry.getValue())));
        return new MetricKey(name, Map.copyOf(normalized));
    }

    /**
     * <p>Copies a series label map and adds or replaces one label.</p>
     * <p>Construction proceeds as follows:</p>
     * <ol><li>Copies the existing entries.</li><li>Inserts the supplied label and returns the map.</li></ol>
     * @param labels existing labels
     * @param name additional label name
     * @param value additional label value
     * @return mutable copy including the added label
     */
    private static Map<String, String> withLabel(
            final Map<String, String> labels,
            final String name,
            final String value) {
        final Map<String, String> result = new LinkedHashMap<>(labels);
        result.put(name, value);
        return result;
    }

    /**
     * <p>Resolves the predefined metadata for a metric name.</p>
     * <p>Lookup proceeds as follows:</p>
     * <ol><li>Looks up the name in the metadata map.</li><li>Rejects an absent entry or returns it.</li></ol>
     * @param name metric family name
     * @return registered metadata
     * @throws IllegalArgumentException if no family is registered under the name
     * @throws NullPointerException if name is null
     */
    private static MetricMetadata metadata(final String name) {
        final MetricMetadata metadata = METADATA.get(name);
        if (metadata == null) {
            throw new IllegalArgumentException("Metrica no registrada: " + name);
        }
        return metadata;
    }

    /**
     * <p>Normalizes an individual label value for use in a metric key.</p>
     * <p>Normalization proceeds as follows:</p>
     * <ol><li>Replaces null or blank values with {@code unknown}.</li>
     * <li>Otherwise truncates values longer than 96 characters.</li></ol>
     * @param value input label value, possibly null
     * @return normalized label value
     */
    private static String normalizeLabel(final String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.length() <= 96 ? value : value.substring(0, 96);
    }

    /**
     * <p>Escapes a Prometheus HELP description.</p>
     * <p>Escaping proceeds as follows:</p>
     * <ol><li>Escapes backslashes and then newline characters.</li></ol>
     * @param value help text
     * @return escaped help text
     */
    private static String escapeHelp(final String value) {
        return value.replace("\\", "\\\\").replace("\n", "\\n");
    }

    /**
     * <p>Escapes a Prometheus label value.</p>
     * <p>Escaping proceeds as follows:</p>
     * <ol><li>Escapes backslashes, newlines and double quotes.</li></ol>
     * @param value unescaped label value
     * @return escaped label value
     */
    private static String escapeLabel(final String value) {
        return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"");
    }

    /**
     * <p>Formats a numeric value for exposition, substituting zero for non-finite values.</p>
     * <p>Formatting proceeds as follows:</p>
     * <ol><li>Checks for NaN or infinity.</li><li>Returns zero or the standard double representation.</li></ol>
     * @param value numeric sample
     * @return textual sample value
     */
    private static String doubleValue(final double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0.0";
        }
        return Double.toString(value);
    }

    /**
     * <p>Creates the fixed catalog of controller metric families and their exposition metadata.</p>
     * <p>Initialization proceeds as follows:</p>
     * <ol><li>Registers counters, gauges and duration histograms.</li><li>Returns an immutable catalog.</li></ol>
     * @return immutable mapping from metric names to metadata
     */
    private static Map<String, MetricMetadata> metadata() {
        final Map<String, MetricMetadata> values = new LinkedHashMap<>();
        counter(values, "sma_packet_in_total", "PacketIn notifications received.");
        counter(values, "sma_packet_in_control_plane_not_ready_total", "PacketIn ignored before readiness.");
        counter(values, "packet_ignored_control_plane_not_ready", "PacketIn ignored before control-plane readiness.");
        counter(values, "packet_ignored_arp", "ARP PacketIn notifications ignored.");
        counter(values, "packet_ignored_unsupported_eth_type", "Unsupported ethertype PacketIn notifications ignored.");
        counter(values, "packet_ignored_unknown_ingress", "PacketIn notifications ignored due to unknown ingress.");
        counter(values, "sma_odl_topology_discovery_success_total", "Successful ODL topology discovery calls.");
        counter(values, "sma_odl_topology_discovery_failure_total", "Failed ODL topology discovery calls.");
        counter(values, "sma_odl_bgpls_resolution_failure_total", "BGP-LS graph-node resolution failures.");
        counter(values, "sma_classification_cache_hit_total", "Classification cache hits.");
        counter(values, "sma_classification_cache_miss_total", "Classification cache misses.");
        counter(values, "sma_classifier_request_total", "External classifier requests sent.");
        counter(values, "sma_classifier_failure_total", "External classifier failures.");
        counter(values, "sma_path_cache_hit_total", "Calculated path cache hits.");
        counter(values, "sma_path_cache_miss_total", "Calculated path cache misses.");
        counter(values, "sma_path_computation_request_total", "CSPF path-computation requests sent.");
        counter(values, "sma_path_computation_success_total", "Successful CSPF path-computation requests.");
        counter(values, "sma_path_computation_failure_total", "Failed CSPF path-computation requests.");
        counter(values, "sma_pcep_topology_refresh_total", "PCEP topology reads attempted.");
        counter(values, "sma_pcep_topology_refresh_failure_total", "PCEP topology read failures.");
        counter(values, "sma_delegated_lsp_discovered_total", "Delegated LSP records discovered.");
        counter(values, "sma_delegated_lsp_missing_total", "Configured delegated LSP records missing.");
        counter(values, "sma_update_lsp_request_total", "PCEP update-lsp requests sent.");
        counter(values, "sma_update_lsp_success_total", "PCEP update-lsp requests confirmed.");
        counter(values, "sma_update_lsp_failure_total", "PCEP update-lsp failures.");
        counter(values, "sma_update_lsp_skipped_no_change_total",
                "PCEP update-lsp requests skipped due to matching state.");
        counter(values, "sma_lsp_update_sent_total", "Directional LSP policy updates sent.");
        counter(values, "sma_lsp_update_skipped_converged_total", "Directional LSP updates skipped as converged.");
        counter(values, "sma_lsp_update_bandwidth_mismatch_total", "Directional LSP bandwidth mismatches observed.");
        counter(values, "sma_pair_policy_apply_failure_total", "Pair-policy LSP application failures.");
        counter(values, "sma_openflow_inventory_discovery_attempts_total", "OpenFlow inventory discovery attempts.");
        counter(values, "sma_openflow_inventory_discovery_success_total", "OpenFlow inventory discovery successes.");
        counter(values, "sma_openflow_inventory_discovery_failure_total", "OpenFlow inventory discovery failures.");
        counter(values, "sma_openflow_switch_resolved_total", "OpenFlow switches resolved.");
        counter(values, "sma_openflow_connector_resolved_total", "OpenFlow connectors resolved.");
        counter(values, "sma_openflow_flow_install_attempts_total", "OpenFlow bootstrap flow install attempts.");
        counter(values, "sma_openflow_flow_install_success_total", "OpenFlow bootstrap flow install successes.");
        counter(values, "sma_openflow_flow_install_failure_total", "OpenFlow bootstrap flow install failures.");
        counter(values, "sma_directional_evidence_recorded_total", "Directional policy evidence records stored.");
        counter(values, "sma_pair_consensus_pending_total", "Pair consensus decisions pending one side.");
        counter(values, "sma_pair_consensus_unresolved_total", "Pair consensus decisions unresolved.");
        counter(values, "sma_packet_workflow_pending_consensus_total", "Packet workflows pending pair consensus.");
        counter(values, "sma_packet_workflow_deferred_total", "Packet workflows deferred by pair policy.");
        counter(values, "sma_pair_consensus_match_total", "Pair consensus decisions with matching evidence.");
        counter(values, "sma_pair_consensus_priority_selected_total", "Pair consensus priority conflict selections.");
        counter(values, "sma_pair_policy_refresh_total", "Active pair policies refreshed.");
        counter(values, "sma_pair_policy_deferred_total", "Pair policy candidates deferred.");
        counter(values, "sma_pair_policy_preempt_total", "Pair policies preempted.");
        counter(values, "sma_pair_policy_expired_total", "Expired pair policies replaced.");
        counter(values, "sma_pair_policy_active_total", "Pair policies installed or refreshed as active.");
        counter(values, "sma_registry_classification_expired_evictions_total", "Classification cache entries expired.");
        counter(values, "sma_registry_calculated_path_expired_evictions_total", "Calculated paths expired.");
        counter(values, "sma_registry_directional_evidence_expired_evictions_total", "Directional evidence expired.");
        counter(values, "sma_registry_active_pair_policy_expired_evictions_total", "Active pair policies expired.");
        counter(values, "sma_bgpls_topology_ttl_expired_total", "Times BGP-LS topology reached TTL.");
        counter(values, "sma_bgpls_topology_refresh_on_demand_total", "On-demand BGP-LS refreshes started.");
        counter(values, "sma_bgpls_topology_refresh_deduplicated_total",
                "BGP-LS refreshes skipped by single-flight state.");
        counter(values, "sma_controller_operational_state_publish_success_total",
                "Operational state publish successes.");
        counter(values, "sma_controller_operational_state_publish_failure_total",
                "Operational state publish failures.");
        counter(values, "sma_pair_consensus_decision_total", "Pair consensus decisions by status.");
        counter(values, "sma_pair_consensus_equal_priority_total", "Equal-priority pair consensus conflicts.");
        counter(values, "sma_pair_consensus_require_both_block_total",
                "Consensus blocked waiting for both directions.");
        counter(values, "sma_pair_policy_retained_stronger_active_total", "Active stronger policies retained.");
        counter(values, "sma_pair_policy_retained_equal_priority_total", "Equal-priority active policies retained.");
        counter(values, "sma_pair_policy_replaced_expired_total", "Expired pair policies replaced.");
        counter(values, "sma_pair_policy_preempted_by_priority_total", "Pair policies preempted by higher priority.");
        counter(values, "sma_lsp_application_ero_confirmed_total", "Directional LSP ERO confirmations.");
        counter(values, "sma_lsp_application_bandwidth_confirmed_total", "Directional LSP bandwidth confirmations.");
        counter(values, "sma_lsp_application_bandwidth_unconfirmed_total",
                "Directional LSP bandwidth mismatches after update.");
        counter(values, "sma_lsp_application_failed_total", "Directional LSP application failures.");
        counter(values, "sma_control_cycle_total", "End-to-end control cycles by outcome and class.");
        counter(values, "sma_lsp_path_verification_total", "Requested ERO versus operational ERO comparisons.");
        gauge(values, "sma_openflow_bootstrap_ready", "OpenFlow bootstrap readiness.");
        gauge(values, "sma_openflow_bootstrap_last_success_timestamp", "Last successful OpenFlow bootstrap time.");
        gauge(values, "sma_registry_classification_exact_entries", "Current exact classification cache entries.");
        gauge(values, "sma_registry_classification_service_entries", "Current service classification cache entries.");
        gauge(values, "sma_registry_calculated_path_entries", "Current calculated path entries.");
        gauge(values, "sma_registry_delegated_lsp_entries", "Current delegated LSP entries.");
        gauge(values, "sma_registry_directional_evidence_buckets", "Current directional evidence buckets.");
        gauge(values, "sma_registry_directional_evidence_entries", "Current directional evidence entries.");
        gauge(values, "sma_registry_active_pair_policy_entries", "Current active pair policy entries.");
        gauge(values, "sma_registry_directional_lsp_application_entries", "Current LSP application entries.");
        gauge(values, "sma_registry_tunnel_pair_entries", "Current tunnel pair entries.");
        gauge(values, "sma_registry_tunnel_direction_entries", "Current tunnel direction entries.");
        gauge(values, "sma_registry_openflow_switch_entries", "Current OpenFlow switch entries.");
        gauge(values, "sma_registry_openflow_connector_entries", "Current OpenFlow connector entries.");
        gauge(values, "sma_registry_bgpls_node_entries", "Current BGP-LS node entries.");
        gauge(values, "sma_bgpls_topology_fresh", "Whether BGP-LS topology is fresh.");
        gauge(values, "sma_bgpls_topology_refresh_in_progress", "Whether BGP-LS refresh is in progress.");
        gauge(values, "sma_bgpls_topology_last_success_epoch_seconds",
                "Last successful BGP-LS refresh Unix timestamp.");
        gauge(values, "sma_bgpls_topology_last_attempt_epoch_seconds", "Last BGP-LS refresh attempt Unix timestamp.");
        gauge(values, "sma_bgpls_topology_fresh_until_epoch_seconds", "BGP-LS fresh-until Unix timestamp.");
        histogram(values, "sma_classifier_round_trip_duration_seconds", "CSA to Python classifier round-trip duration.",
                DEFAULT_BUCKETS);
        histogram(values, "sma_path_computation_duration_seconds", "CSPF path-computation duration.", DEFAULT_BUCKETS);
        histogram(values, "sma_update_lsp_duration_seconds", "PCEP update-lsp interaction duration.", DEFAULT_BUCKETS);
        histogram(values, "sma_lsp_convergence_duration_seconds", "PCEP LSP convergence duration.",
                CONVERGENCE_BUCKETS);
        histogram(values, "sma_control_cycle_duration_seconds", "Accepted PacketIn to LSP decision duration.",
                DEFAULT_BUCKETS);
        return Map.copyOf(values);
    }

    /**
     * <p>Registers counter metadata in a catalog under construction.</p>
     * <p>Registration proceeds as follows:</p>
     * <ol><li>Creates counter metadata without buckets and stores it by name.</li></ol>
     * @param values catalog being populated
     * @param name metric family name
     * @param help exposition help text
     */
    private static void counter(final Map<String, MetricMetadata> values, final String name, final String help) {
        values.put(name, new MetricMetadata(name, MetricType.COUNTER, help, new double[0]));
    }

    /**
     * <p>Registers gauge metadata in a catalog under construction.</p>
     * <p>Registration proceeds as follows:</p>
     * <ol><li>Creates gauge metadata without buckets and stores it by name.</li></ol>
     * @param values catalog being populated
     * @param name metric family name
     * @param help exposition help text
     */
    private static void gauge(final Map<String, MetricMetadata> values, final String name, final String help) {
        values.put(name, new MetricMetadata(name, MetricType.GAUGE, help, new double[0]));
    }

    /**
     * <p>Registers histogram metadata and its finite bucket bounds.</p>
     * <p>Registration proceeds as follows:</p>
     * <ol><li>Copies the bounds and stores histogram metadata by name.</li></ol>
     * @param values catalog being populated
     * @param name metric family name
     * @param help exposition help text
     * @param buckets finite upper bounds
     */
    private static void histogram(
            final Map<String, MetricMetadata> values,
            final String name,
            final String help,
            final double[] buckets) {
        values.put(name, new MetricMetadata(name, MetricType.HISTOGRAM, help, buckets.clone()));
    }

    /** <p>Prometheus family types used to select the appropriate sample renderer.</p> */
    private enum MetricType {
        /** <p>Monotonically incremented counter family.</p> */
        COUNTER("counter"),
        /** <p>Current-value gauge family.</p> */
        GAUGE("gauge"),
        /** <p>Distribution with bucket, sum and count samples.</p> */
        HISTOGRAM("histogram");

        /** <p>Type name written in the Prometheus TYPE line.</p> */
        private final String wireName;

        /**
         * <p>Associates a metric type with its exposition name.</p>
         * <p>Construction proceeds as follows:</p>
         * <ol><li>Stores the supplied wire representation.</li></ol>
         * @param wireName Prometheus type name
         */
        MetricType(final String wireName) {
            this.wireName = wireName;
        }

        /**
         * <p>Returns the exposition name of this metric type.</p>
         * <p>Lookup proceeds as follows:</p>
         * <ol><li>Returns the stored wire representation.</li></ol>
         * @return Prometheus type name
         */
        String wireName() {
            return wireName;
        }
    }

    /**
     * <p>Describes one registered family and the finite bounds used by histogram series.</p>
     * @param name metric family name
     * @param type metric family type
     * @param help exposition help text
     * @param buckets finite histogram upper bounds
     */
    private record MetricMetadata(String name, MetricType type, String help, double[] buckets) {
        /**
         * <p>Validates required fields and copies the supplied bucket array.</p>
         * <p>Construction proceeds as follows:</p>
         * <ol><li>Rejects null name, type or help.</li><li>Clones the bucket bounds.</li></ol>
         * @param name metric family name
         * @param type family type
         * @param help exposition help text
         * @param buckets finite upper bounds
         * @throws NullPointerException if any argument is null
         */
        MetricMetadata {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(help, "help");
            buckets = buckets.clone();
        }
    }

    /**
     * <p>Identifies one metric series and provides ordering for exposition.</p>
     * @param name family name
     * @param labels normalized series labels
     */
    private record MetricKey(String name, Map<String, String> labels) implements Comparable<MetricKey> {
        /**
         * <p>Orders series by name and then by their sorted label representation.</p>
         * <p>Comparison proceeds as follows:</p>
         * <ol><li>Compares names first.</li><li>Compares label strings if the names match.</li></ol>
         * @param other series to compare against
         * @return negative, zero or positive comparison result
         */
        @Override
        public int compareTo(final MetricKey other) {
            final int nameComparison = name.compareTo(other.name);
            if (nameComparison != 0) {
                return nameComparison;
            }
            return labelString(labels).compareTo(labelString(other.labels));
        }
    }

    /**
     * <p>Captures the bounds, non-cumulative counts and totals read from a histogram series.</p>
     * @param buckets finite upper bounds
     * @param bucketCounts counts for the first matching finite bucket
     * @param count total observations, including those beyond finite bounds
     * @param sum sum of observations
     */
    private record HistogramSnapshot(double[] buckets, long[] bucketCounts, long count, double sum) {
    }

    /** <p>Concurrent accumulators for one histogram series, rendered through snapshots.</p> */
    private static final class HistogramState {
        /** <p>Finite bucket upper bounds in configured order.</p> */
        private final double[] buckets;
        /** <p>Counts assigned to each first matching finite bucket.</p> */
        private final LongAdder[] counts;
        /** <p>Total observations including values beyond all finite bounds.</p> */
        private final LongAdder count = new LongAdder();
        /** <p>Sum of all observed values.</p> */
        private final DoubleAdder sum = new DoubleAdder();

        /**
         * <p>Creates empty accumulators for the supplied histogram bounds.</p>
         * <p>Construction proceeds as follows:</p>
         * <ol><li>Clones the bounds.</li><li>Creates one adder for each finite bucket.</li></ol>
         * @param buckets finite upper bounds
         */
        HistogramState(final double[] buckets) {
            this.buckets = buckets.clone();
            this.counts = new LongAdder[buckets.length];
            for (int index = 0; index < buckets.length; index++) {
                counts[index] = new LongAdder();
            }
        }

        /**
         * <p>Adds one observation to this histogram series.</p>
         * <p>Recording proceeds as follows:</p>
         * <ol><li>Updates total count and sum.</li><li>Increments the first bucket whose bound contains the value.</li></ol>
         * @param value observed numeric value
         */
        void observe(final double value) {
            count.increment();
            sum.add(value);
            for (int index = 0; index < buckets.length; index++) {
                if (value <= buckets[index]) {
                    counts[index].increment();
                    return;
                }
            }
        }

        /**
         * <p>Reads a histogram snapshot without blocking concurrent observations.</p>
         * <p>Snapshot construction proceeds as follows:</p>
         * <ol><li>Reads each bucket adder.</li><li>Copies bounds and reads the total count and sum.</li></ol>
         * @return snapshot whose components may reflect concurrent updates at different times
         */
        HistogramSnapshot snapshot() {
            final long[] bucketCounts = new long[counts.length];
            for (int index = 0; index < counts.length; index++) {
                bucketCounts[index] = counts[index].sum();
            }
            return new HistogramSnapshot(buckets.clone(), bucketCounts, count.sum(), sum.sum());
        }
    }

    /**
     * <p>Creates a stable comparison string from a series label map.</p>
     * <p>Construction proceeds as follows:</p>
     * <ol><li>Sorts the entries by label name.</li><li>Joins name-value pairs with commas.</li></ol>
     * @param labels series labels
     * @return sorted comparison string
     */
    private static String labelString(final Map<String, String> labels) {
        final List<Map.Entry<String, String>> entries = new ArrayList<>(labels.entrySet());
        entries.sort(Comparator.comparing((Map.Entry<String, String> entry) -> entry.getKey()));
        final StringJoiner joiner = new StringJoiner(",");
        entries.forEach(entry -> joiner.add(entry.getKey() + "=" + entry.getValue()));
        return joiner.toString();
    }
}
