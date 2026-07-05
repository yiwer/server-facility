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

    // ==================== IDCARD ====================

    @Nested
    @DisplayName("身份证 18 位:前6后4,mod11-2 通过才遮")
    class IdCard {

        @Test
        void mask_validIdCard_masked() {
            // 110101199003070011:校验位经 GB 11643 mod11-2 计算为 1(见 Task 2 Step 1 注)
            assertThat(MaskUtil.mask("身份证 110101199003070011 已核验"))
                    .isEqualTo("身份证 110101********0011 已核验");
        }

        @Test
        void mask_validIdCardEndingX_masked() {
            // 11010119900307002X:尾位 X(sum%11==2),大小写均识别
            assertThat(MaskUtil.mask("11010119900307002X"))
                    .isEqualTo("110101********002X");
            assertThat(MaskUtil.mask("11010119900307002x"))
                    .isEqualTo("110101********002x");
        }

        @Test
        void mask_checksumFailing18Digits_untouched() {
            // 18 个 1:mod11-2 校验位应为 0 ≠ 1,Luhn 和 27 亦不过——双拒,原样保留(雪花 ID 保护)
            String text = "traceId=111111111111111111";
            assertThat(MaskUtil.mask(text)).isSameAs(text);
        }

        @Test
        void mask_idCardEndingX_checksumFails_untouched() {
            // 11010119900307003X:sum%11==4 应为 8 ≠ X——校验不过且尾 X 非纯数字,无 Luhn 级联,原样
            String text = "11010119900307003X";
            assertThat(MaskUtil.mask(text)).isSameAs(text);
        }

        @Test
        void maskIdCard_singleRule_noBankCardCascade() {
            // 单规则 helper 无 BANKCARD 级联:mod11-2 不过、Luhn 过的 18 位串在 maskIdCard 下原样
            String text = "566683211356647666";
            assertThat(MaskUtil.maskIdCard(text)).isSameAs(text);
        }
    }

    // ==================== BANKCARD ====================

    @Nested
    @DisplayName("银行卡 15-19 位:仅留后4,Luhn 通过才遮")
    class BankCard {

        @Test
        void mask_visa16_masked() {
            assertThat(MaskUtil.mask("card=4111111111111111 ok"))
                    .isEqualTo("card=************1111 ok");
        }

        @Test
        void mask_unionPay19_masked() {
            // 6222020000000000000:末位 0 为 Luhn 校验位(基串加权和 20)
            assertThat(MaskUtil.mask("6222020000000000000"))
                    .isEqualTo("***************0000");
        }

        @Test
        void mask_longMax19_luhnFails_untouched() {
            // Long.MAX_VALUE:Luhn 和 78 不过——典型雪花/序列号形态保留
            String text = "id=9223372036854775807";
            assertThat(MaskUtil.mask(text)).isSameAs(text);
        }

        @Test
        void mask_luhnValid18Digits_idChecksumFails_cascadesToBankCard() {
            // 566683211356647666:mod11-2 余 8(应为 4 号位字符 8 ≠ 6)不过、Luhn 和 70 过
            // → 按银行卡样式遮(spec §5.1 级联)
            assertThat(MaskUtil.mask("566683211356647666"))
                    .isEqualTo("**************7666");
        }

        @Test
        void maskBankCard_singleRule_ignoresPhone() {
            assertThat(MaskUtil.maskBankCard("13800138000 4111111111111111"))
                    .isEqualTo("13800138000 ************1111");
        }
    }

    // ==================== 组合 ====================

    @Test
    void mask_multipleHits_allMasked() {
        assertThat(MaskUtil.mask("phone=13800138000 mail=zhangsan@example.com"))
                .isEqualTo("phone=138****8000 mail=z***@example.com");
    }

    // ==================== SECRET 键值 ====================

    @Nested
    @DisplayName("键值秘密:值全遮蔽固定 ******(不保长)")
    class Secret {

        @Test
        void mask_keyEquals_masked() {
            assertThat(MaskUtil.mask("password=P@ssw0rd123 ok"))
                    .isEqualTo("password=****** ok");
        }

        @Test
        void mask_keyColonSpace_masked() {
            assertThat(MaskUtil.mask("pwd: hello,world"))
                    .isEqualTo("pwd: ******,world");
        }

        @Test
        void mask_jsonQuotedPair_keepsQuoteStructure() {
            assertThat(MaskUtil.mask("{\"token\":\"abc-def_123\",\"a\":1}"))
                    .isEqualTo("{\"token\":\"******\",\"a\":1}");
        }

        @Test
        void mask_bearerAuthorization_wholeValueMasked() {
            assertThat(MaskUtil.mask("Authorization: Bearer eyJhbGciOi.eyJzdWIi.sig"))
                    .isEqualTo("Authorization: ******");
        }

        @Test
        void mask_camelCaseKeyVariants_masked() {
            assertThat(MaskUtil.mask("accessToken=aaa apiKey=bbb clientSecret=ccc"))
                    .isEqualTo("accessToken=****** apiKey=****** clientSecret=******");
        }

        @Test
        void mask_urlQueryToken_stopsAtAmpersand() {
            assertThat(MaskUtil.mask("GET /cb?token=abc123&next=1"))
                    .isEqualTo("GET /cb?token=******&next=1");
        }

        @Test
        void mask_lengthNotPreserved() {
            // 不保长:长度本身是秘密信息
            assertThat(MaskUtil.mask("secret=ab")).isEqualTo("secret=******");
            assertThat(MaskUtil.mask("secret=abcdefghijklmnopqrstuvwxyz"))
                    .isEqualTo("secret=******");
        }

        @Test
        void mask_singleQuotedValue_keepsQuoteStructure() {
            assertThat(MaskUtil.mask("token='abc 123'"))
                    .isEqualTo("token='******'");
        }

        @Test
        void mask_singleCharValue_masked() {
            assertThat(MaskUtil.mask("pwd=a")).isEqualTo("pwd=******");
        }

        @Test
        void maskSecrets_singleGroup_ignoresPhone() {
            assertThat(MaskUtil.maskSecrets("13800138000 password=x"))
                    .isEqualTo("13800138000 password=******");
        }

        @Test
        void mask_keywordAsSuffixOfIdentifier_masked_bySubstringSemantics() {
            // 有意的 substring 语义:键名含关键词紧邻分隔符即命中(宁多遮不漏遮)
            assertThat(MaskUtil.mask("mypassword=x")).isEqualTo("mypassword=******");
        }

        @Test
        void mask_keywordNotAdjacentToSeparator_untouched() {
            // 误报面受「关键词须紧邻分隔符」约束:tokenizer 的 izer 隔断,不命中
            String text = "tokenizer=whitespace";
            assertThat(MaskUtil.mask(text)).isSameAs(text);
        }
    }

    // ==================== JWT ====================

    @Nested
    @DisplayName("裸 JWT:整体遮蔽")
    class Jwt {

        @Test
        void mask_bareJwt_masked() {
            assertThat(MaskUtil.mask(
                    "sig eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c end"))
                    .isEqualTo("sig ****** end");
        }

        @Test
        void maskSecrets_coversJwt() {
            assertThat(MaskUtil.maskSecrets("eyJhbGci.eyJzdWIi.c2ln"))
                    .isEqualTo("******");
        }
    }

    // ==================== 幂等与全组合 ====================

    @Nested
    @DisplayName("幂等与全组合(锁定测试)")
    class Idempotence {

        @Test
        void mask_isIdempotent() {
            String text = "u=13800138000 id=110101199003070011 card=4111111111111111"
                    + " m=zhangsan@example.com password=abc jwt=eyJa.eyJb.c2ln";
            String once = MaskUtil.mask(text);
            assertThat(MaskUtil.mask(once)).isEqualTo(once);
        }

        @Test
        void mask_allSixRules_inOneMessage() {
            String text = "phone=13800138000, id=110101199003070011, card=4111111111111111,"
                    + " mail=zhangsan@example.com, password=P@ss, t=eyJhbGci.eyJzdWIi.c2ln";
            assertThat(MaskUtil.mask(text)).isEqualTo(
                    "phone=138****8000, id=110101********0011, card=************1111,"
                    + " mail=z***@example.com, password=******, t=******");
        }
    }
}
