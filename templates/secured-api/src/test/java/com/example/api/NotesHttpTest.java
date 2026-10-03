package com.example.api;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class NotesHttpTest {
    static final JsonMapper JSON = JsonMapper.builder().build();
    @Test void duplicateSlugsConflictOnlyInsideTheirWorkspaceWithoutChangingTheOriginal() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + Postgres.freshUrl())) {
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String workspace = JSON.readTree(send(app, "POST", "/api/workspaces", token, "{\"name\":\"Unique\"}").body()).path("id").asString();
            String path = "/api/workspaces/" + workspace + "/notes";
            var created = send(app, "POST", path, token, "{\"slug\":\"key\",\"title\":\"Original\",\"body\":\"\"}");
            var duplicate = send(app, "POST", path, token, "{\"slug\":\"key\",\"title\":\"Replacement-secret\",\"body\":\"\"}");
            assertThat(duplicate.statusCode()).isEqualTo(409);
            assertThat(JSON.readTree(duplicate.body()).path("code").asString()).isEqualTo("note_slug_conflict");
            assertThat(duplicate.body()).doesNotContain("Replacement-secret", "insert into", "duplicate key");
            assertThat(JSON.readTree(app.get(created.headers().firstValue("Location").orElseThrow(), token).body()).path("title").asString()).isEqualTo("Original");
            String second = JSON.readTree(send(app, "POST", "/api/workspaces", token, "{\"name\":\"Other\"}").body()).path("id").asString();
            assertThat(send(app, "POST", "/api/workspaces/" + second + "/notes", token, "{\"slug\":\"key\",\"title\":\"Other\",\"body\":\"\"}").statusCode()).isEqualTo(201);
        }
    }
    @Test void commandsHaveUnicodeAndSizeBudgetsAndInvalidValuesNeverCreateRows() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + Postgres.freshUrl())) {
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            for (String invalid : List.of("{}", "{\"name\":null}", "{\"name\":\"\"}", "{\"name\":\"  \"}", JSON.writeValueAsString(Map.of("name", "界".repeat(101)))))
                assertThat(send(app, "POST", "/api/workspaces", token, invalid).statusCode()).as(invalid).isEqualTo(400);
            for (int length : new int[]{99, 100}) assertThat(send(app, "POST", "/api/workspaces", token,
                    JSON.writeValueAsString(Map.of("name", "🌱".repeat(length)))).statusCode()).isEqualTo(201);
            String workspace = JSON.readTree(send(app, "POST", "/api/workspaces", token, "{\"name\":\"Bounds\"}").body()).path("id").asString();
            String path = "/api/workspaces/" + workspace + "/notes";
            for (int length : new int[]{63, 64}) {
                var created = send(app, "POST", path, token, JSON.writeValueAsString(Map.of("slug", "a".repeat(length), "title", "🌱".repeat(length == 63 ? 199 : 200), "body", "界".repeat(length == 63 ? 4095 : 4096))));
                assertThat(created.statusCode()).isEqualTo(201);
                assertThat(JSON.readTree(created.body()).path("title").asString().codePointCount(0, JSON.readTree(created.body()).path("title").asString().length())).isEqualTo(length == 63 ? 199 : 200);
                assertThat(send(app, "PUT", created.headers().firstValue("Location").orElseThrow(), token, "{\"title\":null,\"body\":\"\"}").statusCode()).isEqualTo(400);
            }
            var invalid = new ArrayList<String>();
            invalid.addAll(List.of("{}", "{\"slug\":null,\"title\":\"Title\",\"body\":\"\"}", "{\"slug\":\"valid\",\"title\":\"Title\",\"body\":null}", "{\"slug\":\"valid\",\"title\":\"\\uD800\",\"body\":\"\"}", "{\"slug\":\"valid\",\"title\":\"Title\",\"body\":\"\\u0000\"}"));
            for (String slug : List.of("", "A", "bad_slug", "界", "a".repeat(65), "x';drop-table-note")) invalid.add(JSON.writeValueAsString(Map.of("slug", slug, "title", "Title", "body", "")));
            for (String title : List.of("", " ", "🌱".repeat(201))) invalid.add(JSON.writeValueAsString(Map.of("slug", "valid", "title", title, "body", "")));
            invalid.add(JSON.writeValueAsString(Map.of("slug", "valid", "title", "Title", "body", "界".repeat(4097))));
            for (String value : invalid) assertThat(send(app, "POST", path, token, value).statusCode()).isEqualTo(400);
            assertThat(app.get("/api/workspaces/not-a-uuid/notes", token).statusCode()).isEqualTo(400);
            assertThat(JSON.readTree(app.get(path, token).body()).path("total").asLong()).isEqualTo(2);
            var literal = send(app, "POST", path, token, "{\"slug\":\"literal\",\"title\":\"'); drop table note; --\",\"body\":\"\"}");
            assertThat(literal.statusCode()).isEqualTo(201);
            assertThat(JSON.readTree(app.get(literal.headers().firstValue("Location").orElseThrow(), token).body()).path("title").asString()).isEqualTo("'); drop table note; --");
        }
    }
    @Test void paginationHasStableShapeAndBoundedAllowlistedSorting() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + Postgres.freshUrl())) {
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String workspace = JSON.readTree(send(app, "POST", "/api/workspaces", token, "{\"name\":\"Pages\"}").body()).path("id").asString();
            String path = "/api/workspaces/" + workspace + "/notes";
            for (String slug : List.of("c", "a", "b")) assertThat(send(app, "POST", path, token,
                    JSON.writeValueAsString(Map.of("slug", slug, "title", "Same", "body", ""))).statusCode()).isEqualTo(201);
            var response = app.get(path + "?page=1&size=2&sort=slug&direction=asc", token);
            assertThat(response.statusCode()).isEqualTo(200);
            var page = JSON.readTree(response.body());
            assertThat(page.path("page").asInt()).isEqualTo(1); assertThat(page.path("size").asInt()).isEqualTo(2);
            assertThat(page.path("total").asLong()).isEqualTo(3); assertThat(page.path("items").size()).isEqualTo(1);
            assertThat(page.path("items").get(0).path("slug").asString()).isEqualTo("c");
            var defaults = JSON.readTree(app.get(path, token).body());
            assertThat(defaults.path("size").asInt()).isEqualTo(20); assertThat(defaults.path("page").asInt()).isZero();
            for (String sort : List.of("created", "title", "slug")) {
                assertThat(app.get(path + "?sort=" + sort + "&direction=desc", token).statusCode()).isEqualTo(200);
            }
            for (String boundary : List.of("size=99&page=101", "size=100&page=100", "size=1&page=10000")) {
                var empty = app.get(path + "?" + boundary, token); assertThat(empty.statusCode()).isEqualTo(200);
                var value = JSON.readTree(empty.body()); assertThat(value.path("items").isArray()).isTrue();
                assertThat(value.path("items").size()).isZero(); assertThat(value.path("total").asLong()).isEqualTo(3);
            }
            for (String invalid : List.of("page=-1", "page=2147483648", "page=2147483647&size=100", "size=0", "size=-1", "size=101", "size=bad",
                    "size=100&page=101", "size=1&page=10001", "sort=body", "sort=title%3Bdrop%20table%20note", "direction=asc%20nulls%20first", "direction=", "page=")) {
                var bad = app.get(path + "?" + invalid, token); assertThat(bad.statusCode()).as(invalid).isEqualTo(400);
                assertThat(bad.body()).doesNotContain("drop table", "select ");
            }
            assertThat(JSON.readTree(app.get(path, token).body()).path("total").asLong()).isEqualTo(3);
        }
    }
    @Test void anAuthorizedMemberUpdatesAndDeletesOnlyNotesInsideTheirWorkspace() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + Postgres.freshUrl())) {
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String workspace = JSON.readTree(send(app, "POST", "/api/workspaces", token, "{\"name\":\"Team\"}").body()).path("id").asString();
            String second = JSON.readTree(send(app, "POST", "/api/workspaces", token, "{\"name\":\"Other\"}").body()).path("id").asString();
            String location = send(app, "POST", "/api/workspaces/" + workspace + "/notes", token,
                    "{\"slug\":\"draft\",\"title\":\"Draft\",\"body\":\"Old\"}").headers().firstValue("Location").orElseThrow();
            var updated = send(app, "PUT", location, token, "{\"title\":\"Published 🌿\",\"body\":\"New\"}");
            assertThat(updated.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(app.get(location, token).body()).path("title").asString()).isEqualTo("Published 🌿");
            assertThat(JSON.readTree(updated.body()).path("slug").asString()).isEqualTo("draft");
            String wrongWorkspace = location.replace(workspace, second);
            assertThat(app.get(wrongWorkspace, token).statusCode()).isEqualTo(404);
            assertThat(send(app, "PUT", wrongWorkspace, token, "{\"title\":\"Spoof\",\"body\":\"\"}").statusCode()).isEqualTo(404);
            assertThat(send(app, "DELETE", wrongWorkspace, token, "").statusCode()).isEqualTo(404);
            assertThat(send(app, "DELETE", location, token, "").statusCode()).isEqualTo(204);
            assertThat(app.get(location, token).statusCode()).isEqualTo(404);
            assertThat(send(app, "DELETE", location, token, "").statusCode()).isEqualTo(404);
        }
    }
    @Test void workspaceMembershipIsCurrentAndCannotBeReplacedByClaimsOrHeaders() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + database)) {
            String owner = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String other = issuer.token("a", Map.of("scope", "notes:read notes:write", "sub", "bob", "tenant", "admin"), Set.of());
            var workspace = send(app, "POST", "/api/workspaces", owner, "{\"name\":\"Private\"}");
            String id = JSON.readTree(workspace.body()).path("id").asString();
            var created = send(app, "POST", "/api/workspaces/" + id + "/notes", owner, "{\"slug\":\"private\",\"title\":\"Private\",\"body\":\"secret-body\"}");
            String location = created.headers().firstValue("Location").orElseThrow();
            assertThat(app.get(location, null).statusCode()).isEqualTo(401);
            assertThat(app.get(location, issuer.token()).statusCode()).isEqualTo(403);
            assertDenied(app.get(location, other, "X-User-Id", "alice", "X-Tenant-Id", id));
            Postgres.execute(database, "delete from workspace_member where workspace_id = '" + id + "'");
            assertDenied(app.get(location, owner));
            assertThat(send(app, "POST", "/api/workspaces/" + id + "/notes", owner, "{\"slug\":\"second\",\"title\":\"Second\",\"body\":\"\"}").statusCode()).isEqualTo(403);
        }
    }
    private static void assertDenied(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).contains("application/problem+json");
        assertThat(response.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
        var problem = JSON.readTree(response.body());
        assertThat(problem.path("code").asString()).isEqualTo("workspace_forbidden");
        assertThat(problem.path("traceId").asString()).isNotBlank();
        assertThat(response.body()).doesNotContain("secret-body", "select ", "workspace_member");
    }
    @Test void authenticatedCallerCreatesAndReadsPersistedNotesAfterApplicationRestart() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer()) {
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String location;
            try (var app = new RunningApp(issuer, "--spring.datasource.url=" + database)) {
                var workspace = send(app, "POST", "/api/workspaces", token, "{\"name\":\"编辑团队\"}");
                assertThat(workspace.statusCode()).isEqualTo(201);
                String workspaceId = JSON.readTree(workspace.body()).path("id").asString();
                var created = send(app, "POST", "/api/workspaces/" + workspaceId + "/notes", token,
                        "{\"slug\":\"first-note\",\"title\":\"独立金样 🌱\",\"body\":\"line one\\nline two\"}");
                assertThat(created.statusCode()).isEqualTo(201);
                location = created.headers().firstValue("Location").orElseThrow();
                assertNote(app.get(location, token));
            }
            try (var restarted = new RunningApp(issuer, "--spring.datasource.url=" + database)) { assertNote(restarted.get(location, token)); }
        }
    }
    private static void assertNote(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        var body = JSON.readTree(response.body());
        assertThat(body.path("slug").asString()).isEqualTo("first-note");
        assertThat(body.path("title").asString()).isEqualTo("独立金样 🌱");
        assertThat(body.path("body").asString()).isEqualTo("line one\nline two");
    }
    static HttpResponse<String> send(RunningApp app, String method, String path, String token, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(app.base + path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return app.client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
