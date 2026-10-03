package cn.code91.facility.web.ratelimit;

import cn.code91.facility.autoconfigure.FacilityRateLimitAutoConfiguration;
import cn.code91.facility.autoconfigure.FacilityWebAutoConfiguration;
import cn.code91.facility.web.test.EmbeddedServletApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

@Timeout(30)
class RateLimitHttpContractTest {
    @TempDir Path directory;

    @Test void overloadedOperationsHaveIndependentQuotasAtTheActualHttpBoundary() throws Exception {
        try (var app = application(); var client = HttpClient.newHttpClient()) {
            assertThat(get(app, client, "/operation").statusCode()).isEqualTo(200);
            assertThat(get(app, client, "/operation?value=two").statusCode()).isEqualTo(200);
            assertThat(get(app, client, "/operation").statusCode()).isEqualTo(429);
            assertThat(get(app, client, "/operation?value=two").statusCode()).isEqualTo(429);
        }
    }

    @Test void missingAdapterCannotSilentlyExecuteAnAnnotatedOperation() throws Exception {
        try (var app = application("facility.ratelimit.enabled=false"); var client = HttpClient.newHttpClient()) {
            var rejected = get(app, client, "/operation");
            assertThat(rejected.statusCode()).isEqualTo(503);
            assertThat(rejected.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
            assertThat(get(app, client, "/effects").body()).isEqualTo("0");
        }
    }

    @Test void adapterOutageUsesTheSharedSafeServiceUnavailableProtocol() throws Exception {
        try (var app = application(FailingAdapter.class); var client = HttpClient.newHttpClient()) {
            var rejected = get(app, client, "/operation");
            assertThat(rejected.statusCode()).isEqualTo(503);
            assertThat(rejected.body()).doesNotContain("PRIVATE-ADAPTER", "IllegalStateException", "stackTrace");
            assertThat(get(app, client, "/effects").body()).isEqualTo("0");
        }
    }

    private EmbeddedServletApplication application(String... properties) {
        return EmbeddedServletApplication.start(directory, new Class<?>[]{Config.class}, properties);
    }
    private EmbeddedServletApplication application(Class<?> extra, String... properties) {
        return EmbeddedServletApplication.start(directory, new Class<?>[]{Config.class, extra}, properties);
    }
    private static HttpResponse<String> get(EmbeddedServletApplication app, HttpClient client, String path, String... headers) throws Exception {
        var request = HttpRequest.newBuilder(app.uri(path)).timeout(Duration.ofSeconds(5));
        if (headers.length > 0) request.headers(headers);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Configuration(proxyBeanMethods = false) @EnableWebMvc @Import(Endpoints.class)
    @ImportAutoConfiguration({FacilityWebAutoConfiguration.class, FacilityRateLimitAutoConfiguration.class,
            org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration.class})
    static class Config {
        @Bean org.springframework.web.servlet.DispatcherServlet dispatcherServlet() { return new org.springframework.web.servlet.DispatcherServlet(); }
        @Bean org.springframework.boot.webmvc.autoconfigure.DispatcherServletRegistrationBean dispatcherRegistration(org.springframework.web.servlet.DispatcherServlet servlet) {
            var registration = new org.springframework.boot.webmvc.autoconfigure.DispatcherServletRegistrationBean(servlet, "/");
            registration.setAsyncSupported(true); return registration;
        }
    }
    @Configuration(proxyBeanMethods = false) static class FailingAdapter {
        @Bean cn.code91.facility.ratelimit.RateLimiter adapter() {
            return new cn.code91.facility.ratelimit.RateLimiter() {
                public boolean tryAcquire(String key, int permits) { throw new IllegalStateException("PRIVATE-ADAPTER"); }
                public cn.code91.facility.ratelimit.RateLimitResult acquire(String key, int permits, long capacity, double rate) {
                    throw new IllegalStateException("PRIVATE-ADAPTER");
                }
            };
        }
    }
    @RestController static class Endpoints {
        private final java.util.concurrent.atomic.AtomicInteger effects = new java.util.concurrent.atomic.AtomicInteger();
        @GetMapping("/effects") String effects() { return String.valueOf(effects.get()); }
        @GetMapping(value = "/operation", params = "!value") @RateLimit(capacity = 1, permitsPerSecond = 0.001)
        String operation() { effects.incrementAndGet(); return "one"; }
        @GetMapping(value = "/operation", params = "value") @RateLimit(capacity = 1, permitsPerSecond = 0.001)
        String operation(@RequestParam("value") String value) { effects.incrementAndGet(); return "two"; }
    }
}
