/**
 * <h2>cn.code91.facility.cache</h2>
 *
 * <p><b>Purpose:</b> Cache facade — the static facade {@code CacheUtil} (delegates to a
 * container-managed {@code CacheManager} bean via {@code SpringContextHolder}, degrades
 * gracefully when no bean is present: {@code get} returns empty, {@code put}/{@code evict}/
 * {@code clear} are no-ops, {@code getOrCompute} falls through to calling its loader directly
 * and does not cache the result) and its assembly-layer configuration knobs
 * {@code FacilityCacheProperties} (prefix {@code facility.cache}, homed here beside its
 * consumer per C3). {@code CacheUtil} deliberately reuses Spring's own
 * {@code org.springframework.cache.CacheManager}/{@code Cache} SPI rather than a bespoke
 * cache abstraction (ADR-0015) — whichever {@code CacheManager} implementation the host
 * application ends up with (Caffeine, Redis, EhCache, a hand-rolled one) works through this
 * facade unchanged.</p>
 *
 * <p><b>Entry classes:</b> {@code CacheUtil}, {@code FacilityCacheProperties}.</p>
 *
 * <p><b>Depends on:</b> {@code context} ({@code CacheUtil} resolves the Spring-managed
 * {@code CacheManager} via {@code SpringContextHolder.getBean}), {@code result} (that lookup
 * returns a {@code Result}, which {@code CacheUtil} unwraps via {@code .map(...).orElse(...)}
 * at every call site — the {@code error} branch's type argument erases away and never
 * surfaces in this package's compiled bytecode, since {@code CacheUtil} never inspects the
 * failure case explicitly), {@code spring-context} ({@code CacheManager}/{@code Cache}
 * interfaces), Spring Boot configuration-properties annotations
 * ({@code FacilityCacheProperties}). <b>No Caffeine dependency here:</b> neither
 * {@code CacheUtil} nor {@code FacilityCacheProperties} import Caffeine types — the optional
 * {@code caffeine} dependency (paired with {@code spring-context-support}, which hosts
 * {@code CaffeineCacheManager} — see ADR-0015) is consumed exclusively by
 * {@code autoconfigure.FacilityCacheAutoConfiguration}; {@code FacilityCacheProperties}'
 * {@code defaultTtl}/{@code maximumSize} fields are simply ignored whenever that backend
 * isn't the one assembled.</p>
 *
 * <p><b>Depended on by:</b> {@code autoconfigure} ({@code FacilityCacheAutoConfiguration}
 * reads {@code FacilityCacheProperties} to configure whichever {@code CacheManager} bean it
 * assembles), downstream application code ({@code CacheUtil} works without the autoconfigure
 * layer too, as long as some {@code CacheManager} bean is present in the container — e.g. one
 * a host application wires in itself alongside plain {@code @EnableCaching}).</p>
 */
package cn.code91.facility.cache;
