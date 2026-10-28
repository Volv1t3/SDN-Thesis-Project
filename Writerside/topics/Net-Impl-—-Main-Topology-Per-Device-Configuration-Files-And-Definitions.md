# Main Topology Definition — Per Device Configuration Files And Definitions

This page documents the device-level and service-level configuration that backs the main topology definition. It is organized by Cisco XRv devices, the OpenDaylight controller-side configuration, OpenFlow switch bootstrap behavior, and the observability stack.

<show-structure for="chapter,def" depth="2"/>

## Cisco Devices {id="per-device-cisco-devices"}

<p>The four Cisco XRv nodes share the same baseline design: a <code>loopback router ID</code>, <code>OSPF area 0</code>, <code>MPLS-TE</code> on constrained core links, <code>RSVP</code> bandwidth constraints, <code>BGP-LS</code> sessions, <code>NETCONF/SSH</code> management, and <code>SNMP</code> monitoring. LSR1 Alpha and LSR4 Delta additionally define <code>delegated RSVP-TE</code> tunnels because they are the two headends controlled by the application workflow.</p>

<p>The configuration uses IOS XR control-plane resources for distinct jobs: the loopback is the stable protocol identity; OSPF with TE advertises the eligible core topology; RSVP makes the configured reservable bandwidth available to TE signalling; and BGP-LS distributes that topology to OpenDaylight. The descriptions below explain the role of each resource in this lab rather than treating an interface declaration as a tunnel by itself. The terminology and command families follow Cisco's <a href="https://www.cisco.com/c/en/us/support/ios-nx-os-software/ios-xr-software-end-of-sale/products-installation-and-configuration-guides-list.html#anchor110">IOS XR 6.6.x configuration-guide inventory</a>; the deployed XRv image is version <code>6.6.3</code>.</p>

### `sma-xrv-lsr1-alpha` {id="config-sma-xrv-lsr1-alpha"}

Full configuration source view:

<code-block lang="plain text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr1-alpha.cfg" collapsible="true"/>

<deflist type="full" collapsible="true">
<def title="Hostname, Router ID, And Access Interface">
<tabs group="lsr1-hostname-loopback-access">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr1-alpha.cfg" include-lines="1-10"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p><code>Loopback0</code> is LSR1's stable protocol identity. Its <code>/32</code> address is reused as the OSPF router ID, MPLS-TE router ID, BGP-LS update source, and the unnumbered address of the delegated tunnel. Keeping these identities on a loopback prevents a core-link failure from changing the router identity that peers and controller records use.</p>
<p><code>GigabitEthernet0/0/0/0</code> is an access-facing routed interface for the <code>192.168.10.0/24</code> site. It forwards user traffic into the LSR but is deliberately not an RSVP/MPLS-TE resource: it does not contribute an eligible core link to path computation.</p>
</tab>
</tabs>
</def>
<def title="Core And Control Interfaces">
<tabs group="lsr1-core-control">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr1-alpha.cfg" include-lines="12-35"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>These Layer-3 point-to-point interfaces are the physical resources from which the TE graph is built. The baseline links reach LSR2 and LSR3; the <code>VARIANT-2</code> links add alternate LSR2 and LSR4 paths. Their IP addresses establish adjacency reachability only—none of them is an LSP or a tunnel until OSPF-TE, MPLS-TE, and RSVP enable the corresponding control-plane functions.</p>
</tab>
</tabs>
</def>
<def title="Delegated RSVP-TE Tunnel">
<tabs group="lsr1-tunnel">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr1-alpha.cfg" include-lines="37-45"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>This pre-created interface is the forward delegated LSP that the controller-side application identifies by its signalled name and tunnel interface. The <code>pce delegation</code> setting allows the stateful PCE workflow to take control of that LSP; it does not make every core interface PCEP-controlled.</p>
<p><code>ipv4 unnumbered Loopback0</code> gives the tunnel a stable source identity without allocating a separate subnet. Tunnel <code>110</code> targets LSR4's loopback <code>14.14.14.14</code>; <code>path-option 10 dynamic</code> leaves the initial route to dynamic TE computation. Those identifiers name the LSP endpoints, while the selected ERO can change when the eligible topology or its constraints change.</p>
</tab>
</tabs>
</def>
<def title="OSPF Traffic Engineering">
<tabs group="lsr1-ospf">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr1-alpha.cfg" include-lines="47-75"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>OSPF supplies underlay reachability and carries the TE attributes for the core. The loopback and access interfaces are passive, so their prefixes are advertised without forming unnecessary OSPF neighbor relationships. The point-to-point core interfaces form the OSPF adjacencies from which the TE database is learned.</p>
<p><code>mpls traffic-eng router-id Loopback0</code> deliberately reuses the stable loopback identity. <code>distribute link-state instance-id 36</code> enables this router's OSPF link-state information to be exported through the local BGP-LS instance, making the same topology consumable by OpenDaylight.</p>
</tab>
</tabs>
</def>
<def title="MPLS-TE, PCEP, And RSVP Bandwidth">
<tabs group="lsr1-mpls-pce-rsvp">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr1-alpha.cfg" include-lines="76-105"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p><code>mpls traffic-eng interface</code> marks only the core links as eligible for TE. The access and management networks remain outside the TE resource pool. Each RSVP <code>bandwidth</code> value is the reservable capacity advertised for that interface; it intentionally mirrors the Containerlab link and netem constraint assigned to that path.</p>
<p>LSR1 is the PCC and peers with OpenDaylight at <code>10.100.10.2</code>, the PCE. Stateful-client reporting and instantiation let the controller observe and update delegated LSP state. Before a controller update is available, the dynamic path option permits a locally computed TE path; later controller updates use the same advertised topology and RSVP constraints.</p>
</tab>
</tabs>
</def>
<def title="BGP-LS, Static Routing, And Management Services">
<tabs group="lsr1-bgpls-management">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr1-alpha.cfg" include-lines="106-153"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>The <code>link-state link-state</code> address family carries topology information rather than ordinary IPv4 reachability. Loopback-sourced peers keep the router mesh bound to stable identities; the dedicated ODL neighbor uses the directly attached control link. The static route makes the remote access prefix use the delegated tunnel.</p>
<p><code>netconf-yang agent ssh</code> and the SSH server expose the NETCONF management transport used by the controller. <code>snmp-server community ... RO</code> grants the exporter read-only polling access, while <code>snmp-server ifindex persist</code> keeps interface-index values stable across restarts so time series retain their interface identity.</p>
</tab>
</tabs>
</def>
</deflist>

### `sma-xrv-lsr2-bravo` {id="config-sma-xrv-lsr2-bravo"}

Full configuration source view:

<code-block lang="text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr2-bravo.cfg" collapsible="true"/>

<deflist type="full" collapsible="true">
<def title="Hostname, Router ID, And Core Interfaces">
<tabs group="lsr2-core">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr2-bravo.cfg" include-lines="1-20"/>
<code-block lang="text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr2-bravo.cfg" include-lines="27-35"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>LSR2 is a transit router: it has no user-facing access subnet and no delegated tunnel headend. Its loopback supplies the stable OSPF, MPLS-TE, and BGP-LS identity; its point-to-point interfaces contribute the upper path, alternate LSR1 and LSR4 paths, and the deliberately constrained LSR2–LSR3 path to the TE resource graph.</p>
</tab>
</tabs>
</def>
<def title="Controller-Facing Interface">
<tabs group="lsr2-control">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr2-bravo.cfg" include-lines="22-25"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>This interface gives OpenDaylight a dedicated control-plane path to LSR2 for BGP-LS exchange. It is not enabled for MPLS-TE or RSVP because it is not a traffic-engineered data-plane link. The direct controller session complements the loopback-based iBGP mesh, so each router remains an independently reachable topology source.</p>
</tab>
</tabs>
</def>
<def title="OSPF And MPLS-TE">
<tabs group="lsr2-ospf-mpls">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr2-bravo.cfg" include-lines="37-70"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>OSPF advertises the transit links as point-to-point adjacencies and carries their TE attributes in area <code>0</code>. The matching <code>mpls traffic-eng interface</code> declarations are the admission boundary: a core link must appear in both the OSPF topology and the MPLS-TE resource set before a computed RSVP-TE LSP can use it.</p>
</tab>
</tabs>
</def>
<def title="RSVP Bandwidth">
<tabs group="lsr2-rsvp">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr2-bravo.cfg" include-lines="72-87"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>These RSVP values are the reservable bandwidth advertised by LSR2's TE-capable links. They mirror the topology capacities and are deliberately asymmetric, so a bandwidth-aware path computation can distinguish the upper, alternate, and vertical paths.</p>
<p>When an LSP is signalled, the selected route is represented by its RSVP-TE signalling state and, when supplied by the controller, an ERO. The figures here therefore model a resource constraint, not a fixed data rate forced onto all traffic on the interface.</p>
</tab>
</tabs>
</def>
<def title="BGP-LS And Management Services">
<tabs group="lsr2-bgpls-management">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr2-bravo.cfg" include-lines="89-133"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>The iBGP link-state mesh lets LSR2 contribute its TE links to the shared topology view, and the ODL neighbor receives the same link-state address family. NETCONF/SSH exposes the management plane. The read-only SNMPv2c community is consumed by the SNMP exporter to collect IF-MIB interface counters; it is not a write-capable device-management credential.</p>
</tab>
</tabs>
</def>
</deflist>

### `sma-xrv-lsr3-charlie` {id="config-sma-xrv-lsr3-charlie"}

Full configuration source view:

<code-block lang="text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr3-charlie.cfg" collapsible="true"/>

<deflist type="full" collapsible="true">
<def title="Hostname, Router ID, And Core Interfaces">
<tabs group="lsr3-core">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr3-charlie.cfg" include-lines="1-20"/>
<code-block lang="text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr3-charlie.cfg" include-lines="27-30"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>LSR3 is the lower-path transit router. Its <code>13.13.13.13/32</code> loopback is the common OSPF, MPLS-TE, and BGP-LS identity. The core links make the lower route and its alternates visible to TE, including the high-capacity path into LSR4 over <code>10.0.25.0/30</code>.</p>
</tab>
</tabs>
</def>
<def title="Controller-Facing Interface">
<tabs group="lsr3-control">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr3-charlie.cfg" include-lines="22-25"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>The ODL-facing link carries BGP-LS control traffic from LSR3 to the controller. It is intentionally outside the MPLS forwarding graph and receives no RSVP capacity, so a data-plane LSP cannot accidentally select the controller-management link.</p>
</tab>
</tabs>
</def>
<def title="OSPF, MPLS-TE, And RSVP">
<tabs group="lsr3-ospf-mpls-rsvp">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr3-charlie.cfg" include-lines="32-75"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>OSPF and MPLS-TE advertise LSR3's lower and extended paths as eligible TE resources. RSVP reserves <code>10.0.23.0/30</code> as the constrained alternative while exposing the LSR1 and LSR4 links with comparatively higher reservable capacity. This makes the intended trade-off explicit to any CSPF calculation.</p>
</tab>
</tabs>
</def>
<def title="BGP-LS And Management Services">
<tabs group="lsr3-bgpls-management">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr3-charlie.cfg" include-lines="77-121"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>BGP-LS publishes LSR3's lower and extended links to topology consumers. NETCONF over SSH supports controller management; the read-only SNMP community and persistent interface indexes support stable exporter-based interface telemetry.</p>
</tab>
</tabs>
</def>
</deflist>

### `sma-xrv-lsr4-delta` {id="config-sma-xrv-lsr4-delta"}

Full configuration source view:

<code-block lang="text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr4-delta.cfg" collapsible="true"/>

<deflist type="full" collapsible="true">
<def title="Hostname, Router ID, And Access Interface">
<tabs group="lsr4-hostname-loopback-access">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr4-delta.cfg" include-lines="1-10"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>The loopback is the stable protocol identity and the source/destination identity of the reverse LSP. The access interface connects LSR4 to <code>sma-ovs-pe2-foxtrot</code> and provides the default gateway for the Hotel host subnet; like LSR1's access link, it is outside the RSVP/MPLS-TE resource set.</p>
</tab>
</tabs>
</def>
<def title="Core And Control Interfaces">
<tabs group="lsr4-core-control">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr4-delta.cfg" include-lines="12-40"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>LSR4 terminates the right side of the MPLS core and has the largest number of TE-capable adjacencies. It exposes the baseline upper and lower paths, alternate LSR2/LSR3 paths, and the constrained direct LSR1 shortcut. <code>Gi0/0/0/3</code> is reserved for ODL control-plane reachability and is not a TE data-plane resource.</p>
</tab>
</tabs>
</def>
<def title="Delegated RSVP-TE Tunnel">
<tabs group="lsr4-tunnel">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr4-delta.cfg" include-lines="42-50"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>This is the reverse delegated LSP controlled by OpenDaylight. It mirrors LSR1's tunnel in the opposite direction and gives the controller a stable object to update when reverse traffic policy changes. <code>path-option 10 dynamic</code> permits an initial locally computed TE route; subsequent controller updates can replace that route while retaining the same named, delegated LSP.</p>
</tab>
</tabs>
</def>
<def title="OSPF, MPLS-TE, PCEP, And RSVP">
<tabs group="lsr4-ospf-mpls-pce-rsvp">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr4-delta.cfg" include-lines="52-116"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>LSR4 advertises the right-side and shortcut TE links, peers with OpenDaylight as a stateful PCC through <code>10.100.40.2</code>, and exposes RSVP bandwidth values that match the topology constraints. As with LSR1, only the listed core interfaces form the TE resource pool; the access and controller-facing links remain excluded.</p>
</tab>
</tabs>
</def>
<def title="BGP-LS, Static Routing, And Management Services">
<tabs group="lsr4-bgpls-management">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr4-delta.cfg" include-lines="118-165"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>BGP-LS makes LSR4's view of the topology visible to OpenDaylight. The static route sends remote left-site traffic through the reverse delegated tunnel. NETCONF/SSH and read-only SNMP provide the same management and telemetry functions as on the other routers, including persistent IF-MIB interface identities.</p>
</tab>
</tabs>
</def>
</deflist>

## Controller Configuration {id="per-device-controller-configuration"}

The controller configuration is injected through the `sma-odl-controller-india` environment variables in the Containerlab topology. 

<deflist type="full" collapsible="true">
<def title="Controller Image Runtime Identity and BGP Privileged-Port Binding">
<tabs group="controller-image-runtime">
<tab title="Image and topology settings" group-key="settings">
<code-block lang="docker" src="../../src/main/java/baseline-odl/Dockerfile" include-lines="240-267"/>
<code-block lang="yaml" src="../../src/main/containerlab/topologies/main/sdn-mpls-ai-tech-demo-baseline.clab.yaml" include-lines="206-220"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>The controller image creates <code>sma-karaf</code> with UID:GID <code>10002:10002</code>, grants the account passwordless <code>sudo</code>, and launches Karaf as that non-root identity. The Containerlab node grants <code>NET_BIND_SERVICE</code> and configures the controller-side interfaces.</p>
<p>These settings are required by the BGP peer-acceptor configuration that binds <code>21.21.21.21:179</code> before the speaker and peer requests run. The image also exposes TCP port <code>179</code>. See [Configure the BGP peer acceptor](ODL-RESTCONF-—-BGP-LS-Requests.md#configure-bgp-peer-acceptor) for the RESTCONF resource and YANG model.</p>
</tab>
</tabs>
</def>
<def title="Container Logging Configuration">
<tabs group="controller-container-logging">
<tab title="Entrypoint source" group-key="source">
<code-block lang="shell" src="../../src/main/java/baseline-odl/sma-odl-entrypoint.sh" include-lines="5-11"/>
<code-block lang="shell" src="../../src/main/java/baseline-odl/sma-odl-entrypoint.sh" include-lines="40-78"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>The <code>SMA_LOG_*</code> values configure the Karaf logging runtime, not the Java <code>AppConfig</code> record. Before Karaf starts, the entrypoint validates the values, creates the rolling log file, and writes the <code>com.sma.sdn</code> logger configuration. This is why those variables remain in the Containerlab node even though <code>AppConfig.fromEnvironment()</code> does not read them.</p>
</tab>
</tabs>
</def>
</deflist>

Full baseline controller environment view:

<p>This is a source inclusion of the deployed Containerlab node rather than a copied variable list. It is the authoritative set of deployment overrides; <code>AppConfig.fromEnvironment()</code> supplies validated defaults for settings not present here.</p>

<code-block lang="yaml" src="../../src/main/containerlab/topologies/main/sdn-mpls-ai-tech-demo-baseline.clab.yaml" include-lines="137-221" collapsible="true"/>
<deflist type="full" collapsible="true">
<def title="Topology, PCEP, And Path Computation Identity">
<tabs group="controller-topology">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="yaml">
ODL_BGPLS_TOPOLOGY_ID: sma-bgp-linkstate-topology
ODL_PCEP_TOPOLOGY_ID: pcep-topology
ODL_PATH_COMPUTATION_GRAPH_NAME: ted://sma-bgp-linkstate-topology
SMA_HEADEND_RID: 11.11.11.11
SMA_HEADEND_PCC_NODE: pcc://10.100.10.1
SMA_TAILEND_RID: 14.14.14.14
SMA_TAILEND_PCC_NODE: pcc://10.100.40.1
</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>These values identify the BGP-LS topology, PCEP topology, and TE graph that the controller-side application reads from OpenDaylight. The service uses them to locate operational nodes and delegated LSPs before it calculates or applies an LSP update; they do not create those controller resources.</p>
<p>The router IDs and PCC node URIs select the two managed tunnel endpoints. They must match the loopbacks and PCEP control links declared by LSR1 and LSR4, because those identities are later used to locate the direction-specific LSP records.</p>
</tab>
</tabs>
</def>
<def title="Delegated LSP Direction Mapping">
<tabs group="controller-lsp-map">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="yaml">
SMA_FORWARD_LSP_NAME: sma-lsr1-lsr4-delegated
SMA_REVERSE_LSP_NAME: sma-lsr4-lsr1-delegated
SMA_FORWARD_TUNNEL_INTERFACE: tunnel-te110
SMA_REVERSE_TUNNEL_INTERFACE: tunnel-te410
SMA_FORWARD_DIRECTION_KEY: lsr1_to_lsr4
SMA_REVERSE_DIRECTION_KEY: lsr4_to_lsr1
</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>The application uses these names to correlate logical traffic directions, delegated LSP records, and tunnel interfaces. They must remain consistent with the LSR tunnel configuration and policy state. Ingress connector discovery is configured independently through the OVS management address and port-name variables below; the removed ingress-mapping variables are no longer part of <code>AppConfig</code>.</p>
</tab>
</tabs>
</def>
<def title="RESTCONF, Operations, Classifier, And Timeouts">
<tabs group="controller-http">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="yaml">
ODL_RESTCONF_DATA_BASE_URL: http://127.0.0.1:8182/restconf/data
ODL_RESTS_OPERATIONS_BASE_URL: http://127.0.0.1:8181/rests/operations
ODL_USERNAME: admin
ODL_PASSWORD: admin
CLASSIFIER_BASE_URL: http://172.21.121.200:33761
CLASSIFIER_CLASSIFY_PATH: /api/v1/classify
HTTP_REQUEST_TIMEOUT_SECONDS: 10
ODL_OPERATIONAL_VALIDATION_TIMEOUT_SECONDS: 120
</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>RESTCONF data reads use port <code>8182</code>, operation RPCs use port <code>8181</code>, and the classifier call targets the Python API container. The validation timeout bounds how long the application waits for operational state to reflect LSP updates.</p>
</tab>
</tabs>
</def>
<def title="Cache, Retry, And OpenFlow Bootstrap">
<tabs group="controller-runtime">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="yaml">
CLASSIFICATION_CACHE_TTL_SECONDS: 3600
PATH_CACHE_TTL_SECONDS: 60
TOPOLOGY_CACHE_TTL_SECONDS: 300
ODL_RETRY_INITIAL_DELAY_MS: 500
ODL_RETRY_MAX_DELAY_MS: 5000
ODL_RETRY_JITTER_PERCENT: 20
ODL_TOPOLOGY_DISCOVERY_MAX_ATTEMPTS: 5
SMA_OPENFLOW_BOOTSTRAP_ENABLED: true
SMA_OPENFLOW_TABLE_ID: 0
SMA_OVS_ECHO_MGMT_IP: 172.21.121.15
SMA_OVS_FOXTROT_MGMT_IP: 172.21.121.16
</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>TTL settings determine when cached classification, path, and topology state must be refreshed. Retry settings bound ODL startup and discovery attempts. The OpenFlow bootstrap settings let the Java application discover runtime datapath IDs and named edge connectors before it programs deterministic ARP and IPv4 access flows toward the MPLS core.</p>
<p><code>SMA_LOG_LEVEL</code> is intentionally absent from this application-configuration group: the controller image entrypoint consumes the <code>SMA_LOG_*</code> variables to configure Karaf logging before the controller-side application calls <code>AppConfig.fromEnvironment()</code>.</p>
</tab>
</tabs>
</def>
</deflist>

### Controller Configuration — RESTCONF and RPC Operations Used for Controller Preconfiguration {id="controller-restconf-preconfiguration"}

<p>The controller must first receive its BGP-LS configuration, associate that RIB with a topology resource, and register the four NETCONF-managed routers. The controller-side application then reads the BGP-LS, graph, PCEP, and OpenFlow operational state that those subsystems publish. A successful configuration request records intended controller state. It does not by itself establish a BGP session, NETCONF session, PCEP session, or OpenFlow connection.</p>

<deflist type="full" collapsible="true">
<def title="1. Configure the BGP Peer Acceptor">
<tabs group="controller-restconf-peer-acceptor">
<tab title="Request" group-key="request">
<code-block lang="http" src="../../src/main/postman/bgp-ls/configure-bgp-peer-acceptor-for-cisco.http"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>Configure the default BGP peer acceptor before creating the BGP-LS speaker. The resource binds the controller's loopback address <code>21.21.21.21</code> to TCP port <code>179</code>, which allows Cisco IOS XR peers to establish inbound BGP sessions against the configured address.</p>
<p>Port <code>179</code> is a privileged port. The baseline OpenDaylight image grants the Karaf runtime account passwordless <code>sudo</code>, and the Containerlab controller node receives <code>NET_BIND_SERVICE</code>, so the runtime can make this binding. See <a href="ODL-RESTCONF-—-BGP-LS-Requests.md#configure-bgp-peer-acceptor"></a>.</p>
</tab>
</tabs>
</def>
<def title="2. Create the BGP-LS Speaker">
<tabs group="controller-restconf-speaker">
<tab title="Request" group-key="request">
<code-block lang="http" src="../../src/main/postman/bgp-ls/create-odl-bgp-ls-speaker.http"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>Create protocol <code>sma-bgp-ls</code> under <code>network-instance=global-bgp</code>. The request sets the controller router ID to <code>21.21.21.21</code>, local AS to <code>65000</code>, and the <code>LINKSTATE</code> AFI/SAFI. This establishes the BGPCEP RIB identity that later peer and topology requests reference.</p>
<p>A new protocol entry is expected to return <code>201 Created</code>. The saved request contains no observed response. See <a href="ODL-RESTCONF-—-BGP-LS-Requests.md#create-odl-bgpls-speaker"></a> for the resource path, YANG keys, and payload mapping.</p>
</tab>
</tabs>
</def>
<def title="3. Register the Four BGP-LS Peers">
<tabs group="controller-restconf-peers">
<tab title="Requests" group-key="request">
<code-block lang="http" src="../../src/main/postman/bgp-ls/add-lsr1-as-a-bgpls-peer.http"/>
<code-block lang="http" src="../../src/main/postman/bgp-ls/add-lsr2-as-a-bgpls-peer.http"/>
<code-block lang="http" src="../../src/main/postman/bgp-ls/add-lsr3-as-a-bgpls-peer.http"/>
<code-block lang="http" src="../../src/main/postman/bgp-ls/add-lsr4-as-a-bgpls-peer.http"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>Each POST creates one <code>neighbor</code> list entry under the speaker's BGP augmentation. The peer key is its loopback address: LSR1 <code>11.11.11.11</code>, LSR2 <code>12.12.12.12</code>, LSR3 <code>13.13.13.13</code>, and LSR4 <code>14.14.14.14</code>. Every request sets <code>local-address=21.21.21.21</code>, <code>remote-port=179</code>, <code>passive-mode=true</code>, and the <code>LINKSTATE</code> AFI/SAFI.</p>
<p>These writes make the peers available to BGPCEP. TCP and BGP session establishment, capability negotiation, and BGP-LS UPDATE reception occur later. The detailed <a href="ODL-RESTCONF-—-BGP-LS-Requests.md#add-lsr1-bgpls-peer">BGP-LS request catalog</a> documents the shared resource structure and each individual request.</p>
</tab>
</tabs>
</def>
<def title="4. Create the BGP-LS Topology">
<tabs group="controller-restconf-bgpls-topology">
<tab title="Request" group-key="request">
<code-block lang="http" src="../../src/main/postman/bgp-ls/create-bgp-ls-link-state-topology.http"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>This request creates topology <code>sma-bgp-linkstate-topology</code>. Its BGP-LS topology marker and <code>rib-id=sma-bgp-ls</code> associate the topology provider with the speaker RIB. When BGP-LS routes are available, the provider maps their topology and traffic-engineering information into this resource.</p>
<p>The operation configures the association. It does not prove that routes have already been received or that the topology contains nodes and links. See <a href="ODL-RESTCONF-—-BGP-LS-Requests.md#create-bgpls-linkstate-topology">Create the BGP-LS Link-State topology</a>.</p>
</tab>
</tabs>
</def>
<def title="5. Register the Four NETCONF Devices">
<tabs group="controller-restconf-netconf-mounts">
<tab title="Requests" group-key="request">
<code-block lang="http" src="../../src/main/postman/netconf/mount-lsr1-through-netconf.http"/>
<code-block lang="http" src="../../src/main/postman/netconf/mount-lsr2-through-netconf.http"/>
<code-block lang="http" src="../../src/main/postman/netconf/mount-lsr3-through-netconf.http"/>
<code-block lang="http" src="../../src/main/postman/netconf/mount-lsr4-through-netconf.http"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>Each PUT creates one <code>network-topology:topology[topology-id='topology-netconf']/node[node-id]</code> entry with a <code>netconf-node</code> connection configuration. The entries target the LSR loopback addresses <code>11.11.11.11</code>, <code>12.12.12.12</code>, <code>13.13.13.13</code>, and <code>14.14.14.14</code> on NETCONF port <code>830</code>.</p>
<p>OpenDaylight attempts the SSH NETCONF connection after the datastore write. Mount availability, device capabilities, and mounted-device data require a successful authenticated session. See <a href="ODL-RESTCONF-—-NETCONF-Requests.md#netconf-required-initialization">Required RESTCONF Calls for Controller Initialization</a>.</p>
</tab>
</tabs>
</def>
<def title="6. Validate BGP-LS and Graph Availability">
<tabs group="controller-restconf-bgpls-reads">
<tab title="Requests" group-key="request">
<code-block lang="http" src="../../src/main/postman/bgp-ls/list-topologies.http"/>
<code-block lang="http" src="../../src/main/postman/bgp-ls/get-topology-graph-information.http"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>The controller-side application uses the topology list to discover published topology resources. The graph read returns the named traffic-engineering graph <code>ted://sma-bgp-linkstate-topology</code>, including its keyed vertices, directed edges, prefixes, and per-class-type unreserved bandwidth.</p>
<p>Both requests are read-only. Their successful response demonstrates controller-published state, while an empty collection or graph does not establish that the BGP-LS producer routers have converged. See <a href="ODL-RESTCONF-—-BGP-LS-Requests.md#list-topologies">List topologies</a> and <a href="ODL-RESTCONF-—-BGP-LS-Requests.md#get-topology-graph-information">Get topology graph information</a>.</p>
</tab>
</tabs>
</def>
<def title="7. Read PCEP State Before Controller-Side Operations">
<tabs group="controller-restconf-pcep-reads">
<tab title="Requests" group-key="request">
<code-block lang="http" src="../../src/main/postman/pce/query-pcep-topology.http"/>
<code-block lang="http" src="../../src/main/postman/pce/query-pcc-node-ids.http"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>The controller-side application first reads <code>pcep-topology</code> and its PCC node identities. These reads identify the PCEP topology and delegated-LSP state that later path computation and LSP-update operations use. They do not configure a tunnel.</p>
<p>The detailed <a href="ODL-RESTCONF-—-PCEP-Requests.md#pcep-query-topology">PCEP request catalog</a> records the topology and PCC resource hierarchy. The CSA uses a <code>content=all</code> topology read when it needs both intended and operational state.</p>
</tab>
</tabs>
</def>
<def title="8. Discover and Bootstrap OpenFlow Access Switches">
<tabs group="controller-restconf-openflow-bootstrap">
<tab title="Request pattern" group-key="request">
<code-block lang="http" src="../../src/main/postman/openflow/read-openflow-node-inventory.http"/>
<code-block lang="http"><![CDATA[PUT ${ODL_RESTCONF_DATA_BASE_URL}/opendaylight-inventory:nodes/node=${encodedNodeId}/flow-node-inventory:table=${tableId}/flow=${encodedFlowId}]]></code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p><code>OpenflowBootstrapService</code> discovers ECHO and FOXTROT from operational inventory and resolves their current node and connector IDs. It then writes four flows for each switch: ARP host-to-core, ARP core-to-host, IPv4 host-to-core with a controller copy, and IPv4 core-to-host.</p>
<p>The service verifies configured IDs and polls the operational table before it makes the control plane ready. The PUT is generated by the application, rather than stored as a static request, because runtime OpenFlow node IDs are dynamic. See <a href="ODL-RESTCONF-—-OpenFlow-Requests.md#openflow-controller-flow-injection">Controller-Side OpenFlow Flow Injection</a>.</p>
</tab>
</tabs>
</def>
<def title="9. Compute and Apply a Delegated LSP When Requested">
<tabs group="controller-restconf-pcep-operations">
<tab title="Requests" group-key="request">
<code-block lang="http" src="../../src/main/postman/pce/request-pcep-computed-path-from-lsr1-to-lsr4-test.http"/>
<code-block lang="http" src="../../src/main/postman/pce/configurte-lsp-tunnel-after-the-fact.http"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>After the graph and PCEP topology have been read, the application invokes <code>path-computation:get-constrained-path</code> and then <code>network-topology-pcep:update-lsp</code>. The first RPC computes a path against the named graph. The second sends an update for the selected delegated LSP to its PCC.</p>
<p>These are controller-side operations, not baseline bootstrap writes. An HTTP success response to the update does not establish tunnel convergence. The PCEP operational state and RPC result provide the acknowledgment evidence. See [Compute a Constrained Path from LSR1 to LSR4](ODL-RESTCONF-—-PCEP-Requests.md#pcep-compute-lsr1-lsr4-path) and [Update the Delegated LSP after Path Computation](ODL-RESTCONF-—-PCEP-Requests.md#pcep-update-delegated-lsp).</p>
</tab>
</tabs>
</def>
</deflist>

<!-- Historical draft retained in source only while the completed RESTCONF sequence above supersedes it.
<deflist type="full">
<def title="1. Configuring the BGP-LS Listener upon the Global BGP Network Instance">

```HTTP    
    
    POST http://172.21.121.100:8182/restconf/data/openconfig-network-instance:network-instances/network-instance=global-bgp/protocols?
    Content-Type: application/xml 
    Accept: application/xml
    Authorization: admin/admin 
    Body:

    <protocol xmlns="http://openconfig.net/yang/network-instance">
        <name>sma-bgp-ls</name>
    
        <identifier xmlns:oc-pol-types="http://openconfig.net/yang/policy-types">oc-pol-types:BGP</identifier>
    
        <bgp xmlns="urn:opendaylight:params:xml:ns:yang:bgp:openconfig-extensions">
            <global>
                <config>
                    <router-id>172.21.121.100</router-id>
                    <as>65000</as>
                </config>
    
                <afi-safis>
                    <afi-safi>
                        <afi-safi-name>LINKSTATE</afi-safi-name>
                    </afi-safi>
                </afi-safis>
            </global>
        </bgp>
    </protocol>
```

<deflist collapsible="true">
<def title="What is this request doing?" collapsible="true">
<p>The previous request performs the configuration of the global BGP-LS routing protocol of the controller. Given that BGP allows for multiple configurations and multiple types of information to be sent, we need to explicitly tell the controller that the information is is receiving over the BGP-LS peers is not routing information, but rather part of the extension data for the topology and traffic engineering information.</p>
<p>To do this we configure the <code>AFI-SAFI</code> parameter held internally the controller which translate to <code>Address Family Identifier - Subsequent Address Family Identifier</code> which is what tells the multiprotocol BGP routing protocol that lives in the controller that the information being exchanged is <code>LINKSTATE</code> which is a shorthand used by Opendaylight to represent <code>16388 and 71</code> which are the pair of AFI-SAFI values that are standarized to be the definitive representations of the BGP-LS information in an implementation of multiprotocol BGP.</p>
<p>In this case, the system receives both the router id it will use, which in this case corresponds to the router's actual management IP, and the autonomous system number which in this is the same as that of <b>all the routers in the topology</b> meaning that the session built here corresponds to an <b>iBGP session</b> between the <b>BGP-Speakers (core routers) and BGP-Consumer (the controller)</b>. Through this information we can configure the <b>BGPPCEP subsystem</b> in the controller such that it interprets the BGP information coming from its peers as BGP-LS topology and traffic engineering information.</p>
</def>
<def title="What is the expected reply?">
<p>The expected reply for this operation is a <code>201 Created</code> with no additional body or returned information.</p>
</def>
</deflist>
</def>
<def title="2. Adding LSR1 as a BGP Peer to tbe BGP Subsystem in Opendaylight">

```HTTP
POST http://172.21.121.100:8182/restconf/data/openconfig-network-instance:network-instances/network-instance=global-bgp/openconfig-network-instance:protocols/protocol=openconfig-policy-types:BGP,sma-bgp-ls/bgp-openconfig-extensions:bgp/neighbors
Content-Type: application/xml
Accept: application/xml
Authorization: admin/admin
Body:
<neighbor xmlns="urn:opendaylight:params:xml:ns:yang:bgp:openconfig-extensions">
    <neighbor-address>10.100.10.1</neighbor-address>
    <afi-safis>
        <afi-safi>
            <afi-safi-name>LINKSTATE</afi-safi-name>
        </afi-safi>
    </afi-safis>
</neighbor>
```
<deflist collapsible="true">
<def title="What does this URL mean?">
<note>
<p>As with everything in this controller almost, most of the documentation is fragmented between multiple systems:</p>
<ul>
<li><b><format color="CornflowerBlue">OpenConfig YANG models</format></b>: underneath the <code>BGP extensions</code> and <code>RESTCONF API </code>layer of the Opendaylight controller sits a different beast which is the <code>OpenConfig</code> project which is a way to make standardize interfaces and tools for managing <b>networks in a vendor-neutral way</b> through the use of YANG models. <code>YANG</code> defined as <b>Yet Another Generation</b> is a language developed to standardize networking modeling for the structure and constraints for configuration and operational data on network devices. These models are useful for vendor neutral implementations, and even on some vendor devices as YANG models can be used to manipulate through API calls the configuration of devices.
<p>These models however, are documented in remote corners of the internet, like the 7 years old documentation for Cisco YANG models for their IOS configuration, or the obscure YANG models that Opendaylight uses for their configuration of protocols like BGP.</p>
</li>
<li><b><format color="CornflowerBlue">ODL BGPCEP/OpenConfig extension YANG models</format></b>
<p>
    This models add more information and configuration over the default OpenConfig models that are reported in an external repository. Moreover, these models are distributed among various files and not documented alongside the main API. 
</p>
</li>
<li><b><format color="CornflowerBlue">RESTCONF/OpenAPI explorer</format></b> 
<p>
In this case, this model of the data represents a <code>version specific and incomplete version of the YANG models tolerated by an endpoint</code>. In the case of this specific API endpoint, the model represents a simplified tolerated XML or JSON input which does not even describe the AFI-SAFI block that the request uses which showcases how fragmented the data models represented in the application are.
</p>
</li>
<li><b><format color="CornflowerBlue">BGPCEP user documentation</format></b>
<p>This is the only source of truth that showcases the configuration mechanism that we explained above, but not with the level of detail to allow for the undertanding of what is being done or what the URL means. The documentation reports a <b>nearly identical URL</b> that allows the system to perform the similar adding of a BGP Peer into the BGP  global configuration. However, it does not explain what the endpoint is doing or that the objects that it is going through come from three different sources at the same time</p>
</li>
</ul>
<p>Ultimately the one note to take from this is that most of the configuration represented here, and the information used to build the endpoints for the controller side application alongside this system correspond not to the baseline documentation snippets, but to tried and tested snippets that execute the configuration but were at least reviewed for their correctness and the models they followed.</p>
</note>
<p>At a high level, the url provided for this and all other configuration snippets for BGP Peers, considered neighbors in this configuration, is traversing a series of <code>extended OpenConfig YANG models through Opendaylights BGPCEP extensions</code> to configure the object at hand. These systems extend the OpenConfig definition, which is already one of the most complete BGP configuration models with quality of life extensions such as the introduction of the shorthand <code>LINKSTATE</code> for representing the Link State NLRI AFI SAFI configuration. These requests then are trying to traverse this tree:</p>

 ```plain text
 network-instances
└── network-instance[name="global-bgp"]
    └── protocols
        └── protocol[
              identifier="openconfig-policy-types:BGP",
              name="sma-bgp-ls"
           ]
            └── bgp
                └── neighbors
 ```
<p>
    In order to do so, it has to first define the kind of operation it wants to do, an operation that corresponds to a call to <code>/restconf/data</code> which is a data source used in the Opendaylight controller to allow for both the modification and retrieval of data from the internal state of the controller. The RESTCONF specification determines that the parsing after the last <code>/</code> is to be done from left to right so it is important to follow it as such.
</p>
<p>
    First the system appears to be entering the <code>openconfig-network-instance:network-instances</code>  grouping within the YANG model of the <code>openconfig-network-instance</code> module. In this case, the OpenConfig module describes this as a forwarding construct, in which we explicitly look for the network instance of <code>global-bgp</code> under the name construct of the <code>network-instances</code> that might be registered under the RESTCONF data store.
</p>
<p>
    Underneath this we are targetting the <code>protocols leaf</code> which specifically describes the protocols that are configured within that network instance. In this case, the protocols that are configured under the <code>Global BGP</code> network instance within the Opendaylight controller. Within the broad protocols leaf we can access individual <code>protocol</code> definitions which are accessed through a list of <code>key identifier names</code>. In this case, these identifier names are parsed, as defined by RFC 8040 in the schema order, which means that now we are going to delve into the <code>protocol=openconfig-policy-types:BGP,sma-bgp-ls</code> BGP policy types and specifically <b>our BGP protocol instance</b> that we configured as <code>LINKSTATE</code> before.
</p>
<p>
    It is important to note that here there is a variation from the original behavior of the OpenConfig tree YANG model since we are no longer targeting anything that comes from them, rather wemove to target the leaf of the tree which represents the <code>bgp-openconfig-extensions:bgp</code> or the extensions added ontop of the OpenConfig implementation by the opendaylight team using their own YANG modules. In this case, the Opendaylight team is effectively using the baseline OpenConfig BGP module and expanding it with their own <code>openconfig-bgp.yang</code> module which uses both the original definitions, hence why we were able to access the entire tree based on the OpenConfig model and then extends it with the global bgp configuration which we used earlier, and the neighbor configuration which we are using now. At this layer we can introduce the AFI-SAFIS XML elements to configure the neighbor to be appropriately associated as a LINKSTATE neighbor
</p>

```Plain Text
/restconf/data
│
│ RFC 8040
│ RESTCONF YANG datastore root
│
└─ openconfig-network-instance:network-instances
   │
   │ openconfig-network-instance.yang
   │ collection of forwarding/network instances
   │
   └─ network-instance=global-bgp
      │
      │ same YANG model
      │ selects name="global-bgp"
      │
      └─ protocols
         │
         │ same YANG model
         │ routing protocols enabled in this instance
         │
         └─ protocol=
             openconfig-policy-types:BGP,
             sma-bgp-ls
             │
             ├─ identifier =
             │    openconfig-policy-types:BGP
             │
             │    openconfig-policy-types.yang
             │    BGP identity
             │    reference: RFC 4271
             │
             └─ name =
                  sma-bgp-ls
                  operator-assigned instance name

                  ↓

        bgp-openconfig-extensions:bgp
                  │
                  │ bgp-openconfig-extensions.yang
                  │ ODL augmentation of the generic
                  │ OpenConfig protocol entry
                  │
                  │ uses openconfig-bgp:bgp-top
                  ↓
                bgp
                  │
                  │ openconfig-bgp.yang
                  │ BGP global / peer / neighbor model
                  ↓
              neighbors
                  │
                  └─ individual BGP peers
                       │
                       └─ afi-safis
                            │
                            │ openconfig-bgp-
                            │ multiprotocol.yang
                            ↓
                       afi-safi-name
                            │
                            └─ LINKSTATE
                                  │
                                  │ ODL identity in
                                  │ bgp-openconfig-
                                  │ extensions.yang
                                  ↓
                           AFI 16388 / SAFI 71
                           BGP-LS
                           RFC 7752 in model
                           RFC 9552 currently
```
</def>
<def title="What is this request doing?">
<p>
    The previous request is configuring a <b>BGP Peering session between LSR1 and the Controller</b>, effectively being used to initialize a <b>BGP session</b> between the two through TCP port 179 which is the default port exposed for BGP sessions in Opendaylight. The system, effectively before it can even accept BGP-LS information, requires setting up this session to negotiate <b>Address Family information</b>.
</p>
<p>
    In the context of the RFC 9552 which describes the roles for the distribution of link state information using BGP, the role of LSR1 in this case is of a <b>BGP Speaker and a BGP-LS Producer</b>, given that first it must set up a BGP session before it can begin originating <code>BGP-LS UPDATE</code> messages taken from the Link State Databases and Traffic Information Databases produced by IGP protocols such as the OSPF-TE we configured earlier on each router.
</p>
<p>
    Through this specific command, we are informing the BGP subsystem of the controller then to setup a BGP session, to use <code>BGP Capabilities Advertisement</code> to ensure that both speakers can understand effectively the content of the corresponding <code>Link State NLRI's (Network Layer Reachibility Information) encoded TLVs that will be sent</code>, which means the two systems should be able to, a) from the router send <code>Link State NLRIs </code>, and b) that the controller is capable of reading these.
</p>
</def>
<def title="What is the expected reply?">
<p>
    An execution of this command should not return any content, but should return a <code>201 created</code>. 
</p>
</def>
</deflist>
</def>
<def title="3. Adding LSR2 as a BGP Peer to the BGP Subsystem in Opendaylight">

```HTTP 
POST http://172.21.121.100:8182/restconf/data/openconfig-network-instance:network-instances/network-instance=global-bgp/openconfig-network-instance:protocols/protocol=openconfig-policy-types:BGP,sma-bgp-ls/bgp-openconfig-extensions:bgp/neighbors
Content-Type: application/xml
Accept: application/xml
Authorization: admin/admin
Body:
<neighbor xmlns="urn:opendaylight:params:xml:ns:yang:bgp:openconfig-extensions">
    <neighbor-address>10.100.10.1</neighbor-address>
    <afi-safis>
        <afi-safi>
            <afi-safi-name>LINKSTATE</afi-safi-name>
        </afi-safi>
    </afi-safis>
</neighbor>
```
</def>
<def title="4. Adding LSR3 as a BGP Peer to the BGP Subsystem in Opendaylight">

```HTTP 
POST http://172.21.121.100:8182/restconf/data/openconfig-network-instance:network-instances/network-instance=global-bgp/openconfig-network-instance:protocols/protocol=openconfig-policy-types:BGP,sma-bgp-ls/bgp-openconfig-extensions:bgp/neighbors
Content-Type: application/xml
Accept: application/xml
Authorization: admin/admin
Body:
<neighbor xmlns="urn:opendaylight:params:xml:ns:yang:bgp:openconfig-extensions">
    <neighbor-address>10.100.30.1</neighbor-address>
    <afi-safis>
        <afi-safi>
            <afi-safi-name>LINKSTATE</afi-safi-name>
        </afi-safi>
    </afi-safis>
</neighbor>
```
</def>
<def title="5. Adding LSR4 as a BGP Peer to the BGP Subsystem in Opendaylight">

```HTTP 
POST http://172.21.121.100:8182/restconf/data/openconfig-network-instance:network-instances/network-instance=global-bgp/openconfig-network-instance:protocols/protocol=openconfig-policy-types:BGP,sma-bgp-ls/bgp-openconfig-extensions:bgp/neighbors
Content-Type: application/xml
Accept: application/xml
Authorization: admin/admin
Body:
<neighbor xmlns="urn:opendaylight:params:xml:ns:yang:bgp:openconfig-extensions">
    <neighbor-address>10.100.40.1</neighbor-address>
    <afi-safis>
        <afi-safi>
            <afi-safi-name>LINKSTATE</afi-safi-name>
        </afi-safi>
    </afi-safis>
</neighbor>
```
</def>
<def title="6. Create the BGP-LS Link State Topology based on the Router Information Base From the BGP Peers">

```HTTP
POST http://172.21.121.100:8182/restconf/data/network-topology:network-topology/topology=sma-bgp-linkstate-topology
Content-Type: application/xml
Accept: application/xml
Authorization: admin/admin
Body:
<topology xmlns="urn:TBD:params:xml:ns:yang:network-topology">
    <topology-id>sma-bgp-linkstate-topology</topology-id>
    <topology-types>
        <bgp-linkstate-topology
            xmlns="urn:opendaylight:params:xml:ns:yang:odl-bgp-topology-types"/>
    </topology-types>
    <rib-id xmlns="urn:opendaylight:params:xml:ns:yang:odl-bgp-topology-config">sma-bgp-ls</rib-id>
</topology>
```

<deflist collapsible="true">
<def title="What is this request doing?">
<p>
    In this case, the provided requests interacts with the RESTCONF implementation within the Opendaylight controller to define the creation of a <code>topology based on the RIB</code> produced by the <code>BGP UPDATE messages</code> being exchanged by the BGP speaker we configured earlier and the BGP producer speaker at the router side of the network. In this case, the configuration mechanism we are implementing here uses PUT instead of POST as the verb semantics allows us to modify data, which is exactly what we are doing. Before running this command, not a single topology was created based on the RIB produced by BGP, and as such no topology was registered within the system.
</p>
<p>
    The main math this object takes happens around the <code>network-topology:network-topology</code> section of the URL which descibres that we are accessing the <code>YANG module network-topology</code> and that within it we are going to head into the <code>network-topology container</code> that can hold internally a list of <code>configured topologies depending on the source for said topology</code>. In our case the topology we are going to be creating has a <b>topology-id</b> of <code>sma-bgp-linkstate-topology</code>.
</p>
<p>
    The object we declare here has been documented within the <b>running Opendaylight documentation in the controller</b>, however the information that it takes for the underlying content is not documented well enough. The information that is held within the <code>topology-types container</code> corresponds to the augmented version of the baseline <code>topology-types container</code> described in the initial network topologies <a href="https://github.com/opendaylight/mdsal/blob/master/model/ietf-topology/src/main/yang/network-topology%402013-10-21.yang">[network-topology@2013-10-21.yang]</a> through the use of an augmnetation through a cotainer called <code>bgp-linkstate-topology</code> which augments <code>topology-types</code> <a href="https://github.com/opendaylight/bgpcep/blob/0.21.x/bgp/topology-provider/src/main/yang/odl-bgp-topology-types.yang">[odl-bgp-topology-types.yang]</a>. 
</p>
<p>
    Over this augmentation, another leaf element is defined as the <b>rib-id</b> which can only be present when there is a <code>bgp-linkstate-topology</code> container present within the definition. In this case this has to point to the <code>sma-bgp-ls</code> RIB that we created when we configured all routers to talk through BGP to the controller.
</p>
</def>
<def title="What is the expected result?">
<p>Upon execution of this command the expected output is a <code>201 Created</code> with no response body.</p>
</def>
</deflist>
</def>
<def title="7. Mount LSR1 through NETCONF into the Controller for Management">

```HTTP 
PUT http://172.21.121.100:8182/restconf/data/network-topology:network-topology/topology=topology-netconf/node=sma-xrv-lsr1-alpha
Content-Type: application/xml
Accept: application/xml
Authorization: admin/admin
Body:
<node xmlns="urn:TBD:params:xml:ns:yang:network-topology">
    <node-id>sma-xrv-lsr1-alpha</node-id>
    <netconf-node xmlns="urn:opendaylight:netconf-node-topology">
        <host>172.21.121.11</host>
        <port>830</port>
        <login-password-unencrypted>
            <username>{{xrv_netconf_user}}</username>
            <password>{{xrv_netconf_pwd}}</password>
        </login-password-unencrypted>
        <tcp-only>false</tcp-only>
        <reconnect-on-changed-schema>false</reconnect-on-changed-schema>
        <connection-timeout-millis>20000</connection-timeout-millis>
        <max-connection-attempts>0</max-connection-attempts>
        <min-backoff-millis>2000</min-backoff-millis>
        <max-backoff-millis>1800000</max-backoff-millis>
        <backoff-multiplier>1.5</backoff-multiplier>
        <keepalive-delay>30</keepalive-delay>
    </netconf-node>
</node>
```
</def>
</deflist>

-->

## OpenFlow Switches {id="per-device-openflow-switches"}

The OVS containers still create bridges, ports, controller connections, and OVSDB manager sessions at Containerlab startup. The durable forwarding behavior, however, is owned by the Java controller-side OpenFlow bootstrap subsystem. This avoids relying on static datapath IDs or fragile `ovs-ofctl` commands after OpenDaylight starts.

<deflist type="full" collapsible="true">
<def title="Container-Level OVS Bridge Initialization">
<tabs group="openflow-container-init">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="bash"><![CDATA[ovs-vsctl --may-exist add-br sma-ovs-pe1-echo
ovs-vsctl set bridge sma-ovs-pe1-echo datapath_type=netdev
ovs-vsctl --may-exist add-port sma-ovs-pe1-echo host-golf
ovs-vsctl --may-exist add-port sma-ovs-pe1-echo core-lsr1
ovs-vsctl set-manager tcp:172.21.121.100:6640
ovs-vsctl set-controller sma-ovs-pe1-echo tcp:172.21.121.100:6653
ovs-vsctl set bridge sma-ovs-pe1-echo protocols=OpenFlow13

ovs-vsctl --may-exist add-br sma-ovs-pe2-foxtrot
ovs-vsctl set bridge sma-ovs-pe2-foxtrot datapath_type=netdev
ovs-vsctl --may-exist add-port sma-ovs-pe2-foxtrot host-hotel
ovs-vsctl --may-exist add-port sma-ovs-pe2-foxtrot core-lsr4
ovs-vsctl set-manager tcp:172.21.121.100:6640
ovs-vsctl set-controller sma-ovs-pe2-foxtrot tcp:172.21.121.100:6653
ovs-vsctl set bridge sma-ovs-pe2-foxtrot protocols=OpenFlow13]]></code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>Containerlab creates the OVS bridges and binds them to ODL through OVSDB and OpenFlow. The bridges use OpenFlow 1.3 and expose stable port names, but their runtime OpenFlow datapath IDs are not hardcoded by the application.</p>
</tab>
</tabs>
</def>
<def title="OpenFlow Inventory Discovery">
<tabs group="openflow-inventory">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="http"><![CDATA[GET ${ODL_RESTCONF_DATA_BASE_URL}/opendaylight-inventory:nodes?content=nonconfig]]></code-block>
<code-block lang="xml"><![CDATA[<nodes>
  <node>
    <id>openflow:...</id>
    <ip-address>172.21.121.15</ip-address>
    <node-connector>
      <id>openflow:...:1</id>
      <port-number>1</port-number>
      <name>host-golf</name>
    </node-connector>
  </node>
</nodes>]]></code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>The bootstrap service reads the OpenDaylight inventory, selects OpenFlow nodes, matches them by management IP, and resolves named connectors such as <code>host-golf</code>, <code>core-lsr1</code>, <code>host-hotel</code>, and <code>core-lsr4</code>. This is why the flow programming logic does not depend on fixed datapath IDs.</p>
</tab>
</tabs>
</def>
<def title="Flow Programming Endpoint">
<tabs group="openflow-put">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="http"><![CDATA[PUT ${ODL_RESTCONF_DATA_BASE_URL}/opendaylight-inventory:nodes/node=${encodedNodeId}/flow-node-inventory:table=0/flow=${flowId}

PUT http://127.0.0.1:8182/restconf/data/opendaylight-inventory:nodes/node=openflow%3A134951518551619/flow-node-inventory:table=0/flow=sma-bootstrap-foxtrot-arp-host-to-core]]></code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>The controller writes flows into the OpenFlow configuration datastore using RESTCONF <code>PUT</code>. The node ID is URI encoded in the path, while connector IDs inside XML remain unencoded.</p>
</tab>
</tabs>
</def>
<def title="Required Bootstrap Flows">
<tabs group="openflow-flows">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text"><![CDATA[sma-bootstrap-echo-arp-host-to-core
sma-bootstrap-echo-arp-core-to-host
sma-bootstrap-echo-ipv4-host-to-core
sma-bootstrap-echo-ipv4-core-to-host
sma-bootstrap-foxtrot-arp-host-to-core
sma-bootstrap-foxtrot-arp-core-to-host
sma-bootstrap-foxtrot-ipv4-host-to-core
sma-bootstrap-foxtrot-ipv4-core-to-host]]></code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>Each switch receives four deterministic flows: ARP host-to-core, ARP core-to-host, IPv4 host-to-controller-and-core, and IPv4 core-to-host. Only host-originated IPv4 packets are copied to the controller, which avoids reclassifying returning traffic.</p>
</tab>
</tabs>
</def>
<def title="Representative IPv4 Host-To-Core Flow">
<tabs group="openflow-ipv4-flow">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="xml"><![CDATA[<flow xmlns="urn:opendaylight:flow:inventory">
    <id>${flowId}</id>
    <table_id>0</table_id>
    <priority>200</priority>
    <match>
        <in-port>${hostConnectorId}</in-port>
        <ethernet-match>
            <ethernet-type>
                <type>2048</type>
            </ethernet-type>
        </ethernet-match>
    </match>
    <instructions>
        <instruction>
            <order>0</order>
            <apply-actions>
                <action>
                    <order>0</order>
                    <output-action>
                        <output-node-connector>CONTROLLER</output-node-connector>
                        <max-length>65535</max-length>
                    </output-action>
                </action>
                <action>
                    <order>1</order>
                    <output-action>
                        <output-node-connector>${corePortNumber}</output-node-connector>
                        <max-length>0</max-length>
                    </output-action>
                </action>
            </apply-actions>
        </instruction>
    </instructions>
</flow>]]></code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>The host-to-core IPv4 flow is the classification trigger. It forwards traffic normally while sending a full PacketIn copy to OpenDaylight. The flow uses the full connector ID for matching and the numeric port number for output to mirror the validated <code>ovs-ofctl output:&lt;port&gt;</code> behavior.</p>
</tab>
</tabs>
</def>
<def title="Readiness And Verification">
<tabs group="openflow-readiness">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text"><![CDATA[1. RESTCONF data endpoint becomes available.
2. BGP-LS topology discovery succeeds.
3. PCEP topology / delegated LSP discovery succeeds.
4. OpenFlow inventory discovery succeeds.
5. Echo and Foxtrot are resolved by management IP.
6. Required node-connectors are resolved by name.
7. OpenFlow access bootstrap flows are installed.
8. Bootstrap flows are verified.
9. controlPlaneReady = true.]]></code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>The PacketIn workflow must remain disabled until OpenFlow bootstrap completes. Otherwise the controller can receive data-plane events before ARP forwarding, edge connector identity, or deterministic IPv4 forwarding is ready.</p>
</tab>
</tabs>
</def>
</deflist>

## Observability Stack {id="per-device-observability-stack"}

The implemented observability stack consists of Prometheus and the SNMP exporter. Prometheus scrapes the classifier API, the controller-side application metrics endpoint, and the SNMP exporter target that proxies SNMP reads from the XRv routers. Grafana is not yet deployed by this topology and is intentionally not documented as an active service here.

### Baseline Configuration Files

<tabs group="observability-files">
<tab title="Prometheus" group-key="prometheus">
<code-block lang="yaml" src="../../src/main/containerlab/configurations/tech-demonstrator/services/prometheus.yml"/>
</tab>
<tab title="SNMP exporter auth" group-key="snmp">
<code-block lang="yaml" src="../../src/main/containerlab/configurations/tech-demonstrator/services/snmp-auth.yml"/>
</tab>
</tabs>

<deflist type="full" collapsible="true">
<def title="Prometheus Scrape Configuration">
<tabs group="obs-prometheus">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="yaml" src="../../src/main/containerlab/configurations/tech-demonstrator/services/prometheus.yml" include-lines="1-29"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>Prometheus scrapes both software subsystems directly: the Python classifier API on <code>/metrics</code> and the Java controller-side application on <code>/csa/metrics</code>. The five-second scrape and evaluation cadence is intentional for a demonstrator: it makes control-cycle and update-LSP metrics visible quickly. The three-second timeout is shorter than the scrape interval, so a slow target cannot overlap the next scheduled scrape.</p>
</tab>
</tabs>
</def>
<def title="XRv SNMP Scraping">
<tabs group="obs-snmp-scrape">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="yaml" src="../../src/main/containerlab/configurations/tech-demonstrator/services/prometheus.yml" include-lines="30-72"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>The XRv routers are not scraped directly by Prometheus. For each management address, relabelling copies the original target into <code>__param_target</code> and the <code>instance</code> label, then replaces the actual HTTP scrape address with the SNMP exporter at <code>172.21.121.203:9116</code>. The exporter receives the target, authentication profile, and <code>if_mib</code> module parameters and performs the SNMP request to the router. This preserves a per-router time series while using one HTTP exporter endpoint.</p>
</tab>
</tabs>
</def>
<def title="SNMP Authentication">
<tabs group="obs-snmp-auth">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="yaml" src="../../src/main/containerlab/configurations/tech-demonstrator/services/snmp-auth.yml"/>
</tab>
<tab title="Explanation" group-key="explanation">
<p>The exporter uses SNMPv2c community authentication. The community string matches the read-only community configured on each XRv router, allowing IF-MIB interface counters to be collected without granting SNMP write access. It is still a shared secret for this lab and must be replaced or externally managed before a non-demonstration deployment.</p>
</tab>
</tabs>
</def>
</deflist>
