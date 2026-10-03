package cn.code91.facility.web.filter;

import cn.code91.facility.web.test.EmbeddedServletApplication;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.*;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual Servlet/Callable process; assertions cross the HTTP and host-advice seams. */
public final class MdcFailureProcess {
    @RestControllerAdvice @Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
    static class HostAdvice {
        static final BlockingQueue<Exception> failures = new LinkedBlockingQueue<>();
        @ExceptionHandler(Exception.class) org.springframework.http.ResponseEntity<String> failure(Exception failure) {
            failures.add(failure); return org.springframework.http.ResponseEntity.status(500).body("safe-failure");
        }
    }
    @RestController static class FailingCallable {
        @GetMapping("/callable-failure") Callable<String> fail() { return () -> { throw FaultingMdcProvider.PRIMARY; }; }
    }
    public static void main(String[] args) throws Exception {
        try (var app = EmbeddedServletApplication.start(Path.of(args[0]), new Class<?>[]{HostAdvice.class, FailingCallable.class, RequestBoundaryHttpTest.Config.class});
             var client = HttpClient.newHttpClient()) {
            var probe = app.context().getBean(RequestBoundaryHttpTest.Probe.class);
            probe.workerObservation = true; probe.releaseWorker.countDown();
            var request = HttpRequest.newBuilder(app.uri(System.getProperty("facility.test.mdcFault").equals("post") ? "/callable-failure" : "/callable")).timeout(Duration.ofSeconds(8))
                    .header("Fixture-User", "alice").header("X-Trace-Id", "request-a").GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(500);
            if (!System.getProperty("facility.test.mdcFault").equals("servlet")) assertThat(response.body()).isEqualTo("safe-failure");
            else assertThat(response.body()).contains("Internal server error").doesNotContain("MDC-primary", "MDC-rollback");
            if (System.getProperty("facility.test.mdcFault").equals("servlet")) {
                var failure = probe.servletFailures.poll(5, TimeUnit.SECONDS);
                assertThat(failure).isSameAs(FaultingMdcProvider.PRIMARY);
                assertThat(failure.getSuppressed()).containsExactly(FaultingMdcProvider.SECONDARY);
                var first = probe.exits.poll(5, TimeUnit.SECONDS); assertThat(first).isNotNull(); assertThat(first.user()).isNull(); assertThat(first.trace()).isNull();
                var second = client.send(HttpRequest.newBuilder(app.uri("/who")).timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(second.body()).startsWith("anonymous|").endsWith("|127.0.0.1");
                var clean = probe.exits.poll(5, TimeUnit.SECONDS); assertThat(clean).isNotNull(); assertThat(clean.user()).isNull();
                assertThat(clean.thread()).isEqualTo(first.thread());
                System.out.println("MDC_RECOVERY_OK mode=servlet"); return;
            }
            var worker = probe.workerExits.poll(5, TimeUnit.SECONDS);
            assertThat(worker).isNotNull(); assertThat(worker.user()).as("actual worker after partial install").isNull();
            assertThat(worker.trace()).isEqualTo("worker-host");
            Exception primary = HostAdvice.failures.poll(5, TimeUnit.SECONDS);
            assertThat(primary).isSameAs(FaultingMdcProvider.PRIMARY);
            if (java.util.Set.of("rollback", "post").contains(System.getProperty("facility.test.mdcFault")))
                assertThat(primary.getSuppressed()).containsExactly(FaultingMdcProvider.SECONDARY);
            var next = client.send(HttpRequest.newBuilder(app.uri("/callable")).timeout(Duration.ofSeconds(8))
                    .header("X-Trace-Id", "request-b").GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(next.body()).isEqualTo("anonymous|worker-host");
            var reused = probe.workerExits.poll(5, TimeUnit.SECONDS); assertThat(reused).isNotNull();
            assertThat(reused.thread()).isEqualTo(worker.thread()); assertThat(reused.user()).isNull();
            System.out.println("MDC_RECOVERY_OK mode=" + System.getProperty("facility.test.mdcFault"));
        }
    }
}
