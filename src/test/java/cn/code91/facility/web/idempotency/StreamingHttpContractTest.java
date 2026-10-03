package cn.code91.facility.web.idempotency;

import cn.code91.facility.autoconfigure.FacilityIdempotencyAutoConfiguration;
import cn.code91.facility.web.test.EmbeddedServletApplication;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingHttpContractTest {
    @TempDir Path directory;

    @ParameterizedTest @ValueSource(strings = {"boolean-keep", "boolean-clear", "status", "full-keep", "full-clear"})
    @Timeout(20)
    void servlet61RedirectsPreserveLiveContainerSemanticsButNeverReplayDiscardedCapture(String mode) throws Exception {
        try (var app = EmbeddedServletApplication.start(directory, new Class<?>[]{WebConfiguration.class});
             var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(app.uri("/redirect/" + mode))
                    .header("Idempotency-Key", mode).GET().build();
            var first = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(first.statusCode()).isEqualTo(mode.startsWith("boolean") ? 302 : 307);
            assertThat(first.headers().firstValue("Location").orElseThrow()).endsWith("/destination");
            if (mode.endsWith("keep")) assertThat(first.body()).isEqualTo("prefix");
            else assertThat(first.body()).doesNotContain("prefix");
            var replay = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(replay.statusCode()).isEqualTo(503);
            assertThat(replay.headers().firstValue("Location")).isEmpty();
            assertThat(replay.body()).doesNotContain("prefix");
        }
    }

    @org.junit.jupiter.api.Test @Timeout(20)
    void servlet61CharsetOverloadCannotChangeEncodingAfterWriterSelection() throws Exception {
        try (var app = EmbeddedServletApplication.start(directory, new Class<?>[]{WebConfiguration.class});
             var client = HttpClient.newHttpClient()) {
            for (int attempt = 0; attempt < 2; attempt++) {
                var response = client.send(HttpRequest.newBuilder(app.uri("/charset"))
                        .header("Idempotency-Key", "charset").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.headers().firstValue("Content-Type").orElseThrow()).contains("charset=UTF-8");
                assertThat(response.body()).isEqualTo(new byte[]{(byte) 0xc3, (byte) 0xa9});
            }
        }
    }

    @org.junit.jupiter.api.Test @Timeout(20)
    void actualAsyncSseEmitterFlushesBeforeCompletion() throws Exception {
        try (var app = EmbeddedServletApplication.start(directory, new Class<?>[]{WebConfiguration.class});
             var client = HttpClient.newHttpClient()) {
            var producer = app.context().getBean(Endpoints.class);
            var pending = client.sendAsync(HttpRequest.newBuilder(app.uri("/async-events")).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            assertThat(producer.asyncReady.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                producer.emitter.send("first");
                var response = pending.get(5, TimeUnit.SECONDS);
                try (var body = response.body()) {
                    assertThat(new String(body.readNBytes(12), StandardCharsets.UTF_8)).isEqualTo("data:first\n\n");
                    producer.emitter.send("last");
                    producer.emitter.complete();
                    assertThat(new String(body.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("data:last\n\n");
                }
            } finally { producer.emitter.complete(); }
        }
    }

    @org.junit.jupiter.api.Test @Timeout(20)
    void committedFailureIsNotRewrittenOrSavedAsACompleteReplayAndErrorDispatchIsUnbuffered() throws Exception {
        try (var app = EmbeddedServletApplication.start(directory, new Class<?>[]{WebConfiguration.class});
             var client = HttpClient.newHttpClient()) {
            var first = client.send(HttpRequest.newBuilder(app.uri("/partial-error"))
                    .header("Idempotency-Key", "partial").GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(first.statusCode()).isEqualTo(200);
            assertThat(first.body()).isEqualTo("prefix");
            var retry = client.send(HttpRequest.newBuilder(app.uri("/partial-error"))
                    .header("Idempotency-Key", "partial").GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(retry.statusCode()).isEqualTo(503);
            var error = client.send(HttpRequest.newBuilder(app.uri("/send-error")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(error.statusCode()).isEqualTo(404);
            assertThat(error.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
            assertThat(error.body()).doesNotContain("SENTINEL");
        }
    }

    @org.junit.jupiter.api.Test @Timeout(20)
    void writerCompletesTrailingSurrogateButOrdinaryFlushPreservesASplitPair() throws Exception {
        try (var app = EmbeddedServletApplication.start(directory, new Class<?>[]{WebConfiguration.class});
             var client = HttpClient.newHttpClient()) {
            for (int attempt = 0; attempt < 2; attempt++) {
                var response = client.send(HttpRequest.newBuilder(app.uri("/characters"))
                        .header("Idempotency-Key", "characters").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).isEqualTo(new byte[]{(byte)0xf0, (byte)0x9f, (byte)0x98, (byte)0x80, 0x3f});
            }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"/download", "/events"}) @Timeout(20)
    void nonTargetPrefixArrivesWhileProducerIsStillWaiting(String path) throws Exception {
        try (var app = EmbeddedServletApplication.start(directory, new Class<?>[]{WebConfiguration.class});
             var client = HttpClient.newHttpClient()) {
            var producer = app.context().getBean(Endpoints.class);
            try {
                var response = client.sendAsync(HttpRequest.newBuilder(app.uri(path)).GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream()).get(5, TimeUnit.SECONDS);
                try (var body = response.body()) {
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(body.readNBytes(13)).isEqualTo("data: first\n\n".getBytes(StandardCharsets.UTF_8));
                    assertThat(producer.finished.getCount()).isEqualTo(1);
                    producer.release.countDown();
                    assertThat(new String(body.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("data: last\n\n");
                }
                assertThat(producer.finished.await(5, TimeUnit.SECONDS)).isTrue();
            } finally { producer.release.countDown(); }
        }
    }

    @Configuration(proxyBeanMethods = false) @EnableWebMvc
    @Import({FacilityIdempotencyAutoConfiguration.class, Endpoints.class,
            cn.code91.facility.autoconfigure.FacilityWebAutoConfiguration.class,
            org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration.class})
    static class WebConfiguration {
        @Bean DispatcherServlet dispatcherServlet() { return new DispatcherServlet(); }
        @Bean IdempotencyAuthorization authorization() {
            return (request, operation, body) -> new IdempotencyAuthorization.Command("fixture", "fixture", "stream-contract-v1");
        }
    }

    @RestController
    static class Endpoints {
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(1);
        final CountDownLatch asyncReady = new CountDownLatch(1);
        volatile org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter;
        @Idempotent @GetMapping("/redirect/{mode}")
        void redirect(@org.springframework.web.bind.annotation.PathVariable("mode") String mode,
                      HttpServletResponse response) throws Exception {
            response.getWriter().write("prefix");
            switch (mode) {
                case "boolean-keep" -> response.sendRedirect("/destination", false);
                case "boolean-clear" -> response.sendRedirect("/destination", true);
                case "status" -> response.sendRedirect("/destination", 307);
                case "full-keep" -> response.sendRedirect("/destination", 307, false);
                case "full-clear" -> response.sendRedirect("/destination", 307, true);
                default -> throw new IllegalArgumentException(mode);
            }
        }
        @Idempotent @GetMapping("/charset")
        void charset(HttpServletResponse response) throws Exception {
            response.setContentType("text/plain");
            response.setCharacterEncoding(StandardCharsets.UTF_8);
            var writer = response.getWriter();
            response.setCharacterEncoding(StandardCharsets.ISO_8859_1);
            writer.write("é");
        }
        @GetMapping("/async-events") org.springframework.web.servlet.mvc.method.annotation.SseEmitter asyncEvents() {
            emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(10_000L);
            asyncReady.countDown();
            return emitter;
        }
        @Idempotent @GetMapping("/partial-error")
        void partialError(HttpServletResponse response) throws Exception {
            response.getOutputStream().write("prefix".getBytes(StandardCharsets.UTF_8));
            response.flushBuffer();
            throw new IllegalStateException("SENTINEL");
        }
        @GetMapping("/send-error") void sendError(HttpServletResponse response) throws Exception {
            response.sendError(404, "SENTINEL");
        }
        @Idempotent @GetMapping("/characters")
        void characters(HttpServletResponse response) throws Exception {
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write('\ud83d');
            response.flushBuffer();
            response.getWriter().write('\ude00');
            response.getWriter().write('\ud83d');
        }
        @GetMapping({"/download", "/events"})
        void stream(HttpServletResponse response) throws Exception {
            response.setContentType("text/event-stream");
            try {
                response.getOutputStream().write("data: first\n\n".getBytes(StandardCharsets.UTF_8));
                response.flushBuffer();
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("client did not receive prefix");
                response.getOutputStream().write("data: last\n\n".getBytes(StandardCharsets.UTF_8));
            } finally { finished.countDown(); }
        }
    }
}
