# Secured MVC API

This independent application verifies Bearer JWTs and passes the verified issuer/subject pair to application business operations. It uses Spring Boot 4.1.1, Spring Security 7.1.1 and the ordinary `server-facility` jar. The `notes` Module owns current workspace membership, CRUD invariants and transactions through PostgreSQL 18.6, Flyway 12.4.0 and Spring JdbcClient. It does not implement an identity provider.

## Create and build

Prerequisites: JDK 25 on `JAVA_HOME` and `PATH`, PostgreSQL 18.6 native tools on `PG_BIN`, network access to Maven Central for the first build, and the selected `cn.code91:server-facility` version in a reachable Maven repository. The checked-in Wrapper downloads Maven 3.10.0 and checks its SHA-256. JDK 21 and other Maven versions fail validation. Tests bind ephemeral loopback ports, create a private PostgreSQL cluster and generate disposable RSA keys; they need no external account, Docker or credentials. Missing PostgreSQL fails the tests, rather than skipping them.

After creating the independent application below, use its JDK-only helper on Windows/Linux x86_64 to download a pinned native distribution from Maven Central and verify its checked-in SHA-512 before extracting with the OS `tar`. Supply a new ASCII directory outside this application (for example `C:/Temp/my-api-pg` or `/tmp/my-api-pg`):

```text
java dev/PreparePostgres.java /absolute/new/tools-directory
```

Set `PG_BIN` to the printed `bin` path (`$env:PG_BIN='C:/Temp/my-api-pg/bin'` in PowerShell, `export PG_BIN=/tmp/my-api-pg/bin` in a POSIX shell). This step only prepares tools. Existing PostgreSQL 18.6 tools are also supported. Keep native tools and database state paths ASCII on Windows; the application itself is verified in a path containing spaces, Chinese and Hebrew characters. Linux tests must run as an ordinary user, as PostgreSQL refuses `initdb` as root.

For this source snapshot, first run `./mvnw clean install` in the facility repository. On Windows use `mvnw.cmd` wherever these instructions show `./mvnw`. Then create a fresh directory:

```text
java templates/Instantiate.java templates/secured-api /absolute/path/to/my-api
```

The destination must not exist. The copy includes its own Wrapper, POM, tests and development helper; it excludes build output. Change to that directory and run:

```text
./mvnw clean verify
```

This runs actual HTTP, key rotation, PostgreSQL migrations, transaction rollback, lock/connection budgets, database outage/recovery, compiled business-boundary checks and coverage gates (88% instructions/lines, 75% branches). Test database shutdown failures fail the suite. The executable artifact is `target/secured-api-1.0.0-SNAPSHOT.jar`. There is no parent POM or reactor dependency on the source checkout. Rename coordinates/packages and own the generated application as normal application code; this is a copy operation, not a synchronizing generator.

## Run locally with real signatures

After building, run the disposable fixture in one terminal:

```text
java dev/LocalIssuer.java .local
```

`.local` must be a new directory. The fixture binds only loopback, generates a fresh in-memory RSA private key, writes two ten-minute test tokens and `local.properties`, and serves read-only metadata/JWK documents for ten minutes. It provides no login, token-issuance HTTP endpoint or refresh service. These files are ignored by Git; the helper is outside `src` and absent from the application jar. Restart with a new directory to generate new fixtures.

Start the development database in a second terminal, using a new ASCII state directory with an existing parent:

```text
java dev/LocalDatabase.java /absolute/path/to/my-api-database
```

This foreground helper binds only `127.0.0.1`, uses development-only trust authentication and writes `database.properties`. Press Enter to stop it cleanly. Reusing its marked directory restarts the same database; its data is retained. It refuses unrelated existing directories and an active database. Never expose this trust-authenticated cluster or use this recipe for production. Use an application-owned database, authenticated role, secret management and backup/restore policy in production.

The helper uses PostgreSQL's `pg_ctl start -w` for native startup and readiness, including the restricted-token launch required on Windows administrator accounts. Startup has a 15-second native wait and 30-second process limit; fast shutdown has a 60-second native wait and 70-second process limit. Native logs remain in the owned state directory; a failure includes a bounded diagnostic tail. Closing the foreground owner removes the readiness file and stops its database. Do not forcibly terminate the owner: a forced operating-system kill cannot run JVM cleanup; use `pg_ctl -D <owned-state>/data -m fast -w -t 60 stop` for explicit recovery before restarting.

In another terminal, use both generated property files (file URIs may be percent encoded for spaces):

```text
java -jar target/secured-api-1.0.0-SNAPSHOT.jar --spring.config.additional-location=file:./.local/local.properties,file:/absolute/path/to/my-api-database/database.properties
```

`GET http://localhost:8080/health` returns `{"status":"UP"}`. Business `GET /api/greeting` requires the token. PowerShell:

```powershell
$demoToken = Get-Content -Raw .local/token.txt
curl.exe -H "Authorization: Bearer $demoToken" http://localhost:8080/api/greeting
```

POSIX shell:

```sh
curl -H "Authorization: Bearer $(cat .local/token.txt)" http://localhost:8080/api/greeting
```

The result contains `actor.issuer`, `actor.subject` (`local-demo`) and `message` (`Hello`). No token returns 401; `no-scope-token.txt` returns 403. A token in the query string or a spoofed identity header does not authenticate. Stop the API and issuer with Ctrl+C; stop the database with Enter. Local configuration is an explicit loopback trust policy, never an authentication bypass.

## Persistent business operations

The local token includes `notes:read notes:write`. Production issuers must grant these operation scopes deliberately. `POST /api/workspaces` with `{"name":"My workspace"}` creates a workspace and the caller's membership in one transaction. Its relative `Location` is the workspace's notes collection. Creating a workspace does not grant access to any other workspace. Membership is keyed by workspace plus verified issuer/subject and is checked anew on each Module operation; JWT scope alone is insufficient. No incoming header selects an authenticated actor.

Use the returned collection URI:

| Method | Path | JSON / result |
| --- | --- | --- |
| POST | collection | `Idempotency-Key: create-first-note` and `{"slug":"first-note","title":"Hello 🌱","body":"Persist me"}` → 201, relative note Location |
| GET | note Location | 200 with `id`, `workspaceId`, `slug`, `title`, `body` |
| PUT | note Location | `Idempotency-Key: update-first-note` and `{"title":"Updated","body":"Same note"}` → 200 |
| DELETE | note Location | 204; subsequent GET → 404 |
| GET | collection + `?page=0&size=20&sort=created&direction=asc` | `{items,page,size,total}` |

Workspace names contain 1–100 Unicode code points; titles 1–200; bodies 0–4096. Names/titles cannot be blank. NUL and unpaired surrogates are rejected. Slugs use 1–64 lowercase ASCII letters/digits/hyphens and start with a letter/digit; they are unique within a workspace and immutable. A different command creating an existing slug returns409 without changing the prior note. Retrying a successfully committed key and command restores its original result. Body JSON is bounded by the application's Jackson factory: 65,536 document bytes, 16 nesting levels, 16,384-character strings, 128-character field names, 64-character numbers and 4,096 tokens. These parser limits also apply to chunked input.

Note POST/PUT callers must now provide exactly one bounded ASCII request key. The public Notes Module likewise requires an explicit key and owns its transaction: invoke it outside another transaction. See [the command protocol](COMMANDS.md) for scope/fingerprint, current authorization, original-result replay, lifetime workspace capacity, receipt expiry and safe retry rules. Workspace creation and DELETE retain their existing contract.

Page is zero-based, size is 1–100, and `page * size` must not exceed 10,000 (overflow is rejected). Allowed sorts are `created`, `title`, `slug`; direction is `asc` or `desc`. Only this fixed mapping enters SQL. Every sort has an ID tie-breaker. Invalid values return 400, empty results keep the same structure, and count/rows share a read-only repeatable-read snapshot. The public Module has no Servlet, SecurityContext or static SessionUser dependency; it owns authorization and uses bound JdbcClient values.

Restart the API with the same database and issuer policy to read committed notes again. The disposable issuer creates a new issuer URI/key when restarted; it intentionally does not promise a permanent development identity. Production identity continuity belongs to the configured issuer.

## Database migrations and finite budgets

Only Flyway manages schema (`spring.sql.init.mode=never`); there is no H2 fallback, ORM schema creation or second initializer. Startup migrates the same Hikari DataSource used by the Module, validates checksums and rejects a partial target, ignored future migration, missing/empty migration directory or missing required V1/V2/V3 history. A separate `spring.flyway.url/user` DataSource is unsupported. V1 creates workspace, membership and note; V2 adds input constraints and the default list index; V3 preserves identity text while adding bounded membership indexing, command identity, receipts and the workspace command budget. The test-only frozen V1 is the first application checkpoint, with an independently written literal SQL sample; it is not a claim about a prior production release. Migration scripts are immutable after deployment; add a new version to change schema. Back up real data before upgrading and own migration privileges/rollout policy in the application.

Defaults: pool maximum 4/minimum idle 0, acquisition 1s, validation 500ms, initial connection 1s; driver connect 2s, socket read 4s and cancel signal 1s; PostgreSQL statement timeout 2s/lock timeout 500ms; JdbcTemplate query 2s; Module transaction 3s; Flyway lock retries 2/connect retries 0. Each is a stage budget, not a total HTTP deadline. Flyway's pinned retry implementation shares static policy, so applications in the same JVM must use the same finite migration policy. JDBC URL parameters are rejected because they can override driver properties; configure TLS and other driver settings explicitly through datasource properties.

Configuration validates finite ranges before migrations: pool 1–16; acquisition 250–5000ms; validation 250–1000ms; initialization 1–5000ms; driver connect 1–5s/socket 1–10s/cancel 1–2s; statement 100–10000ms/lock 50–5000ms with lock shorter; query 1–3 whole seconds; Flyway lock retries 0–5 with no connection retries. Fractional query durations are rejected because Boot converts that duration to integer seconds, which would turn subsecond values into zero. Zero/infinite or silently normalized settings fail startup. The effective migration policy is checked again before use.

Unavailable database/connection/statement budget produces safe503 `persistence_unavailable`; the command's unique-claim lock timeout specifically returns409 `command_processing` with a finite retry hint. Other lock failures remain503. Unexpected SQL/transaction faults produce safe500 `persistence_failed`; a known slug conflict produces409 `note_slug_conflict`. SQL, submitted payload, credentials and driver causes are neither returned nor passed into the facility error logger; the sanitized error retains the HTTP correlation identifier. A timeout does not prove that a write had no effect: note commands retain the same key when retrying. Exact commit-boundary process-loss recovery and receipt-cleanup evidence remain ticket30's responsibility.

## Production trust and HTTP policy

Set all three values, through application properties, command-line arguments or equivalent Spring environment variables:

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://identity.example/issuer
spring.security.oauth2.resourceserver.jwt.audiences=my-api
app.security.resource-uri=https://api.example
# Optional direct keys endpoint; issuer validation still applies:
# spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://identity.example/issuer/keys
# Default 2s; must be positive and at most 10s:
app.security.jwk-timeout=2s
```

Use `prod` (or no local profile). Missing trust fails startup. Production endpoints must use HTTPS, with no URI userinfo, query or fragment; the local profile additionally permits HTTP only on localhost/127.0.0.1/::1. These rules apply to discovered JWK endpoints too. Do not activate `local` in production; `local,prod` is rejected. Only one audience is accepted in application configuration. `public-key-location` is deliberately unsupported by this template policy.

RS256 is the only permitted algorithm. Standard issuer/audience/type and timestamp validation remain enabled. Expiry and a nonblank subject are required; `nbf` is checked when present with the standard 60-second clock tolerance. Health readiness does not imply the external issuer is reachable: discovery is deferred until a token needs it. Each metadata/JWK response is capped at 65,536 bytes with a total deadline covering the body. Redirects are rejected; request credentials and application HTTP interceptors are not forwarded.

Actor uses the decoder's normalized subject, not a separate raw JSON parser. In this pinned stack, signed numeric `sub:42` and string `sub:"42"` become the same identity `"42"` even before the standard processor's claims-verifier hook. The issuer must own an unambiguous subject namespace and should issue String subjects as RFC7519 specifies. This template verifies trust and the resulting identity; it does not promise stricter raw claim-type validation than the standard decoder.

Normal key rotation uses the standard Nimbus cache: published new keys are fetched when needed. Cached matching keys can work during an outage; a required unavailable refresh returns 503. Removing a key does not promise immediate revocation until cache refresh/expiry. Unknown/invalid keys after a successful fetch return 401. Plan overlap when rotating keys and use a separate explicit policy if immediate revocation is required.

Invalid or missing credentials return 401; insufficient operation scope returns 403; unavailable authentication dependencies return 503. Errors use facility's safe ProblemDetail, no-store and the active standard trace identifier (or a UUID incident reference when no trace scope is active). `WWW-Authenticate` has only fixed Bearer challenge values. Raw tokens, decoder causes, upstream text and untrusted host values are not returned. `/health` and Spring Security's protected-resource metadata at `/.well-known/oauth-protected-resource` are public. The latter uses the configured canonical resource URI. Other unlisted application routes are denied. The API is stateless, has no form/basic login, and disables CSRF for header-only credentials; changing the authentication transport requires revisiting that policy.

## Extend the application

Add operations to the application and explicitly authorize their routes in `SecurityConfiguration`. `greeting` is plain Java: business code accepts an `Actor` argument and has no Servlet, Security or static session-holder dependency. Additional `OAuth2TokenValidator<Jwt>` beans compose with the standard decoder. Change the application-owned configuration for different algorithms, scopes or trust policy; the library does not own these deployment decisions.

Platform threads are the default. `spring.threads.virtual.enabled=true` selects the tested virtual-thread mode. Servlet connections, platform worker counts and application executor concurrency/queue defaults are finite; these are capacity choices to tune with deployment measurements, not a throughput claim.

Callable uses Spring Security's MVC integration. DeferredResult producers have identity and trace only when submitted through this application's managed `AsyncTaskExecutor`. Its private Micrometer registry propagates this application’s ObservationRegistry scope and trace MDC; the standard Security wrapper restores worker identity. A small trace rollback guard also handles an accessor throwing after partial installation. Submit rejection never runs the task; completion, exception and cancellation restore worker state. Arbitrary third-party threads must receive the immutable `Actor` explicitly or use a deliberately chosen managed execution path. There is no global inheritable-thread-local identity strategy.

Forwarded headers are disabled at the Servlet layer. With no proxy configuration, the network origin is the numeric direct peer. Set `facility.web.proxy.trusted-proxies` to deployment-owned CIDRs to interpret X-Forwarded-For through facility's bounded chain policy. This changes network-origin interpretation only; verified JWT identity remains authoritative. Standard W3C traceparent is used only for correlation, never identity; the legacy X-Trace-Id header is ignored. Do not enable broad framework/native forwarded-header rewriting in front of this policy without revalidating the deployment boundary.

The repository verification runner instantiates into a new directory outside the checkout, builds with an isolated repository, checks the consumed library jar, archives the exact inputs and starts the executable jar with a JDK-only client in both thread modes. That client creates data, restarts the API, verifies literal stored content, and updates/deletes through signed HTTP against native PostgreSQL. See the source repository's ticket27/ticket28 verification reports for exact source SHA, environment, counts and CI evidence.

The same-JVM `RunningApp` test host shares a single SLF4J/Logback context. It serializes only the standard Boot logging listener's event callbacks to avoid concurrent reconfiguration of that shared context. Application refresh, database connections and Flyway migration callbacks remain concurrent; the migration test requires both applications to reach the native `BEFORE_MIGRATE` barrier and repeats that lifecycle three times. This fixture policy does not provide independent logging systems for two applications in one JVM. Production deployment uses one application per JVM; separate processes have separate logging contexts.

Coverage uses JaCoCo offline instrumentation to support Windows directories outside the native code page. Successful tests restore original bytecode before packaging; the coverage runtime is test scoped. After a failed or interrupted test run, use `clean verify` to discard instrumented leftovers. Architecture checks inspect the compiler's original bytecode, and isolated test subprocesses contribute to the same coverage file through the test-only runtime.

The application owns MessageSource (`i18n/application` before `i18n/facility-messages`) and translates at GreetingController, leaving the business module free of Spring. Boot Actuator and Brave/Zipkin provide standard tracing; export is disabled by default with `management.tracing.export.zipkin.enabled=false`. Enable a backend and choose sampling in deployment configuration. See [application observability](../../docs/building/application-observability.md) for safe logging, legacy migration and propagation boundaries.
