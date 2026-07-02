package cn.code91.facility.date;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DateUtil - format/parse round-trip (RV2-15)")
class DateUtilTest {

    @Test @DisplayName("format 正常")
    void formatOk() {
        assertThat(DateUtil.format(LocalDate.of(2025, 1, 2), "yyyy-MM-dd").get()).isEqualTo("2025-01-02");
    }

    @Test @DisplayName("format null 入参 → err")
    void formatNull() {
        assertThat(DateUtil.format(null, "yyyy-MM-dd").isErr()).isTrue();
    }

    @Test @DisplayName("parseDate 正常")
    void parseOk() {
        assertThat(DateUtil.parseDate("2025-01-02", "yyyy-MM-dd").get()).isEqualTo(LocalDate.of(2025, 1, 2));
    }

    @Test @DisplayName("parseDate 非法字符串 → err")
    void parseBad() {
        assertThat(DateUtil.parseDate("not-a-date", "yyyy-MM-dd").isErr()).isTrue();
    }

    private static java.util.Date at(int y, int mo, int d, int h, int mi) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.clear();
        cal.set(y, mo - 1, d, h, mi, 0);
        return cal.getTime();
    }

    @Test @DisplayName("nowDay 截断到本日零点")
    void nowDay_truncatesToMidnight() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(DateUtil.nowDay());
        assertThat(cal.get(java.util.Calendar.HOUR_OF_DAY)).isZero();
        assertThat(cal.get(java.util.Calendar.MINUTE)).isZero();
        assertThat(cal.get(java.util.Calendar.SECOND)).isZero();
        assertThat(cal.get(java.util.Calendar.MILLISECOND)).isZero();
    }

    @Test @DisplayName("isSameDay 同日不同时刻为 true")
    void isSameDay_sameDayDifferentTimes_true() {
        assertThat(DateUtil.isSameDay(at(2026, 7, 2, 1, 0), at(2026, 7, 2, 23, 59))).isTrue();
    }

    @Test @DisplayName("isSameDay 跨日为 false")
    void isSameDay_differentDays_false() {
        assertThat(DateUtil.isSameDay(at(2026, 7, 2, 23, 59), at(2026, 7, 3, 0, 0))).isFalse();
    }

    @Test @DisplayName("isSameDay null 入参抛 NPE")
    void isSameDay_nullArg_throwsNPE() {
        org.assertj.core.api.Assertions.assertThatNullPointerException()
                .isThrownBy(() -> DateUtil.isSameDay(null, new java.util.Date()));
    }

    @Test @DisplayName("yesterday 为前一日零点")
    void yesterday_isPreviousDayAtMidnight() {
        assertThat(DateUtil.yesterday(at(2026, 7, 2, 15, 30))).isEqualTo(at(2026, 7, 1, 0, 0));
    }

    @Test @DisplayName("tomorrow 为后一日零点")
    void tomorrow_isNextDayAtMidnight() {
        assertThat(DateUtil.tomorrow(at(2026, 7, 2, 15, 30))).isEqualTo(at(2026, 7, 3, 0, 0));
    }

    @Test @DisplayName("yesterday null 入参抛 NPE")
    void yesterday_nullArg_throwsNPE() {
        org.assertj.core.api.Assertions.assertThatNullPointerException()
                .isThrownBy(() -> DateUtil.yesterday((java.util.Date) null)); // cast 消除 yesterday(Date)/yesterday(LocalDate) 重载歧义
    }
}
