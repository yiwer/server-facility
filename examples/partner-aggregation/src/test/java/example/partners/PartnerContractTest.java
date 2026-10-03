package example.partners;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import brave.Tracing;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BraveBaggageManager;
import io.micrometer.tracing.brave.bridge.BravePropagator;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import static org.assertj.core.api.Assertions.*;
import static example.partners.PartnerApplication.*;

class PartnerContractTest {
    @Test void concurrentApplicationsIsolateCredentialsAndClosingOneDoesNotCloseTheOther() throws Exception {
        try (var catalog = new PartnerServer(); var inventory = new PartnerServer();
             var first = start(catalog, inventory); var second = start(catalog, inventory,
                     "--partners.catalog.token=catalog-second", "--partners.inventory.token=inventory-second")) {
            var firstClients = List.copyOf(first.getBeansOfType(HttpClient.class).values());
            var secondClients = List.copyOf(second.getBeansOfType(HttpClient.class).values());
            try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
                var jobs = new ArrayList<java.util.concurrent.Future<?>>();
                for (int i = 0; i < 16; i++) {
                    jobs.add(workers.submit(() -> first.getBean(PartnerModule.class).offer("book")));
                    jobs.add(workers.submit(() -> second.getBean(PartnerModule.class).offer("book")));
                }
                for (var job : jobs) assertThat(job.get(5, TimeUnit.SECONDS)).isNotNull();
            }
            assertThat(catalog.requests).hasSize(32).allSatisfy(request -> assertThat(request).contains("/products/").doesNotContain("inventory-"));
            assertThat(inventory.requests).hasSize(32).allSatisfy(request -> assertThat(request).contains("/stock/").doesNotContain("catalog-"));
            assertThat(catalog.requests.stream().filter(value -> value.contains("catalog-second")).count()).isEqualTo(16);
            assertThat(inventory.requests.stream().filter(value -> value.contains("inventory-second")).count()).isEqualTo(16);
            first.close();
            for (var client : firstClients) assertThat(client.awaitTermination(Duration.ofSeconds(3))).isTrue();
            assertThat(second.getBean(Catalog.class).find("book")).isNotNull();
            second.close();
            for (var client : secondClients) assertThat(client.awaitTermination(Duration.ofSeconds(3))).isTrue();
        }
    }
    @Test void invalidConfigurationFailsBeforeAnyPartnerRequest() throws Exception {
        try (var catalog = new PartnerServer(); var inventory = new PartnerServer()) {
            for (String value : new String[]{"--partners.catalog.request-timeout=PT0S", "--partners.catalog.request-timeout=PT0.000001S",
                    "--partners.catalog.request-timeout=-PT1S", "--partners.catalog.request-timeout=PT1H",
                    "--partners.catalog.max-response-bytes=16777217", "--partners.inventory.max-response-bytes=0"}) {
                assertThatThrownBy(() -> { try (var app = start(catalog, inventory, value)) {} }).as(value).isInstanceOf(RuntimeException.class);
            }
            assertThat(catalog.requests).isEmpty(); assertThat(inventory.requests).isEmpty();
        }
    }
    @Test void decompressedBytesAreLimitedBeforeJsonMaterialization() throws Exception {
        try (var catalog = new PartnerServer(); var inventory = new PartnerServer(); var app = start(catalog, inventory, "--partners.catalog.max-response-bytes=128")) {
            catalog.gzip = true;
            assertThat(app.getBean(Catalog.class).find("book")).isEqualTo(new Product("book", "A Book"));
            catalog.bodyOverride = "{\"sku\":\"book\",\"product_name\":\"" + "x".repeat(1000) + "\"}";
            var failure = catchThrowableOfType(PartnerFailure.class, () -> app.getBean(Catalog.class).find("book"));
            assertThat(catalog.wireBodyBytes).isLessThan(128);
            assertThat(failure.kind()).isEqualTo(PartnerFailure.Kind.RESPONSE_TOO_LARGE);
        }
    }
    @Test void actualHostTracingCreatesSeparateClientSpansWithinTheApplicationTrace() throws Exception {
        try (var catalog = new PartnerServer(); var inventory = new PartnerServer(); var app = start(catalog, inventory)) {
            var tracer = app.getBean(Tracer.class);
            var span = tracer.nextSpan().name("aggregate-offer").start();
            try (var ignored = tracer.withSpan(span)) { app.getBean(PartnerModule.class).offer("book"); }
            finally { span.end(); }
            assertThat(catalog.traces).containsExactly(span.context().traceId());
            assertThat(inventory.traces).containsExactly(span.context().traceId());
            assertThat(catalog.spans).hasSize(1).doesNotContain(span.context().spanId());
            assertThat(inventory.spans).hasSize(1).doesNotContain(span.context().spanId(), catalog.spans.getFirst());
            assertThat(tracer.currentSpan()).isNull();
        }
    }
    @Test void aRetryOnlyReceivesTheRemainderOfTheOriginalDeadline() throws Exception {
        try (var catalog = new PartnerServer(); var inventory = new PartnerServer(); var app = start(catalog, inventory)) {
            catalog.retryAfter = "0"; catalog.unavailable.set(1); catalog.block = true; catalog.blockSecond = true;
            var worker = Executors.newSingleThreadExecutor();
            try {
                long started = System.nanoTime();
                var result = worker.submit(() -> catchThrowable(() -> app.getBean(Catalog.class).findWithRetry("book")));
                assertThat(catalog.entered.await(1, TimeUnit.SECONDS)).isTrue();
                Thread.sleep(1300); // Consume a real, bounded portion of the one 2-second budget.
                catalog.release.countDown();
                assertThat(catalog.secondEntered.await(1, TimeUnit.SECONDS)).isTrue();
                var failure = result.get(3, TimeUnit.SECONDS);
                assertThat(failure).isInstanceOf(PartnerFailure.class);
                assertThat(((PartnerFailure) failure).kind()).isEqualTo(PartnerFailure.Kind.RESPONSE_TIMEOUT);
                assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(2700));
                assertThat(catalog.requests).hasSize(2);
            } finally { catalog.release.countDown(); catalog.secondRelease.countDown(); worker.shutdownNow(); assertThat(worker.awaitTermination(3, TimeUnit.SECONDS)).isTrue(); }
        }
    }
    @Test void onlyExplicitSafeCatalogReadsRetryAndTheyAttemptAtMostTwice() throws Exception {
        try (var catalog = new PartnerServer(); var inventory = new PartnerServer(); var app = start(catalog, inventory)) {
            catalog.retryAfter = "0"; catalog.unavailable.set(1);
            assertThatThrownBy(() -> app.getBean(Catalog.class).find("book")).isInstanceOf(PartnerFailure.class);
            assertThat(catalog.requests).hasSize(1);
            catalog.unavailable.set(1);
            assertThat(app.getBean(Catalog.class).findWithRetry("book")).isEqualTo(new Product("book", "A Book"));
            assertThat(catalog.requests).hasSize(3);
            catalog.unavailable.set(10);
            assertThatThrownBy(() -> app.getBean(Catalog.class).findWithRetry("book")).isInstanceOf(PartnerFailure.class);
            assertThat(catalog.requests).hasSize(5);
        }
    }
    @Test void aCommittedCommandWithNoResponseIsUnknownAndNeverAutomaticallyResent() throws Exception {
        try (var catalog = new PartnerServer(); var inventory = new PartnerServer(); var app = start(catalog, inventory)) {
            inventory.disconnectAfterBody = true;
            var uncertain = catchThrowableOfType(PartnerFailure.class, () -> app.getBean(Inventory.class).reserve(new Reservation("book", 2)));
            assertThat(uncertain.kind()).isEqualTo(PartnerFailure.Kind.TRANSPORT_FAILED);
            assertThat(uncertain.outcome()).isEqualTo(PartnerFailure.Outcome.UNKNOWN);
            assertThat(inventory.requests).hasSize(1);
            assertThat(inventory.bodies).containsExactly("{\"sku\":\"book\",\"quantity\":2}");
            assertThat(inventory.committed.get()).isEqualTo(1);
            inventory.server.stop(0);
            var refused = catchThrowableOfType(PartnerFailure.class, () -> app.getBean(Inventory.class).reserve(new Reservation("book", 2)));
            assertThat(refused.kind()).isEqualTo(PartnerFailure.Kind.CONNECT_FAILED);
            assertThat(refused.outcome()).isEqualTo(PartnerFailure.Outcome.NO_EFFECT);
            assertThat(inventory.requests).hasSize(1);
        }
    }
    @Test void cancellationStopsHeaderAndBodyReadsAndPreservesTheCallerInterrupt() throws Exception {
        for (boolean afterHeaders : new boolean[]{false, true}) {
            try (var catalog = new PartnerServer(); var inventory = new PartnerServer(); var app = start(catalog, inventory)) {
                catalog.block = true; catalog.slowBody = afterHeaders;
                var result = new java.util.concurrent.atomic.AtomicReference<Throwable>();
                var interrupted = new java.util.concurrent.atomic.AtomicBoolean();
                var caller = Thread.ofVirtual().start(() -> {
                    result.set(catchThrowable(() -> app.getBean(Catalog.class).find("book")));
                    interrupted.set(Thread.currentThread().isInterrupted());
                });
                try {
                    assertThat(catalog.entered.await(2, TimeUnit.SECONDS)).isTrue();
                    caller.interrupt(); caller.join(1500);
                    assertThat(caller.isAlive()).isFalse();
                    assertThat(result.get()).isInstanceOf(PartnerFailure.class);
                    assertThat(((PartnerFailure) result.get()).kind()).isEqualTo(PartnerFailure.Kind.CANCELLED);
                    assertThat(interrupted).isTrue();
                    assertThat(catalog.requests).hasSize(1);
                } finally { catalog.release.countDown(); caller.interrupt(); caller.join(3000); assertThat(caller.isAlive()).isFalse(); }
            }
        }
    }
    @Test void serviceTimeoutsCoverHeadersAndBodyWithoutDelayingTheOtherPartner() throws Exception {
        for (boolean afterHeaders : new boolean[]{false, true}) {
            try (var catalog = new PartnerServer(); var inventory = new PartnerServer();
                 var app = start(catalog, inventory, "--partners.catalog.request-timeout=PT0.2S")) {
                catalog.block = true; catalog.slowBody = afterHeaders;
                var worker = Executors.newSingleThreadExecutor();
                try {
                    long started = System.nanoTime();
                    var response = worker.submit(() -> catchThrowable(() -> app.getBean(Catalog.class).find("book")));
                    assertThat(catalog.entered.await(2, TimeUnit.SECONDS)).isTrue();
                    assertThat(app.getBean(Inventory.class).stock("book")).containsExactly(new Stock("north", 7));
                    var thrown = response.get(2, TimeUnit.SECONDS);
                    assertThat(thrown).isInstanceOf(PartnerFailure.class);
                    assertThat(((PartnerFailure) thrown).kind()).isEqualTo(PartnerFailure.Kind.RESPONSE_TIMEOUT);
                    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(1500));
                    assertThat(catalog.requests).hasSize(1);
                } finally { catalog.release.countDown(); worker.shutdownNow(); assertThat(worker.awaitTermination(3, TimeUnit.SECONDS)).isTrue(); }
            }
        }
    }
    @Test void invalidEmptyTruncatedAndOversizedBodiesHaveBoundedSafeFailures() throws Exception {
        try (var catalog = new PartnerServer(); var inventory = new PartnerServer(); var app = start(catalog, inventory, "--partners.catalog.max-response-bytes=64")) {
            for (String body : List.of("{remote-secret", "", "{\"sku\":\"book\",\"product_name\":\"" + "x".repeat(100) + "\"}", "{\"sku\":\"book\",\"product_name\":\"A Book\"}")) {
                catalog.bodyOverride = body; catalog.truncated = body.endsWith("A Book\"}");
                var thrown = catchThrowable(() -> app.getBean(Catalog.class).find("book"));
                assertThat(thrown).as("body=%s truncated=%s", body, catalog.truncated).isInstanceOf(PartnerFailure.class);
                var failure = (PartnerFailure) thrown;
                assertThat(failure.kind()).isEqualTo(body.length() > 64 ? PartnerFailure.Kind.RESPONSE_TOO_LARGE : PartnerFailure.Kind.BAD_RESPONSE);
                assertThat(failure.status()).isEqualTo(200);
                assertThat(failure).hasNoCause().hasMessageNotContaining("remote-secret");
            }
            assertThat(catalog.requests).hasSize(4);
        }
    }
    @Test void statusFailuresRetainSafeProtocolHeadersWithoutRemoteSecretsAndNeverRetry() throws Exception {
        try (var catalog = new PartnerServer(); var inventory = new PartnerServer(); var app = start(catalog, inventory)) {
            for (int status : new int[]{403, 429, 503, 302}) {
                inventory.status = status;
                var failure = catchThrowableOfType(PartnerFailure.class, () -> app.getBean(Inventory.class).reserve(new Reservation("book", 2)));
                assertThat(failure.status()).isEqualTo(status);
                assertThat(failure.kind()).isEqualTo(status < 400 ? PartnerFailure.Kind.UNEXPECTED_STATUS : status < 500 ? PartnerFailure.Kind.CLIENT_ERROR : PartnerFailure.Kind.SERVER_ERROR);
                assertThat(failure.outcome()).isEqualTo(status < 400 || status >= 500 ? PartnerFailure.Outcome.UNKNOWN : PartnerFailure.Outcome.NO_EFFECT);
                assertThat(failure.headers()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of("x-request-id", "remote-42", "retry-after", "1"));
                assertThat(failure).hasNoCause().hasMessageNotContaining("remote-secret").hasMessageNotContaining("inventory-secret");
            }
            assertThat(inventory.requests).hasSize(4);
        }
    }
    @Test void aggregationUsesTwoServicesWithHostJsonAndCustomizerAndHandlesNoContent() throws Exception {
        try (var catalog = new PartnerServer(); var inventory = new PartnerServer();
             var app = start(catalog, inventory)) {
            var offer = app.getBean(PartnerModule.class).offer("book");
            assertThat(offer.product()).isEqualTo(new Product("book", "A Book"));
            assertThat(offer.stock()).containsExactly(new Stock("north", 7));
            app.getBean(Inventory.class).reserve(new Reservation("book", 2));
            assertThat(catalog.requests).hasSize(1);
            assertThat(inventory.requests).hasSize(2);
            assertThat(catalog.requests.getFirst()).contains("GET /products/book", "Bearer catalog-secret", "host-policy");
            assertThat(inventory.requests).allSatisfy(request -> assertThat(request).contains("Bearer inventory-secret", "host-policy").doesNotContain("catalog-secret"));
            assertThat(inventory.bodies).contains("{\"sku\":\"book\",\"quantity\":2}");
        }
    }

    static ConfigurableApplicationContext start(PartnerServer catalog, PartnerServer inventory, String... overrides) {
        var args = new ArrayList<>(List.of("--spring.main.banner-mode=off", "--logging.level.root=ERROR",
                "--spring.jackson.property-naming-strategy=SNAKE_CASE",
                "--partners.catalog.base-url=" + catalog.url(), "--partners.inventory.base-url=" + inventory.url(),
                "--partners.catalog.token=catalog-secret", "--partners.inventory.token=inventory-secret"));
        args.addAll(List.of(overrides));
        var distinct = new java.util.LinkedHashMap<String, String>();
        for (String argument : args) distinct.put(argument.substring(0, argument.indexOf('=')), argument.substring(argument.indexOf('=') + 1));
        return new SpringApplicationBuilder(PartnerApplication.class, HostPolicy.class).web(WebApplicationType.NONE)
                .run(distinct.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).toArray(String[]::new));
    }
    @Configuration(proxyBeanMethods = false)
    static class HostPolicy {
        @Bean RestClientCustomizer hostPolicy(ObservationRegistry registry) {
            return builder -> builder.defaultHeader("X-Host-Policy", "host-policy").observationRegistry(registry);
        }
        @Bean(destroyMethod = "close") Tracing tracing() { return Tracing.newBuilder().traceId128Bit(true).sampler(brave.sampler.Sampler.ALWAYS_SAMPLE).build(); }
        @Bean Tracer tracer(Tracing tracing) { return new BraveTracer(tracing.tracer(), new BraveCurrentTraceContext(tracing.currentTraceContext()), new BraveBaggageManager()); }
        @Bean ObservationRegistry observationRegistry(Tracer tracer, Tracing tracing) {
            var registry = ObservationRegistry.create();
            registry.observationConfig().observationHandler(new PropagatingSenderTracingObservationHandler<>(tracer, new BravePropagator(tracing)));
            return registry;
        }
    }

    static final class PartnerServer implements AutoCloseable {
        final List<String> requests = new CopyOnWriteArrayList<>();
        final List<String> bodies = new CopyOnWriteArrayList<>();
        final List<String> traces = new CopyOnWriteArrayList<>();
        final List<String> spans = new CopyOnWriteArrayList<>();
        final java.util.concurrent.ExecutorService executor = Executors.newFixedThreadPool(4);
        final HttpServer server;
        volatile int status = 200;
        volatile String bodyOverride;
        volatile boolean truncated;
        volatile boolean block;
        volatile boolean slowBody;
        volatile boolean disconnectAfterBody;
        final java.util.concurrent.atomic.AtomicInteger committed = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger unavailable = new java.util.concurrent.atomic.AtomicInteger();
        volatile String retryAfter = "1";
        volatile boolean gzip;
        volatile int wireBodyBytes;
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        volatile boolean blockSecond;
        final CountDownLatch secondEntered = new CountDownLatch(1);
        final CountDownLatch secondRelease = new CountDownLatch(1);
        PartnerServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", this::handle);
            server.start();
        }
        String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " " + exchange.getRequestHeaders().getFirst("Authorization") + " " + exchange.getRequestHeaders().getFirst("X-Host-Policy"));
                bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                traces.add(String.valueOf(exchange.getRequestHeaders().getFirst("X-B3-TraceId")));
                spans.add(String.valueOf(exchange.getRequestHeaders().getFirst("X-B3-SpanId")));
                if (disconnectAfterBody) { committed.incrementAndGet(); return; }
                if (block && !slowBody) awaitRelease();
                if (blockSecond && requests.size() >= 2) {
                    secondEntered.countDown();
                    try { if (!secondRelease.await(5, TimeUnit.SECONDS)) throw new IOException("second fixture deadline"); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
                }
                int replyStatus = unavailable.getAndUpdate(value -> Math.max(0, value - 1)) > 0 ? 503 : status;
                if (replyStatus != 200) {
                    exchange.getResponseHeaders().set("X-Request-Id", "remote-42");
                    exchange.getResponseHeaders().set("Retry-After", retryAfter);
                    exchange.getResponseHeaders().set("Set-Cookie", "remote-secret");
                    exchange.getResponseHeaders().set("Location", url() + "/redirect-target");
                    byte[] bytes = "remote-secret".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(replyStatus, bytes.length); exchange.getResponseBody().write(bytes); return;
                }
                if (exchange.getRequestMethod().equals("POST")) { committed.incrementAndGet(); exchange.sendResponseHeaders(204, -1); return; }
                String body = exchange.getRequestURI().getPath().startsWith("/products/") ? "{\"sku\":\"book\",\"product_name\":\"A Book\"}" : "[{\"warehouse_name\":\"north\",\"available\":7}]";
                if (bodyOverride != null) body = bodyOverride;
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                if (gzip) {
                    var compressed = new java.io.ByteArrayOutputStream();
                    try (var output = new java.util.zip.GZIPOutputStream(compressed)) { output.write(bytes); }
                    bytes = compressed.toByteArray(); exchange.getResponseHeaders().set("Content-Encoding", "gzip");
                }
                wireBodyBytes = bytes.length;
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length + (truncated ? 3 : 0));
                if (block && slowBody) {
                    exchange.getResponseBody().write(bytes, 0, 1); exchange.getResponseBody().flush();
                    awaitRelease(); exchange.getResponseBody().write(bytes, 1, bytes.length - 1);
                } else exchange.getResponseBody().write(bytes);
                exchange.getResponseBody().flush();
            }
        }
        private void awaitRelease() throws IOException {
            entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("fixture release timeout"); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
        }
        @Override public void close() throws Exception { release.countDown(); secondRelease.countDown(); server.stop(0); executor.shutdownNow(); assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue(); }
    }
}
