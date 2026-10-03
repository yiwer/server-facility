package cn.code91.facility.async;

import cn.code91.facility.result.Result;
import jakarta.annotation.Nullable;
import java.util.Objects;

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
 * 直到调用 {@link #submit()} 或 {@link #await()} 才真正触发执行。每个用户执行段在选定执行器上应用拦截器链。
 * </p>
 *
 * <h3>执行模型：</h3>
 * <pre>
 * 每个 supplier / mapper / recovery / effect 执行段:
 *   安装提交时捕获的 MDC
 *   interceptor[order=0]  ← 最外层
 *     interceptor[order=10]
 *       interceptor[order=20]
 *         当前用户回调（同步完成）
 *   finally 恢复工作线程原 MDC
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
     * 创建惰性异步任务（共享平台线程池：4 个 daemon 工作线程、256 个队列项，空闲 30 秒回收）
     */
    static <T> Async<T> supply(ThrowableSupplier<T> supplier) {
        return DefaultAsync.of(supplier, null);
    }

    /**
     * 创建惰性异步任务（使用指定执行器；执行器归调用方所有，Async 不关闭它）
     */
    static <T> Async<T> supply(ThrowableSupplier<T> supplier, Executor executor) {
        return DefaultAsync.of(supplier, Objects.requireNonNull(executor, "executor"));
    }

    /**
     * 创建无返回值的惰性异步任务（使用共享、有界的平台线程执行器）
     */
    static Async<Void> run(ThrowableRunnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        return DefaultAsync.of(() -> {
            runnable.run();
            return null;
        }, null);
    }

    /**
     * 创建无返回值的惰性异步任务（使用指定执行器）
     */
    static Async<Void> run(ThrowableRunnable runnable, Executor executor) {
        Objects.requireNonNull(runnable, "runnable");
        Objects.requireNonNull(executor, "executor");
        return DefaultAsync.of(() -> {
            runnable.run();
            return null;
        }, executor);
    }

    /**
     * 创建已知的成功任务（无拦截器时立即完成；有拦截器时在执行器上执行其作用域）
     */
    static <T> Async<T> completed(@Nullable T value) {
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
     * 并行执行所有任务，返回第一个成功的结果，并协作式取消其他任务。
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
     * 设置执行器（提交时生效，覆盖本任务工厂的执行器；子任务未指定执行器时继承）
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
    Async<T> attribute(String key, @Nullable Object value);

    /**
     * 对成功结果进行转换
     */
    <U> Async<U> map(Function<? super T, ? extends U> mapper);

    /**
     * 对成功结果进行扁平化转换（内层继承执行器、剩余预算、MDC 及拦截器，可覆盖自己的执行器/元数据）
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
     * 设置整次提交的预算，从 submit 开始以单调时间计算，等于截止时间即超时。
     * 零/负时长立即超时；重复设置只缩短预算；子任务不能延长父截止时间。
     * 超时返回 {@code Result.err(TimeoutException)} 并协作式中断工作，不保证任意代码已停止。
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
     * 触发执行，返回 CompletableFuture；异步执行器下非阻塞，直接执行器可在调用线程执行。
     * cancel(true) 向实际工作传播中断；cancel(false) 不中断运行中的工作。
     * Future 取消遵循 JDK CancellationException 语义；已完成结果不能被取消改写。
     */
    CompletableFuture<Result<T, Throwable>> submit();

    // ==================== 并行组合 ====================

    /**
     * 阻塞等待执行完成，返回 {@link Result}。
     * <p>不会抛出异常，失败信息包含在 {@code Result.err()} 中。</p>
     */
    default Result<T, Throwable> await() {
        CompletableFuture<Result<T, Throwable>> submission = submit();
        try {
            return submission.get();
        } catch (InterruptedException e) {
            submission.cancel(true);
            Thread.currentThread().interrupt();
            return Result.err(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return Result.err(cause != null ? cause : e);
        }
    }

    /**
     * 阻塞等待执行完成（带超时），返回 {@link Result}。
     * <p>预算包含整个任务树；超时时返回 {@code Result.err(TimeoutException)} 并中断工作。
     * 等待被中断时取消工作、恢复中断标志并抛出异常。</p>
     *
     * @throws InterruptedException 如果等待被中断
     */
    default Result<T, Throwable> await(Duration timeout) throws InterruptedException {
        CompletableFuture<Result<T, Throwable>> submission = timeout(timeout).submit();
        try {
            return submission.get();
        } catch (InterruptedException e) {
            submission.cancel(true);
            Thread.currentThread().interrupt();
            throw e;
        } catch (CancellationException e) {
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
