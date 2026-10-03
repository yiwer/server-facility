package cn.code91.facility.autoconfigure;

import cn.code91.facility.web.FacilityWebCorsProperties;
import cn.code91.facility.web.exception.AbstractGlobalExceptionHandler;
import cn.code91.facility.web.exception.DefaultGlobalExceptionHandler;
import cn.code91.facility.web.exception.FacilityWebExceptionProperties;
import cn.code91.facility.web.exception.FacilityHttpErrors;
import cn.code91.facility.web.exception.FacilityHttpErrorFilter;
import cn.code91.facility.web.filter.FacilityWebRepeatableRequestProperties;
import cn.code91.facility.web.filter.FacilityWebTraceProperties;
import cn.code91.facility.web.filter.RepeatableRequestFilter;
import cn.code91.facility.web.filter.TraceIdFilter;
import cn.code91.facility.web.interceptor.AccessLogInterceptor;
import cn.code91.facility.web.interceptor.FacilityWebAccessLogProperties;
import jakarta.servlet.Servlet;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@ConditionalOnClass(Servlet.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties({
    FacilityWebTraceProperties.class,
    FacilityWebRepeatableRequestProperties.class,
    FacilityWebAccessLogProperties.class,
    FacilityWebExceptionProperties.class,
    FacilityWebCorsProperties.class
})
public class FacilityWebAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "facility.web.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
    public TraceIdFilter traceIdFilter(FacilityWebTraceProperties props) {
        return new TraceIdFilter(props);
    }

    @Bean
    @ConditionalOnProperty(prefix = "facility.web.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration(TraceIdFilter traceIdFilter) {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(traceIdFilter);
        registration.addUrlPatterns("/*");
        registration.setName("traceIdFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    @ConditionalOnProperty(prefix = "facility.web.repeatable-request", name = "enabled", havingValue = "true")
    public RepeatableRequestFilter repeatableRequestFilter(FacilityWebRepeatableRequestProperties props) {
        return new RepeatableRequestFilter(props);
    }

    @Bean
    @ConditionalOnProperty(prefix = "facility.web.repeatable-request", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<RepeatableRequestFilter> repeatableRequestFilterRegistration(RepeatableRequestFilter repeatableRequestFilter) {
        FilterRegistrationBean<RepeatableRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(repeatableRequestFilter);
        registration.addUrlPatterns("/*");
        registration.setName("repeatableRequestFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return registration;
    }

    @Bean
    @ConditionalOnProperty(prefix = "facility.web.access-log", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AccessLogInterceptor accessLogInterceptor(FacilityWebAccessLogProperties props) {
        return new AccessLogInterceptor(props);
    }

    @Bean
    @ConditionalOnMissingBean(name = "facilityWebMvcConfigurer")
    @ConditionalOnBean(AccessLogInterceptor.class)
    public WebMvcConfigurer facilityWebMvcConfigurer(AccessLogInterceptor accessLogInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(accessLogInterceptor);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(name = "facilityCorsWebMvcConfigurer")
    @ConditionalOnProperty(prefix = "facility.web.cors", name = "enabled", havingValue = "true", matchIfMissing = true)
    public WebMvcConfigurer facilityCorsWebMvcConfigurer(FacilityWebCorsProperties props) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                if (props.getAllowedOrigins() == null || props.getAllowedOrigins().isEmpty()) {
                    // RP-05：空列表 = 不开 CORS。安全默认，生产部署须显式列举允许来源。
                    return;
                }
                CorsRegistration reg = registry.addMapping("/**")
                    .allowedOrigins(props.getAllowedOrigins().toArray(new String[0]))
                    .allowedMethods(props.getAllowedMethods().toArray(new String[0]))
                    .allowedHeaders(props.getAllowedHeaders().toArray(new String[0]))
                    .allowCredentials(props.isAllowCredentials())
                    .maxAge(props.getMaxAge());
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(AbstractGlobalExceptionHandler.class)
    public AbstractGlobalExceptionHandler defaultGlobalExceptionHandler(
            FacilityHttpErrors errors) {
        return new DefaultGlobalExceptionHandler(errors);
    }

    @Bean
    @ConditionalOnMissingBean(FacilityHttpErrors.class)
    public FacilityHttpErrors facilityHttpErrors(FacilityWebExceptionProperties props, FacilityWebTraceProperties trace,
            org.springframework.context.ApplicationContext context,
            org.springframework.beans.factory.ObjectProvider<com.fasterxml.jackson.databind.ObjectMapper> mappers) {
        return new FacilityHttpErrors(props, context, mappers.getIfAvailable(
                () -> org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build()), trace);
    }

    @Bean
    @ConditionalOnMissingBean(name = "facilityHttpErrorFilterRegistration")
    public FilterRegistrationBean<FacilityHttpErrorFilter> facilityHttpErrorFilterRegistration(FacilityHttpErrors errors) {
        var registration = new FilterRegistrationBean<>(new FacilityHttpErrorFilter(errors));
        registration.setName("facilityHttpErrorFilter");
        registration.setDispatcherTypes(jakarta.servlet.DispatcherType.REQUEST, jakarta.servlet.DispatcherType.ASYNC,
                jakarta.servlet.DispatcherType.ERROR);
        registration.setAsyncSupported(true);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    public org.springframework.boot.web.server.ErrorPageRegistrar facilityErrorPageFallback(Environment environment) {
        return new ErrorPageFallback(environment.getProperty("server.error.path", "/error"));
    }

    // Install the fallback first. Boot and host registrars can still choose their own error destinations.
    private record ErrorPageFallback(String path) implements org.springframework.boot.web.server.ErrorPageRegistrar, Ordered {
        @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
        @Override public void registerErrorPages(org.springframework.boot.web.server.ErrorPageRegistry registry) {
            registry.addErrorPages(new org.springframework.boot.web.server.ErrorPage(path));
        }
    }

    @Bean
    public cn.code91.facility.web.interceptor.SessionUserClearInterceptor sessionUserClearInterceptor() {
        return new cn.code91.facility.web.interceptor.SessionUserClearInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(name = "facilitySessionWebMvcConfigurer")
    @ConditionalOnBean(cn.code91.facility.web.interceptor.SessionUserClearInterceptor.class)
    public WebMvcConfigurer facilitySessionWebMvcConfigurer(
            cn.code91.facility.web.interceptor.SessionUserClearInterceptor sessionUserClearInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(sessionUserClearInterceptor);
            }
        };
    }
}
