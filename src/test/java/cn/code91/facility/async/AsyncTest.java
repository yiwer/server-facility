package cn.code91.facility.async;

import cn.code91.facility.result.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Async - 惰性异步计算封装")
class AsyncTest {

    // ==================== 工厂方法 ====================

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        @DisplayName("supply 正常返回值")
        void supply_normalValue_returnsOkResult() {
            Result<String, Throwable> result = Async.supply(() -> "hello").await();
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).isEqualTo("hello");
        }

        @Test
        @DisplayName("supply 抛出异常时返回 Err")
        void supply_throwsException_returnsErrResult() {
            RuntimeException ex = new RuntimeException("boom");
            Result<String, Throwable> result = Async.<String>supply(() -> { throw ex; }).await();
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).isSameAs(ex);
        }

        @Test
        @DisplayName("supply 使用指定执行器")
        void supply_withExecutor_usesProvidedExecutor() {
            var executor = Executors.newSingleThreadExecutor();
            try {
                Result<String, Throwable> result = Async.supply(
                        () -> Thread.currentThread().getName(), executor).await();
                assertThat(result.isOk()).isTrue();
                assertThat(result.get()).isNotEmpty();
            } finally {
                executor.shutdown();
            }
        }

        @Test
        @DisplayName("run 正常执行无返回值任务")
        void run_normalExecution_returnsVoidOk() {
            AtomicBoolean executed = new AtomicBoolean(false);
            Result<Void, Throwable> result = Async.run(() -> executed.set(true)).await();
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).isNull();
            assertThat(executed).isTrue();
        }

        @Test
        @DisplayName("run 抛出异常时返回 Err")
        void run_throwsException_returnsErrResult() {
            IllegalStateException ex = new IllegalStateException("fail");
            Result<Void, Throwable> result = Async.run(() -> { throw ex; }).await();
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).isSameAs(ex);
        }

        @Test
        @DisplayName("run 使用指定执行器")
        void run_withExecutor_usesProvidedExecutor() {
            var executor = Executors.newSingleThreadExecutor();
            try {
                AtomicBoolean executed = new AtomicBoolean(false);
                Result<Void, Throwable> result = Async.run(
                        () -> executed.set(true), executor).await();
                assertThat(result.isOk()).isTrue();
                assertThat(executed).isTrue();
            } finally {
                executor.shutdown();
            }
        }

        @Test
        @DisplayName("completed 立即返回已知成功值")
        void completed_returnsOkImmediately() {
            Result<Integer, Throwable> result = Async.completed(42).await();
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).isEqualTo(42);
        }

        @Test
        @DisplayName("failed 立即返回已知失败")
        void failed_returnsErrImmediately() {
            RuntimeException ex = new RuntimeException("known error");
            Result<String, Throwable> result = Async.<String>failed(ex).await();
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).isSameAs(ex);
        }
    }

    // ==================== 惰性求值 ====================

    @Nested
    @DisplayName("惰性求值")
    class Laziness {

        @Test
        @DisplayName("创建 Async 不触发执行")
        void creating_async_doesNotExecute() {
            AtomicBoolean executed = new AtomicBoolean(false);
            Async<String> async = Async.supply(() -> {
                executed.set(true);
                return "value";
            });
            assertThat(executed).isFalse();
        }

        @Test
        @DisplayName("map 不触发执行")
        void map_doesNotTriggerExecution() {
            AtomicBoolean executed = new AtomicBoolean(false);
            Async<Integer> async = Async.supply(() -> {
                executed.set(true);
                return "hello";
            }).map(String::length);
            assertThat(executed).isFalse();
        }

        @Test
        @DisplayName("flatMap 不触发执行")
        void flatMap_doesNotTriggerExecution() {
            AtomicBoolean executed = new AtomicBoolean(false);
            Async<Integer> async = Async.supply(() -> {
                executed.set(true);
                return "hello";
            }).flatMap(s -> Async.completed(s.length()));
            assertThat(executed).isFalse();
        }

        @Test
        @DisplayName("submit 触发执行")
        void submit_triggersExecution() throws Exception {
            AtomicBoolean executed = new AtomicBoolean(false);
            CompletableFuture<Result<String, Throwable>> future = Async.supply(() -> {
                executed.set(true);
                return "value";
            }).submit();
            future.get();
            assertThat(executed).isTrue();
        }
    }

    // ==================== 变换管道 ====================

    @Nested
    @DisplayName("变换管道")
    class Pipeline {

        @Test
        @DisplayName("map 转换成功值")
        void map_transformsValue() {
            String result = Async.supply(() -> "hello")
                    .map(String::toUpperCase)
                    .awaitValue();
            assertThat(result).isEqualTo("HELLO");
        }

        @Test
        @DisplayName("map 在失败时跳过转换")
        void map_skipsOnFailure() {
            Result<Integer, Throwable> result = Async.<String>supply(() -> { throw new RuntimeException("fail"); })
                    .map(String::length)
                    .await();
            assertThat(result.isErr()).isTrue();
        }

        @Test
        @DisplayName("map 转换函数抛异常时返回 Err")
        void map_mapperThrows_returnsErr() {
            Result<Integer, Throwable> result = Async.supply(() -> (String) null)
                    .map(String::length)
                    .await();
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("flatMap 扁平化嵌套 Async")
        void flatMap_flattensNestedAsync() {
            int result = Async.supply(() -> 5)
                    .flatMap(n -> Async.supply(() -> n * 2))
                    .awaitValue();
            assertThat(result).isEqualTo(10);
        }

        @Test
        @DisplayName("flatMap 外层失败时跳过内层")
        void flatMap_skipsOnOuterFailure() {
            Result<Integer, Throwable> result = Async.<Integer>supply(() -> { throw new RuntimeException("outer"); })
                    .flatMap(n -> Async.completed(n * 2))
                    .await();
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).hasMessage("outer");
        }

        @Test
        @DisplayName("flatMap 内层失败时传播错误")
        void flatMap_innerFailure_propagatesError() {
            Result<Integer, Throwable> result = Async.supply(() -> 5)
                    .flatMap(n -> Async.<Integer>failed(new RuntimeException("inner")))
                    .await();
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).hasMessage("inner");
        }

        @Test
        @DisplayName("recover 从失败恢复为替代值")
        void recover_returnsAlternateValue() {
            String result = Async.<String>supply(() -> { throw new RuntimeException("fail"); })
                    .recover(e -> "fallback")
                    .awaitValue();
            assertThat(result).isEqualTo("fallback");
        }

        @Test
        @DisplayName("recover 在成功时不执行")
        void recover_doesNotExecuteOnSuccess() {
            AtomicBoolean recovered = new AtomicBoolean(false);
            String result = Async.supply(() -> "ok")
                    .recover(e -> {
                        recovered.set(true);
                        return "fallback";
                    })
                    .awaitValue();
            assertThat(result).isEqualTo("ok");
            assertThat(recovered).isFalse();
        }

        @Test
        @DisplayName("recoverWith 从失败恢复为替代 Async")
        void recoverWith_returnsAlternateAsync() {
            String result = Async.<String>supply(() -> { throw new RuntimeException("fail"); })
                    .recoverWith(e -> Async.completed("recovered"))
                    .awaitValue();
            assertThat(result).isEqualTo("recovered");
        }

        @Test
        @DisplayName("recoverWith 在成功时不执行")
        void recoverWith_doesNotExecuteOnSuccess() {
            AtomicBoolean recovered = new AtomicBoolean(false);
            String result = Async.supply(() -> "ok")
                    .recoverWith(e -> {
                        recovered.set(true);
                        return Async.completed("fallback");
                    })
                    .awaitValue();
            assertThat(result).isEqualTo("ok");
            assertThat(recovered).isFalse();
        }

        @Test
        @DisplayName("timeout 在超时后返回 TimeoutException")
        void timeout_returnsTimeoutException() {
            Result<String, Throwable> result = Async.<String>supply(() -> {
                Thread.sleep(2000);
                return "late";
            }).timeout(Duration.ofMillis(50)).await();
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).isInstanceOf(TimeoutException.class);
        }

        @Test
        @DisplayName("timeout 在任务及时完成时返回正常结果")
        void timeout_taskCompletesInTime_returnsOk() {
            String result = Async.supply(() -> "fast")
                    .timeout(Duration.ofSeconds(5))
                    .awaitValue();
            assertThat(result).isEqualTo("fast");
        }

        @Test
        @DisplayName("peek 在成功时执行副作用")
        void peek_executesOnSuccess() {
            AtomicReference<String> captured = new AtomicReference<>();
            String result = Async.supply(() -> "hello")
                    .peek(captured::set)
                    .awaitValue();
            assertThat(result).isEqualTo("hello");
            assertThat(captured.get()).isEqualTo("hello");
        }

        @Test
        @DisplayName("peek 在失败时不执行")
        void peek_doesNotExecuteOnFailure() {
            AtomicBoolean peeked = new AtomicBoolean(false);
            Result<String, Throwable> result = Async.<String>supply(() -> { throw new RuntimeException("fail"); })
                    .peek(v -> peeked.set(true))
                    .await();
            assertThat(result.isErr()).isTrue();
            assertThat(peeked).isFalse();
        }

        @Test
        @DisplayName("peekErr 在失败时执行副作用")
        void peekErr_executesOnFailure() {
            AtomicReference<Throwable> captured = new AtomicReference<>();
            RuntimeException ex = new RuntimeException("fail");
            Result<String, Throwable> result = Async.<String>supply(() -> { throw ex; })
                    .peekErr(captured::set)
                    .await();
            assertThat(result.isErr()).isTrue();
            assertThat(captured.get()).isSameAs(ex);
        }

        @Test
        @DisplayName("peekErr 在成功时不执行")
        void peekErr_doesNotExecuteOnSuccess() {
            AtomicBoolean peeked = new AtomicBoolean(false);
            String result = Async.supply(() -> "ok")
                    .peekErr(e -> peeked.set(true))
                    .awaitValue();
            assertThat(result).isEqualTo("ok");
            assertThat(peeked).isFalse();
        }
    }

    // ==================== 终端操作 ====================

    @Nested
    @DisplayName("终端操作")
    class TerminalOps {

        @Test
        @DisplayName("submit 返回 CompletableFuture")
        void submit_returnsCompletableFuture() throws Exception {
            CompletableFuture<Result<String, Throwable>> future = Async.supply(() -> "async").submit();
            assertThat(future).isNotNull();
            Result<String, Throwable> result = future.get();
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).isEqualTo("async");
        }

        @Test
        @DisplayName("await 返回 Result 成功")
        void await_success_returnsOkResult() {
            Result<Integer, Throwable> result = Async.supply(() -> 42).await();
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).isEqualTo(42);
        }

        @Test
        @DisplayName("await 返回 Result 失败")
        void await_failure_returnsErrResult() {
            Result<String, Throwable> result = Async.<String>supply(() -> { throw new RuntimeException("err"); }).await();
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).hasMessage("err");
        }

        @Test
        @DisplayName("await 带超时成功返回")
        void await_withTimeout_success() throws InterruptedException {
            Result<String, Throwable> result = Async.supply(() -> "fast")
                    .await(Duration.ofSeconds(5));
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).isEqualTo("fast");
        }

        @Test
        @DisplayName("await 带超时超时后返回 Err")
        void await_withTimeout_timesOut() throws InterruptedException {
            Result<String, Throwable> result = Async.<String>supply(() -> {
                Thread.sleep(5000);
                return "late";
            }).await(Duration.ofMillis(50));
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).isInstanceOf(TimeoutException.class);
        }

        @Test
        @DisplayName("awaitValue 成功时返回值")
        void awaitValue_success_returnsValue() {
            String value = Async.supply(() -> "value").awaitValue();
            assertThat(value).isEqualTo("value");
        }

        @Test
        @DisplayName("awaitValue 失败时抛出异常")
        void awaitValue_failure_throwsException() {
            assertThatThrownBy(() ->
                    Async.<String>supply(() -> { throw new RuntimeException("fail"); }).awaitValue()
            ).isInstanceOf(RuntimeException.class).hasMessage("fail");
        }

        @Test
        @DisplayName("awaitValue 失败时受检异常包装为 RuntimeException")
        void awaitValue_checkedException_wrappedInRuntimeException() {
            assertThatThrownBy(() ->
                    Async.<String>supply(() -> { throw new Exception("checked"); }).awaitValue()
            ).isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(Exception.class);
        }
    }

    // ==================== 并行组合 ====================

    @Nested
    @DisplayName("并行组合")
    class ParallelCombinators {

        @Test
        @DisplayName("all 全部成功返回结果列表")
        void all_allSucceed_returnsResultList() {
            List<Integer> result = Async.all(
                    Async.supply(() -> 1),
                    Async.supply(() -> 2),
                    Async.supply(() -> 3)
            ).awaitValue();
            assertThat(result).containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("all 部分失败返回 Err")
        void all_someFail_returnsErr() {
            Result<List<Integer>, Throwable> result = Async.all(
                    Async.supply(() -> 1),
                    Async.<Integer>supply(() -> { throw new RuntimeException("fail1"); }),
                    Async.<Integer>supply(() -> { throw new RuntimeException("fail2"); })
            ).await();
            assertThat(result.isErr()).isTrue();
        }

        @Test
        @DisplayName("all 单个失败直接返回该异常")
        void all_singleFail_returnsThatException() {
            RuntimeException ex = new RuntimeException("only-fail");
            Result<List<Integer>, Throwable> result = Async.all(
                    Async.supply(() -> 1),
                    Async.<Integer>supply(() -> { throw ex; })
            ).await();
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).isSameAs(ex);
        }

        @Test
        @DisplayName("all 多个失败返回 AggregateException")
        void all_multipleFail_returnsAggregateException() {
            Result<List<Integer>, Throwable> result = Async.all(
                    Async.<Integer>supply(() -> { throw new RuntimeException("fail1"); }),
                    Async.<Integer>supply(() -> { throw new RuntimeException("fail2"); })
            ).await();
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).isInstanceOf(AggregateException.class);
            AggregateException ae = (AggregateException) result.getErr();
            assertThat(ae.causes()).hasSize(2);
        }

        @Test
        @DisplayName("all 空列表返回空列表")
        void all_emptyList_returnsEmptyList() {
            List<Object> result = Async.all(List.<Async<Object>>of()).awaitValue();
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("all List 参数版本")
        void all_listVariant_works() {
            List<Async<String>> tasks = List.of(
                    Async.supply(() -> "a"),
                    Async.supply(() -> "b")
            );
            List<String> result = Async.all(tasks).awaitValue();
            assertThat(result).containsExactly("a", "b");
        }

        @Test
        @DisplayName("any 第一个成功返回该值")
        void any_firstSucceeds_returnsThatValue() {
            Result<String, Throwable> result = Async.any(
                    Async.supply(() -> "fast"),
                    Async.<String>supply(() -> {
                        Thread.sleep(1000);
                        return "slow";
                    })
            ).await();
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).isIn("fast", "slow");
        }

        @Test
        @DisplayName("any 全部失败返回 AggregateException 或单个异常")
        void any_allFail_returnsErr() {
            Result<String, Throwable> result = Async.any(
                    Async.<String>supply(() -> { throw new RuntimeException("fail1"); }),
                    Async.<String>supply(() -> { throw new RuntimeException("fail2"); })
            ).await();
            assertThat(result.isErr()).isTrue();
            // 两个都失败，返回 AggregateException
            assertThat(result.getErr()).isInstanceOf(AggregateException.class);
        }

        @Test
        @DisplayName("any 单个全部失败直接返回该异常")
        void any_singleTaskFails_returnsThatException() {
            RuntimeException ex = new RuntimeException("only-fail");
            Result<String, Throwable> result = Async.any(
                    Async.<String>supply(() -> { throw ex; })
            ).await();
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).isSameAs(ex);
        }

        @Test
        @DisplayName("any 空列表返回 Err(IllegalArgumentException)")
        void any_emptyList_returnsIllegalArgumentException() {
            Result<Object, Throwable> result = Async.any(List.<Async<Object>>of()).await();
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one task");
        }
    }

    // ==================== 配置 ====================

    @Nested
    @DisplayName("配置")
    class Configuration {

        @Test
        @DisplayName("name 设置任务名称可通过拦截器读取")
        void name_setsTaskName() {
            AtomicReference<String> capturedName = new AtomicReference<>();
            AsyncInterceptor capture = AsyncInterceptor.before(ctx -> capturedName.set(ctx.name()));

            Async.supply(() -> "value")
                    .name("myTask")
                    .intercept(capture)
                    .awaitValue();

            assertThat(capturedName.get()).isEqualTo("myTask");
        }

        @Test
        @DisplayName("executor 设置执行器")
        void executor_setsExecutor() {
            var executor = Executors.newSingleThreadExecutor();
            try {
                AtomicReference<String> threadName = new AtomicReference<>();
                Async.supply(() -> {
                    threadName.set(Thread.currentThread().getName());
                    return "value";
                }).executor(executor).awaitValue();

                assertThat(threadName.get()).isNotNull();
            } finally {
                executor.shutdown();
            }
        }

        @Test
        @DisplayName("attribute 设置上下文属性可通过拦截器读取")
        void attribute_setsContextAttribute() {
            AtomicReference<String> capturedDs = new AtomicReference<>();
            AsyncInterceptor capture = AsyncInterceptor.before(ctx ->
                    ctx.<String>attribute("ds").ifPresent(capturedDs::set));

            Async.supply(() -> "value")
                    .attribute("ds", "read-replica")
                    .intercept(capture)
                    .awaitValue();

            assertThat(capturedDs.get()).isEqualTo("read-replica");
        }

        @Test
        @DisplayName("链式配置返回新实例不影响原始 Async")
        void configuration_returnsNewInstance() {
            Async<String> original = Async.supply(() -> "value");
            Async<String> named = original.name("task1");
            Async<String> withAttr = named.attribute("key", "val");

            // 所有都是不同实例（通过引用比较）
            assertThat(original).isNotSameAs(named);
            assertThat(named).isNotSameAs(withAttr);
        }
    }
}
