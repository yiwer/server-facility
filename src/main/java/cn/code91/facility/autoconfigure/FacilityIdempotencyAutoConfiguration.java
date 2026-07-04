package cn.code91.facility.autoconfigure;

import cn.code91.facility.idempotency.FacilityIdempotencyProperties;
import cn.code91.facility.idempotency.IdempotencyStore;
import cn.code91.facility.idempotency.InMemoryIdempotencyStore;
import cn.code91.facility.web.idempotency.IdempotencyFilter;
import cn.code91.facility.web.idempotency.IdempotencyInterceptor;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 幂等自动装配(ADR-0017)。
 * <p>
 * {@code facilityIdempotencyStore} 不带 web 条件——纯通用能力,非 web 场景(内部 RPC 等)
 * 可直接注入 {@link IdempotencyStore}。{@link IdempotencyInterceptor} 与其
 * {@link FilterRegistrationBean}/{@link WebMvcConfigurer} 注册仅在 servlet 栈 web 应用中
 * 装配——{@link IdempotencyFilter} 把响应包装为 {@code ContentCachingResponseWrapper}
 * 使 {@link IdempotencyInterceptor#afterCompletion} 能读到完整响应体,两者职责分工与
 * 状态机细节见 ADR-0017。全部 bean 均 {@code @ConditionalOnMissingBean}(或
 * {@code @ConditionalOnMissingBean(name = ...)})——消费方声明同类型(或同名)bean 即可
 * 整体覆盖默认实现。
 * </p>
 *
 * @author yvvb
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(FacilityIdempotencyProperties.class)
@ConditionalOnProperty(prefix = "facility.idempotency", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FacilityIdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotencyStore.class)
    public IdempotencyStore facilityIdempotencyStore(FacilityIdempotencyProperties props) {
        return new InMemoryIdempotencyStore(props.getMaxEntries());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public IdempotencyInterceptor idempotencyInterceptor(IdempotencyStore store, FacilityIdempotencyProperties props) {
        return new IdempotencyInterceptor(store, props.getDefaultTtl().toMillis());
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public FilterRegistrationBean<IdempotencyFilter> idempotencyFilterRegistration() {
        FilterRegistrationBean<IdempotencyFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new IdempotencyFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean("facilityIdempotencyWebMvcConfigurer")
    @ConditionalOnMissingBean(name = "facilityIdempotencyWebMvcConfigurer")
    @ConditionalOnBean(IdempotencyInterceptor.class)
    public WebMvcConfigurer facilityIdempotencyWebMvcConfigurer(IdempotencyInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor);
            }
        };
    }
}
