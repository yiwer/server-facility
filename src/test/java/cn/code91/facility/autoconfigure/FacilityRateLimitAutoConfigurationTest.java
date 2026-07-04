package cn.code91.facility.autoconfigure;

import cn.code91.facility.ratelimit.FacilityRateLimitProperties;
import cn.code91.facility.ratelimit.RateLimitResult;
import cn.code91.facility.ratelimit.RateLimiter;
import cn.code91.facility.ratelimit.TokenBucketRateLimiter;
import cn.code91.facility.web.ratelimit.RateLimitInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

class FacilityRateLimitAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(FacilityRateLimitAutoConfiguration.class));

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(FacilityRateLimitAutoConfiguration.class));

    @Test
    @DisplayName("默认装配 RateLimiter(TokenBucketRateLimiter 实现)")
    void registersRateLimiterByDefault() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(RateLimiter.class));
    }

    @Test
    @DisplayName("Servlet web 上下文额外装配 RateLimitInterceptor + WebMvcConfigurer")
    void webRunner_registersInterceptor() {
        webRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(RateLimitInterceptor.class);
            assertThat(ctx).hasBean("facilityRateLimitWebMvcConfigurer");
            WebMvcConfigurer configurer =
                ctx.getBean("facilityRateLimitWebMvcConfigurer", WebMvcConfigurer.class);
            // InterceptorRegistry 不暴露内部列表；驱动 addInterceptors 覆盖该方法体,
            // 不抛异常即视为拦截器注册成功。
            configurer.addInterceptors(new InterceptorRegistry());
        });
    }

    @Test
    @DisplayName("非 web 上下文不装配 RateLimitInterceptor(SPI bean 无 web 条件,仍可用)")
    void nonWebRunner_noInterceptorButRateLimiterPresent() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(RateLimiter.class);
            assertThat(ctx).doesNotHaveBean(RateLimitInterceptor.class);
        });
    }

    @Test
    @DisplayName("已存在用户 RateLimiter bean → 不注册 TokenBucketRateLimiter,沿用用户 bean")
    void userRateLimiterBean_backsOff() {
        runner
            .withBean(RateLimiter.class, StubRateLimiter::new)
            .run(ctx -> {
                assertThat(ctx).hasSingleBean(RateLimiter.class);
                assertThat(ctx.getBean(RateLimiter.class))
                    .isInstanceOf(StubRateLimiter.class)
                    .isNotInstanceOf(TokenBucketRateLimiter.class);
            });
    }

    @Test
    @DisplayName("facility.ratelimit.enabled=false → 不装配 RateLimiter")
    void enabledFalse_noBeans() {
        runner
            .withPropertyValues("facility.ratelimit.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(RateLimiter.class));
    }

    @Test
    @DisplayName("properties 绑定:facility.ratelimit.default-capacity 生效")
    void propertiesBind() {
        runner
            .withPropertyValues("facility.ratelimit.default-capacity=5")
            .run(ctx -> assertThat(ctx.getBean(FacilityRateLimitProperties.class).getDefaultCapacity())
                .isEqualTo(5L));
    }

    /** 用户自定义 {@link RateLimiter} 实现,验证装配层为其让位。 */
    private static final class StubRateLimiter implements RateLimiter {
        @Override
        public boolean tryAcquire(String key, int permits) {
            return true;
        }

        @Override
        public RateLimitResult acquire(String key, int permits, long capacity, double permitsPerSecond) {
            return new RateLimitResult(true, Long.MAX_VALUE, 0);
        }
    }
}
