# OpenDaylight OpenFlow RESTCONF Requests

This topic documents the OpenFlow RESTCONF artifacts in `src/main/postman/openflow` and the controller-side flow PUTs generated at bootstrap. The static `configure-echo-s-flow-rules.http` and `configure-echo-s-flow-rules-2.http` examples are intentionally excluded. They target one recorded switch identity and do not represent the dynamic ECHO and FOXTROT programming implemented by the application.

<show-structure for="chapter" depth="2"/>

The model links use OpenFlowPlugin `v0.21.3` commit `aa89c920c95d22454aedf85526e4ef5a49f3215e`, which matches the application's OpenFlowPlugin dependency line. The PCEP links use the BGPCEP and MD-SAL release-source commits cited by the other RESTCONF catalogs. The deployed controller's YANG library remains the authority for its installed schemas and revisions.

## Required RESTCONF Calls for Controller Side Application Initialization {id="openflow-required-controller-initialization"}

The initialization sequence first reads PCEP state required by the controller-side application. These requests belong here because the application uses the resulting topology and PCC data before its broader control-plane work. They are PCEP resources, not OpenFlow inventory requests.

### Query the PCEP Topology {id="openflow-required-query-pcep-topology"}

#### Request {id="openflow-required-query-pcep-topology-request"}

<code-block lang="http" src="../../src/main/postman/pce/query-pcep-topology.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates the RESTCONF client. |
| `Accept` | `application/xml` | Requests XML topology data. |

The stored URL has no query selector. `OdlRestconfDataClient.getPcepTopology` reads the same keyed topology with `content=all`.

#### RESTCONF resource and YANG objects {id="openflow-required-query-pcep-topology-yang"}

The URI selects `network-topology:network-topology/topology[pcep-topology]`. The [shared topology model](https://github.com/opendaylight/mdsal/blob/90214b900e62ddd7175dbfc8df20f217efd44762/model/ietf-topology/src/main/yang/network-topology@2013-10-21.yang) provides the keyed topology list. [network-topology-pcep.yang](https://github.com/opendaylight/bgpcep/blob/50f3ac909751be46354c44619a989e2a940a4e2c/pcep/topology/topology-api/src/main/yang/network-topology-pcep.yang) provides PCEP topology state.

<compare type="left-right" first-title="Request body" second-title="YANG resource shape (abridged)">
<code-block lang="text">No request body</code-block>
<code-block lang="text">container network-topology {
  list topology {                         // key "topology-id"
    leaf topology-id { type topology-id; } // pcep-topology
    container topology-types { ... }
    list node {                           // key "node-id"
      leaf node-id { type node-id; }
      // network-topology-pcep augments this node with PCC and LSP state
    }
  }
}</code-block>
</compare>

| Resource element | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `topology` | Keyed list | `pcep-topology` | Selects controller-maintained PCEP state. |

#### Effect and response {id="openflow-required-query-pcep-topology-effect"}

The GET is read-only and normally returns `200 OK`. The collection has no recorded response. A topology response is controller state, not evidence that a PCC is currently usable.

### Query PCC Node IDs {id="openflow-required-query-pcc-node-ids"}

#### Request {id="openflow-required-query-pcc-node-ids-request"}

<code-block lang="http" src="../../src/main/postman/pce/query-pcc-node-ids.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates the RESTCONF client. |
| `Accept` | `application/xml` | Requests XML topology data. |

The `content=nonconfig` query selects operational state.

#### RESTCONF resource and YANG objects {id="openflow-required-query-pcc-node-ids-yang"}

The request reads PCEP topology nodes from the shared network-topology list. PCC identities are node list keys, while the PCEP provider augments those nodes with its operational state.

<compare type="left-right" first-title="Request body" second-title="YANG resource shape (abridged)">
<code-block lang="text">No request body. Query: content=nonconfig</code-block>
<code-block lang="text">container network-topology {
  list topology {                         // key "topology-id"
    leaf topology-id { type topology-id; } // pcep-topology
    list node {                           // key "node-id"
      leaf node-id { type node-id; }       // PCC identity in this collection
      // network-topology-pcep augmentation: operational PCC/LSP branches
    }
  }
}</code-block>
</compare>

| Resource element | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `node` | Keyed topology list | Returned by the controller | Enumerates PCEP provider nodes. |
| `content` | RFC 8040 selector | `nonconfig` | Excludes configured data. |

#### Effect and response {id="openflow-required-query-pcc-node-ids-effect"}

The GET normally returns `200 OK`. No response is recorded in the source collection.

## Controller-Side OpenFlow Flow Injection {id="openflow-controller-flow-injection"}

`OpenflowBootstrapService` discovers ECHO and FOXTROT in operational inventory, resolves their current node and connector identities, then creates four rules for each switch. `OpenflowFlowProvisioningService` writes each rule with RESTCONF PUT and retries `409` and `503` responses. It accepts `200`, `201`, and `204` as successful writes. `OpenflowBootstrapVerifier` then reads each configured flow and polls the operational table before bootstrap readiness is set.

### Install Generated ARP and IPv4 Access Flows {id="openflow-generated-bootstrap-flows"}

This is a source-derived request family, not a saved Postman request. It replaces the excluded static Echo examples with the request pattern that the application actually sends for both discovered switches.

#### Request {id="openflow-generated-bootstrap-flows-request"}

<code-block lang="http">
PUT ${ODL_RESTCONF_DATA_BASE_URL}/opendaylight-inventory:nodes/node=${encodedNodeId}/flow-node-inventory:table=${tableId}/flow=${encodedFlowId}
Authorization: Basic {{odl_basic_auth}}
Accept: application/xml
Content-Type: application/xml
</code-block>

`encodedNodeId` is the discovered OpenFlow node key, and `encodedFlowId` is the percent-encoded deterministic flow ID. `OpenflowBootstrapService` generates the following four rules per switch:

| Flow ID suffix | EtherType | Ingress to egress | Controller copy |
|---|---:|---|---|
| `arp-host-to-core` | `2054` | host port → core port | No |
| `arp-core-to-host` | `2054` | core port → host port | No |
| `ipv4-host-to-core` | `2048` | host port → core port | Yes |
| `ipv4-core-to-host` | `2048` | core port → host port | No |

The complete flow ID is `sma-bootstrap-${logicalNameLowercase}-${suffix}`. Each discovered switch receives its own base cookie plus offsets one through four.

#### RESTCONF resource and YANG objects {id="openflow-generated-bootstrap-flows-yang"}

The URI selects `opendaylight-inventory:nodes/node` and the `flow-node-inventory:table` and `flow` list entries. [opendaylight-inventory.yang](https://github.com/opendaylight/openflowplugin/blob/aa89c920c95d22454aedf85526e4ef5a49f3215e/model/model-inventory/src/main/yang/opendaylight-inventory.yang) defines `nodes/node`, keyed by `id`. [flow-node-inventory.yang](https://github.com/opendaylight/openflowplugin/blob/aa89c920c95d22454aedf85526e4ef5a49f3215e/model/model-flow-service/src/main/yang/flow-node-inventory.yang) augments a node with `table`, keyed by `id`, and nested `flow`, also keyed by `id`. [opendaylight-flow-types.yang](https://github.com/opendaylight/openflowplugin/blob/aa89c920c95d22454aedf85526e4ef5a49f3215e/model/model-flow-base/src/main/yang/opendaylight-flow-types.yang) supplies flow identity, priority, cookie, timeouts, flags, match, and instructions. [opendaylight-match-types.yang](https://github.com/opendaylight/openflowplugin/blob/aa89c920c95d22454aedf85526e4ef5a49f3215e/model/model-flow-base/src/main/yang/opendaylight-match-types.yang) defines `in-port` and Ethernet matching. [opendaylight-action-types.yang](https://github.com/opendaylight/openflowplugin/blob/aa89c920c95d22454aedf85526e4ef5a49f3215e/model/model-flow-base/src/main/yang/opendaylight-action-types.yang) defines ordered output actions.

<compare type="top-bottom" first-title="Generated XML payload (abridged)" second-title="YANG model composition (abridged)">
<code-block lang="xml">&lt;flow xmlns="urn:opendaylight:flow:inventory"&gt;
  &lt;id&gt;sma-bootstrap-echo-ipv4-host-to-core&lt;/id&gt;
  &lt;table_id&gt;0&lt;/table_id&gt;&lt;priority&gt;${priority}&lt;/priority&gt;&lt;cookie&gt;${cookie}&lt;/cookie&gt;
  &lt;idle-timeout&gt;0&lt;/idle-timeout&gt;&lt;hard-timeout&gt;0&lt;/hard-timeout&gt;&lt;flags&gt;SEND_FLOW_REM&lt;/flags&gt;
  &lt;match&gt;&lt;in-port&gt;${inputConnectorId}&lt;/in-port&gt;&lt;ethernet-match&gt;&lt;ethernet-type&gt;&lt;type&gt;2048&lt;/type&gt;&lt;/ethernet-type&gt;&lt;/ethernet-match&gt;&lt;/match&gt;
  &lt;instructions&gt;&lt;instruction&gt;&lt;order&gt;0&lt;/order&gt;&lt;apply-actions&gt;
    &lt;action&gt;&lt;order&gt;0&lt;/order&gt;&lt;output-action&gt;&lt;output-node-connector&gt;CONTROLLER&lt;/output-node-connector&gt;&lt;max-length&gt;65535&lt;/max-length&gt;&lt;/output-action&gt;&lt;/action&gt;
    &lt;action&gt;&lt;order&gt;1&lt;/order&gt;&lt;output-action&gt;&lt;output-node-connector&gt;${outputPort}&lt;/output-node-connector&gt;&lt;max-length&gt;0&lt;/max-length&gt;&lt;/output-action&gt;&lt;/action&gt;
  &lt;/apply-actions&gt;&lt;/instruction&gt;&lt;/instructions&gt;
&lt;/flow&gt;</code-block>
<code-block lang="text">container opendaylight-inventory:nodes {
  list node {                              // key "id"
    leaf id { type inet:uri; }
    // flow-node-inventory augments /nodes/node
    list flow-node-inventory:table {       // key "id"
      leaf id { type uint8; }
      list flow {                          // key "id"
        leaf id { type flow-id; }
        leaf table_id { type uint8; }
        leaf priority { type uint16; }
        leaf cookie { type uint64; }
        leaf idle-timeout { type uint16; }
        leaf hard-timeout { type uint16; }
        leaf-list flags { type flow-mod-flags; }
        container match {
          leaf in-port { type inv:node-connector-id; }
          container ethernet-match {
            container ethernet-type { leaf type { type uint16; } }
          }
        }
        container instructions {
          list instruction {               // key "order"
            leaf order { type uint32; }
            choice instruction {
              case apply-actions { container apply-actions {
                list action {              // key "order"
                  leaf order { type uint32; }
                  container output-action {
                    leaf output-node-connector { type inet:uri; }
                    leaf max-length { type uint16; }
                  }
                }
              }}
            }
          }
        }
      }
    }
  }
}</code-block>
</compare>

| Payload object | YANG role | Runtime value | Meaning |
|---|---|---|---|
| `node` | Inventory node list key | Discovered ECHO or FOXTROT node | Selects the actual switch. |
| `flow` | Flow list entry | Generated deterministic ID | Selects one configured rule. |
| `match/in-port` | Ingress connector match | Discovered connector ID | Matches the full connector identifier. |
| `ethernet-type/type` | Ethernet type match | `2054` or `2048` | Selects ARP or IPv4. |
| `output-node-connector` | Ordered output action | `CONTROLLER` or numeric port | Copies only host-to-core IPv4 traffic, then forwards to the egress port. |

#### Effect and response {id="openflow-generated-bootstrap-flows-effect"}

Each PUT creates or replaces one configured flow. A successful response does not prove that the flow reached the switch. The application verifies configured flow IDs first and then waits until all four IDs appear in the operational table. No Postman response applies to these generated requests.

## Complete RESTCONF Calls {id="openflow-complete-restconf-calls"}

The remaining maintained OpenFlow request files inspect inventory, individual switch state, topology, and a selected flow table. The two static Echo flow PUT examples are excluded from this catalog because their hard-coded identity does not match controller-side bootstrap behavior.

### Read OpenFlow Node Inventory {id="openflow-read-node-inventory"}

#### Request {id="openflow-read-node-inventory-request"}

<code-block lang="http" src="../../src/main/postman/openflow/read-openflow-node-inventory.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates the RESTCONF client. |
| `Accept` | `application/xml` | Requests XML inventory data. |

The `content=nonconfig` query requests operational inventory. `OpenflowInventoryService` uses this same GET for ECHO and FOXTROT discovery.

#### RESTCONF resource and YANG objects {id="openflow-read-node-inventory-yang"}

The resource is the `opendaylight-inventory:nodes` root from [opendaylight-inventory.yang](https://github.com/opendaylight/openflowplugin/blob/aa89c920c95d22454aedf85526e4ef5a49f3215e/model/model-inventory/src/main/yang/opendaylight-inventory.yang). The flow plugin augments inventory nodes with tables and connectors.

<compare type="left-right" first-title="Request body" second-title="YANG resource shape (abridged)">
<code-block lang="text">No request body. Query: content=nonconfig</code-block>
<code-block lang="text">container opendaylight-inventory:nodes {
  list node {                              // key "id"
    leaf id { type inet:uri; }
    list node-connector {                  // key "id"; inventory augmentation
      leaf id { type node-connector-id; }
      // operational connector state omitted
    }
    list flow-node-inventory:table {       // augmentation; key "id"
      leaf id { type uint8; }
      list flow {                          // key "id"
        leaf id { type flow-id; }
        // configured and operational flow fields omitted
      }
    }
  }
}</code-block>
</compare>

| Resource element | YANG role | Value | Meaning |
|---|---|---|---|
| `nodes` | Inventory root | — | Contains controller-known OpenFlow nodes. |
| `node` | Keyed list | Returned by controller | Supplies dynamic switch IDs. |

#### Effect and response {id="openflow-read-node-inventory-effect"}

The GET normally returns `200 OK` with operational inventory. No response is recorded.

### Read ECHO OpenFlow Switch State {id="openflow-read-echo-state"}

#### Request {id="openflow-read-echo-state-request"}

<code-block lang="http" src="../../src/main/postman/openflow/read-openflow-ovs-switch-echo-state.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates the RESTCONF client. |
| `Accept` | `application/xml` | Requests XML state. |

#### RESTCONF resource and YANG objects {id="openflow-read-echo-state-yang"}

The URI selects the recorded `openflow:47147806706251` inventory node. That identifier is an example from the collection and is not the dynamic identity used by bootstrap.

<compare type="left-right" first-title="Request body" second-title="YANG resource shape (abridged)">
<code-block lang="text">No request body. Query: content=nonconfig</code-block>
<code-block lang="text">container opendaylight-inventory:nodes {
  list node {                              // key "id"
    leaf id { type inet:uri; }             // openflow:47147806706251
    list node-connector {                  // key "id"
      leaf id { type node-connector-id; }
      // operational port and statistics state omitted
    }
    // inventory and flow-node-inventory operational augmentations omitted
  }
}</code-block>
</compare>

#### Effect and response {id="openflow-read-echo-state-effect"}

The GET normally returns `200 OK` when the inventory node exists. No response is recorded.

### Read FOXTROT OpenFlow Switch State {id="openflow-read-foxtrot-state"}

#### Request {id="openflow-read-foxtrot-state-request"}

<code-block lang="http" src="../../src/main/postman/openflow/read-openflow-ovs-switch-foxtrot-state.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates the RESTCONF client. |
| `Accept` | `application/xml` | Requests XML state. |

#### RESTCONF resource and YANG objects {id="openflow-read-foxtrot-state-yang"}

The URI selects the recorded `openflow:125577889442889` inventory node. Bootstrap resolves FOXTROT dynamically instead of relying on this stored key.

<compare type="left-right" first-title="Request body" second-title="YANG resource shape (abridged)">
<code-block lang="text">No request body. Query: content=nonconfig</code-block>
<code-block lang="text">container opendaylight-inventory:nodes {
  list node {                              // key "id"
    leaf id { type inet:uri; }             // openflow:125577889442889
    list node-connector {                  // key "id"
      leaf id { type node-connector-id; }
      // operational port and statistics state omitted
    }
    // inventory and flow-node-inventory operational augmentations omitted
  }
}</code-block>
</compare>

#### Effect and response {id="openflow-read-foxtrot-state-effect"}

The GET normally returns `200 OK` when the inventory node exists. No response is recorded.

### Read OpenFlow Topology Information {id="openflow-read-topology-information"}

#### Request {id="openflow-read-topology-information-request"}

<code-block lang="http" src="../../src/main/postman/openflow/read-openflow-topology-information.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates the RESTCONF client. |
| `Accept` | `application/xml` | Requests XML topology data. |

#### RESTCONF resource and YANG objects {id="openflow-read-topology-information-yang"}

The GET selects topology `flow:1` with both configuration and operational data. The shared [network-topology model](https://github.com/opendaylight/mdsal/blob/90214b900e62ddd7175dbfc8df20f217efd44762/model/ietf-topology/src/main/yang/network-topology@2013-10-21.yang) defines the keyed topology resource. The producing OpenFlow topology provider and its installed schema revision are not established by this collection.

<compare type="left-right" first-title="Request body" second-title="YANG resource shape (abridged)">
<code-block lang="text">No request body. Query: content=all</code-block>
<code-block lang="text">container network-topology {
  list topology {                          // key "topology-id"
    leaf topology-id { type topology-id; } // flow:1
    container topology-types { ... }
    list node {                            // key "node-id"
      leaf node-id { type node-id; }
      list termination-point {             // key "tp-id"
        leaf tp-id { type tp-id; }
      }
    }
    list link {                            // key "link-id"
      leaf link-id { type link-id; }
      container source { leaf source-node { type node-ref; } }
      container destination { leaf dest-node { type node-ref; } }
    }
  }
}</code-block>
</compare>

#### Effect and response {id="openflow-read-topology-information-effect"}

The GET is read-only and normally returns `200 OK`. No response is recorded.

### Read Table-Miss and Flow Table State {id="openflow-read-table-state"}

#### Request {id="openflow-read-table-state-request"}

<code-block lang="http" src="../../src/main/postman/openflow/read-table-miss-configured-behavior-in-openflow.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates the RESTCONF client. |
| `Accept` | `application/xml` | Requests XML table data. |

#### RESTCONF resource and YANG objects {id="openflow-read-table-state-yang"}

The path selects table `0` below one recorded inventory node. `flow-node-inventory:table` is the flow-plugin augmentation of an inventory node. The controller-side verifier uses a related table GET with `content=nonconfig` after it has written generated bootstrap flows.

<compare type="left-right" first-title="Request body" second-title="YANG resource shape (abridged)">
<code-block lang="text">No request body. Query: content=all</code-block>
<code-block lang="text">container opendaylight-inventory:nodes {
  list node {                              // key "id"
    leaf id { type inet:uri; }
    list flow-node-inventory:table {       // augmentation; key "id"
      leaf id { type uint8; }              // 0
      list flow {                          // key "id"
        leaf id { type flow-id; }
        leaf table_id { type uint8; }
        container match { ... }
        container instructions { ... }
        // counters and remaining flow state omitted
      }
    }
  }
}</code-block>
</compare>

#### Effect and response {id="openflow-read-table-state-effect"}

The GET normally returns `200 OK` with configured and operational table data. No response is recorded.
