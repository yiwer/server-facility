package cn.code91.facility.masking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MaskUtil - 脱敏静态门面")
class MaskUtilTest {

    // ==================== 私有构造 ====================

    @Test
    void constructor_isPrivateAndThrows() throws NoSuchMethodException {
        Constructor<MaskUtil> constructor = MaskUtil.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    // ==================== null/空/无命中契约 ====================

    @Nested
    @DisplayName("null 契约与零分配")
    class NullContract {

        @Test
        void mask_null_returnsNull() {
            assertThat(MaskUtil.mask(null)).isNull();
        }

        @Test
        void mask_empty_returnsEmpty() {
            assertThat(MaskUtil.mask("")).isEmpty();
        }

        @Test
        void mask_noHit_returnsSameInstance() {
            String text = "hello world, nothing sensitive";
            assertThat(MaskUtil.mask(text)).isSameAs(text);
        }

        @Test
        void maskPhone_null_returnsNull() {
            assertThat(MaskUtil.maskPhone(null)).isNull();
        }

        @Test
        void maskEmail_null_returnsNull() {
            assertThat(MaskUtil.maskEmail(null)).isNull();
        }
    }

    // ==================== PHONE ====================

    @Nested
    @DisplayName("手机号:前3后4")
    class Phone {

        @Test
        void mask_phone_inText() {
            assertThat(MaskUtil.mask("用户 13800138000 下单"))
                    .isEqualTo("用户 138****8000 下单");
        }

        @Test
        void mask_phone_bareValue() {
            assertThat(MaskUtil.mask("15912345678")).isEqualTo("159****5678");
        }

        @Test
        void mask_phone_insideLongerDigitRun_untouched() {
            // 13 位 epoch 毫秒:PHONE 后向边界 (?!\d) 拦截,BANKCARD 下限 15 不收
            String text = "ts=1751675300123";
            assertThat(MaskUtil.mask(text)).isSameAs(text);
        }

        @Test
        void mask_twelveDigitOrderNo_untouched() {
            String text = "orderNo=123456789012";
            assertThat(MaskUtil.mask(text)).isSameAs(text);
        }

        @Test
        void maskPhone_singleRule_ignoresEmail() {
            assertThat(MaskUtil.maskPhone("13800138000 zhangsan@example.com"))
                    .isEqualTo("138****8000 zhangsan@example.com");
        }
    }

    // ==================== EMAIL ====================

    @Nested
    @DisplayName("邮箱:留首字符与完整域名")
    class Email {

        @Test
        void mask_email_inText() {
            assertThat(MaskUtil.mask("联系 zhangsan@example.com 确认"))
                    .isEqualTo("联系 z***@example.com 确认");
        }

        @Test
        void mask_email_shortLocal() {
            assertThat(MaskUtil.mask("a@b.co")).isEqualTo("a***@b.co");
        }

        @Test
        void mask_email_dottedLocalAndSubdomain() {
            assertThat(MaskUtil.mask("zhang.san+tag@ex-mail.co.uk"))
                    .isEqualTo("z***@ex-mail.co.uk");
        }

        @Test
        void mask_emailWithPhoneLocal_maskedAsEmail() {
            // EMAIL 先于 PHONE:local 恰为手机号形态按邮箱语义整体遮蔽(spec §5.1)
            assertThat(MaskUtil.mask("13800138000@qq.com")).isEqualTo("1***@qq.com");
        }

        @Test
        void maskEmail_singleRule_ignoresPhone() {
            assertThat(MaskUtil.maskEmail("13800138000 zhangsan@example.com"))
                    .isEqualTo("13800138000 z***@example.com");
        }
    }

    // ==================== 组合 ====================

    @Test
    void mask_multipleHits_allMasked() {
        assertThat(MaskUtil.mask("phone=13800138000 mail=zhangsan@example.com"))
                .isEqualTo("phone=138****8000 mail=z***@example.com");
    }
}
