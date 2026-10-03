package example.partners;

import java.util.Map;

/** Safe application error; no response body, credential, request URL or nested transport exception. */
public final class PartnerFailure extends RuntimeException {
    public enum Kind { CLIENT_ERROR, SERVER_ERROR, UNEXPECTED_STATUS, CONNECT_FAILED, CONNECT_TIMEOUT,
        RESPONSE_TIMEOUT, BAD_RESPONSE, RESPONSE_TOO_LARGE, CANCELLED, TRANSPORT_FAILED }
    public enum Outcome { NO_EFFECT, UNKNOWN }
    private final Kind kind;
    private final Outcome outcome;
    private final int status;
    private final Map<String, String> headers;
    PartnerFailure(Kind kind, Outcome outcome, int status, Map<String, String> headers) {
        super("Partner request failed: " + kind);
        this.kind = kind; this.outcome = outcome; this.status = status; this.headers = Map.copyOf(headers);
    }
    public Kind kind() { return kind; }
    public Outcome outcome() { return outcome; }
    public int status() { return status; }
    public Map<String, String> headers() { return headers; }
}
