# Observability Layer Implementation — Grafana Implementation

<show-structure for="chapter" depth="2"/>

Grafana is the visualization layer for the Prometheus scrape data produced by the Python Inference API, the Java Controller Side Application, and the SNMP exporter. The Containerlab topology bind-mounts the repository provisioning tree into the Grafana container at <path>/etc/grafana/provisioning</path>. Its dashboard provider then loads every dashboard artifact from <path>/etc/grafana/provisioning/dashboards</path> into the <code>SDN-MPLS-ML Tech Demonstrator</code> folder. The same provisioning tree registers <code>Prometheus</code> as the default proxy datasource, while <path>services/prometheus.yml</path> defines the API, controller, and router scrape targets that supply the panels.

<code-block lang="yaml" src="../../src/main/containerlab/configurations/tech-demonstrator/services/provisioning/dashboards/dashboard.yml" include-lines="1-15"/>

## Provisioned dashboard artifacts {id="grafana-provisioned-dashboard-artifacts"}

The dashboard files are maintained together in <path>src/main/containerlab/configurations/tech-demonstrator/services/provisioning/dashboards</path>. Each is a Grafana v2 <code>Dashboard</code> artifact and remains editable after file provisioning, so the repository file is the durable configuration to update when a panel is changed. The Python and controller dashboards expose the application-specific metric families cataloged in [Python API Metrics and Operational Signals](Python-Inference-API-—-Application-Construction-Metrics-and-Operational-Signals.md) and [Java Controller Metrics and Operational Signals](Java-Controller-Side-Application-—-Application-Construction-Metrics-and-Operational-Signals.md). The integrated dashboard adds SNMP-derived network evidence to those application-level observations.

| Dashboard | Repository artifact | Grafana metadata name | Operational focus |
|---|---|---|---|
| Python Inference API | <path>python-inference-api-observabilidad.json</path> | <code>sdn-ml-api-observabilidad</code> | API readiness, classifier-pool capacity, classifications, inference timing, confidence, policy selection, and controlled failures. |
| Java Controller Side Application | <path>java-controller-side-application-observabilidad.json</path> | <code>sdn-ml-csa-observabilidad</code> | Control cycles, pair-policy consensus, topology freshness, OpenFlow bootstrap, CSPF and PCEP timing, LSP convergence, caches, and application failures. |
| Integrated MPLS Network and Services | <path>sdn-mpls-observabilidad-integral.json</path> | <code>sdn-mpls-observabilidad-integral</code> | MPLS link utilization, router availability, interface errors and discards, plus the inference API and controller-side application as system services. |

## Python Inference API dashboard {id="grafana-python-inference-api-dashboard"}

<path>src/main/containerlab/configurations/tech-demonstrator/services/provisioning/dashboards/python-inference-api-observabilidad.json</path> defines the <code>sdn-ml-api-observabilidad</code> dashboard. Its panels establish whether the API is ready, identify active workers and pool occupancy, then relate completed classifications to predicted traffic classes, confidence, selected policies, request latency, inference latency, and bounded error categories. This view is intended to distinguish unavailable model capacity or controlled request rejection from a successful classification cycle. The underlying metric meanings and label contracts remain owned by the [Python API metric catalog](Python-Inference-API-—-Application-Construction-Metrics-and-Operational-Signals.md).

<note>
<b>Screenshot placeholder.</b> Add a sanitized capture of this dashboard at <path>Writerside/images/grafana-python-inference-api-dashboard.png</path> and replace this note with an image that shows the current readiness, pool, latency, and error panels.
</note>

## Java Controller Side Application dashboard {id="grafana-java-controller-side-application-dashboard"}

<path>src/main/containerlab/configurations/tech-demonstrator/services/provisioning/dashboards/java-controller-side-application-observabilidad.json</path> defines the <code>sdn-ml-csa-observabilidad</code> dashboard. It follows the controller's decision path from the external classifier request through pair-policy consensus, BGP-LS topology freshness, CSPF calculation, PCEP Update-LSP, and operational LSP convergence. The dashboard also exposes bootstrap readiness, cache effectiveness, active policy state, preemption behavior, and component-level failures. A reported successful control cycle describes the CSA outcome and does not by itself establish router-level bandwidth confirmation, which is why convergence and path-verification panels remain separate.

<note>
<b>Screenshot placeholder.</b> Add a sanitized capture at <path>Writerside/images/grafana-java-controller-side-application-dashboard.png</path> and replace this note with an image that shows the control-cycle, topology, PCEP, and LSP-convergence panels.
</note>

## Integrated MPLS network and services dashboard {id="grafana-integrated-mpls-network-and-services-dashboard"}

<path>src/main/containerlab/configurations/tech-demonstrator/services/provisioning/dashboards/sdn-mpls-observabilidad-integral.json</path> defines the <code>sdn-mpls-observabilidad-integral</code> dashboard. This is the cross-layer view: it maps bidirectional MPLS link utilization against the configured capacity, allows selection of a logical link for current and historical traffic, and reports router availability together with interface errors and discards. Its service panels provide the surrounding inference API and controller-side application context, allowing a network condition to be assessed alongside the control-plane and classification services. The Prometheus scrape configuration identifies the three source categories as the API, CSA, and SNMP exporter targets.

<note>
<b>Screenshot placeholder.</b> Add a sanitized capture at <path>Writerside/images/grafana-integrated-mpls-network-and-services-dashboard.png</path> and replace this note with an image that shows the MPLS topology, selected-link traffic, router availability, and interface-quality panels.
</note>
