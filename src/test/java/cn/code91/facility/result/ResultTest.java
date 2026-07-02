package cn.code91.facility.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Result - 函数式结果类型")
class ResultTest {

    // ==================== 工厂方法 ====================

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        void ok_withValue_returnsOkWithValue() {
            Result<String, String> result = Result.ok("hello");
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).isEqualTo("hello");
        }

        @Test
        void ok_withNullValue_returnsOkWithNull() {
            Result<String, String> result = Result.ok(null);
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).isNull();
        }

        @Test
        void ok_void_returnsOkWithNullValue() {
            Result<Void, String> result = Result.ok();
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).isNull();
        }

        @Test
        void err_withError_returnsErr() {
            Result<String, String> result = Result.err("error");
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).isEqualTo("error");
        }

        @Test
        void err_withNull_throwsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Result.err(null));
        }

        @Test
        void of_normalExecution_returnsOk() {
            Result<String, Exception> result = Result.of(() -> "value");
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).isEqualTo("value");
        }

        @Test
        void of_throwsException_returnsErr() {
            RuntimeException ex = new RuntimeException("boom");
            Result<String, Exception> result = Result.of(() -> { throw ex; });
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).isSameAs(ex);
        }

        @Test
        void of_throwsInterruptedException_restoresInterruptAndReturnsErr() {
            Result<String, Exception> result = Result.of(() -> {
                throw new InterruptedException("interrupted");
            });
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).isInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            // Clear interrupt status for other tests
            Thread.interrupted();
        }

        @Test
        void of_nullSupplier_throwsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Result.of(null));
        }

        @Test
        void ofRunnable_normalExecution_returnsOk() {
            AtomicBoolean executed = new AtomicBoolean(false);
            Result<Void, Exception> result = Result.ofRunnable(() -> executed.set(true));
            assertThat(result.isOk()).isTrue();
            assertThat(executed).isTrue();
        }

        @Test
        void ofRunnable_throwsException_returnsErr() {
            Result<Void, Exception> result = Result.ofRunnable(() -> {
                throw new IllegalStateException("fail");
            });
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void fromOptional_present_returnsOk() {
            Result<String, String> result = Result.fromOptional(Optional.of("val"), () -> "err");
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).isEqualTo("val");
        }

        @Test
        void fromOptional_empty_returnsErr() {
            Result<String, String> result = Result.fromOptional(Optional.empty(), () -> "err");
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).isEqualTo("err");
        }

        @Test
        void fromNullable_nonNull_returnsOk() {
            Result<String, String> result = Result.fromNullable("val", () -> "err");
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).isEqualTo("val");
        }

        @Test
        void fromNullable_null_returnsErr() {
            Result<String, String> result = Result.fromNullable(null, () -> "err");
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).isEqualTo("err");
        }
    }

    // ==================== 状态查询 ====================

    @Nested
    @DisplayName("状态查询")
    class StateQuery {

        @Test
        void isOk_onOk_returnsTrue() {
            assertThat(Result.ok("v").isOk()).isTrue();
        }

        @Test
        void isOk_onErr_returnsFalse() {
            assertThat(Result.err("e").isOk()).isFalse();
        }

        @Test
        void isErr_onErr_returnsTrue() {
            assertThat(Result.err("e").isErr()).isTrue();
        }

        @Test
        void isErr_onOk_returnsFalse() {
            assertThat(Result.ok("v").isErr()).isFalse();
        }

        @Test
        void isOkAnd_okAndMatch_returnsTrue() {
            assertThat(Result.ok("hello").isOkAnd(s -> s.length() == 5)).isTrue();
        }

        @Test
        void isOkAnd_okAndNoMatch_returnsFalse() {
            assertThat(Result.ok("hi").isOkAnd(s -> s.length() == 5)).isFalse();
        }

        @Test
        void isOkAnd_onErr_returnsFalse() {
            assertThat(Result.<String, String>err("e").isOkAnd(s -> true)).isFalse();
        }

        @Test
        void isErrAnd_errAndMatch_returnsTrue() {
            assertThat(Result.<String, String>err("error").isErrAnd(e -> e.startsWith("err"))).isTrue();
        }

        @Test
        void isErrAnd_errAndNoMatch_returnsFalse() {
            assertThat(Result.<String, String>err("x").isErrAnd(e -> e.startsWith("err"))).isFalse();
        }

        @Test
        void isErrAnd_onOk_returnsFalse() {
            assertThat(Result.<String, String>ok("v").isErrAnd(e -> true)).isFalse();
        }
    }

    // ==================== 取值方法 ====================

    @Nested
    @DisplayName("取值方法")
    class ValueExtraction {

        @Test
        void get_onOk_returnsValue() {
            assertThat(Result.ok("v").get()).isEqualTo("v");
        }

        @Test
        void get_onErr_throwsNoSuchElement() {
            assertThatExceptionOfType(NoSuchElementException.class)
                    .isThrownBy(() -> Result.err("e").get());
        }

        @Test
        void get_onErrWithThrowable_includesCauseInException() {
            RuntimeException cause = new RuntimeException("root");
            Result<String, RuntimeException> result = Result.err(cause);
            assertThatExceptionOfType(NoSuchElementException.class)
                    .isThrownBy(result::get)
                    .withCause(cause);
        }

        @Test
        void getErr_onErr_returnsError() {
            assertThat(Result.err("e").getErr()).isEqualTo("e");
        }

        @Test
        void getErr_onOk_throwsNoSuchElement() {
            assertThatExceptionOfType(NoSuchElementException.class)
                    .isThrownBy(() -> Result.ok("v").getErr());
        }

        @Test
        void orElse_onOk_returnsValue() {
            assertThat(Result.ok("v").orElse("default")).isEqualTo("v");
        }

        @Test
        void orElse_onErr_returnsDefault() {
            assertThat(Result.<String, String>err("e").orElse("default")).isEqualTo("default");
        }

        @Test
        void orElseGet_onOk_doesNotCallSupplier() {
            AtomicBoolean called = new AtomicBoolean(false);
            Result.ok("v").orElseGet(() -> { called.set(true); return "x"; });
            assertThat(called).isFalse();
        }

        @Test
        void orElseGet_onErr_callsSupplier() {
            assertThat(Result.<String, String>err("e").orElseGet(() -> "computed")).isEqualTo("computed");
        }

        @Test
        void orElseMap_onOk_doesNotCallFunction() {
            assertThat(Result.<String, String>ok("v").orElseMap(e -> "mapped")).isEqualTo("v");
        }

        @Test
        void orElseMap_onErr_mapsFromError() {
            assertThat(Result.<String, String>err("err").orElseMap(e -> "mapped-" + e)).isEqualTo("mapped-err");
        }

        @Test
        void orElseThrow_supplier_onOk_returnsValue() throws Exception {
            java.util.function.Supplier<RuntimeException> supplier = () -> new RuntimeException("fail");
            assertThat(Result.ok("v").orElseThrow(supplier)).isEqualTo("v");
        }

        @Test
        void orElseThrow_supplier_onErr_throwsException() {
            java.util.function.Supplier<IllegalStateException> supplier = () -> new IllegalStateException("fail");
            assertThatThrownBy(() -> Result.err("e").orElseThrow(supplier))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("fail");
        }

        @Test
        void orElseThrow_function_onOk_returnsValue() throws Exception {
            java.util.function.Function<String, RuntimeException> mapper = RuntimeException::new;
            assertThat(Result.<String, String>ok("v").orElseThrow(mapper)).isEqualTo("v");
        }

        @Test
        void orElseThrow_function_onErr_throwsMappedException() {
            java.util.function.Function<String, IllegalArgumentException> mapper = IllegalArgumentException::new;
            assertThatThrownBy(() -> Result.<String, String>err("boom").orElseThrow(mapper))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("boom");
        }

        @Test
        void toOptional_onOk_returnsPresent() {
            assertThat(Result.ok("v").toOptional()).contains("v");
        }

        @Test
        void toOptional_onOkWithNull_returnsEmpty() {
            assertThat(Result.ok(null).toOptional()).isEmpty();
        }

        @Test
        void toOptional_onErr_returnsEmpty() {
            assertThat(Result.err("e").toOptional()).isEmpty();
        }

        @Test
        void toOptionalErr_onErr_returnsPresent() {
            assertThat(Result.err("e").toOptionalErr()).contains("e");
        }

        @Test
        void toOptionalErr_onOk_returnsEmpty() {
            assertThat(Result.ok("v").toOptionalErr()).isEmpty();
        }

        @Test
        void stream_onOk_returnsSingleElementStream() {
            assertThat(Result.ok("v").stream().toList()).containsExactly("v");
        }

        @Test
        void stream_onOkWithNull_returnsEmptyStream() {
            assertThat(Result.ok(null).stream().toList()).isEmpty();
        }

        @Test
        void stream_onErr_returnsEmptyStream() {
            assertThat(Result.err("e").stream().toList()).isEmpty();
        }
    }

    // ==================== 变换操作 ====================

    @Nested
    @DisplayName("变换操作")
    class Transformations {

        @Test
        void map_onOk_transformsValue() {
            assertThat(Result.<String, String>ok("hello").map(String::toUpperCase).get()).isEqualTo("HELLO");
        }

        @Test
        void map_onErr_returnsSelf() {
            Result<String, String> err = Result.err("e");
            Result<Integer, String> mapped = err.map(String::length);
            assertThat(mapped.isErr()).isTrue();
            assertThat(mapped.getErr()).isEqualTo("e");
        }

        @Test
        void mapErr_onErr_transformsError() {
            assertThat(Result.<String, String>err("err").mapErr(String::length).getErr()).isEqualTo(3);
        }

        @Test
        void mapErr_onOk_returnsSelf() {
            Result<String, String> ok = Result.ok("v");
            Result<String, Integer> mapped = ok.mapErr(String::length);
            assertThat(mapped.isOk()).isTrue();
            assertThat(mapped.get()).isEqualTo("v");
        }

        @Test
        void bimap_onOk_appliesOkMapper() {
            Result<Integer, Integer> result = Result.<String, String>ok("hi").bimap(String::length, String::length);
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).isEqualTo(2);
        }

        @Test
        void bimap_onErr_appliesErrMapper() {
            Result<Integer, Integer> result = Result.<String, String>err("err").bimap(String::length, String::length);
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).isEqualTo(3);
        }

        @Test
        void flatMap_onOk_chainsResult() {
            Result<Integer, String> result = Result.<String, String>ok("42")
                    .flatMap(s -> Result.ok(Integer.parseInt(s)));
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).isEqualTo(42);
        }

        @Test
        void flatMap_onOk_canReturnErr() {
            Result<Integer, String> result = Result.<String, String>ok("bad")
                    .flatMap(s -> Result.err("parse failed"));
            assertThat(result.isErr()).isTrue();
        }

        @Test
        void flatMap_onErr_doesNotExecuteMapper() {
            AtomicBoolean called = new AtomicBoolean(false);
            Result.<String, String>err("e").flatMap(s -> {
                called.set(true);
                return Result.ok(s);
            });
            assertThat(called).isFalse();
        }

        @Test
        void ensure_okAndPass_returnsSelf() {
            Result<String, String> ok = Result.ok("hello");
            assertThat(ok.ensure(s -> s.length() > 0, () -> "empty")).isSameAs(ok);
        }

        @Test
        void ensure_okAndFail_returnsErr() {
            Result<String, String> result = Result.<String, String>ok("").ensure(s -> s.length() > 0, () -> "empty");
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).isEqualTo("empty");
        }

        @Test
        void ensure_onErr_returnsSelf() {
            Result<String, String> err = Result.err("e");
            assertThat(err.ensure(s -> true, () -> "x")).isSameAs(err);
        }

        @Test
        void recover_onErr_recoversToOk() {
            Result<String, String> result = Result.<String, String>err("e").recover(e -> "recovered");
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).isEqualTo("recovered");
        }

        @Test
        void recover_onOk_returnsSelf() {
            Result<String, String> ok = Result.ok("v");
            assertThat(ok.recover(e -> "x")).isSameAs(ok);
        }

        @Test
        void recoverWith_onErr_recoversToResult() {
            Result<String, String> result = Result.<String, String>err("e").recoverWith(e -> Result.ok("recovered"));
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).isEqualTo("recovered");
        }

        @Test
        void recoverWith_onOk_returnsSelf() {
            Result<String, String> ok = Result.ok("v");
            assertThat(ok.recoverWith(e -> Result.ok("x"))).isSameAs(ok);
        }

        @Test
        void swap_onOk_returnsErr() {
            Result<String, Integer> swapped = Result.<Integer, String>ok(42).swap();
            assertThat(swapped.isErr()).isTrue();
            assertThat(swapped.getErr()).isEqualTo(42);
        }

        @Test
        void swap_onErr_returnsOk() {
            Result<String, Integer> swapped = Result.<Integer, String>err("e").swap();
            assertThat(swapped.isOk()).isTrue();
            assertThat(swapped.get()).isEqualTo("e");
        }
    }

    // ==================== 组合操作 ====================

    @Nested
    @DisplayName("组合操作")
    class Composition {

        @Test
        void and_onOk_returnsOther() {
            Result<Integer, String> result = Result.<String, String>ok("v").and(Result.ok(42));
            assertThat(result.get()).isEqualTo(42);
        }

        @Test
        void and_onErr_returnsOriginalErr() {
            Result<Integer, String> result = Result.<String, String>err("e1").and(Result.ok(42));
            assertThat(result.getErr()).isEqualTo("e1");
        }

        @Test
        void andThen_onOk_executesSupplier() {
            Result<Integer, String> result = Result.<String, String>ok("v").andThen(() -> Result.ok(99));
            assertThat(result.get()).isEqualTo(99);
        }

        @Test
        void andThen_onErr_doesNotExecuteSupplier() {
            AtomicBoolean called = new AtomicBoolean(false);
            Result.<String, String>err("e").andThen(() -> {
                called.set(true);
                return Result.ok(1);
            });
            assertThat(called).isFalse();
        }

        @Test
        void or_onOk_returnsSelf() {
            Result<String, String> ok = Result.ok("v");
            assertThat(ok.or(Result.ok("other"))).isSameAs(ok);
        }

        @Test
        void or_onErr_returnsOther() {
            Result<String, String> result = Result.<String, String>err("e").or(Result.ok("fallback"));
            assertThat(result.get()).isEqualTo("fallback");
        }

        @Test
        void orElseSupplier_onOk_returnsSelf() {
            Result<String, String> ok = Result.ok("v");
            assertThat(ok.orElseSupplier(() -> Result.ok("other"))).isSameAs(ok);
        }

        @Test
        void orElseSupplier_onErr_executesSupplier() {
            Result<String, String> result = Result.<String, String>err("e")
                    .orElseSupplier(() -> Result.ok("computed"));
            assertThat(result.get()).isEqualTo("computed");
        }
    }

    // ==================== 副作用 ====================

    @Nested
    @DisplayName("副作用操作")
    class SideEffects {

        @Test
        void peek_onOk_executesAction() {
            AtomicReference<String> ref = new AtomicReference<>();
            Result<String, String> ok = Result.ok("v");
            Result<String, String> result = ok.peek(ref::set);
            assertThat(ref.get()).isEqualTo("v");
            assertThat(result).isSameAs(ok);
        }

        @Test
        void peek_onErr_doesNotExecuteAction() {
            AtomicBoolean called = new AtomicBoolean(false);
            Result.err("e").peek(v -> called.set(true));
            assertThat(called).isFalse();
        }

        @Test
        void peekErr_onErr_executesAction() {
            AtomicReference<String> ref = new AtomicReference<>();
            Result<String, String> err = Result.err("e");
            Result<String, String> result = err.peekErr(ref::set);
            assertThat(ref.get()).isEqualTo("e");
            assertThat(result).isSameAs(err);
        }

        @Test
        void peekErr_onOk_doesNotExecuteAction() {
            AtomicBoolean called = new AtomicBoolean(false);
            Result.ok("v").peekErr(e -> called.set(true));
            assertThat(called).isFalse();
        }

        @Test
        void ifOk_onOk_executesAction() {
            AtomicReference<String> ref = new AtomicReference<>();
            Result.ok("v").ifOk(ref::set);
            assertThat(ref.get()).isEqualTo("v");
        }

        @Test
        void ifOk_onErr_doesNothing() {
            AtomicBoolean called = new AtomicBoolean(false);
            Result.err("e").ifOk(v -> called.set(true));
            assertThat(called).isFalse();
        }

        @Test
        void ifErr_onErr_executesAction() {
            AtomicReference<String> ref = new AtomicReference<>();
            Result.<String, String>err("e").ifErr(ref::set);
            assertThat(ref.get()).isEqualTo("e");
        }

        @Test
        void ifErr_onOk_doesNothing() {
            AtomicBoolean called = new AtomicBoolean(false);
            Result.ok("v").ifErr(e -> called.set(true));
            assertThat(called).isFalse();
        }

        @Test
        void match_onOk_dispatchesToOkAction() {
            AtomicReference<String> ref = new AtomicReference<>();
            Result.ok("v").match(ref::set, e -> { throw new AssertionError("should not be called"); });
            assertThat(ref.get()).isEqualTo("v");
        }

        @Test
        void match_onErr_dispatchesToErrAction() {
            AtomicReference<String> ref = new AtomicReference<>();
            Result.<String, String>err("e").match(v -> { throw new AssertionError("should not be called"); }, ref::set);
            assertThat(ref.get()).isEqualTo("e");
        }

        @Test
        void fold_onOk_appliesOkMapper() {
            String result = Result.<String, String>ok("hello").fold(s -> "ok:" + s, e -> "err:" + e);
            assertThat(result).isEqualTo("ok:hello");
        }

        @Test
        void fold_onErr_appliesErrMapper() {
            String result = Result.<String, String>err("fail").fold(s -> "ok:" + s, e -> "err:" + e);
            assertThat(result).isEqualTo("err:fail");
        }
    }

    // ==================== 集合操作 ====================

    @Nested
    @DisplayName("集合操作")
    class CollectionOperations {

        @Test
        void collectShortCircuit_allOk_returnsOkList() {
            List<Result<Integer, String>> results = List.of(Result.ok(1), Result.ok(2), Result.ok(3));
            Result<List<Integer>, String> collected = Result.collectShortCircuit(results);
            assertThat(collected.isOk()).isTrue();
            assertThat(collected.get()).containsExactly(1, 2, 3);
        }

        @Test
        void collectShortCircuit_firstErr_returnsFirstErr() {
            List<Result<Integer, String>> results = List.of(Result.ok(1), Result.err("e1"), Result.err("e2"));
            Result<List<Integer>, String> collected = Result.collectShortCircuit(results);
            assertThat(collected.isErr()).isTrue();
            assertThat(collected.getErr()).isEqualTo("e1");
        }

        @Test
        void collectShortCircuit_emptyList_returnsEmptyOkList() {
            Result<List<Integer>, String> collected = Result.collectShortCircuit(List.of());
            assertThat(collected.isOk()).isTrue();
            assertThat(collected.get()).isEmpty();
        }

        @Test
        void collectAll_allOk_returnsOkList() {
            Result<List<Integer>, List<String>> result = Stream.<Result<Integer, String>>of(
                    Result.ok(1), Result.ok(2)
            ).collect(Result.collectAll());
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).containsExactly(1, 2);
        }

        @Test
        void collectAll_hasErrors_returnsErrList() {
            Result<List<Integer>, List<String>> result = Stream.<Result<Integer, String>>of(
                    Result.ok(1), Result.err("e1"), Result.ok(2), Result.err("e2")
            ).collect(Result.collectAll());
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr()).containsExactly("e1", "e2");
        }

        @Test
        void collectAll_empty_returnsEmptyOkList() {
            Result<List<Integer>, List<String>> result = Stream.<Result<Integer, String>>of()
                    .collect(Result.collectAll());
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).isEmpty();
        }
    }

    // ==================== toString ====================

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        void ok_toString() {
            assertThat(Result.ok("hello").toString()).isEqualTo("Ok(hello)");
        }

        @Test
        void err_toString() {
            assertThat(Result.err("fail").toString()).isEqualTo("Err(fail)");
        }
    }

    // ==================== empty() 工厂方法 ====================

    @Nested
    @DisplayName("Result.empty() - 无值成功工厂方法 (phase-4 RP-10 / ADR-0007)")
    class EmptyTests {

        @Test
        @DisplayName("empty() 返回 Ok 实例，isOk()=true 且 get()=null")
        void emptyReturnsOkWithNullValue() {
            Result<String, String> result = Result.empty();
            assertThat(result.isOk()).isTrue();
            assertThat(result.isErr()).isFalse();
            assertThat(result.get()).isNull();
        }

        @Test
        @DisplayName("empty().toOptional() 为 Optional.empty()")
        void emptyToOptionalIsEmpty() {
            assertThat(Result.<String, String>empty().toOptional()).isEmpty();
        }

        @Test
        @DisplayName("empty().stream() 为空 Stream")
        void emptyStreamIsEmpty() {
            assertThat(Result.<String, String>empty().stream().toList()).isEmpty();
        }

        @Test
        @DisplayName("empty() 与 ok(null) 语义等价（toOptional / stream 均空）")
        void emptyEquivalentToOkNull() {
            Result<String, String> empty = Result.empty();
            Result<String, String> okNull = Result.ok(null);
            assertThat(empty.toOptional()).isEqualTo(okNull.toOptional());
            assertThat(empty.stream().toList()).isEqualTo(okNull.stream().toList());
        }

        @Test
        @DisplayName("empty() 支持任意 T / E 类型参数")
        void emptySupportsArbitraryTypes() {
            Result<Integer, RuntimeException> intResult = Result.empty();
            Result<List<String>, IllegalArgumentException> listResult = Result.empty();
            assertThat(intResult.isOk()).isTrue();
            assertThat(listResult.isOk()).isTrue();
        }
    }
}
