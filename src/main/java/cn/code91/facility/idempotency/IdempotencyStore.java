package cn.code91.facility.idempotency;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Independent claim and receipt store. Qualified operations bind a trusted scope/key to a
 * canonical fingerprint and condition completion on the current owner and generation.
 * Lease expiry does not cancel external work; conditional updates protect the receipt only.
 * Local implementations cannot promise persistence, cross-process exclusion or exactly-once effects.
 *
 * <p>Legacy adapters remain binary compatible and default to unsupported for qualified operations.
 * Migrate acquisition, lookup and completion together; never mix namespaces for one business command.</p>
 */
public interface IdempotencyStore {
    /**
     * Atomically acquires a missing command, reports a live owner, replays a retained receipt,
     * rejects a fingerprint conflict, or reports unavailability. Only Acquired permits this
     * protocol's execution path. A result/terminal expiry never grants a new execution.
     * PROCESSING lease expiry may issue a new owner for the same fingerprint; old work may still run.
     */
    default ClaimResult claim(ClaimRequest request) {
        Objects.requireNonNull(request, "request");
        return new ClaimResult.Unavailable(ClaimResult.Reason.UNSUPPORTED);
    }

    /**
     * Saves an opaque receipt only for the current, live PROCESSING token. Retention starts at
     * completion, is positive whole milliseconds, and expires at equality. APPLIED means only
     * that the record update succeeded. A qualified storage failure must retain an UNKNOWN
     * terminal; it must not silently restore execution permission. Caller retains its input array.
     */
    default ClaimUpdate complete(ClaimToken token, byte[] receipt, Duration retention) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(receipt, "receipt");
        ClaimInputs.millis(retention, "retention");
        return ClaimUpdate.UNAVAILABLE;
    }

    /**
     * Permanently stops the still-current PROCESSING token without granting re-execution,
     * including after its lease expires if no replacement owner has acquired it. Repeated,
     * replaced, foreign and terminal updates are REJECTED. This is not rollback of external
     * business work and cannot undo an execution qualification already granted to a new owner.
     */
    default ClaimUpdate release(ClaimToken token) {
        Objects.requireNonNull(token, "token");
        return ClaimUpdate.UNAVAILABLE;
    }

    /**
     * Legacy ownerless acquisition with historical TTL re-entry. Keys must be nonblank, at most
     * 256 UTF-16 units, without control characters; TTL must be positive milliseconds.
     * @deprecated Migrate the entire command path to {@link #claim(ClaimRequest)}.
     */
    @Deprecated(since = "0.1.0", forRemoval = false)
    boolean tryBegin(String key, long ttlMillis);

    /**
     * Looks up only the legacy namespace; expired records are absent.
     * @deprecated Qualified callers use the atomic decision from {@link #claim(ClaimRequest)}.
     */
    @Deprecated(since = "0.1.0", forRemoval = false)
    Optional<IdempotencyRecord> find(String key);

    /**
     * Completes only an existing live legacy PROCESSING record. This ownerless operation cannot
     * distinguish two executions after lease expiry. The local store throws IllegalStateException
     * for absent, expired, completed, closed or over-budget state; it cannot create a new record.
     * @deprecated Migrate to {@link #complete(ClaimToken, byte[], Duration)}.
     */
    @Deprecated(since = "0.1.0", forRemoval = false)
    void complete(String key, IdempotencyRecord done);
}
