package cn.code91.facility.web.filter;

import cn.code91.facility.autoconfigure.FacilityWebAutoConfiguration;
import cn.code91.facility.autoconfigure.FacilityIdempotencyAutoConfiguration;
import cn.code91.facility.web.test.EmbeddedServletApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class RepeatableHttpContractTest {
    @TempDir Path directory;

    @Test @Timeout(20)
    void slowChunkedWriterCompletesOnlyAfterTheFinalChunkAndReadsRemainIdentical() throws Exception {
        var waiting = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        try (var app = application(); var client = HttpClient.newHttpClient()) {
            var publisher = HttpRequest.BodyPublishers.ofInputStream(() -> new java.io.InputStream() {
                int chunk;
                @Override public int read() { throw new AssertionError("bulk publisher expected"); }
                @Override public int read(byte[] target, int offset, int length) throws java.io.IOException {
                    if (chunk == 2) return -1;
                    if (chunk == 1) {
                        waiting.countDown();
                        try { if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new java.io.IOException("release deadline"); }
                        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new java.io.IOException(failure); }
                    }
                    byte[] bytes = (chunk++ == 0 ? "ab" : "cd").getBytes(StandardCharsets.UTF_8);
                    System.arraycopy(bytes, 0, target, offset, bytes.length);
                    return bytes.length;
                }
            });
            var pending = client.sendAsync(HttpRequest.newBuilder(app.uri("/repeat"))
                    .header("Content-Type", "application/json").POST(publisher).build(), HttpResponse.BodyHandlers.ofString());
            try {
                assertThat(waiting.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(pending).isNotDone();
                release.countDown();
                var response = pending.get(5, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).isEqualTo("61626364:61626364");
            } finally { release.countDown(); }
        }
    }

    @Test @Timeout(30)
    void actualKnownAndChunkedBodiesAtTheBudgetUseOneSharedErrorProtocol() throws Exception {
        try (var app = application(); var client = HttpClient.newHttpClient()) {
            for (boolean chunked : new boolean[]{false, true}) {
                for (String payload : new String[]{"", "abc", "éé", "ééx"}) {
                    byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
                    var publisher = chunked ? HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes))
                            : HttpRequest.BodyPublishers.ofByteArray(bytes);
                    var response = client.send(HttpRequest.newBuilder(app.uri("/repeat"))
                            .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/problem+json;charset=UTF-8")
                            .POST(publisher).build(), HttpResponse.BodyHandlers.ofString());
                    if (bytes.length <= 4) {
                        assertThat(response.statusCode()).isEqualTo(200);
                        String expected = HexFormat.of().formatHex(bytes);
                        assertThat(response.body()).isEqualTo(expected + ":" + expected);
                    } else {
                        assertThat(response.statusCode()).isEqualTo(413);
                        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
                        assertThat(new ObjectMapper().readTree(response.body()).path("status").asInt()).isEqualTo(413);
                    }
                }
            }
            var registrations = app.context().getServletContext().getFilterRegistrations();
            assertThat(registrations.keySet()).contains("repeatableRequestFilter", "idempotencyFilterRegistration");
            assertThat(registrations.values().stream().filter(r -> r.getClassName().equals(RepeatableRequestFilter.class.getName())).count()).isEqualTo(1);
        }
    }

    @Test @Timeout(20)
    void realCharsetAndPseudoMediaTypeRespectSelectionAndMalformedCharsetIs400() throws Exception {
        try (var app = application(); var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(app.uri("/characters"))
                    .header("Content-Type", "application/json;charset=ISO-8859-1")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[]{(byte)0xe9})).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.body()).isEqualTo("é:é");
            response = client.send(HttpRequest.newBuilder(app.uri("/repeat"))
                    .header("Content-Type", "application/json-unknown")
                    .POST(HttpRequest.BodyPublishers.ofString("abcde")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("6162636465:");
            response = client.send(HttpRequest.newBuilder(app.uri("/repeat"))
                    .header("Content-Type", "application/json;charset=not-real-charset")
                    .POST(HttpRequest.BodyPublishers.ofString("abc")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(new ObjectMapper().readTree(response.body()).path("status").asInt()).isEqualTo(400);
        }
    }

    private EmbeddedServletApplication application() {
        return EmbeddedServletApplication.start(directory, new Class<?>[]{WebConfiguration.class},
                "facility.web.repeatable-request.enabled=true", "facility.web.repeatable-request.max-body-bytes=4",
                "facility.web.access-log.enabled=false");
    }

    @Configuration(proxyBeanMethods = false) @EnableWebMvc
    @Import({FacilityWebAutoConfiguration.class, FacilityIdempotencyAutoConfiguration.class, Endpoints.class,
            org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration.class})
    static class WebConfiguration {
        @Bean DispatcherServlet dispatcherServlet() { return new DispatcherServlet(); }
    }
    @RestController static class Endpoints {
        @PostMapping("/repeat") String repeat(HttpServletRequest request) throws Exception {
            return HexFormat.of().formatHex(request.getInputStream().readAllBytes()) + ":"
                    + HexFormat.of().formatHex(request.getInputStream().readAllBytes());
        }
        @PostMapping(value="/characters", produces="text/plain;charset=UTF-8") String characters(HttpServletRequest request) throws Exception {
            return request.getReader().readLine() + ":" + request.getReader().readLine();
        }
    }
}
