# ADR-0035: Authorized and bounded HTTP response replay

## Status

Accepted — implementation and incremental public-seam evidence recorded; final platform gate status is tracked separately in the ticket report.

日期：2026-10-04。

替代 ADR-0017 的 HTTP consumer 作用域、旧 ownerless 完成路径与“完整幂等”表述；保留 ADR-0028 的普通流直通原则与 ADR-0034 的 qualified claim 主体契约。仅就 release 部分替代 ADR-0034：过期但仍为当前 PROCESSING owner 可以终止；被替换 owner 不能更新新 generation。

## Context

The existing HTTP interceptor finds a response by the unqualified client key and returns it from MVC `preHandle`. That bypasses the controller invocation, including method authorization attached to that invocation. A previously authenticated request or native Principal does not establish current resource permission. A key also cannot by itself isolate tenants, actors, routes or overloaded operations.

Ticket 11 provides qualified claims, conditional receipt updates and permanent terminal bindings. Ticket 05 makes ordinary HTTP output stream through a bounded optional tee. Their combination still needs an explicit current-authorization decision, a business-content equivalence rule, a response save policy and an end-of-filter completion point. The old HTTP `complete(String, ...)` cannot provide ownership.

Spring method security applies through method interceptors. The inference for an MVC replay is that returning before method invocation omits those checks. Re-running an arbitrary protected method to obtain authorization would execute business work, so the library must not attempt that. A separate small host Adapter is the explicit trade-off: the application supplies current operation authorization and normalization once, and the library centralizes the HTTP/claim machinery.

## Decision

### Current authorization and business identity

Introduce one required host Interface taking the current request, resolved HandlerMethod and bounded request bytes, returning trusted tenant/actor and a bounded canonical command fingerprint. The Adapter performs authorization and normalization only; it must not execute business side effects. It runs before both acquisition and replay, on every request. Missing support and invalid output reject the annotated operation before execution. The library does not infer trust from arbitrary headers, ThreadLocal compatibility identity or JWT content.

The host publishes its command normalization rule. It includes relevant resource identifiers and business input and excludes credentials, trace identifiers and incidental transport metadata. Two fixture Adapters demonstrate normalized structured commands and strict binary commands. The library validates the result and derives an unambiguous operation scope using full handler signature and route/method identity. No generic JSON sorting policy claims equivalence for all business domains.

### Finite HTTP selection

Only explicit synchronous finite targets are supported. Ordinary requests/downloads/SSE remain pass-through. Request buffering starts only after an annotated target is selected, has a positive byte budget, and preserves the complete bytes for controller binding. Unsupported asynchronous, multipart/form or nonblocking target input/output is rejected explicitly, rather than capturing an incomplete request/response. Runtime async escape is also a failure.

Body transformations must run before the idempotency capture filter (`HIGHEST_PRECEDENCE + 3`). Wrappers after that boundary must use stable standard Servlet request/body delegation. The annotated path rejects overrides of body readers or the wrapped-request accessor, and bounds wrapper traversal. This structural check is a conservative compatibility guard, not proof of arbitrary wrapper semantics. A host remains responsible for stable wrapper behavior and for matching the authorization normalization to controller binding. Ordinary unannotated requests retain their original streaming wrapper behavior. Real HTTP tests cover transformations on both sides of the boundary; the installed-jar Security consumer separately verifies the standard Security wrapper chain.

Only an `Acquired` qualified claim allows the controller to run. The compatibility constructor no longer restores old ownerless behavior. Store/provider disablement cannot silently turn an annotated operation into unprotected execution. Errors use the shared ticket 04 HTTP policy, including its explicit legacy response configuration.

### Receipt and failure policy

Completion observes successful filter exit after MVC completion. Candidate statuses are 2xx except 206 and explicitly returned 400/404/409/410/422 business rejections. Empty bodies are allowed; declared Content-Length must agree with captured bytes. Advice-translated exceptions (even HTTP 200), 5xx, unsupported representation transformations, incomplete/oversized output, async escape and I/O failure terminate without a replayable receipt. Safe headers are an explicit allowlist; 201 preserves Location. Cookies, auth, transient tracing, framing and hop-by-hop headers are never replayed. Unsupported content encoding makes a response ineligible rather than stripping its encoding header from compressed bytes.

The HTTP receipt is opaque to the claim store, versioned and bounded. Receipt decoding validates its complete structure before writing any response. Lease and result retention are distinct settings. Expired results and terminal failures do not create a new execution permit. A qualified storage failure must retain UNKNOWN according to the store contract. Misbehaving third-party stores cannot acquire safe semantics merely by implementing an interface.

Narrow claim adjustment for the HTTP failure case: `release(token)` may permanently terminate the same still-current PROCESSING owner even after its lease expires. It does not acquire, renew or complete business work. Owner/generation mismatches and terminal states remain rejected. This allows a failure observed after the lease to remove future retry permission while that owner is still current; it cannot overwrite a replacement owner. `complete` still requires a live lease. Public worker-barrier claim tests and real HTTP late-owner scenarios prove both termination-before-replacement and rejection-after-replacement schedules.

### Scope and migration

This decision governs `web.idempotency`, its auto-configuration/properties and HTTP consumers. Migrate the whole HTTP acquisition/lookup/completion path together. Retain deprecated external ownerless SPI methods solely for compatibility; their explicit legacy compatibility tests remain. Replace HTTP tests that inspect the old namespace with consumer behavior tests.

This is local HTTP response replay. It does not atomically commit business state with the receipt, persist across process loss, stop an expired owner's side effects or provide cross-system exactly-once. Ticket 29 owns the business database command/receipt protocol. A new owner may already have been granted under ADR-0034's lease exception before the old failure is observed; the library cannot undo that grant or those effects.

## Consequences

**Positive**:

- Replays obey current host authorization and are isolated by trusted identity and operation.
- Normal traffic remains streaming; selected request and receipt memory has explicit positive budgets.
- Conditional terminal updates prevent late responses and known capture failures from silently restoring old ownerless retry behavior.
- One small host Adapter hides the library's HTTP capture, receipt format and claim coordination from application code.

**Negative**:

- Existing annotated applications need an explicit host Adapter and must publish normalization/authorization rules.
- Finite response replay excludes async/streaming targets and does not substitute for transactional business receipts.
- Permanent local terminal bindings consume bounded capacity until the store closes; capacity exhaustion is an explicit refusal.
- A client may receive a business response before later receipt storage fails; streaming delivery cannot promise the outcome of a later storage operation. A committed Store exception may truncate the final network frame and be logged by the container. Facility diagnostics do not sanitize arbitrary host/container appenders; Store adapters must supply bounded safe exception messages and causes.

**Carry-forward**:

- Ticket 29/30 supplies durable transactional command/receipt behavior and recovery. Their future evidence is not a prerequisite for the local HTTP contract, and this ticket does not claim their guarantees.
- Ticket 33 repeats the J06/J07/J08 combinations against one release candidate and both supported operating systems.

## References

1. [Ticket 12 plan and TDD sequence](../superpowers/plans/2026-10-04-ticket-12-http-replay.md).
2. [Qualified legacy claims](0034-qualified-legacy-claims.md).
3. [Bounded web streams](0028-bounded-web-streams.md).
4. [Spring Security method authorization](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html).
5. [Migration contract](../building/authorized-http-replay.md) and [incremental/final evidence](../verification/ticket-12-authorized-replay.md).
6. [Approved PRD v0.2](../superpowers/specs/2026-10-03-server-facility-next-prd.md), D05 and AC-05/06.

---

*本 ADR 遵循 Michael Nygard 模板。模板见 `docs/adr/0000-adr-template.md`。*
