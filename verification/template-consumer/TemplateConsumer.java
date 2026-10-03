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
        Process helper = new ProcessBuilder(JAVA, "-Xmx64m", "-XX:ActiveProcessorCount=2", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                "dev/LocalIssuer.java", app.relativize(state).toString())
                .directory(app.toFile()).redirectErrorStream(true).redirectOutput(helperLog.toFile()).start();
        try {
            awaitFile(state.resolve("no-scope-token.txt"), helper, helperLog);
            String token = Files.readString(state.resolve("token.txt")), denied = Files.readString(state.resolve("no-scope-token.txt"));
            Path jar = app.resolve("target/secured-api-1.0.0-SNAPSHOT.jar");
            check(Files.isRegularFile(jar), "packaged application jar missing");
            for (boolean virtual : new boolean[]{false, true}) {
                Path log = evidence.resolve("packaged-" + virtual + ".log");
                Process running = launch(app, jar, log, "--spring.config.additional-location=" + state.resolve("local.properties").toUri().toASCIIString(),
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
                    Files.writeString(evidence.resolve("packaged-" + virtual + "-result.txt"),
                            "PASS actual executable jar; health 200; anonymous 401; signed actor 200; denied 403; query-only 401; unlisted 403\n"
                                    + "virtual=" + virtual + " sha256=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))) + "\n");
                } finally { stop(running); }
            }
            Path failedLog = evidence.resolve("production-missing-trust.log");
            Process invalid = launch(app, jar, failedLog, "--spring.profiles.active=prod");
            try {
                check(invalid.waitFor(30, TimeUnit.SECONDS), "unconfigured production process did not stop");
                check(invalid.exitValue() != 0 && Files.readString(failedLog).contains("Invalid application JWT trust policy"), "production silently accepted missing trust");
            } finally { stop(invalid); }
            System.out.println("PACKAGED_TEMPLATE_PASS platform/virtual restart; no repository source or test classpath");
        } finally { stop(helper); }
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
