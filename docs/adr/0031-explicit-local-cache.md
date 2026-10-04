# ADR-0031: Explicit Spring Cache capability with bounded local policy

## Status

Accepted — implementation decisions accepted; platform verification status is tracked separately in the ticket report.

日期：2026-10-04。

Supersedes ADR0015 only for default registration, silent permanent-map fallback, unbounded dynamic names and legacy check-load-put behavior. Preserve its choice of Spring Cache SPI, optional paired Caffeine/Spring support dependencies and user-manager ownership. ADR0047 remains historical platform evidence; new selected-cache semantics require fresh consumer verification.

## Context

The old default installs a CacheManager in every application. With missing Caffeine or Spring support it silently replaces TTL/capacity with an unbounded ConcurrentMap. CacheUtil separately checks, loads and inserts, losing the selected backend's same-key loading/null semantics. A dynamic manager also admits arbitrary names even when each cache has an entry bound.

The approved ticket requires explicit capability selection, honest dependency failure and public TTL/capacity/loading/lifecycle evidence. Spring Cache already supplies the public loading and invalidation interface; Caffeine implements local expiration, eviction and per-key coordination. Another generic API would duplicate these responsibilities.

## Decision

Recommend constructor injection of the application's Spring CacheManager/Cache or standard Spring annotations. Facility cache support is absent by default; explicit local selection must have Caffeine and its Spring adapter or fail clearly before use. User CacheManager beans take precedence and own their policy. No automatic permanent-map fallback.

Selected local caches have a finite configured name set, positive expiration and entry capacity. New cache names cannot grow the manager indefinitely. Expiration uses Caffeine's monotonic ticker; user ticker injection is a standard technology seam. Entry capacity is subject to Caffeine maintenance and is not a byte/weight bound for arbitrary caller values.

Loading, null, failure and concurrent invalidation behavior are documented against the selected Spring/Caffeine implementation and verified through public operations. CacheUtil remains deprecated compatibility, delegates loading to Cache.get(key, Callable), and does not create a second cache. Missing-manager legacy fallback is explicitly separate from selected capability guarantees.

A public close regression retrieved a previously loaded 1MiB value after the old application's close. The selected manager therefore uses a private lifecycle adapter holding the standard Caffeine manager; close detaches the whole provider before attempting every cache's cleanup, so a retained closed manager cannot retain payloads or provider tables. This is lifetime ownership, not a new caching algorithm. Concrete CaffeineCacheManager bean casts are no longer a contract; CacheManager/Cache and native cache access remain standard.

A real initial-load barrier also demonstrated that invalidateAll can return while the business loader is still running. Hosts must stop new calls and quiesce loaders before closing, and must not reuse borrowed Cache handles afterwards. Do not promise arbitrary loader cancellation or a hard close deadline. Cleanup failures preserve the first cause and attempt subsequent caches; third-party provider/logging behavior is outside facility log sanitization. Full public constraints and migration are in [local-cache.md](../building/local-cache.md).

## Consequences

**Positive**: standard Spring injection and explicit local policy replace unbounded implicit fallback; no extra global cache or scheduler.

**Negative**: applications must enable local caching deliberately and declare both optional dependencies, fixed names and their domain invalidation policy. User backends remain possible through Spring SPI with their own guarantees. No remote backend, business-authority cache, custom scheduler or new cache DSL is introduced.

**Carry-forward**: Linux CI and final batch review retain their own evidence gates. Business invalidation and host loader lifetime remain application responsibilities.

Legacy callers relying on default registration/dynamic names/no-expiry fallback must migrate rather than silently receive weaker semantics. Old signatures should be retained or explicitly refused with migration notes; consumers and historical expectations are updated alongside behavior tests.

## References

- [Formal ticket08](../../.scratch/server-facility-next/issues/08-cache-guarantees.md).
- [Runtime contract research](../research/2026-10-03-runtime-contracts.md), cache section.
- [ADR0015](0015-cache-facade-cachemanager.md).
- [Implementation plan](../superpowers/plans/2026-10-04-ticket-08-cache-guarantees.md).

---

*本 ADR 遵循 Michael Nygard 模板，最终状态随本票证据闭合。*
