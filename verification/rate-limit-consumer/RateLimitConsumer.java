import cn.code91.facility.ratelimit.*;
import java.util.*;
import java.util.concurrent.*;

/** Ordinary jar, JDK-only consumer. No test/framework or library source output on the classpath. */
public class RateLimitConsumer {
    private static final int SLOTS = 1024;
    public static void main(String[] arguments) throws Exception {
        for (String absent : List.of("org.springframework.context.ApplicationContext", "org.slf4j.Logger", "jakarta.annotation.Nullable")) {
            try { Class.forName(absent); throw new AssertionError("Unexpected framework dependency: " + absent); }
            catch (ClassNotFoundException expected) { }
        }
        var limiter = new TokenBucketRateLimiter(1, Double.MIN_VALUE, SLOTS, () -> 0L);
        String suffix = "x".repeat(500);
        List<String> residents = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            String key = "resident" + i + suffix;
            require(limiter.tryAcquire(key), "initial admission " + i); residents.add(key);
        }
        for (int i = 0; i < 32768; i++) {
            try { limiter.tryAcquire("new" + i + suffix); throw new AssertionError("new identity reset an exhausted slot: " + i); }
            catch (RateLimiterUnavailableException expected) { }
            String resident = residents.get(i % SLOTS);
            require(!limiter.tryAcquire(resident), "quota restored by churn: " + i);
            try { limiter.tryAcquire(resident, -1); throw new AssertionError("negative debit accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        var large = new TokenBucketRateLimiter(Long.MAX_VALUE, Double.MIN_VALUE, 1, () -> 0L);
        require(large.acquire("large", 1, Long.MAX_VALUE, Double.MIN_VALUE).remaining() == 9223372036854775806L, "large debit lost");
        require(large.acquire("large", Integer.MAX_VALUE, Long.MAX_VALUE, Double.MIN_VALUE).remaining() == 9223372034707292159L, "large repeated debit lost");

        var concurrent = new TokenBucketRateLimiter(37, 1, 1, () -> 0L);
        var start = new CyclicBarrier(16);
        int accepted = 0;
        try (var executor = Executors.newFixedThreadPool(16)) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int worker = 0; worker < 16; worker++) futures.add(executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS); int count = 0;
                for (int i = 0; i < 16; i++) if (concurrent.tryAcquire("same")) count++;
                return count;
            }));
            for (var future : futures) accepted += future.get(10, TimeUnit.SECONDS);
        }
        require(accepted == 37, "concurrent debit count " + accepted);
        require(!concurrent.tryAcquire("same"), "concurrent residual credit");
        System.out.println("RATE_LIMIT_CONSUMER_PASS slots=1024 churn=32768 workers=16 exact-long=true framework=absent");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
