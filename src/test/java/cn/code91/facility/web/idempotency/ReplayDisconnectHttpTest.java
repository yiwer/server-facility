package cn.code91.facility.web.idempotency;

import cn.code91.facility.web.test.EmbeddedServletApplication;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.*;
import org.springframework.web.bind.annotation.*;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
class ReplayDisconnectHttpTest {
    @TempDir Path directory;
    @Test void actualPeerResetEvenWhenCaughtByTheControllerCannotLeaveAReceiptOrNewExecutionPermission() throws Exception {
        try (var app = EmbeddedServletApplication.start(directory, new Class<?>[]{AuthorizedReplayHttpTest.Config.class,
                AuthorizedReplayHttpTest.Authorized.class, AuthorizedReplayHttpTest.ControlledTime.class, DisconnectConfiguration.class});
             var client = HttpClient.newHttpClient()) {
            try (var socket = new Socket()) {
                socket.setReceiveBufferSize(1024); socket.connect(new InetSocketAddress("127.0.0.1", app.uri("/").getPort()));
                socket.setSoTimeout(5000);
                socket.getOutputStream().write("GET /disconnect-command HTTP/1.1\r\nHost: localhost\r\nIdempotency-Key: peer\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                assertThat(socket.getInputStream().read()).isEqualTo((int) 'H');
                assertThat(get(app, client, "/disconnect-ready", false).body()).isEqualTo("ready");
                socket.setSoLinger(true, 0);
            }
            assertThat(get(app, client, "/disconnect-release", false).statusCode()).isEqualTo(200);
            assertThat(get(app, client, "/disconnect-done", false).body()).isEqualTo("failed=true,effects=1");
            assertThat(get(app, client, "/disconnect-command", true).statusCode()).isEqualTo(503);
            assertThat(get(app, client, "/clock/1000000", false).statusCode()).isEqualTo(200);
            assertThat(get(app, client, "/disconnect-command", true).statusCode()).isEqualTo(503);
            assertThat(get(app, client, "/disconnect-done", false).body()).isEqualTo("failed=true,effects=1");
        }
    }
    private static HttpResponse<String> get(EmbeddedServletApplication app, HttpClient client, String path, boolean key) throws Exception {
        var request = HttpRequest.newBuilder(app.uri(path)).timeout(Duration.ofSeconds(7));
        if (key) request.header("Idempotency-Key", "peer");
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    @Configuration(proxyBeanMethods = false) @Import(DisconnectEndpoint.class) static class DisconnectConfiguration {
        @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> completion(DisconnectEndpoint endpoint) {
            var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter>();
            registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 2);
            registration.setFilter((request, response, chain) -> {
                try { chain.doFilter(request, response); }
                finally { if (((HttpServletRequest) request).getRequestURI().equals("/disconnect-command")) endpoint.done.countDown(); }
            });
            return registration;
        }
    }
    @RestController static class DisconnectEndpoint {
        private final CountDownLatch ready = new CountDownLatch(1), release = new CountDownLatch(1), done = new CountDownLatch(1);
        private final AtomicBoolean failed = new AtomicBoolean();
        private final AtomicInteger effects = new AtomicInteger();
        @Idempotent @GetMapping("/disconnect-command") void command(HttpServletResponse response) throws Exception {
            effects.incrementAndGet();
            response.getOutputStream().write("prefix".getBytes(StandardCharsets.US_ASCII)); response.flushBuffer(); ready.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("fixture release timed out");
            try {
                byte[] chunk = new byte[8192];
                for (int index = 0; index < 2048; index++) { response.getOutputStream().write(chunk); response.flushBuffer(); }
            } catch (java.io.IOException expected) { failed.set(true); }
        }
        @GetMapping("/disconnect-ready") String ready() throws InterruptedException { return ready.await(5, TimeUnit.SECONDS) ? "ready" : "timeout"; }
        @GetMapping("/disconnect-release") String release() { release.countDown(); return "released"; }
        @GetMapping("/disconnect-done") String done() throws InterruptedException {
            if (!done.await(5, TimeUnit.SECONDS)) return "timeout";
            return "failed=" + failed.get() + ",effects=" + effects.get();
        }
    }
}
