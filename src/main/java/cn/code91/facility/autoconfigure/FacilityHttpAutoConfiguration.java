package cn.code91.facility.autoconfigure;

import cn.code91.facility.http.FacilityHttpProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

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
 *
 * @author yvvb
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(FacilityHttpProperties.class)
@ConditionalOnClass(name = "org.springframework.web.client.RestClient")
public class FacilityHttpAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RestClient.class)
    public RestClient facilityRestClient(FacilityHttpProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getConnectTimeout());
        factory.setReadTimeout(props.getReadTimeout());
        return RestClient.builder().requestFactory(factory).build();
    }
}
