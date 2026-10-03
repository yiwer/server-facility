package com.example.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationHttpTest {
    @Test void publicMetadataUsesTheConfiguredResourceAndForwardedHeadersCannotChangeTrust() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer)) {
            var response = app.get("/.well-known/oauth-protected-resource", null,
                    "Forwarded", "host=attacker.example;proto=http", "X-Forwarded-Host", "attacker.example", "X-Forwarded-For", "198.51.100.99");
            assertThat(response.statusCode()).isEqualTo(200);
            var body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body());
            assertThat(body.path("resource").asString()).isEqualTo("https://api.example.test");
            var invalid = app.get("/api/greeting", "not.a.jwt", "X-Forwarded-Host", "attacker.example");
            assertThat(invalid.headers().firstValue("WWW-Authenticate")).contains("Bearer error=\"invalid_token\"");
            assertThat(invalid.body()).doesNotContain("attacker.example");
        }
    }

    @Test void validAuthenticationWithoutOperationScopeGetsSafe403AndInvalidTokenGetsFixedChallenge() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer)) {
            var forbidden = app.get("/api/greeting", issuer.token("a", java.util.Map.of("scope", "other:read"), java.util.Set.of()));
            assertThat(forbidden.statusCode()).isEqualTo(403);
            assertThat(forbidden.headers().firstValue("WWW-Authenticate")).contains("Bearer error=\"insufficient_scope\"");
            assertThat(forbidden.body()).contains("\"status\":403", "\"code\":403", "\"traceId\"").doesNotContain("alice");
            var invalid = app.get("/api/greeting", "not.a.jwt");
            assertThat(invalid.statusCode()).isEqualTo(401);
            assertThat(invalid.headers().firstValue("WWW-Authenticate")).contains("Bearer error=\"invalid_token\"");
            assertThat(invalid.body()).doesNotContain("not.a.jwt", "parse", "Exception", "error_description");
            assertThat(app.get("/unlisted", issuer.token()).statusCode()).isEqualTo(403);
        }
    }

    @Test void credentialsMustHaveRequiredIdentityAndExpiryAndMatchTrustedAudienceAndIssuer() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer)) {
            var tokens = java.util.List.of(
                    issuer.token("a", java.util.Map.of(), java.util.Set.of("exp")),
                    issuer.token("a", java.util.Map.of(), java.util.Set.of("sub")),
                    issuer.token("a", java.util.Map.of("sub", " "), java.util.Set.of()),
                    issuer.token("a", java.util.Map.of(), java.util.Set.of("iss")),
                    issuer.token("a", java.util.Map.of(), java.util.Set.of("aud")),
                    issuer.token("a", java.util.Map.of("iss", "https://wrong.example.test"), java.util.Set.of()),
                    issuer.token("a", java.util.Map.of("aud", java.util.List.of("another-api")), java.util.Set.of()),
                    issuer.token("a", java.util.Map.of("exp", java.time.Instant.now().minusSeconds(120).getEpochSecond()), java.util.Set.of()),
                    issuer.token("a", java.util.Map.of("nbf", java.time.Instant.now().plusSeconds(120).getEpochSecond()), java.util.Set.of()),
                    issuer.token("a", java.util.Map.of("aud", 42), java.util.Set.of()),
                    issuer.token("a", java.util.Map.of("iss", 42), java.util.Set.of()),
                    issuer.token("a", java.util.Map.of("exp", "not-a-number"), java.util.Set.of()),
                    issuer.token("a", java.util.Map.of("nbf", "not-a-number"), java.util.Set.of()),
                    "eyJhbGciOiJub25lIn0.e30.", "not.a.jwt");
            for (int i = 0; i < tokens.size(); i++) {
                var response = app.get("/api/greeting", tokens.get(i));
                assertThat(response.statusCode()).as("rejected credential case %s", i).isEqualTo(401);
                assertThat(response.body()).contains("\"status\":401").doesNotContain(tokens.get(i), "alice");
            }
            // The standard Security claim converter owns representation: numeric subject becomes its String form.
            var numericSubject = app.get("/api/greeting", issuer.token("a", java.util.Map.of("sub", 42), java.util.Set.of()));
            assertThat(numericSubject.statusCode()).isEqualTo(200);
            assertThat(tools.jackson.databind.json.JsonMapper.builder().build().readTree(numericSubject.body())
                    .path("actor").path("subject").asString()).isEqualTo("42");
        }
    }

    @Test void validSignedJwtProducesExplicitActor() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer)) {
            var response = app.get("/api/greeting", issuer.token());
            assertThat(response.statusCode()).isEqualTo(200);
            var body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body());
            assertThat(body.path("actor").path("issuer").asString()).isEqualTo(issuer.issuer());
            assertThat(body.path("actor").path("subject").asString()).isEqualTo("alice");
            assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
        }
    }

    @Test void anonymousBusinessRequestGetsSafe401WhileHealthIsPublic() throws Exception {
        try (var issuer = new TestIssuer(); var app = SpringApplication.run(ApiApplication.class, "--server.port=0", "--spring.main.banner-mode=off",
                "--spring.security.oauth2.resourceserver.jwt.issuer-uri=" + issuer.issuer(),
                "--spring.security.oauth2.resourceserver.jwt.audiences=secured-api", "--spring.profiles.active=local",
                "--app.security.resource-uri=https://api.example.test")) {
            int port = ((ServletWebServerApplicationContext) app).getWebServer().getPort();
            try (var client = HttpClient.newHttpClient()) {
                var health = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/health"))
                        .timeout(Duration.ofSeconds(5)).build(), HttpResponse.BodyHandlers.ofString());
                assertThat(health.statusCode()).isEqualTo(200);
                var denied = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/greeting"))
                        .timeout(Duration.ofSeconds(5)).build(), HttpResponse.BodyHandlers.ofString());
                assertThat(denied.statusCode()).isEqualTo(401);
                assertThat(denied.headers().firstValue("WWW-Authenticate")).contains("Bearer");
                assertThat(denied.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v).contains("application/problem+json"));
                assertThat(denied.body()).contains("\"status\":401", "\"code\":401", "\"traceId\"", "\"errors\":[]");
            }
        }
    }
}
