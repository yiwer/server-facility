import cn.code91.facility.date.DateUtil;
import cn.code91.facility.number.NumberFormat;
import cn.code91.facility.number.NumberUnits;
import cn.code91.facility.pattern.Patterns;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

/** Runs on an ordinary facility jar and JDK alone. Historical literals predate the changed policies. */
public final class ValuePolicyConsumer {
    public static void main(String[] args) throws Exception {
        boolean historical = args.length == 1 && args[0].equals("historical");
        absent("org.springframework.context.ApplicationContext");
        absent("org.junit.jupiter.api.Test");
        absent("org.slf4j.LoggerFactory");
        Locale.setDefault(Locale.Category.FORMAT, Locale.US);
        check(DateUtil.parseDate("2025-02-30", "yyyy-MM-dd").get().equals(LocalDate.of(2025, 2, 28)), "legacy SMART normalization");
        check(DateUtil.parseDate("2025-13-01", "yyyy-MM-dd").isErr(), "invalid month");
        check(DateUtil.format(LocalDate.of(2028, 2, 29), "'yyyy' uuuu-MM-dd").get().equals("yyyy 2028-02-29"), "quoted pattern unchanged");
        check(DateUtil.format(LocalDate.of(2028, 1, 2), "MMM").get().equals("Jan"), "US month");
        check(NumberFormat.formatMoney(new BigDecimal("1234.5")).equals("1,234.50"), "US money");
        check(NumberFormat.format(new BigDecimal("-1.5"), 0).equals("-2"), "HALF_UP display");
        check(NumberFormat.format(null, 2).isEmpty(), "null display");
        Locale.setDefault(Locale.Category.FORMAT, Locale.FRANCE);
        check(DateUtil.format(LocalDate.of(2028, 1, 2), "MMM").get().equals(historical ? "Jan" : "janv."), "declared locale migration");
        check(NumberFormat.formatMoney(new BigDecimal("1234.5")).equals("1\u202f234,50"), "French money");
        check(NumberFormat.formatSize(1536).equals("1,50 KB"), "legacy localized binary display");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        check(DateUtil.longToLocalDateTime(0).equals(LocalDateTime.of(1970, 1, 1, 0, 0)), "UTC epoch");
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        check(DateUtil.longToLocalDateTime(0).equals(LocalDateTime.of(1970, 1, 1, 8, 0)), "Shanghai epoch");
        check(NumberUnits.mmToPx(new BigDecimal("12.7"), 1).equals(BigDecimal.ONE), "legacy positive half");
        check(NumberUnits.mmToPx(new BigDecimal("-12.7"), 1).equals(BigDecimal.ZERO), "legacy negative half rounds toward positive infinity");
        check(NumberUnits.pxToMm(new BigDecimal("3"), 2).equals(new BigDecimal("38.10")), "legacy decimal scale");
        check(NumberUnits.pxToMm(BigDecimal.ONE, 0).equals(BigDecimal.ZERO), "legacy zero DPI");
        check(NumberUnits.mmToPx(null, 96).equals(BigDecimal.ZERO), "legacy null unit");
        check(NumberFormat.parseSize("1.5KB").orElseThrow() == 1536L, "legacy unit multiplier");
        check(NumberFormat.parseSize("-1.9B").orElseThrow() == -1L, "legacy signed truncation");
        check(NumberFormat.parseSize("9007199254740993B").orElseThrow()
                == (historical ? 9_007_199_254_740_992L : 9_007_199_254_740_993L), "declared binary64 precision migration");
        check(historical ? NumberFormat.parseSize("1e100GB").orElseThrow() == Long.MAX_VALUE
                : NumberFormat.parseSize("1e100GB").isEmpty(), "declared overflow migration");
        check(Patterns.matches("2025-02-30", Patterns.DATE), "legacy date regex checks only shape");
        check(Patterns.findAll("b a b", "[ab]").equals(java.util.List.of("b", "a", "b")), "legacy duplicate list order");
        if (!historical) resources();
        System.out.println("VALUE_GOLDENS_PASS historical=" + historical);
    }

    private static void resources() throws Exception {
        Locale.setDefault(Locale.Category.FORMAT, Locale.US);
        var random = new Random(200043);
        for (int i = 0; i < 512; i++) {
            long expected = random.nextLong();
            check(NumberFormat.parseSize(Long.toString(expected) + "B").orElseThrow() == expected, "signed exact integer property");
        }
        churn(0, 2_000);
        long before = retained(), peak = before;
        int threads = ManagementFactory.getThreadMXBean().getThreadCount();
        for (int cycle = 1; cycle <= 5; cycle++) {
            churn(cycle * 2_000, 2_000);
            peak = Math.max(peak, retained());
            check(peak <= before + 8L * 1_024 * 1_024 && peak < 48L * 1_024 * 1_024, "finite retained heap");
        }
        int afterThreads = ManagementFactory.getThreadMXBean().getThreadCount();
        check(afterThreads <= threads + 2, "no growing thread ownership");
        check(Patterns.cacheSize() <= 256, "public retained entry budget");
        Patterns.clearCache();
        check(Patterns.cacheSize() == 0, "explicit cache release");
        System.out.println("VALUE_RESOURCE_PASS seed=200043 properties=512 rounds=10000 heap64m=true retained="
                + before + "->" + peak + " threads=" + threads + "->" + afterThreads);
    }

    private static void churn(int start, int count) {
        for (int i = start; i < start + count; i++) {
            String marker = "item" + i;
            String text = "a".repeat(1_024) + marker;
            check(Patterns.compile(text).matcher(text).matches(), "rotated literal pattern");
            check(Patterns.cacheSize() <= 256, "strict retention during rotation");
            check(DateUtil.format(LocalDate.of(2028, 2, 29), "'" + marker + "' uuuu-MM-dd")
                    .get().equals(marker + " 2028-02-29"), "rotated formatter output");
            check(Patterns.tryCompile("[" + marker).isEmpty(), "syntax failure has no retained entry");
        }
    }

    private static long retained() throws InterruptedException {
        System.gc();
        Thread.sleep(50); // GC observation only; no semantic/concurrency assertion depends on this delay.
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    private static void absent(String name) throws Exception {
        try { Class.forName(name); throw new AssertionError("Unexpected runtime dependency: " + name); }
        catch (ClassNotFoundException expected) { }
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
