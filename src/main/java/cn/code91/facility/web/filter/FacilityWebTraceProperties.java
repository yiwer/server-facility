package cn.code91.facility.web.filter;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "facility.web.trace")
public class FacilityWebTraceProperties {
    /** Explicit opt-in for the legacy correlation filter; applications own standard Micrometer tracing. */
    private boolean enabled = false;
    private String headerName = "X-Trace-Id";
    private String mdcKey = "traceId";
    /** When true, generate a UUID traceId if the inbound header is absent. */
    private boolean generateIfAbsent = true;
    /** Accept a bounded correlation header, never an authenticated identity. Set false at untrusted boundaries. */
    private boolean acceptInbound = true;
}
