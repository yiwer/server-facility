package cn.code91.facility.date;

import cn.code91.facility.structure.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;

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

    // ==================== longToLocalDateTime / localDateTimeToLong (P7-T3 补测) ====================

    @Test @DisplayName("longToLocalDateTime 按系统时区换算,与 JDK Instant API 独立核验一致")
    void longToLocalDateTime_matchesJdkConversion() {
        long epochMilli = 1_700_000_000_000L;
        LocalDateTime expected = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
        assertThat(DateUtil.longToLocalDateTime(epochMilli)).isEqualTo(expected);
    }

    @Test @DisplayName("localDateTimeToLong 按系统时区换算,与 JDK ZonedDateTime API 独立核验一致")
    void localDateTimeToLong_matchesJdkConversion() {
        LocalDateTime dt = LocalDateTime.of(2026, 7, 2, 10, 30, 0);
        long expected = dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        assertThat(DateUtil.localDateTimeToLong(dt)).isEqualTo(expected);
    }

    @Test @DisplayName("longToLocalDateTime / localDateTimeToLong 互为逆运算")
    void longLocalDateTime_roundTrip() {
        long original = System.currentTimeMillis();
        assertThat(DateUtil.localDateTimeToLong(DateUtil.longToLocalDateTime(original))).isEqualTo(original);
    }

    // ==================== format / formatDateTimeNow / formatDateNow 边界 ====================

    @Test @DisplayName("format 字段不支持(LocalDate 格式化 HH:mm:ss)→ err")
    void format_unsupportedField_err() {
        assertThat(DateUtil.format(LocalDate.of(2025, 1, 2), "HH:mm:ss").isErr()).isTrue();
    }

    @Test @DisplayName("formatDateTimeNow 返回匹配 pattern 结构的字符串")
    void formatDateTimeNow_matchesPatternShape() {
        var result = DateUtil.formatDateTimeNow("yyyy-MM-dd HH:mm:ss");
        assertThat(result.isOk()).isTrue();
        assertThat(result.get()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
    }

    @Test @DisplayName("formatDateNow 返回匹配 pattern 结构的字符串")
    void formatDateNow_matchesPatternShape() {
        var result = DateUtil.formatDateNow("yyyy-MM-dd");
        assertThat(result.isOk()).isTrue();
        assertThat(result.get()).matches("\\d{4}-\\d{2}-\\d{2}");
    }

    // ==================== parseDateTime ====================

    @Test @DisplayName("parseDateTime 正常解析")
    void parseDateTime_ok() {
        assertThat(DateUtil.parseDateTime("2025-01-02 10:30:00", "yyyy-MM-dd HH:mm:ss").get())
                .isEqualTo(LocalDateTime.of(2025, 1, 2, 10, 30, 0));
    }

    @Test @DisplayName("parseDateTime null 字符串 → err")
    void parseDateTime_nullStr_err() {
        assertThat(DateUtil.parseDateTime(null, "yyyy-MM-dd HH:mm:ss").isErr()).isTrue();
    }

    @Test @DisplayName("parseDateTime null pattern → err")
    void parseDateTime_nullPattern_err() {
        assertThat(DateUtil.parseDateTime("2025-01-02 10:30:00", null).isErr()).isTrue();
    }

    @Test @DisplayName("parseDateTime 非法字符串 → err")
    void parseDateTime_badStr_err() {
        assertThat(DateUtil.parseDateTime("not-a-datetime", "yyyy-MM-dd HH:mm:ss").isErr()).isTrue();
    }

    // ==================== parseDate(2-arg) null 边界 + parseDate varargs ====================

    @Test @DisplayName("parseDate(2-arg) null 字符串 → err")
    void parseDate_nullStr_err() {
        assertThat(DateUtil.parseDate(null, "yyyy-MM-dd").isErr()).isTrue();
    }

    @Test @DisplayName("parseDate(2-arg) null pattern → err")
    void parseDate_nullPattern_err() {
        assertThat(DateUtil.parseDate("2025-01-02", (String) null).isErr()).isTrue();
    }

    @Test @DisplayName("parseDate 多格式尝试:后备格式命中")
    void parseDateVarargs_fallbackPatternMatches() {
        assertThat(DateUtil.parseDate("2025/01/02", "yyyy-MM-dd", "yyyy/MM/dd").get())
                .isEqualTo(LocalDate.of(2025, 1, 2));
    }

    @Test @DisplayName("parseDate 多格式全部失败 → err")
    void parseDateVarargs_allFail_err() {
        assertThat(DateUtil.parseDate("garbage", "yyyy-MM-dd", "yyyy/MM/dd").isErr()).isTrue();
    }

    // ==================== isSameDay(LocalDate) / isSameDay(LocalDateTime) ====================

    @Test @DisplayName("isSameDay(LocalDate) 各分支:相同/不同/null")
    void isSameDayLocalDate_branches() {
        assertThat(DateUtil.isSameDay(LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 2))).isTrue();
        assertThat(DateUtil.isSameDay(LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 3))).isFalse();
        assertThat(DateUtil.isSameDay((LocalDate) null, LocalDate.of(2026, 7, 2))).isFalse();
    }

    @Test @DisplayName("isSameDay(LocalDateTime) 各分支:同日不同时刻/跨日/null")
    void isSameDayLocalDateTime_branches() {
        assertThat(DateUtil.isSameDay(
                LocalDateTime.of(2026, 7, 2, 1, 0), LocalDateTime.of(2026, 7, 2, 23, 59))).isTrue();
        assertThat(DateUtil.isSameDay(
                LocalDateTime.of(2026, 7, 2, 23, 59), LocalDateTime.of(2026, 7, 3, 0, 0))).isFalse();
        assertThat(DateUtil.isSameDay((LocalDateTime) null, LocalDateTime.now())).isFalse();
    }

    // ==================== noAfter / noBefore / isBefore / isAfter / isNextDay ====================

    @Test @DisplayName("noAfter 各分支:不晚于/晚于/null")
    void noAfter_branches() {
        assertThat(DateUtil.noAfter(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2))).isTrue();
        assertThat(DateUtil.noAfter(LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 2))).isFalse();
        assertThat(DateUtil.noAfter(null, LocalDate.of(2026, 7, 2))).isFalse();
    }

    @Test @DisplayName("noBefore 各分支:不早于/早于/null")
    void noBefore_branches() {
        assertThat(DateUtil.noBefore(LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 2))).isTrue();
        assertThat(DateUtil.noBefore(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2))).isFalse();
        assertThat(DateUtil.noBefore(null, LocalDate.of(2026, 7, 2))).isFalse();
    }

    @Test @DisplayName("isBefore 各分支:早于/不早于/null")
    void isBefore_branches() {
        assertThat(DateUtil.isBefore(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2))).isTrue();
        assertThat(DateUtil.isBefore(LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 2))).isFalse();
        assertThat(DateUtil.isBefore(null, LocalDate.of(2026, 7, 2))).isFalse();
    }

    @Test @DisplayName("isAfter 各分支:晚于/不晚于/null")
    void isAfter_branches() {
        assertThat(DateUtil.isAfter(LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 2))).isTrue();
        assertThat(DateUtil.isAfter(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2))).isFalse();
        assertThat(DateUtil.isAfter(null, LocalDate.of(2026, 7, 2))).isFalse();
    }

    @Test @DisplayName("isNextDay 各分支:是次日/不是/null")
    void isNextDay_branches() {
        assertThat(DateUtil.isNextDay(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2))).isTrue();
        assertThat(DateUtil.isNextDay(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3))).isFalse();
        assertThat(DateUtil.isNextDay(null, LocalDate.of(2026, 7, 2))).isFalse();
    }

    // ==================== truncateEquals / truncateBefore / truncateAfter ====================

    @Test @DisplayName("truncateEquals 各分支:截断后相等/不相等/null")
    void truncateEquals_branches() {
        LocalDateTime a = LocalDateTime.of(2026, 7, 2, 10, 15, 30);
        LocalDateTime b = LocalDateTime.of(2026, 7, 2, 10, 45, 0);
        assertThat(DateUtil.truncateEquals(a, b, ChronoUnit.HOURS)).isTrue();
        assertThat(DateUtil.truncateEquals(a, b, ChronoUnit.MINUTES)).isFalse();
        assertThat(DateUtil.truncateEquals(null, b, ChronoUnit.HOURS)).isFalse();
    }

    @Test @DisplayName("truncateBefore 各分支:截断后早于/不早于/null")
    void truncateBefore_branches() {
        LocalDateTime a = LocalDateTime.of(2026, 7, 2, 10, 0, 0);
        LocalDateTime b = LocalDateTime.of(2026, 7, 2, 12, 0, 0);
        assertThat(DateUtil.truncateBefore(a, b, ChronoUnit.DAYS)).isFalse();
        assertThat(DateUtil.truncateBefore(a, b, ChronoUnit.HOURS)).isTrue();
        assertThat(DateUtil.truncateBefore(null, b, ChronoUnit.HOURS)).isFalse();
    }

    @Test @DisplayName("truncateAfter 各分支:截断后晚于/不晚于/null")
    void truncateAfter_branches() {
        LocalDateTime a = LocalDateTime.of(2026, 7, 2, 12, 0, 0);
        LocalDateTime b = LocalDateTime.of(2026, 7, 2, 10, 0, 0);
        assertThat(DateUtil.truncateAfter(a, b, ChronoUnit.DAYS)).isFalse();
        assertThat(DateUtil.truncateAfter(a, b, ChronoUnit.HOURS)).isTrue();
        assertThat(DateUtil.truncateAfter(null, b, ChronoUnit.HOURS)).isFalse();
    }

    // ==================== safePlusDays / safeMinusDays / tomorrow(LocalDate) / yesterday(LocalDate) ====================

    @Test @DisplayName("safePlusDays 各分支:正常/MAX_DATE 不变/null 不变")
    void safePlusDays_branches() {
        assertThat(DateUtil.safePlusDays(LocalDate.of(2026, 7, 2), 3)).isEqualTo(LocalDate.of(2026, 7, 5));
        assertThat(DateUtil.safePlusDays(DateUtil.MAX_DATE, 1)).isEqualTo(DateUtil.MAX_DATE);
        assertThat(DateUtil.safePlusDays(null, 1)).isNull();
    }

    @Test @DisplayName("safeMinusDays 各分支:正常/MIN_DATE 不变/null 不变")
    void safeMinusDays_branches() {
        assertThat(DateUtil.safeMinusDays(LocalDate.of(2026, 7, 5), 3)).isEqualTo(LocalDate.of(2026, 7, 2));
        assertThat(DateUtil.safeMinusDays(DateUtil.MIN_DATE, 1)).isEqualTo(DateUtil.MIN_DATE);
        assertThat(DateUtil.safeMinusDays(null, 1)).isNull();
    }

    @Test @DisplayName("tomorrow(LocalDate) 各分支:正常/MAX_DATE 不变/null 不变")
    void tomorrowLocalDate_branches() {
        assertThat(DateUtil.tomorrow(LocalDate.of(2026, 7, 2))).isEqualTo(LocalDate.of(2026, 7, 3));
        assertThat(DateUtil.tomorrow(DateUtil.MAX_DATE)).isEqualTo(DateUtil.MAX_DATE);
        assertThat(DateUtil.tomorrow((LocalDate) null)).isNull();
    }

    @Test @DisplayName("yesterday(LocalDate) 各分支:正常/MIN_DATE 不变/null 不变")
    void yesterdayLocalDate_branches() {
        assertThat(DateUtil.yesterday(LocalDate.of(2026, 7, 2))).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(DateUtil.yesterday(DateUtil.MIN_DATE)).isEqualTo(DateUtil.MIN_DATE);
        assertThat(DateUtil.yesterday((LocalDate) null)).isNull();
    }

    // ==================== Date <-> LocalDate/LocalDateTime 互转 ====================

    @Test @DisplayName("localDateToDate / dateToLocalDate 往返一致,null 返回 null")
    void localDateDateRoundTrip() {
        LocalDate date = LocalDate.of(2026, 7, 2);
        Date converted = DateUtil.localDateToDate(date);
        assertThat(DateUtil.dateToLocalDate(converted)).isEqualTo(date);
        assertThat(DateUtil.localDateToDate(null)).isNull();
        assertThat(DateUtil.dateToLocalDate((Date) null)).isNull();
    }

    @Test @DisplayName("localDateTimeToDate / dateToLocalDateTime 往返一致,null 返回 null")
    void localDateTimeDateRoundTrip() {
        LocalDateTime dt = LocalDateTime.of(2026, 7, 2, 10, 30, 15);
        Date converted = DateUtil.localDateTimeToDate(dt);
        assertThat(DateUtil.dateToLocalDateTime(converted)).isEqualTo(dt);
        assertThat(DateUtil.localDateTimeToDate(null)).isNull();
        assertThat(DateUtil.dateToLocalDateTime(null)).isNull();
    }

    @Test @DisplayName("dateToLocalDate 对 java.sql.Date 走精确分支,不经时区换算")
    void dateToLocalDate_sqlDateBranch() {
        java.sql.Date sqlDate = java.sql.Date.valueOf(LocalDate.of(2026, 7, 2));
        assertThat(DateUtil.dateToLocalDate(sqlDate)).isEqualTo(LocalDate.of(2026, 7, 2));
    }

    // ==================== minOne / maxOne / minMaxTuple ====================

    @Test @DisplayName("minOne / maxOne 各分支:正常/单 null")
    void minMaxOne_branches() {
        LocalDate d1 = LocalDate.of(2026, 7, 1);
        LocalDate d2 = LocalDate.of(2026, 7, 5);
        assertThat(DateUtil.minOne(d1, d2)).isEqualTo(d1);
        assertThat(DateUtil.maxOne(d1, d2)).isEqualTo(d2);
        assertThat(DateUtil.minOne(null, d2)).isEqualTo(d2);
        assertThat(DateUtil.minOne(d1, null)).isEqualTo(d1);
        assertThat(DateUtil.maxOne(null, d2)).isEqualTo(d2);
        assertThat(DateUtil.maxOne(d1, null)).isEqualTo(d1);
    }

    @Test @DisplayName("minMaxTuple 各分支:正常顺序/需交换/含 null/全 null")
    void minMaxTuple_branches() {
        LocalDate d1 = LocalDate.of(2026, 7, 1);
        LocalDate d2 = LocalDate.of(2026, 7, 5);
        assertThat(DateUtil.minMaxTuple(d1, d2)).isEqualTo(Tuple.of(d1, d2));
        assertThat(DateUtil.minMaxTuple(d2, d1)).isEqualTo(Tuple.of(d1, d2));
        assertThat(DateUtil.minMaxTuple(null, d2)).isEqualTo(Tuple.of(d2, d2));
        assertThat(DateUtil.minMaxTuple(d1, null)).isEqualTo(Tuple.of(d1, d1));
        assertThat(DateUtil.minMaxTuple(null, null)).isEqualTo(Tuple.of(null, null));
    }
}
