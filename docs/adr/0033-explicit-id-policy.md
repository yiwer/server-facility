# ADR-0033: UUID default and explicit bounded SnowId

## Status

Accepted, 2026-10-04, ticket10. Supersedes ADR0023 unbounded/non-interruptible waits. Retains ADR0008 instance epoch parsing and historic bit layout.

## Context

Default node0 is not a deployment allocation protocol. A frozen wall clock cannot measure its own timeout. Sequence state must remain unchanged when admission, recovery or rollover fails.

## Decision

New applications use JDK UUID.randomUUID, with application-owned Supplier<UUID> when injection is useful. No generic ID framework. SnowId auto-configuration is opt-in and both node parts must be explicit. Legacy numeric facade refuses a missing provider; old signatures remain and are deprecated in favor of injected generators. A monotonic finite per-call wait covers lock admission and clock recovery/sequence rollover; interruption preserves the flag and fails. User clock callbacks must be nonblocking. Validate the41-bit delta before encoding and keep failed attempts state-neutral.

## Consequences

Stored IDs and parsing are not rewritten; legacy false now means bounded recovery instead of never throwing. Node reuse/restarts require deployment fencing/high-water recovery outside this library; UUID is the default when that protocol is absent. Every observation of rollback, including rollover/recovery waits, enforces the immediate-refusal policy. Arbitrary long epoch constructors and old parsers remain; only generation validates range. See the verification record for qualification status.

## References

- [Identifier policy](../building/identifier-policy.md)
- [Ticket10 evidence](../verification/ticket-10-id-policy.md)
- [JDK25 UUID](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/UUID.html)
