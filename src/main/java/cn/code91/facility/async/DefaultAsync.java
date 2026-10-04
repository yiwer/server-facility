package cn.code91.facility.async;

import cn.code91.facility.result.Result;
import jakarta.annotation.Nullable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/** Immutable description of one lazy asynchronous computation. */
public final class DefaultAsync<T> implements Async<T> {
    private final Function<AsyncExecution, CompletableFuture<Result<T, Throwable>>> computation;
    private final List<AsyncInterceptor> interceptors;
    private final Executor executor;
    private final AsyncContext context;
    private final Duration timeout;

    private DefaultAsync(Function<AsyncExecution, CompletableFuture<Result<T, Throwable>>> computation,
                         List<AsyncInterceptor> interceptors, Executor executor, AsyncContext context) {
        this(computation, interceptors, executor, context, null);
    }

    private DefaultAsync(Function<AsyncExecution, CompletableFuture<Result<T, Throwable>>> computation,
                         List<AsyncInterceptor> interceptors, Executor executor, AsyncContext context, Duration timeout) {
        this.timeout = timeout;
        this.computation = computation;
        this.interceptors = List.copyOf(interceptors);
        this.executor = executor;
        this.context = context;
    }

    static <T> DefaultAsync<T> of(ThrowableSupplier<T> supplier, Executor executor) {
        Objects.requireNonNull(supplier, "supplier");
        return new DefaultAsync<>(e -> e.stage(() -> Result.ok(supplier.get())), List.of(), executor, AsyncContext.empty());
    }

    static <T> DefaultAsync<T> completed(T value) {
        return new DefaultAsync<>(e -> e.value(Result.ok(value)), List.of(), null, AsyncContext.empty());
    }

    static <T> DefaultAsync<T> failed(Throwable throwable) {
        Objects.requireNonNull(throwable, "throwable");
        return new DefaultAsync<>(e -> e.value(Result.err(throwable)), List.of(), null, AsyncContext.empty());
    }

    static <T> DefaultAsync<List<T>> all(List<? extends Async<T>> input) {
        var tasks = List.copyOf(input);
        return new DefaultAsync<>(e -> {
            List<CompletableFuture<Result<T, Throwable>>> futures = tasks.stream().map(t -> start(t, e)).toList();
            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
                List<T> values = new ArrayList<>();
                List<Throwable> errors = new ArrayList<>();
                for (var future : futures) {
                    var result = future.join();
                    if (result.isErr()) errors.add(result.getErr());
                    else values.add(result.get());
                }
                return errors.isEmpty() ? Result.ok(values) : Result.err(aggregate(errors));
            });
        }, List.of(), null, AsyncContext.empty());
    }

    static <T> DefaultAsync<T> any(List<? extends Async<T>> input) {
        var tasks = List.copyOf(input);
        return new DefaultAsync<>(e -> {
            if (tasks.isEmpty()) return CompletableFuture.completedFuture(Result.err(new IllegalArgumentException("any() requires at least one task")));
            var promise = new CompletableFuture<Result<T, Throwable>>();
            var remaining = new AtomicInteger(tasks.size());
            List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
            var futures = tasks.stream().map(task -> start(task, e)).toList();
            for (var future : futures) {
                future.thenAccept(result -> {
                    if (result.isOk() && promise.complete(result)) {
                        for (var other : futures) if (other != future) other.cancel(true);
                    }
                    else if (result.isErr()) errors.add(result.getErr());
                    if (remaining.decrementAndGet() == 0 && !promise.isDone()) promise.complete(Result.err(aggregate(errors)));
                });
            }
            return promise;
        }, List.of(), null, AsyncContext.empty());
    }

    private static Throwable aggregate(List<Throwable> errors) {
        return errors.size() == 1 ? errors.getFirst() : new AggregateException(errors);
    }

    private static <T> CompletableFuture<Result<T, Throwable>> start(Async<T> task, AsyncExecution parent) {
        return ((DefaultAsync<T>) Objects.requireNonNull(task, "nested task")).start(parent);
    }

    @Override public Async<T> name(String name) { return new DefaultAsync<>(computation, interceptors, executor, context.withName(name), timeout); }
    @Override public Async<T> executor(Executor executor) { return new DefaultAsync<>(computation, interceptors, Objects.requireNonNull(executor, "executor"), context, timeout); }
    @Override public Async<T> attribute(String key, @Nullable Object value) { return new DefaultAsync<>(computation, interceptors, executor, context.with(key, value), timeout); }
    @Override public Async<T> intercept(AsyncInterceptor... values) { return intercept(Arrays.asList(values)); }
    @Override public Async<T> intercept(List<AsyncInterceptor> values) {
        var merged = new ArrayList<>(interceptors);
        merged.addAll(List.copyOf(values));
        return new DefaultAsync<>(computation, merged, executor, context, timeout);
    }

    @Override public <U> Async<U> map(Function<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        return derive(e -> computation.apply(e).thenCompose(r -> r.isErr()
                ? CompletableFuture.completedFuture(Result.err(r.getErr()))
                : e.stage(() -> Result.ok(mapper.apply(r.get())))));
    }

    @Override public <U> Async<U> flatMap(Function<? super T, ? extends Async<U>> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        return derive(e -> computation.apply(e).thenCompose(r -> r.isErr()
                ? CompletableFuture.completedFuture(Result.err(r.getErr()))
                : e.stage(() -> Result.<Async<U>, Throwable>ok(mapper.apply(r.get())))
                    .thenCompose(next -> next.isErr() ? CompletableFuture.completedFuture(Result.err(next.getErr())) : start(next.get(), e))));
    }

    @Override public Async<T> recover(Function<? super Throwable, ? extends T> recovery) {
        Objects.requireNonNull(recovery, "recovery");
        return derive(e -> computation.apply(e).thenCompose(r -> r.isOk()
                ? CompletableFuture.completedFuture(r) : e.stage(() -> Result.ok(recovery.apply(r.getErr())))));
    }

    @Override public Async<T> recoverWith(Function<? super Throwable, ? extends Async<T>> recovery) {
        Objects.requireNonNull(recovery, "recovery");
        return derive(e -> computation.apply(e).thenCompose(r -> r.isOk()
                ? CompletableFuture.completedFuture(r)
                : e.stage(() -> Result.<Async<T>, Throwable>ok(recovery.apply(r.getErr())))
                    .thenCompose(next -> next.isErr() ? CompletableFuture.completedFuture(Result.err(next.getErr())) : start(next.get(), e))));
    }

    @Override public Async<T> timeout(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        return new DefaultAsync<>(computation, interceptors, executor, context,
                timeout == null || duration.compareTo(timeout) < 0 ? duration : timeout);
    }

    @Override public Async<T> peek(Consumer<? super T> action) {
        Objects.requireNonNull(action, "action");
        return derive(e -> computation.apply(e).thenCompose(r -> e.stage(() -> {
            if (r.isOk()) try { action.accept(r.get()); } catch (Exception ignored) { /* observational callback */ }
            return r;
        })));
    }

    @Override public Async<T> peekErr(Consumer<? super Throwable> action) {
        Objects.requireNonNull(action, "action");
        return derive(e -> computation.apply(e).thenCompose(r -> e.stage(() -> {
            if (r.isErr()) try { action.accept(r.getErr()); } catch (Exception ignored) { /* observational callback */ }
            return r;
        })));
    }

    private <U> DefaultAsync<U> derive(Function<AsyncExecution, CompletableFuture<Result<U, Throwable>>> operation) {
        return new DefaultAsync<>(operation, interceptors, executor, context, timeout);
    }

    @Override public CompletableFuture<Result<T, Throwable>> submit() { return start(null); }

    private CompletableFuture<Result<T, Throwable>> start(AsyncExecution parent) {
        Executor selected = executor != null ? executor : parent != null ? parent.executor : AsyncExecution.DEFAULT_EXECUTOR;
        Map<String, String> mdc;
        try { mdc = parent == null ? org.slf4j.MDC.getCopyOfContextMap() : parent.mdc; }
        catch (Throwable failure) { return CompletableFuture.completedFuture(Result.err(unwrap(failure))); }
        AsyncExecution execution = new AsyncExecution(selected, context, interceptors,
                mdc, parent, timeout);
        return execution.submit(() -> computation.apply(execution));
    }

    static Throwable unwrap(Throwable failure) {
        while ((failure instanceof CompletionException || failure instanceof ExecutionException) && failure.getCause() != null) failure = failure.getCause();
        return failure;
    }
}
