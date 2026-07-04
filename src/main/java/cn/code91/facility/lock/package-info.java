/**
 * <h2>cn.code91.facility.lock</h2>
 *
 * <p><b>Purpose:</b> General-purpose distributed locking — the {@code DistributedLock} SPI (a
 * replaceable seam, ADR-0016), its default single-node implementation
 * {@code InMemoryDistributedLock} (one {@code ReentrantLock} per key in a
 * {@code ConcurrentHashMap}, with the same unbounded-key clear-and-warn protection used by
 * {@code TokenBucketRateLimiter}, see ADR-0014), {@code LockAcquisitionException} (thrown by
 * {@code DistributedLock#executeWithLock} when {@code tryLock} times out), the static facade
 * {@code LockUtil} (delegates to a container-managed {@code DistributedLock} bean via
 * {@code SpringContextHolder}, degrades to running the action without any lock — plus a WARN
 * log line — when no bean is present), and its assembly-layer configuration knobs
 * {@code FacilityLockProperties} (prefix {@code facility.lock}, homed here beside its consumer
 * per C3).</p>
 *
 * <p><b>Entry classes:</b> {@code DistributedLock}, {@code LockAcquisitionException},
 * {@code InMemoryDistributedLock}, {@code LockUtil}, {@code FacilityLockProperties}.</p>
 *
 * <p><b>Zero web dependency:</b> this package carries no servlet-stack import, so it is directly
 * reusable outside web applications (batch jobs, scheduled tasks) via {@code LockUtil} or plain
 * {@code DistributedLock} injection — unlike the rate-limit and idempotency clusters, locking has
 * no HTTP-facing counterpart that would need splitting into a {@code web.*} sibling package.</p>
 *
 * <p><b>Depends on:</b> {@code context} ({@code LockUtil} resolves the Spring-managed
 * {@code DistributedLock} via {@code SpringContextHolder.getBean}), {@code result}/{@code error}
 * (that lookup returns a {@code Result<DistributedLock, WrappedError>}; {@code LockUtil} inspects
 * it both via {@code .map(...).orElse(...)} — {@code tryLock}/{@code unlock} — and, in
 * {@code executeWithLock}, an explicit {@code isErr()} check so the degrade path can log a WARN
 * before running the action), {@code log} ({@code InMemoryDistributedLock} logs a WARN when its
 * unbounded-key protection clears the lock set; {@code LockUtil} logs a WARN on every no-bean
 * {@code executeWithLock} call), Spring Boot configuration-properties annotations
 * ({@code FacilityLockProperties}).</p>
 *
 * <p><b>Depended on by:</b> {@code autoconfigure} ({@code FacilityLockAutoConfiguration} reads
 * {@code FacilityLockProperties} to size the default {@code InMemoryDistributedLock} it
 * assembles, with no web condition), downstream application code ({@code LockUtil} and
 * {@code DistributedLock} work without the autoconfigure layer too, as long as some
 * {@code DistributedLock} bean is present in the container — e.g. a Redisson-backed one a host
 * application wires in itself, see ADR-0016's real-seam upgrade walkthrough).</p>
 */
package cn.code91.facility.lock;
