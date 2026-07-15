# Test Results

> The following file contains the rest results from running commands upon the raised topology in order to figure out if connection between them has been setup and if OSPF and other base modules have been properly configured

## XR1 Test Results

1. Base Health and Interface State

```plaintext
RP/0/0/CPU0:xr1#show running-config 
Wed Jul 15 00:52:26.113 UTC
Building configuration...
!! IOS XR Configuration 6.6.3.21I
!! Last configuration change at Wed Jul 15 00:19:55 2026 by clab
!
hostname xr1
vrf clab-mgmt
 description Containerlab management VRF (DO NOT DELETE)
 address-family ipv4 unicast
 !
 address-family ipv6 unicast
 !
!
interface Loopback0
 ipv4 address 1.1.1.1 255.255.255.255
!
interface MgmtEth0/0/CPU0/0
 description Containerlab management interface
 vrf clab-mgmt
 ipv4 address 10.0.0.15 255.255.255.0
 ipv6 address 2001:db8::2/64
!
interface GigabitEthernet0/0/0/0
 description TO-XR2
 ipv4 address 10.0.12.1 255.255.255.252
!
interface GigabitEthernet0/0/0/1
 description TO-XR3
 ipv4 address 10.0.13.1 255.255.255.252
!
router static
 vrf clab-mgmt
  address-family ipv4 unicast
   0.0.0.0/0 10.0.0.2
  !
  address-family ipv6 unicast
   ::/0 2001:db8::1
  !
 !
!
router ospf 100
 router-id 1.1.1.1
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
 !
 mpls traffic-eng router-id Loopback0
!         
rsvp      
 interface GigabitEthernet0/0/0/0
  bandwidth 100000
 !        
 interface GigabitEthernet0/0/0/1
  bandwidth 100000
 !        
!         
mpls traffic-eng
 interface GigabitEthernet0/0/0/0
 !        
 interface GigabitEthernet0/0/0/1
 !        
!         
xml agent tty
!         
netconf-yang agent
 ssh      
!         
ssh server v2
ssh server vrf clab-mgmt
ssh server netconf vrf default
ssh server netconf vrf clab-mgmt
end       
          
RP/0/0/CPU0:xr1#
RP/0/0/CPU0:xr1#show interfaces brief 
Wed Jul 15 00:53:27.029 UTC

               Intf       Intf        LineP              Encap  MTU        BW
               Name       State       State               Type (byte)    (Kbps)
--------------------------------------------------------------------------------
                Lo0          up          up           Loopback  1500          0
                Nu0          up          up               Null  1500          0
       Mg0/0/CPU0/0          up          up               ARPA  1514    1000000
          Gi0/0/0/0          up          up               ARPA  1514    1000000
          Gi0/0/0/1          up          up               ARPA  1514    1000000

RP/0/0/CPU0:xr1#
       Mg0/0/CPU0/0          up          up               ARPA  1514    100000000000
          Gi0/0/0/0          up          up               ARPA  1514    100000000000
          Gi0/0/0/1          up          up               ARPA  1514    100000000000

RP/0/0/CPU0:xr1#
RP/0/0/CPU0:xr1#show interfaces description 
Wed Jul 15 00:53:47.657 UTC

Interface          Status      Protocol    Description
--------------------------------------------------------------------------------
Lo0                up          up          
Nu0                up          up          
Mg0/0/CPU0/0       up          up          Containerlab management interface
Gi0/0/0/0          up          up          TO-XR2
Gi0/0/0/1          up          up          TO-XR3

RP/0/0/CPU0:xr1#
RP/0/0/CPU0:xr1#show interfaces GigabitEthernet 0/0/0/0
Wed Jul 15 00:54:04.646 UTC
GigabitEthernet0/0/0/0 is up, line protocol is up 
  Interface state transitions: 1
  Hardware is GigabitEthernet, address is aac1.ab35.29e3 (bia aac1.ab35.29e3)
  Description: TO-XR2
  Internet address is 10.0.12.1/30
  MTU 1514 bytes, BW 1000000 Kbit (Max: 1000000 Kbit)
     reliability 255/255, txload 0/255, rxload 0/255
  Encapsulation ARPA,
  Full-duplex, 1000Mb/s, unknown, link type is force-up
  output flow control is off, input flow control is off
  Carrier delay (up) is 10 msec
  loopback not set,
  Last link flapped 00:34:09
  ARP type ARPA, ARP timeout 04:00:00
  Last input 00:00:00, output 00:00:08
  Last clearing of "show interface" counters never
  5 minute input rate 0 bits/sec, 0 packets/sec
  5 minute output rate 0 bits/sec, 0 packets/sec
     237 packets input, 28016 bytes, 0 total input drops
     0 drops for unrecognized upper-level protocol
     Received 1 broadcast packets, 230 multicast packets
              0 runts, 0 giants, 0 throttles, 0 parity
     0 input errors, 0 CRC, 0 frame, 0 overrun, 0 ignored, 0 abort
     231 packets output, 28494 bytes, 0 total output drops
     Output 1 broadcast packets, 223 multicast packets
     0 output errors, 0 underruns, 0 applique, 0 resets
     0 output buffer failures, 0 output buffers swapped out
     1 carrier transitions


RP/0/0/CPU0:xr1#show interfaces gigabitEthernet 0/0/0/1
Wed Jul 15 00:54:12.695 UTC
GigabitEthernet0/0/0/1 is up, line protocol is up 
  Interface state transitions: 1
  Hardware is GigabitEthernet, address is aac1.abf9.a14f (bia aac1.abf9.a14f)
  Description: TO-XR3
  Internet address is 10.0.13.1/30
  MTU 1514 bytes, BW 1000000 Kbit (Max: 1000000 Kbit)
     reliability 255/255, txload 0/255, rxload 0/255
  Encapsulation ARPA,
  Full-duplex, 1000Mb/s, unknown, link type is force-up
  output flow control is off, input flow control is off
  Carrier delay (up) is 10 msec
  loopback not set,
  Last link flapped 00:34:17
  ARP type ARPA, ARP timeout 04:00:00
  Last input 00:00:06, output 00:00:00
  Last clearing of "show interface" counters never
  5 minute input rate 0 bits/sec, 0 packets/sec
  5 minute output rate 0 bits/sec, 0 packets/sec
     238 packets input, 28566 bytes, 0 total input drops
     0 drops for unrecognized upper-level protocol
     Received 1 broadcast packets, 231 multicast packets
              0 runts, 0 giants, 0 throttles, 0 parity
     0 input errors, 0 CRC, 0 frame, 0 overrun, 0 ignored, 0 abort
     240 packets output, 29188 bytes, 0 total output drops
     Output 1 broadcast packets, 232 multicast packets
     0 output errors, 0 underruns, 0 applique, 0 resets
     0 output buffer failures, 0 output buffers swapped out
     1 carrier transitions


RP/0/0/CPU0:xr1#
RP/0/0/CPU0:xr1#show interfaces Loopback 0
Wed Jul 15 00:54:32.974 UTC
Loopback0 is up, line protocol is up 
  Interface state transitions: 1
  Hardware is Loopback interface(s)
  Internet address is 1.1.1.1/32
  MTU 1500 bytes, BW 0 Kbit
     reliability Unknown, txload Unknown, rxload Unknown
  Encapsulation Loopback,  loopback not set,
  Last link flapped 00:34:34
  Last input Unknown, output Unknown
  Last clearing of "show interface" counters Unknown
  Input/output data rate is disabled.


RP/0/0/CPU0:xr1#
RP/0/0/CPU0:xr1#show ipv4 interface brief 
Wed Jul 15 00:54:55.582 UTC

Interface                      IP-Address      Status          Protocol Vrf-Name
Loopback0                      1.1.1.1         Up              Up       default 
MgmtEth0/0/CPU0/0              10.0.0.15       Up              Up       clab-mgmt
GigabitEthernet0/0/0/0         10.0.12.1       Up              Up       default 
GigabitEthernet0/0/0/1         10.0.13.1       Up              Up       default 
RP/0/0/CPU0:xr1#
RP/0/0/CPU0:xr1#admin show platform 
Wed Jul 15 00:55:12.281 UTC
Node            Type            PLIM            State           Config State
-----------------------------------------------------------------------------
0/0/CPU0        RP(Active)      N/A             IOS XR RUN      PWR,NSHUT,MON
RP/0/0/CPU0:xr1#
```

2. OSPF underlay validation

```plaintext 
RP/0/0/CPU0:xr1#show ospf
Wed Jul 15 00:56:18.127 UTC

 Routing Process "ospf 100" with ID 1.1.1.1
 Role: Primary Active
 NSR (Non-stop routing) is Enabled
 Supports only single TOS(TOS0) routes
 Supports opaque LSA
 Router is not originating router-LSAs with maximum metric
 Initial SPF schedule delay 50 msecs
 Minimum hold time between two consecutive SPFs 200 msecs
 Maximum wait time between two consecutive SPFs 5000 msecs
 Initial LSA throttle delay 50 msecs
 Minimum hold time for LSA throttle 200 msecs
 Maximum wait time for LSA throttle 5000 msecs
 Minimum LSA interval 200 msecs. Minimum LSA arrival 100 msecs
 LSA refresh interval 1800 seconds
 Flood pacing interval 33 msecs. Retransmission pacing interval 66 msecs
 Adjacency stagger enabled; initial (per area): 2, maximum: 64
    Number of neighbors forming: 0, 2 full
 Maximum number of configured interfaces 1024
 Number of external LSA 0. Checksum Sum 00000000
 Number of opaque AS LSA 0. Checksum Sum 00000000
 Number of DCbitless external and opaque AS LSA 0
 Number of DoNotAge external and opaque AS LSA 0
 Number of areas in this router is 1. 1 normal 0 stub 0 nssa
 External flood list length 0
 SNMP trap is enabled
 LSD connected, registered, bound, revision 1
 Segment Routing Global Block default (16000-23999), not allocated
 Segment Routing Local Block, not allocated
 Strict-SPF capability is enabled
    Area BACKBONE(0)
        Number of interfaces in this area is 3
        Area has RRR enabled, topology version 12
        SPF algorithm executed 7 times
        Number of LSA 16.  Checksum Sum 0x08bb9f
        Number of opaque link LSA 0.  Checksum Sum 00000000
        Number of DCbitless LSA 0
        Number of indication LSA 0
        Number of DoNotAge LSA 0
        Flood list length 0
        Number of LFA enabled interfaces 0, LFA revision 0
        Number of Per Prefix LFA enabled interfaces 0
        Number of neighbors forming in staggered mode 0, 2 full
RP/0/0/CPU0:xr1#show ospf neighbor 
Wed Jul 15 00:56:23.696 UTC

* Indicates MADJ interface
# Indicates Neighbor awaiting BFD session up

Neighbors for OSPF 100

Neighbor ID     Pri   State           Dead Time   Address         Interface
2.2.2.2         1     FULL/  -        00:00:37    10.0.12.2       GigabitEthernet0/0/0/0
    Neighbor is up for 00:36:13
3.3.3.3         1     FULL/  -        00:00:34    10.0.13.2       GigabitEthernet0/0/0/1
    Neighbor is up for 00:36:14

Total neighbor count: 2
RP/0/0/CPU0:xr1#show ospf interface brief 
Wed Jul 15 00:56:28.526 UTC

* Indicates MADJ interface, (P) Indicates fast detect hold down state

Interfaces for OSPF 100

Interface          PID   Area            IP Address/Mask    Cost  State Nbrs F/C
Lo0                100   0               1.1.1.1/32         1     LOOP  0/0
Gi0/0/0/0          100   0               10.0.12.1/30       1     P2P   1/1
Gi0/0/0/1          100   0               10.0.13.1/30       1     P2P   1/1
RP/0/0/CPU0:xr1#show  ospf database 
Wed Jul 15 00:56:33.416 UTC


            OSPF Router with ID (1.1.1.1) (Process ID 100)

                Router Link States (Area 0)

Link ID         ADV Router      Age         Seq#       Checksum Link count
1.1.1.1         1.1.1.1         224         0x80000004 0x00a1d6 5
2.2.2.2         2.2.2.2         161         0x80000004 0x0084d0 5
3.3.3.3         3.3.3.3         214         0x80000004 0x00bc76 5
4.4.4.4         4.4.4.4         139         0x80000004 0x00e42b 5

                Type-10 Opaque Link Area Link States (Area 0)

Link ID         ADV Router      Age         Seq#       Checksum Opaque ID
1.0.0.0         1.1.1.1         224         0x80000002 0x0056d2        0
1.0.0.0         2.2.2.2         160         0x80000002 0x005ac6        0
1.0.0.0         3.3.3.3         214         0x80000002 0x005eba        0
1.0.0.0         4.4.4.4         139         0x80000002 0x0062ae        0
1.0.0.3         1.1.1.1         224         0x80000002 0x003c1a        3
1.0.0.3         4.4.4.4         139         0x80000002 0x00f43d        3
1.0.0.4         1.1.1.1         224         0x80000002 0x00c688        4
1.0.0.4         2.2.2.2         160         0x80000002 0x00b99b        4
1.0.0.4         3.3.3.3         214         0x80000002 0x00dd71        4
1.0.0.4         4.4.4.4         139         0x80000002 0x00d345        4
1.0.0.5         2.2.2.2         160         0x80000002 0x00c966        5
1.0.0.5         3.3.3.3         214         0x80000002 0x0042d5        5
RP/0/0/CPU0:xr1#show ospf database router 
Wed Jul 15 00:56:38.595 UTC


            OSPF Router with ID (1.1.1.1) (Process ID 100)

                Router Link States (Area 0)

  LS age: 229
  Options: (No TOS-capability, DC)
  LS Type: Router Links
  Link State ID: 1.1.1.1
  Advertising Router: 1.1.1.1
  LS Seq Number: 80000004
  Checksum: 0xa1d6
  Length: 84
   Number of Links: 5

    Link connected to: a Stub Network
     (Link ID) Network/subnet number: 1.1.1.1
     (Link Data) Network Mask: 255.255.255.255
      Number of TOS metrics: 0
       TOS 0 Metrics: 1

    Link connected to: another Router (point-to-point)
     (Link ID) Neighboring Router ID: 2.2.2.2
     (Link Data) Router Interface address: 10.0.12.1
      Number of TOS metrics: 0
       TOS 0 Metrics: 1

    Link connected to: a Stub Network
     (Link ID) Network/subnet number: 10.0.12.0
     (Link Data) Network Mask: 255.255.255.252
      Number of TOS metrics: 0
       TOS 0 Metrics: 1

    Link connected to: another Router (point-to-point)
     (Link ID) Neighboring Router ID: 3.3.3.3
     (Link Data) Router Interface address: 10.0.13.1
      Number of TOS metrics: 0
       TOS 0 Metrics: 1

    Link connected to: a Stub Network
     (Link ID) Network/subnet number: 10.0.13.0
     (Link Data) Network Mask: 255.255.255.252
      Number of TOS metrics: 0
       TOS 0 Metrics: 1


  Routing Bit Set on this LSA
  LS age: 166
  Options: (No TOS-capability, DC)
  LS Type: Router Links
  Link State ID: 2.2.2.2
  Advertising Router: 2.2.2.2
  LS Seq Number: 80000004
  Checksum: 0x84d0
  Length: 84
   Number of Links: 5
          
    Link connected to: a Stub Network
     (Link ID) Network/subnet number: 2.2.2.2
     (Link Data) Network Mask: 255.255.255.255
      Number of TOS metrics: 0
       TOS 0 Metrics: 1
          
    Link connected to: another Router (point-to-point)
     (Link ID) Neighboring Router ID: 1.1.1.1
     (Link Data) Router Interface address: 10.0.12.2
      Number of TOS metrics: 0
       TOS 0 Metrics: 1
          
    Link connected to: a Stub Network
     (Link ID) Network/subnet number: 10.0.12.0
     (Link Data) Network Mask: 255.255.255.252
      Number of TOS metrics: 0
       TOS 0 Metrics: 1
          
    Link connected to: another Router (point-to-point)
     (Link ID) Neighboring Router ID: 4.4.4.4
     (Link Data) Router Interface address: 10.0.24.1
      Number of TOS metrics: 0
       TOS 0 Metrics: 1
          
    Link connected to: a Stub Network
     (Link ID) Network/subnet number: 10.0.24.0
     (Link Data) Network Mask: 255.255.255.252
      Number of TOS metrics: 0
       TOS 0 Metrics: 1
          
          
  Routing Bit Set on this LSA
  LS age: 219
  Options: (No TOS-capability, DC)
  LS Type: Router Links
  Link State ID: 3.3.3.3
  Advertising Router: 3.3.3.3
  LS Seq Number: 80000004
  Checksum: 0xbc76
  Length: 84
   Number of Links: 5
          
    Link connected to: a Stub Network
     (Link ID) Network/subnet number: 3.3.3.3
     (Link Data) Network Mask: 255.255.255.255
      Number of TOS metrics: 0
       TOS 0 Metrics: 1
          
    Link connected to: another Router (point-to-point)
     (Link ID) Neighboring Router ID: 1.1.1.1
     (Link Data) Router Interface address: 10.0.13.2
      Number of TOS metrics: 0
       TOS 0 Metrics: 1
          
    Link connected to: a Stub Network
     (Link ID) Network/subnet number: 10.0.13.0
     (Link Data) Network Mask: 255.255.255.252
      Number of TOS metrics: 0
       TOS 0 Metrics: 1
          
    Link connected to: another Router (point-to-point)
     (Link ID) Neighboring Router ID: 4.4.4.4
     (Link Data) Router Interface address: 10.0.34.1
      Number of TOS metrics: 0
       TOS 0 Metrics: 1
          
    Link connected to: a Stub Network
     (Link ID) Network/subnet number: 10.0.34.0
     (Link Data) Network Mask: 255.255.255.252
      Number of TOS metrics: 0
       TOS 0 Metrics: 1
          
          
  Routing Bit Set on this LSA
  LS age: 144
  Options: (No TOS-capability, DC)
  LS Type: Router Links
  Link State ID: 4.4.4.4
  Advertising Router: 4.4.4.4
  LS Seq Number: 80000004
  Checksum: 0xe42b
  Length: 84
   Number of Links: 5
          
    Link connected to: a Stub Network
     (Link ID) Network/subnet number: 4.4.4.4
     (Link Data) Network Mask: 255.255.255.255
      Number of TOS metrics: 0
       TOS 0 Metrics: 1
          
    Link connected to: another Router (point-to-point)
     (Link ID) Neighboring Router ID: 2.2.2.2
     (Link Data) Router Interface address: 10.0.24.2
      Number of TOS metrics: 0
       TOS 0 Metrics: 1
          
    Link connected to: a Stub Network
     (Link ID) Network/subnet number: 10.0.24.0
     (Link Data) Network Mask: 255.255.255.252
      Number of TOS metrics: 0
       TOS 0 Metrics: 1
          
    Link connected to: another Router (point-to-point)
     (Link ID) Neighboring Router ID: 3.3.3.3
     (Link Data) Router Interface address: 10.0.34.2
      Number of TOS metrics: 0
       TOS 0 Metrics: 1
          
    Link connected to: a Stub Network
     (Link ID) Network/subnet number: 10.0.34.0
     (Link Data) Network Mask: 255.255.255.252
      Number of TOS metrics: 0
       TOS 0 Metrics: 1
          
          
RP/0/0/CPU0:xr1#
RP/0/0/CPU0:xr1#show route ospf 
Wed Jul 15 00:57:12.873 UTC

O    2.2.2.2/32 [110/2] via 10.0.12.2, 00:37:02, GigabitEthernet0/0/0/0
O    3.3.3.3/32 [110/2] via 10.0.13.2, 00:37:03, GigabitEthernet0/0/0/1
O    4.4.4.4/32 [110/3] via 10.0.13.2, 00:37:02, GigabitEthernet0/0/0/1
                [110/3] via 10.0.12.2, 00:37:02, GigabitEthernet0/0/0/0
O    10.0.24.0/30 [110/2] via 10.0.12.2, 00:37:02, GigabitEthernet0/0/0/0
O    10.0.34.0/30 [110/2] via 10.0.13.2, 00:37:03, GigabitEthernet0/0/0/1
RP/0/0/CPU0:xr1#show route 2.2.2.2
Wed Jul 15 00:57:22.362 UTC

Routing entry for 2.2.2.2/32
  Known via "ospf 100", distance 110, metric 2, type intra area
  Installed Jul 15 00:20:10.655 for 00:37:11
  Routing Descriptor Blocks
    10.0.12.2, from 2.2.2.2, via GigabitEthernet0/0/0/0
      Route metric is 2
  No advertising protos. 
RP/0/0/CPU0:xr1#show route 3.3.3.3
Wed Jul 15 00:57:33.982 UTC

Routing entry for 3.3.3.3/32
  Known via "ospf 100", distance 110, metric 2, type intra area
  Installed Jul 15 00:20:09.405 for 00:37:24
  Routing Descriptor Blocks
    10.0.13.2, from 3.3.3.3, via GigabitEthernet0/0/0/1
      Route metric is 2
  No advertising protos. 
RP/0/0/CPU0:xr1#show route 4.4.4.4
Wed Jul 15 00:57:38.941 UTC

Routing entry for 4.4.4.4/32
  Known via "ospf 100", distance 110, metric 3, type intra area
  Installed Jul 15 00:20:10.655 for 00:37:28
  Routing Descriptor Blocks
    10.0.12.2, from 4.4.4.4, via GigabitEthernet0/0/0/0
      Route metric is 3
    10.0.13.2, from 4.4.4.4, via GigabitEthernet0/0/0/1
      Route metric is 3
  No advertising protos. 
RP/0/0/CPU0:xr1#
```

3. MPLS-TE enablement checks

```plaintext
RP/0/0/CPU0:xr1#show running-config mpls traffic-eng 
Wed Jul 15 00:58:25.108 UTC
mpls traffic-eng
 interface GigabitEthernet0/0/0/0
 !
 interface GigabitEthernet0/0/0/1
 !
!
RP/0/0/CPU0:xr1#show mpls traffic-eng topology brief          
Wed Jul 15 00:59:23.874 UTC
My_System_id: 1.1.1.1 (OSPF 100 area 0)
My_BC_Model_Type: RDM 

Signalling error holddown: 10 sec Global Link Generation 336

IGP Id: 1.1.1.1, MPLS TE Id: 1.1.1.1 Router Node  (OSPF 100 area 0)

  Link[0]:Point-to-Point, Nbr IGP Id:3.3.3.3, Nbr Node Id:2, gen:329
      Frag Id:4, Intf Address:10.0.13.1, Intf Id:0
      Nbr Intf Address:10.0.13.2, Nbr Intf Id:0
      TE Metric:1, IGP Metric:1
      Delay metrics (uSec): Not present
      Loss metrics: Not present
      Bandwidth metrics (kbps): Not present
      Attribute Flags: 0x0
      Ext Admin Group: 
          Length: 256 bits
          Value : 0x::
      Attribute Names: 
      Switching Capability:None, Encoding:unassigned
      BC Model ID:RDM
      Physical BW:1000000 (kbps), Max Reservable BW Global:100000 (kbps)
      Max Reservable BW Sub:0 (kbps)

  Link[1]:Point-to-Point, Nbr IGP Id:2.2.2.2, Nbr Node Id:4, gen:330
      Frag Id:3, Intf Address:10.0.12.1, Intf Id:0
      Nbr Intf Address:10.0.12.2, Nbr Intf Id:0
      TE Metric:1, IGP Metric:1
      Delay metrics (uSec): Not present
      Loss metrics: Not present
      Bandwidth metrics (kbps): Not present
      Attribute Flags: 0x0
      Ext Admin Group: 
          Length: 256 bits
          Value : 0x::
      Attribute Names: 
      Switching Capability:None, Encoding:unassigned
      BC Model ID:RDM
      Physical BW:1000000 (kbps), Max Reservable BW Global:100000 (kbps)
      Max Reservable BW Sub:0 (kbps)

IGP Id: 2.2.2.2, MPLS TE Id: 2.2.2.2 Router Node  (OSPF 100 area 0)

  Link[0]:Point-to-Point, Nbr IGP Id:1.1.1.1, Nbr Node Id:1, gen:335
      Frag Id:4, Intf Address:10.0.12.2, Intf Id:0
      Nbr Intf Address:10.0.12.1, Nbr Intf Id:0
      TE Metric:1, IGP Metric:1
      Delay metrics (uSec): Not present
      Loss metrics: Not present
      Bandwidth metrics (kbps): Not present
      Attribute Flags: 0x0
      Ext Admin Group: 
          Length: 256 bits
          Value : 0x::
      Attribute Names: 
      Switching Capability:None, Encoding:unassigned
      BC Model ID:RDM
      Physical BW:1000000 (kbps), Max Reservable BW Global:100000 (kbps)
      Max Reservable BW Sub:0 (kbps)
          
  Link[1]:Point-to-Point, Nbr IGP Id:4.4.4.4, Nbr Node Id:3, gen:336
      Frag Id:5, Intf Address:10.0.24.1, Intf Id:0
      Nbr Intf Address:10.0.24.2, Nbr Intf Id:0
      TE Metric:1, IGP Metric:1
      Delay metrics (uSec): Not present
      Loss metrics: Not present
      Bandwidth metrics (kbps): Not present
      Attribute Flags: 0x0
      Ext Admin Group: 
          Length: 256 bits
          Value : 0x::
      Attribute Names: 
      Switching Capability:None, Encoding:unassigned
      BC Model ID:RDM
      Physical BW:1000000 (kbps), Max Reservable BW Global:100000 (kbps)
      Max Reservable BW Sub:0 (kbps)
          
IGP Id: 3.3.3.3, MPLS TE Id: 3.3.3.3 Router Node  (OSPF 100 area 0)
          
  Link[0]:Point-to-Point, Nbr IGP Id:4.4.4.4, Nbr Node Id:3, gen:331
      Frag Id:5, Intf Address:10.0.34.1, Intf Id:0
      Nbr Intf Address:10.0.34.2, Nbr Intf Id:0
      TE Metric:1, IGP Metric:1
      Delay metrics (uSec): Not present
      Loss metrics: Not present
      Bandwidth metrics (kbps): Not present
      Attribute Flags: 0x0
      Ext Admin Group: 
          Length: 256 bits
          Value : 0x::
      Attribute Names: 
      Switching Capability:None, Encoding:unassigned
      BC Model ID:RDM
      Physical BW:1000000 (kbps), Max Reservable BW Global:100000 (kbps)
      Max Reservable BW Sub:0 (kbps)
          
  Link[1]:Point-to-Point, Nbr IGP Id:1.1.1.1, Nbr Node Id:1, gen:332
      Frag Id:4, Intf Address:10.0.13.2, Intf Id:0
      Nbr Intf Address:10.0.13.1, Nbr Intf Id:0
      TE Metric:1, IGP Metric:1
      Delay metrics (uSec): Not present
      Loss metrics: Not present
      Bandwidth metrics (kbps): Not present
      Attribute Flags: 0x0
      Ext Admin Group: 
          Length: 256 bits
          Value : 0x::
      Attribute Names: 
      Switching Capability:None, Encoding:unassigned
      BC Model ID:RDM
      Physical BW:1000000 (kbps), Max Reservable BW Global:100000 (kbps)
      Max Reservable BW Sub:0 (kbps)
          
IGP Id: 4.4.4.4, MPLS TE Id: 4.4.4.4 Router Node  (OSPF 100 area 0)
          
  Link[0]:Point-to-Point, Nbr IGP Id:3.3.3.3, Nbr Node Id:2, gen:333
      Frag Id:4, Intf Address:10.0.34.2, Intf Id:0
      Nbr Intf Address:10.0.34.1, Nbr Intf Id:0
      TE Metric:1, IGP Metric:1
      Delay metrics (uSec): Not present
      Loss metrics: Not present
      Bandwidth metrics (kbps): Not present
      Attribute Flags: 0x0
      Ext Admin Group: 
          Length: 256 bits
          Value : 0x::
      Attribute Names: 
      Switching Capability:None, Encoding:unassigned
      BC Model ID:RDM
      Physical BW:1000000 (kbps), Max Reservable BW Global:100000 (kbps)
      Max Reservable BW Sub:0 (kbps)
          
  Link[1]:Point-to-Point, Nbr IGP Id:2.2.2.2, Nbr Node Id:4, gen:334
      Frag Id:3, Intf Address:10.0.24.2, Intf Id:0
      Nbr Intf Address:10.0.24.1, Nbr Intf Id:0
      TE Metric:1, IGP Metric:1
      Delay metrics (uSec): Not present
      Loss metrics: Not present
      Bandwidth metrics (kbps): Not present
      Attribute Flags: 0x0
      Ext Admin Group: 
          Length: 256 bits
          Value : 0x::
      Attribute Names: 
      Switching Capability:None, Encoding:unassigned
      BC Model ID:RDM
      Physical BW:1000000 (kbps), Max Reservable BW Global:100000 (kbps)
      Max Reservable BW Sub:0 (kbps)
RP/0/0/CPU0:xr1#
RP/0/0/CPU0:xr1#show mpls traffic-eng link-management advertisements 
Wed Jul 15 01:00:24.540 UTC

  Flooding Status             : Ready
  Last Flooding               : 2246 seconds ago
  Last Flooding Trigger       : TE Link came Up
  Next Periodic Flooding In   : 95 seconds 
  Diff-Serv TE Mode           : Not enabled 
  Configured Areas            : 1

  IGP Area[1]:: OSPF 100 area 0
      Flooding Protocol   : OSPF
      IGP System ID       : 1.1.1.1
      MPLS TE Router ID   : 1.1.1.1
      Flooded Links       : 2

      Link ID:: 0 (GigabitEthernet0/0/0/0)
          Link IP Address      : 10.0.12.1
          O/G Intf ID          : 3
          Neighbor             : ID 2.2.2.2, IP 10.0.12.2
          TE Metric            : 1
          IGP Metric           : 1
          Physical BW          : 1000000 kbits/sec
          BCID                 : RDM 
          Max Reservable BW    : 100000 kbits/sec
          Res Global BW        : 100000 kbits/sec
          Res Sub BW           : 0 kbits/sec

          Downstream::
                                Global Pool   Sub Pool   
                                -----------   -----------
            Reservable BW[0]:        100000             0  kbits/sec
            Reservable BW[1]:        100000             0  kbits/sec
            Reservable BW[2]:        100000             0  kbits/sec
            Reservable BW[3]:        100000             0  kbits/sec
            Reservable BW[4]:        100000             0  kbits/sec
            Reservable BW[5]:        100000             0  kbits/sec
            Reservable BW[6]:        100000             0  kbits/sec
            Reservable BW[7]:        100000             0  kbits/sec

          Attribute Flags: 0x00000000
          Ext Admin Group: 
              Length: 256 bits
              Value : 0x::
          Attribute Names: 

      Link ID:: 1 (GigabitEthernet0/0/0/1)
          Link IP Address      : 10.0.13.1
          O/G Intf ID          : 4
          Neighbor             : ID 3.3.3.3, IP 10.0.13.2
          TE Metric            : 1
          IGP Metric           : 1
          Physical BW          : 1000000 kbits/sec
          BCID                 : RDM 
          Max Reservable BW    : 100000 kbits/sec
          Res Global BW        : 100000 kbits/sec
          Res Sub BW           : 0 kbits/sec
          
          Downstream::
                                Global Pool   Sub Pool   
                                -----------   -----------
            Reservable BW[0]:        100000             0  kbits/sec
            Reservable BW[1]:        100000             0  kbits/sec
            Reservable BW[2]:        100000             0  kbits/sec
            Reservable BW[3]:        100000             0  kbits/sec
            Reservable BW[4]:        100000             0  kbits/sec
            Reservable BW[5]:        100000             0  kbits/sec
            Reservable BW[6]:        100000             0  kbits/sec
            Reservable BW[7]:        100000             0  kbits/sec
          
          Attribute Flags: 0x00000000
          Ext Admin Group: 
              Length: 256 bits
              Value : 0x::
          Attribute Names: 
          
RP/0/0/CPU0:xr1#
```

4. RSVP-TE checks 

```plaintext 
RP/0/0/CPU0:xr1#show running-config rsvp 
Wed Jul 15 01:02:21.872 UTC
rsvp
 interface GigabitEthernet0/0/0/0
  bandwidth 100000
 !
 interface GigabitEthernet0/0/0/1
  bandwidth 100000
 !
!

RP/0/0/CPU0:xr1#show rsvp interface 
Wed Jul 15 01:02:28.541 UTC

*: RDM: Default I/F B/W % : 75% [default] (max resv/bc0), 0% [default] (bc1)

Interface                 MaxBW (bps)  MaxFlow (bps) Allocated (bps)      MaxSub (bps) 
------------------------- ------------ ------------- -------------------- -------------
GigabitEthernet0/0/0/0           100M           100M             0 (  0%)            0 
GigabitEthernet0/0/0/1           100M           100M             0 (  0%)            0 
RP/0/0/CPU0:xr1#show rsvp interface detail 
Wed Jul 15 01:02:36.841 UTC

*: RDM: Default I/F B/W % : 75% [default] (max resv/bc0), 0% [default] (bc1)

INTERFACE: GigabitEthernet0/0/0/0 (ifh=0x20).
 VRF ID: 0x60000000 (Default).
 BW (bits/sec): Max=100M. MaxFlow=100M.
                Allocated=0 (0%). MaxSub=0.
 Signalling: No DSCP marking. No rate limiting.
 States in: 0. Max missed msgs: 4.
 Max out-of-band missed msgs: 38000.
 Expiry timer: Not running. Refresh interval: 45s.
 Normal Refresh timer: Not running. Out-of-band refresh interval: 0s.
 Summary refresh timer: Not running.
 Refresh reduction local: Enabled. Summary Refresh: Enabled (1472 bytes max).
 Reliable summary refresh: Disabled. Bundling: Enabled. (1500 bytes max).
 Ack hold: 400 ms, Ack max size: 1500 bytes. Retransmit: 2100ms.

INTERFACE: GigabitEthernet0/0/0/1 (ifh=0x40).
 VRF ID: 0x60000000 (Default).
 BW (bits/sec): Max=100M. MaxFlow=100M.
                Allocated=0 (0%). MaxSub=0.
 Signalling: No DSCP marking. No rate limiting.
 States in: 0. Max missed msgs: 4.
 Max out-of-band missed msgs: 38000.
 Expiry timer: Not running. Refresh interval: 45s.
 Normal Refresh timer: Not running. Out-of-band refresh interval: 0s.
 Summary refresh timer: Not running.
 Refresh reduction local: Enabled. Summary Refresh: Enabled (1472 bytes max).
 Reliable summary refresh: Disabled. Bundling: Enabled. (1500 bytes max).
 Ack hold: 400 ms, Ack max size: 1500 bytes. Retransmit: 2100ms.

RP/0/0/CPU0:xr1#show rsvp neighbors 
Wed Jul 15 01:02:40.601 UTC
RP/0/0/CPU0:xr1#show rsvp counters 
% Incomplete command.
RP/0/0/CPU0:xr1#show rsvp counters ?
  chkpt                 checkpoint counters(cisco-support)
  chunks                Chunks counts(cisco-support)
  database              Database counts
  destroy-reasons       Path/Resv destroy reason counts(cisco-support)
  events                Event counts
  handles               Handle database counts(cisco-support)
  issu                  Show ISSU counters
  memory                Memory pool counts(cisco-support)
  messages              Message counts
  mib                   MIB counts(cisco-support)
  notifications-client  Client notification counts(cisco-support)
  nsr                   Show NSR counters
  oor                   OOR (Out Of Resource) counts
  pak                   Packet counters(cisco-support)
  policy                Policy counts(cisco-support)
  prefix-filtering      Prefix filtering counts
RP/0/0/CPU0:xr1#show rsvp counters events
Wed Jul 15 01:03:05.769 UTC
GigabitEthernet0/0/0/0                  GigabitEthernet0/0/0/1                  
 Expired Path states           0         Expired Path states           0        
 Expired Resv states           0         Expired Resv states           0        
 NACKs received                0         NACKs received                0        
All-RSVP-Interfaces                     
 Expired Path states           0        
 Expired Resv states           0        
 NACKs received                0        
RP/0/0/CPU0:xr1#
```

5. NETCONFG management plane checks 

```plaintext 
RP/0/0/CPU0:xr1#show running-config ssh 
Wed Jul 15 01:03:53.546 UTC
ssh server v2
ssh server vrf clab-mgmt
ssh server netconf vrf default
ssh server netconf vrf clab-mgmt

RP/0/0/CPU0:xr1#show running-config netconf-yang 
% Incomplete command.
RP/0/0/CPU0:xr1#show running-config netconf-yang ?
  agent  NETCONF YANG agent configuration commands
RP/0/0/CPU0:xr1#show running-config netconf-yang agent 
Wed Jul 15 01:04:10.214 UTC
netconf-yang agent
 ssh
!

RP/0/0/CPU0:xr1#show running-config netconf-yang ?         
  agent  NETCONF YANG agent configuration commands
RP/0/0/CPU0:xr1#show running-config netconf-yang commands ?
                                                 ^
% Invalid input detected at '^' marker.
RP/0/0/CPU0:xr1#show running-config netconf-yang agent    
Wed Jul 15 01:04:35.793 UTC
netconf-yang agent
 ssh
!

RP/0/0/CPU0:xr1#show net
netconf     netconf-yang  netio  netio-debug
netio-perf  
RP/0/0/CPU0:xr1#show netconf-yang statistics 
Wed Jul 15 01:04:45.092 UTC
Summary statistics
                         # requests|             total time|   min time per request|   max time per request|   avg time per request|
other                             0|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|
close-session                     0|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|
kill-session                      0|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|
get-schema                        0|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|
get                               0|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|
get-config                        0|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|
edit-config                       0|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|
commit                            0|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|
cancel-commit                     0|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|
lock                              0|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|
unlock                            0|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|
discard-changes                   0|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|
validate                          0|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|
xml parse                         0|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|
netconf processor                 0|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|
YFW                               0|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|
pending requests                  0|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|
invoke rpc                        0|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|
copy-config                       0|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|
create-subscription               0|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|       0h  0m  0s   0ms|
RP/0/0/CPU0:xr1#show net
netconf     netconf-yang  netio  netio-debug
netio-perf  
RP/0/0/CPU0:xr1#show netconf
netconf  netconf-yang  
RP/0/0/CPU0:xr1#show netconf-yang clients
Wed Jul 15 01:04:54.971 UTC
No active netconf sessions found.
RP/0/0/CPU0:xr1#show users 
Wed Jul 15 01:04:58.731 UTC
   Line            User                 Service  Conns   Idle        Location
*  vty0            clab                 ssh          0  00:00:00     172.20.30.1
RP/0/0/CPU0:xr1#
```

6. MPLS-TE Tunnels 

```plaintext
RP/0/0/CPU0:xr1#show running-config interface tunnel-t
tunnel-te  tunnel-tp  
RP/0/0/CPU0:xr1#show running-config interface tunnel-te
% Incomplete command.
RP/0/0/CPU0:xr1#show running-config interface tunnel-te*
                                               ^
% Invalid input detected at '^' marker.
RP/0/0/CPU0:xr1#show running-config interface tunnel-te ?
  <0-65535>  
RP/0/0/CPU0:xr1#show running-config interface tunnel-te * 
Wed Jul 15 01:05:34.279 UTC
% No such configuration item(s)

RP/0/0/CPU0:xr1#show running-config interface tunnel-te 0
Wed Jul 15 01:05:37.898 UTC
% No such configuration item(s)

RP/0/0/CPU0:xr1#show mpls traffic-eng tunnels brief 
Wed Jul 15 01:05:50.238 UTC

RP/0/0/CPU0:xr1#show mp
mpa  mpg  mpls  
RP/0/0/CPU0:xr1#show mpls traffic-eng tunnels 
Wed Jul 15 01:05:57.607 UTC

RP/0/0/CPU0:xr1#
```


## XR3 Test Results 

1. Basic Health and Interface State

```plaintext 
show running-config
show ip interface brief
show interfaces description
show interfaces GigabitEthernet0/0/0/0
show interfaces GigabitEthernet0/0/0/1
show interfaces Loopback0
show ipv4 interface brief
admin show platform

```

```plaintext 
# Results
RP/0/0/CPU0:xr3#show running-config
Wed Jul 15 02:21:37.846 UTC
Building configuration...
!! IOS XR Configuration 6.6.3.21I
!! Last configuration change at Wed Jul 15 00:19:55 2026 by clab
!
hostname xr3
vrf clab-mgmt
 description Containerlab management VRF (DO NOT DELETE)
 address-family ipv4 unicast
 !
 address-family ipv6 unicast
 !
!
interface Loopback0
 ipv4 address 3.3.3.3 255.255.255.255
!
interface MgmtEth0/0/CPU0/0
 description Containerlab management interface
 vrf clab-mgmt
 ipv4 address 10.0.0.15 255.255.255.0
 ipv6 address 2001:db8::2/64
!
interface GigabitEthernet0/0/0/0
 description TO-XR1
 ipv4 address 10.0.13.2 255.255.255.252
!
interface GigabitEthernet0/0/0/1
 description TO-XR4
 ipv4 address 10.0.34.1 255.255.255.252
!
router static
 vrf clab-mgmt
  address-family ipv4 unicast
   0.0.0.0/0 10.0.0.2
  !
  address-family ipv6 unicast
   ::/0 2001:db8::1
  !
 !
!
router ospf 100
 router-id 3.3.3.3
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
 !
 mpls traffic-eng router-id Loopback0
RP/0/0/CPU0:xr3#how ip interface brief
                 ^
% Invalid input detected at '^' marker.
RP/0/0/CPU0:xr3#show interfaces description
Wed Jul 15 02:21:37.986 UTC

Interface          Status      Protocol    Description
--------------------------------------------------------------------------------
Lo0                up          up          
Nu0                up          up          
Mg0/0/CPU0/0       up          up          Containerlab management interface
Gi0/0/0/0          up          up          TO-XR1
Gi0/0/0/1          up          up          TO-XR4

RP/0/0/CPU0:xr3#show interfaces GigabitEthernet0/0/0/0
Wed Jul 15 02:21:38.146 UTC
GigabitEthernet0/0/0/0 is up, line protocol is up 
  Interface state transitions: 1
  Hardware is GigabitEthernet, address is aac1.abc0.efaa (bia aac1.abc0.efaa)
  Description: TO-XR1
  Internet address is 10.0.13.2/30
  MTU 1514 bytes, BW 1000000 Kbit (Max: 1000000 Kbit)
     reliability 255/255, txload 0/255, rxload 0/255
  Encapsulation ARPA,
  Full-duplex, 1000Mb/s, unknown, link type is force-up
  output flow control is off, input flow control is off
  Carrier delay (up) is 10 msec
  loopback not set,
  Last link flapped 02:01:43
  ARP type ARPA, ARP timeout 04:00:00
  Last input 00:00:01, output 00:00:01
  Last clearing of "show interface" counters never
  5 minute input rate 0 bits/sec, 0 packets/sec
  5 minute output rate 0 bits/sec, 0 packets/sec
     813 packets input, 97156 bytes, 0 total input drops
     0 drops for unrecognized upper-level protocol
     Received 1 broadcast packets, 805 multicast packets
              0 runts, 0 giants, 0 throttles, 0 parity
     0 input errors, 0 CRC, 0 frame, 0 overrun, 0 ignored, 0 abort
     801 packets output, 95082 bytes, 0 total output drops
     Output 1 broadcast packets, 794 multicast packets
     0 output errors, 0 underruns, 0 applique, 0 resets
     0 output buffer failures, 0 output buffers swapped out
     1 carrier transitions


RP/0/0/CPU0:xr3#show interfaces GigabitEthernet0/0/0/1
Wed Jul 15 02:21:38.326 UTC
GigabitEthernet0/0/0/1 is up, line protocol is up 
  Interface state transitions: 1
  Hardware is GigabitEthernet, address is aac1.ab89.4d32 (bia aac1.ab89.4d32)
  Description: TO-XR4
  Internet address is 10.0.34.1/30
  MTU 1514 bytes, BW 1000000 Kbit (Max: 1000000 Kbit)
     reliability 255/255, txload 0/255, rxload 0/255
  Encapsulation ARPA,
  Full-duplex, 1000Mb/s, unknown, link type is force-up
  output flow control is off, input flow control is off
  Carrier delay (up) is 10 msec
  loopback not set,
  Last link flapped 02:01:43
  ARP type ARPA, ARP timeout 04:00:00
  Last input 00:00:01, output 00:00:01
  Last clearing of "show interface" counters never
  5 minute input rate 0 bits/sec, 0 packets/sec
  5 minute output rate 0 bits/sec, 0 packets/sec
     809 packets input, 96204 bytes, 0 total input drops
     0 drops for unrecognized upper-level protocol
     Received 1 broadcast packets, 802 multicast packets
              0 runts, 0 giants, 0 throttles, 0 parity
     0 input errors, 0 CRC, 0 frame, 0 overrun, 0 ignored, 0 abort
     806 packets output, 96732 bytes, 0 total output drops
     Output 1 broadcast packets, 798 multicast packets
     0 output errors, 0 underruns, 0 applique, 0 resets
     0 output buffer failures, 0 output buffers swapped out
     1 carrier transitions


RP/0/0/CPU0:xr3#show interfaces Loopback0
Wed Jul 15 02:21:38.476 UTC
Loopback0 is up, line protocol is up 
  Interface state transitions: 1
  Hardware is Loopback interface(s)
  Internet address is 3.3.3.3/32
  MTU 1500 bytes, BW 0 Kbit
     reliability Unknown, txload Unknown, rxload Unknown
  Encapsulation Loopback,  loopback not set,
  Last link flapped 02:01:39
  Last input Unknown, output Unknown
  Last clearing of "show interface" counters Unknown
  Input/output data rate is disabled.


RP/0/0/CPU0:xr3#show ipv4 interface brief
Wed Jul 15 02:21:38.606 UTC

Interface                      IP-Address      Status          Protocol Vrf-Name
Loopback0                      3.3.3.3         Up              Up       default 
MgmtEth0/0/CPU0/0              10.0.0.15       Up              Up       clab-mgmt
GigabitEthernet0/0/0/0         10.0.13.2       Up              Up       default 
GigabitEthernet0/0/0/1         10.0.34.1       Up              Up       default 
RP/0/0/CPU0:xr3#admin show platform
Wed Jul 15 02:21:55.465 UTC
Node            Type            PLIM            State           Config State
-----------------------------------------------------------------------------
0/0/CPU0        RP(Active)      N/A             IOS XR RUN      PWR,NSHUT,MON
RP/0/0/CPU0:xr3#
```

2. OSPF Underlay Validation

```
show ospf
show ospf neighbor
show ospf interface
show ospf interface brief
show ospf database
show ospf database router
show route ospf
show route 2.2.2.2
show route 3.3.3.3
show route 4.4.4.4
```

```plaintext 
# Results

RP/0/0/CPU0:xr3#show ospf
Wed Jul 15 02:24:36.554 UTC

 Routing Process "ospf 100" with ID 3.3.3.3
 Role: Primary Active
 NSR (Non-stop routing) is Enabled
 Supports only single TOS(TOS0) routes
 Supports opaque LSA
 Router is not originating router-LSAs with maximum metric
 Initial SPF schedule delay 50 msecs
 Minimum hold time between two consecutive SPFs 200 msecs
 Maximum wait time between two consecutive SPFs 5000 msecs
 Initial LSA throttle delay 50 msecs
 Minimum hold time for LSA throttle 200 msecs
 Maximum wait time for LSA throttle 5000 msecs
 Minimum LSA interval 200 msecs. Minimum LSA arrival 100 msecs
 LSA refresh interval 1800 seconds
 Flood pacing interval 33 msecs. Retransmission pacing interval 66 msecs
 Adjacency stagger enabled; initial (per area): 2, maximum: 64
    Number of neighbors forming: 0, 2 full
 Maximum number of configured interfaces 1024
 Number of external LSA 0. Checksum Sum 00000000
 Number of opaque AS LSA 0. Checksum Sum 00000000
 Number of DCbitless external and opaque AS LSA 0
 Number of DoNotAge external and opaque AS LSA 0
 Number of areas in this router is 1. 1 normal 0 stub 0 nssa
 External flood list length 0
 SNMP trap is enabled
 LSD connected, registered, bound, revision 1
 Segment Routing Global Block default (16000-23999), not allocated
 Segment Routing Local Block, not allocated
 Strict-SPF capability is enabled
    Area BACKBONE(0)
        Number of interfaces in this area is 3
        Area has RRR enabled, topology version 12
        SPF algorithm executed 7 times
        Number of LSA 16.  Checksum Sum 0x08db5f
        Number of opaque link LSA 0.  Checksum Sum 00000000
        Number of DCbitless LSA 0
        Number of indication LSA 0
        Number of DoNotAge LSA 0
        Flood list length 0
        Number of LFA enabled interfaces 0, LFA revision 0
        Number of Per Prefix LFA enabled interfaces 0
        Number of neighbors forming in staggered mode 0, 2 full
RP/0/0/CPU0:xr3#show ospf neighbor
Wed Jul 15 02:24:36.744 UTC

* Indicates MADJ interface
# Indicates Neighbor awaiting BFD session up

Neighbors for OSPF 100

Neighbor ID     Pri   State           Dead Time   Address         Interface
1.1.1.1         1     FULL/  -        00:00:37    10.0.13.1       GigabitEthernet0/0/0/0
    Neighbor is up for 02:04:26
4.4.4.4         1     FULL/  -        00:00:36    10.0.34.2       GigabitEthernet0/0/0/1
    Neighbor is up for 02:04:27

Total neighbor count: 2
RP/0/0/CPU0:xr3#show ospf interface
Wed Jul 15 02:24:36.894 UTC

Interfaces for OSPF 100

Loopback0 is up, line protocol is up 
  Internet Address 3.3.3.3/32, Area 0
  Label stack Primary label 0 Backup label 0 SRTE label 0
  Process ID 100, Router ID 3.3.3.3, Network Type LOOPBACK, Cost: 1
  Loopback interface is treated as a stub Host
GigabitEthernet0/0/0/0 is up, line protocol is up 
  Internet Address 10.0.13.2/30, Area 0
  Label stack Primary label 1 Backup label 3 SRTE label 10
  Process ID 100, Router ID 3.3.3.3, Network Type POINT_TO_POINT, Cost: 1
  Transmit Delay is 1 sec, State POINT_TO_POINT, MTU 1500, MaxPktSz 1500
  Forward reference No, Unnumbered no,  Bandwidth 1000000 
  Timer intervals configured, Hello 10, Dead 40, Wait 40, Retransmit 5
    Hello due in 00:00:05:314
  Index 2/2, flood queue length 0
  Next 0(0)/0(0)
  Last flood scan length is 3, maximum is 3
  Last flood scan time is 0 msec, maximum is 0 msec
  LS Ack List: current length 0, high water mark 7
  Neighbor Count is 1, Adjacent neighbor count is 1
    Adjacent with neighbor 1.1.1.1
  Suppress hello for 0 neighbor(s)
  Multi-area interface Count is 0
GigabitEthernet0/0/0/1 is up, line protocol is up 
  Internet Address 10.0.34.1/30, Area 0
  Label stack Primary label 1 Backup label 3 SRTE label 10
  Process ID 100, Router ID 3.3.3.3, Network Type POINT_TO_POINT, Cost: 1
  Transmit Delay is 1 sec, State POINT_TO_POINT, MTU 1500, MaxPktSz 1500
  Forward reference No, Unnumbered no,  Bandwidth 1000000 
  Timer intervals configured, Hello 10, Dead 40, Wait 40, Retransmit 5
    Hello due in 00:00:05:788
  Index 3/3, flood queue length 0
  Next 0(0)/0(0)
  Last flood scan length is 3, maximum is 4
  Last flood scan time is 0 msec, maximum is 0 msec
  LS Ack List: current length 0, high water mark 6
  Neighbor Count is 1, Adjacent neighbor count is 1
    Adjacent with neighbor 4.4.4.4
  Suppress hello for 0 neighbor(s)
  Multi-area interface Count is 0
RP/0/0/CPU0:xr3#show ospf interface brief
Wed Jul 15 02:24:37.024 UTC

* Indicates MADJ interface, (P) Indicates fast detect hold down state

Interfaces for OSPF 100

Interface          PID   Area            IP Address/Mask    Cost  State Nbrs F/C
Lo0                100   0               3.3.3.3/32         1     LOOP  0/0
Gi0/0/0/0          100   0               10.0.13.2/30       1     P2P   1/1
Gi0/0/0/1          100   0               10.0.34.1/30       1     P2P   1/1
RP/0/0/CPU0:xr3#show ospf database
Wed Jul 15 02:24:37.144 UTC


            OSPF Router with ID (3.3.3.3) (Process ID 100)

                Router Link States (Area 0)

Link ID         ADV Router      Age         Seq#       Checksum Link count
1.1.1.1         1.1.1.1         1453        0x80000006 0x009dd8 5
2.2.2.2         2.2.2.2         1385        0x80000006 0x0080d2 5
3.3.3.3         3.3.3.3         1446        0x80000006 0x00b878 5
4.4.4.4         4.4.4.4         1373        0x80000006 0x00e02d 5

                Type-10 Opaque Link Area Link States (Area 0)

Link ID         ADV Router      Age         Seq#       Checksum Opaque ID
1.0.0.0         1.1.1.1         1453        0x80000004 0x0052d4        0
1.0.0.0         2.2.2.2         1385        0x80000004 0x0056c8        0
1.0.0.0         3.3.3.3         1446        0x80000004 0x005abc        0
1.0.0.0         4.4.4.4         1373        0x80000004 0x005eb0        0
1.0.0.3         1.1.1.1         1453        0x80000004 0x00381c        3
1.0.0.3         4.4.4.4         1373        0x80000004 0x00f03f        3
1.0.0.4         1.1.1.1         1453        0x80000004 0x00c28a        4
1.0.0.4         2.2.2.2         1385        0x80000004 0x00b59d        4
1.0.0.4         3.3.3.3         1446        0x80000004 0x00d973        4
1.0.0.4         4.4.4.4         1373        0x80000004 0x00cf47        4
1.0.0.5         2.2.2.2         1385        0x80000004 0x00c568        5
1.0.0.5         3.3.3.3         1446        0x80000004 0x003ed7        5
RP/0/0/CPU0:xr3#show ospf database router
Wed Jul 15 02:24:37.254 UTC


            OSPF Router with ID (3.3.3.3) (Process ID 100)

                Router Link States (Area 0)

  Routing Bit Set on this LSA
  LS age: 1453
  Options: (No TOS-capability, DC)
  LS Type: Router Links
  Link State ID: 1.1.1.1
  Advertising Router: 1.1.1.1
  LS Seq Number: 80000006
  Checksum: 0x9dd8
  Length: 84
   Number of Links: 5

    Link connected to: a Stub Network
     (Link ID) Network/subnet number: 1.1.1.1
     (Link Data) Network Mask: 255.255.255.255
      Number of TOS metrics: 0
       TOS 0 Metrics: 1

    Link connected to: another Router (point-to-point)
     (Link ID) Neighboring Router ID: 2.2.2.2
     (Link Data) Router Interface address: 10.0.12.1
      Number of TOS metrics: 0
       TOS 0 Metrics: 1

    Link connected to: a Stub Network
     (Link ID) Network/subnet number: 10.0.12.0
     (Link Data) Network Mask: 255.255.255.252
      Number of TOS metrics: 0
       TOS 0 Metrics: 1

    Link connected to: another Router (point-to-point)
     (Link ID) Neighboring Router ID: 3.3.3.3
     (Link Data) Router Interface address: 10.0.13.1
      Number of TOS metrics: 0
       TOS 0 Metrics: 1

    Link connected to: a Stub Network
     (Link ID) Network/subnet number: 10.0.13.0
     (Link Data) Network Mask: 255.255.255.252
      Number of TOS metrics: 0
       TOS 0 Metrics: 1


  Routing Bit Set on this LSA
  LS age: 1385
  Options: (No TOS-capability, DC)
  LS Type: Router Links
  Link State ID: 2.2.2.2
RP/0/0/CPU0:xr3#how route ospf
                 ^
% Invalid input detected at '^' marker.
RP/0/0/CPU0:xr3#show route 2.2.2.2
Wed Jul 15 02:24:37.404 UTC

Routing entry for 2.2.2.2/32
  Known via "ospf 100", distance 110, metric 3, type intra area
  Installed Jul 15 00:20:11.105 for 02:04:26
  Routing Descriptor Blocks
    10.0.13.1, from 2.2.2.2, via GigabitEthernet0/0/0/0
      Route metric is 3
    10.0.34.2, from 2.2.2.2, via GigabitEthernet0/0/0/1
      Route metric is 3
  No advertising protos. 
RP/0/0/CPU0:xr3#show route 3.3.3.3
Wed Jul 15 02:24:37.554 UTC

Routing entry for 3.3.3.3/32
  Known via "local", distance 0, metric 0 (connected)
  Installed Jul 15 00:20:00.056 for 02:04:37
  Routing Descriptor Blocks
    directly connected, via Loopback0
      Route metric is 0
  Redist Advertisers:
    ospf/100 (protoid=2, clientid=19)

RP/0/0/CPU0:xr3#show route 4.4.4.4
Wed Jul 15 02:24:38.344 UTC

Routing entry for 4.4.4.4/32
  Known via "ospf 100", distance 110, metric 2, type intra area
  Installed Jul 15 00:20:09.865 for 02:04:28
  Routing Descriptor Blocks
    10.0.34.2, from 4.4.4.4, via GigabitEthernet0/0/0/1
      Route metric is 2
  No advertising protos. 
RP/0/0/CPU0:xr3#
```

3. MPLS-TE Enablement Checks
   
```
show running-config mpls traffic-eng
show mpls traffic-eng interfaces
show mpls traffic-eng topology
show mpls traffic-eng topology brief
show mpls traffic-eng link-management advertisements
show mpls traffic-eng router-id
```

```plaintext 
RP/0/0/CPU0:xr3#show mpls traffic-eng topology  
Wed Jul 15 02:29:28.284 UTC
My_System_id: 3.3.3.3 (OSPF 100 area 0)
My_BC_Model_Type: RDM 

Signalling error holddown: 10 sec Global Link Generation 1088

IGP Id: 1.1.1.1, MPLS TE Id: 1.1.1.1 Router Node  (OSPF 100 area 0)

  Link[0]:Point-to-Point, Nbr IGP Id:3.3.3.3, Nbr Node Id:1, gen:1085
      Frag Id:4, Intf Address:10.0.13.1, Intf Id:0
      Nbr Intf Address:10.0.13.2, Nbr Intf Id:0
      TE Metric:1, IGP Metric:1
      Delay metrics (uSec): Not present
      Loss metrics: Not present
      Bandwidth metrics (kbps): Not present
      Attribute Flags: 0x0
      Ext Admin Group: 
          Length: 256 bits
          Value : 0x::
      Attribute Names: 
      Switching Capability:None, Encoding:unassigned
      BC Model ID:RDM
      Physical BW:1000000 (kbps), Max Reservable BW Global:100000 (kbps)
      Max Reservable BW Sub:0 (kbps)
                                 Global Pool       Sub Pool
               Total Allocated   Reservable        Reservable
               BW (kbps)         BW (kbps)         BW (kbps)
               ---------------   -----------       ----------
        bw[0]:            0         100000                0
        bw[1]:            0         100000                0
        bw[2]:            0         100000                0
        bw[3]:            0         100000                0
        bw[4]:            0         100000                0
        bw[5]:            0         100000                0
        bw[6]:            0         100000                0
        bw[7]:            0         100000                0

  Link[1]:Point-to-Point, Nbr IGP Id:2.2.2.2, Nbr Node Id:4, gen:1086
      Frag Id:3, Intf Address:10.0.12.1, Intf Id:0
      Nbr Intf Address:10.0.12.2, Nbr Intf Id:0
      TE Metric:1, IGP Metric:1
      Delay metrics (uSec): Not present
      Loss metrics: Not present
      Bandwidth metrics (kbps): Not present
      Attribute Flags: 0x0
      Ext Admin Group: 
          Length: 256 bits
          Value : 0x::
      Attribute Names: 
      Switching Capability:None, Encoding:unassigned
      BC Model ID:RDM
      Physical BW:1000000 (kbps), Max Reservable BW Global:100000 (kbps)
      Max Reservable BW Sub:0 (kbps)
                                 Global Pool       Sub Pool
               Total Allocated   Reservable        Reservable
               BW (kbps)         BW (kbps)         BW (kbps)
               ---------------   -----------       ----------
        bw[0]:            0         100000                0
        bw[1]:            0         100000                0
        bw[2]:            0         100000                0
        bw[3]:            0         100000                0
        bw[4]:            0         100000                0
        bw[5]:            0         100000                0
        bw[6]:            0         100000                0
        bw[7]:            0         100000                0
          
IGP Id: 2.2.2.2, MPLS TE Id: 2.2.2.2 Router Node  (OSPF 100 area 0)
          
  Link[0]:Point-to-Point, Nbr IGP Id:1.1.1.1, Nbr Node Id:3, gen:1087
      Frag Id:4, Intf Address:10.0.12.2, Intf Id:0
      Nbr Intf Address:10.0.12.1, Nbr Intf Id:0
      TE Metric:1, IGP Metric:1
      Delay metrics (uSec): Not present
      Loss metrics: Not present
      Bandwidth metrics (kbps): Not present
      Attribute Flags: 0x0
      Ext Admin Group: 
          Length: 256 bits
          Value : 0x::
      Attribute Names: 
      Switching Capability:None, Encoding:unassigned
      BC Model ID:RDM
      Physical BW:1000000 (kbps), Max Reservable BW Global:100000 (kbps)
      Max Reservable BW Sub:0 (kbps)
                                 Global Pool       Sub Pool
               Total Allocated   Reservable        Reservable
               BW (kbps)         BW (kbps)         BW (kbps)
               ---------------   -----------       ----------
        bw[0]:            0         100000                0
        bw[1]:            0         100000                0
        bw[2]:            0         100000                0
        bw[3]:            0         100000                0
        bw[4]:            0         100000                0
        bw[5]:            0         100000                0
        bw[6]:            0         100000                0
        bw[7]:            0         100000                0
          
  Link[1]:Point-to-Point, Nbr IGP Id:4.4.4.4, Nbr Node Id:2, gen:1088
      Frag Id:5, Intf Address:10.0.24.1, Intf Id:0
      Nbr Intf Address:10.0.24.2, Nbr Intf Id:0
      TE Metric:1, IGP Metric:1
      Delay metrics (uSec): Not present
      Loss metrics: Not present
      Bandwidth metrics (kbps): Not present
      Attribute Flags: 0x0
      Ext Admin Group: 
          Length: 256 bits
          Value : 0x::
      Attribute Names: 
      Switching Capability:None, Encoding:unassigned
      BC Model ID:RDM
      Physical BW:1000000 (kbps), Max Reservable BW Global:100000 (kbps)
      Max Reservable BW Sub:0 (kbps)
                                 Global Pool       Sub Pool
               Total Allocated   Reservable        Reservable
               BW (kbps)         BW (kbps)         BW (kbps)
               ---------------   -----------       ----------
        bw[0]:            0         100000                0
        bw[1]:            0         100000                0
        bw[2]:            0         100000                0
        bw[3]:            0         100000                0
        bw[4]:            0         100000                0
        bw[5]:            0         100000                0
        bw[6]:            0         100000                0
        bw[7]:            0         100000                0
          
IGP Id: 3.3.3.3, MPLS TE Id: 3.3.3.3 Router Node  (OSPF 100 area 0)
          
  Link[0]:Point-to-Point, Nbr IGP Id:4.4.4.4, Nbr Node Id:2, gen:1081
      Frag Id:5, Intf Address:10.0.34.1, Intf Id:0
      Nbr Intf Address:10.0.34.2, Nbr Intf Id:0
      TE Metric:1, IGP Metric:1
      Delay metrics (uSec): Not present
      Loss metrics: Not present
      Bandwidth metrics (kbps): Not present
      Attribute Flags: 0x0
      Ext Admin Group: 
          Length: 256 bits
          Value : 0x::
      Attribute Names: 
      Switching Capability:None, Encoding:unassigned
      BC Model ID:RDM
      Physical BW:1000000 (kbps), Max Reservable BW Global:100000 (kbps)
      Max Reservable BW Sub:0 (kbps)
                                 Global Pool       Sub Pool
               Total Allocated   Reservable        Reservable
               BW (kbps)         BW (kbps)         BW (kbps)
               ---------------   -----------       ----------
        bw[0]:            0         100000                0
        bw[1]:            0         100000                0
        bw[2]:            0         100000                0
        bw[3]:            0         100000                0
        bw[4]:            0         100000                0
        bw[5]:            0         100000                0
        bw[6]:            0         100000                0
        bw[7]:            0         100000                0
          
  Link[1]:Point-to-Point, Nbr IGP Id:1.1.1.1, Nbr Node Id:3, gen:1082
      Frag Id:4, Intf Address:10.0.13.2, Intf Id:0
      Nbr Intf Address:10.0.13.1, Nbr Intf Id:0
      TE Metric:1, IGP Metric:1
      Delay metrics (uSec): Not present
      Loss metrics: Not present
      Bandwidth metrics (kbps): Not present
      Attribute Flags: 0x0
      Ext Admin Group: 
          Length: 256 bits
          Value : 0x::
      Attribute Names: 
      Switching Capability:None, Encoding:unassigned
      BC Model ID:RDM
      Physical BW:1000000 (kbps), Max Reservable BW Global:100000 (kbps)
      Max Reservable BW Sub:0 (kbps)
                                 Global Pool       Sub Pool
               Total Allocated   Reservable        Reservable
               BW (kbps)         BW (kbps)         BW (kbps)
               ---------------   -----------       ----------
        bw[0]:            0         100000                0
        bw[1]:            0         100000                0
        bw[2]:            0         100000                0
        bw[3]:            0         100000                0
        bw[4]:            0         100000                0
        bw[5]:            0         100000                0
        bw[6]:            0         100000                0
        bw[7]:            0         100000                0
          
IGP Id: 4.4.4.4, MPLS TE Id: 4.4.4.4 Router Node  (OSPF 100 area 0)
          
  Link[0]:Point-to-Point, Nbr IGP Id:3.3.3.3, Nbr Node Id:1, gen:1083
      Frag Id:4, Intf Address:10.0.34.2, Intf Id:0
      Nbr Intf Address:10.0.34.1, Nbr Intf Id:0
      TE Metric:1, IGP Metric:1
      Delay metrics (uSec): Not present
      Loss metrics: Not present
      Bandwidth metrics (kbps): Not present
      Attribute Flags: 0x0
      Ext Admin Group: 
          Length: 256 bits
          Value : 0x::
      Attribute Names: 
      Switching Capability:None, Encoding:unassigned
      BC Model ID:RDM
      Physical BW:1000000 (kbps), Max Reservable BW Global:100000 (kbps)
      Max Reservable BW Sub:0 (kbps)
                                 Global Pool       Sub Pool
               Total Allocated   Reservable        Reservable
               BW (kbps)         BW (kbps)         BW (kbps)
               ---------------   -----------       ----------
        bw[0]:            0         100000                0
        bw[1]:            0         100000                0
        bw[2]:            0         100000                0
        bw[3]:            0         100000                0
        bw[4]:            0         100000                0
        bw[5]:            0         100000                0
        bw[6]:            0         100000                0
        bw[7]:            0         100000                0
          
  Link[1]:Point-to-Point, Nbr IGP Id:2.2.2.2, Nbr Node Id:4, gen:1084
      Frag Id:3, Intf Address:10.0.24.2, Intf Id:0
      Nbr Intf Address:10.0.24.1, Nbr Intf Id:0
      TE Metric:1, IGP Metric:1
      Delay metrics (uSec): Not present
      Loss metrics: Not present
      Bandwidth metrics (kbps): Not present
      Attribute Flags: 0x0
      Ext Admin Group: 
          Length: 256 bits
          Value : 0x::
      Attribute Names: 
      Switching Capability:None, Encoding:unassigned
      BC Model ID:RDM
      Physical BW:1000000 (kbps), Max Reservable BW Global:100000 (kbps)
      Max Reservable BW Sub:0 (kbps)
                                 Global Pool       Sub Pool
               Total Allocated   Reservable        Reservable
               BW (kbps)         BW (kbps)         BW (kbps)
               ---------------   -----------       ----------
        bw[0]:            0         100000                0
        bw[1]:            0         100000                0
        bw[2]:            0         100000                0
        bw[3]:            0         100000                0
        bw[4]:            0         100000                0
        bw[5]:            0         100000                0
        bw[6]:            0         100000                0
        bw[7]:            0         100000                0
RP/0/0/CPU0:xr3#

```
4. RSVP-TE Checks
5. NETCONF Management Plane Checks
6. TE Tunnel Readiness Checks