package example.exports;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Objects;

/** Application-owned input for one scheduled export; it does not start a scheduler or allocate the budget. */
public final class ExportRequests {
    private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private final Clock clock;
    private final ZoneId zone;
    private final DateTimeFormatter display;

    public ExportRequests(Clock clock, ZoneId zone, Locale locale) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zone = Objects.requireNonNull(zone, "zone");
        this.display = DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm XXX", Objects.requireNonNull(locale, "locale"));
    }

    public Plan plan(String localSchedule, ZoneOffset preferredOffset, String capacityMiB) {
        if (capacityMiB == null || capacityMiB.length() > 32 || !capacityMiB.matches("[0-9]+(?:\\.[0-9]{1,6})?")) {
            throw new IllegalArgumentException("Capacity must be a finite decimal MiB value");
        }
        BigDecimal capacity = new BigDecimal(capacityMiB);
        if (capacity.signum() <= 0 || capacity.compareTo(BigDecimal.valueOf(64)) > 0) {
            throw new IllegalArgumentException("Capacity must be positive and at most64 MiB");
        }
        if (localSchedule == null || localSchedule.length() != 16) {
            throw new IllegalArgumentException("Schedule requires a fixed local timestamp");
        }
        LocalDateTime local;
        try {
            local = LocalDateTime.parse(localSchedule, INPUT);
        } catch (DateTimeException invalid) {
            throw new IllegalArgumentException("Schedule is not a valid local timestamp");
        }
        if (local.getYear() < 1 || local.getYear() > 9_999) {
            throw new IllegalArgumentException("Schedule year is outside1..9999");
        }
        var offsets = zone.getRules().getValidOffsets(local);
        if (offsets.isEmpty()) throw new IllegalArgumentException("Schedule falls in a daylight-saving gap");
        if ((offsets.size() > 1 && preferredOffset == null)
                || (preferredOffset != null && !offsets.contains(preferredOffset))) {
            throw new IllegalArgumentException("Schedule requires a valid explicit offset");
        }
        var scheduled = ZonedDateTime.ofLocal(local, zone, preferredOffset);
        Instant now = clock.instant();
        Duration ahead = Duration.between(now, scheduled.toInstant());
        if (ahead.isNegative() || ahead.isZero() || ahead.compareTo(Duration.ofDays(30)) > 0) {
            throw new IllegalArgumentException("Schedule must be in the next30 days");
        }
        long bytes = capacity.multiply(BigDecimal.valueOf(1_048_576)).setScale(0, RoundingMode.FLOOR).longValueExact();
        return new Plan(scheduled.toInstant(), bytes, display.format(scheduled));
    }

    public record Plan(Instant startsAt, long byteBudget, String displayTime) {}
}
