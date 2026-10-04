# Assembly workflow application

This application is the secured-api2026.10.0 template plus the ticket31 five-task example and separately recorded post-benchmark qualification. Runtime coordinate is0.2.0-SNAPSHOT; application version remains1.0.0-SNAPSHOT. The measured source ZIP (before later qualification) has SHA-256 `55e6ff4183d72699c58f8b5312695d3f217c48cf9d6715b4a5e6912636229c0f`. Current application source belongs to you; no synchronization or automatic replacement is provided. See [README](README.md), [upgrade policy](UPGRADING.md) and [observability](OBSERVABILITY.md).

Install or resolve the real `cn.code91:server-facility:0.2.0-SNAPSHOT` ordinary runtime from your approved artifact source; then run `mvnw.cmd clean verify` on Windows or `./mvnw clean verify` on Linux. The generated Wrapper is independent of this repository. Verification requires `PG_BIN` pointing to PostgreSQL18.6 native tools (or the inherited documented database fixture mode). All original tests run. Test-only synthetic inventory/staging defaults are declared in Surefire; they are not production defaults. The optional Tika4.1.0 dependency is test-scoped solely for the separately named type-aware pipeline qualification.

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


The full retained suite verifies application behavior, including the workflow-specific tests. Packaging produces the executable `target/secured-api-1.0.0-SNAPSHOT.jar`. HTTP authentication is inherited from the copied application; consult its README for local issuer and native database fixture commands. Start the issuer/database first, set the three additional variables above to explicit owned resources, and launch the executable with the generated local trust/database property files. The independently maintained repository qualification uses the executable itself with owned fixtures; development `spring-boot:run` success alone is not packaged acceptance. No production deployment has been performed.
