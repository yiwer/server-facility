# Ticket 12 verification — authorized bounded HTTP replay

In progress. This report is not a completed acceptance claim. Worktree `ticket-12`, branch `codex/ticket-12`; initial source `6a6667269551eae3d46b7c6bf626fe5f9fd101b3`, implementation baseline advanced to `b7b7ea46778972822c7a757cf593d441bc1cde3c` (runner diagnostics only). The coordinator authorized implementation after ticket 11's actual Ubuntu all completed; the unrelated combined Windows consumer build remained under separate diagnosis.

Public seams: actual loopback Tomcat HTTP via `EmbeddedServletApplication`, fixture business effects exposed through HTTP, qualified store public operations, fake clock and real worker barriers. ADR-0035 and the ticket-specific plan describe the interface and migration. Raw logs are in `.verification-results/ticket-12` and survive Maven clean.

## Incremental evidence

Commands use the checked-in `mvnw.cmd` and `-Dtest=<public contract test or method> test`; no internal-field tests or mock call-count proofs are used for these scenarios.

| Cycle | RED / existing behavior | GREEN / result |
| --- | --- | --- |
| 01 missing current authorization | `red-01-missing-authorization.log`: actual HTTP returned 200 instead of 503 | `green-01-missing-authorization.log`: 1/0/0/0; safe 503 and effects zero |
| 02 expired owner termination | `red-02-expired-termination.log`: expired current owner release REJECTED instead of APPLIED | `green-02-expired-termination.log`: 17/0/0/0; actual worker barrier proves expired current owner may terminate, replacement B's receipt survives late A |
| 03 qualified HTTP replay | `red-03-qualified-replay.log`: explicit host Interface absent (compilation RED) | `green-03-qualified-replay.log`: 2/0/0/0; first and replay literal receipt, one effect |
| 04 normalized request | `red-04-normalized-request.log`: no bounded input reached host normalization, actual request failed | `green-04-normalized-request.log`: 3/0/0/0; field order/whitespace equivalent, amount change conflicts, controller reads original complete body |
| 05 disabled provider | `red-05-disabled-infrastructure.log`: disabling infrastructure executed annotated target | `green-05-disabled-infrastructure.log`: 4/0/0/0; required annotation guard remains and effects zero |
| 06 current permission | Existing qualified path already passed; no artificial RED or source change | `contract-06-current-permission.log`: 1/0/0/0; success → revoke → 403 → restore → original receipt, one effect |
| 07 created headers | `red-07-created-headers.log`: Location absent on 201 replay | `green-07-created-headers.log`: 6/0/0/0; Location preserved, cookie/auth/private headers absent |
| 08 server-error result | `red-08-server-error-result.log`: explicit 500 response was replayed as an eligible receipt | `green-08-server-error-result.log`: 7/0/0/0; later attempts remain unavailable after fake-clock lease/retention expiry |
| 09 full filter completion | `red-09-outbound-filter-failure.log`: inner filter failed after MVC had already saved a receipt | `green-09-outbound-filter-failure.log`: 8/0/0/0; save moves to successful filter exit; failure terminates without later execution |
| 10 transformed body | `red-10-filtered-replay.log`: literal `base-1|tail-1` became `base-1|tail-1|tail-2` | `green-10-filtered-replay.log`: 9/0/0/0; defer replay emission until filter exit and suppress repeated inner body transformation |
| 11 outbound denial | `red-11-outbound-denial.log`: deferred success overwrote the inner filter's new 403 | `green-11-outbound-denial.log`: 10/0/0/0; current denial wins and saved body is not exposed |
| 12 declared async | `red-12-declared-async.log`: Callable target executed instead of being refused | `green-12-declared-async.log`: 11/0/0/0; Callable, DeferredResult, CompletionStage, SSE, stream and entity-wrapped stream refused before method effects |
| 13 runtime async | `red-13-runtime-async.log`: direct Servlet startAsync escaped a finite declaration | `green-13-runtime-async.log`: 12/0/0/0; explicit failure followed by permanent refusal after lease |
| 14 key inputs | `red-14-key-boundaries.log`: overlong client key became 500 instead of a client error | `green-14-key-boundaries.log`: 13/0/0/0; missing/blank/control/overlong/duplicate key rejected, maximum 256 valid |

| 15 trusted identity input | `red-15-identity-values.log`: unpaired surrogate accepted | `green-15-identity-values.log`: value boundaries, valid Unicode and redacted diagnostics |
| 16 inherited operations | `red-16-inherited-operations.log`: B received A's receipt | `green-16-inherited-operations.log`: concrete controller type joins full handler operation identity |
| 17 independent deadlines | `red-17-independent-deadlines.log`: lease/retention were coupled | `green-17-independent-deadlines.log`: exact fake-clock retention boundary with independent lease |
| 18 advice outcomes | Existing path already passed; no artificial RED | `contract-18-advice-exceptions.log`: translated 200/400/500 exceptions remain permanently unavailable after lease expiry |
| 19 body wrappers | `red-19-body-wrapper.log`: body transformation after capture executed with mismatched authorized input | `green-19-body-wrapper.log`: later transformation refused, earlier transformation normalized/replayed, ordinary requests unchanged |
| 20 prior output access | `red-20-premature-output.log`: writer prefix allowed target execution | `green-20-premature-output.log`: safe 503 before claim/effect, prefix absent |
| 21 status policy | `red-21-save-statuses.log`: 206 was replayable | `green-21-save-statuses.log`: finite 2xx except 206 and explicit 400/404/409/410/422 replay; transport/auth/rate/server statuses terminate |
| 22 input budget | Existing implementation passed; no artificial RED | `contract-22-input-budget.log`: known-length/chunked N-1/N/N+1, controller input isolated from host array mutation |
| 23 unsupported form input | `red-23-unsupported-input.log`: form fell through into binding instead of explicit refusal | `green-23-unsupported-input.log`: form/multipart 415 before business execution |
| 24 exact output budget | `red-24-output-budget.log`: default 1 MiB body fit capture but metadata overflowed store budget | `green-24-output-budget.log`: small N-1/N/N+1 and exact default limit; total receipt storage remains bounded |
| 25 representation metadata | `red-25-representation-metadata.log`: encoded representation replayed without its encoding | `green-25-representation-metadata.log`: encoding/range/oversized metadata terminate without receipt |
| 26 foreign receipt | `red-26-receipt-validation.log`: truncated receipt leaked into generic 500 | `green-26b-receipt-validation.log`: independent FHR1 literal, all truncations, invalid framing/status/header and body budget; initial `green-26` exposed a test mutation yielding valid 255 (within the documented 2xx range), fixed to an invalid status without changing policy |
| 27 current entity headers | `red-27-replay-entity-headers.log`: stale Content-Encoding polluted replay | `green-27-replay-entity-headers.log`: stale representation/framing headers cleared, fresh security header retained |
| 28 HTTP owner race | `red-28-http-lease-race.log`: processing reply lacked Retry-After | `green-28b-http-lease-race.log`: real A/B barrier at lease 9/10 ms, old action continues but B receipt survives; initial `green-28` found ResponseStatusException headers immutable, replaced by standard ErrorResponseException |
| 29 late oversize | Existing narrowed release path passed; no artificial RED | `contract-29-late-oversize.log`: after-lease oversized response terminates unreplaced owner and remains unavailable at later time |
| 30 store outage | `red-30-store-unavailability.log`: unavailable backend surfaced as generic 500 | `green-30-store-unavailability.log`: old-only/null/throwing provider returns safe 503 without legacy fallback or effects |
| 31 actual Security consumer | Existing implementation passed; no artificial RED | `contract-31-security-consumer.log`, `contract-31b-security-consumer.log`: installed ordinary jar, native authenticated identity, permission withdrawal/restoration, actual Security wrappers; -Xmx128m/2 CPU, 32 bindings, 512 churn, two closed/recreated applications |

Checkpoint preparation `prepare-installed-consumer-2.log` ran the selected new HTTP/value/claim contract group: 43 tests, 0 failures/errors/skips, then installed the ordinary jar for the independent consumer. JaCoCo was explicitly skipped for this preparation, so this is **not** the full coverage/architecture gate. The first preparation command failed because an unquoted PowerShell dotted Maven argument was split; `prepare-installed-consumer.log` remains unchanged. The independent consumer completed in about 66 seconds including capacity churn; the integrated runner grants a bounded 120-second process deadline.

`red-32-legacy-http-migration.log` confirms the planned migration debt: 55 selected existing tests, 12 failures and 18 errors, primarily because old constructors now reject missing authorization and old tests inspect ownerless state. These tests will be migrated to the qualified public HTTP behavior; the historical SPI compatibility tests remain intact.
Counts are tests/failures/errors/skips for the exact command, not full-suite counts. Later cycles and final runner evidence are pending. Old HTTP tests expecting unqualified ownerless behavior have not yet been migrated; no full-suite success is asserted.

## Changes to prior expectations

Ticket 11's clock-rollback test previously asserted an expired current owner's release was rejected. ADR-0035 narrows that rule: termination removes future permission and may apply until replacement, while completion still needs a live lease. The existing test now checks rejection **after** replacement; the new `IdempotencyTerminationContractTest` proves both real-worker schedules through public operations. Other ticket 11 state, capacity, ownership and clock failure assertions remain.

## Outstanding evidence

Response eligibility/advice/I/O/store failures, final filter completion, async refusal, request/response budgets, header/receipt validation, identity/route/overload isolation, quota coupling, consumer migration, independent installed-jar/resource tests, full quality gates, source hash and Windows/Linux final evidence are still pending. Do not mark the formal ticket closed from this incremental report.
