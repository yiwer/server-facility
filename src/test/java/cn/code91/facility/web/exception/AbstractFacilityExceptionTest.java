package cn.code91.facility.web.exception;

import cn.code91.facility.error.FacilityErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AbstractFacilityException - 基类抽取 (RV2-13)")
class AbstractFacilityExceptionTest {

    @Test @DisplayName("BusinessException/SystemException 均为 AbstractFacilityException + FacilityException 子类型，且继承方法行为不变")
    void subclassesShareBase() {
        BusinessException biz = BusinessException.of(FacilityErrorType.JSON_SERIALIZE_ERROR, "a");
        SystemException sys = SystemException.of(FacilityErrorType.JSON_SERIALIZE_ERROR, "a");

        assertThat(biz).isInstanceOf(AbstractFacilityException.class).isInstanceOf(FacilityException.class);
        assertThat(sys).isInstanceOf(AbstractFacilityException.class).isInstanceOf(FacilityException.class);

        assertThat(biz.getCode()).isEqualTo(FacilityErrorType.JSON_SERIALIZE_ERROR.getCode());
        assertThat(biz.getErrorType()).isEqualTo(FacilityErrorType.JSON_SERIALIZE_ERROR);
        assertThat(biz.getArgs()).containsExactly("a");
        assertThat(biz.toWrappedError().getErrorType()).isEqualTo(FacilityErrorType.JSON_SERIALIZE_ERROR);
    }
}
