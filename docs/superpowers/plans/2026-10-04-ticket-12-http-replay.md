# Ticket 12 implementation plan: authorized bounded HTTP replay

2026-10-04. Design/TDD plan, not implementation or verification evidence. Starting integration commit: `6a6667269551eae3d46b7c6bf626fe5f9fd101b3`. Product work starts after ticket 11's Linux CI gate; the coordinator owns that gate. ADR-0035 will supersede the HTTP-consumer portions of ADR-0017 while retaining ADR-0034's qualified-record contract.

## Authorized seams and scope

The approved tracker permits tests through actual HTTP, business Modules and facility public interfaces. Use the existing `EmbeddedServletApplication` and a real client for HTTP assertions, including side-effect counters exposed as fixture endpoints. Use a fake clock and barriers at the public claim interface for deterministic lease competition. Do not test the private capture/receipt implementation or compare private field shapes.

The coordinator also approved one explicit host Adapter. It authorizes the current operation and supplies trusted tenant/actor plus the normalized business-command fingerprint on **every** attempt, before claim lookup or replay. It must not execute the business action. Native Principal presence and an earlier security filter are insufficient to prove current resource/method authorization. No JWT parsing or general authorization DSL belongs in this Module.

The Adapter's input is the current request, the resolved HandlerMethod and the finite request-body bytes. Its output is a small validated value. Applications must derive identity from their trusted security context and fingerprint from their validated, normalized command. Examples will specify literal equivalence/non-equivalence vectors: field order and irrelevant formatting, actor/tenant/operation changes, changed business values, credentials and tracing metadata. A host may choose strict byte identity for a binary command, but must publish that normalization rule. The library does not invent business equivalence by sorting arbitrary JSON.

The library owns length/control-character validation, operation identity, unambiguous scope encoding, bounded body access without consuming the controller's input, qualified claim decisions, finite capture, terminal receipt encoding and safe response replay. Full method signature and route/HTTP operation identity prevent overload and cross-route collisions. Application-specific resource identifiers and business parameters must be covered by the normalized fingerprint. No raw key, identity, credentials or body appears in failure logging.

## Planned behavior

1. Non-target requests, downloads and SSE retain the existing streaming path. The filter does not eagerly read every request or allocate every response buffer. Only an explicitly annotated synchronous finite target may be selected. Missing host Adapter/store/capture, disabled required support and invalid configuration reject before business execution.
2. Target selection rejects unsupported async/streaming return contracts before execution. A runtime async escape is rejected and cannot leave a replayable partial result. Multipart/form and nonblocking input require an explicit supported contract; do not silently fingerprint an already-consumed or partial request. The first implementation may reject these target types while passing ordinary requests unchanged.
3. Key and request budgets are checked before claim. Only `Acquired` allows execution. `Processing` and fingerprint conflicts return safe conflict responses; unsupported, expired-result, released, unknown, capacity and closed decisions return safe unavailability. Errors use the ticket 04 shared policy, including explicit legacy-envelope configuration.
4. Response capture remains a streaming tee. Completion must observe the filter's successful exit, not just MVC `afterCompletion`, so a downstream filter write/failure cannot occur after an already-saved incomplete receipt. Resolved advice exceptions, escaped exceptions, capture overflow, unsupported entity transformations, runtime async, I/O failure and incomplete output are ineligible for replay and terminate the qualified record without granting a new execution.
5. Publish a fixed save policy: finite successful 2xx and explicit business-rejection 4xx responses are candidates; 5xx, exception-advice responses and incomplete results are not. Empty responses are valid candidates. Errors before acquisition create no claim. A saved 4xx is still subject to current authorization on every replay. Tests distinguish an explicit business 4xx from an advice-translated exception, including advice returning HTTP 200.
6. Store a bounded, versioned opaque HTTP receipt through `complete(ClaimToken, bytes, retention)`. Decode foreign/malformed/oversized receipts safely. Replay only an explicit header allowlist, preserving necessary `Content-Type` and `Location` for 201. Never replay cookies, authentication, transient tracing, hop-by-hop headers or stale framing headers. A response with unsupported content encoding must be ineligible rather than replaying compressed bytes without their representation metadata.
7. Lease and result retention are separate settings. Keep deprecated constructors/annotation members only with explicit migration semantics; no old `find/tryBegin/complete(String, ...)` fallback. A compliant custom store must retain UNKNOWN after a qualified storage failure. The library cannot turn an adapter that lies about durability into a safe store.
8. ADR-0034's lease exception remains explicit: expiry may grant another owner even while old business work runs. Conditional completion/release only protects the record and cannot stop external effects. Tests must prove late A cannot overwrite B's receipt. Result expiry, oversized results, connection loss and terminal storage failures must not be advertised as automatic permission to execute again. If the HTTP consumer needs an additional terminal operation for the expired-but-not-replaced owner, resolve that narrowly at the existing claim seam with its own RED/GREEN contract; never hide it in a second global key registry.

## Vertical TDD sequence

Each row is a candidate next slice, not a batch of tests to write up front. Record actual RED/GREEN commands and results as work proceeds; reorder when one slice exposes a prerequisite.

| Slice | Observable contract / independent oracle |
| --- | --- |
| 1 | Existing annotated HTTP target without the host Adapter returns safe unavailability and public effect count stays zero. Non-target still responds. |
| 2 | Explicit host Adapter plus qualified store returns a literal first receipt and identical retry, effect count one. Old-only SPI cannot authorize execution. |
| 3 | Current permission withdrawn after first success gives denial on retry; restoring permission replays. A method-authorization fixture establishes that direct MVC replay alone is insufficient. |
| 4 | Same key is isolated by trusted tenant/actor, full overloaded method and route; forged identity headers cannot change trusted scope. |
| 5 | Host normalization literal vectors replay equivalent commands and reject changed business content. Credentials/trace do not change command fingerprint. |
| 6 | Missing/blank/control/duplicate/overlong key and invalid Adapter output fail before business execution; max boundary succeeds. |
| 7 | Request bytes at the limit remain readable by the controller; one byte over, unknown-length oversized input and unsupported media/input modes reject safely. Ordinary large input streams. |
| 8 | Synchronous 201 body and Location, empty 204 and explicit business 4xx replay with the declared header allowlist. Cookies/secrets/transient metadata never enter replay. |
| 9 | Exception advice returning 4xx/5xx/200, unhandled exception and explicit 5xx produce terminal unavailability on retry after the configured retention/lease boundary, not another effect. |
| 10 | Response size at/over budget; unsupported encoding and response/writer reset semantics; no partial receipt. Bytes already sent remain stream-owned. |
| 11 | Actual client disconnect and outbound filter/serialization/completion failures cannot create a replayable partial result; compliant store UNKNOWN policy stays fail-closed. |
| 12 | Known Callable/DeferredResult/stream/SSE targets are rejected, dynamic startAsync escape terminates; unannotated counterparts continue to stream. |
| 13 | Fake-clock A/B real-worker lease race: B's receipt survives A's late completion, including late A failure. Explicitly record remaining business side-effect limitation. |
| 14 | Entry quota charges every attempt; business method quota runs only on execution. Preserve existing rate-limit ordering and native Principal timing. |
| 15 | Actual auto-configuration, custom bean override, disabled/missing optional dependencies, filter order and single registration, two application isolation and close. |
| 16 | Installed ordinary-jar consumer and bounded-heap/key-churn exercise public HTTP contracts; corrupt receipt literals and repeated lifecycle prove resources remain bounded. |

## Migration inventory

The sole product ownerless HTTP completion is `web/idempotency/IdempotencyInterceptor.java`; migrate acquisition, lookup and completion together. Auto-configuration currently constructs its two-argument compatibility constructor. `IdempotencyInterceptorTest` inspects the old namespace and manually constructs old records; replace those behavioral expectations with public qualified/HTTP contracts rather than retaining unsafe expectations. `IdempotencyEndToEndTest`, rate-limit HTTP replay fixture, bounded streaming tests and `verification/json-consumer/.../PlatformWebConsumer.java` require explicit host authorization setup. Test-only legacy SPI compatibility fixtures under `verification/claim-consumer/legacy-api` and `IdempotencyCompatibilityContractTest` intentionally remain old-API consumers, and must not be rewritten into false compatibility evidence.

Do not change ticket 26's Trace/log/MessageSource defaults or template business transaction protocols. Ticket 29 owns durable command/receipt transactions; this HTTP response Adapter does not provide them. Existing independent consumer runner methods must survive final integration.

## Verification and handoff

Store this ticket's raw evidence under `.verification-results/ticket-12` (outside Maven clean). Final evidence must identify source SHA, test counts, runner output, ordinary jar hash and exact environment. Merge the latest integration before the full runner, resolve both source and consumer changes, check conflict markers and `git diff --check`. Use the real Java 21 negative-control home `C:/Users/yiwer/AppData/Local/Temp/server-facility-research-tools/jdk21/jdk-21.0.12.1+1`. A ticket 11 PASS is not ticket 12 evidence. Central ADR index/counts belong to the merger; Linux CI is a separate gate.

## Primary reference

[Spring Security method authorization](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html) describes method authorization through method interceptors. The inference used here is that a response returned directly by MVC preHandle does not invoke that protected method, so a replay consumer needs an explicit current-operation authorization decision of its own.
