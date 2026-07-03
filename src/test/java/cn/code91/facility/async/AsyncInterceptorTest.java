package cn.code91.facility.async;

import cn.code91.facility.result.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AsyncInterceptor - 异步任务拦截器")
class AsyncInterceptorTest {

    // ==================== 工厂方法 ====================

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        @DisplayName("before 创建仅在执行前触发的拦截器")
        void before_executesBeforeTask() {
            AtomicBoolean beforeCalled = new AtomicBoolean(false);
            AsyncInterceptor interceptor = AsyncInterceptor.before(ctx -> beforeCalled.set(true));

            Async.supply(() -> "value")
                    .intercept(interceptor)
                    .awaitValue();

            assertThat(beforeCalled).isTrue();
        }

        @Test
        @DisplayName("before 拦截器能读取上下文")
        void before_readsContext() {
            AtomicReference<String> capturedName = new AtomicReference<>();
            AsyncInterceptor interceptor = AsyncInterceptor.before(ctx -> capturedName.set(ctx.name()));

            Async.supply(() -> "value")
                    .name("testTask")
                    .intercept(interceptor)
                    .awaitValue();

            assertThat(capturedName.get()).isEqualTo("testTask");
        }

        @Test
        @DisplayName("after 创建仅在执行后触发的拦截器")
        void after_executesAfterTask() {
            AtomicBoolean afterCalled = new AtomicBoolean(false);
            AtomicReference<Result<?, Throwable>> capturedResult = new AtomicReference<>();
            AsyncInterceptor interceptor = AsyncInterceptor.after((ctx, result) -> {
                afterCalled.set(true);
                capturedResult.set(result);
            });

            Async.supply(() -> "value")
                    .intercept(interceptor)
                    .awaitValue();

            assertThat(afterCalled).isTrue();
            assertThat(capturedResult.get().isOk()).isTrue();
        }

        @Test
        @DisplayName("after 失败时也能接收到结果")
        void after_receivesErrResult() {
            AtomicReference<Result<?, Throwable>> capturedResult = new AtomicReference<>();
            AsyncInterceptor interceptor = AsyncInterceptor.after((ctx, result) -> capturedResult.set(result));

            Async.<String>supply(() -> { throw new RuntimeException("fail"); })
                    .intercept(interceptor)
                    .await();

            assertThat(capturedResult.get()).isNotNull();
            assertThat(capturedResult.get().isErr()).isTrue();
        }

        @Test
        @DisplayName("around 创建 before + after 拦截器")
        void around_executesBothBeforeAndAfter() {
            AtomicBoolean beforeCalled = new AtomicBoolean(false);
            AtomicBoolean afterCalled = new AtomicBoolean(false);
            AsyncInterceptor interceptor = AsyncInterceptor.around(
                    ctx -> beforeCalled.set(true),
                    (ctx, result) -> afterCalled.set(true)
            );

            Async.supply(() -> "value")
                    .intercept(interceptor)
                    .awaitValue();

            assertThat(beforeCalled).isTrue();
            assertThat(afterCalled).isTrue();
        }

        @Test
        @DisplayName("around 接受 null before")
        void around_nullBefore_onlyAfterExecutes() {
            AtomicBoolean afterCalled = new AtomicBoolean(false);
            AsyncInterceptor interceptor = AsyncInterceptor.around(
                    null,
                    (ctx, result) -> afterCalled.set(true)
            );

            Async.supply(() -> "value")
                    .intercept(interceptor)
                    .awaitValue();

            assertThat(afterCalled).isTrue();
        }

        @Test
        @DisplayName("around 接受 null after")
        void around_nullAfter_onlyBeforeExecutes() {
            AtomicBoolean beforeCalled = new AtomicBoolean(false);
            AsyncInterceptor interceptor = AsyncInterceptor.around(
                    ctx -> beforeCalled.set(true),
                    null
            );

            Async.supply(() -> "value")
                    .intercept(interceptor)
                    .awaitValue();

            assertThat(beforeCalled).isTrue();
        }

        @Test
        @DisplayName("around 两个都为 null 不影响任务执行")
        void around_bothNull_taskStillExecutes() {
            AsyncInterceptor interceptor = AsyncInterceptor.around(null, null);

            String result = Async.supply(() -> "value")
                    .intercept(interceptor)
                    .awaitValue();

            assertThat(result).isEqualTo("value");
        }
    }

    // ==================== 默认 order ====================

    @Nested
    @DisplayName("order 排序")
    class OrderSorting {

        @Test
        @DisplayName("默认 order 值为 0")
        void defaultOrder_isZero() {
            AsyncInterceptor interceptor = AsyncInterceptor.before(ctx -> {});
            assertThat(interceptor.order()).isEqualTo(0);
        }

        @Test
        @DisplayName("order 值小的拦截器 before 先执行、after 后执行")
        void order_smallerOrderBeforeFirst_afterLast() {
            List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());

            AsyncInterceptor outer = new AsyncInterceptor() {
                @Override
                public <T> java.util.concurrent.CompletableFuture<Result<T, Throwable>> intercept(
                        AsyncContext context, AsyncInvocation<T> invocation) {
                    executionOrder.add("outer-before");
                    return invocation.proceed().whenComplete((r, e) -> executionOrder.add("outer-after"));
                }

                @Override
                public int order() {
                    return 0;
                }
            };

            AsyncInterceptor inner = new AsyncInterceptor() {
                @Override
                public <T> java.util.concurrent.CompletableFuture<Result<T, Throwable>> intercept(
                        AsyncContext context, AsyncInvocation<T> invocation) {
                    executionOrder.add("inner-before");
                    return invocation.proceed().whenComplete((r, e) -> executionOrder.add("inner-after"));
                }

                @Override
                public int order() {
                    return 10;
                }
            };

            Async.supply(() -> "value")
                    .intercept(inner, outer)  // 故意乱序添加
                    .awaitValue();

            // order=0 (outer) 的 before 先执行，after 后执行
            assertThat(executionOrder).containsExactly(
                    "outer-before", "inner-before", "inner-after", "outer-after");
        }

        @Test
        @DisplayName("三层拦截器按 order 升序排列执行")
        void threeInterceptors_executeInOrderSequence() {
            List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());

            AsyncInterceptor first = createOrderedInterceptor("first", 5, executionOrder);
            AsyncInterceptor second = createOrderedInterceptor("second", 10, executionOrder);
            AsyncInterceptor third = createOrderedInterceptor("third", 20, executionOrder);

            Async.supply(() -> "value")
                    .intercept(third, first, second)  // 故意乱序
                    .awaitValue();

            assertThat(executionOrder).containsExactly(
                    "first-before", "second-before", "third-before",
                    "third-after", "second-after", "first-after");
        }
    }

    // ==================== 多拦截器协同 ====================

    @Nested
    @DisplayName("多拦截器协同")
    class MultipleInterceptors {

        @Test
        @DisplayName("intercept varargs 添加多个拦截器")
        void intercept_varargs_addsMultiple() {
            List<String> order = Collections.synchronizedList(new ArrayList<>());
            AsyncInterceptor a = AsyncInterceptor.before(ctx -> order.add("a"));
            AsyncInterceptor b = AsyncInterceptor.before(ctx -> order.add("b"));

            Async.supply(() -> "value")
                    .intercept(a, b)
                    .awaitValue();

            assertThat(order).containsExactly("a", "b");
        }

        @Test
        @DisplayName("intercept List 添加拦截器列表")
        void intercept_list_addsAll() {
            List<String> order = Collections.synchronizedList(new ArrayList<>());
            AsyncInterceptor a = AsyncInterceptor.before(ctx -> order.add("a"));
            AsyncInterceptor b = AsyncInterceptor.before(ctx -> order.add("b"));

            Async.supply(() -> "value")
                    .intercept(List.of(a, b))
                    .awaitValue();

            assertThat(order).containsExactly("a", "b");
        }

        @Test
        @DisplayName("拦截器可以修改后续行为但不改变结果")
        void interceptor_doesNotAlterResult() {
            AtomicBoolean intercepted = new AtomicBoolean(false);
            AsyncInterceptor logging = AsyncInterceptor.around(
                    ctx -> intercepted.set(true),
                    (ctx, result) -> { /* no-op */ }
            );

            String result = Async.supply(() -> "original")
                    .intercept(logging)
                    .awaitValue();

            assertThat(intercepted).isTrue();
            assertThat(result).isEqualTo("original");
        }
    }

    // ==================== 辅助方法 ====================

    private static AsyncInterceptor createOrderedInterceptor(
            String name, int order, List<String> executionOrder) {
        return new AsyncInterceptor() {
            @Override
            public <T> java.util.concurrent.CompletableFuture<Result<T, Throwable>> intercept(
                    AsyncContext context, AsyncInvocation<T> invocation) {
                executionOrder.add(name + "-before");
                return invocation.proceed().whenComplete((r, e) -> executionOrder.add(name + "-after"));
            }

            @Override
            public int order() {
                return order;
            }
        };
    }
}
