# Ticket19: explicit mapping and bounded legacy copying (in progress)

Baseline integration418e26f, synchronized2b06f52 before implementation; branchcodex/ticket-19. Logs stay in`.verification-results/ticket-19`, outside target. ADR0042 is Proposed. Formal19 and Q01–Q10/J14 govern this work; no unexecuted scenario is marked passed.

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

These tests call CopyUtil, not private guard methods. The cycle RED deliberately stops after32 calls rather than exhausting the host stack. Existing historical shallow-reference, null-default, array, generic collection and exception policies remain in the regressions.

Remaining: independent bounded-heap/resource and seeded compatibility consumer, executable named-record order mapping with independent field oracle and mutation controls, migration/ADR closure and final ordinary-jar/full gates. Current slices do not satisfy the complete ticket.
