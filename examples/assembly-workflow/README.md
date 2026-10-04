# Five-task assembly workflow

This fixed overlay turns a fresh independent `templates/secured-api` copy into the application produced by the measured five-task exercise, with explicitly separate post-benchmark qualification. It adds application-owned hello, quote, failure, inventory and CSV-import endpoints. It does not regenerate an existing business application or change the runtime. Use [the evidence report](../../docs/verification/ticket-31-template-upgrade.md) for exact measured artifacts and limitations.

From the repository root, with JDK25 and the checked-in Maven Wrapper3.10.0:

```text
java templates/Instantiate.java templates/secured-api /absolute/new/workflow-app
java examples/assembly-workflow/Apply.java examples/assembly-workflow /absolute/new/workflow-app
```

`Apply` checks all declared text preimages (CRLF normalized to LF) and new-file absence before any write. It refuses modified targets or reapplication. This is a fixed, reviewable example overlay, not an upgrade/synchronization engine. The independent application keeps its original template marker, documentation, tests and 88% instruction/line and 75% branch coverage gates. The overlay adds exactly the listed source and owned-document files in `preimages.properties`; inherited source is not duplicated here.

Install the current ordinary runtime with the repository Wrapper `clean install`; then in the new application run `mvnw.cmd clean verify` on Windows or `./mvnw clean verify` on Linux. The generated Wrapper is independent of this repository. Verification requires `PG_BIN` pointing to PostgreSQL18.6 native tools (or the inherited documented database fixture mode). All original tests run. Test-only synthetic inventory/staging defaults are declared in Surefire; they are not production defaults. The optional Tika4.1.0 dependency is test-scoped solely for the separately named type-aware pipeline qualification.

For a running application provide these additional required values:

| Variable | Ownership and meaning |
|---|---|
| `BENCH_UPSTREAM_BASE_URL` | Application-owned HTTP(S) inventory base URL; GET `/inventory/{sku}`. |
| `BENCH_UPSTREAM_CREDENTIAL` | Separate upstream bearer credential. The caller JWT is never forwarded. |
| `BENCH_STAGING_DIRECTORY` | Application-owned writable directory; each import creates and cleans its own child. |

Also supply the independent application's documented PostgreSQL and JWT issuer/audience/resource settings; see its copied README and `OBSERVABILITY.md`. Start `java -jar target/secured-api-1.0.0-SNAPSHOT.jar` with local or production configuration as documented there. This ticket performs no deployment and supplies no real secrets. For production, provision trust, database, writable staging and upstream policies explicitly; the executable contains neither development signing fixtures nor coverage/Tika test dependencies.

All `/api/bench/**` operations require the existing `greeting:read` scope. Quotes use application-owned field and arithmetic rules. Errors have fixed safe problem details. Inventory uses Boot's managed `RestClient.Builder`, the host observation/customizer policy and `ResponseBodyLimit`; it allows one admission, no application retry, 4096 actual response bytes and a 500ms transport/body deadline. A transport failure after the application deadline maps to the established timeout response even when Spring closes the body without a timeout cause. Redirects are disabled. This finite example does not define a general partner SDK.

The HTTP import accepts exactly one total multipart part named `file`, enforces 4096 actual source bytes through `SafeUpload`, parses strict CSV with bounded rows/fields and then applies business limits. It intentionally uses **size plus CSV structure/business admission**, not MIME admission. `WorkflowTypeAwareUploadTest` separately composes the public size-and-type-aware upload API with real Tika detection, exact saved bytes/hash, single-source ownership and CSV parsing. Current runtime `UploadTypeIntegrityTest` supplies broader seeded type/probe evidence. Neither result is substituted for the other.

`WorkflowImportInterruptionTest` exercises the same public import operation using a cooperative source in platform/virtual workers. Before interruption it observes a real staged regular file containing the emitted22-byte prefix and unchanged unrelated paths. It then requires input close, preserved interrupt status, actual worker exit within3s, complete directory restoration and successful subsequent import. This does not claim arbitrary socket disconnect cancellation or cancellation inside every parser. Fixture code is test-only.

`java verification/Verify.java all` from the library repository builds another independent copy, retains all inherited and added suites/coverage/model/dependency evidence, checks its nested ordinary runtime byte identity and runs the final executable via JDK-only `WorkflowConsumer` against owned issuer/PostgreSQL/upstream fixtures in platform and virtual modes. Artifact role `assembly-workflow` has its own manifest and executable even though its generated application GAV matches the base template. Deliberate negative probes remain outside positive report directories.
