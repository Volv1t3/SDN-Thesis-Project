# Network Implementation — Main Topology Definition

This topic describes the main Containerlab topology used by the SDN-MPLS-ML technical demonstrator. It documents the lab groups, the addressing plan, the interface-to-interface link inventory, and the application systems deployed around the MPLS-TE core. The main idea of this page is to serve as a repository for the topology and services in the event of loss of data, or for extension of the topology. Various addresses, specially in the management network have been assigned <b>in tandem</b> to represent services that are grouped together, and while this can be seen from the topology itself, it is useful to have it registered here.

The source of truth for this page is the baseline topology file at `src/main/containerlab/topologies/main/sdn-mpls-ai-tech-demo-baseline.clab.yaml` and the device/service configuration files under `src/main/containerlab/configurations/tech-demonstrator`. The configuration files that were used for all four cisco IOS XRv routers will be discussed in a follow up topic where they will be analyzed in depth to understand what each configuration line does per device.

<show-structure for="chapter" depth="3"/>

## Topology Overview {id="main-topology-overview"}
<p>The main topology is a single <code>Containerlab</code> deployment named <b><code>sdn-mpls-ai-techdemonstrator-baseline</code></b>. It combines the MPLS-TE core used for describing the IGP routing protocols and BGP extensions for topology reporting to the SDN controller, OpenFlow edge switching, an OpenDaylight controller, the Python traffic classification API, and a monitoring stack on a shared management network.</p>
<tip>
<p>The technologies that were used in this topology will be discussed in depth both in the thesis documentation and an additional section in this documentation</p>
</tip>

<tip>The naming convention maintained for this entire topology consists of a common root <code>sma-</code> followed by the system or component that is implemented within the container. For example:
<ul>
<li><format color="CornflowerBlue"><code>sma-xrv</code></format>: is the root of all Cisco XRv devices within the topology, followed by their <code>lsrN</code> designation which comes from the shorthand for <code>Label Switching Routers</code> and finally followed by a <code>NATO military code name </code> based on their order of appearance as they were configured in the system.</li>
</ul>

</tip>

<img src="img.png" thumbnail="true"/>

<p>The following definition list explains each of the layers presented above.</p>
<deflist type="full" collapsible="false">

<def title="SDN Overlay And Edge Switching">
<p>The SDN overlay is implemented through OpenDaylight and two Open vSwitch edge bridges. The first Open vSwitch, <code>sma-ovs-pe1-echo</code> sits between the <code>sma-ubuntu-edge-golf</code> and <code>sma-xrv-lsr1-alpha</code> representing, for the left hand side of the topology, the first interconnected site of the MPLS-TE core. The Open vSwitch used here is representing what would normally be deployed as a Customer Edge Router.</p>
<note>
<p>The reason that there is a switch instead of a router in between the core MPLS-TE network and the customer end devices has to do with the <code>PacketIn Notifications</code> that are raised by <code>OpenFlow switches</code>.
</p>
<p>
Being crucial to our implementation, no Cisco XRv router implements the openflow protocol on their own, and so they do not expose the notification to the controller which would help us perform traffic classification and traffic engineering steering. Given this, even if we had placed another router in front of the edge device, we would've still needed the Openflow based Open vSwitch device to <code>raise the notification to the controller and allow the flow to work</code>.
</p>
</note>

<p>On the right hand side of the network, what we would call the second site to interconnect, sits <code>sma-ovs-pe2-foxtrot</code> between <code>sma-xrv-lsr4-delta</code>, and the <code>sma-ubuntu-edge-hotel</code>, again acting as a customer edge device. Both bridges are configured for OpenFlow 1.3 and point their controller and OVSDB manager sessions at <code>172.21.121.100</code>, the OpenDaylight container.</p>
</def>
<def title="MPLS-TE Core">
<p>The MPLS-TE core is built from four Cisco XRv routers: <code>sma-xrv-lsr1-alpha</code>, <code>sma-xrv-lsr2-bravo</code>, <code>sma-xrv-lsr3-charlie</code>, and <code>sma-xrv-lsr4-delta</code>. The original MPLS-TE core contained four links, representing a rhombus that spanned a single link between the lsr1 and lsr4 devices.</p>
<p>This topology was kept for the final implementation, but it was enhanced with added links spanning from lsr1 connecting to lsr2 and lsr4, and more link starting from lsr2 and lsr3 to add variation into the CSPF path selection.</p>
</def>
<def title="Testing Nodes">
<p>The data-plane test endpoints are Ubuntu containers. <code>sma-ubuntu-edge-golf</code> uses <code>192.168.10.10/24</code> behind the left OVS edge. <code>sma-ubuntu-edge-hotel</code> uses <code>192.168.20.10/24</code> behind the right OVS edge. Their static routes send remote access-network and lab-control prefixes toward the adjacent XRv access interface.</p>
<p>The image from which these devices are built upon corresponds to a custom Dockerfile describing an <code>Ubuntu 24.04 image</code> with additional tooling installed for network traffic generation, configuration and testing. This image will be presented alongside the configuration for the MPLS core in a future section.</p>
</def>
<def title="Controller And API Layer">
<p>The OpenDaylight container, <code>sma-odl-controller-india</code>, runs the controller-side application and the ODL services used by the workflow. The ML classifier runs separately as <code>sma-odl-ml-api-kilo</code> on <code>172.21.121.200:33761</code>. The controller calls the classifier at <code>/api/v1/classify</code>, keeps ODL RESTCONF data access on <code>8182</code>, and uses ODL operations through <code>8181</code>.</p>
</def>
<def title="Observability Layer">
<p>Grafana (<code>sma-obs-grafana-lima</code>), Prometheus (<code>sma-obs-prometheus-mike</code>), and an SNMP exporter (<code>sma-obs-snmp-exporter-november</code>) run as lab services on the management network. Prometheus scrapes the Python classifier API at <code>/metrics</code>, the controller-side application at <code>/csa/metrics</code> through its <code>8181 HTTP operations port</code> which is used for RPC operations but in this case is used for exposing an HTTP endpoint, and the XRv routers through SNMP exporter.</p>
<tip>
The SNMP exporter is configured to scrape metrics from the same <code>community of sdn-mpls-ml-tech-demo-monitor</code> which is configured to be the read only community for SNMP exporting on all four routers.
</tip>
</def>
</deflist>

### Component Groups

<p>Having expored a general view of the groupins, the following table defines specifically the name sof the devices in question and their purpose within the topology.</p>

| Group | Nodes | Purpose |
|---|---|---|
| MPLS-TE core | `sma-xrv-lsr1-alpha`, `sma-xrv-lsr2-bravo`, `sma-xrv-lsr3-charlie`, `sma-xrv-lsr4-delta` | Runs OSPF, MPLS-TE, RSVP-TE, BGP-LS, NETCONF, SNMP, and delegated RSVP-TE tunnels. |
| OpenFlow edge | `sma-ovs-pe1-echo`, `sma-ovs-pe2-foxtrot` | Bridges host traffic into and out of the MPLS edge and mirrors selected IPv4 packets to the controller. |
| Traffic endpoints | `sma-ubuntu-edge-golf`, `sma-ubuntu-edge-hotel` | Generate and receive traffic used to validate classification and tunnel-policy behavior. |
| SDN controller | `sma-odl-controller-india` | Hosts OpenDaylight, the controller-side application, RESTCONF exposure, PCEP interaction, path computation, and OpenFlow programming. |
| ML/API layer | `sma-odl-ml-api-kilo` | Provides traffic classification and policy mapping through an HTTP API. |
| Observability | `sma-obs-grafana-lima`, `sma-obs-prometheus-mike`, `sma-obs-snmp-exporter-november` | Provides dashboards, metric scraping, controller/API metric collection, and SNMP interface telemetry. |


## Addressing And Interface Model {id="main-topology-addressing"}

The lab separates addresses into five practical planes: management, access, MPLS core, controller control-plane, and service endpoints. This separation makes it possible to observe and operate the containers without confusing management reachability with the traffic being steered through delegated MPLS-TE tunnels.

### Management Network

<p>All lab nodes attach to the Containerlab management network `sma-mgmt` with subnet `172.21.121.0/24`. This is a network that has to be configured as it is exposed to the local testing environment, the node where the topology runs, and it is used for SSH access and management of these devices. Its role is crucial in testing systems that do not have a GUI like the Python ML API, the Controller Side Application or the SDN Controller itself which all function internally and expose only some API endpoints for management.</p> 

<note>
<p>All of the connections here are not used for traffic steering, as these are only used for management and network access, as such only a single interface on all devices is configured to have this IP assigned to it. For routers its configuration is done <code>externally to Cisco IOS configuration files</code> which means no interface that we are aware of has access to this, so it cannot even be exposed through OSPF.</p>

</note>

| Node | Management IPv4 | Role |
|---|---:|---|
| `sma-xrv-lsr1-alpha` | `172.21.121.11` | MPLS-TE LSR and headend PCC for the forward delegated LSP from lsr1 to lsr4 known as  `sma-lsr1-lsr4-delegated`. |
| `sma-xrv-lsr2-bravo` | `172.21.121.12` | MPLS-TE transit LSR. Earlier on this device was not configured to be part of this network, it was discovered that it had to be registered in the network based on all of the interfaces that participated in the LSR core. |
| `sma-xrv-lsr3-charlie` | `172.21.121.13` | MPLS-TE transit LSR. |
| `sma-xrv-lsr4-delta` | `172.21.121.14` | MPLS-TE LSR and headend PCC for the reverse delegated LSP called `sma-lsr4-lsr1-delegated`. |
| `sma-ovs-pe1-echo` | `172.21.121.15` | Left OpenFlow edge bridge. |
| `sma-ovs-pe2-foxtrot` | `172.21.121.16` | Right OpenFlow edge bridge. |
| `sma-ubuntu-edge-golf` | `172.21.121.17` | Left traffic host. |
| `sma-ubuntu-edge-hotel` | `172.21.121.18` | Right traffic host. |
| `sma-odl-controller-india` | `172.21.121.100` | OpenDaylight and controller-side application. |
| `sma-odl-ml-api-kilo` | `172.21.121.200` | Python classifier API. |
| `sma-obs-grafana-lima` | `172.21.121.201` | Grafana service. |
| `sma-obs-prometheus-mike` | `172.21.121.202` | Prometheus service. |
| `sma-obs-snmp-exporter-november` | `172.21.121.203` | SNMP exporter service. |

### Router IDs And Delegated Tunnels

<p>This is the core of the network that was created after LSP instantiation from the controller was proven to not be possible given the mismatched API between Opendaylight and Cisco XRv devices. As such these two delegated tunnels were created to serve as the bidirectional tunnels used for interconnecting the two sites together. In this case, the controller updates these LSPs rather than create new ones to maintain a functional topology.</p>

| Router | Loopback0 / Router ID | Delegated tunnel | Signalled name | Destination |
|---|---|---:|---|---|
| `sma-xrv-lsr1-alpha` | `11.11.11.11/32` | `tunnel-te110` | `sma-lsr1-lsr4-delegated` | `14.14.14.14` |
| `sma-xrv-lsr2-bravo` | `12.12.12.12/32` | Only a transit LSR | Not applicable | Not applicable |
| `sma-xrv-lsr3-charlie` | `13.13.13.13/32` | Only a transit LSR | Not applicable | Not applicable |
| `sma-xrv-lsr4-delta` | `14.14.14.14/32` | `tunnel-te410` | `sma-lsr4-lsr1-delegated` | `11.11.11.11` |

<p>When viewed from this perspective then, the system only truly has a single pair of tunnels, effectively a single tunnel, which is updated constantly during testing based on the different traffic requirements raised by incoming packet flows. While this design is useful and simple enough to be implemented within a constrained virtual environment, a real deployment would follow a VRF approach and use subnet mapping to different tunnels in order to offer more tunnels at the same time.</p>

### Access Links
<p>
The access link information that is stored in the network is <b><code>simple by design</code></b>, this network was not meant to showcase tens of hosts talking to each other constantly over the topology. Instead, it was meant to validate that the notion of site-to-site interconnectivity, mediated through the programmability of an SDN network and the proven MPLS-TE implementations of Cisco devices could be used as a real implementation mechanism to bridge physical infrastructure and software defined technologies.
</p>
<p>As such, the hosts implemented here are relatively simple, having only a single interface connected to the corresponding OVS switch defined on either side of the topology. They are in turn under a single subnet corresponding to the subnet of the router's Gig0/0 interface.</p>


| Source device | Source interface/address | Destination device | Destination interface/address | Link role | Use |
|---|---|---|---|---|---|
| `sma-ubuntu-edge-golf` | `eth1`, `192.168.10.10/24` | `sma-ovs-pe1-echo` | `host-golf` | `access_host_to_ovs` | Left-side host access into the OVS edge bridge. |
| `sma-ovs-pe1-echo` | `core-lsr1` | `sma-xrv-lsr1-alpha` | `Gi0/0/0/0`, `192.168.10.1/24` | `access_ovs_to_lsr` | Left-side edge handoff from OpenFlow bridge into the MPLS headend. |
| `sma-xrv-lsr4-delta` | `Gi0/0/0/0`, `192.168.20.1/24` | `sma-ovs-pe2-foxtrot` | `core-lsr4` | `access_lsr_to_ovs` | Right-side edge handoff from MPLS tail/headend into OpenFlow bridge. |
| `sma-ovs-pe2-foxtrot` | `host-hotel` | `sma-ubuntu-edge-hotel` | `eth1`, `192.168.20.10/24` | `access_ovs_to_host` | Right-side host access out of the OVS edge bridge. |

<p>Internally, each host knows the route towards its default gateway, attached to either lsr1 or lsr4 depending on the site of the network.</p>

### Baseline MPLS-TE Core Links

<p>The baseline core preserves the original diamond structure. These links participate in OSPF area <code>0</code>, have MPLS-TE enabled on the XRv interfaces, and mirror the Containerlab link capacities in the XRv RSVP bandwidth configuration.</p>
<p>It is of particular importance the notion of them being on the same <code>area 0</code>, as these devices aren't just sharing MPLS information or RSVP information or, for that matter, aren't simply sharing traffic engineering information. For each of the protocols that form above these devices, including MPLS-TE and BGP-LS, a baseline <i>Interior Gateway Protocol</i> has to be established in order to produce both reachability and traffic engineering information.</p>
<p>As such, when these devices define their OSPF area to be 0, they are defining both that <b>this is the area they will use for normal IGP operations</b> including determining adjancencies and distributing reachability information. Additionally, these devices were configured to share <b>MPLS-TE information on the same area</b>, which means traffic engineering metrics and data points can also be transmitted, allowing all devices to have both a reachability and TE view of the network</p>
<br/><br/>
<p>Moreover, the configuration used to describe these devices also determines their participation within the MPLS-Te core network. Initially, the first implemented version assumed that only the head end and tail end routers needed to participate within the MPLS-TE network and that by specifying the interfaces over which MPLS-TE traffic could go through other routers would learn the topological information to allow for this traffic to go through. On the contrary, the lsr2 and lsr3 routers never allowed traffic to go through them, effectively severing MPLS LSPs. For this reason, most devices are now part of the same MPLS-TE core network and report that MPLS-TE traffic can go through their respective links alongside OSPF configuration which already allows reachability and TE information to flow through.</p>
<p>With this configuration, and the BGP-LS configuration that will be discussed later on the topology is capable of both routing information through the core based on MPLS labels over signaled and reserved RSVP-TE LSPs and share topology information to the controller.</p>

| Source device | Source interface/address | Destination device | Destination interface/address | Subnet | Capacity |
|---|---|---|---|---|---:|
| `sma-xrv-lsr1-alpha` | `Gi0/0/0/1`, `10.0.11.1/30` | `sma-xrv-lsr2-bravo` | `Gi0/0/0/1`, `10.0.11.2/30` | `10.0.11.0/30` | `50000 kbps` |
| `sma-xrv-lsr1-alpha` | `Gi0/0/0/2`, `10.0.12.1/30` | `sma-xrv-lsr3-charlie` | `Gi0/0/0/2`, `10.0.12.2/30` | `10.0.12.0/30` | `75000 kbps` |
| `sma-xrv-lsr2-bravo` | `Gi0/0/0/0`, `10.0.21.1/30` | `sma-xrv-lsr4-delta` | `Gi0/0/0/2`, `10.0.21.2/30` | `10.0.21.0/30` | `50000 kbps` |
| `sma-xrv-lsr3-charlie` | `Gi0/0/0/1`, `10.0.22.1/30` | `sma-xrv-lsr4-delta` | `Gi0/0/0/1`, `10.0.22.2/30` | `10.0.22.0/30` | `75000 kbps` |

### Extended MPLS-TE Links

<p>The extended links make the main topology richer than the original diamond. They introduce alternate lower-capacity and high-capacity paths so policy profiles and requested bandwidth can influence CSPF and <code>update-lsp</code> outcomes.</p>

| Source device | Source interface/address | Destination device | Destination interface/address | Subnet | Capacity |
|---|---|---|---|---|---:|
| `sma-xrv-lsr1-alpha` | `Gi0/0/0/4`, `10.0.13.1/30` | `sma-xrv-lsr2-bravo` | `Gi0/0/0/2`, `10.0.13.2/30` | `10.0.13.0/30` | `25000 kbps` |
| `sma-xrv-lsr1-alpha` | `Gi0/0/0/5`, `10.0.14.1/30` | `sma-xrv-lsr4-delta` | `Gi0/0/0/6`, `10.0.14.2/30` | `10.0.14.0/30` | `10000 kbps` |
| `sma-xrv-lsr2-bravo` | `Gi0/0/0/5`, `10.0.23.1/30` | `sma-xrv-lsr3-charlie` | `Gi0/0/0/4`, `10.0.23.2/30` | `10.0.23.0/30` | `10000 kbps` |
| `sma-xrv-lsr2-bravo` | `Gi0/0/0/4`, `10.0.24.1/30` | `sma-xrv-lsr4-delta` | `Gi0/0/0/4`, `10.0.24.2/30` | `10.0.24.0/30` | `25000 kbps` |
| `sma-xrv-lsr3-charlie` | `Gi0/0/0/0`, `10.0.25.1/30` | `sma-xrv-lsr4-delta` | `Gi0/0/0/5`, `10.0.25.2/30` | `10.0.25.0/30` | `75000 kbps` |

### OpenDaylight Control Links

<p>The ODL control links are not part of the constrained MPLS-TE data-plane model. They provide the reachability needed for BGP-LS, PCEP/PCC interaction, controller-side topology discovery, and ODL operational workflows.</p>
<br/><br/>
<p>The final topology implements these three mechanisms separatedly and it is important to introduce them clearly. First, when it comes to BGP-LS, this extension over BGP was engineered to make it easy to transport network topology and traffic engineering information from a topology to a controller or a PCE enabled device for the construction of their <i>Traffic Engineering Databases</i> which are the internal databases a PCE device maintains of the state of the network. In order to do so, at least for Opendaylight it is okay to have a <b>single device with a peer towards the corresponding controller who would receive network information through said connection.</b> For this to happen of course, both the controller's BGP-LS subsystem and the router's susbystem should be configured to share this information.</p>
<p>The information configured on the controller and the routers should correspond to the <code>Autonomous System number</code> and the corresponding <code>Address Family Identifer and Subsequent Address Family Identifier</code> values which in this case correspond to <code>65000</code> on all devices and the controller, effectively defining an <b>iBGP session</b> between them, and <code>link-state link-state</code> which corresponds to the recorded and standardize way of defining that the BGP-LS session here is carrying topological information.</p>
<br/><br/>
<p>In the case of PCEP/PCC interaction, the main configuration corresponds from the router side of the topology, in which both head end routers need to determine that their PCE or <i>Path Computation Element</i> corresponds to the SDN controller we have implemented, which is why the second address often the .2 in the /30 network between all routers is configured in some way either for PCE or for BGP-LS. In the case of PCE/PCC configuration, the PCE always receives the ODL IP as the router in this case is a client of said PCE to whom it delegates the MPLS-TE LSP tunnels that it already has for their management.</p>

| XRv router | XRv interface/address | ODL interface/address | Subnet | Use |
|---|---|---|---|---|
| `sma-xrv-lsr1-alpha` | `Gi0/0/0/3`, `10.100.10.1/30` | `eth1`, `10.100.10.2/30` | `10.100.10.0/30` | LSR1 BGP-LS and PCEP peer path to ODL. |
| `sma-xrv-lsr2-bravo` | `Gi0/0/0/3`, `10.100.20.1/30` | `eth2`, `10.100.20.2/30` | `10.100.20.0/30` | LSR2 BGP-LS path to ODL. |
| `sma-xrv-lsr3-charlie` | `Gi0/0/0/3`, `10.100.30.1/30` | `eth3`, `10.100.30.2/30` | `10.100.30.0/30` | LSR3 BGP-LS path to ODL. |
| `sma-xrv-lsr4-delta` | `Gi0/0/0/3`, `10.100.40.1/30` | `eth4`, `10.100.40.2/30` | `10.100.40.0/30` | LSR4 BGP-LS and PCEP peer path to ODL. |

### OpenFlow Edge Behavior

<p>Both OVS bridges are started as <code>netdev</code> bridges and configured with OpenFlow 1.3. Their baseline flow rules keep ARP forwarding local between access and core ports while sending host-originated IPv4 traffic to the controller before forwarding it into the core.</p>

<tip>The main reason traffic is <b>allowed after being forwarded to the core</b> is that we are assuming that connectivity between sites must never be lost even while the appropriate tunnel is being configured. In this case, allowing traffic to flow allows us to guarantee connectivity while the controller side application injects all configuration rules.</tip>
<tip>A conscious decision was made to <b><code>block the resending of core to host traffic up to the controller</code></b> again as we can operate under the assumption that traffic returning to the originating host, on either side has already gone through a classifying process and therefore does not need more notifications.</tip>

| Bridge | Ports | Controller/manager | Baseline behavior |
|---|---|---|---|
| `sma-ovs-pe1-echo` | `host-golf`, `core-lsr1` | Controller `tcp:172.21.121.100:6653`; manager `tcp:172.21.121.100:6640` | ARP is forwarded in both directions. IPv4 packets entering from `host-golf` are sent to the controller and then output to `core-lsr1`. Core-to-host IPv4 is forwarded to `host-golf`. |
| `sma-ovs-pe2-foxtrot` | `host-hotel`, `core-lsr4` | Controller `tcp:172.21.121.100:6653`; manager `tcp:172.21.121.100:6640` | ARP is forwarded in both directions. IPv4 packets entering from `host-hotel` are sent to the controller and then output to `core-lsr4`. Core-to-host IPv4 is forwarded to `host-hotel`. |

## Implemented Systems And Their Uses {id="main-topology-systems"}

This topology is not only a packet-forwarding graph. It is a combined control, inference, observability, and validation environment. Each supporting system is deployed inside the same Containerlab file so the demonstration can be started as a single lab.

### Routing, MPLS-TE, And Signalling

| System | Implemented where | Use in the demonstrator |
|---|---|---|
| OSPF area `0` | XRv routers | Provides IGP reachability and distributes link-state information for the TE model. |
| MPLS Traffic Engineering | XRv core interfaces | Marks the constrained links as MPLS-TE-capable and exposes the TE attributes needed by path computation. |
| RSVP-TE bandwidth | XRv core interfaces | Mirrors the Containerlab link capacities so bandwidth-aware CSPF decisions can be validated against configured constraints. |
| BGP-LS | XRv routers and ODL | Exposes the link-state topology toward ODL and the controller-side application. |
| PCEP / PCC | LSR1 Alpha, LSR4 Delta, ODL | Allows ODL to update the delegated RSVP-TE LSPs `sma-lsr1-lsr4-delegated` and `sma-lsr4-lsr1-delegated`. |
| NETCONF and SSH | XRv routers | Keeps the routers reachable for management and future validation workflows. |
| SNMP | XRv routers and SNMP exporter | Exposes interface telemetry to Prometheus through the SNMP exporter. |

### Controller And Classifier Workflow

| System | Endpoint or configuration | Use |
|---|---|---|
| OpenDaylight RESTCONF data | `http://127.0.0.1:8182/restconf/data` from inside the ODL container | Reads topology state, PCEP topology state, operational state, OpenFlow inventory, and controller-side application data. |
| ODL operations | `http://127.0.0.1:8181/rests/operations` from inside the ODL container | Executes path computation and PCEP `update-lsp` operations. |
| Controller-side application | `sma-odl-controller-india` | Handles PacketIn notifications, extracts features, calls the classifier, computes or reuses constrained paths, and updates delegated LSPs. |
| Python classifier API | `http://172.21.121.200:33761/api/v1/classify` | Classifies packet features and returns the policy profile used by the controller-side workflow. |
| Classifier metrics | `http://172.21.121.200:33761/metrics` | Provides Prometheus metrics for the API layer. |
| Controller-side metrics | `http://172.21.121.100:8181/csa/metrics` | Provides Prometheus metrics for the Java controller-side application. |

### Observability Stack

| Service | Address / port | Configuration source | Use |
|---|---|---|---|
| Grafana | `172.21.121.201`, host port `34761` | `services/provisioning` bind mount | Dashboard and visualization layer for operational validation. |
| Prometheus | `172.21.121.202`, host port `34762` | `services/prometheus.yml` | Scrapes the classifier API, controller-side application, and SNMP exporter. |
| SNMP exporter | `172.21.121.203:9116` | `services/snmp-auth.yml` | Converts XRv SNMP interface telemetry into Prometheus metrics. |
| Controller logs | `/logs` inside ODL container | `data-folders/karaf-csa-logs` bind mount | Stores controller-side application logs for workflow tracing and failure diagnosis. |
| API logs | `/api/logs` inside API container | `data-folders/ml-api-logs` bind mount | Stores classifier API request and inference logs. |

