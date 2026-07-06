package cn.code91.facility.async;

import cn.code91.facility.result.Result;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * {@link Async} 的默认实现类。
 * <p>
 * 由静态工厂方法创建，封装单次惰性计算。
 * </p>
 * <p>
 * 核心字段：
 * <ul>
 *   <li>{@code computation} — 惰性计算描述，提交时注入生效执行器</li>
 *   <li>{@code interceptors} — 不可变拦截器列表</li>
 *   <li>{@code executor} — 执行器（null 时使用虚拟线程）</li>
 *   <li>{@code context} — 任务上下文（名称 + 属性袋）</li>
 * </ul>
 * </p>
 */
public final class DefaultAsync<T> implements Async<T> {

    private final Function<Executor, CompletableFuture<Result<T, Throwable>>> computation;
    private final List<AsyncInterceptor> interceptors;
    private final Executor executor;
    private final AsyncContext context;

    // ==================== Internal full constructor ====================

    private DefaultAsync(
            Function<Executor, CompletableFuture<Result<T, Throwable>>> computation,
            List<AsyncInterceptor> interceptors,
            Executor executor,
            AsyncContext context) {
        this.computation = computation;
        this.interceptors = Collections.unmodifiableList(interceptors);
        this.executor = executor;
        this.context = context;
    }

    // ==================== 包内工厂（供 Async 接口调用）====================

    static <T> DefaultAsync<T> of(ThrowableSupplier<T> supplier, Executor executor) {
        Function<Executor, CompletableFuture<Result<T, Throwable>>> computation = exec ->
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return Result.<T, Throwable>ok(supplier.get());
                            } catch (Exception e) {
                                return Result.<T, Throwable>err(e);
                            }
                        },
                        exec != null ? exec : Executors.newVirtualThreadPerTaskExecutor()
                );
        return new DefaultAsync<>(computation, new ArrayList<>(), executor, AsyncContext.empty());
    }

    static <T> DefaultAsync<T> completed(T value) {
        return new DefaultAsync<>(
                exec -> CompletableFuture.completedFuture(Result.ok(value)),
                new ArrayList<>(),
                null,
                AsyncContext.empty()
        );
    }

    static <T> DefaultAsync<T> failed(Throwable throwable) {
        return new DefaultAsync<>(
                exec -> CompletableFuture.completedFuture(Result.err(throwable)),
                new ArrayList<>(),
                null,
                AsyncContext.empty()
        );
    }

    // ==================== 配置（惰性，返回新实例）====================

    @SuppressWarnings("unchecked")
    static <T> DefaultAsync<List<T>> all(List<? extends Async<T>> tasks) {
        Function<Executor, CompletableFuture<Result<List<T>, Throwable>>> computation = exec -> {
            if (tasks.isEmpty()) {
                return CompletableFuture.completedFuture(Result.ok(List.of()));
            }

            List<CompletableFuture<Result<T, Throwable>>> futures = new ArrayList<>();
            for (Async<T> task : tasks) {
                futures.add(task.submit());
            }

            @SuppressWarnings("rawtypes")
            CompletableFuture<Void> allDone =
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

            return allDone.thenApply(v -> {
                List<T> results = new ArrayList<>();
                List<Throwable> errors = new ArrayList<>();
                for (CompletableFuture<Result<T, Throwable>> f : futures) {
                    Result<T, Throwable> r = f.join();
                    if (r.isErr()) {
                        errors.add(r.getErr());
                    } else {
                        results.add(r.get());
                    }
                }
                if (!errors.isEmpty()) {
                    Throwable err = errors.size() == 1 ? errors.get(0) : new AggregateException(errors);
                    return Result.<List<T>, Throwable>err(err);
                }
                return Result.<List<T>, Throwable>ok(results);
            });
        };

        return new DefaultAsync<>(computation, new ArrayList<>(), null, AsyncContext.empty());
    }

    static <T> DefaultAsync<T> any(List<? extends Async<T>> tasks) {
        Function<Executor, CompletableFuture<Result<T, Throwable>>> computation = exec -> {
            if (tasks.isEmpty()) {
                return CompletableFuture.completedFuture(
                        Result.err(new IllegalArgumentException("any() requires at least one task")));
            }

            CompletableFuture<Result<T, Throwable>> promise = new CompletableFuture<>();
            AtomicInteger remaining = new AtomicInteger(tasks.size());
            // 使用线程安全列表收集所有失败原因，而非只保留最后一个
            List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

            for (Async<T> task : tasks) {
                task.submit().whenComplete((result, ex) -> {
                    if (ex != null) {
                        errors.add(ex);
                    } else if (result.isOk()) {
                        // 第一个成功立即完成 promise，后续结果被忽略
                        promise.complete(result);
                    } else {
                        errors.add(result.getErr());
                    }
                    if (remaining.decrementAndGet() == 0) {
                        // 全部完成且 promise 未被成功完成，说明全部失败
                        Throwable err = errors.size() == 1 ? errors.get(0) : new AggregateException(errors);
                        promise.complete(Result.err(err));
                    }
                });
            }

            return promise;
        };

        return new DefaultAsync<>(computation, new ArrayList<>(), null, AsyncContext.empty());
    }

    @Override
    public Async<T> name(String name) {
        return new DefaultAsync<>(computation, interceptors, executor, context.withName(name));
    }

    @Override
    public Async<T> executor(Executor executor) {
        return new DefaultAsync<>(computation, interceptors, executor, context);
    }

    @Override
    public Async<T> intercept(AsyncInterceptor... interceptors) {
        List<AsyncInterceptor> merged = new ArrayList<>(this.interceptors);
        merged.addAll(Arrays.asList(interceptors));
        return new DefaultAsync<>(computation, merged, executor, context);
    }

    // ==================== 变换（惰性 pipeline）====================

    @Override
    public Async<T> intercept(List<AsyncInterceptor> interceptors) {
        List<AsyncInterceptor> merged = new ArrayList<>(this.interceptors);
        merged.addAll(interceptors);
        return new DefaultAsync<>(computation, merged, executor, context);
    }

    @Override
    public Async<T> attribute(String key, Object value) {
        return new DefaultAsync<>(computation, interceptors, executor, context.with(key, value));
    }

    @Override
    public <U> Async<U> map(Function<? super T, ? extends U> mapper) {
        Function<Executor, CompletableFuture<Result<U, Throwable>>> newComp = exec ->
                computation.apply(exec).thenApply(result -> {
                    if (result.isErr()) {
                        @SuppressWarnings("unchecked")
                        Result<U, Throwable> err = (Result<U, Throwable>) result;
                        return err;
                    }
                    try {
                        return Result.<U, Throwable>ok(mapper.apply(result.get()));
                    } catch (Exception e) {
                        return Result.<U, Throwable>err(e);
                    }
                });
        return new DefaultAsync<>(newComp, interceptors, executor, context);
    }

    @Override
    public <U> Async<U> flatMap(Function<? super T, ? extends Async<U>> mapper) {
        Function<Executor, CompletableFuture<Result<U, Throwable>>> newComp = exec ->
                computation.apply(exec).thenCompose(result -> {
                    if (result.isErr()) {
                        @SuppressWarnings("unchecked")
                        Result<U, Throwable> err = (Result<U, Throwable>) result;
                        return CompletableFuture.completedFuture(err);
                    }
                    try {
                        Async<U> inner = mapper.apply(result.get());
                        // 内层 Async 独立 submit，其拦截器会正常运行
                        return inner.submit();
                    } catch (Exception e) {
                        return CompletableFuture.completedFuture(Result.<U, Throwable>err(e));
                    }
                });
        return new DefaultAsync<>(newComp, interceptors, executor, context);
    }

    @Override
    public Async<T> recover(Function<? super Throwable, ? extends T> recovery) {
        Function<Executor, CompletableFuture<Result<T, Throwable>>> newComp = exec ->
                computation.apply(exec).thenApply(result -> {
                    if (result.isOk()) {
                        return result;
                    }
                    try {
                        return Result.<T, Throwable>ok(recovery.apply(result.getErr()));
                    } catch (Exception e) {
                        return Result.<T, Throwable>err(e);
                    }
                });
        return new DefaultAsync<>(newComp, interceptors, executor, context);
    }

    @Override
    public Async<T> recoverWith(Function<? super Throwable, ? extends Async<T>> recovery) {
        Function<Executor, CompletableFuture<Result<T, Throwable>>> newComp = exec ->
                computation.apply(exec).thenCompose(result -> {
                    if (result.isOk()) {
                        return CompletableFuture.completedFuture(result);
                    }
                    try {
                        return recovery.apply(result.getErr()).submit();
                    } catch (Exception e) {
                        return CompletableFuture.completedFuture(Result.<T, Throwable>err(e));
                    }
                });
        return new DefaultAsync<>(newComp, interceptors, executor, context);
    }

    /**
     * 超时仅影响观察侧：返回的 future 按时超时，但底层计算不被中断，会继续跑完
     * （虚拟线程静默占用）——资源密集/长任务慎用；真取消需可取消句柄，记 roadmap。
     */
    @Override
    public Async<T> timeout(Duration duration) {
        Function<Executor, CompletableFuture<Result<T, Throwable>>> newComp = exec ->
                computation.apply(exec)
                        .orTimeout(duration.toNanos(), TimeUnit.NANOSECONDS)
                        .exceptionally(ex -> {
                            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                            TimeoutException te = (cause instanceof TimeoutException t) ? t
                                    : new TimeoutException(cause.getMessage());
                            return Result.<T, Throwable>err(te);
                        });
        return new DefaultAsync<>(newComp, interceptors, executor, context);
    }

    // ==================== 终端操作（触发执行）====================

    @Override
    public Async<T> peek(Consumer<? super T> action) {
        Function<Executor, CompletableFuture<Result<T, Throwable>>> newComp = exec ->
                computation.apply(exec).thenApply(result -> {
                    if (result.isOk()) {
                        try {
                            action.accept(result.get());
                        } catch (Exception ignored) {
                            // peek 是副作用，不影响主流程
                        }
                    }
                    return result;
                });
        return new DefaultAsync<>(newComp, interceptors, executor, context);
    }

    // ==================== 并行组合 ====================

    @Override
    public Async<T> peekErr(Consumer<? super Throwable> action) {
        Function<Executor, CompletableFuture<Result<T, Throwable>>> newComp = exec ->
                computation.apply(exec).thenApply(result -> {
                    if (result.isErr()) {
                        try {
                            action.accept(result.getErr());
                        } catch (Exception ignored) {
                            // peek 是副作用，不影响主流程
                        }
                    }
                    return result;
                });
        return new DefaultAsync<>(newComp, interceptors, executor, context);
    }

    @Override
    public CompletableFuture<Result<T, Throwable>> submit() {
        // 按 order 升序排序，order 小的在外层（先执行 before，后执行 after）
        List<AsyncInterceptor> sorted = new ArrayList<>(interceptors);
        sorted.sort((a, b) -> Integer.compare(a.order(), b.order()));

        // 最内层 = 实际 computation：注入当前 executor 字段（可为 null——of() 构造的
        // computation 在 apply 时刻自行兜底虚拟线程，completed/failed/all/any 的
        // computation 忽略该参数）。fluent executor() 覆盖工厂方法给定的执行器由此生效。
        AsyncInvocation<T> chain = () -> computation.apply(executor);
        for (int i = sorted.size() - 1; i >= 0; i--) {
            AsyncInterceptor interceptor = sorted.get(i);
            AsyncInvocation<T> next = chain;
            chain = () -> interceptor.intercept(context, next);
        }

        return chain.proceed();
    }
}
