# SDN-MPLS-ML Tech Demonstrator Documentation Overview

## General Project Information And Attribution

<deflist collapsible="false" type="full">
<def title="Project Information">
<list type="bullet" columns="2">
<li><b><format color="CornflowerBlue">Project Title:</format></b></li>
<li>Diseño e Implementación de una arquitectura <i>Software Defined Networking</i> (SDN) para clasificación y control adaptativo de tráfico sobre una red MPLS-TE</li>
<li><b><format color="CornflowerBlue">Project Author:</format></b></li>
<li>Santiago Francisco Arellano Jaramillo</li>
<li><b><format color="CornflowerBlue">Project Supervisor:</format></b></li>
<li>Ricardo Flores Moyano</li>
<li><b><format color="CornflowerBlue">Expected Graduation Date:</format></b></li>
<li>December 2026</li>
</list>
</def>
</deflist>

## Introduction To The Project

The <b><code>SDN-MPLS-ML Tech Demonstrator</code></b> is a networking and machine learning project that combines <b><code>Software Defined Networking</code></b>, supervised traffic classification, and MPLS-TE tunnel control. Its goal is to show how an SDN controller can observe a first packet, classify the expected application type, select a deterministic traffic policy, compute or reuse an MPLS-TE path, and reconfigure bidirectional RSVP-TE delegated LSPs over a controlled core network.

From an implementation perspective, the demonstrator is not a single monolithic application. It is a coordinated system built from a Cisco XRv MPLS-TE topology, a trained XGBoost traffic classifier, a FastAPI inference service, an OpenDaylight-based controller side application, and an observability layer that exposes both application and network metrics. The tabs below summarize each major component and the responsibilities that should be kept in mind while reading the rest of this documentation.

<procedure type="choices" title="Major Implemented Components" collapsible="false" id="major-implemented-components">
<tabs group="major-component">
<tab title="MPLS-TE Core Network" group-key="mpls-te-core">
<p>The MPLS-TE core is the physical and logical baseline of the demonstrator. It is implemented as a four-router Cisco XRv topology deployed through Containerlab and built from Cisco CML images. The selected image, <b><code>Cisco XRv 6.6.3 Full</code></b>, provides the MPLS, OSPF-TE, RSVP-TE, PCEP, and BGP-LS capabilities needed by the experiment while keeping resource usage low enough for repeatable virtual deployments.</p>
<deflist type="full" collapsible="true">
<def title="Responsibilities">
<list>
<li><b><format color="CornflowerBlue">MPLS forwarding baseline</format></b>: maintains label switching across the four-router core and provides the data-plane path used by the delegated TE tunnels.</li>
<li><b><format color="CornflowerBlue">Traffic engineering configuration</format></b>: enables OSPF-TE, RSVP-TE, MPLS-TE, tunnel interfaces, and per-link attributes required for constrained path selection.</li>
<li><b><format color="CornflowerBlue">Bidirectional tunnel foundation</format></b>: defines the headend-to-tailend and tailend-to-headend LSPs that later become visible as delegated LSPs to the controller.</li>
<li><b><format color="CornflowerBlue">Topology and metric export</format></b>: advertises router, link, and traffic engineering state through BGP-LS so the controller can build a usable Traffic Engineering Database.</li>
<li><b><format color="CornflowerBlue">PCE/PCC participation</format></b>: acts as the PCC side of the PCEP architecture, allowing OpenDaylight to inspect and update delegated LSPs without reconfiguring router CLI state directly.</li>
</list>
</def>
<def title="Important Design Notes">
<list>
<li>The MPLS-TE layer intentionally owns low-level label distribution and tunnel viability, as the SDN layer only changes the selected explicit route and requested tunnel attributes.</li>
<li>VRF-based designs were discarded for this image because Cisco XRv virtualization lacks the hardware support required by the target VRF behavior. This is due to a documented limitation at the Cisco XRv images were the VRF implementation requires an ASIC system that is only available in physical hardware and has yet to be emulated for these routers.</li>
<li>The topology was validated incrementally because many controller-visible capabilities depend on exact protocol behavior rather than static configuration alone. This was specially significant during the configuration of MPLS-TE, RSVP-TE and OSPF-TE as the reported configuration from Cisco did not align directly with the commands that the Cisco IOS version running on the XRv routers offered. This difference caused many test and revision phases to be done during the definition of the baseline configurations of all routers, and most importantly on the tunnels and their implementation.</li>
</list>
</def>
</deflist>
</tab>
<tab title="XGBoost Traffic Classification Model" group-key="xgboost-model">
<p>The traffic classification model is the machine learning component that maps safe first-packet features to a traffic class. It is trained with XGBoost using selected normal-flow samples from the SDNFlow dataset obtained from IEEE Dataport with support from the project supervisor.</p>
<deflist type="full" collapsible="true">
<def title="Responsibilities">
<list>
<li><b><format color="CornflowerBlue">First-packet classification</format></b>: predicts the traffic class from four quickly extractable fields: <code>Ethernet type, IP protocol, source port, and destination port</code>.</li>
<li><b><format color="CornflowerBlue">Bidirectional service recognition</format></b>: supports flow and response-flow patterns so the controller can classify traffic observed from either direction.</li>
<li><b><format color="CornflowerBlue">Controlled class vocabulary</format></b>: produces one of the known service classes used by the policy layer rather than arbitrary labels.</li>
<li><b><format color="CornflowerBlue">Low-latency inference target</format></b>: keeps the feature set intentionally small so classification can happen in the control loop after a PacketIn notification.</li>
</list>
</def>
<def title="Important Design Notes">
<list>
<li>The model does not directly configure the network. It provides the class evidence consumed by the API and controller-side policy logic.</li>
<li>The first-packet approach was selected because it matches application-aware SD-WAN behavior better than waiting for a completed flow, and it allows for the intended behavior of the network as adaptable to incoming traffic rather than traffic that has already passed, and a configuration that hopes it will come back again in the same format.</li>
<li>The model is treated as an inference artifact at runtime, while operational policy enrichment is performed by the Python API layer. In this case, the model is exported as a dual-file setup with a model and its configuration, allowing for validation to happen at the Python API side.  The rules that are applied per traffic class are added with other configurations files also available in the API, which detaches the model from traffic policy responsibilities.</li>
</list>
</def>
</deflist>
</tab>
<tab title="Python FastAPI Classification API" group-key="python-api">
<p>The Python API wraps the trained classifier in an observable and deterministic inference service. It is deployed as a standalone container and exposes a classification endpoint used by the Java controller side application during PacketIn processing.</p>
<deflist type="full" collapsible="true">
<def title="Responsibilities">
<list>
<li><b><format color="CornflowerBlue">Classifier lifecycle management</format></b>: loads model artifacts and keeps a bounded pool of classifier instances ready for concurrent inference.</li>
<li><b><format color="CornflowerBlue">Request validation</format></b>: validates packet-feature payloads before they reach the model, preventing malformed controller requests from entering the inference path.</li>
<li><b><format color="CornflowerBlue">Policy enrichment</format></b>: maps the predicted class to deterministic operational policy fields such as bandwidth, DSCP, MPLS TC, setup priority, and hold priority.</li>
<li><b><format color="CornflowerBlue">Observability</format></b>: emits structured logs, correlation identifiers, and Prometheus-compatible metrics for inference behavior and API health.</li>
<li><b><format color="CornflowerBlue">Operational isolation</format></b>: separates Python model execution from the Java controller bundle, allowing the classifier runtime to be scaled, monitored, and rebuilt independently.</li>
</list>
</def>
<def title="Important Design Notes">
<list>
<li>The API is currently designed around a bounded queue of classifier instances, initially sized for up to five parallel classification requests. The behavior of this system can be increased or decreased depending on resource usage on the deployed system through environment variables.</li>
<li>The API response is intentionally richer than the raw model prediction because the controller needs a directly actionable traffic policy. This information is then used within the controller side application for consensus over traffic policy and LSP modification.</li>
<li>The service is part of the control loop, so request latency and error visibility are documented as first-class operational concerns reported through Prometheus counters and gauges alongside model classification information.</li>
</list>
</def>
</deflist>
</tab>
<tab title="OpenDaylight Controller And Java CSA" group-key="odl-java-csa">
<p>The controller layer is built around <b><code>OpenDaylight Vanadium 0.23.1</code></b> and a custom Java controller side application deployed as an Apache Karaf feature. OpenDaylight provides the southbound protocol stack and operational data stores, while the controller side application implements the project-specific workflow that ties <code>PacketIn</code> notifications, classification, policy consensus, constrained path computation, delegated LSP updates, OpenFlow bootstrap flows, RESTCONF state exposure, and metrics together.</p>
<deflist type="full" collapsible="true">
<def title="Responsibilities">
<list>
<li><b><format color="CornflowerBlue">Controller distribution</format></b>: packages OpenDaylight with the required BGP-LS, PCEP, OpenFlow, RESTCONF, and NETCONF capabilities enabled for the demonstrator environment.</li>
<li><b><format color="CornflowerBlue">Topology discovery and freshness</format></b>: reads BGP-LS and PCEP operational state, caches it with TTL semantics, and refreshes it before workflows that require fresh topology.</li>
<li><b><format color="CornflowerBlue">Packet workflow orchestration</format></b>: receives OpenFlow <code>PacketIn</code> notifications, extracts safe packet features, calls the classifier API, records evidence, and drives policy decisions.</li>
<li><b><format color="CornflowerBlue">Policy consensus and preemption</format></b>: coordinates bidirectional evidence and decides when a new traffic policy should replace or retain the active pair policy.</li>
<li><b><format color="CornflowerBlue">Constrained path and LSP control</format></b>: invokes OpenDaylight path computation and PCEP <code>update-lsp</code> operations to update delegated RSVP-TE LSPs.</li>
<li><b><format color="CornflowerBlue">Operational state exposure</format></b>: publishes controller-side caches, policy decisions, topology snapshots, OpenFlow state, and readiness state through the <code>csa:controller-state</code> RESTCONF tree.</li>
</list>
</def>
<def title="Important Design Notes">
<list>
<li>NETCONF remains available in the controller image, but the final design does not rely on NETCONF to change tunnel state.</li>
<li>The controller image is custom because official OpenDaylight Docker images do not provide the exact Vanadium runtime required by this project.</li>
<li>The Java application treats OpenDaylight as both a protocol gateway and a source of truth for operational topology, rather than as a passive library.</li>
</list>
</def>
</deflist>
</tab>
<tab title="Grafana Dashboards And Observability" group-key="observability">
<p>The observability layer makes the system measurable outside the control loop. It combines Prometheus-style metrics from the Python API and Java controller side application with SNMP-derived router metrics and Grafana dashboards for validation, troubleshooting, and demonstration.</p>
<deflist type="full" collapsible="true">
<def title="Responsibilities">
<list>
<li><b><format color="CornflowerBlue">Application metrics collection</format></b>: scrapes classifier and controller metrics for classification, cache, topology, path computation, LSP update, consensus, and control-cycle behavior.</li>
<li><b><format color="CornflowerBlue">Router and interface visibility</format></b>: gathers interface and network device metrics through SNMP exporters for topology-level validation.</li>
<li><b><format color="CornflowerBlue">Operational validation</format></b>: provides dashboards that correlate control-plane decisions with network and application behavior.</li>
<li><b><format color="CornflowerBlue">Debugging support</format></b>: exposes failure counters, latency histograms, freshness gauges, and current controller state so experiments can be explained after execution.</li>
<li><b><format color="CornflowerBlue">RESTCONF state complement</format></b>: complements Grafana dashboards with direct controller-state retrieval through OpenDaylight RESTCONF endpoints.</li>
</list>
</def>
<def title="Important Design Notes">
<list>
<li>Metrics are not just a presentation layer, they are part of the validation strategy for a system with several asynchronous subsystems.</li>
<li>RESTCONF operational data and Prometheus metrics answer different questions, so both are intentionally documented.</li>
<li>The dashboards are expected to evolve as new experiments and validation criteria are added.</li>
</list>
</def>
</deflist>
</tab>
</tabs>
</procedure>

All major components in this thesis have been implemented with an explicit validation-before-integration mindset. Cisco XRv behavior, OpenDaylight API contracts, PCEP update semantics, RESTCONF state visibility, classifier response contracts, and metrics exposition were each validated before being treated as stable building blocks. This approach was necessary because several technologies in the stack expose behavior that depends on the exact runtime, image version, YANG model, or operational state available at the moment of testing.

## Format And Content Of This Documentation

This documentation focuses on the implementation details of the demonstrator. The thesis document discusses the research framing, motivation, evaluation, results, and future work. This Writerside documentation is meant to explain how the system is built, deployed, operated, inspected, and extended. It therefore includes configuration details, runtime topology descriptions, API contracts, class and module diagrams, operational endpoints, metrics, and concrete engineering tradeoffs.

The content is organized around the same layers that exist in the demonstrator. Readers can use it as a deployment guide, a component reference, or a technical map for understanding how a <code>PacketIn</code> event becomes a traffic policy decision and, eventually, a delegated LSP update.

<deflist type="full" collapsible="true">
<def title="Network Topology Implementation">
<p>This section covers the networking side of the thesis: virtualization prerequisites, Containerlab topology definitions, Cisco XRv image assumptions, router startup behavior, OSPF-TE, RSVP-TE, MPLS-TE, BGP-LS, PCEP, tunnel interfaces, and the baseline delegated LSP setup.</p>
<list>
<li>Environment requirements and host dependencies.</li>
<li>Router images, licensing assumptions, and known XRv limitations.</li>
<li>Full topology structure, addressing, interfaces, and link roles.</li>
<li>Protocol configuration and the reason each protocol is required.</li>
<li>Validation commands used to prove the MPLS-TE and PCEP baseline is operational.</li>
</list>
</def>
<def title="Model Implementation And Deployment">
<p>This section covers the machine learning side of the thesis: dataset selection, feature reduction, model training, model packaging, inference behavior, policy mapping, and the FastAPI deployment that exposes the classifier to the controller side application.</p>
<list>
<li>Dataset origin, class selection, and preprocessing assumptions.</li>
<li>Feature set rationale for the first-packet classification model.</li>
<li>XGBoost training, evaluation, and exported model artifacts.</li>
<li>FastAPI request and response schemas.</li>
<li>Classifier pool behavior, runtime configuration, logging, and metrics.</li>
</list>
</def>
<def title="SDN Controller Implementation And Deployment">
<p>This section covers the OpenDaylight and Java controller side application implementation: custom controller image, required OpenDaylight features, Java bundle lifecycle, service graph, topology refresh behavior, PacketIn workflow, policy consensus, path computation, delegated LSP updates, OpenFlow bootstrap, RESTCONF operational state exposure, and controller metrics.</p>
<list>
<li>OpenDaylight Vanadium runtime and enabled protocol features.</li>
<li>Controller side application initialization and readiness checks.</li>
<li>Packet workflow sequence from OpenFlow notification to LSP convergence.</li>
<li>Registry/cache behavior and TTL-driven refresh semantics.</li>
<li>RESTCONF operational endpoints under <code>csa:controller-state</code>.</li>
<li>Prometheus metrics exposed through the controller side application.</li>
</list>
</def>
<def title="Observability, Validation, And Demonstration Workflow">
<p>This section covers the supporting operational material: Prometheus metrics, Grafana dashboards, SNMP-based router visibility, structured logs, validation scripts, experiment flow, and the evidence used to prove that the controller action changed the operational tunnel state.</p>
<list>
<li>Application metrics from the Python API and Java controller.</li>
<li>Router and interface metrics exported through SNMP.</li>
<li>Dashboard panels used for live demonstration and post-run analysis.</li>
<li>Validation paths for topology freshness, classification, path computation, and LSP update confirmation.</li>
<li>Known failure modes and the evidence expected when each one occurs.</li>
</list>
</def>
</deflist>

## Frequently Asked Questions

<deflist type="full" collapsible="true">
<def title="Is this documentation a replacement for the thesis document?">
<p>No. The thesis document presents the research framing, motivation, evaluation, conclusions, and formal academic discussion. This Writerside documentation is the implementation companion, therefore, it explains how the demonstrator is assembled, configured, executed, validated, and inspected.</p>
</def>
<def title="Why does the system classify only the first packet instead of waiting for a complete flow?">
<p>The first-packet approach allows the controller to make a policy decision early enough to affect the tunnel selection for the flow. Waiting for the flow to finish would be useful for offline analysis, but it would not support adaptive path control during the flow itself.</p>
</def>
<def title="Why are delegated LSPs used instead of creating tunnels from scratch every time?">
<p>The project relies on preexisting RSVP-TE LSPs delegated to the PCE. This keeps router-side tunnel identity stable while allowing the controller to update path and bandwidth requirements through PCEP operations. The main reason delegated LSPs were used was due to an <b>incompatibility between Opendaylight and Cisco XRv routers</b> which caused tunnels to be created but fail to register at the controller as delegated. Specifically the <code>ipv4 unnumbered A.B.C.D</code> parameter that is required for all tunels in these routers was not explicitly supported by the controller and therefore all tunnels were created in an incomplete state.</p>
<p>An effort was made, and is documented to use the NETCONF connections implemented for all routers to append after creation the interface IP assignment, and to attempt to bring back the tunnel. Despite being able to successfully replace the IP assignment, the tunnel never registered itself as delegated to the controller given that from the Cisco XRv side it reported failure to the controller. This caused the system to become split, the controller couldn't manage the tunnel, and the routers could only hold onto it on the state it was created in.</p>
<p>Although it was posited that perhaps this mechanism could be kept, deletion of the tunnels was impossible, so in an effort to not introduce more <code>version and IOS dependant commands into the creation flow</code> a decision was made to use <code>previously defined delegated tunnels</code> that could allow the system to showcase its programmable prowess without the need to couple these systems more closely than needed.</p>
</def>
<def title="Why does the documentation include both RESTCONF state and Prometheus metrics?">
<p>RESTCONF exposes the current structured operational state of the controller side application, including caches, topology objects, and policy state. Prometheus metrics expose counters, gauges, and latency histograms that are better suited for dashboards, trends, and failure-rate analysis. Both are needed for complete validation.</p>
<p>In addition to representing different states of the system, the use of RESTCONF presents the system as another component within the normal RESTCONF tree exposed by the Opendaylight controller, and it allows us to register our application as a proper module within the Apache Karaf system they use. This means that our system presents its information in a documented, structured approach like any other component of the controller in both XML and JSON schemas.</p>
</def>
<def title="Can this demonstrator be extended to more routers, classes, or policies?">
<p>Yes, but each extension has a different cost. More routers require topology and protocol validation. More classes require dataset and model work. More policies require deterministic mapping in the classifier API and corresponding controller-side validation. The documentation is structured so those extension points can be expanded later.</p>
</def>
</deflist>
