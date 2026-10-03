# ADR-0050: Application-owned JWT trust in an independent MVC template

## Status

Accepted. 2026-10-04. Ticket27, PRD v0.2 FR03/07 and AC03/04/07.

Extends ADR0027's public HTTP error policy to actual Spring Security and ADR0029's distinction between compatibility request context and authentication. No historical JWT consumer exists to migrate. The new template never uses SessionUserHolder as authentication; the legacy library interface is retained for existing consumers.

## Context

The library is an ordinary jar. A useful application needs an independently buildable entrypoint with an explicit trust policy, real authorization, safe401/403 and instructions that do not silently turn missing production configuration into anonymous access. Security7.1.1 defers issuer discovery and provides decoder customizers and validators; network unavailability is not equivalent to missing trust configuration.

## Decision

- Deliver one MVC template under `templates/secured-api`, with its own POM, Wrapper, executable Boot jar and application-owned configuration. The library does not acquire a Security dependency. Copying the template creates an independent project, without a reactor or repository test classpath. Template and runtime versions are recorded separately.
- Spring Security verifies Bearer JWTs, owns SecurityContext and authorizes explicit operation scopes. The business Actor is the validated issuer/subject pair, passed explicitly into application operations. No custom JWT parser, IdP, signing endpoint or user database is introduced.
- Production requires explicit issuer, audience and canonical resource URI. Boot's resource-server properties configure trust endpoints; application startup validates missing/invalid trust independently of lazy upstream discovery. A JWK URI is optional; when provided, issuer validation is retained. Default algorithms are explicitly RS256. Expiry and nonblank subject are required; nbf is checked when present. Standard validators remain composed.
- Actor uses the standard decoder's normalized subject. A real signed numeric `sub:42` and string `sub:"42"` both reach Nimbus's standard JWTClaimsSetVerifier hook as the String `"42"`, before Spring's MappedJwtClaimSetConverter. Neither of these ordinary extension points can distinguish the original forms. This template keeps Boot's deferred decoder and does not add a raw JWT parser. The trusted issuer must provide an unambiguous subject namespace and should emit RFC7519 String subjects. The collision is a checked HTTP/processor-hook golden, not a claim of strict raw JWT syntax validation.
- Use Boot's JwtDecoder, OAuth2TokenValidator beans and JwkSetUriJwtDecoderBuilderCustomizer. JWK I/O has explicit finite budgets. Key rotation follows the standard provider cache/refresh semantics; already cached keys can remain usable during an outage, while a required unavailable refresh fails closed. This is not an instant-revocation promise.
- The local profile explicitly selects loopback test trust with real RSA signatures. Its development helper is a disposable fixture, excluded from the application artifact and without OAuth login/token endpoints. No verification failure selects local identity or anonymous business access.
- Error adapters compose public FacilityHttpErrors with standard ErrorResponseException. Invalid credentials use401, authenticated permission failure403, unavailable authentication infrastructure503. The standard resolver accepts header credentials only; query/form tokens are disabled. Challenge headers contain only fixed RFC6750 values; original JWTs, descriptions, exceptions and attacker-controlled Host/URL values are not copied. API errors retain04's ProblemDetail, no-store and trace identifiers.
- Spring Security7.1 automatically serves nonbusiness protected-resource metadata before authorization. Its canonical resource value is owned by application configuration and is tested against Host/forwarded-header spoofing. Health and this documented protocol endpoint are public; business routes are protected and unlisted routes denied.
- Session storage/form/basic login are absent. CSRF is disabled for the header-token-only stateless API. Default forwarding is disabled; explicit trusted proxy configuration affects network-origin interpretation, never actor identity. Platform threads are default and virtual threads are opt-in.
- Callable uses native Security context integration. DeferredResult propagation is promised only for producers submitted through this application's managed executor. Arbitrary third-party producers must receive Actor explicitly. The application TaskDecorator uses DelegatingSecurityContextRunnable for identity and a private ContextPropagatingTaskDecorator registry containing only the configured trace key. No global registry, inheritable ThreadLocal strategy or static identity bridge is added.
- Boot4.1.1 manages Micrometer context-propagation1.2.1. Its DefaultContextSnapshot installs accessors sequentially and creates the restoring scope only after all setters return; a setter throwing after changing MDC does not roll back installation. A private registry does not change this fact. A local trace-key finally guard captures the worker value before installation, restores it on partial failure and preserves the original failure with any cleanup failure suppressed. The native Security wrapper independently restores worker identity even if trace installation or rollback throws. Isolated logging-provider fault processes reproduce the original leak and verify both failure paths; actual executor submit/reject/exception/cancellation and Servlet lifecycle tests verify normal ownership.

## Consequences

**Positive:** a generated application has one explicit authentication path, an ordinary Spring policy surface and independently testable packaging. It preserves the runtime's safe HTTP protocol.

**Negative:** deployment must supply trust/resource configuration and reachable keys when refresh is needed. Cached-key rotation has a bounded stale acceptance window; immediate token revocation requires a different explicitly selected deployment policy. Local fixture credentials are deliberately unsuitable for production.

**Carry-forward:** ticket28 adds persistent business authorization;29/30 add transactional receipt and recovery. Ticket31 records template upgrades;33 repeats final candidate combinations. No database guarantee is claimed by27.

## References

1. [Boot4.1.1 decoder configuration](https://github.com/spring-projects/spring-boot/blob/v4.1.1/module/spring-boot-security-oauth2-resource-server/src/main/java/org/springframework/boot/security/oauth2/server/resource/autoconfigure/JwtDecoderConfiguration.java).
2. [Security7.1.1 JWT provider](https://github.com/spring-projects/spring-security/blob/7.1.1/oauth2/oauth2-resource-server/src/main/java/org/springframework/security/oauth2/server/resource/authentication/JwtAuthenticationProvider.java).
3. [Security7.1.1 resource metadata filter](https://github.com/spring-projects/spring-security/blob/7.1.1/oauth2/oauth2-resource-server/src/main/java/org/springframework/security/oauth2/server/resource/web/OAuth2ProtectedResourceMetadataFilter.java).
4. [RFC6750](https://www.rfc-editor.org/rfc/rfc6750.html#section-3), [MVC asynchronous Security integration](https://docs.spring.io/spring-security/reference/servlet/integrations/mvc.html).
5. [Micrometer1.2.1 snapshot installation/restoration](https://github.com/micrometer-metrics/context-propagation/blob/v1.2.1/context-propagation/src/main/java/io/micrometer/context/DefaultContextSnapshot.java), [Security7.1.1 standard Runnable wrapper](https://github.com/spring-projects/spring-security/blob/7.1.1/core/src/main/java/org/springframework/security/concurrent/DelegatingSecurityContextRunnable.java).

Implementation and exact budgets/tests are tracked in ticket27 and its verification report; this ADR is not execution evidence.
