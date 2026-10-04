# Ticket19: explicit mapping and bounded legacy copying

> 当前状态（2026-10-04）：本票已由207c0cc / CI16两OS完整门闭合，见[联合验收](ticket-07-12-19-28-32-ci.md)。下文保留各次冻结/交接时的本地证据和当时待验事项；其中pending描述不代表现在仍未关闭。

Baseline integration418e26f, synchronized2b06f52 before implementation; branchcodex/ticket-19. Logs stay in`.verification-results/ticket-19`, outside target. ADR0042 is Accepted. Formal19 and Q01–Q10/J14 govern this work; no unexecuted scenario is marked passed.

| Round | Public contract | RED | GREEN |
|---|---|---|---|
|01|autoCopy must never reflectively replace a final field or run the target constructor before rejecting it|red-01-final-field.log: expected rejection missing|green-01-final-field.log:25 tests|
|02|recursive CopyTrait→autoCopy cycle rejects and the same source can be copied after the cycle is removed|red-02-cycle.log: recursion reached the test's32-call safety guard|green-02-cycle.log:26 tests|
|03|legacy reflective nesting is explicitly limited to32 active sources;31/32 pass and33 rejects|red-03-depth.log:33 was accepted|green-03-depth.log:27 tests|

|04|deep-copy sorted containers reject rather than discard their comparator|red-04-comparator.log:sorted-set accepted (first assertion)|green-04-comparator.log:28 tests, sorted-set and sorted-map exercised|
|05|unsupported concrete container rejects before target constructor side effects|red-05-concrete.log:LinkedList target constructor ran before assignment failure|green-05-concrete.log:29 tests|
|06|list copies have a fixed10,000-element operation budget|red-06-list-budget.log:10,001 accepted|green-06-list-budget.log:33 tests,9,999/10,000/10,001 through both list APIs|

|07|set, function-set, map-values and map-all share the fixed entry budget|red-07-container-budget.log:all four parameterized operations accepted10,001|green-07-container-budget.log:37 tests, each N−1/N/N+1|

|08|reflective arrays/trait-arrays/collections/value-map/all-map count non-null fields and entries in one10,000-work budget|red-08-reflective-budget.log:all five shapes accepted one field plus10,000 entries|green-08-reflective-budget.log:66 tests, combined N−1/N/N+1|
|09|interrupt stops subsequent callbacks, preserves interrupt flag and cleans the scope|red-09-cancel.log:second callback still executed|green-09-cancel.log:67 tests|
|10|optional diagnostics cannot change tolerant copy outcome when the standard backend throws|red-10-logging.log:actual Logback TurboFilter RuntimeException escaped LogUtil.isWarnEnabled|green-10-logging.log:19 tests, both warning sites attempted and tolerated|

|11|inaccessible JDK module fields reject through CopyException|red-11-access.log:InaccessibleObjectException escaped|green-11-access.log:71 tests|
|12|characterize shallow references, copied alias independence, null defaults, immutable input normalization, key collision order, actual iteration, nested budget, Runtime/Error cleanup and concurrent same-source isolation|no new product behavior; invalid-12-assertion-compile.log retains an erroneous AssertJ void chain|green-12-policy-matrix.log:76 tests|
|13|reflective CopyTrait arrays check interruption between callbacks after reserving slots|red-13-array-cancel.log:second callback still executed|green-13-array-cancel.log:77 tests|

|14|executable order-to-dispatch conversion uses complete named fields, duplicate-line order, sorted attributes, null note and owned immutable containers|red-14-explicit.log:unimplemented public application boundary|green-14-explicit.log:literal business consumer passed|
|15|application line/attribute cardinality has finite declared/actual bounds|red-15-dispatch-budget.log:1001 lines accepted|green-15-dispatch-budget.log:line999/1000/1001, attribute63/64/65, empty-line rejection|
|16|application text and quantity have explicit finite limits and malformed/null behavior|red-16-dispatch-values.log:first oversized text accepted|green-16-dispatch-values.log:all six text dimensions N−1/N/N+1, quantity, Unicode and required-null scenarios|
|17|actual mapping source evolution has compiler/business controls|added target field and renamed accessor really failed javac; same-type swap compiled but failed the independent literal oracle|green-17-evolution.log plus evolution/** source/diagnostics/summary|
|18|ordinary-jar selected legacy subset and finite heap/resource behavior|not a new behavior RED; test+jar:jar prepares the actual fixture, not a full quality gate|green-18-jar-fixture.log and green-18-resource.log:64MiB/512seed/2000 copies+rejections/200 Errors;1,649,560→1,657,832 retained bytes,7→7 threads|
|19|null fields still require traversal and must consume nested work|red-19-null-field-budget.log:5001 entries+5001 null fields accepted|green-19-null-field-budget.log:78 related tests;4999/5000 pass,5001 reject|
|20|old public ABI and historical compatible subset remain executable|compile-20-old-api.log compiles actual consumer against frozen pre19 ordinary jar;green-20-old-api.log passes with that jar|green-20-new-api-binary.log runs identical class files on new jar, SHA manifest checked in|
|21|resource rerun after null traversal correction|earlier source's resource evidence is retained, not relabeled|green-21-resource-final-slice.log repeats64MiB consumer:1,649,560→1,657,832 retained bytes,7→7 threads|
|22|a Map key callback interrupts before the same entry value callback, for copyMapAll and autoCopy|red-22-map-key-interruption.log: both public paths executed the forbidden value callback (2 failures)|green-22-map-key-interruption.log: between-callback check,80/0/0/0 selected tests; flag retained and next independent budget fresh|

These tests call CopyUtil, not private guard methods. The cycle RED deliberately stops after32 calls rather than exhausting the host stack. Existing historical shallow-reference, null-default, array, generic collection and exception policies remain in the regressions.

Final corrected-source all now passed as recorded below; central merge and actual cross-platform CI remain. The ticket stays verification-pending. The intermediate test+jar:jar commands are fixture preparation plus selected regressions, not full quality/consumer acceptance.


## Contract and ownership map

The new application-owned `OrderDispatch.prepare` executes outside Spring/facility runtime. Its annotation-only compile dependency documents nullable notes. It maps a concrete order to a dispatch value; no production root-library autoCopy call existed, so this is an executable migration example, not a falsely claimed migrated live service. Source record shape is explicit, target constructor arguments are compile-checked, and literal business results detect same-type swaps. See the [example](../../examples/order-mapping/README.md).

Legacy policies and finite work are specified in [migration](../building/explicit-mapping.md). The first array/collection work slice counted only non-null fields; round19 tightened this to every visited eligible field, including null, and has its own observed RED/GREEN. The library counts10,000 work units and32 active synchronous calls, not arbitrary callback CPU/allocation, a universal graph size, or user Class definitions. It creates no executor/queue/temp files; scope cleanup is tested after ordinary exceptions, Error, budget rejection, interruption and concurrent copies. ClassValue follows JVM Class lifetime rather than retaining classes in a global strong map.

The binary fixture is compiled now against ticket16's real pre19 jar (SHA25612c2113e54d8ec3552753ff408e41f49fce0bf24690f05e2d5805ae03cb81bef), not a fictional old production application. Exact class bytes/source/hash manifest are checked in; the historical common subset runs on both old/new artifacts. New final/cycle/budget refusals are intentional migrations, not old-compatible behavior claims.

| Standard | Evidence / limit |
|---|---|
| Q01 | Formal19/FR08–09/AC11–12, ADR0042, public CopyUtil and application module; no new mapper DSL |
| Q02 | Work/depth/text/cardinality/quantity N−1/N/N+1; null/empty/defaults/final/immutable/Unicode; concrete containers and comparator policies |
| Q03 | Actual JVM reflection and standard SLF4J/Logback; ordinary jar with no framework runtime for non-warning legacy subset; independently compiled application consumer |
| Q04 | RuntimeException/Error identity for collection callbacks, controlled standard backend exception, interruption before next callback, barrier-scheduled same-source concurrency and fresh subsequent scope |
| Q05 | Explicit10,000/32 library work policy, application1000lines/64attributes and bounded text;64MiB consumer increasing inputs,2000 bounded successes/rejections,200 failures, retained heap and thread count |
| Q06 | Frozen pre-change ordinary jar ABI binary, historical null/default/shallow contract; explicit semantic migration where compatibility is intentionally tightened |
| Q07 | Seed190042/512 properties plus literal complete business oracle; actual javac added/renamed controls and runtime same-type swap rejection |
| Q08 | Raw logs and invalid fixture compile attempt preserved; final source/environment/OS belongs to full runner below; no private values in copy warnings |
| Q09 | Original coverage/architecture/dependency/full runner passed on4bcad87 below; selected tests remain separate evidence |
| Q10 | Code/example/migration/ADR/evidence delivered together; corrected-source Windows full gate passed, Linux remains outstanding |

J14 DTO evolution applies directly. No new persisted wire format, network request, transaction, TTL or filesystem publication is introduced; database/timezone/real backend fault scenarios from unrelated tickets are not fabricated here. Old ordinary-jar behavior and compiler/business mutation controls are the independent evolution evidence. Future33 owns final all-ticket candidate rechecks separately.


## First frozen full run, before review correction

`2353992f04c9446f0ec00b1c20b978304e4c4876` (includes central07/d28072f) completed Windows `all --fresh` with `RESULT=PASS`,101 commands, in `.verification-results/20261004-071141-243-all`. Root library1662/0/0/0; instruction24999/26917,line4924/5219,branch2638/3108 and all original gates passed. OracleJDK25.0.4.1/Windows11 amd64/zh_CN/Asia/Shanghai, real JDK21 prerequisite and pinned PostgreSQL18.6 tools. Library jar SHA256 `bb1e493252eacd764ae246f42d823e84206d07437a675c267e49397ded541d69`.

The four new mapping commands passed: actual application compilation/business result/evolution controls;64MiB ordinary-jar resource consumer(seed190042/512,2000 rounds,200 callback Errors,retained1650632→1658904bytes,threads7→7); identical pre-change consumer class bytes linked against the new ordinary jar. Other dependency/optional/Web consumers, partner15, template76 with PostgreSQL packaged platform/virtual CRUD/restart, coverage negatives, five repeated application lifecycles and three toolchain negatives all passed. This local result does not replace the Windows CI14 database failure investigation.

Root's subsequent focused review found that a key.copy interruption was not checked before the same Map entry's value.copy. Round22 reproduced the external value callback twice through public helpers and reflected fields, then added a zero-work interruption check at both boundaries. The first full run above remains a historical pre-correction source, not the final ticket candidate. Final corrected-source integration evidence is still required below; no failed or unexecuted scenario is relabeled as passing.


## Final corrected-source full run

Clean source `4bcad8726e1b4379a0a56fe0804d1164086ae7e8` includes the Map callback correction and joint central CI15 candidatee3382ce (07/12/32 and28 native-process repair). On2026-10-04 it completed `java verification/Verify.java all --fresh`: **110 commands, RESULT=PASS, exit0**. Raw evidence is `.verification-results/20261004-075535-739-all`, outer log `.verification-results/ticket-19/final-corrected-all.log`. Oracle25.0.4.1+1-LTS-5, Windows11 amd64, zh_CN, Asia/Shanghai; WrapperMaven3.10.0, fresh isolated dependency repository, actual JDK21 negative control and pinned PostgreSQL18.6 tools.

Root library **1712 tests,0 failures/errors/skips**,5 architecture tests, unchanged dependency and88/88/75 coverage gates pass. Exact counters: instruction26467/28471,line5136/5443,branch2879/3390. Ordinary jar SHA256 `5853fd0737501be5a63f0d0efbbb59823cf820c7dcd219d76c7e72d97100cef2`. Independent template76 and partner15 tests pass; actual PostgreSQL diagnostics/lifecycle/cleanup and platform/virtual executable-jar HTTP restart all pass. Template jar SHA256 `1413a76ede0f526b142c32b7bb53209a8d6fd9dd2ea058f7635aa0e5b077fb9d`.

The new mapping commands13–16 consume that ordinary jar and independently compiled application. Literal DTO values, duplicate order, independent containers and actual compiler/business evolution controls pass. The64MiB legacy consumer records seed190042,512 properties,2000 successful/budget-rejected rounds and200 callback Errors; retained heap1651624→1659896bytes,threads7→7. The same pre-change class files run against the new jar successfully. Ordinary dependency graphs, optional/Web/HTML/Security replay consumers, five application lifecycle cycles, both missing-coverage controls and three toolchain controls also pass.

This result is from a separate local execution. It does not erase CI15 Windows' ConcurrentModificationException in the template's concurrent migration scenario, nor establish that unrelated flaky path is fixed. That issue remains with28 and is recorded centrally. Ticket19's Linux/new joint source is still pending; future33's final all-ticket candidate review remains separately owned. No promised program callback CPU or arbitrary graph snapshot guarantee was added.
