# Ticket10 — explicit identifier policy

2026-10-04 verification-pending: complete Windows gate passed; Linux awaits the next integrated CI. Based on integration1d6377d, independent tree codex/ticket-10. Approved seams: public IdUtil/SnowIdGenerator, real Spring configuration, ordinary jar application operations. No new generic generator framework. ADR0033 replaces ADR0023 unbounded waits, preserves ADR0008 instance epoch parsing.

## TDD evidence

Raw logs `.verification-results/ticket-10`, shared dependency cache only, no changed SNAPSHOT installed there. Wrapper Maven3.10.0 and Oracle25.0.4.1 on Windows11/zh_CN/Asia-Shanghai. Each numbered RED precedes that contract implementation; the frozen full gate is recorded below.

| Slice | Observed result |
|---|---|
|01 default|New app incorrectly created implicit Snow node RED; disabled-by-default opt-in GREEN1 test.|
|02 nodes|Enabled configuration omitted one/both nodes and started RED; sentinel-1 explicit-required policy GREEN2.|
|03 facade|Missing provider returned node0 ID RED; explicit refusal GREEN3. First green-named log actually failed compilation because Result was mistakenly treated as Optional; corrected in green2 without changing assertion.|
|04 frozen wall time|Missing Duration property compilation RED; after adding it, real frozen rollback failed2s watchdog RED. Monotonic elapsed wait GREEN4. Watchdog advances only the owned test clock to release the old implementation.|
|05 sequence|1024 IDs then frozen rollover did not return RED; local candidate state and bounded wait GREEN5, including3 repeated failures and literal next-millisecond sequence0/1.|
|06 interruption|Pre-interrupted caller still generated ID RED; flag-preserving refusal and no consumed sequence GREEN6.|
|07 admission|Caller blocked behind owner beyond original budget RED; timed ReentrantLock plus original-deadline commit check GREEN7. Paused clock callback is a controlled scheduler/owner obstruction, not a promise to preempt arbitrary user code.|
|08 epoch|41-bit overflow RED plus negative-epoch sentinel error; range validation and separate issued-state GREEN9, preserving arbitrary long constructors/old readers.|

Historical comparison: `verification/id-consumer/LegacyIdSamples.java` was actually executed against unchanged0ee9d547022371ad31f885605e999de17ec22777 ordinary jar SHA2565ef94b3fc60274afd223070491b6d33dca39273df57d4923fd702eaf6ec6ca1f. Stored163851281, zero0, maximum36028797018963967 and negative epoch-1000 literal parsing PASS; old parser's negative-ID arithmetic remains an explicitly unvalidated legacy reader. First command log failed because PowerShell split an unquoted JVM argument; successful quoted-argument log is `legacy-original-0ee9d54-green.log`, original retained.

Design review `coordination/ticket-10-design-review.md` prompted preserving arbitrary legacy epoch constructors and separating generation range checks; restart high-water strictly above previous use is a deployment requirement. This short review does not replace final independent Standards/Spec reviews.

## Follow-up slices and preflight

-09 invalid zero/negative/over-one-minute/huge Duration and negative rollback threshold RED→GREEN10;10 reentrant callback hidden state RED→GREEN11;11 timeout had no standard cause RED→GREEN11.
-12 related regression suite PASS55/0/0/0: true/false frozen clocks, admission/active wait interruption with flags, maximum55-bit/no-wrap, duplicate nodes' deterministic collision, callback failure and extreme epoch subtraction. No new RED is claimed for supplementary regressions.
-13 resource preflight passed against target/classes, **not an ordinary jar**:2048 fixed-seed cases,8 workers×16384 per platform/virtual mode,10000 churn successes/rejections/interruptions under64MiB/2CPU. Baseline1889992/max1919488 retained bytes.
-14 UUID application preflight first failed BeanDefinitionOverrideException: a conditional default bean in regular imported configuration was incorrectly treated as auto-configuration order. Corrected the application-owned operation to use qualified ObjectProvider with direct UUID fallback. Diagnostic and corrected logs retained; corrected3-context business/JSON128MiB process PASS against target/classes, **not final jar evidence**.
-15 independent short review reproduced growing rollback bypassing the immediate-refusal threshold while already waiting. Two real REDs (recovery and sequence rollover) precede per-observation threshold enforcement; GREEN21/0/0/0 across the three new contract suites. Next call confirms failed attempts do not consume state. `coordination/ticket-10-premerge-review.md` contains the external probe.

Actual historical JsonUtil on the unchanged original jar also PASS literal numeric `{"id":36028797018963967}` and explicit string `{"id":"36028797018963967"}`; `legacy-original-json.log` preserves the run with its original runtime graph.

## Qualification matrix

| Standard | Contract and evidence |
|---|---|
|Q01|FR05/09, AC08/12 map to IdPolicyContractTest, SnowIdBudgetContractTest, SnowIdRecoveryContractTest, real boot configuration and ordinary consumers. ADR0033 supersedes0023 and preserves0008.|
|Q02|Explicit node0/3 and missing/-1/4, null properties/clock, zero/negative/huge wait, arbitrary epoch parsing versus generation range,1024sequence limit, rollback and forward jumps.|
|Q03|Verify installs ordinary jar into isolated repository, compiles standalone JDK-only consumer and separate Boot draft-operation consumer; frozen all run passed below.|
|Q04|Barrier-owned admission and clock callback, live interruption, frozen clock, failing/reentrant supplier and failure-neutral literal sequences; no external nodes/database mocked.|
|Q05|One per-call monotonic budget including admission, bounded per-instance state,64MiB/2CPU/45s resource process; callback must cooperate and OS/GC are not hard real-time.|
|Q06|Unchanged0ee9d54 actual jar parsing and actual old JsonUtil literals; no stored-ID rewrite or silent long→string conversion.|
|Q07|Seed100025/2048 representable timestamp/node cases; platform/virtual finite concurrency samples cannot establish unlimited uniqueness.|
|Q08|Logs/environment/source/isolated graph and fixed diagnostic messages recorded; no caller ID or secret appears in refusal messages. Linux pending repository CI.|
|Q09|Original88/88/75 coverage and5 architecture gates unchanged; no test removal/ignore. Frozen all passed1733/0/0/0 with all original gates.|
|Q10|Policy, example/defaults, ADR, changelog and tests delivered together. Ordinary jar/full Windows gate passed; remains pending until integrated Linux closes.|

## Frozen complete Windows gate

Command (JDK25/PG_BIN/VERIFY_WRONG_JAVA_HOME configured): `java verification/Verify.java all --fresh`.

- Exact source `fa26fc3d2031ffce9470c41ff483fdeb33989699`, clean tree, includes integrated07/12/19/28 CI15 repair/32 at207c0cc. Implementation8e38835; full gate113 commands PASS in `.verification-results/20261004-084154-877-all/summary.txt`, outer log `ticket-10/16-all-fresh.log`. Wrapper3.10.0 and a fresh isolated repository.
- Oracle25.0.4.1+1-LTS-5, Windows11/amd64, Asia/Shanghai, zh_CN; native PostgreSQL18.6. Library1733 tests,0 failures/errors/skips,5 architecture contracts, unchanged dependency rules. Coverage instruction26611/28597, line5161/5464, branch2906/3412; original88/88/75 thresholds pass. Compared with integrated1712,21 new ID contract tests were added; existing tests remain, with explicit opt-in setup and corrected bounded-wait descriptions.
- Ordinary jar SHA256 `cc8c8a405a4e747b459c0d1eb850aa7794c90610c593d2c906b3431dc14ef312`. Actual12-id-consumer.log: retained baseline1933944/max1963184 bytes,64MiB/2CPU, same fixed corpus/seed2048/10000cycles and both thread modes. Source origin is asserted to be a jar; Spring/JUnit are absent.13-id-application-consumer.log PASS through3 real contexts and Drafts.create, current numeric/string/UUID literal JSON and sibling close.
- Existing core/mapping/crypto/IO/CSV/Excel/rate/lock/claim/HTTP replay/JSON/optional-graph/partner consumers all pass. Template78/0/0/0 with original coverage; all3 PostgreSQL CLI contracts and packaged platform/virtual restart pass. Template jar SHA256 `564db14091b8b789d3257948f60f67a176b2bd7516fcf4a371391811ce9ab02e`. Five repeated application processes and all checksum/JAVA_HOME/real-JDK21 negative controls pass; no excluded or skipped gate.
- Short independent review in coordination/ticket-10-premerge-review.md rechecked growing rollback fix and UUID application/ordinary-jar entrance; no open finding. Final independent Standards/Spec review remains required for the whole integration branch.

## Remaining integrated qualification

This frozen source precedes08 and20; their later merge is not retroactively included in these counts or artifact hashes. Linux ID qualification awaits the repository CI on the integrated source. Existing CI16 qualified207c0cc before10 and cannot close this ticket. No finite sample is advertised as proof of unlimited uniqueness, and no cross-process node assignment is claimed. Final33 owns one candidate across all modules.

