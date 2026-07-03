package cn.code91.facility.web;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "facility.web.cors")
public class FacilityWebCorsProperties {
    private boolean enabled = true;
    /**
     * 允许跨域的来源列表。
     * 默认空列表 = 不开 CORS。生产部署须显式列举允许来源（例如 ["https://example.com"]）。
     * 若需开发期全开，配置 beacon.facility.web.cors.allowed-origins=*。
     */
    private List<String> allowedOrigins = List.of();
    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");
    private List<String> allowedHeaders = List.of("*");
    private boolean allowCredentials = false;
    /** (声明性约束:≥0;绑定不校验——ADR-0013) */
    private long maxAge = 3600L;
}
