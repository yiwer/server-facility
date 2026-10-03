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

    @Test void explicitFailOpenCoversAbsenceAndOutageButNeverInvalidAnnotationCosts() throws Exception {
        try (var app = application("facility.ratelimit.enabled=false", "facility.ratelimit.fail-open=true"); var client = HttpClient.newHttpClient()) {
            assertThat(get(app, client, "/operation").statusCode()).isEqualTo(200);
            assertThat(get(app, client, "/invalid-cost").statusCode()).isEqualTo(500);
            assertThat(get(app, client, "/effects").body()).isEqualTo("1");
        }
        try (var app = application(FailingAdapter.class, "facility.ratelimit.fail-open=true"); var client = HttpClient.newHttpClient()) {
            assertThat(get(app, client, "/operation").statusCode()).isEqualTo(200);
            assertThat(get(app, client, "/invalid-cost").statusCode()).isEqualTo(500);
            assertThat(get(app, client, "/effects").body()).isEqualTo("1");
        }
    }

    @Test void principalQuotaUsesOnlyTheHostPrincipalAndKeepsAuthenticatedActorsSeparate() throws Exception {
        try (var app = application(TestAuthentication.class); var client = HttpClient.newHttpClient()) {
            assertThat(get(app, client, "/principal", "X-User", "alice").statusCode()).isEqualTo(403);
            assertThat(get(app, client, "/principal", "Fixture-Principal", "alice").statusCode()).isEqualTo(200);
            assertThat(get(app, client, "/principal", "Fixture-Principal", "bob").statusCode()).isEqualTo(200);
            assertThat(get(app, client, "/principal", "Fixture-Principal", "alice", "X-Forwarded-For", "192.0.2.99").statusCode()).isEqualTo(429);
            assertThat(get(app, client, "/effects").body()).isEqualTo("2");
        }
    }

    @Test void asyncRedispatchDoesNotChargeTheSameProtectedOperationTwice() throws Exception {
        try (var app = application(); var client = HttpClient.newHttpClient()) {
            assertThat(get(app, client, "/async").statusCode()).isEqualTo(200);
            assertThat(get(app, client, "/async").statusCode()).isEqualTo(429);
            assertThat(get(app, client, "/effects").body()).isEqualTo("1");
        }
    }

    @Test void blankAliasesAreInvalidEvenWithExplicitFallback() throws Exception {
        try (var app = application("facility.ratelimit.fail-open=true"); var client = HttpClient.newHttpClient()) {
            assertThat(get(app, client, "/blank-alias").statusCode()).isEqualTo(500);
            assertThat(get(app, client, "/effects").body()).isEqualTo("0");
        }
    }

    @Test void entranceQuotaPrecedesIdempotencyAndChargesEachReplayAttempt() throws Exception {
        try (var app = application(Replay.class); var client = HttpClient.newHttpClient()) {
            var first = get(app, client, "/replay", "Idempotency-Key", "same-command");
            assertThat(first.statusCode()).isEqualTo(200);
            assertThat(get(app, client, "/replay", "Idempotency-Key", "same-command").body()).isEqualTo(first.body());
            assertThat(get(app, client, "/replay", "Idempotency-Key", "same-command").statusCode()).isEqualTo(429);
            assertThat(get(app, client, "/effects").body()).isEqualTo("1");
        }
    }

    @Test void forwardingHeadersCannotChangePeerQuotaWithoutAnExplicitTrustedProxy() throws Exception {
        try (var app = application(); var client = HttpClient.newHttpClient()) {
            assertThat(get(app, client, "/operation", "X-Forwarded-For", "192.0.2.1").statusCode()).isEqualTo(200);
            assertThat(get(app, client, "/operation", "X-Forwarded-For", "192.0.2.2").statusCode()).isEqualTo(429);
        }
        try (var app = application("facility.web.proxy.trusted-proxies[0]=127.0.0.1/32"); var client = HttpClient.newHttpClient()) {
            assertThat(get(app, client, "/operation", "X-Forwarded-For", "192.0.2.1").statusCode()).isEqualTo(200);
            assertThat(get(app, client, "/operation", "X-Forwarded-For", "192.0.2.2").statusCode()).isEqualTo(200);
            assertThat(get(app, client, "/operation", "X-Forwarded-For", "203.0.113.9, 192.0.2.1").statusCode()).isEqualTo(429);
        }
    }

    @Test void sameNamedClassesInDifferentPackagesHaveIndependentOperations() throws Exception {
        try (var app = application(Packages.class); var client = HttpClient.newHttpClient()) {
            for (String path : new String[]{"/left-quota", "/right-quota"}) assertThat(get(app, client, path).statusCode()).isEqualTo(200);
            for (String path : new String[]{"/left-quota", "/right-quota"}) assertThat(get(app, client, path).statusCode()).isEqualTo(429);
        }
    }

    @Test void globalAliasesAreSharedExplicitlyAndSeparateFromPrincipalAliases() throws Exception {
        try (var app = application(TestAuthentication.class); var client = HttpClient.newHttpClient()) {
            assertThat(get(app, client, "/global", "Fixture-Principal", "alice").statusCode()).isEqualTo(200);
            assertThat(get(app, client, "/global-alias", "Fixture-Principal", "bob").statusCode()).isEqualTo(429);
            assertThat(get(app, client, "/principal-alias", "Fixture-Principal", "alice").statusCode()).isEqualTo(200);
            assertThat(get(app, client, "/principal-alias", "Fixture-Principal", "bob").statusCode()).isEqualTo(200);
            assertThat(get(app, client, "/principal-alias", "Fixture-Principal", "alice").statusCode()).isEqualTo(429);
            assertThat(get(app, client, "/principal-alias", "Fixture-Principal", "x".repeat(128)).statusCode()).isEqualTo(200);
            assertThat(get(app, client, "/principal-alias", "Fixture-Principal", "x".repeat(129)).statusCode()).isEqualTo(403);
        }
    }

    @Test void retryAfterCeilingAndSafeLegacyEnvelopeUseTheSharedHttpPolicy() throws Exception {
        for (boolean problemDetail : new boolean[]{true, false}) {
            try (var app = application(RejectingAdapter.class, "facility.web.exception.use-problem-detail=" + problemDetail); var client = HttpClient.newHttpClient()) {
                var fractional = get(app, client, "/operation");
                assertThat(fractional.statusCode()).isEqualTo(429);
                assertThat(fractional.headers().firstValue("Retry-After")).contains("2");
                assertThat(fractional.body()).doesNotContain("operation", "RateLimitExceededException", "127.0.0.1");
                var saturated = get(app, client, "/operation");
                assertThat(saturated.statusCode()).isEqualTo(429);
                assertThat(saturated.headers().firstValue("Retry-After")).contains("9223372036854776");
            }
        }
        try (var app = application(FailingAdapter.class, "facility.web.exception.use-problem-detail=false"); var client = HttpClient.newHttpClient()) {
            var unavailable = get(app, client, "/operation");
            assertThat(unavailable.statusCode()).isEqualTo(200);
            assertThat(unavailable.body()).contains("\"code\":503").doesNotContain("PRIVATE-ADAPTER");
        }
    }

    @Test void invalidPoliciesNeverInvokeTheHostAdapterEvenUnderFailOpen() throws Exception {
        try (var app = application(FailingAdapter.class, "facility.ratelimit.fail-open=true"); var client = HttpClient.newHttpClient()) {
            for (String path : new String[]{"/invalid-cost", "/zero-cost", "/over-cost", "/negative-capacity", "/negative-rate", "/nan-rate", "/infinite-rate", "/control-alias"})
                assertThat(get(app, client, path).statusCode()).as(path).isEqualTo(500);
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
    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(cn.code91.facility.autoconfigure.FacilityIdempotencyAutoConfiguration.class)
    static class Replay {
        @Bean cn.code91.facility.web.idempotency.IdempotencyAuthorization authorization() {
            return (request, operation, body) -> new cn.code91.facility.web.idempotency.IdempotencyAuthorization.Command("fixture", "fixture", "empty-v1");
        }
    }
    @Configuration(proxyBeanMethods = false)
    @Import({cn.code91.facility.web.ratelimit.left.QuotaEndpoint.class, cn.code91.facility.web.ratelimit.right.QuotaEndpoint.class})
    static class Packages { }
    @Configuration(proxyBeanMethods = false) static class RejectingAdapter {
        @Bean cn.code91.facility.ratelimit.RateLimiter adapter() {
            return new cn.code91.facility.ratelimit.RateLimiter() {
                private final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
                public boolean tryAcquire(String key, int permits) { return false; }
                public cn.code91.facility.ratelimit.RateLimitResult acquire(String key, int permits, long capacity, double rate) {
                    return new cn.code91.facility.ratelimit.RateLimitResult(false, 0, calls.getAndIncrement() == 0 ? 1001 : Long.MAX_VALUE);
                }
            };
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
    @Configuration(proxyBeanMethods = false) static class TestAuthentication {
        @Bean org.springframework.boot.web.server.WebServerFactoryCustomizer<org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory> fixturePrincipal() {
            return factory -> factory.addContextValves(new org.apache.catalina.valves.ValveBase(true) {
                @Override public void invoke(org.apache.catalina.connector.Request request, org.apache.catalina.connector.Response response)
                        throws java.io.IOException, jakarta.servlet.ServletException {
                    String name = request.getHeader("Fixture-Principal");
                    if (name != null) request.setUserPrincipal(() -> name);
                    getNext().invoke(request, response);
                }
            });
        }
    }
    @RestController static class Endpoints {
        private final java.util.concurrent.atomic.AtomicInteger effects = new java.util.concurrent.atomic.AtomicInteger();
        @GetMapping("/effects") String effects() { return String.valueOf(effects.get()); }
        @GetMapping("/async") @RateLimit(capacity = 1, permitsPerSecond = 0.001)
        java.util.concurrent.Callable<String> async() { return () -> { effects.incrementAndGet(); return "async"; }; }
        @GetMapping("/blank-alias") @RateLimit(key = "   ")
        String blankAlias() { effects.incrementAndGet(); return "invalid"; }
        @GetMapping("/replay") @cn.code91.facility.web.idempotency.Idempotent
        @RateLimit(capacity = 2, permitsPerSecond = 0.001)
        String replay() { return "receipt-" + effects.incrementAndGet(); }
        @GetMapping("/global") @RateLimit(key = "shared-alias", scope = RateLimit.Scope.GLOBAL, capacity = 1, permitsPerSecond = 0.001)
        String global() { return "global"; }
        @GetMapping("/global-alias") @RateLimit(key = "shared-alias", capacity = 1, permitsPerSecond = 0.001)
        String globalAlias() { return "global"; }
        @GetMapping("/principal-alias") @RateLimit(key = "shared-alias", scope = RateLimit.Scope.PRINCIPAL, capacity = 1, permitsPerSecond = 0.001)
        String principalAlias() { return "principal"; }
        @GetMapping("/zero-cost") @RateLimit(permits = 0) String zeroCost() { effects.incrementAndGet(); return "invalid"; }
        @GetMapping("/over-cost") @RateLimit(permits = 2, capacity = 1) String overCost() { effects.incrementAndGet(); return "invalid"; }
        @GetMapping("/negative-capacity") @RateLimit(capacity = -1) String negativeCapacity() { effects.incrementAndGet(); return "invalid"; }
        @GetMapping("/negative-rate") @RateLimit(permitsPerSecond = -1) String negativeRate() { effects.incrementAndGet(); return "invalid"; }
        @GetMapping("/nan-rate") @RateLimit(permitsPerSecond = Double.NaN) String nanRate() { effects.incrementAndGet(); return "invalid"; }
        @GetMapping("/infinite-rate") @RateLimit(permitsPerSecond = Double.POSITIVE_INFINITY) String infiniteRate() { effects.incrementAndGet(); return "invalid"; }
        @GetMapping("/control-alias") @RateLimit(key = "bad\talias") String controlAlias() { effects.incrementAndGet(); return "invalid"; }
        @GetMapping("/invalid-cost") @RateLimit(permits = -1)
        String invalidCost() { effects.incrementAndGet(); return "invalid"; }
        @GetMapping("/principal") @RateLimit(scope = RateLimit.Scope.PRINCIPAL, capacity = 1, permitsPerSecond = 0.001)
        String principal() { effects.incrementAndGet(); return "principal"; }
        @GetMapping(value = "/operation", params = "!value") @RateLimit(capacity = 1, permitsPerSecond = 0.001)
        String operation() { effects.incrementAndGet(); return "one"; }
        @GetMapping(value = "/operation", params = "value") @RateLimit(capacity = 1, permitsPerSecond = 0.001)
        String operation(@RequestParam("value") String value) { effects.incrementAndGet(); return "two"; }
    }
}
