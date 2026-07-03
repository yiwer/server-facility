package cn.code91.facility.web.filter;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "facility.web.repeatable-request")
public class FacilityWebRepeatableRequestProperties {
    private boolean enabled = true;

    /**
     * Reject requests whose body exceeds this size (bytes). 0 = no limit (NOT recommended).
     * (声明性约束:≥0;绑定不校验——ADR-0013)
     */
    private long maxBodyBytes = 10L * 1024 * 1024;

    /** Only wrap requests with one of these Content-Type prefixes (lowercased match). */
    private List<String> includeContentTypes = List.of(
        "application/json", "application/xml", "text/"
    );

    /** Skip wrapping for requests matching these Ant-style paths. */
    private List<String> excludePaths = List.of("/actuator/**");
}
