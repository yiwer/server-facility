package cn.code91.facility.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("WrappedError - 不可变错误容器")
class WrappedErrorTest {

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        void of_withErrorTypeOnly() {
            WrappedError err = WrappedError.of(FacilityErrorType.JSON_SERIALIZE_ERROR);
            assertThat(err.getErrorType()).isEqualTo(FacilityErrorType.JSON_SERIALIZE_ERROR);
            assertThat(err.getException()).isNull();
            assertThat(err.hasException()).isFalse();
            assertThat(err.hasArgs()).isFalse();
        }

        @Test
        void of_withErrorTypeAndException() {
            RuntimeException ex = new RuntimeException("boom");
            WrappedError err = WrappedError.of(FacilityErrorType.JSON_SERIALIZE_ERROR, ex);
            assertThat(err.getErrorType()).isEqualTo(FacilityErrorType.JSON_SERIALIZE_ERROR);
            assertThat(err.getException()).isSameAs(ex);
            assertThat(err.hasException()).isTrue();
            assertThat(err.hasArgs()).isFalse();
        }

        @Test
        void of_withErrorTypeExceptionAndArgs() {
            RuntimeException ex = new RuntimeException("boom");
            WrappedError err = WrappedError.of(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED, ex, "test.txt", 42);
            assertThat(err.hasException()).isTrue();
            assertThat(err.hasArgs()).isTrue();
            assertThat(err.getArgCount()).isEqualTo(2);
            assertThat(err.getArg(0)).isEqualTo("test.txt");
            assertThat(err.getArg(1)).isEqualTo(42);
        }

        @Test
        void ofWithArgs_createsWithArgsOnly() {
            WrappedError err = WrappedError.ofWithArgs(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED, "path/to/file");
            assertThat(err.hasException()).isFalse();
            assertThat(err.hasArgs()).isTrue();
            assertThat(err.getArg(0)).isEqualTo("path/to/file");
        }

        @Test
        void of_nullErrorType_throwsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> WrappedError.of(null));
        }
    }

    @Nested
    @DisplayName("查询方法")
    class QueryMethods {

        @Test
        void getArg_validIndex_returnsArg() {
            WrappedError err = WrappedError.ofWithArgs(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED, "a", "b");
            assertThat(err.getArg(0)).isEqualTo("a");
            assertThat(err.getArg(1)).isEqualTo("b");
        }

        @Test
        void getArg_invalidIndex_throwsIndexOutOfBounds() {
            WrappedError err = WrappedError.of(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED);
            assertThatExceptionOfType(IndexOutOfBoundsException.class)
                    .isThrownBy(() -> err.getArg(0));
        }

        @Test
        void getArg_negativeIndex_throwsIndexOutOfBounds() {
            WrappedError err = WrappedError.ofWithArgs(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED, "a");
            assertThatExceptionOfType(IndexOutOfBoundsException.class)
                    .isThrownBy(() -> err.getArg(-1));
        }

        @Test
        void getArgWithType_correctType_returnsTypedValue() {
            WrappedError err = WrappedError.ofWithArgs(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED, 42L);
            Long val = err.getArg(0, Long.class);
            assertThat(val).isEqualTo(42L);
        }

        @Test
        void getArgWithType_wrongType_throwsClassCast() {
            WrappedError err = WrappedError.ofWithArgs(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED, "str");
            assertThatExceptionOfType(ClassCastException.class)
                    .isThrownBy(() -> err.getArg(0, Integer.class));
        }

        @Test
        void getArgWithType_nullArg_returnsNull() {
            WrappedError err = WrappedError.ofWithArgs(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED, (Object) null);
            assertThat(err.getArg(0, String.class)).isNull();
        }

        @Test
        void isErrorType_matching_returnsTrue() {
            WrappedError err = WrappedError.of(FacilityErrorType.JSON_SERIALIZE_ERROR);
            assertThat(err.isErrorType(FacilityErrorType.JSON_SERIALIZE_ERROR)).isTrue();
        }

        @Test
        void isErrorType_notMatching_returnsFalse() {
            WrappedError err = WrappedError.of(FacilityErrorType.JSON_SERIALIZE_ERROR);
            assertThat(err.isErrorType(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED)).isFalse();
        }

        @Test
        void isErrorCode_matching_returnsTrue() {
            WrappedError err = WrappedError.of(FacilityErrorType.JSON_SERIALIZE_ERROR);
            assertThat(err.isErrorCode(500101)).isTrue();
        }

        @Test
        void isErrorCode_notMatching_returnsFalse() {
            WrappedError err = WrappedError.of(FacilityErrorType.JSON_SERIALIZE_ERROR);
            assertThat(err.isErrorCode(999)).isFalse();
        }

        @Test
        void getArgsList_returnsUnmodifiableList() {
            WrappedError err = WrappedError.ofWithArgs(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED, "a", "b");
            assertThat(err.getArgsList()).containsExactly("a", "b");
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> err.getArgsList().add("c"));
        }
    }

    @Nested
    @DisplayName("消息格式化")
    class MessageFormatting {

        @Test
        void getFormattedMessage_noArgs_returnsDefaultMessage() {
            WrappedError err = WrappedError.of(FacilityErrorType.JSON_SERIALIZE_ERROR);
            assertThat(err.getFormattedMessage()).isEqualTo("对象序列化异常");
        }

        @Test
        void getFullMessage_withException_includesExceptionInfo() {
            RuntimeException ex = new RuntimeException("root cause");
            WrappedError err = WrappedError.of(FacilityErrorType.JSON_SERIALIZE_ERROR, ex);
            String fullMsg = err.getFullMessage();
            assertThat(fullMsg).contains("FACILITY-500101");
            assertThat(fullMsg).contains("对象序列化异常");
            assertThat(fullMsg).contains("RuntimeException");
            assertThat(fullMsg).contains("root cause");
        }

        @Test
        void getFullMessage_withoutException_noExceptionInfo() {
            WrappedError err = WrappedError.of(FacilityErrorType.JSON_SERIALIZE_ERROR);
            String fullMsg = err.getFullMessage();
            assertThat(fullMsg).contains("FACILITY-500101");
            assertThat(fullMsg).doesNotContain("Exception");
        }
    }

    @Nested
    @DisplayName("防御性拷贝")
    class DefensiveCopy {

        @Test
        void modifyingOriginalArgs_doesNotAffectWrappedError() {
            Object[] args = {"original"};
            WrappedError err = WrappedError.ofWithArgs(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED, args);
            args[0] = "modified";
            assertThat(err.getArg(0)).isEqualTo("original");
        }

        @Test
        void getArgs_returnsDefensiveCopy() {
            WrappedError err = WrappedError.ofWithArgs(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED, "a");
            Object[] returned = err.getArgs();
            returned[0] = "modified";
            assertThat(err.getArg(0)).isEqualTo("a");
        }
    }

    @Nested
    @DisplayName("equals/hashCode/toString")
    class ObjectMethods {

        @Test
        void equals_sameTypeAndArgs_equal() {
            WrappedError e1 = WrappedError.ofWithArgs(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED, "a");
            WrappedError e2 = WrappedError.ofWithArgs(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED, "a");
            assertThat(e1).isEqualTo(e2);
            assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
        }

        @Test
        void equals_differentType_notEqual() {
            WrappedError e1 = WrappedError.of(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED);
            WrappedError e2 = WrappedError.of(FacilityErrorType.JSON_SERIALIZE_ERROR);
            assertThat(e1).isNotEqualTo(e2);
        }

        @Test
        void toString_containsKeyInfo() {
            WrappedError err = WrappedError.of(FacilityErrorType.JSON_SERIALIZE_ERROR);
            assertThat(err.toString()).contains("FACILITY-500101");
        }
    }
}
