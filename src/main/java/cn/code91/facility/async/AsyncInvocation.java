package cn.code91.facility.async;

import cn.code91.facility.result.Result;

import java.util.concurrent.CompletableFuture;

/**
 * <b>拦截器链调用抽象</b>
 * <p>
 * 类比 Spring AOP 的 {@code MethodInvocation}，代表链中下游（更靠近实际任务）的调用。
 * 拦截器通过调用 {@link #proceed()} 将控制权交给下一个拦截器或实际任务。
 * </p>
 *
 * <h3>around 拦截示例：</h3>
 * <pre>{@code
 * public <T> CompletableFuture<Result<T, Throwable>> intercept(
 *         AsyncContext ctx, AsyncInvocation<T> invocation) {
 *     log.info("before: {}", ctx.name());
 *     return invocation.proceed()
 *         .whenComplete((r, e) -> log.info("after: {}", ctx.name()));
 * }
 * }</pre>
 *
 * @param <T> 任务结果类型
 * @author yvvb
 * @since 1.0.0
 */
@FunctionalInterface
public interface AsyncInvocation<T> {

    /**
     * 向链的下游传递控制权，执行下一个拦截器或实际任务。
     *
     * @return 代表异步结果的 CompletableFuture
     */
    CompletableFuture<Result<T, Throwable>> proceed();
}
