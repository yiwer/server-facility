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
    @Import({FacilityIdempotencyAutoConfiguration.class, Endpoints.class})
    static class WebConfiguration {
        @Bean DispatcherServlet dispatcherServlet() { return new DispatcherServlet(); }
    }

    @RestController
    static class Endpoints {
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(1);
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
