package com.example.api;

import com.example.fixtures.AdditionalTrustFixture;
import com.example.fixtures.LifecycleFixture;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class ConfigurationHttpTest {
    @Test void productionConfigurationStartsWithoutContactingTheIssuerButMissingTrustNeverStarts() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer,
                "--spring.profiles.active=prod",
                "--spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.invalid")) {
            assertThat(app.get("/health", null).statusCode()).isEqualTo(200);
            assertThat(app.get("/api/greeting", null).statusCode()).isEqualTo(401);
            assertThat(issuer.metadataRequests.get()).isZero();
        }
        assertThatThrownBy(() -> {
            try (var ignored = org.springframework.boot.SpringApplication.run(ApiApplication.class,
                    "--spring.datasource.url=" + Postgres.sharedUrl(), "--spring.datasource.username=postgres", "--spring.datasource.password=",
                    "--server.port=0", "--spring.profiles.active=prod", "--logging.level.root=OFF")) { }
        }).hasStackTraceContaining("Invalid application JWT trust policy");
    }

    @ParameterizedTest @ValueSource(strings = {"0ms", "-1ms", "11s"})
    void invalidNetworkBudgetsFailAtStartup(String timeout) throws Exception {
        try (var issuer = new TestIssuer()) {
            assertThatThrownBy(() -> { try (var ignored = new RunningApp(issuer, "--app.security.jwk-timeout=" + timeout)) { } })
                    .hasStackTraceContaining("JWK timeout must be positive and at most 10 seconds");
        }
    }

    @Test void restartRefreshesKeysAndTwoApplicationsKeepTheirOwnTrustAndUserCustomization() throws Exception {
        try (var issuer = new TestIssuer(); var secondIssuer = new TestIssuer()) {
            try (var app = new RunningApp(issuer)) { assertThat(app.get("/api/greeting", issuer.token()).statusCode()).isEqualTo(200); }
            int previous = issuer.keyRequests.get();
            try (var second = new RunningApp(secondIssuer)) {
              try (var app = new RunningApp(issuer, new Class<?>[]{AdditionalTrustFixture.class})) {
                assertThat(app.get("/api/greeting", issuer.token()).statusCode()).isEqualTo(200);
                assertThat(issuer.keyRequests.get()).isGreaterThan(previous);
                assertThat(app.get("/api/greeting", issuer.token("a", Map.of("sub", "blocked-subject"), Set.of())).statusCode()).isEqualTo(401);
                assertThat(second.get("/api/greeting", secondIssuer.token("a", Map.of("sub", "blocked-subject"), Set.of())).statusCode()).isEqualTo(200);
                assertThat(app.get("/api/greeting", secondIssuer.token()).statusCode()).isEqualTo(401);
                assertThat(second.get("/api/greeting", issuer.token()).statusCode()).isEqualTo(401);
              }
              assertThat(second.get("/api/greeting", secondIssuer.token()).statusCode()).isEqualTo(200);
            }
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void proxyConfigurationOnlyChangesNetworkOriginNeverTheVerifiedActor(boolean trustProxy) throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, new Class<?>[]{LifecycleFixture.class},
                "--facility.web.proxy.trusted-proxies=" + (trustProxy ? "127.0.0.1/32" : ""))) {
            var response = app.get("/api/greeting/origin", issuer.token(), "X-Forwarded-For", "198.51.100.19",
                    "X-User-Id", "admin", "X-Trace-Id", "untrusted-trace");
            assertThat(response.statusCode()).isEqualTo(200);
            var json = JsonMapper.builder().build().readTree(response.body());
            assertThat(json.path("subject").asString()).isEqualTo("alice");
            assertThat(json.path("ip").asString()).isEqualTo(trustProxy ? "198.51.100.19" : "127.0.0.1");
            assertThat(response.headers().firstValue("X-Trace-Id").orElseThrow()).isNotEqualTo("untrusted-trace");
            assertThat(app.get("/api/greeting/origin", null, "X-User-Id", "admin").statusCode()).isEqualTo(401);
            try (var socket = new Socket("127.0.0.1", app.context.getWebServer().getPort())) {
                socket.setSoTimeout(5000);
                socket.getOutputStream().write("GET /.well-known/oauth-protected-resource HTTP/1.1\r\nHost: evil.invalid\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                String wire = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                assertThat(wire).startsWith("HTTP/1.1 200").contains("https://api.example.test").doesNotContain("evil.invalid");
            }
        }
    }
}
