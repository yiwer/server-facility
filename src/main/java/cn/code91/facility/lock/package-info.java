/**
 * LocalKeyedMutex provides bounded per-instance thread-owned exclusion with safe key reclamation.
 * Legacy DistributedLock, InMemoryDistributedLock and LockUtil signatures remain for explicit
 * migration; no local implementation is advertised as a default distributed capability.
 * The mutex itself is JDK-only. Static compatibility uses context/result/error, while property
 * metadata uses Boot; no log backend, container, background reaper or remote-lock engine is required.
 * See ADR0030 and docs/building/local-locking.md for owner, wait, close and application migration.
 */
package cn.code91.facility.lock;
