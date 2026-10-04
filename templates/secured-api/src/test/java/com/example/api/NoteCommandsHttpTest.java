package com.example.api;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.example.api.NotesHttpTest.*;
import static org.assertj.core.api.Assertions.*;

class NoteCommandsHttpTest {
    @Test void workspaceIssuerSubjectAndUpdateTargetCannotBorrowAnotherCommandResult() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var otherIssuer = new TestIssuer();
             var app = new RunningApp(issuer, "--spring.datasource.url=" + database);
             var other = new RunningApp(otherIssuer, "--spring.datasource.url=" + database)) {
            String alice = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String bob = issuer.token("a", Map.of("scope", "notes:read notes:write", "sub", "bob"), Set.of());
            String foreignAlice = otherIssuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String workspace = JSON.readTree(send(app, "POST", "/api/workspaces", alice, "{\"name\":\"Shared\"}").body()).path("id").asString();
            String secondWorkspace = JSON.readTree(send(app, "POST", "/api/workspaces", alice, "{\"name\":\"Separate\"}").body()).path("id").asString();
            try (var connection = Postgres.connect(database); var grant = connection.prepareStatement("insert into workspace_member(workspace_id, issuer, subject) values (?::uuid, ?, ?)")) {
                for (var member : List.of(List.of(issuer.issuer(), "bob"), List.of(otherIssuer.issuer(), "alice"))) {
                    grant.setString(1, workspace); grant.setString(2, member.get(0)); grant.setString(3, member.get(1)); grant.executeUpdate();
                }
            }
            String path = "/api/workspaces/" + workspace + "/notes";
            var a = command(app, "POST", path, alice, "shared-key", "{\"slug\":\"alice\",\"title\":\"A\",\"body\":\"\"}");
            var b = command(app, "POST", path, bob, "shared-key", "{\"slug\":\"bob\",\"title\":\"B\",\"body\":\"\"}");
            var c = command(other, "POST", path, foreignAlice, "shared-key", "{\"slug\":\"foreign\",\"title\":\"C\",\"body\":\"\"}");
            var d = command(app, "POST", path.replace(workspace, secondWorkspace), alice, "shared-key", "{\"slug\":\"alice\",\"title\":\"A\",\"body\":\"\"}");
            for (var created : List.of(a, b, c, d)) assertThat(created.statusCode()).isEqualTo(201);
            assertThat(List.of(a, b, c, d).stream().map(r -> r.headers().firstValue("Location").orElseThrow()).distinct().count()).isEqualTo(4);
            String edit = "{\"title\":\"Edit\",\"body\":\"\"}";
            assertThat(command(app, "PUT", a.headers().firstValue("Location").orElseThrow(), alice, "target-key", edit).statusCode()).isEqualTo(200);
            var differentTarget = command(app, "PUT", b.headers().firstValue("Location").orElseThrow(), alice, "target-key", edit);
            assertThat(differentTarget.statusCode()).isEqualTo(409);
            assertThat(JSON.readTree(differentTarget.body()).path("code").asString()).isEqualTo("command_conflict");
            assertThat(JSON.readTree(app.get(b.headers().firstValue("Location").orElseThrow(), alice).body()).path("title").asString()).isEqualTo("B");
            String readOnly = issuer.token("a", Map.of("scope", "notes:read"), Set.of());
            assertThat(command(app, "POST", path, readOnly, "shared-key", "{\"slug\":\"alice\",\"title\":\"A\",\"body\":\"\"}").statusCode()).isEqualTo(403);
        }
    }
    @Test void expiryDoesNotReleaseIdentityAndCurrentAuthorizationPrecedesConflictAndExpiredResults() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + database)) {
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String workspace = JSON.readTree(send(app, "POST", "/api/workspaces", token, "{\"name\":\"Expiry\"}").body()).path("id").asString();
            String path = "/api/workspaces/" + workspace + "/notes";
            String body = "{\"slug\":\"expired\",\"title\":\"Original\",\"body\":\"private receipt\"}";
            var first = command(app, "POST", path, token, "retained-key", body);
            assertThat(first.statusCode()).isEqualTo(201);
            assertThat(send(app, "DELETE", first.headers().firstValue("Location").orElseThrow(), token, "").statusCode()).isEqualTo(204);
            Postgres.execute(database, "update note_command set receipt_expires_at = clock_timestamp()");
            var expired = command(app, "POST", path, token, "retained-key", body);
            assertThat(expired.statusCode()).isEqualTo(410);
            assertThat(JSON.readTree(expired.body()).path("code").asString()).isEqualTo("command_receipt_expired");
            assertThat(expired.body()).doesNotContain("private receipt", "retained-key");
            assertThat(PersistenceFailureHttpTest.count(database, "select count(*) from note")).isZero();
            assertThat(PersistenceFailureHttpTest.count(database, "select count(*) from note_command")).isEqualTo(1);
            assertThat(PersistenceFailureHttpTest.count(database, "select command_count from workspace")).isEqualTo(1);
            Postgres.execute(database, "delete from workspace_member where workspace_id = '" + workspace + "'");
            for (String content : List.of(body, body.replace("Original", "Different"))) {
                var denied = command(app, "POST", path, token, "retained-key", content);
                assertThat(denied.statusCode()).isEqualTo(403);
                assertThat(JSON.readTree(denied.body()).path("code").asString()).isEqualTo("workspace_forbidden");
            }
        }
    }
    @Test void theRequestKeyIsOneExplicitBoundedAsciiTokenAtBothPublicEntrypoints() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + Postgres.freshUrl())) {
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String workspace = JSON.readTree(send(app, "POST", "/api/workspaces", token, "{\"name\":\"Keys\"}").body()).path("id").asString();
            String path = "/api/workspaces/" + workspace + "/notes";
            String body = "{\"slug\":\"key\",\"title\":\"Title\",\"body\":\"\"}";
            for (String key : Arrays.asList("", "one,two", "one two", "a".repeat(129), null)) {
                var bad = command(app, "POST", path, token, key, body);
                assertThat(bad.statusCode()).as("invalid request key").isEqualTo(400);
                assertThat(JSON.readTree(bad.body()).path("code").asString()).isEqualTo("invalid_command_key");
            }
            var duplicate = HttpRequest.newBuilder(URI.create(app.base + path)).timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                    .header("Idempotency-Key", "duplicate").header("Idempotency-Key", "duplicate")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            assertThat(app.client.send(duplicate, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(400);
            var notes = app.context.getBean(com.example.api.notes.Notes.class);
            var actor = new com.example.api.greeting.Actor(issuer.issuer(), "alice");
            for (String key : Arrays.asList(null, "", "a,b", "界", "a".repeat(129))) {
                assertThatThrownBy(() -> notes.create(actor, UUID.fromString(workspace), key, new com.example.api.notes.Notes.NewNote("key", "Title", "")))
                        .isInstanceOf(com.example.api.notes.NotesFailure.class).hasMessage("invalid_command_key");
            }
            for (String key : List.of("a", "b".repeat(127), "C_:.0-" + "d".repeat(122))) {
                var created = command(app, "POST", path, token, key, body);
                assertThat(created.statusCode()).isEqualTo(201);
                assertThat(send(app, "DELETE", created.headers().firstValue("Location").orElseThrow(), token, "").statusCode()).isEqualTo(204);
            }
        }
    }
    @Test void operationsAreSeparateAndReplaySurvivesLaterEditingAndDeletionWithoutResurrectingTheNote() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + Postgres.freshUrl())) {
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String workspace = JSON.readTree(send(app, "POST", "/api/workspaces", token, "{\"name\":\"Commands\"}").body()).path("id").asString();
            String path = "/api/workspaces/" + workspace + "/notes";
            String original = "{\"slug\":\"draft\",\"title\":\"Original\",\"body\":\"\"}";
            var created = command(app, "POST", path, token, "same-key", original);
            String location = created.headers().firstValue("Location").orElseThrow();
            String edit = "{\"title\":\"First edit\",\"body\":\"Saved result\"}";
            var updated = command(app, "PUT", location, token, "same-key", edit);
            assertThat(updated.statusCode()).isEqualTo(200);
            assertThat(command(app, "PUT", location, token, "later", "{\"title\":\"Later edit\",\"body\":\"\"}").statusCode()).isEqualTo(200);
            assertThat(command(app, "PUT", location, token, "same-key", edit).body()).isEqualTo(updated.body());
            assertThat(JSON.readTree(app.get(location, token).body()).path("title").asString()).isEqualTo("Later edit");
            assertThat(send(app, "DELETE", location, token, "").statusCode()).isEqualTo(204);
            var createReplay = command(app, "POST", path, token, "same-key", original);
            assertThat(createReplay.statusCode()).isEqualTo(201); assertThat(createReplay.body()).isEqualTo(created.body());
            assertThat(createReplay.headers().firstValue("Location")).isEqualTo(created.headers().firstValue("Location"));
            var updateReplay = command(app, "PUT", location, token, "same-key", edit);
            assertThat(updateReplay.statusCode()).isEqualTo(200); assertThat(updateReplay.body()).isEqualTo(updated.body());
            assertThat(app.get(location, token).statusCode()).isEqualTo(404);
        }
    }
    @Test void aKeyCannotBeReusedForDifferentValidatedContent() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + Postgres.freshUrl())) {
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String workspace = JSON.readTree(send(app, "POST", "/api/workspaces", token, "{\"name\":\"Commands\"}").body()).path("id").asString();
            String path = "/api/workspaces/" + workspace + "/notes";
            var first = command(app, "POST", path, token, "same-key", "{\"slug\":\"first\",\"title\":\"Title\",\"body\":\"ab\"}");
            assertThat(first.statusCode()).isEqualTo(201);
            var conflict = command(app, "POST", path, token, "same-key", "{\"slug\":\"first\",\"title\":\"Titlea\",\"body\":\"b\"}");
            assertThat(conflict.statusCode()).isEqualTo(409);
            assertThat(JSON.readTree(conflict.body()).path("code").asString()).isEqualTo("command_conflict");
            assertThat(conflict.body()).doesNotContain("Titlea", "same-key", "select ");
            assertThat(JSON.readTree(app.get(path, token).body()).path("total").asLong()).isEqualTo(1);
        }
    }
    @Test void repeatingAnAuthenticatedCreateReturnsTheSameCommittedBusinessResult() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + Postgres.freshUrl())) {
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String workspace = JSON.readTree(send(app, "POST", "/api/workspaces", token, "{\"name\":\"Commands\"}").body()).path("id").asString();
            String path = "/api/workspaces/" + workspace + "/notes";
            var first = command(app, "POST", path, token, "create-001", "{\"slug\":\"draft\",\"title\":\"独立金样 🌱\",\"body\":\"line one\\nline two\"}");
            var retry = command(app, "POST", path, token, "create-001", "{ \"body\":\"line one\\nline two\", \"title\":\"独立金样 🌱\", \"slug\":\"draft\" }");
            assertThat(first.statusCode()).isEqualTo(201);
            assertThat(retry.statusCode()).isEqualTo(201);
            assertThat(retry.headers().firstValue("Location")).isEqualTo(first.headers().firstValue("Location"));
            assertThat(retry.body()).isEqualTo(first.body());
            assertThat(JSON.readTree(app.get(path, token).body()).path("total").asLong()).isEqualTo(1);
        }
    }

    static HttpResponse<String> command(RunningApp app, String method, String path, String token, String key, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(app.base + path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (key != null) request.header("Idempotency-Key", key);
        return app.client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
