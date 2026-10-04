package cn.code91.facility.async;

import org.slf4j.*;
import org.slf4j.helpers.*;
import org.slf4j.spi.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Host logging SPI faults are isolated from the parent test JVM. */
public final class AsyncMdcFailureProcess {
    static final RuntimeException PRIMARY = new IllegalStateException("primary-mdc-or-action");
    static final AssertionError SECONDARY = new AssertionError("secondary-mdc-restore");
    static final AtomicBoolean ARMED = new AtomicBoolean();
    static String mode;

    public static void main(String[] args) throws Exception {
        mode = args[0];
        var worker = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("async-mdc-contract").factory());
        CompletableFuture<?> pending = null;
        try {
            worker.submit(() -> MDC.put("owner", "worker")).get(2, TimeUnit.SECONDS);
            MDC.put("owner", "submitter");
            var calls = new AtomicInteger();
            ARMED.set(true);
            var result = Async.supply(() -> {
                calls.incrementAndGet();
                assertThat(MDC.get("owner")).isEqualTo("submitter");
                if (mode.equals("restore-failure") || mode.equals("restore-same")) throw PRIMARY;
                return "completed";
            }, worker).submit();
            pending = result;
            var observed = result.get(2, TimeUnit.SECONDS);
            assertThat(observed.isErr()).isTrue();
            assertThat(observed.getErr()).isSameAs(PRIMARY);
            if (mode.equals("rollback") || mode.equals("restore-failure"))
                assertThat(observed.getErr().getSuppressed()).containsExactly(SECONDARY);
            else assertThat(observed.getErr().getSuppressed()).isEmpty();
            assertThat(calls.get()).isEqualTo(mode.startsWith("restore-") ? 1 : 0);
            assertThat(worker.submit(() -> MDC.get("owner")).get(2, TimeUnit.SECONDS)).isEqualTo("worker");
            assertThat(Async.supply(() -> MDC.get("owner"), worker).submit().get(2, TimeUnit.SECONDS).get()).isEqualTo("submitter");
            assertThat(worker.submit(() -> MDC.get("owner")).get(2, TimeUnit.SECONDS)).isEqualTo("worker");
            assertThat(MDC.get("owner")).isEqualTo("submitter");
            System.out.println("ASYNC_MDC_FAILURE_PASS mode=" + mode);
        } finally {
            if (pending != null) pending.cancel(true);
            ARMED.set(false);
            MDC.clear();
            worker.shutdownNow();
            assertThat(worker.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
            System.out.println("WORKER_TERMINATED");
        }
    }

    public static final class FaultingMdcProvider implements SLF4JServiceProvider {
        private final MDCAdapter adapter = new FaultAdapter();
        @Override public ILoggerFactory getLoggerFactory() { return new NOPLoggerFactory(); }
        @Override public IMarkerFactory getMarkerFactory() { return new BasicMarkerFactory(); }
        @Override public MDCAdapter getMDCAdapter() { return adapter; }
        @Override public String getRequestedApiVersion() { return "2.0.99"; }
        @Override public void initialize() {}
    }

    private static final class FaultAdapter implements MDCAdapter {
        private final BasicMDCAdapter delegate = new BasicMDCAdapter();
        private boolean worker() { return Thread.currentThread().getName().equals("async-mdc-contract"); }
        @Override public Map<String, String> getCopyOfContextMap() {
            if ((mode.equals("snapshot") && worker() || mode.equals("caller-snapshot") && !worker())
                    && ARMED.compareAndSet(true, false)) throw PRIMARY;
            return delegate.getCopyOfContextMap();
        }
        @Override public void setContextMap(Map<String, String> context) {
            delegate.setContextMap(context);
            if (!worker() || !ARMED.get()) return;
            boolean installing = "submitter".equals(context.get("owner"));
            if (installing && (mode.equals("install") || mode.equals("rollback"))) {
                if (mode.equals("install")) ARMED.set(false);
                throw PRIMARY;
            }
            if (!installing && ARMED.compareAndSet(true, false)) {
                if (mode.equals("rollback") || mode.equals("restore-failure")) throw SECONDARY;
                if (mode.equals("restore-success") || mode.equals("restore-same")) throw PRIMARY;
            }
        }
        @Override public void clear() { delegate.clear(); }
        @Override public String get(String key) { return delegate.get(key); }
        @Override public void put(String key, String value) { delegate.put(key, value); }
        @Override public void remove(String key) { delegate.remove(key); }
        @Override public void pushByKey(String key, String value) { delegate.pushByKey(key, value); }
        @Override public String popByKey(String key) { return delegate.popByKey(key); }
        @Override public Deque<String> getCopyOfDequeByKey(String key) { return delegate.getCopyOfDequeByKey(key); }
        @Override public void clearDequeByKey(String key) { delegate.clearDequeByKey(key); }
    }
}
