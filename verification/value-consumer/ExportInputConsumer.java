import example.exports.ExportRequests;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Random;

/** Independent application-boundary oracle; no facility or test-framework classes. */
public final class ExportInputConsumer {
    public static void main(String[] args) {
        var requests = new ExportRequests(Clock.fixed(Instant.parse("2028-02-01T00:00:00Z"), ZoneId.of("UTC")),
                ZoneId.of("Asia/Shanghai"), Locale.US);
        var plan = requests.plan("2028-02-29 10:15", null, "1.5");
        check(plan.startsAt().equals(Instant.parse("2028-02-29T02:15:00Z")), "explicit local time to instant");
        check(plan.byteBudget() == 1_572_864L, "1.5 MiB literal bytes");
        check(plan.displayTime().equals("29 Feb 2028 10:15 +08:00"), "explicit display locale and offset");
        for (String invalid : new String[]{"0", "-1", "65", "NaN", "Infinity", "1e100", "1e-999999999", "9".repeat(33)}) {
            rejects(() -> requests.plan("2028-02-29 10:15", null, invalid), "finite positive budget");
        }
        check(requests.plan("2028-02-29 10:15", null, "64").byteBudget() == 67_108_864L, "64 MiB inclusive");
        check(requests.plan("2028-02-29 10:15", null, "0.000001").byteBudget() == 1L, "fractional bytes floor");
        check(requests.plan("2028-02-29 10:15", null, "1.000001").byteBudget() == 1_048_577L, "decimal MiB floor");
        rejects(() -> requests.plan("2028-02-29 10:15", null, "0.000000"), "zero after rounding");
        for (String invalid : new String[]{"2028-02-30 10:15", "2027-02-29 10:15", "2028-04-31 10:15",
                "0000-02-01 10:15", "10000-02-01 10:15", "2028-02-29 24:00", "2028-02-29 10:15:00",
                "SECRET-".repeat(100), "", null}) {
            rejects(() -> requests.plan(invalid, null, "1"), "strict fixed timestamp without input disclosure");
        }
        for (String invalid : new String[]{"2028-02-01 07:59", "2028-02-01 08:00", "2028-03-02 08:01"}) {
            rejects(() -> requests.plan(invalid, null, "1"), "future schedule within30 days");
        }
        check(requests.plan("2028-03-02 08:00", null, "1").startsAt()
                .equals(Instant.parse("2028-03-02T00:00:00Z")), "inclusive30 day horizon");
        check(requests.plan("2028-02-01 08:01", null, "1").startsAt()
                .equals(Instant.parse("2028-02-01T00:01:00Z")), "first representable future minute");
        var spring = new ExportRequests(Clock.fixed(Instant.parse("2028-03-01T00:00:00Z"), ZoneOffset.UTC),
                ZoneId.of("America/New_York"), Locale.US);
        rejects(() -> spring.plan("2028-03-12 02:30", null, "1"), "DST gap must not shift schedule");
        rejects(() -> spring.plan("2028-03-12 02:30", ZoneOffset.ofHours(-5), "1"), "gap offset cannot create local time");
        check(spring.plan("2028-03-12 03:30", null, "1").startsAt()
                .equals(Instant.parse("2028-03-12T07:30:00Z")), "first hour after DST gap");
        var autumn = new ExportRequests(Clock.fixed(Instant.parse("2028-11-01T00:00:00Z"), ZoneOffset.UTC),
                ZoneId.of("America/New_York"), Locale.US);
        rejects(() -> autumn.plan("2028-11-05 01:30", null, "1"), "overlap requires explicit offset");
        rejects(() -> autumn.plan("2028-11-05 01:30", ZoneOffset.UTC, "1"), "overlap requires valid offset");
        rejects(() -> autumn.plan("2028-11-05 03:30", ZoneOffset.ofHours(-4), "1"), "normal time requires valid offset too");
        check(autumn.plan("2028-11-05 01:30", ZoneOffset.ofHours(-4), "1").startsAt()
                .equals(Instant.parse("2028-11-05T05:30:00Z")), "earlier overlap instant");
        check(autumn.plan("2028-11-05 01:30", ZoneOffset.ofHours(-5), "1").startsAt()
                .equals(Instant.parse("2028-11-05T06:30:00Z")), "later overlap instant");
        boundaryRegressions(requests);
        System.out.println("Export input contract PASS");
    }

    private static void boundaryRegressions(ExportRequests requests) {
        for (int length : new int[]{31, 32}) {
            check(requests.plan("2028-02-29 10:15", null, "0".repeat(length - 1) + "1").byteBudget()
                    == 1_048_576L, "bounded leading zeros");
        }
        for (String invalid : new String[]{null, "", " ", "1 MiB", "1MB", "1,5", "+1", "0x1.0p0",
                "1.0000000", "64.000001", "0".repeat(32) + "1", "\u0661", "1\ud800"}) {
            rejects(() -> requests.plan("2028-02-29 10:15", null, invalid), "fixed decimal input");
        }
        var earliest = new ExportRequests(Clock.fixed(Instant.parse("0001-01-01T00:00:00Z"), ZoneOffset.UTC),
                ZoneOffset.UTC, Locale.US);
        check(earliest.plan("0001-01-01 00:01", null, "1").startsAt()
                .equals(Instant.parse("0001-01-01T00:01:00Z")), "minimum year");
        var latest = new ExportRequests(Clock.fixed(Instant.parse("9999-12-01T23:59:00Z"), ZoneOffset.UTC),
                ZoneOffset.UTC, Locale.FRANCE);
        var last = latest.plan("9999-12-31 23:59", null, "1");
        check(last.startsAt().equals(Instant.parse("9999-12-31T23:59:00Z")), "maximum year");
        check(last.displayTime().equals("31 d\u00e9c. 9999 23:59 Z"), "explicit French display");
        var otherClockZone = new ExportRequests(Clock.fixed(Instant.parse("2028-02-01T00:00:00Z"),
                ZoneId.of("Pacific/Auckland")), ZoneId.of("Asia/Shanghai"), Locale.US);
        check(otherClockZone.plan("2028-02-29 10:15", null, "1.5")
                .equals(requests.plan("2028-02-29 10:15", null, "1.5")), "clock zone cannot replace application zone");
        var random = new Random(200043);
        for (int sample = 0; sample < 512; sample++) {
            int quarterMiB = 1 + random.nextInt(256);
            String[] fraction = {".00", ".25", ".50", ".75"};
            String input = quarterMiB / 4 + fraction[quarterMiB % 4];
            check(requests.plan("2028-02-29 10:15", null, input).byteBudget() == quarterMiB * 262_144L,
                    "independent integer quarter-MiB oracle,seed200043");
        }
        for (int i = 0; i < 200; i++) {
            rejects(() -> springGap(), "repeat rejection has no retained state");
            check(requests.plan("2028-02-29 10:15", null, "1").byteBudget() == 1_048_576L, "reuse after rejection");
        }
    }

    private static void springGap() {
        new ExportRequests(Clock.fixed(Instant.parse("2028-03-01T00:00:00Z"), ZoneOffset.UTC),
                ZoneId.of("America/New_York"), Locale.US).plan("2028-03-12 02:30", null, "1");
    }

    static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    static void rejects(Runnable action, String label) {
        try { action.run(); }
        catch (IllegalArgumentException expected) {
            check(expected.getCause() == null, "input failure has no raw cause");
            check(!expected.getMessage().contains("SECRET"), "input failure has no raw value");
            return;
        }
        throw new AssertionError("Expected refusal: " + label);
    }
}
