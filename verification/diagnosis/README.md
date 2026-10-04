# Bounded NoteIdentityStorage diagnosis

This branch is a diagnostic experiment, not a release candidate or a product fix.
Its baseline is `ee57dfae21419890f5cf7de6ccd16491ba80f93c`. CI23 run
`37196719190`, attempt1, Linux job `111420034366` failed `workflow-build` with a
public annotation identifying `com.example.api.NoteIdentityStorageTest` and
`java.lang.AssertionError`. The annotation does not identify a method or line.
Raw CI23 logs/artifacts were unavailable (403). No cause is assigned from that evidence.

The diagnosing-bugs and TDD skills are applied at the already approved signed
HTTP / public Notes Module / actual PostgreSQL seams. The immediate task is to
produce a sharp, actually runnable red signal. No production behavior, assertion,
threshold, timeout policy or skip setting is changed. This diagnostic loop is
not yet executed on Linux when committed.

## Fixed campaign

Run from a fresh Linux checkout:

```sh
python3 -B verification/diagnosis/NoteIdentity.py run
```

The dedicated workflow triggers only a push to `codex/ticket-33-diagnosis`.
The existing candidate workflow is unchanged and does not trigger on that push.
The job has a20-minute total limit; the harness has a17-minute campaign deadline,
individual command limits and bounded15-second TERM/KILL fallback for its own
process group. A timeout ends the campaign rather than treating incomplete
cleanup as success. There are four predetermined executions and zero retries:

| Case | Original classes, one reused fork | Trace control | Test command limit | Expected discovery |
|---|---|---|---:|---:|
| single-original | NoteIdentityStorageTest | Original behavior |180s|2|
| same-fork-prefix | BusinessBoundaryTest, DatabaseConfigurationTest, MigrationHttpTest, NoteCommandsHttpTest, NoteIdentityStorageTest | Original behavior |300s|24|
| fixed-trace-clear | NoteIdentityStorageTest | Fixed standard W3C trace without `bad` |180s|2|
| fixed-trace-bad | NoteIdentityStorageTest | Fixed standard W3C trace containing `bad` |180s|2|

The prefix is a deliberately bounded context/lifecycle workload, not a claim to
reconstruct CI23's unobserved filesystem test order. Classes execute in explicit
alphabetical order. Surefire is configured with `forkCount=1`, `reuseForks=true`
and Jupiter parallel execution disabled; reports retain distinct hashes of the
actual `sun.java.command` property to check that the selected suites used one
fork launch identity. Method order is left unchanged. Expected30 total cases
include8 invocations of the original two target methods.

Expanded prefix counts are2/6/8/6/2. These were checked against source annotations
and ticket31's actual `20261004-172739-256-all/workflow/positive-surefire-reports`
XML files. The6 include DatabaseConfigurationTest's two parameter values; the8
include MigrationHttpTest's two parameter values and three repetitions. Prefix
method displays are not published. Only the target's two ordinary methods are
subject to the strict method-name and source-line allowlist.

Each case uses the actual `templates/Instantiate.java`,
`examples/assembly-workflow/Apply.java` and `TemplateLineage.java` commands to
produce an independent Unicode/space-path application with the real workflow
overlay. The original `Postgres`, `PostgresLifecycle`, `RunningApp`, issuer,
Flyway migrations and database budgets are unchanged. Pinned PostgreSQL18.6 is
prepared by the original downloader and fixture; database tests are not skipped.
Each test JVM owns its native cluster and test database cleanup normally.

The two target methods and their complete source file remain byte-exact in all
four cases, including every original assertion. The LF-normalized target hash
is `4639d29cd4ab388aa85f1d46ca65d5c758b293d3d59545fe6b745bf63e608a28`.
The original send helper hash is
`ecca8031970d0a260c9b9848b0b5b1bbff96b5dc626bdd4e86644098a9b9fd2e`.
Only the two control copies replace the single return in `NotesHttpTest.send`:
they add one fixed `traceparent` header and record integer observations from the
actual returned response, then return that same response to the original test.
The fixed traces are valid32-digit hexadecimal values; only one includes `bad`.
No authority, JWT, subject, request body or production tracing configuration is
changed. A red trace control could establish a test defect, but would not by
itself prove that CI23 failed for that same reason.

The ordinary runtime JAR is built into an isolated, campaign-owned Maven
repository with the original wrapper and these goals:

```sh
bash mvnw -B -ntp -C -s <root>/verification/settings.xml -gs <root>/verification/settings.xml \
  -Dmaven.repo.local=<private-repository> clean compile jar:jar install:install
```

This bootstrap does not run or qualify runtime tests or coverage. Each copied
application runs the original test lifecycle with the following fixed selector;
the prefix case changes only the selector to the five listed classes:

```sh
bash mvnw -B -ntp -C -s <root>/verification/settings.xml -gs <root>/verification/settings.xml \
  -Dmaven.repo.local=<private-repository> \
  -Dtest=NoteIdentityStorageTest -DforkCount=1 -DreuseForks=true \
  -Dsurefire.runOrder=alphabetical -Djunit.jupiter.execution.parallel.enabled=false clean test
```

No skip or ignore-failure flag is used. `verify` coverage/report checks are not
invoked by this focused diagnostic command and are not claimed as passed.
Every red result is retained, and later cases in the finite matrix still run.
Any red test leaves the diagnostic job red, including the intentional control.

## Evidence and disclosure boundary

Durable artifacts are limited to `.verification-results/note-identity/public/`:

- `preimage-and-plan.json`: baseline/diagnostic commits, planned size/count,
  fixed assertion-line allowlist, target/helper preimages and retention boundary.
- `results.json`: actual commands, cwd, start/end/elapsed/exit/budget/timeout,
  runtime JAR digest, copied target/helper hashes, exact discovered counts,
  source-validated target methods and allowed assertion lines, integer status
  comparisons, fixed control observation integers, database cleanup counts,
  report hashes and fork-launch hashes.
- `harness-error.json`, only on harness failure: fixed integer classification.

Each case's safe structured result is also emitted as a GitHub notice annotation,
so public check-run annotations can expose the signal without access to raw
logs/artifacts. This avoids another loop depending on inaccessible console logs.
Names are accepted only from the baseline-checked source. The two methods'
allowed assertion lines are15/16/22/34/35/36 and47/49/53/54. HTTP status extraction
is restricted to status assertion lines34/49/53. Unknown names/lines become
integer counts, never raw text. The control event schema contains only integer
status, trace equality/substring classification, body substring classification
outside the trace field, and the fixed `invalid_actor` code classification.

Raw Maven/native logs, full Surefire XML, raw exception/cause text and actual
response bodies remain runner-private and are **not uploaded**. The runner is
ephemeral, so those raw files will not be available later; their hashes bind the
derived facts but do not supply recoverable original contents. No runner-private
path is offered as durable evidence. JWTs/headers/arbitrary causes never enter
the public summary. Commands contain only fixed paths, selectors and budgets.

Preparation validation is a sanitizer self-test, not product RED/GREEN:

```sh
python3 -B verification/diagnosis/NoteIdentity.py self-test
```

Its first execution rejected an invalid33-digit trace fixture literal before
any external command. Static review then corrected the initial prefix count
that omitted parameter/repetition expansion; this was not an actual Maven red.
The corrected32-digit fixtures pass the source-line, untrusted-name and private
parameter-display non-disclosure controls. No local Maven execution is authorized
for this helper while the primary owns the candidate's full local gate.
