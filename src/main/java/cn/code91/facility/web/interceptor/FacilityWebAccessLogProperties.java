package cn.code91.facility.web.interceptor;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "facility.web.access-log")
public class FacilityWebAccessLogProperties {
    private boolean enabled = true;
    private boolean logHeaders = false;
    /** (声明性约束:≥0;绑定不校验——ADR-0013) */
    private long slowThresholdMillis = 1000L;
}
