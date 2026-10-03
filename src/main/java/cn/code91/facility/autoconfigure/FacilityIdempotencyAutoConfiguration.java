package cn.code91.facility.autoconfigure;

import cn.code91.facility.idempotency.FacilityIdempotencyProperties;
import cn.code91.facility.idempotency.IdempotencyStore;
import cn.code91.facility.idempotency.InMemoryIdempotencyStore;
import cn.code91.facility.web.idempotency.IdempotencyFilter;
import cn.code91.facility.web.idempotency.IdempotencyInterceptor;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Qualified claim 与已授权 HTTP 重放自动装配（ADR-0034/0035）。
 * <p>
 * {@code facilityIdempotencyStore} 不带 web 条件——纯通用能力,非 web 场景(内部 RPC 等)
 * 可直接注入 {@link IdempotencyStore}。{@link IdempotencyInterceptor} 与其
 * {@link FilterRegistrationBean}/{@link WebMvcConfigurer} 注册仅在 servlet 栈 web 应用中
 * 装配——{@link IdempotencyFilter} 默认直通，qualified claim 成功后才开启有界响应副本。
 * HTTP 目标必须提供当前授权 Adapter；关闭 provider/filter 后注解守卫仍拒绝无保护执行。
 * 超限或传输失败不保存且不隐式重新授予资格。捕获与流所有权见 ADR-0028。
 * 全部 bean 均 {@code @ConditionalOnMissingBean}(或
 * {@code @ConditionalOnMissingBean(name = ...)})——消费方声明同类型(或同名)bean 即可
 * 整体覆盖默认实现。
 * </p>
 *
 * @author yvvb
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(FacilityIdempotencyProperties.class)
public class FacilityIdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotencyStore.class)
    @ConditionalOnProperty(prefix = "facility.idempotency", name = "enabled", havingValue = "true", matchIfMissing = true)
    public IdempotencyStore facilityIdempotencyStore(FacilityIdempotencyProperties props) {
        if (props.getMaxResponseBytes() <= 0) throw new IllegalArgumentException("maxResponseBytes must be positive");
        // FHR1: 16 framing bytes and at most two 4096-byte ASCII metadata values.
        return new InMemoryIdempotencyStore(props.getMaxEntries(), Math.addExact(props.getMaxResponseBytes(), 8208),
                props.getMaxStoredReceiptBytes(), java.time.Clock.systemUTC());
    }

    // A method-level condition cannot protect optional types during configuration introspection.
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {"jakarta.servlet.Filter", "org.springframework.web.servlet.config.annotation.WebMvcConfigurer"})
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class ServletConfiguration {
        @Bean
        @ConditionalOnMissingBean
        public IdempotencyInterceptor idempotencyInterceptor(org.springframework.beans.factory.ObjectProvider<IdempotencyStore> store,
                FacilityIdempotencyProperties props,
                org.springframework.beans.factory.ObjectProvider<cn.code91.facility.web.idempotency.IdempotencyAuthorization> authorization) {
            return new IdempotencyInterceptor(store.getIfAvailable(), authorization.getIfAvailable(), props);
        }

        @Bean
        @ConditionalOnMissingBean(IdempotencyFilter.class)
        @ConditionalOnProperty(prefix = "facility.idempotency", name = "enabled", havingValue = "true", matchIfMissing = true)
        public IdempotencyFilter idempotencyFilter(FacilityIdempotencyProperties props) {
            return new IdempotencyFilter(props.getMaxResponseBytes(), props.getMaxRequestBytes());
        }

        @Bean
        @ConditionalOnMissingBean(name = "idempotencyFilterRegistration")
        @ConditionalOnProperty(prefix = "facility.idempotency", name = "enabled", havingValue = "true", matchIfMissing = true)
        public FilterRegistrationBean<IdempotencyFilter> idempotencyFilterRegistration(IdempotencyFilter filter) {
            FilterRegistrationBean<IdempotencyFilter> registration = new FilterRegistrationBean<>();
            registration.setFilter(filter);
            registration.setName("idempotencyFilterRegistration");
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
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
}
