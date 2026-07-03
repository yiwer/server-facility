package cn.code91.facility.web.exception;

import cn.code91.facility.error.ErrorTypeInterface;
import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

@DisplayName("BusinessException & SystemException")
class BusinessExceptionTest {

    // ==================== BusinessException ====================

    @Nested
    @DisplayName("BusinessException")
    class BusinessExceptionTests {

        @Test
        void of_withErrorType_createsException() {
            BusinessException ex = BusinessException.of(FacilityErrorType.JSON_SERIALIZE_ERROR);
            assertThat(ex.getCode()).isEqualTo(500101);
            assertThat(ex.getErrorType()).isEqualTo(FacilityErrorType.JSON_SERIALIZE_ERROR);
            assertThat(ex.getMessage()).isEqualTo("对象序列化异常");
            assertThat(ex.getCause()).isNull();
        }

        @Test
        void of_withArgs_formatsMessage() {
            ErrorTypeInterface type = new ErrorTypeInterface() {
                @Override public int getCode() { return 1; }
                @Override public String getMessageKey() { return "test"; }
                @Override public String getDefaultMessage() { return "用户 {0} 不存在"; }
            };
            BusinessException ex = BusinessException.of(type, "张三");
            assertThat(ex.getMessage()).isEqualTo("用户 张三 不存在");
            assertThat(ex.getFormattedMessage()).isEqualTo("用户 张三 不存在");
        }

        @Test
        void of_withCause_wrapsException() {
            RuntimeException cause = new RuntimeException("root");
            BusinessException ex = BusinessException.of(FacilityErrorType.JSON_SERIALIZE_ERROR, cause);
            assertThat(ex.getCause()).isSameAs(cause);
        }

        @Test
        void supplierErr_returnsSupplier() {
            Supplier<BusinessException> supplier = BusinessException.supplierErr(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED);
            BusinessException ex = supplier.get();
            assertThat(ex.getCode()).isEqualTo(500000);
        }

        @Test
        void fromWrappedError_convertsCorrectly() {
            RuntimeException cause = new RuntimeException("cause");
            WrappedError error = WrappedError.of(FacilityErrorType.JSON_SERIALIZE_ERROR, cause, "arg1");
            BusinessException ex = BusinessException.fromWrappedError(error);
            assertThat(ex.getCode()).isEqualTo(500101);
            assertThat(ex.getCause()).isSameAs(cause);
            assertThat(ex.getArgs()).containsExactly("arg1");
        }

        @Test
        void toWrappedError_convertsBack() {
            RuntimeException cause = new RuntimeException("cause");
            BusinessException ex = BusinessException.of(FacilityErrorType.JSON_SERIALIZE_ERROR, cause, "arg1");
            WrappedError error = ex.toWrappedError();
            assertThat(error.getErrorType()).isEqualTo(FacilityErrorType.JSON_SERIALIZE_ERROR);
            assertThat(error.getException()).isSameAs(cause);
            assertThat(error.getArg(0)).isEqualTo("arg1");
        }

        @Test
        void toWrappedError_noCause_noException() {
            BusinessException ex = BusinessException.of(FacilityErrorType.JSON_SERIALIZE_ERROR);
            WrappedError error = ex.toWrappedError();
            assertThat(error.hasException()).isFalse();
        }

        @Test
        void getArgs_returnsDefensiveCopy() {
            BusinessException ex = BusinessException.of(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED, "a", "b");
            Object[] args = ex.getArgs();
            args[0] = "modified";
            assertThat(ex.getArgs()[0]).isEqualTo("a");
        }
    }

    // ==================== SystemException ====================

    @Nested
    @DisplayName("SystemException")
    class SystemExceptionTests {

        @Test
        void of_withErrorType_createsException() {
            SystemException ex = SystemException.of(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED);
            assertThat(ex.getCode()).isEqualTo(500000);
            assertThat(ex.getErrorType()).isEqualTo(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED);
        }

        @Test
        void of_withCause_wrapsException() {
            RuntimeException cause = new RuntimeException("root");
            SystemException ex = SystemException.of(FacilityErrorType.JSON_SERIALIZE_ERROR, cause);
            assertThat(ex.getCause()).isSameAs(cause);
        }

        @Test
        void supplierErr_returnsSupplier() {
            Supplier<SystemException> supplier = SystemException.supplierErr(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED);
            SystemException ex = supplier.get();
            assertThat(ex.getCode()).isEqualTo(500000);
        }

        @Test
        void fromWrappedError_convertsCorrectly() {
            WrappedError error = WrappedError.of(FacilityErrorType.JSON_SERIALIZE_ERROR);
            SystemException ex = SystemException.fromWrappedError(error);
            assertThat(ex.getCode()).isEqualTo(500101);
        }

        @Test
        void toWrappedError_convertsBack() {
            RuntimeException cause = new RuntimeException("cause");
            SystemException ex = SystemException.of(FacilityErrorType.JSON_SERIALIZE_ERROR, cause);
            WrappedError error = ex.toWrappedError();
            assertThat(error.getErrorType()).isEqualTo(FacilityErrorType.JSON_SERIALIZE_ERROR);
            assertThat(error.getException()).isSameAs(cause);
        }
    }
}
