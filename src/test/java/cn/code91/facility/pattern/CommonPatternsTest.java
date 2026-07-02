package cn.code91.facility.pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CommonPatterns - 预制谓词")
class CommonPatternsTest {

    @Test
    void isEmail() {
        assertThat(CommonPatterns.isEmail("a.b+c@d-e.org")).isTrue();
        assertThat(CommonPatterns.isEmail("not-an-email")).isFalse();
        assertThat(CommonPatterns.isEmail(null)).isFalse();
    }

    @Test
    void isMobileCN() {
        assertThat(CommonPatterns.isMobileCN("13912345678")).isTrue();
        assertThat(CommonPatterns.isMobileCN("12912345678")).isFalse();
    }

    @Test
    void isIdCard_accepts18And15() {
        assertThat(CommonPatterns.isIdCard("110101199003077516")).isTrue();
        assertThat(CommonPatterns.isIdCard("110101900307751")).isTrue();
        assertThat(CommonPatterns.isIdCard("12345")).isFalse();
    }

    @Test
    void isUrl() {
        assertThat(CommonPatterns.isUrl("https://example.com/a?b=1")).isTrue();
        assertThat(CommonPatterns.isUrl("ftp://example.com")).isFalse();
    }

    @Test
    void isIpv4() {
        assertThat(CommonPatterns.isIpv4("10.0.0.255")).isTrue();
        assertThat(CommonPatterns.isIpv4("10.0.0.256")).isFalse();
    }

    @Test
    void isInteger() {
        assertThat(CommonPatterns.isInteger("-42")).isTrue();
        assertThat(CommonPatterns.isInteger("4.2")).isFalse();
    }

    @Test
    void isNumber() {
        assertThat(CommonPatterns.isNumber("-4.2")).isTrue();
        assertThat(CommonPatterns.isNumber("4.")).isFalse();
    }

    @Test
    void containsChinese() {
        assertThat(CommonPatterns.containsChinese("hello 世界")).isTrue();
        assertThat(CommonPatterns.containsChinese("hello")).isFalse();
    }

    @Test
    void isUuid() {
        assertThat(CommonPatterns.isUuid("123e4567-e89b-12d3-a456-426614174000")).isTrue();
        assertThat(CommonPatterns.isUuid("123e4567e89b12d3a456426614174000")).isFalse();
    }

    @Test
    void nullInput_alwaysFalse() {
        assertThat(CommonPatterns.isUuid(null)).isFalse();
        assertThat(CommonPatterns.containsChinese(null)).isFalse();
    }
}
