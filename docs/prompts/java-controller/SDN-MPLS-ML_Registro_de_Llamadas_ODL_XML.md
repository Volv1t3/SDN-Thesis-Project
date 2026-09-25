# Registro de Llamadas ODL XML

## Proposito

Este registro documenta las llamadas RESTCONF y RESTS realizadas por la aplicacion controladora SDN-MPLS-ML. Su objetivo es conservar una referencia unica para los encabezados XML, los serializadores, los deserializadores y los cuerpos esperados de cada intercambio con OpenDaylight.

Los registros operativos emiten los cuerpos XML completos de solicitud y respuesta en niveles DEBUG e INFO. El encabezado `Authorization` no se registra.

## Convenciones Generales

| Elemento | Valor requerido |
| --- | --- |
| RESTCONF y RESTS `Accept` | `application/xml` |
| RESTCONF y RESTS con cuerpo `Content-Type` | `application/xml` |
| RESTCONF `GET` | No envia cuerpo ni `Content-Type` |
| Clasificador ML | `application/json` mediante HTTP/1.1 |

## Calculo de Camino Restringido

| Campo | Valor |
| --- | --- |
| Metodo | `POST` |
| Endpoint | `/rests/operations/path-computation:get-constrained-path` |
| Cliente | `OdlOperationsClient` |
| Serializador | `PathComputationRequestXmlSerializer` |
| Deserializador | `PathComputationResponseXmlDeserializer` |

### Solicitud XML Esperada

```xml
<?xml version="1.0" encoding="UTF-8"?>
<input xmlns="urn:opendaylight:params:xml:ns:yang:path:computation">
    <graph-name>Some graph-name</graph-name>
    <source>0</source>
    <destination>0</destination>
    <constraints xmlns="urn:opendaylight:params:xml:ns:yang:path:computation">
        <loss>0</loss>
        <include-route xmlns="urn:opendaylight:params:xml:ns:yang:path:computation">
            <ipv4>0.0.0.0</ipv4>
            <ipv6>:</ipv6>
        </include-route>
        <delay>0</delay>
        <jitter>0</jitter>
        <metric>0</metric>
        <bandwidth>-92233720368547760</bandwidth>
        <te-metric>0</te-metric>
        <exclude-route xmlns="urn:opendaylight:params:xml:ns:yang:path:computation">
            <ipv4>0.0.0.0</ipv4>
            <ipv6>:</ipv6>
        </exclude-route>
        <class-type>0</class-type>
        <admin-group>0</admin-group>
        <address-family>ipv4</address-family>
    </constraints>
    <algorithm>spf</algorithm>
</input>
```
#### Schema XML

```text
path-computation_get-constrained-path_input{
graph-name*	string
example: Some graph-name
source	integer
example: 0
destination	integer
example: 0
constraints	path-computation_constraints{
loss	integer($int64)
example: 0
Maximum loss for selected edges

include-route	[
Speficy routes which must be included in the computed path, i.e. IRO

path-computation_constraints_include-route{
description:	
Speficy routes which must be included in the computed path, i.e. IRO

ipv4	string
example: 0.0.0.0
minLength: 0
maxLength: 2147483647
ipv6	string
example: :
minLength: 0
maxLength: 2147483647
}]
delay	integer($int64)
example: 0
Maximum end to end delay

jitter	integer($int64)
example: 0
Maximum delay variation for selected edges

metric	integer($int64)
example: 0
Maximum end to end IGP metric

bandwidth	number
example: -92233720368547760
Requested bandwidth for the computed path

te-metric	integer($int64)
example: 0
Maximum end to end Traffic Engineering metric

exclude-route	[
Speficy routes which must be excluded in the computed path, i.e. XRO

path-computation_constraints_exclude-route{
description:	
Speficy routes which must be excluded in the computed path, i.e. XRO

ipv4	string
example: 0.0.0.0
minLength: 0
maxLength: 2147483647
ipv6	string
example: :
minLength: 0
maxLength: 2147483647
}]
class-type	integer($int32)
example: 0
Class Type for bandwidth constraints

admin-group	integer($int64)
example: 0
Admin group to select edges

address-family	string
default: ipv4
example: ipv4
Enum:
[ ipv4, ipv6, sr-ipv4, sr-ipv6 ]
}
algorithm	string
default: spf
example: spf
Enum:
[ spf, cspf, samcra ]
}
```

### Respuesta XML Esperada

```xml
<!-- Pendiente: cuerpo XML esperado de la respuesta de calculo de camino. -->
<?xml version="1.0" encoding="UTF-8"?>
<output xmlns="urn:opendaylight:params:xml:ns:yang:path:computation">
    <path-description xmlns="urn:opendaylight:params:xml:ns:yang:path:computation">
        <remote-ipv6>:</remote-ipv6>
        <remote-ipv4>0.0.0.0</remote-ipv4>
        <ipv4>0.0.0.0</ipv4>
        <ipv6>:</ipv6>
        <sid>0</sid>
    </path-description>
    <status>idle</status>
    <computed-metric>0</computed-metric>
    <computed-te-metric>0</computed-te-metric>
    <computed-delay>0</computed-delay>
</output>
```

#### Schema 

```text 
path-computation_get-constrained-path_output{
path-description	[path-computation_path-description{
remote-ipv6	string
example: :
minLength: 0
maxLength: 2147483647
Remote IPv6 address

remote-ipv4	string
example: 0.0.0.0
minLength: 0
maxLength: 2147483647
Remote IPv4 address

ipv4	string
example: 0.0.0.0
minLength: 0
maxLength: 2147483647
ipv6	string
example: :
minLength: 0
maxLength: 2147483647
sid	integer($int64)
example: 0
Segment Routing Identifier as an Index or MPLS label

}]
status	string
example: idle
Enum:
[ idle, in-progress, active, completed, failed, no-path, no-source, no-destination, equal-endpoints ]
computed-metric	integer($int64)
example: 0
computed-te-metric	integer($int64)
example: 0
computed-delay	integer($int64)
example: 0
}
```

## Actualizacion de LSP Delegado

| Campo | Valor |
| --- | --- |
| Metodo | `POST` |
| Endpoint | `/rests/operations/network-topology-pcep:update-lsp` |
| Cliente | `OdlOperationsClient` |
| Serializador | `UpdateLspRequestXmlSerializer` |
| Deserializador | `UpdateLspResponseXmlDeserializer` |

### Solicitud XML Esperada

```xml
<!-- Pendiente: cuerpo XML esperado para network-topology-pcep:update-lsp. -->
<?xml version="1.0" encoding="UTF-8"?>
<input xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
    <network-topology-ref>string</network-topology-ref>
    <arguments xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
        <path-setup-type xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
            <pst>rsvp-te</pst>
        </path-setup-type>
        <metadata xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
        </metadata>
        <ero xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
            <processing-rule>true</processing-rule>
            <subobject xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                <loose>true</loose>
                <as-number xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                    <as-number>0</as-number>
                </as-number>
            </subobject>
            <ignore>true</ignore>
        </ero>
        <bandwidth xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
            <processing-rule>true</processing-rule>
            <bandwidth>string</bandwidth>
            <ignore>true</ignore>
        </bandwidth>
        <iro xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
            <processing-rule>true</processing-rule>
            <subobject xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                <loose>true</loose>
                <as-number xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                    <as-number>0</as-number>
                </as-number>
            </subobject>
            <ignore>true</ignore>
        </iro>
        <lsp xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
            <processing-rule>true</processing-rule>
            <plsp-id>0</plsp-id>
            <tlvs xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                <lsp-error-code xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                    <error-code>0</error-code>
                </lsp-error-code>
                <symbolic-path-name xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                    <path-name>string</path-name>
                </symbolic-path-name>
                <lsp-db-version xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                    <lsp-db-version-value>0</lsp-db-version-value>
                </lsp-db-version>
                <sr-policy-lsp xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                    <computation-priority>0</computation-priority>
                    <invalidation xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                        <oper-drop>true</oper-drop>
                        <config-drop>true</config-drop>
                    </invalidation>
                    <enl-policy>ipv4-only</enl-policy>
                </sr-policy-lsp>
                <lsp-identifiers xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                    <lsp-id>0</lsp-id>
                    <ipv4 xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                        <ipv4-extended-tunnel-id></ipv4-extended-tunnel-id>
                        <ipv4-tunnel-endpoint-address></ipv4-tunnel-endpoint-address>
                        <ipv4-tunnel-sender-address></ipv4-tunnel-sender-address>
                    </ipv4>
                    <tunnel-id>0</tunnel-id>
                </lsp-identifiers>
                <path-binding xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                    <binding-type>mpls-label</binding-type>
                    <flags xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                        <removal>true</removal>
                        <specified>true</specified>
                    </flags>
                    <mpls-label>0</mpls-label>
                </path-binding>
                <vendor-information-tlv xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                    <enterprise-number>0</enterprise-number>
                </vendor-information-tlv>
                <rsvp-error-spec xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                    <rsvp-error xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                        <node></node>
                        <code>0</code>
                        <flags>not-guilty in-place</flags>
                        <value>0</value>
                    </rsvp-error>
                </rsvp-error-spec>
            </tlvs>
            <ignore>true</ignore>
            <lsp-flags xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                <delegate>true</delegate>
                <pce-allocation>true</pce-allocation>
                <administrative>true</administrative>
                <operational>down</operational>
                <create>true</create>
                <sync>true</sync>
                <remove>true</remove>
            </lsp-flags>
        </lsp>
        <lspa xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
            <processing-rule>true</processing-rule>
            <tlvs xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                <auto-bandwidth-attributes xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                    <underflow-threshold-percentage xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                        <bandwidth>string</bandwidth>
                        <percentage>1</percentage>
                        <count>1</count>
                    </underflow-threshold-percentage>
                    <maximum-bandwidth xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                        <bandwidth>string</bandwidth>
                    </maximum-bandwidth>
                    <down-adjustment-threshold xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                        <bandwidth>string</bandwidth>
                    </down-adjustment-threshold>
                    <adjustment-interval xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                        <adjustment>1</adjustment>
                    </adjustment-interval>
                    <minimum-bandwidth xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                        <bandwidth>string</bandwidth>
                    </minimum-bandwidth>
                    <sample-interval xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                        <interval>1</interval>
                    </sample-interval>
                    <overflow-threshold xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                        <bandwidth>string</bandwidth>
                        <count>1</count>
                    </overflow-threshold>
                    <underflow-threshold xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                        <bandwidth>string</bandwidth>
                        <count>1</count>
                    </underflow-threshold>
                    <adjustment-threshold xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                        <bandwidth>string</bandwidth>
                    </adjustment-threshold>
                    <adjustment-threshold-percentage xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                        <bandwidth>0</bandwidth>
                        <percentage>1</percentage>
                    </adjustment-threshold-percentage>
                    <down-adjustment-interval xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                        <down-adjustment>1</down-adjustment>
                    </down-adjustment-interval>
                    <overflow-threshold-percentage xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                        <bandwidth>string</bandwidth>
                        <percentage>1</percentage>
                        <count>1</count>
                    </overflow-threshold-percentage>
                    <down-adjustment-threshold-percentage xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                        <bandwidth>0</bandwidth>
                        <percentage>1</percentage>
                    </down-adjustment-threshold-percentage>
                </auto-bandwidth-attributes>
                <vendor-information-tlv xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                    <enterprise-number>0</enterprise-number>
                </vendor-information-tlv>
            </tlvs>
            <include-any>0</include-any>
            <label-recording-desired>true</label-recording-desired>
            <se-style-desired>true</se-style-desired>
            <local-protection-desired>true</local-protection-desired>
            <session-name>Some session-name</session-name>
            <include-all>0</include-all>
            <exclude-any>0</exclude-any>
            <ignore>true</ignore>
            <setup-priority>0</setup-priority>
            <hold-priority>0</hold-priority>
        </lspa>
        <rro xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
            <processing-rule>true</processing-rule>
            <subobject xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                <protection-available>true</protection-available>
                <protection-in-use>true</protection-in-use>
                <ip-prefix xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                    <ip-prefix>0.0.0.0/0</ip-prefix>
                </ip-prefix>
            </subobject>
            <ignore>true</ignore>
        </rro>
        <association-group xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
            <processing-rule>true</processing-rule>
            <association-id>0</association-id>
            <association-tlvs xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                <global-association-source>0</global-association-source>
                <extended-association-id>0</extended-association-id>
                <protecting>true</protecting>
                <secondary>true</secondary>
                <protection-type>unprotected</protection-type>
            </association-tlvs>
            <association-type>path-protection</association-type>
            <removal-flag>true</removal-flag>
            <ignore>true</ignore>
            <association-source></association-source>
        </association-group>
        <xro xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
            <processing-rule>true</processing-rule>
            <subobject xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                <attribute>interface</attribute>
                <as-number xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                    <as-number>0</as-number>
                </as-number>
                <mandatory>true</mandatory>
            </subobject>
            <flags>fail fail</flags>
            <ignore>true</ignore>
        </xro>
        <of xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
            <processing-rule>true</processing-rule>
            <code>0</code>
            <tlvs xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                <vendor-information-tlv xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                    <enterprise-number>0</enterprise-number>
                </vendor-information-tlv>
            </tlvs>
            <ignore>true</ignore>
        </of>
        <metrics xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
            <metric xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                <processing-rule>true</processing-rule>
                <computed>true</computed>
                <metric-type>0</metric-type>
                <bound>true</bound>
                <ignore>true</ignore>
                <value>string</value>
            </metric>
        </metrics>
        <reoptimization-bandwidth xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
            <processing-rule>true</processing-rule>
            <bandwidth>string</bandwidth>
            <ignore>true</ignore>
        </reoptimization-bandwidth>
        <class-type xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
            <processing-rule>true</processing-rule>
            <ignore>true</ignore>
            <class-type>1</class-type>
        </class-type>
    </arguments>
    <name>Some name</name>
    <node>Some node</node>
</input>
```
#### Schema 

```text
network-topology-pcep_update-lsp_input{
network-topology-ref*	string
arguments	network-topology-pcep_arguments{
path-setup-type	network-topology-pcep_arguments_path-setup-type{
pst	string
default: rsvp-te
example: rsvp-te
PST=0: Path is setup via RSVP-TE signaling protocol(default).

Enum:
[ rsvp-te, sr-mpls, pcecc, srv6, native-ip ]
}
metadata	network-topology-pcep_arguments_metadata{
description:	
Container for external metadata attached to the LSP. Contents of this container are not propagated onto the router itself, so it is persisted only while the LSP is present.

}
ero	network-topology-pcep_arguments_ero{
processing-rule	boolean
default: false
example: true
subobject	[network-topology-pcep_arguments_ero_subobject{
loose*	boolean
example: true
as-number*	network-topology-pcep_arguments_ero_subobject_as-number{
as-number*	integer($int64)
example: 0
}
}]
ignore	boolean
default: false
example: true
}
bandwidth	network-topology-pcep_arguments_bandwidth{
processing-rule	boolean
default: false
example: true
bandwidth	string($byte)
ignore	boolean
default: false
example: true
}
iro	network-topology-pcep_arguments_iro{
processing-rule	boolean
default: false
example: true
subobject	[network-topology-pcep_arguments_iro_subobject{
loose*	boolean
example: true
as-number*	network-topology-pcep_arguments_iro_subobject_as-number{
as-number*	integer($int64)
example: 0
}
}]
ignore	boolean
default: false
example: true
}
lsp*	network-topology-pcep_arguments_lsp{
processing-rule	boolean
default: false
example: true
plsp-id*	integer($int64)
example: 0
tlvs	network-topology-pcep_arguments_lsp_tlvs{
lsp-error-code	network-topology-pcep_arguments_lsp_tlvs_lsp-error-code{
error-code	integer($int64)
example: 0
}
symbolic-path-name	network-topology-pcep_arguments_lsp_tlvs_symbolic-path-name{
path-name	string($byte)
}
lsp-db-version	network-topology-pcep_arguments_lsp_tlvs_lsp-db-version{
lsp-db-version-value	integer
example: 0
}
sr-policy-lsp	network-topology-pcep_arguments_lsp_tlvs_sr-policy-lsp{
computation-priority	integer($int32)
example: 0
invalidation	network-topology-pcep_arguments_lsp_tlvs_sr-policy-lsp_invalidation{
oper-drop	boolean
example: true
config-drop	boolean
example: true
}
enl-policy	string
example: ipv4-only
Enum:
[ ipv4-only, ipv6-only, both-ipv4-ipv6, no-enl ]
}
lsp-identifiers	network-topology-pcep_arguments_lsp_tlvs_lsp-identifiers{
lsp-id	integer($int64)
example: 0
ipv4*	network-topology-pcep_arguments_lsp_tlvs_lsp-identifiers_ipv4{
ipv4-extended-tunnel-id*	string
example:
minLength: 0
maxLength: 2147483647
ipv4-tunnel-endpoint-address*	string
example:
minLength: 0
maxLength: 2147483647
ipv4-tunnel-sender-address*	string
example:
minLength: 0
maxLength: 2147483647
}
tunnel-id	integer($int32)
example: 0
}
path-binding	network-topology-pcep_arguments_lsp_tlvs_path-binding{
binding-type	string
example: mpls-label
Enum:
[ mpls-label, mpls-label-entry, srv6, srv6-behavior ]
flags	network-topology-pcep_arguments_lsp_tlvs_path-binding_flags{
removal	boolean
example: true
specified	boolean
example: true
}
mpls-label	integer($int64)
example: 0
}
vendor-information-tlv	[
VENDOR-INFORMATION-TLV

network-topology-pcep_arguments_lsp_tlvs_vendor-information-tlv{
description:	
VENDOR-INFORMATION-TLV

enterprise-number	integer($int64)
example: 0
}]
rsvp-error-spec	network-topology-pcep_arguments_lsp_tlvs_rsvp-error-spec{
rsvp-error*	network-topology-pcep_arguments_lsp_tlvs_rsvp-error-spec_rsvp-error{
node*	string
example:
minLength: 0
maxLength: 2147483647
code*	integer($int32)
example: 0
flags	string
minItems: 0
default: not-guilty in-place
uniqueItems: true
Enum:
Array [ 2 ]
value*	integer($int32)
example: 0
}
}
}
ignore	boolean
default: false
example: true
lsp-flags	network-topology-pcep_arguments_lsp_lsp-flags{
delegate	boolean
default: false
example: true
pce-allocation	boolean
default: false
example: true
administrative	boolean
default: false
example: true
operational	string
default: down
example: down
Enum:
[ down, up, active, going-down, going-up ]
create	boolean
default: false
example: true
sync	boolean
default: false
example: true
remove	boolean
default: false
example: true
}
}
lspa	network-topology-pcep_arguments_lspa{
processing-rule	boolean
default: false
example: true
tlvs	network-topology-pcep_arguments_lspa_tlvs{
auto-bandwidth-attributes	network-topology-pcep_arguments_lspa_tlvs_auto-bandwidth-attributes{
underflow-threshold-percentage	network-topology-pcep_arguments_lspa_tlvs_auto-bandwidth-attributes_underflow-threshold-percentage{
bandwidth	string($byte)
percentage	integer($int32)
example: 1
count	integer($int32)
example: 1
}
maximum-bandwidth	network-topology-pcep_arguments_lspa_tlvs_auto-bandwidth-attributes_maximum-bandwidth{
bandwidth	string($byte)
}
down-adjustment-threshold	network-topology-pcep_arguments_lspa_tlvs_auto-bandwidth-attributes_down-adjustment-threshold{
bandwidth	string($byte)
}
adjustment-interval	network-topology-pcep_arguments_lspa_tlvs_auto-bandwidth-attributes_adjustment-interval{
adjustment	integer($int64)
default: 86400
example: 1
}
minimum-bandwidth	network-topology-pcep_arguments_lspa_tlvs_auto-bandwidth-attributes_minimum-bandwidth{
bandwidth	string($byte)
}
sample-interval	network-topology-pcep_arguments_lspa_tlvs_auto-bandwidth-attributes_sample-interval{
interval	integer($int64)
default: 300
example: 1
}
overflow-threshold	network-topology-pcep_arguments_lspa_tlvs_auto-bandwidth-attributes_overflow-threshold{
bandwidth	string($byte)
count	integer($int32)
example: 1
}
underflow-threshold	network-topology-pcep_arguments_lspa_tlvs_auto-bandwidth-attributes_underflow-threshold{
bandwidth	string($byte)
count	integer($int32)
example: 1
}
adjustment-threshold	network-topology-pcep_arguments_lspa_tlvs_auto-bandwidth-attributes_adjustment-threshold{
bandwidth	string($byte)
}
adjustment-threshold-percentage	network-topology-pcep_arguments_lspa_tlvs_auto-bandwidth-attributes_adjustment-threshold-percentage{
bandwidth	string($byte)
default: 0
percentage	integer($int32)
default: 5
example: 1
}
down-adjustment-interval	network-topology-pcep_arguments_lspa_tlvs_auto-bandwidth-attributes_down-adjustment-interval{
down-adjustment	integer($int64)
default: 86400
example: 1
}
overflow-threshold-percentage	network-topology-pcep_arguments_lspa_tlvs_auto-bandwidth-attributes_overflow-threshold-percentage{
bandwidth	string($byte)
percentage	integer($int32)
example: 1
count	integer($int32)
example: 1
}
down-adjustment-threshold-percentage	network-topology-pcep_arguments_lspa_tlvs_auto-bandwidth-attributes_down-adjustment-threshold-percentage{
bandwidth	string($byte)
default: 0
percentage	integer($int32)
default: 5
example: 1
}
}
vendor-information-tlv	[
VENDOR-INFORMATION-TLV

network-topology-pcep_arguments_lspa_tlvs_vendor-information-tlv{
description:	
VENDOR-INFORMATION-TLV

enterprise-number	integer($int64)
example: 0
}]
}
include-any	integer($int64)
example: 0
label-recording-desired	boolean
default: false
example: true
se-style-desired	boolean
default: false
example: true
local-protection-desired	boolean
default: false
example: true
session-name	string
example: Some session-name
include-all	integer($int64)
example: 0
exclude-any	integer($int64)
example: 0
ignore	boolean
default: false
example: true
setup-priority	string
default: 0
hold-priority	string
default: 0
}
rro	network-topology-pcep_arguments_rro{
processing-rule	boolean
default: false
example: true
subobject	[network-topology-pcep_arguments_rro_subobject{
protection-available	boolean
default: false
example: true
protection-in-use	boolean
default: false
example: true
ip-prefix*	network-topology-pcep_arguments_rro_subobject_ip-prefix{
ip-prefix*	string
example: 0.0.0.0/0
minLength: 0
maxLength: 2147483647
}
}]
ignore	boolean
default: false
example: true
}
association-group	network-topology-pcep_arguments_association-group{
processing-rule	boolean
default: false
example: true
association-id	integer($int32)
example: 0
association-tlvs	network-topology-pcep_arguments_association-group_association-tlvs{
description:	
Base Association TLVs definition

global-association-source	integer($int64)
example: 0
extended-association-id	[integer($int64)
example: 0]
protecting	boolean
example: true
secondary	boolean
example: true
protection-type	string
example: unprotected
Enum:
[ unprotected, full-rerouting, rerouting-without-extra-traffic, protection-with-extra-traffic, unidirectional-protection, bidirectional-protection ]
}
association-type	string
example: path-protection
Enum:
[ path-protection, disjoint, policy, single-side-lsp, double-side-lsp, sr-policy, virtual-network-lsp ]
removal-flag	boolean
example: true
ignore	boolean
default: false
example: true
association-source	string
example:
minLength: 0
maxLength: 2147483647
}
xro*	network-topology-pcep_arguments_xro{
processing-rule	boolean
default: false
example: true
subobject	[network-topology-pcep_arguments_xro_subobject{
attribute*	string
example: interface
Enum:
[ interface, node, srlg ]
as-number*	network-topology-pcep_arguments_xro_subobject_as-number{
as-number*	integer($int64)
example: 0
}
mandatory	boolean
default: false
example: true
}]
flags*	string
minItems: 0
default: fail fail
uniqueItems: true
Enum:
[ fail ]
ignore	boolean
default: false
example: true
}
of*	network-topology-pcep_arguments_of{
processing-rule	boolean
default: false
example: true
code*	integer($int32)
example: 0
tlvs	network-topology-pcep_arguments_of_tlvs{
vendor-information-tlv	[
VENDOR-INFORMATION-TLV

network-topology-pcep_arguments_of_tlvs_vendor-information-tlv{
description:	
VENDOR-INFORMATION-TLV

enterprise-number	integer($int64)
example: 0
}]
}
ignore	boolean
default: false
example: true
}
metrics	[network-topology-pcep_arguments_metrics{
metric*	network-topology-pcep_arguments_metrics_metric{
processing-rule	boolean
default: false
example: true
computed	boolean
default: false
example: true
metric-type*	integer($int32)
example: 0
bound	boolean
default: false
example: true
ignore	boolean
default: false
example: true
value	string($byte)
}
}]
reoptimization-bandwidth	network-topology-pcep_arguments_reoptimization-bandwidth{
processing-rule	boolean
default: false
example: true
bandwidth	string($byte)
ignore	boolean
default: false
example: true
}
class-type*	network-topology-pcep_arguments_class-type{
processing-rule	boolean
default: false
example: true
ignore	boolean
default: false
example: true
class-type*	integer($int32)
example: 1
}
}
name*	string
example: Some name
node*	string
example: Some node
}
```


### Respuesta XML Esperada

```xml
<!-- Pendiente: cuerpo XML esperado de la respuesta update-lsp. -->
<?xml version="1.0" encoding="UTF-8"?>
<output xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
    <failure>unsent</failure>
    <error xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
        <error-object xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
            <processing-rule>true</processing-rule>
            <tlvs xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                <vendor-information-tlv xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                    <enterprise-number>0</enterprise-number>
                </vendor-information-tlv>
                <req-missing xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                    <request-id>1</request-id>
                </req-missing>
            </tlvs>
            <ignore>true</ignore>
            <type>0</type>
            <value>0</value>
        </error-object>
    </error>
</output>
```
#### Schema

```text
network-topology-pcep_update-lsp_output{
failure	string
example: unsent
Enum:
[ unsent, no-ack, failed ]
error	[network-topology-pcep_error{
error-object*	network-topology-pcep_error_error-object{
processing-rule	boolean
default: false
example: true
tlvs	network-topology-pcep_error_error-object_tlvs{
vendor-information-tlv	[
VENDOR-INFORMATION-TLV

network-topology-pcep_error_error-object_tlvs_vendor-information-tlv{
description:	
VENDOR-INFORMATION-TLV

enterprise-number	integer($int64)
example: 0
}]
req-missing	network-topology-pcep_error_error-object_tlvs_req-missing{
request-id	integer($int64)
example: 1
}
}
ignore	boolean
default: false
example: true
type*	integer($int32)
example: 0
value*	integer($int32)
example: 0
}
}]
}
```

## Topologia PCEP

| Campo | Valor |
| --- | --- |
| Metodo | `GET` |
| Endpoint | `/restconf/data/network-topology:network-topology/topology=pcep-topology?content=all` |
| Cliente | `OdlRestconfDataClient` |
| Deserializador | `PcepTopologyXmlDeserializer` |

### Solicitud XML Esperada

```xml
<!-- Sin cuerpo: solicitud GET RESTCONF. -->
<?xml version="1.0" encoding="UTF-8"?>
<topology xmlns="urn:TBD:params:xml:ns:yang:network-topology">
    <node xmlns="urn:TBD:params:xml:ns:yang:network-topology">
        <node-id>Some node-id</node-id>
        <supported-tunnel-entry xmlns="urn:opendaylight:params:xml:ns:yang:overlay">
            <tunnel-type>tunnel-type-base</tunnel-type>
            <ip-port-locator-entry xmlns="urn:opendaylight:params:xml:ns:yang:overlay">
                <port>0</port>
                <ip>0.0.0.0</ip>
            </ip-port-locator-entry>
        </supported-tunnel-entry>
        <termination-point xmlns="urn:TBD:params:xml:ns:yang:network-topology">
            <interface-uuid xmlns="urn:opendaylight:params:xml:ns:yang:ovsdb">00000000-0000-0000-0000-000000000000</interface-uuid>
            <inventory-node-connector-ref xmlns="urn:opendaylight:model:topology:inventory">string</inventory-node-connector-ref>
            <interface-type xmlns="urn:opendaylight:params:xml:ns:yang:ovsdb">interface-type-base</interface-type>
            <name xmlns="urn:opendaylight:params:xml:ns:yang:ovsdb">Some name</name>
            <tp-id>Some tp-id</tp-id>
            <port-uuid xmlns="urn:opendaylight:params:xml:ns:yang:ovsdb">00000000-0000-0000-0000-000000000000</port-uuid>
            <mac xmlns="urn:opendaylight:params:xml:ns:yang:ovsdb">00:00:00:00:00:00</mac>
        </termination-point>
        <ignore-missing-schema-sources xmlns="urn:opendaylight:netconf-node-optional">
            <allowed>true</allowed>
            <reconnect-time>0</reconnect-time>
        </ignore-missing-schema-sources>
        <protocol-entry xmlns="urn:opendaylight:params:xml:ns:yang:ovsdb">
            <protocol>ovsdb-bridge-protocol-base</protocol>
        </protocol-entry>
        <bridge-uuid xmlns="urn:opendaylight:params:xml:ns:yang:ovsdb">00000000-0000-0000-0000-000000000000</bridge-uuid>
        <bridge-openflow-node-ref xmlns="urn:opendaylight:params:xml:ns:yang:ovsdb">string</bridge-openflow-node-ref>
        <inventory-node-ref xmlns="urn:opendaylight:model:topology:inventory">string</inventory-node-ref>
        <supporting-node xmlns="urn:TBD:params:xml:ns:yang:network-topology">
            <node-ref>Some node-ref</node-ref>
            <path-computation-client xmlns="urn:opendaylight:params:xml:ns:yang:topology:tunnel:pcep">
                <controlling>true</controlling>
            </path-computation-client>
            <topology-ref>Some topology-ref</topology-ref>
        </supporting-node>
        <bridge-name xmlns="urn:opendaylight:params:xml:ns:yang:ovsdb">Some bridge-name</bridge-name>
    </node>
    <topology-types xmlns="urn:TBD:params:xml:ns:yang:network-topology">
        <bgp-ipv6-reachability-topology xmlns="urn:opendaylight:params:xml:ns:yang:odl-bgp-topology-types">
        </bgp-ipv6-reachability-topology>
        <bgp-ipv4-reachability-topology xmlns="urn:opendaylight:params:xml:ns:yang:odl-bgp-topology-types">
        </bgp-ipv4-reachability-topology>
        <topology-netconf xmlns="urn:opendaylight:netconf-node-topology">
            <ssh-transport-topology-parameters xmlns="urn:opendaylight:netconf-node-topology">
                <key-exchange xmlns="urn:opendaylight:netconf-node-topology">
                    <key-exchange-alg>diffie-hellman-group-exchange-sha1</key-exchange-alg>
                </key-exchange>
                <encryption xmlns="urn:opendaylight:netconf-node-topology">
                    <encryption-alg>3des-cbc</encryption-alg>
                </encryption>
                <host-key xmlns="urn:opendaylight:netconf-node-topology">
                    <host-key-alg>ssh-dss</host-key-alg>
                </host-key>
                <mac xmlns="urn:opendaylight:netconf-node-topology">
                    <mac-alg>hmac-sha1</mac-alg>
                </mac>
            </ssh-transport-topology-parameters>
        </topology-netconf>
        <bgp-linkstate-topology xmlns="urn:opendaylight:params:xml:ns:yang:odl-bgp-topology-types">
        </bgp-linkstate-topology>
        <l3-unicast-igp-topology xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <isis xmlns="urn:TBD:params:xml:ns:yang:network:isis-topology">
            </isis>
            <ospf xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
            </ospf>
        </l3-unicast-igp-topology>
        <topology-sr xmlns="urn:opendaylight:params:xml:ns:yang:topology:sr">
        </topology-sr>
        <topology-pcep xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
            <timer xmlns="urn:opendaylight:params:xml:ns:yang:odl:pcep:stats:provider:config">1</timer>
            <capabilities xmlns="urn:opendaylight:params:xml:ns:yang:odl:pcep:topology-provider">
                <association-group xmlns="urn:opendaylight:params:xml:ns:yang:odl:pcep:topology-provider">
                    <disjoint-path>true</disjoint-path>
                    <double-side-lsp>true</double-side-lsp>
                    <single-side-lsp>true</single-side-lsp>
                    <path-protection>true</path-protection>
                    <sr-policy>true</sr-policy>
                    <enabled>true</enabled>
                    <policy>true</policy>
                </association-group>
                <path-setup-type xmlns="urn:opendaylight:params:xml:ns:yang:odl:pcep:topology-provider">
                    <rsvp-te>true</rsvp-te>
                    <srv6>true</srv6>
                    <sr-mpls>true</sr-mpls>
                    <enabled>true</enabled>
                </path-setup-type>
                <auto-bandwidth xmlns="urn:opendaylight:params:xml:ns:yang:odl:pcep:topology-provider">
                    <enabled>true</enabled>
                </auto-bandwidth>
                <p2mp xmlns="urn:opendaylight:params:xml:ns:yang:odl:pcep:topology-provider">
                    <enabled>true</enabled>
                </p2mp>
                <stateful xmlns="urn:opendaylight:params:xml:ns:yang:odl:pcep:topology-provider">
                    <include-db-version>true</include-db-version>
                    <initiated>true</initiated>
                    <triggered-resync>true</triggered-resync>
                    <triggered-initial-sync>true</triggered-initial-sync>
                    <delta-lsp-sync-capability>true</delta-lsp-sync-capability>
                    <active>true</active>
                    <enabled>true</enabled>
                </stateful>
            </capabilities>
            <session-config xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                <max-unknown-messages>1</max-unknown-messages>
                <listen-address></listen-address>
                <dead-timer-value>0</dead-timer-value>
                <tls xmlns="urn:opendaylight:params:xml:ns:yang:topology:pcep">
                    <keystore-type>JKS</keystore-type>
                    <truststore-password>Some truststore-password</truststore-password>
                    <keystore>Some keystore</keystore>
                    <keystore-path-type>PATH</keystore-path-type>
                    <truststore>Some truststore</truststore>
                    <truststore-path-type>PATH</truststore-path-type>
                    <keystore-password>Some keystore-password</keystore-password>
                    <certificate-password>Some certificate-password</certificate-password>
                    <truststore-type>JKS</truststore-type>
                </tls>
                <keep-alive-timer-value>0</keep-alive-timer-value>
                <rpc-timeout>-32768</rpc-timeout>
                <listen-port>0</listen-port>
            </session-config>
            <ted-name>Some ted-name</ted-name>
        </topology-pcep>
        <topology-tunnel-pcep xmlns="urn:opendaylight:params:xml:ns:yang:topology:tunnel:pcep">
        </topology-tunnel-pcep>
        <topology-tunnel xmlns="urn:opendaylight:params:xml:ns:yang:topology:tunnel">
        </topology-tunnel>
    </topology-types>
    <topology-type xmlns="urn:opendaylight:params:xml:ns:yang:overlay">topology-type-base</topology-type>
    <topology-id>Some topology-id</topology-id>
    <link xmlns="urn:TBD:params:xml:ns:yang:network-topology">
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <flag>flag-identity</flag>
            <metric>0</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                    <max-link-bandwidth>-92233720368547760</max-link-bandwidth>
                    <color>0</color>
                    <unreserved-bandwidth xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                        <bandwidth>-92233720368547760</bandwidth>
                        <priority>0</priority>
                    </unreserved-bandwidth>
                    <te-default-metric>0</te-default-metric>
                    <srlg xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                        <srlg-values xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                            <srlg-value>0</srlg-value>
                        </srlg-values>
                        <interface-switching-capabilities xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                            <packet-switch-capable xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                                <interface-mtu>0</interface-mtu>
                                <minimum-lsp-bandwidth>-92233720368547760</minimum-lsp-bandwidth>
                            </packet-switch-capable>
                            <max-lsp-bandwidth xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                                <bandwidth>-92233720368547760</bandwidth>
                                <priority>0</priority>
                            </max-lsp-bandwidth>
                            <switching-capability>PSC-1</switching-capability>
                            <time-division-multiplex-capable xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                                <indication>0</indication>
                                <minimum-lsp-bandwidth>-92233720368547760</minimum-lsp-bandwidth>
                            </time-division-multiplex-capable>
                            <encoding>0</encoding>
                        </interface-switching-capabilities>
                        <link-protection-type>0</link-protection-type>
                    </srlg>
                    <max-resv-link-bandwidth>-92233720368547760</max-resv-link-bandwidth>
                </ted>
                <multi-topology-id>0</multi-topology-id>
            </ospf-link-attributes>
            <name>Some name</name>
            <isis-link-attributes xmlns="urn:TBD:params:xml:ns:yang:network:isis-topology">
                <ted xmlns="urn:TBD:params:xml:ns:yang:network:isis-topology">
                    <max-link-bandwidth>-92233720368547760</max-link-bandwidth>
                    <color>0</color>
                    <unreserved-bandwidth xmlns="urn:TBD:params:xml:ns:yang:network:isis-topology">
                        <bandwidth>-92233720368547760</bandwidth>
                        <priority>0</priority>
                    </unreserved-bandwidth>
                    <te-default-metric>0</te-default-metric>
                    <srlg xmlns="urn:TBD:params:xml:ns:yang:network:isis-topology">
                        <srlg-values xmlns="urn:TBD:params:xml:ns:yang:network:isis-topology">
                            <srlg-value>0</srlg-value>
                        </srlg-values>
                        <interface-switching-capabilities xmlns="urn:TBD:params:xml:ns:yang:network:isis-topology">
                            <packet-switch-capable xmlns="urn:TBD:params:xml:ns:yang:network:isis-topology">
                                <interface-mtu>0</interface-mtu>
                                <minimum-lsp-bandwidth>-92233720368547760</minimum-lsp-bandwidth>
                            </packet-switch-capable>
                            <max-lsp-bandwidth xmlns="urn:TBD:params:xml:ns:yang:network:isis-topology">
                                <bandwidth>-92233720368547760</bandwidth>
                                <priority>0</priority>
                            </max-lsp-bandwidth>
                            <switching-capability>PSC-1</switching-capability>
                            <time-division-multiplex-capable xmlns="urn:TBD:params:xml:ns:yang:network:isis-topology">
                                <indication>0</indication>
                                <minimum-lsp-bandwidth>-92233720368547760</minimum-lsp-bandwidth>
                            </time-division-multiplex-capable>
                            <encoding>0</encoding>
                        </interface-switching-capabilities>
                        <link-protection-type>0</link-protection-type>
                    </srlg>
                    <max-resv-link-bandwidth>-92233720368547760</max-resv-link-bandwidth>
                </ted>
                <multi-topology-id>0</multi-topology-id>
            </isis-link-attributes>
        </igp-link-attributes>
        <administrative-status xmlns="urn:opendaylight:params:xml:ns:yang:topology:tunnel:pcep">active</administrative-status>
        <link-id>Some link-id</link-id>
        <tunnel-type xmlns="urn:opendaylight:params:xml:ns:yang:overlay">tunnel-type-base</tunnel-type>
        <supporting-link xmlns="urn:TBD:params:xml:ns:yang:network-topology">
            <link-ref>Some link-ref</link-ref>
        </supporting-link>
        <segment xmlns="urn:opendaylight:params:xml:ns:yang:topology:sr">0</segment>
        <destination xmlns="urn:TBD:params:xml:ns:yang:network-topology">
            <dest-node>Some dest-node</dest-node>
            <dest-tp>Some dest-tp</dest-tp>
            <port xmlns="urn:opendaylight:params:xml:ns:yang:overlay">0</port>
            <ip xmlns="urn:opendaylight:params:xml:ns:yang:overlay">0.0.0.0</ip>
        </destination>
        <source xmlns="urn:TBD:params:xml:ns:yang:network-topology">
            <source-node>Some source-node</source-node>
            <port xmlns="urn:opendaylight:params:xml:ns:yang:overlay">0</port>
            <ip xmlns="urn:opendaylight:params:xml:ns:yang:overlay">0.0.0.0</ip>
            <source-tp>Some source-tp</source-tp>
        </source>
    </link>
    <pcep-topology-reference xmlns="urn:opendaylight:params:xml:ns:yang:topology:tunnel:pcep:config">Some pcep-topology-reference</pcep-topology-reference>
    <igp-topology-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
        <isis-topology-attributes xmlns="urn:TBD:params:xml:ns:yang:network:isis-topology">
            <net>00.0000.0000.0000.0000.0000.0000</net>
        </isis-topology-attributes>
        <flag>flag-identity</flag>
        <name>Some name</name>
        <ospf-topology-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
            <area-id>0</area-id>
        </ospf-topology-attributes>
    </igp-topology-attributes>
    <underlay-topology xmlns="urn:TBD:params:xml:ns:yang:network-topology">
        <topology-ref>Some topology-ref</topology-ref>
    </underlay-topology>
</topology>
```

#### Schema 

```text 
network-topology_network-topology_topology{
description:	
This is the model of an abstract topology. A topology contains nodes and links. Each topology MUST be identified by unique topology-id for reason that a network could contain many topologies.

node	[
The list of network nodes defined for the topology.

network-topology_network-topology_topology_node{
description:	
The list of network nodes defined for the topology.

node-id	string
example: Some node-id
The identifier of a node in the topology. A node is specific to a topology to which it belongs.

overlay:supported-tunnel-entry	[network-topology_network-topology_topology_node_supported-tunnel-entry{
tunnel-type	string
example: tunnel-type-base
Enum:
[ tunnel-type-base, tunnel-type-vxlan-gpe, tunnel-type-gre, tunnel-type-vxlan ]
ip-port-locator-entry	[network-topology_network-topology_topology_node_supported-tunnel-entry_ip-port-locator-entry{
port	integer($int32)
example: 0
Data-plane port number

ip	string
example: 0.0.0.0
minLength: 0
maxLength: 2147483647
Data-plane IP address

}]
}]
termination-point	[
A termination point can terminate a link. Depending on the type of topology, a termination point could, for example, refer to a port or an interface.

network-topology_network-topology_topology_node_termination-point{
description:	
A termination point can terminate a link. Depending on the type of topology, a termination point could, for example, refer to a port or an interface.

ovsdb:interface-uuid	string
example: 00000000-0000-0000-0000-000000000000
minLength: 0
maxLength: 2147483647
xml: OrderedMap { "name": "interface-uuid", "namespace": "urn:opendaylight:params:xml:ns:yang:ovsdb" }
The unique identifier of the OVSDB interface


xml:
   name: interface-uuid
   namespace: urn:opendaylight:params:xml:ns:yang:ovsdb
opendaylight-topology-inventory:inventory-node-connector-ref	string
xml: OrderedMap { "name": "inventory-node-connector-ref", "namespace": "urn:opendaylight:model:topology:inventory" }
xml:
   name: inventory-node-connector-ref
   namespace: urn:opendaylight:model:topology:inventory
ovsdb:interface-type	string
example: interface-type-base
xml: OrderedMap { "name": "interface-type", "namespace": "urn:opendaylight:params:xml:ns:yang:ovsdb" }
The type of the OVSDB interface


xml:
   name: interface-type
   namespace: urn:opendaylight:params:xml:ns:yang:ovsdb
Enum:
[ interface-type-base, interface-type-vxlan, interface-type-dpdk, interface-type-dpdkvhost, interface-type-dpdkvhostuserclient, interface-type-system, interface-type-geneve, interface-type-gre, interface-type-lisp, interface-type-gre64, interface-type-internal, interface-type-tap, interface-type-ipsec-gre64, interface-type-stt, interface-type-patch, interface-type-dpdkr, interface-type-ipsec-gre, interface-type-dpdkvhostuser, interface-type-vxlan-gpe ]
ovsdb:name	string
example: Some name
xml: OrderedMap { "name": "name", "namespace": "urn:opendaylight:params:xml:ns:yang:ovsdb" }
The name of the OVSDB port/interface


xml:
   name: name
   namespace: urn:opendaylight:params:xml:ns:yang:ovsdb
tp-id	string
example: Some tp-id
ovsdb:port-uuid	string
example: 00000000-0000-0000-0000-000000000000
minLength: 0
maxLength: 2147483647
xml: OrderedMap { "name": "port-uuid", "namespace": "urn:opendaylight:params:xml:ns:yang:ovsdb" }
The unique identifier of the OVSDB port


xml:
   name: port-uuid
   namespace: urn:opendaylight:params:xml:ns:yang:ovsdb
ovsdb:mac	string
example: 00:00:00:00:00:00
minLength: 0
maxLength: 2147483647
xml: OrderedMap { "name": "mac", "namespace": "urn:opendaylight:params:xml:ns:yang:ovsdb" }
Ethernet address to use for this interface. If unset, the default is used


xml:
   name: mac
   namespace: urn:opendaylight:params:xml:ns:yang:ovsdb
}]
netconf-node-optional:ignore-missing-schema-sources	network-topology_network-topology_topology_node_ignore-missing-schema-sources{
description:	
Allows mount point to reconnect on the 'missing schema sources' error. WARNING - enabling the reconnection on the 'missing schema sources' error can lead to unexpected errors at runtime.

allowed	boolean
default: false
example: true
Allows reconnection of the mount point. Default false.

reconnect-time	integer($int64)
default: 5000
example: 0
Time for reconnection - in units milliseconds. Default 5000 ms.

}
ovsdb:protocol-entry	[network-topology_network-topology_topology_node_protocol-entry{
protocol	string
example: ovsdb-bridge-protocol-base
Protocol bridge should seek to speak to its controller

Enum:
[ ovsdb-bridge-protocol-base, ovsdb-bridge-protocol-openflow-14, ovsdb-bridge-protocol-openflow-15, ovsdb-bridge-protocol-openflow-10, ovsdb-bridge-protocol-openflow-13, ovsdb-bridge-protocol-openflow-11, ovsdb-bridge-protocol-openflow-12 ]
}]
ovsdb:bridge-uuid	string
example: 00000000-0000-0000-0000-000000000000
minLength: 0
maxLength: 2147483647
xml: OrderedMap { "name": "bridge-uuid", "namespace": "urn:opendaylight:params:xml:ns:yang:ovsdb" }
The unique identifier of the bridge


xml:
   name: bridge-uuid
   namespace: urn:opendaylight:params:xml:ns:yang:ovsdb
ovsdb:bridge-openflow-node-ref	string
xml: OrderedMap { "name": "bridge-openflow-node-ref", "namespace": "urn:opendaylight:params:xml:ns:yang:ovsdb" }
A reference to the openflow node


xml:
   name: bridge-openflow-node-ref
   namespace: urn:opendaylight:params:xml:ns:yang:ovsdb
opendaylight-topology-inventory:inventory-node-ref	string
xml: OrderedMap { "name": "inventory-node-ref", "namespace": "urn:opendaylight:model:topology:inventory" }
xml:
   name: inventory-node-ref
   namespace: urn:opendaylight:model:topology:inventory
supporting-node	[
This list defines vertical layering information for nodes. It allows to capture for any given node, which node (or nodes) in the corresponding underlay topology it maps onto. A node can map to zero, one, or more nodes below it; accordingly there can be zero, one, or more elements in the list. If there are specific layering requirements, for example specific to a particular type of topology that only allows for certain layering relationships, the choice below can be augmented with additional cases. A list has been chosen rather than a leaf-list in order to provide room for augmentations, e.g. for statistics or priorization information associated with supporting nodes.

network-topology_network-topology_topology_node_supporting-node{
description:	
This list defines vertical layering information for nodes. It allows to capture for any given node, which node (or nodes) in the corresponding underlay topology it maps onto. A node can map to zero, one, or more nodes below it; accordingly there can be zero, one, or more elements in the list. If there are specific layering requirements, for example specific to a particular type of topology that only allows for certain layering relationships, the choice below can be augmented with additional cases. A list has been chosen rather than a leaf-list in order to provide room for augmentations, e.g. for statistics or priorization information associated with supporting nodes.

node-ref	string
example: Some node-ref
topology-tunnel-pcep:path-computation-client	network-topology_network-topology_topology_node_supporting-node_path-computation-client{
controlling	boolean
default: false
example: true
}
topology-ref	string
example: Some topology-ref
}]
ovsdb:bridge-name	string
example: Some bridge-name
xml: OrderedMap { "name": "bridge-name", "namespace": "urn:opendaylight:params:xml:ns:yang:ovsdb" }
The name of the bridge


xml:
   name: bridge-name
   namespace: urn:opendaylight:params:xml:ns:yang:ovsdb
}]
topology-types	network-topology_network-topology_topology_topology-types{
description:	
This container is used to identify the type, or types (as a topology can support several types simultaneously), of the topology. Topology types are the subject of several integrity constraints that an implementing server can validate in order to maintain integrity of the datastore. Topology types are indicated through separate data nodes; the set of topology types is expected to increase over time. To add support for a new topology, an augmenting module needs to augment this container with a new empty optional container to indicate the new topology type. The use of a container allows to indicate a subcategorization of topology types. The container SHALL NOT be augmented with any data nodes that serve a purpose other than identifying a particular topology type.

odl-bgp-topology-types:bgp-ipv6-reachability-topology	network-topology_network-topology_topology_topology-types_bgp-ipv6-reachability-topology{
}
odl-bgp-topology-types:bgp-ipv4-reachability-topology	network-topology_network-topology_topology_topology-types_bgp-ipv4-reachability-topology{
}
netconf-node-topology:topology-netconf	network-topology_network-topology_topology_topology-types_topology-netconf{
ssh-transport-topology-parameters	network-topology_network-topology_topology_topology-types_topology-netconf_ssh-transport-topology-parameters{
description:	
Default topology wide configurable parameters of the SSH transport layer.

key-exchange	network-topology_network-topology_topology_topology-types_topology-netconf_ssh-transport-topology-parameters_key-exchange{
description:	
Parameters regarding key exchange.

key-exchange-alg	[
Acceptable key exchange algorithms in order of decreasing preference.

If this leaf-list is not configured (has zero elements), the acceptable key exchange algorithms are implementation-defined.

string
example: diffie-hellman-group-exchange-sha1
Enum:
Array [ 189 ]
]
}
encryption	network-topology_network-topology_topology_topology-types_topology-netconf_ssh-transport-topology-parameters_encryption{
description:	
Parameters regarding encryption.

encryption-alg	[...]
}
host-key	network-topology_network-topology_topology_topology-types_topology-netconf_ssh-transport-topology-parameters_host-key{
description:	
Parameters regarding host key.

host-key-alg	[...]
}
mac	network-topology_network-topology_topology_topology-types_topology-netconf_ssh-transport-topology-parameters_mac{
description:	
Parameters regarding message authentication code (MAC).

mac-alg	[...]
}
}
}
odl-bgp-topology-types:bgp-linkstate-topology	network-topology_network-topology_topology_topology-types_bgp-linkstate-topology{...}
l3-unicast-igp-topology:l3-unicast-igp-topology	network-topology_network-topology_topology_topology-types_l3-unicast-igp-topology{...}
network-topology-sr:topology-sr	network-topology_network-topology_topology_topology-types_topology-sr{...}
network-topology-pcep:topology-pcep	network-topology_network-topology_topology_topology-types_topology-pcep{
odl-pcep-stats-provider:timer	integer($int32)
default: 5
example: 1
xml: OrderedMap { "name": "timer", "namespace": "urn:opendaylight:params:xml:ns:yang:odl:pcep:stats:provider:config" }
xml:
   name: timer
   namespace: urn:opendaylight:params:xml:ns:yang:odl:pcep:stats:provider:config
odl-pcep-topology-provider:capabilities	network-topology_network-topology_topology_topology-types_topology-pcep_capabilities{
association-group	network-topology_network-topology_topology_topology-types_topology-pcep_capabilities_association-group{
disjoint-path	boolean
default: true
example: true
double-side-lsp	boolean
default: true
example: true
single-side-lsp	boolean
default: true
example: true
path-protection	boolean
default: true
example: true
sr-policy	boolean
default: true
example: true
enabled	boolean
default: true
example: true
policy	boolean
default: true
example: true
}
path-setup-type	network-topology_network-topology_topology_topology-types_topology-pcep_capabilities_path-setup-type{
rsvp-te	boolean
default: true
example: true
srv6	boolean
default: true
example: true
sr-mpls	boolean
default: true
example: true
enabled	boolean
default: true
example: true
}
auto-bandwidth	network-topology_network-topology_topology_topology-types_topology-pcep_capabilities_auto-bandwidth{
enabled	boolean
default: false
example: true
}
p2mp	network-topology_network-topology_topology_topology-types_topology-pcep_capabilities_p2mp{
enabled	boolean
default: true
example: true
}
stateful	network-topology_network-topology_topology_topology-types_topology-pcep_capabilities_stateful{
include-db-version	boolean
default: true
example: true
initiated	boolean
default: true
example: true
triggered-resync	boolean
default: true
example: true
triggered-initial-sync	boolean
default: true
example: true
delta-lsp-sync-capability	boolean
default: true
example: true
active	boolean
default: true
example: true
enabled	boolean
default: true
example: true
}
}
session-config	network-topology_network-topology_topology_topology-types_topology-pcep_session-config{
description:	
PCEP topology config

max-unknown-messages	integer($int32)
default: 5
example: 1
listen-address	string
default: 0.0.0.0
example:
minLength: 0
maxLength: 2147483647
dead-timer-value	integer($int32)
default: 120
example: 0
tls	network-topology_network-topology_topology_topology-types_topology-pcep_session-config_tls{
keystore-type*	string
example: JKS
keystore type (JKS or PKCS12)

Enum:
[ JKS, PKCS12 ]
truststore-password*	string
example: Some truststore-password
password protecting truststore

keystore*	string
example: Some keystore
keystore location

keystore-path-type*	string
example: PATH
keystore path type (CLASSPATH or PATH)

Enum:
[ PATH, CLASSPATH ]
truststore*	string
example: Some truststore
truststore location

truststore-path-type*	string
example: PATH
truststore path type (CLASSPATH or PATH)

Enum:
[ PATH, CLASSPATH ]
keystore-password*	string
example: Some keystore-password
password protecting keystore

certificate-password*	string
example: Some certificate-password
password protecting certificate

truststore-type*	string
example: JKS
truststore type (JKS or PKCS12)

Enum:
[ JKS, PKCS12 ]
}
keep-alive-timer-value	integer($int32)
default: 30
example: 0
rpc-timeout	integer($int32)
default: 30
example: -32768
listen-port	integer($int32)
default: 4189
example: 0
}
ted-name	string
example: Some ted-name
}
topology-tunnel-pcep:topology-tunnel-pcep	network-topology_network-topology_topology_topology-types_topology-tunnel-pcep{
}
topology-tunnel:topology-tunnel	network-topology_network-topology_topology_topology-types_topology-tunnel{
}
}
overlay:topology-type	string
example: topology-type-base
xml: OrderedMap { "name": "topology-type", "namespace": "urn:opendaylight:params:xml:ns:yang:overlay" }
xml:
   name: topology-type
   namespace: urn:opendaylight:params:xml:ns:yang:overlay
Enum:
[ topology-type-base, topology-type-overlay ]
topology-id	string
example: Some topology-id
It is presumed that a datastore will contain many topologies. To distinguish between topologies it is vital to have UNIQUE topology identifiers.

link	[
A Network Link connects a by Local (Source) node and a Remote (Destination) Network Nodes via a set of the nodes' termination points. As it is possible to have several links between the same source and destination nodes, and as a link could potentially be re-homed between termination points, to ensure that we would always know to distinguish between links, every link is identified by a dedicated link identifier. Note that a link models a point-to-point link, not a multipoint link. Layering dependencies on links in underlay topologies are not represented as the layering information of nodes and of termination points is sufficient.

network-topology_network-topology_topology_link{
description:	
A Network Link connects a by Local (Source) node and a Remote (Destination) Network Nodes via a set of the nodes' termination points. As it is possible to have several links between the same source and destination nodes, and as a link could potentially be re-homed between termination points, to ensure that we would always know to distinguish between links, every link is identified by a dedicated link identifier. Note that a link models a point-to-point link, not a multipoint link. Layering dependencies on links in underlay topologies are not represented as the layering information of nodes and of termination points is sufficient.

l3-unicast-igp-topology:igp-link-attributes	network-topology_network-topology_topology_link_igp-link-attributes{
flag	[
Link flags

string
example: flag-identity
Enum:
Array [ 2 ]
]
metric	integer($int64)
example: 0
Link Metric

ospf-topology:ospf-link-attributes	network-topology_network-topology_topology_link_igp-link-attributes_ospf-link-attributes{...}
name	[...]
isis-topology:isis-link-attributes	network-topology_network-topology_topology_link_igp-link-attributes_isis-link-attributes{...}
}
topology-tunnel-pcep:administrative-status	string
example: active
xml: OrderedMap { "name": "administrative-status", "namespace": "urn:opendaylight:params:xml:ns:yang:topology:tunnel:pcep" }
xml:
   name: administrative-status
   namespace: urn:opendaylight:params:xml:ns:yang:topology:tunnel:pcep
Enum:
[ active, inactive ]
link-id	[...]
overlay:tunnel-type	[...]
supporting-link	[...]
network-topology-sr:segment	[...]
destination*	network-topology_network-topology_topology_link_destination{...}
source*	network-topology_network-topology_topology_link_source{...}
}]
topology-tunnel-pcep-config:pcep-topology-reference	string
example: Some pcep-topology-reference
xml: OrderedMap { "name": "pcep-topology-reference", "namespace": "urn:opendaylight:params:xml:ns:yang:topology:tunnel:pcep:config" }
An absolute reference to a topology instance.


xml:
   name: pcep-topology-reference
   namespace: urn:opendaylight:params:xml:ns:yang:topology:tunnel:pcep:config
l3-unicast-igp-topology:igp-topology-attributes	network-topology_network-topology_topology_igp-topology-attributes{...}
underlay-topology	[...]
}
```

### Respuesta XML Esperada

```xml
<!-- Pendiente: cuerpo XML esperado de la topologia PCEP. -->
```

## Topologia BGP-LS

| Campo | Valor |
| --- | --- |
| Metodo | `GET` |
| Endpoint | `/restconf/data/network-topology:network-topology/topology=sma-bgp-linkstate-topology?content=nonconfig` |
| Cliente | `OdlRestconfDataClient` |
| Deserializador | `BgpLsTopologyXmlDeserializer` |

### Respuesta XML Esperada

```xml
<!-- Sin cuerpo: solicitud GET RESTCONF. -->
<topology xmlns="urn:TBD:params:xml:ns:yang:network-topology">
    <topology-id>sma-bgp-linkstate-topology</topology-id>
    <rib-id xmlns="urn:opendaylight:params:xml:ns:yang:odl-bgp-topology-config">sma-bgp-ls</rib-id>
    <link>
        <link-id>bgpls://Ospf:34/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=218959117&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=185273099&amp;ipv4-iface=10.0.12.2&amp;ipv4-neigh=10.0.12.1</link-id>
        <source>
            <source-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</source-node>
            <source-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.12.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>12500000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.12.1</dest-tp>
            <dest-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:34/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=235802126&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=185273099&amp;ipv4-iface=10.0.14.2&amp;ipv4-neigh=10.0.14.1</link-id>
        <source>
            <source-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</source-node>
            <source-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.14.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>1250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.14.1</dest-tp>
            <dest-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:33/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=202116108&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=235802126&amp;ipv4-iface=10.0.24.1&amp;ipv4-neigh=10.0.24.2</link-id>
        <source>
            <source-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</source-node>
            <source-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.24.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>3125000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.24.2</dest-tp>
            <dest-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:34/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=218959117&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=235802126&amp;ipv4-iface=10.0.25.1&amp;ipv4-neigh=10.0.25.2</link-id>
        <source>
            <source-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</source-node>
            <source-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.25.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>9375000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.25.2</dest-tp>
            <dest-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:36/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=202116108&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=235802126&amp;ipv4-iface=10.0.24.1&amp;ipv4-neigh=10.0.24.2</link-id>
        <source>
            <source-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</source-node>
            <source-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.24.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>3125000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.24.2</dest-tp>
            <dest-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:36/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=202116108&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=185273099&amp;ipv4-iface=10.0.11.2&amp;ipv4-neigh=10.0.11.1</link-id>
        <source>
            <source-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</source-node>
            <source-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.11.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>6250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.11.1</dest-tp>
            <dest-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:35/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=218959117&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=235802126&amp;ipv4-iface=10.0.22.1&amp;ipv4-neigh=10.0.22.2</link-id>
        <source>
            <source-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</source-node>
            <source-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.22.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>12500000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.22.2</dest-tp>
            <dest-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:34/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=218959117&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=202116108&amp;ipv4-iface=10.0.23.2&amp;ipv4-neigh=10.0.23.1</link-id>
        <source>
            <source-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</source-node>
            <source-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.23.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>1250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.23.1</dest-tp>
            <dest-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:36/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=185273099&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=202116108&amp;ipv4-iface=10.0.11.1&amp;ipv4-neigh=10.0.11.2</link-id>
        <source>
            <source-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</source-node>
            <source-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.11.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>6250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.11.2</dest-tp>
            <dest-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:33/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=202116108&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=185273099&amp;ipv4-iface=10.0.11.2&amp;ipv4-neigh=10.0.11.1</link-id>
        <source>
            <source-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</source-node>
            <source-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.11.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>6250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.11.1</dest-tp>
            <dest-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:34/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=202116108&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=185273099&amp;ipv4-iface=10.0.13.2&amp;ipv4-neigh=10.0.13.1</link-id>
        <source>
            <source-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</source-node>
            <source-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.13.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>3125000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.13.1</dest-tp>
            <dest-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:36/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=235802126&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=202116108&amp;ipv4-iface=10.0.24.2&amp;ipv4-neigh=10.0.24.1</link-id>
        <source>
            <source-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</source-node>
            <source-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.24.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>3125000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.24.1</dest-tp>
            <dest-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:34/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=235802126&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=218959117&amp;ipv4-iface=10.0.22.2&amp;ipv4-neigh=10.0.22.1</link-id>
        <source>
            <source-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</source-node>
            <source-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.22.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>12500000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.22.1</dest-tp>
            <dest-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:33/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=235802126&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=202116108&amp;ipv4-iface=10.0.24.2&amp;ipv4-neigh=10.0.24.1</link-id>
        <source>
            <source-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</source-node>
            <source-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.24.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>3125000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.24.1</dest-tp>
            <dest-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:33/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=185273099&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=218959117&amp;ipv4-iface=10.0.12.1&amp;ipv4-neigh=10.0.12.2</link-id>
        <source>
            <source-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</source-node>
            <source-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.12.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>12500000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.12.2</dest-tp>
            <dest-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:33/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=185273099&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=202116108&amp;ipv4-iface=10.0.11.1&amp;ipv4-neigh=10.0.11.2</link-id>
        <source>
            <source-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</source-node>
            <source-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.11.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>6250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.11.2</dest-tp>
            <dest-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:34/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=235802126&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=202116108&amp;ipv4-iface=10.0.21.2&amp;ipv4-neigh=10.0.21.1</link-id>
        <source>
            <source-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</source-node>
            <source-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.21.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>6250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.21.1</dest-tp>
            <dest-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:35/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=202116108&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=218959117&amp;ipv4-iface=10.0.23.1&amp;ipv4-neigh=10.0.23.2</link-id>
        <source>
            <source-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</source-node>
            <source-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.23.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>1250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.23.2</dest-tp>
            <dest-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:36/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=185273099&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=218959117&amp;ipv4-iface=10.0.12.1&amp;ipv4-neigh=10.0.12.2</link-id>
        <source>
            <source-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</source-node>
            <source-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.12.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>12500000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.12.2</dest-tp>
            <dest-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:36/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=202116108&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=235802126&amp;ipv4-iface=10.0.21.1&amp;ipv4-neigh=10.0.21.2</link-id>
        <source>
            <source-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</source-node>
            <source-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.21.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>6250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.21.2</dest-tp>
            <dest-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:34/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=185273099&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=202116108&amp;ipv4-iface=10.0.13.1&amp;ipv4-neigh=10.0.13.2</link-id>
        <source>
            <source-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</source-node>
            <source-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.13.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>3125000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.13.2</dest-tp>
            <dest-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:35/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=235802126&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=202116108&amp;ipv4-iface=10.0.21.2&amp;ipv4-neigh=10.0.21.1</link-id>
        <source>
            <source-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</source-node>
            <source-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.21.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>6250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.21.1</dest-tp>
            <dest-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:34/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=218959117&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=235802126&amp;ipv4-iface=10.0.22.1&amp;ipv4-neigh=10.0.22.2</link-id>
        <source>
            <source-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</source-node>
            <source-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.22.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>12500000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.22.2</dest-tp>
            <dest-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:36/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=235802126&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=218959117&amp;ipv4-iface=10.0.25.2&amp;ipv4-neigh=10.0.25.1</link-id>
        <source>
            <source-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</source-node>
            <source-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.25.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>9375000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.25.1</dest-tp>
            <dest-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:33/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=235802126&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=185273099&amp;ipv4-iface=10.0.14.2&amp;ipv4-neigh=10.0.14.1</link-id>
        <source>
            <source-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</source-node>
            <source-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.14.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>1250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.14.1</dest-tp>
            <dest-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:33/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=235802126&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=218959117&amp;ipv4-iface=10.0.25.2&amp;ipv4-neigh=10.0.25.1</link-id>
        <source>
            <source-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</source-node>
            <source-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.25.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>9375000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.25.1</dest-tp>
            <dest-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:36/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=218959117&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=202116108&amp;ipv4-iface=10.0.23.2&amp;ipv4-neigh=10.0.23.1</link-id>
        <source>
            <source-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</source-node>
            <source-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.23.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>1250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.23.1</dest-tp>
            <dest-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:33/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=202116108&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=235802126&amp;ipv4-iface=10.0.21.1&amp;ipv4-neigh=10.0.21.2</link-id>
        <source>
            <source-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</source-node>
            <source-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.21.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>6250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.21.2</dest-tp>
            <dest-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:35/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=202116108&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=185273099&amp;ipv4-iface=10.0.13.2&amp;ipv4-neigh=10.0.13.1</link-id>
        <source>
            <source-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</source-node>
            <source-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.13.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>3125000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.13.1</dest-tp>
            <dest-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:33/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=218959117&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=202116108&amp;ipv4-iface=10.0.23.2&amp;ipv4-neigh=10.0.23.1</link-id>
        <source>
            <source-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</source-node>
            <source-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.23.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>1250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.23.1</dest-tp>
            <dest-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:36/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=202116108&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=218959117&amp;ipv4-iface=10.0.23.1&amp;ipv4-neigh=10.0.23.2</link-id>
        <source>
            <source-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</source-node>
            <source-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.23.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>1250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.23.2</dest-tp>
            <dest-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:33/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=202116108&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=218959117&amp;ipv4-iface=10.0.23.1&amp;ipv4-neigh=10.0.23.2</link-id>
        <source>
            <source-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</source-node>
            <source-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.23.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>1250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.23.2</dest-tp>
            <dest-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:36/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=235802126&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=185273099&amp;ipv4-iface=10.0.14.2&amp;ipv4-neigh=10.0.14.1</link-id>
        <source>
            <source-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</source-node>
            <source-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.14.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>1250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.14.1</dest-tp>
            <dest-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:35/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=185273099&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=235802126&amp;ipv4-iface=10.0.14.1&amp;ipv4-neigh=10.0.14.2</link-id>
        <source>
            <source-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</source-node>
            <source-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.14.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>1250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>0.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.14.2</dest-tp>
            <dest-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:35/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=235802126&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=218959117&amp;ipv4-iface=10.0.22.2&amp;ipv4-neigh=10.0.22.1</link-id>
        <source>
            <source-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</source-node>
            <source-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.22.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>12500000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.22.1</dest-tp>
            <dest-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:35/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=185273099&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=202116108&amp;ipv4-iface=10.0.11.1&amp;ipv4-neigh=10.0.11.2</link-id>
        <source>
            <source-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</source-node>
            <source-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.11.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>6250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.11.2</dest-tp>
            <dest-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:33/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=185273099&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=235802126&amp;ipv4-iface=10.0.14.1&amp;ipv4-neigh=10.0.14.2</link-id>
        <source>
            <source-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</source-node>
            <source-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.14.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>1250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>0.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.14.2</dest-tp>
            <dest-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:35/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=235802126&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=202116108&amp;ipv4-iface=10.0.24.2&amp;ipv4-neigh=10.0.24.1</link-id>
        <source>
            <source-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</source-node>
            <source-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.24.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>3125000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.24.1</dest-tp>
            <dest-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:35/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=235802126&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=218959117&amp;ipv4-iface=10.0.25.2&amp;ipv4-neigh=10.0.25.1</link-id>
        <source>
            <source-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</source-node>
            <source-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.25.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>9375000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.25.1</dest-tp>
            <dest-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:33/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=235802126&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=218959117&amp;ipv4-iface=10.0.22.2&amp;ipv4-neigh=10.0.22.1</link-id>
        <source>
            <source-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</source-node>
            <source-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.22.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>12500000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.22.1</dest-tp>
            <dest-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:34/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=202116108&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=218959117&amp;ipv4-iface=10.0.23.1&amp;ipv4-neigh=10.0.23.2</link-id>
        <source>
            <source-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</source-node>
            <source-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.23.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>1250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.23.2</dest-tp>
            <dest-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:36/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=185273099&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=202116108&amp;ipv4-iface=10.0.13.1&amp;ipv4-neigh=10.0.13.2</link-id>
        <source>
            <source-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</source-node>
            <source-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.13.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>3125000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.13.2</dest-tp>
            <dest-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:36/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=185273099&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=235802126&amp;ipv4-iface=10.0.14.1&amp;ipv4-neigh=10.0.14.2</link-id>
        <source>
            <source-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</source-node>
            <source-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.14.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>1250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>0.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.14.2</dest-tp>
            <dest-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:33/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=202116108&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=185273099&amp;ipv4-iface=10.0.13.2&amp;ipv4-neigh=10.0.13.1</link-id>
        <source>
            <source-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</source-node>
            <source-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.13.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>3125000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.13.1</dest-tp>
            <dest-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:35/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=185273099&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=218959117&amp;ipv4-iface=10.0.12.1&amp;ipv4-neigh=10.0.12.2</link-id>
        <source>
            <source-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</source-node>
            <source-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.12.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>12500000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.12.2</dest-tp>
            <dest-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:36/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=235802126&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=218959117&amp;ipv4-iface=10.0.22.2&amp;ipv4-neigh=10.0.22.1</link-id>
        <source>
            <source-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</source-node>
            <source-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.22.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>12500000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.22.1</dest-tp>
            <dest-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:33/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=218959117&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=235802126&amp;ipv4-iface=10.0.22.1&amp;ipv4-neigh=10.0.22.2</link-id>
        <source>
            <source-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</source-node>
            <source-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.22.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>12500000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.22.2</dest-tp>
            <dest-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:35/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=202116108&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=235802126&amp;ipv4-iface=10.0.21.1&amp;ipv4-neigh=10.0.21.2</link-id>
        <source>
            <source-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</source-node>
            <source-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.21.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>6250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.21.2</dest-tp>
            <dest-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:35/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=218959117&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=235802126&amp;ipv4-iface=10.0.25.1&amp;ipv4-neigh=10.0.25.2</link-id>
        <source>
            <source-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</source-node>
            <source-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.25.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>9375000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.25.2</dest-tp>
            <dest-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:35/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=218959117&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=185273099&amp;ipv4-iface=10.0.12.2&amp;ipv4-neigh=10.0.12.1</link-id>
        <source>
            <source-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</source-node>
            <source-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.12.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>12500000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.12.1</dest-tp>
            <dest-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:33/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=185273099&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=202116108&amp;ipv4-iface=10.0.13.1&amp;ipv4-neigh=10.0.13.2</link-id>
        <source>
            <source-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</source-node>
            <source-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.13.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>3125000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.13.2</dest-tp>
            <dest-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:36/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=218959117&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=235802126&amp;ipv4-iface=10.0.22.1&amp;ipv4-neigh=10.0.22.2</link-id>
        <source>
            <source-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</source-node>
            <source-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.22.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>12500000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.22.2</dest-tp>
            <dest-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:34/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=202116108&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=185273099&amp;ipv4-iface=10.0.11.2&amp;ipv4-neigh=10.0.11.1</link-id>
        <source>
            <source-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</source-node>
            <source-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.11.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>6250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.11.1</dest-tp>
            <dest-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:34/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=202116108&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=235802126&amp;ipv4-iface=10.0.24.1&amp;ipv4-neigh=10.0.24.2</link-id>
        <source>
            <source-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</source-node>
            <source-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.24.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>3125000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.24.2</dest-tp>
            <dest-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:35/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=218959117&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=202116108&amp;ipv4-iface=10.0.23.2&amp;ipv4-neigh=10.0.23.1</link-id>
        <source>
            <source-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</source-node>
            <source-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.23.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>1250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.23.1</dest-tp>
            <dest-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:34/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=185273099&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=202116108&amp;ipv4-iface=10.0.11.1&amp;ipv4-neigh=10.0.11.2</link-id>
        <source>
            <source-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</source-node>
            <source-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.11.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>6250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.11.2</dest-tp>
            <dest-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:34/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=235802126&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=202116108&amp;ipv4-iface=10.0.24.2&amp;ipv4-neigh=10.0.24.1</link-id>
        <source>
            <source-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</source-node>
            <source-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.24.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>3125000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.24.1</dest-tp>
            <dest-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:35/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=202116108&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=185273099&amp;ipv4-iface=10.0.11.2&amp;ipv4-neigh=10.0.11.1</link-id>
        <source>
            <source-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</source-node>
            <source-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.11.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>6250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.11.1</dest-tp>
            <dest-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:35/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=202116108&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=235802126&amp;ipv4-iface=10.0.24.1&amp;ipv4-neigh=10.0.24.2</link-id>
        <source>
            <source-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</source-node>
            <source-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.24.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>3125000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.24.2</dest-tp>
            <dest-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:34/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=185273099&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=235802126&amp;ipv4-iface=10.0.14.1&amp;ipv4-neigh=10.0.14.2</link-id>
        <source>
            <source-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</source-node>
            <source-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.14.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>1250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>0.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.14.2</dest-tp>
            <dest-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:35/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=235802126&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=185273099&amp;ipv4-iface=10.0.14.2&amp;ipv4-neigh=10.0.14.1</link-id>
        <source>
            <source-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</source-node>
            <source-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.14.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>1250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>1250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.14.1</dest-tp>
            <dest-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:35/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=185273099&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=202116108&amp;ipv4-iface=10.0.13.1&amp;ipv4-neigh=10.0.13.2</link-id>
        <source>
            <source-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</source-node>
            <source-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.13.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>3125000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.13.2</dest-tp>
            <dest-node>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:34/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=235802126&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=218959117&amp;ipv4-iface=10.0.25.2&amp;ipv4-neigh=10.0.25.1</link-id>
        <source>
            <source-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</source-node>
            <source-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.25.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>9375000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.25.1</dest-tp>
            <dest-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:33/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=235802126&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=202116108&amp;ipv4-iface=10.0.21.2&amp;ipv4-neigh=10.0.21.1</link-id>
        <source>
            <source-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</source-node>
            <source-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.21.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>6250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.21.1</dest-tp>
            <dest-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:36/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=218959117&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=235802126&amp;ipv4-iface=10.0.25.1&amp;ipv4-neigh=10.0.25.2</link-id>
        <source>
            <source-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</source-node>
            <source-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.25.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>9375000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.25.2</dest-tp>
            <dest-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:34/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=202116108&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=235802126&amp;ipv4-iface=10.0.21.1&amp;ipv4-neigh=10.0.21.2</link-id>
        <source>
            <source-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</source-node>
            <source-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.21.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>6250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.21.2</dest-tp>
            <dest-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:34/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=185273099&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=218959117&amp;ipv4-iface=10.0.12.1&amp;ipv4-neigh=10.0.12.2</link-id>
        <source>
            <source-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</source-node>
            <source-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.12.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>12500000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.12.2</dest-tp>
            <dest-node>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:36/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=202116108&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=185273099&amp;ipv4-iface=10.0.13.2&amp;ipv4-neigh=10.0.13.1</link-id>
        <source>
            <source-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</source-node>
            <source-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.13.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>3125000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>3125000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.13.1</dest-tp>
            <dest-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:36/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=218959117&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=185273099&amp;ipv4-iface=10.0.12.2&amp;ipv4-neigh=10.0.12.1</link-id>
        <source>
            <source-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</source-node>
            <source-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.12.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>12500000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.12.1</dest-tp>
            <dest-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:33/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=218959117&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=185273099&amp;ipv4-iface=10.0.12.2&amp;ipv4-neigh=10.0.12.1</link-id>
        <source>
            <source-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</source-node>
            <source-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.12.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>12500000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>12500000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.12.1</dest-tp>
            <dest-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:33/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=218959117&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=235802126&amp;ipv4-iface=10.0.25.1&amp;ipv4-neigh=10.0.25.2</link-id>
        <source>
            <source-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</source-node>
            <source-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.25.1</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>9375000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>9375000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.25.2</dest-tp>
            <dest-node>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</dest-node>
        </destination>
    </link>
    <link>
        <link-id>bgpls://Ospf:36/type=link&amp;local-as=65000&amp;local-domain=0&amp;local-area=0&amp;local-router=235802126&amp;remote-as=65000&amp;remote-domain=0&amp;remote-area=0&amp;remote-router=202116108&amp;ipv4-iface=10.0.21.2&amp;ipv4-neigh=10.0.21.1</link-id>
        <source>
            <source-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</source-node>
            <source-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.21.2</source-tp>
        </source>
        <igp-link-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <metric>1</metric>
            <ospf-link-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <max-resv-link-bandwidth>6250000.0</max-resv-link-bandwidth>
                    <max-link-bandwidth>125000000.0</max-link-bandwidth>
                    <unreserved-bandwidth>
                        <priority>2</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>1</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>4</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>3</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>6</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>5</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>7</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <unreserved-bandwidth>
                        <priority>0</priority>
                        <bandwidth>6250000.0</bandwidth>
                    </unreserved-bandwidth>
                    <color>0</color>
                    <te-default-metric>1</te-default-metric>
                </ted>
            </ospf-link-attributes>
        </igp-link-attributes>
        <destination>
            <dest-tp>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.21.1</dest-tp>
            <dest-node>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</dest-node>
        </destination>
    </link>
    <node>
        <node-id>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</node-id>
        <igp-node-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <router-id>11.11.11.11</router-id>
            <ospf-node-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <te-router-id-ipv4>11.11.11.11</te-router-id-ipv4>
                </ted>
            </ospf-node-attributes>
            <prefix>
                <prefix>10.0.14.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.12.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>11.11.11.11/32</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>192.168.10.0/24</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.11.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.13.0/30</prefix>
                <metric>1</metric>
            </prefix>
        </igp-node-attributes>
        <termination-point>
            <tp-id>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.14.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.14.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.13.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.13.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.12.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.12.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.11.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.11.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
    </node>
    <node>
        <node-id>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</node-id>
        <igp-node-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <router-id>14.14.14.14</router-id>
            <ospf-node-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <te-router-id-ipv4>14.14.14.14</te-router-id-ipv4>
                </ted>
            </ospf-node-attributes>
            <prefix>
                <prefix>192.168.20.0/24</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.14.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.24.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.22.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.21.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>14.14.14.14/32</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.25.0/30</prefix>
                <metric>1</metric>
            </prefix>
        </igp-node-attributes>
        <termination-point>
            <tp-id>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.24.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.24.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.14.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.14.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.25.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.25.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.22.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.22.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.21.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.21.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
    </node>
    <node>
        <node-id>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</node-id>
        <igp-node-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <router-id>12.12.12.12</router-id>
            <ospf-node-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <te-router-id-ipv4>12.12.12.12</te-router-id-ipv4>
                </ted>
            </ospf-node-attributes>
            <prefix>
                <prefix>12.12.12.12/32</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.24.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.23.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.21.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.11.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.13.0/30</prefix>
                <metric>1</metric>
            </prefix>
        </igp-node-attributes>
        <termination-point>
            <tp-id>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.13.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.13.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.11.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.11.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.23.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.23.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.24.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.24.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.21.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.21.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
    </node>
    <node>
        <node-id>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</node-id>
        <igp-node-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <router-id>11.11.11.11</router-id>
            <ospf-node-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <te-router-id-ipv4>11.11.11.11</te-router-id-ipv4>
                </ted>
            </ospf-node-attributes>
            <prefix>
                <prefix>10.0.12.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.14.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>11.11.11.11/32</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>192.168.10.0/24</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.11.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.13.0/30</prefix>
                <metric>1</metric>
            </prefix>
        </igp-node-attributes>
        <termination-point>
            <tp-id>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.14.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.14.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.12.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.12.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.13.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.13.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.11.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.11.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
    </node>
    <node>
        <node-id>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</node-id>
        <igp-node-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <router-id>12.12.12.12</router-id>
            <ospf-node-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <te-router-id-ipv4>12.12.12.12</te-router-id-ipv4>
                </ted>
            </ospf-node-attributes>
            <prefix>
                <prefix>12.12.12.12/32</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.24.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.23.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.21.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.11.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.13.0/30</prefix>
                <metric>1</metric>
            </prefix>
        </igp-node-attributes>
        <termination-point>
            <tp-id>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.24.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.24.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.13.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.13.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.21.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.21.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.23.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.23.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.11.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.11.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
    </node>
    <node>
        <node-id>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</node-id>
        <igp-node-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <router-id>12.12.12.12</router-id>
            <ospf-node-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <te-router-id-ipv4>12.12.12.12</te-router-id-ipv4>
                </ted>
            </ospf-node-attributes>
            <prefix>
                <prefix>12.12.12.12/32</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.24.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.23.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.21.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.11.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.13.0/30</prefix>
                <metric>1</metric>
            </prefix>
        </igp-node-attributes>
        <termination-point>
            <tp-id>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.23.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.23.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.24.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.24.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.13.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.13.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.11.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.11.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.21.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.21.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
    </node>
    <node>
        <node-id>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</node-id>
        <igp-node-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <router-id>11.11.11.11</router-id>
            <ospf-node-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <te-router-id-ipv4>11.11.11.11</te-router-id-ipv4>
                </ted>
            </ospf-node-attributes>
            <prefix>
                <prefix>10.0.12.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.14.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>11.11.11.11/32</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>192.168.10.0/24</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.11.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.13.0/30</prefix>
                <metric>1</metric>
            </prefix>
        </igp-node-attributes>
        <termination-point>
            <tp-id>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.11.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.11.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.14.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.14.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.13.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.13.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.12.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.12.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
    </node>
    <node>
        <node-id>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099</node-id>
        <igp-node-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <router-id>11.11.11.11</router-id>
            <ospf-node-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <te-router-id-ipv4>11.11.11.11</te-router-id-ipv4>
                </ted>
            </ospf-node-attributes>
            <prefix>
                <prefix>10.0.12.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.14.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>11.11.11.11/32</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>192.168.10.0/24</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.11.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.13.0/30</prefix>
                <metric>1</metric>
            </prefix>
        </igp-node-attributes>
        <termination-point>
            <tp-id>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.13.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.13.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.14.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.14.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.11.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.11.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.12.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.12.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
    </node>
    <node>
        <node-id>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</node-id>
        <igp-node-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <router-id>13.13.13.13</router-id>
            <ospf-node-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <te-router-id-ipv4>13.13.13.13</te-router-id-ipv4>
                </ted>
            </ospf-node-attributes>
            <prefix>
                <prefix>10.0.22.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.12.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>13.13.13.13/32</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.23.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.25.0/30</prefix>
                <metric>1</metric>
            </prefix>
        </igp-node-attributes>
        <termination-point>
            <tp-id>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.22.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.22.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.23.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.23.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.25.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.25.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.12.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.12.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
    </node>
    <node>
        <node-id>bgpls://Ospf:35/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</node-id>
        <igp-node-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <router-id>13.13.13.13</router-id>
            <ospf-node-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <te-router-id-ipv4>13.13.13.13</te-router-id-ipv4>
                </ted>
            </ospf-node-attributes>
            <prefix>
                <prefix>10.0.22.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.12.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>13.13.13.13/32</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.23.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.25.0/30</prefix>
                <metric>1</metric>
            </prefix>
        </igp-node-attributes>
        <termination-point>
            <tp-id>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.25.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.25.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.12.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.12.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.23.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.23.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:35/type=tp&amp;ipv4=10.0.22.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.22.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
    </node>
    <node>
        <node-id>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</node-id>
        <igp-node-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <router-id>14.14.14.14</router-id>
            <ospf-node-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <te-router-id-ipv4>14.14.14.14</te-router-id-ipv4>
                </ted>
            </ospf-node-attributes>
            <prefix>
                <prefix>192.168.20.0/24</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.14.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.24.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.22.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>14.14.14.14/32</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.21.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.25.0/30</prefix>
                <metric>1</metric>
            </prefix>
        </igp-node-attributes>
        <termination-point>
            <tp-id>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.21.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.21.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.14.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.14.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.25.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.25.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.24.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.24.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.22.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.22.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
    </node>
    <node>
        <node-id>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=202116108</node-id>
        <igp-node-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <router-id>12.12.12.12</router-id>
            <ospf-node-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <te-router-id-ipv4>12.12.12.12</te-router-id-ipv4>
                </ted>
            </ospf-node-attributes>
            <prefix>
                <prefix>12.12.12.12/32</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.24.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.23.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.21.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.11.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.13.0/30</prefix>
                <metric>1</metric>
            </prefix>
        </igp-node-attributes>
        <termination-point>
            <tp-id>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.21.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.21.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.13.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.13.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.24.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.24.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.11.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.11.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.23.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.23.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
    </node>
    <node>
        <node-id>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</node-id>
        <igp-node-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <router-id>13.13.13.13</router-id>
            <ospf-node-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <te-router-id-ipv4>13.13.13.13</te-router-id-ipv4>
                </ted>
            </ospf-node-attributes>
            <prefix>
                <prefix>10.0.22.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.12.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>13.13.13.13/32</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.23.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.25.0/30</prefix>
                <metric>1</metric>
            </prefix>
        </igp-node-attributes>
        <termination-point>
            <tp-id>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.12.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.12.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.25.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.25.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.23.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.23.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.22.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.22.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
    </node>
    <node>
        <node-id>bgpls://Ospf:33/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=218959117</node-id>
        <igp-node-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <router-id>13.13.13.13</router-id>
            <ospf-node-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <te-router-id-ipv4>13.13.13.13</te-router-id-ipv4>
                </ted>
            </ospf-node-attributes>
            <prefix>
                <prefix>10.0.22.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.12.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>13.13.13.13/32</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.23.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.25.0/30</prefix>
                <metric>1</metric>
            </prefix>
        </igp-node-attributes>
        <termination-point>
            <tp-id>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.22.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.22.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.25.1</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.25.1</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.12.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.12.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:33/type=tp&amp;ipv4=10.0.23.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.23.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
    </node>
    <node>
        <node-id>bgpls://Ospf:34/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</node-id>
        <igp-node-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <router-id>14.14.14.14</router-id>
            <ospf-node-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <te-router-id-ipv4>14.14.14.14</te-router-id-ipv4>
                </ted>
            </ospf-node-attributes>
            <prefix>
                <prefix>192.168.20.0/24</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.14.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.24.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.22.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>14.14.14.14/32</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.21.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.25.0/30</prefix>
                <metric>1</metric>
            </prefix>
        </igp-node-attributes>
        <termination-point>
            <tp-id>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.24.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.24.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.14.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.14.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.25.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.25.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.22.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.22.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:34/type=tp&amp;ipv4=10.0.21.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.21.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
    </node>
    <node>
        <node-id>bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=235802126</node-id>
        <igp-node-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
            <router-id>14.14.14.14</router-id>
            <ospf-node-attributes xmlns="urn:TBD:params:xml:ns:yang:ospf-topology">
                <ted>
                    <te-router-id-ipv4>14.14.14.14</te-router-id-ipv4>
                </ted>
            </ospf-node-attributes>
            <prefix>
                <prefix>192.168.20.0/24</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.14.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.24.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.22.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>14.14.14.14/32</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.25.0/30</prefix>
                <metric>1</metric>
            </prefix>
            <prefix>
                <prefix>10.0.21.0/30</prefix>
                <metric>1</metric>
            </prefix>
        </igp-node-attributes>
        <termination-point>
            <tp-id>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.21.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.21.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.22.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.22.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.24.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.24.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.14.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.14.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
        <termination-point>
            <tp-id>bgpls://Ospf:36/type=tp&amp;ipv4=10.0.25.2</tp-id>
            <igp-termination-point-attributes xmlns="urn:TBD:params:xml:ns:yang:nt:l3-unicast-igp-topology">
                <ip-address>10.0.25.2</ip-address>
            </igp-termination-point-attributes>
        </termination-point>
    </node>
    <server-provided>true</server-provided>
    <topology-types>
        <bgp-linkstate-topology xmlns="urn:opendaylight:params:xml:ns:yang:odl-bgp-topology-types"/>
    </topology-types>
</topology>
```



## Inventario OpenFlow

| Campo | Valor |
| --- | --- |
| Metodo | `GET` |
| Endpoint | `/restconf/data/opendaylight-inventory:nodes?content=nonconfig` |
| Cliente | `OdlRestconfDataClient` |
| Deserializador | `OpenflowInventoryXmlDeserializer` |

### Solicitud XML Esperada

```xml
<!-- Sin cuerpo: solicitud GET RESTCONF. -->
```

### Respuesta XML Esperada

```xml
<!-- Pendiente: cuerpo XML esperado del inventario OpenFlow. -->
<?xml version="1.0" encoding="UTF-8"?>
<nodes xmlns="urn:opendaylight:inventory">
    <node xmlns="urn:opendaylight:inventory">
        <meter-features xmlns="urn:opendaylight:meter:statistics">
            <meter-band-supported>meter-band</meter-band-supported>
            <max_meter>0</max_meter>
            <max_bands>0</max_bands>
            <meter-capabilities-supported>meter-capability</meter-capabilities-supported>
            <max_color>0</max_color>
        </meter-features>
        <supported-match-types xmlns="urn:opendaylight:flow:inventory">
            <match-type xmlns="urn:opendaylight:flow:inventory">
                <support-state>native</support-state>
                <match>Some match</match>
            </match-type>
        </supported-match-types>
        <supported-instructions xmlns="urn:opendaylight:flow:inventory">
            <instruction-type xmlns="urn:opendaylight:flow:inventory">
                <support-state>native</support-state>
                <instruction>Some instruction</instruction>
            </instruction-type>
        </supported-instructions>
        <snapshot-gathering-status-start xmlns="urn:opendaylight:flow:inventory">
            <begin>0000-00-00T00:00:00Z</begin>
        </snapshot-gathering-status-start>
        <switch-features xmlns="urn:opendaylight:flow:inventory">
            <capabilities>feature-capability</capabilities>
            <max_buffers>0</max_buffers>
            <max_tables>0</max_tables>
        </switch-features>
        <supported-actions xmlns="urn:opendaylight:flow:inventory">
            <action-type xmlns="urn:opendaylight:flow:inventory">
                <support-state>native</support-state>
                <action>Some action</action>
            </action-type>
        </supported-actions>
        <snapshot-gathering-status-end xmlns="urn:opendaylight:flow:inventory">
            <end>0000-00-00T00:00:00Z</end>
            <succeeded>true</succeeded>
        </snapshot-gathering-status-end>
        <id>Some id</id>
        <node-connector xmlns="urn:opendaylight:inventory">
            <reason xmlns="urn:opendaylight:flow:inventory">add</reason>
            <peer-features xmlns="urn:opendaylight:flow:inventory">ten-mb-hd pause-asym</peer-features>
            <name xmlns="urn:opendaylight:flow:inventory">Some name</name>
            <maximum-speed xmlns="urn:opendaylight:flow:inventory">0</maximum-speed>
            <id>Some id</id>
            <state xmlns="urn:opendaylight:flow:inventory">
                <link-down>true</link-down>
                <blocked>true</blocked>
                <live>true</live>
            </state>
            <current-feature xmlns="urn:opendaylight:flow:inventory">ten-mb-hd pause-asym</current-feature>
            <current-speed xmlns="urn:opendaylight:flow:inventory">0</current-speed>
            <port-number xmlns="urn:opendaylight:flow:inventory">0</port-number>
            <supported xmlns="urn:opendaylight:flow:inventory">ten-mb-hd pause-asym</supported>
        </node-connector>
        <group-features xmlns="urn:opendaylight:group:statistics">
            <group-types-supported>group-type</group-types-supported>
            <max-groups>0</max-groups>
            <group-capabilities-supported>group-capability</group-capabilities-supported>
            <actions>0</actions>
        </group-features>
    </node>
</nodes>
```

## Aprovisionamiento de Flujo OpenFlow

| Campo | Valor |
| --- | --- |
| Metodo | `PUT` |
| Endpoint | `/restconf/data/opendaylight-inventory:nodes/node={node-id}/flow-node-inventory:table=0/flow={flow-id}` |
| Cliente | `OdlRestconfDataClient` |
| Serializador | `OpenflowFlowXmlSerializer` |

### Solicitud XML Esperada

```xml
<!-- Pendiente: cuerpo XML esperado para el flujo OpenFlow. -->
```

### Respuesta XML Esperada

```xml
<!-- Pendiente: cuerpo XML esperado de la respuesta RESTCONF PUT. -->
```

## Verificaciones RESTCONF Complementarias

Las consultas de topologia global, configuracion de flujos y estado operativo de tablas tambien se registran automaticamente por `OdlRestconfDataClient`. Estas llamadas conservan los mismos encabezados XML y permiten correlacionar el aprovisionamiento configurado con el estado operativo publicado por OpenDaylight.
