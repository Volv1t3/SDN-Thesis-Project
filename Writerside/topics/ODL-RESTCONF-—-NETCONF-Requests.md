# OpenDaylight NETCONF RESTCONF Requests

This topic documents the 25 HTTP requests saved in the NETCONF collection. Four requests configure NETCONF mounts for the project's XRv routers during controller initialization. The remaining requests inspect connection state, access mounted device data, retrieve YANG schemas, configure interfaces, or use the controller's PCEP and path-computation services.

<show-structure for="chapter" depth="2"/>

The target controller distribution is **OpenDaylight Vanadium SR1 (`0.23.1`)**. NETCONF follows its own `10.0.x` component version line, described in the [Vanadium NETCONF release notes](https://docs.opendaylight.org/en/stable-vanadium/release-notes/projects/netconf.html). The referenced YANG source revisions document the models examined during research. Maintenance-branch snapshots do not establish the exact bundles installed in the running controller. Beyond `yang-ext:mount`, YANG resources are defined by the connected device. Public Cisco and OpenConfig models provide reference schemas when the device's advertised revisions are unavailable.

The saved requests use both `http://172.21.121.100:8182/restconf` and `http://172.21.121.100:8181/rests`. Their addresses, media types, payloads, and credential placeholders are preserved as recorded. Expected RESTCONF responses are distinguished from historical Postman results throughout this topic. No controller requests were executed to prepare this documentation aside from the ones already executed to produce the HTTP request results in the source repository.

## Required RESTCONF Calls for Controller Initialization {id="netconf-required-initialization"}

Run the mount configuration calls for LSR1, LSR2, LSR3, and LSR4 against the controller's NETCONF topology. Each configuration write precedes a separate connection process that includes NETCONF session establishment, schema discovery, and mount availability. An accepted configuration write therefore needs a subsequent operational-state check to establish whether the router is connected. These requests configure management access and leave BGP-LS peers and PCEP LSPs unchanged.

### Mount LSR1 through NETCONF {id="mount-lsr1-netconf"}

This PUT configures the NETCONF connector for `sma-xrv-lsr1-alpha` at its loopback address, `11.11.11.11:830`. The parent `topology-netconf` topology must already contain the NETCONF topology-type marker because this node-level PUT does not create it.

#### Request {id="mount-lsr1-netconf-request"}

<code-block lang="http" src="../../src/main/postman/netconf/mount-lsr1-through-netconf.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates the RESTCONF client to OpenDaylight. The credential is supplied outside version control. |
| `Accept` | `application/xml` | Requests XML if a response representation is returned. |
| `Content-Type` | `application/xml` | Declares the literal XML request body. |

The URL identifies a specific node instance, which PUT creates or replaces. No query parameters are present.

#### RESTCONF resource and YANG objects {id="mount-lsr1-netconf-yang"}

The request selects `network-topology:network-topology` → `topology[topology-id='topology-netconf']` → `node[node-id='sma-xrv-lsr1-alpha']`. The [shared topology module](https://github.com/opendaylight/mdsal/blob/a341bc343a9f319687fbee228032ffac68c3da67/model/ietf-topology/src/main/yang/network-topology@2013-10-21.yang) defines the `topology` and `node` lists and the `node-id` leaf. The [NETCONF topology augmentation](https://github.com/opendaylight/netconf/blob/0b7cb725a4f7224fb57afd4b7e47c29cb8763d24/apps/netconf-topology/src/main/yang/netconf-node-topology.yang) adds the `netconf-node` presence container under the node when ../../nt:topology-types/topology-netconf is satisfied. The value `topology-netconf` identifies a topology list entry.

The NETCONF augmentation uses the `credentials` and `connection-parameters` groupings from [odl-netconf-device.yang](https://github.com/opendaylight/netconf/blob/0b7cb725a4f7224fb57afd4b7e47c29cb8763d24/plugins/netconf-client-mdsal/src/main/yang/odl-netconf-device.yang), in the namespace `urn:opendaylight:netconf:device`. The referenced model assigns the grouping-defined fields to that namespace. The outer `node` and `node-id` fields belong to `urn:TBD:params:xml:ns:yang:network-topology`, while `netconf-node` belongs to `urn:opendaylight:netconf-node-topology`.

The saved XML places the unprefixed credential and connection fields in the `netconf-node` namespace. Those namespaces conflict with the pinned model, so compatibility with this model cannot be established. An earlier controller schema may have accepted the request. The original XML is preserved for comparison.

<compare type="top-bottom" first-title="Request payload (XML)" second-title="YANG model composition (abridged)">
<code-block lang="xml" src="../../src/main/postman/netconf/mount-lsr1-through-netconf.http"/>
<code-block lang="text">
network-topology:network-topology
  topology [key: topology-id]
    topology-types
      netconf-node-topology:topology-netconf  (presence marker)
    node [key: node-id]
      node-id: network-topology:node-id (inet:uri)
      netconf-node-topology:netconf-node  (presence container)
        // when ../../nt:topology-types/topology-netconf
        // uses odl-netconf-device:credentials
        choice credentials (mandatory)
          login-password-unencrypted
            username: string
            password: string
        // uses odl-netconf-device:connection-parameters
        host: inet:host
        port: inet:port-number
        tcp-only: boolean
        reconnect-on-changed-schema: boolean
        connection-timeout-millis: uint32
        max-connection-attempts: uint32
        min-backoff-millis: uint16
        max-backoff-millis: uint32
        backoff-multiplier: decimal64 (fraction-digits 1; range 1..max)
        keepalive-delay: uint32
        // other configuration and operational fields omitted
</code-block>
</compare>

The `credentials` choice in the YANG model selects the `login-password-unencrypted` container represented in the XML payload. According to the [device model](https://github.com/opendaylight/netconf/blob/0b7cb725a4f7224fb57afd4b7e47c29cb8763d24/plugins/netconf-client-mdsal/src/main/yang/odl-netconf-device.yang), the omitted `protocol/name` defaults to `SSH` and `backoff-jitter` defaults to `0.1`. The connection retry policy therefore includes randomized jitter.

| Payload object or field | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `node` / `node-id` | Topology node list entry and key | `sma-xrv-lsr1-alpha` | Selects the same node as the URI and identifies the connector in this topology. |
| `netconf-node` | Augmented presence container | Present | Declares the node's NETCONF device configuration. |
| `host` | `inet:host` leaf | `11.11.11.11` | Router loopback address used by the connector. |
| `port` | `inet:port-number` leaf | `830` | Device NETCONF transport port. |
| `login-password-unencrypted` | Container selected by the mandatory credentials choice | Present | Supplies the device password in unencrypted form within the configuration payload. |
| `username` / `password` | String credential leaves | `{{xrv_netconf_user}}` / `{{xrv_netconf_pwd}}` | Device-login credentials, independently supplied from the RESTCONF authorization credential. |
| `tcp-only` | Boolean transport leaf | `false` | Disables plain TCP-only transport. The omitted protocol selector defaults to SSH. |
| `reconnect-on-changed-schema` | Boolean connection-policy leaf | `false` | Does not enable automatic disconnect/reconnect on a remote schema-change notification. |
| `connection-timeout-millis` | `uint32` connection timeout | `20000` | Allows 20 seconds for connection establishment. |
| `max-connection-attempts` | `uint32` retry policy | `0` | Configures unlimited connection retries. |
| `min-backoff-millis` | `uint16` initial retry-delay parameter | `2000` | Sets a 2-second initial base delay, subject to jitter and subsequent multiplication. |
| `max-backoff-millis` | `uint32` maximum backoff parameter | `1800000` | Sets the 30-minute backoff bound described by the model, with jitter still applicable. |
| `backoff-multiplier` | `decimal64` retry factor | `1.5` | Multiplies the backoff for each additional attempt. |
| `keepalive-delay` | `uint32` idle-session keepalive delay | `30` | Requests NETCONF keepalive RPCs at 30-second intervals while the session is idle. |

#### Effect and response {id="mount-lsr1-netconf-effect"}

A successful PUT writes the `sma-xrv-lsr1-alpha` node configuration. OpenDaylight then attempts to establish and authenticate the NETCONF connection. Schema discovery and mount availability depend on a successful session. Connection status and discovered capabilities are available as operational data defined by the [device model](https://github.com/opendaylight/netconf/blob/0b7cb725a4f7224fb57afd4b7e47c29cb8763d24/plugins/netconf-client-mdsal/src/main/yang/odl-netconf-device.yang).

The source Postman collection contains no recorded response for this request. Under [RFC 8040 PUT semantics](https://www.rfc-editor.org/rfc/rfc8040.html#section-4.5), the expected status is `201 Created` when the node is created or `204 No Content` when an existing node is replaced. Neither status confirms that the router has established a NETCONF session.

### Mount LSR2 through NETCONF {id="mount-lsr2-netconf"}

This PUT configures the NETCONF connector for `sma-xrv-lsr2-bravo` at its loopback address, `12.12.12.12:830`. The parent `topology-netconf` topology must already contain the NETCONF topology-type marker because this node-level PUT does not create it.

#### Request {id="mount-lsr2-netconf-request"}

<code-block lang="http" src="../../src/main/postman/netconf/mount-lsr2-through-netconf.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates the RESTCONF client to OpenDaylight. The credential is supplied outside version control. |
| `Accept` | `application/xml` | Requests XML if a response representation is returned. |
| `Content-Type` | `application/xml` | Declares the literal XML request body. |

The URL identifies a specific node instance, which PUT creates or replaces. No query parameters are present.

#### RESTCONF resource and YANG objects {id="mount-lsr2-netconf-yang"}

The request selects `network-topology:network-topology` → `topology[topology-id='topology-netconf']` → `node[node-id='sma-xrv-lsr2-bravo']`. The [shared topology module](https://github.com/opendaylight/mdsal/blob/a341bc343a9f319687fbee228032ffac68c3da67/model/ietf-topology/src/main/yang/network-topology@2013-10-21.yang) defines the `topology` and `node` lists and the `node-id` leaf. The [NETCONF topology augmentation](https://github.com/opendaylight/netconf/blob/0b7cb725a4f7224fb57afd4b7e47c29cb8763d24/apps/netconf-topology/src/main/yang/netconf-node-topology.yang) adds the `netconf-node` presence container under the node when ../../nt:topology-types/topology-netconf is satisfied. The value `topology-netconf` identifies a topology list entry.

The NETCONF augmentation uses the `credentials` and `connection-parameters` groupings from [odl-netconf-device.yang](https://github.com/opendaylight/netconf/blob/0b7cb725a4f7224fb57afd4b7e47c29cb8763d24/plugins/netconf-client-mdsal/src/main/yang/odl-netconf-device.yang), in namespace `urn:opendaylight:netconf:device`. The referenced model assigns the grouping-defined fields to that namespace. The outer `node` and `node-id` fields belong to `urn:TBD:params:xml:ns:yang:network-topology`, while `netconf-node` belongs to `urn:opendaylight:netconf-node-topology`.

The saved XML places the unprefixed credential and connection fields in the `netconf-node` namespace. Those namespaces conflict with the pinned model, so compatibility with this model cannot be established. An earlier controller schema may have accepted the request. The original XML is preserved for comparison.

<compare type="top-bottom" first-title="Request payload (XML)" second-title="YANG model composition (abridged)">
<code-block lang="xml" src="../../src/main/postman/netconf/mount-lsr2-through-netconf.http"/>
<code-block lang="text">
network-topology:network-topology
  topology [key: topology-id]
    topology-types
      netconf-node-topology:topology-netconf  (presence marker)
    node [key: node-id]
      node-id: network-topology:node-id (inet:uri)
      netconf-node-topology:netconf-node  (presence container)
        // when ../../nt:topology-types/topology-netconf
        // uses odl-netconf-device:credentials
        choice credentials (mandatory)
          login-password-unencrypted
            username: string
            password: string
        // uses odl-netconf-device:connection-parameters
        host: inet:host
        port: inet:port-number
        tcp-only: boolean
        reconnect-on-changed-schema: boolean
        connection-timeout-millis: uint32
        max-connection-attempts: uint32
        min-backoff-millis: uint16
        max-backoff-millis: uint32
        backoff-multiplier: decimal64 (fraction-digits 1; range 1..max)
        keepalive-delay: uint32
        // other configuration and operational fields omitted
</code-block>
</compare>

The `credentials` choice in the YANG model selects the `login-password-unencrypted` container represented in the XML payload. According to the [device model](https://github.com/opendaylight/netconf/blob/0b7cb725a4f7224fb57afd4b7e47c29cb8763d24/plugins/netconf-client-mdsal/src/main/yang/odl-netconf-device.yang), the omitted `protocol/name` defaults to `SSH` and `backoff-jitter` defaults to `0.1`. The connection retry policy therefore includes randomized jitter.

| Payload object or field | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `node` / `node-id` | Topology node list entry and key | `sma-xrv-lsr2-bravo` | Selects the same node as the URI and identifies the connector in this topology. |
| `netconf-node` | Augmented presence container | Present | Declares the node's NETCONF device configuration. |
| `host` | `inet:host` leaf | `12.12.12.12` | Router loopback address used by the connector. |
| `port` | `inet:port-number` leaf | `830` | Device NETCONF transport port. |
| `login-password-unencrypted` | Container selected by the mandatory credentials choice | Present | Supplies the device password in unencrypted form within the configuration payload. |
| `username` / `password` | String credential leaves | `{{xrv_netconf_user}}` / `{{xrv_netconf_pwd}}` | Device-login credentials, independently supplied from the RESTCONF authorization credential. |
| `tcp-only` | Boolean transport leaf | `false` | Disables plain TCP-only transport. The omitted protocol selector defaults to SSH. |
| `reconnect-on-changed-schema` | Boolean connection-policy leaf | `false` | Does not enable automatic disconnect/reconnect on a remote schema-change notification. |
| `connection-timeout-millis` | `uint32` connection timeout | `20000` | Allows 20 seconds for connection establishment. |
| `max-connection-attempts` | `uint32` retry policy | `0` | Configures unlimited connection retries. |
| `min-backoff-millis` | `uint16` initial retry-delay parameter | `2000` | Sets a 2-second initial base delay, subject to jitter and subsequent multiplication. |
| `max-backoff-millis` | `uint32` maximum backoff parameter | `1800000` | Sets the 30-minute backoff bound described by the model, with jitter still applicable. |
| `backoff-multiplier` | `decimal64` retry factor | `1.5` | Multiplies the backoff for each additional attempt. |
| `keepalive-delay` | `uint32` idle-session keepalive delay | `30` | Requests NETCONF keepalive RPCs at 30-second intervals while the session is idle. |

#### Effect and response {id="mount-lsr2-netconf-effect"}

A successful PUT writes the `sma-xrv-lsr2-bravo` node configuration. OpenDaylight then attempts to establish and authenticate the NETCONF connection. Schema discovery and mount availability depend on a successful session. Connection status and discovered capabilities are available as operational data defined by the [device model](https://github.com/opendaylight/netconf/blob/0b7cb725a4f7224fb57afd4b7e47c29cb8763d24/plugins/netconf-client-mdsal/src/main/yang/odl-netconf-device.yang).

The source Postman collection contains no recorded response for this request. Under [RFC 8040 PUT semantics](https://www.rfc-editor.org/rfc/rfc8040.html#section-4.5), the expected status is `201 Created` when the node is created or `204 No Content` when an existing node is replaced. Neither status confirms that the router has established a NETCONF session.

### Mount LSR3 through NETCONF {id="mount-lsr3-netconf"}

This PUT configures the NETCONF connector for `sma-xrv-lsr3-charlie` at its loopback address, `13.13.13.13:830`. The parent `topology-netconf` topology must already contain the NETCONF topology-type marker because this node-level PUT does not create it.

#### Request {id="mount-lsr3-netconf-request"}

<code-block lang="http" src="../../src/main/postman/netconf/mount-lsr3-through-netconf.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates the RESTCONF client to OpenDaylight. The credential is supplied outside version control. |
| `Accept` | `application/xml` | Requests XML if a response representation is returned. |
| `Content-Type` | `application/xml` | Declares the literal XML request body. |

The URL identifies a specific node instance, which PUT creates or replaces. No query parameters are present.

#### RESTCONF resource and YANG objects {id="mount-lsr3-netconf-yang"}

The request selects `network-topology:network-topology` → `topology[topology-id='topology-netconf']` → `node[node-id='sma-xrv-lsr3-charlie']`. The [shared topology module](https://github.com/opendaylight/mdsal/blob/a341bc343a9f319687fbee228032ffac68c3da67/model/ietf-topology/src/main/yang/network-topology@2013-10-21.yang) defines the `topology` and `node` lists and the `node-id` leaf. The [NETCONF topology augmentation](https://github.com/opendaylight/netconf/blob/0b7cb725a4f7224fb57afd4b7e47c29cb8763d24/apps/netconf-topology/src/main/yang/netconf-node-topology.yang) adds the `netconf-node` presence container under the node when ../../nt:topology-types/topology-netconf is satisfied. The value `topology-netconf` identifies a topology list entry.

The NETCONF augmentation uses the `credentials` and `connection-parameters` groupings from [odl-netconf-device.yang](https://github.com/opendaylight/netconf/blob/0b7cb725a4f7224fb57afd4b7e47c29cb8763d24/plugins/netconf-client-mdsal/src/main/yang/odl-netconf-device.yang), in namespace `urn:opendaylight:netconf:device`. The referenced model assigns the grouping-defined fields to that namespace. The outer `node` and `node-id` fields belong to `urn:TBD:params:xml:ns:yang:network-topology`, while `netconf-node` belongs to `urn:opendaylight:netconf-node-topology`.

The saved XML places the unprefixed credential and connection fields in the `netconf-node` namespace. Those namespaces conflict with the pinned model, so compatibility with this model cannot be established. An earlier controller schema may have accepted the request. The original XML is preserved for comparison.

<compare type="top-bottom" first-title="Request payload (XML)" second-title="YANG model composition (abridged)">
<code-block lang="xml" src="../../src/main/postman/netconf/mount-lsr3-through-netconf.http"/>
<code-block lang="text">
network-topology:network-topology
  topology [key: topology-id]
    topology-types
      netconf-node-topology:topology-netconf  (presence marker)
    node [key: node-id]
      node-id: network-topology:node-id (inet:uri)
      netconf-node-topology:netconf-node  (presence container)
        // when ../../nt:topology-types/topology-netconf
        // uses odl-netconf-device:credentials
        choice credentials (mandatory)
          login-password-unencrypted
            username: string
            password: string
        // uses odl-netconf-device:connection-parameters
        host: inet:host
        port: inet:port-number
        tcp-only: boolean
        reconnect-on-changed-schema: boolean
        connection-timeout-millis: uint32
        max-connection-attempts: uint32
        min-backoff-millis: uint16
        max-backoff-millis: uint32
        backoff-multiplier: decimal64 (fraction-digits 1; range 1..max)
        keepalive-delay: uint32
        // other configuration and operational fields omitted
</code-block>
</compare>

The `credentials` choice in the YANG model selects the `login-password-unencrypted` container represented in the XML payload. According to the [device model](https://github.com/opendaylight/netconf/blob/0b7cb725a4f7224fb57afd4b7e47c29cb8763d24/plugins/netconf-client-mdsal/src/main/yang/odl-netconf-device.yang), the omitted `protocol/name` defaults to `SSH` and `backoff-jitter` defaults to `0.1`. The connection retry policy therefore includes randomized jitter.

| Payload object or field | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `node` / `node-id` | Topology node list entry and key | `sma-xrv-lsr3-charlie` | Selects the same node as the URI and identifies the connector in this topology. |
| `netconf-node` | Augmented presence container | Present | Declares the node's NETCONF device configuration. |
| `host` | `inet:host` leaf | `13.13.13.13` | Router loopback address used by the connector. |
| `port` | `inet:port-number` leaf | `830` | Device NETCONF transport port. |
| `login-password-unencrypted` | Container selected by the mandatory credentials choice | Present | Supplies the device password in unencrypted form within the configuration payload. |
| `username` / `password` | String credential leaves | `{{xrv_netconf_user}}` / `{{xrv_netconf_pwd}}` | Device-login credentials, independently supplied from the RESTCONF authorization credential. |
| `tcp-only` | Boolean transport leaf | `false` | Disables plain TCP-only transport. The omitted protocol selector defaults to SSH. |
| `reconnect-on-changed-schema` | Boolean connection-policy leaf | `false` | Does not enable automatic disconnect/reconnect on a remote schema-change notification. |
| `connection-timeout-millis` | `uint32` connection timeout | `20000` | Allows 20 seconds for connection establishment. |
| `max-connection-attempts` | `uint32` retry policy | `0` | Configures unlimited connection retries. |
| `min-backoff-millis` | `uint16` initial retry-delay parameter | `2000` | Sets a 2-second initial base delay, subject to jitter and subsequent multiplication. |
| `max-backoff-millis` | `uint32` maximum backoff parameter | `1800000` | Sets the 30-minute backoff bound described by the model, with jitter still applicable. |
| `backoff-multiplier` | `decimal64` retry factor | `1.5` | Multiplies the backoff for each additional attempt. |
| `keepalive-delay` | `uint32` idle-session keepalive delay | `30` | Requests NETCONF keepalive RPCs at 30-second intervals while the session is idle. |

#### Effect and response {id="mount-lsr3-netconf-effect"}

A successful PUT writes the `sma-xrv-lsr3-charlie` node configuration. OpenDaylight then attempts to establish and authenticate the NETCONF connection. Schema discovery and mount availability depend on a successful session. Connection status and discovered capabilities are available as operational data defined by the [device model](https://github.com/opendaylight/netconf/blob/0b7cb725a4f7224fb57afd4b7e47c29cb8763d24/plugins/netconf-client-mdsal/src/main/yang/odl-netconf-device.yang).

The source Postman collection contains no recorded response for this request. Under [RFC 8040 PUT semantics](https://www.rfc-editor.org/rfc/rfc8040.html#section-4.5), the expected status is `201 Created` when the node is created or `204 No Content` when an existing node is replaced. Neither status confirms that the router has established a NETCONF session.

### Mount LSR4 through NETCONF {id="mount-lsr4-netconf"}

This PUT configures the NETCONF connector for `sma-xrv-lsr4-delta` at its loopback address, `14.14.14.14:830`. The parent `topology-netconf` topology must already contain the NETCONF topology-type marker because this node-level PUT does not create it.

#### Request {id="mount-lsr4-netconf-request"}

<code-block lang="http" src="../../src/main/postman/netconf/mount-lsr4-through-netconf.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Authenticates the RESTCONF client to OpenDaylight. The credential is supplied outside version control. |
| `Accept` | `application/xml` | Requests XML if a response representation is returned. |
| `Content-Type` | `application/xml` | Declares the literal XML request body. |

The URL identifies a specific node instance, which PUT creates or replaces. No query parameters are present.

#### RESTCONF resource and YANG objects {id="mount-lsr4-netconf-yang"}

The request selects `network-topology:network-topology` → `topology[topology-id='topology-netconf']` → `node[node-id='sma-xrv-lsr4-delta']`. The [shared topology module](https://github.com/opendaylight/mdsal/blob/a341bc343a9f319687fbee228032ffac68c3da67/model/ietf-topology/src/main/yang/network-topology@2013-10-21.yang) defines the `topology` and `node` lists and the `node-id` leaf. The [NETCONF topology augmentation](https://github.com/opendaylight/netconf/blob/0b7cb725a4f7224fb57afd4b7e47c29cb8763d24/apps/netconf-topology/src/main/yang/netconf-node-topology.yang) adds the `netconf-node` presence container under the node when ../../nt:topology-types/topology-netconf is satisfied. The value `topology-netconf` identifies a topology list entry.

The NETCONF augmentation uses the `credentials` and `connection-parameters` groupings from [odl-netconf-device.yang](https://github.com/opendaylight/netconf/blob/0b7cb725a4f7224fb57afd4b7e47c29cb8763d24/plugins/netconf-client-mdsal/src/main/yang/odl-netconf-device.yang), in namespace `urn:opendaylight:netconf:device`. The referenced model assigns the grouping-defined fields to that namespace. The outer `node` and `node-id` fields belong to `urn:TBD:params:xml:ns:yang:network-topology`, while `netconf-node` belongs to `urn:opendaylight:netconf-node-topology`.

The saved XML places the unprefixed credential and connection fields in the `netconf-node` namespace. Those namespaces conflict with the pinned model, so compatibility with this model cannot be established. An earlier controller schema may have accepted the request. The original XML is preserved for comparison.

<compare type="top-bottom" first-title="Request payload (XML)" second-title="YANG model composition (abridged)">
<code-block lang="xml" src="../../src/main/postman/netconf/mount-lsr4-through-netconf.http"/>
<code-block lang="text">
network-topology:network-topology
  topology [key: topology-id]
    topology-types
      netconf-node-topology:topology-netconf  (presence marker)
    node [key: node-id]
      node-id: network-topology:node-id (inet:uri)
      netconf-node-topology:netconf-node  (presence container)
        // when ../../nt:topology-types/topology-netconf
        // uses odl-netconf-device:credentials
        choice credentials (mandatory)
          login-password-unencrypted
            username: string
            password: string
        // uses odl-netconf-device:connection-parameters
        host: inet:host
        port: inet:port-number
        tcp-only: boolean
        reconnect-on-changed-schema: boolean
        connection-timeout-millis: uint32
        max-connection-attempts: uint32
        min-backoff-millis: uint16
        max-backoff-millis: uint32
        backoff-multiplier: decimal64 (fraction-digits 1; range 1..max)
        keepalive-delay: uint32
        // other configuration and operational fields omitted
</code-block>
</compare>

The `credentials` choice in the YANG model selects the `login-password-unencrypted` container represented in the XML payload. According to the [device model](https://github.com/opendaylight/netconf/blob/0b7cb725a4f7224fb57afd4b7e47c29cb8763d24/plugins/netconf-client-mdsal/src/main/yang/odl-netconf-device.yang), the omitted `protocol/name` defaults to `SSH` and `backoff-jitter` defaults to `0.1`. The connection retry policy therefore includes randomized jitter.

| Payload object or field | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `node` / `node-id` | Topology node list entry and key | `sma-xrv-lsr4-delta` | Selects the same node as the URI and identifies the connector in this topology. |
| `netconf-node` | Augmented presence container | Present | Declares the node's NETCONF device configuration. |
| `host` | `inet:host` leaf | `14.14.14.14` | Router loopback address used by the connector. |
| `port` | `inet:port-number` leaf | `830` | Device NETCONF transport port. |
| `login-password-unencrypted` | Container selected by the mandatory credentials choice | Present | Supplies the device password in unencrypted form within the configuration payload. |
| `username` / `password` | String credential leaves | `{{xrv_netconf_user}}` / `{{xrv_netconf_pwd}}` | Device-login credentials, independently supplied from the RESTCONF authorization credential. |
| `tcp-only` | Boolean transport leaf | `false` | Disables plain TCP-only transport. The omitted protocol selector defaults to SSH. |
| `reconnect-on-changed-schema` | Boolean connection-policy leaf | `false` | Does not enable automatic disconnect/reconnect on a remote schema-change notification. |
| `connection-timeout-millis` | `uint32` connection timeout | `20000` | Allows 20 seconds for connection establishment. |
| `max-connection-attempts` | `uint32` retry policy | `0` | Configures unlimited connection retries. |
| `min-backoff-millis` | `uint16` initial retry-delay parameter | `2000` | Sets a 2-second initial base delay, subject to jitter and subsequent multiplication. |
| `max-backoff-millis` | `uint32` maximum backoff parameter | `1800000` | Sets the 30-minute backoff bound described by the model, with jitter still applicable. |
| `backoff-multiplier` | `decimal64` retry factor | `1.5` | Multiplies the backoff for each additional attempt. |
| `keepalive-delay` | `uint32` idle-session keepalive delay | `30` | Requests NETCONF keepalive RPCs at 30-second intervals while the session is idle. |

#### Effect and response {id="mount-lsr4-netconf-effect"}

A successful PUT writes the `sma-xrv-lsr4-delta` node configuration. OpenDaylight then attempts to establish and authenticate the NETCONF connection. Schema discovery and mount availability depend on a successful session. Connection status and discovered capabilities are available as operational data defined by the [device model](https://github.com/opendaylight/netconf/blob/0b7cb725a4f7224fb57afd4b7e47c29cb8763d24/plugins/netconf-client-mdsal/src/main/yang/odl-netconf-device.yang).

The source Postman collection contains no recorded response for this request. Under [RFC 8040 PUT semantics](https://www.rfc-editor.org/rfc/rfc8040.html#section-4.5), the expected status is `201 Created` when the node is created or `204 No Content` when an existing node is replaced. Neither status confirms that the router has established a NETCONF session.

## Complete RESTCONF Calls {id="netconf-complete-restconf-calls"}

The remaining 21 requests cover topology and device reads, schema retrieval, and interface configuration resulting from previous testing upon the notion of <code>controller initiated create LSP requests</code>. The four initialization requests that appeared in the preceding section must have been executed earlier so that any of these requests works against the devices mounted.


### Read Complete NETCONF Topology {id="netconf-read-complete-netconf-topology"}

This GET retrieves OpenDaylight's topology and NETCONF connection-state data when available. Router configuration and operational data reside under the separate mounted device tree.

#### Request {id="netconf-read-complete-netconf-topology-request"}

<code-block lang="http" src="../../src/main/postman/netconf/read-complete-netconf-topology.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | External Basic authentication placeholder. |
| `Accept` | `application/xml` | Requests an XML response. |

The query parameter `content=all` requests configuration and operational data. The GET has no body or `Content-Type` header.

#### RESTCONF resource and YANG objects {id="netconf-read-complete-netconf-topology-yang-objects"}

The controller-side path uses [network-topology, revision 2013-10-21](https://github.com/opendaylight/mdsal/blob/15.0.x/model/ietf-topology/src/main/yang/network-topology@2013-10-21.yang), with `topology` keyed by `topology-id` and `node` keyed by `node-id`. The [NETCONF topology augmentation](https://github.com/opendaylight/netconf/blob/10.0.x/apps/netconf-topology/src/main/yang/netconf-node-topology.yang) adds `netconf-node`, whose operational connection fields come from the [connection-oper grouping](https://github.com/opendaylight/netconf/blob/10.0.x/plugins/netconf-client-mdsal/src/main/yang/odl-netconf-device.yang).

<compare type="top-bottom" first-title="Request body" second-title="YANG resource composition (abridged)">
    <code-block lang="XML"><![CDATA[
    <topology xmlns="urn:TBD:params:xml:ns:yang:network-topology">
    <topology-id>topology-netconf</topology-id>
    <node>
        <node-id>sma-xrv-lsr2-bravo</node-id>
        <netconf-node xmlns="urn:opendaylight:netconf-node-topology">
            <reconnect-on-changed-schema>false</reconnect-on-changed-schema>
            <connection-timeout-millis>20000</connection-timeout-millis>
            <port>830</port>
            <max-backoff-millis>1800000</max-backoff-millis>
            <keepalive-delay>30</keepalive-delay>
            <tcp-only>false</tcp-only>
            <connection-status>connecting</connection-status>
            <login-password-unencrypted>
                <username>{{xrv_netconf_user}}</username>
                <password>{{xrv_netconf_pwd}}</password>
            </login-password-unencrypted>
            <max-connection-attempts>0</max-connection-attempts>
            <min-backoff-millis>2000</min-backoff-millis>
            <backoff-multiplier>1.5</backoff-multiplier>
            <host>12.12.12.12</host>
        </netconf-node>
    </node>
    <node>
        <node-id>sma-xrv-lsr1-alpha</node-id>
        <netconf-node xmlns="urn:opendaylight:netconf-node-topology">
            <reconnect-on-changed-schema>false</reconnect-on-changed-schema>
            <connection-timeout-millis>20000</connection-timeout-millis>
            <port>830</port>
            <max-backoff-millis>1800000</max-backoff-millis>
            <keepalive-delay>30</keepalive-delay>
            <tcp-only>false</tcp-only>
            <connection-status>connecting</connection-status>
            <login-password-unencrypted>
                <username>{{xrv_netconf_user}}</username>
                <password>{{xrv_netconf_pwd}}</password>
            </login-password-unencrypted>
            <max-connection-attempts>0</max-connection-attempts>
            <min-backoff-millis>2000</min-backoff-millis>
            <backoff-multiplier>1.5</backoff-multiplier>
            <host>11.11.11.11</host>
        </netconf-node>
    </node>
    <node>
        <node-id>sma-xrv-lsr3-charlie</node-id>
        <netconf-node xmlns="urn:opendaylight:netconf-node-topology">
            <reconnect-on-changed-schema>false</reconnect-on-changed-schema>
            <connection-timeout-millis>20000</connection-timeout-millis>
            <port>830</port>
            <max-backoff-millis>1800000</max-backoff-millis>
            <keepalive-delay>30</keepalive-delay>
            <tcp-only>false</tcp-only>
            <connection-status>connecting</connection-status>
            <login-password-unencrypted>
                <username>{{xrv_netconf_user}}</username>
                <password>{{xrv_netconf_pwd}}</password>
            </login-password-unencrypted>
            <max-connection-attempts>0</max-connection-attempts>
            <min-backoff-millis>2000</min-backoff-millis>
            <backoff-multiplier>1.5</backoff-multiplier>
            <host>13.13.13.13</host>
        </netconf-node>
    </node>
    <node>
        <node-id>sma-xrv-lsr4-delta</node-id>
        <netconf-node xmlns="urn:opendaylight:netconf-node-topology">
            <reconnect-on-changed-schema>false</reconnect-on-changed-schema>
            <connection-timeout-millis>20000</connection-timeout-millis>
            <port>830</port>
            <max-backoff-millis>1800000</max-backoff-millis>
            <keepalive-delay>30</keepalive-delay>
            <tcp-only>false</tcp-only>
            <connection-status>connecting</connection-status>
            <login-password-unencrypted>
                <username>{{xrv_netconf_user}}</username>
                <password>{{xrv_netconf_pwd}}</password>
            </login-password-unencrypted>
            <max-connection-attempts>0</max-connection-attempts>
            <min-backoff-millis>2000</min-backoff-millis>
            <backoff-multiplier>1.5</backoff-multiplier>
            <host>14.14.14.14</host>
        </netconf-node>
    </node>
    <topology-types>
        <topology-netconf xmlns="urn:opendaylight:netconf-node-topology">
            <ssh-transport-topology-parameters>
                <mac>
                    <mac-alg>hmac-sha2-256</mac-alg>
                    <mac-alg>hmac-sha2-512</mac-alg>
                    <mac-alg>hmac-sha1</mac-alg>
                </mac>
                <host-key>
                    <host-key-alg>ecdsa-sha2-nistp256</host-key-alg>
                    <host-key-alg>ecdsa-sha2-nistp384</host-key-alg>
                    <host-key-alg>ecdsa-sha2-nistp521</host-key-alg>
                    <host-key-alg>ssh-ed25519</host-key-alg>
                    <host-key-alg>rsa-sha2-512</host-key-alg>
                    <host-key-alg>rsa-sha2-256</host-key-alg>
                    <host-key-alg>ssh-rsa</host-key-alg>
                </host-key>
                <key-exchange>
                    <key-exchange-alg>sntrup761x25519-sha512</key-exchange-alg>
                    <key-exchange-alg>mlkem768x25519-sha256</key-exchange-alg>
                    <key-exchange-alg>mlkem1024nistp384-sha384</key-exchange-alg>
                    <key-exchange-alg>mlkem768nistp256-sha256</key-exchange-alg>
                    <key-exchange-alg>curve25519-sha256</key-exchange-alg>
                    <key-exchange-alg>curve448-sha512</key-exchange-alg>
                    <key-exchange-alg>ecdh-sha2-nistp521</key-exchange-alg>
                    <key-exchange-alg>ecdh-sha2-nistp384</key-exchange-alg>
                    <key-exchange-alg>ecdh-sha2-nistp256</key-exchange-alg>
                    <key-exchange-alg>diffie-hellman-group-exchange-sha256</key-exchange-alg>
                    <key-exchange-alg>diffie-hellman-group18-sha512</key-exchange-alg>
                    <key-exchange-alg>diffie-hellman-group17-sha512</key-exchange-alg>
                    <key-exchange-alg>diffie-hellman-group16-sha512</key-exchange-alg>
                    <key-exchange-alg>diffie-hellman-group15-sha512</key-exchange-alg>
                    <key-exchange-alg>diffie-hellman-group14-sha256</key-exchange-alg>
                </key-exchange>
                <encryption>
                    <encryption-alg>chacha20-poly1305</encryption-alg>
                    <encryption-alg>aes128-ctr</encryption-alg>
                    <encryption-alg>aes192-ctr</encryption-alg>
                    <encryption-alg>aes256-ctr</encryption-alg>
                    <encryption-alg>AEAD_AES_128_GCM</encryption-alg>
                    <encryption-alg>AEAD_AES_256_GCM</encryption-alg>
                    <encryption-alg>aes128-cbc</encryption-alg>
                    <encryption-alg>aes192-cbc</encryption-alg>
                    <encryption-alg>aes256-cbc</encryption-alg>
                </encryption>
            </ssh-transport-topology-parameters>
        </topology-netconf>
    </topology-types>
</topology>

]]>
</code-block>
    <code-block lang="text">
container network-topology {
  list topology [key: topology-id] {
    leaf topology-id: topology-id;                 // topology-netconf
    container topology-types {
      netconf-node-topology:topology-netconf;      // presence marker
    }
    list node [key: node-id] {
      leaf node-id: node-id;
      netconf-node-topology:netconf-node {         // presence container
        leaf connection-status { config false; }
        leaf session-id { config false; }
        leaf-list available-capabilities { config false; }
        leaf-list unavailable-capabilities { config false; }
      }
    }
  }
}
    </code-block>
</compare>

| Resource element | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `topology=topology-netconf` | Topology list key | topology-netconf | Selects the NETCONF device topology. |
| `netconf-node` | NETCONF augmentation container | Returned when present | Owns connection configuration and operational status in the researched 10.0.x model. |

#### Effect and response {id="netconf-read-complete-netconf-topology-effect-response"}

A successful read is expected to return `200 OK` with the selected XML representation. The GET does not modify configuration or initiate a new protocol session. The source Postman collection contains no recorded response for this request.

<note>In the current OpenDaylight version used, it is possible for one or more of these devices to appear under the status of <code>connected</code> while the rest of these devices appear under a <code>connecting</code> state. Despite this, all devices <b>are completely available for querying and modification</b>, showing that there is a delay, or error during translation from the state of the connection to the reported state in the return body of this request.</note>

### Read Topology State for LSR1 Alpha {id="netconf-read-topology-state-for-lsr1-alpha"}

This GET retrieves OpenDaylight's topology and NETCONF connection-state data when available. Router configuration and operational data reside under the separate mounted device tree.

#### Request {id="netconf-read-topology-state-for-lsr1-alpha-request"}

<code-block lang="http" src="../../src/main/postman/netconf/read-topology-state-for-lsr1-alpha.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | External Basic authentication placeholder. |
| `Accept` | `application/xml` | Requests an XML response. |

The query parameter `content=nonconfig` requests operational data. The GET has no body or `Content-Type` header.

#### RESTCONF resource and YANG objects {id="netconf-read-topology-state-for-lsr1-alpha-yang-objects"}

The controller-side path uses [network-topology, revision 2013-10-21](https://github.com/opendaylight/mdsal/blob/15.0.x/model/ietf-topology/src/main/yang/network-topology@2013-10-21.yang), with `topology` keyed by `topology-id` and `node` keyed by `node-id`. The [NETCONF topology augmentation](https://github.com/opendaylight/netconf/blob/10.0.x/apps/netconf-topology/src/main/yang/netconf-node-topology.yang) adds `netconf-node`, whose operational connection fields come from the [connection-oper grouping](https://github.com/opendaylight/netconf/blob/10.0.x/plugins/netconf-client-mdsal/src/main/yang/odl-netconf-device.yang).

<compare type="left-right" first-title="Request body" second-title="YANG resource composition (abridged)">
    <code-block lang="text">No request body: the URI selects the resource.</code-block>
    <code-block lang="text">
container network-topology {
  list topology [key: topology-id] {
    leaf topology-id: topology-id;                 // topology-netconf
    list node [key: node-id] {
      leaf node-id: node-id;                       // sma-xrv-lsr1-alpha
      netconf-node-topology:netconf-node {
        leaf connection-status { config false; }
        leaf session-id { config false; }
        leaf-list available-capabilities { config false; }
        leaf-list unavailable-capabilities { config false; }
      }
    }
  }
}
    </code-block>
</compare>

| Resource element | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `topology=topology-netconf` | Topology list key | topology-netconf | Selects the NETCONF device topology. |
| `node=sma-xrv-lsr1-alpha` | Node list key | sma-xrv-lsr1-alpha | Selects this controller-side device entry. |
| `netconf-node` | NETCONF augmentation container | Returned when present | Owns connection configuration and operational status in the researched 10.0.x model. |

#### Effect and response {id="netconf-read-topology-state-for-lsr1-alpha-effect-response"}

A successful read is expected to return `200 OK` with the selected XML representation. The GET does not modify configuration or initiate a new protocol session. The source Postman collection contains no recorded response for this request.

### Read Topology State for LSR2 Bravo {id="netconf-read-topology-state-for-lsr2-bravo"}

This GET retrieves OpenDaylight's topology and NETCONF connection-state data when available. Router configuration and operational data reside under the separate mounted device tree.

#### Request {id="netconf-read-topology-state-for-lsr2-bravo-request"}

<code-block lang="http" src="../../src/main/postman/netconf/read-topology-state-for-lsr2-bravo.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | External Basic authentication placeholder. |
| `Accept` | `application/xml` | Requests an XML response. |

The query parameter `content=all` requests configuration and operational data. The GET has no body or `Content-Type` header.

#### RESTCONF resource and YANG objects {id="netconf-read-topology-state-for-lsr2-bravo-yang-objects"}

The controller-side path uses [network-topology, revision 2013-10-21](https://github.com/opendaylight/mdsal/blob/15.0.x/model/ietf-topology/src/main/yang/network-topology@2013-10-21.yang), with `topology` keyed by `topology-id` and `node` keyed by `node-id`. The [NETCONF topology augmentation](https://github.com/opendaylight/netconf/blob/10.0.x/apps/netconf-topology/src/main/yang/netconf-node-topology.yang) adds `netconf-node`, whose operational connection fields come from the [connection-oper grouping](https://github.com/opendaylight/netconf/blob/10.0.x/plugins/netconf-client-mdsal/src/main/yang/odl-netconf-device.yang).

<compare type="left-right" first-title="Request body" second-title="YANG resource composition (abridged)">
    <code-block lang="text">No request body: the URI selects the resource.</code-block>
    <code-block lang="text">
container network-topology {
  list topology [key: topology-id] {
    leaf topology-id: topology-id;                 // topology-netconf
    list node [key: node-id] {
      leaf node-id: node-id;                       // sma-xrv-lsr2-bravo
      netconf-node-topology:netconf-node {
        leaf connection-status { config false; }
        leaf session-id { config false; }
        leaf-list available-capabilities { config false; }
        leaf-list unavailable-capabilities { config false; }
      }
    }
  }
}
    </code-block>
</compare>

| Resource element | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `topology=topology-netconf` | Topology list key | topology-netconf | Selects the NETCONF device topology. |
| `node=sma-xrv-lsr2-bravo` | Node list key | sma-xrv-lsr2-bravo | Selects this controller-side device entry. |
| `netconf-node` | NETCONF augmentation container | Returned when present | Owns connection configuration and operational status in the researched 10.0.x model. |

#### Effect and response {id="netconf-read-topology-state-for-lsr2-bravo-effect-response"}

A successful read is expected to return `200 OK` with the selected XML representation. The GET does not modify configuration or initiate a new protocol session. The source Postman collection contains no recorded response for this request.

### Read Topology State for LSR3 Charlie {id="netconf-read-topology-state-for-lsr3-charlie"}

This GET retrieves OpenDaylight's topology and NETCONF connection-state data when available. Router configuration and operational data reside under the separate mounted device tree.

#### Request {id="netconf-read-topology-state-for-lsr3-charlie-request"}

<code-block lang="http" src="../../src/main/postman/netconf/read-topology-state-for-lsr3-charlie.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | External Basic authentication placeholder. |
| `Accept` | `application/xml` | Requests an XML response. |

The query parameter `content=all` requests configuration and operational data. The GET has no body or `Content-Type` header.

#### RESTCONF resource and YANG objects {id="netconf-read-topology-state-for-lsr3-charlie-yang-objects"}

The controller-side path uses [network-topology, revision 2013-10-21](https://github.com/opendaylight/mdsal/blob/15.0.x/model/ietf-topology/src/main/yang/network-topology@2013-10-21.yang), with `topology` keyed by `topology-id` and `node` keyed by `node-id`. The [NETCONF topology augmentation](https://github.com/opendaylight/netconf/blob/10.0.x/apps/netconf-topology/src/main/yang/netconf-node-topology.yang) adds `netconf-node`, whose operational connection fields come from the [connection-oper grouping](https://github.com/opendaylight/netconf/blob/10.0.x/plugins/netconf-client-mdsal/src/main/yang/odl-netconf-device.yang).

<compare type="left-right" first-title="Request body" second-title="YANG resource composition (abridged)">
    <code-block lang="text">No request body: the URI selects the resource.</code-block>
    <code-block lang="text">
container network-topology {
  list topology [key: topology-id] {
    leaf topology-id: topology-id;                 // topology-netconf
    list node [key: node-id] {
      leaf node-id: node-id;                       // sma-xrv-lsr3-charlie
      netconf-node-topology:netconf-node {
        leaf connection-status { config false; }
        leaf session-id { config false; }
        leaf-list available-capabilities { config false; }
        leaf-list unavailable-capabilities { config false; }
      }
    }
  }
}
    </code-block>
</compare>

| Resource element | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `topology=topology-netconf` | Topology list key | topology-netconf | Selects the NETCONF device topology. |
| `node=sma-xrv-lsr3-charlie` | Node list key | sma-xrv-lsr3-charlie | Selects this controller-side device entry. |
| `netconf-node` | NETCONF augmentation container | Returned when present | Owns connection configuration and operational status in the researched 10.0.x model. |

#### Effect and response {id="netconf-read-topology-state-for-lsr3-charlie-effect-response"}

A successful read is expected to return `200 OK` with the selected XML representation. The GET does not modify configuration or initiate a new protocol session. The source Postman collection contains no recorded response for this request.

### Read Topology State for LSR4 {id="netconf-read-topology-state-for-lsr4"}

This GET retrieves OpenDaylight's topology and NETCONF connection-state data when available. Router configuration and operational data reside under the separate mounted device tree.

#### Request {id="netconf-read-topology-state-for-lsr4-request"}

<code-block lang="http" src="../../src/main/postman/netconf/read-topology-state-for-lsr4.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | External Basic authentication placeholder. |
| `Accept` | `application/xml` | Requests an XML response. |

The query parameter `content=all` requests configuration and operational data. The GET has no body or `Content-Type` header.

#### RESTCONF resource and YANG objects {id="netconf-read-topology-state-for-lsr4-yang-objects"}

The controller-side path uses [network-topology, revision 2013-10-21](https://github.com/opendaylight/mdsal/blob/15.0.x/model/ietf-topology/src/main/yang/network-topology@2013-10-21.yang), with `topology` keyed by `topology-id` and `node` keyed by `node-id`. The [NETCONF topology augmentation](https://github.com/opendaylight/netconf/blob/10.0.x/apps/netconf-topology/src/main/yang/netconf-node-topology.yang) adds `netconf-node`, whose operational connection fields come from the [connection-oper grouping](https://github.com/opendaylight/netconf/blob/10.0.x/plugins/netconf-client-mdsal/src/main/yang/odl-netconf-device.yang).

<compare type="left-right" first-title="Request body" second-title="YANG resource composition (abridged)">
    <code-block lang="text">No request body: the URI selects the resource.</code-block>
    <code-block lang="text">
container network-topology {
  list topology [key: topology-id] {
    leaf topology-id: topology-id;                 // topology-netconf
    list node [key: node-id] {
      leaf node-id: node-id;                       // sma-xrv-lsr4-delta
      netconf-node-topology:netconf-node {
        leaf connection-status { config false; }
        leaf session-id { config false; }
        leaf-list available-capabilities { config false; }
        leaf-list unavailable-capabilities { config false; }
      }
    }
  }
}
    </code-block>
</compare>

| Resource element | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `topology=topology-netconf` | Topology list key | topology-netconf | Selects the NETCONF device topology. |
| `node=sma-xrv-lsr4-delta` | Node list key | sma-xrv-lsr4-delta | Selects this controller-side device entry. |
| `netconf-node` | NETCONF augmentation container | Returned when present | Owns connection configuration and operational status in the researched 10.0.x model. |

#### Effect and response {id="netconf-read-topology-state-for-lsr4-effect-response"}

A successful read is expected to return `200 OK` with the selected XML representation. The GET does not modify configuration or initiate a new protocol session. The source Postman collection contains no recorded response for this request.

### Read NETCONF YANG Mount Point For XRv Router {id="netconf-read-netconf-yang-mount-point-for-xrv-router"}

The final `yang-ext:mount` segment enters the mounted router's YANG data tree. The resulting resource is the device's data root, which exposes the top-level modules available on that router. The controller maintains the surrounding topology-node metadata separately.

#### Request {id="netconf-read-netconf-yang-mount-point-for-xrv-router-request"}

<code-block lang="http" src="../../src/main/postman/netconf/read-netconf-yang-mount-point-for-xrv-router.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | External Basic authentication placeholder. |
| `Accept` | `application/xml` | Requests an XML response. |

No query parameters are supplied, so the GET uses the default `content=all` selection. The request has no body or `Content-Type` header.

#### RESTCONF resource and YANG objects {id="netconf-read-netconf-yang-mount-point-for-xrv-router-yang-objects"}

The controller-side path uses [network-topology, revision 2013-10-21](https://github.com/opendaylight/mdsal/blob/15.0.x/model/ietf-topology/src/main/yang/network-topology@2013-10-21.yang), with `topology` keyed by `topology-id` and `node` keyed by `node-id`. The [NETCONF topology augmentation](https://github.com/opendaylight/netconf/blob/10.0.x/apps/netconf-topology/src/main/yang/netconf-node-topology.yang) adds `netconf-node`, whose operational connection fields come from the [connection-oper grouping](https://github.com/opendaylight/netconf/blob/10.0.x/plugins/netconf-client-mdsal/src/main/yang/odl-netconf-device.yang).

<compare type="left-right" first-title="Request body" second-title="YANG resource composition (abridged)">
    <code-block lang="text">No request body: the URI selects the resource.</code-block>
    <code-block lang="text">
container network-topology {
  list topology [key: topology-id] {
    leaf topology-id: topology-id;                 // topology-netconf
    list node [key: node-id] {
      leaf node-id: node-id;                       // sma-xrv-lsr1-alpha
      yang-ext:mount {                             // RESTCONF mount boundary
        // mounted device's top-level containers and lists begin here
      }
    }
  }
}
    </code-block>
</compare>

| Resource element | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `topology=topology-netconf` | Topology list key | topology-netconf | Selects the NETCONF device topology. |
| `node=sma-xrv-lsr1-alpha` | Node list key | sma-xrv-lsr1-alpha | Selects this controller-side device entry. |
| `yang-ext:mount` | RESTCONF mount boundary | Mounted root | Selects device data exposed through the NETCONF mount. |

#### Effect and response {id="netconf-read-netconf-yang-mount-point-for-xrv-router-effect-response"}

A successful read is expected to return `200 OK` with the selected XML representation. The GET does not modify configuration or initiate a new protocol session. The source Postman collection contains no recorded response for this request.

### Read NETCONF YANG Capabilities For LSR1 {id="netconf-read-netconf-yang-capabilities-for-lsr1"}

This endpoint retrieves the device's schema inventory, including module identifiers and revisions. It reads metadata from `netconf-state/schemas` rather than the `netconf-state/capabilities` leaf-list. The schema inventory does not include the module source text.

#### Request {id="netconf-read-netconf-yang-capabilities-for-lsr1-request"}

<code-block lang="http" src="../../src/main/postman/netconf/read-netconf-yang-capabilities-for-lsr1.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | External Basic authentication placeholder. |
| `Accept` | `application/xml` | Requests an XML response. |

No query parameters are supplied, so the GET uses the default `content=all` selection. The request has no body or `Content-Type` header.

#### RESTCONF resource and YANG objects {id="netconf-read-netconf-yang-capabilities-for-lsr1-yang-objects"}

The controller-side path uses [network-topology, revision 2013-10-21](https://github.com/opendaylight/mdsal/blob/15.0.x/model/ietf-topology/src/main/yang/network-topology@2013-10-21.yang), with `topology` keyed by `topology-id` and `node` keyed by `node-id`. The [NETCONF topology augmentation](https://github.com/opendaylight/netconf/blob/10.0.x/apps/netconf-topology/src/main/yang/netconf-node-topology.yang) adds `netconf-node`, whose operational connection fields come from the [connection-oper grouping](https://github.com/opendaylight/netconf/blob/10.0.x/plugins/netconf-client-mdsal/src/main/yang/odl-netconf-device.yang). [RFC 6022, section 5](https://www.rfc-editor.org/rfc/rfc6022.html#section-5) defines `ietf-netconf-monitoring` (revision 2010-10-04), namespace `urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring`.

<compare type="left-right" first-title="Request body" second-title="YANG resource composition (abridged)">
    <code-block lang="text">No request body: the URI selects the resource.</code-block>
    <code-block lang="text">
container netconf-state { config false;
  container schemas {
    list schema [key: identifier version format] {
      leaf identifier { type string; }
      leaf version { type string; }
      leaf format { type identityref { base schema-format; } }
      leaf namespace { type inet:uri; }
      leaf-list location { type inet:uri; }
    }
  }
}
    </code-block>
</compare>

| Resource element | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `topology=topology-netconf` | Topology list key | topology-netconf | Selects the NETCONF device topology. |
| `node=sma-xrv-lsr1-alpha` | Node list key | sma-xrv-lsr1-alpha | Selects this controller-side device entry. |
| `schemas/schema` | Operational list | No individual schema key specified | Inventory keyed in order by identifier, version and format. |

#### Effect and response {id="netconf-read-netconf-yang-capabilities-for-lsr1-effect-response"}

A successful read is expected to return `200 OK` with the selected XML representation. The GET does not modify configuration or initiate a new protocol session. The source Postman collection contains no recorded response for this request.

### Read Interface Operational Properties from LSR1 {id="netconf-read-netconf-yang-interface-configuration-from-lsr1"}

The saved request title mentions interface configuration, but the selected `Cisco-IOS-XR-ifmgr-oper:interface-properties` resource has `config false` status. It returns operational interface properties from the router. Writable interface settings are defined separately by `Cisco-IOS-XR-ifmgr-cfg`.

#### Request {id="netconf-read-netconf-yang-interface-configuration-from-lsr1-request"}

<code-block lang="http" src="../../src/main/postman/netconf/read-netconf-yang-interface-configuration-from-lsr1.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | External Basic authentication placeholder. |
| `Accept` | `application/xml` | Requests an XML response. |

No query parameters are supplied, so the GET uses the default `content=all` selection. The request has no body or `Content-Type` header.

#### RESTCONF resource and YANG objects {id="netconf-read-netconf-yang-interface-configuration-from-lsr1-yang-objects"}

The controller-side path uses [network-topology, revision 2013-10-21](https://github.com/opendaylight/mdsal/blob/15.0.x/model/ietf-topology/src/main/yang/network-topology@2013-10-21.yang), with `topology` keyed by `topology-id` and `node` keyed by `node-id`. The [NETCONF topology augmentation](https://github.com/opendaylight/netconf/blob/10.0.x/apps/netconf-topology/src/main/yang/netconf-node-topology.yang) adds `netconf-node`, whose operational connection fields come from the [connection-oper grouping](https://github.com/opendaylight/netconf/blob/10.0.x/plugins/netconf-client-mdsal/src/main/yang/odl-netconf-device.yang). For the router's mounted data, this comparison uses the [Cisco XR 7.6.1 catalog copy of Cisco-IOS-XR-ifmgr-oper](https://github.com/YangModels/yang/blob/main/vendor/cisco/xr/761/Cisco-IOS-XR-ifmgr-oper.yang) (revision 2020-10-01). The installed device's advertised YANG revision and deviations were not recorded.

<compare type="left-right" first-title="Request body" second-title="YANG resource composition (abridged)">
    <code-block lang="text">No request body: the URI selects the resource.</code-block>
    <code-block lang="text">
container interface-properties { config false;
  container data-nodes {
    list data-node [key: data-node-name] {
      leaf data-node-name: string;
      container system-view {
        container interfaces {
          list interface [key: interface-name] { leaf interface-name: string; }
        }
      }
    }
  }
}
    </code-block>
</compare>

| Resource element | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `topology=topology-netconf` | Topology list key | topology-netconf | Selects the NETCONF device topology. |
| `node=sma-xrv-lsr1-alpha` | Node list key | sma-xrv-lsr1-alpha | Selects this controller-side device entry. |
| `interface-properties` | Mounted device resource | Selected by URI | Read-only interface properties across the device data-node views. |

#### Effect and response {id="netconf-read-netconf-yang-interface-configuration-from-lsr1-effect-response"}

A successful read is expected to return `200 OK` with the selected XML representation. The GET does not modify configuration or initiate a new protocol session. The source Postman collection contains no recorded response for this request.

### Read IPv4 Interface Operational Data from LSR1 {id="netconf-read-netconf-yang-ipv4-interfaces-from-routers"}

The request targets only `sma-xrv-lsr1-alpha`, although the saved title refers to multiple routers. It retrieves operational IPv4 interface data from the `Cisco-IOS-XR-ipv4-io-oper:ipv4-network` container, which does not configure interface addresses.

#### Request {id="netconf-read-netconf-yang-ipv4-interfaces-from-routers-request"}

<code-block lang="http" src="../../src/main/postman/netconf/read-netconf-yang-ipv4-interfaces-from-routers.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | External Basic authentication placeholder. |
| `Accept` | `application/xml` | Requests an XML response. |

No query parameters are supplied, so the GET uses the default `content=all` selection. The request has no body or `Content-Type` header.

#### RESTCONF resource and YANG objects {id="netconf-read-netconf-yang-ipv4-interfaces-from-routers-yang-objects"}

The controller-side path uses [network-topology, revision 2013-10-21](https://github.com/opendaylight/mdsal/blob/15.0.x/model/ietf-topology/src/main/yang/network-topology@2013-10-21.yang), with `topology` keyed by `topology-id` and `node` keyed by `node-id`. The [NETCONF topology augmentation](https://github.com/opendaylight/netconf/blob/10.0.x/apps/netconf-topology/src/main/yang/netconf-node-topology.yang) adds `netconf-node`, whose operational connection fields come from the [connection-oper grouping](https://github.com/opendaylight/netconf/blob/10.0.x/plugins/netconf-client-mdsal/src/main/yang/odl-netconf-device.yang). For the router's mounted data, this comparison uses the [Cisco XR 7.6.1 catalog copy of Cisco-IOS-XR-ipv4-io-oper](https://github.com/YangModels/yang/blob/main/vendor/cisco/xr/761/Cisco-IOS-XR-ipv4-io-oper.yang) (revision 2021-12-09). The installed device's advertised YANG revision and deviations were not recorded.

<compare type="left-right" first-title="Request body" second-title="YANG resource composition (abridged)">
    <code-block lang="text">No request body: the URI selects the resource.</code-block>
    <code-block lang="text">
container ipv4-network { config false;
  container nodes {
    list node [key: node-name] {
      leaf node-name: string;
      container interface-data { container vrfs {
        list vrf [key: vrf-name] {
          leaf vrf-name: string;
          container briefs { list brief [key: interface-name] { leaf interface-name: string; } }
          container details { list detail [key: interface-name] { leaf interface-name: string; } }
        }
      }}
    }
  }
}
    </code-block>
</compare>

| Resource element | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `topology=topology-netconf` | Topology list key | topology-netconf | Selects the NETCONF device topology. |
| `node=sma-xrv-lsr1-alpha` | Node list key | sma-xrv-lsr1-alpha | Selects this controller-side device entry. |
| `ipv4-network` | Mounted device resource | Selected by URI | Read-only IPv4 interface and VRF data for the mounted router. |

#### Effect and response {id="netconf-read-netconf-yang-ipv4-interfaces-from-routers-effect-response"}

A successful read is expected to return `200 OK` with the selected XML representation. The GET does not modify configuration or initiate a new protocol session. The saved Postman result records `200 OK` without a response body, so the returned fields and compatibility with the current controller cannot be confirmed from that result.

### Read the Test Loopback Interface {id="netconf-get-test-loopback-if-information"}

The request selects `Loopback99` from the mounted OpenConfig `interfaces/interface` list, which is keyed by `name`. The key references `../config/name`. The interface entry contains both configuration and a `config false` state container, and the request reads both through the default RESTCONF `content=all` selection.

#### Request {id="netconf-get-test-loopback-if-information-request"}

<code-block lang="http" src="../../src/main/postman/netconf/get-test-loopback-if-information.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | External Basic authentication placeholder. |
| `Accept` | `application/xml` | Requests an XML response. |

No query parameters are supplied, so the GET uses the default `content=all` selection. The request has no body or `Content-Type` header.

#### RESTCONF resource and YANG objects {id="netconf-get-test-loopback-if-information-yang-objects"}

The controller-side path uses [network-topology, revision 2013-10-21](https://github.com/opendaylight/mdsal/blob/15.0.x/model/ietf-topology/src/main/yang/network-topology@2013-10-21.yang), with `topology` keyed by `topology-id` and `node` keyed by `node-id`. The [NETCONF topology augmentation](https://github.com/opendaylight/netconf/blob/10.0.x/apps/netconf-topology/src/main/yang/netconf-node-topology.yang) adds `netconf-node`, whose operational connection fields come from the [connection-oper grouping](https://github.com/opendaylight/netconf/blob/10.0.x/plugins/netconf-client-mdsal/src/main/yang/odl-netconf-device.yang). For the router's mounted data, this comparison uses the [Cisco XR 7.6.1 catalog copy of openconfig-interfaces](https://github.com/YangModels/yang/blob/main/vendor/cisco/xr/761/openconfig-interfaces.yang) (revision see cited file). The installed device's advertised YANG revision and deviations were not recorded.

<compare type="left-right" first-title="Request body" second-title="YANG resource composition (abridged)">
    <code-block lang="text">No request body: the URI selects the resource.</code-block>
    <code-block lang="text">
container interfaces {
  list interface [key: name] {
    leaf name { type leafref { path "../config/name"; } } // Loopback99
    container config { leaf name: string; /* configured interface fields */ }
    container state { config false; /* observed interface fields */ }
    container subinterfaces {
      list subinterface [key: index] {
        leaf index { type uint32; }
        container config { leaf index: uint32; }
        container state { config false; }
      }
    }
  }
}
    </code-block>
</compare>

| Resource element | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `topology=topology-netconf` | Topology list key | topology-netconf | Selects the NETCONF device topology. |
| `node=sma-xrv-lsr1-alpha` | Node list key | sma-xrv-lsr1-alpha | Selects this controller-side device entry. |
| `interface=Loopback99` | Mounted device resource | Selected by URI | The selected OpenConfig interface entry, subject to device support and deviations. |

#### Effect and response {id="netconf-get-test-loopback-if-information-effect-response"}

A successful read is expected to return `200 OK` with the selected XML representation. The GET does not modify configuration or initiate a new protocol session. The source Postman collection contains no recorded response for this request.

### Read Tunnel Heads for Name Correlation on LSR4 {id="netconf-extract-actual-tunnel-ids-based-on-name-correlation"}

This request retrieves the operational tunnel-head entries from LSR4. A consuming application can correlate the returned entries with symbolic LSP names, since the request itself does not filter or resolve names. The enclosing `mpls-te` container has `config false` status. The `content=all` selector still returns operational data for that resource.

#### Request {id="netconf-extract-actual-tunnel-ids-based-on-name-correlation-request"}

<code-block lang="http" src="../../src/main/postman/netconf/extract-actual-tunnel-ids-based-on-name-correlation.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | External Basic authentication placeholder. |
| `Accept` | `application/xml` | Requests an XML response. |

The query parameter `content=all` requests configuration and operational data. The GET has no body or `Content-Type` header.

#### RESTCONF resource and YANG objects {id="netconf-extract-actual-tunnel-ids-based-on-name-correlation-yang-objects"}

The controller-side path uses [network-topology, revision 2013-10-21](https://github.com/opendaylight/mdsal/blob/15.0.x/model/ietf-topology/src/main/yang/network-topology@2013-10-21.yang), with `topology` keyed by `topology-id` and `node` keyed by `node-id`. The [NETCONF topology augmentation](https://github.com/opendaylight/netconf/blob/10.0.x/apps/netconf-topology/src/main/yang/netconf-node-topology.yang) adds `netconf-node`, whose operational connection fields come from the [connection-oper grouping](https://github.com/opendaylight/netconf/blob/10.0.x/plugins/netconf-client-mdsal/src/main/yang/odl-netconf-device.yang). For the router's mounted data, this comparison uses the [Cisco XR 7.6.1 catalog copy of Cisco-IOS-XR-mpls-te-oper](https://github.com/YangModels/yang/blob/main/vendor/cisco/xr/761/Cisco-IOS-XR-mpls-te-oper.yang) (revision 2021-08-09). The installed device's advertised YANG revision and deviations were not recorded.

<compare type="left-right" first-title="Request body" second-title="YANG resource composition (abridged)">
    <code-block lang="text">No request body: the URI selects the resource.</code-block>
    <code-block lang="text">
container mpls-te { config false;
  container p2p-p2mp-tunnel { container tunnel-heads {
    list tunnel-head [key: tunnel-name] {
      leaf tunnel-name: string;
      uses MPLS-TE-P2MP-TUNNEL-BAG; // expands operational tunnel fields
    }
  }}
}
    </code-block>
</compare>

| Resource element | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `topology=topology-netconf` | Topology list key | topology-netconf | Selects the NETCONF device topology. |
| `node=sma-xrv-lsr4-delta` | Node list key | sma-xrv-lsr4-delta | Selects this controller-side device entry. |
| `tunnel-heads` | Mounted device resource | Selected by URI | Collection of head-end P2P/P2MP tunnel operational entries keyed by tunnel-name. |

#### Effect and response {id="netconf-extract-actual-tunnel-ids-based-on-name-correlation-effect-response"}

A successful read is expected to return `200 OK` with the selected XML representation. The GET does not modify configuration or initiate a new protocol session. The saved Postman result records `200 OK` without a response body, so the returned fields and compatibility with the current controller cannot be confirmed from that result.

### Delete Any Topology Node From NETCONF Topology {id="netconf-delete-any-topology-node-from-netconf-topology"}

The saved title refers to any topology node, but the URI specifically addresses `sma-xrv-lsr1-alpha`. Deleting that controller-side node removes its NETCONF connector without deleting the topology or the router's own interface configuration.

#### Request {id="netconf-delete-any-topology-node-from-netconf-topology-request"}

<code-block lang="http" src="../../src/main/postman/netconf/delete-any-topology-node-from-netconf-topology.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | External Basic authentication placeholder. |
| `Accept` | `application/xml` | Requests an XML response. |

The request has no query parameters, body, or `Content-Type` header.

#### RESTCONF resource and YANG objects {id="netconf-delete-any-topology-node-from-netconf-topology-yang-objects"}

The controller-side path uses [network-topology, revision 2013-10-21](https://github.com/opendaylight/mdsal/blob/15.0.x/model/ietf-topology/src/main/yang/network-topology@2013-10-21.yang), with `topology` keyed by `topology-id` and `node` keyed by `node-id`. The [NETCONF topology augmentation](https://github.com/opendaylight/netconf/blob/10.0.x/apps/netconf-topology/src/main/yang/netconf-node-topology.yang) adds `netconf-node`, whose operational connection fields come from the [connection-oper grouping](https://github.com/opendaylight/netconf/blob/10.0.x/plugins/netconf-client-mdsal/src/main/yang/odl-netconf-device.yang).

<compare type="left-right" first-title="Request body" second-title="YANG resource composition (abridged)">
    <code-block lang="text">No request body: the URI selects the resource.</code-block>
    <code-block lang="text">
container network-topology {
  list topology [key: topology-id] {
    leaf topology-id: topology-id;                 // topology-netconf
    list node [key: node-id] {
      leaf node-id: node-id;                       // sma-xrv-lsr1-alpha
      // deletion addresses this complete list entry
    }
  }
}
    </code-block>
</compare>

| Resource element | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `topology=topology-netconf` | Topology list key | topology-netconf | Selects the NETCONF device topology. |
| `node=sma-xrv-lsr1-alpha` | Node list key | sma-xrv-lsr1-alpha | Selects this controller-side device entry. |

#### Effect and response {id="netconf-delete-any-topology-node-from-netconf-topology-effect-response"}

The expected response to a successful deletion is `204 No Content`. Removing the configured topology node triggers removal of its NETCONF connector and mount. Connection teardown and operational cleanup follow the connector lifecycle, while configuration held by the router remains unchanged. The source Postman collection contains no recorded response.

### Create Loopback99 on LSR1 {id="netconf-create-loopback-interface-on-routers"}

#### Request {id="netconf-create-loopback-interface-on-routers-request"}

<code-block lang="http" src="../../src/main/postman/netconf/create-loopback-interface-on-routers.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Controller authentication placeholder. |
| `Accept` | `application/xml` | Requested response encoding. |
| `Content-Type` | `application/xml` | XML request encoding. |

The request has no query parameters.

#### RESTCONF resource and YANG objects {id="netconf-create-loopback-interface-on-routers-yang-objects"}

The data URI selects `topology-netconf`, the `sma-xrv-lsr1-alpha` node, and its `yang-ext:mount`. The remaining resource path addresses the router. [Cisco-IOS-XR-ifmgr-cfg](https://github.com/YangModels/yang/blob/main/vendor/cisco/xr/711/Cisco-IOS-XR-ifmgr-cfg.yang) defines `interface-configurations/interface-configuration` with keys `active interface-name` in that order, yielding `act,Loopback99`. The cited IOS XR 7.1.1 model provides a vendor reference (revision `2019-04-05`). The mounted device's advertised revision was not recorded.

<compare type="top-bottom" first-title="Literal request payload (XML)" second-title="YANG model composition (abridged)">
<code-block lang="xml">
&lt;interface-configuration xmlns="http://cisco.com/ns/yang/Cisco-IOS-XR-ifmgr-cfg"&gt;

    &lt;active&gt;act&lt;/active&gt;
    &lt;interface-name&gt;Loopback99&lt;/interface-name&gt;
    &lt;description&gt;ODL NETCONF configuration test&lt;/description&gt;
&lt;/interface-configuration&gt;
</code-block>
<code-block lang="text">
container interface-configurations {
list interface-configuration {
key "active interface-name";
leaf active { type Interface-active; }
leaf interface-name { type xr:Interface-name; }
leaf description { type string; }
}
}
</code-block>
</compare>

| Payload object or field | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `active` | First list key | `act` | Selects active interface configuration. It does not indicate the operational state of the interface. |
| `interface-name` | Second list key | `Loopback99` | Matches the addressed interface instance. |
| `description` | String leaf | `ODL NETCONF configuration test` | Sets the interface description. No IP address is supplied. |

#### Effect and response {id="netconf-create-loopback-interface-on-routers-effect-response"}

The PUT creates or replaces the complete `Loopback99` interface configuration through the NETCONF mount. The supplied entry contains an interface description without an IP address. Expected success is `201 Created` for a new entry or `204 No Content` for an existing one. The source collection contains no recorded response.

### Configure Loopback0 Address Borrowing on Tunnel TE200 {id="netconf-configure-lo-on-tunnel-created"}

#### Request {id="netconf-configure-lo-on-tunnel-created-request"}

<code-block lang="http" src="../../src/main/postman/netconf/configure-lo-on-tunnel-created.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Controller authentication placeholder. |
| `Accept` | `application/xml` | Requested response encoding. |
| `Content-Type` | `application/xml` | XML request encoding. |

The request has no query parameters.

#### RESTCONF resource and YANG objects {id="netconf-configure-lo-on-tunnel-created-yang-objects"}

The data URI selects `topology-netconf`, the `sma-xrv-lsr1-alpha` node, and its `yang-ext:mount`. The remaining resource path addresses the router. [Cisco-IOS-XR-ifmgr-cfg](https://github.com/YangModels/yang/blob/main/vendor/cisco/xr/711/Cisco-IOS-XR-ifmgr-cfg.yang) defines `interface-configurations/interface-configuration` with keys `active interface-name` in that order, yielding `act,tunnel-te200`. The cited IOS XR 7.1.1 model provides a vendor reference (revision `2019-04-05`). The mounted device's advertised revision was not recorded.

[Cisco-IOS-XR-ipv4-io-cfg](https://github.com/YangModels/yang/blob/main/vendor/cisco/xr/711/Cisco-IOS-XR-ipv4-io-cfg.yang) augments each interface entry with `ipv4-network`. The `addresses/unnumbered` field is an interface-name leaf. The XML namespace changes from the interface manager model to the IPv4 model. The `unnumbered` leaf borrows an address from an existing interface instead of configuring a numbered IP-address leaf.

<compare type="top-bottom" first-title="Literal request payload (XML)" second-title="YANG model composition (abridged)">
<code-block lang="xml">
&lt;interface-configuration xmlns="http://cisco.com/ns/yang/Cisco-IOS-XR-ifmgr-cfg"&gt;
    &lt;active&gt;act&lt;/active&gt;
    &lt;interface-name&gt;tunnel-te200&lt;/interface-name&gt;

    &lt;ipv4-network xmlns="http://cisco.com/ns/yang/Cisco-IOS-XR-ipv4-io-cfg"&gt;
        &lt;addresses&gt;
            &lt;unnumbered&gt;Loopback0&lt;/unnumbered&gt;
        &lt;/addresses&gt;
    &lt;/ipv4-network&gt;
&lt;/interface-configuration&gt;
</code-block>
<code-block lang="text">
container interface-configurations {
list interface-configuration {
key "active interface-name";
leaf active { type Interface-active; }
leaf interface-name { type xr:Interface-name; }
leaf description { type string; }
}
}
// Cisco-IOS-XR-ipv4-io-cfg augmentation
augment "/ifmgr:interface-configurations/ifmgr:interface-configuration" {
container ipv4-network {
container addresses {
leaf unnumbered { type xr:Interface-name; }
}
}
}
</code-block>
</compare>

| Payload object or field | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `active` | First list key | `act` | Selects active interface configuration. It does not indicate the operational state of the interface. |
| `interface-name` | Second list key | `tunnel-te200` | Matches the addressed interface instance. |
| `ipv4-network/addresses/unnumbered` | Augmented configuration leaf | `Loopback0` | Interface supplying the unnumbered IPv4 address. |

#### Effect and response {id="netconf-configure-lo-on-tunnel-created-effect-response"}

The PUT creates or replaces the complete `tunnel-te200` interface entry through the NETCONF mount, including the unnumbered-address configuration. Expected success is `201 Created` when the entry is created or `204 No Content` when it is replaced. These responses do not establish the operational state of the tunnel or its forwarding path. The source Postman collection contains no recorded response.

### Assign the Loopback Address to Tunnel TE300 {id="netconf-assign-the-loopback-address-to-created-tunnels"}

#### Request {id="netconf-assign-the-loopback-address-to-created-tunnels-request"}

<code-block lang="http" src="../../src/main/postman/netconf/assign-the-loopback-address-to-created-tunnels.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Controller authentication placeholder. |
| `Accept` | `application/xml` | Requested response encoding. |
| `Content-Type` | `application/xml` | XML request encoding. |

The request has no query parameters.

#### RESTCONF resource and YANG objects {id="netconf-assign-the-loopback-address-to-created-tunnels-yang-objects"}

The data URI selects `topology-netconf`, the `sma-xrv-lsr1-alpha` node, and its `yang-ext:mount`. The remaining resource path addresses the router. [Cisco-IOS-XR-ifmgr-cfg](https://github.com/YangModels/yang/blob/main/vendor/cisco/xr/711/Cisco-IOS-XR-ifmgr-cfg.yang) defines `interface-configurations/interface-configuration` with keys `active interface-name` in that order, yielding `act,tunnel-te300`. The cited IOS XR 7.1.1 model provides a vendor reference (revision `2019-04-05`). The mounted device's advertised revision was not recorded.

[Cisco-IOS-XR-ipv4-io-cfg](https://github.com/YangModels/yang/blob/main/vendor/cisco/xr/711/Cisco-IOS-XR-ipv4-io-cfg.yang) augments each interface entry with `ipv4-network`. The `addresses/unnumbered` field is an interface-name leaf. The XML namespace changes from the interface manager model to the IPv4 model. The `unnumbered` leaf borrows an address from an existing interface instead of configuring a numbered IP-address leaf.

<compare type="top-bottom" first-title="Literal request payload (XML)" second-title="YANG model composition (abridged)">
<code-block lang="xml">
&lt;interface-configuration xmlns="http://cisco.com/ns/yang/Cisco-IOS-XR-ifmgr-cfg"&gt;
    &lt;active&gt;act&lt;/active&gt;
    &lt;interface-name&gt;tunnel-te300&lt;/interface-name&gt;

    &lt;ipv4-network xmlns="http://cisco.com/ns/yang/Cisco-IOS-XR-ipv4-io-cfg"&gt;
        &lt;addresses&gt;
            &lt;unnumbered&gt;Loopback0&lt;/unnumbered&gt;
        &lt;/addresses&gt;
    &lt;/ipv4-network&gt;
&lt;/interface-configuration&gt;
</code-block>
<code-block lang="text">
container interface-configurations {
list interface-configuration {
key "active interface-name";
leaf active { type Interface-active; }
leaf interface-name { type xr:Interface-name; }
leaf description { type string; }
}
}
// Cisco-IOS-XR-ipv4-io-cfg augmentation
augment "/ifmgr:interface-configurations/ifmgr:interface-configuration" {
container ipv4-network {
container addresses {
leaf unnumbered { type xr:Interface-name; }
}
}
}
</code-block>
</compare>

| Payload object or field | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `active` | First list key | `act` | Selects active interface configuration. It does not indicate the operational state of the interface. |
| `interface-name` | Second list key | `tunnel-te300` | Matches the addressed interface instance. |
| `ipv4-network/addresses/unnumbered` | Augmented configuration leaf | `Loopback0` | Interface supplying the unnumbered IPv4 address. |

#### Effect and response {id="netconf-assign-the-loopback-address-to-created-tunnels-effect-response"}

The PUT creates or replaces the complete `tunnel-te300` interface entry through the NETCONF mount, including the unnumbered-address configuration. Expected success is `201 Created` when the entry is created or `204 No Content` when it is replaced. These responses do not establish the operational state of the tunnel or its forwarding path. The saved Postman response is `204 No Content`. This is a historical result rather than a new request against the current controller.

### Assign the Test IPv4 Address to Loopback99 {id="netconf-put-test-ip-address-on-test-loopback-if"}

#### Request {id="netconf-put-test-ip-address-on-test-loopback-if-request"}

<code-block lang="http" src="../../src/main/postman/netconf/put-test-ip-address-on-test-loopback-if.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Controller authentication placeholder. |
| `Accept` | `application/xml` | Requested response encoding. |
| `Content-Type` | `application/xml` | XML request encoding. |

The request has no query parameters.

#### RESTCONF resource and YANG objects {id="netconf-put-test-ip-address-on-test-loopback-if-yang-objects"}

The request traverses LSR1's NETCONF mount to the OpenConfig resource `openconfig-interfaces:interfaces/interface=Loopback99/subinterfaces/subinterface=0`. The enclosing interface is keyed by `name`, and the subinterface is keyed by `index`. The [canonical OpenConfig interface model](https://github.com/openconfig/public/blob/master/release/models/interfaces/openconfig-interfaces.yang) defines the subinterface key as a leafref to `config/index`. The [openconfig-if-ip](https://github.com/openconfig/public/blob/master/release/models/interfaces/openconfig-if-ip.yang) augmentation contributes `ipv4` and an address list keyed by `ip`, which references `config/ip`. These upstream models explain the schema composition. The router's deployed model revisions were not recorded.

<compare type="top-bottom" first-title="Literal request payload (XML)" second-title="YANG model composition (abridged)">
<code-block lang="xml">
&lt;subinterface xmlns="http://openconfig.net/yang/interfaces"&gt;
    &lt;index&gt;0&lt;/index&gt;
    &lt;config&gt;
        &lt;index&gt;0&lt;/index&gt;
        &lt;enabled&gt;true&lt;/enabled&gt;
    &lt;/config&gt;
    &lt;ipv4 xmlns="http://openconfig.net/yang/interfaces/ip"&gt;
        &lt;addresses&gt;
            &lt;address&gt;
                &lt;ip&gt;99.99.99.99&lt;/ip&gt;
                &lt;config&gt;
                    &lt;ip&gt;99.99.99.99&lt;/ip&gt;
                    &lt;prefix-length&gt;32&lt;/prefix-length&gt;
                &lt;/config&gt;
            &lt;/address&gt;
        &lt;/addresses&gt;
    &lt;/ipv4&gt;
&lt;/subinterface&gt;
</code-block>
<code-block lang="text">
// Canonical OpenConfig composition, not deployed-revision proof
container interfaces {
  list interface [key: name] {
    leaf name { type leafref { path "../config/name"; } }
    container subinterfaces {
      list subinterface [key: index] {
        leaf index { type leafref { path "../config/index"; } }
        container config {
          leaf index { type uint32; }
          leaf enabled { type boolean; }
        }
        openconfig-if-ip:ipv4 {             // augmentation
          container addresses {
            list address [key: ip] {
              leaf ip { type leafref { path "../config/ip"; } }
              container config {
                leaf ip { type inet:ipv4-address; }
                leaf prefix-length { type uint8 { range "0..32"; } }
              }
            }
          }
        }
      }
    }
  }
}
</code-block>
</compare>

| Payload object or field | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `index`, `config/index` | List key and its target | `0` | Select the default logical subinterface. |
| `config/enabled` | Boolean configuration | `true` | Requested administrative enablement. |
| `ipv4/addresses/address/ip`, `config/ip` | Address key and target | `99.99.99.99` | Address to configure. |
| `prefix-length` | IPv4 prefix length | `32` | Host prefix. |

#### Effect and response {id="netconf-put-test-ip-address-on-test-loopback-if-effect-response"}

The PUT creates or replaces subinterface `0` through the router's NETCONF mount, including its IPv4 address subtree. Expected success is `201 Created` for creation or `204 No Content` for replacement. The response does not establish reachability to `99.99.99.99`. The source Postman collection contains no recorded response.

### Retrieve the Router’s IPv4 Operational YANG Schema {id="netconf-pull-netconf-yang-schemas-for-module"}

#### Request {id="netconf-pull-netconf-yang-schemas-for-module-request"}

<code-block lang="http" src="../../src/main/postman/netconf/pull-netconf-yang-schemas-for-module.http"/>

| Header | Value | Explanation |
|---|---|---|
| `Authorization` | `Basic {{odl_basic_auth}}` | Controller authentication placeholder. |
| `Accept` | `application/xml` | Requested response encoding. |
| `Content-Type` | `application/xml` | XML request encoding. |

The request has no query parameters.

#### RESTCONF resource and YANG objects {id="netconf-pull-netconf-yang-schemas-for-module-yang-objects"}

The `/operations` URI traverses the LSR1 mount and invokes the router's `ietf-netconf-monitoring:get-schema` RPC. It retrieves YANG module source rather than interface counters or installing a schema on the controller. [ietf-netconf-monitoring, revision 2010-10-04](https://github.com/YangModels/yang/blob/main/standard/ietf/RFC/ietf-netconf-monitoring@2010-10-04.yang) defines the required `identifier`, optional `version` and `format`, and an `anyxml` output named `data`. The value `yang` is an identity derived from `schema-format` within the XML's default namespace.

<compare type="top-bottom" first-title="Literal request payload (XML)" second-title="YANG model composition (abridged)">
<code-block lang="xml">
&lt;input xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring"&gt;
    &lt;identifier&gt;Cisco-IOS-XR-ipv4-ma-oper&lt;/identifier&gt;
    &lt;version&gt;2018-08-01&lt;/version&gt;
    &lt;format&gt;yang&lt;/format&gt;
&lt;/input&gt;
</code-block>
<code-block lang="text">
rpc get-schema {
  input {
    leaf identifier { type string; mandatory true; }
    leaf version { type string; }
    leaf format { type identityref { base schema-format; } }
  }
  output { anyxml data; }
}
identity yang { base schema-format; }
</code-block>
</compare>

| Payload object or field | YANG role | Value in this request | Meaning |
|---|---|---|---|
| `identifier` | Required string | `Cisco-IOS-XR-ipv4-ma-oper` | Requested device module. |
| `version` | Optional schema revision string | `2018-08-01` | Selects the requested revision. |
| `format` | Schema-format identityref | `yang` | Requests YANG source syntax. |

#### Effect and response {id="netconf-pull-netconf-yang-schemas-for-module-effect-response"}

A successful RPC normally returns `200 OK` with the requested YANG module in its output. It does not modify configuration. Saved Postman results include `200 OK` and a Cisco IPv4 operational module, while another saved response belongs to a separate OpenConfig schema request. These historical examples do not confirm that every requested revision remains available on the current router.
