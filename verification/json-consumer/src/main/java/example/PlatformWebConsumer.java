package example;

import cn.code91.facility.json.Jsons;
import cn.code91.facility.web.filter.*;
import cn.code91.facility.web.idempotency.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Production web starter, installed library jar and actual loopback HTTP; no test fixtures. */
public final class PlatformWebConsumer {
    @SpringBootConfiguration @EnableAutoConfiguration
    static class Application {
        @Bean Endpoint endpoint() { return new Endpoint(); }
        @Bean IdempotencyAuthorization authorization() {
            return (request, operation, body) -> new IdempotencyAuthorization.Command("platform-fixture", "fixture", "empty-v1");
        }

        @Bean @ConditionalOnProperty(name="platform.user", havingValue="true")
        JsonMapper applicationMapper() {
            return new JacksonJsonHttpMessageConverter().getMapper().rebuild()
                    .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
        }
        @Bean @ConditionalOnProperty(name="platform.user", havingValue="true")
        TraceIdFilter applicationTrace(FacilityWebTraceProperties props) {
            return new ObservedTrace(props);
        }
        @Bean @ConditionalOnProperty(name="platform.user", havingValue="true")
        RepeatableRequestFilter applicationRepeatable(FacilityWebRepeatableRequestProperties props) {
            return new ObservedRepeatable(props);
        }
        @Bean @ConditionalOnProperty(name="platform.user", havingValue="true")
        IdempotencyFilter applicationCapture() { return new ObservedCapture(); }
    }

    static final class ObservedTrace extends TraceIdFilter {
        ObservedTrace(FacilityWebTraceProperties props) { super(props); }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            observe(request, "trace");
            super.doFilterInternal(request, response, chain);
        }
    }
    static final class ObservedRepeatable extends RepeatableRequestFilter {
        ObservedRepeatable(FacilityWebRepeatableRequestProperties props) { super(props); }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            observe(request, "repeatable");
            super.doFilterInternal(request, response, chain);
        }
    }
    static final class ObservedCapture extends IdempotencyFilter {
        ObservedCapture() { super(16); }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            observe(request, "capture");
            super.doFilterInternal(request, response, chain);
        }
    }
    static void observe(HttpServletRequest request, String stage) {
        Object previous = request.getAttribute("platform.order");
        request.setAttribute("platform.order", previous == null ? stage : previous + "," + stage);
    }

    public record Shape(String camelName) {}
    @RestController
    static class Endpoint {
        final AtomicInteger calls = new AtomicInteger();
        @GetMapping("/shape") Shape shape() { return new Shape("application"); }
        @GetMapping(value="/order", produces="text/plain")
        String order(HttpServletRequest request) { return String.valueOf(request.getAttribute("platform.order")); }
        @PostMapping(value="/read", produces="text/plain")
        String read(HttpServletRequest request) throws IOException {
            return new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8) + "|"
                    + new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
        @Idempotent @GetMapping(value="/replay", produces="text/plain")
        String replay() { return "once-" + calls.incrementAndGet(); }
        @GetMapping("/error")
        void error(HttpServletResponse response) throws IOException { response.sendError(404, "PRIVATE_SENTINEL"); }
    }

    public static void main(String[] args) throws Exception {
        String scenario = args[0];
        require(Set.of("default", "user", "disabled").contains(scenario), "unknown scenario");
        boolean user = scenario.equals("user"), disabled = scenario.equals("disabled");
        for (String absent : List.of("org.apache.tika.Tika", "org.apache.poi.ss.usermodel.Workbook",
                "org.hibernate.validator.HibernateValidator", "org.junit.jupiter.api.Test")) {
            try { Class.forName(absent); throw new AssertionError("optional/test graph leaked: " + absent); }
            catch (ClassNotFoundException expected) { }
        }
        var application = new SpringApplication(Application.class);
        application.setRegisterShutdownHook(false);
        var properties = new ArrayList<>(List.of("--server.address=127.0.0.1", "--server.port=0", "--spring.main.banner-mode=off",
                "--logging.level.root=WARN", "--server.tomcat.threads.max=4", "--server.tomcat.threads.min-spare=1",
                "--server.shutdown=immediate", "--facility.web.exception.use-problem-detail=true",
                "--platform.user=" + user, "--facility.web.repeatable-request.enabled=" + user,
                "--facility.web.repeatable-request.max-body-bytes=8", "--facility.idempotency.enabled=" + !disabled,
                "--facility.web.trace.enabled=" + !disabled, "--facility.web.trace.accept-inbound=" + user));
        try (var app = (ServletWebServerApplicationContext)application.run(properties.toArray(String[]::new));
             var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            JsonMapper mapper = app.getBean(JsonMapper.class);
            require(app.getBean(Jsons.class).mapper() == mapper, "application JSON mapper identity");
            var converters = app.getBean(RequestMappingHandlerAdapter.class).getMessageConverters().stream()
                    .filter(JacksonJsonHttpMessageConverter.class::isInstance).map(JacksonJsonHttpMessageConverter.class::cast).toList();
            require(!converters.isEmpty() && converters.stream().allMatch(converter -> converter.getMapper() == mapper), "MVC mapper identity");
            if (user) require(mapper == app.getBean("applicationMapper"), "user mapper backoff");
            var shape = send(app, client, "/shape", null);
            require(shape.statusCode() == 200, "shape status");
            require(shape.body().equals(user ? "{\"camel_name\":\"application\"}" : "{\"camelName\":\"application\"}"), "host policy on actual HTTP");
            var trace = shape.headers().firstValue("X-Trace-Id");
            require(disabled ? trace.isEmpty() : user ? trace.orElseThrow().equals("matrix-trace")
                    : trace.orElseThrow().matches("[0-9a-f]{32}"), "trace enable/disable and explicit inbound trust");

            var registrations = app.getServletContext().getFilterRegistrations();
            for (var entry : registrations.entrySet()) System.out.println("SERVLET_FILTER " + entry.getKey() + "=" + entry.getValue().getClassName());
            require(count(registrations, user ? ObservedRepeatable.class : RepeatableRequestFilter.class) == (user ? 1 : 0), "repeatable registered exactly when enabled");
            require(count(registrations, user ? ObservedCapture.class : IdempotencyFilter.class) == (disabled ? 0 : 1), "capture registered once");
            if (user) {
                require(count(registrations, ObservedTrace.class) == 0, "trace is invoked by request context boundary, not auto-registered again");
                require(send(app, client, "/order", null).body().equals("trace,repeatable,capture"), "actual filter execution order");
            }
            require(registrations.values().stream().filter(value -> value.getClassName().equals(
                    "cn.code91.facility.web.filter.FacilityRequestContextFilter")).count() == 1, "one request context boundary");
            require(registrations.values().stream().filter(value -> value.getClassName().equals(
                    "cn.code91.facility.web.exception.FacilityHttpErrorFilter")).count() == 1, "one HTTP error boundary");

            var body = send(app, client, "/read", "ab");
            require(body.statusCode() == 200 && body.body().equals(user ? "ab|ab" : "ab|"), "repeatable body semantics");
            var over = send(app, client, "/read", "123456789");
            require(over.statusCode() == (user ? 413 : 200), "explicit request budget");
            if (user) {
                require(over.headers().firstValue("X-Trace-Id").orElseThrow().equals("matrix-trace"), "trace precedes rejection");
                require(mapper.readTree(over.body()).path("status").asInt() == 413, "error filter surrounds repeatable rejection");
            }
            var first = send(app, client, "/replay", null);
            var second = send(app, client, "/replay", null);
            if (disabled) {
                require(first.statusCode() == 503 && second.statusCode() == 503, "disabled provider refuses annotated execution");
                require(mapper.readTree(first.body()).path("status").asInt() == 503, "disabled capability safe HTTP policy");
                require(app.getBean(Endpoint.class).calls.get() == 0, "missing capability never executes unprotected command");
            } else {
                require(first.statusCode() == 200 && second.statusCode() == 200 && first.body().equals("once-1"), "initial command response");
                require(second.body().equals("once-1"), "capture registration and qualified replay");
                require(app.getBean(Endpoint.class).calls.get() == 1, "only qualified first command executes");
            }
            var error = send(app, client, "/error", null);
            require(error.statusCode() == 404 && !error.body().contains("PRIVATE_SENTINEL"), "actual ERROR dispatch safe status");
            require(mapper.readTree(error.body()).path("status").asInt() == 404, "ERROR dispatch JSON protocol");
        }
        System.out.println("PLATFORM_WEB_OK " + scenario);
    }

    static long count(Map<String, ? extends FilterRegistration> registrations, Class<?> type) {
        return registrations.values().stream().filter(value -> value.getClassName().equals(type.getName())).count();
    }
    static HttpResponse<String> send(ServletWebServerApplicationContext app, HttpClient client, String path, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.getWebServer().getPort() + path))
                .timeout(Duration.ofSeconds(5)).header("X-Trace-Id", "matrix-trace").header("Idempotency-Key", "matrix-command");
        if (body == null) request.GET();
        else request.header("Content-Type", "text/plain;charset=UTF-8").POST(HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
