package cn.code91.facility.result;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

/**
 * <b>操作结果封装类</b>
 * <p>
 * 用于封装可能成功或失败的操作结果，设计灵感来自 Rust 的 Result&lt;T, E&gt;。
 * 成功时持有结果值 T，失败时持有错误信息 E。
 * </p>
 *
 * <h3>设计原则：</h3>
 * <ul>
 *   <li>不可变性：Result 一旦创建，状态不可改变</li>
 *   <li>空安全：成功值允许为 null，错误值不允许为 null</li>
 *   <li>函数式：支持 map、flatMap、recover 等链式操作</li>
 *   <li>线程安全：不可变对象天然线程安全</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 创建结果
 * Result<Integer, String> success = Result.ok(42);
 * Result<Integer, String> failure = Result.err("计算失败");
 *
 * // 链式处理
 * String message = success
 *     .map(n -> n * 2)
 *     .ensure(n -> n > 50, () -> "数值太小")
 *     .map(Object::toString)
 *     .orElse("默认值");
 *
 * // 异常捕获
 * Result<String, Exception> result = Result.of(() -> riskyOperation())
 *     .peekErr(e -> logger.error("操作失败", e))
 *     .recover(e -> "fallback");
 * }</pre>
 *
 * @param <T> 成功时的结果类型
 * @param <E> 失败时的错误类型
 *
 * @author yvvb
 * @apiNote 重构版本，修复了线程安全和异常处理问题
 * @since 2.0.0
 */
public sealed interface Result<T, E> extends Serializable permits Result.Ok, Result.Err {

    @Serial
    long serialVersionUID = 2L;

    // ==================== 工厂方法 ====================

    /**
     * 创建成功结果
     *
     * @param value 成功值（允许为 null）
     */
    static <T, E> Result<T, E> ok(T value) {
        return new Ok<>(value);
    }

    /**
     * 创建无值的成功结果
     */
    static <E> Result<Void, E> ok() {
        return new Ok<>(null);
    }

    /**
     * 创建无值的成功结果（与 {@link #ok(Object)} 传 null 语义等价，但更显式）。
     * <p>设计意图：phase-4 RP-10 / ADR-0007。让"无值成功"有专属 API，与
     * Java {@link java.util.Optional#empty()} 形状对齐。</p>
     *
     * @return 内部 value=null 的 Ok 实例
     * @since phase-4
     */
    static <T, E> Result<T, E> empty() {
        return new Ok<>(null);
    }

    /**
     * 创建失败结果
     *
     * @param error 错误信息（不允许为 null）
     *
     * @throws NullPointerException 如果 error 为 null
     */
    static <T, E> Result<T, E> err(E error) {
        Objects.requireNonNull(error, "错误信息不能为 null");
        return new Err<>(error);
    }

    /**
     * 执行 Supplier，捕获异常转为 Err
     * <p>重构说明：修复了异常处理问题，正确处理中断异常</p>
     *
     * @apiNote 只捕获 Exception，不捕获 Error（如 OOM、StackOverflow）
     */
    static <T> Result<T, Exception> of(ThrowableSupplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier cannot be null");
        try {
            return ok(supplier.get());
        } catch (InterruptedException e) {
            // 恢复中断状态
            Thread.currentThread().interrupt();
            return err(e);
        } catch (Exception e) {
            return err(e);
        }
        // 不捕获 Error 和其他 Throwable，让它们自然传播
    }

    /**
     * 执行 Runnable，捕获异常转为 Err（无返回值）
     */
    static Result<Void, Exception> ofRunnable(ThrowableRunnable runnable) {
        Objects.requireNonNull(runnable, "runnable cannot be null");
        try {
            runnable.run();
            return ok();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return err(e);
        } catch (Exception e) {
            return err(e);
        }
    }

    /**
     * 从 Optional 创建 Result
     *
     * @param optional     Optional 值
     * @param errorIfEmpty 值为空时的错误
     */
    static <T, E> Result<T, E> fromOptional(Optional<T> optional, Supplier<E> errorIfEmpty) {
        Objects.requireNonNull(optional, "optional cannot be null");
        Objects.requireNonNull(errorIfEmpty, "errorIfEmpty cannot be null");
        return optional.<Result<T, E>>map(Result::ok)
                .orElseGet(() -> err(errorIfEmpty.get()));
    }

    /**
     * 从可空值创建 Result
     *
     * @param nullable    可能为 null 的值
     * @param errorIfNull 值为 null 时的错误
     */
    static <T, E> Result<T, E> fromNullable(T nullable, Supplier<E> errorIfNull) {
        Objects.requireNonNull(errorIfNull, "errorIfNull cannot be null");
        return nullable != null ? ok(nullable) : err(errorIfNull.get());
    }

    // ==================== 状态查询 ====================

    /**
     * 短路收集（传统循环方式）
     * <p>
     * 这是真正的短路实现，遇到第一个错误立即返回。
     * </p>
     *
     * @since 2.0.0
     */
    static <T, E> Result<List<T>, E> collectShortCircuit(Iterable<Result<T, E>> results) {
        Objects.requireNonNull(results, "results cannot be null");
        List<T> list = new ArrayList<>();
        for (Result<T, E> result : results) {
            if (result.isErr()) {
                return err(result.getErr());
            }
            list.add(result.get());
        }
        return ok(list);
    }

    /**
     * 全量收集：收集所有结果，将成功值和失败值分别放入两个列表
     * <p>适用于需要处理所有结果（如批处理报告）的场景。</p>
     *
     * @return 如果有错误返回 Err(List&lt;E&gt;)，否则返回 Ok(List&lt;T&gt;)
     */
    static <T, E> Collector<Result<T, E>, ?, Result<List<T>, List<E>>> collectAll() {
        class Accumulator {
            final List<T> successes = new ArrayList<>();
            final List<E> failures = new ArrayList<>();
        }

        return Collector.of(
                Accumulator::new,
                (acc, result) -> {
                    if (result.isOk()) {
                        acc.successes.add(result.get());
                    } else {
                        acc.failures.add(result.getErr());
                    }
                },
                (acc1, acc2) -> {
                    acc1.successes.addAll(acc2.successes);
                    acc1.failures.addAll(acc2.failures);
                    return acc1;
                },
                acc -> acc.failures.isEmpty() ? ok(acc.successes) : err(acc.failures)
        );
    }

    /**
     * 是否成功
     */
    boolean isOk();

    // ==================== 值获取 ====================

    /**
     * 是否失败
     */
    default boolean isErr() {
        return !isOk();
    }

    /**
     * 成功且值满足条件
     */
    default boolean isOkAnd(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate cannot be null");
        return isOk() && predicate.test(get());
    }

    /**
     * 失败且错误满足条件
     */
    default boolean isErrAnd(Predicate<? super E> predicate) {
        Objects.requireNonNull(predicate, "predicate cannot be null");
        return isErr() && predicate.test(getErr());
    }

    /**
     * 获取成功值，失败时抛出异常
     *
     * @throws NoSuchElementException 如果是失败结果
     */
    T get();

    /**
     * 获取错误值，成功时抛出异常
     *
     * @throws NoSuchElementException 如果是成功结果
     */
    E getErr();

    /**
     * 获取成功值，失败时返回默认值
     */
    default T orElse(T defaultValue) {
        return isOk() ? get() : defaultValue;
    }

    /**
     * 获取成功值，失败时通过函数计算默认值
     */
    default T orElseGet(Supplier<? extends T> supplier) {
        Objects.requireNonNull(supplier, "supplier cannot be null");
        return isOk() ? get() : supplier.get();
    }

    /**
     * 获取成功值，失败时根据错误计算默认值
     */
    default T orElseMap(Function<? super E, ? extends T> function) {
        Objects.requireNonNull(function, "function cannot be null");
        return isOk() ? get() : function.apply(getErr());
    }

    /**
     * 获取成功值，失败时抛出指定异常
     */
    default <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        Objects.requireNonNull(exceptionSupplier, "exceptionSupplier cannot be null");
        if (isOk()) {
            return get();
        }
        throw exceptionSupplier.get();
    }

    // ==================== 转换操作 ====================

    /**
     * 获取成功值，失败时根据错误抛出异常
     */
    default <X extends Throwable> T orElseThrow(Function<? super E, ? extends X> exceptionMapper) throws X {
        Objects.requireNonNull(exceptionMapper, "exceptionMapper cannot be null");
        if (isOk()) {
            return get();
        }
        throw exceptionMapper.apply(getErr());
    }

    /**
     * 对成功值进行转换
     */
    <U> Result<U, E> map(Function<? super T, ? extends U> mapper);

    /**
     * 对失败值进行转换
     */
    <F> Result<T, F> mapErr(Function<? super E, ? extends F> mapper);

    /**
     * 同时转换成功值和失败值
     */
    default <U, F> Result<U, F> bimap(
            Function<? super T, ? extends U> okMapper,
            Function<? super E, ? extends F> errMapper) {
        Objects.requireNonNull(okMapper, "okMapper cannot be null");
        Objects.requireNonNull(errMapper, "errMapper cannot be null");
        if (isOk()) {
            return ok(okMapper.apply(get()));
        }
        return err(errMapper.apply(getErr()));
    }

    /**
     * 对成功值进行扁平化转换
     */
    default <U> Result<U, E> flatMap(Function<? super T, ? extends Result<U, E>> mapper) {
        Objects.requireNonNull(mapper, "mapper cannot be null");
        if (isErr()) {
            @SuppressWarnings("unchecked")
            Result<U, E> self = (Result<U, E>) this;
            return self;
        }
        Result<U, E> result = mapper.apply(get());
        return Objects.requireNonNull(result, "flatMap 返回值不能为 null");
    }

    /**
     * 确保值满足条件，否则返回错误
     * <p>语义：校验业务规则</p>
     *
     * @param predicate     校验条件
     * @param errorSupplier 不满足条件时的错误提供者
     */
    default Result<T, E> ensure(Predicate<? super T> predicate, Supplier<? extends E> errorSupplier) {
        Objects.requireNonNull(predicate, "predicate cannot be null");
        Objects.requireNonNull(errorSupplier, "errorSupplier cannot be null");
        if (isErr()) {
            return this;
        }
        return predicate.test(get()) ? this : err(errorSupplier.get());
    }

    // ==================== 组合操作 ====================

    /**
     * 从失败中恢复，返回新的成功值
     */
    default Result<T, E> recover(Function<? super E, ? extends T> recoveryFunction) {
        Objects.requireNonNull(recoveryFunction, "recoveryFunction cannot be null");
        if (isOk()) {
            return this;
        }
        return ok(recoveryFunction.apply(getErr()));
    }

    /**
     * 从失败中恢复，返回新的 Result
     */
    default Result<T, E> recoverWith(Function<? super E, ? extends Result<T, E>> recoveryFunction) {
        Objects.requireNonNull(recoveryFunction, "recoveryFunction cannot be null");
        if (isOk()) {
            return this;
        }
        Result<T, E> result = recoveryFunction.apply(getErr());
        return Objects.requireNonNull(result, "recoverWith 返回值不能为 null");
    }

    /**
     * 交换成功和失败。
     * <p>{@code Ok(v)} → {@code Err(v)}；{@code Err(e)} → {@code Ok(e)}。
     * 因 {@code Err} 不变量要求非空错误值，对 {@code Ok(null)}（如 {@link #empty()}）
     * 抛 {@link IllegalStateException}（RV2-03）。</p>
     */
    default Result<E, T> swap() {
        if (isOk()) {
            T value = get();
            if (value == null) {
                throw new IllegalStateException(
                    "cannot swap a null-valued Ok (e.g. Result.empty()) into Err: Err requires a non-null error");
            }
            return err(value);
        }
        return ok(getErr());
    }

    /**
     * 如果当前成功，返回 other；否则返回当前错误
     */
    default <U> Result<U, E> and(Result<U, E> other) {
        Objects.requireNonNull(other, "other cannot be null");
        if (isErr()) {
            @SuppressWarnings("unchecked")
            Result<U, E> self = (Result<U, E>) this;
            return self;
        }
        return other;
    }

    // ==================== 副作用操作 ====================

    /**
     * 如果当前成功，执行 supplier 返回新 Result；否则返回当前错误
     */
    default <U> Result<U, E> andThen(Supplier<? extends Result<U, E>> supplier) {
        Objects.requireNonNull(supplier, "supplier cannot be null");
        if (isErr()) {
            @SuppressWarnings("unchecked")
            Result<U, E> self = (Result<U, E>) this;
            return self;
        }
        return Objects.requireNonNull(supplier.get(), "andThen 返回值不能为 null");
    }

    /**
     * 如果当前失败，返回 other；否则返回当前成功值
     */
    default Result<T, E> or(Result<T, E> other) {
        Objects.requireNonNull(other, "other cannot be null");
        return isOk() ? this : other;
    }

    /**
     * 如果当前失败，执行 supplier 返回新 Result；否则返回当前成功值
     * <p>重构说明：重命名为 orElseSupplier 避免与 orElse(T) 重载歧义</p>
     *
     * @since 2.0.0
     */
    default Result<T, E> orElseSupplier(Supplier<? extends Result<T, E>> supplier) {
        Objects.requireNonNull(supplier, "supplier cannot be null");
        if (isOk()) {
            return this;
        }
        return Objects.requireNonNull(supplier.get(), "orElseSupplier 返回值不能为 null");
    }

    /**
     * 成功时执行操作（查看成功值），返回自身用于链式调用
     */
    default Result<T, E> peek(Consumer<? super T> action) {
        Objects.requireNonNull(action, "action cannot be null");
        if (isOk()) {
            action.accept(get());
        }
        return this;
    }

    /**
     * 失败时执行操作（查看错误值），返回自身用于链式调用
     */
    default Result<T, E> peekErr(Consumer<? super E> action) {
        Objects.requireNonNull(action, "action cannot be null");
        if (isErr()) {
            action.accept(getErr());
        }
        return this;
    }

    /**
     * 成功时执行操作
     */
    default void ifOk(Consumer<? super T> action) {
        Objects.requireNonNull(action, "action cannot be null");
        if (isOk()) {
            action.accept(get());
        }
    }

    // ==================== 互操作 ====================

    /**
     * 失败时执行操作
     */
    default void ifErr(Consumer<? super E> action) {
        Objects.requireNonNull(action, "action cannot be null");
        if (isErr()) {
            action.accept(getErr());
        }
    }

    /**
     * 根据结果状态执行不同操作
     */
    default void match(Consumer<? super T> okAction, Consumer<? super E> errAction) {
        Objects.requireNonNull(okAction, "okAction cannot be null");
        Objects.requireNonNull(errAction, "errAction cannot be null");
        if (isOk()) {
            okAction.accept(get());
        } else {
            errAction.accept(getErr());
        }
    }

    /**
     * 根据结果状态返回不同值（模式匹配）
     */
    default <U> U fold(Function<? super T, ? extends U> okMapper, Function<? super E, ? extends U> errMapper) {
        Objects.requireNonNull(okMapper, "okMapper cannot be null");
        Objects.requireNonNull(errMapper, "errMapper cannot be null");
        return isOk() ? okMapper.apply(get()) : errMapper.apply(getErr());
    }

    // ==================== Collectors ====================

    /**
     * 转换为 Optional（仅保留成功值）
     */
    default Optional<T> toOptional() {
        return isOk() ? Optional.ofNullable(get()) : Optional.empty();
    }

    /**
     * 错误转换为 Optional
     */
    default Optional<E> toOptionalErr() {
        return isErr() ? Optional.of(getErr()) : Optional.empty();
    }

    /**
     * 转换为 Stream（仅保留成功值）
     */
    default Stream<T> stream() {
        return isOk() ? Stream.ofNullable(get()) : Stream.empty();
    }

    // ==================== 函数式接口 ====================

    /**
     * 可抛出异常的 Supplier
     */
    @FunctionalInterface
    interface ThrowableSupplier<T> {
        T get() throws Exception;
    }

    /**
     * 可抛出异常的 Runnable
     */
    @FunctionalInterface
    interface ThrowableRunnable {
        void run() throws Exception;
    }

    // ==================== 成功实现 ====================

    /**
     * 成功结果实现（不可变、线程安全）
     */
    record Ok<T, E>(T value) implements Result<T, E> {

        @Serial
        private static final long serialVersionUID = 2L;

        @Override
        public boolean isOk() {
            return true;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public E getErr() {
            throw new NoSuchElementException("调用 getErr() 但 Result 是 Ok");
        }

        @Override
        public <U> Result<U, E> map(Function<? super T, ? extends U> mapper) {
            Objects.requireNonNull(mapper, "mapper cannot be null");
            return new Ok<>(mapper.apply(value));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <F> Result<T, F> mapErr(Function<? super E, ? extends F> mapper) {
            Objects.requireNonNull(mapper, "mapper cannot be null");
            // Ok 不持有 E，可以安全地复用实例
            return (Result<T, F>) this;
        }

        @Override
        public String toString() {
            return "Ok(" + value + ")";
        }
    }

    // ==================== 失败实现 ====================

    /**
     * 失败结果实现（不可变、线程安全）
     */
    record Err<T, E>(E error) implements Result<T, E> {

        @Serial
        private static final long serialVersionUID = 2L;

        public Err {
            Objects.requireNonNull(error, "错误信息不能为 null");
        }

        @Override
        public boolean isOk() {
            return false;
        }

        @Override
        public T get() {
            if (error instanceof Throwable t) {
                throw new NoSuchElementException("调用 get() 但 Result 是 Err: " + t.getMessage(), t);
            }
            throw new NoSuchElementException("调用 get() 但 Result 是 Err: " + error);
        }

        @Override
        public E getErr() {
            return error;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <U> Result<U, E> map(Function<? super T, ? extends U> mapper) {
            Objects.requireNonNull(mapper, "mapper cannot be null");
            // Err 不持有 T，可以安全地复用实例
            return (Result<U, E>) this;
        }

        @Override
        public <F> Result<T, F> mapErr(Function<? super E, ? extends F> mapper) {
            Objects.requireNonNull(mapper, "mapper cannot be null");
            return new Err<>(mapper.apply(error));
        }

        @Override
        public String toString() {
            return "Err(" + error + ")";
        }
    }
}
