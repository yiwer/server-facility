# Note command protocol

Note POST and PUT require one `Idempotency-Key` header containing 1–128 ASCII letters, digits, `.`, `_`, `:` or `-`. Duplicate header fields, comma lists, missing and invalid values are rejected. The HTTP parser's ordinary field whitespace rules still apply. Direct `Notes.create/update` callers supply the same explicit key argument. Generate a new key for a new intended change and retain that key and command when retrying an uncertain result.

The scope is workspace, verified issuer/subject, operation (`create` or `update`) and key. Changing workspace, Actor or operation creates a different identity. A note's slug remains an independent workspace-local business uniqueness rule. Update also binds the target note ID; reusing a key for another target is a conflict. JSON ordering/whitespace do not change the validated command. Title/body whitespace and Unicode are preserved, so canonically equivalent Unicode strings can still be different commands.

The Module first checks current membership, attempts the unique command insert, and checks membership again after any wait. It reads a losing insert's committed winner in a separate READ COMMITTED statement. Authentication/method permissions and membership remain mandatory for a replay; revocation is checked before revealing a fingerprint conflict or expired receipt. Permission is observed at those checks, not promised to remain unchanged forever after a response begins.

The command row, note change, successful-command charge and original typed Note result commit together. A replay returns that result with the original create201/Location or update200. Later edits or deletion do not change the receipt and a replay never resurrects the note. A command that rolls back leaves no saved success or charge. New-key requests can retry business refusals after their cause is corrected; reusing a successfully committed key for different content cannot execute.

Call public Notes operations outside an existing transaction. The Module rejects an ambient transaction with `IllegalStateException` instead of silently joining it or committing independently with REQUIRES_NEW. Its writes explicitly use READ COMMITTED even when pool defaults differ; pagination retains its own REPEATABLE READ snapshot. This is an application Module contract, not a generic library transaction policy.

| Result | HTTP / fixed code | Caller action |
| --- | --- | --- |
| Successful create/update or its replay | 201 / 200 | Retain the original result and key. |
| Invalid or ambiguous key | 400 `invalid_command_key` | Correct the request. |
| Invalid storage identity | 400 `invalid_actor` | Correct the issuer/application integration; do not truncate identity. |
| Current membership denied | 403 `workspace_forbidden` | Regain permission through application policy. |
| Same identity, different command | 409 `command_conflict` | Use the intended original command or a new key for a new operation. |
| Unique claim still waiting when its finite lock budget expires | 409 `command_processing`, `Retry-After: 1` | Retry the same key and command later with a bounded client policy. This does not cancel the owner. |
| Workspace lifetime identity capacity reached | 429 `workspace_command_limit`, no `Retry-After` | A new key cannot regain capacity. Existing identities remain replayable; do not retry forever. |
| Database/connection/statement unavailable | 503 `persistence_unavailable` | Treat outcome as uncertain; retry the same key after recovery. |
| Unexpected persistence failure | 500 `persistence_failed` | Investigate safe server correlation; do not infer non-commit from a lost response. |

The default database lock wait is500ms; other connection/statement/transaction stage budgets are listed in README. They are separate finite budgets, not a complete request deadline. Before the unique claim, the transaction takes the INSERT's normal table and workspace foreign-key locks under those same budgets. Unrelated table/foreign-key contention remains503; only contention at the command identity claim produces the processing result. This classification belongs to the application-owned schema; adding other triggers or constraints requires reviewing their lock/error policy. The application never blindly retries a whole transaction in-process. SQL and underlying exception details are not exposed as error payloads or passed to the HTTP error logger.

Each issuer and subject is limited to65536 UTF-8 bytes for Notes storage. NUL and unpaired UTF-16 surrogates are rejected before conversion or JDBC; legal text is neither normalized nor truncated. The trusted JWT configuration and its smaller HTTP credential/header budgets still apply. V3 preserves V1/V2 identity text within this new bound while replacing the oversized membership text key with a bounded SHA-256 index and full tuple checks. A private trigger computes UTF-8 byte lengths on insert/update, including legacy three-column fixture inserts. Digest collisions reject and cannot authorize another identity.

V1/V2 did not enforce this storage ceiling. Long compressible identities could fit their old index. Before upgrading an existing database, the migration owner must run this read-only preflight; it reveals counts and lengths, not identity text:

```sql
select count(*) as unsupported_members,
       max(octet_length(convert_to(issuer, 'UTF8'))) as maximum_issuer_bytes,
       max(octet_length(convert_to(subject, 'UTF8'))) as maximum_subject_bytes
from workspace_member
where octet_length(convert_to(issuer, 'UTF8')) > 65536
   or octet_length(convert_to(subject, 'UTF8')) > 65536;
```

Automatic V3 upgrade requires `unsupported_members = 0`. Otherwise stop the upgrade: the checked migration also refuses and rolls back, preserving the original membership values and previous migration history. Do not trim, hash-replace or silently rebind the stored issuer/subject. An owner-approved trusted-issuer migration must explicitly transition membership to the new verified identity. Alternatively the application owner can review and test a coordinated higher, still finite storage bound in Java and the not-yet-deployed V3 schema. That is an application fork and migration decision, not an existing configuration option. Never edit a V3 migration already applied elsewhere; evolve deployed schema with a new migration and reviewed rollout.

The byte protocol uses a four-byte big-endian length before every UTF-8 field. Actor fields are `actor-v1`, issuer, subject. Create fingerprint fields are `note-create-v1`, slug, title, body. Update fields are `note-update-v1`, lowercase canonical UUID, title, body. Persisted fingerprint versions must continue to be understood when this application evolves; changing that protocol requires an explicit data/caller migration.

The workspace lifetime limit is10000 successful command identities. Replay does not spend another unit. Capacity is per workspace, not a bound on the whole database: workspace creation and tenant admission require deployment-owned policy. Full capacity still permits reads, replay and membership revocation, and rejects new successful command identities. Ingress admission is separate: if the application selects the optional rate-limit annotation/provider, each request attempt can spend that allowance even when it replays or conflicts. The core Notes command works when the optional rate-limit provider is disabled.

Receipts are available for24 hours from their successful result write. Their identity remains permanently reserved after expiry, including after physical response cleanup. An expired receipt returns410 `command_receipt_expired`; it never executes again. Do not delete identities to regain quota. Ticket30 owns the executable cleanup and exact commit-boundary process-loss/dual-process recovery evidence. This protocol document alone is not that evidence.
