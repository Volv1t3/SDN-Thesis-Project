# OpenDaylight PCEP RESTCONF Requests

This topic documents the PCEP-related RESTCONF requests in `src/main/postman/pce`. The requests use both the legacy-looking `/rests` base path and the `/restconf` base path recorded in the collection. They are preserved as source artifacts and do not demonstrate that either endpoint is currently enabled.

<show-structure for="chapter" depth="2"/>

The model references below use a [BGPCEP `0.23.x` source snapshot](https://github.com/opendaylight/bgpcep/tree/db82b92c1fde7db09a4ad491cb13defa3b12e002). That snapshot supports model research for OpenDaylight Vanadium `0.23.1`, but it does not establish the exact YANG revisions advertised by a running controller. Recorded responses are identified separately from protocol-expected responses.

## Required RESTCONF Calls for the Controller-Side-Application Usage and Controller Validation {id="pcep-required-csa-calls"}

The controller-side application first reads PCEP topology state, then requests a constrained path, and finally updates the delegated LSP. The stored requests select the same resources as the application. The application adds `content=all` to its topology read and builds the two operation bodies from current configuration and topology data.

### Query the PCEP Topology {id="pcep-query-topology"}

The topology is the controller-side application's source for PCC and delegated-LSP discovery.

#### Request {id="pcep-query-topology-request"}

<code-block lang="http" src="../../src/main/postman/pce/query-pcep-topology.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates the RESTCONF client. |
| `Accept` | `application/xml` | Requests an XML representation. |

The stored URL has no query parameters. The application requests the same topology list instance with `?content=all` to include configured and operational data.

#### RESTCONF resource and YANG objects {id="pcep-query-topology-yang"}

The resource selects `network-topology:network-topology` → `topology[topology-id='pcep-topology']`. The [shared network-topology model](https://github.com/opendaylight/mdsal/blob/a341bc343a9f319687fbee228032ffac68c3da67/model/ietf-topology/src/main/yang/network-topology@2013-10-21.yang) defines the topology list and its key. The [PCEP topology model](https://github.com/opendaylight/bgpcep/blob/db82b92c1fde7db09a4ad491cb13defa3b12e002/pcep/topology/topology-api/src/main/yang/network-topology-pcep.yang) augments a PCEP topology with provider-specific state.

<compare type="top-bottom" first-title="Request body" second-title="YANG resource composition (abridged)">
<code-block lang="text">No request body</code-block>
<code-block lang="text">network-topology:network-topology
  list topology [key: topology-id]
    leaf topology-id: topology-id  // pcep-topology
    container topology-types
      // topology type markers are augment targets
    network-topology-pcep:topology-pcep  // augmentation
      container session-config
        // PCEP provider configuration omitted
      leaf ted-name: string
    list node [key: node-id]
      leaf node-id: node-id
      // PCEP provider augments the node with PCC and LSP state</code-block>
</compare>

#### Effect and response {id="pcep-query-topology-effect"}

The GET retrieves the selected topology representation. A successful read is normally `200 OK`. The source collection has no recorded response. A response is a controller view of topology data and does not independently prove PCC connectivity.

### Compute a Constrained Path from LSR1 to LSR4 {id="pcep-compute-lsr1-lsr4-path"}

This request is the static collection example for the path-computation operation invoked by the controller-side application after topology discovery.

#### Request {id="pcep-compute-lsr1-lsr4-path-request"}

<code-block lang="http" src="../../src/main/postman/pce/request-pcep-computed-path-from-lsr1-to-lsr4-test.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates the operation request. |
| `Accept` | `application/xml` twice | The duplicate header is preserved from the source. |
| `Content-Type` | `application/xml` | Declares the XML input payload. |

No query parameters are present.

#### RESTCONF resource and YANG objects {id="pcep-compute-lsr1-lsr4-path-yang"}

`path-computation:get-constrained-path` is an RPC under `/operations`. It computes a path against the named graph and does not create an LSP. [path-computation](https://github.com/opendaylight/bgpcep/blob/db82b92c1fde7db09a4ad491cb13defa3b12e002/algo/algo-api/src/main/yang/path-computation.yang) defines the RPC, graph vertex identifiers, algorithm, and constraints. The imported [graph model](https://github.com/opendaylight/bgpcep/blob/db82b92c1fde7db09a4ad491cb13defa3b12e002/graph/graph-api/src/main/yang/graph.yang) defines bandwidth as decimal bytes per second.

<compare type="top-bottom" first-title="Request payload (abridged)" second-title="YANG model composition (abridged)">
<code-block lang="xml">&lt;input xmlns="urn:opendaylight:params:xml:ns:yang:path:computation"&gt;
  &lt;graph-name&gt;ted://sma-bgp-linkstate-topology&lt;/graph-name&gt;
  &lt;source&gt;185273099&lt;/source&gt;
  &lt;destination&gt;235802126&lt;/destination&gt;
  &lt;constraints&gt;
    &lt;address-family&gt;ipv4&lt;/address-family&gt;
    &lt;bandwidth&gt;5000000&lt;/bandwidth&gt;
    &lt;class-type&gt;0&lt;/class-type&gt;
  &lt;/constraints&gt;
  &lt;algorithm&gt;cspf&lt;/algorithm&gt;
&lt;/input&gt;
</code-block>
<code-block lang="text">rpc get-constrained-path {
  input {
    leaf graph-name { type string; mandatory true; }
    leaf source { type uint64; mandatory true; }
    leaf destination { type uint64; mandatory true; }
    container constraints {
      leaf address-family { type address-family; }
      leaf bandwidth { type graph:decimal-bandwidth; }
      leaf class-type { type uint8 { range "0..7"; } }
    }
    leaf algorithm { type algorithm; }
  }
  output { /* computation status and path descriptions */ }
}</code-block>
</compare>

The numeric endpoint values are graph vertex identifiers. They are not literal router addresses. The collection's `5000000` bandwidth is 5 MB/s.

#### Effect and response {id="pcep-compute-lsr1-lsr4-path-effect"}

An accepted operation normally returns `200 OK` with computation output. A completed computation is not an instruction to a PCC and does not provision a tunnel. No response is recorded for this request.

### Update the Delegated LSP after Path Computation {id="pcep-update-delegated-lsp"}

This RPC is the collection example for the controller-side application's delegated-LSP update step.

#### Request {id="pcep-update-delegated-lsp-request"}

<code-block lang="http" src="../../src/main/postman/pce/configurte-lsp-tunnel-after-the-fact.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates the operation request. |
| `Accept` | `application/xml` | Requests XML output. |
| `Content-Type` | `application/xml` | Declares the XML input payload. |

#### RESTCONF resource and YANG objects {id="pcep-update-delegated-lsp-yang"}

The `network-topology-pcep:update-lsp` RPC takes the target topology reference, PCC node reference, LSP name, and PCEP arguments. The [PCEP topology RPC model](https://github.com/opendaylight/bgpcep/blob/db82b92c1fde7db09a4ad491cb13defa3b12e002/pcep/topology/topology-api/src/main/yang/network-topology-pcep.yang) defines the operation. [ODL topology references](https://github.com/opendaylight/bgpcep/blob/db82b92c1fde7db09a4ad491cb13defa3b12e002/topology/api/src/main/yang/odl-network-topology.yang) defines `network-topology-ref`. [PCEP types](https://github.com/opendaylight/bgpcep/blob/db82b92c1fde7db09a4ad491cb13defa3b12e002/pcep/api/src/main/yang/pcep-types.yang), [RSVP](https://github.com/opendaylight/bgpcep/blob/db82b92c1fde7db09a4ad491cb13defa3b12e002/rsvp/api/src/main/yang/rsvp.yang), and the [stateful PCEP augmentation](https://github.com/opendaylight/bgpcep/blob/db82b92c1fde7db09a4ad491cb13defa3b12e002/pcep/ietf-stateful/src/main/yang/odl-pcep-ietf-stateful.yang) provide the path, ERO, and LSP objects.

<compare type="top-bottom" first-title="Request payload (abridged)" second-title="YANG model composition (abridged)">
<code-block lang="xml">&lt;input xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep"&gt;
  &lt;node&gt;pcc://10.100.40.1&lt;/node&gt;
  &lt;name&gt;sma-lsr4-lsr1-delegated&lt;/name&gt;
  &lt;arguments&gt;... ERO, bandwidth, LSP, and path setup ...&lt;/arguments&gt;
  &lt;network-topology-ref&gt;...pcep-topology...&lt;/network-topology-ref&gt;
&lt;/input&gt;</code-block>
<code-block lang="text">rpc update-lsp {
  input {
    uses ont:network-topology-reference;  // leaf network-topology-ref
    leaf node { type nt:node-ref; mandatory true; }
    leaf name { type string; mandatory true; }
    container arguments {
      uses pcep:path-definition;          // ERO, bandwidth, attributes
      // stateful PCEP augmentation adds lsp and path-setup-type
    }
  }
  output { uses operation-result; }
}</code-block>
</compare>

The historical payload places `path-setup-type` and `lsp` in the topology namespace. The cited stateful model gives these augmented elements the stateful PCEP namespace. It also defines `delegate` and `administrative` directly under `lsp`, rather than in an `lsp-flags` container. The literal request is retained, but it is not a validated Vanadium payload.

#### Effect and response {id="pcep-update-delegated-lsp-effect"}

The RPC sends an update instruction to the selected PCC and returns an operation result. HTTP `200 OK` alone does not establish tunnel convergence. The result fields and subsequent PCEP operational state determine whether the PCC acknowledged the update. The source collection has no recorded response.

## Complete RESTCONF Calls {id="pcep-complete-restconf-calls"}

The remaining requests configure the PCEP topology provider, inspect topology and mounted-device state, manage LSPs, and invoke related PCEP operations.

### Configure the PCE TED {id="pcep-configure-ted"}

#### Request {id="pcep-configure-ted-request"}

<code-block lang="http" src="../../src/main/postman/pce/configure-pce-ted-properly.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates the PUT request. |
| `Accept` / `Content-Type` | `application/xml` | Requests and supplies XML. |

#### RESTCONF resource and YANG objects {id="pcep-configure-ted-yang"}

The PUT targets the PCEP topology augmentation beneath `topology[pcep-topology]/topology-types`. `topology-pcep`, its `session-config`, and `ted-name` are defined by the [PCEP topology model](https://github.com/opendaylight/bgpcep/blob/db82b92c1fde7db09a4ad491cb13defa3b12e002/pcep/topology/topology-api/src/main/yang/network-topology-pcep.yang).

<compare type="top-bottom" first-title="Request payload" second-title="YANG model composition (abridged)">
<code-block lang="xml">&lt;topology-pcep&gt;&lt;session-config&gt;...&lt;/session-config&gt;&lt;ted-name&gt;ted://sma-bgp-linkstate-topology&lt;/ted-name&gt;&lt;/topology-pcep&gt;</code-block>
<code-block lang="text">augment /nt:network-topology/nt:topology/nt:topology-types {
  container topology-pcep {
    container session-config {
      leaf rpc-timeout { type uint16; }
      leaf listen-address { type inet:ip-address; }
      leaf listen-port { type inet:port-number; }
      leaf dead-timer-value { type uint8; }
      leaf keep-alive-timer-value { type uint8; }
    }
    leaf ted-name { type string; }
  }
}</code-block>
</compare>

#### Effect and response {id="pcep-configure-ted-effect"}

The PUT creates or replaces the selected configuration. RFC 8040 allows `201 Created` or `204 No Content`, depending on resource existence. No response is recorded.

### Query PCC Node IDs {id="pcep-query-pcc-node-ids"}

#### Request {id="pcep-query-pcc-node-ids-request"}

<code-block lang="http" src="../../src/main/postman/pce/query-pcc-node-ids.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` / `Accept` | Basic placeholder / `application/xml` | Authenticates and requests XML. |

#### RESTCONF resource and YANG objects {id="pcep-query-pcc-node-ids-yang"}

The request reads `topology[pcep-topology]` with `content=nonconfig`, selecting operational data. PCC nodes are `node` list entries from the [shared topology model](https://github.com/opendaylight/mdsal/blob/a341bc343a9f319687fbee228032ffac68c3da67/model/ietf-topology/src/main/yang/network-topology@2013-10-21.yang), augmented by the PCEP provider.

<compare type="top-bottom" first-title="Request body" second-title="YANG resource composition (abridged)">
<code-block lang="text">No request body. Query: content=nonconfig</code-block>
<code-block lang="text">container network-topology {
  list topology [key: topology-id] {
    leaf topology-id: topology-id;         // pcep-topology
    list node [key: node-id] {
      leaf node-id: node-id;
      // network-topology-pcep operational PCC/LSP augmentation
    }
  }
}</code-block>
</compare>

#### Effect and response {id="pcep-query-pcc-node-ids-effect"}

The request normally returns `200 OK` and a nonconfiguration representation. No response is recorded.

### Query PCC Node State {id="pcep-query-pcc-node-state"}

#### Request {id="pcep-query-pcc-node-state-request"}

<code-block lang="http" src="../../src/main/postman/pce/query-pcc-node-state.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` / `Accept` | Basic placeholder / `application/xml` | Authenticates and requests XML. |

#### RESTCONF resource and YANG objects {id="pcep-query-pcc-node-state-yang"}

The URI selects `node[node-id='pcc://10.100.10.1']` in `pcep-topology`. The percent-encoded slashes are part of the list-key path segment.

<compare type="top-bottom" first-title="Request body" second-title="YANG resource composition (abridged)">
<code-block lang="text">No request body</code-block>
<code-block lang="text">container network-topology {
  list topology [key: topology-id] {
    leaf topology-id: topology-id;         // pcep-topology
    list node [key: node-id] {
      leaf node-id: node-id;               // pcc://10.100.10.1
      // PCEP augmentation provides PCC session and LSP state
    }
  }
}</code-block>
</compare>

#### Effect and response {id="pcep-query-pcc-node-state-effect"}

The GET normally returns `200 OK` if the keyed node is present. No response is recorded.

### Query PCEP LSR1 State {id="pcep-query-lsr1-state"}

#### Request {id="pcep-query-lsr1-state-request"}

<code-block lang="http" src="../../src/main/postman/pce/query-pcep-lsr1-state.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` / `Accept` | Basic placeholder / `application/xml` | Authenticates and requests XML. |
| `Content-Type` | `application/xml` | Preserved although this GET has no body. |

#### RESTCONF resource and YANG objects {id="pcep-query-lsr1-state-yang"}

The request selects the same PCC node with `content=all`, asking for both configuration and operational data.

<compare type="top-bottom" first-title="Request body" second-title="YANG resource composition (abridged)">
<code-block lang="text">No request body. Query: content=all</code-block>
<code-block lang="text">container network-topology {
  list topology [key: topology-id] {
    leaf topology-id: topology-id;         // pcep-topology
    list node [key: node-id] {
      leaf node-id: node-id;               // pcc://10.100.10.1
      // configured and operational PCEP augmentation branches
    }
  }
}</code-block>
</compare>

#### Effect and response {id="pcep-query-lsr1-state-effect"}

The GET normally returns `200 OK`. No response is recorded.

### Verify a Delegated LSP in OpenDaylight {id="pcep-verify-delegated-lsp"}

#### Request {id="pcep-verify-delegated-lsp-request"}

<code-block lang="http" src="../../src/main/postman/pce/verify-pce-delegated-lsp-in-odl-infra.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` / `Accept` | Basic placeholder / `application/xml` | Authenticates and requests XML. |

#### RESTCONF resource and YANG objects {id="pcep-verify-delegated-lsp-yang"}

This reads the PCC node that contains PCEP server state, including configured or reported LSP information when published by the controller.

<compare type="top-bottom" first-title="Request body" second-title="YANG resource composition (abridged)">
<code-block lang="text">No request body</code-block>
<code-block lang="text">container network-topology {
  list topology [key: topology-id] {
    leaf topology-id: topology-id;         // pcep-topology
    list node [key: node-id] {
      leaf node-id: node-id;               // pcc://10.100.10.1
      // pcep-server augmentation owns configured and reported LSP state
    }
  }
}</code-block>
</compare>

#### Effect and response {id="pcep-verify-delegated-lsp-effect"}

The GET normally returns `200 OK`. Its contents are required to evaluate current controller state. No response is recorded.

### Request a Delegated LSP with Intended Path {id="pcep-request-delegated-lsp"}

#### Request {id="pcep-request-delegated-lsp-request"}

<code-block lang="http" src="../../src/main/postman/pce/request-pce-delegated-lsp-10k-bandwidth.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates the POST request. |
| `Accept` / `Content-Type` | `application/xml` | Requests and supplies XML. |

#### RESTCONF resource and YANG objects {id="pcep-request-delegated-lsp-yang"}

The body uses `pcep-server:configured-lsp` and supplies an intended path. The URI selects the PCC node rather than a `configured-lsp` collection or list entry, so the source does not establish that this POST is a schema-valid RESTCONF create operation for the cited model.

<compare type="top-bottom" first-title="Request payload (abridged)" second-title="YANG model composition (abridged)">
<code-block lang="xml">&lt;configured-lsp xmlns="urn:opendaylight:params:xml:ns:yang:pcep:server"&gt;&lt;name&gt;dns-lsr1-lsr4-test&lt;/name&gt;&lt;intended-path&gt;...&lt;/intended-path&gt;&lt;/configured-lsp&gt;</code-block>
<code-block lang="text">augment .../nt:node {
  container pcep-server {
    list configured-lsp [key: name] {
      leaf name: string;
      container intended-path {
        leaf source: inet:ip-address;
        leaf destination: inet:ip-address;
        container constraints { /* requested path constraints */ }
      }
    }
  }
}</code-block>
</compare>

#### Effect and response {id="pcep-request-delegated-lsp-effect"}

No response is recorded. The endpoint and HTTP method must be checked against the controller's mounted schema before use.

### Trigger PCEP Synchronization {id="pcep-trigger-sync"}

#### Request {id="pcep-trigger-sync-request"}

<code-block lang="http" src="../../src/main/postman/pce/trigger-pcep-sync.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates the operation request. |
| `Accept` / `Content-Type` | `application/xml` | Requests and supplies XML. |

#### RESTCONF resource and YANG objects {id="pcep-trigger-sync-yang"}

`network-topology-pcep:trigger-sync` accepts the topology reference and the target PCC node, as defined by the [PCEP topology RPC model](https://github.com/opendaylight/bgpcep/blob/db82b92c1fde7db09a4ad491cb13defa3b12e002/pcep/topology/topology-api/src/main/yang/network-topology-pcep.yang).

<compare type="top-bottom" first-title="Request payload" second-title="YANG model composition (abridged)">
<code-block lang="xml">&lt;input&gt;&lt;node&gt;pcc://10.100.10.1&lt;/node&gt;&lt;network-topology-ref&gt;...pcep-topology...&lt;/network-topology-ref&gt;&lt;/input&gt;</code-block>
<code-block lang="text">rpc trigger-sync {
  input {
    uses ont:network-topology-reference;  // leaf network-topology-ref
    leaf node { type nt:node-ref; mandatory true; }
  }
}</code-block>
</compare>

#### Effect and response {id="pcep-trigger-sync-effect"}

The RPC asks the PCEP provider to synchronize the selected PCC. No response is recorded, so completion must be checked through operational state.

### Create a PCE-Delegated LSP {id="pcep-create-delegated-lsp"}

#### Request {id="pcep-create-delegated-lsp-request"}

<code-block lang="http" src="../../src/main/postman/pce/create-lsp-pce-delegated-tunnel.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates the operation request. |
| `Accept` / `Content-Type` | `application/xml` | Requests and supplies XML. |

#### RESTCONF resource and YANG objects {id="pcep-create-delegated-lsp-yang"}

`network-topology-pcep:add-lsp` uses the same PCEP RPC model as `update-lsp`. Its arguments include endpoints, an ERO, stateful LSP data, and the path setup type. The saved XML has the same stateful-namespace and `lsp-flags` differences described for the required update request.

<compare type="top-bottom" first-title="Request payload (abridged)" second-title="YANG model composition (abridged)">
<code-block lang="xml">&lt;input&gt;&lt;node&gt;pcc://10.100.10.1&lt;/node&gt;&lt;name&gt;sma-test-lsr1-lsr4-ident-001&lt;/name&gt;&lt;arguments&gt;... endpoints, ERO, LSP ...&lt;/arguments&gt;&lt;/input&gt;</code-block>
<code-block lang="text">rpc add-lsp {
  input {
    uses ont:network-topology-reference;
    leaf node { type nt:node-ref; mandatory true; }
    leaf name { type string; mandatory true; }
    container arguments {
      uses pcep:path-definition;
      uses pcep:endpoints-object;
      // stateful LSP data and path setup type are augmentations
    }
  }
  output { uses operation-result; }
}</code-block>
</compare>

#### Effect and response {id="pcep-create-delegated-lsp-effect"}

The collection records HTTP `200 OK` responses with `failure=no-ack`. Those responses do not establish successful LSP creation because the PCC acknowledgement was absent.

### Create an Explicit-ERO LSP {id="pcep-create-explicit-ero-lsp"}

#### Request {id="pcep-create-explicit-ero-lsp-request"}

<code-block lang="http" src="../../src/main/postman/pce/explicit-ero-lsp-creation.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates the operation request. |
| `Accept` / `Content-Type` | `application/xml` | Requests and supplies XML. |

#### RESTCONF resource and YANG objects {id="pcep-create-explicit-ero-lsp-yang"}

The request invokes `add-lsp` with IPv4 endpoints and an ordered explicit route object. The [RSVP model](https://github.com/opendaylight/bgpcep/blob/db82b92c1fde7db09a4ad491cb13defa3b12e002/rsvp/api/src/main/yang/rsvp.yang) defines the IP-prefix route-subobject choice.

<compare type="top-bottom" first-title="Request payload (abridged)" second-title="YANG model composition (abridged)">
<code-block lang="xml">&lt;arguments&gt;&lt;endpoints-obj&gt;...11.11.11.11 to 14.14.14.14...&lt;/endpoints-obj&gt;&lt;ero&gt;...10.0.12.2/32, 10.0.25.2/32, 14.14.14.14/32...&lt;/ero&gt;&lt;/arguments&gt;</code-block>
<code-block lang="text">rpc add-lsp / input / container arguments {
  container endpoints-obj {
    // endpoint choice, including IPv4 endpoints, is defined by PCEP types
  }
  container ero {
    list subobject [ordered-by user] {
      choice subobject-type {
        case ip-prefix { container ip-prefix { /* address and prefix length */ } }
      }
    }
  }
}</code-block>
</compare>

#### Effect and response {id="pcep-create-explicit-ero-lsp-effect"}

The RPC sends an explicit route request to the PCC. No response is recorded.

### Update an Explicit-ERO LSP {id="pcep-update-explicit-ero-lsp"}

#### Request {id="pcep-update-explicit-ero-lsp-request"}

<code-block lang="http" src="../../src/main/postman/pce/explicit-ero-lsp-update-not-creation.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | The only HTTP header supplied by the source request. |

#### RESTCONF resource and YANG objects {id="pcep-update-explicit-ero-lsp-yang"}

This is an `update-lsp` request for a named PCC LSP. The source does not declare `Accept` or `Content-Type`, which may affect server media-type processing. Its `lsp-flags` nesting has the same cited-model mismatch as the required update request.

<compare type="top-bottom" first-title="Request payload (abridged)" second-title="YANG model composition (abridged)">
<code-block lang="xml">&lt;input&gt;&lt;node&gt;pcc://10.100.10.1&lt;/node&gt;&lt;name&gt;dns-lsr1-lsr4-delegated&lt;/name&gt;&lt;arguments&gt;... plsp-id and ERO ...&lt;/arguments&gt;&lt;/input&gt;</code-block>
<code-block lang="text">rpc update-lsp {
  input {
    uses ont:network-topology-reference;
    leaf node { type nt:node-ref; mandatory true; }
    leaf name { type string; mandatory true; }
    container arguments {
      uses pcep:path-definition;           // includes ERO
      // stateful augmentation: lsp and path-setup-type
    }
  }
  output { uses operation-result; }
}</code-block>
</compare>

#### Effect and response {id="pcep-update-explicit-ero-lsp-effect"}

The RPC result must be interpreted with PCC operational state. No response is recorded.

### Remove a Configured LSP {id="pcep-remove-configured-lsp"}

#### Request {id="pcep-remove-configured-lsp-request"}

<code-block lang="http" src="../../src/main/postman/pce/remove-configured-lsp.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates the operation request. |
| `Accept` | `application/xml` twice | Duplicate header preserved from the source. |
| `Content-Type` | `application/xml` | Declares the XML input. |

#### RESTCONF resource and YANG objects {id="pcep-remove-configured-lsp-yang"}

`network-topology-pcep:remove-lsp` identifies an LSP by topology reference, PCC node, and name.

<compare type="top-bottom" first-title="Request payload" second-title="YANG model composition (abridged)">
<code-block lang="xml">&lt;input&gt;&lt;network-topology-ref&gt;...pcep-topology...&lt;/network-topology-ref&gt;&lt;node&gt;pcc://10.100.10.1&lt;/node&gt;&lt;name&gt;sma-lsr4-lsr1-cspf-80m&lt;/name&gt;&lt;/input&gt;</code-block>
<code-block lang="text">rpc remove-lsp {
  input {
    uses ont:network-topology-reference;
    leaf node { type nt:node-ref; mandatory true; }
    leaf name { type string; mandatory true; }
  }
  output { uses operation-result; }
}</code-block>
</compare>

#### Effect and response {id="pcep-remove-configured-lsp-effect"}

The RPC requests removal at the selected PCC. A response is not recorded, so subsequent operational state determines completion.

### Remove a Previous LSP Request {id="pcep-remove-previous-lsp"}

#### Request {id="pcep-remove-previous-lsp-request"}

<code-block lang="http" src="../../src/main/postman/pce/remove-previous-lsp-request.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates the operation request. |
| `Accept` / `Content-Type` | `application/xml` | Requests and supplies XML. |

#### RESTCONF resource and YANG objects {id="pcep-remove-previous-lsp-yang"}

This is another `remove-lsp` invocation. It targets `pcc://10.100.10.1` and the LSP name `dns-lsr1-lsr4-explicit-noplsid`.

<compare type="top-bottom" first-title="Request payload" second-title="YANG model composition (abridged)">
<code-block lang="xml">&lt;input&gt;&lt;node&gt;pcc://10.100.10.1&lt;/node&gt;&lt;name&gt;dns-lsr1-lsr4-explicit-noplsid&lt;/name&gt;&lt;network-topology-ref&gt;...pcep-topology...&lt;/network-topology-ref&gt;&lt;/input&gt;</code-block>
<code-block lang="text">rpc remove-lsp {
  input {
    uses ont:network-topology-reference;
    leaf node { type nt:node-ref; mandatory true; }
    leaf name { type string; mandatory true; }
  }
  output { uses operation-result; }
}</code-block>
</compare>

#### Effect and response {id="pcep-remove-previous-lsp-effect"}

The operation asks the PCC to remove the named LSP. No response is recorded.

### Request a Constrained Path Example {id="pcep-constrained-path-example"}

#### Request {id="pcep-constrained-path-example-request"}

<code-block lang="http" src="../../src/main/postman/pce/request-constrainted-path-examples.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates the operation request. |
| `Accept` / `Content-Type` | `application/xml` | Requests and supplies XML. |

#### RESTCONF resource and YANG objects {id="pcep-constrained-path-example-yang"}

This uses the same `get-constrained-path` RPC as the required computation request. It changes the requested bandwidth to `10000000` bytes per second.

<compare type="top-bottom" first-title="Request payload (abridged)" second-title="YANG model composition (abridged)">
<code-block lang="xml">&lt;input&gt;&lt;graph-name&gt;ted://sma-bgp-linkstate-topology&lt;/graph-name&gt;&lt;source&gt;185273099&lt;/source&gt;&lt;destination&gt;235802126&lt;/destination&gt;&lt;constraints&gt;&lt;bandwidth&gt;10000000&lt;/bandwidth&gt;&lt;/constraints&gt;&lt;algorithm&gt;cspf&lt;/algorithm&gt;&lt;/input&gt;</code-block>
<code-block lang="text">rpc get-constrained-path {
  input {
    leaf graph-name { type string; mandatory true; }
    leaf source { type uint64; mandatory true; }
    leaf destination { type uint64; mandatory true; }
    container constraints { leaf bandwidth { type graph:decimal-bandwidth; } }
    leaf algorithm { type algorithm; }
  }
  output { /* computation status and path descriptions */ }
}</code-block>
</compare>

#### Effect and response {id="pcep-constrained-path-example-effect"}

The collection records `200 OK` examples with `status=completed` and metric `2`. They are historical graph results and do not predict the current graph result.

### Delete a PCE Delegated LSP Configuration {id="pcep-delete-delegated-lsp"}

#### Request {id="pcep-delete-delegated-lsp-request"}

<code-block lang="http" src="../../src/main/postman/pce/delete-pce-delegated-lsp-in-odl.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` / `Accept` | Basic placeholder / `application/xml` | Authenticates and requests XML. |

#### RESTCONF resource and YANG objects {id="pcep-delete-delegated-lsp-yang"}

The DELETE selects a `pcep-server:configured-lsp` list entry by PCC node and LSP name. The URI carries the `pcc://` key with percent-encoded colon and slashes.

<compare type="top-bottom" first-title="Request body" second-title="YANG model composition (abridged)">
<code-block lang="text">No request body</code-block>
<code-block lang="text">container network-topology {
  list topology [key: topology-id] {
    leaf topology-id: topology-id;          // pcep-topology
    list node [key: node-id] {
      leaf node-id: node-id;                // pcc://10.100.10.1
      container pcep-server {               // PCEP augmentation
        list configured-lsp [key: name] {
          leaf name: string;                // dns-lsr1-lsr4-10m
        }
      }
    }
  }
}</code-block>
</compare>

#### Effect and response {id="pcep-delete-delegated-lsp-effect"}

An existing datastore resource is normally deleted with `204 No Content` under RFC 8040. No response is recorded, and a datastore deletion does not itself confirm PCC withdrawal.

### Query MPLS-TE Operational Data through a NETCONF Mount {id="pcep-query-mpls-te-mounted"}

#### Request {id="pcep-query-mpls-te-mounted-request"}

<code-block lang="http" src="../../src/main/postman/pce/query-mpls-te-operational-data-through-netconf-mount.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` / `Accept` | Basic placeholder / `application/xml` | Authenticates and requests XML. |

#### RESTCONF resource and YANG objects {id="pcep-query-mpls-te-mounted-yang"}

The path crosses `yang-ext:mount` below NETCONF node `sma-xrv-lsr1-alpha` and selects `Cisco-IOS-XR-mpls-te-oper:mpls-te`. The exact Cisco model revision is advertised by the mounted device and is not available in this source collection.

<compare type="top-bottom" first-title="Request body" second-title="YANG resource composition (abridged)">
<code-block lang="text">No request body</code-block>
<code-block lang="text">container network-topology {
  list topology [key: topology-id] {
    leaf topology-id: topology-id;          // topology-netconf
    list node [key: node-id] {
      leaf node-id: node-id;                // sma-xrv-lsr1-alpha
      yang-ext:mount {
        Cisco-IOS-XR-mpls-te-oper:mpls-te;  // mounted-device schema root
        // exact mounted revision and children are unverified here
      }
    }
  }
}</code-block>
</compare>

#### Effect and response {id="pcep-query-mpls-te-mounted-effect"}

The GET requires an available NETCONF mount and normally returns `200 OK`. No response is recorded.
