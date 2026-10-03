package cn.code91.facility.async;

import cn.code91.facility.result.Result;
import org.slf4j.MDC;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One submission's deadline, work handles and scoped execution. No Spring/global context lookup. */
final class AsyncExecution {
    static final Executor DEFAULT_EXECUTOR = defaultExecutor();
    private static final ScheduledThreadPoolExecutor TIMER = timer();
    final Executor executor;
    final Map<String, String> mdc;
    private final AsyncContext context;
    private final List<AsyncInterceptor> interceptors;
    private final AsyncExecution parent;
    private final boolean timed;
    private final long deadline;
    private final Set<AsyncExecution> children = ConcurrentHashMap.newKeySet();
    private final Set<FutureTask<?>> tasks = ConcurrentHashMap.newKeySet();
    private final AtomicReference<Throwable> stopped = new AtomicReference<>();
    private volatile Consumer<Throwable> fail;
    private final AtomicBoolean settled = new AtomicBoolean();
    private volatile boolean interruptOnStop = true;

    AsyncExecution(Executor executor, AsyncContext context, List<AsyncInterceptor> interceptors,
                   Map<String, String> mdc, AsyncExecution parent, Duration timeout) {
        this.executor = executor;
        this.context = parent == null ? context : context.inherit(parent.context);
        var chain = new ArrayList<AsyncInterceptor>();
        if (parent != null) chain.addAll(parent.interceptors);
        chain.addAll(interceptors);
        this.interceptors = chain.stream().sorted(Comparator.comparingInt(AsyncInterceptor::order)).toList();
        this.mdc = mdc;
        this.parent = parent;
        long now = System.nanoTime();
        this.timed = timeout != null || parent != null && parent.timed;
        long budget = timeout == null ? Long.MAX_VALUE : nanos(timeout);
        if (parent != null && parent.timed) budget = Math.min(budget, Math.max(0, parent.deadline - now));
        this.deadline = now + budget;
    }

    <T> CompletableFuture<Result<T, Throwable>> submit(Supplier<CompletableFuture<Result<T, Throwable>>> computation) {
        var promise = new CompletableFuture<Result<T, Throwable>>() {
            @Override public boolean cancel(boolean interrupt) {
                if (!settled.compareAndSet(false, true)) return isCancelled();
                interruptOnStop = interrupt;
                stopped.set(new CancellationException("Async submission cancelled"));
                boolean cancelled = super.cancel(interrupt);
                cancelWork(interrupt);
                return cancelled;
            }
        };
        fail = reason -> promise.complete(Result.err(reason));
        if (parent != null) {
            parent.children.add(this);
            if (parent.stopped.get() != null) stop(parent.stopped.get(), parent.interruptOnStop);
        }
        ScheduledFuture<?> alarm = timed ? TIMER.schedule(() -> stop(new TimeoutException("Async deadline exceeded")),
                Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS) : null;
        promise.whenComplete((r, e) -> {
            if (alarm != null) alarm.cancel(false);
            if (parent != null) parent.children.remove(this);
        });
        if (available()) {
            try {
                computation.get().whenComplete((result, failure) -> {
                    if (available() && settled.compareAndSet(false, true)) promise.complete(failure == null ? result : Result.err(DefaultAsync.unwrap(failure)));
                });
            } catch (Throwable failure) { if (settled.compareAndSet(false, true)) promise.complete(Result.err(DefaultAsync.unwrap(failure))); }
        }
        return promise;
    }

    private static long nanos(Duration duration) {
        if (duration.isNegative() || duration.isZero()) return 0;
        try { return duration.toNanos(); } catch (ArithmeticException overflow) { return Long.MAX_VALUE; }
    }

    private boolean available() {
        if (stopped.get() != null) return false;
        if (timed && deadline - System.nanoTime() <= 0) {
            stop(new TimeoutException("Async deadline exceeded"));
            return false;
        }
        return true;
    }

    private void stop(Throwable reason) { stop(reason, true); }
    private void stop(Throwable reason, boolean interrupt) {
        if (!settled.compareAndSet(false, true)) return;
        interruptOnStop = interrupt;
        stopped.set(reason);
        fail.accept(reason);
        cancelWork(interrupt);
    }

    private void cancelWork(boolean interrupt) {
        for (var child : children) child.stop(stopped.get(), interrupt);
        for (var task : tasks) {
            task.cancel(interrupt);
            if (executor instanceof ThreadPoolExecutor pool) pool.remove(task);
            else if (executor instanceof org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor springPool)
                springPool.getThreadPoolExecutor().remove(task);
        }
    }

    <T> CompletableFuture<Result<T, Throwable>> value(Result<T, Throwable> value) {
        return interceptors.isEmpty() ? CompletableFuture.completedFuture(value) : stage(() -> value);
    }

    <T> CompletableFuture<Result<T, Throwable>> stage(Async.ThrowableSupplier<Result<T, Throwable>> action) {
        var result = new CompletableFuture<Result<T, Throwable>>();
        FutureTask<Void> work = new FutureTask<>(() -> {
            if (!available()) { result.complete(Result.err(stopped.get())); return null; }
            var previous = MDC.getCopyOfContextMap();
            Result<T, Throwable> value;
            try {
                install(mdc);
                AsyncInvocation<T> chain = () -> {
                    try { return CompletableFuture.completedFuture(action.get()); }
                    catch (Throwable failure) { return CompletableFuture.completedFuture(Result.err(failure)); }
                };
                for (int i = interceptors.size() - 1; i >= 0; i--) {
                    var interceptor = interceptors.get(i);
                    var next = chain;
                    chain = () -> interceptor.intercept(context, next);
                }
                var intercepted = Objects.requireNonNull(chain.proceed(), "interceptor result");
                if (!intercepted.isDone()) throw new IllegalStateException("AsyncInterceptor must complete within its execution scope");
                value = Objects.requireNonNull(intercepted.join(), "interceptor Result");
            } catch (Throwable failure) { value = Result.err(DefaultAsync.unwrap(failure)); }
            finally { install(previous); }
            result.complete(value);
            return null;
        }) {
            @Override protected void done() {
                tasks.remove(this);
                if (isCancelled()) result.complete(Result.err(new CancellationException("Async execution cancelled")));
            }
        };
        tasks.add(work);
        if (!available()) work.cancel(true);
        else try { executor.execute(work); }
        catch (Throwable failure) { tasks.remove(work); result.complete(Result.err(failure)); }
        return result;
    }

    private static void install(Map<String, String> context) {
        if (context == null) MDC.clear(); else MDC.setContextMap(context);
    }

    private static ThreadPoolExecutor defaultExecutor() {
        var executor = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(256),
                Thread.ofPlatform().daemon().name("facility-async-default-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static ScheduledThreadPoolExecutor timer() {
        var timer = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().daemon().name("facility-async-deadline").factory());
        timer.setRemoveOnCancelPolicy(true);
        timer.setKeepAliveTime(30, TimeUnit.SECONDS);
        timer.allowCoreThreadTimeOut(true);
        return timer;
    }
}
