# Final independent review of server-facility-next

Fixed implementation source: 2d14f6a79a05e4e693a7d0e4abb83c4890653e45. Baseline: 0ee9d547022371ad31f885605e999de17ec22777. Both reviews ran independently after the same-candidate CI25 gates passed. The original reports below retain their review-time limits; later local verification is recorded separately in the release evidence.

## Standards

Source: `2d14f6a79a05e4e693a7d0e4abb83c4890653e45`; baseline: `0ee9d547022371ad31f885605e999de17ec22777`. Repository: `E:/GenCode/server-facility`; clean source identity verified at start and end. Range: `git diff 0ee9d547022371ad31f885605e999de17ec22777...2d14f6a79a05e4e693a7d0e4abb83c4890653e45`. All 339 supplied commit lines read.

**No remaining actionable documented-standard breach or Fowler-smell finding.** This is the final fixed-range Standards review, including the actual repair implementation, not a renamed preliminary report. Review covered the runtime, application/template/examples, verification and CI changes, with renewed inspection of failure/resource ownership, positive budgets, application-owned services and nullable public contracts.

Previous findings independently resolved in source (runtime paths below relative to `src/main/java/cn/code91/facility/`):

- **S1:** `async/AsyncExecution.java:125` captures MDC inside the failure boundary; `:142–150` restores before completion, preserves the primary Throwable, suppresses distinct cleanup failure and avoids self-suppression. `async/DefaultAsync.java:164–165` also converts caller snapshot failure to Err. These satisfy ADR0026 decisions 5–6; inspected the isolated throwing-MDC regression sources.
- **S2:** `io/Zipping.java:55,98,148` rejects normalized root destinations before staging in both entry points. The expected failure now remains Result under DESIGN C1 / ADR0037. Inspected the two root-destination regression cases.
- **S3:** Checked the A/A2 nullable inventory against the actual annotation diff, including `web/exception/FacilityHttpErrors.java:57`, `idempotency/IdempotencyRecord.java:20,33`, and generated property annotations at `idempotency/FacilityIdempotencyProperties.java:37–43`. Required collaborators and nonnull containers remain distinct. Narrow companion coverage and untouched legacy exclusions are proportionate to C5. ADR0010's pure-JDK error package and ADR0043's JDK-only example take precedence through README's authority order.

Applied DESIGN §7, CONTEXT, README authority, ADR index/applicable decisions (including 0056), and template GLOSSARY. No additional AGENTS/CONTRIBUTING/standards files found. All twelve supplied Fowler heuristics were considered as judgments, with repository overrides and tool-enforced rules excluded.

Limits: read-only source review; no tests, Maven or network executed; no independent final Spec findings consulted. Local CI25 receipts record run `37203623059`, attempt `1`, exact-source both-OS all/platform/qualification and four-role comparison success. I did not treat the separate resumed local run as passed or independently reproduce those executions. This result does not itself close ticket 33.

## Spec

Source: `2d14f6a79a05e4e693a7d0e4abb83c4890653e45`; baseline: `0ee9d547022371ad31f885605e999de17ec22777`.
Exact range: `git diff 0ee9d547022371ad31f885605e999de17ec22777...2d14f6a79a05e4e693a7d0e4abb83c4890653e45`.
Clean HEAD confirmed at entry and completion. Read all339 supplied commits; change set contains772 files. This is a new final review, not the earlier e85 pre-review relabeled.

**No actionable Spec findings.** No remaining supported missing/partial requirement, incorrect implementation of a requirement, or unrequested scope was identified against PRD v0.2, all33 approved tickets, FR01–FR10/AC01–AC15, Q01–Q10 and J01–J17. FR11/AC16 remain conditional.

Rechecked the actual prior-finding repairs and new regressions:

- F1: `AsyncExecution.java:121–150` now catches worker MDC capture/setup/restore failures and completes the result after cleanup; `DefaultAsync.java:164` also represents caller-capture failure. Original action failure remains primary, distinct cleanup failure is suppressed, and self-suppression is avoided. Seven isolated SPI cases exercise the failure paths, adjacent reuse and termination, satisfying the reviewed ticket03/D06 terminal-outcome gap.
- F2: `FacilityHttpErrors.java:165–191` constructs validation fields from structured path metadata; flattened paths retain only their trusted prefix. Real HTTP tests cover ordinary, injected-bracket and nested Map/List keys and direct constraint violations, plus rejected-value privacy. The earlier ticket04 secret-reflection gap is closed.

The broader review traced application-owned security/transactions, current authorization before replay, permanent command identity and receipt cleanup, streaming/capture, lifecycle/lock ownership, external HTTP, file/format budgets, compatibility/upgrade consumers and qualification wiring. The previously documented committed-response/container-log boundary remains explicit.

Local copies of CI25 public metadata/notices identify source2d14, run37203623059, attempt1: bothOS all/platform/qualification and four-role comparison PASS, with1790/15/132/153 tests each. Frozen qualification code binds current-runtime historical upgrade and inventories to that candidate. Historical benchmark, older runtime upgrades and previous candidate executions remain separate.

Limits: static source/test/evidence review; no tests, Maven, network calls or source edits performed. Raw remote archives were not downloaded/read. The fresh local all/platform execution remains independently pending and is not claimed PASS; root must reconcile it and complete ticket33 evidence/status closure. No final Standards report was consulted or findings exchanged.

Standards: 0 findings, no outstanding severity. Spec: 0 findings, no outstanding severity.
