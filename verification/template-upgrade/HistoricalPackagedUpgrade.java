import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** External historical before/after executable-jar proof; never loads application test classes. */
class HistoricalPackagedUpgrade {
    static final String JAVA = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
    public static void main(String[] args) throws Exception {
        Path fixture = Path.of(args[0]).toAbsolutePath(), before = Path.of(args[1]).toAbsolutePath();
        Path after = Path.of(args[2]).toAbsolutePath(), evidence = Files.createDirectories(Path.of(args[3]).toAbsolutePath());
        Path issuerState = evidence.resolve("issuer"), databaseState = evidence.resolve("database");
        Path issuerLog = evidence.resolve("issuer.log"), databaseLog = evidence.resolve("database.log");
        Process issuer = null, database = null; Throwable primary = null;
        try {
            issuer = new ProcessBuilder(JAVA, "-Xmx64m", "-XX:ActiveProcessorCount=2", "dev/LocalIssuer.java", issuerState.toString())
                    .directory(fixture.toFile()).redirectErrorStream(true).redirectOutput(issuerLog.toFile()).start();
            database = new ProcessBuilder(JAVA, "-Xmx64m", "-XX:ActiveProcessorCount=2", "dev/LocalDatabase.java", databaseState.toString())
                    .directory(fixture.toFile()).redirectErrorStream(true).redirectOutput(databaseLog.toFile()).start();
            awaitFile(databaseState.resolve("database.properties"), database, databaseLog);
            awaitFile(issuerState.resolve("no-scope-token.txt"), issuer, issuerLog);
            String token = Files.readString(issuerState.resolve("token.txt")), denied = Files.readString(issuerState.resolve("no-scope-token.txt"));
            String location = null, storedBefore = null;
            for (int stage = 0; stage < 3; stage++) {
                boolean virtual = stage == 2;
                Path jar = stage == 0 ? before : after;
                String label = stage == 0 ? "before-platform" : virtual ? "after-virtual" : "after-platform";
                Path log = evidence.resolve(label + ".log");
                Process app = launch(fixture, jar, log,
                        "--spring.config.additional-location=" + issuerState.resolve("local.properties").toUri().toASCIIString() + "," + databaseState.resolve("database.properties").toUri().toASCIIString(),
                        "--spring.threads.virtual.enabled=" + virtual);
                System.out.println("PROCESS_START stage=" + label + " pid=" + app.pid() + " jarSha256=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))));
                try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
                    String base = awaitServer(app, log);
                    check(get(client, base, "/api/greeting/customer", null).statusCode() == 401, "custom endpoint lost authentication");
                    check(get(client, base, "/api/greeting/customer", denied).statusCode() == 403, "custom endpoint lost authorization");
                    var custom = get(client, base, "/api/greeting/customer", token);
                    check(custom.statusCode() == 200 && custom.body().equals("{\"label\":\"orders-north\",\"application\":\"customer-notes\"}"), "custom business/configuration changed: " + custom.statusCode() + " " + custom.body());
                    if (stage == 0) {
                        var workspace = send(client, base, "POST", "/api/workspaces", token, "{\"name\":\"Upgrade workspace\"}");
                        check(workspace.statusCode() == 201, "workspace creation failed");
                        var created = send(client, base, "POST", workspace.headers().firstValue("Location").orElseThrow(), token,
                                "{\"slug\":\"upgrade-proof\",\"title\":\"Before 🌱\",\"body\":\"literal first\\nsecond\"}");
                        check(created.statusCode() == 201, "historical note creation failed: " + created.statusCode());
                        location = created.headers().firstValue("Location").orElseThrow();
                        storedBefore = get(client, base, location, token).body();
                        check(storedBefore.contains("Before 🌱") && storedBefore.contains("literal first\\nsecond"), "literal note missing");
                        Files.writeString(evidence.resolve("persisted-before.json"), storedBefore);
                        Files.writeString(evidence.resolve("note-location.txt"), location);
                    }
                    var stored = get(client, base, location, token);
                    check(stored.statusCode() == 200 && stored.body().equals(storedBefore), "persistent note changed across historical upgrade/restart");
                    Files.writeString(evidence.resolve(label + "-result.txt"), "PASS custom401/403/200 literal configuration; identical persisted note; virtual=" + virtual + "\n");
                } finally { stop(app); }
            }
            System.out.println("HISTORICAL_PACKAGED_UPGRADE_PASS same issuer/database; before-platform -> after-platform -> after-virtual");
        } catch (Exception | Error failure) { primary = failure; throw failure; }
        finally {
            Throwable cleanup = null;
            try { if (issuer != null) stop(issuer); } catch (Exception | Error failure) { cleanup = failure; }
            try {
                if (database != null) {
                    if (database.isAlive()) database.getOutputStream().close();
                    if (!database.waitFor(85, TimeUnit.SECONDS)) { stop(database); throw new AssertionError("database cleanup timeout"); }
                    check(database.exitValue() == 0 && !Files.exists(databaseState.resolve("data/postmaster.pid")), "database remained running or cleanup failed");
                    System.out.println("DATABASE_STOP exit=" + database.exitValue() + " postmasterAbsent=true");
                }
            } catch (Exception | Error failure) { cleanup = append(cleanup, failure); }
            if (cleanup != null) {
                if (primary != null) primary.addSuppressed(cleanup);
                else if (cleanup instanceof Exception exception) throw exception;
                else throw (Error) cleanup;
            }
        }
    }
    private static Throwable append(Throwable primary, Throwable next) {
        if (primary == null) return next;
        if (primary != next) primary.addSuppressed(next);
        return primary;
    }
    private static Path path(String value) {
        return value.startsWith("file:") ? Path.of(URI.create(value)) : Path.of(value);
    }
    private static Process launch(Path app, Path jar, Path log, String... options) throws Exception {
        var command = new ArrayList<>(List.of(JAVA, "-Xmx256m", "-XX:ActiveProcessorCount=2", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-jar", jar.toAbsolutePath().toString(),
                "--server.port=0", "--server.address=127.0.0.1", "--spring.main.banner-mode=off", "--server.shutdown=immediate"));
        command.addAll(List.of(options));
        return new ProcessBuilder(command).directory(app.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
    }
    private static HttpResponse<String> get(HttpClient client, String base, String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(5));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> send(HttpClient client, String base, String method, String path, String token, String body) throws Exception {
        return send(client, base, method, path, token, body, null);
    }
    private static HttpResponse<String> send(HttpClient client, String base, String method, String path, String token, String body, String key) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (key != null) request.header("Idempotency-Key", key);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private static void awaitFile(Path file, Process process, Path log) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!Files.isRegularFile(file) && process.isAlive() && System.nanoTime() < deadline) Thread.sleep(50);
        check(Files.isRegularFile(file), "local fixture failed: " + Files.readString(log));
    }
    private static String awaitServer(Process process, Path log) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        var pattern = Pattern.compile("Tomcat started on port (\\d+)");
        while (process.isAlive() && System.nanoTime() < deadline) {
            var match = pattern.matcher(Files.readString(log));
            if (match.find()) return "http://127.0.0.1:" + match.group(1);
            Thread.sleep(50);
        }
        throw new AssertionError("application not ready: " + Files.readString(log));
    }
    private static void stop(Process process) throws Exception {
        long pid = process.pid();
        if (process.isAlive()) process.destroy();
        if (!process.waitFor(10, TimeUnit.SECONDS)) { process.destroyForcibly(); check(process.waitFor(10, TimeUnit.SECONDS), "child process did not stop"); }
        System.out.println("PROCESS_STOP pid=" + pid + " alive=" + process.isAlive() + " exit=" + process.exitValue() + " mechanism=Process.destroy-no-graceful-claim");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
