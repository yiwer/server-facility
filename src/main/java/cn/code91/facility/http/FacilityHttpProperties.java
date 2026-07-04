package cn.code91.facility.http;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@link cn.code91.facility.autoconfigure.FacilityHttpAutoConfiguration HTTP client 自动装配} 默认参数。
 * <p>
 * 校验策略(ADR-0013):不用 {@code @Validated}——避免强迫消费方引入 Bean Validation
 * provider(无 provider 的默认 Boot 应用会启动即崩);下列声明性约束仅供文档参考,
 * 绑定期不校验。
 * </p>
 *
 * @author yvvb
 * @since 1.0.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "facility.http")
public class FacilityHttpProperties {

    /**
     * 默认 {@link org.springframework.web.client.RestClient} 建立连接的超时时长。默认 5 秒。
     * (声明性约束:&gt;0;绑定不校验——ADR-0013)
     */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * 默认 {@link org.springframework.web.client.RestClient} 等待响应的读超时时长。默认 10 秒。
     * (声明性约束:&gt;0;绑定不校验——ADR-0013)
     */
    private Duration readTimeout = Duration.ofSeconds(10);
}
