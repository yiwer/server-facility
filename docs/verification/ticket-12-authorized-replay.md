# Ticket 12 verification — authorized bounded HTTP replay

> 当前状态（2026-10-04）：本票已由207c0cc / CI16两OS完整门闭合，见[联合验收](ticket-07-12-19-28-32-ci.md)。下文保留各次冻结/交接时的本地证据和当时待验事项；其中pending描述不代表现在仍未关闭。

Windows final all passed; verification-pending only for Linux CI. This report distinguishes the retained first-run migration failure from the final successful candidate. Worktree `ticket-12`, branch `codex/ticket-12`; initial source `6a6667269551eae3d46b7c6bf626fe5f9fd101b3`, implementation baseline advanced to `b7b7ea46778972822c7a757cf593d441bc1cde3c` (runner diagnostics only). The coordinator authorized implementation after ticket 11's actual Ubuntu all completed; the unrelated combined Windows consumer build remained under separate diagnosis.

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


| 32 repository consumer migration | `red-32-legacy-http-migration.log`: 55 tests, 12 failures/18 errors from missing authorization and obsolete ownerless expectations | `green-32-legacy-http-migration.log`: 46/0/0/0; public compatibility constructor guards, qualified filter/MockMvc consumers and actual streaming/quota fixtures |
| 33 peer reset | Existing failure path passed; no artificial RED | `contract-33-peer-reset.log`: actual socket RST after a 1024-byte prefix, public HTTP barriers, observed write failure, retry unavailable after clock advance, one effect |
| 34 completion failure | Initial fixture required a complete committed response and encountered the real truncated final-chunk failure | `contract-34b-completion-failure.log`: UNKNOWN-return/throwing compliant stores both refuse subsequent execution; literal committed prefix retained; first `contract-34` log preserved |
| 35 serialization failure | Existing policy passed; no artificial RED | `contract-35-serialization-failure.log`: actual Jackson getter failure yields safe 500, later 503, one effect |
| 36 explicit legacy | Existing shared policy passed; no artificial RED | `contract-36-explicit-legacy.log`: HTTP 200/code 503 safe legacy envelope, no key/exception disclosure or execution |
| 37 existing async | `red-37-preexisting-async.log`: an upstream async-started request reached business execution | `green-37-preexisting-async.log`: refusal before claim/effect |
| 38 response framing | `red-38-response-framing.log`: mismatched Content-Length produced a replayable different body | `green-38-response-framing.log`: 34/0/0/0; mismatched framing terminal, complete HTTP test group passed |
| 39 multiple encodings | `red-39-multiple-encoding.log`: identity plus a second gzip header was eligible | `green-39-multiple-encoding.log`: multiple encodings terminate |
| 40 quota order | Initial fixture imported RateLimit from the wrong package; no product defect or artificial RED claimed | `contract-40b-quota-order.log`: entry capacity charges acquisition/replay/conflict/new key, fifth request 429; business capacity charges only two actual effects |
| 41 overloaded method/query | Existing scope and host policy passed; no artificial RED | `contract-41-overload-query.log`: overloaded same-path operations isolate literal receipts; host-relevant query change conflicts, two effects |


| 42 processing quota combination | Existing product behavior passed; additional J07 evidence, no artificial RED | `contract-42-processing-quota.log`: 2/0/0/0; first HTTP request held at a public barrier, second409 consumes final entry token, third429, only one business permit/effect, first receipt completes after release |
| 43 disabled installed consumer | First full all stopped at `49-platform-web-disabled.log`: old platform consumer expected disabling the provider to execute annotated work | `green-43-disabled-consumer-build.log` and `green-43-disabled-consumer.log`: actual installed-jar HTTP now asserts safe503 and zero effects; no product change, old assertion migrated to the approved contract |

Checkpoint preparation `prepare-installed-consumer-2.log` ran the selected new HTTP/value/claim contract group: 43 tests, 0 failures/errors/skips, then installed the ordinary jar for the independent consumer. JaCoCo was explicitly skipped for this preparation, so this is **not** the full coverage/architecture gate. The first preparation command failed because an unquoted PowerShell dotted Maven argument was split; `prepare-installed-consumer.log` remains unchanged. The independent consumer completed in about 66 seconds including capacity churn; the integrated runner grants a bounded 120-second process deadline.

Counts are tests/failures/errors/skips for the exact command, not full-suite counts. Source checkpoint `811aab7` was merged with ticket 26/16 integration `9009810` at `ed8a650`; later cycles run on that worktree with their corresponding incremental changes. Final integrated source and full-run evidence follow below when available.

The old direct interceptor tests asserted ownerless records and ignored the new required host authorization. They now verify the retained compatibility constructor's explicit refusal. Qualified behavior is exercised by the actual HTTP suites (`AuthorizedReplayHttpTest`, `ReplayBudgetHttpTest`, `ReplayReceiptHttpTest`, `ReplayOwnershipHttpTest`, `ReplayStoreHttpTest`, `ReplayDisconnectHttpTest`, `ReplayQuotaHttpTest`), while `ResponseCaptureContractTest` retains public filter/Servlet response fault and budget behavior. Existing ownerless SPI binary/source compatibility tests remain. Existing MockMvc, streaming/resource, rate-limit replay and independent platform consumers now supply an explicit host Adapter; they no longer inspect the old store namespace for HTTP results.

The committed `PRIVATE-COMPLETION` Store fixture deliberately demonstrates a limit: the original failure can reach container logging and can truncate the final frame after a literal 200 response prefix. The test accepts this real transport outcome, verifies UNKNOWN and rejects later execution. It does not swallow the Store exception to manufacture a complete 200. This boundary and logs `contract-34-completion-failure.log`/`contract-34b-completion-failure.log` are recorded in coordination `review-followups.md` for ticket 33. Facility logging policy does not sanitize arbitrary host/container appenders; host adapters must produce bounded safe exceptions.

## Changes to prior expectations

Ticket 11's clock-rollback test previously asserted an expired current owner's release was rejected. ADR-0035 narrows that rule: termination removes future permission and may apply until replacement, while completion still needs a live lease. The existing test now checks rejection **after** replacement; the new `IdempotencyTerminationContractTest` proves both real-worker schedules through public operations. Other ticket 11 state, capacity, ownership and clock failure assertions remain.

## Contract and Q01–Q10 traceability

| Requirement | Public evidence and scope |
| --- | --- |
| Q01 / FR03/04/09 / AC04/05/06/12 | Formal ticket, ADR0035 and migration contract map to the HTTP suites and 43 incremental cycles above; the narrow release change has its own public claim worker regression. |
| Q02 | Keys missing/blank/control/duplicate/256/257; trusted identity null/blank/control/Unicode/unpaired surrogate; positive budgets; input/output N−1/N/N+1 including chunked input; empty204; independent lease/retention exact deadlines; overloaded and inherited methods; safe metadata and mismatched/multiple framing. |
| Q03 / J06 | Real Tomcat HTTP and installed ordinary jar with actual Security filter wrappers, native identity, method policy, revoke/restore. No mock call count substitutes for permission or replay. |
| Q04 | A blocked at a public HTTP barrier while B observes live/expired lease and then completes; RST after a known response prefix; serialization/advice/inner-filter faults; qualified Store refusal/throw; async startup and escape. Receipt or terminal outcome and business effects are observed through HTTP. External database atomicity/process recovery are out of scope and owned by29/30. |
| Q05 / J08 | Independent positive selected request/response budgets; default Store single receipt metadata allowance and aggregate64MiB/100000 bindings; tests use smaller explicit limits. Ordinary streaming/SSE and96MiB resource process remain in the final runner. Installed consumer uses128MiB/2CPU/120s,32 bindings,512 churn,2 closed/recreated applications. No new executor, queue, connection pool, file or background timer. Host concurrency/body transforms/parser limits remain explicit responsibilities. |
| Q06 | Independently specified FHR1 hex literal and explicit legacy error fields; all truncations and invalid headers/framing rejected before stored bytes are emitted. Deprecated interceptor constructor remains linkable but refuses missing authorization; old Store SPI binary compatibility remains in ClaimConsumer. HTTP behavior migration intentionally removes unsafe ownerless lookup assertions, mapped above. |
| Q07 | Deterministic exhaustive truncation of the fixed receipt plus finite magic/status/length/header mutations; no random seed needed. Stable literal receipts and effect counts across scope/permission/churn sequences; existing claim state-machine seed110034 retained by the final runner. |
| Q08 | Exact candidate/environment/OS/JDK/dependencies and raw failures are recorded by the final runner below. Safe error sentinels cover selected paths. Committed host Store/container exception logging boundary is explicitly retained, with coordinated33 follow-up. Linux result is separate, never inferred from Windows. |
| Q09 | Root coverage88% instruction/line and75% branch, architecture/dependency gates unchanged. Historical SPI tests remain; old HTTP assertions migrated to public qualified contracts with actual network coverage. Final Windows gate results are recorded below; Linux remains pending. |
| Q10 | Source, public migration, ADR, tests and original RED/GREEN logs delivered together. Local full gate and Linux closure are explicitly separate.29/33 own future durable/release-candidate combinations. |

J07 is exercised by `ReplayQuotaHttpTest`: four distinct attempts (first, replay, conflict, new key) consume four entry tokens; a fifth is429. Only the two acquired operations consume business quota. A second real HTTP barrier case holds the first operation and proves a Processing409 consumes the second/final entry token, so the next request is429 while business effects stay one. `ReplayOwnershipHttpTest` separately proves exact lease Retry-After and late-owner behavior. The09 identity/IP tests and these new HTTP combinations run together in the final library gate.

## Outstanding evidence

Incremental public seams and the final Windows all are complete. Linux CI remains the only ticket12 platform closure item; status is verification-pending. Tickets29/33 own their later durable/release-candidate combinations and do not retroactively replace this ticket's own evidence.

## First integrated all — retained migration failure

Candidate `5c759b5111e48675a9a36218e8f53e30c37dfd95` began clean, with explicit PG_BIN and valid Java21 negative-control home. `.verification-results/20261004-070251-893-all/summary.txt` is **RESULT=FAIL**, not a completed all claim. The root library passed1643/0/0/0 with unchanged coverage/architecture/dependency gates; ordinary core/crypto/IO/CSV/Excel/rate-limit/claim consumers, claim fault JVMs, the new Security replay consumer, JSON consumer and default/user platform HTTP consumers passed before `platform-web-disabled` failed its obsolete expectation of naked annotated execution. Cycle43 corrects only that migrated consumer assertion, preserving the real503 response/zero effects. Cycle42 additionally closes the combined Processing entry-charge evidence gap; no production file changed after5c759b5. The next integrated all verifies both changes and the latest07 integration.

## Review and second integrated candidate

Implementation03 reviewed the unchanged5c759b5 production code through both specification and standards axes without editing the worktree. The review checked current authorize-before-claim/replay, trusted tenant/actor/full operation scope, late-owner/current-generation termination, advice/oversize/disconnect receipt refusal, deferred emission after inner filters and current denial, positive resource budgets, deprecated ABI migration, original failure plus suppressed cleanup, ordinary pass-through and allowed headers. No blocking finding was reported. The review explicitly retained the structural wrapper limitation and host/container exception-logging boundary; it did not execute or replace the final all or ticket33's candidate review.

Second candidate `3563d920f355db61c1fa9249efd8eefc1da5ac1a` merges07 central `d28072fb4c5a6a60b81f3ed675cac1d2552bbae0`, preserving `lockConsumer`, `httpReplayConsumer` and every existing runner entry. It began with a clean worktree. `.verification-results/20261004-071310-376-all` is the second full-run evidence directory. Its final summary is RESULT=PASS, exit0; all stages passed as detailed below.

## Final Windows all and exact artifact

Tested source: `3563d920f355db61c1fa9249efd8eefc1da5ac1a`. The run began clean. Command:

```powershell
$env:PG_BIN='C:/Users/yiwer/AppData/Local/Temp/server-facility-research-tools/postgres-18.6.0-windows/bin'
$env:VERIFY_WRONG_JAVA_HOME='C:/Users/yiwer/AppData/Local/Temp/server-facility-research-tools/jdk21/jdk-21.0.12.1+1'
java verification/Verify.java all
```

`.verification-results/20261004-071310-376-all/summary.txt` is **RESULT=PASS**, process exit0. Oracle JDK25.0.4.1+1-LTS-5, checked-in Maven Wrapper3.10.0, Windows11 amd64, Asia/Shanghai, zh_CN; isolated repository, fresh=false. PostgreSQL18.6 binaries were explicitly supplied; the template owned its temporary cluster and lifecycle.

- Root library1667 tests,0 failures/errors/skips; all five named architecture rules and dependency analysis passed. Instruction25894/27905=92.793%, line5015/5325=94.178%, branch2784/3294=84.517%; original88%/88%/75% limits remain.
- Ordinary core/crypto/IO/CSV/Excel/lock/rate-limit/claim consumers, real missing-library graphs, JSON and actual platform HTTP/default/user/disabled cases all passed. Historical SPI/binary consumers and32MiB claim fault probes remain.
- `46-http-replay-security.log` contains the full PASS sentinel for trusted tenant/actor/route, current permission revoke/restore,32 bindings/512 churn/2 application cycles with128MiB/2CPU/120s. It uses the installed ordinary jar; JUnit/library test fixtures are absent.
- The actual HTTP suites cover finite input/output limits, current authorization, different/inherited/overloaded operations, normalizer/controller ownership, async refusal, advice/serialization/inner-filter faults, late generation, qualified Store UNKNOWN failure and real socket RST. Root streaming/resource tests also passed; ordinary downloads/SSE retain their streaming behavior.
- Partner independent build, executable HTTP and missing-coverage negative control passed. Secured template76 tests passed with no failures/errors/skips, followed by actual executable-jar platform/virtual and restart HTTP cases using PostgreSQL. Template no-overwrite and missing-coverage negative controls passed.
- Five repeated independent application startup/use/close cycles and checksum/missing-JDK/realJava21 negative controls passed. No product/test/runner source changed during this final run; only this ticket's explanatory documentation was edited afterward.
- Library jar SHA256: `faa5383527384643f490496239d6d305f000eb6284804aa8b6cd5c62f770589f`. Secured template jar SHA256: `40c4b4b207d3f6e03c926ac48f5421fca0dfa80149af5f0aa35ebf48f433c53f`.

Integration remained `d28072fb4c5a6a60b81f3ed675cac1d2552bbae0` at completion and is already an ancestor. Final delivery adds only CHANGELOG/CONTEXT/migration/report/formal ticket notes to the exact tested source. `coordination/ticket-12-premerge-review.md` records implementation03's short two-axis review. Linux CI must still run on the merged candidate before closed; local PostgreSQL success is not substituted for the coordinator's separate CI14 Windows diagnosis on ticket28.
