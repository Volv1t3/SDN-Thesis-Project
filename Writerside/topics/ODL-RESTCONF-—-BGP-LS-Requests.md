# OpenDaylight BGP-LS RESTCONF Requests

This page documents the BGP-LS RESTCONF requests used to configure the OpenDaylight controller. Each request is described both as an HTTP operation and as a change to a YANG-modeled object. The BGP speaker and protocol resource must exist before adding any peer. The model citations use the BGPCEP `v0.23.1` source commit `50f3ac909751be46354c44619a989e2a940a4e2c` and the MD-SAL `v15.0.1` source commit `90214b900e62ddd7175dbfc8df20f217efd44762`. These are release-source matches for Vanadium SR1. They are not a substitute for the deployed controller's advertised YANG library. Despite this, the expected libraries and their behavior <b>should match the behavior defined here</b>, as the models used and the endpoints used were reported both by the internal <b>Swagger Based RESTCONF explorer</b> and the corresponding source code that was used for the controller-side application, and the controller build as reported by the project's sources on GitHub.

<show-structure for="chapter" depth="2"/>

## Required RESTCONF Calls for Controller Initialization {id="bgpls-required-controller-initialization"}

Create the BGP-LS speaker, configure its four peers, and associate its RIB with the BGP-LS topology before relying on topology data. The controller-side application reads the topology-list resource to discover available topologies. The graph request remains in this section because it is the collection's companion operational view for controller validation.

The requests presented here corresponds to the compendium of both controller-side application request, and external RESTCONF requests used for configuring the controller for the environment to work properly. As mentioned above, the BGP-LS speaker, which establishes the BGP process identity and its configuration as a link state BGP extension rather than a normal BGP implementation. It is in the RIB formed by this process, defined under the name of <b><code>sma-bgp-ls</code></b> that will compile and create the corresponding information store for the incoming NLRIs from the peers that are being configured later on. From this topology, the internal graph is created and used to represent the topological information coming through the BGP-LS sessions from the MPLS-TE core towards the controller, as well as any traffic engineering attributes associated with received NLRIs into a single topology, representing the information received, devices as nodes and links as edges, for their use in the computation engine in later requests.

### Create the OpenDaylight BGP-LS speaker {id="create-odl-bgpls-speaker"}

This request creates the configured <code>sma-bgp-ls</code> BGP protocol instance in the existing <code>global-bgp</code> network instance. It must precede the peer requests. The purpose of this request is to configure the underlying global bgp instance that runs within the controller in order for it to be configured with a specific <i>autonomous system number</i> as well as a specific <i>router ID</i> that will be used for the general configuration of the protocol. Through this request, we are configuring both the protocol and the iformation that will be carried over the sessions formed through this BGP protocol, in this case, configuring them to be link state or BGP-LS information sent over by peers.

In the model presented below, it is possible to define multiple <b><i>peers</i></b> already through the speaker configuration, but the implementation preserved the separation of these requests in order to make the code self documenting and clearer. 

#### Request {id="create-odl-bgpls-speaker-request"}

<code-block lang="http" src="../../src/main/postman/bgp-ls/create-odl-bgp-ls-speaker.http" emphasize-lines="22,29,30,35"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates to RESTCONF without publishing a credential. |
| `Accept` | `application/xml` | Requests XML if the controller returns a representation. |
| `Content-Type` | `application/xml` | Declares the XML YANG payload. |

The trailing `?` in the source URL contains no query parameter.

#### RESTCONF resource and YANG objects {id="create-odl-bgpls-speaker-yang"}

The POST addresses the <code>protocols</code> collection under <code>network-instance[name='global-bgp']</code>. Its body creates a <code>protocol</code> list entry identified by the ordered keys <code>identifier=BGP</code> and <code>name=sma-bgp-ls</code>. The ODL BGPCEP augmentation makes the nested <code>bgp</code> object available below that entry.

<compare type="top-bottom" first-title="Request payload (XML)" second-title="YANG model composition (abridged)">
<code-block lang="xml">
&lt;protocol xmlns="http://openconfig.net/yang/network-instance"&gt;
  &lt;name&gt;sma-bgp-ls&lt;/name&gt;
  &lt;identifier&gt;BGP&lt;/identifier&gt;
  &lt;bgp&gt;&lt;global&gt;&lt;config&gt;
    &lt;router-id&gt;172.21.121.100&lt;/router-id&gt;&lt;as&gt;65000&lt;/as&gt;
  &lt;/config&gt;&lt;afi-safis&gt;&lt;afi-safi&gt;
    &lt;afi-safi-name&gt;LINKSTATE&lt;/afi-safi-name&gt;
  &lt;/afi-safi&gt;&lt;/afi-safis&gt;&lt;/global&gt;&lt;/bgp&gt;
&lt;/protocol&gt;
</code-block>
<code-block lang="text">
container network-instances {
  list network-instance [key: name] {
    leaf name: string;                       // global-bgp
    container protocols {
      list protocol [key: identifier name] {
        leaf identifier: identityref;        // BGP
        leaf name: string;                   // sma-bgp-ls
        // bgp-openconfig-extensions augments this list entry
        container bgp { uses openconfig-bgp:bgp-top; }
          container global { container config {
            leaf as { type inet:as-number; mandatory true; }
            leaf router-id { type inet:ipv4-address; }
          }}
          container afi-safis { list afi-safi [key: afi-safi-name] {
            leaf afi-safi-name: identityref; // LINKSTATE
          }}
      }
    }
  }
}
identity LINKSTATE { base afi-safi-type; }
</code-block>
</compare>

The [ODL network-instance model](https://github.com/opendaylight/bgpcep/blob/50f3ac909751be46354c44619a989e2a940a4e2c/bgp/openconfig-api/src/main/yang/openconfig-network-instance.yang) owns the protocol list. [bgp-openconfig-extensions.yang](https://github.com/opendaylight/bgpcep/blob/50f3ac909751be46354c44619a989e2a940a4e2c/bgp/openconfig-api/src/main/yang/bgp-openconfig-extensions.yang) augments it with BGP, while [openconfig-bgp.yang](https://github.com/opendaylight/bgpcep/blob/50f3ac909751be46354c44619a989e2a940a4e2c/bgp/openconfig-api/src/main/yang/openconfig-bgp.yang) defines global ASN/router ID data and the multiprotocol configuration.

| Payload object or field | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `protocol` | List entry | — | Creates the named BGP protocol instance. |
| `identifier` / `name` | Ordered protocol list keys | `BGP` / `sma-bgp-ls` | Identifies this BGP speaker/RIB instance. |
| `router-id` | Global BGP configuration leaf | `172.21.121.100` | Controller-side IPv4 BGP router ID. |
| `as` | Mandatory global configuration leaf | `65000` | Local autonomous-system number. |
| `afi-safi-name` | AFI/SAFI identity key | `LINKSTATE` | Enables BGP-LS for the speaker. |

#### Effect and response {id="create-odl-bgpls-speaker-effect"}

The request creates protocol-instance configuration and declares its global BGP identity plus BGP-LS family; it does not create peers, establish sessions, or create a topology. No <b><code>response body</code></b>was recorded in Postman. 

<tip>
<p>A successful POST-created child is expected to return <code>201 Created</code>, a <code>Location</code> header, and no body under <a href="https://www.rfc-editor.org/rfc/rfc8040.html#section-4.4.1">RFC 8040</a>.</p> 
<p>During the controller evaluation and testing, the response received was always <b><code>201 - Created</code></b></p>
</tip>

### Add LSR1 as a BGP-LS peer {id="add-lsr1-bgpls-peer"}

This request adds the controller-facing address of <code>sma-xrv-lsr1-alpha</code> (<code>10.100.10.1</code>) as a BGP neighbor of the previously created <code>sma-bgp-ls</code> BGP protocol. It configures the neighbor for the BGP Link-State address family so that, once the session is established, BGP-LS UPDATE messages can populate the speaker's RIB. 

This is a crucial step in the creation of the underlying iBGP mesh that contains both the MPLS-TE core devices and the controller, allowing for the continuing transmission of data insofar as a at least one node maintains its session open.

#### Request {id="add-lsr1-bgpls-peer-request"}

<code-block lang="http" src="../../src/main/postman/bgp-ls/add-lsr1-as-a-bgp-ls-peer.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates to the RESTCONF service. Define the credential outside version control. |
| `Accept` | `application/xml` | Requests an XML representation if the controller returns a response body. |
| `Content-Type` | `application/xml` | Declares that the request payload is XML encoded according to the relevant YANG model. |

The request has no query parameters. The request path identifies an existing protocol list entry with the ordered keys <code>identifier=openconfig-policy-types:BGP</code> and <code>name=sma-bgp-ls</code>; RFC 8040 list-key ordering is therefore significant.

#### RESTCONF resource and YANG objects {id="add-lsr1-bgpls-peer-yang-objects"}

<p>
The request targets the <code>neighbors</code> container of the BGP augmentation attached to the already configured <code>sma-bgp-ls</code> protocol. A successful POST creates a child <code>neighbor</code> list entry. It does not create the BGP-LS topology and it does not synchronously establish a TCP or BGP session, rather, OpenDaylight subsequently attempts or accepts the session according to the peer configuration.
</p>
<p>
The resource path follows the object hierarchy below. The first part is the OpenConfig network-instance model. Then <code>bgp-openconfig-extensions:bgp</code> is the OpenDaylight BGPCEP augmentation that contributes the BGP-specific configuration beneath the selected protocol.
</p>
<code-block lang="text">
openconfig-network-instance:network-instances
└─ network-instance[name='global-bgp']
   └─ protocols
      └─ protocol[identifier='openconfig-policy-types:BGP'][name='sma-bgp-ls']
         └─ bgp-openconfig-extensions:bgp
            └─ neighbors
               └─ neighbor[neighbor-address='10.100.10.1']
</code-block>
<p>
Within the <code>neighbors</code> container, <code>neighbor</code> is a list keyed by <code>neighbor-address</code>. Consequently, the address identifies this peer both when it is created and when it is later read or deleted. The <code>afi-safis</code> subtree makes the peer multiprotocol-aware; the selected <code>LINKSTATE</code> identity enables the BGP-LS AFI/SAFI rather than treating the value as an arbitrary string.
</p>

<compare type="top-bottom" first-title="Request payload (XML)" second-title="YANG schema shape (abridged)">
<code-block lang="xml">
&lt;neighbor xmlns="urn:opendaylight:params:xml:ns:yang:bgp:openconfig-extensions"&gt;
    &lt;neighbor-address&gt;10.100.10.1&lt;/neighbor-address&gt;
    &lt;afi-safis&gt;
        &lt;afi-safi&gt;
            &lt;afi-safi-name&gt;LINKSTATE&lt;/afi-safi-name&gt;
        &lt;/afi-safi&gt;
    &lt;/afi-safis&gt;
&lt;/neighbor&gt;
</code-block>
<code-block lang="text">
// ODL BGPCEP extension: attaches BGP to a protocol entry
augment /netinst:network-instances/netinst:network-instance
        /netinst:protocols/netinst:protocol {
  uses openconfig-bgp:bgp-top;
}

// OpenConfig BGP: identifying leaves for the request payload
list neighbor {
  key "neighbor-address";
  leaf neighbor-address {
    type inet:ip-address;
  }
}

list afi-safi {
  key "afi-safi-name";
  leaf afi-safi-name {
    type leafref { path "../config/afi-safi-name"; }
  }
  // The referenced config leaf is an AFI_SAFI_TYPE identityref.
}

// Resulting instantiated tree (abridged)
container bgp {
  list neighbor {
    key "neighbor-address";
    container afi-safis {
      list afi-safi {
        key "afi-safi-name";
      }
    }
  }
}
</code-block>
</compare>

The top panel is the exact XML list entry sent by this project. The bottom panel is an abridged schema shape, not a complete module: it isolates the objects that make the payload addressable and valid. In the JSON RESTCONF encoding, the <code>neighbor</code> and <code>afi-safi</code> YANG lists are represented as arrays while the XML represents this same list structure through repeatable child elements.

The path prefix is supplied by the [OpenConfig network-instance model](https://github.com/openconfig/public/blob/master/release/models/network-instance/openconfig-network-instance.yang): <code>network-instance</code> is keyed by <code>name</code>, and <code>protocol</code> by <code>identifier name</code>. The [OpenDaylight BGPCEP augmentation](https://github.com/opendaylight/bgpcep/blob/50f3ac909751be46354c44619a989e2a940a4e2c/bgp/openconfig-api/src/main/yang/bgp-openconfig-extensions.yang) contributes <code>bgp-openconfig-extensions:bgp</code> by using the OpenConfig BGP top-level grouping; it does not redefine the peer list. The [OpenDaylight BGP-LS guide](https://docs.opendaylight.org/projects/bgpcep/en/latest/bgp/bgp-user-guide-linkstate-family.html) shows this same RESTCONF target and peer-creation structure. The [OpenConfig BGP neighbor model](https://github.com/openconfig/public/blob/master/release/models/bgp/openconfig-bgp-neighbor.yang) defines the canonical <code>neighbor</code> and <code>afi-safi</code> list relationships, while the [multiprotocol model](https://github.com/openconfig/public/blob/master/release/models/bgp/openconfig-bgp-common-multiprotocol.yang) resolves the AFI/SAFI identity type. Finally, the [OpenDaylight `LINKSTATE` identity](https://github.com/opendaylight/bgpcep/blob/50f3ac909751be46354c44619a989e2a940a4e2c/bgp/openconfig-api/src/main/yang/bgp-openconfig-extensions.yang) derives from that identity family and denotes BGP-LS (AFI/SAFI 16388/71), which is why <code>LINKSTATE</code> has protocol semantics rather than being an unconstrained label.

| Payload object or field | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `neighbor` | List entry | — | Adds one remote BGP peer below the selected BGP protocol. |
| `neighbor-address` | List key and IP-address leaf | `10.100.10.1` | The controller-facing BGP address of LSR1 Alpha; uniquely identifies this neighbor. |
| `afi-safis` | Container | — | Holds the address-family configuration enabled for this peer. |
| `afi-safi` | List entry | — | Selects one AFI/SAFI configuration. |
| `afi-safi-name` | Identity/key leaf | `LINKSTATE` | Selects the BGP Link-State AFI/SAFI, enabling the peer to exchange BGP-LS information. |

The surrounding <code>network-instance</code> and <code>protocol</code> objects come from the OpenConfig network-instance model. The BGP neighbor list and its AFI/SAFI subtree follow the OpenConfig BGP model, while the BGP container and the <code>LINKSTATE</code> identity are supplied by OpenDaylight's BGPCEP OpenConfig extensions.

#### Effect and response {id="add-lsr1-bgpls-peer-effect-response"}

On success, the configured data store contains a neighbor entry for <code>10.100.10.1</code> below <code>sma-bgp-ls</code>. Per [RFC 8040 POST semantics](https://www.rfc-editor.org/rfc/rfc8040.html#section-4.4.1), creating this child resource returns <code>201 Created</code>, no response body, and a <code>Location</code> header that identifies the created neighbor. The source Postman collection did not record an observed response. If the same neighbor key already exists, the request fails with <code>409 Conflict</code>.

The controller uses BGP's default destination port of <code>179</code> when no explicit transport override is configured. That default is model behavior; this request does not itself set a port, peer AS, timers, or transport parameters.

### Add LSR2 as a BGP-LS peer {id="add-lsr2-bgpls-peer"}

This creates the neighbor entry for LSR2 Alpha's controller-facing address, <code>10.100.20.1</code>, below the existing <code>sma-bgp-ls</code> BGP protocol.

#### Request {id="add-lsr2-bgpls-peer-request"}

<code-block lang="http" src="../../src/main/postman/bgp-ls/add-lsr2-as-bgp-ls-peer.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates to RESTCONF; define the credential outside version control. |
| `Accept` | `application/xml` | Requests XML if the controller returns a representation. |
| `Content-Type` | `application/xml` | Declares the XML YANG payload. |

There are no query parameters.

#### RESTCONF resource and YANG objects {id="add-lsr2-bgpls-peer-yang"}

The endpoint selects <code>network-instance[name='global-bgp']</code>, then <code>protocol[identifier='BGP'][name='sma-bgp-ls']</code>, and posts a child under the OpenDaylight BGPCEP <code>bgp-openconfig-extensions:bgp/neighbors</code> augmentation. The protocol list keys are ordered <code>identifier name</code> in the RESTCONF URI.

<compare type="left-right" first-title="Request payload (XML)" second-title="YANG model composition (abridged)">
<code-block lang="xml">
&lt;neighbor xmlns="urn:opendaylight:params:xml:ns:yang:bgp:openconfig-extensions"&gt;
  &lt;neighbor-address&gt;10.100.20.1&lt;/neighbor-address&gt;
  &lt;afi-safis&gt;&lt;afi-safi&gt;&lt;afi-safi-name&gt;LINKSTATE&lt;/afi-safi-name&gt;&lt;/afi-safi&gt;&lt;/afi-safis&gt;
&lt;/neighbor&gt;
</code-block>
<code-block lang="text">
augment .../network-instance/protocols/protocol {
  uses openconfig-bgp:bgp-top; // ODL BGPCEP extension
}
list neighbor { key "neighbor-address";
  leaf neighbor-address { type inet:ip-address; }
}
list afi-safi { key "afi-safi-name"; }
identity LINKSTATE { base afi-safi-type; } // AFI/SAFI 16388/71
</code-block>
</compare>

OpenDaylight's [BGPCEP extension model](https://github.com/opendaylight/bgpcep/blob/50f3ac909751be46354c44619a989e2a940a4e2c/bgp/openconfig-api/src/main/yang/bgp-openconfig-extensions.yang) attaches the BGP model to a protocol entry. Its bundled [network-instance model](https://github.com/opendaylight/bgpcep/blob/50f3ac909751be46354c44619a989e2a940a4e2c/bgp/openconfig-api/src/main/yang/openconfig-network-instance.yang) supplies the enclosing keys, and bundled [BGP model](https://github.com/opendaylight/bgpcep/blob/50f3ac909751be46354c44619a989e2a940a4e2c/bgp/openconfig-api/src/main/yang/openconfig-bgp.yang) supplies the neighbor list. <code>LINKSTATE</code> is the ODL extension identity for BGP-LS, not a free-form label.

| Payload object or field | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `neighbor` | List entry | — | Creates one remote BGP peer. |
| `neighbor-address` | List key; IP-address leaf | `10.100.20.1` | LSR2's controller-facing BGP address. |
| `afi-safis` / `afi-safi` | Container / keyed list | — | Selects BGP multiprotocol families for this peer. |
| `afi-safi-name` | AFI/SAFI identity key | `LINKSTATE` | Enables the BGP Link-State family. |

#### Effect and response {id="add-lsr2-bgpls-peer-effect"}

The configuration datastore gains the LSR2 neighbor under <code>sma-bgp-ls</code>. It does not synchronously establish a session, receive UPDATEs, populate the Loc-RIB, or create a topology. The collection records no observed response. Per [RFC 8040](https://www.rfc-editor.org/rfc/rfc8040.html#section-4.4.1), a new child created by POST returns <code>201 Created</code>, no body, and a <code>Location</code> header; an existing key returns <code>409 Conflict</code>.

### Add LSR3 as a BGP-LS peer {id="add-lsr3-bgpls-peer"}

This creates the equivalent BGP-LS neighbor entry for LSR3 Charlie at <code>10.100.30.1</code>.

#### Request {id="add-lsr3-bgpls-peer-request"}

<code-block lang="http" src="../../src/main/postman/bgp-ls/add-lsr3-as-bpg-ls-peer.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates to RESTCONF; define the credential outside version control. |
| `Accept` | `application/xml` | Requests XML if a representation is returned. |
| `Content-Type` | `application/xml` | Declares the XML YANG payload. |

There are no query parameters.

#### RESTCONF resource and YANG objects {id="add-lsr3-bgpls-peer-yang"}

The resource hierarchy is the same as LSR2: the POST enters the ODL BGP augmentation beneath <code>protocol[identifier='BGP'][name='sma-bgp-ls']</code> and creates one neighbor list entry. Only the list key changes.

<compare type="left-right" first-title="Request payload (XML)" second-title="YANG model composition (abridged)">
<code-block lang="xml">
&lt;neighbor xmlns="urn:opendaylight:params:xml:ns:yang:bgp:openconfig-extensions"&gt;
  &lt;neighbor-address&gt;10.100.30.1&lt;/neighbor-address&gt;
  &lt;afi-safis&gt;&lt;afi-safi&gt;&lt;afi-safi-name&gt;LINKSTATE&lt;/afi-safi-name&gt;&lt;/afi-safi&gt;&lt;/afi-safis&gt;
&lt;/neighbor&gt;
</code-block>
<code-block lang="text">
container network-instances {
  list network-instance [key: name] {
    container protocols { list protocol [key: identifier name] {
      leaf identifier: identityref;
      leaf name: string;
      container bgp {                         // ODL augmentation
        container neighbors { list neighbor [key: neighbor-address] {
          leaf neighbor-address { type inet:ip-address; }
          container afi-safis { list afi-safi [key: afi-safi-name] {
            leaf afi-safi-name: identityref;  // LINKSTATE
          }}
        }}
      }
    }}
  }
}
</code-block>
</compare>

The [ODL BGPCEP extension](https://github.com/opendaylight/bgpcep/blob/50f3ac909751be46354c44619a989e2a940a4e2c/bgp/openconfig-api/src/main/yang/bgp-openconfig-extensions.yang) supplies <code>bgp</code>; [OpenDaylight's BGP model](https://github.com/opendaylight/bgpcep/blob/50f3ac909751be46354c44619a989e2a940a4e2c/bgp/openconfig-api/src/main/yang/openconfig-bgp.yang) defines the direct <code>inet:ip-address</code> peer key; and the [Link-State guide](https://docs.opendaylight.org/projects/bgpcep/en/latest/bgp/bgp-user-guide-linkstate-family.html#bgp-peer) documents this peer-creation shape.

| Payload object or field | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `neighbor` | List entry | — | Creates the remote-peer configuration. |
| `neighbor-address` | List key and IP-address leaf | `10.100.30.1` | LSR3's controller-facing BGP address. |
| `afi-safis` / `afi-safi` | Container / keyed list | — | Holds enabled per-peer address families. |
| `afi-safi-name` | Identity key | `LINKSTATE` | Selects BGP-LS AFI/SAFI 16388/71. |

#### Effect and response {id="add-lsr3-bgpls-peer-effect"}

The request adds only configured peer data under <code>sma-bgp-ls</code>; session establishment and route ingestion are later protocol effects. The source collection has no observed response. A successful new POST is expected to return <code>201 Created</code> with <code>Location</code> and no body; a duplicate key is <code>409 Conflict</code> under [RFC 8040](https://www.rfc-editor.org/rfc/rfc8040.html#section-4.4.1).

### Add LSR4 as a BGP-LS peer {id="add-lsr4-bgpls-peer"}

This creates the BGP-LS neighbor entry for LSR4 Delta at <code>10.100.40.1</code>.

#### Request {id="add-lsr4-bgpls-peer-request"}

<code-block lang="http" src="../../src/main/postman/bgp-ls/add-lsr4-as-bgp-ls-peer.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates to RESTCONF; define the credential outside version control. |
| `Accept` | `application/xml` | Requests XML if a representation is returned. |
| `Content-Type` | `application/xml` | Declares the XML YANG payload. |

There are no query parameters.

#### RESTCONF resource and YANG objects {id="add-lsr4-bgpls-peer-yang"}

The POST targets the same <code>neighbors</code> collection under the existing <code>sma-bgp-ls</code> protocol. The peer identity is the <code>neighbor-address</code> list key, while the BGPCEP <code>LINKSTATE</code> identity selects the route family.

<compare type="left-right" first-title="Request payload (XML)" second-title="YANG model composition (abridged)">
<code-block lang="xml">
&lt;neighbor xmlns="urn:opendaylight:params:xml:ns:yang:bgp:openconfig-extensions"&gt;
  &lt;neighbor-address&gt;10.100.40.1&lt;/neighbor-address&gt;
  &lt;afi-safis&gt;&lt;afi-safi&gt;&lt;afi-safi-name&gt;LINKSTATE&lt;/afi-safi-name&gt;&lt;/afi-safi&gt;&lt;/afi-safis&gt;
&lt;/neighbor&gt;
</code-block>
<code-block lang="text">
augment .../protocol { uses openconfig-bgp:bgp-top; }
container neighbors {
  list neighbor { key "neighbor-address";
    leaf neighbor-address { type inet:ip-address; }
  }
  list afi-safi { key "afi-safi-name"; }
}
identity LINKSTATE { base bgp-types:afi-safi-type; }
</code-block>
</compare>

The precise ODL augmentation and <code>LINKSTATE</code> identity are declared in [bgp-openconfig-extensions.yang](https://github.com/opendaylight/bgpcep/blob/50f3ac909751be46354c44619a989e2a940a4e2c/bgp/openconfig-api/src/main/yang/bgp-openconfig-extensions.yang). The [BGP model](https://github.com/opendaylight/bgpcep/blob/50f3ac909751be46354c44619a989e2a940a4e2c/bgp/openconfig-api/src/main/yang/openconfig-bgp.yang) owns the neighbor and AFI/SAFI structure; ODL's [BGP-LS guide](https://docs.opendaylight.org/projects/bgpcep/en/latest/bgp/bgp-user-guide-linkstate-family.html#bgp-peer) documents the matching RESTCONF operation.

| Payload object or field | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `neighbor` | List entry | — | Adds one remote BGP peer. |
| `neighbor-address` | List key; IP-address leaf | `10.100.40.1` | LSR4's controller-facing BGP address. |
| `afi-safis` / `afi-safi` | Container / keyed list | — | Enables a per-peer multiprotocol family. |
| `afi-safi-name` | AFI/SAFI identity key | `LINKSTATE` | Selects BGP-LS information exchange. |

#### Effect and response {id="add-lsr4-bgpls-peer-effect"}

The configured peer is added beneath the existing protocol, but no session, Loc-RIB route, or topology is created synchronously. No Postman response was recorded. The expected result is <code>201 Created</code> with a <code>Location</code> header and no body for a new child, or <code>409 Conflict</code> for the same existing key, as defined by [RFC 8040](https://www.rfc-editor.org/rfc/rfc8040.html#section-4.4.1).

### Create the BGP-LS Link-State topology {id="create-bgpls-linkstate-topology"}

This PUT declares a topology resource that is BGP-LS aware and associates it with the <code>sma-bgp-ls</code> RIB.

#### Request {id="create-bgpls-linkstate-topology-request"}

<code-block lang="http" src="../../src/main/postman/bgp-ls/create-bgp-ls-link-state-topology.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates to RESTCONF without publishing a credential. |
| `Accept` | `application/xml` | Requests XML if a representation is returned. |
| `Content-Type` | `application/xml` | Declares the XML YANG payload. |

There are no query parameters. The topology key in the URI and the payload's <code>topology-id</code> must match.

#### RESTCONF resource and YANG objects {id="create-bgpls-linkstate-topology-yang"}

The path addresses <code>network-topology:network-topology/topology[ topology-id='sma-bgp-linkstate-topology' ]</code>. The base topology model provides the list and <code>topology-types</code> container; ODL BGPCEP augments it with the BGP-LS presence marker and conditionally permits <code>rib-id</code> when that marker is present.

<compare type="left-right" first-title="Request payload (XML)" second-title="YANG model composition (abridged)">
<code-block lang="xml">
&lt;topology xmlns="urn:TBD:params:xml:ns:yang:network-topology"&gt;
  &lt;topology-id&gt;sma-bgp-linkstate-topology&lt;/topology-id&gt;
  &lt;topology-types&gt;&lt;bgp-linkstate-topology /&gt;&lt;/topology-types&gt;
  &lt;rib-id&gt;sma-bgp-ls&lt;/rib-id&gt;
&lt;/topology&gt;
</code-block>
<code-block lang="text">
container network-topology {
  list topology { key "topology-id";
    container topology-types { }
  }
}
augment .../topology-types { container bgp-linkstate-topology; }
augment .../topology {
  when ".../topology-types/...:bgp-linkstate-topology";
  leaf rib-id { type rib:rib-id; }
}
</code-block>
</compare>

The base [network-topology model](https://github.com/opendaylight/mdsal/blob/90214b900e62ddd7175dbfc8df20f217efd44762/model/ietf-topology/src/main/yang/network-topology%402013-10-21.yang) owns the topology list. [odl-bgp-topology-types.yang](https://github.com/opendaylight/bgpcep/blob/50f3ac909751be46354c44619a989e2a940a4e2c/bgp/topology-provider/src/main/yang/odl-bgp-topology-types.yang) adds the BGP-LS marker, and [odl-bgp-topology-config.yang](https://github.com/opendaylight/bgpcep/blob/50f3ac909751be46354c44619a989e2a940a4e2c/bgp/topology-provider/src/main/yang/odl-bgp-topology-config.yang) adds the conditional RIB association. The BGP topology provider later maps BGP-LS data into this declared topology; this request does not itself receive routes or establish peers.

| Payload object or field | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `topology` | Topology list entry | — | Creates or replaces the selected topology resource. |
| `topology-id` | List key | `sma-bgp-linkstate-topology` | Project-selected topology identity; must match the URI. |
| `topology-types` | Base container | — | Holds topology-type selections. |
| `bgp-linkstate-topology` | ODL BGPCEP presence augmentation | Present | Marks the topology as BGP-LS aware. |
| `rib-id` | Conditional ODL augmentation leaf | `sma-bgp-ls` | Associates the topology with the named BGP RIB. |

#### Effect and response {id="create-bgpls-linkstate-topology-effect"}

PUT creates the topology resource when absent or replaces its representation when present. The collection records no observed response. Under [RFC 8040 PUT semantics](https://www.rfc-editor.org/rfc/rfc8040.html#section-4.5), expected success is <code>201 Created</code> for a new resource or <code>204 No Content</code> for replacement. The effect is topology configuration associated with the RIB, not synchronous BGP session creation or route ingestion.

### List topologies {id="list-topologies"}

#### Request {id="list-topologies-request"}

<code-block lang="http" src="../../src/main/postman/bgp-ls/list-topologies.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates to RESTCONF without publishing a credential. |
| `Accept` | `application/xml` | Requests an XML representation of the selected resource. |

The `content=nonconfig` query selector requests operational topology data only. `OdlRestconfDataClient.getNetworkTopologyList()` sends this same resource path for controller-side topology discovery.

#### RESTCONF resource and YANG objects {id="list-topologies-yang"}

The endpoint selects the top-level <code>network-topology:network-topology</code> container and its topology list. It does not select one topology by key. [Network topology model](https://github.com/opendaylight/mdsal/blob/90214b900e62ddd7175dbfc8df20f217efd44762/model/ietf-topology/src/main/yang/network-topology%402013-10-21.yang).

<compare type="left-right" first-title="Request body" second-title="YANG resource shape (abridged)">
<code-block lang="text">No request body: the URI and query parameters select the target resource.</code-block>
<code-block lang="text">
container network-topology {
  list topology { key "topology-id";
    leaf topology-id;
  }
}
</code-block>
</compare>

| Resource element | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `network-topology` | Top-level container | — | Holds all registered topology entries. |
| `topology` | Keyed list | Returned by the controller | Lists topology resources. |
| `content` | RFC 8040 query parameter | `nonconfig` | Requests operational nodes only. |

#### Effect and response {id="list-topologies-effect"}

This GET does not modify any topology. A successful response is expected to be <code>200 OK</code> with operational topology entries. No observed response is stored.

### Get topology graph information {id="get-topology-graph-information"}

#### Request {id="get-topology-graph-information-request"}

<code-block lang="http" src="../../src/main/postman/bgp-ls/get-topology-graph-information.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates to RESTCONF without publishing a credential. |
| `Accept` | `application/xml` | Requests an XML representation of the selected resource. |

The `content=nonconfig` query selector requests operational data only. The source collection identifies this graph resource, but `OdlOperationsClient` does not issue this GET. It only invokes path-computation and PCEP update operations.

#### RESTCONF resource and YANG objects {id="get-topology-graph-information-yang"}

The endpoint selects the <code>graph:graph-topology</code> container from [graph.yang](https://github.com/opendaylight/bgpcep/blob/50f3ac909751be46354c44619a989e2a940a4e2c/graph/graph-api/src/main/yang/graph.yang), module <code>graph</code>, namespace <code>urn:opendaylight:params:xml:ns:yang:graph</code>, revision <code>2025-01-15</code>. The supplied controller response selects one <code>graph</code> list entry named <code>ted://sma-bgp-linkstate-topology</code>. Its repeated <code>edge</code>, <code>vertex</code>, <code>prefix</code>, and <code>unreserved-bandwidth</code> elements correspond to keyed YANG lists rather than unstructured XML repetition.

The model does not mark this tree <code>config false</code>. The matching [graph server implementation](https://github.com/opendaylight/bgpcep/blob/50f3ac909751be46354c44619a989e2a940a4e2c/graph/graph-impl/src/main/java/org/opendaylight/graph/impl/ConnectedGraphServer.java) publishes graph objects to the operational datastore, which explains the response to <code>content=nonconfig</code> without changing the model's declared schema nodes.


<code-block lang="text">
container graph-topology {
  list graph [key: name] {
    leaf name { type string; } // ted://sma-bgp-linkstate-topology
    leaf domain-scope {
      type enumeration { intra-domain; inter-domain; }
      default intra-domain;
    }
    leaf asn { type uint32; }
    list vertex [key: vertex-id] {
      leaf vertex-id { type uint64; mandatory true; }
      leaf name { type string; }
      leaf router-id { type inet:ipv4-address; }
      leaf type { type node-type; }
      leaf asn { type uint32; }
      container sr-node-attributes { /* SR fields omitted */ }
    }
    list edge [key: edge-id] {
      leaf edge-id { type uint64; mandatory true; }
      leaf local-vertex-id { type uint64; }
      leaf remote-vertex-id { type uint64; }
      leaf name { type string; }
      container edge-attributes {
        leaf metric { type uint32; }
        leaf local-address { type inet:ipv4-address; }
        leaf remote-address { type inet:ipv4-address; }
        container extended-metric {
          leaf-list extended-admin-group { type uint32; }
        }
        container te-metric {
          leaf metric { type uint32; }
          leaf max-link-bandwidth { type decimal-bandwidth; }
          leaf max-resv-link-bandwidth { type decimal-bandwidth; }
          leaf admin-group { type uint32; }
          list unreserved-bandwidth [key: class-type] {
            leaf class-type { type uint8 { range "0..7"; } }
            leaf bandwidth { type decimal-bandwidth; }
          }
        }
      }
    }
    list prefix [key: prefix] {
      leaf prefix { type inet:ip-prefix; mandatory true; }
      leaf vertex-id { type uint64; }
      leaf igp-metric { type uint32; }
      container sr-prefix-attributes { /* SR fields omitted */ }
    }
  }
}
</code-block>

| Resource element | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `graph:graph-topology` | Top-level container | — | Root container from the BGPCEP `graph` model. |
| `graph` | Keyed list | `ted://sma-bgp-linkstate-topology` | Selects one named traffic-engineering graph. |
| `vertex` / `edge` / `prefix` | Keyed child lists | Returned by the controller | Describe graph vertices, directed edges, and address prefixes. |
| `unreserved-bandwidth` | Keyed list under `edge-attributes/te-metric` | Returned per class type | Holds the available bandwidth for class types zero through seven. |
| `content` | RFC 8040 query parameter | `nonconfig` | Requests only operational data. |

#### Effect and response {id="get-topology-graph-information-effect"}

This GET is read-only. The expected successful response is <code>200 OK</code>. The request artifact records no response, but the supplied controller response contains an operational graph with vertices, directed edges, prefixes, and TE metrics. A successful read describes the controller's current graph projection and does not itself establish current BGP peer or router reachability.

## Complete RESTCONF Calls {id="bgpls-complete-restconf-calls"}

The remaining requests inspect configured and operational BGP-LS data, route state, and the lifecycle of the BGP-LS topology.

### Check whether the BGPCEP BGP subsystem is active {id="check-bgprib-active"}

#### Request {id="check-bgprib-active-request"}

<code-block lang="http" src="../../src/main/postman/bgp-ls/check-if-bgpcep-bgp-subsystem-is-active.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates to RESTCONF without publishing a credential. |
| `Accept` | `application/xml` | Requests an XML representation of the selected resource. |

The `content=nonconfig` query selector requests operational data only.

#### RESTCONF resource and YANG objects {id="check-bgprib-active-yang"}

The endpoint selects the top-level <code>bgp-rib:bgp-rib</code> container. It is the BGPCEP RIB service root; its <code>rib</code> list exposes the BGP speaker instances installed in the controller. OpenDaylight documents this resource as the RESTCONF presence check for the BGP plugin. [Running BGP guide](https://docs.opendaylight.org/projects/bgpcep/en/latest/bgp/bgp-user-guide-running-bgp.html), [BGP RIB model](https://github.com/opendaylight/bgpcep/blob/50f3ac909751be46354c44619a989e2a940a4e2c/bgp/rib-api/src/main/yang/bgp-rib.yang).

<compare type="left-right" first-title="Request body" second-title="YANG resource shape (abridged)">
<code-block lang="text">No request body: the URI and query parameters select the target resource.</code-block>
<code-block lang="text">
container bgp-rib {
  list rib { key "id"; config false;
    leaf id { type rib-id; }
    container loc-rib {
      list tables { key "afi safi";
        leaf afi: identityref;
        leaf safi: identityref;
        // route choice and per-family route containers follow
      }
    }
  }
}
</code-block>
</compare>

| Resource element | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `bgp-rib` | Top-level operational container | — | Root of BGPCEP RIB data. |
| `rib` | Operational list | Returned by the controller | Each BGP speaker/RIB instance. |
| `content` | RFC 8040 query parameter | `nonconfig` | Restricts the response to non-configuration data. |

#### Effect and response {id="check-bgprib-active-effect"}

This GET does not modify the controller. A successful response is expected to be <code>200 OK</code> with the available RIB instances; the source collection records no observed response.

### Read the configured BGP protocol {id="read-configured-bgpls-protocol"}

#### Request {id="read-configured-bgpls-protocol-request"}

<code-block lang="http" src="../../src/main/postman/bgp-ls/read-the-configured-bgp-protocol.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates to RESTCONF without publishing a credential. |
| `Accept` | `application/xml` | Requests an XML representation of the selected resource. |

There are no query parameters.

#### RESTCONF resource and YANG objects {id="read-configured-bgpls-protocol-yang"}

The URI addresses the OpenConfig protocol list entry whose ordered keys are <code>identifier=openconfig-policy-types:BGP</code> and <code>name=sma-bgp-ls</code>, under <code>network-instance[name=global-bgp]</code>. The BGPCEP extension augments that protocol entry with the BGP subtree. [Network-instance model](https://github.com/opendaylight/bgpcep/blob/50f3ac909751be46354c44619a989e2a940a4e2c/bgp/openconfig-api/src/main/yang/openconfig-network-instance.yang), [BGP OpenConfig extension](https://github.com/opendaylight/bgpcep/blob/50f3ac909751be46354c44619a989e2a940a4e2c/bgp/openconfig-api/src/main/yang/bgp-openconfig-extensions.yang).

<compare type="left-right" first-title="Request body" second-title="YANG resource shape (abridged)">
<code-block lang="text">No request body: the URI and query parameters select the target resource.</code-block>
<code-block lang="text">
container network-instances {
  list network-instance { key "name";
    leaf name: string;
    container protocols {
      list protocol { key "identifier name";
        leaf identifier: identityref;
        leaf name: string;
      }
    }
  }
}
augment .../protocol { uses openconfig-bgp:bgp-top; }
</code-block>
</compare>

| Resource element | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `network-instance` | List key | `global-bgp` | Container holding the BGP protocol instance. |
| `protocol.identifier` | First list key | `BGP` | Selects the BGP protocol type. |
| `protocol.name` | Second list key | `sma-bgp-ls` | Selects this project’s BGP-LS speaker. |

#### Effect and response {id="read-configured-bgpls-protocol-effect"}

This GET is read-only. A successful response is expected to be <code>200 OK</code> with the configured protocol representation; no observed response is stored in the source collection.

### Get BGP-LS Loc-RIB routes {id="get-bgpls-loc-rib-routes"}

#### Request {id="get-bgpls-loc-rib-routes-request"}

<code-block lang="http" src="../../src/main/postman/bgp-ls/get-bgp-ls-routes.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates to RESTCONF without publishing a credential. |
| `Accept` | `application/xml` | Requests an XML representation of the selected resource. |

The `content=nonconfig` query selector requests operational Link-State route data only.

#### RESTCONF resource and YANG objects {id="get-bgpls-loc-rib-routes-yang"}

The path selects <code>rib[id=sma-bgp-ls]</code>, its <code>loc-rib</code>, and the table keyed by the Link-State AFI and SAFI identities. The BGP-LS module augments the table route choice with <code>linkstate-routes</code>. [BGP RIB model](https://github.com/opendaylight/bgpcep/blob/50f3ac909751be46354c44619a989e2a940a4e2c/bgp/rib-api/src/main/yang/bgp-rib.yang), [BGP Link-State model](https://github.com/opendaylight/bgpcep/blob/50f3ac909751be46354c44619a989e2a940a4e2c/bgp/extensions/linkstate/src/main/yang/bgp-linkstate.yang), [ODL Link-State guide](https://docs.opendaylight.org/projects/bgpcep/en/latest/bgp/bgp-user-guide-linkstate-family.html).

<compare type="left-right" first-title="Request body" second-title="YANG resource shape (abridged)">
<code-block lang="text">No request body: the URI and query parameters select the target resource.</code-block>
<code-block lang="text">
list rib { key "id"; config false;
  container loc-rib {
    list tables { key "afi safi";
      case linkstate-routes-case {
        container linkstate-routes {
          list linkstate-route { key "route-key path-id"; }
        }
      }
    }
  }
}
</code-block>
</compare>

| Resource element | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `rib` | Operational list key | `sma-bgp-ls` | Selects the project BGP-LS RIB. |
| `tables` | AFI/SAFI-keyed list | Link-State identities | Selects the BGP-LS table. |
| `linkstate-routes` | Augmented operational container | — | Contains returned BGP-LS routes. |

#### Effect and response {id="get-bgpls-loc-rib-routes-effect"}

This GET does not change configuration or RIB state. The expected successful response is <code>200 OK</code> with XML operational data; the route list can be empty. The collection has no recorded response.

### Delete the BGP-LS Link-State topology {id="delete-bgpls-topology"}

#### Request {id="delete-bgpls-topology-request"}

<code-block lang="http" src="../../src/main/postman/bgp-ls/delete-bgp-ls-link-state-topology.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates to RESTCONF without publishing a credential. |
| `Accept` | `application/xml` | Requests an XML representation of the selected resource. |

There are no query parameters or request body.

#### RESTCONF resource and YANG objects {id="delete-bgpls-topology-yang"}

The URI addresses the topology list instance keyed by <code>topology-id=sma-bgp-linkstate-topology</code>. This is the same base topology object used by the creation request; the BGP-LS type and RIB association are properties of that instance. [Network topology model](https://github.com/opendaylight/mdsal/blob/90214b900e62ddd7175dbfc8df20f217efd44762/model/ietf-topology/src/main/yang/network-topology%402013-10-21.yang), [BGP topology configuration](https://github.com/opendaylight/bgpcep/blob/50f3ac909751be46354c44619a989e2a940a4e2c/bgp/topology-provider/src/main/yang/odl-bgp-topology-config.yang).

<compare type="left-right" first-title="Request body" second-title="YANG resource shape (abridged)">
<code-block lang="text">No request body: the URI and query parameters select the target resource.</code-block>
<code-block lang="text">
container network-topology {
  list topology { key "topology-id";
    leaf topology-id;
    container topology-types { ... }
  }
}
</code-block>
</compare>

| Resource element | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `topology` | Keyed list instance | — | The configured topology resource to remove. |
| `topology-id` | List key | `sma-bgp-linkstate-topology` | Identifies the target topology. |

#### Effect and response {id="delete-bgpls-topology-effect"}

The DELETE removes the addressed topology configuration; it does not delete the BGP RIB or BGP peer configuration. The source collection has no observed response. A successful deletion is normally <code>204 No Content</code> under [RFC 8040](https://www.rfc-editor.org/rfc/rfc8040.html#section-4.6).

### Read the BGP-LS Link-State topology {id="read-bgpls-topology"}

#### Request {id="read-bgpls-topology-request"}

<code-block lang="http" src="../../src/main/postman/bgp-ls/read-bgp-ls-link-state-topology.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates to RESTCONF without publishing a credential. |
| `Accept` | `application/xml` | Requests an XML representation of the selected resource. |

The `content=all` query selector requests both configuration and non-configuration nodes.

#### RESTCONF resource and YANG objects {id="read-bgpls-topology-yang"}

The GET reads the topology list instance keyed by <code>sma-bgp-linkstate-topology</code>. The BGP topology provider maps BGP-LS routes to nodes, links, and prefixes in this resource. [Topology Provider guide](https://docs.opendaylight.org/projects/bgpcep/en/latest/bgp/bgp-user-guide-topology-provider.html), [network-topology model](https://github.com/opendaylight/mdsal/blob/90214b900e62ddd7175dbfc8df20f217efd44762/model/ietf-topology/src/main/yang/network-topology%402013-10-21.yang).

<compare type="left-right" first-title="Request body" second-title="YANG resource shape (abridged)">
<code-block lang="text">No request body: the URI and query parameters select the target resource.</code-block>
<code-block lang="text">
list topology { key "topology-id";
  leaf topology-id: topology-id;
  container topology-types { // BGP-LS marker is an augmentation }
  list node { key "node-id";
    leaf node-id: node-id;
    list termination-point { key "tp-id"; leaf tp-id: tp-id; }
  }
  list link { key "link-id";
    leaf link-id: link-id;
    container source { leaf source-node: node-ref; leaf source-tp: tp-ref; }
    container destination { leaf dest-node: node-ref; leaf dest-tp: tp-ref; }
  }
}
</code-block>
</compare>

| Resource element | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `topology` | Keyed list instance | `sma-bgp-linkstate-topology` | Selects the BGP-LS topology. |
| `content` | RFC 8040 query parameter | `all` | Includes configuration and operational nodes. |
| `node` / `link` | Topology child lists | Returned by the controller | Mapped topology state when available. |

#### Effect and response {id="read-bgpls-topology-effect"}

This GET is read-only. The expected successful response is <code>200 OK</code> with the selected topology representation; no response was recorded in the collection.

### Get network topology information {id="get-network-topology-information"}

#### Request {id="get-network-topology-information-request"}

<code-block lang="http" src="../../src/main/postman/bgp-ls/get-network-topology-information.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates to RESTCONF without publishing a credential. |
| `Accept` | `application/xml` | Requests an XML representation of the selected resource. |

The `content=nonconfig` query selector requests operational topology data only.

#### RESTCONF resource and YANG objects {id="get-network-topology-information-yang"}

This is the keyed variant of the topology read: it addresses <code>topology[topology-id=sma-bgp-linkstate-topology]</code> and excludes its configuration nodes. The base topology model owns the list key; the BGP topology provider supplies mapped operational content when BGP-LS data exists. [Network topology model](https://github.com/opendaylight/mdsal/blob/90214b900e62ddd7175dbfc8df20f217efd44762/model/ietf-topology/src/main/yang/network-topology%402013-10-21.yang), [Topology Provider guide](https://docs.opendaylight.org/projects/bgpcep/en/latest/bgp/bgp-user-guide-topology-provider.html).

<compare type="left-right" first-title="Request body" second-title="YANG resource shape (abridged)">
<code-block lang="text">No request body: the URI and query parameters select the target resource.</code-block>
<code-block lang="text">
list topology { key "topology-id";
  leaf topology-id: topology-id;
  list node { key "node-id"; leaf node-id: node-id; }
  list link { key "link-id";
    leaf link-id: link-id;
    container source { leaf source-node: node-ref; }
    container destination { leaf dest-node: node-ref; }
  }
}
</code-block>
</compare>

| Resource element | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `topology` | Keyed list instance | `sma-bgp-linkstate-topology` | Selects the project BGP-LS topology. |
| `node` / `link` | Operational child lists | Returned by the controller | Mapped BGP-LS topology state. |
| `content` | RFC 8040 query parameter | `nonconfig` | Excludes configured topology declaration fields. |

#### Effect and response {id="get-network-topology-information-effect"}

This GET is read-only. A successful response is expected to be <code>200 OK</code> with the operational representation; no response was recorded in the collection.
