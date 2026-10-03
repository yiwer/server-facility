import cn.code91.facility.idempotency.IdempotencyRecord;
import cn.code91.facility.idempotency.IdempotencyStore;
import java.util.Optional;

/** Compiled against the exact pre-expansion SPI snapshot from c32e72e. */
public final class LegacyOnlyStore implements IdempotencyStore {
    public boolean tryBegin(String key, long ttlMillis) { throw new AssertionError("old acquisition called"); }
    public Optional<IdempotencyRecord> find(String key) { throw new AssertionError("old lookup called"); }
    public void complete(String key, IdempotencyRecord done) { throw new AssertionError("old completion called"); }
}
