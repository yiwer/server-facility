package com.example.api;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkImportHttpTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @TempDir Path staging;

    @Test void importsQuotedUtf8RowsInOrderAndRestoresOwnedStaging() throws Exception {
        Files.writeString(staging.resolve("UNRELATED.ticket31"), "unrelated sentinel bytes");
        var baseline = snapshot(staging);
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--bench.staging-directory=" + staging)) {
            String token = issuer.token();
            String csv = "name,quantity\n\"蓝色, widget\",3\nplain,2\n";
            for (String content : new String[] {csv, csv.replace("\n", "\r\n")}) {
                var response = post(app, token, content.getBytes(StandardCharsets.UTF_8));
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(JSON.readTree(response.body())).isEqualTo(JSON.readTree(
                        "{\"rows\":2,\"totalQuantity\":5,\"items\":[{\"name\":\"蓝色, widget\",\"quantity\":3},{\"name\":\"plain\",\"quantity\":2}]}"));
                assertThat(snapshot(staging)).isEqualTo(baseline);
            }
        }
    }

    @Test void acceptsRowUnicodeAndFileByteBounds() throws Exception {
        Files.writeString(staging.resolve("UNRELATED.ticket31"), "unrelated sentinel bytes");
        var baseline = snapshot(staging);
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--bench.staging-directory=" + staging)) {
            String token = issuer.token();
            var thirtyTwo = post(app, token, ("name,quantity\n" + "plain,1\n".repeat(32)).getBytes(StandardCharsets.UTF_8));
            assertThat(thirtyTwo.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(thirtyTwo.body()).path("rows").intValue()).isEqualTo(32);
            assertThat(JSON.readTree(thirtyTwo.body()).path("totalQuantity").intValue()).isEqualTo(32);
            assertThat(snapshot(staging)).isEqualTo(baseline);

            String name = "😀".repeat(40);
            var unicode = post(app, token, ("name,quantity\n" + name + ",1000\n").getBytes(StandardCharsets.UTF_8));
            assertThat(unicode.statusCode()).isEqualTo(200);
            var item = JSON.readTree(unicode.body()).path("items").get(0);
            assertThat(item.path("name").asString()).isEqualTo(name);
            assertThat(item.path("quantity").intValue()).isEqualTo(1000);
            assertThat(snapshot(staging)).isEqualTo(baseline);

            var limitCsv = new StringBuilder("name,quantity\n");
            for (int row = 0; row < 25; row++) limitCsv.append(name).append(",1").append(row < 7 ? "\r\n" : "\n");
            byte[] limitBytes = limitCsv.toString().getBytes(StandardCharsets.UTF_8);
            assertThat(limitBytes.length).isEqualTo(4096);
            var limit = post(app, token, limitBytes);
            assertThat(limit.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(limit.body()).path("rows").intValue()).isEqualTo(25);
            assertThat(JSON.readTree(limit.body()).path("totalQuantity").intValue()).isEqualTo(25);
            assertThat(snapshot(staging)).isEqualTo(baseline);
        }
    }

    @Test void invalidDataAndMultipartShapeProduceSafeProblemsAndRestoreStaging() throws Exception {
        Files.writeString(staging.resolve("UNRELATED.ticket31"), "unrelated sentinel bytes");
        var baseline = snapshot(staging);
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--bench.staging-directory=" + staging)) {
            String token = issuer.token();
            var invalid = new ArrayList<byte[]>();
            for (String csv : List.of(
                    "name,quantity\nplain,0\n", "name,quantity\nplain,1001\n",
                    "name,quantity\nplain,BENCH_PRIVATE_FAILURE\n", "name,quantity\nplain,1.5\n",
                    "name,quantity\n,2\n", "name,quantity\n   ,2\n",
                    "name,quantity\n" + "a".repeat(41) + ",2\n",
                    "name,quantity\n" + "😀".repeat(41) + ",2\n",
                    "name,quantity\n" + "plain,1\n".repeat(33),
                    "wrong,quantity\nplain,2\n", "name,quantity\nplain\n",
                    "name,quantity\nplain,2,extra\n", "name,quantity\n\"unfinished,2\n",
                    "name,quantity\n", "")) invalid.add(csv.getBytes(StandardCharsets.UTF_8));
            var malformed = new ByteArrayOutputStream();
            malformed.write("name,quantity\n".getBytes(StandardCharsets.UTF_8));
            malformed.write(new byte[] {(byte) 0xc3, 0x28, ',', '2', '\n'});
            invalid.add(malformed.toByteArray());
            for (byte[] csv : invalid) {
                var response = post(app, token, csv);
                assertThat(snapshot(staging)).isEqualTo(baseline);
                assertProblem(response, 400, "invalid_import", "Import data is invalid", token);
            }
            byte[] valid = "name,quantity\nplain,1\n".getBytes(StandardCharsets.UTF_8);
            for (byte[][] parts : List.of(new byte[0][], new byte[][] {valid, valid})) {
                var response = post(app, token, parts);
                assertThat(snapshot(staging)).isEqualTo(baseline);
                assertProblem(response, 400, "invalid_import", "Import data is invalid", token);
            }
            var oversized = post(app, token, new byte[4097]);
            assertThat(snapshot(staging)).isEqualTo(baseline);
            assertProblem(oversized, 413, "import_too_large", "Import exceeds the byte limit", token);
        }
    }

    private static void assertProblem(HttpResponse<String> response, int status, String code, String detail, String token) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).startsWith("application/problem+json"));
        var problem = JSON.readTree(response.body());
        assertThat(problem.path("status").isIntegralNumber()).isTrue();
        assertThat(problem.path("status").intValue()).isEqualTo(status);
        assertThat(problem.path("code").asString()).isEqualTo(code);
        assertThat(problem.path("detail").asString()).isEqualTo(detail);
        assertThat(response.body()).doesNotContain(token, "BENCH_PRIVATE_FAILURE", "BENCH_UPSTREAM_ONLY", "Exception",
                "\"stack\"", "\"stackTrace\"", "\"exception\"", "\"cause\"");
    }

    private static HttpResponse<String> post(RunningApp app, String token, byte[]... files) throws Exception {
        String boundary = "benchmark-import-boundary";
        var body = new ByteArrayOutputStream();
        for (byte[] file : files) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"import.csv\"\r\n"
                    + "Content-Type: text/csv\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(file);
            body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return app.client.send(HttpRequest.newBuilder(URI.create(app.base + "/api/bench/import"))
                .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Map<String, String> snapshot(Path root) throws Exception {
        var state = new TreeMap<String, String>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                state.put(root.relativize(path).toString(), Files.isDirectory(path)
                        ? "directory" : Base64.getEncoder().encodeToString(Files.readAllBytes(path)));
            }
        }
        return state;
    }
}
