# Controller-Side Application RESTCONF Operational-State Requests

The controller-side application publishes a read-only `csa:controller-state` snapshot to the MD-SAL operational datastore. The application-owned <code>csa.yang</code> module defines every RESTCONF response below. Each request is a GET and does not change controller state.

The XML blocks are literal excerpts from the supplied controller-state response, captured at `2026-10-20T03:14:44.726999414Z`. Where that snapshot reports an empty list, the documentation shows the exact count evidence rather than inventing an entry.

<show-structure for="chapter" depth="2"/>

## Get the Complete CSA Operational State {id="csa-read-runtime-status"}

### Request {id="csa-read-runtime-status-request"}

<code-block lang="http" src="../../src/main/postman/csa-restconf/get-runtime-status-of-the-csa.http"/>

### RESTCONF resource and YANG objects {id="csa-read-runtime-status-restconf-resource-and-yang-objects"}

`GET /restconf/data/csa:controller-state` selects the complete operational root.

#### YANG response body structure {id="csa-read-runtime-status-yang-response-body-structure"}

<code-block lang="text" src="../../src/main/java/sdn-mpls-ml-odl-cs-app/impl/src/main/yang/csa.yang" />

#### Observed XML response excerpt {id="csa-read-runtime-status-observed-xml-response-excerpt"}

<code-block lang="xml" src="csa-controller-state-observed-response.xml"/>

### Effect and response {id="csa-read-runtime-status-effect-and-response"}

The operation normally returns `200 OK` with the available application snapshot. Its contents are point-in-time operational data, not an independent health check of every device or protocol session.

## Get BGP-LS State of the CSA {id="csa-read-bgp-ls-state"}

### Request {id="csa-read-bgp-ls-state-request"}

<code-block lang="http" src="../../src/main/postman/csa-restconf/get-bgp-ls-state-of-the-csa.http"/>

### RESTCONF resource and YANG objects {id="csa-read-bgp-ls-state-restconf-resource-and-yang-objects"}

The URL is `GET /restconf/data/csa:controller-state?fields=bgp-ls-node`, which projects only the BGP-LS node list.

#### YANG response body structure {id="csa-read-bgp-ls-state-yang-response-body-structure"}

<code-block lang="text">container csa:controller-state { config false;
  list bgp-ls-node [key: router-id] {
    leaf router-id: string; // list key
    leaf topology-id: string; leaf node-id: string;
    leaf te-router-id-ipv4: string; leaf graph-node-id: uint64;
  }
}</code-block>

#### Observed XML response excerpt {id="csa-read-bgp-ls-state-observed-xml-response-excerpt"}

<code-block lang="xml">&lt;bgp-ls-node&gt;
    &lt;router-id&gt;11.11.11.11&lt;/router-id&gt;
    &lt;te-router-id-ipv4&gt;11.11.11.11&lt;/te-router-id-ipv4&gt;
    &lt;node-id&gt;bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099&lt;/node-id&gt;
    &lt;graph-node-id&gt;185273099&lt;/graph-node-id&gt;
    &lt;topology-id&gt;sma-bgp-linkstate-topology&lt;/topology-id&gt;
&lt;/bgp-ls-node&gt;</code-block>

### Effect and response {id="csa-read-bgp-ls-state-effect-and-response"}

It exposes BGP-LS nodes already copied to the CSA snapshot; it does not refresh topology.

## Get Cache State of the CSA {id="csa-read-cache-state"}

### Request {id="csa-read-cache-state-request"}

<code-block lang="http" src="../../src/main/postman/csa-restconf/get-cache-state-of-the-csa.http"/>

### RESTCONF resource and YANG objects {id="csa-read-cache-state-restconf-resource-and-yang-objects"}

`GET /restconf/data/csa:controller-state/cache-state` selects the count-only child container.

#### YANG response body structure {id="csa-read-cache-state-yang-response-body-structure"}

<code-block lang="text">container csa:controller-state { config false;
  container cache-state {
    leaf classification-entry-count: uint32;
    leaf classification-exact-entry-count: uint32;
    leaf classification-service-entry-count: uint32;
    leaf calculated-path-entry-count: uint32;
    leaf delegated-lsp-count: uint32;
    leaf policy-evidence-bucket-count: uint32;
    leaf directional-evidence-entry-count: uint32;
    leaf active-pair-policy-count: uint32;
    leaf directional-lsp-application-count: uint32;
    leaf openflow-switch-count: uint32;
  }
}</code-block>

#### Observed XML response {id="csa-read-cache-state-observed-xml-response"}

<code-block lang="xml">&lt;cache-state xmlns="urn:sma:sdn:csa"&gt;
    &lt;classification-service-entry-count&gt;0&lt;/classification-service-entry-count&gt;
    &lt;delegated-lsp-count&gt;2&lt;/delegated-lsp-count&gt;
    &lt;calculated-path-entry-count&gt;0&lt;/calculated-path-entry-count&gt;
    &lt;classification-exact-entry-count&gt;0&lt;/classification-exact-entry-count&gt;
    &lt;policy-evidence-bucket-count&gt;0&lt;/policy-evidence-bucket-count&gt;
    &lt;directional-evidence-entry-count&gt;0&lt;/directional-evidence-entry-count&gt;
    &lt;classification-entry-count&gt;0&lt;/classification-entry-count&gt;
    &lt;active-pair-policy-count&gt;0&lt;/active-pair-policy-count&gt;
    &lt;directional-lsp-application-count&gt;0&lt;/directional-lsp-application-count&gt;
    &lt;openflow-switch-count&gt;2&lt;/openflow-switch-count&gt;
&lt;/cache-state&gt;</code-block>

### Effect and response {id="csa-read-cache-state-effect-and-response"}

The counts describe registry entries in the published snapshot. A zero means no entry was published at the capture time.

## Get Calculated Path Cache of the CSA {id="csa-read-calculated-path-cache"}

### Request {id="csa-read-calculated-path-cache-request"}

<code-block lang="http" src="../../src/main/postman/csa-restconf/get-calculated-path-cache-of-the-csa.http"/>

### RESTCONF resource and YANG objects {id="csa-read-calculated-path-cache-restconf-resource-and-yang-objects"}

The URL is `GET /restconf/data/csa:controller-state?fields=calculated-path-cache-entry`.

#### YANG response body structure {id="csa-read-calculated-path-cache-yang-response-body-structure"}

<code-block lang="text">container csa:controller-state { config false;
  list calculated-path-cache-entry [key: entry-id] {
    leaf entry-id: string; // list key
    leaf graph-name: string; leaf source-graph-node-id: uint64;
    leaf destination-graph-node-id: uint64; leaf bandwidth-bytes-per-second: uint64;
    leaf class-type: uint32; leaf algorithm: string; leaf computed-te-metric: uint32;
    leaf-list path-hop: string; leaf-list ero-subobject: string;
    leaf calculated-at: string; leaf expires-at: string;
  }
}</code-block>

#### Observed XML response evidence {id="csa-read-calculated-path-cache-observed-xml-response-evidence"}

<code-block lang="xml">&lt;cache-state&gt;
    &lt;calculated-path-entry-count&gt;0&lt;/calculated-path-entry-count&gt;
&lt;/cache-state&gt;</code-block>

### Effect and response {id="csa-read-calculated-path-cache-effect-and-response"}

No calculated-path entry was present in the supplied snapshot. This read does not invoke PCEP computation.

## Get Class Registry Cache {id="csa-read-class-registry-cache"}

### Request {id="csa-read-class-registry-cache-request"}

<code-block lang="http" src="../../src/main/postman/csa-restconf/get-class-registry-cache.http"/>

### RESTCONF resource and YANG objects {id="csa-read-class-registry-cache-restconf-resource-and-yang-objects"}

The URL is `GET /restconf/data/csa:controller-state?fields=classification-exact-cache-entry`.

#### YANG response body structure {id="csa-read-class-registry-cache-yang-response-body-structure"}

<code-block lang="text">container csa:controller-state { config false;
  list classification-exact-cache-entry [key: entry-id] {
    leaf entry-id: string; // list key
    leaf ingress-switch-name: string; leaf ingress-connector-name: string;
    leaf eth-type: uint32; leaf ip-protocol: uint32;
    leaf source-port: uint32; leaf destination-port: uint32;
    // uses classification-result-fields
    leaf request-id: string; leaf model-name: string;
    leaf class-id: uint32; leaf class-name: string; leaf confidence: string;
    leaf-list probability: string; leaf profile-name: string;
    leaf dscp: uint32; leaf mpls-tc: uint32;
    leaf requested-bandwidth-kbps: uint64; leaf cached-at: string; leaf expires-at: string;
  }
}</code-block>

#### Observed XML response evidence {id="csa-read-class-registry-cache-observed-xml-response-evidence"}

<code-block lang="xml">&lt;cache-state&gt;
    &lt;classification-exact-entry-count&gt;0&lt;/classification-exact-entry-count&gt;
&lt;/cache-state&gt;</code-block>

### Effect and response {id="csa-read-class-registry-cache-effect-and-response"}

The supplied snapshot contains no exact-classification entries. The request reads cached results and does not classify traffic.

## Get Classification Registry Cache of the CSA {id="csa-read-classification-registry-cache"}

### Request {id="csa-read-classification-registry-cache-request"}

<code-block lang="http" src="../../src/main/postman/csa-restconf/get-classification-registry-cache-of-the-csa.http"/>

### RESTCONF resource and YANG objects {id="csa-read-classification-registry-cache-restconf-resource-and-yang-objects"}

This is a separately saved collection artifact, with the same `GET /restconf/data/csa:controller-state?fields=classification-exact-cache-entry` URL as **Get Class Registry Cache**.

#### YANG response body structure {id="csa-read-classification-registry-cache-yang-response-body-structure"}

<code-block lang="text">container csa:controller-state { config false;
  list classification-exact-cache-entry [key: entry-id] {
    leaf entry-id: string; // list key
    leaf ingress-switch-name: string; leaf ingress-connector-name: string;
    leaf eth-type: uint32; leaf ip-protocol: uint32;
    leaf source-port: uint32; leaf destination-port: uint32;
    // uses classification-result-fields
    leaf class-id: uint32; leaf class-name: string; leaf confidence: string;
    leaf profile-name: string; leaf dscp: uint32; leaf mpls-tc: uint32;
    leaf requested-bandwidth-kbps: uint64; leaf cached-at: string; leaf expires-at: string;
  }
}</code-block>

#### Observed XML response evidence {id="csa-read-classification-registry-cache-observed-xml-response-evidence"}

<code-block lang="xml">&lt;cache-state&gt;
    &lt;classification-exact-entry-count&gt;0&lt;/classification-exact-entry-count&gt;
&lt;/cache-state&gt;</code-block>

### Effect and response {id="csa-read-classification-registry-cache-effect-and-response"}

The same zero-entry state applies to this alias artifact, which is retained as its own documented request.

## Get Control-Plane Status of the CSA {id="csa-read-control-plane-status"}

### Request {id="csa-read-control-plane-status-request"}

<code-block lang="http" src="../../src/main/postman/csa-restconf/get-control-plane-status-of-the-csa.http"/>

### RESTCONF resource and YANG objects {id="csa-read-control-plane-status-restconf-resource-and-yang-objects"}

`GET /restconf/data/csa:controller-state/control-plane` selects the lifecycle and topology-freshness container.

#### YANG response body structure {id="csa-read-control-plane-status-yang-response-body-structure"}

<code-block lang="text">container csa:controller-state { config false;
  container control-plane {
    leaf ready: boolean; leaf closed: boolean; leaf topology-id: string;
    leaf topology-ttl-millis: uint64; leaf topology-fresh: boolean;
    leaf topology-refresh-in-progress: boolean;
    leaf topology-last-successful-refresh: string; leaf topology-last-refresh-attempt: string;
    leaf topology-fresh-until: string; leaf topology-last-failure: string;
    leaf topology-refresh-success-count: uint64; leaf topology-refresh-failure-count: uint64;
  }
}</code-block>

#### Observed XML response {id="csa-read-control-plane-status-observed-xml-response"}

<code-block lang="xml">&lt;control-plane xmlns="urn:sma:sdn:csa"&gt;
    &lt;topology-last-refresh-attempt&gt;2026-10-20T03:13:45.801701567Z&lt;/topology-last-refresh-attempt&gt;
    &lt;topology-last-failure/&gt;
    &lt;topology-last-successful-refresh&gt;2026-10-20T03:13:45.801701567Z&lt;/topology-last-successful-refresh&gt;
    &lt;topology-fresh&gt;true&lt;/topology-fresh&gt;
    &lt;topology-id&gt;sma-bgp-linkstate-topology&lt;/topology-id&gt;
    &lt;topology-ttl-millis&gt;300000&lt;/topology-ttl-millis&gt;
    &lt;closed&gt;false&lt;/closed&gt;
    &lt;topology-fresh-until&gt;2026-10-20T03:18:45.801701567Z&lt;/topology-fresh-until&gt;
    &lt;ready&gt;true&lt;/ready&gt;
    &lt;topology-refresh-in-progress&gt;false&lt;/topology-refresh-in-progress&gt;
    &lt;topology-refresh-success-count&gt;1&lt;/topology-refresh-success-count&gt;
    &lt;topology-refresh-failure-count&gt;0&lt;/topology-refresh-failure-count&gt;
&lt;/control-plane&gt;</code-block>

### Effect and response {id="csa-read-control-plane-status-effect-and-response"}

`ready` is the CSA readiness gate. It reports application state and does not independently validate the external topology source.

## Get CSA Metrics Endpoint {id="csa-read-metrics-endpoint"}

### Request {id="csa-read-metrics-endpoint-request"}

<code-block lang="http" src="../../src/main/postman/csa-restconf/get-csa-metrics-endpoint.http"/>

### RESTCONF resource and YANG objects {id="csa-read-metrics-endpoint-restconf-resource-and-yang-objects"}

This saved request targets `GET http://172.21.121.100:8181/csa/metrics`, not `/restconf/data` on port `8182`; it is outside `csa.yang`.

#### Response body representation {id="csa-read-metrics-endpoint-response-body-representation"}

<code-block lang="text">No csa.yang response-body structure applies.
This is an application metrics endpoint, not a RESTCONF data resource.</code-block>

#### Observed XML response {id="csa-read-metrics-endpoint-observed-xml-response"}

<code-block lang="xml">No XML response for /csa/metrics was supplied with the controller-state capture.</code-block>

### Effect and response {id="csa-read-metrics-endpoint-effect-and-response"}

The endpoint is read-only. Its collection artifact does not establish an XML media type, so this topic does not invent one.

## Get Delegated LSP Cache of the CSA {id="csa-read-delegated-lsp-cache"}

### Request {id="csa-read-delegated-lsp-cache-request"}

<code-block lang="http" src="../../src/main/postman/csa-restconf/get-delegated-lsp-cache-of-the-csa.http"/>

### RESTCONF resource and YANG objects {id="csa-read-delegated-lsp-cache-restconf-resource-and-yang-objects"}

The URL is `GET /restconf/data/csa:controller-state?fields=delegated-lsp-entry`.

#### YANG response body structure {id="csa-read-delegated-lsp-cache-yang-response-body-structure"}

<code-block lang="text">container csa:controller-state { config false;
  list delegated-lsp-entry [key: direction-key] {
    leaf direction-key: string; // list key
    leaf pcc-node: string; leaf lsp-name: string; leaf tunnel-interface-name: string;
    leaf source-router-id: string; leaf destination-router-id: string;
    leaf plsp-id: uint64; leaf tunnel-id: uint64; leaf lsp-id: uint64;
    leaf delegated: boolean; leaf administrative-up: boolean;
    leaf operational-state: string; leaf valid-for-update: boolean;
    leaf-list active-ero-subobject: string; leaf reported-bandwidth-base64: string;
    leaf discovered-at: string; leaf updated-at: string;
  }
}</code-block>

#### Observed XML response excerpt {id="csa-read-delegated-lsp-cache-observed-xml-response-excerpt"}

<code-block lang="xml">&lt;delegated-lsp-entry&gt;
    &lt;direction-key&gt;lsr1_to_lsr4&lt;/direction-key&gt;
    &lt;updated-at&gt;2026-10-20T03:13:45.887614045Z&lt;/updated-at&gt;
    &lt;lsp-id&gt;3&lt;/lsp-id&gt;
    &lt;valid-for-update&gt;true&lt;/valid-for-update&gt;
    &lt;source-router-id&gt;11.11.11.11&lt;/source-router-id&gt;
    &lt;tunnel-interface-name&gt;tunnel-te110&lt;/tunnel-interface-name&gt;
    &lt;pcc-node&gt;pcc://10.100.10.1&lt;/pcc-node&gt;
    &lt;lsp-name&gt;sma-lsr1-lsr4-delegated&lt;/lsp-name&gt;
    &lt;tunnel-id&gt;110&lt;/tunnel-id&gt;
    &lt;delegated&gt;true&lt;/delegated&gt;
    &lt;active-ero-subobject&gt;strict|10.0.14.2/32&lt;/active-ero-subobject&gt;
    &lt;active-ero-subobject&gt;strict|14.14.14.14/32&lt;/active-ero-subobject&gt;
    &lt;destination-router-id&gt;14.14.14.14&lt;/destination-router-id&gt;
    &lt;administrative-up&gt;true&lt;/administrative-up&gt;
    &lt;plsp-id&gt;111&lt;/plsp-id&gt;
    &lt;operational-state&gt;up&lt;/operational-state&gt;
    &lt;discovered-at&gt;2026-10-20T03:13:45.887614045Z&lt;/discovered-at&gt;
    &lt;reported-bandwidth-base64&gt;AAAAAA==&lt;/reported-bandwidth-base64&gt;
&lt;/delegated-lsp-entry&gt;</code-block>

### Effect and response {id="csa-read-delegated-lsp-cache-effect-and-response"}

The data records delegated LSP state already observed by the CSA. It does not update a PCC LSP.

## Get Directional LSP Application of the CSA {id="csa-read-directional-lsp-application"}

### Request {id="csa-read-directional-lsp-application-request"}

<code-block lang="http" src="../../src/main/postman/csa-restconf/get-directional-lsp-application-of-the-csa.http"/>

### RESTCONF resource and YANG objects {id="csa-read-directional-lsp-application-restconf-resource-and-yang-objects"}

The URL is `GET /restconf/data/csa:controller-state?fields=directional-lsp-application-entry`.

#### YANG response body structure {id="csa-read-directional-lsp-application-yang-response-body-structure"}

<code-block lang="text">container csa:controller-state { config false;
  list directional-lsp-application-entry [key: entry-id] {
    leaf entry-id: string; // list key
    leaf operation-id: string; leaf workflow-id: string; leaf packet-sequence: uint64;
    leaf pair-key: string; leaf direction-key: string; leaf policy-hash: string;
    leaf desired-lsp-state-hash: string; leaf status: string;
    leaf update-lsp-http-status: uint32; leaf update-lsp-sent: boolean;
    leaf pcep-ero-confirmed: boolean; leaf pcep-bandwidth-confirmed: boolean;
  }
}</code-block>

#### Observed XML response evidence {id="csa-read-directional-lsp-application-observed-xml-response-evidence"}

<code-block lang="xml">&lt;cache-state&gt;
    &lt;directional-lsp-application-count&gt;0&lt;/directional-lsp-application-count&gt;
&lt;/cache-state&gt;</code-block>

### Effect and response {id="csa-read-directional-lsp-application-effect-and-response"}

No application record was present in the supplied snapshot. The query reads prior workflow evidence and does not send an LSP update.

## Get Directional Policy Entry of the CSA {id="csa-read-directional-policy-entry"}

### Request {id="csa-read-directional-policy-entry-request"}

<code-block lang="http" src="../../src/main/postman/csa-restconf/get-directional-policy-entry-of-the-csa.http"/>

### RESTCONF resource and YANG objects {id="csa-read-directional-policy-entry-restconf-resource-and-yang-objects"}

The URL is `GET /restconf/data/csa:controller-state?fields=active-pair-policy-entry`.

#### YANG response body structure {id="csa-read-directional-policy-entry-yang-response-body-structure"}

<code-block lang="text">container csa:controller-state { config false;
  list active-pair-policy-entry [key: pair-key] {
    leaf pair-key: string; // list key
    // uses policy-fields
    leaf service-eth-type: uint32; leaf service-ip-protocol: uint32;
    leaf canonical-service-port: uint32; leaf service-key: string;
    leaf class-name: string; leaf profile-name: string; leaf dscp: uint32; leaf mpls-tc: uint32;
    leaf requested-bandwidth-kbps: uint64; leaf setup-priority: uint32; leaf hold-priority: uint32;
    leaf policy-schema-version: string; leaf policy-hash: string;
    leaf generation: uint64; leaf installed-at: string; leaf last-refreshed-at: string; leaf expires-at: string;
  }
}</code-block>

#### Observed XML response evidence {id="csa-read-directional-policy-entry-observed-xml-response-evidence"}

<code-block lang="xml">&lt;cache-state&gt;
    &lt;active-pair-policy-count&gt;0&lt;/active-pair-policy-count&gt;
&lt;/cache-state&gt;</code-block>

### Effect and response {id="csa-read-directional-policy-entry-effect-and-response"}

No active pair policy was published in the supplied snapshot. This read exposes consensus results already retained by the CSA.

## Get Directional Policy Cache of the CSA {id="csa-read-directional-policy-cache"}

### Request {id="csa-read-directional-policy-cache-request"}

<code-block lang="http" src="../../src/main/postman/csa-restconf/get-directional-policycache-of-the-csa.http"/>

### RESTCONF resource and YANG objects {id="csa-read-directional-policy-cache-restconf-resource-and-yang-objects"}

The URL is `GET /restconf/data/csa:controller-state?fields=directional-policy-evidence-entry`.

#### YANG response body structure {id="csa-read-directional-policy-cache-yang-response-body-structure"}

<code-block lang="text">container csa:controller-state { config false;
  list directional-policy-evidence-entry [key: entry-id] {
    leaf entry-id: string; // list key
    leaf pair-key: string; leaf side: string; leaf direction-key: string;
    leaf ingress-switch-name: string; leaf ingress-connector-name: string;
    leaf eth-type: uint32; leaf ip-protocol: uint32; leaf source-port: uint32; leaf destination-port: uint32;
    // uses policy-fields
    leaf policy-hash: string; leaf requested-bandwidth-kbps: uint64;
    leaf bucket-created-at: string; leaf bucket-updated-at: string; leaf bucket-expires-at: string;
    leaf observed-at: string; leaf expires-at: string;
  }
}</code-block>

#### Observed XML response evidence {id="csa-read-directional-policy-cache-observed-xml-response-evidence"}

<code-block lang="xml">&lt;cache-state&gt;
    &lt;directional-evidence-entry-count&gt;0&lt;/directional-evidence-entry-count&gt;
    &lt;policy-evidence-bucket-count&gt;0&lt;/policy-evidence-bucket-count&gt;
&lt;/cache-state&gt;</code-block>

### Effect and response {id="csa-read-directional-policy-cache-effect-and-response"}

No directional evidence was present in the captured snapshot. The request does not classify traffic or establish consensus.

## Get Policy State of the CSA {id="csa-read-policy-state"}

### Request {id="csa-read-policy-state-request"}

<code-block lang="http" src="../../src/main/postman/csa-restconf/get-policy-state-of-the-csa.http"/>

### RESTCONF resource and YANG objects {id="csa-read-policy-state-restconf-resource-and-yang-objects"}

`GET /restconf/data/csa:controller-state/policy-state` selects the published policy controls.

#### YANG response body structure {id="csa-read-policy-state-yang-response-body-structure"}

<code-block lang="text">container csa:controller-state { config false;
  container policy-state {
    leaf consensus-enabled: boolean; leaf require-both-directions: boolean;
    leaf single-side-provisional-enabled: boolean; leaf equal-priority-action: string;
    leaf evidence-ttl-millis: uint64; leaf active-policy-idle-ttl-millis: uint64;
    leaf active-policy-sweeper-enabled: boolean; leaf active-policy-sweeper-interval-millis: uint64;
    leaf priority-preemption-enabled: boolean; leaf policy-hash-version: uint32;
    leaf lsp-application-scope: string; leaf require-all-lsp-directions: boolean;
    leaf classification-cache-ttl-millis: uint64; leaf calculated-path-cache-ttl-millis: uint64;
  }
}</code-block>

#### Observed XML response {id="csa-read-policy-state-observed-xml-response"}

<code-block lang="xml">&lt;policy-state xmlns="urn:sma:sdn:csa"&gt;
    &lt;priority-preemption-enabled&gt;true&lt;/priority-preemption-enabled&gt;
    &lt;active-policy-sweeper-interval-millis&gt;15000&lt;/active-policy-sweeper-interval-millis&gt;
    &lt;lsp-application-scope&gt;BIDIRECTIONAL_PAIR&lt;/lsp-application-scope&gt;
    &lt;evidence-ttl-millis&gt;10000&lt;/evidence-ttl-millis&gt;
    &lt;equal-priority-action&gt;KEEP_CURRENT_OR_DEFER&lt;/equal-priority-action&gt;
    &lt;require-all-lsp-directions&gt;true&lt;/require-all-lsp-directions&gt;
    &lt;calculated-path-cache-ttl-millis&gt;60000&lt;/calculated-path-cache-ttl-millis&gt;
    &lt;active-policy-idle-ttl-millis&gt;60000&lt;/active-policy-idle-ttl-millis&gt;
    &lt;policy-hash-version&gt;1&lt;/policy-hash-version&gt;
    &lt;require-both-directions&gt;true&lt;/require-both-directions&gt;
    &lt;classification-cache-ttl-millis&gt;3600000&lt;/classification-cache-ttl-millis&gt;
    &lt;single-side-provisional-enabled&gt;false&lt;/single-side-provisional-enabled&gt;
    &lt;active-policy-sweeper-enabled&gt;true&lt;/active-policy-sweeper-enabled&gt;
    &lt;consensus-enabled&gt;true&lt;/consensus-enabled&gt;
&lt;/policy-state&gt;</code-block>

### Effect and response {id="csa-read-policy-state-effect-and-response"}

The endpoint reports policy controls used by the CSA and does not change them.

## Get Tunnel Direction Entry of the CSA {id="csa-read-tunnel-direction-entry"}

### Request {id="csa-read-tunnel-direction-entry-request"}

<code-block lang="http" src="../../src/main/postman/csa-restconf/get-tunnel-direction-entry-of-the-csa.http"/>

### RESTCONF resource and YANG objects {id="csa-read-tunnel-direction-entry-restconf-resource-and-yang-objects"}

The URL is `GET /restconf/data/csa:controller-state?fields=tunnel-direction-entry`.

#### YANG response body structure {id="csa-read-tunnel-direction-entry-yang-response-body-structure"}

<code-block lang="text">container csa:controller-state { config false;
  list tunnel-direction-entry [key: direction-key] {
    leaf direction-key: string; // list key
    leaf pair-key: string; leaf source-logical-name: string; leaf destination-logical-name: string;
    leaf source-router-id: string; leaf destination-router-id: string;
    leaf source-pcc-node: string; leaf destination-pcc-node: string;
  }
}</code-block>

#### Observed XML response excerpt {id="csa-read-tunnel-direction-entry-observed-xml-response-excerpt"}

<code-block lang="xml">&lt;tunnel-direction-entry&gt;
    &lt;direction-key&gt;lsr1_to_lsr4&lt;/direction-key&gt;
    &lt;pair-key&gt;lsr1_lsr4&lt;/pair-key&gt;
    &lt;destination-logical-name&gt;lsr4&lt;/destination-logical-name&gt;
    &lt;source-router-id&gt;11.11.11.11&lt;/source-router-id&gt;
    &lt;source-pcc-node&gt;pcc://10.100.10.1&lt;/source-pcc-node&gt;
    &lt;source-logical-name&gt;lsr1&lt;/source-logical-name&gt;
    &lt;destination-router-id&gt;14.14.14.14&lt;/destination-router-id&gt;
    &lt;destination-pcc-node&gt;pcc://10.100.40.1&lt;/destination-pcc-node&gt;
&lt;/tunnel-direction-entry&gt;</code-block>

### Effect and response {id="csa-read-tunnel-direction-entry-effect-and-response"}

The request reads configured tunnel-direction correlation data. It does not query PCCs or change tunnel configuration.

## Get Tunnel Pair Entry of the CSA {id="csa-read-tunnel-pair-entry"}

### Request {id="csa-read-tunnel-pair-entry-request"}

<code-block lang="http" src="../../src/main/postman/csa-restconf/get-tunnel-pair-entry-of-the-csa.http"/>

### RESTCONF resource and YANG objects {id="csa-read-tunnel-pair-entry-restconf-resource-and-yang-objects"}

The URL is `GET /restconf/data/csa:controller-state?fields=tunnel-pair-entry`.

#### YANG response body structure {id="csa-read-tunnel-pair-entry-yang-response-body-structure"}

<code-block lang="text">container csa:controller-state { config false;
  list tunnel-pair-entry [key: pair-key] {
    leaf pair-key: string; // list key
    leaf left-switch-name: string; leaf right-switch-name: string;
    leaf left-router-id: string; leaf right-router-id: string;
    leaf forward-direction-key: string; leaf reverse-direction-key: string;
  }
}</code-block>

#### Observed XML response {id="csa-read-tunnel-pair-entry-observed-xml-response"}

<code-block lang="xml">&lt;tunnel-pair-entry xmlns="urn:sma:sdn:csa"&gt;
    &lt;pair-key&gt;lsr1_lsr4&lt;/pair-key&gt;
    &lt;left-switch-name&gt;ECHO&lt;/left-switch-name&gt;
    &lt;reverse-direction-key&gt;lsr4_to_lsr1&lt;/reverse-direction-key&gt;
    &lt;right-switch-name&gt;FOXTROT&lt;/right-switch-name&gt;
    &lt;right-router-id&gt;14.14.14.14&lt;/right-router-id&gt;
    &lt;forward-direction-key&gt;lsr1_to_lsr4&lt;/forward-direction-key&gt;
    &lt;left-router-id&gt;11.11.11.11&lt;/left-router-id&gt;
&lt;/tunnel-pair-entry&gt;</code-block>

### Effect and response {id="csa-read-tunnel-pair-entry-effect-and-response"}

The data relates the two configured directions of a tunnel pair. It is read-only operational state.

## Get CSA Controller-State Generation Timestamp {id="csa-read-generated-at"}

### Request {id="csa-read-generated-at-request"}

<code-block lang="http" src="../../src/main/postman/csa-restconf/get-controller-state-generated-at-of-the-csa.http"/>

### RESTCONF resource and YANG objects {id="csa-read-generated-at-restconf-resource-and-yang-objects"}

`GET /restconf/data/csa:controller-state/generated-at` selects the root's generation timestamp leaf.

#### YANG response body structure {id="csa-read-generated-at-yang-response-body-structure"}

<code-block lang="text">container csa:controller-state { config false;
  leaf generated-at: string;
}</code-block>

#### Observed XML response member {id="csa-read-generated-at-observed-xml-response-member"}

<code-block lang="xml">&lt;generated-at&gt;2026-10-20T03:14:44.726999414Z&lt;/generated-at&gt;</code-block>

### Effect and response {id="csa-read-generated-at-effect-and-response"}

The value identifies when the publisher built the operational snapshot. It does not indicate when each individual source registry was last changed.

## Get CSA Processed Packet Count {id="csa-read-processed-packet-count"}

### Request {id="csa-read-processed-packet-count-request"}

<code-block lang="http" src="../../src/main/postman/csa-restconf/get-processed-packet-count-of-the-csa.http"/>

### RESTCONF resource and YANG objects {id="csa-read-processed-packet-count-restconf-resource-and-yang-objects"}

`GET /restconf/data/csa:controller-state/processed-packet-count` selects the root's unsigned 64-bit packet-count leaf.

#### YANG response body structure {id="csa-read-processed-packet-count-yang-response-body-structure"}

<code-block lang="text">container csa:controller-state { config false;
  leaf processed-packet-count: uint64;
}</code-block>

#### Observed XML response member {id="csa-read-processed-packet-count-observed-xml-response-member"}

<code-block lang="xml">&lt;processed-packet-count&gt;8&lt;/processed-packet-count&gt;</code-block>

### Effect and response {id="csa-read-processed-packet-count-effect-and-response"}

The leaf reports the packet count published in this snapshot. The read does not reset or advance the counter.

## Get OpenFlow Switches of the CSA {id="csa-read-openflow-switches"}

### Request {id="csa-read-openflow-switches-request"}

<code-block lang="http" src="../../src/main/postman/csa-restconf/get-openflow-switches-of-the-csa.http"/>

### RESTCONF resource and YANG objects {id="csa-read-openflow-switches-restconf-resource-and-yang-objects"}

The URL is `GET /restconf/data/csa:controller-state?fields=openflow-switch`, which projects the OpenFlow-switch list and its connectors.

#### YANG response body structure {id="csa-read-openflow-switches-yang-response-body-structure"}

<code-block lang="text">container csa:controller-state { config false;
  list openflow-switch [key: logical-name] {
    leaf logical-name: string; // list key
    leaf management-ip: string; leaf node-id: string; leaf encoded-node-id: string;
    list connector [key: connector-id] {
      leaf connector-id: string; // list key
      leaf name: string; leaf port-number: uint32; leaf hardware-address: string;
      leaf live: boolean; leaf link-down: boolean;
    }
  }
}</code-block>

#### Observed XML response excerpt {id="csa-read-openflow-switches-observed-xml-response-excerpt"}

<code-block lang="xml">&lt;openflow-switch&gt;
    &lt;logical-name&gt;FOXTROT&lt;/logical-name&gt;
    &lt;node-id&gt;openflow:179101492710982&lt;/node-id&gt;
    &lt;connector&gt;
        &lt;connector-id&gt;openflow:179101492710982:2&lt;/connector-id&gt;
        &lt;link-down&gt;false&lt;/link-down&gt;
        &lt;name&gt;core-lsr4&lt;/name&gt;
        &lt;port-number&gt;2&lt;/port-number&gt;
        &lt;live&gt;true&lt;/live&gt;
        &lt;hardware-address&gt;aa:c1:ab:c4:f1:67&lt;/hardware-address&gt;
    &lt;/connector&gt;
    &lt;management-ip&gt;172.21.121.16&lt;/management-ip&gt;
    &lt;encoded-node-id&gt;openflow%3A179101492710982&lt;/encoded-node-id&gt;
&lt;/openflow-switch&gt;</code-block>

### Effect and response {id="csa-read-openflow-switches-effect-and-response"}

The request reads OpenFlow inventory that the CSA published. It does not refresh inventory or provision a flow.

## Get Classification Service Cache of the CSA {id="csa-read-classification-service-cache"}

### Request {id="csa-read-classification-service-cache-request"}

<code-block lang="http" src="../../src/main/postman/csa-restconf/get-classification-service-cache-of-the-csa.http"/>

### RESTCONF resource and YANG objects {id="csa-read-classification-service-cache-restconf-resource-and-yang-objects"}

The URL is `GET /restconf/data/csa:controller-state?fields=classification-service-cache-entry`.

#### YANG response body structure {id="csa-read-classification-service-cache-yang-response-body-structure"}

<code-block lang="text">container csa:controller-state { config false;
  list classification-service-cache-entry [key: entry-id] {
    leaf entry-id: string; // list key
    leaf ingress-switch-name: string; leaf eth-type: uint32; leaf ip-protocol: uint32;
    leaf canonical-service-port: uint32;
    // uses classification-result-fields
    leaf request-id: string; leaf model-name: string;
    leaf class-id: uint32; leaf class-name: string; leaf confidence: string;
    leaf-list probability: string; leaf profile-name: string;
    leaf dscp: uint32; leaf mpls-tc: uint32;
    leaf requested-bandwidth-kbps: uint64; leaf cached-at: string; leaf expires-at: string;
  }
}</code-block>

#### Observed XML response evidence {id="csa-read-classification-service-cache-observed-xml-response-evidence"}

<code-block lang="xml">&lt;cache-state&gt;
    &lt;classification-service-entry-count&gt;0&lt;/classification-service-entry-count&gt;
&lt;/cache-state&gt;</code-block>

### Effect and response {id="csa-read-classification-service-cache-effect-and-response"}

No service-cache entry was present in the supplied snapshot. The request reads application cache state and does not run classification.
