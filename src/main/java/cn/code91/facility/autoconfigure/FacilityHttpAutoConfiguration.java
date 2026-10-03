package cn.code91.facility.autoconfigure;

import cn.code91.facility.http.FacilityHttpProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.time.Duration;

/**
 * HTTP client 自动装配(ADR-0018)。
 * <p>
 * {@code @ConditionalOnClass} 以类全限定名字符串(而非 {@code RestClient.class} 字面量)声明——
 * 与 {@link FacilityCacheAutoConfiguration} 的既定写法一致,spring-web 是 optional 依赖,消费方
 * classpath 可能不存在该类,ASM 字节码读取的条件求值不会触发缺失类的加载。
 * </p>
 * <p>
 * {@code facilityRestClient} 是 seam:{@code @ConditionalOnMissingBean(RestClient.class)}——消费方
 * 声明自己的 {@code RestClient} bean(如替换为 Apache HttpComponents/OkHttp 的
 * {@code ClientHttpRequestFactory},或整体自定义 {@code RestClient})即可整体覆盖默认实例,
 * {@code HttpClients} 门面调用点不变。
 * </p>
 * <p>
 * {@code facility.http.enabled=false} 可整体关闭(F22,与其余四簇开关对称;缺省 true)。
 * </p>
 * <p>
 * 在Boot RestClient装配之后，克隆宿主builder以保留JSON、customizer、观测与factory。
 * facility.http的历史超时属性仅在宿主builder缺席时作用于兼容factory；新应用配置自己的服务Adapter。
 * </p>
 *
 * @author yvvb
 * @since 1.0.0
 */
@AutoConfiguration(afterName = "org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration")
@EnableConfigurationProperties(FacilityHttpProperties.class)
@ConditionalOnClass(name = "org.springframework.web.client.RestClient")
@ConditionalOnProperty(prefix = "facility.http", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FacilityHttpAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RestClient.class)
    public RestClient facilityRestClient(FacilityHttpProperties props, ObjectProvider<RestClient.Builder> builders) {
        RestClient.Builder host = builders.getIfAvailable();
        if (host != null) return host.clone().build();
        return facilityRestClient(props);
    }

    /** Legacy direct construction; application code should inject its Boot-managed builder. */
    @Deprecated(since = "0.1.0", forRemoval = false)
    public RestClient facilityRestClient(FacilityHttpProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMillis(props.getConnectTimeout(), "connect-timeout"));
        factory.setReadTimeout(timeoutMillis(props.getReadTimeout(), "read-timeout"));
        return RestClient.builder().requestFactory(factory).build();
    }

    private static int timeoutMillis(Duration timeout, String property) {
        if (timeout == null || timeout.compareTo(Duration.ofMillis(1)) < 0
                || timeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException("facility.http." + property + " must be between 1ms and 2147483647ms");
        }
        return (int) timeout.toMillis();
    }
}
