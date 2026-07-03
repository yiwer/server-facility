package cn.code91.facility.number;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("NumberFormat.formatSize - 各量级 + ≥1EB 进位不越界 (RV2-04, 债3)")
class NumberFormatSizeTest {

    @Test @DisplayName("Long.MAX_VALUE / 1EB 不抛 AIOOBE，进位 EB(债3:原 clamp 落 PB)")
    void exabyteScaleDoesNotOverflow() {
        assertThatCode(() -> NumberFormat.formatSize(Long.MAX_VALUE)).doesNotThrowAnyException();
        assertThat(NumberFormat.formatSize(Long.MAX_VALUE)).endsWith("EB");
        assertThat(NumberFormat.formatSize(1L << 60)).isEqualTo("1.00 EB"); // 1 EB = 1024^6
    }

    @Test @DisplayName("各量级边界正确(B→PB,1024^n 无浮点偏差)")
    void commonBoundaries() {
        assertThat(NumberFormat.formatSize(0)).isEqualTo("0 B");
        assertThat(NumberFormat.formatSize(1023)).isEqualTo("1023 B");
        assertThat(NumberFormat.formatSize(1024)).isEqualTo("1.00 KB");
        assertThat(NumberFormat.formatSize(1536)).isEqualTo("1.50 KB");
        assertThat(NumberFormat.formatSize(1048576L)).isEqualTo("1.00 MB");
        assertThat(NumberFormat.formatSize(1073741824L)).isEqualTo("1.00 GB");
        assertThat(NumberFormat.formatSize(1099511627776L)).isEqualTo("1.00 TB");
        assertThat(NumberFormat.formatSize(1125899906842624L)).isEqualTo("1.00 PB");
    }

    @Test @DisplayName("负字节走 B 分支不崩溃")
    void negativeBytes() {
        assertThat(NumberFormat.formatSize(-5L)).isEqualTo("-5 B");
    }
}
