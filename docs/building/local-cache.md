# Application-owned local caching

Ticket08 / ADR0031 uses Spring `CacheManager` and `Cache` as the public interface. Facility does not create a cache manager by default. Inject the application's manager into a constructor, or use Spring annotations with the application's `@EnableCaching` configuration.

## Explicit local selection

Add both `com.github.ben-manes.caffeine:caffeine` and `org.springframework:spring-context-support` to the application; the Boot BOM supplies their versions. These remain optional library dependencies. Select the local policy deliberately:

```yaml
facility:
  cache:
    enabled: true
    cache-names: [users, products]
    default-ttl: 10m
    maximum-size: 10000
```

This selection requires both dependencies. Missing either produces the stable startup error `Selected local cache requires Caffeine and spring-context-support`; there is no permanent-map fallback. Disabled/unselected applications start without these dependencies and receive no facility cache manager. An application-provided `CacheManager` always takes precedence, including on incomplete dependency graphs; that manager owns its own TTL, capacity, loading and lifecycle guarantees.

Names must be a nonempty finite set of distinct nonblank strings, at most 256 UTF-16 code units each, without ISO control characters. Unknown names return `null` and do not create more caches. TTL must be positive and fit exactly in positive signed-long nanoseconds; entry capacity must be positive. The defaults for a selected manager are 10 minutes and 10,000 entries **per declared cache**. All its declared caches share that policy. For different per-cache policies, provide a host manager.

```java
final class UserQueries {
    private final Cache users;
    UserQueries(CacheManager manager) {
        this.users = java.util.Objects.requireNonNull(manager.getCache("users"), "users cache required");
    }
    User find(String id) {
        return users.get(id, () -> loadUser(id));
    }
    // Application authorization and domain invalidation remain outside this example.
}
```

## Observable provider contract

The selected provider is Caffeine through Spring's adapter; no facility loading algorithm, scheduler or global cache is added. An injected Caffeine `Ticker` may supply an application/test monotonic clock. The default uses its system ticker. A read does not extend the write deadline; values are unavailable at the deadline even before maintenance physically reclaims an expired entry. `maximum-size` is an entry policy, observed after Caffeine maintenance (`cleanUp()` on the public native cache). Concurrent writes may temporarily exceed that number. This is not an instantaneous admission bound, a byte budget, or a bound on host loaders/keys/values. The total configured entry policy is the per-cache capacity multiplied by the finite configured name count.

`Cache.get(key, Callable)` coalesces a successful same-key load on this provider; null is a cached hit. A loader exception leaves no result and is reported as Spring `Cache.ValueRetrievalException` with its original cause. A subsequent request may try the loader again. Provider failures propagate and do not trigger an uncached business fallback. Values are shared references, not deep copies; callers own mutation policy. Concurrent eviction of the same key waits for the tested load and removes its result before the eviction returns. Different tested keys can make progress while one key is loading; this is not a promise that arbitrary colliding keys or all host backends never contend.

Ordinary `@Cacheable` does not imply a general same-key loading guarantee. Choose Spring's `sync = true` where its provider/annotation restrictions suit the application. Eviction, transaction timing, stale authorization/data and remote consistency remain application responsibilities. This cache is not an idempotency receipt store, an execution permit or a business uniqueness mechanism.

## Lifetime and failures

Every selected manager belongs to its application. It exposes the standard SPI through a private lifecycle adapter rather than promising the concrete `CaffeineCacheManager` bean type. `Cache.getNativeCache()` remains Caffeine's public native cache for maintenance/observation.

On close the manager first detaches its provider, stops publishing caches (`getCache` returns null; names are empty), and attempts invalidation/maintenance for every declared cache. Repeated close is harmless. The first cleanup failure remains primary; distinct later failures are suppressed. A failed provider operation cannot guarantee that the affected cache was cleared. The detached manager no longer owns that provider even in this failure case. Cleanup does not affect another application.

Hosts must stop new cache calls and quiesce in-flight loaders before closing. A borrowed `Cache` must not be used after its application's lifetime. Close does not cancel arbitrary business work: an initial load into an empty cache can finish after invalidation has returned, and other provider operations can wait for a loader. No hard close deadline is promised for uncooperative host work or clock callbacks. The facility manager detaches its large tables and loaded results; separately retained cache handles, loader tasks or values are the host's references.

Facility's cache path does not log keys, values, loader messages or stack traces. Spring/Caffeine and application backends can propagate or log their own exceptions, including a failing host ticker during maintenance. Supply safe adapter exceptions and configure host logging as appropriate; this contract does not sanitize all third-party loggers or `Cache.ValueRetrievalException` contents.

## Migration

- Replace static `CacheUtil` lookup with constructor injection. Its public signatures remain binary compatible and are deprecated. It creates no second cache. Missing manager/name preserves only its historical direct-loader/no-op behavior; that compatibility path is outside selected local guarantees.
- `CacheUtil.getOrCompute` now delegates to `Cache.get(key, Callable)`, so cached null is a hit and loader failures follow Spring's exception contract. Single-flight behavior belongs to the selected backend, not every `CacheManager` implementation.
- Explicitly enable and name local caches. Code relying on automatic registration, dynamic names or missing-dependency permanent maps must migrate. `@EnableCaching` still belongs to the host.
- Do not inject/cast the facility bean as concrete `CaffeineCacheManager`. Inject `CacheManager`; provide your own bean if you need provider-specific manager mutation.
- The old `FacilityCacheAutoConfiguration.caffeineCacheManager(properties)` factory signature remains deprecated and returns the same owned SPI; explicit callers must supply valid names and close its `DisposableBean` lifecycle or register it as a managed bean. The old `concurrentMapCacheManager()` signature remains but deliberately throws the stable dependency error instead of creating a misleading permanent fallback.

Evidence and remaining platform gates are recorded in [ticket08 verification](../verification/ticket-08-cache-guarantees.md). Historical ADR0015/0047 evidence does not stand in for the new actual dependency matrix.
