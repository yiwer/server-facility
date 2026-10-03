package cn.code91.facility.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Local, bounded claim store. Qualified command bindings survive receipt expiry until close;
 * PROCESSING lease expiry alone permits a new owner for the same fingerprint.
 * Conditional record updates cannot stop external side effects of an expired owner.
 * Legacy records occupy an isolated namespace with their historical TTL/re-entry semantics.
 * All mutations share one monitor so entry and byte budgets are strict across both namespaces.
 * No background threads, durable recovery or cross-process coordination are provided.
 */
public final class InMemoryIdempotencyStore implements IdempotencyStore, AutoCloseable {

    private Map<String, IdempotencyRecord> store = new HashMap<>();
    private final int maxEntries;
    private final int maxReceiptBytes;
    private final long maxStoredReceiptBytes;
    private final Clock clock;
    private long generation;
    private long lastObservedTime = Long.MIN_VALUE;
    private long storedReceiptBytes;
    private boolean closed;
    private record QualifiedKey(String scope, String key) { }
    private enum Phase { PROCESSING, DONE, RESULT_EXPIRED, RELEASED, UNKNOWN }
    private static final class ClaimState {
        final ClaimRequest request;
        final ClaimToken token;
        long expiresAt;
        Phase phase = Phase.PROCESSING;
        byte[] receipt;
        ClaimState(ClaimRequest request, ClaimToken token, long expiresAt) {
            this.request = request; this.token = token; this.expiresAt = expiresAt;
        }
    }
    private Map<QualifiedKey, ClaimState> claims = new HashMap<>();


    /**
     * 参数范围守卫（F13/ADR-0013）：非正数启动期快速失败，消除 maxEntries=0「每次先
     * clear 再建」的荒谬行为。
     *
     * @param maxEntries 记录集合的无界防护上限
     */
    public InMemoryIdempotencyStore(int maxEntries) {
        this(maxEntries, 1024 * 1024, 64L * 1024 * 1024, Clock.systemUTC());
    }

    /** Creates a local store with explicit receipt budgets and a host-owned millisecond clock. */
    public InMemoryIdempotencyStore(int maxEntries, int maxReceiptBytes, long maxStoredReceiptBytes, Clock clock) {
        if (maxEntries <= 0) throw new IllegalArgumentException("maxEntries must be > 0");
        if (maxReceiptBytes <= 0) throw new IllegalArgumentException("maxReceiptBytes must be > 0");
        if (maxStoredReceiptBytes <= 0) throw new IllegalArgumentException("maxStoredReceiptBytes must be > 0");
        this.maxEntries = maxEntries;
        this.maxReceiptBytes = maxReceiptBytes;
        this.maxStoredReceiptBytes = maxStoredReceiptBytes;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override public synchronized ClaimResult claim(ClaimRequest request) {
        Objects.requireNonNull(request, "request");
        if (closed) return new ClaimResult.Unavailable(ClaimResult.Reason.CLOSED);
        long now;
        try { now = now(); }
        catch (RuntimeException unavailable) { return new ClaimResult.Unavailable(ClaimResult.Reason.CLOCK); }
        var key = new QualifiedKey(request.scope(), request.key());
        ClaimState existing = claims.get(key);
        if (existing != null && !existing.request.fingerprint().equals(request.fingerprint())) return new ClaimResult.Conflict();
        if (existing != null && existing.phase == Phase.DONE) {
            if (now < existing.expiresAt) return new ClaimResult.Replay(existing.receipt);
            expireReceipt(existing);
        }
        if (existing != null && existing.phase == Phase.RESULT_EXPIRED)
            return new ClaimResult.Unavailable(ClaimResult.Reason.RESULT_EXPIRED);
        if (existing != null && existing.phase == Phase.RELEASED)
            return new ClaimResult.Unavailable(ClaimResult.Reason.RELEASED);
        if (existing != null && existing.phase == Phase.UNKNOWN)
            return new ClaimResult.Unavailable(ClaimResult.Reason.UNKNOWN);
        if (existing != null && now < existing.expiresAt)
            return new ClaimResult.Processing(existing.expiresAt - now);
        if (existing == null && (long) claims.size() + store.size() >= maxEntries) {
            purgeLegacy(now);
            if ((long) claims.size() + store.size() >= maxEntries)
                return new ClaimResult.Unavailable(ClaimResult.Reason.CAPACITY);
        }
        long deadline;
        try { deadline = Math.addExact(now, request.lease().toMillis()); }
        catch (ArithmeticException overflow) { return new ClaimResult.Unavailable(ClaimResult.Reason.CLOCK); }
        if (generation == Long.MAX_VALUE) return new ClaimResult.Unavailable(ClaimResult.Reason.CAPACITY);
        ClaimToken token = new ClaimToken(request.scope(), request.key(), UUID.randomUUID(), ++generation);
        claims.put(key, new ClaimState(request, token, deadline));
        return new ClaimResult.Acquired(token);
    }

    @Override public synchronized ClaimUpdate complete(ClaimToken token, byte[] receipt, Duration retention) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(receipt, "receipt");
        ClaimInputs.millis(retention, "retention");
        if (closed) return ClaimUpdate.UNAVAILABLE;
        ClaimState existing = claims.get(new QualifiedKey(token.scope(), token.key()));
        if (existing == null || !existing.token.equals(token) || existing.phase != Phase.PROCESSING)
            return ClaimUpdate.REJECTED;
        // Qualified failures, including Error from the host clock, must not grant another execution.
        existing.phase = Phase.UNKNOWN;
        long now;
        try { now = now(); }
        catch (RuntimeException unavailable) {
            existing.phase = Phase.UNKNOWN;
            return ClaimUpdate.UNAVAILABLE;
        }
        if (now >= existing.expiresAt) {
            existing.phase = Phase.PROCESSING; // Known expired qualification: preserve the explicit lease policy.
            return ClaimUpdate.REJECTED;
        }
        long deadline;
        try { deadline = Math.addExact(now, retention.toMillis()); }
        catch (ArithmeticException overflow) {
            existing.phase = Phase.UNKNOWN;
            return ClaimUpdate.UNAVAILABLE;
        }
        if (receipt.length > maxReceiptBytes) {
            existing.phase = Phase.UNKNOWN;
            return ClaimUpdate.UNAVAILABLE;
        }
        if (receipt.length > maxStoredReceiptBytes - storedReceiptBytes) reclaimPayloads(now);
        if (receipt.length > maxStoredReceiptBytes - storedReceiptBytes) {
            existing.phase = Phase.UNKNOWN;
            return ClaimUpdate.UNAVAILABLE;
        }
        // If allocation fails, UNKNOWN remains without swallowing the original Error.
        byte[] ownedReceipt = receipt.clone();
        existing.receipt = ownedReceipt;
        storedReceiptBytes += receipt.length;
        existing.expiresAt = deadline;
        existing.phase = Phase.DONE;
        return ClaimUpdate.APPLIED;
    }

    @Override
    public synchronized ClaimUpdate release(ClaimToken token) {
        Objects.requireNonNull(token, "token");
        if (closed) return ClaimUpdate.UNAVAILABLE;
        ClaimState existing = claims.get(new QualifiedKey(token.scope(), token.key()));
        if (existing == null || !existing.token.equals(token) || existing.phase != Phase.PROCESSING)
            return ClaimUpdate.REJECTED;
        // Qualified failures, including Error from the host clock, must not grant another execution.
        existing.phase = Phase.UNKNOWN;
        try { now(); }
        catch (RuntimeException unavailable) {
            existing.phase = Phase.UNKNOWN;
            return ClaimUpdate.UNAVAILABLE;
        }
        // Termination removes permission; it does not renew execution or write a late receipt.
        // The exact owner/generation above still prevents damaging a replacement owner.
        existing.phase = Phase.RELEASED;
        return ClaimUpdate.APPLIED;
    }

    private long now() {
        lastObservedTime = Math.max(lastObservedTime, clock.millis());
        return lastObservedTime;
    }

    /** Closes this local store permanently and releases retained bindings and receipts. */
    @Override public synchronized void close() {
        closed = true;
        // clear() retains a HashMap's expanded table even when this closed store remains reachable.
        claims = Map.of();
        store = Map.of();
        storedReceiptBytes = 0;
    }

    private void expireReceipt(ClaimState state) {
        storedReceiptBytes -= state.receipt.length;
        state.receipt = null;
        state.phase = Phase.RESULT_EXPIRED;
    }

    @Override
    public synchronized boolean tryBegin(String key, long ttlMillis) {
        ClaimInputs.text(key, "key", 256);
        if (ttlMillis <= 0) throw new IllegalArgumentException("ttlMillis must be > 0");
        ensureOpen();
        long now = now();
        IdempotencyRecord existing = store.get(key);
        if (existing != null && !existing.isExpired(now)) return false;
        if (existing != null || (long) claims.size() + store.size() >= maxEntries) purgeLegacy(now);
        if ((long) claims.size() + store.size() >= maxEntries) return false;
        store.put(key, IdempotencyRecord.processing(Math.addExact(now, ttlMillis)));
        return true;
    }

    @Override
    public synchronized Optional<IdempotencyRecord> find(String key) {
        ClaimInputs.text(key, "key", 256);
        ensureOpen();
        IdempotencyRecord record = store.get(key);
        return record == null || record.isExpired(now()) ? Optional.empty() : Optional.of(record);
    }

    @Override
    public synchronized void complete(String key, IdempotencyRecord done) {
        ClaimInputs.text(key, "key", 256);
        Objects.requireNonNull(done, "done");
        ensureOpen();
        long now = now();
        IdempotencyRecord existing = store.get(key);
        if (existing == null || existing.state() != IdempotencyRecord.State.PROCESSING || existing.isExpired(now))
            throw new IllegalStateException("Legacy completion requires a live legacy PROCESSING record");
        if (done.state() != IdempotencyRecord.State.DONE || done.isExpired(now))
            throw new IllegalArgumentException("Legacy completion requires an unexpired DONE record");
        int length = done.bodyLength();
        if (length > maxReceiptBytes) throw new IllegalStateException("Receipt exceeds per-record byte budget");
        if (length > maxStoredReceiptBytes - storedReceiptBytes) reclaimPayloads(now);
        if (length > maxStoredReceiptBytes - storedReceiptBytes)
            throw new IllegalStateException("Receipt exceeds total byte budget");
        store.put(key, done);
        storedReceiptBytes += length;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Idempotency store is closed");
    }

    private void purgeLegacy(long now) {
        store.entrySet().removeIf(entry -> {
            IdempotencyRecord record = entry.getValue();
            if (!record.isExpired(now)) return false;
            storedReceiptBytes -= record.bodyLength();
            return true;
        });
    }

    private void reclaimPayloads(long now) {
        purgeLegacy(now);
        for (ClaimState state : claims.values())
            if (state.phase == Phase.DONE && now >= state.expiresAt) expireReceipt(state);
    }
}
