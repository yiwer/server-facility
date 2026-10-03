# Secured MVC API

This independent application verifies Bearer JWTs and passes the verified issuer/subject pair to a small business operation. It uses Spring Boot 4.1.1, Spring Security 7.1.1 and the ordinary `server-facility` jar. It has no database or identity-provider implementation.

## Create and build

Prerequisites: JDK 25 on `JAVA_HOME` and `PATH`, network access to Maven Central for the first build, and the selected `cn.code91:server-facility` version in a reachable Maven repository. The checked-in Wrapper downloads Maven 3.10.0 and checks its SHA-256. JDK 21 and other Maven versions fail validation. Tests bind ephemeral loopback ports and generate disposable RSA keys; they need no external account, Docker or credentials.

For this source snapshot, first run `./mvnw clean install` in the facility repository. On Windows use `mvnw.cmd` wherever these instructions show `./mvnw`. Then create a fresh directory:

```text
java templates/Instantiate.java templates/secured-api /absolute/path/to/my-api
```

The destination must not exist. The copy includes its own Wrapper, POM, tests and development helper; it excludes build output. Change to that directory and run:

```text
./mvnw clean verify
```

This runs actual HTTP and key-rotation tests, isolated context-failure subprocesses, compiled business-boundary checks and coverage gates (88% instructions/lines, 75% branches). The executable artifact is `target/secured-api-1.0.0-SNAPSHOT.jar`. There is no parent POM or reactor dependency on the source checkout. Rename coordinates/packages and own the generated application as normal application code; this is a copy operation, not a synchronizing generator.

## Run locally with real signatures

After building, run the disposable fixture in one terminal:

```text
java dev/LocalIssuer.java .local
```

`.local` must be a new directory. The fixture binds only loopback, generates a fresh in-memory RSA private key, writes two ten-minute test tokens and `local.properties`, and serves read-only metadata/JWK documents for ten minutes. It provides no login, token-issuance HTTP endpoint or refresh service. These files are ignored by Git; the helper is outside `src` and absent from the application jar. Restart with a new directory to generate new fixtures.

In another terminal:

```text
java -jar target/secured-api-1.0.0-SNAPSHOT.jar --spring.config.additional-location=file:./.local/local.properties
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

The result contains `actor.issuer`, `actor.subject` (`local-demo`) and `message` (`Hello`). No token returns 401; `no-scope-token.txt` returns 403. A token in the query string or a spoofed identity header does not authenticate. Stop both processes with Ctrl+C. Local configuration is an explicit loopback trust policy, never an authentication bypass.

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

The repository verification runner instantiates into a new directory outside the checkout, builds with an isolated repository, checks the consumed library jar, archives the exact inputs and starts the executable jar with a JDK-only client in both thread modes. See the source repository's ticket27 verification report for exact source SHA, environment, counts and CI evidence. Ticket28 adds persistent authorization; this template makes no transaction or retry guarantee.

Coverage uses JaCoCo offline instrumentation to support Windows directories outside the native code page. Successful tests restore original bytecode before packaging; the coverage runtime is test scoped. After a failed or interrupted test run, use `clean verify` to discard instrumented leftovers. Architecture checks inspect the compiler's original bytecode, and isolated test subprocesses contribute to the same coverage file through the test-only runtime.

The application owns MessageSource (`i18n/application` before `i18n/facility-messages`) and translates at GreetingController, leaving the business module free of Spring. Boot Actuator and Brave/Zipkin provide standard tracing; export is disabled by default with `management.tracing.export.zipkin.enabled=false`. Enable a backend and choose sampling in deployment configuration. See [application observability](../../docs/building/application-observability.md) for safe logging, legacy migration and propagation boundaries.
