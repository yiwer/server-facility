package cn.code91.facility.web.idempotency;

import cn.code91.facility.autoconfigure.FacilityIdempotencyAutoConfiguration;
import cn.code91.facility.web.test.EmbeddedServletApplication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.lang.management.ManagementFactory;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

/** Isolated resource workload, deliberately launched without a coverage agent and with a hard heap cap. */
public final class StreamingResourceProcess {
    public static void main(String[] args) throws Exception {
        long largestRetained = 0;
        try (var app = EmbeddedServletApplication.start(Path.of(args[0]), new Class<?>[]{ConfigurationUnderTest.class},
                    "facility.idempotency.max-response-bytes=65536"); var client = HttpClient.newHttpClient()) {
            consume(app, client, "/bulk", 8L * 1024 * 1024, "warm");
            long baseline = retained();
            for (String path : new String[]{"/bulk", "/capture"}) {
                for (long size : new long[]{64L * 1024 * 1024, 256L * 1024 * 1024}) {
                    consume(app, client, path, size, path + size);
                    long used = retained();
                    largestRetained = Math.max(largestRetained, used);
                    if (used - baseline > 16L * 1024 * 1024) throw new AssertionError("retained heap grew: " + baseline + " -> " + used);
                    System.out.println("RESOURCE_SAMPLE path=" + path + " bytes=" + size + " retained=" + used);
                }
            }
            System.out.println("RESOURCE_OK heapMax=" + Runtime.getRuntime().maxMemory() + " baseline=" + baseline
                    + " largestRetained=" + largestRetained + " growthLimit=16777216 captureBudget=65536");
        }
    }

    private static long retained() {
        System.gc();
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    private static void consume(EmbeddedServletApplication app, HttpClient client, String path, long size, String key) throws Exception {
        var response = client.send(HttpRequest.newBuilder(app.uri(path + "?bytes=" + size))
                .timeout(Duration.ofSeconds(15)).header("Idempotency-Key", key).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) throw new AssertionError("unexpected status " + response.statusCode());
        long count = 0;
        byte[] buffer = new byte[8192];
        try (var input = response.body()) {
            for (int read; (read = input.read(buffer)) != -1;) {
                count += read;
                if (read > 0 && (buffer[0] != 90 || buffer[read - 1] != 90)) throw new AssertionError("corrupt body");
            }
        }
        if (count != size) throw new AssertionError("truncated: " + count + " != " + size);
    }

    @Configuration(proxyBeanMethods = false) @EnableWebMvc
    @Import({FacilityIdempotencyAutoConfiguration.class, Endpoints.class})
    static class ConfigurationUnderTest {
        @Bean DispatcherServlet dispatcherServlet() { return new DispatcherServlet(); }
        @Bean IdempotencyAuthorization authorization() {
            return (request, operation, body) -> new IdempotencyAuthorization.Command("resource-fixture", "fixture",
                    "size-v1:" + request.getParameter("bytes"));
        }
    }
    @RestController static class Endpoints {
        @GetMapping("/bulk") void bulk(HttpServletRequest request, HttpServletResponse response) throws Exception { write(request, response); }
        @Idempotent @GetMapping("/capture") void captured(HttpServletRequest request, HttpServletResponse response) throws Exception { write(request, response); }
        private void write(HttpServletRequest request, HttpServletResponse response) throws Exception {
            long remaining = Long.parseLong(request.getParameter("bytes"));
            response.setContentLengthLong(remaining);
            byte[] chunk = new byte[8192];
            java.util.Arrays.fill(chunk, (byte)90);
            while (remaining > 0) {
                int count = (int)Math.min(remaining, chunk.length);
                response.getOutputStream().write(chunk, 0, count);
                remaining -= count;
            }
        }
    }
}
