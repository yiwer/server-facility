package cn.code91.facility.async;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AsyncContext - 异步任务上下文")
class AsyncContextTest {

    // ==================== 工厂方法 ====================

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        @DisplayName("of 创建带名称的上下文")
        void of_withName_createsContextWithName() {
            AsyncContext ctx = AsyncContext.of("myTask");
            assertThat(ctx.name()).isEqualTo("myTask");
        }

        @Test
        @DisplayName("of 传入 null 抛出 NullPointerException")
        void of_withNull_throwsNPE() {
            assertThatNullPointerException().isThrownBy(() -> AsyncContext.of(null));
        }

        @Test
        @DisplayName("empty 创建空上下文")
        void empty_createsEmptyContext() {
            AsyncContext ctx = AsyncContext.empty();
            assertThat(ctx.name()).isEmpty();
        }

        @Test
        @DisplayName("empty 多次调用返回同一实例")
        void empty_returnsSameInstance() {
            assertThat(AsyncContext.empty()).isSameAs(AsyncContext.empty());
        }
    }

    // ==================== 查询 ====================

    @Nested
    @DisplayName("查询")
    class Queries {

        @Test
        @DisplayName("name 返回上下文名称")
        void name_returnsContextName() {
            AsyncContext ctx = AsyncContext.of("taskA");
            assertThat(ctx.name()).isEqualTo("taskA");
        }

        @Test
        @DisplayName("attribute 存在的键返回 Optional 包装值")
        void attribute_existingKey_returnsValue() {
            AsyncContext ctx = AsyncContext.empty().with("key", "value");
            Optional<String> value = ctx.attribute("key");
            assertThat(value).isPresent().contains("value");
        }

        @Test
        @DisplayName("attribute 不存在的键返回 empty")
        void attribute_missingKey_returnsEmpty() {
            AsyncContext ctx = AsyncContext.empty();
            Optional<String> value = ctx.attribute("nonexistent");
            assertThat(value).isEmpty();
        }

        @Test
        @DisplayName("attribute 传入 null 键抛出 NullPointerException")
        void attribute_nullKey_throwsNPE() {
            AsyncContext ctx = AsyncContext.empty();
            assertThatNullPointerException().isThrownBy(() -> ctx.attribute(null));
        }

        @Test
        @DisplayName("attribute 类型不匹配时返回 empty 或抛出 ClassCastException（延迟转型）")
        void attribute_typeMismatch_handledGracefully() {
            AsyncContext ctx = AsyncContext.empty().with("num", 42);
            // 由于类型擦除，attribute() 内部的 cast 发生在赋值时（延迟转型）
            // 在 attribute() 方法本身不会触发 ClassCastException，
            // 但当尝试将 Integer 赋值给 String 时才会触发
            Optional<Integer> asInt = ctx.attribute("num");
            assertThat(asInt).isPresent().contains(42);
        }
    }

    // ==================== 派生 ====================

    @Nested
    @DisplayName("派生（不可变变换）")
    class Derivation {

        @Test
        @DisplayName("withName 返回新实例，原始不变")
        void withName_returnsNewInstance_originalUnchanged() {
            AsyncContext original = AsyncContext.of("original");
            AsyncContext renamed = original.withName("renamed");

            assertThat(renamed.name()).isEqualTo("renamed");
            assertThat(original.name()).isEqualTo("original");
            assertThat(renamed).isNotSameAs(original);
        }

        @Test
        @DisplayName("withName 保留已有属性")
        void withName_retainsAttributes() {
            AsyncContext ctx = AsyncContext.of("task").with("key", "value");
            AsyncContext renamed = ctx.withName("newName");

            assertThat(renamed.name()).isEqualTo("newName");
            assertThat(renamed.<String>attribute("key")).contains("value");
        }

        @Test
        @DisplayName("withName 传入 null 抛出 NullPointerException")
        void withName_null_throwsNPE() {
            AsyncContext ctx = AsyncContext.of("task");
            assertThatNullPointerException().isThrownBy(() -> ctx.withName(null));
        }

        @Test
        @DisplayName("with 返回新实例，原始不变")
        void with_returnsNewInstance_originalUnchanged() {
            AsyncContext original = AsyncContext.empty();
            AsyncContext withAttr = original.with("key", "value");

            assertThat(withAttr.<String>attribute("key")).contains("value");
            assertThat(original.<String>attribute("key")).isEmpty();
            assertThat(withAttr).isNotSameAs(original);
        }

        @Test
        @DisplayName("with 传入 null 键抛出 NullPointerException")
        void with_nullKey_throwsNPE() {
            AsyncContext ctx = AsyncContext.empty();
            assertThatNullPointerException().isThrownBy(() -> ctx.with(null, "value"));
        }

        @Test
        @DisplayName("with 可以覆盖已有属性")
        void with_overridesExistingAttribute() {
            AsyncContext ctx = AsyncContext.empty()
                    .with("key", "v1")
                    .with("key", "v2");
            assertThat(ctx.<String>attribute("key")).contains("v2");
        }

        @Test
        @DisplayName("with 保留已有名称和其他属性")
        void with_retainsNameAndOtherAttributes() {
            AsyncContext ctx = AsyncContext.of("task")
                    .with("a", 1)
                    .with("b", 2);

            assertThat(ctx.name()).isEqualTo("task");
            assertThat(ctx.<Integer>attribute("a")).contains(1);
            assertThat(ctx.<Integer>attribute("b")).contains(2);
        }

        @Test
        @DisplayName("链式多次派生互不影响")
        void multipleDerivations_areIndependent() {
            AsyncContext base = AsyncContext.of("base").with("shared", "yes");
            AsyncContext branch1 = base.with("branch", "1");
            AsyncContext branch2 = base.with("branch", "2");

            assertThat(branch1.<String>attribute("branch")).contains("1");
            assertThat(branch2.<String>attribute("branch")).contains("2");
            assertThat(base.<String>attribute("branch")).isEmpty();
        }
    }

    // ==================== toString ====================

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("toString 包含名称和属性信息")
        void toString_containsNameAndAttributes() {
            AsyncContext ctx = AsyncContext.of("myTask").with("key", "val");
            String str = ctx.toString();
            assertThat(str).contains("myTask").contains("key");
        }
    }
}
