package com.example.api;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class JwkLifecycleHttpTest {
    @Test void issuerDiscoveryCannotBroadenTheApplicationsAcceptedAlgorithms() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer)) {
            issuer.algorithm = "RS512";
            assertThat(app.get("/api/greeting", issuer.token()).statusCode()).isEqualTo(401);
        }
    }

    @Test void discoveryIsDeferredAndRotationUsesPublishedKeysWithoutRestartingTheApplication() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer)) {
            assertThat(issuer.metadataRequests.get()).isZero();
            assertThat(app.get("/health", null).statusCode()).isEqualTo(200);
            assertThat(issuer.metadataRequests.get()).isZero();
            String a = issuer.token();
            assertThat(app.get("/api/greeting", a).statusCode()).isEqualTo(200);
            assertThat(issuer.metadataRequests.get()).isPositive();
            int initialFetches = issuer.keyRequests.get();
            issuer.published = java.util.List.of("a", "b");
            assertThat(app.get("/api/greeting", issuer.token("b", java.util.Map.of(), java.util.Set.of())).statusCode()).isEqualTo(200);
            assertThat(issuer.keyRequests.get()).isGreaterThan(initialFetches);
            issuer.keyStatus = 503;
            assertThat(app.get("/api/greeting", a).statusCode()).isEqualTo(200); // Usable cached key; no instant-revocation promise.
            assertThat(app.get("/api/greeting", issuer.token("unknown", java.util.Map.of(), java.util.Set.of())).statusCode()).isEqualTo(503);
            issuer.keyStatus = 200; issuer.published = java.util.List.of("b");
            assertThat(app.get("/api/greeting", issuer.token("unknown", java.util.Map.of(), java.util.Set.of())).statusCode()).isEqualTo(401);
            assertThat(app.get("/api/greeting", a).statusCode()).isEqualTo(401);
            assertThat(app.get("/api/greeting", issuer.token("b", java.util.Map.of(), java.util.Set.of())).statusCode()).isEqualTo(200);
        }
    }

    @Test void stalledKeyResponseHasAnOverallFetchDeadlineAndTheNextRequestRecovers() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer,
                "--spring.security.oauth2.resourceserver.jwt.jwk-set-uri=" + issuer.issuer() + "/keys",
                "--app.security.jwk-timeout=250ms")) {
            issuer.keyEntered = new java.util.concurrent.CountDownLatch(1);
            issuer.keyRelease = new java.util.concurrent.CountDownLatch(1);
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(app.base + "/api/greeting"))
                    .header("Authorization", "Bearer " + issuer.token()).timeout(java.time.Duration.ofSeconds(3)).build();
            var future = app.client.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            try {
                assertThat(issuer.keyEntered.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var response = future.get(2, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(response.statusCode()).isEqualTo(503);
                assertThat(response.body()).doesNotContain("TimeoutException", issuer.issuer());
            } finally {
                future.cancel(true); issuer.keyRelease.countDown();
            }
            issuer.keyEntered = null; issuer.keyRelease = null;
            assertThat(app.get("/api/greeting", issuer.token()).statusCode()).isEqualTo(200);
        }
    }

    @Test void missingRemoteKeysReturnSafe503AndRecoverWhenTheIssuerReturns() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer,
                "--spring.security.oauth2.resourceserver.jwt.jwk-set-uri=" + issuer.issuer() + "/keys")) {
            assertThat(issuer.metadataRequests.get()).isZero();
            assertThat(issuer.keyRequests.get()).isZero();
            assertThat(app.get("/health", null).statusCode()).isEqualTo(200);
            issuer.keyStatus = 503; issuer.keyBody = "credential=SHOULD_NOT_ESCAPE upstream-secret";
            var unavailable = app.get("/api/greeting", issuer.token());
            assertThat(unavailable.statusCode()).isEqualTo(503);
            assertThat(unavailable.body()).contains("\"status\":503", "\"traceId\"")
                    .doesNotContain("SHOULD_NOT_ESCAPE", "upstream-secret", issuer.issuer(), "Exception");
            assertThat(unavailable.headers().firstValue("WWW-Authenticate")).isEmpty();
            issuer.keyStatus = 200; issuer.keyBody = null;
            assertThat(app.get("/api/greeting", issuer.token()).statusCode()).isEqualTo(200);
            assertThat(issuer.metadataRequests.get()).isZero();
        }
    }
}
