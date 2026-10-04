package com.example.api;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.example.api.NotesHttpTest.*;
import static com.example.api.NoteCommandsHttpTest.command;
import static org.assertj.core.api.Assertions.*;

class NoteIdentityStorageTest {
    @Test void storageRejectsInvalidUnicodeBeforeJdbcAndUsesActualUtf8Bytes() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + Postgres.freshUrl())) {
            var notes = app.context.getBean(com.example.api.notes.Notes.class);
            for (String bad : List.of("nul\u0000", "high\ud800", "low\udc00", "界".repeat(21845) + "ab", "a".repeat(65537))) {
                for (var actor : List.of(new com.example.api.greeting.Actor(issuer.issuer(), bad), new com.example.api.greeting.Actor(bad, "subject"))) {
                    assertThatThrownBy(() -> notes.createWorkspace(actor, "Invalid identity"))
                            .isInstanceOf(com.example.api.notes.NotesFailure.class).hasMessage("invalid_actor");
                }
            }
            for (String valid : List.of("界".repeat(21845), "界".repeat(21845) + "a")) {
                var actor = new com.example.api.greeting.Actor(issuer.issuer(), valid);
                var workspace = notes.createWorkspace(actor, "Exact byte budget");
                assertThat(notes.list(actor, workspace.id(), new com.example.api.notes.Notes.PageQuery(0, 1, "slug", "asc")).total()).isZero();
            }
            // The trusted authority signs the exact escaped JSON; no replacement encoding can hide malformed text.
            for (String escaped : List.of("bad\\u0000", "bad\\uD800", "bad\\uDC00")) {
                String valid = issuer.token("a", Map.of("scope", "notes:read notes:write", "sub", "replace-subject"), Set.of());
                String[] parts = valid.split("\\.");
                String json = new String(Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8).replace("replace-subject", escaped);
                String unsigned = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var signature = java.security.Signature.getInstance("SHA256withRSA"); signature.initSign(issuer.a.getPrivate());
                signature.update(unsigned.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                String token = unsigned + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
                for (String traceId : List.of("0123456789abcdef0123456789abcdef", "0123456789abcdef0123456789baddef")) {
                    var request = HttpRequest.newBuilder(URI.create(app.base + "/api/workspaces"))
                            .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + token)
                            .header("traceparent", "00-" + traceId + "-1234567890abcdef-01")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Rejected\"}")).build();
                    var rejected = app.client.send(request, HttpResponse.BodyHandlers.ofString());
                    assertThat(rejected.statusCode()).isEqualTo(400);
                    var problem = (tools.jackson.databind.node.ObjectNode) JSON.readTree(rejected.body());
                    assertThat(problem.path("code").asString()).isEqualTo("invalid_actor");
                    assertThat(problem.path("traceId").asString()).isEqualTo(traceId);
                    assertThat(problem.path("instance").asString()).isEqualTo("urn:facility:error:" + traceId);
                    // Correlation metadata may contain the input sentinel by coincidence.
                    // Validate it exactly before checking every remaining public error field.
                    problem.remove("traceId");
                    problem.remove("instance");
                    assertThat(problem.toString()).doesNotContain("bad", "insert", "postgres");
                }
            }
        }
    }
    @Test void aLegalOpaqueSignedSubjectDoesNotBecomeTooLargeForTheMembershipIndex() throws Exception {
        var random = new Random(290029L);
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        var subject = new StringBuilder();
        for (int i = 0; i < 4000; i++) subject.append(alphabet.charAt(random.nextInt(alphabet.length())));
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + Postgres.freshUrl())) {
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write", "sub", subject.toString()), Set.of());
            assertThat(token.length()).isLessThan(7500);
            var created = send(app, "POST", "/api/workspaces", token, "{\"name\":\"Opaque actor\"}");
            assertThat(created.statusCode()).isEqualTo(201);
            String path = created.headers().firstValue("Location").orElseThrow();
            String body = "{\"slug\":\"opaque\",\"title\":\"Works\",\"body\":\"\"}";
            var first = command(app, "POST", path, token, "long-actor", body);
            assertThat(first.statusCode()).isEqualTo(201);
            assertThat(command(app, "POST", path, token, "long-actor", body).body()).isEqualTo(first.body());
        }
    }
}
