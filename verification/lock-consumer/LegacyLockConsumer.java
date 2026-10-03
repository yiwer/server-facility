import cn.code91.facility.lock.DistributedLock;
import cn.code91.facility.lock.InMemoryDistributedLock;
import java.time.Duration;

/** Compiled against the original SPI; only the current jar is available at runtime. */
public class LegacyLockConsumer {
    public static void main(String[] args) {
        DistributedLock lock = new InMemoryDistributedLock(1);
        if (!"first".equals(lock.executeWithLock("one", Duration.ZERO, () -> "first"))) throw new AssertionError();
        if (!"second".equals(lock.executeWithLock("two", Duration.ZERO, () -> "second"))) throw new AssertionError();
        lock.executeWithLock("runnable", Duration.ZERO, (Runnable) () -> {});
        lock.unlock("unknown");
        System.out.println("LEGACY_LOCK_CONSUMER_PASS historicalSpi=true currentRuntime=true");
    }
}
