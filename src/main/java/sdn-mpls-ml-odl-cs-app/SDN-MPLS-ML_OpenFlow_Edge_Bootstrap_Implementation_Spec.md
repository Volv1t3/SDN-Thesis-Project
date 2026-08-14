# SDN-MPLS-ML OpenFlow Edge Bootstrap Implementation Specification

## 1. Purpose

Add a Java-side OpenFlow bootstrap subsystem that:

```text
1. Reads OpenDaylight OpenFlow inventory after ODL readiness.
2. Maps OVS management IPs to OpenFlow node IDs.
3. Maps connector names to OpenFlow node-connector IDs and port numbers.
4. Installs deterministic ARP and IPv4 access forwarding flows.
5. Verifies that the flows are present before enabling PacketIn ML workflow.
```

This replaces fragile Containerlab-time `ovs-ofctl add-flow` behavior with controller-owned OpenFlow configuration.

Containerlab should still create the OVS bridge, add ports, set OpenFlow 1.3, set manager, and set controller. Forwarding flows should be owned by the Java app after OpenDaylight has discovered the switches.

---

## 2. Inventory Source Endpoint

The bootstrap service must read:

```http
GET http://172.21.121.100:8182/restconf/data/opendaylight-inventory:nodes?content=nonconfig
```

Using the app’s configured RESTCONF data base URL:

```env
ODL_RESTCONF_DATA_BASE_URL=http://127.0.0.1:8182/restconf/data
```

Internal app endpoint:

```text
GET ${ODL_RESTCONF_DATA_BASE_URL}/opendaylight-inventory:nodes?content=nonconfig
```

The service must parse:

```xml
<nodes>
  <node>
    <id>openflow:...</id>
    <ip-address>...</ip-address>
    <table>
      <id>0</id>
      ...
    </table>
    <node-connector>
      <id>openflow:...:1</id>
      <port-number>1</port-number>
      <name>host-golf</name>
      ...
    </node-connector>
    ...
  </node>
</nodes>
```

The app must not hardcode OpenFlow node IDs because these can change when OVS recreates the datapath.

---

## 3. Required Environment Variables

Add these variables:

```env
# OpenFlow inventory / flow programming
SMA_OPENFLOW_BOOTSTRAP_ENABLED=true
SMA_OPENFLOW_TABLE_ID=0

# Echo / PE1
SMA_OVS_ECHO_MGMT_IP=172.21.121.15
SMA_OVS_ECHO_HOST_PORT_NAME=host-golf
SMA_OVS_ECHO_CORE_PORT_NAME=core-lsr1

# Foxtrot / PE2
SMA_OVS_FOXTROT_MGMT_IP=172.21.121.16
SMA_OVS_FOXTROT_HOST_PORT_NAME=host-hotel
SMA_OVS_FOXTROT_CORE_PORT_NAME=core-lsr4

# Flow priorities
SMA_OPENFLOW_ARP_PRIORITY=300
SMA_OPENFLOW_IPV4_PRIORITY=200

# Optional; do not enable by default while debugging
SMA_OPENFLOW_INSTALL_DEFAULT_DROP=false
SMA_OPENFLOW_DEFAULT_DROP_PRIORITY=0
```

Keep ODL RESTCONF authentication as part of the existing app configuration:

```env
ODL_USERNAME=admin
ODL_PASSWORD=admin
ODL_RESTCONF_DATA_BASE_URL=http://127.0.0.1:8182/restconf/data
```

---

## 4. New Package and Classes

Add package:

```text
com.sma.sdn.openflow
```

Recommended classes:

```text
OpenflowInventoryService
OpenflowSwitchRegistry
OpenflowFlowProvisioningService
OpenflowBootstrapService
OpenflowInventoryXmlDeserializer
OpenflowFlowXmlSerializer
OpenflowBootstrapVerifier
```

Recommended models:

```text
OpenflowSwitchRecord
OpenflowConnectorRecord
OpenflowBootstrapProfile
OpenflowFlowDefinition
OpenflowFlowInstallResult
```

---

## 5. Data Models

### 5.1 `OpenflowSwitchRecord`

```java
public record OpenflowSwitchRecord(
    String logicalName,
    String managementIp,
    String nodeId,
    String encodedNodeId,
    Map<String, OpenflowConnectorRecord> connectorsByName,
    Map<String, OpenflowConnectorRecord> connectorsById,
    Map<Integer, OpenflowConnectorRecord> connectorsByPortNumber
) {}
```

Example logical names:

```text
ECHO
FOXTROT
```

### 5.2 `OpenflowConnectorRecord`

```java
public record OpenflowConnectorRecord(
    String connectorId,
    String name,
    int portNumber,
    String hardwareAddress,
    boolean live,
    boolean linkDown
) {}
```

Example resolved records:

```text
Echo:
  managementIp = 172.21.121.15
  nodeId = openflow:<runtime-id>
  host connector name = host-golf
  core connector name = core-lsr1

Foxtrot:
  managementIp = 172.21.121.16
  nodeId = openflow:134951518551619
  host connector name = host-hotel
  host connector id = openflow:134951518551619:1
  host port number = 1
  core connector name = core-lsr4
  core connector id = openflow:134951518551619:2
  core port number = 2
```

---

## 6. Discovery Algorithm

`OpenflowInventoryService.discoverSwitches()` must:

```text
1. GET opendaylight-inventory:nodes?content=nonconfig.
2. Iterate all <node> elements.
3. Select only OpenFlow nodes whose <id> starts with "openflow:".
4. Read node-level <ip-address>.
5. Match node IP against:
     SMA_OVS_ECHO_MGMT_IP
     SMA_OVS_FOXTROT_MGMT_IP
6. For each matched node, parse all <node-connector> children.
7. Ignore LOCAL connector for forwarding bootstrap.
8. Build connector maps by:
     connector name
     connector ID
     port number
9. Resolve required connector names:
     Echo: host-golf, core-lsr1
     Foxtrot: host-hotel, core-lsr4
10. Fail readiness if any required switch or connector is missing.
```

Required validation per connector:

```text
connector.id is present
connector.name is present
connector.portNumber is present
connector.state.live == true
connector.state.link-down == false
```

Do not accept a connector that is present but down.

---

## 7. Flow Programming Endpoint

Use RESTCONF data `PUT`, not `packet-out`.

The endpoint shape is:

```http
PUT ${ODL_RESTCONF_DATA_BASE_URL}/opendaylight-inventory:nodes/node=${encodedNodeId}/flow-node-inventory:table=0/flow=${flowId}
```

Example with encoded node ID:

```http
PUT http://127.0.0.1:8182/restconf/data/opendaylight-inventory:nodes/node=openflow%3A134951518551619/flow-node-inventory:table=0/flow=sma-bootstrap-foxtrot-arp-host-to-core
```

Important:

```text
nodeId raw:      openflow:134951518551619
nodeId encoded:  openflow%3A134951518551619
```

Only encode the node ID in the URI. Do not encode the connector ID inside the XML body.

---

## 8. Required Bootstrap Flows Per Switch

For each switch, install four flows:

```text
1. ARP host → core
2. ARP core → host
3. IPv4 host → controller copy + core
4. IPv4 core → host
```

The flow behavior must match the validated OVS intent:

```bash
ovs-ofctl -O OpenFlow13 add-flow <bridge> "priority=300,arp,in_port=<host>,actions=output:<core>"
ovs-ofctl -O OpenFlow13 add-flow <bridge> "priority=300,arp,in_port=<core>,actions=output:<host>"
ovs-ofctl -O OpenFlow13 add-flow <bridge> "priority=200,ip,in_port=<host>,actions=CONTROLLER:65535,output:<core>"
ovs-ofctl -O OpenFlow13 add-flow <bridge> "priority=200,ip,in_port=<core>,actions=output:<host>"
```

Do **not** install the default drop rule in the first implementation pass. OpenDaylight already installs an enforced table-miss flow with priority `0` that sends unmatched traffic to the controller.

The default drop rule can be added later behind:

```env
SMA_OPENFLOW_INSTALL_DEFAULT_DROP=true
```

It should not be mandatory because it can conflict with ODL’s table-miss enforcer and make debugging harder.

---

## 9. XML Templates

### 9.1 ARP Host → Core

```xml
<?xml version="1.0" encoding="UTF-8"?>
<flow xmlns="urn:opendaylight:flow:inventory">
    <id>${flowId}</id>
    <table_id>0</table_id>
    <priority>300</priority>
    <cookie>${cookie}</cookie>
    <idle-timeout>0</idle-timeout>
    <hard-timeout>0</hard-timeout>
    <flags>SEND_FLOW_REM</flags>

    <match>
        <in-port>${hostConnectorId}</in-port>
        <ethernet-match>
            <ethernet-type>
                <type>2054</type>
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
                        <output-node-connector>${corePortNumber}</output-node-connector>
                        <max-length>0</max-length>
                    </output-action>
                </action>
            </apply-actions>
        </instruction>
    </instructions>
</flow>
```

### 9.2 ARP Core → Host

```xml
<?xml version="1.0" encoding="UTF-8"?>
<flow xmlns="urn:opendaylight:flow:inventory">
    <id>${flowId}</id>
    <table_id>0</table_id>
    <priority>300</priority>
    <cookie>${cookie}</cookie>
    <idle-timeout>0</idle-timeout>
    <hard-timeout>0</hard-timeout>
    <flags>SEND_FLOW_REM</flags>

    <match>
        <in-port>${coreConnectorId}</in-port>
        <ethernet-match>
            <ethernet-type>
                <type>2054</type>
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
                        <output-node-connector>${hostPortNumber}</output-node-connector>
                        <max-length>0</max-length>
                    </output-action>
                </action>
            </apply-actions>
        </instruction>
    </instructions>
</flow>
```

### 9.3 IPv4 Host → Controller Copy + Core

```xml
<?xml version="1.0" encoding="UTF-8"?>
<flow xmlns="urn:opendaylight:flow:inventory">
    <id>${flowId}</id>
    <table_id>0</table_id>
    <priority>200</priority>
    <cookie>${cookie}</cookie>
    <idle-timeout>0</idle-timeout>
    <hard-timeout>0</hard-timeout>
    <flags>SEND_FLOW_REM</flags>

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
</flow>
```

### 9.4 IPv4 Core → Host

```xml
<?xml version="1.0" encoding="UTF-8"?>
<flow xmlns="urn:opendaylight:flow:inventory">
    <id>${flowId}</id>
    <table_id>0</table_id>
    <priority>200</priority>
    <cookie>${cookie}</cookie>
    <idle-timeout>0</idle-timeout>
    <hard-timeout>0</hard-timeout>
    <flags>SEND_FLOW_REM</flags>

    <match>
        <in-port>${coreConnectorId}</in-port>
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
                        <output-node-connector>${hostPortNumber}</output-node-connector>
                        <max-length>0</max-length>
                    </output-action>
                </action>
            </apply-actions>
        </instruction>
    </instructions>
</flow>
```

Use the full connector ID for `<in-port>` because PacketIn and inventory references use full connector identifiers. Use the numeric `port-number` for `output-node-connector` because it is the most reliable equivalent of the successful `ovs-ofctl output:<port>` behavior. Keep both values in the registry.

---

## 10. Concrete Flow Definitions

### 10.1 Echo / PE1

Resolved from environment:

```text
management IP: 172.21.121.15
host connector name: host-golf
core connector name: core-lsr1
```

Flow IDs:

```text
sma-bootstrap-echo-arp-host-to-core
sma-bootstrap-echo-arp-core-to-host
sma-bootstrap-echo-ipv4-host-to-core
sma-bootstrap-echo-ipv4-core-to-host
```

Mappings after discovery:

```text
hostConnectorId = connector named host-golf
hostPortNumber = port-number of host-golf
coreConnectorId = connector named core-lsr1
corePortNumber = port-number of core-lsr1
```

### 10.2 Foxtrot / PE2

Resolved from environment and inventory:

```text
management IP: 172.21.121.16
node ID: openflow:134951518551619
host connector name: host-hotel
host connector ID: openflow:134951518551619:1
host port number: 1
core connector name: core-lsr4
core connector ID: openflow:134951518551619:2
core port number: 2
```

Flow IDs:

```text
sma-bootstrap-foxtrot-arp-host-to-core
sma-bootstrap-foxtrot-arp-core-to-host
sma-bootstrap-foxtrot-ipv4-host-to-core
sma-bootstrap-foxtrot-ipv4-core-to-host
```

The same logic applies to Echo, but its runtime node ID must be discovered from the current inventory rather than hardcoded.

---

## 11. Service Responsibilities

### 11.1 `OpenflowInventoryService`

Responsible for:

```text
GET inventory
parse nodes
parse node connectors
return discovered switch records
```

It must not install flows.

### 11.2 `OpenflowSwitchRegistry`

Responsible for storing:

```text
logical switch name → OpenflowSwitchRecord
management IP → OpenflowSwitchRecord
node ID → OpenflowSwitchRecord
node connector ID → connector metadata
```

It should expose:

```java
OpenflowSwitchRecord getEcho();
OpenflowSwitchRecord getFoxtrot();
Optional<OpenflowSwitchRecord> findByNodeId(String nodeId);
Optional<OpenflowConnectorRecord> findConnector(String nodeId, String connectorName);
```

### 11.3 `OpenflowFlowProvisioningService`

Responsible for:

```text
build flow XML
PUT flow into config datastore
classify RESTCONF response
retry transient failures
```

It should expose:

```java
OpenflowFlowInstallResult installFlow(OpenflowSwitchRecord switchRecord,
                                      OpenflowFlowDefinition flowDefinition);
```

### 11.4 `OpenflowBootstrapService`

Responsible for orchestration:

```text
discover switches
validate required connectors
install required access flows
verify flow presence
mark OpenFlow bootstrap ready
```

It should expose:

```java
void initialize();
boolean isReady();
```

---

## 12. Startup Sequence Integration

Update the application readiness loop to include OpenFlow bootstrap.

Current desired startup sequence:

```text
1. Provider init starts.
2. PacketReceived listener registers.
3. Background readiness loop starts.
4. RESTCONF data endpoint becomes available.
5. BGP-LS topology discovery succeeds.
6. PCEP topology / delegated LSP discovery succeeds.
7. OpenFlow inventory discovery succeeds.
8. Echo and Foxtrot are resolved by management IP.
9. Required node-connectors are resolved by name.
10. OpenFlow access bootstrap flows are installed.
11. Bootstrap flows are verified.
12. controlPlaneReady = true.
```

PacketIn processing must remain disabled until step 12.

That avoids this failure mode:

```text
PacketIn arrives
→ ARP not forwarded
→ ML workflow receives only eth_type 2054
→ no IPv4 traffic reaches the router
```

---

## 13. Verification Logic

After each flow `PUT`, verify config first:

```http
GET ${ODL_RESTCONF_DATA_BASE_URL}/opendaylight-inventory:nodes/node=${encodedNodeId}/flow-node-inventory:table=0/flow=${flowId}?content=config
```

Then verify operational/nonconfig:

```http
GET ${ODL_RESTCONF_DATA_BASE_URL}/opendaylight-inventory:nodes/node=${encodedNodeId}/flow-node-inventory:table=0?content=nonconfig
```

Readiness should require config verification. Operational verification can retry because OpenFlowPlugin may take a moment to push the flow to the switch.

Recommended behavior:

```text
Config missing after PUT:
  hard failure for this readiness attempt; retry later.

Config present but operational missing:
  warning; retry verification.

Operational present:
  flow installed successfully.
```

---

## 14. RESTCONF Result Handling

Treat these as success:

```text
HTTP 200
HTTP 201
HTTP 204
```

Treat these as retryable:

```text
HTTP 409
HTTP 503
connection refused
connection timeout
inventory temporarily missing node
```

Treat these as configuration errors:

```text
HTTP 400
HTTP 404 on a resolved node/table path
missing connector name
duplicate connector name
connector down
```

---

## 15. Logging Requirements

Log one clear summary per switch:

```text
Resolved OpenFlow switch logicalName=ECHO managementIp=172.21.121.15 nodeId=openflow:...
Resolved connector switch=ECHO name=host-golf connectorId=openflow:...:1 portNumber=1 live=true
Resolved connector switch=ECHO name=core-lsr1 connectorId=openflow:...:2 portNumber=2 live=true
```

Log each installed flow:

```text
Installed OpenFlow bootstrap flow switch=ECHO flowId=sma-bootstrap-echo-arp-host-to-core priority=300 ethType=2054 in=host-golf out=core-lsr1
```

On failure:

```text
OpenFlow bootstrap not ready; will retry: missing connector name=core-lsr1 switch=ECHO
```

Do not mark the whole bundle failed for OpenFlow bootstrap failures. The provider should stay active and retry.

---

## 16. Metrics

Add metrics:

```text
sma_openflow_inventory_discovery_attempts_total
sma_openflow_inventory_discovery_success_total
sma_openflow_inventory_discovery_failure_total

sma_openflow_switch_resolved_total
sma_openflow_connector_resolved_total

sma_openflow_flow_install_attempts_total
sma_openflow_flow_install_success_total
sma_openflow_flow_install_failure_total

sma_openflow_bootstrap_ready
sma_openflow_bootstrap_last_success_timestamp
```

Packet ignore metrics should distinguish:

```text
packet_ignored_control_plane_not_ready
packet_ignored_arp
packet_ignored_unknown_ingress
packet_ignored_unsupported_eth_type
```

---

## 17. Acceptance Criteria

The implementation is accepted when:

```text
1. Java app starts without Blueprint failure.
2. OpenFlow inventory is read from opendaylight-inventory:nodes?content=nonconfig.
3. Echo is discovered by 172.21.121.15.
4. Foxtrot is discovered by 172.21.121.16.
5. Echo resolves host-golf and core-lsr1 connectors by name.
6. Foxtrot resolves host-hotel and core-lsr4 connectors by name.
7. Four bootstrap flows are installed per switch.
8. ARP no longer appears repeatedly as the only PacketIn type.
9. Golf can ARP and ping 192.168.10.1.
10. Hotel can ARP and ping 192.168.20.1.
11. IPv4 host-originated packets still generate PacketIn copies.
12. PacketIn IPv4 traffic shows eth_type=2048 instead of only eth_type=2054.
13. The ML workflow starts only after OpenFlow, BGP-LS, and PCEP readiness are complete.
```

---

## 18. Manual Validation Commands

### 18.1 Read OpenFlow Inventory

```bash
curl -u admin:admin \
  -H "Accept: application/xml" \
  "http://172.21.121.100:8182/restconf/data/opendaylight-inventory:nodes?content=nonconfig"
```

### 18.2 Read Echo Table 0

```bash
curl -u admin:admin \
  -H "Accept: application/xml" \
  "http://172.21.121.100:8182/restconf/data/opendaylight-inventory:nodes/node=openflow%3A47147806706251/flow-node-inventory:table=0?content=all"
```

### 18.3 Read Foxtrot Table 0

```bash
curl -u admin:admin \
  -H "Accept: application/xml" \
  "http://172.21.121.100:8182/restconf/data/opendaylight-inventory:nodes/node=openflow%3A134951518551619/flow-node-inventory:table=0?content=all"
```

### 18.4 Validate OVS Runtime Flow Table

```bash
ovs-ofctl -O OpenFlow13 dump-flows sma-ovs-pe1-echo
ovs-ofctl -O OpenFlow13 dump-flows sma-ovs-pe2-foxtrot
```

Expected flow classes per OVS:

```text
priority=300,arp,in_port=<host> actions=output:<core>
priority=300,arp,in_port=<core> actions=output:<host>
priority=200,ip,in_port=<host> actions=CONTROLLER:65535,output:<core>
priority=200,ip,in_port=<core> actions=output:<host>
```

### 18.5 Validate Access Segments

From Golf:

```bash
ip neigh flush dev eth1
ping 192.168.10.1
ip neigh show
```

From Hotel:

```bash
ip neigh flush dev eth1
ping 192.168.20.1
ip neigh show
```

---

## 19. Implementation Principle

The final ownership model is:

```text
Containerlab creates the OVS bridges.
OpenDaylight owns the OpenFlow connection.
The Java app owns the required access flows.
The OpenFlow inventory endpoint is the authority for node ID and connector mapping.
```

The app should no longer depend on hardcoded datapath IDs or manual `ovs-ofctl` calls. It should derive the runtime OpenFlow node and connector identifiers from ODL inventory, then install the exact ARP and IPv4 flows needed for the demonstrator’s access plane.
