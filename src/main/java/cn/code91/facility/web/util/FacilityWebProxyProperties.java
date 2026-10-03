package cn.code91.facility.web.util;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

/** Explicit proxy ownership; an empty list uses only the numeric Servlet peer. */
@Getter @Setter @ConfigurationProperties("facility.web.proxy")
public class FacilityWebProxyProperties {
    private List<String> trustedProxies = List.of();
}
