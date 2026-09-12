# Main Topology Definition — Per Device Configuration Files And Definitions

This page documents the device-level and service-level configuration that backs the main topology definition. It is organized by Cisco XRv devices, the OpenDaylight controller-side configuration, OpenFlow switch bootstrap behavior, and the observability stack.

<show-structure for="chapter,def" depth="2"/>

## Cisco Devices {id="per-device-cisco-devices"}

<p>The four Cisco XRv nodes share the same baseline design: a <code>loopback router ID</code>, <code>OSPF area 0</code>, <code>MPLS-TE</code> on constrained core links, <code>RSVP</code> bandwidth constraints, <code>BGP-LS</code> sessions, <code>NETCONF/SSH</code> management, and <code>SNMP</code> monitoring. LSR1 Alpha and LSR4 Delta additionally define <code>delegated RSVP-TE</code> tunnels because they are the two headends controlled by the application workflow. </p>

### `sma-xrv-lsr1-alpha` {id="config-sma-xrv-lsr1-alpha"}

Full configuration source view:

<code-block lang="plain text" src="../../src/main/containerlab/configurations/tech-demonstrator/sma-xrv-lsr1-alpha.cfg" collapsible="true"/>

<deflist type="full" collapsible="true">
<def title="Hostname, Router ID, And Access Interface">
<tabs group="lsr1-hostname-loopback-access">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text">
hostname sma-xrv-lsr1-alpha
!
interface Loopback0
 description PROTOCOL-ROUTER-ID
 ipv4 address 11.11.11.11 255.255.255.255
!
interface GigabitEthernet0/0/0/0
 description ACCESS-TO-SMA-OVS-PE1-ECHO
 ipv4 address 192.168.10.1 255.255.255.0
 no shutdown</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>The main configuration snippet defines two of the basic elements of the router, first it defines the <code>Loopback0 interface</code> used as the identifying interface for both RSVP-TE delegated tunnels, for the OSPF router identify and to establish the identity of this router in the BGP-LS mesh being created within the MPLS-TE core and the ODL controller.</p>
<p>Second it defines the access interface for the left hand side of the network, describing the <code>192.168.10.1/24 network</code> which is representative of our first site to interconnect through this network. These two make up the basis of the router as they define its identity and its purpose to the user.</p>
</tab>
</tabs>
</def>
<def title="Core And Control Interfaces">
<tabs group="lsr1-core-control">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text">
interface GigabitEthernet0/0/0/1
 description CORE-TO-SMA-XRV-LSR2-BRAVO
 ipv4 address 10.0.11.1 255.255.255.252
 no shutdown
!
interface GigabitEthernet0/0/0/2
 description CORE-TO-SMA-XRV-LSR3-CHARLIE
 ipv4 address 10.0.12.1 255.255.255.252
 no shutdown
!
interface GigabitEthernet0/0/0/3
 description CONTROL-TO-ODL-ETH1
 ipv4 address 10.100.10.1 255.255.255.252
 no shutdown
!
interface GigabitEthernet0/0/0/4
 description CORE-TO-SMA-XRV-LSR2-BRAVO-VARIANT-2
 ipv4 address 10.0.13.1 255.255.255.252
 no shutdown
!
interface GigabitEthernet0/0/0/5
 description CORE-TO-SMA-XRV-LSR4-DELTA-VARIANT-2
 ipv4 address 10.0.14.1 255.255.255.252
 no shutdown
</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>The following snippet describes the interfaces that are defined within the core lsr1 router, describing both the <code>VARIANT-2</code> interfaces of the extended topology, and the <code>CORE</code> interfaces of the baseline topology. These are basic Gigabit Ethernet interfaces that are registered both here and in the topology file, these do not represent any tunnel yet.</p>
</tab>
</tabs>
</def>
<def title="Delegated RSVP-TE Tunnel">
<tabs group="lsr1-tunnel">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text">
interface tunnel-te110
 description SMA-DELEGATED-LSR1-TO-LSR4
 ipv4 unnumbered Loopback0
 signalled-name sma-lsr1-lsr4-delegated
 destination 14.14.14.14
 pce
  delegation
 !
 path-option 10 dynamic
</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>This pre-created tunnel is the <code>forward delegated LSP</code> controlled by the <code>Controller Side Application</code>. Its implementation is important as it is described as being <code>delegated to the pce</code> directly, which means that this is one of the LSPs that the controller will receive and register in its own databases and allow us to handle directly through RESTCONF operations on the controller.</p>
<p>Something important to mention is that it has been assigned the <code>ipv4 unnumbered Loopback0</code> address. This address has been used as the identity of the router in the OSPF IGP definition, the MPLS-TE definitions, and even in the BGP-LS mesh that has been created, so its use here is significant. The Loopback0 address that was defined initially creates a <b>static identity to the router</b> that can be used in multiple protocols as the identifier for the router. Its use follows what the requirements for <code>RSVP-TE tunnels define</code> as these tunnels need to be identified by a tunnel id, in this case <b>110</b> as well as the <code>destination IP</code> which in this case is <b>lsr4's stable loopback0 interface</b> and an source IP which in this  case is the <b>loopback of lsr1</b>. This means that in theory, these identifiers are not use to define the <i>routing of the MPLS LSP tunnels</i>, rather they are used to determine the convention of the <i>identifying parameters of the tunnel not its routing mechanism</i> as it is possible that the route this tunnel might follow changes constantly.</p>
</tab>
</tabs>
</def>
<def title="OSPF Traffic Engineering">
<tabs group="lsr1-ospf">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text">
router ospf 100
 router-id 11.11.11.11
 mpls traffic-eng router-id Loopback0
 distribute link-state instance-id 36
 area 0
  mpls traffic-eng
  interface Loopback0
   passive enable
  !
  interface GigabitEthernet0/0/0/0
   passive enable
  !
  interface GigabitEthernet0/0/0/1
   network point-to-point
  !
  interface GigabitEthernet0/0/0/2
   network point-to-point
  !
  interface GigabitEthernet0/0/0/4
   network point-to-point
  !
  interface GigabitEthernet0/0/0/5
   network point-to-point
</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>OSPF provides reachability and traffic-engineering link-state information. We define access and loopback interfaces as passive, while MPLS-TE core links are point-to-point OSPF adjacency. The distributed link-state instance feeds BGP-LS export. In this case, we determine that all of the MPLS-TE core links are part of the OSPF IGP protocol's definition such that these also form adjancencies with other OSPF instances in the network and generate a full view of the topology to distribute, through BGP-LS, to the controller.</p>
<p>In addition to this, we define the identity of the router that the OSPF-TE Opaque LSAs  will communicate to other routers. This stable identity is used such that it can be associated with an MPLS-TE router allowing these routers to build topology and traffic engineering databases of the other router within the topology. By Cisco's documentation it is recommended to use <b>the same ID as the OSPF-TE ID for the MPLS-TE router ID</b>. </p>
<p>A third interesting component worth mentioning is the configuration of the <code>distribute link-state instance-id XX</code> line which is a configuration required to <b>establish the OSPF-TE to BGP-LS link state information sharing link</b> where topological information from OSPF-TE can be registered into the <code>Link State Caches</code> of the BGP-LS instance running in the corresponding router (in this case lsr1) and later on distributed to ODL.</p>
</tab>
</tabs>
</def>
<def title="MPLS-TE, PCEP, And RSVP Bandwidth">
<tabs group="lsr1-mpls-pce-rsvp">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text">
mpls traffic-eng interface GigabitEthernet0/0/0/1
mpls traffic-eng interface GigabitEthernet0/0/0/2
mpls traffic-eng interface GigabitEthernet0/0/0/4
mpls traffic-eng interface GigabitEthernet0/0/0/5
!
mpls traffic-eng pce
 peer source ipv4 10.100.10.1
 peer ipv4 10.100.10.2
 stateful-client
  instantiation
  report
 !
 auto-tunnel pcc
  tunnel-id min 300 max 399
 !
!
rsvp
 interface GigabitEthernet0/0/0/1
  bandwidth 50000
 !
 interface GigabitEthernet0/0/0/2
  bandwidth 75000
 !
 interface GigabitEthernet0/0/0/4
  bandwidth 25000
 !
 interface GigabitEthernet0/0/0/5
  bandwidth 10000
</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>MPLS-TE is enabled only on constrained core links excluding from the MPLS-TE network the core user facing interfaces and the management IP network which crucially has never appeared in any configurations. The PCEP peer points to ODL on <code>10.100.10.2</code> defining in this case the relationship of <code>lsr1 as the PCC and ODL as the PCE</code>, and RSVP bandwidth values mirror the Containerlab link metadata and netem bandwidth shaping. These values are the constraints consumed by path computation and signaled through OSPF-TE, picked up by BGP-LS and shared to ODL. While the configuration itself determines the split between ODL and lsr1 as PCE and PCC, at the beginning of the network, before the connection between the routers and ODL, each headend router performs a Constraint Shortest Path First Calculation to determine the initial route their tunnels will take over the network, a route that might be updated by ODL. </p>
</tab>
</tabs>
</def>
<def title="BGP-LS, Static Routing, And Management Services">
<tabs group="lsr1-bgpls-management">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text">
router bgp 65000
 bgp router-id 11.11.11.11
 address-family link-state link-state
 !
 neighbor 12.12.12.12
  remote-as 65000
  description BGP-LS-MESH-LSR2-BRAVO
  update-source Loopback0
  address-family link-state link-state
 !
 neighbor 13.13.13.13
  remote-as 65000
  description BGP-LS-MESH-LSR3-CHARLIE
  update-source Loopback0
  address-family link-state link-state
 !
 neighbor 14.14.14.14
  remote-as 65000
  description BGP-LS-MESH-LSR4-DELTA
  update-source Loopback0
  address-family link-state link-state
 !
 neighbor 10.100.10.2
  remote-as 65000
  description BGP-LS-TO-ODL
  address-family link-state link-state
!
router static
 address-family ipv4 unicast
  192.168.20.0/24 tunnel-te110
!
netconf-yang agent ssh
ssh server v2
ssh server netconf vrf default
snmp-server community sdn-mpls-ml-tech-demo-monitor RO
snmp-server ifindex persist
</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>BGP-LS exports topology information to peer LSRs and ODL. The static route forces traffic toward the remote access subnet through the delegated tunnel. NETCONF/SSH keep the router manageable, while SNMP supports Prometheus collection through the SNMP exporter.</p>
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
<code-block lang="text">
hostname sma-xrv-lsr2-bravo
!
interface Loopback0
 description PROTOCOL-ROUTER-ID
 ipv4 address 12.12.12.12 255.255.255.255
!
interface GigabitEthernet0/0/0/0
 description CORE-TO-SMA-XRV-LSR4-DELTA
 ipv4 address 10.0.21.1 255.255.255.252
 no shutdown
!
interface GigabitEthernet0/0/0/1
 description CORE-TO-SMA-XRV-LSR1-ALPHA
 ipv4 address 10.0.11.2 255.255.255.252
 no shutdown
!
interface GigabitEthernet0/0/0/2
 description CORE-TO-SMA-XRV-LSR1-ALPHA-VARIANT-2
 ipv4 address 10.0.13.2 255.255.255.252
 no shutdown
!
interface GigabitEthernet0/0/0/4
 description CORE-TO-SMA-XRV-LSR4-DELTA-VARIANT-2
 ipv4 address 10.0.24.1 255.255.255.252
 no shutdown
!
interface GigabitEthernet0/0/0/5
 description CORE-TO-SMA-XRV-LSR3-CHARLIE-VARIANT-2
 ipv4 address 10.0.23.1 255.255.255.252
 no shutdown
</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>LSR2 is a transit router in the MPLS-TE graph. It has no access segment and no delegated tunnel headend role. Its interfaces define the upper diamond path, the alternate LSR1 link, the alternate LSR4 link, and the vertical LSR2-LSR3 constrained path.</p>
</tab>
</tabs>
</def>
<def title="Controller-Facing Interface">
<tabs group="lsr2-control">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text">
interface GigabitEthernet0/0/0/3
 description CONTROL-TO-ODL-ETH2
 ipv4 address 10.100.20.1 255.255.255.252
 no shutdown
</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>This interface gives ODL a dedicated control-plane path to LSR2 for BGP-LS exchange. It is not enabled for MPLS-TE or RSVP because it is not a traffic-engineered data-plane link. This is part of the BGP-LS mesh that was defined within the MPLS-TE core network, which connects all routers as <b>redundant topology information sources</b> in the event of failure of any of the devices.</p>
</tab>
</tabs>
</def>
<def title="OSPF And MPLS-TE">
<tabs group="lsr2-ospf-mpls">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="Plain Text">
router ospf 100
 router-id 12.12.12.12
 mpls traffic-eng router-id Loopback0
 distribute link-state instance-id 35
 area 0
  mpls traffic-eng
  interface Loopback0
   passive enable
  !
  interface GigabitEthernet0/0/0/0
   network point-to-point
  !
  interface GigabitEthernet0/0/0/1
   network point-to-point
  !
  interface GigabitEthernet0/0/0/2
   network point-to-point
  !
  interface GigabitEthernet0/0/0/4
   network point-to-point
  !
  interface GigabitEthernet0/0/0/5
   network point-to-point
!
mpls traffic-eng interface GigabitEthernet0/0/0/0
mpls traffic-eng interface GigabitEthernet0/0/0/1
mpls traffic-eng interface GigabitEthernet0/0/0/2
mpls traffic-eng interface GigabitEthernet0/0/0/4
mpls traffic-eng interface GigabitEthernet0/0/0/5
</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>OSPF advertises the transit links as point-to-point adjacencies and enables TE attributes in area <code>0</code>. MPLS-TE is enabled on each constrained core interface so CSPF can consider them as viable TE links. This is of special importance given that previous testing showcased that there is a clear need to have <b>all links that will allow tunnels to be carried over it must be listed through mpls traffic-eng intervace ...</b> commands. As such, not only do we need to list the interfaces that participate in OSPF adjacency protocols, but also in MPLS-TE for tunnels to forward their traffic over.</p>
</tab>
</tabs>
</def>
<def title="RSVP Bandwidth">
<tabs group="lsr2-rsvp">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text">
rsvp
 interface GigabitEthernet0/0/0/0
  bandwidth 50000
 !
 interface GigabitEthernet0/0/0/1
  bandwidth 50000
 !
 interface GigabitEthernet0/0/0/2
  bandwidth 25000
 !
 interface GigabitEthernet0/0/0/4
  bandwidth 25000
 !
 interface GigabitEthernet0/0/0/5
  bandwidth 10000
</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>These RSVP bandwidth values mirror the Containerlab link capacities. They are intentionally asymmetric across topology options so classifier-selected policies can produce different path choices. In this case what we are doing with this configuration is defining that the MPLS-TE core interfaces can be used for defining corresponding LSPs and what amount of bandwidth do these have.</p>
<p>
When working with RSVP, the main object that is transmitted initially is the <code>PATH object</code> which can define a <b>Explicit Route Object (ERO)</b> which is the approach we are using when we configure the tunnels from the controller or when we initially define the route that the local CSPF algorithm defined. All in all, RSVP requires first sending this PATH object which is a collection of <code>one IP address per LSR on the calculated path</code>, this means that the PATH is indicating only which routers it must reach, often through their Loopback addresses, and to get to those the IGP routing information is considered, using that information, which is transmitted through an <code>RSVP_HOP</code> message added to the corresponding PATH message through each jump it takes over the network, the routers in a given ERO can all register the path that their devices will take.
</p>
</tab>
</tabs>
</def>
<def title="BGP-LS And Management Services">
<tabs group="lsr2-bgpls-management">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text">
router bgp 65000
 bgp router-id 12.12.12.12
 address-family link-state link-state
 !
 neighbor 11.11.11.11
  remote-as 65000
  description BGP-LS-MESH-LSR1-ALPHA
  update-source Loopback0
  address-family link-state link-state
 !
 neighbor 13.13.13.13
  remote-as 65000
  description BGP-LS-MESH-LSR3-CHARLIE
  update-source Loopback0
  address-family link-state link-state
 !
 neighbor 14.14.14.14
  remote-as 65000
  description BGP-LS-MESH-LSR4-DELTA
  update-source Loopback0
  address-family link-state link-state
 !
 neighbor 10.100.20.2
  remote-as 65000
  description BGP-LS-TO-ODL
  address-family link-state link-state
!
netconf-yang agent ssh
ssh server v2
ssh server netconf vrf default
snmp-server ifindex persist
snmp-server community sdn-mpls-ml-tech-demo-monitor RO
</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>The iBGP link-state mesh allows LSR2 to contribute its TE links to the topology view. The ODL neighbor receives the same address family, and the management services keep the node reachable for inspection and telemetry. In this case, it is important to determine that the corresponding SNMP server configuration lines, similarly to the ones presented in all other routers correspond to the definition of a the allowed SNMPv2c protocol that is implemented between the routers and the SNMP exporter service to get bandwidth usage, throughput and interface information.</p>
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
<code-block lang="text">
hostname sma-xrv-lsr3-charlie
!
interface Loopback0
 description PROTOCOL-ROUTER-ID
 ipv4 address 13.13.13.13 255.255.255.255
!
interface GigabitEthernet0/0/0/0
 description CORE-TO-SMA-XRV-LSR4-DELTA-VARIANT-2
 ipv4 address 10.0.25.1 255.255.255.252
 no shutdown
!
interface GigabitEthernet0/0/0/1
 description CORE-TO-SMA-XRV-LSR4-DELTA
 ipv4 address 10.0.22.1 255.255.255.252
 no shutdown
!
interface GigabitEthernet0/0/0/2
 description CORE-TO-SMA-XRV-LSR1-ALPHA
 ipv4 address 10.0.12.2 255.255.255.252
 no shutdown
!
interface GigabitEthernet0/0/0/4
 description CORE-TO-SMA-XRV-LSR2-BRAVO-VARIANT-2
 ipv4 address 10.0.23.2 255.255.255.252
 no shutdown
</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>LSR3 is the lower-path transit router. It connects to LSR1, LSR2, and LSR4 and provides one of the high-bandwidth paths into LSR4 through <code>10.0.25.0/30</code>. In this case, we define its identity to be <code>13.13.13.13/32</code> as the loopback address which is associated with OSPF-TE, MPLS-TE, and BGP-LS.</p>
</tab>
</tabs>
</def>
<def title="Controller-Facing Interface">
<tabs group="lsr3-control">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text">
interface GigabitEthernet0/0/0/3
 description CONTROL-TO-ODL-ETH3
 ipv4 address 10.100.30.1 255.255.255.252
 no shutdown
</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>The ODL-facing link carries BGP-LS control traffic from LSR3 to the controller. It is separate from the MPLS forwarding graph and does not participate in TE signalling.</p>
</tab>
</tabs>
</def>
<def title="OSPF, MPLS-TE, And RSVP">
<tabs group="lsr3-ospf-mpls-rsvp">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text">
router ospf 100
 router-id 13.13.13.13
 mpls traffic-eng router-id Loopback0
 distribute link-state instance-id 33
 area 0
  mpls traffic-eng
  interface Loopback0
   passive enable
  !
  interface GigabitEthernet0/0/0/0
   network point-to-point
  !
  interface GigabitEthernet0/0/0/1
   network point-to-point
  !
  interface GigabitEthernet0/0/0/2
   network point-to-point
  !
  interface GigabitEthernet0/0/0/4
   network point-to-point
!
mpls traffic-eng interface GigabitEthernet0/0/0/0
mpls traffic-eng interface GigabitEthernet0/0/0/1
mpls traffic-eng interface GigabitEthernet0/0/0/2
mpls traffic-eng interface GigabitEthernet0/0/0/4
!
rsvp
 interface GigabitEthernet0/0/0/0
  bandwidth 75000
 !
 interface GigabitEthernet0/0/0/1
  bandwidth 75000
 !
 interface GigabitEthernet0/0/0/2
  bandwidth 75000
 !
 interface GigabitEthernet0/0/0/4
  bandwidth 10000</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>LSR3 advertises its lower-path and extended-path links into the TE topology. RSVP bandwidth makes <code>10.0.23.0/30</code> intentionally constrained while keeping the LSR1 and LSR4 paths comparatively high capacity.</p>
</tab>
</tabs>
</def>
<def title="BGP-LS And Management Services">
<tabs group="lsr3-bgpls-management">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text">
router bgp 65000
 bgp router-id 13.13.13.13
 address-family link-state link-state
 !
 neighbor 11.11.11.11
  remote-as 65000
  description BGP-LS-MESH-LSR1-ALPHA
  update-source Loopback0
  address-family link-state link-state
 !
 neighbor 12.12.12.12
  remote-as 65000
  description BGP-LS-MESH-LSR2-BRAVO
  update-source Loopback0
  address-family link-state link-state
 !
 neighbor 14.14.14.14
  remote-as 65000
  description BGP-LS-MESH-LSR4-DELTA
  update-source Loopback0
  address-family link-state link-state
 !
 neighbor 10.100.30.2
  remote-as 65000
  description BGP-LS-TO-ODL
  address-family link-state link-state
!
netconf-yang agent ssh
ssh server v2
ssh server netconf vrf default
snmp-server ifindex persist
snmp-server community sdn-mpls-ml-tech-demo-monitor RO
</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>BGP-LS publishes LSR3's lower-path and extended-path links to the topology consumers. NETCONF, SSH, and SNMP are enabled consistently with the other routers for management and telemetry.</p>
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
<code-block lang="text">
hostname sma-xrv-lsr4-delta
!
interface Loopback0
 description PROTOCOL-ROUTER-ID
 ipv4 address 14.14.14.14 255.255.255.255
!
interface GigabitEthernet0/0/0/0
 description ACCESS-TO-SMA-OVS-PE2-FOXTROT
 ipv4 address 192.168.20.1 255.255.255.0
 no shutdown
</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>The loopback address is the stable router ID and reverse LSP destination/source identity. The access interface connects LSR4 to <code>sma-ovs-pe2-foxtrot</code> and provides the default gateway for the Hotel host subnet.</p>
</tab>
</tabs>
</def>
<def title="Core And Control Interfaces">
<tabs group="lsr4-core-control">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text">
interface GigabitEthernet0/0/0/1
 description CORE-TO-SMA-XRV-LSR3-CHARLIE
 ipv4 address 10.0.22.2 255.255.255.252
 no shutdown
!
interface GigabitEthernet0/0/0/2
 description CORE-TO-SMA-XRV-LSR2-BRAVO
 ipv4 address 10.0.21.2 255.255.255.252
 no shutdown
!
interface GigabitEthernet0/0/0/3
 description CONTROL-TO-ODL-ETH4
 ipv4 address 10.100.40.1 255.255.255.252
 no shutdown
!
interface GigabitEthernet0/0/0/4
 description CORE-TO-SMA-XRV-LSR2-BRAVO-VARIANT-2
 ipv4 address 10.0.24.2 255.255.255.252
 no shutdown
!
interface GigabitEthernet0/0/0/5
 description CORE-TO-SMA-XRV-LSR3-CHARLIE-VARIANT-2
 ipv4 address 10.0.25.2 255.255.255.252
 no shutdown
!
interface GigabitEthernet0/0/0/6
 description CORE-TO-SMA-XRV-LSR1-ALPHA-VARIANT-2
 ipv4 address 10.0.14.2 255.255.255.252
 no shutdown
</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>LSR4 terminates the right side of the MPLS core and has the largest number of core adjacencies. It connects to the baseline upper and lower paths, the alternate LSR2/LSR3 paths, and the constrained direct shortcut from LSR1. <code>Gi0/0/0/3</code> is reserved for ODL control-plane reachability.</p>
</tab>
</tabs>
</def>
<def title="Delegated RSVP-TE Tunnel">
<tabs group="lsr4-tunnel">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text">
interface tunnel-te410
 description SMA-DELEGATED-LSR4-TO-LSR1
 ipv4 unnumbered Loopback0
 signalled-name sma-lsr4-lsr1-delegated
 destination 11.11.11.11
 pce
  delegation
 !
 path-option 10 dynamic
</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>This is the reverse delegated LSP controlled by OpenDaylight. It mirrors the LSR1 tunnel role in the opposite direction and gives the controller a stable object to update when reverse-direction traffic policy changes. IN this case, the configuration option of <code>path-option 10 dynamic</code> that was also registered on the side of the lsr1 router, is determining that the lsp has to receive an initial dynamically and locally calculated LSP ERO and that it should be setup to allow for communication, our intended approach.</p>
</tab>
</tabs>
</def>
<def title="OSPF, MPLS-TE, PCEP, And RSVP">
<tabs group="lsr4-ospf-mpls-pce-rsvp">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text">
router ospf 100
 router-id 14.14.14.14
 mpls traffic-eng router-id Loopback0
 distribute link-state instance-id 34
 area 0
  mpls traffic-eng
  interface Loopback0
   passive enable
  !
  interface GigabitEthernet0/0/0/0
   passive enable
  !
  interface GigabitEthernet0/0/0/1
   network point-to-point
  !
  interface GigabitEthernet0/0/0/2
   network point-to-point
  !
  interface GigabitEthernet0/0/0/4
   network point-to-point
  !
  interface GigabitEthernet0/0/0/5
   network point-to-point
  !
  interface GigabitEthernet0/0/0/6
   network point-to-point
!
mpls traffic-eng interface GigabitEthernet0/0/0/1
mpls traffic-eng interface GigabitEthernet0/0/0/2
mpls traffic-eng interface GigabitEthernet0/0/0/4
mpls traffic-eng interface GigabitEthernet0/0/0/5
mpls traffic-eng interface GigabitEthernet0/0/0/6
!
mpls traffic-eng pce
 peer source ipv4 10.100.40.1
 peer ipv4 10.100.40.2
 stateful-client
  instantiation
  report
!
rsvp
 interface GigabitEthernet0/0/0/1
  bandwidth 75000
 !
 interface GigabitEthernet0/0/0/2
  bandwidth 50000
 !
 interface GigabitEthernet0/0/0/4
  bandwidth 25000
 !
 interface GigabitEthernet0/0/0/5
  bandwidth 75000
 !
 interface GigabitEthernet0/0/0/6
  bandwidth 10000
</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>LSR4 advertises all right-side and shortcut TE links, peers with ODL as a stateful PCC through <code>10.100.40.2</code>, and exposes RSVP bandwidth values that match the declared topology constraints.</p>
</tab>
</tabs>
</def>
<def title="BGP-LS, Static Routing, And Management Services">
<tabs group="lsr4-bgpls-management">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="text">
router bgp 65000
 bgp router-id 14.14.14.14
 address-family link-state link-state
 !
 neighbor 11.11.11.11
  remote-as 65000
  description BGP-LS-MESH-LSR1-ALPHA
  update-source Loopback0
  address-family link-state link-state
 !
 neighbor 12.12.12.12
  remote-as 65000
  description BGP-LS-MESH-LSR2-BRAVO
  update-source Loopback0
  address-family link-state link-state
 !
 neighbor 13.13.13.13
  remote-as 65000
  description BGP-LS-MESH-LSR3-CHARLIE
  update-source Loopback0
  address-family link-state link-state
 !
 neighbor 10.100.40.2
  remote-as 65000
  description BGP-LS-TO-ODL
  address-family link-state link-state
!
router static
 address-family ipv4 unicast
  192.168.10.0/24 tunnel-te410
!
netconf-yang agent ssh
ssh server v2
ssh server netconf vrf default
snmp-server ifindex persist
snmp-server community sdn-mpls-ml-tech-demo-monitor RO
</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>BGP-LS makes LSR4's view of the topology visible to ODL. The static route sends remote left-site traffic through the reverse delegated tunnel, and the management services are enabled consistently for operations and telemetry.</p>
</tab>
</tabs>
</def>
</deflist>

## Controller Configuration {id="per-device-controller-configuration"}

The controller configuration is injected through the `sma-odl-controller-india` environment variables in the Containerlab topology. 

Full baseline controller environment view:

<code-block lang="yaml" collapsible="true">
sma-odl-controller-india:
  kind: linux
  image: docker.io/arellanosantiago/sdn-mpls-ai-tech-demo-opendaylight:0.23.1-baseline
  mgmt-ipv4: 172.21.121.100
  memory: 6G
  env:
    JAVA_MIN_MEM: 4G
    JAVA_MAX_MEM: 6G
    # Corresponding Controller side application environment variables 
    TUNNEL_CREATION_MODE: DELEGATED_TUNNEL_UPDATE
    SMA_LOG_DIRECTORY: /logs
    SMA_LOG_FILENAME: sdn_mpls_ml_controller.log
    SMA_LOG_FILE_MAX_BYTES: 10485760
    SMA_LOG_FILE_BACKUP_COUNT: 5
    SMA_LOG_LEVEL: DEBUG
    ODL_BGPLS_TOPOLOGY_ID: sma-bgp-linkstate-topology
    ODL_PCEP_TOPOLOGY_ID: pcep-topology
    ODL_PATH_COMPUTATION_GRAPH_NAME: ted://sma-bgp-linkstate-topology
    SMA_HEADEND_RID: 11.11.11.11
    SMA_HEADEND_PCC_NODE: pcc://10.100.10.1
    SMA_TAILEND_RID: 14.14.14.14
    SMA_TAILEND_PCC_NODE: pcc://10.100.40.1
    SMA_FORWARD_LSP_NAME: sma-lsr1-lsr4-delegated
    SMA_REVERSE_LSP_NAME: sma-lsr4-lsr1-delegated
    SMA_FORWARD_TUNNEL_INTERFACE: tunnel-te110
    SMA_REVERSE_TUNNEL_INTERFACE: tunnel-te410
    SMA_FORWARD_DIRECTION_KEY: lsr1_to_lsr4
    SMA_REVERSE_DIRECTION_KEY: lsr4_to_lsr1
    SMA_HEADEND_TO_TAILEND_INGRESS: sma-ovs-pe1-echo|host-golf
    SMA_TAILEND_TO_HEADEND_INGRESS: sma-ovs-pe2-foxtrot|host-hotel
    ODL_RESTCONF_DATA_BASE_URL: http://127.0.0.1:8182/restconf/data
    ODL_RESTS_OPERATIONS_BASE_URL: http://127.0.0.1:8181/rests/operations
    ODL_USERNAME: admin
    ODL_PASSWORD: admin
    CLASSIFIER_BASE_URL: http://172.21.121.200:33761
    CLASSIFIER_CLASSIFY_PATH: /api/v1/classify
    CLASSIFICATION_CACHE_TTL_SECONDS: 3600
    PATH_CACHE_TTL_SECONDS: 60
    TOPOLOGY_CACHE_TTL_SECONDS: 300
    ODL_OPERATIONAL_VALIDATION_TIMEOUT_SECONDS: 120
    HTTP_REQUEST_TIMEOUT_SECONDS: 10
    ODL_RETRY_INITIAL_DELAY_MS: 500
    ODL_RETRY_MAX_DELAY_MS: 5000
    ODL_RETRY_JITTER_PERCENT: 20
    ODL_TOPOLOGY_DISCOVERY_MAX_ATTEMPTS: 5
    SMA_OPENFLOW_BOOTSTRAP_ENABLED: true
    SMA_OPENFLOW_TABLE_ID: 0
    SMA_OVS_ECHO_MGMT_IP: 172.21.121.15
    SMA_OVS_ECHO_HOST_PORT_NAME: host-golf
    SMA_OVS_ECHO_CORE_PORT_NAME: core-lsr1
    SMA_OVS_FOXTROT_MGMT_IP: 172.21.121.16
    SMA_OVS_FOXTROT_HOST_PORT_NAME: host-hotel
    SMA_OVS_FOXTROT_CORE_PORT_NAME: core-lsr4
    SMA_OPENFLOW_ARP_PRIORITY: 300
    SMA_OPENFLOW_IPV4_PRIORITY: 200
    SMA_OPENFLOW_INSTALL_DEFAULT_DROP: false
    SMA_OPENFLOW_DEFAULT_DROP_PRIORITY: 
</code-block>
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
<p>This first snippet of documentation showcases the environment variables used to configure the BGPLS topology, PCEP Toplogy and Traffic Engineering Database that the controller side application needs to use to poll the corresponding internal state of the BGP-LS RIB database and Link State Caches, the PCEP inventory and the Traffic Engineering Database associated with the BGP-LS topology. With this information, components like the Graph Computation Algorithms used for Constraint Shortest Path First can learn the concrete state of the topology upon computation time and produce a workable ERO. Moreover, this information allows the internal state of the controller side application to mirror the state of the controller and keep local cached copies of nodes, LSPs, openflow nodes and connectors, and current topological information for fast recalculations</p>
<p>In addition to this, the system must know exactly the headend and tailend router configurations, as these ids will be used to extract their information internally during the controller side operation. The information for the headend and tailend node information will also be used to 
communicate directly towards these devices during <code>update_lsp calls</code>.</p>
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
SMA_HEADEND_TO_TAILEND_INGRESS: sma-ovs-pe1-echo|host-golf
SMA_TAILEND_TO_HEADEND_INGRESS: sma-ovs-pe2-foxtrot|host-hotel
</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>The application uses these names to correlate PacketIn ingress points, logical traffic directions, delegated LSP records, and tunnel interfaces. They must remain consistent with router configuration, OpenFlow connector names, and policy state.</p>
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
<def title="Cache, Retry, Logging, And OpenFlow Bootstrap">
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
SMA_LOG_LEVEL: DEBUG
SMA_OPENFLOW_BOOTSTRAP_ENABLED: true
SMA_OPENFLOW_TABLE_ID: 0
SMA_OVS_ECHO_MGMT_IP: 172.21.121.15
SMA_OVS_FOXTROT_MGMT_IP: 172.21.121.16
</code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>TTL settings determine when cached classification, path, and topology state must be refreshed. Retry settings protect ODL startup/discovery timing. The OpenFlow bootstrap settings let the Java application discover the runtime datapath IDs and program deterministic edge flows corresponding to the baseline rules of controller notification + push data towards MPLS core.</p>
</tab>
</tabs>
</def>
</deflist>

### Controller Configuration — RESTCONF and RPC Operations used for controller preconfiguration
<p>The calls presented in this section are to be executed in the order they are provided as these determine the configuration of the baseline 
entities required for making the controller usable for the controller side application, and in general, for enabling the entire
system to function properly. The specific order of these calls allows for the configuration intially of BGP-LS, then 
registering all core routers as BGP-LS peers, then link state topologies used to store the corresponding link state informationa rriving from 
the MPLS-TE core peers in the controller so that finally we can mount all devices using NETCONF.
</p>
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

The observability stack consists of Prometheus, Grafana, and SNMP exporter. Prometheus scrapes the classifier API, the controller-side application metrics endpoint, and the SNMP exporter target that proxies SNMP reads from the XRv routers.

### Baseline Configuration Files

<tabs group="observability-files">
<tab title="Prometheus" group-key="prometheus">
<code-block lang="yaml" src="../../src/main/containerlab/configurations/tech-demonstrator/services/prometheus.yml"/>
</tab>
<tab title="SNMP exporter auth" group-key="snmp">
<code-block lang="yaml" src="../../src/main/containerlab/configurations/tech-demonstrator/services/snmp-auth.yml"/>
</tab>
<tab title="Grafana datasource" group-key="datasource">
<code-block lang="yaml" src="../../src/main/containerlab/configurations/tech-demonstrator/services/provisioning/datasources/datasources.yml"/>
</tab>
<tab title="Grafana dashboards" group-key="dashboards">
<code-block lang="yaml" src="../../src/main/containerlab/configurations/tech-demonstrator/services/provisioning/dashboards/dashboard.yml"/>
</tab>
</tabs>

<deflist type="full" collapsible="true">
<def title="Prometheus Scrape Configuration">
<tabs group="obs-prometheus">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="yaml"><![CDATA[global:
  scrape_interval: 5s
  scrape_timeout: 3s
  evaluation_interval: 5s

scrape_configs:
  - job_name: sdn-mpls-ml-api-kilo
    metrics_path: /metrics
    static_configs:
      - targets:
          - 172.21.121.200:33761
  - job_name: sdn-mpls-odl-csa-india
    metrics_path: /csa/metrics
    static_configs:
      - targets:
          - 172.21.121.100:8181]]></code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>Prometheus scrapes both software subsystems directly: the Python classifier API on <code>/metrics</code> and the Java controller-side application on <code>/csa/metrics</code>. A short scrape interval makes control-cycle and update-lsp metrics visible during demonstrations.</p>
</tab>
</tabs>
</def>
<def title="XRv SNMP Scraping">
<tabs group="obs-snmp-scrape">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="yaml"><![CDATA[- job_name: sdn-mpls-xrv-snmp
  metrics_path: /snmp
  params:
    auth:
      - sma_monitor_v2
    module:
      - if_mib
  static_configs:
    - targets:
        - 172.21.121.11
        - 172.21.121.12
        - 172.21.121.13
        - 172.21.121.14
  relabel_configs:
    - source_labels:
        - __address__
      target_label: __param_target
    - target_label: __address__
      replacement: 172.21.121.203:9116]]></code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>The XRv routers are not scraped directly by Prometheus. Prometheus sends SNMP scrape requests to the SNMP exporter at <code>172.21.121.203:9116</code>, passing the target router address through the <code>__param_target</code> relabeling rule.</p>
</tab>
</tabs>
</def>
<def title="SNMP Authentication">
<tabs group="obs-snmp-auth">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="yaml"><![CDATA[auths:
  sma_monitor_v2:
    version: 2
    community: sdn-mpls-ml-tech-demo-monitor]]></code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>The exporter uses SNMPv2 community authentication. The community string matches the read-only community configured on each XRv router, allowing interface counters and IF-MIB data to be collected.</p>
</tab>
</tabs>
</def>
<def title="Grafana Provisioning">
<tabs group="obs-grafana">
<tab title="Configuration snippet" group-key="snippet">
<code-block lang="yaml"><![CDATA[datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://172.21.121.202:34762
    isDefault: true

providers:
  - name: 'SDN-MPLS-ML Tech Demonstrator Dashboards'
    folder: 'SDN-MPLS-ML Tech Demonstrator'
    type: file
    updateIntervalSeconds: 10
    options:
      path: /etc/grafana/provisioning/dashboards]]></code-block>
</tab>
<tab title="Explanation" group-key="explanation">
<p>Grafana is provisioned with Prometheus as its default datasource and a dashboard provider folder for the demonstrator. This keeps dashboard loading deterministic when the Containerlab service starts.</p>
</tab>
</tabs>
</def>
</deflist>
