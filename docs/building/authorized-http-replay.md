# Migrating annotated HTTP response replay

`@Idempotent` selects a bounded synchronous HTTP operation. It now requires an explicit `IdempotencyAuthorization` bean and the qualified `IdempotencyStore` protocol. The deprecated two-argument interceptor constructor remains linkable but rejects annotated operations because it has no current-authorization Adapter. Disabling the store/filter does not silently execute annotated methods without protection.

## Host authorization and normalization

The host Interface is `authorize(HttpServletRequest, HandlerMethod, byte[]) → Command(tenant, actor, fingerprint)`. It runs on every attempt, before both claim acquisition and receipt replay. It only checks current permission and normalizes the command; it must never perform business side effects. Return identity from the application's trusted security context. Native Principal presence alone does not prove current resource or method permission. Reuse the same host permission policy used by method authorization: MVC replay skips the controller invocation and therefore does not run its method-security advice.

Tenant and actor are each limited to 256 UTF-16 units; fingerprint to 128. Values are nonblank, contain neither controls nor unpaired surrogates, and diagnostic `toString` is redacted. The byte array is a defensive copy; changing it does not modify controller input. The host must include every relevant resource identifier, query/business parameter and normalized body field in its fingerprint. Media type, charset and other representation metadata must also participate whenever they change controller interpretation; they are not automatically incidental transport fields. Credentials, incidental tracing and transport headers normally do not define a business command. Scope additionally binds the concrete controller type, full method signature, HTTP method and request path; the qualified store binds this scope and the client key together.

The structured HTTP fixture defines `{"currency":"USD","amount":7}` and `{ "amount":7, "currency":"USD" }` as equivalent, but amount 8 as different. The binary consumer treats every byte as significant. These are separate published host rules; the library does not claim that arbitrary JSON sorting establishes business equivalence.

[The independent Security consumer](../../verification/http-replay-consumer/src/main/java/example/HttpReplayConsumer.java) is a compiling example using actual Boot Security wrappers, native authenticated identity, a shared current-permission service, `@PreAuthorize`, tenant/actor/route isolation, permission withdrawal and restoration. Its Basic credentials and identity map are loopback test fixtures, not a production credential scheme. Template authentication remains governed by ticket 27; durable business command/receipt transactions belong to ticket 29.

## Filter, body and async boundary

The registration order remains `HIGHEST_PRECEDENCE + 3`, after shared errors (+1) and optional repeatable input (+2). Non-target traffic remains streaming; only a selected finite operation reads a bounded request or allocates a response copy. Host body transformations must run before this capture boundary. Wrappers after capture must use stable standard Servlet request/body delegation. Overrides of body readers or wrapped-request accessors are conservatively refused, and wrapper traversal has a finite depth. The structural check cannot prove arbitrary host wrapper semantics; the host owns stable delegation and agreement between its normalizer and controller binding. Actual standard Security wrappers are covered by the independent consumer.

Selected form/multipart input is unsupported. Framework/container parsing may precede handler selection; its limits remain host responsibilities. This budget covers the library's selected input copy, not arbitrary upstream parsing or body-transform memory. Ordinary upload behavior is unchanged. Known Callable, DeferredResult, CompletionStage, WebAsyncTask, SSE/streaming and reactive return contracts are refused before invocation. Runtime Servlet async escape is rejected; this protocol does not promise partial async replay. An earlier body read or response writer/stream access also prevents safe target selection.

## Budgets, deadlines and receipt policy

For example, configure independent execution and result deadlines:

```yaml
facility:
  idempotency:
    enabled: true
    lease: 30s
    result-retention: 5m
    max-entries: 100000
    max-request-bytes: 1048576
    max-response-bytes: 1048576
    max-stored-receipt-bytes: 67108864
```

Budgets are positive. Durations are positive whole milliseconds. The deprecated `default-ttl` supplies either unset duration (default 5 minutes); an explicit positive `@Idempotent.ttlSeconds` remains a compatibility override for both durations. Zero selects the independent settings. The default local store reserves 8208 bytes beyond each response budget for FHR1 framing and two bounded metadata values; its aggregate stored-byte budget still applies. A custom Store must set compatible receipt budgets and obey the qualified ownership/UNKNOWN contract.

Candidate statuses are 2xx except 206, and explicit business 400/404/409/410/422. Empty responses are allowed. Advice-translated exceptions, even advice returning 200, are never candidates. Other statuses, unsupported content encoding, Content-Range/trailers, oversized metadata, overflow, incomplete output and failures terminate without a receipt. Completion runs after the inner filter chain exits successfully, not merely at MVC completion. Inner body transformations occur once in the stored response; replay is emitted after the inner chain exits and cannot override its current denial.

Only Content-Type and Location are stored, each at most 4096 printable ASCII bytes. Cookies, authentication, private headers, tracing, framing and hop-by-hop state are not copied from the original response. Replay clears stale representation/framing headers and keeps current security headers. The opaque FHR1 format is big-endian magic `0x46485231`, status int, two DataOutput UTF strings (Content-Type then Location, printable ASCII only), body-length int, then exactly that many bytes. Malformed, unsupported or over-budget foreign receipts fail before writing stored data.

Missing/invalid keys are 400; oversized selected input is 413; unsupported selected media is 415. A live owner produces 409 with a positive rounded-up Retry-After; changed fingerprint produces 409. Unsupported, expired-result, released, unknown, closed or capacity decisions produce 503. These errors use the shared HTTP policy. Explicit `facility.web.exception.use-problem-detail=false` selects its legacy envelope, including HTTP 200 for most errors; it never restores ownerless execution or exception-message disclosure.

## Ownership and failure limits

Only a qualified `Acquired` result permits invocation. Replay still checks current authorization. A PROCESSING lease can expire while old business work continues; a replacement owner can then run. Conditional updates protect the receipt, not external side effects. A late old response cannot overwrite a replacement generation. The still-current PROCESSING owner may terminate after lease expiry, but a replaced owner cannot terminate its replacement. This narrowly supersedes ADR-0034's original live-lease requirement for `release`; completion still requires a live lease.

Receipt retention expiry and terminal failure never create another execution permit. Local terminal bindings remain until store close; a full table returns unavailable instead of evicting a binding and reauthorizing the command. This consumes finite capacity. Process loss or deliberately replacing the store loses local state; use business uniqueness and the ticket 29 transaction/receipt protocol when durable effect guarantees are required.

Streaming output can reach the client before receipt storage finishes. A committed response followed by a Store exception may end with a connection/final-chunk failure; no complete success response is promised. The original exception is preserved, and compliant Store failure retains UNKNOWN. The existing HTTP boundary rethrows committed failures, so a container can log a host Store's exception message/cause. Ticket 26 confines facility-emitted diagnostics; it does not sanitize arbitrary container or host appenders. Store adapters must produce bounded safe errors without SQL, credentials or sensitive payloads in message/cause. The `PRIVATE-COMPLETION` fixture and this boundary are recorded for ticket 33/final review.

All repository HTTP consumers migrate acquisition, lookup and completion together. Deprecated ownerless SPI methods remain only for explicitly isolated compatibility consumers; do not mix namespaces for a command. See [ADR-0035](../adr/0035-authorized-bounded-http-replay.md) and the [ticket evidence](../verification/ticket-12-authorized-replay.md) for exact acceptance status and limitations.
