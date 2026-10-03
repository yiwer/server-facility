package cn.code91.facility.autoconfigure;

import cn.code91.facility.ratelimit.FacilityRateLimitProperties;
import cn.code91.facility.ratelimit.RateLimiter;
import cn.code91.facility.ratelimit.TokenBucketRateLimiter;
import cn.code91.facility.web.ratelimit.RateLimitInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 限流自动装配(ADR-0014)。
 * <p>
 * {@code facilityRateLimiter}(SPI 默认实现)不带 web 条件——非 web 场景(批处理、
 * 定时任务)可直接注入 {@link RateLimiter} 或经 {@code RateLimiterUtil} 编程式使用;
 * {@link RateLimitInterceptor} 与其 {@link WebMvcConfigurer} 注册仅在 servlet 栈
 * web 应用中装配。三个 bean 均 {@code @ConditionalOnMissingBean}——消费方声明同类型
 * (或同名)bean 即可整体覆盖默认实现。
 * </p>
 *
 * @author yvvb
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(FacilityRateLimitProperties.class)
public class FacilityRateLimitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RateLimiter.class)
    @ConditionalOnProperty(prefix = "facility.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RateLimiter facilityRateLimiter(FacilityRateLimitProperties props) {
        return new TokenBucketRateLimiter(
                props.getDefaultCapacity(), props.getDefaultPermitsPerSecond(), props.getMaxBuckets());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {"jakarta.servlet.Servlet", "org.springframework.web.servlet.config.annotation.WebMvcConfigurer"})
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class ServletConfiguration {
        @Bean
        @ConditionalOnMissingBean
        public RateLimitInterceptor rateLimitInterceptor(org.springframework.beans.factory.ObjectProvider<RateLimiter> rateLimiter, FacilityRateLimitProperties props) {
            return new RateLimitInterceptor(rateLimiter.getIfAvailable(), props.getDefaultCapacity(), props.getDefaultPermitsPerSecond(), props.isFailOpen());
        }

        @Bean("facilityRateLimitWebMvcConfigurer")
        @ConditionalOnMissingBean(name = "facilityRateLimitWebMvcConfigurer")
        @ConditionalOnBean(RateLimitInterceptor.class)
        public WebMvcConfigurer facilityRateLimitWebMvcConfigurer(RateLimitInterceptor interceptor) {
            return new WebMvcConfigurer() {
                @Override
                public void addInterceptors(InterceptorRegistry registry) {
                    registry.addInterceptor(interceptor).order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 20);
                }
            };
        }
    }
}
