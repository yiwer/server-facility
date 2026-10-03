package cn.code91.facility.web.exception;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** HTTP error protocol selection. See ADR-0027 for the explicit compatibility path. */
@Getter
@Setter
@ConfigurationProperties(prefix = "facility.web.exception")
public class FacilityWebExceptionProperties {
    /** @deprecated Retained for configuration binding only; no profile exposes automatic error traces. */
    @Deprecated
    private List<String> includeTraceProfiles = List.of();

    /** Default RFC 9457 errors; false explicitly selects the safe legacy HTTP 200 envelope (429 stays 429). */
    private boolean useProblemDetail = true;
}
