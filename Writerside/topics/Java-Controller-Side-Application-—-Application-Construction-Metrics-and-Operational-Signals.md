# Application Construction — Metrics and Operational Signals

<show-structure for="chapter" depth="2"/>

The Java Controller Side Application exports the fixed metric catalog held by `SdnMplsMlMetrics` at `GET /csa/metrics`. The provider constructs one shared registry, passes it to classification, topology, path, policy, OpenFlow, and LSP services, and registers `PrometheusMetricsServlet` through Karaf's `HttpService`. Each scrape renders the registry at request time as Prometheus text with a `200` response. [Operational State, Metrics, and Structured Logging](Java-Controller-Side-Application-—-Application-Construction-Operational-State-Metrics-and-Structured-Logging.md#metric-registry-and-scrape-endpoint) owns the broader relationship among this scrape surface, the MD-SAL operational-state publisher, and JSON logs. This page is the catalog reference for the metric names themselves.

## Registry, endpoint, and family types {id="java-metrics-registry-endpoint-and-family-types"}

The registry has three concurrent stores. `LongAdder` values back counters, `Double` values replace gauge samples, and per-series histogram state retains bucket adders, a count, and a sum. The registry's immutable metadata map is the publication boundary: counter and gauge writers can technically create an unregistered internal key, but `renderPrometheusText()` iterates only the registered catalog. Histogram observations are stricter because they reject an unknown name or any registered name whose type is not `HISTOGRAM`.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/metrics/SdnMplsMlMetrics.java" include-lines="43-50"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/metrics/SdnMplsMlMetrics.java" include-lines="83-90"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/metrics/SdnMplsMlMetrics.java" include-lines="176-200"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/impl/SDN_MPLS_ML_Provider.java" include-lines="802-814"/>

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/metrics/PrometheusMetricsServlet.java" include-lines="58-64"/>

<code-block lang="mermaid">
    sequenceDiagram
        participant Service as CSA service
        participant Registry as SdnMplsMlMetrics
        participant Provider as CSA provider
        participant Servlet as PrometheusMetricsServlet
        participant Prometheus as Prometheus
        Service->>Registry: increment, setGauge, or observeHistogram
        Provider->>Servlet: register at /csa/metrics
        Prometheus->>Servlet: GET /csa/metrics
        Servlet->>Registry: renderPrometheusText()
        Registry-->>Servlet: HELP, TYPE, and live samples
        Servlet-->>Prometheus: 200 Prometheus exposition
</code-block>

The `Counter`, `Gauge`, and `Histogram` types in the catalog are Prometheus family types, not Java field types. Counters are monotonically incremented per normalized series. Gauges are replaced with the current `double` value. Histograms render `_bucket`, `_sum`, and `_count` samples with seconds as the unit for every duration family. The table below uses the metadata help text as the operational definition, so it covers families that have not yet received a sample in the current controller process.

## Counter families {id="java-metrics-counter-families"}

Counter names describe occurrences rather than current state. A `_total` suffix usually makes the accumulation intent explicit, although several legacy packet-ignore family names are counters without that suffix. Label keys are intentionally not declared by the metadata catalog. Each producer supplies the labels relevant to its event, and the registry sorts label keys, converts blank or null values to `unknown`, and truncates other values to 96 characters before forming a series key. This allows the same catalog to serve packet, topology, consensus, and LSP evidence without pretending that every family has one shared label schema.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/metrics/SdnMplsMlMetrics.java" include-lines="358-414"/>

| Metric name | Prometheus type | Operational meaning |
|---|---|---|
| `sma_packet_in_total` | `Counter` | PacketIn notifications received. |
| `sma_packet_in_control_plane_not_ready_total` | `Counter` | PacketIn ignored before readiness. |
| `packet_ignored_control_plane_not_ready` | `Counter` | PacketIn ignored before control-plane readiness. |
| `packet_ignored_arp` | `Counter` | ARP PacketIn notifications ignored. |
| `packet_ignored_unsupported_eth_type` | `Counter` | Unsupported ethertype PacketIn notifications ignored. |
| `packet_ignored_unknown_ingress` | `Counter` | PacketIn notifications ignored due to unknown ingress. |
| `sma_odl_topology_discovery_success_total` | `Counter` | Successful ODL topology discovery calls. |
| `sma_odl_topology_discovery_failure_total` | `Counter` | Failed ODL topology discovery calls. |
| `sma_odl_bgpls_resolution_failure_total` | `Counter` | BGP-LS graph-node resolution failures. |
| `sma_classification_cache_hit_total` | `Counter` | Classification cache hits. |
| `sma_classification_cache_miss_total` | `Counter` | Classification cache misses. |
| `sma_classifier_request_total` | `Counter` | External classifier requests sent. |
| `sma_classifier_failure_total` | `Counter` | External classifier failures. |
| `sma_path_cache_hit_total` | `Counter` | Calculated path cache hits. |
| `sma_path_cache_miss_total` | `Counter` | Calculated path cache misses. |
| `sma_path_computation_request_total` | `Counter` | CSPF path-computation requests sent. |
| `sma_path_computation_success_total` | `Counter` | Successful CSPF path-computation requests. |
| `sma_path_computation_failure_total` | `Counter` | Failed CSPF path-computation requests. |
| `sma_pcep_topology_refresh_total` | `Counter` | PCEP topology reads attempted. |
| `sma_pcep_topology_refresh_failure_total` | `Counter` | PCEP topology read failures. |
| `sma_delegated_lsp_discovered_total` | `Counter` | Delegated LSP records discovered. |
| `sma_delegated_lsp_missing_total` | `Counter` | Configured delegated LSP records missing. |
| `sma_update_lsp_request_total` | `Counter` | PCEP update-lsp requests sent. |
| `sma_update_lsp_success_total` | `Counter` | PCEP update-lsp requests confirmed. |
| `sma_update_lsp_failure_total` | `Counter` | PCEP update-lsp failures. |
| `sma_update_lsp_skipped_no_change_total` | `Counter` | PCEP update-lsp requests skipped due to matching state. |
| `sma_lsp_update_sent_total` | `Counter` | Directional LSP policy updates sent. |
| `sma_lsp_update_skipped_converged_total` | `Counter` | Directional LSP updates skipped as converged. |
| `sma_lsp_update_bandwidth_mismatch_total` | `Counter` | Directional LSP bandwidth mismatches observed. |
| `sma_pair_policy_apply_failure_total` | `Counter` | Pair-policy LSP application failures. |
| `sma_openflow_inventory_discovery_attempts_total` | `Counter` | OpenFlow inventory discovery attempts. |
| `sma_openflow_inventory_discovery_success_total` | `Counter` | OpenFlow inventory discovery successes. |
| `sma_openflow_inventory_discovery_failure_total` | `Counter` | OpenFlow inventory discovery failures. |
| `sma_openflow_switch_resolved_total` | `Counter` | OpenFlow switches resolved. |
| `sma_openflow_connector_resolved_total` | `Counter` | OpenFlow connectors resolved. |
| `sma_openflow_flow_install_attempts_total` | `Counter` | OpenFlow bootstrap flow install attempts. |
| `sma_openflow_flow_install_success_total` | `Counter` | OpenFlow bootstrap flow install successes. |
| `sma_openflow_flow_install_failure_total` | `Counter` | OpenFlow bootstrap flow install failures. |
| `sma_directional_evidence_recorded_total` | `Counter` | Directional policy evidence records stored. |
| `sma_pair_consensus_pending_total` | `Counter` | Pair consensus decisions pending one side. |
| `sma_pair_consensus_unresolved_total` | `Counter` | Pair consensus decisions unresolved. |
| `sma_packet_workflow_pending_consensus_total` | `Counter` | Packet workflows pending pair consensus. |
| `sma_packet_workflow_deferred_total` | `Counter` | Packet workflows deferred by pair policy. |
| `sma_pair_consensus_match_total` | `Counter` | Pair consensus decisions with matching evidence. |
| `sma_pair_consensus_priority_selected_total` | `Counter` | Pair consensus priority conflict selections. |
| `sma_pair_policy_refresh_total` | `Counter` | Active pair policies refreshed. |
| `sma_pair_policy_deferred_total` | `Counter` | Pair policy candidates deferred. |
| `sma_pair_policy_preempt_total` | `Counter` | Pair policies preempted. |
| `sma_pair_policy_expired_total` | `Counter` | Expired pair policies replaced. |
| `sma_pair_policy_active_total` | `Counter` | Pair policies installed or refreshed as active. |
| `sma_registry_classification_expired_evictions_total` | `Counter` | Classification cache entries expired. |
| `sma_registry_calculated_path_expired_evictions_total` | `Counter` | Calculated paths expired. |
| `sma_registry_directional_evidence_expired_evictions_total` | `Counter` | Directional evidence expired. |
| `sma_registry_active_pair_policy_expired_evictions_total` | `Counter` | Active pair policies expired. |
| `sma_bgpls_topology_ttl_expired_total` | `Counter` | Times BGP-LS topology reached TTL. |
| `sma_bgpls_topology_refresh_on_demand_total` | `Counter` | On-demand BGP-LS refreshes started. |
| `sma_bgpls_topology_refresh_deduplicated_total` | `Counter` | BGP-LS refreshes skipped by single-flight state. |
| `sma_controller_operational_state_publish_success_total` | `Counter` | Operational state publish successes. |
| `sma_controller_operational_state_publish_failure_total` | `Counter` | Operational state publish failures. |
| `sma_pair_consensus_decision_total` | `Counter` | Pair consensus decisions by status. |
| `sma_pair_consensus_equal_priority_total` | `Counter` | Equal-priority pair consensus conflicts. |
| `sma_pair_consensus_require_both_block_total` | `Counter` | Consensus blocked waiting for both directions. |
| `sma_pair_policy_retained_stronger_active_total` | `Counter` | Active stronger policies retained. |
| `sma_pair_policy_retained_equal_priority_total` | `Counter` | Equal-priority active policies retained. |
| `sma_pair_policy_replaced_expired_total` | `Counter` | Expired pair policies replaced. |
| `sma_pair_policy_preempted_by_priority_total` | `Counter` | Pair policies preempted by higher priority. |
| `sma_lsp_application_ero_confirmed_total` | `Counter` | Directional LSP ERO confirmations. |
| `sma_lsp_application_bandwidth_confirmed_total` | `Counter` | Directional LSP bandwidth confirmations. |
| `sma_lsp_application_bandwidth_unconfirmed_total` | `Counter` | Directional LSP bandwidth mismatches after update. |
| `sma_lsp_application_failed_total` | `Counter` | Directional LSP application failures. |
| `sma_control_cycle_total` | `Counter` | End-to-end control cycles by outcome and class. |
| `sma_lsp_path_verification_total` | `Counter` | Requested ERO versus operational ERO comparisons. |

These counters provide a bridge between the controller's major responsibilities. Packet and classifier outcomes are developed in [PacketIn Classification Workflow](Java-Controller-Side-Application-—-Application-Construction-PacketIn-Classification-Workflow.md), consensus and preemption outcomes are owned by [Pair Policy Consensus and Preemption](Java-Controller-Side-Application-—-Application-Construction-Pair-Policy-Consensus-and-Preemption.md), and LSP-update measures correspond to [Delegated LSP Lifecycle and Tunnel Application](Java-Controller-Side-Application-—-Application-Construction-Delegated-LSP-Lifecycle-and-Tunnel-Application.md). The metric catalog observes those mechanisms. It does not perform their decisions or change their registries.

## Gauge families {id="java-metrics-gauge-families"}

Gauge samples replace the preceding value for the same metric name and normalized label map. They therefore answer current-state questions such as whether OpenFlow bootstrap is ready, how many cache entries exist, or whether topology refresh is in progress. Timestamps use epoch seconds. Boolean conditions use their current numeric gauge value rather than a counter of transitions.

| Metric name | Prometheus type | Operational meaning |
|---|---|---|
| `sma_openflow_bootstrap_ready` | `Gauge` | OpenFlow bootstrap readiness. |
| `sma_openflow_bootstrap_last_success_timestamp` | `Gauge` | Last successful OpenFlow bootstrap time. |
| `sma_registry_classification_exact_entries` | `Gauge` | Current exact classification cache entries. |
| `sma_registry_classification_service_entries` | `Gauge` | Current service classification cache entries. |
| `sma_registry_calculated_path_entries` | `Gauge` | Current calculated path entries. |
| `sma_registry_delegated_lsp_entries` | `Gauge` | Current delegated LSP entries. |
| `sma_registry_directional_evidence_buckets` | `Gauge` | Current directional evidence buckets. |
| `sma_registry_directional_evidence_entries` | `Gauge` | Current directional evidence entries. |
| `sma_registry_active_pair_policy_entries` | `Gauge` | Current active pair policy entries. |
| `sma_registry_directional_lsp_application_entries` | `Gauge` | Current LSP application entries. |
| `sma_registry_tunnel_pair_entries` | `Gauge` | Current tunnel pair entries. |
| `sma_registry_tunnel_direction_entries` | `Gauge` | Current tunnel direction entries. |
| `sma_registry_openflow_switch_entries` | `Gauge` | Current OpenFlow switch entries. |
| `sma_registry_openflow_connector_entries` | `Gauge` | Current OpenFlow connector entries. |
| `sma_registry_bgpls_node_entries` | `Gauge` | Current BGP-LS node entries. |
| `sma_bgpls_topology_fresh` | `Gauge` | Whether BGP-LS topology is fresh. |
| `sma_bgpls_topology_refresh_in_progress` | `Gauge` | Whether BGP-LS refresh is in progress. |
| `sma_bgpls_topology_last_success_epoch_seconds` | `Gauge` | Last successful BGP-LS refresh Unix timestamp. |
| `sma_bgpls_topology_last_attempt_epoch_seconds` | `Gauge` | Last BGP-LS refresh attempt Unix timestamp. |
| `sma_bgpls_topology_fresh_until_epoch_seconds` | `Gauge` | BGP-LS fresh-until Unix timestamp. |

The OpenFlow readiness and timestamp gauges correspond directly to the bootstrap phases documented in [OpenFlow Bootstrap and Packet Suppression](Java-Controller-Side-Application-—-Application-Construction-OpenFlow-Bootstrap-and-Packet-Suppression.md). The BGP-LS freshness gauges belong to the discovery and cache lifecycle described in [Topology Discovery and Path Computation](Java-Controller-Side-Application-—-Application-Construction-Topology-Discovery-and-Path-Computation.md). Registry-entry gauges report the values gathered for controller operational state, but they remain a Prometheus view rather than the YANG binding itself.

## Duration histogram families {id="java-metrics-duration-histogram-families"}

Every histogram measures seconds. The classifier, path-computation, update-LSP, and control-cycle families use the ordinary duration boundary set from 1 millisecond through 30 seconds. LSP convergence has a wider final finite bound of 60 seconds. An observation increments the first finite bucket that contains its value, while exposition accumulates those first-match counts to produce Prometheus's required cumulative buckets before emitting the `+Inf` bucket, sum, and count.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/metrics/SdnMplsMlMetrics.java" include-lines="295-324"/>

| Metric name | Prometheus type | Operational meaning |
|---|---|---|
| `sma_classifier_round_trip_duration_seconds` | `Histogram` | Duration of the CSA-to-Python classifier round trip. |
| `sma_path_computation_duration_seconds` | `Histogram` | Duration of CSPF path computation. |
| `sma_update_lsp_duration_seconds` | `Histogram` | Duration of a PCEP update-LSP interaction. |
| `sma_lsp_convergence_duration_seconds` | `Histogram` | Duration until a PCEP LSP converges. |
| `sma_control_cycle_duration_seconds` | `Histogram` | Duration from an accepted PacketIn to the LSP decision. |

<code-block lang="mermaid">
flowchart LR
    PacketIn[Accepted PacketIn] --> Classifier[Classifier round trip]
    Classifier --> Consensus[Pair-policy consensus]
    Consensus --> Path[CSPF path computation]
    Path --> Update[PCEP update-LSP]
    Update --> Verify[LSP convergence and path verification]
    Classifier --> H1[sma_classifier_round_trip_duration_seconds]
    Path --> H2[sma_path_computation_duration_seconds]
    Update --> H3[sma_update_lsp_duration_seconds]
    Verify --> H4[sma_lsp_convergence_duration_seconds]
    PacketIn --> H5[sma_control_cycle_duration_seconds]
</code-block>

## Scrape consistency and interpretation {id="java-metrics-scrape-consistency-and-interpretation"}

No global lock makes a scrape transactional. Counter, gauge, and histogram maps can change while the servlet renders the exposition, and a histogram snapshot reads its bucket adders, count, and sum independently. The output is therefore a concurrent operational observation. It is still deterministic in family coverage because the exporter writes `HELP` and `TYPE` entries for every cataloged family, even when the registry has no stored series for that family.

<code-block lang="java" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/java/com/sma/sdn/metrics/SdnMplsMlMetrics.java" include-lines="241-263"/>

The normalized-label rule prevents otherwise equivalent label maps from splitting into distinct series because of insertion order. It also constrains blank values and unusually long values before they become metric keys. Consumers should preserve the label keys emitted by a live scrape rather than infer a universal schema from the catalog, because the relevant producer determines the labels for each family. For source-level context about producer behavior, use the linked PacketIn, policy, topology, OpenFlow, and LSP topics instead of treating a metric family as proof that a particular control-plane operation succeeded.
