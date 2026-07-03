package cn.code91.facility.web.filter;

public class PayloadTooLargeException extends RuntimeException {
    private final long actualBytes;
    private final long limitBytes;
    public PayloadTooLargeException(long actualBytes, long limitBytes) {
        super("Request body " + actualBytes + " bytes exceeds limit " + limitBytes + " bytes");
        this.actualBytes = actualBytes;
        this.limitBytes = limitBytes;
    }
    public long getActualBytes() { return actualBytes; }
    public long getLimitBytes() { return limitBytes; }
}
