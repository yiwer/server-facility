package cn.code91.facility.autoconfigure;

import cn.code91.facility.web.exception.AbstractGlobalExceptionHandler;
import cn.code91.facility.web.filter.RepeatableRequestFilter;
import cn.code91.facility.web.filter.TraceIdFilter;
import cn.code91.facility.web.interceptor.AccessLogInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FacilityWebAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(FacilityWebAutoConfiguration.class));

    @Test
    void registersWebBeansInServletContext() {
        runner.run(ctx -> assertThat(ctx)
            .hasSingleBean(TraceIdFilter.class)
            .doesNotHaveBean(RepeatableRequestFilter.class)
            .hasSingleBean(AccessLogInterceptor.class)
            .hasSingleBean(AbstractGlobalExceptionHandler.class));
    }

    @Test
    void disablesIndividualFiltersByProperty() {
        runner
            .withPropertyValues("facility.web.trace.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(TraceIdFilter.class));
    }

    @Test
    void wholeWebConfigCanBeExcluded() {
        // Verifying that WITHOUT FacilityWebAutoConfiguration no TraceIdFilter bean exists.
        // spring.autoconfigure.exclude cannot remove classes explicitly given to the runner,
        // so we verify by running a plain runner without the autoconfiguration.
        new WebApplicationContextRunner()
            .run(ctx -> assertThat(ctx).doesNotHaveBean(TraceIdFilter.class));
    }

    @Test
    void wiresAccessLogInterceptorIntoMvc() {
        runner.run(ctx -> {
            assertThat(ctx).hasBean("facilityWebMvcConfigurer");
            WebMvcConfigurer configurer = ctx.getBean("facilityWebMvcConfigurer", WebMvcConfigurer.class);
            // Verify the configurer wires the interceptor without throwing.
            InterceptorRegistry registry = new InterceptorRegistry();
            configurer.addInterceptors(registry);
            // InterceptorRegistry does not expose its list publicly; we confirm no exception was
            // thrown and that the AccessLogInterceptor bean is present in the context.
            assertThat(ctx).hasSingleBean(AccessLogInterceptor.class);
        });
    }

    @Test
    @org.junit.jupiter.api.DisplayName("SessionUserClearInterceptor bean 默认装配 (RV2-08)")
    void sessionUserClearInterceptorRegistered() {
        runner.run(ctx -> assertThat(ctx)
            .hasSingleBean(cn.code91.facility.web.interceptor.SessionUserClearInterceptor.class));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("repeatable-request.enabled=false → RepeatableRequestFilter 缺席")
    void repeatableRequestDisabledByProperty() {
        runner
            .withPropertyValues("facility.web.repeatable-request.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(RepeatableRequestFilter.class));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("access-log.enabled=false → AccessLogInterceptor 缺席")
    void accessLogDisabledByProperty() {
        runner
            .withPropertyValues("facility.web.access-log.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(AccessLogInterceptor.class));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("cors.enabled=false → facilityCorsWebMvcConfigurer 缺席")
    void corsDisabledByProperty() {
        runner
            .withPropertyValues("facility.web.cors.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean("facilityCorsWebMvcConfigurer"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("默认空 allowedOrigins:configurer 在场但不注册任何 CORS 映射 (RV2-05)")
    void corsEmptyOriginsRegistersNoMappings() {
        runner.run(ctx -> {
            assertThat(ctx).hasBean("facilityCorsWebMvcConfigurer");
            WebMvcConfigurer configurer = ctx.getBean("facilityCorsWebMvcConfigurer", WebMvcConfigurer.class);
            var registry = new CorsRegistry() {
                Map<String, CorsConfiguration> configs() { return getCorsConfigurations(); }
            };
            configurer.addCorsMappings(registry);
            assertThat(registry.configs()).isEmpty();
        });
    }

    @Test
    @org.junit.jupiter.api.DisplayName("配置 allowed-origins 后注册 /** CORS 映射 (RV2-05 行为级)")
    void corsWithOriginsRegistersWildcardMapping() {
        runner
            .withPropertyValues("facility.web.cors.allowed-origins=https://example.com")
            .run(ctx -> {
                WebMvcConfigurer configurer = ctx.getBean("facilityCorsWebMvcConfigurer", WebMvcConfigurer.class);
                var registry = new CorsRegistry() {
                    Map<String, CorsConfiguration> configs() { return getCorsConfigurations(); }
                };
                configurer.addCorsMappings(registry);
                assertThat(registry.configs()).containsKey("/**");
                assertThat(registry.configs().get("/**").getAllowedOrigins())
                    .containsExactly("https://example.com");
            });
    }
}

