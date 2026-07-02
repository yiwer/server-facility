package cn.code91.facility.number;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("NumberFormat.formatSize - ≥1EB 不越界 + 常规正确 (RV2-04)")
class NumberFormatSizeTest {

    @Test @DisplayName("Long.MAX_VALUE / 1EB 不抛 AIOOBE，落 PB")
    void exabyteScaleDoesNotOverflow() {
        assertThatCode(() -> NumberFormat.formatSize(Long.MAX_VALUE)).doesNotThrowAnyException();
        assertThat(NumberFormat.formatSize(Long.MAX_VALUE)).endsWith("PB");
        assertThat(NumberFormat.formatSize(1L << 60)).endsWith("PB"); // 1 EB
    }

    @Test @DisplayName("常规边界正确")
    void commonBoundaries() {
        assertThat(NumberFormat.formatSize(0)).isEqualTo("0 B");
        assertThat(NumberFormat.formatSize(1023)).isEqualTo("1023 B");
        assertThat(NumberFormat.formatSize(1024)).isEqualTo("1.00 KB");
        assertThat(NumberFormat.formatSize(1536)).isEqualTo("1.50 KB");
    }
}
