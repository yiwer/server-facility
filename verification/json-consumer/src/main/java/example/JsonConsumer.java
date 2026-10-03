package example;

import cn.code91.facility.json.Jsons;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;

/** The same ordinary-jar consumer runs against the pre-expand and expanded artifacts. */
public final class JsonConsumer {
    enum Status { READY }
    public record Sample(long longId, BigDecimal amount, Status status, LocalDate localDate,
                         LocalTime localTime, LocalDateTime localDateTime, Instant instant,
                         Date legacyDate, String nullable, Optional<String> optional, Optional<String> empty) {}
    public record Item(String name, int quantity) {}
    public record Basket<T>(List<T> items) {}

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Application {
        @Bean
        Endpoint endpoint(ObjectMapper mapper, ObjectProvider<Jsons> jsons, Environment environment) {
            // Only this selection is different for the migration comparison; all assertions and HTTP routes are shared.
            return new Endpoint(environment.getProperty("consumer.access").equals("constructed")
                    ? new Jsons(mapper) : jsons.getObject());
        }

        @Bean
        @ConditionalOnProperty(name = "consumer.policy", havingValue = "custom")
        Jackson2ObjectMapperBuilderCustomizer policy() {
            return builder -> {
                var numbers = new SimpleModule("consumer-numbers");
                numbers.addSerializer(Long.class, ToStringSerializer.instance);
                numbers.addSerializer(Long.TYPE, ToStringSerializer.instance);
                numbers.addSerializer(BigDecimal.class, ToStringSerializer.instance);
                builder.modulesToInstall(numbers);
                builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
                builder.serializationInclusion(JsonInclude.Include.NON_EMPTY);
                builder.featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
            };
        }
    }

    @RestController
    static class Endpoint {
        private final Jsons jsons;
        Endpoint(Jsons jsons) { this.jsons = jsons; }
        @GetMapping(value = "/mvc", produces = "application/json")
        Sample mvc() { return sample(); }
        @GetMapping(value = "/service", produces = "application/json")
        String service() { return jsons.serializeUnsafe(sample()); }
        @PostMapping(value = "/echo", consumes = "application/json", produces = "application/json")
        Item echo(@RequestBody Item item) { return item; }
    }

    public static void main(String[] args) throws Exception {
        var access = args.length == 0 ? "injected" : args[0];
        require(Set.of("constructed", "injected").contains(access), "unknown access mode");
        Path artifact = Path.of(Jsons.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        require(artifact.toString().endsWith(".jar"), "ordinary installed jar required: " + artifact);
        System.out.println("JSON_ARTIFACT " + artifact + " sha256=" + HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(artifact))));
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            if (access.equals("constructed")) {
                for (String policy : List.of("default", "custom")) {
                    try (var app = start(access, policy)) { verify(app, client, policy); }
                }
            } else {
                try (var first = start(access, "default"); var second = start(access, "custom")) {
                    // Alternating real HTTP requests observe two simultaneously live applications.
                    verify(first, client, "default");
                    verify(second, client, "custom");
                    verify(first, client, "default");
                    first.close();
                    verify(second, client, "custom");
                    try (var rebuilt = start(access, "default")) {
                        verify(rebuilt, client, "default");
                        verify(second, client, "custom");
                    }
                }
            }
        }
        System.out.println("JSON_CONSUMER_OK " + access);
    }

    static ServletWebServerApplicationContext start(String access, String policy) {
        var application = new SpringApplication(Application.class);
        application.setRegisterShutdownHook(false);
        return (ServletWebServerApplicationContext) application.run("--server.address=127.0.0.1", "--server.port=0",
                "--spring.main.banner-mode=off", "--logging.level.root=WARN", "--consumer.access=" + access,
                "--facility.web.exception.use-problem-detail=true",
                "--consumer.policy=" + policy, "--spring.jackson.date-format=yyyy-MM-dd HH:mm:ss",
                "--spring.jackson.time-zone=" + (policy.equals("custom") ? "Asia/Shanghai" : "UTC"),
                "--server.tomcat.threads.max=4", "--server.tomcat.threads.min-spare=1");
    }

    static void verify(ServletWebServerApplicationContext app, HttpClient client, String policy) throws Exception {
        for (String path : List.of("/mvc", "/service")) {
            String expected = fixture(policy + (path.equals("/mvc") ? "-http.json" : ".json"));
            var response = send(app, client, path, null);
            require(response.statusCode() == 200, policy + path + " status=" + response.statusCode());
            require(response.headers().firstValue("content-type").orElse("").startsWith("application/json"), "JSON content type");
            require(response.body().equals(expected), policy + path + " expected=" + expected + " actual=" + response.body());
        }
        var jsons = app.getBean(Endpoint.class).jsons;
        require(jsons.serialize(null).get().equals("null"), "null root");
        var basket = jsons.deserialize(fixture("generic.json"), new TypeReference<Basket<Item>>() {});
        boolean custom = policy.equals("custom");
        require(custom ? basket.isErr() : basket.get().items().equals(List.of(new Item("中文 🧪", 2), new Item("second", 0))), "generic/unknown field policy");
        require(jsons.deserialize(fixture("invalid.txt"), Item.class).isErr(), "invalid JSON error channel");
        require(jsons.deserialize("", Item.class).isErr(), "empty JSON error channel");
        require(jsons.deserialize((String) null, Item.class).isErr(), "null source error channel");
        require(jsons.deserialize("{\"name\":\"bad\",\"quantity\":2147483648}", Item.class).isErr(), "integer overflow error channel");
        var trailing = jsons.deserialize(fixture("trailing.json"), Item.class);
        require(custom ? trailing.isErr() : trailing.get().equals(new Item("first", 1)), "trailing data policy");
        var echo = send(app, client, "/echo", "{\"name\":\"中文 🧪\",\"quantity\":2}");
        require(echo.statusCode() == 200 && echo.body().equals("{\"name\":\"中文 \\uD83E\\uDDEA\",\"quantity\":2}"), "UTF-8 HTTP input/output");
        var malformed = send(app, client, "/echo", fixture("invalid.txt"));
        require(malformed.statusCode() == 400, "malformed HTTP input status=" + malformed.statusCode() + " body=" + malformed.body());
        require(send(app, client, "/echo", fixture("trailing.json")).statusCode() == (custom ? 400 : 200), "HTTP trailing policy");
        System.out.println("GOLDEN_OK " + policy + " json/http/config/parse");
    }

    static HttpResponse<String> send(ServletWebServerApplicationContext app, HttpClient client, String path, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.getWebServer().getPort() + path))
                .timeout(Duration.ofSeconds(5));
        if (body == null) request.GET();
        else request.header("Content-Type", "application/json; charset=UTF-8").POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    static Sample sample() {
        return new Sample(9007199254740993L, new BigDecimal("3.140"), Status.READY, LocalDate.of(2024, 2, 29),
                LocalTime.of(23, 59, 58), LocalDateTime.of(2024, 2, 29, 23, 59, 58),
                Instant.parse("2024-02-29T15:59:58Z"), new Date(0), null, Optional.of("中文 🧪"), Optional.empty());
    }

    static String fixture(String name) throws Exception {
        try (var input = JsonConsumer.class.getResourceAsStream("/golden/" + name)) {
            return new String(Objects.requireNonNull(input, name).readAllBytes(), StandardCharsets.UTF_8).stripTrailing();
        }
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
