package cn.code91.facility.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ErrorTypeInterface - 错误类型契约")
class ErrorTypeInterfaceTest {

    @Nested
    @DisplayName("派生方法")
    class DerivedMethods {

        @Test
        void getFullCode_returnsModuleDashCode() {
            assertThat(FacilityErrorType.JSON_SERIALIZE_ERROR.getFullCode()).isEqualTo("FACILITY-500101");
        }

        @Test
        void format_noArgs_returnsDefaultMessage() {
            assertThat(FacilityErrorType.JSON_SERIALIZE_ERROR.format()).isEqualTo("对象序列化异常");
        }

        @Test
        void format_withArgs_substitutesPlaceholders() {
            // Use a custom error type with placeholder
            ErrorTypeInterface customType = new ErrorTypeInterface() {
                @Override public int getCode() { return 1; }
                @Override public String getMessageKey() { return "test"; }
                @Override public String getDefaultMessage() { return "用户 {0} 不存在"; }
            };
            assertThat(customType.format("张三")).isEqualTo("用户 张三 不存在");
        }

        @Test
        void format_invalidPattern_fallbackGracefully() {
            // MessageFormat with invalid pattern should not throw
            ErrorTypeInterface customType = new ErrorTypeInterface() {
                @Override public int getCode() { return 1; }
                @Override public String getMessageKey() { return "test"; }
                @Override public String getDefaultMessage() { return "bad pattern {"; }
            };
            String result = customType.format("arg");
            assertThat(result).contains("bad pattern {");
            assertThat(result).contains("格式化失败");
        }

        @Test
        void getSeverity_defaultIsInfo() {
            assertThat(FacilityErrorType.JSON_SERIALIZE_ERROR.getSeverity())
                    .isEqualTo(ErrorTypeInterface.ErrorSeverity.INFO);
        }

        @Test
        void getModule_default_returnsUnknown() {
            ErrorTypeInterface custom = new ErrorTypeInterface() {
                @Override public int getCode() { return 1; }
                @Override public String getMessageKey() { return "test"; }
                @Override public String getDefaultMessage() { return "msg"; }
            };
            assertThat(custom.getModule()).isEqualTo("UNKNOWN");
        }

        @Test
        void validateCodeRange_inRange_returnsTrue() {
            assertThat(FacilityErrorType.JSON_SERIALIZE_ERROR.validateCodeRange(500000, 500199)).isTrue();
        }

        @Test
        void validateCodeRange_outOfRange_returnsFalse() {
            assertThat(FacilityErrorType.JSON_SERIALIZE_ERROR.validateCodeRange(600000, 600099)).isFalse();
        }

        @Test
        void getDetailedDescription_containsAllFields() {
            String desc = FacilityErrorType.JSON_SERIALIZE_ERROR.getDetailedDescription();
            assertThat(desc).contains("FACILITY-500101");
            assertThat(desc).contains("facility.json.serialize_error");
            assertThat(desc).contains("对象序列化异常");
            assertThat(desc).contains("INFO");
        }
    }

    @Nested
    @DisplayName("FacilityErrorType 枚举完整性")
    class FacilityErrorTypeCompleteness {

        @Test
        void allCodes_areInValidRange() {
            for (FacilityErrorType type : FacilityErrorType.values()) {
                assertThat(type.getCode())
                        .as("Code for %s", type.name())
                        .isBetween(500000, 500699);
            }
        }

        @Test
        void allCodes_areUnique() {
            Set<Integer> codes = new HashSet<>();
            for (FacilityErrorType type : FacilityErrorType.values()) {
                assertThat(codes.add(type.getCode()))
                        .as("Duplicate code %d in %s", type.getCode(), type.name())
                        .isTrue();
            }
        }

        @Test
        void allMessageKeys_areNonBlank() {
            for (FacilityErrorType type : FacilityErrorType.values()) {
                assertThat(type.getMessageKey())
                        .as("messageKey for %s", type.name())
                        .isNotBlank();
            }
        }

        @Test
        void allDefaultMessages_areNonBlank() {
            for (FacilityErrorType type : FacilityErrorType.values()) {
                assertThat(type.getDefaultMessage())
                        .as("defaultMessage for %s", type.name())
                        .isNotBlank();
            }
        }
    }
}
