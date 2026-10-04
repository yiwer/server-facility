# Ticket31: versioned template and historical upgrade

Date: 2026-10-04. **Current candidate full gates and cross-platform CI remain required before closure.** The actual historical upgrade and both frozen five-task sequences are complete. The reusable workflow is derived from the immutable final new source, with additional qualifications explicitly excluded from common costs. Local full candidate evidence is recorded below as it completes; historical or measured-arm passes are not substituted for that evidence.

## Current artifact and copied application

Source `4a5025a8559df6390d4484d015031e6d08082fd1` was built by an actual ordinary `clean package` under JDK25.0.4.1 and Maven Wrapper3.10.0. Tests were explicitly skipped for this early benchmark input; it is not a full quality-gate run. Actual coordinates are `cn.code91:server-facility:0.2.0-SNAPSHOT`, size472789 bytes, SHA-256 `18e2c02dcf783eecbaadf12e8cce7ddc16bef6df64832feea55e935b3bc69ae5`. Maven jar metadata records0.2; no BOOT-INF appears in the library. The original jar was not relabeled.

The frozen runtime `src` tree is `cbd05bc5a1f74e6eb4546ef750846de1308d8d0d`, template tree `324cfccdbaf592445776d22978dc67d0f0bf7f28`, root POM blob `d9e480253c1269b044a62c0f10f8c78d9e34e074`. Immutable copied jar/POM/manifest/build log are in `E:/GenCode/server-facility-worktrees/coordination/ticket31-runtime-freeze`. Root owns a separate new-arm Maven repository; historical verification does not write to it.

The copied `secured-api` marker identifies revision2026.10.0 and the required runtime coordinate separately from the application's Maven version. Exact Git/artifact identities belong in external delivery evidence. `TemplateLineage.java` first failed on a real old copy with no marker, then passed against the updated independent copy: correct template/runtime line and12 local Markdown links. The former repository-relative observability link is now an application-owned document. The ordinary `Verify.securedTemplate` path calls this check before building the copy. Red/green logs and both unchanged copies are retained in coordination as `ticket31-lineage-*`.

Actual re-extraction of the frozen ordinary jar, compared with baseline `0ee9d547022371ad31f885605e999de17ec22777`/SHA `5ef94b3fc60274afd223070491b6d33dca39273df57d4923fd702eaf6ec6ca1f`, yields29 packages,162 reachable public/protected types and25 removed/replaced old descriptors. The [migration ledger](../building/runtime-migration-ledger.md), [complete API extraction](ticket31/api-ticket31-inventory.json) and [319-file package/source/consumer map](ticket31/runtime-package-map.json) distinguish source, binary, behavior, HTTP/wire, data and template compatibility. No blanket binary compatibility or arbitrary old-line sunset is claimed. Final33 must re-extract its own candidate.

## Historical custom application: actual execution

This is the historical template-only step `4a5ad5d2513ccf7d87f2feeb95056551b4b0ef15` → `307dae62ea63fce19c1630dfaf0fa0b811869a71`, not the old-runtime/new-template five-task comparison. The selected source trees are `24a021b3640e430279edd551415df771cf4f0543` → `c1ec08c15699da507463179dc2b4b7775d0fbfa3`; both have the same runtime source tree and POM. The sidecar explicitly says reconstructed development checkpoint, not past public release.

The unchanged historical runtime jar is `cn.code91:server-facility:0.1.0-SNAPSHOT`, SHA-256 `e97cedb5ab07ac9cabe638bf001ded6a5bb2f22ac521351fe09755d3ebc59595`. This was an intermediate Boot4 checkpoint before explicit coordinate separation; a SNAPSHOT name alone cannot establish platform or bytes. It was installed with its exact historical POM into the private historical repository. Both resulting executable jars contain these identical nested runtime bytes.

The real application owns a custom `/api/greeting/customer` controller, literal `orders-north` label, `customer-notes` application name, and a custom test source. Existing JWT `greeting:read` policy protects it. The sample starts with `1500ms`, which actually becomes1s. The guarded three-file historical patch changes the allowed policy to integral1–3s; the sample explicitly selects1s to preserve its old effective value. The upgrade changes exactly the three patch targets, one property line and the origin marker. Custom Java/test hashes and every unrelated configuration byte remain identical.

The [shipped recipe and tools](../../verification/template-upgrade/README.md) preserve a reviewed four-file test-host-only overlay identically on both sides. It fixes known shared logging/tracing fixture races and scopes fresh PostgreSQL database ownership; every original historical test remains. This is a declared overlaid historical suite. It is not described as untouched source. Current external issuer/database fixtures are separately frozen and do not overwrite historical application helpers.

| Executed stage | Actual result |
|---|---|
| Before `clean verify -Dupgrade.phase=before` |78 tests,0 failures/errors/skips; original architecture and coverage gates passed. Includes4 custom test invocations.|
| After `clean verify -Dupgrade.phase=after` |80 tests,0 failures/errors/skips; original gates passed. The two additional cases are the historical fractional/subsecond policy regressions.|
| Custom HTTP/configuration | Both stages: signed200 with exact label/name, anonymous401, insufficient-scope403; real context restart with the same test DB.|
| Real JDBC stage | Configured query1s and server statement2s; `pg_sleep(1.6)` outside a transaction throws `QueryTimeoutException`. Before platform/virtual observed1052/1033ms; after1033/1025ms. Subsequent `select42` and authenticated business HTTP succeed. Independent outer test timeout20s. No total-HTTP deadline is inferred.|
| Guard refusals | Wrong source, modified owned target and repeated application refuse with complete file manifest unchanged. Final actual upgraded sample reapplication leaves all319 files unchanged.|
| Packaged persistent restart | Historical before platform → upgraded platform → upgraded virtual share one live issuer/database. Each custom401/403/200 succeeds; the same note Location and full literal representation survive.|
| Process/database cleanup | Three application PIDs and issuer PID are confirmed exited. Windows `Process.destroy` is recorded and is not graceful-Spring-shutdown evidence. Native database owner exits0; postmaster PID absent.|
| Pure property helper |2 tests pass for literal/comment preservation and missing/duplicate/already-changed/noncanonical/escaped/continued-line refusal.|

Before application jar SHA-256: `8240309b89316b25334e757754abe606de60d7bf955cc5537ecb2791268356bf`. After: `7842f94dce345b2cc2ca5405ac8c4718cd464dcb7055654a07f7e64f84a6ae73`. The [before](ticket31/historical-before-summary.json) and [after](ticket31/historical-after-summary.json) summaries retain exact coverage counts; both exceed88% instructions/lines and75% branches.

Environment: Windows11, Asia/Shanghai, JDK25.0.4.1, checked-in Wrapper3.10.0, PostgreSQL18.6 native tools, bounded ephemeral loopback fixtures. The sample and its separate Maven repository are under `E:/GenCode/server-facility-worktrees/coordination/ticket31-history-*`. Exact source archives, jars, test/coverage reports, schema/cleanup diagnostics, effective POM/dependency tree, commands, fixtures and hashes are retained there. [Evidence manifest](ticket31/historical-evidence-manifest.json) indexes retained artifacts and excludes mutable dependency caches, database data and fixture token files.

## Retained failures and corrections

The draft upgrade helper's first actual refusal suite failed because inherited Windows Git autocrlf converted staged patch postimages. Exact-hash verification stopped before application writes. `04-upgrade-staging-red.log` preserves the failure; the helper now passes explicit `core.autocrlf=false`/`core.eol=lf` for its Git operations and the actual three refusals pass in `06-refusals.json`. The originally prepared overlay omitted the tracing fixture's `com/example` prefix; the corrected exact four-file overlay was frozen before any application build. Both versions remain in evidence.

The first historical Maven command used an unquoted PowerShell dotted `-Dupgrade.phase` argument; Maven refused the resulting `.phase=before` token before compiling or testing. `08-before-verify.log` preserves this invocation error. The corrected quoted command is the recorded successful `09-before-verify.log`; `12-after-verify.log` records the after run. No test failure was discarded or relabeled as product TDD RED. The documented before/after test counts are the actual successful full suite discoveries.

## Actual five-task comparison

The [checked-in comparison JSON](ticket31/benchmark-comparison.json) is the exact coordinator-generated report, SHA-256 `df3a9a6113bbdc464133c9c664b41f28b80a030075899cc96ecd7ee379dcbce0`. A separately reviewed read-only summarizer passed21 happy/negative qualification checks before producing it. It validates paired controller/gate events, build-to-source binding, Task1 distinct generations with identical executable bytes, start/stop jar identities, both nested runtime identities and exact source archive members. [Provenance](ticket31/benchmark-provenance.json) locates the retained1318-file/306889855-byte corpus and its SHA-256 manifest; dependency caches, native data and private signing keys are not copied into this report.

Each fresh implementer received the same five task contracts and finite acceptance procedures on the same Windows host/JDK/Wrapper, with deliberately different old runtime/minimal app and current runtime/template inputs. Root invocation serialization, overlap, queue time, host load and dependency caches differ. This is one implementer per arm, not a statistical sample or token measurement. Bootstrap/root messages and explicit emitted reads are separately recorded. No percentage improvement, speed claim or broad productivity conclusion is inferred.

| Recorded value | Old | New |
|---|---:|---:|
| Explicit reads / emitted UTF-8 bytes |31 /239989|45 /250232|
| Recorded bootstrap/root messages / UTF-8 bytes |22 /9331|25 /9701|
| First explicit read to final recorded invocation, seconds |3678.837331|4527.606646|
| Intentional TDD unsuccessful commands |8|9|
| Build failures / designated acceptance failures |0 /0|0 /0|
| Final selected tests (fail/error/skip all0) |8|14|
| Final source archive members |16|111|
| Final executable bytes |26518633|33272681|

All five designated task stages passed on their first attempt in both arms. Task1 contains development7 observations plus two distinct packaged generations of7 observations each using the same executable; Tasks2–5 contain10/2/6/18 observations. Thus57 observations per arm are recorded stages, not57 independent task trials. New Task4's observed slow case was504 in0.528s. The targeted14-test new build does not establish the inherited130-test suite or original coverage thresholds; those belong to the final reusable workflow qualification.

Old final executable SHA-256 `4fda1f03dfccb3d534856c4392baa4ee1d5c70cc784612c19742581ae9464263` contains the original ordinary runtime `5ef94b3f…6ca1f`. New final executable `ed06aedc07f9b305c8c7b556606affe395405c0571fb69552ad5122c9a69346e` contains the actual0.2 runtime `18e2c02d…69ae5`. Final new source ZIP SHA-256 `55e6ff4183d72699c58f8b5312695d3f217c48cf9d6715b4a5e6912636229c0f`; manifest `514f54d581882e16479688cb722a03d04c2620efe08bfd1f311a7bc3686db98d`. Both owned native databases and issuer finished, owner PASS at06:34:26UTC. Process termination is not graceful Spring shutdown evidence.

The frozen finite multipart gate checked two file parts, but did not check a file plus a non-file form part with the same name. Post-gate source review found the new controller counted only `getFiles`. This is a disclosed coverage gap; no frozen observation is rewritten as a failure. The immutable measured source, ZIP, JAR, ledgers and oracles are unchanged.

## Post-benchmark reusable workflow qualification

The [20-file application overlay](../../examples/assembly-workflow/README.md) preserves a fresh current template and all inherited tests, rather than retaining a second full template. Source hashes bind the exact inputs. A guarded fixed application checks all existing target preimages and new-file absence before writing; actual wrong-preimage and reapply attempts preserved the full independent file manifest. These preliminary18-file overlay commands, including the116-file result before adding the two owned-document entries, are retained under `coordination/ticket31-delivery-evidence/overlay-qualification`.

Only the following changes follow the accepted new source: exact total multipart named-part cardinality, a minimal public import-operation seam, Boot-managed outbound builder/observation plus4096-byte response budget/body deadline classification, test-only fixture defaults/dependencies, and additional qualifications. Application configuration, HTTP errors and business rules remain owned by the application. No runtime API or runtime source is added for this example.

| Qualification | Actual retained result |
|---|---|
| Mixed file/form same-name HTTP | `03-mixed-red`:400 expected,200 observed against accepted source. `04-mixed-green`: new regression plus3 retained import tests,4/0/0/0. |
| Actual oversized upstream body | `05-outbound-red`:502 expected,200 observed. `06-outbound-green`:4/0/0/0 after managed builder and actual-byte limiter. |
| Body deadline, blocked tail, host trace/customizer | `07-outbound-qualified` exposed504 expected/503 actual for a timed-out body read. Spring closes that stream without necessarily retaining `HttpTimeoutException`; application elapsed deadline classification fixed it. `08-outbound-qualified-green`:6/0/0/0, including retained common errors, real bounded-tail refusal while fixture remains blocked, subsequent healthy use, W3C trace propagation with a distinct client span, builder customizer and separate upstream bearer. |
| Actual application partial-write interruption | `09-import-interruption`:6/0/0/0 (3 retained imports,1 mixed,2 platform/virtual interruption). The real per-request staged file contains literal22-byte prefix before interruption; unrelated tree unchanged; source closes, worker actually exits within3s with interrupt retained, no staged results remain and subsequent import produces2 rows/total5. |
| Type-aware upload → CSV | `10-type-pipeline-red` is explicitly a missing optional Tika classpath prerequisite error, **not a behavioral product RED**. With explicit test-scoped Tika, `11-type-pipeline-green`:1/0/0/0. Simultaneous4096-byte/type admission refuses wrong type and oversize; one nonmarkable source is opened/closed once, saved bytes/hash equal the independently supplied literal CSV and strict parsing returns the three exact records. |

`02-mixed-red` is another retained invocation error: an unquoted PowerShell dotted `-D` argument was split before tests. Its corrected command is03. Commands/logs/positive or negative XML remain separately labelled; unsuccessful qualification attempts are not included in frozen common costs or positive suite totals.

The HTTP import intentionally performs size plus strict CSV/business admission. The separately named type-aware pipeline composes `SafeUpload` with Tika and CSV; current candidate runtime `UploadTypeIntegrityTest` additionally supplies48 seeded probe/byte-preservation cases and bounded detector refusal. This combination records the J12 paths honestly without pretending that `allowedMimeTypes=null` enforces MIME or weakening the actual partial-write precondition. Cooperative input interruption is not arbitrary socket disconnect or general parser cancellation.

## Requirement and quality-gate map

| Requirement | Evidence and remaining boundary |
|---|---|
| FR07 / AC03 | Current independent-copy marker/docs plus original template and new workflow consumers; historical actual executable persistent restart. Final current candidate all/CI recorded separately. |
| FR08 / AC11 |29-package/API/consumer ledger; accepted five-task source and20-file owned example overlay with business fields outside runtime. |
| FR09 / AC12 / J14 | Separate runtime/template coordinates; exact guarded historical patch, custom-source byte preservation and explicit configuration migration. Final33 candidate re-extraction remains separate. |
| FR10 / AC14 | Actual frozen old/new tasks, context/message/command costs, source/jar/fixture bindings and documented measurement limits. |
| AC15 / J11 external HTTP policy | Existing two-service partner consumer remains in all; workflow actual builder/customizer/host JSON/trace, separate credential, one finite-window admission, no retry, bounded body/deadline and healthy reuse. |
| J12 upload to parsing | Same-candidate runtime combined size/type tests; separate real type-aware upload→CSV byte/hash pipeline; endpoint strict CSV/business errors/cleanup and actual partial-file cooperative interruption/reuse. |

Q01 maps every changed seam above to its behavioral test and source. Q02 covers frozen literal field limits, Unicode/CSV/byte boundaries, exact multipart cardinality and overlay preimage refusal. Q03 uses independent ordinary/executable jars plus real HTTP, PostgreSQL and streams. Q04 uses explicit upload read and outbound-body barriers, finite deadlines and actual worker/process cleanup; original recovery tests remain. Q05 records4096-byte input/output,33 CSV records including header,2 columns,80 UTF-16 field units/40 code-point names, quantity1–1000 and finite worker/fixture budgets. Q06 retains original historical artifacts plus independent literal CSV/HTTP expected values. Q07 reuses runtime fixed-seed upload/parser tests and deterministic finite boundary cases; a fixed overlay does not need a random patch generator. Q08 archives source, GAV/SHA, environment, complete commands and errors without publishing private fixture keys. Q09 retains all original tests/architecture and88/88/75 gates, separates intentional negatives, and requires final all/platform and cross-platform CI. Q10 delivers source, ADR0054, migration policy and evidence together; ticket closure awaits those final candidate gates.

The first complete standalone workflow `clean verify` (`12-full-workflow-verify`,355.348s) discovered151 tests (130 retained template +14 accepted common-task +7 additional qualification),0 failures/errors/skips. It passed unchanged thresholds: instruction3082/3154, line427/437, branch296/343. This qualification used explicit synthetic BENCH environment values; final same-candidate all additionally validates the overlay's test-only Surefire environment for inherited child JVMs. It is not substituted for the following final candidate run.

## Current candidate full gates

Final local all/platform and merger CI evidence will be recorded here after completion. The added fourth artifact role is `assembly-workflow`; `workflow/artifact-manifest.json` records its actual executable SHA/size/GAV, nested runtime SHA/size/GAV and explicit positive suite/coverage paths. Matching the base application's generated GAV does not make the two artifact roles interchangeable.
