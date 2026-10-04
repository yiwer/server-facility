# ADR-0052: Persist note command identity with its business transaction

## Status

Accepted. 2026-10-04. Ticket29, FR04/07, AC03/06. Extends ADR0051's application Module and ADR0050's verified Actor. This decision records the approved contract; executed acceptance evidence is recorded separately.

## Context

A response can be lost after a note commits. The workspace-local slug is a business uniqueness rule, not a request identity. An HTTP cache or separately committed lease cannot atomically establish whether the business change happened. The application already owns the PostgreSQL transaction and current workspace membership.

## Decision

- Note creation and update require an explicit `Idempotency-Key` in HTTP and an explicit key at the public Notes Module. Existing callers must supply a new key for each new command and retain it across retries. Workspace creation and deletion retain their existing contract. No implicit random key, generic command framework or independently committed claim is added.
- Command identity is workspace, verified issuer/subject Actor, fixed operation and key. A versioned SHA-256 fingerprint covers validated command values as length-prefixed UTF-8 fields; update includes its target note ID. JSON member order and whitespace do not change the command. Text is neither trimmed nor Unicode-normalized. Full identity fields are checked alongside their bounded digest so collisions reject rather than join identities.
- The unique command row, note change, business-command charge and typed Note receipt commit in one transaction. A unique insert that loses a race is followed by a separate SELECT under READ COMMITTED. PostgreSQL can wait on a conflicting row that was not visible to the original statement snapshot; a one-statement insert-or-select CTE is therefore insufficient.
- Notes explicitly selects READ COMMITTED for its write transaction and rejects an existing caller transaction with a fixed programming error. It neither silently inherits another transaction's isolation/commit scope nor opens a surprising REQUIRES_NEW side transaction. Its public operations own transaction completion. Read-only pagination retains its explicit REPEATABLE READ snapshot. Actual stricter Hikari defaults and a live caller transaction are counterexamples under test.
- The Module checks current membership before accessing a command and after a competing claim finishes waiting. Authentication, method authority and current membership are required for replay as for a first attempt. A receipt grants no authorization.
- Only successful committed commands consume the workspace's lifetime budget of 10000 identities. Replay consumes no further business unit. Full capacity permits replay, reading and revocation, while rejecting new keys. Optional ingress admission from ADR0032 remains an independent earlier decision and charges attempts, including replay; command correctness does not require that optional provider.
- Successful receipts are available for 24 hours. The command identity outlives the response representation permanently; an expired receipt returns an explicit gone result and cannot execute again. Validation/authorization refusals, uniqueness failures and rolled-back infrastructure failures save no success receipt and consume no unit. Failure classification and finite unique-key waits distinguish mismatched content, a command still processing, infrastructure unavailability and completed replay. Process-loss recovery and physical receipt cleanup are verified in ticket30.
- Notes accepts each issuer/subject as at most 65536 UTF-8 bytes, with no NUL or unpaired surrogate. This is an application storage protocol, not a new identity-provider normalization rule. Invalid text is rejected before encoding or JDBC. Legal text remains exact.
- V3 preserves V1/V2 and migrates workspace membership from a potentially oversized text B-tree key to a bounded actor digest plus complete tuple checks. PostgreSQL18.6 marks `convert_to(text,name)` STABLE; an actual generated-column DDL failed with42P17. A private BEFORE INSERT/UPDATE trigger honestly computes the digest using explicit UTF-8 byte lengths instead. Existing rows are backfilled without changing identity text, and inserts supplying the old three columns continue to work. No falsely IMMUTABLE wrapper is declared.
- Tests exercise signed real HTTP, public Notes operations and the approved controlled PostgreSQL boundary. SQL barriers and test-only database faults establish contention and atomicity without adding product fault hooks. The digest has an independently calculated literal golden.

## Consequences

The copied application owns a small, explicit command recovery protocol. A successful retry restores the original Note and stable status/Location even after later changes; it does not read a fresh representation and pretend that was the original result. Permanent identities and bounded lifetime capacity are deliberate tradeoffs, and operators must not delete identity rows to regain capacity. Receipt expiry does not release identity. A new retention or archival design would require another application decision.

This is a behavior change for note POST/PUT callers and for direct Module callers. It does not change facility's ordinary jar, the local HTTP replay SPI or arbitrary third-party endpoints. Ticket30 must still prove commit-boundary process failures, separate JVMs and cleanup. Ticket33 requires the same final candidate's complete dual-OS gates.

## References

- [PostgreSQL18 READ COMMITTED visibility and conflicting inserts](https://www.postgresql.org/docs/18/transaction-iso.html).
- [PostgreSQL18 binary strings and SHA-256](https://www.postgresql.org/docs/18/functions-binarystring.html), [generated-column restrictions](https://www.postgresql.org/docs/18/ddl-generated-columns.html).
- [PostgreSQL18.6 actual function volatility declarations](https://github.com/postgres/postgres/blob/REL_18_6/src/include/catalog/pg_proc.dat), [trigger semantics](https://www.postgresql.org/docs/18/sql-createtrigger.html).
