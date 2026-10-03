package cn.code91.facility.async;

import cn.code91.facility.result.Result;
import jakarta.annotation.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * <b>异步任务拦截器 SPI</b>
 * <p>
 * 每个用户执行段（supplier、mapper、recovery、effect）在实际工作线程同步进入与退出。
 * 同一逻辑 pipeline 可以多次执行此链。proceed 返回已经完成的 Future；
 * 拦截器也必须返回已完成 Future，不能自行异步派发。用 try/finally 恢复 ThreadLocal 原值。
 * 仅传播 MDC 与显式元数据；不会复制调用线程的事务或安全身份。
 * </p>
 *
 * <h3>执行顺序：</h3>
 * <pre>
 * order=0  (最外层)
 *   order=10
 *     order=20  (最内层，最靠近实际任务)
 *       actualTask
 * </pre>
 *
 * <h3>实现示例（动态数据源）：</h3>
 * <pre>{@code
 * class DsInterceptor implements AsyncInterceptor {
 *     public <T> CompletableFuture<Result<T, Throwable>> intercept(
 *             AsyncContext ctx, AsyncInvocation<T> invocation) {
 *         String ds = ctx.<String>attribute("ds").orElse("primary");
 *         String previous = DynamicDsHolder.get();
 *         DynamicDsHolder.set(ds);
 *         try {
 *             return invocation.proceed();
 *         } finally {
 *             if (previous == null) DynamicDsHolder.clear();
 *             else DynamicDsHolder.set(previous);
 *         }
 *     }
 *     public int order() { return 10; }
 * }
 * }</pre>
 *
 * @author yvvb
 * @since 1.0.0
 */
public interface AsyncInterceptor {

    /**
     * 同步拦截一个用户执行段。链顺序稳定，子任务继承父拦截器。
     *
     * @param context    任务上下文（名称 + 属性袋）
     * @param invocation 下游调用，调用 {@code invocation.proceed()} 继续链
     * @return 已完成的结果 Future；未完成 Future 被拒绝为 IllegalStateException
     */
    <T> CompletableFuture<Result<T, Throwable>> intercept(
            AsyncContext context, AsyncInvocation<T> invocation);

    /**
     * 拦截器顺序。数值越小越靠外层（先 before、后 after）。默认 0。
     */
    default int order() {
        return 0;
    }

    // ==================== 工厂辅助方法 ====================

    /**
     * 创建仅在任务执行前触发的拦截器
     *
     * @param action 接收上下文的 before 动作
     */
    static AsyncInterceptor before(Consumer<AsyncContext> action) {
        return around(action, null);
    }

    /**
     * 创建仅在任务执行后触发的拦截器（无论成功或失败）
     *
     * @param action 接收上下文和结果的 after 动作
     */
    static AsyncInterceptor after(BiConsumer<AsyncContext, Result<?, Throwable>> action) {
        return around(null, action);
    }

    /**
     * 创建 around 拦截器（before + after）
     *
     * @param before 执行前动作，可为 null
     * @param after  执行后动作，可为 null
     */
    static AsyncInterceptor around(
            @Nullable Consumer<AsyncContext> before,
            @Nullable BiConsumer<AsyncContext, Result<?, Throwable>> after) {
        return new AsyncInterceptor() {
            @Override
            public <T> CompletableFuture<Result<T, Throwable>> intercept(
                    AsyncContext context, AsyncInvocation<T> invocation) {
                Result<T, Throwable> result;
                try {
                    if (before != null) before.accept(context);
                    var future = java.util.Objects.requireNonNull(invocation.proceed(), "invocation result");
                    if (!future.isDone()) throw new IllegalStateException("AsyncInterceptor must complete within its execution scope");
                    result = java.util.Objects.requireNonNull(future.join(), "invocation Result");
                } catch (Throwable failure) {
                    result = Result.err(DefaultAsync.unwrap(failure));
                }
                try {
                    return CompletableFuture.completedFuture(result);
                } finally {
                    if (after != null) after.accept(context, result);
                }
            }
        };
    }
}
