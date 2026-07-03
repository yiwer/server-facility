package cn.code91.facility.async;

import cn.code91.facility.result.Result;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * <b>异步任务拦截器 SPI</b>
 * <p>
 * 基于 around 模式的拦截器接口，可在异步任务执行前后插入横切逻辑，
 * 适用于日志监控、事务包裹、动态数据源上下文切换等场景。
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
 *         DynamicDsHolder.set(ds);
 *         return invocation.proceed()
 *             .whenComplete((r, e) -> DynamicDsHolder.clear());
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
     * 拦截异步任务执行
     *
     * @param context    任务上下文（名称 + 属性袋）
     * @param invocation 下游调用，调用 {@code invocation.proceed()} 继续链
     * @return 异步结果 Future
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
            Consumer<AsyncContext> before,
            BiConsumer<AsyncContext, Result<?, Throwable>> after) {
        return new AsyncInterceptor() {
            @Override
            public <T> CompletableFuture<Result<T, Throwable>> intercept(
                    AsyncContext context, AsyncInvocation<T> invocation) {
                if (before != null) {
                    before.accept(context);
                }
                CompletableFuture<Result<T, Throwable>> future = invocation.proceed();
                if (after != null) {
                    return future.whenComplete((result, ex) -> {
                        Result<T, Throwable> r = (ex != null) ? Result.err(ex) : result;
                        after.accept(context, r);
                    });
                }
                return future;
            }
        };
    }
}
