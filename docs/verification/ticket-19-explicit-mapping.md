# Ticket19: explicit mapping and bounded legacy copying (in progress)

Baseline integration418e26f, synchronized2b06f52 before implementation; branchcodex/ticket-19. Logs stay in`.verification-results/ticket-19`, outside target. ADR0042 is Proposed. Formal19 and Q01–Q10/J14 govern this work; no unexecuted scenario is marked passed.

| Round | Public contract | RED | GREEN |
|---|---|---|---|
|01|autoCopy must never reflectively replace a final field or run the target constructor before rejecting it|red-01-final-field.log: expected rejection missing|green-01-final-field.log:25 tests|
|02|recursive CopyTrait→autoCopy cycle rejects and the same source can be copied after the cycle is removed|red-02-cycle.log: recursion reached the test's32-call safety guard|green-02-cycle.log:26 tests|
|03|legacy reflective nesting is explicitly limited to32 active sources;31/32 pass and33 rejects|red-03-depth.log:33 was accepted|green-03-depth.log:27 tests|

These tests call CopyUtil, not private guard methods. The cycle RED deliberately stops after32 calls rather than exhausting the host stack. Existing historical shallow-reference, null-default, array, generic collection and exception policies remain in the regressions.

Remaining: concrete-container/comparator/alias/null policy matrix, bounded retained copying and cleanup, safe standard logging, executable named-record order mapping with independent field oracle and mutation controls, migration/ADR closure and final ordinary-jar/full gates. Current slices do not satisfy the complete ticket.
