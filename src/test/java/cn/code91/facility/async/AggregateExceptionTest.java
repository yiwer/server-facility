package cn.code91.facility.async;

import cn.code91.facility.result.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AggregateException - 聚合异常")
class AggregateExceptionTest {

    // ==================== 通过 Async.all 触发 ====================

    @Nested
    @DisplayName("通过 Async.all 触发")
    class ViaAsyncAll {

        @Test
        @DisplayName("多个任务失败时产生 AggregateException")
        void all_multipleFailures_producesAggregateException() {
            Result<List<Integer>, Throwable> result = Async.all(
                    Async.<Integer>supply(() -> { throw new RuntimeException("err1"); }),
                    Async.<Integer>supply(() -> { throw new IllegalStateException("err2"); })
            ).await();

            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).isInstanceOf(AggregateException.class);

            AggregateException ae = (AggregateException) result.getErr();
            assertThat(ae.causes()).hasSize(2);
        }

        @Test
        @DisplayName("causes 返回不可修改列表")
        void causes_returnsUnmodifiableList() {
            Result<List<Integer>, Throwable> result = Async.all(
                    Async.<Integer>supply(() -> { throw new RuntimeException("err1"); }),
                    Async.<Integer>supply(() -> { throw new RuntimeException("err2"); })
            ).await();

            AggregateException ae = (AggregateException) result.getErr();
            assertThatThrownBy(() -> ae.causes().add(new RuntimeException("injected")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("getMessage 格式为 N task(s) failed: [...]")
        void getMessage_hasExpectedFormat() {
            Result<List<Integer>, Throwable> result = Async.all(
                    Async.<Integer>supply(() -> { throw new RuntimeException("reason1"); }),
                    Async.<Integer>supply(() -> { throw new IllegalArgumentException("reason2"); })
            ).await();

            AggregateException ae = (AggregateException) result.getErr();
            String message = ae.getMessage();
            assertThat(message).startsWith("2 task(s) failed: [");
            assertThat(message).contains("RuntimeException: reason1");
            assertThat(message).contains("IllegalArgumentException: reason2");
            assertThat(message).endsWith("]");
        }

        @Test
        @DisplayName("单个任务失败时不产生 AggregateException")
        void all_singleFailure_doesNotProduceAggregateException() {
            Result<List<Integer>, Throwable> result = Async.all(
                    Async.supply(() -> 1),
                    Async.<Integer>supply(() -> { throw new RuntimeException("only"); })
            ).await();

            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).isNotInstanceOf(AggregateException.class);
            assertThat(result.getErr()).isInstanceOf(RuntimeException.class).hasMessage("only");
        }
    }

    // ==================== 通过 Async.any 触发 ====================

    @Nested
    @DisplayName("通过 Async.any 触发")
    class ViaAsyncAny {

        @Test
        @DisplayName("所有任务失败时产生 AggregateException")
        void any_allFailures_producesAggregateException() {
            Result<String, Throwable> result = Async.any(
                    Async.<String>supply(() -> { throw new RuntimeException("err1"); }),
                    Async.<String>supply(() -> { throw new IllegalStateException("err2"); })
            ).await();

            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).isInstanceOf(AggregateException.class);

            AggregateException ae = (AggregateException) result.getErr();
            assertThat(ae.causes()).hasSize(2);
        }

        @Test
        @DisplayName("any 单个失败不产生 AggregateException")
        void any_singleFailure_returnsDirectException() {
            RuntimeException ex = new RuntimeException("only");
            Result<String, Throwable> result = Async.any(
                    Async.<String>supply(() -> { throw ex; })
            ).await();

            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).isSameAs(ex);
            assertThat(result.getErr()).isNotInstanceOf(AggregateException.class);
        }
    }

    // ==================== causes 内容验证 ====================

    @Nested
    @DisplayName("causes 内容验证")
    class CausesContent {

        @Test
        @DisplayName("causes 包含所有失败任务的异常")
        void causes_containsAllFailureExceptions() {
            RuntimeException ex1 = new RuntimeException("first");
            IllegalStateException ex2 = new IllegalStateException("second");
            IllegalArgumentException ex3 = new IllegalArgumentException("third");

            Result<List<String>, Throwable> result = Async.all(
                    Async.<String>supply(() -> { throw ex1; }),
                    Async.<String>supply(() -> { throw ex2; }),
                    Async.<String>supply(() -> { throw ex3; })
            ).await();

            assertThat(result.isErr()).isTrue();
            AggregateException ae = (AggregateException) result.getErr();
            assertThat(ae.causes()).hasSize(3);
            assertThat(ae.causes()).containsExactlyInAnyOrder(ex1, ex2, ex3);
        }

        @Test
        @DisplayName("getMessage 包含每个异常的类名和消息")
        void getMessage_containsEachExceptionInfo() {
            Result<List<String>, Throwable> result = Async.all(
                    Async.<String>supply(() -> { throw new NullPointerException("null ref"); }),
                    Async.<String>supply(() -> { throw new ArithmeticException("div by zero"); })
            ).await();

            AggregateException ae = (AggregateException) result.getErr();
            assertThat(ae.getMessage())
                    .contains("NullPointerException: null ref")
                    .contains("ArithmeticException: div by zero")
                    .startsWith("2 task(s) failed:");
        }
    }
}
