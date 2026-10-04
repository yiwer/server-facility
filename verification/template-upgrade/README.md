# Fixed historical template upgrade

This reproducible demonstration upgrades the actual development template `4a5ad5d2513ccf7d87f2feeb95056551b4b0ef15` to its direct child `307dae62ea63fce19c1630dfaf0fa0b811869a71`. Neither is presented as a public release. The source patch is three files, 11 additions/3 deletions, SHA-256 `9fab9045ce45106fd0296bd68f1826f3bd3ce51961c877e1b3aaf6fb89d58a5e`. It changes the JDBC query budget to integral 1–3 seconds; runtime source/POM are identical across this historical pair.

Use Python3, Git with those historical objects available, JDK25, the copied Maven3.10.0 Wrapper and PostgreSQL18.6 native tools on `PG_BIN`. The historical ordinary jar must have SHA-256 `e97cedb5ab07ac9cabe638bf001ded6a5bb2f22ac521351fe09755d3ebc59595` and its exact historical POM. The helper refuses any other bytes. Intermediate Boot4 checkpoints still used `0.1.0-SNAPSHOT` before the explicit line split; the coordinate by itself does not identify those bytes or their platform.

`HistoricalUpgrade.py` is a fixed demonstration, not a generic updater. Its property migration supports this historical canonical LF file format only; escaped property keys and continuation lines refuse before writes. Wrong lineage, an edited patch target and duplicate application leave the full file-byte manifest unchanged. Git patch staging explicitly disables inherited newline conversion. Multi-file writes attempt restoration after an ordinary write failure, but are not power-loss atomic. Own the sample directory exclusively, with application/build processes stopped during application of the patch.

## Execution recipe

Choose new absolute directories outside every repository worktree for `APP` and `EVIDENCE`, and a private Maven repository `REPOSITORY`. Substitute the actual absolute paths in these commands. Save stdout/stderr and exit codes for every invocation; do not overwrite a failed attempt's log.

```text
python verification/template-upgrade/HistoricalUpgrade.py check-history --repo REPO
python verification/template-upgrade/HistoricalUpgrade.py prepare --repo REPO --app APP --runtime-jar HISTORICAL_JAR --evidence EVIDENCE/before-inputs.json
python verification/template-upgrade/HistoricalUpgrade.py refusals --repo REPO --app APP --evidence EVIDENCE/refusals.json
```

Preparation creates an actual custom `/api/greeting/customer` controller and one custom test source. Its literal `orders-north` label, application name and JWT scope policy are application-owned. Initialize an application Git branch with local `core.autocrlf=false`. The marker is explicitly reconstructed historical provenance. Save the exact historical root POM with byte-preserving `git show` capture, then install the supplied jar and that POM into the private repository with `org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file`. Do not install a newly resolved mutable SNAPSHOT in its place.

This demonstration uses the identical, declared [four-file test-host overlay](test-host-overlay.patch) on both sides: serialized shared Boot logging callbacks, a read-only tracing scope observation, and paired per-test PostgreSQL ownership/lifecycle. These fix independently diagnosed host issues while preserving every original test and production file. Apply with `git -c core.autocrlf=false -c core.eol=lf apply --check PATCH` followed by `apply PATCH`; review [fixture provenance](fixture-provenance.json). This is an overlaid historical suite, not untouched-history evidence.

From the copied application, run the actual Wrapper `clean verify "-Dupgrade.phase=before" "-Dmaven.repo.local=REPOSITORY"`. Quote `-D` arguments in PowerShell. Keep original quality gates, all discovered tests and the custom test; missing PostgreSQL is failure, not a skip. Archive the source, Surefire/coverage reports and executable jar before cleaning. The custom test proves signed200/anonymous401/denied403, configured timeout1s, a real `pg_sleep(1.6)` cancellation with server2s outside a transaction, connection reuse, and both thread modes.

```text
python verification/template-upgrade/HistoricalUpgrade.py upgrade --repo REPO --app APP --evidence EVIDENCE/upgrade.json
```

Review exactly five changed files: the three historical patch targets, the canonical `1500ms → 1s` property and the origin marker. The chosen1s preserves the actual old truncated value; the helper does not round arbitrary settings. Custom Java/test hashes and every unrelated property byte must match. Run `clean verify "-Dupgrade.phase=after" "-Dmaven.repo.local=REPOSITORY"`; the unchanged custom test now requires an explicit1500ms override to fail startup. Archive the second executable jar and reports; verify both nested ordinary runtime hashes against the same historical jar.

For packaged proof, create an external fixture directory containing `dev/LocalIssuer.java` and `dev/LocalDatabase.java` from the declared current source in `fixture-provenance.json`; do not replace historical application helpers. Record their hashes. Run:

```text
java verification/template-upgrade/HistoricalPackagedUpgrade.java FIXTURE BEFORE_JAR AFTER_JAR EVIDENCE/packaged
```

The external JDK-only client starts the historical before jar, stores literal Notes data, then starts the upgraded jar in platform and virtual modes against the same issuer/database. Each generation requires identical custom behavior and stored representation. Process IDs, jar hashes and termination are recorded. `Process.destroy` on Windows is not claimed as graceful Spring shutdown. The native database owner must close successfully with no postmaster remaining. Fixture token files are local test credentials and need not be published with the source report.

Finally repeat the upgrade on the final sample and compare its complete byte manifest before/after refusal. Retain failures, corrections, source archives and environment evidence. `python -m unittest discover -s verification/template-upgrade -p test_historical_upgrade.py -v` checks the small canonical-property migration separately.

## Current-candidate requalification

Ticket33 can prepare a separate sample with `--runtime-manifest MANIFEST --runtime-jar CANDIDATE_JAR` on every helper operation. The manifest contains exactly `sourceCommit`, `sourceTree`, `pomBlob`, `coordinate`, `sha256`; the helper verifies Git source/POM, actual jar Maven metadata and checksum. Pass the exact current `-Dfacility.version=...` to every application Maven invocation. The resulting marker says `candidate-requalification`. Keep the original historical proof unchanged; this separate run qualifies the old template step against the new candidate and must not silently substitute historical bytes if it fails.

The three-file patch neither upgrades every historical app to template2026.10.0 nor migrates the later Notes schema. See the [current migration ledger](../../docs/building/runtime-migration-ledger.md) and [application upgrade guidance](../../templates/secured-api/UPGRADING.md).
