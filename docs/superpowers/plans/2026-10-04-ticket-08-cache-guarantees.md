# Ticket08: explicit local cache guarantees

Approved scope: FR02/05/08, AC02/08/09 and formal issue08. Start1d6377d after ticket12 integration. Reuse Spring CacheManager/Cache and Caffeine; no new generic cache facade or remote backend.

## Public seams and sequence

The user has approved public facility/consumer seams; no additional approval is needed. Tests use ApplicationContextRunner for public configuration, actual Spring Cache operations and annotation proxies, an injected Caffeine Ticker at the clock boundary, real worker/latch schedules, and independently resolved ordinary-jar dependency graphs. They do not inspect private maps or infer behavior from mock invocation counts.

Vertical slices: (1) default capability absence; (2) explicit selection and missing dependency rejection/user override; (3) fixed finite cache names and positive TTL/capacity inputs; (4) deterministic expiration and capacity maintenance; (5) public loading contract for same-key/null/failure plus legacy migration; (6) invalidation races/different-key parallelism; (7) two application and close ownership; (8) actual four dependency graphs/ordinary jar resource consumer; (9) repository consumer migration, full gates and review.

Preserve old public signatures where practical but deprecate static holder lookup; recommended use injects CacheManager and uses Spring Cache directly. Document loaded null, exception, invalidation/concurrent insertion and Caffeine eventual maintenance semantics honestly. A configured finite name set bounds manager growth; entry capacity is not a byte/weight bound on caller-owned keys and values. Host managers own their own policy and lifecycle.

## Evidence

Each new contract is written and observed before the minimal implementation change. If existing behavior already passes, record a positive contract without inventing RED. Logs live in .verification-results/ticket-08. Final clean all includes actual optional dependency graphs, constrained resource JVM and existing quality gates. Record exact source, environment and any failed attempt; Linux is a separate CI closure item. ADR0031 supersedes the relevant0015 fallback/default portions and preserves the Spring SPI/optional dependency rationale.
