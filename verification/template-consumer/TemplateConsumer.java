import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** External JDK-only client. The application is launched exclusively from its independently built executable jar. */
class TemplateConsumer {
    static final String JAVA = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
    public static void main(String[] args) throws Exception {
        Path app = path(args[0]).toAbsolutePath(), evidence = Files.createDirectories(path(args[1]));
        Path state = app.resolve("target/local-trust-" + UUID.randomUUID());
        Path helperLog = evidence.resolve("local-fixture.log");
        Process helper = null, database = null;
        Path databaseRoot = null, databaseState = null;
        Path databaseLog = evidence.resolve("local-database.log");
        Throwable primary = null;
        try {
        helper = new ProcessBuilder(JAVA, "-Xmx64m", "-XX:ActiveProcessorCount=2", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                "dev/LocalIssuer.java", app.relativize(state).toString())
                .directory(app.toFile()).redirectErrorStream(true).redirectOutput(helperLog.toFile()).start();
        databaseRoot = Files.createTempDirectory("facility-packaged-database-");
        databaseState = databaseRoot.resolve("cluster");
        database = new ProcessBuilder(JAVA, "-Xmx64m", "-XX:ActiveProcessorCount=2", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "dev/LocalDatabase.java", databaseState.toString())
                .directory(app.toFile()).redirectErrorStream(true).redirectOutput(databaseLog.toFile()).start();
            awaitFile(databaseState.resolve("database.properties"), database, databaseLog);
            awaitFile(state.resolve("no-scope-token.txt"), helper, helperLog);
            String token = Files.readString(state.resolve("token.txt")), denied = Files.readString(state.resolve("no-scope-token.txt"));
            Path jar = app.resolve("target/secured-api-1.0.0-SNAPSHOT.jar");
            check(Files.isRegularFile(jar), "packaged application jar missing");
            String noteLocation = null, workspaceLocation = null;
            for (boolean virtual : new boolean[]{false, true}) {
                Path log = evidence.resolve("packaged-" + virtual + ".log");
                Process running = launch(app, jar, log, "--spring.config.additional-location=" + state.resolve("local.properties").toUri().toASCIIString()
                                + "," + databaseState.resolve("database.properties").toUri().toASCIIString(),
                        "--spring.threads.virtual.enabled=" + virtual);
                try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
                    String base = awaitServer(running, log);
                    check(get(client, base, "/health", null).statusCode() == 200, "health failed");
                    var missing = get(client, base, "/api/greeting", null);
                    check(missing.statusCode() == 401 && missing.headers().firstValue("WWW-Authenticate").orElse("").equals("Bearer"), "anonymous business was not challenged");
                    var success = get(client, base, "/api/greeting", token);
                    check(success.statusCode() == 200 && success.body().contains("\"subject\":\"local-demo\"") && success.body().contains("\"message\":\"Hello\""), "verified actor missing");
                    check(success.headers().firstValue("Set-Cookie").isEmpty(), "stateless API created a session");
                    var forbidden = get(client, base, "/api/greeting", denied);
                    check(forbidden.statusCode() == 403 && forbidden.headers().firstValue("WWW-Authenticate").orElse("").equals("Bearer error=\"insufficient_scope\""), "scope permission not enforced");
                    for (var failed : List.of(missing, forbidden)) {
                        check(failed.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"), "safe problem protocol missing");
                        check(failed.body().contains("\"traceId\"") && !failed.body().contains(token) && !failed.body().contains("Exception"), "unsafe error body");
                    }
                    check(get(client, base, "/api/greeting?access_token=" + token, null).statusCode() == 401, "query token unexpectedly authenticated");
                    check(get(client, base, "/unlisted", token).statusCode() == 403, "unlisted operation allowed");
                    if (!virtual) {
                        var workspace = send(client, base, "POST", "/api/workspaces", token, "{\"name\":\"Persistent workspace\"}");
                        check(workspace.statusCode() == 201, "workspace creation failed: " + workspace.statusCode());
                        workspaceLocation = workspace.headers().firstValue("Location").orElseThrow();
                        var created = send(client, base, "POST", workspaceLocation, token,
                                "{\"slug\":\"restart-proof\",\"title\":\"Persistent 🌱\",\"body\":\"literal first\\nsecond\"}");
                        check(created.statusCode() == 201, "persistent note creation failed");
                        noteLocation = created.headers().firstValue("Location").orElseThrow();
                    }
                    var stored = get(client, base, noteLocation, token);
                    check(stored.statusCode() == 200 && stored.body().contains("Persistent 🌱") && stored.body().contains("literal first\\nsecond"), "literal persistent representation lost across process restart");
                    check(get(client, base, workspaceLocation + "?size=101", token).statusCode() == 400, "page budget not enforced");
                    check(get(client, base, workspaceLocation + "?sort=slug", token).body().contains("\"total\":1"), "persistent list missing");
                    if (virtual) {
                        check(send(client, base, "PUT", noteLocation, token, "{\"title\":\"Updated\",\"body\":\"after restart\"}").statusCode() == 200, "update after restart failed");
                        check(send(client, base, "DELETE", noteLocation, token, null).statusCode() == 204, "delete failed");
                        check(get(client, base, noteLocation, token).statusCode() == 404, "deleted note still readable");
                    }
                    Files.writeString(evidence.resolve("packaged-" + virtual + "-result.txt"),
                            "PASS actual executable jar; health 200; anonymous 401; signed actor 200; denied 403; query-only 401; unlisted 403\n"
                                    + "PostgreSQL18.6 migrated; signed CRUD; literal persistence across process restart; bounded list; virtual=" + virtual + " sha256=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))) + "\n");
                } finally { stop(running); }
            }
            Path failedLog = evidence.resolve("production-missing-trust.log");
            Process invalid = launch(app, jar, failedLog, "--spring.profiles.active=prod", "--spring.config.additional-location=" + databaseState.resolve("database.properties").toUri().toASCIIString());
            try {
                check(invalid.waitFor(30, TimeUnit.SECONDS), "unconfigured production process did not stop");
                check(invalid.exitValue() != 0 && Files.readString(failedLog).contains("Invalid application JWT trust policy"), "production silently accepted missing trust");
            } finally { stop(invalid); }
            System.out.println("PACKAGED_TEMPLATE_PASS platform/virtual restart; no repository source or test classpath");
        } catch (Exception | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            Throwable cleanup = null;
            try { if (helper != null) stop(helper); } catch (Exception | Error failure) { cleanup = failure; }
            try {
                if (database != null) {
                    if (database.isAlive()) database.getOutputStream().close();
                    if (!database.waitFor(85, TimeUnit.SECONDS)) { stop(database); throw new AssertionError("local database cleanup timed out"); }
                    check(database.exitValue() == 0, "local database cleanup failed: " + Files.readString(databaseLog));
                }
            } catch (Exception | Error failure) { cleanup = append(cleanup, failure); }
            // Archive even when startup or shutdown failed; never delete the only native diagnostics first.
            try {
                Path archived = Files.createDirectories(evidence.resolve("postgres"));
                if (databaseState != null && Files.isDirectory(databaseState)) try (var files = Files.list(databaseState)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) Files.copy(file, archived.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception | Error failure) { cleanup = append(cleanup, failure); }
            try {
                if (databaseState != null) check(!Files.exists(databaseState.resolve("data/postmaster.pid")), "native database remained running");
                if (databaseRoot != null && cleanup == null) {
                Path owned = databaseRoot.toRealPath();
                check(owned.getParent().equals(Path.of(System.getProperty("java.io.tmpdir")).toRealPath())
                        && owned.getFileName().toString().startsWith("facility-packaged-database-"), "unexpected owned database directory");
                try (var files = Files.walk(owned)) { for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file); }
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
        var command = new ArrayList<>(List.of(JAVA, "-Xmx256m", "-XX:ActiveProcessorCount=2", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-jar", app.relativize(jar).toString(),
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
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
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
        if (process.isAlive()) process.destroy();
        if (!process.waitFor(10, TimeUnit.SECONDS)) { process.destroyForcibly(); check(process.waitFor(10, TimeUnit.SECONDS), "child process did not stop"); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
