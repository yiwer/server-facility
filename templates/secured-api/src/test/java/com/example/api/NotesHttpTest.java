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
