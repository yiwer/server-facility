package cn.code91.facility.autoconfigure;

import cn.code91.facility.idempotency.FacilityIdempotencyProperties;
import cn.code91.facility.idempotency.IdempotencyRecord;
import cn.code91.facility.idempotency.IdempotencyStore;
import cn.code91.facility.idempotency.InMemoryIdempotencyStore;
import cn.code91.facility.web.idempotency.IdempotencyFilter;
import cn.code91.facility.web.idempotency.IdempotencyInterceptor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FacilityIdempotencyAutoConfiguration - 幂等装配(默认 InMemoryIdempotencyStore + web 拦截器/Filter,可被用户 bean 覆盖)")
class FacilityIdempotencyAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(FacilityIdempotencyAutoConfiguration.class));

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(FacilityIdempotencyAutoConfiguration.class));

    @Test
    @DisplayName("默认装配 IdempotencyStore(InMemoryIdempotencyStore 实现)")
    void registersStoreByDefault() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(IdempotencyStore.class);
            assertThat(ctx.getBean(IdempotencyStore.class)).isInstanceOf(InMemoryIdempotencyStore.class);
        });
    }

    @Test
    @DisplayName("Servlet web 上下文额外装配 IdempotencyInterceptor + FilterRegistrationBean<IdempotencyFilter> + WebMvcConfigurer")
    void webRunner_registersInterceptorAndFilter() {
        webRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(IdempotencyInterceptor.class);
            assertThat(ctx).hasSingleBean(FilterRegistrationBean.class);
            assertThat(ctx).hasBean("facilityIdempotencyWebMvcConfigurer");

            WebMvcConfigurer configurer =
                ctx.getBean("facilityIdempotencyWebMvcConfigurer", WebMvcConfigurer.class);
            // InterceptorRegistry 不暴露内部列表；驱动 addInterceptors 覆盖该方法体,
            // 不抛异常即视为拦截器注册成功。
            configurer.addInterceptors(new InterceptorRegistry());

            FilterRegistrationBean<?> registration = ctx.getBean(FilterRegistrationBean.class);
            assertThat(registration.getFilter()).isInstanceOf(IdempotencyFilter.class);
            assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 3);
            assertThat(registration.getUrlPatterns()).containsExactly("/*");
        });
    }

    @Test
    @DisplayName("非 web 上下文不装配 IdempotencyInterceptor(SPI bean 无 web 条件,仍可用)")
    void nonWebRunner_noInterceptorButStorePresent() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(IdempotencyStore.class);
            assertThat(ctx).doesNotHaveBean(IdempotencyInterceptor.class);
            assertThat(ctx).doesNotHaveBean(FilterRegistrationBean.class);
        });
    }

    @Test
    @DisplayName("已存在用户 IdempotencyStore bean → 不注册 InMemoryIdempotencyStore,沿用用户 bean")
    void userIdempotencyStoreBean_backsOff() {
        runner
            .withBean(IdempotencyStore.class, StubIdempotencyStore::new)
            .run(ctx -> {
                assertThat(ctx).hasSingleBean(IdempotencyStore.class);
                assertThat(ctx.getBean(IdempotencyStore.class))
                    .isInstanceOf(StubIdempotencyStore.class)
                    .isNotInstanceOf(InMemoryIdempotencyStore.class);
            });
    }

    @Test
    @DisplayName("facility.idempotency.enabled=false → 不装配 IdempotencyStore")
    void enabledFalse_noBean() {
        runner
            .withPropertyValues("facility.idempotency.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(IdempotencyStore.class));
    }

    @Test
    @DisplayName("properties 绑定:facility.idempotency.max-entries 生效")
    void propertiesBind() {
        runner
            .withPropertyValues("facility.idempotency.max-entries=5")
            .run(ctx -> assertThat(ctx.getBean(FacilityIdempotencyProperties.class).getMaxEntries())
                .isEqualTo(5));
    }

    /** 用户自定义 {@link IdempotencyStore} 实现,验证装配层为其让位。 */
    private static final class StubIdempotencyStore implements IdempotencyStore {
        @Override
        public boolean tryBegin(String key, long ttlMillis) {
            return true;
        }

        @Override
        public Optional<IdempotencyRecord> find(String key) {
            return Optional.empty();
        }

        @Override
        public void complete(String key, IdempotencyRecord done) {
        }
    }
}

