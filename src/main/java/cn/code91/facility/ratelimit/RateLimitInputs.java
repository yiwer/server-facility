package cn.code91.facility.ratelimit;

/** Shared guards for the default implementation and compatibility facade. */
final class RateLimitInputs {
    private RateLimitInputs() { }
    static void keyAndCost(String key, int permits) {
        if (key == null || key.isBlank() || key.length() > 512 || key.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("key must contain 1..512 characters without controls");
        if (permits <= 0) throw new IllegalArgumentException("permits must be > 0");
    }
    static void request(String key, int permits, long capacity, double rate) {
        keyAndCost(key, permits);
        if (capacity <= 0 || permits > capacity) throw new IllegalArgumentException("capacity must be positive and cover permits");
        if (!(rate > 0) || !Double.isFinite(rate)) throw new IllegalArgumentException("permitsPerSecond must be finite and > 0");
    }
}
