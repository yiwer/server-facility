package cn.code91.facility.web.idempotency;

import cn.code91.facility.autoconfigure.FacilityIdempotencyAutoConfiguration;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
class AuthorizedReplayHttpTest {
    @TempDir Path directory;

    @Test void missingCurrentAuthorizationAdapterRejectsBeforeExecutingTheTarget() throws Exception {
        try (var app = application(); var client = HttpClient.newHttpClient()) {
            var rejected = request(app, client, "/command", "Idempotency-Key", "command-1");
            assertThat(rejected.statusCode()).isEqualTo(503);
            assertThat(rejected.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
            assertThat(rejected.body()).doesNotContain("command-1", "Exception", "stackTrace");
            assertThat(request(app, client, "/effects").body()).isEqualTo("0");
        }
    }

    @Test void explicitAuthorizationReplaysTheQualifiedReceiptWithoutRepeatingTheAction() throws Exception {
        try (var app = application(Authorized.class); var client = HttpClient.newHttpClient()) {
            assertThat(request(app, client, "/command", "Idempotency-Key", "command-1").body()).isEqualTo("receipt-1");
            var replay = request(app, client, "/command", "Idempotency-Key", "command-1");
            assertThat(replay.statusCode()).isEqualTo(200);
            assertThat(replay.body()).isEqualTo("receipt-1");
            assertThat(request(app, client, "/effects").body()).isEqualTo("1");
        }
    }

    @Test void normalizedBusinessContentReplaysWhileChangedContentConflictsAndControllerStillReadsItsBody() throws Exception {
        try (var app = application(StructuredAuthorization.class); var client = HttpClient.newHttpClient()) {
            var first = post(app, client, "{\"currency\":\"USD\",\"amount\":7}");
            assertThat(first.statusCode()).isEqualTo(200);
            assertThat(first.body()).isEqualTo("receipt-1:USD:7");
            assertThat(post(app, client, "{ \"amount\" : 7, \"currency\" : \"USD\" }").body()).isEqualTo("receipt-1:USD:7");
            assertThat(post(app, client, "{\"currency\":\"USD\",\"amount\":8}").statusCode()).isEqualTo(409);
            assertThat(request(app, client, "/effects").body()).isEqualTo("1");
        }
    }

    @Test void disabledInfrastructureStillGuardsAnnotatedOperations() throws Exception {
        try (var app = application(new String[]{"facility.idempotency.enabled=false"}, Authorized.class);
             var client = HttpClient.newHttpClient()) {
            assertThat(request(app, client, "/command", "Idempotency-Key", "disabled").statusCode()).isEqualTo(503);
            assertThat(request(app, client, "/effects").body()).isEqualTo("0");
        }
    }

    @Test void permissionRevocationIsCheckedBeforeEveryReplay() throws Exception {
        try (var app = application(CurrentAuthorization.class); var client = HttpClient.newHttpClient()) {
            assertThat(request(app, client, "/command", "Idempotency-Key", "permission").body()).isEqualTo("receipt-1");
            assertThat(request(app, client, "/permission/false").statusCode()).isEqualTo(200);
            assertThat(request(app, client, "/command", "Idempotency-Key", "permission").statusCode()).isEqualTo(403);
            assertThat(request(app, client, "/permission/true").statusCode()).isEqualTo(200);
            assertThat(request(app, client, "/command", "Idempotency-Key", "permission").body()).isEqualTo("receipt-1");
            assertThat(request(app, client, "/effects").body()).isEqualTo("1");
        }
    }

    @Test void createdReceiptPreservesLocationButNeverReplaysCookiesAuthenticationOrPrivateHeaders() throws Exception {
        try (var app = application(Authorized.class); var client = HttpClient.newHttpClient()) {
            var first = request(app, client, "/created", "Idempotency-Key", "created-1");
            assertThat(first.statusCode()).isEqualTo(201);
            assertThat(first.headers().firstValue("Location")).contains("/orders/1");
            var replay = request(app, client, "/created", "Idempotency-Key", "created-1");
            assertThat(replay.statusCode()).isEqualTo(201);
            assertThat(replay.body()).isEqualTo("created-1");
            assertThat(replay.headers().firstValue("Location")).contains("/orders/1");
            for (String name : java.util.List.of("Set-Cookie", "WWW-Authenticate", "X-Private"))
                assertThat(replay.headers().allValues(name)).isEmpty();
            assertThat(request(app, client, "/effects").body()).isEqualTo("1");
        }
    }

    @Test void serverErrorResultPermanentlyRefusesAutomaticExecutionEvenAfterItsLeaseAndRetention() throws Exception {
        try (var app = application(Authorized.class, ControlledTime.class); var client = HttpClient.newHttpClient()) {
            assertThat(request(app, client, "/failed-result", "Idempotency-Key", "failed-1").statusCode()).isEqualTo(500);
            var retry = request(app, client, "/failed-result", "Idempotency-Key", "failed-1");
            assertThat(retry.statusCode()).isEqualTo(503);
            assertThat(retry.body()).doesNotContain("business-failed");
            assertThat(request(app, client, "/clock/5000").statusCode()).isEqualTo(200);
            assertThat(request(app, client, "/failed-result", "Idempotency-Key", "failed-1").statusCode()).isEqualTo(503);
            assertThat(request(app, client, "/effects").body()).isEqualTo("1");
        }
    }

    @Test void failureAfterMvcCompletionCannotLeaveAReceiptOrAuthorizeAnotherExecution() throws Exception {
        try (var app = application(Authorized.class, FailingOutboundFilter.class, ControlledTime.class);
             var client = HttpClient.newHttpClient()) {
            var first = request(app, client, "/post-filter-failure", "Idempotency-Key", "filter-failed");
            assertThat(first.statusCode()).isEqualTo(500);
            assertThat(first.body()).doesNotContain("PRIVATE-POST-FILTER");
            assertThat(request(app, client, "/post-filter-failure", "Idempotency-Key", "filter-failed").statusCode()).isEqualTo(503);
            assertThat(request(app, client, "/clock/5000").statusCode()).isEqualTo(200);
            assertThat(request(app, client, "/post-filter-failure", "Idempotency-Key", "filter-failed").statusCode()).isEqualTo(503);
            assertThat(request(app, client, "/effects").body()).isEqualTo("1");
        }
    }

    @Test void receiptIncludesTheCompletedInnerFilterBodyWithoutApplyingItsTransformationTwice() throws Exception {
        try (var app = application(Authorized.class, OutboundFooter.class); var client = HttpClient.newHttpClient()) {
            assertThat(request(app, client, "/filtered-body", "Idempotency-Key", "footer").body()).isEqualTo("base-1|tail-1");
            assertThat(request(app, client, "/filtered-body", "Idempotency-Key", "footer").body()).isEqualTo("base-1|tail-1");
            assertThat(request(app, client, "/effects").body()).isEqualTo("1");
        }
    }

    @Test void replayCannotOverwriteAnOutboundFiltersCurrentDenial() throws Exception {
        try (var app = application(Authorized.class, OutboundDenial.class); var client = HttpClient.newHttpClient()) {
            assertThat(request(app, client, "/command", "Idempotency-Key", "denial").body()).isEqualTo("receipt-1");
            var denied = request(app, client, "/command", "Idempotency-Key", "denial");
            assertThat(denied.statusCode()).isEqualTo(403);
            assertThat(denied.body()).doesNotContain("receipt-1");
            assertThat(request(app, client, "/effects").body()).isEqualTo("1");
        }
    }

    @Test void declaredAsyncAndStreamingTargetsAreRejectedBeforeTheirMethodsExecute() throws Exception {
        try (var app = application(Authorized.class); var client = HttpClient.newHttpClient()) {
            for (String kind : java.util.List.of("callable", "deferred", "stage", "sse", "stream", "entity-stream"))
                assertThat(request(app, client, "/async-" + kind, "Idempotency-Key", kind).statusCode()).as(kind).isEqualTo(503);
            assertThat(request(app, client, "/effects").body()).isEqualTo("0");
        }
    }

    @Test void runtimeAsyncEscapeFailsAndPermanentlyStopsTheQualifiedCommand() throws Exception {
        try (var app = application(Authorized.class, ControlledTime.class); var client = HttpClient.newHttpClient()) {
            assertThat(request(app, client, "/async-dynamic", "Idempotency-Key", "escape").statusCode()).isEqualTo(500);
            assertThat(request(app, client, "/clock/5000").statusCode()).isEqualTo(200);
            assertThat(request(app, client, "/async-dynamic", "Idempotency-Key", "escape").statusCode()).isEqualTo(503);
            assertThat(request(app, client, "/effects").body()).isEqualTo("1");
        }
    }

    @Test void invalidOrAmbiguousKeysAreClientErrorsAndTheMaximumValidKeyStillWorks() throws Exception {
        try (var app = application(Authorized.class); var client = HttpClient.newHttpClient()) {
            assertThat(request(app, client, "/command").statusCode()).isEqualTo(400);
            for (String key : java.util.List.of(" ", "x".repeat(257), "embedded\tcontrol"))
                assertThat(request(app, client, "/command", "Idempotency-Key", key).statusCode()).isEqualTo(400);
            assertThat(request(app, client, "/command", "Idempotency-Key", "first", "Idempotency-Key", "second").statusCode()).isEqualTo(400);
            assertThat(request(app, client, "/effects").body()).isEqualTo("0");
            assertThat(request(app, client, "/command", "Idempotency-Key", "x".repeat(256)).body()).isEqualTo("receipt-1");
        }
    }

    @Test void inheritedMethodsOnDifferentControllerTypesRemainDifferentOperations() throws Exception {
        try (var app = application(Authorized.class, InheritedOperations.class); var client = HttpClient.newHttpClient()) {
            assertThat(request(app, client, "/inherited?kind=a", "Idempotency-Key", "inherited").body()).isEqualTo("a-1");
            assertThat(request(app, client, "/inherited?kind=b", "Idempotency-Key", "inherited").body()).isEqualTo("b-1");
            assertThat(request(app, client, "/inherited?kind=a", "Idempotency-Key", "inherited").body()).isEqualTo("a-1");
            assertThat(request(app, client, "/inherited?kind=b", "Idempotency-Key", "inherited").body()).isEqualTo("b-1");
        }
    }

    @Test void executionLeaseAndReceiptRetentionHaveIndependentExclusiveDeadlines() throws Exception {
        try (var app = application(new String[]{"facility.idempotency.default-ttl=1ms", "facility.idempotency.lease=10ms",
                "facility.idempotency.result-retention=100ms"}, Authorized.class, ControlledTime.class);
             var client = HttpClient.newHttpClient()) {
            assertThat(request(app, client, "/command", "Idempotency-Key", "deadlines").body()).isEqualTo("receipt-1");
            assertThat(request(app, client, "/clock/10").statusCode()).isEqualTo(200);
            assertThat(request(app, client, "/command", "Idempotency-Key", "deadlines").body()).isEqualTo("receipt-1");
            assertThat(request(app, client, "/clock/99").statusCode()).isEqualTo(200);
            assertThat(request(app, client, "/command", "Idempotency-Key", "deadlines").body()).isEqualTo("receipt-1");
            assertThat(request(app, client, "/clock/100").statusCode()).isEqualTo(200);
            assertThat(request(app, client, "/command", "Idempotency-Key", "deadlines").statusCode()).isEqualTo(503);
            assertThat(request(app, client, "/effects").body()).isEqualTo("1");
        }
    }

    @Test void resolvedAdviceExceptionsNeverBecomeSuccessfulReplayReceipts() throws Exception {
        try (var app = application(Authorized.class, ControlledTime.class, SwallowingAdvice.class);
             var client = HttpClient.newHttpClient()) {
            for (int status : new int[]{200, 400, 500}) {
                assertThat(request(app, client, "/advice/" + status, "Idempotency-Key", "advice-" + status).statusCode()).isEqualTo(status);
                assertThat(request(app, client, "/advice/" + status, "Idempotency-Key", "advice-" + status).statusCode()).isEqualTo(503);
            }
            assertThat(request(app, client, "/clock/100000").statusCode()).isEqualTo(200);
            for (int status : new int[]{200, 400, 500})
                assertThat(request(app, client, "/advice/" + status, "Idempotency-Key", "advice-" + status).statusCode()).isEqualTo(503);
            assertThat(request(app, client, "/effects").body()).isEqualTo("3");
        }
    }

    @Test void bodyTransformationsMustPrecedeCaptureWhileOrdinaryRequestsRemainTransparent() throws Exception {
        try (var app = application(StructuredAuthorization.class, LateBodyTransform.class); var client = HttpClient.newHttpClient()) {
            assertThat(post(app, client, "{\"currency\":\"USD\",\"amount\":7}").statusCode()).isEqualTo(503);
            assertThat(request(app, client, "/effects").body()).isEqualTo("0");
            var ordinary = client.send(HttpRequest.newBuilder(app.uri("/ordinary-body"))
                    .POST(HttpRequest.BodyPublishers.ofString("{\"amount\":7}")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(ordinary.body()).isEqualTo("{\"amount\":8}");
        }
        try (var app = application(StructuredAuthorization.class, EarlyBodyTransform.class); var client = HttpClient.newHttpClient()) {
            assertThat(post(app, client, "{\"currency\":\"USD\",\"amount\":7}").body()).isEqualTo("receipt-1:USD:8");
            assertThat(post(app, client, "{\"currency\":\"USD\",\"amount\":8}").body()).isEqualTo("receipt-1:USD:8");
            assertThat(post(app, client, "{\"currency\":\"USD\",\"amount\":9}").statusCode()).isEqualTo(409);
            assertThat(request(app, client, "/effects").body()).isEqualTo("1");
        }
    }

    @Test void previouslyAccessedOutputRejectsBeforeClaimOrBusinessExecution() throws Exception {
        try (var app = application(Authorized.class, PrematureOutput.class); var client = HttpClient.newHttpClient()) {
            var rejected = request(app, client, "/command", "Idempotency-Key", "output-accessed");
            assertThat(rejected.statusCode()).isEqualTo(503);
            assertThat(rejected.body()).doesNotContain("PRIVATE-PREFIX", "receipt-1");
            assertThat(request(app, client, "/effects").body()).isEqualTo("0");
        }
    }

    @Test void finiteSuccessAndExplicitBusinessRejectionsReplayButTransportAndAuthorizationStatusesDoNot() throws Exception {
        try (var app = application(Authorized.class); var client = HttpClient.newHttpClient()) {
            for (int status : new int[]{200, 201, 204, 400, 404, 409, 410, 422}) {
                String path = "/returned-status/" + status;
                var first = request(app, client, path, "Idempotency-Key", "status");
                var replay = request(app, client, path, "Idempotency-Key", "status");
                assertThat(first.statusCode()).isEqualTo(status);
                assertThat(replay.statusCode()).isEqualTo(status);
                assertThat(replay.body()).isEqualTo(first.body());
            }
            for (int status : new int[]{206, 302, 401, 403, 407, 416, 429, 500}) {
                String path = "/returned-status/" + status;
                assertThat(request(app, client, path, "Idempotency-Key", "status").statusCode()).isEqualTo(status);
                assertThat(request(app, client, path, "Idempotency-Key", "status").statusCode()).as("ineligible %s", status).isEqualTo(503);
            }
            assertThat(request(app, client, "/effects").body()).isEqualTo("16");
        }
    }

    @Test void unsupportedRepresentationMetadataNeverProducesAReplayReceipt() throws Exception {
        try (var app = application(Authorized.class); var client = HttpClient.newHttpClient()) {
            for (String kind : java.util.List.of("encoded", "range", "large-location", "multiple-encoding")) {
                assertThat(request(app, client, "/representation/" + kind, "Idempotency-Key", "metadata").statusCode()).isEqualTo(200);
                assertThat(request(app, client, "/representation/" + kind, "Idempotency-Key", "metadata").statusCode()).as(kind).isEqualTo(503);
            }
            assertThat(request(app, client, "/effects").body()).isEqualTo("4");
        }
    }

    @Test void replayReplacesStaleEntityMetadataButRetainsCurrentSecurityHeaders() throws Exception {
        try (var app = application(Authorized.class, CurrentEntityHeaders.class); var client = HttpClient.newHttpClient()) {
            assertThat(request(app, client, "/command", "Idempotency-Key", "headers").body()).isEqualTo("receipt-1");
            var replay = request(app, client, "/command", "Idempotency-Key", "headers");
            assertThat(replay.statusCode()).isEqualTo(200);
            assertThat(replay.body()).isEqualTo("receipt-1");
            for (String name : java.util.List.of("Content-Encoding", "Content-Disposition", "Content-Range", "ETag", "Last-Modified"))
                assertThat(replay.headers().allValues(name)).as(name).isEmpty();
            assertThat(replay.headers().firstValue("X-Frame-Options")).contains("DENY");
        }
    }

    @Test void responseSerializationFailureCannotTurnAPartialResultIntoAReceipt() throws Exception {
        try (var app = application(Authorized.class, ControlledTime.class); var client = HttpClient.newHttpClient()) {
            var first = request(app, client, "/serialization-failure", "Idempotency-Key", "serializer");
            assertThat(first.statusCode()).isEqualTo(500);
            assertThat(first.body()).doesNotContain("PRIVATE-SERIALIZATION", "visible-prefix", "Exception");
            assertThat(request(app, client, "/clock/1000000").statusCode()).isEqualTo(200);
            assertThat(request(app, client, "/serialization-failure", "Idempotency-Key", "serializer").statusCode()).isEqualTo(503);
            assertThat(request(app, client, "/effects").body()).isEqualTo("1");
        }
    }

    @Test void explicitLegacyErrorEnvelopeDoesNotRestoreUnsafeExecutionOrExposeTheKey() throws Exception {
        try (var app = application(new String[]{"facility.web.exception.use-problem-detail=false"}); var client = HttpClient.newHttpClient()) {
            var response = request(app, client, "/command", "Idempotency-Key", "PRIVATE-KEY");
            assertThat(response.statusCode()).isEqualTo(200);
            var body = new tools.jackson.databind.ObjectMapper().readTree(response.body());
            assertThat(body.get("code").asInt()).isEqualTo(503);
            assertThat(body.get("message").asString()).isEqualTo("Service Unavailable");
            assertThat(response.body()).doesNotContain("PRIVATE-KEY", "Exception", "stackTrace");
            assertThat(request(app, client, "/effects").body()).isEqualTo("0");
        }
    }

    @Test void anAlreadyAsynchronousRequestCannotAcquireAFiniteCommand() throws Exception {
        try (var app = application(Authorized.class, AlreadyAsync.class); var client = HttpClient.newHttpClient()) {
            assertThat(request(app, client, "/command", "Idempotency-Key", "already-async").statusCode()).isEqualTo(503);
            assertThat(request(app, client, "/effects").body()).isEqualTo("0");
        }
    }

    @Test void inconsistentEntityFramingCannotSaveBytesDifferentFromTheFirstWireResponse() throws Exception {
        try (var app = application(Authorized.class); var client = HttpClient.newHttpClient()) {
            var first = request(app, client, "/short-framing", "Idempotency-Key", "framing");
            assertThat(first.statusCode()).isEqualTo(200);
            assertThat(first.body()).isEqualTo("ab");
            assertThat(request(app, client, "/short-framing", "Idempotency-Key", "framing").statusCode()).isEqualTo(503);
            assertThat(request(app, client, "/effects").body()).isEqualTo("1");
        }
    }

    @Test void fullOverloadSignatureSeparatesOperationsWhileHostNormalizationIncludesBusinessQueryValues() throws Exception {
        try (var app = application(OverloadAuthorization.class); var client = HttpClient.newHttpClient()) {
            assertThat(request(app, client, "/overloaded", "Idempotency-Key", "overload").body()).isEqualTo("plain-1");
            assertThat(request(app, client, "/overloaded?mode=fast", "Idempotency-Key", "overload").body()).isEqualTo("fast-2");
            assertThat(request(app, client, "/overloaded", "Idempotency-Key", "overload").body()).isEqualTo("plain-1");
            assertThat(request(app, client, "/overloaded?mode=fast", "Idempotency-Key", "overload").body()).isEqualTo("fast-2");
            assertThat(request(app, client, "/overloaded?mode=other", "Idempotency-Key", "overload").statusCode()).isEqualTo(409);
            assertThat(request(app, client, "/effects").body()).isEqualTo("2");
        }
    }

    private static HttpResponse<String> post(EmbeddedServletApplication app, HttpClient client, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(app.uri("/structured")).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json").header("Idempotency-Key", "structured-1")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private EmbeddedServletApplication application(Class<?>... extra) {
        return application(new String[0], extra);
    }

    private EmbeddedServletApplication application(String[] properties, Class<?>... extra) {
        var configs = new java.util.ArrayList<Class<?>>();
        configs.add(Config.class); configs.addAll(java.util.List.of(extra));
        return EmbeddedServletApplication.start(directory, configs.toArray(Class<?>[]::new), properties);
    }

    private static HttpResponse<String> request(EmbeddedServletApplication app, HttpClient client, String path,
                                                 String... headers) throws Exception {
        var request = HttpRequest.newBuilder(app.uri(path)).timeout(Duration.ofSeconds(5));
        if (headers.length != 0) request.headers(headers);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Configuration(proxyBeanMethods = false) @EnableWebMvc @Import(Endpoints.class)
    @ImportAutoConfiguration({FacilityWebAutoConfiguration.class, FacilityIdempotencyAutoConfiguration.class,
            org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration.class})
    static class Config {
        @Bean org.springframework.web.servlet.DispatcherServlet dispatcherServlet() { return new org.springframework.web.servlet.DispatcherServlet(); }
        @Bean org.springframework.boot.webmvc.autoconfigure.DispatcherServletRegistrationBean dispatcherRegistration(org.springframework.web.servlet.DispatcherServlet servlet) {
            var registration = new org.springframework.boot.webmvc.autoconfigure.DispatcherServletRegistrationBean(servlet, "/");
            registration.setAsyncSupported(true); return registration;
        }
    }

    @Configuration(proxyBeanMethods = false) static class Authorized {
        @Bean IdempotencyAuthorization authorization() {
            return (request, operation, body) -> new IdempotencyAuthorization.Command("tenant-a", "actor-a", "empty-command-v1");
        }
    }

    @Configuration(proxyBeanMethods = false) static class StructuredAuthorization {
        @Bean IdempotencyAuthorization authorization() {
            return (request, operation, body) -> {
                var command = new tools.jackson.databind.ObjectMapper().readTree(body);
                return new IdempotencyAuthorization.Command("tenant-a", "actor-a",
                        "purchase-v1:" + command.get("currency").asString() + ":" + command.get("amount").asInt());
            };
        }
    }

    @Configuration(proxyBeanMethods = false) static class OverloadAuthorization {
        @Bean IdempotencyAuthorization authorization() {
            return (request, operation, body) -> new IdempotencyAuthorization.Command("tenant", "actor",
                    "mode-v1:" + java.util.Objects.requireNonNullElse(request.getParameter("mode"), "fast"));
        }
    }

    @Configuration(proxyBeanMethods = false) @Import(PermissionHost.class) static class CurrentAuthorization { }
    @RestController static class PermissionHost implements IdempotencyAuthorization {
        private final java.util.concurrent.atomic.AtomicBoolean allowed = new java.util.concurrent.atomic.AtomicBoolean(true);
        @GetMapping("/permission/{allowed}") String permission(@PathVariable("allowed") boolean value) {
            allowed.set(value); return "updated";
        }
        @Override public Command authorize(jakarta.servlet.http.HttpServletRequest request,
                                            org.springframework.web.method.HandlerMethod operation, byte[] body) {
            if (!allowed.get()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
            return new Command("tenant-a", "actor-a", "empty-command-v1");
        }
    }

    @Configuration(proxyBeanMethods = false) @Import(TimeHost.class) static class ControlledTime {
        @Bean cn.code91.facility.idempotency.IdempotencyStore store(TimeHost clock) {
            return new cn.code91.facility.idempotency.InMemoryIdempotencyStore(100, 2 * 1024 * 1024, 4 * 1024 * 1024, clock);
        }
    }
    @RestController static class TimeHost extends java.time.Clock {
        private final java.util.concurrent.atomic.AtomicLong time = new java.util.concurrent.atomic.AtomicLong();
        @GetMapping("/clock/{millis}") String advance(@PathVariable("millis") long value) { time.set(value); return "advanced"; }
        @Override public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        @Override public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public java.time.Instant instant() { return java.time.Instant.ofEpochMilli(millis()); }
        @Override public long millis() { return time.get(); }
    }

    @Configuration(proxyBeanMethods = false) static class FailingOutboundFilter {
        @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> outboundFailure() {
            var first = new java.util.concurrent.atomic.AtomicBoolean(true);
            var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter>();
            registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 4);
            registration.setFilter((request, response, chain) -> {
                chain.doFilter(request, response);
                if (((jakarta.servlet.http.HttpServletRequest) request).getRequestURI().equals("/post-filter-failure")
                        && first.compareAndSet(true, false)) throw new java.io.IOException("PRIVATE-POST-FILTER");
            });
            return registration;
        }
    }

    @Configuration(proxyBeanMethods = false) static class OutboundFooter {
        @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> footer() {
            var sequence = new AtomicInteger();
            var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter>();
            registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 4);
            registration.setFilter((request, response, chain) -> {
                chain.doFilter(request, response);
                if (((jakarta.servlet.http.HttpServletRequest) request).getRequestURI().equals("/filtered-body")) {
                    response.getOutputStream().write(("|tail-" + sequence.incrementAndGet()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    response.flushBuffer();
                }
            });
            return registration;
        }
    }

    @Configuration(proxyBeanMethods = false) static class OutboundDenial {
        @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> denial() {
            var sequence = new AtomicInteger();
            var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter>();
            registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 4);
            registration.setFilter((request, response, chain) -> {
                chain.doFilter(request, response);
                if (((jakarta.servlet.http.HttpServletRequest) request).getRequestURI().equals("/command")
                        && sequence.incrementAndGet() > 1) {
                    response.resetBuffer();
                    ((jakarta.servlet.http.HttpServletResponse) response).setStatus(403);
                    response.getWriter().write("denied-now");
                }
            });
            return registration;
        }
    }

    @Configuration(proxyBeanMethods = false) @Import({InheritedA.class, InheritedB.class}) static class InheritedOperations { }
    static abstract class InheritedBase {
        private final AtomicInteger effects = new AtomicInteger();
        abstract String prefix();
        @Idempotent @GetMapping String inherited() { return prefix() + "-" + effects.incrementAndGet(); }
    }
    @RestController @RequestMapping(path = "/inherited", params = "kind=a") static class InheritedA extends InheritedBase {
        @Override String prefix() { return "a"; }
    }
    @RestController @RequestMapping(path = "/inherited", params = "kind=b") static class InheritedB extends InheritedBase {
        @Override String prefix() { return "b"; }
    }
    static final class PlannedFailure extends RuntimeException {
        final int status;
        PlannedFailure(int status) { super("PRIVATE-ADVICE-FAILURE"); this.status = status; }
    }
    @RestControllerAdvice @org.springframework.core.annotation.Order(-100)
    static class SwallowingAdvice {
        @ExceptionHandler(PlannedFailure.class) org.springframework.http.ResponseEntity<String> swallow(PlannedFailure failure) {
            return org.springframework.http.ResponseEntity.status(failure.status).body("advice-result");
        }
    }

    @Configuration(proxyBeanMethods = false) static class AlreadyAsync {
        @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> alreadyAsync() {
            var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter>();
            registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 4); registration.setAsyncSupported(true);
            registration.setFilter((request, response, chain) -> {
                if (((jakarta.servlet.http.HttpServletRequest) request).getRequestURI().equals("/command")) {
                    var async = request.startAsync(request, response);
                    try { chain.doFilter(request, response); } finally { async.complete(); }
                } else chain.doFilter(request, response);
            });
            return registration;
        }
    }

    @Configuration(proxyBeanMethods = false) static class CurrentEntityHeaders {
        @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> currentEntityHeaders() {
            var sequence = new AtomicInteger();
            var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter>();
            registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 4);
            registration.setFilter((request, response, chain) -> {
                chain.doFilter(request, response);
                if (((jakarta.servlet.http.HttpServletRequest) request).getRequestURI().equals("/command") && sequence.incrementAndGet() > 1) {
                    var target = (jakarta.servlet.http.HttpServletResponse) response;
                    target.setHeader("Content-Encoding", "gzip"); target.setHeader("Content-Disposition", "attachment; filename=wrong.bin");
                    target.setHeader("Content-Range", "bytes 0-1/2"); target.setHeader("ETag", "\"wrong\"");
                    target.setHeader("Last-Modified", "Wed, 01 Jan 2025 00:00:00 GMT"); target.setHeader("Content-Length", "2");
                    target.setHeader("X-Frame-Options", "DENY");
                }
            });
            return registration;
        }
    }

    @Configuration(proxyBeanMethods = false) static class PrematureOutput {
        @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> prematureOutput() {
            var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter>();
            registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 4);
            registration.setFilter((request, response, chain) -> {
                if (((jakarta.servlet.http.HttpServletRequest) request).getRequestURI().equals("/command"))
                    response.getWriter().write("PRIVATE-PREFIX");
                chain.doFilter(request, response);
            });
            return registration;
        }
    }

    @Configuration(proxyBeanMethods = false) static class LateBodyTransform {
        @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> bodyTransform() { return bodyTransformAt(4); }
    }
    @Configuration(proxyBeanMethods = false) static class EarlyBodyTransform {
        @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> bodyTransform() { return bodyTransformAt(2); }
    }
    private static org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> bodyTransformAt(int offset) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter>();
        registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + offset);
        registration.setFilter((request, response, chain) -> chain.doFilter(new jakarta.servlet.http.HttpServletRequestWrapper(
                (jakarta.servlet.http.HttpServletRequest) request) {
            @Override public jakarta.servlet.ServletInputStream getInputStream() throws java.io.IOException {
                var source = super.getInputStream();
                return new jakarta.servlet.ServletInputStream() {
                    @Override public int read() throws java.io.IOException { int value = source.read(); return value == '7' ? '8' : value; }
                    @Override public boolean isFinished() { return source.isFinished(); }
                    @Override public boolean isReady() { return source.isReady(); }
                    @Override public void setReadListener(jakarta.servlet.ReadListener listener) { source.setReadListener(listener); }
                };
            }
        }, response));
        return registration;
    }

    @RestController static class Endpoints {
        private final AtomicInteger effects = new AtomicInteger();
        @GetMapping("/effects") String effects() { return String.valueOf(effects.get()); }
        @PostMapping("/ordinary-body") String ordinaryBody(@RequestBody String body) { return body; }
        @Idempotent @GetMapping("/command") String command() { return "receipt-" + effects.incrementAndGet(); }
        @Idempotent @PostMapping("/structured") String structured(@RequestBody String body) {
            var command = new tools.jackson.databind.ObjectMapper().readTree(body);
            return "receipt-" + effects.incrementAndGet() + ":" + command.get("currency").asString() + ":" + command.get("amount").asInt();
        }
        @Idempotent @GetMapping("/created") org.springframework.http.ResponseEntity<String> created() {
            int id = effects.incrementAndGet();
            return org.springframework.http.ResponseEntity.created(java.net.URI.create("/orders/" + id))
                    .header("Set-Cookie", "session=PRIVATE-SESSION").header("WWW-Authenticate", "PRIVATE-CHALLENGE")
                    .header("X-Private", "PRIVATE-HEADER").body("created-" + id);
        }
        @Idempotent(ttlSeconds = 1) @GetMapping("/failed-result") org.springframework.http.ResponseEntity<String> failedResult() {
            return org.springframework.http.ResponseEntity.internalServerError().body("business-failed-" + effects.incrementAndGet());
        }
        @Idempotent(ttlSeconds = 1) @GetMapping("/post-filter-failure") void postFilterFailure() { effects.incrementAndGet(); }
        @Idempotent @GetMapping("/filtered-body") void filteredBody(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
            response.setContentType("text/plain");
            response.getOutputStream().write(("base-" + effects.incrementAndGet()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        @Idempotent @GetMapping("/async-callable") java.util.concurrent.Callable<String> callable() {
            effects.incrementAndGet(); return () -> "async";
        }
        @Idempotent @GetMapping("/async-deferred") org.springframework.web.context.request.async.DeferredResult<String> deferred() {
            effects.incrementAndGet(); var result = new org.springframework.web.context.request.async.DeferredResult<String>();
            result.setResult("async"); return result;
        }
        @Idempotent @GetMapping("/async-stage") java.util.concurrent.CompletionStage<String> stage() {
            effects.incrementAndGet(); return java.util.concurrent.CompletableFuture.completedFuture("async");
        }
        @Idempotent @GetMapping("/async-sse") org.springframework.web.servlet.mvc.method.annotation.SseEmitter sse() {
            effects.incrementAndGet(); var emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter();
            emitter.complete(); return emitter;
        }
        @Idempotent @GetMapping("/async-stream") org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody stream() {
            effects.incrementAndGet(); return output -> output.write('x');
        }
        @Idempotent @GetMapping("/async-entity-stream") org.springframework.http.ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> entityStream() {
            effects.incrementAndGet(); return org.springframework.http.ResponseEntity.ok(output -> output.write('x'));
        }
        @Idempotent(ttlSeconds = 1) @GetMapping("/async-dynamic") void dynamic(jakarta.servlet.http.HttpServletRequest request) throws java.io.IOException {
            effects.incrementAndGet(); var async = request.startAsync();
            async.getResponse().getWriter().write("async-escaped"); async.complete();
        }
        @Idempotent(ttlSeconds = 1) @GetMapping("/advice/{status}") String advice(@PathVariable("status") int status) {
            effects.incrementAndGet(); throw new PlannedFailure(status);
        }
        @Idempotent @GetMapping("/returned-status/{status}") org.springframework.http.ResponseEntity<String> returnedStatus(@PathVariable("status") int status) {
            return org.springframework.http.ResponseEntity.status(status).body("result-" + effects.incrementAndGet());
        }
        @Idempotent @GetMapping("/representation/{kind}") org.springframework.http.ResponseEntity<String> representation(@PathVariable("kind") String kind) {
            var result = org.springframework.http.ResponseEntity.ok();
            if (kind.equals("encoded")) result.header("Content-Encoding", "gzip");
            if (kind.equals("multiple-encoding")) result.header("Content-Encoding", "identity", "gzip");
            if (kind.equals("range")) result.header("Content-Range", "bytes 0-5/10");
            if (kind.equals("large-location")) result.header("Location", "/" + "x".repeat(4096));
            return result.body("metadata-" + effects.incrementAndGet());
        }
        @Idempotent @GetMapping("/serialization-failure") PoisonResult serializationFailure() { effects.incrementAndGet(); return new PoisonResult(); }
        @Idempotent @GetMapping("/short-framing") void shortFraming(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
            effects.incrementAndGet(); response.setContentLength(2); response.getOutputStream().write(new byte[]{'a', 'b', 'c'});
        }
        @Idempotent @GetMapping(value = "/overloaded", params = "!mode") String overloaded() { return "plain-" + effects.incrementAndGet(); }
        @Idempotent @GetMapping(value = "/overloaded", params = "mode") String overloaded(@RequestParam("mode") String mode) { return mode + "-" + effects.incrementAndGet(); }
    }
    public static class PoisonResult {
        public String getVisible() { return "visible-prefix"; }
        public String getPoison() { throw new IllegalStateException("PRIVATE-SERIALIZATION"); }
    }
}
