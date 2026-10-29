# Application Construction — Middleware

<show-structure for="chapter" depth="3"/>

The application middleware package establishes two process-wide HTTP transport
invariants before FastAPI selects a route or validates a request model. Every
HTTP response is associated with a server-created request identifier, and no
request body exceeding the configured byte limit reaches FastAPI. Both concerns
are implemented as plain ASGI callables, which lets them observe `scope`,
`receive`, and `send` directly instead of relying on endpoint-specific code.

This page documents the package at
<path>src/main/python/api/app/middleware</path>. It follows the exact control
flow used by the installed application, including short-circuit responses and
the final exception barrier. It does not assign behavior to a middleware where
the source delegates to FastAPI, the central handlers, or an endpoint.

## Package boundary and registration {id="middleware-package-boundary"}

The package initializer is a deliberately small public boundary. It exports the
two classes consumed by the application composition root. It does not configure
middleware, create state, or perform request processing on import.

<code-block lang="python" src="../../src/main/python/api/app/middleware/__init__.py" include-lines="1-11"/>

The application creates its middleware list in source order: correlation,
followed by request-size validation. FastAPI receives that list while the app is
constructed. The repository test asserts the corresponding `user_middleware`
order, and the correlation middleware's forwarded-request event identifies its
next ASGI layer as `request_size_limit`.

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_main.py" include-lines="148-170"/>

<code-block lang="mermaid">
flowchart TB
    Server[ASGI server] --> Correlation[CorrelationIdMiddleware]
    Correlation --> Size[RequestSizeLimitMiddleware]
    Size --> FastAPI[FastAPI routing and validation]
    FastAPI --> Endpoint[Selected endpoint]
    Endpoint --> FastAPI
    FastAPI --> Size
    Size --> Correlation
    Correlation --> Caller[HTTP caller]
</code-block>

The arrows returning from the endpoint represent ASGI response messages, not a
second request. The two wrappers remain active while downstream code emits
`http.response.start` and `http.response.body`, which is why a response created
by size validation or a central handler can still receive the correlation
header.

| Package member | Defined responsibility | Direct consumer | Important exclusion |
|---|---|---|---|
| `__init__.py` | Re-exports both middleware classes | application main module | It is not an ASGI layer |
| `CorrelationIdMiddleware` | Server request ID, response-header injection, final fallback | configured middleware list | It does not accept a caller ID |
| `RequestSizeLimitMiddleware` | Header check, streamed-byte check, replay, rejection telemetry | configured middleware list | It does not parse JSON or Pydantic models |

### ASGI inputs and ownership {id="middleware-asgi-inputs"}

Each middleware receives the standard ASGI triplet. `scope` is the shared
dictionary that contains the protocol type, headers, route-independent request
metadata, application object, and a mutable state dictionary. `receive` is an
awaitable source of inbound messages. `send` is an awaitable sink for outbound
messages. The size middleware consumes inbound request messages, so it must give
the next application a replacement `receive` callable after acceptance.

<code-block lang="mermaid">
sequenceDiagram
    participant U as ASGI server
    participant C as Correlation middleware
    participant S as Size middleware
    participant F as FastAPI
    U->>C: scope, receive, send
    C->>C: writes scope.state.request_id
    C->>S: scope, receive, wrapped send
    S->>S: consumes receive messages
    S->>F: scope, replay receive, traced send
    F-->>S: response ASGI messages
    S-->>C: response ASGI messages
    C-->>U: response start and body
</code-block>

The diagram does not imply that a body is always present. For an empty HTTP
body, the size middleware still finishes its loop on an `http.request` message
with `more_body` false, builds a replay callable for the empty byte string, and
passes that callable onward.

## Correlation middleware {id="correlation-middleware"}

`CorrelationIdMiddleware` is the outer application HTTP layer. Its constants
name `X-Request-ID` as the public response header and define the structured
attributes used only when an otherwise unhandled exception reaches this layer.
The implementation imports the common error-response builder and the request
error metric recorder rather than serializing a separate fallback shape.

<code-block lang="python" src="../../src/main/python/api/app/middleware/sdn_mpls_ml_correlation_middleware.py" include-lines="18-40"/>

### Scope-type branch {id="correlation-scope-type"}

The first branch is protocol selection. A scope whose type is not `http` is
passed unchanged to the wrapped application with the original `receive` and
`send`. No UUID is created, no state is populated, and no HTTP response header
is inserted. This avoids imposing HTTP response semantics on another ASGI
protocol such as a lifespan or WebSocket scope.

<code-block lang="python" src="../../src/main/python/api/app/middleware/sdn_mpls_ml_correlation_middleware.py" include-lines="92-100"/>

| Scope type | Correlation action | State mutation | Header action |
|---|---|---|---|
| `http` | creates one UUID4 string | stores `request_id` | wraps outbound response start |
| any other type | delegates immediately | none | none |

This is a pass-through rule rather than an error rule. A non-HTTP scope is not
malformed from the middleware's perspective, and the downstream ASGI
application remains responsible for its protocol-specific behavior.

### Authoritative request-ID creation {id="authoritative-request-id"}

For an HTTP scope, the middleware always calls `uuid.uuid4()` and converts that
value to a string. It initializes `scope["state"]` when necessary and writes
the value under `request_id`. There is no branch that reads an inbound
`X-Request-ID` header, a JSON field, or any other caller-provided candidate.
The server therefore owns the correlation value for each HTTP request.

<code-block lang="python" src="../../src/main/python/api/app/middleware/sdn_mpls_ml_correlation_middleware.py" include-lines="97-109"/>

<code-block lang="mermaid">
flowchart TD
    Incoming[HTTP scope] --> NewId[uuid.uuid4 string]
    NewId --> State[scope state request_id]
    State --> FastAPIState[FastAPI Request.state]
    FastAPIState --> RouteHelper[get_request_id request helper]
    NewId --> ResponseHeader[X-Request-ID response header]
    RouteHelper --> SuccessBody[success response request_id]
    FastAPIState --> ErrorHandler[central error handler]
    ErrorHandler --> ErrorBody[error response request_id]
</code-block>

The state write happens before the next middleware, routing, validation, or
endpoint code. Consequently, a size rejection that occurs immediately after
the correlation layer can include the same ID in its error body and receive the
same ID in its header when the response start passes back through this wrapper.

### Response-start header injection {id="correlation-response-header"}

The wrapper examines only `http.response.start` messages. It copies the message
header collection, compares names case-insensitively to the lower-case byte
form of `X-Request-ID`, and appends the generated ASCII value only when no such
header already exists. The source preserves a downstream-supplied header of
that name instead of adding a duplicate.

<code-block lang="python" src="../../src/main/python/api/app/middleware/sdn_mpls_ml_correlation_middleware.py" include-lines="111-154"/>

| Outbound ASGI message | Wrapper behavior | Reason |
|---|---|---|
| `http.response.start`, no request-ID header | appends lower-case byte header | exposes the generated ID |
| `http.response.start`, header present in any case | leaves existing header unchanged | prevents duplicate names |
| `http.response.body` | logs fragment size and `more_body` | preserves streaming observability |
| another message type | forwards it without header logic | only response start owns headers |

The header insertion is deliberately late in the response path. It does not
depend on an endpoint returning a particular response class, and it covers
responses emitted by the size middleware as well as FastAPI's handlers.

### Forwarding and last fallback {id="correlation-forwarding-fallback"}

After logging the handoff, the correlation layer calls the wrapped application
with the original inbound `receive` and its wrapped outbound `send`. A broad
exception handler surrounds that call. It is the final fallback for exceptions
that were not converted into a response downstream, not the normal path for
application errors that FastAPI's registered handlers already handle.

<code-block lang="python" src="../../src/main/python/api/app/middleware/sdn_mpls_ml_correlation_middleware.py" include-lines="156-194"/>

<code-block lang="mermaid">
flowchart TD
    Enter[HTTP request already correlated] --> Downstream[call next ASGI app]
    Downstream --> Returned{returns normally?}
    Returned -- yes --> Done[response already forwarded]
    Returned -- no --> Metric[record INTERNAL_ERROR metric when enabled]
    Metric --> Log[emit unhandled_exception structured log]
    Log --> Fallback[build 500 INTERNAL_ERROR response]
    Fallback --> Header[wrapped send adds X-Request-ID]
    Header --> Done
</code-block>

The fallback passes the generated request ID and fixed component, stage, check,
and retryability values to `build_error_response`. Its response therefore uses
the shared error schema rather than a middleware-only body. The metric helper
enables the request-error counter only when application services expose
validated settings with Prometheus enabled.

<code-block lang="python" src="../../src/main/python/api/app/middleware/sdn_mpls_ml_correlation_middleware.py" include-lines="197-247"/>

### Safe correlation logs {id="correlation-safe-logs"}

Correlation debug logs describe transport metadata without serializing all
headers. The request metadata selects method, path, HTTP version, and only
`content-type`, `content-length`, `transfer-encoding`, and `accept`. The same
allowlist is used for response headers observed at response start.

<code-block lang="python" src="../../src/main/python/api/app/middleware/sdn_mpls_ml_correlation_middleware.py" include-lines="250-268"/>

This selection is important because arbitrary headers can carry credentials or
other sensitive caller data. The code does not claim that the four selected
header values are always non-sensitive. It confines this middleware's logging
surface to values relevant to HTTP body parsing and content negotiation.

## Request-size middleware {id="request-size-middleware"}

`RequestSizeLimitMiddleware` is the inner HTTP layer. It validates a declared
`Content-Length` when one exists, counts the actual bytes delivered through
ASGI, and only then invokes FastAPI with an equivalent single-message body. It
does not trust a small declared value as proof that the stream is small.

<code-block lang="python" src="../../src/main/python/api/app/middleware/sdn_mpls_ml_request_size_validation_middleware.py" include-lines="14-32"/>

### Limit and correlation lookup {id="request-size-lookups"}

For an HTTP scope, the middleware reads `request_size_limit_bytes` from
`scope["app"].state.services` and obtains the previously written request ID
from `scope["state"]`. The limit is part of application services published by
the lifespan, not a per-route constant and not a number parsed by this package.

<code-block lang="python" src="../../src/main/python/api/app/middleware/sdn_mpls_ml_request_size_validation_middleware.py" include-lines="324-363"/>

The source assumes that its HTTP invocation has an application and initialized
services. That is consistent with its registration on the constructed FastAPI
app after lifespan initialization. Its helper returns `None` for a missing
request ID rather than manufacturing one, because creation belongs exclusively
to the outer correlation middleware.

### Content-Length parser {id="content-length-parser"}

The parser scans ASGI's byte header list for the exact normalized key
`b"content-length"`. It decodes the value as ASCII, trims whitespace, converts
it to an integer, and rejects decoding failures, nonnumeric values, and negative
numbers through a private sentinel. If the header is absent, it returns `None`.

<code-block lang="python" src="../../src/main/python/api/app/middleware/sdn_mpls_ml_request_size_validation_middleware.py" include-lines="409-446"/>

| Header condition | Parser result | Next main branch |
|---|---|---|
| absent | `None` | inspect actual ASGI bytes |
| ASCII nonnegative integer | integer | compare declared size, then inspect bytes |
| invalid ASCII | private invalid sentinel | controlled 400 |
| nonnumeric string | private invalid sentinel | controlled 400 |
| negative integer | private invalid sentinel | controlled 400 |

The parser has no branch that checks whether a declared size equals the final
streamed byte count. The final validated-body log records whether those values
match when a declaration exists, but acceptance is determined by the configured
limit and the observed byte counter.

### Early declaration branches {id="request-size-declaration-branches"}

The main callable passes non-HTTP traffic unchanged, then obtains the limit,
request ID, and parsed declaration. It writes an initial structured event before
making either early rejection decision. The metadata includes the selected
request headers, declared integer when applicable, and effective byte limit.

<code-block lang="python" src="../../src/main/python/api/app/middleware/sdn_mpls_ml_request_size_validation_middleware.py" include-lines="69-111"/>

An invalid declaration records the `invalid_content_length` rejection reason,
logs a warning with `InvalidContentLengthError` attributes, builds a shared
error response, sends it directly, and returns without calling FastAPI. Its
status is 400. A declaration larger than the limit follows the same short-
circuit structure with `RequestTooLargeError`, reason `declared_size_exceeded`,
and status 413.

<code-block lang="python" src="../../src/main/python/api/app/middleware/sdn_mpls_ml_request_size_validation_middleware.py" include-lines="113-180"/>

<code-block lang="mermaid">
flowchart TD
    Start[HTTP scope] --> Parse[parse Content-Length]
    Parse --> Invalid{invalid sentinel?}
    Invalid -- yes --> Bad[record invalid_content_length]
    Bad --> Error400[shared 400 error response]
    Invalid -- no --> Declared{integer greater than limit?}
    Declared -- yes --> Large[record declared_size_exceeded]
    Large --> Error413[shared 413 error response]
    Declared -- no --> Stream[consume and count ASGI body]
</code-block>

Neither early branch reads the body before replying. That is why a declared
oversize request can be rejected before a route, JSON parser, or Pydantic model
observes the request. Both branches use the original `send`, which is the
correlation wrapper supplied by the outer middleware, so their response start
receives `X-Request-ID`.

### Streamed-byte enforcement {id="streamed-byte-enforcement"}

When the declaration is absent, valid, or within the limit, the middleware reads
messages until it sees a final request chunk or a disconnect. It accumulates
only `http.request` bodies in a `bytearray`, tracks the received byte count and
chunk count, and ignores other non-disconnect message types while continuing to
read. A disconnect logs the bytes observed so far and ends the loop.

<code-block lang="python" src="../../src/main/python/api/app/middleware/sdn_mpls_ml_request_size_validation_middleware.py" include-lines="182-243"/>

The decisive test is `received_bytes > max_body_bytes`. Equality is accepted by
this middleware. When the counter crosses the limit, it records
`actual_size_exceeded`, logs a warning using `RequestTooLargeError`, sends the
same structured 413 family, and returns immediately. A false or misleading
`Content-Length` therefore cannot bypass actual-body enforcement.

<code-block lang="python" src="../../src/main/python/api/app/middleware/sdn_mpls_ml_request_size_validation_middleware.py" include-lines="243-264"/>

<code-block lang="mermaid">
sequenceDiagram
    participant Client
    participant Size as Size middleware
    participant FastAPI
    Client->>Size: http.request chunk 1
    Size->>Size: append and count
    alt bytes exceed limit
        Size-->>Client: 413 shared error response
    else more_body true
        Client->>Size: next http.request chunk
        Size->>Size: append and count
    else final chunk
        Size->>FastAPI: one replayed http.request body
    end
</code-block>

The size layer may already have consumed a portion of an oversized stream when
it returns 413. Its guarantee is that no bytes after the branch are handed to
the wrapped FastAPI application. It does not promise to drain the remaining
network stream or to validate a JSON payload after the byte limit fails.

### Accepted-body replay {id="accepted-body-replay"}

After a non-rejected loop, the middleware converts the buffer to immutable
bytes, logs a body-validation event, constructs a replay callable, and calls
the wrapped application. The log records a SHA-256 digest, the byte count,
chunk count, declared length when present, whether final bytes matched that
declaration, and the next ASGI layer name.

<code-block lang="python" src="../../src/main/python/api/app/middleware/sdn_mpls_ml_request_size_validation_middleware.py" include-lines="267-316"/>

The replay callable first returns exactly one `http.request` message containing
the complete validated byte string and `more_body` false. Every later call
returns `http.disconnect`. It therefore does not replay the original chunk
boundaries, non-request messages, or an original disconnect that the middleware
already consumed.

<code-block lang="python" src="../../src/main/python/api/app/middleware/sdn_mpls_ml_request_size_validation_middleware.py" include-lines="448-524"/>

| Replay-call state | Returned message | Observable consequence |
|---|---|---|
| first call | one complete `http.request`, `more_body=false` | FastAPI receives the accepted body |
| later call | `http.disconnect` | no second body delivery |
| original transport chunks | not replayed individually | inner code receives normalized content |

The repository's direct-ASGI tests verify that a fragmented valid JSON body is
replayed to Pydantic and produces a successful classification response. They
also verify 413 for a missing declaration with an oversized streamed body and
for a falsely small declaration followed by oversized bytes.

### Response tracing and safe headers {id="request-size-response-tracing"}

For an accepted request, `send_with_trace` logs each response message before
forwarding it to the outer correlation middleware. Its metadata distinguishes
message type, response status, body fragment size, and `more_body`; it does not
modify response contents or headers.

<code-block lang="python" src="../../src/main/python/api/app/middleware/sdn_mpls_ml_request_size_validation_middleware.py" include-lines="294-316"/>

The request-size logging helper emits structured records with service name,
event, request ID, component, failure fields, error code, and bounded metadata.
Its selected-header helper uses the same four-header allowlist as the correlation
layer: content type, content length, transfer encoding, and accept.

<code-block lang="python" src="../../src/main/python/api/app/middleware/sdn_mpls_ml_request_size_validation_middleware.py" include-lines="526-587"/>

## Rejection telemetry and error contracts {id="middleware-errors-telemetry"}

The size middleware calls `_record_rejection` for each of its three rejection
reasons. The helper obtains validated settings through application state, only
enables metrics when Prometheus is enabled, increments the body-rejection
metric by reason, and records the matching request error. It intentionally does
not invoke the central HTTP handler, because the middleware is already emitting
the response itself.

<code-block lang="python" src="../../src/main/python/api/app/middleware/sdn_mpls_ml_request_size_validation_middleware.py" include-lines="380-407"/>

| Branch | HTTP status | Error code | Rejection reason | Route reached? |
|---|---:|---|---|---|
| invalid `Content-Length` | 400 | `INVALID_CONTENT_LENGTH` | `invalid_content_length` | no |
| declared size exceeds limit | 413 | `REQUEST_TOO_LARGE` | `declared_size_exceeded` | no |
| actual received size exceeds limit | 413 | `REQUEST_TOO_LARGE` | `actual_size_exceeded` | no |
| downstream exception escaping handlers | 500 | `INTERNAL_ERROR` | not a body-rejection reason | no further route work |

The shared builder serializes `ErrorResponse`, containing an optional
`request_id` and an `ErrorBody` with code, message, component, failed stage,
failed check, and retryability. It optionally adds details and returns a
`JSONResponse` with the requested status. The error schema documents the
transport correlation relationship: in normal HTTP flows the body request ID
should match the `X-Request-ID` header.

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_http_responses.py" include-lines="15-66"/>

<code-block lang="python" src="../../src/main/python/api/app/schemas/sdn_mpls_ml_baseline_validation_models.py" include-lines="35-70"/>

<code-block lang="mermaid">
classDiagram
    class ErrorResponse {
        request_id: string or null
        error: ErrorBody
    }
    class ErrorBody {
        code: string
        message: string
        component: string or null
        failed_stage: string or null
        failed_check: string or null
        retryable: boolean or null
    }
    ErrorResponse --> ErrorBody
</code-block>

## Consumers and handler interaction {id="middleware-consumers"}

The middleware package has three principal groups of consumers. The main module
registers both classes. Route code uses the `get_request_id` helper after
FastAPI represents the shared scope state as `Request.state`. Central handlers
read the state through their own defensive helper when an exception does not
already carry a request ID. Tests invoke the application through a controlled
ASGI harness to examine headers, body shape, and size branches.

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_request_context.py" include-lines="18-44"/>

The route helper raises `RuntimeError` if correlation did not initialize the
state. That is an invariant check, not a fallback ID generator. In the normal
registered stack, the outer middleware runs before route code and satisfies the
invariant. If the helper is used outside that HTTP path, the source makes the
missing middleware visible instead of silently emitting an unrelated ID.

<code-block lang="python" src="../../src/main/python/api/app/sdn_mpls_ml_main.py" include-lines="173-250"/>

The main module's `_error_response` delegates to the exact same builder used by
both middleware classes. Its `_request_id_from_request` helper returns `None`
when state is absent, which lets a central handler preserve a controlled
exception ID when available or use the state-derived ID when the normal HTTP
path supplied it.

<code-block lang="mermaid">
flowchart TD
    Correlation[Correlation middleware] --> ScopeState[scope state]
    ScopeState --> FastAPIRequest[Request.state]
    FastAPIRequest --> ContextHelper[get_request_id]
    FastAPIRequest --> MainHelper[_request_id_from_request]
    ContextHelper --> Routes[health and inference routes]
    MainHelper --> Handlers[central handlers]
    Size[Size middleware] --> SharedBuilder[build_error_response]
    Handlers --> SharedBuilder
    Correlation --> SharedBuilder
</code-block>

### Endpoint and validation boundary {id="middleware-endpoint-boundary"}

The size middleware runs before FastAPI route selection and Pydantic request
validation. A rejected body never reaches an endpoint. An accepted body is
replayed to FastAPI, which then performs its own JSON parsing, request-model
validation, dependency resolution, and route-specific logic. Those operations
can produce different errors, but they are outside the byte-limit decision.

| Layer | Owns | Does not own |
|---|---|---|
| correlation middleware | server ID creation and response header insertion | caller ID acceptance or route selection |
| size middleware | declared and actual byte limit | JSON syntax or packet-feature validation |
| FastAPI validation | request parsing and schema checks | body stream replay implementation |
| central handlers | normalized downstream exception responses | early size-rejection decision |
| endpoint | service operation and business result | transport ID generation |

The application composition root registers the routers after construction. This
does not change middleware precedence. A route's `Request` object still carries
the state established before FastAPI resolves that router, so success and
handled-error paths can reuse the generated transport identifier.

## Complete branch matrix {id="middleware-branch-matrix"}

The following matrix brings the package's meaningful branches together. It is a
summary of source flow, not an additional policy layer.

| Entry or event | Correlation result | Size result | Downstream result |
|---|---|---|---|
| non-HTTP scope | direct pass-through | direct pass-through if reached | protocol owner decides |
| HTTP request begins | UUID stored in scope state | reads limit and declaration | no route yet |
| invalid declaration | ID exists | 400 short circuit, metrics and warning | FastAPI not called |
| declared body larger than limit | ID exists | 413 short circuit, metrics and warning | FastAPI not called |
| streamed bytes cross limit | ID exists | 413 short circuit, metrics and warning | FastAPI not called |
| stream ends within limit | ID exists | creates single-message replay | FastAPI receives body |
| disconnect before final request body | ID exists | logs disconnect, replays accumulated body | FastAPI receives replay |
| accepted downstream response start | injects header if absent | logs and forwards | caller receives response |
| accepted downstream body fragment | logs and forwards | logs and forwards | caller receives bytes |
| unhandled downstream exception | records final error and emits 500 | exception has escaped inner work | caller receives shared fallback |

The disconnect row follows the current source literally. The loop breaks on an
`http.disconnect`, then builds a replay from accumulated bytes. The middleware
does not create a separate disconnect error response. Any behavior that FastAPI
then derives from the replayed body belongs to the downstream framework.

## Operational states {id="middleware-operational-states"}

The middleware behavior can also be read as a compact state machine. The state
machine describes one HTTP request. It deliberately omits non-HTTP scopes,
which leave the machine through immediate delegation.

<code-block lang="mermaid">
stateDiagram-v2
    [*] --> Correlated
    Correlated --> DeclarationChecked
    DeclarationChecked --> Rejected400: invalid declaration
    DeclarationChecked --> Rejected413: declared exceeds limit
    DeclarationChecked --> Reading: declaration absent or acceptable
    Reading --> Reading: request chunk and bytes within limit
    Reading --> Rejected413: accumulated bytes exceed limit
    Reading --> Buffered: final chunk or disconnect
    Buffered --> Replaying
    Replaying --> Downstream
    Downstream --> Completed: response forwarded
    Downstream --> Fallback500: exception escapes
    Rejected400 --> Completed
    Rejected413 --> Completed
    Fallback500 --> Completed
</code-block>

`Completed` means the middleware has either forwarded response messages or
invoked an ASGI response object that emitted them. It does not imply that the
caller successfully consumed the network response, nor does it make a claim
about server shutdown or process readiness.

## Source excerpts index {id="middleware-source-index"}

Every code excerpt on this page references maintained repository source. The
index helps readers find the implementation region associated with each
discussion without duplicating source as unmanaged documentation.

| Source file | Included implementation areas | Documented concern |
|---|---|---|
| `middleware/__init__.py` | lines 1-11 | public exports |
| `sdn_mpls_ml_correlation_middleware.py` | lines 18-268 | correlation, forwarding, fallback, metadata |
| `sdn_mpls_ml_request_size_validation_middleware.py` | lines 14-590 | limit, parsing, stream enforcement, replay, telemetry |
| `sdn_mpls_ml_main.py` | lines 148-250 | registration and common handler helpers |
| `sdn_mpls_ml_http_responses.py` | lines 15-66 | shared error serialization |
| `schemas/sdn_mpls_ml_baseline_validation_models.py` | lines 35-70 | error response model |
| `sdn_mpls_ml_request_context.py` | lines 18-44 | route request-ID consumer |

The source is the authority for exact log messages, constant values, and
implementation details. The diagrams on this page are self-contained
representations of the package's relationships and avoid relying on diagrams in
<path>docs/python-api</path>.

## Structured event catalog {id="middleware-event-catalog"}

Middleware logs use stable event strings that identify the stage of an ASGI
request. The catalog below records the names in source and the conditions that
cause them. It does not claim a particular logging handler, log level filter, or
external collector configuration.

| Event | Layer | Condition | Key bounded metadata |
|---|---|---|---|
| `correlation_request_received` | correlation | HTTP ID has been stored | ASGI layer and selected request metadata |
| `correlation_request_forwarded` | correlation | next application is invoked | next layer name |
| `correlation_response_start_forwarded` | correlation | response start observed | status and selected response headers |
| `correlation_response_body_forwarded` | correlation | response body fragment observed | fragment bytes and `more_body` |
| `unhandled_exception` | correlation | downstream exception escapes | fallback component, stage, check, status |
| `request_size_validation_started` | size | HTTP inspection begins | declaration, limit, method, path, selected headers |
| `invalid_content_length` | size | parser returns invalid sentinel | invalid-content-length error attributes |
| `request_too_large` | size | declared or actual limit branch | request-too-large error attributes |
| `request_size_client_disconnected` | size | inbound disconnect observed | received byte count |
| `request_size_body_chunk_received` | size | request chunk accepted into buffer | index, chunk bytes, total bytes, limit |
| `request_size_body_validated` | size | read loop completes without oversize | body digest, counts, declaration comparison |
| `request_size_response_forwarded` | size | downstream emits response message | message type, status, body bytes |
| `request_size_body_replayed` | size | first replay receive call | normalized body digest and bytes |
| `request_size_replay_exhausted` | size | later replay receive call | disconnect message type |

All listed size events are passed through `_log_middleware_event`, which provides
the common service, event, request-ID, component, diagnostic, error-code, and
metadata fields. The correlation layer uses its own small helper for common
debug fields and constructs the fallback error event directly.

### Event correlation fields {id="middleware-event-fields"}

The request identifier is carried in both middleware logging helpers for HTTP
paths. Size logs use the state-derived value, while correlation logs use the
value generated in the outer layer. The two are the same value when registration
order is preserved.

The service field is derived defensively from scope application state. If the
app or its services are unavailable, the helper returns `None` rather than
raising while attempting to log an event. If validated settings exist, the
helper uses their application name; otherwise it uses raw-settings application
name.

| Field | Correlation source | Size source | Purpose |
|---|---|---|---|
| `service` | scope service-name helper | scope service-name helper | identifies configured application when available |
| `event` | fixed event supplied at call site | fixed event supplied at call site | identifies middleware transition |
| `request_id` | freshly generated UUID | state lookup | correlates one HTTP flow |
| `component` | `http_middleware` or fallback component | supplied error or middleware component | identifies emitting concern |
| `metadata` | selected transport data | selected transport data and byte facts | adds bounded diagnostics |

