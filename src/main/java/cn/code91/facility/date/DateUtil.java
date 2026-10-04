package cn.code91.facility.date;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;
import cn.code91.facility.structure.Tuple;
import jakarta.annotation.Nullable;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;


/**
 * <b>日期时间工具类</b>
 * <p>
 * 提供日期时间的格式化、解析、比较、转换等常用操作。
 * Legacy入口使用调用时的默认FORMAT Locale与SMART解析；不持有动态formatter缓存。
 * 新业务直接拥有显式Locale、ResolverStyle、ZoneId和Clock的java.time政策。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 格式化日期
 * Result<String, ErrorTypeInterface> result = DateUtil.format(LocalDate.now(), "yyyy-MM-dd");
 *
 * // 解析日期字符串
 * Result<LocalDate, ErrorTypeInterface> dateResult = DateUtil.parseDate("2025-01-01", "yyyy-MM-dd");
 *
 * // 日期比较
 * boolean isBefore = DateUtil.isBefore(date1, date2);
 *
 * // 日期转换
 * Date date = DateUtil.localDateToDate(LocalDate.now());
 * }</pre>
 *
 * @author yvvb
 * @since 2025/4/16
 */
public final class DateUtil {

    /**
     * 业务上的最大日期，用于表示无限远的未来
     */
    public static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);

    /**
     * 业务上的最小日期，用于表示最早的历史时间
     */
    public static final LocalDate MIN_DATE = LocalDate.of(1000, 1, 1);

    /**
     * 支持的日期格式列表，用于自动解析多种格式的日期字符串
     */
    public static final String[] SUPPORT_DATE_FORMAT = {"yyyy-MM-dd", "yyyy-M-dd", "yyyy/MM/dd", "yyyy/MM/d", "yyyy/M/d", "yyyy/M/dd", "yyyy年MM月dd日", "yyyyMMdd"};

    /**
     * 私有构造函数，防止实例化
     */
    private DateUtil() {
    }

    /**
     * 获取当前日期的Date对象
     *
     * @return {@link Date} 当前日期的Date对象
     */
    public static Date nowDay() {
        return truncateToDay(new Date());
    }

    /**
     * 判断两个Date对象是否在同一日期
     *
     * @param date1 {@link Date} 日期1
     * @param date2 {@link Date} 日期2
     *
     * @return boolean 是否同一天
     */
    public static boolean isSameDay(Date date1, Date date2) {
        return truncateToDay(date1).equals(truncateToDay(date2));
    }

    /**
     * @param source {@link Long} 毫秒数
     *
     * @return {@link LocalDateTime} 时间
     *
     * @apiNote Long转LocalDateTime工具方法
     */
    public static LocalDateTime longToLocalDateTime(long source) {
        return Instant.ofEpochMilli(source).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * 获取传入Date的前一天的Date对象
     *
     * @param date {@link Date} 传入时间对象
     *
     * @return {@link Date} 前一天的Date对象
     */
    public static Date yesterday(Date date) {
        return truncatedPlusDays(date, -1);
    }

    /**
     * 获取传入Date的后一天的Date对象
     *
     * @param date {@link Date} 传入时间对象
     *
     * @return {@link Date} 后一天的Date对象
     */
    public static Date tomorrow(Date date) {
        return truncatedPlusDays(date, 1);
    }

    /**
     * 截断到当日零点(默认时区)。镜像原 lang3 {@code DateUtils.truncate(date, Calendar.DATE)} 语义;
     * null 抛 NPE(与 lang3 3.20 一致)。
     */
    private static Date truncateToDay(Date date) {
        if (date == null) {
            throw new NullPointerException("date must not be null");
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    /**
     * 截断到零点后加减天数。镜像原 lang3 {@code DateUtils.addDays(DateUtils.truncate(...), n)} 组合。
     */
    private static Date truncatedPlusDays(Date date, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(truncateToDay(date));
        cal.add(Calendar.DAY_OF_MONTH, days);
        return cal.getTime();
    }

    /**
     * @param time {@link LocalDateTime} 时间
     *
     * @return 毫秒数
     *
     * @apiNote LocalDateTime转long工具方法
     */
    public static long localDateTimeToLong(LocalDateTime time) {
        return time.toInstant(ZoneId.systemDefault().getRules().getOffset(time)).toEpochMilli();
    }

    /**
     * @param temporal {@link TemporalAccessor} 需要转化的日期时间
     * @param pattern  {@link String}时间格式
     *
     * @return {@link String} 日期时间字符串
     *
     * @apiNote 格式化日期时间工具方法
     */
    public static Result<String, WrappedError> format(@Nullable TemporalAccessor temporal, @Nullable String pattern) {
        if (temporal == null || pattern == null) {
            return Result.err(WrappedError.of(FacilityErrorType.FORMAT_TEMPORAL_ERROR));
        }
        try {
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(pattern);
            return Result.ok(dateTimeFormatter.format(temporal));
        } catch (DateTimeException | IllegalArgumentException exception) {
            return Result.err(WrappedError.of(FacilityErrorType.FORMAT_TEMPORAL_ERROR, exception));
        }
    }

    /**
     * @param pattern {@link String}时间格式
     *
     * @return {@link  Result} 日期时间字符串封装结果
     *
     * @apiNote 格式化当前时间工具方法
     */
    public static Result<String, WrappedError> formatDateTimeNow(@Nullable String pattern) {
        return format(LocalDateTime.now(), pattern);
    }

    /**
     * @param pattern {@link String}时间格式
     *
     * @return {@link  Result} 日期时间字符串封装结果
     *
     * @apiNote 格式化当前日期工具方法
     */
    public static Result<String, WrappedError> formatDateNow(@Nullable String pattern) {
        return format(LocalDate.now(), pattern);
    }

    /**
     * @param localDateTimeStr {@link String} 日期时间字符串
     * @param pattern          {@link String}时间格式
     *
     * @return {@link LocalDateTime} 解析得到的时间
     *
     * @apiNote 从字符串解析LocalDateTime的工具方法
     */
    public static Result<LocalDateTime, WrappedError> parseDateTime(@Nullable String localDateTimeStr, @Nullable String pattern) {
        if (localDateTimeStr == null || pattern == null) {
            return Result.err(WrappedError.of(FacilityErrorType.PARSE_STR_TO_TEMPORAL_ERROR));
        }
        try {
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(pattern);
            return Result.ok(LocalDateTime.parse(localDateTimeStr, dateTimeFormatter));
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            return Result.err(WrappedError.of(FacilityErrorType.PARSE_STR_TO_TEMPORAL_ERROR, exception));
        }
    }

    /**
     * @param localDateStr {@link String} 日期时间字符串
     * @param pattern      {@link String}时间格式
     *
     * @return {@link LocalDate} 解析得到的日期
     *
     * @apiNote 从字符串解析LocalDate的工具方法
     */
    public static Result<LocalDate, WrappedError> parseDate(@Nullable String localDateStr, @Nullable String pattern) {
        if (localDateStr == null || pattern == null) {
            return Result.err(WrappedError.of(FacilityErrorType.PARSE_STR_TO_TEMPORAL_ERROR));
        }
        try {
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(pattern);
            return Result.ok(LocalDate.parse(localDateStr, dateTimeFormatter));
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            return Result.err(WrappedError.of(FacilityErrorType.PARSE_STR_TO_TEMPORAL_ERROR, exception));
        }
    }


    /**
     * <b>使用多种格式尝试解析日期字符串</b>
     * <p>
     * 依次尝试使用提供的格式解析日期，返回第一个成功的结果。
     * </p>
     *
     * @param localDateStr 日期字符串
     * @param patterns     日期格式数组
     *
     * @return {@link Result} 解析成功返回LocalDate，全部失败返回错误
     */
    public static Result<LocalDate, WrappedError> parseDate(@Nullable String localDateStr, String... patterns) {
        for (String pattern : patterns) {
            Result<LocalDate, WrappedError> result = parseDate(localDateStr, pattern);
            if (result.isOk()) {
                return result;
            }
        }
        return Result.err(WrappedError.of(FacilityErrorType.PARSE_STR_TO_TEMPORAL_ERROR));
    }

    /**
     * @param localDate1 {@link LocalDate} 日期1
     * @param localDate2 {@link LocalDate} 日期2
     *
     * @return 判断结果 {@link  Boolean }
     *
     * @apiNote 判断两个日期是否为同一天(param @ Nullable)
     */
    public static boolean isSameDay(LocalDate localDate1, LocalDate localDate2) {
        if (Objects.isNull(localDate1) || Objects.isNull(localDate2)) {
            return false;
        }
        return localDate1.equals(localDate2);
    }

    /**
     * @param localDate1 {@link LocalDate} 日期1（可为null）
     * @param localDate2 {@link LocalDate} 日期2（可为null）
     *
     * @return 判断结果
     *
     * @apiNote 判断日期1不晚于日期2，任一参数为null返回false
     */
    public static boolean noAfter(@Nullable LocalDate localDate1, @Nullable LocalDate localDate2) {
        if (Objects.isNull(localDate1) || Objects.isNull(localDate2)) {
            return false;
        }
        return !localDate1.isAfter(localDate2);
    }

    /**
     * @param localDate1 {@link LocalDate} 日期1（可为null）
     * @param localDate2 {@link LocalDate} 日期2（可为null）
     *
     * @return 判断结果
     *
     * @apiNote 判断日期1不早于日期2，任一参数为null返回false
     */
    public static boolean noBefore(@Nullable LocalDate localDate1, @Nullable LocalDate localDate2) {
        if (Objects.isNull(localDate1) || Objects.isNull(localDate2)) {
            return false;
        }
        return !localDate1.isBefore(localDate2);
    }


    /**
     * @param date1 {@link LocalDate} 日期1（可为null）
     * @param date2 {@link LocalDate} 日期2（可为null）
     *
     * @return 判断结果
     *
     * @apiNote 判断两个日期，参数一是否在参数二之前，任一参数为null返回false
     */
    public static boolean isBefore(@Nullable LocalDate date1, @Nullable LocalDate date2) {
        if (Objects.isNull(date1) || Objects.isNull(date2)) {
            return false;
        }
        return date1.isBefore(date2);
    }

    /**
     * @param date1 {@link LocalDate} 日期1（可为null）
     * @param date2 {@link LocalDate} 日期2（可为null）
     *
     * @return 判断结果
     *
     * @apiNote 判断两个日期，参数一是否在参数二之后，任一参数为null返回false
     */
    public static boolean isAfter(@Nullable LocalDate date1, @Nullable LocalDate date2) {
        if (Objects.isNull(date1) || Objects.isNull(date2)) {
            return false;
        }
        return date1.isAfter(date2);
    }

    /**
     * <b>判断日期2是否是日期1的下一天</b>
     *
     * @param localDate1 日期1（可为null）
     * @param localDate2 日期2（可为null）
     *
     * @return true-日期2是日期1的下一天，任一参数为null返回false
     */
    public static boolean isNextDay(@Nullable LocalDate localDate1, @Nullable LocalDate localDate2) {
        if (Objects.isNull(localDate1) || Objects.isNull(localDate2)) {
            return false;
        }
        return isSameDay(tomorrow(localDate1), localDate2);
    }

    /**
     * @param dateTime1 {@link LocalDateTime} 时间1（可为null）
     * @param dateTime2 {@link LocalDateTime} 时间2（可为null）
     *
     * @return 判断结果
     *
     * @apiNote 判断两个时间是否为同一天，任一参数为null返回false
     */
    public static boolean isSameDay(@Nullable LocalDateTime dateTime1, @Nullable LocalDateTime dateTime2) {
        if (Objects.isNull(dateTime1) || Objects.isNull(dateTime2)) {
            return false;
        }
        return dateTime1.toLocalDate().equals(dateTime2.toLocalDate());
    }

    /**
     * @param dateTime1 {@link LocalDateTime} 时间1（可为null）
     * @param dateTime2 {@link LocalDateTime} 时间2（可为null）
     * @param unit      {@link ChronoUnit} 单位
     *
     * @return 判断结果
     *
     * @apiNote 判断两个时间截断到指定时间单位是否相同，任一时间参数为null返回false
     */
    public static boolean truncateEquals(@Nullable LocalDateTime dateTime1, @Nullable LocalDateTime dateTime2, ChronoUnit unit) {
        if (Objects.isNull(dateTime1) || Objects.isNull(dateTime2)) {
            return false;
        }
        return dateTime1.truncatedTo(unit).equals(dateTime2.truncatedTo(unit));
    }

    /**
     * @param dateTime1 {@link LocalDateTime} 时间1（可为null）
     * @param dateTime2 {@link LocalDateTime} 时间2（可为null）
     * @param unit      {@link ChronoUnit} 单位
     *
     * @return 判断结果
     *
     * @apiNote 判断两个时间截断到指定时间单位，参数一是否在参数二之前，任一时间参数为null返回false
     */
    public static boolean truncateBefore(@Nullable LocalDateTime dateTime1, @Nullable LocalDateTime dateTime2, ChronoUnit unit) {
        if (Objects.isNull(dateTime1) || Objects.isNull(dateTime2)) {
            return false;
        }
        return dateTime1.truncatedTo(unit).isBefore(dateTime2.truncatedTo(unit));
    }

    /**
     * @param dateTime1 {@link LocalDateTime} 时间1（可为null）
     * @param dateTime2 {@link LocalDateTime} 时间2（可为null）
     * @param unit      {@link ChronoUnit} 单位
     *
     * @return 判断结果
     *
     * @apiNote 判断两个时间截断到指定时间单位，参数一是否在参数二之后，任一时间参数为null返回false
     */
    public static boolean truncateAfter(@Nullable LocalDateTime dateTime1, @Nullable LocalDateTime dateTime2, ChronoUnit unit) {
        if (Objects.isNull(dateTime1) || Objects.isNull(dateTime2)) {
            return false;
        }
        return dateTime1.truncatedTo(unit).isAfter(dateTime2.truncatedTo(unit));
    }


    /**
     * @param date      {@link LocalDate} 操作日期
     * @param daysToAdd {@link Integer} 要增加的日期
     *
     * @return 增加后的日期 {@link  LocalDate}
     *
     * @apiNote 安全地对日期进行添加操作
     */
    public static LocalDate safePlusDays(LocalDate date, Integer daysToAdd) {
        if (Objects.isNull(date) || MAX_DATE.equals(date)) {
            return date;
        }
        return date.plusDays(daysToAdd);
    }

    /**
     * <b>获取指定日期的后一天（LocalDate版本）</b>
     * <p>
     * 如果日期为null或已达到最大日期，则直接返回原值。
     * </p>
     *
     * @param date 日期
     *
     * @return 后一天的日期
     */
    public static LocalDate tomorrow(LocalDate date) {
        if (Objects.isNull(date) || MAX_DATE.equals(date)) {
            return date;
        }
        return date.plusDays(1);
    }

    /**
     * <b>获取指定日期的前一天（LocalDate版本）</b>
     * <p>
     * 如果日期为null或已达到最小日期，则直接返回原值。
     * </p>
     *
     * @param date 日期
     *
     * @return 前一天的日期
     */
    public static LocalDate yesterday(LocalDate date) {
        if (Objects.isNull(date) || MIN_DATE.equals(date)) {
            return date;
        }
        return date.minusDays(1);
    }

    /**
     * @param date        {@link LocalDate} 操作日期
     * @param daysToMinus {@link Integer} 要减少的日期
     *
     * @return 减少后的日期 {@link  LocalDate}
     *
     * @apiNote 安全地对日期进行减少操作
     */
    public static LocalDate safeMinusDays(LocalDate date, Integer daysToMinus) {
        if (Objects.isNull(date) || MIN_DATE.equals(date)) {
            return date;
        }
        return date.minusDays(daysToMinus);
    }

    /**
     * @param localDate {@link LocalDate} 要转换的LocalDate对象
     *
     * @return 转换后的日期对象 {@link Date}
     *
     * @apiNote <b>将LocalDate对象转换为Date</b>
     * <br>从localDate的atStartOfDay方法转到LocalDateTime再加上时区转到Date
     */
    @Nullable
    public static Date localDateToDate(@Nullable LocalDate localDate) {
        if (Objects.isNull(localDate)) {
            return null;
        }
        return Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * @param dateTime {@link LocalDateTime} 要转换的LocalDateTime对象
     *
     * @return 转换后的日期对象 {@link Date}
     *
     * @apiNote <b>将LocalDateTime对象转换为Date</b>
     * <br>LocalDateTime再加上时区转为ZoneDateTime,再用toInstant转到Date
     */
    @Nullable
    public static Date localDateTimeToDate(@Nullable LocalDateTime dateTime) {
        if (Objects.isNull(dateTime)) {
            return null;
        }
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * @param date {@link Date} 要转换的Date对象
     *
     * @return 转换后的日期对象 {@link LocalDateTime}
     *
     * @apiNote <b>将Date对象转换为LocalDateTime对象</b>
     */
    @Nullable
    public static LocalDateTime dateToLocalDateTime(@Nullable Date date) {
        if (Objects.isNull(date)) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * @param date {@link Date} 要转换的Date对象
     *
     * @return 转换后的日期对象 {@link LocalDate}
     *
     * @apiNote <b>将Date对象转换为LocalDate对象</b>
     * <br/> 注意会截断精度到DAY
     */
    @Nullable
    public static LocalDate dateToLocalDate(@Nullable Date date) {
        if (Objects.isNull(date)) {
            return null;
        }
        if (date instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /**
     * <b>获取两个日期中较早的一个</b>
     * <p>
     * null值会被忽略，返回非null的日期。
     * </p>
     *
     * @param date1 日期1（可为null）
     * @param date2 日期2（可为null）
     *
     * @return 较早的日期
     */
    public static LocalDate minOne(LocalDate date1, LocalDate date2) {
        if (Objects.isNull(date1)) {
            return date2;
        } else if (Objects.isNull(date2)) {
            return date1;
        }
        return date1.isAfter(date2) ? date2 : date1;
    }

    /**
     * <b>获取两个日期中较晚的一个</b>
     * <p>
     * null值会被忽略，返回非null的日期。
     * </p>
     *
     * @param date1 日期1（可为null）
     * @param date2 日期2（可为null）
     *
     * @return 较晚的日期
     */
    public static LocalDate maxOne(LocalDate date1, LocalDate date2) {
        if (Objects.isNull(date1)) {
            return date2;
        } else if (Objects.isNull(date2)) {
            return date1;
        }
        return date1.isAfter(date2) ? date1 : date2;
    }

    /**
     * <b>将两个日期排序为(较早, 较晚)的元组</b>
     * <p>
     * 返回一个Tuple，value1为较早的日期，value2为较晚的日期。
     * </p>
     *
     * @param date1 日期1
     * @param date2 日期2
     *
     * @return 排序后的日期元组
     */
    public static Tuple<LocalDate, LocalDate> minMaxTuple(@Nullable LocalDate date1, @Nullable LocalDate date2) {
        if (Objects.isNull(date1) && Objects.isNull(date2)) {
            return Tuple.of(null, null);
        } else if (Objects.isNull(date1)) {
            return Tuple.of(date2, date2);
        } else if (Objects.isNull(date2)) {
            return Tuple.of(date1, date1);
        }
        return date1.isAfter(date2) ? Tuple.of(date2, date1) : Tuple.of(date1, date2);
    }
}
