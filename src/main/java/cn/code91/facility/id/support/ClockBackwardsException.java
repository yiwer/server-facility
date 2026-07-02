package cn.code91.facility.id.support;

public class ClockBackwardsException extends RuntimeException {
    private final long deltaMillis;
    public ClockBackwardsException(long deltaMillis) {
        super("System clock moved backwards by " + deltaMillis + " ms");
        this.deltaMillis = deltaMillis;
    }
    public long getDeltaMillis() { return deltaMillis; }
}
