# Ticket08 cache guarantees — incremental evidence

> 当前状态（2026-10-04）：本票已由250ce2d / CI18两OS完整门闭合，见[联合验收](ticket-08-10-20-ci18.md)。下文保留各次冻结来源的本地证据及当时待验事项；其中pending描述不代表当前状态。

Windows verification is complete through the original library/consumer run plus the explicitly recorded corrected whole-tail run below. The original `all` summary remains FAIL; it is not relabelled. Current Linux CI is pending, so ticket08 remains verification-pending. Worktree ticket-08, branch codex/ticket-08, starting integration `1d6377dee64db3e8b072dd590a9f09c78df5b6df`. Raw RED/GREEN logs live in .verification-results/ticket-08 and survive clean. Public seams and decisions are in the ticket plan and ADR0031.

| Cycle | RED / prior behavior | GREEN / result |
| --- | --- | --- |
| 01 default capability absence | red-01-default-absence.log: unselected application contains caffeineCacheManager | green-01-default-absence.log:1/0/0/0; no implicit cache manager |
| 02 missing backend | red-02-missing-dependency.log: selected cache starts successfully with a permanent Map when Caffeine is hidden | green-02-missing-dependency.log:2/0/0/0; explicit stable missing dependency failure |
| 03 selection/override matrix | Existing implementation passed; no artificial RED | contract-03-selection-matrix.log:4/0/0/0; missing Spring support or both dependencies refuses; disabled and explicit user-manager contexts start; complete pair supports public cache get/put |
| 04 fixed names | red-04-fixed-names.log: configured names ignored by the dynamic manager | green-04-fixed-names.log:5/0/0/0; two declared caches remain the only caches after4096 unknown names |
| 05 policy validation | red-05-policy-inputs.log: selected zero TTL starts successfully | green-05-policy-inputs.log:6/0/0/0; nonpositive/overflow TTL, nonpositive capacity, missing/duplicate/overlong/control names reject before cache publication |
| 06 expiration clock | red-06-expiration-clock.log: at10s fake time the old manager still returned the value | green-06-expiration-clock.log:7/0/0/0; supplied public Caffeine Ticker drives exact write expiry, read does not extend it; optional signatures moved behind class-level dependency guards |
| 07 capacity/maintenance | Existing selected backend passed; no artificial RED | contract-07-capacity-maintenance.log:8/0/0/0; N−1/N/N+1 at two entries, public Caffeine cleanUp observation point,4096key churn, clear and reload; no instantaneous strict-size claim |
| 08 legacy null loading | red-08-null-loading.log: legacy check/load/put calls the null loader twice | green-08-null-loading.log:17/0/0/0; delegates Cache.get loading contract |
| 09 actual workers | Existing backend passed | contract-09-concurrent-loading.log:3/0/0/0; first loader barrier, six followers, different-key completion before release |
| 10 null/failure/invalidation | Existing selected backend passed | contract-10-loader-failures.log:4/0/0/0; cached null, checked loader failure cause, no pollution, recovery and eviction |
| 11 close ownership | red-11-close-retention.log: closed application still exposes loaded1MiB value | green-11-close-retention.log:20/0/0/0; private standard-SPI lifecycle adapter detaches provider and clears owned values; second app independent |
| 12 invalidation/close probe | contract-12-invalidation-close-races.log:7 tests/1 failure; the experimental assertion that close always waits for an initial loader was false | This failed assumption is preserved, not reported as a provider bug or silently erased |
| 13 host lifetime correction | Provider behavior determines the documented contract; no product change to create artificial waiting | contract-13-close-host-lifetime.log:31/0/0/0; manager detaches, initial loader may finish later; host quiesce responsibility explicit; historical default/fallback and @Cacheable expectations migrated |
| 14 provider fault probe | contract-14-provider-failures.log:9 tests/1 failure; experimental expectation of clearing a cache whose ticker keeps throwing was false | Backend read failure passes without calling business loader or replacing cached data; cleanup guarantee narrowed to attempts and ownership release |
| 15 cleanup continues | No product change; one failing external clock read then recovery tests the declared best-effort cleanup boundary | contract-15-provider-cleanup-attempts.log:9/0/0/0; exact first exception preserved, other cache cleared, manager detached, repeat destroy harmless |
| 16 legacy failure contract | Existing loading delegation passed | contract-16-compatibility.log:34/0/0/0; Cache.ValueRetrievalException cause, failed load recovery, wrong type does not replace value, null loader rejection |

Commands use the checked-in Maven Wrapper with `-Dtest=... test`. Actual filenames are retained under `.verification-results/ticket-08`; the root full suite is not claimed green during this incremental phase. Host ticker faults can cause Caffeine's own maintenance logger to print the original cause: the facility cache code itself introduces no key/value/message logging, and does not claim control over third-party logging.

## Independent consumer preflight

`consumer-preflight-package.log` builds an ordinary jar with tests skipped, `consumer-preflight-install.log` installs that exact jar to the local development Maven repository, and `consumer-preflight-build.log` compiles the independent Maven application. These steps are explicitly a preflight, not a substitute for the clean full quality gate or isolated-repository provenance checks.

`consumer-preflight-64m.log` passed in a real `-Xmx64m -XX:ActiveProcessorCount=2` JVM:2 independent application policies, seed80031/2048 public expiry+invalidation operations,8192 unknown cache names,4096 entry churn,8 real workers;256 retained closed managers, each formerly holding32768 entries and1MiB payload. Loader threads are joined and contexts close naturally. No test dependency is present in this consumer graph. Retained managers expose empty names; no private map inspection or GC timing assertion.

`matrix-preflight-{minimal,caffeine-only,context-support-only,cache-pair}-{default,disabled,override,cache-enabled}.log`:16 actual independent production-graph JVMs all passed. Default/disabled does not force registration; a host manager wins even under explicit selection and missing dependencies; missing either local backend dependency rejects selection; complete pair uses standard Cache/CacheManager with finite names. Final runner additionally retains the fifth no-Jackson graph and existing JSON/executor checks, bringing its matrix to24 JVM scenarios.

## Remaining gate

The Windows evidence is now complete as described below. Linux closure requires current CI; historical ticket24/default-fallback evidence does not cover this changed policy.


Merged integration `c581dc6` (ticket19/32 and ticket28 Windows process fix) at `0d11ef5fed98b648f564ac72400a14760345aff4`; runner preserves mapping/html/cache/http/claim/partner/template consumers. Full gate will run the combined source.

## Whole-run attempt and corrected consumer expectation

Frozen source `6881088b7908a9f619e19b0fb4619f48fb6d7b11` ran `java verification/Verify.java all` with PG_BIN explicitly pointing to the installed PostgreSQL18.6 tools and VERIFY_WRONG_JAVA_HOME to the actual21.0.12.1+1 JDK. Raw report `.verification-results/20261004-082150-220-all/summary.txt` is **FAIL** and remains unchanged.

The library gate passed **1729 tests/0 failures/0 errors/0 skips**,5 architecture tests, dependency analysis and unchanged88/88/75 thresholds. Exact counters: instruction26678/28695,line5168/5476,branch2911/3430. The1712 integration baseline grows by17 tests:6 explicit configuration +9 public local cache +2 legacy loading tests. The seven historical auto-configuration cases were migrated one-for-one; no cases were deleted or broadly skipped, and @Cacheable's two cases now explicitly select the facility capability.

Library jar SHA256 is `ce9edfb7198d82eee5ab40007718881552dc72370d19ed033567dfefb9f6a608`. All preceding ordinary consumers, including core/mapping/crypto/IO/CSV/Excel/limits/HTML/lock/claim, the new64MiB cache consumer, authorized real HTTP replay, JSON/Web/upload graphs, completed successfully in this run. Cache consumer's exact marker and artifact hash are in `53-cache-consumer.log`.

The run stopped at `83-matrix-no-jackson-module-override.log`: the expanded override scenario explicitly supplies a JsonMapper, but the old no-Jackson-module assertion still required zero mappers. This was a consumer assertion contradiction, not cache fallback or a product exception. Commit `82049ea` restricts the absence assertion to scenarios without that explicit override; `green-17-explicit-mapper-override.log` confirms the same actual graph now passes. No product source changed. The full matrix and every not-yet-executed tail gate passed against the same library artifact in the separate report below; the original all is not relabelled as PASS.

## Contract and integration traceability

| Standard | Public evidence / boundary |
| --- | --- |
| Q01 | FR02/05/08,AC02/08/09 map to explicit configuration, Spring Cache operations, selected policy and ordinary-jar consumers; seven reproduced defects have recorded RED/GREEN. ADR0031 and local-cache migration record input, exception and ownership changes. |
| Q02 | Default absence; selected hit/miss/evict/reload/null;1ns TTL and capacity1; exact expiry before/equal/after; N−1/N/N+1 capacity after maintenance; nonpositive/overflow policy; finite names, duplicate/control/257-character rejection and256-character/Unicode success. No format/Locale-dependent parsing is owned here. |
| Q03 | Actual independent Maven graphs with neither/either/both optional dependencies, host override and disabled cases; ordinary installed jar; standard Spring @Cacheable proxies; two application policies and close isolation. No cache correctness claim is derived from a mock call count. |
| Q04 | Public Ticker time and backend-failure injection; actual first-loader/follower/different-key/eviction/close barriers; checked loader cause/no pollution/recovery; every cache cleanup attempted after one host clock failure, first exception retained. Arbitrary loader cancellation and hard close deadlines are deliberately outside ownership; no database/I/O side effect protocol is claimed. |
| Q05 | Configured finite names × positive per-cache entry policy;4096 key and8192 unknown-name churn;256 retained closed managers formerly holding32768 entries plus1MiB values in a64MiB/2CPU JVM; real workers join and contexts close. Capacity is not an instantaneous or byte admission bound. Borrowed handles/host loaders require quiescence. |
| Q06 | This local cache has no stored wire/file protocol requiring historical format goldens. Existing public method descriptors are retained; explicit breaking semantics and concrete-manager type migration are documented and covered by legacy public tests/ordinary source consumers. No precompiled historical binary compatibility test is claimed for08. |
| Q07 | CacheConsumer seed80031,2048 deterministic public expiry/invalidation operations over32 keys, with independently maintained expected values/deadlines; targeted exact boundaries and separate capacity tests supplement the finite model. |
| Q08 | Raw summaries record exact source/JDK/OS/Locale/timezone/dependency trees/artifacts and commands. Expected failure logs are retained. Facility introduces no key/value/message diagnostics; Caffeine/Spring/host logging is explicitly outside total sanitization. Current CI remains a separate platform gate. |
| Q09 | Root1729 and original coverage/architecture/dependency gates passed above. Changed legacy expectations,17 added tests, failed exploratory assumptions and failed whole-run fixture are enumerated, not hidden or deleted. |
| Q10 | Code, ADR0031, public migration, ordinary consumers and raw local logs are delivered together. Windows combined evidence is complete below; current Linux CI remains pending. Final33 batch review is separate and does not make future unrelated scenarios prerequisites for this cache ticket. |

J01/J02: actual ordinary-jar dependency loading and selection/override matrix. J05: independent TTL/capacity/data and closing one application. J15: finite names, expiry/churn and repeated retained-manager cleanup. Other J03/04/06–14/16/17 scenarios belong to their owners; cache is not an execution permit, distributed receipt, parser, request identity source or lock. The combined runner retains those existing consumers without attributing their semantics to08.

Limited independent review of6881088 by impl03 found no blocker; scope and exclusions are saved at `../coordination/ticket-08-premerge-review-impl03.md` relative to the worktree parent. It neither ran this full gate nor replaces final33 standards/spec review.

## Final Windows continuation and handoff

After integrating the28 test-host logging fix (`9a8cd45`), the tail froze at `d0afe97`. It finished with exit0 and **RESULT=PASS (tail scope only)**: `.verification-results/20261004-083656-716-cache-tail/summary.txt`, outer log `.verification-results/ticket-08/tail-console.log`. The61 commands rerun the entire5-graph/24-scenario matrix, independent partner application and missing-coverage negative, template78 tests/0 failures/0 errors/0 skips,3 PostgreSQL CLI process controls, packaged platform/virtual HTTP with restart, template absent-coverage negative,5 independent resource application cycles, and checksum/missing-JDK/actual-JDK21 negatives.

The continuation first required `git diff --exit-code 6881088 HEAD -- src pom.xml .mvn` to be empty, then required both the target and isolated-repository jar to match the original `ce9edfb7198d82eee5ab40007718881552dc72370d19ed033567dfefb9f6a608` SHA256. The only verification differences from the initial all are the corrected explicit-mapper assertion and28's tested logging fixture/bounded failure diagnostics. Template product/dev helpers are unchanged by that28 fix; its78 tests were freshly executed, not borrowed from another ticket. Partner jar SHA256 is `2b501f28ffec3916e1649ec7ab0eea1303d2394cefea043c008003bfa80c73e6`; template jar SHA256 is `b7eaa5946433676375b2b8e6e8573a147343ddd27881307718463bfceb6d6445`.

Both runs use Oracle JDK25.0.4.1+1-LTS-5, Windows11/10.0 amd64, Asia/Shanghai and zh_CN, an isolated per-worktree Maven repository (`fresh=false`), checked-in Wrapper/settings, PostgreSQL18.6 and an actual JDK21 negative. The original full run stores root Surefire/JaCoCo/architecture/dependency/artifact evidence; the continuation stores the complete updated matrix, template, partner and process evidence. A local continuation harness and exact Verify.java are copied into the tail report, preserving replayable input. Normal CI still uses the unchanged public `java verification/Verify.java all --fresh` entry and will run everything in one invocation.

The local continuation command was:

```powershell
javac --release 25 -encoding UTF-8 -d .verification-results/ticket-08/tail-classes verification/Verify.java .verification-results/ticket-08/CacheVerificationTail.java
# PG_BIN and VERIFY_WRONG_JAVA_HOME are explicitly set as recorded above.
java -cp .verification-results/ticket-08/tail-classes CacheVerificationTail
```

Afterward, central documentation tip `207c0cc` merged as `ac71681532c6ede625d6eb9a456ea4e7bf7a57f3`; `git diff d0afe97 HEAD -- src pom.xml .mvn verification templates` is empty. Final handoff documentation does not change tested product, test or consumer sources. No main checkout write or push was performed by this ticket. The sole ticket completion gap is current Linux CI; final33 same-candidate review remains its own batch responsibility.
