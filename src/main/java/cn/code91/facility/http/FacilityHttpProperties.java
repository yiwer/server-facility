package cn.code91.facility.http;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@link cn.code91.facility.autoconfigure.FacilityHttpAutoConfiguration HTTP client 自动装配} 默认参数。
 * <p>
 * 不依赖Bean Validation provider。兼容factory使用超时前验证1ms至2147483647ms；
 * 存在Boot管理的builder时保持宿主factory，以下历史超时不覆盖宿主设置（ADR0048）。
 * </p>
 *
 * @author yvvb
 * @since 1.0.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "facility.http")
public class FacilityHttpProperties {

    /** 是否启用 HTTP client 自动装配。默认 {@code true}。 */
    private boolean enabled = true;

    /**
     * 默认 {@link org.springframework.web.client.RestClient} 建立连接的超时时长。默认 5 秒。
     * 仅兼容factory使用，允许1ms至2147483647ms。
     */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * 默认 {@link org.springframework.web.client.RestClient} 等待响应的读超时时长。默认 10 秒。
     * 仅兼容factory使用，允许1ms至2147483647ms。
     */
    private Duration readTimeout = Duration.ofSeconds(10);
}
