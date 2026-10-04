# Ticket31: versioned template and historical upgrade

Date: 2026-10-04. **Partial checkpoint: ticket31 remains open.** Active version/lineage, independent-copy documentation, API/package migration inventory and the real historical customization upgrade are implemented and verified below. Root separately owns the identical five-task fresh old/new comparison. Its new-arm result, the reusable upload/CSV consumer and cooperative-interruption qualification, and final current-candidate `all`/platform CI remain pending. Do not combine these historical passes with other candidates into a claimed current all-PASS result.

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

## Requirement and remaining-gate map

| Requirement | Checkpoint evidence / remaining work |
|---|---|
| FR07 / AC03 | Independent copy with usable docs/lineage; historical packaged persistent restart passed. Current candidate full copy/build/start consumer still requires final all/CI.|
| FR08 / AC11 |29-package/API/consumer ledger and application-owned custom source. Reusable five-task/uploadCSV example awaits fresh new-arm source.|
| FR09 / AC12 / J14 | Separate coordinates and markers; exact guarded historical diff, preserved customization, explicit configuration migration and actual same-runtime proof. Final33 current-candidate requalification remains separate.|
| FR10 / AC14 | Root retains one common fixed task and separate actual old/new arm logs; comparative result not yet available at this checkpoint. No improvement percentage is inferred.|
| AC15 / J11 | Prior partner consumer remains; actual five-task adapter product/result will be integrated after the new arm completes.|
| J12 | Controlled upload/CSV consumer, failure/cancellation cleanup and cooperative interruption qualification remain pending.|

Q01/Q03/Q06/Q08/Q10 are linked above to real copy/artifact/HTTP/database source and results. Q02 includes fixed-format ambiguity and exact-source/file refusal boundaries; generic property migration is expressly unsupported. Q04/Q05 include real query cancellation, recovery and finite fixture/database cleanup. Q07 uses deterministic fixed historical blobs, literal business data and complete byte manifests; random fuzz is not useful for this fixed patch. Q09 preserves historical gates and discovers all retained tests; **current candidate all/platform CI is still required**. No checkbox for the whole ticket is closed by this report.
