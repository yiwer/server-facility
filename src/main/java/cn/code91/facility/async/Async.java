package cn.code91.facility.async;

import cn.code91.facility.result.Result;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * <b>惰性异步计算封装</b>
 * <p>
 * 类比 Rust Future / Reactor Mono，描述一个尚未执行的异步计算。
 * 调用 {@link #supply}、{@link #map}、{@link #flatMap} 等方法只构建计算描述，
 * 直到调用 {@link #submit()} 或 {@link #await()} 才真正触发执行并应用拦截器链。
 * </p>
 *
 * <h3>执行模型：</h3>
 * <pre>
 * submit() 触发执行:
 *   interceptor[order=0]  ← 最外层
 *     interceptor[order=10]
 *       interceptor[order=20]
 *         actualTask + map/flatMap pipeline
 * </pre>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * AsyncInterceptor logging = AsyncInterceptor.around(
 *     ctx -> log.info("[{}] start", ctx.name()),
 *     (ctx, r) -> log.info("[{}] done, ok={}", ctx.name(), r.isOk())
 * );
 *
 * // 阻塞等待，返回 Result
 * Result<String, Throwable> result = Async.supply(() -> userRepo.findById(id))
 *     .name("findUser")
 *     .attribute("ds", "read-replica")
 *     .intercept(logging, new DsInterceptor())
 *     .map(User::getName)
 *     .await();
 *
 * // 直接获取值，失败时抛出异常
 * String name = Async.supply(() -> userRepo.findById(id))
 *     .map(User::getName)
 *     .recover(e -> "unknown")
 *     .awaitValue();
 * }</pre>
 *
 * @param <T> 计算结果类型
 *
 * @author yvvb
 * @since 1.0.0
 */
public sealed interface Async<T> permits DefaultAsync {

    // ==================== 工厂方法 ====================

    /**
     * 创建惰性异步任务（使用默认虚拟线程执行器）
     */
    static <T> Async<T> supply(ThrowableSupplier<T> supplier) {
        return DefaultAsync.of(supplier, null);
    }

    /**
     * 创建惰性异步任务（使用指定执行器）
     */
    static <T> Async<T> supply(ThrowableSupplier<T> supplier, Executor executor) {
        return DefaultAsync.of(supplier, executor);
    }

    /**
     * 创建无返回值的惰性异步任务（使用默认虚拟线程执行器）
     */
    static Async<Void> run(ThrowableRunnable runnable) {
        return DefaultAsync.of(() -> {
            runnable.run();
            return null;
        }, null);
    }

    /**
     * 创建无返回值的惰性异步任务（使用指定执行器）
     */
    static Async<Void> run(ThrowableRunnable runnable, Executor executor) {
        return DefaultAsync.of(() -> {
            runnable.run();
            return null;
        }, executor);
    }

    /**
     * 创建已完成的成功任务（已知值，不执行异步）
     */
    static <T> Async<T> completed(T value) {
        return DefaultAsync.completed(value);
    }

    /**
     * 创建已失败的任务
     */
    static <T> Async<T> failed(Throwable throwable) {
        return DefaultAsync.failed(throwable);
    }

    // ==================== 配置（惰性，返回新实例）====================

    /**
     * 并行执行所有任务，全部成功时返回结果列表。
     * <p>任意任务失败时，等待其余任务完成后收集所有错误并返回 {@link AggregateException}；
     * 只有一个任务失败时直接返回该异常。</p>
     */
    static <T> Async<List<T>> all(List<? extends Async<T>> tasks) {
        return DefaultAsync.all(tasks);
    }

    /**
     * 并行执行所有任务，全部成功时返回结果列表（varargs 便利方法）
     *
     * @see #all(List)
     */
    @SafeVarargs
    static <T> Async<List<T>> all(Async<T>... tasks) {
        return DefaultAsync.all(List.of(tasks));
    }

    /**
     * 并行执行所有任务，返回第一个成功的结果。
     * <p>全部失败时收集所有错误并返回 {@link AggregateException}；
     * 只有一个任务失败时直接返回该异常。</p>
     */
    static <T> Async<T> any(List<? extends Async<T>> tasks) {
        return DefaultAsync.any(tasks);
    }

    /**
     * 并行执行所有任务，返回第一个成功的结果（varargs 便利方法）
     *
     * @see #any(List)
     */
    @SafeVarargs
    static <T> Async<T> any(Async<T>... tasks) {
        return DefaultAsync.any(List.of(tasks));
    }

    /**
     * 设置任务名称（用于日志、监控）
     */
    Async<T> name(String name);

    // ==================== 变换（惰性 pipeline，不触发执行）====================

    /**
     * 设置执行器
     */
    Async<T> executor(Executor executor);

    /**
     * 添加拦截器
     */
    Async<T> intercept(AsyncInterceptor... interceptors);

    /**
     * 添加拦截器列表
     */
    Async<T> intercept(List<AsyncInterceptor> interceptors);

    /**
     * 设置上下文属性（供拦截器读取）
     */
    Async<T> attribute(String key, Object value);

    /**
     * 对成功结果进行转换
     */
    <U> Async<U> map(Function<? super T, ? extends U> mapper);

    /**
     * 对成功结果进行扁平化转换（内层 Async 的拦截器将独立执行）
     */
    <U> Async<U> flatMap(Function<? super T, ? extends Async<U>> mapper);

    /**
     * 从失败中恢复，返回替代值
     */
    Async<T> recover(Function<? super Throwable, ? extends T> recovery);

    // ==================== 终端操作（触发执行）====================

    /**
     * 从失败中恢复，返回替代 Async 任务
     */
    Async<T> recoverWith(Function<? super Throwable, ? extends Async<T>> recovery);

    /**
     * 设置超时，超时后结果为 {@code Result.err(TimeoutException)}
     */
    Async<T> timeout(Duration duration);

    /**
     * 成功时执行副作用（不改变结果）
     */
    Async<T> peek(Consumer<? super T> action);

    /**
     * 失败时执行副作用（不改变结果）
     */
    Async<T> peekErr(Consumer<? super Throwable> action);

    /**
     * 触发执行，返回 CompletableFuture（非阻塞）
     */
    CompletableFuture<Result<T, Throwable>> submit();

    // ==================== 并行组合 ====================

    /**
     * 阻塞等待执行完成，返回 {@link Result}。
     * <p>不会抛出异常，失败信息包含在 {@code Result.err()} 中。</p>
     */
    default Result<T, Throwable> await() {
        try {
            return submit().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.err(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return Result.err(cause != null ? cause : e);
        }
    }

    /**
     * 阻塞等待执行完成（带超时），返回 {@link Result}。
     * <p>超时时返回 {@code Result.err(TimeoutException)}；中断时抛出异常。</p>
     *
     * @throws InterruptedException 如果等待被中断
     */
    default Result<T, Throwable> await(Duration timeout) throws InterruptedException {
        try {
            return submit().get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (TimeoutException e) {
            return Result.err(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return Result.err(cause != null ? cause : e);
        }
    }

    /**
     * 阻塞等待并直接返回成功值。
     * <p>
     * 相比 {@link #await()}，省去手动检查 {@code result.isErr()} 的步骤，
     * 适合确信会成功或已通过 {@link #recover} 兜底的场景。
     * 失败时将原始异常包装为 {@link RuntimeException} 抛出。
     * </p>
     *
     * <pre>{@code
     * // 不用每次写 result.get()
     * String name = Async.supply(() -> findUser(id))
     *     .map(User::getName)
     *     .recover(e -> "unknown")
     *     .awaitValue();
     * }</pre>
     */
    default T awaitValue() {
        Result<T, Throwable> result = await();
        if (result.isErr()) {
            Throwable err = result.getErr();
            if (err instanceof RuntimeException re) throw re;
            throw new RuntimeException(err);
        }
        return result.get();
    }

    /**
     * 阻塞等待（带超时）并直接返回成功值。
     * <p>失败或超时时抛出异常；中断时抛出 {@link InterruptedException}。</p>
     *
     * @throws InterruptedException 如果等待被中断
     */
    default T awaitValue(Duration timeout) throws InterruptedException {
        Result<T, Throwable> result = await(timeout);
        if (result.isErr()) {
            Throwable err = result.getErr();
            if (err instanceof InterruptedException ie) throw ie;
            if (err instanceof RuntimeException re) throw re;
            throw new RuntimeException(err);
        }
        return result.get();
    }

    // ==================== 嵌套函数式接口 ====================

    /**
     * 可抛出受检异常的 Supplier
     */
    @FunctionalInterface
    interface ThrowableSupplier<T> {
        T get() throws Exception;
    }

    /**
     * 可抛出受检异常的 Runnable
     */
    @FunctionalInterface
    interface ThrowableRunnable {
        void run() throws Exception;
    }
}
