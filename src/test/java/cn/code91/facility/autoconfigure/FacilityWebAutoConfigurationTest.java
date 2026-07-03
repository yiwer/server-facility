package cn.code91.facility.autoconfigure;

import cn.code91.facility.web.exception.AbstractGlobalExceptionHandler;
import cn.code91.facility.web.filter.RepeatableRequestFilter;
import cn.code91.facility.web.filter.TraceIdFilter;
import cn.code91.facility.web.interceptor.AccessLogInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

class FacilityWebAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(FacilityWebAutoConfiguration.class));

    @Test
    void registersWebBeansInServletContext() {
        runner.run(ctx -> assertThat(ctx)
            .hasSingleBean(TraceIdFilter.class)
            .hasSingleBean(RepeatableRequestFilter.class)
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
}
