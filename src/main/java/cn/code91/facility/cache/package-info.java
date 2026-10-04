/**
 * Application cache configuration and deprecated single-context compatibility (ADR0031).
 * New application code injects Spring CacheManager/Cache. Explicit facility local selection
 * requires the optional Caffeine + spring-context-support pair, fixed names and positive TTL/
 * capacity; absence defaults to no manager. A host manager owns its own policy and lifecycle.
 * CacheUtil delegates to Spring Cache without holding another cache. Its historical missing-bean
 * fallback is isolated from selected capability guarantees. This package depends on Spring Cache,
 * Boot properties and the legacy context/result lookup; optional provider types remain in guarded
 * autoconfiguration. Business invalidation, key/value byte budgets and loader lifetime belong to
 * the application. See docs/building/local-cache.md.
 */
package cn.code91.facility.cache;
