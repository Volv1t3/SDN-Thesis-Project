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

/** In-memory Prometheus metric registry used by the controller-side application. */
public final class SdnMplsMlMetrics {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(SdnMplsMlMetrics.class);
    private static final double[] DEFAULT_BUCKETS = {
        0.001, 0.0025, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0, 30.0
    };
    private static final double[] CONVERGENCE_BUCKETS = {
        0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0, 30.0, 60.0
    };
    private static final Map<String, MetricMetadata> METADATA = metadata();

    private final Map<MetricKey, LongAdder> counters = new ConcurrentHashMap<>();
    private final Map<MetricKey, Double> gauges = new ConcurrentHashMap<>();
    private final Map<MetricKey, HistogramState> histograms = new ConcurrentHashMap<>();

    /** Compatibility wrapper for existing unlabeled counters. */
    public void increment(final String counterName) {
        incrementCounter(counterName);
    }

    public void incrementCounter(final String counterName) {
        incrementCounter(counterName, Map.of());
    }

    public void incrementCounter(final String counterName, final Map<String, String> labels) {
        final MetricKey key = key(counterName, labels);
        final LongAdder counter = counters.computeIfAbsent(key, ignored -> new LongAdder());
        counter.increment();
        LOG.trace("metric_counter_incremented", "incrementCounter",
                "Se incremento un contador operativo",
                StructuredLogger.fields("counter_name", counterName, "labels", key.labels(), "value", counter.sum()));
    }

    /** Compatibility lookup for legacy tests and logs using unlabeled values. */
    public long value(final String metricName) {
        final Double gauge = gauges.get(key(metricName, Map.of()));
        if (gauge != null) {
            return gauge.longValue();
        }
        final LongAdder value = counters.get(key(metricName, Map.of()));
        return value == null ? 0L : value.sum();
    }

    /** Compatibility wrapper for existing unlabeled gauges. */
    public void set(final String metricName, final long value) {
        setGauge(metricName, value);
    }

    public void setGauge(final String metricName, final long value) {
        setGauge(metricName, (double) value);
    }

    public void setGauge(final String metricName, final double value) {
        setGauge(metricName, Map.of(), value);
    }

    public void setGauge(final String metricName, final Map<String, String> labels, final double value) {
        final MetricKey key = key(metricName, labels);
        gauges.put(key, value);
        LOG.trace("metric_gauge_updated", "setGauge",
                "Se actualizo una metrica de estado.",
                StructuredLogger.fields("metric_name", metricName, "labels", key.labels(), "value", value));
    }

    public void observeHistogram(final String metricName, final double seconds) {
        observeHistogram(metricName, Map.of(), seconds);
    }

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

    public String renderPrometheusText() {
        final StringBuilder output = new StringBuilder(8192);
        METADATA.values().forEach(metadata -> appendMetric(output, metadata));
        return output.toString();
    }

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

    private void appendCounters(final StringBuilder output, final String name) {
        counters.entrySet().stream()
                .filter(entry -> entry.getKey().name().equals(name))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> appendSample(output, name, entry.getKey().labels(),
                        Long.toString(entry.getValue().sum())));
    }

    private void appendGauges(final StringBuilder output, final String name) {
        gauges.entrySet().stream()
                .filter(entry -> entry.getKey().name().equals(name))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> appendSample(output, name, entry.getKey().labels(), doubleValue(entry.getValue())));
    }

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

    private static MetricKey key(final String name, final Map<String, String> labels) {
        final Map<String, String> normalized = new LinkedHashMap<>();
        labels.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> normalized.put(entry.getKey(), normalizeLabel(entry.getValue())));
        return new MetricKey(name, Map.copyOf(normalized));
    }

    private static Map<String, String> withLabel(
            final Map<String, String> labels,
            final String name,
            final String value) {
        final Map<String, String> result = new LinkedHashMap<>(labels);
        result.put(name, value);
        return result;
    }

    private static MetricMetadata metadata(final String name) {
        final MetricMetadata metadata = METADATA.get(name);
        if (metadata == null) {
            throw new IllegalArgumentException("Metrica no registrada: " + name);
        }
        return metadata;
    }

    private static String normalizeLabel(final String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.length() <= 96 ? value : value.substring(0, 96);
    }

    private static String escapeHelp(final String value) {
        return value.replace("\\", "\\\\").replace("\n", "\\n");
    }

    private static String escapeLabel(final String value) {
        return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"");
    }

    private static String doubleValue(final double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0.0";
        }
        return Double.toString(value);
    }

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
        counter(values, "sma_pair_consensus_service_key_selected_total",
                "Pair consensus service-key conflict selections.");
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
        counter(values, "sma_pair_consensus_single_side_provisional_total",
                "Single-side provisional consensus decisions.");
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

    private static void counter(final Map<String, MetricMetadata> values, final String name, final String help) {
        values.put(name, new MetricMetadata(name, MetricType.COUNTER, help, new double[0]));
    }

    private static void gauge(final Map<String, MetricMetadata> values, final String name, final String help) {
        values.put(name, new MetricMetadata(name, MetricType.GAUGE, help, new double[0]));
    }

    private static void histogram(
            final Map<String, MetricMetadata> values,
            final String name,
            final String help,
            final double[] buckets) {
        values.put(name, new MetricMetadata(name, MetricType.HISTOGRAM, help, buckets.clone()));
    }

    private enum MetricType {
        COUNTER("counter"),
        GAUGE("gauge"),
        HISTOGRAM("histogram");

        private final String wireName;

        MetricType(final String wireName) {
            this.wireName = wireName;
        }

        String wireName() {
            return wireName;
        }
    }

    private record MetricMetadata(String name, MetricType type, String help, double[] buckets) {
        MetricMetadata {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(help, "help");
            buckets = buckets.clone();
        }
    }

    private record MetricKey(String name, Map<String, String> labels) implements Comparable<MetricKey> {
        @Override
        public int compareTo(final MetricKey other) {
            final int nameComparison = name.compareTo(other.name);
            if (nameComparison != 0) {
                return nameComparison;
            }
            return labelString(labels).compareTo(labelString(other.labels));
        }
    }

    private record HistogramSnapshot(double[] buckets, long[] bucketCounts, long count, double sum) {
    }

    private static final class HistogramState {
        private final double[] buckets;
        private final LongAdder[] counts;
        private final LongAdder count = new LongAdder();
        private final DoubleAdder sum = new DoubleAdder();

        HistogramState(final double[] buckets) {
            this.buckets = buckets.clone();
            this.counts = new LongAdder[buckets.length];
            for (int index = 0; index < buckets.length; index++) {
                counts[index] = new LongAdder();
            }
        }

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

        HistogramSnapshot snapshot() {
            final long[] bucketCounts = new long[counts.length];
            for (int index = 0; index < counts.length; index++) {
                bucketCounts[index] = counts[index].sum();
            }
            return new HistogramSnapshot(buckets.clone(), bucketCounts, count.sum(), sum.sum());
        }
    }

    private static String labelString(final Map<String, String> labels) {
        final List<Map.Entry<String, String>> entries = new ArrayList<>(labels.entrySet());
        entries.sort(Comparator.comparing((Map.Entry<String, String> entry) -> entry.getKey()));
        final StringJoiner joiner = new StringJoiner(",");
        entries.forEach(entry -> joiner.add(entry.getKey() + "=" + entry.getValue()));
        return joiner.toString();
    }
}
