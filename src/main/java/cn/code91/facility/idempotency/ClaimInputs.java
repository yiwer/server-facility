package cn.code91.facility.idempotency;

import java.time.Duration;
import java.util.Objects;

final class ClaimInputs {
    private ClaimInputs() { }

    static void text(String value, String name, int maxLength) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maxLength || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(name + " must be nonblank, bounded and contain no control characters");
    }

    static long millis(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative() || duration.isZero() || duration.getNano() % 1_000_000 != 0)
            throw new IllegalArgumentException(name + " must be positive whole milliseconds");
        try {
            return duration.toMillis();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(name + " exceeds the millisecond range", overflow);
        }
    }
}
