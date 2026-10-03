package cn.code91.facility.web.exception;

import cn.code91.facility.autoconfigure.FacilityWebAutoConfiguration;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HttpErrorContractTest {
    @TempDir Path directory;

    @Test
    void defaultHttpFailureHasProblemStatusAndNeverExposesItsCause() throws Exception {
        try (var app = application(); var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(app.uri("/failure"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(response.headers().firstValue("Content-Type").orElse(""))
                    .startsWith("application/problem+json");
            var body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body());
            assertThat(body.path("status").asInt()).isEqualTo(500);
            assertThat(body.path("detail").asString()).isEqualTo("Internal server error");
            assertThat(response.body()).doesNotContain("SECRET-INPUT", "IllegalStateException", "stackTrace");
        }
    }

    @Test
    void methodRejectionRetainsTheFrameworkAllowHeader() throws Exception {
        try (var app = application(); var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(app.uri("/success"))
                    .timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(405);
            assertThat(response.headers().firstValue("Allow").orElse("")).contains("GET");
        }
    }

    @Test
    void filterFailureUsesTheSameSafeHttpProtocol() throws Exception {
        try (var app = application(); var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(app.uri("/filter-failure"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
            assertThat(tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body()).path("detail").asString()).isEqualTo("Internal server error");
            assertThat(response.body()).doesNotContain("SECRET-INPUT", "IllegalStateException");
        }
    }

    @Test
    void errorDispatchStaysSafeWhenHostRegistersOnlyOneStatus() throws Exception {
        try (var app = application(); var client = HttpClient.newHttpClient()) {
            for (int status : new int[]{404, 502}) {
                var response = client.send(HttpRequest.newBuilder(app.uri("/send-error?status=" + status)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(status);
                assertThat(response.headers().firstValue("X-Dispatcher")).contains("ERROR");
                assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
                assertThat(response.body()).doesNotContain("SECRET-INPUT", "Tomcat", "stackTrace");
            }
        }
    }

    @Test
    void problemGoldenHasStableCodeTraceAndNoInputDerivedInstance() throws Exception {
        try (var app = application(); var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(app.uri("/failure/SECRET-INPUT?token=SECRET-INPUT"))
                    .header("X-Trace-Id", "contract-trace").GET().build(), HttpResponse.BodyHandlers.ofString());
            var body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body());
            assertThat(body).isEqualTo(tools.jackson.databind.json.JsonMapper.builder().build().readTree("""
                    {"type":"about:blank","title":"Internal Server Error","status":500,"detail":"Internal server error",
                     "instance":"urn:facility:error:contract-trace","code":500,"traceId":"contract-trace","errors":[]}
                    """));
            assertThat(body.path("code").asInt()).isEqualTo(500);
            assertThat(body.path("traceId").asString()).isEqualTo("contract-trace");
            assertThat(body.path("instance").asString()).isEqualTo("urn:facility:error:contract-trace");
            assertThat(body.path("errors").isArray()).isTrue();
            assertThat(body.path("errors")).isEmpty();
            assertThat(response.body()).doesNotContain("SECRET-INPUT");
            var success = client.send(HttpRequest.newBuilder(app.uri("/success")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(success.statusCode()).isEqualTo(200);
            assertThat(tools.jackson.databind.json.JsonMapper.builder().build().readTree(success.body())).isEqualTo(tools.jackson.databind.json.JsonMapper.builder().build().readTree("{\"name\":\"sample\"}"));
        }
    }

    @Test
    void statusMatrixRetainsProtocolAndSafeBodies() throws Exception {
        try (var app = application(); var client = HttpClient.newHttpClient()) {
            var cases = Map.of("/number?n=SECRET-INPUT", 400, "/missing/SECRET-INPUT", 404,
                    "/status/409", 409, "/status/422", 422, "/rate", 429, "/async", 503,
                    "/return-invalid", 500);
            for (var entry : cases.entrySet()) {
                var response = client.send(HttpRequest.newBuilder(app.uri(entry.getKey()))
                        .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).as(entry.getKey()).isEqualTo(entry.getValue());
                assertThat(tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body()).path("status").asInt()).isEqualTo(entry.getValue());
                assertThat(response.body()).doesNotContain("SECRET-INPUT", "Exception", "stackTrace");
                if (entry.getValue() == 429) assertThat(response.headers().firstValue("Retry-After")).contains("2");
            }
            var unacceptable = client.send(HttpRequest.newBuilder(app.uri("/success")).header("Accept", "text/plain")
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(unacceptable.statusCode()).isEqualTo(406);
            var unsupported = client.send(HttpRequest.newBuilder(app.uri("/body")).header("Content-Type", "text/plain")
                    .POST(HttpRequest.BodyPublishers.ofString("SECRET-INPUT")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(unsupported.statusCode()).isEqualTo(415);
            assertThat(unsupported.headers().firstValue("Accept").orElse("")).contains("application/json");
        }
    }

    @Test
    void validationExposesFieldIdentityWithoutRejectedValueOrConstraintMessage() throws Exception {
        try (var app = application(); var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(app.uri("/body")).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"password\":\"SECRET-INPUT\"}")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(400);
            var body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body());
            assertThat(body.path("errors").size()).isEqualTo(1);
            assertThat(body.path("errors").get(0).path("field").asString()).isEqualTo("password");
            assertThat(body.path("errors").get(0).path("code").asString()).isEqualTo("invalid");
            assertThat(body.path("errors").get(0).path("message").asString()).isEqualTo("Invalid value");
            assertThat(response.body()).doesNotContain("SECRET-INPUT", "Size", "rejectedValue");
            var malformed = client.send(HttpRequest.newBuilder(app.uri("/body")).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{SECRET-INPUT")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(malformed.statusCode()).isEqualTo(400);
            assertThat(malformed.body()).doesNotContain("SECRET-INPUT", "JsonParseException");
        }
    }

    @Test
    void explicitLegacyGoldenIsSafeEvenInDevProfile() throws Exception {
        try (var app = application("facility.web.exception.use-problem-detail=false", "spring.profiles.active=dev");
             var client = HttpClient.newHttpClient()) {
            for (String path : new String[]{"/failure", "/filter-failure"}) {
                var response = client.send(HttpRequest.newBuilder(app.uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body())).isEqualTo(tools.jackson.databind.json.JsonMapper.builder().build().readTree(
                        "{\"code\":500,\"message\":\"Internal server error\",\"data\":null,\"description\":\"\",\"success\":false}"));
                assertThat(response.body()).doesNotContain("SECRET-INPUT", "Exception");
            }
        }
    }

    @Test
    void hostAdviceTakesPrecedenceOverDefaultAdvice() throws Exception {
        try (var app = application(new Class<?>[]{HostAdvice.class}); var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(app.uri("/failure")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body()).path("handled").asString()).isEqualTo("host");
        }
    }

    @Test
    void hostMapperAndLocaleApplyAtBothMvcAndFilterBoundaries() throws Exception {
        try (var app = application(new Class<?>[]{HostCustomization.class}); var client = HttpClient.newHttpClient()) {
            for (String path : new String[]{"/failure", "/filter-failure"}) {
                var response = client.send(HttpRequest.newBuilder(app.uri(path)).header("Accept-Language", "fr")
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(500);
                var body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body());
                assertThat(body.path("hostMapper").asBoolean()).isTrue();
                assertThat(body.path("detail").asString()).isEqualTo("Erreur serveur");
                assertThat(response.body()).doesNotContain("SECRET-INPUT");
            }
        }
    }

    @Test
    void serializationFailureHasSafeFallbackEvenWhenErrorSerializerFails() throws Exception {
        try (var app = application(new Class<?>[]{BrokenMapper.class}); var client = HttpClient.newHttpClient()) {
            for (String path : new String[]{"/filter-failure", "/serialization"}) {
                var response = client.send(HttpRequest.newBuilder(app.uri(path)).timeout(Duration.ofSeconds(5))
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(500);
                assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
                var body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body());
                assertThat(body.path("status").asInt()).isEqualTo(500);
                assertThat(body.path("detail").asString()).isEqualTo("Internal server error");
                assertThat(body.path("traceId").asString()).isNotBlank();
                assertThat(response.body()).doesNotContain("SECRET-INPUT", "Exception", "Tomcat");
            }
        }
    }

    @Test
    void committedResponseIsNeverOverwrittenOrAppended() throws Exception {
        try (var app = application(); var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(app.uri("/committed")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("already-sent");
        }
    }

    @Test
    void realMultipartDistinguishesMalformedInputFromUploadLimit() throws Exception {
        try (var app = application(); var client = HttpClient.newHttpClient()) {
            var malformed = client.send(HttpRequest.newBuilder(app.uri("/upload"))
                    .header("Content-Type", "multipart/form-data")
                    .POST(HttpRequest.BodyPublishers.ofString("SECRET-INPUT")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(malformed.statusCode()).isEqualTo(400);
            assertThat(malformed.body()).doesNotContain("SECRET-INPUT", "Exception");
            for (int size : new int[]{63, 64, 65}) {
                String body = "--boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"file.txt\"\r\n"
                        + "Content-Type: text/plain\r\n\r\n" + "x".repeat(size) + "\r\n--boundary--\r\n";
                var response = client.send(HttpRequest.newBuilder(app.uri("/upload"))
                        .header("Content-Type", "multipart/form-data; boundary=boundary")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).as("file bytes %s", size).isEqualTo(size > 64 ? 413 : 200);
                if (size > 64) assertThat(tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body()).path("status").asInt()).isEqualTo(413);
                else assertThat(tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body()).path("bytes").asLong()).isEqualTo(size);
            }
        }
    }

    @Test
    void uncommittedWriterAndEntityMetadataAreReplacedWithoutLosingSecurityHeaders() throws Exception {
        try (var app = application(); var client = HttpClient.newHttpClient()) {
            for (String path : new String[]{"/writer-failure", "/mvc-writer-failure"}) {
            var response = client.send(HttpRequest.newBuilder(app.uri(path)).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
            for (String header : new String[]{"Content-Encoding", "Content-Disposition", "ETag", "Last-Modified", "Content-Range"}) {
                assertThat(response.headers().firstValue(header)).as(header).isEmpty();
            }
            assertThat(response.headers().firstValue("Content-Length")).isNotEqualTo(java.util.Optional.of("999"));
            assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
            assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
            assertThat(tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body()).path("status").asInt()).isEqualTo(500);
            assertThat(response.body()).doesNotContain("SECRET-INPUT");
            }
        }
    }

    @Test
    void bootStandardErrorMappingRemainsSafeWithDiagnosticPropertiesEnabled() throws Exception {
        try (var app = application(new Class<?>[]{org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration.class},
                "spring.web.error.path=/host-error", "spring.web.error.include-message=always", "spring.web.error.include-stacktrace=always");
             var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(app.uri("/send-error?status=502")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(502);
            assertThat(response.headers().firstValue("X-Error-Target")).contains("/host-error");
            assertThat(tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body()).path("status").asInt()).isEqualTo(502);
            assertThat(response.body()).doesNotContain("SECRET-INPUT", "stackTrace", "exception");
        }
    }

    @Test
    void authenticationAdaptersCanReuseStandardStatusAndChallengeWithoutAnErrorDsl() throws Exception {
        try (var app = application(); var client = HttpClient.newHttpClient()) {
            for (int status : new int[]{401, 403}) {
                var response = client.send(HttpRequest.newBuilder(app.uri("/adapter-status?status=" + status))
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(status);
                assertThat(tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body()).path("code").asInt()).isEqualTo(status);
                if (status == 401) assertThat(response.headers().firstValue("WWW-Authenticate")).contains("Bearer realm=api");
                assertThat(response.body()).doesNotContain("SECRET-INPUT");
            }
        }
    }

    @Test
    void concurrentRequestsKeepLocaleAndTraceInTheirOwnResponse() throws Exception {
        try (var app = application(new Class<?>[]{HostCustomization.class}); var client = HttpClient.newHttpClient()) {
            var pending = java.util.stream.IntStream.range(0, 16).mapToObj(index -> {
                String locale = index % 2 == 0 ? "fr" : "en";
                return client.sendAsync(HttpRequest.newBuilder(app.uri(index % 3 == 0 ? "/filter-failure" : "/failure"))
                        .timeout(Duration.ofSeconds(5)).header("Accept-Language", locale).header("X-Trace-Id", "request-" + index)
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
            }).toList();
            for (int index = 0; index < pending.size(); index++) {
                var response = pending.get(index).join();
                assertThat(response.statusCode()).isEqualTo(500);
                var body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body());
                assertThat(body.path("detail").asString()).isEqualTo(index % 2 == 0 ? "Erreur serveur" : "Internal server error");
                assertThat(body.path("traceId").asString()).isEqualTo("request-" + index);
                assertThat(response.headers().firstValue("X-Trace-Id")).contains("request-" + index);
            }
        }
    }

    private cn.code91.facility.web.test.EmbeddedServletApplication application(String... properties) {
        return application(new Class<?>[0], properties);
    }

    private cn.code91.facility.web.test.EmbeddedServletApplication application(Class<?>[] additional, String... properties) {
        String[] defaults = {"facility.web.trace.enabled=true", "facility.web.repeatable-request.enabled=false", "facility.web.access-log.enabled=false"};
        String[] all = java.util.stream.Stream.concat(java.util.Arrays.stream(defaults), java.util.Arrays.stream(properties))
                .toArray(String[]::new);
        return cn.code91.facility.web.test.EmbeddedServletApplication.start(directory,
                java.util.stream.Stream.concat(java.util.stream.Stream.of(WebConfiguration.class), java.util.Arrays.stream(additional))
                        .toArray(Class<?>[]::new), all);
    }

    @org.springframework.web.bind.annotation.RestControllerAdvice
    @org.springframework.core.annotation.Order(0)
    static class HostAdvice {
        @org.springframework.web.bind.annotation.ExceptionHandler(IllegalStateException.class)
        org.springframework.http.ResponseEntity<Object> handle() {
            return org.springframework.http.ResponseEntity.status(409).body(Map.of("handled", "host"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class BrokenMapper {
        @Bean @org.springframework.context.annotation.Primary JsonMapper brokenMapper() {
            var module = new tools.jackson.databind.module.SimpleModule();
            module.addSerializer(org.springframework.http.ProblemDetail.class, new tools.jackson.databind.ValueSerializer<>() {
                @Override public void serialize(org.springframework.http.ProblemDetail value, tools.jackson.core.JsonGenerator gen,
                                                tools.jackson.databind.SerializationContext serializers) {
                    throw tools.jackson.core.exc.JacksonIOException.construct(new java.io.IOException("SECRET-INPUT"));
                }
            });
            return JsonMapper.builder().addModule(module).build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class HostCustomization {
        @Bean(name = "messageSource") org.springframework.context.MessageSource messages() {
            var messages = new org.springframework.context.support.StaticMessageSource();
            messages.addMessage("facility.web.error.system", java.util.Locale.FRENCH, "Erreur serveur");
            return messages;
        }
        @Bean @org.springframework.context.annotation.Primary JsonMapper hostMapper() {
            var module = new tools.jackson.databind.module.SimpleModule();
            module.addSerializer(org.springframework.http.ProblemDetail.class, new tools.jackson.databind.ValueSerializer<>() {
                @Override public void serialize(org.springframework.http.ProblemDetail value, tools.jackson.core.JsonGenerator gen,
                                                tools.jackson.databind.SerializationContext serializers) {
                    gen.writeStartObject();
                    gen.writeBooleanProperty("hostMapper", true);
                    gen.writeStringProperty("detail", value.getDetail());
                    gen.writeNumberProperty("status", value.getStatus());
                    gen.writeStringProperty("traceId", (String) value.getProperties().get("traceId"));
                    gen.writeEndObject();
                }
            });
            return JsonMapper.builder().addModule(module).build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({FacilityWebAutoConfiguration.class, Endpoints.class,
            org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration.class})
    static class WebConfiguration implements org.springframework.web.servlet.config.annotation.WebMvcConfigurer {
        @org.springframework.beans.factory.annotation.Autowired JsonMapper mapper;
        @Override public void extendMessageConverters(java.util.List<org.springframework.http.converter.HttpMessageConverter<?>> converters) {
            converters.removeIf(org.springframework.http.converter.json.JacksonJsonHttpMessageConverter.class::isInstance);
            converters.add(new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter(mapper));
        }
        @Override @Bean public org.springframework.validation.Validator getValidator() {
            var validator = new org.springframework.validation.beanvalidation.LocalValidatorFactoryBean();
            validator.setMessageInterpolator(new org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator());
            return validator;
        }
        @Bean org.springframework.web.servlet.DispatcherServlet dispatcherServlet() {
            return new org.springframework.web.servlet.DispatcherServlet();
        }
        @Bean org.springframework.boot.webmvc.autoconfigure.DispatcherServletRegistrationBean dispatcherRegistration(
                org.springframework.web.servlet.DispatcherServlet servlet) {
            var registration = new org.springframework.boot.webmvc.autoconfigure.DispatcherServletRegistrationBean(servlet, "/");
            registration.setMultipartConfig(new jakarta.servlet.MultipartConfigElement("", 64, 1024, 0));
            return registration;
        }
        @Bean org.springframework.web.multipart.MultipartResolver multipartResolver() {
            return new org.springframework.web.multipart.support.StandardServletMultipartResolver();
        }
        @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> failureFilter(FacilityHttpErrors errors) {
            var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter>();
            registration.setFilter((request, response, chain) -> {
                if (((jakarta.servlet.http.HttpServletRequest) request).getRequestURI().equals("/adapter-status")) {
                    int status = Integer.parseInt(request.getParameter("status"));
                    var failure = new org.springframework.web.ErrorResponseException(org.springframework.http.HttpStatusCode.valueOf(status));
                    failure.getBody().setDetail("SECRET-INPUT");
                    if (status == 401) failure.getHeaders().set("WWW-Authenticate", "Bearer realm=api");
                    errors.write((jakarta.servlet.http.HttpServletRequest) request, (jakarta.servlet.http.HttpServletResponse) response, failure);
                    return;
                }
                if (((jakarta.servlet.http.HttpServletRequest) request).getRequestURI().equals("/filter-failure")) {
                    throw new IllegalStateException("SECRET-INPUT");
                }
                if (((jakarta.servlet.http.HttpServletRequest) request).getRequestURI().equals("/writer-failure")) {
                    var http = (jakarta.servlet.http.HttpServletResponse) response;
                    http.setContentType("text/plain");
                    http.setContentLength(999);
                    http.setHeader("Content-Encoding", "gzip");
                    http.setHeader("Content-Disposition", "attachment; filename=old.txt");
                    http.setHeader("ETag", "old");
                    http.setHeader("Last-Modified", "Wed, 21 Oct 2015 07:28:00 GMT");
                    http.setHeader("Content-Range", "bytes 0-998/999");
                    http.setHeader("X-Content-Type-Options", "nosniff");
                    http.getWriter().write("SECRET-INPUT");
                    throw new IllegalStateException("SECRET-INPUT");
                }
                if (((jakarta.servlet.http.HttpServletRequest) request).getRequestURI().equals("/committed")) {
                    response.setContentLength(12);
                    response.getOutputStream().write("already-sent".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    response.flushBuffer();
                    throw new IllegalStateException("SECRET-INPUT");
                }
                if (((jakarta.servlet.http.HttpServletRequest) request).getRequestURI().equals("/send-error")) {
                    ((jakarta.servlet.http.HttpServletResponse) response).sendError(
                            Integer.parseInt(request.getParameter("status")), "SECRET-INPUT");
                    return;
                }
                chain.doFilter(request, response);
            });
            registration.setOrder(20);
            return registration;
        }
        @Bean org.springframework.boot.web.error.ErrorPageRegistrar hostPartialErrorPage() {
            return registry -> registry.addErrorPages(new org.springframework.boot.web.error.ErrorPage(
                    org.springframework.http.HttpStatus.NOT_FOUND, "/host-not-found"));
        }
        @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> dispatchObserver() {
            var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter>();
            registration.setFilter((request, response, chain) -> {
                ((jakarta.servlet.http.HttpServletResponse) response).setHeader("X-Dispatcher", request.getDispatcherType().name());
                ((jakarta.servlet.http.HttpServletResponse) response).setHeader("X-Error-Target", ((jakarta.servlet.http.HttpServletRequest) request).getRequestURI());
                chain.doFilter(request, response);
            });
            registration.setDispatcherTypes(jakarta.servlet.DispatcherType.ERROR);
            registration.setOrder(Integer.MIN_VALUE);
            return registration;
        }
        @Bean JsonMapper objectMapper() { return new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter().getMapper(); }
    }

    @RestController
    static class Endpoints {
        @org.springframework.web.bind.annotation.PostMapping("/upload")
        Map<String, Long> upload(@org.springframework.web.bind.annotation.RequestPart org.springframework.web.multipart.MultipartFile file) {
            return Map.of("bytes", file.getSize());
        }
        @GetMapping("/mvc-writer-failure") void writerFailure(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
            response.setContentType("text/plain");
            response.setContentLength(999);
            response.setHeader("Content-Encoding", "gzip");
            response.setHeader("Content-Disposition", "attachment; filename=old.txt");
            response.setHeader("ETag", "old");
            response.setHeader("Last-Modified", "Wed, 21 Oct 2015 07:28:00 GMT");
            response.setHeader("Content-Range", "bytes 0-998/999");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.getWriter().write("SECRET-INPUT");
            throw new IllegalStateException("SECRET-INPUT");
        }
        @GetMapping("/serialization") BrokenBody serialization() { return new BrokenBody(); }
        static class BrokenBody {
            public String getValue() { throw new IllegalStateException("SECRET-INPUT"); }
        }
        @GetMapping("/number") Map<String, Integer> number(@org.springframework.web.bind.annotation.RequestParam int n) {
            return Map.of("n", n);
        }
        @GetMapping("/status/{status}") Object status(@org.springframework.web.bind.annotation.PathVariable int status) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatusCode.valueOf(status), "SECRET-INPUT");
        }
        @GetMapping("/rate") Object rate() { throw new cn.code91.facility.web.ratelimit.RateLimitExceededException("SECRET-INPUT", 1501); }
        @GetMapping("/async") org.springframework.web.context.request.async.DeferredResult<Object> async() {
            return new org.springframework.web.context.request.async.DeferredResult<>(50L);
        }
        @GetMapping("/return-invalid") @jakarta.validation.constraints.NotNull Object invalidReturn() { return null; }
        @org.springframework.web.bind.annotation.PostMapping(value = "/body", consumes = "application/json")
        Input body(@org.springframework.web.bind.annotation.RequestBody @jakarta.validation.Valid Input input) { return input; }
        record Input(@jakarta.validation.constraints.Size(max = 3, message = "SECRET-INPUT") String password) {}
        @GetMapping({"/failure", "/failure/{input}"}) Object failure() { throw new IllegalStateException("SECRET-INPUT"); }
        @GetMapping("/success") Map<String, String> success() { return Map.of("name", "sample"); }
    }
}
