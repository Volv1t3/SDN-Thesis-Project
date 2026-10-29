# Application Construction — Policy

<show-structure for="chapter" depth="3"/>

The <path>app.policy</path> package translates an already validated traffic-class prediction into a traffic policy. It has two responsibilities: validate the policy artifact during startup and resolve an effective policy during classification. It does not classify packets, lease a classifier, or construct an HTTP response. Those responsibilities are documented in [Application Construction — Inference](Python-Inference-API-—-Application-Construction-Inference.md) and [Application Construction — Model Runtime](Python-Inference-API-—-Application-Construction-Model-Runtime.md).

The package has a deliberately small runtime surface. <code>load_policy_file</code> loads and validates JSON from the configured artifact path. <code>PolicyMapper</code> retains the resulting immutable-in-practice Pydantic object and resolves one policy from a predicted class and confidence. The dependency builder creates this mapper after startup validation, while the inference route consumes its result. The parent [Application Construction](Python-Inference-API-—-Application-Construction.md#application-state) topic describes how that mapper is stored in <code>AppServices</code>.

## Package boundary and collaborators {id="policy-package-boundary"}

The package contains a namespace marker, a Pydantic artifact schema, and the mapper/load helper. The namespace marker has no executable behavior. The validation models express the accepted policy-file structure. The mapper reads the policy file once, selects the default profile for low-confidence predictions where a threshold is configured, and otherwise retrieves the policy keyed by the predicted traffic class.

<code-block lang="mermaid">
flowchart TD
    Artifact["Policy JSON artifact"] --> Loader["load_policy_file"]
    Loader --> Schema["PolicyFile Pydantic model"]
    Schema --> Mapper["PolicyMapper"]
    Prediction["predicted class and confidence"] --> Mapper
    Mapper --> Selected["TrafficPolicy"]
    Selected --> Route["inference response construction"]
    Route --> Caller["ClassifyResponse"]
</code-block>

The package boundary matters because policy validation is not a request-time operation. The policy file is parsed and validated while services are initialized. A classification request receives a mapper that has already passed startup validation. The route can therefore concentrate on selecting a policy and reporting any unexpected mapper failure through its established error boundary.

| Concern | Owning module | Policy package involvement | Backlink |
|---|---|---|---|
| Artifact path and startup order | dependency builder | consumes validated <code>PolicyFile</code> | [Application Construction startup](Python-Inference-API-—-Application-Construction.md#startup-validation) |
| Prediction production | model runtime | supplies class and confidence | [Model Runtime](Python-Inference-API-—-Application-Construction-Model-Runtime.md) |
| HTTP request and response | inference route | maps prediction to response policy | [Inference](Python-Inference-API-—-Application-Construction-Inference.md#inference-policy-mapping) |
| Selection and fallback | policy mapper | owns decision | this topic |
| Selection metrics and logs | inference and observability | consumes mapper outcome | [Observability](Python-Inference-API-—-Application-Construction-Observability.md) |

## Policy artifact schema {id="policy-artifact-schema"}

The policy artifact is a strict Pydantic document. Each schema class forbids undeclared fields and uses strict integer types for numeric policy values. Strict validation prevents an external JSON artifact from quietly coercing a string such as <code>"46"</code> into a DSCP value or accepting an additional undeclared property. The startup builder treats invalid artifacts as readiness failures instead of publishing a mapper with partial or ambiguous policy data.

<code-block lang="python" src="../../src/main/python/api/app/policy/sdn_mpls_ml_policy_validation_models.py" include-lines="10-63"/>

<code-block lang="mermaid">
classDiagram
    class PathConstraints {
        +requested_bandwidth_kbps: StrictInt
        +setup_priority: StrictInt
        +hold_priority: StrictInt
    }
    class TrafficPolicy {
        +profile_name: str
        +dscp: StrictInt
        +mpls_tc: StrictInt
        +path_constraints: PathConstraints
    }
    class PolicyFile {
        +schema_version: str
        +default_profile: TrafficPolicy
        +class_policies: dict[str, TrafficPolicy]
    }
    TrafficPolicy --> PathConstraints
    PolicyFile --> TrafficPolicy : default profile
    PolicyFile --> TrafficPolicy : class mapping values
</code-block>

### Path constraints {id="policy-path-constraints"}

<code>PathConstraints</code> represents values that become path or tunnel constraints downstream of the Python API. 
Requested bandwidth is a nonnegative strict integer. Setup and hold priorities are strict integers constrained to 
the MPLS-TE priority range from zero through seven. The model does not choose a path or configure a tunnel, rather it 
validates the policy data that the controller-side consumer receives in the classification response.

The parent API documentation distinguishes policy selection from applying a policy to an LSP. This package performs only the selection side. The controller may have its own pair-policy and directional application rules, which are outside this Python package. Keeping that boundary explicit prevents the policy JSON document from being read as a direct device configuration.

| Field | Type and range | Meaning in this package | Not performed here |
|---|---|---|---|
| <code>requested_bandwidth_kbps</code> | strict integer, at least zero | requested bandwidth carried by a profile | bandwidth reservation |
| <code>setup_priority</code> | strict integer, zero to seven | setup priority carried by a profile | PCEP signaling |
| <code>hold_priority</code> | strict integer, zero to seven | hold priority carried by a profile | LSP modification |

### Traffic profile {id="traffic-profile"}

<code>TrafficPolicy</code> combines a profile name, DSCP value, MPLS traffic class, and nested path constraints. DSCP is limited to zero through sixty-three, and MPLS traffic class is limited to zero through seven. The profile-name validator rejects names that contain only whitespace while retaining the supplied nonempty name. The response model later serializes these validated values for the classification caller.

The policy schema defines structural correctness. Startup performs additional application-specific checks on the complete mapping, including expected traffic-class coverage and configured bandwidth bounds. That second stage belongs to the dependency builder because it requires the classification domain and startup settings. The policy package remains responsible for the local JSON structure and primitive value ranges.

<code-block lang="python" src="../../src/main/python/api/app/policy/sdn_mpls_ml_policy_validation_models.py" include-lines="66-97"/>

<code-block lang="mermaid">
flowchart TB
    Json["JSON object"] --> Root["PolicyFile"]
    Root --> Version{"schema version is 1.0?"}
    Version -- no --> Reject["Pydantic validation error"]
    Version -- yes --> Default["default_profile: TrafficPolicy"]
    Version --> Classes["class_policies: class to TrafficPolicy"]
    Default --> ProfileRules["DSCP, MPLS TC, constraints"]
    Classes --> ProfileRules
    ProfileRules --> Valid["artifact structure valid"]
</code-block>

### Root policy document {id="root-policy-document"}

The root <code>PolicyFile</code> requires a schema version, one default profile, and a mapping from traffic-class names to traffic policies. Its version validator accepts only the supported <code>1.0</code> schema. The default profile is not merely a missing-class fallback. It is the named result of the low-confidence selection branch when a confidence threshold applies.

The mapping uses traffic-class strings as keys. The mapper indexes that mapping only after the predictor has produced a normalized class name. The startup dependency builder verifies complete expected-class coverage before it publishes a mapper. A missing key after startup would therefore be unexpected and is treated by the inference caller as a controlled policy-mapping failure.

## Loading and startup validation {id="policy-loading-startup"}

<code>load_policy_file</code> reads a file as UTF-8 text, parses it as JSON, and calls <code>PolicyFile.model_validate</code>. It converts JSON decode errors, filesystem errors, and Pydantic validation errors into <code>ValueError</code> with a project message. The startup dependency module catches and translates those loading failures into <code>StartupValidationError</code>, which becomes cached readiness state. The detailed startup sequence is covered by [Application Construction](Python-Inference-API-—-Application-Construction.md#startup-validation).

<code-block lang="python" src="../../src/main/python/api/app/policy/sdn_mpls_ml_policy_mapper.py" include-lines="10-49"/>

<code-block lang="mermaid">
sequenceDiagram
    participant Startup as startup dependency builder
    participant Loader as load_policy_file
    participant Disk as policy JSON file
    participant Schema as PolicyFile
    participant Ready as ReadinessState
    Startup->>Loader: load configured path
    Loader->>Disk: read UTF-8 text
    Disk-->>Loader: JSON text
    Loader->>Loader: parse JSON
    Loader->>Schema: model_validate(payload)
    alt valid document
        Schema-->>Startup: PolicyFile
        Startup->>Ready: continue startup validation
    else unreadable, invalid JSON, or schema failure
        Loader-->>Startup: ValueError
        Startup->>Ready: mark_failed(structured startup error)
    end
</code-block>

The loader does not make a policy decision, and it does not inspect a prediction. It establishes a typed artifact 
boundary. This separation is useful for operations because an invalid document is diagnosed at startup rather than appearing as an arbitrary failure after a caller submits a packet. A ready process has already loaded a syntactically and structurally valid policy document.

| Loader condition | Direct exception type | Startup consequence | Runtime mapper availability |
|---|---|---|---|
| File can be read and parsed | none | validation continues | possible after complete startup |
| File cannot be read | <code>ValueError</code> from OS error | readiness failure | unavailable |
| JSON is malformed | <code>ValueError</code> from decode error | readiness failure | unavailable |
| Schema fields are invalid | <code>ValueError</code> from validation error | readiness failure | unavailable |
| Complete map check fails | startup validation error | readiness failure | unavailable |

### Complete-map validation boundary {id="complete-map-boundary"}

The dependency builder performs checks that are intentionally not embedded in <code>PolicyFile</code>. It knows the expected traffic classes, configured minimum and maximum tunnel bandwidth, and default-profile convention. It validates that no unknown classes occur, that all expected classes are represented, that the default profile meets its required semantics, and that every policy serializes cleanly within configured limits.

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_dependencies.py" include-lines="1602-1738"/>

This distinction produces two useful failure layers. The Pydantic document schema reports that a policy is malformed in its own local structure. Complete-map validation reports that a structurally valid policy is incompatible with the application’s supported classes or startup configuration. Both failures prevent a mapper from being published, but they guide different corrections.

## Policy selection and confidence fallback {id="policy-selection"}

<code>PolicyMapper</code> retains a validated <code>PolicyFile</code> and an optional minimum confidence. The mapper has two normal selection outcomes. If a threshold is configured and the prediction confidence is strictly lower than it, the mapper returns <code>default_profile</code>, <code>True</code>, and <code>confidence_below_threshold</code>. Otherwise it indexes <code>class_policies</code> using the predicted class and returns that profile, <code>False</code>, and no reason.

<code-block lang="python" src="../../src/main/python/api/app/policy/sdn_mpls_ml_policy_mapper.py" include-lines="52-104"/>

<code-block lang="mermaid">
flowchart TD
    Input["predicted class and confidence"] --> Threshold{"minimum confidence configured and confidence below it?"}
    Threshold -- yes --> Default["default_profile"]
    Default --> Fallback["fallback=true\nreason=confidence_below_threshold"]
    Threshold -- no --> ClassMap["class_policies[predicted_class]"]
    ClassMap --> Direct["fallback=false\nreason absent"]
    Fallback --> Route["inference route builds PolicyResponse"]
    Direct --> Route
</code-block>

The comparison is strictly less than the threshold. A confidence equal to the configured threshold selects the class-specific policy. An absent threshold disables confidence fallback. In that situation, the mapper directly indexes the class policy mapping for every normalized prediction. The mapper does not calculate confidence and does not normalize class names. Those are responsibilities of model runtime and inference orchestration.

| Threshold state | Confidence relation | Selected profile | Fallback fields |
|---|---|---|---|
| Threshold absent | any valid confidence | class-specific profile | <code>false</code>, no reason |
| Threshold present | lower than threshold | default profile | <code>true</code>, <code>confidence_below_threshold</code> |
| Threshold present | equal to threshold | class-specific profile | <code>false</code>, no reason |
| Threshold present | greater than threshold | class-specific profile | <code>false</code>, no reason |

### Default policy property {id="default-policy-property"}

The <code>default_policy</code> property exposes the configured default profile to a caller that needs the best-effort policy directly. The ordinary inference path uses <code>resolve</code> because it needs the fallback flag and optional reason for its response, logs, and metrics. The property does not clone or mutate the profile. It returns the validated model held by the mapper.

The default profile is a policy artifact value, not a dynamically generated policy. A confidence fallback therefore remains reproducible for a given loaded artifact and threshold. Changing the default behavior requires updating configuration or the artifact, followed by startup validation, rather than adding conditional logic to the HTTP handler.

## Inference and response boundary {id="policy-inference-boundary"}

The inference route calls <code>resolve</code> only after it receives a valid <code>PredictionResult</code>. It catches unexpected mapper exceptions and turns them into <code>PolicyMappingFailedError</code>. When selection succeeds, it serializes the selected <code>TrafficPolicy</code> into <code>PolicyResponse</code> and attaches the fallback fields. The full classification route, response schema, and HTTP branches are documented in [Application Construction — Inference](Python-Inference-API-—-Application-Construction-Inference.md#inference-policy-mapping).

<code-block lang="python" src="../../src/main/python/api/app/api/sdn_mpls_ml_inference_route.py" include-lines="238-278"/>

<code-block lang="mermaid">
sequenceDiagram
    participant Route as classify()
    participant Predictor as classifier adapter
    participant Mapper as PolicyMapper
    participant Metrics as observability
    participant Response as PolicyResponse
    Route->>Predictor: execute inference
    Predictor-->>Route: PredictionResult
    Route->>Mapper: resolve(class name, confidence)
    alt selection succeeds
        Mapper-->>Route: policy, fallback, reason
        Route->>Metrics: count selection and fallback if needed
        Route->>Response: serialize policy and fallback fields
    else mapper raises
        Mapper-->>Route: exception
        Route->>Route: raise PolicyMappingFailedError
    end
</code-block>

The policy package does not attach a request ID. Request correlation is established by middleware and consumed by the inference route. It also does not create the public response model. It returns typed selection data. These separations preserve a small policy module and let the central error handler keep a uniform HTTP error contract.

| Boundary object | Produced by | Consumed by | Policy package responsibility |
|---|---|---|---|
| <code>PredictionResult</code> | model runtime | inference route | uses class and confidence only |
| <code>TrafficPolicy</code> | policy mapper | inference route | owns validated selection |
| <code>PolicyResponse</code> | inference schema/route | API caller | no direct construction |
| <code>PolicyMappingFailedError</code> | inference route | central handler | does not define error |
| <code>request_id</code> | middleware | route and response | no direct access |

## Policy observability boundary {id="policy-observability-boundary"}

The mapper has no direct Prometheus dependency. It returns the selection outcome that lets the inference route increment policy selection metrics and, for a fallback, a dedicated fallback counter. The route also emits an informational structured event when a fallback applies. The [Application Construction — Observability](Python-Inference-API-—-Application-Construction-Observability.md) topic documents metric registration, label conventions, and scrape behavior.

<code-block lang="mermaid">
flowchart TD
    Mapper["PolicyMapper.resolve"] --> Selection["selected policy, fallback, reason"]
    Selection --> Route["inference route"]
    Route --> SelectionMetric["policy selection counter"]
    Route --> FallbackMetric["fallback counter when true"]
    Route --> Log["policy_fallback_applied log event"]
    Route --> Response["PolicyResponse"]
    SelectionMetric --> Scrape["fixed /metrics scrape"]
    FallbackMetric --> Scrape
</code-block>

This boundary prevents a mapper unit test from requiring a Prometheus registry or HTTP request. The mapper can be tested with a validated policy file, a predicted class, and a confidence value. Integration tests can then establish that the inference route turns its selection into the expected response, event, and metric labels. This layered verification follows the application construction boundaries rather than duplicating selection logic in observability code.

| Evidence | Producer | Trigger | Backlink |
|---|---|---|---|
| Selection counter | inference route | any selected policy | [Inference metrics](Python-Inference-API-—-Application-Construction-Inference.md#inference-metrics) |
| Fallback counter | inference route | fallback is true | [Observability](Python-Inference-API-—-Application-Construction-Observability.md) |
| Fallback log | inference route | fallback is true | [Application logging](Python-Inference-API-—-Application-Construction.md#logging-relationship) |
| Public policy object | inference route | selection succeeds | [API directory](Python-Inference-API-—-API-Request-Directory.md) |

## Policy invariants and maintenance {id="policy-invariants"}

Several invariants keep policy selection predictable. The policy artifact must be fully validated before a mapper reaches the ready service container. A default profile exists even when no confidence fallback threshold is configured. A fallback is a successful selection outcome, not a classification failure. A class-specific selection assumes complete-map validation has established the key before runtime. An unexpected key error is handled by the caller as a policy-mapping failure rather than converted into a synthetic fallback.

| Invariant | Source boundary | Operational consequence |
|---|---|---|
| Artifact is loaded once at startup | loader and dependencies | requests do not parse policy JSON |
| Unknown artifact fields are forbidden | Pydantic models | configuration drift fails early |
| Supported schema is fixed | root version validator | incompatible artifact fails early |
| Default profile is present | <code>PolicyFile</code> | low-confidence selection has a defined target |
| Low confidence uses strict comparison | <code>PolicyMapper.resolve</code> | equality retains class profile |
| Direct selection indexes normalized class | mapper | class-domain mismatch is an error path |
| Fallback preserves prediction validity | inference route | result remains successful |
| Metrics remain outside mapper | inference and observability | mapper stays deterministic and testable |

When changing policy behavior, review the artifact schema, startup complete-map checks, mapper selection logic, inference response representation, and observability labels together. The change may also affect the controller-side policy consumer, but that consumer is outside the Python package and must be verified independently. The parent application page supplies the end-to-end construction map for that review.

## Source map and cross-references {id="policy-source-map"}

The source excerpts on this page map directly to the two files in <path>app.policy</path>. The dependency excerpts establish the startup boundary, and the inference excerpt establishes the sole request-time consumer. The page intentionally links outward for model execution, HTTP response serialization, request correlation, and metric implementation to avoid presenting one package as the owner of the entire classification pipeline.

| Source | Provides | Related documentation |
|---|---|---|
| <code>policy/\_\_init\_\_.py</code> | package namespace | this page |
| <code>policy_validation_models.py</code> | strict artifact schema | this page |
| <code>policy_mapper.py</code> | loading and selection | this page |
| <code>sdn_mpls_ml_dependencies.py</code> | startup map coverage | [Application Construction](Python-Inference-API-—-Application-Construction.md#startup-validation) |
| <code>sdn_mpls_ml_inference_route.py</code> | request-time consumer | [Inference](Python-Inference-API-—-Application-Construction-Inference.md) |
| <code>sdn_mpls_ml_classification_metrics.py</code> | metric producer support | [Observability](Python-Inference-API-—-Application-Construction-Observability.md) |

The policy package is complete when its artifact is valid, its mapping covers the supported class domain, and its mapper returns an explicit selection outcome. The surrounding application is responsible for using that outcome in a request. This division keeps policy selection transparent and makes low-confidence behavior an explicit, documented choice.

## Artifact interpretation and decision ledger {id="policy-decision-ledger"}

The policy document represents a static set of traffic-profile candidates. It does not encode a probability distribution, perform packet inspection, or remember earlier requests. Its default profile and class map are both part of the loaded artifact. A runtime selection receives only a model-produced class name and confidence, then chooses one of those already validated objects.

This fixed-data design has an important consequence for maintenance. The artifact can be reviewed as configuration before deployment, while the mapper can be reviewed as a small deterministic decision function. A production change should not depend on the order of JSON object members because the typed root model names the default profile and the class-policy map explicitly. It should instead be evaluated in terms of schema validity, complete class coverage, and the configured confidence threshold.

| Decision input | Origin | Policy interpretation | Selection effect |
|---|---|---|---|
| <code>schema_version</code> | artifact root | identifies supported document contract | startup accepts only version <code>1.0</code> |
| <code>default_profile</code> | artifact root | best-effort policy candidate | selected for low confidence when threshold applies |
| <code>class_policies</code> | artifact root | mapping of traffic-class names | selected for direct class resolution |
| <code>profile_name</code> | policy object | stable named profile | returned to caller in serialized policy |
| <code>dscp</code> | policy object | requested DSCP marking | returned as validated integer |
| <code>mpls_tc</code> | policy object | requested MPLS traffic class | returned as validated integer |
| <code>requested_bandwidth_kbps</code> | nested constraints | requested path bandwidth | returned as validated integer |
| <code>setup_priority</code> | nested constraints | requested setup priority | returned as validated integer |
| <code>hold_priority</code> | nested constraints | requested hold priority | returned as validated integer |
| predicted class | model runtime | class-map lookup key | selects class-specific policy |
| prediction confidence | model runtime | threshold comparison operand | may select default profile |
| minimum confidence | validated settings | optional fallback threshold | enables or disables fallback branch |

### Artifact validation scenarios {id="policy-artifact-scenarios"}

An unreadable policy artifact fails before JSON parsing begins. The loader turns the filesystem condition into a value error with the policy-read message. Startup records the controlled failure in readiness state. A liveness probe can still receive a response because the HTTP process exists, while readiness and classification correctly communicate that the service cannot provide a validated policy-backed inference result.

Malformed JSON reaches the loader's decoder branch. A valid JSON document with an incompatible structure reaches the Pydantic branch instead. These distinctions are useful to an operator because syntax and structure call for different corrections. Neither branch is deferred until a user submits a classification request.

| Artifact scenario | Local validator | Why it fails | Corrective boundary |
|---|---|---|---|
| File absent | filesystem read | configured artifact cannot be opened | deployment volume or path |
| File unreadable | filesystem read | process lacks readable content | permissions or mount |
| Invalid JSON text | JSON decoder | serialization syntax is malformed | artifact producer |
| Unknown root field | strict <code>PolicyFile</code> | document contract rejects drift | artifact producer |
| Unsupported schema version | version validator | runtime supports only <code>1.0</code> | artifact migration |
| Missing default profile | root model | fallback target is required | artifact producer |
| Wrong DSCP type | strict policy model | string or coercible value is forbidden | artifact producer |
| Out-of-range MPLS TC | field range | traffic class must be zero through seven | artifact producer |
| Empty profile name | profile validator | name carries no useful value | artifact producer |
| Missing expected class | complete-map validation | application class domain is incomplete | artifact producer |
| Unknown class key | complete-map validation | mapping exceeds supported class domain | artifact producer |
| Invalid bandwidth convention | complete-map validation | deployment bounds or default semantics fail | configuration and artifact review |

### Profile field relationships {id="policy-profile-fields"}

The schema deliberately separates classification semantics from traffic-engineering values. A class name appears only as a map key. A profile contains traffic treatment and path constraints. This means more than one class can, if the artifact author chooses, point to policy objects with equivalent values. The mapper does not impose uniqueness across profile names or prevent equivalent profile definitions.

The application-specific complete-map checks add consistency without changing that ownership. They verify whether each expected class has a profile and whether profile values stay inside configured tunnel-bandwidth rules. The model runtime remains the authority for which class names can be predicted. The policy package remains the authority for whether a document can represent the selected policy values.

<code-block lang="mermaid">
flowchart TB
    ClassName["normalized predicted class"] --> Lookup["class_policies lookup"]
    Lookup --> Profile["TrafficPolicy"]
    Profile --> Marking["DSCP and MPLS TC"]
    Profile --> Constraints["PathConstraints"]
    Constraints --> Bandwidth["requested bandwidth"]
    Constraints --> Priorities["setup and hold priorities"]
    Profile --> Response["PolicyResponse serialization by route"]
</code-block>

## Runtime selection scenarios {id="policy-runtime-scenarios"}

The mapper has no mutable request state. Each <code>resolve</code> call evaluates the confidence threshold, then returns references to models held by the validated policy file. The route can call the mapper repeatedly for concurrent requests because the decision function does not update class policies, the default profile, or the threshold. Pooling and thread execution are separate concerns documented in [Application Construction — Model Runtime](Python-Inference-API-—-Application-Construction-Model-Runtime.md).

A successful class-specific selection means that confidence did not activate fallback and a policy exists for the predicted class. It does not mean the policy has already been applied to a network device. A successful fallback likewise means the selection rule chose a defined default profile. It does not mean that the prediction was discarded or that classification failed.

| Scenario | Threshold evaluation | Map access | Mapper result | Route outcome |
|---|---|---|---|---|
| Threshold absent, known class | skipped | class key read | class policy, false, no reason | success |
| Threshold present, confidence above | false | class key read | class policy, false, no reason | success |
| Threshold present, confidence equal | false because comparison is strict | class key read | class policy, false, no reason | success |
| Threshold present, confidence below | true | no class-map access required | default profile, true, reason | success with fallback |
| Map key unexpectedly absent | false | key lookup fails | exception | controlled policy mapping failure |
| Invalid mapper construction | cannot occur after ready startup | not reached | not published | not-ready path |

### Confidence threshold semantics {id="policy-threshold-semantics"}

The threshold is optional. When it is <code>None</code>, confidence does not alter policy selection. When it is set, fallback activates only for a confidence strictly below the value. This preserves an intuitive boundary where the configured minimum itself is acceptable. The model runtime calculates confidence and validates model output before the mapper sees it, so the mapper does not need to examine probability vectors.

The fallback reason is a stable machine-readable string. The inference route copies it into the policy response and uses it for fallback observability. It is not a free-form explanation generated from the traffic class or model probabilities. This makes metric label and log interpretation stable across requests that select the same fallback branch.

<code-block lang="mermaid">
stateDiagram-v2
    [*] --> ReceivePrediction
    ReceivePrediction --> DirectSelection : threshold absent
    ReceivePrediction --> CompareThreshold : threshold present
    CompareThreshold --> DefaultFallback : confidence below threshold
    CompareThreshold --> DirectSelection : confidence at or above threshold
    DirectSelection --> SelectedClassProfile
    DefaultFallback --> SelectedDefaultProfile
    SelectedClassProfile --> [*]
    SelectedDefaultProfile --> [*]
</code-block>

## Failure ownership and non-claims {id="policy-failure-ownership"}

The policy package exposes loader errors as values errors because it is used by the startup dependency layer. It does not create FastAPI exceptions, status codes, JSON responses, request IDs, or metrics. The inference route owns conversion of an unexpected <code>resolve</code> exception into <code>PolicyMappingFailedError</code>. The central application handler owns conversion of that domain error into a common HTTP error envelope.

This division helps readers diagnose a failure without attributing it to the wrong package. A readiness failure indicates that a mapper was not published. A policy-mapping failure during a ready request indicates that selection itself raised unexpectedly. A low-confidence fallback is not a failure at all. It is an intended successful branch with an explicit policy and reason.

| Observation | Correct owner | Meaning | Related page |
|---|---|---|---|
| Readiness reports policy schema error | startup dependencies | artifact was not publishable | [Application Construction](Python-Inference-API-—-Application-Construction.md#startup-validation) |
| Request returns <code>MODEL_NOT_READY</code> | inference route | ready services are incomplete | [Inference](Python-Inference-API-—-Application-Construction-Inference.md#inference-ready-state-gate) |
| Request returns policy mapping error | inference route and central handler | mapper raised unexpectedly | [Application Construction error boundary](Python-Inference-API-—-Application-Construction.md#error-boundary) |
| Response contains fallback flag | mapper and inference route | low confidence selected default profile | this topic |
| Metric records a fallback | inference route and observability | successful fallback branch occurred | [Observability](Python-Inference-API-—-Application-Construction-Observability.md) |

The package makes no claim that the selected DSCP, MPLS traffic class, bandwidth, or priorities were applied downstream. The Python API returns policy intent to its caller. Controller-side policy application is a separate system concern. This is especially important when interpreting a successful classification response in an end-to-end deployment.

## Maintenance checklist {id="policy-maintenance-checklist"}

When a policy artifact changes, review it in stages. First validate local JSON syntax and strict schema fields. Then evaluate it against the application class domain and configured bandwidth bounds. Finally exercise the intended prediction confidence cases through the inference route. This sequence follows the same startup-to-request progression as the executable implementation.

| Change | Review required | Backlink |
|---|---|---|
| Add or remove a class policy | expected class coverage and model metadata names | [Model Runtime metadata](Python-Inference-API-—-Application-Construction-Model-Runtime.md#model-metadata) |
| Change threshold | equality and below-threshold behavior | [Inference policy boundary](Python-Inference-API-—-Application-Construction-Inference.md#inference-policy-mapping) |
| Change default profile | fallback response and metrics meaning | [Observability](Python-Inference-API-—-Application-Construction-Observability.md) |
| Change constraints | strict ranges and startup bandwidth rules | [Environment Variables](Python-Inference-API-—-Application-Construction-Environment-Variables.md) |
| Change schema version | loader and root model support | this topic |
| Change response fields | policy response model and public contract | [API Request Directory](Python-Inference-API-—-API-Request-Directory.md) |

The test boundary should also remain layered. Unit tests can validate the models, loader, and mapper decisions with local artifacts. Startup tests can validate that incompatible complete maps leave the process not ready. API tests can validate the serialized policy and fallback fields. Observability tests can validate that the caller records selection and fallback evidence. These tests complement one another rather than making the mapper responsible for HTTP or metrics behavior.

## Editing the policy mapping artifact {id="editing-policy-mapping-artifact"}

The deployed mapping is the JSON artifact below. Its path is the default value of <code>POLICY_FILENAME</code> beneath <code>CONFIG_DIR</code>. Operators can use another filename through that setting, but the replacement document must retain the same policy-file contract. The Pydantic models beside the artifact are the executable schema. They describe the document that the process reads, rather than a separate JSON Schema file that an editor must discover or keep synchronized.

<compare type="left-right" first-title="Editable policy mapping" second-title="Executable Pydantic schema">
    <code-block lang="json" src="../../src/main/python/api/configs/sdn_mpls_ml_traffic_class_to_policy_mapping.json" include-lines="1-85"/>
    <code-block lang="python" src="../../src/main/python/api/app/policy/sdn_mpls_ml_policy_validation_models.py" include-lines="15-97"/>
</compare>

The document has three required root fields. <code>schema_version</code> is presently fixed at <code>"1.0"</code>. <code>default_profile</code> supplies the policy returned for low-confidence predictions. <code>class_policies</code> maps each supported predicted class to a <code>TrafficPolicy</code>. The schema rejects unknown fields at every level and uses strict integers, so quoted numbers and extra convenience keys are configuration errors rather than values that the loader silently coerces.

Each profile has a nonblank <code>profile_name</code>, a DSCP value from zero through 63, an MPLS traffic-class value from zero through seven, and one nested <code>path_constraints</code> object. That object requires an integer nonnegative bandwidth in kilobits per second and priorities from zero through seven. The Pydantic schema validates those local fields. The startup dependency layer then evaluates the policy in the context of the running application, as described in [Complete-map validation](#complete-map-boundary).

### Supported edits and required invariants {id="policy-mapping-edit-invariants"}

An operator may change treatment values and tunnel constraints in an existing class profile when the target deployment accepts those values. The key set is different. Class keys must exactly match the class names that the model metadata exposes to the application. A profile may be duplicated deliberately across two classes, but a class cannot be removed, renamed, or added solely by editing this document. Such a change requires a compatible model metadata and application-class change as well.

The default profile is intentionally shaped like every other policy so that the response can always serialize the same policy structure. Its requested bandwidth is a special invariant. It must be zero, which records that default selection does not request a tunnel through this policy contract. Every class-specific policy passes the configured minimum and maximum tunnel-bandwidth checks. Those limits come from the application configuration, so the valid range is deployment-specific even though the JSON schema accepts all nonnegative integer bandwidths.

| Editable element | Schema rule | Startup rule | Operational consequence |
|---|---|---|---|
| <code>schema_version</code> | exact string <code>"1.0"</code> | unsupported version prevents publication | update the runtime before changing version |
| <code>default_profile.profile_name</code> | nonblank string | serializable profile | names the fallback treatment in the response |
| <code>default_profile.path_constraints.requested_bandwidth_kbps</code> | integer at least zero | must equal zero | prevents default fallback from requesting a tunnel |
| class key | string map key | must be known and complete | selects the profile for a predicted class |
| class profile DSCP and MPLS TC | strict bounded integers | serializable profile | contributes marking intent to the response |
| class profile bandwidth | nonnegative strict integer | within configured tunnel bounds | determines requested path capacity |
| setup and hold priorities | strict integers from zero through seven | schema-valid value retained | supplies path-priority intent |

The JSON artifact is read only during application startup. Updating a mounted or local file does not replace the mapper held in <code>app.state.services</code> by an already-running process. Restart the API after a successful configuration review so the lifecycle constructs a new validated <code>PolicyMapper</code>. The readiness endpoint then reflects whether the new artifact was loaded and accepted. This preserves a single, stable policy snapshot for all requests handled by one process lifetime.

<code-block lang="mermaid">
flowchart LR
    Edit["operator edits JSON mapping"] --> Restart["API startup"]
    Restart --> Parse["JSON parse"]
    Parse --> Schema["PolicyFile Pydantic validation"]
    Schema --> Complete["complete-map and bandwidth validation"]
    Complete --> Publish["publish PolicyMapper"]
    Publish --> Ready["ready policy snapshot"]
    Parse --> NotReady["controlled startup failure"]
    Schema --> NotReady
    Complete --> NotReady
</code-block>

### Safe review sequence {id="policy-mapping-safe-review"}

Review the policy document as an artifact with both static and runtime constraints. Begin by preserving the root object and field names shown in the mapping. Change the relevant numeric values as JSON numbers, not strings. Then compare the <code>class_policies</code> keys to the deployed model class domain. A syntactically correct policy that omits a model class will still leave the application unready because the mapper cannot provide a class-specific decision for the full supported domain.

Next, evaluate the default profile separately from class profiles. Keep its bandwidth at zero. For a class policy, choose a requested bandwidth within the deployment bounds described in [Application Construction — Environment Variables](Python-Inference-API-—-Application-Construction-Environment-Variables.md). The Pydantic ranges do not encode those deployment bounds because the values are settings rather than file-schema constants. Setting a priority outside zero through seven, adding an unrecognized object member, or changing an integer to a string fails earlier in strict schema validation.

Finally, restart and inspect readiness before submitting classification traffic. A success indicates that the process has parsed the file, validated its schema, and accepted the complete-map constraints. It does not demonstrate downstream controller application of the returned policy. To inspect selection behavior, submit a classification whose class and confidence exercise the changed profile, then compare the response policy with the expected fallback behavior in [Inference policy mapping](Python-Inference-API-—-Application-Construction-Inference.md#inference-policy-mapping).

<procedure title="Review a policy mapping change" id="review-policy-mapping-change">
    <step>Preserve the three root fields and edit policy values with JSON-native types.</step>
    <step>Confirm that every <code>class_policies</code> key belongs to the model class domain and that none is missing.</step>
    <step>Keep the default profile bandwidth at zero and keep class-profile bandwidths within the configured bounds.</step>
    <step>Restart the API and verify readiness before routing classification traffic to the process.</step>
    <step>Exercise the affected class and confidence case through the inference endpoint.</step>
</procedure>

### Failure interpretation for operators {id="policy-mapping-operator-failures"}

Schema and startup failures identify different correction boundaries. An invalid JSON document, unexpected member, wrong primitive type, out-of-range DSCP, or unsupported version is a policy-file contract failure. An unknown or missing class key is a contract mismatch between the mapping and the model class domain. A class bandwidth below or above the configured limit is a configuration-policy mismatch. In all three cases, correcting the JSON alone may be insufficient if the model metadata or deployment settings remain incompatible.

| Readiness evidence | Likely boundary | Inspect first | Relevant explanation |
|---|---|---|---|
| <code>POLICY_SCHEMA_INVALID</code> | JSON syntax or Pydantic contract | field names, types, ranges, and nested objects | [Policy artifact schema](#policy-artifact-schema) |
| <code>POLICY_VERSION_UNSUPPORTED</code> | runtime/version compatibility | <code>schema_version</code> and deployed API version | [Root policy document](#root-policy-document) |
| <code>POLICY_MAP_INCOMPLETE</code> | mapping and model class mismatch | missing <code>class_policies</code> keys | [Model metadata](Python-Inference-API-—-Application-Construction-Model-Runtime.md#model-metadata) |
| <code>POLICY_CLASS_UNKNOWN</code> | mapping and model class mismatch | unexpected class key | [Complete-map validation](#complete-map-boundary) |
| bandwidth validation error | deployment settings and class profile | configured bounds and class bandwidth | [Environment Variables](Python-Inference-API-—-Application-Construction-Environment-Variables.md) |

The inference and observability pages deliberately avoid duplicating this artifact contract. The inference page describes how a validated mapper contributes a policy response after prediction. The observability page describes the resulting selection and fallback evidence. This page remains the reference for the mapping that system operators edit and for the validation layers that decide whether that mapping can be published.

### File selection at startup {id="policy-mapping-file-selection"}

The artifact name is an application setting, while the JSON content is a policy artifact. By default, <code>CONFIG_DIR</code> resolves to <code>/configs</code> and <code>POLICY_FILENAME</code> resolves to <code>sdn_mpls_ml_traffic_class_to_policy_mapping.json</code>. Startup combines those values before it calls the loader. A deployment can select a different mapping by changing the filename setting or mounting a different file under the configured directory, provided that the resulting document satisfies the schema and complete-map rules already described.

<compare type="left-right" first-title="Default settings" second-title="Artifact path construction">
    <code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_config.py" include-lines="33-46"/>
    <code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_dependencies.py" include-lines="957-966"/>
</compare>

The filename is required after whitespace is trimmed. An empty value fails configuration preflight before the policy artifact is examined. A nonempty filename that does not resolve to a readable file fails artifact validation. The loader then separates malformed JSON from a schema violation. This ordered boundary gives operators a direct diagnostic path from settings, to mount or filesystem visibility, to the content contract.

<code-block lang="mermaid">
flowchart TB
    Settings["CONFIG_DIR and POLICY_FILENAME"] --> Join["Path(config_dir) / policy_filename"]
    Join --> Exists{"readable artifact?"}
    Exists -->|no| ArtifactError["POLICY_FILE_NOT_FOUND or artifact failure"]
    Exists -->|yes| Loader["load_policy_file"]
    Loader --> Valid["validated PolicyFile"]
    Valid --> Mapper["PolicyMapper when mapping is enabled"]
</code-block>

| Change objective | Change boundary | Follow-up verification |
|---|---|---|
| Alter an existing treatment | JSON profile values | restart and exercise the relevant class |
| Supply a versioned alternative mapping | mounted file and <code>POLICY_FILENAME</code> | confirm the resolved artifact and readiness |
| Move configuration storage | <code>CONFIG_DIR</code> and deployment mount | confirm the process can read the resulting path |
| Add a prediction class | model metadata, application class domain, and JSON map | validate all artifacts as one compatibility change |

The filename setting does not loosen the document contract. It changes the artifact selected for validation. The same startup path and readiness boundary apply to the default file and to an operator-specified alternative. For the broader initialization sequence and its readiness ownership, see [Application Construction](Python-Inference-API-—-Application-Construction.md#startup-validation).
