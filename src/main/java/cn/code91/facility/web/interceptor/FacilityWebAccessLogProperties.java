package cn.code91.facility.web.interceptor;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "facility.web.access-log")
public class FacilityWebAccessLogProperties {
    private boolean enabled = true;
    /** 慢请求阈值(毫秒):耗时 ≥ 该值的请求日志升 WARN 并标记 slow;0=禁用。(声明性约束:≥0;绑定不校验——ADR-0013) */
    private long slowThresholdMillis = 1000L;
}
