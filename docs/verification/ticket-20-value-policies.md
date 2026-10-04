# 20 — Explicit date, capacity and pattern policies

2026-10-04, in-progress. Worktree ticket20/codex/ticket-20 begins at central `1d6377dee64db3e8b072dd590a9f09c78df5b6df`. The formal ticket and all Q01–Q10/J14/J16 obligations remain open until the recorded scenarios and final gates complete. Planning is in the outside-worktree coordination/ticket-20-implementation-plan.md; this report records actual evidence only.

Public seams: DateUtil, NumberFormat, NumberUnits and Patterns, plus the independently compiled application-owned export-input Module. These are covered by the user's prior approval of utility and application seams. No private cache field inspection or fake regex-cancellation proof is used.

| Cycle | Behavior | RED | GREEN |
|---|---|---|---|
|01|integral capacity above binary64 exact range keeps every byte|red-01-exact-capacity.log:9007199254740993B became9007199254740992|green-01-exact-capacity.log:11 selected tests pass after decimal multiplication|
|02|signed long overflow is refused; signed bounds/zero/fraction truncation are explicit|red-02-capacity-overflow.log:3 overflows returned MIN,MAX,0|green-02-capacity-overflow.log:15 selected tests pass; signed bounds and truncation remain characterized|

|03|finite capacity text and decimal scale before arithmetic|red-03-capacity-bounds.log:129-character and scale129 inputs were accepted|green-03-capacity-bounds.log:17 selected tests pass, including127/128 boundaries|

|04|legacy calls observe current FORMAT Locale rather than the first cached locale|red-04-date-locale.log:isolated JVM returned English month after French switch|green-04-date-locale.log:45 selected tests pass after removing dynamic formatter retention|

|05|rotating developer patterns retain at most256 entries|red-05-pattern-retention.log:cache reached257|green-05-pattern-retention.log:67 pattern tests pass with finite retention|

|06|oversized trusted regex compiles without global retention|red-06-pattern-key-size.log:4097-unit pattern was retained|green-06-pattern-key-size.log:68 selected tests pass,4095/4096 boundaries retained|
|07|application Clock/Zone/Locale input produces literal instant and bytes|red-07-export-input.log:module not present, compilation fails|green-07-export-input.log:independent JDK-only32MiB consumer passes|
|08|application capacity must be positive finite decimal MiB, at most64|red-08-export-budget.log:zero accepted|green-08-export-budget.log:normal and invalid/upper-bound cases pass|
|09|fractional bytes use explicit floor|red-09-export-rounding.log:ArithmeticException for0.000001MiB|green-09-export-rounding.log:literal1 and1048577byte cases pass|
|10|fixed strict date/year/length and safe failure channel|red-10-export-strict-date.log:raw DateTimeParseException disclosed input|green-10-export-strict-date.log:fixed IllegalArgumentException, invalid date/year/length cases pass|
|11|injected Clock enforces future and inclusive30-day horizon|red-11-export-clock.log:past input accepted|green-11-export-clock.log:horizon and nearest future minute pass|
|12|nonexistent DST local time is rejected rather than shifted|red-12-export-gap.log:02:30 gap silently shifted|green-12-export-gap.log:gap refused,03:30 literal instant passes|
|13|overlap requires valid explicit offset, other offsets cannot be silently ignored|red-13-export-overlap.log:ambiguous null offset accepted|green-13-export-overlap.log:both valid overlap instants and invalid offsets pass|

Raw logs live in ignored `.verification-results/ticket-20`, outside Maven clean output. Initial source behavior and independent preflight are retained in coordination/value-policy-preflight.md. The intermediate exact-decimal implementation was not a release candidate: overflow refusal and finite decimal input bounds are separate TDD slices. Generic signed parsing is not a positive resource-budget policy; the new application flow must explicitly reject nonpositive capacities.

## Sources checked during implementation

- [JDK25 BigDecimal](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/math/BigDecimal.html): exact decimal arithmetic and exact integral conversion, with caller-owned scale/precision screening to bound extreme operations.
- [JDK25 DateTimeFormatter](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/format/DateTimeFormatter.html): explicit formatting policy; strict parsing and legacy behavior must be distinguished.
- [JDK25 ZoneRules](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/zone/ZoneRules.html): valid local offsets form the basis for explicit gap/overlap handling.

These references support the design; they do not replace actual public-boundary tests or cross-platform evidence. No final all or CI result is claimed yet.


## Later regressions and compatibility (not invented RED)

`green-14-export-regressions.log` extends the passing JDK-only application consumer with 31/32/33 text budgets, six-decimal policy, ASCII/Unicode/malformed inputs, minimum/maximum years, explicit French display, clock-zone independence, seed200043/512 integer quarter-MiB oracles and200 repeated gap failures followed by successful reuse. `green-15-legacy-regressions.log` passed147 selected tests across date/number/pattern; it includes four-worker concurrent pattern churn, public cache bounds, syntax/flags/group/replacement and shape-only date goldens. These are regressions of implemented/public old policies, not mislabeled product RED.

`green-16-historical-goldens.log` compiles and runs ValuePolicyConsumer against the actual pre20 ordinary jar from19 source4bcad87, SHA2565853fd0737501be5a63f0d0efbbb59823cf820c7dcd219d76c7e72d97100cef2. It passes literal SMART/default-zone/money/Math.round/list samples and explicitly expects historical precision/overflow/locale-cache outcomes. The same consumer classes then pass current semantics against target/classes in`green-17-value-resource-preflight.log`:64MiB/2CPU,512 numeric properties,10000 measured rotations plus syntax failures, retained5562120→5562120bytes,threads7→7. This second result is expressly a compiled-class preflight, not the final ordinary-jar or whole quality gate.

The old-jar path guard first rejected an incorrect artifact path before compiling; it did not execute a failing behavior test. The source file and historical artifact checksum are recorded in verification/value-consumer/README.md. Final runner copies those sources and the application inputs, compiles the application without the facility jar, and runs three separate default-zone/locale environments.


Cycle18 (`red-18-capacity-ascii.log`) demonstrates two Unicode-digit cases accepted by the intermediate BigDecimal parser although the old Double syntax rejected them. The minimal ASCII character guard preserves that boundary; `green-18-capacity-ascii.log` passes29/0/0/0. The first GREEN command was misparsed by PowerShell because a Maven property argument was unquoted; `attempt-18-powershell-argument.log` preserves this harness failure, before test execution. The corrected quoted command is the passing evidence.

## Contract traceability and limits

| Requirement | Public oracle and scope |
|---|---|
|Q01 / FR05,FR08,FR09 / AC08,AC11,AC12|ADR0043, migration table and this vertical RED/GREEN ledger; application-owned ExportRequests.plan replaces implicit environment in a representative executable input flow, without claiming a nonexistent production chain was migrated.|
|Q02|CapacityParsingContractTest signed bounds/128/scale/Unicode, DateUtilTest legacy null/date/ranges, independent ExportInputConsumer year0001/9999, DST gap/overlap, strict calendar, input32, six decimals, positive64MiB and30-day horizon; PatternRetentionContractTest syntax/flags/groups/4096.|
|Q03|Runner compiles the application separately with JDK alone; ValuePolicyConsumer consumes only ordinary jar+own classes with Spring/JUnit/SLF4J absent. Final artifact execution remains pending below.|
|Q04|Four-worker CountDownLatch release verifies concurrent pattern churn/public bound; fixed Clock controls time and repeated refusal/reuse. These paths do not own external I/O, transactions, worker queues or cancelable activities, so injected network failures/process recovery are not applicable. Arbitrary regex interruption is explicitly unsupported.|
|Q05|Application32UTF16/6decimals/64MiB and fixed16-unit timestamp; genericcapacity128UTF16/scale[-128,128]; pattern256entries/4096-unit retained keys; DateUtil no dynamic formatter retention. Separate64MiB process warms2000 then rotates10000 successes plus10000 syntax failures with registered heap/thread thresholds. Host-owned display values, regex/content/output bounds remain caller responsibilities.|
|Q06 / J14|Actual pre20 ordinary jar5853fd...cef2 from4bcad87, same compiled consumer with literal SMART/Locale/zone/money/Math.round/list and explicitly different precision/overflow/cache expectations. No public signature removed; NumberUnits is deprecated.31 owns the overall upgrade rehearsal.|
|Q07|Seed200043:512 signed exact-byte values and512 quarter-MiB values with independent integer oracles. Fixed literal instants and declared128/32/256/4096 boundaries; no new fuzz framework.|
|Q08|Logs retain command failures and label source-class preflight separately; default Locale/TimeZone changes occur only in child JVMs. New input errors omit raw value/cause; legacy Result causes remain documented, not falsely declared safe. Final runner records environment/artifact/source below.|
|Q09|Existing thresholds88/88/75, architecture/dependency rules and tests unchanged; final all pending. Added public tests and independent consumers are not coverage substitutes.|
|Q10 / J16|This report, ADR, migration guide and reproducible sources ship together. Local complete gate and subsequent Linux CI must be recorded before closure.33 owns final candidate combinations, not a prerequisite cycle blocking this ticket's own evidence.|

Windows/Linux path/device/link and byte-decoder semantics are not new capabilities here: inputs are Java Strings and the application opens no files. J16's applicable Unicode, Locale, DST and extreme numeric values run through these actual entrances. The runner still executes in Unicode/space paths and the final CI must prove supported OS invocation. No blanket arbitrary-BigDecimal output-size or regex CPU safety promise is made.
