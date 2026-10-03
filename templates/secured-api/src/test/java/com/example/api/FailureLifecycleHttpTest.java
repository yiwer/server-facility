package com.example.api;

import com.example.fixtures.LifecycleFixture;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class FailureLifecycleHttpTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void failuresAndDisconnectRestoreIdentityBeforeTheNextRequest(boolean virtual) throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, new Class<?>[]{LifecycleFixture.class},
                "--spring.threads.virtual.enabled=" + virtual,
                "--spring.task.execution.pool.core-size=1", "--spring.task.execution.pool.max-size=1")) {
            @SuppressWarnings("unchecked") var events = (BlockingQueue<LifecycleFixture.Cleanup>) app.context.getBean("cleanupEvents");
            for (var scenario : Map.of("filter-error", 500, "mvc-error", 500, "callable-error", 500, "timeout", 503, "servlet-error", 418).entrySet()) {
                var response = app.get("/api/greeting/" + scenario.getKey(), issuer.token());
                assertThat(response.statusCode()).as(scenario.getKey()).isEqualTo(scenario.getValue());
                assertThat(response.body()).doesNotContain("SECRET", "Exception", issuer.token());
                assertThat(response.headers().firstValue("X-Trace-Id")).isEmpty();
                assertThat(tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body())
                        .path("traceId").asString()).matches("(?:[0-9a-f]{16}|[0-9a-f]{32}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");
                if (scenario.getKey().equals("servlet-error")) assertThat(response.headers().firstValue("X-Actual-Dispatch")).contains("ERROR");
                var next = app.get("/api/greeting/probe", issuer.token("a", Map.of("sub", "bob"), Set.of()));
                assertThat(next.statusCode()).isEqualTo(200);
                assertThat(next.body()).contains("bob").doesNotContain("alice");
                assertThat(app.get("/api/greeting/probe", null).statusCode()).isEqualTo(401);
            }
            var controller = app.context.getBean(LifecycleFixture.ProbeController.class);
            try (var socket = new Socket("127.0.0.1", app.context.getWebServer().getPort())) {
                socket.getOutputStream().write(("GET /api/greeting/disconnect HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer "
                        + issuer.token() + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                assertThat(controller.entered.await(5, TimeUnit.SECONDS)).isTrue();
                socket.setSoLinger(true, 0);
            } finally { controller.release.countDown(); }
            assertThat(controller.exited.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(app.get("/api/greeting/deferred", issuer.token("a", Map.of("sub", "carol"), Set.of())).body()).contains("carol").doesNotContain("alice");
            assertThat(app.get("/api/greeting/probe", null).statusCode()).isEqualTo(401);
            assertThat(events).isNotEmpty().allSatisfy(event -> assertThat(event.authentication()).as(event.path() + event.dispatch()).isNull());
            assertThat(events).anySatisfy(event -> assertThat(event.dispatch()).isEqualTo(jakarta.servlet.DispatcherType.ERROR));
        }
    }
}
