package com.example.api;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.*;
import static org.assertj.core.api.Assertions.*;

/** Owns one real JVM, its control sockets, HTTP client and independently observed database sessions. */
final class CommandProcess implements AutoCloseable {
    final String database, application = "recovery-" + UUID.randomUUID(), secret = UUID.randomUUID().toString();
    final Path evidence;
    final Process process;
    final HttpClient client;
    final String base;
    private final ServerSocket control;
    private boolean closed, killed;

    CommandProcess(TestIssuer issuer, String database) throws Exception { this(issuer, database, false); }
    CommandProcess(TestIssuer issuer, String database, boolean longLocks) throws Exception {
        this.database = database;
        evidence = Files.createDirectories(Path.of("target", "command-processes", application)).toAbsolutePath();
        Files.createDirectory(evidence.resolve("tmp"));
        ServerSocket listener = null; HttpClient http = null; Process child = null;
        long started = System.nanoTime();
        try {
            listener = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")); listener.setSoTimeout(45000);
            http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            var settings = new Properties(); settings.setProperty("database", database); settings.setProperty("issuer", issuer.issuer());
            settings.setProperty("application", application); settings.setProperty("secret", secret);
            settings.setProperty("longLocks", Boolean.toString(longLocks));
            settings.setProperty("controlPort", Integer.toString(listener.getLocalPort()));
            Path configuration = evidence.resolve("host.properties");
            try (var output = Files.newOutputStream(configuration)) { settings.store(output, "Owned test host; contains no signed credential"); }
            var manifest = new Manifest(); manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            String classpath = Arrays.stream(System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"))
                            .split(java.util.regex.Pattern.quote(File.pathSeparator)))
                    .map(entry -> Path.of(entry).toAbsolutePath().toUri().toASCIIString()).collect(java.util.stream.Collectors.joining(" "));
            manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, classpath);
            try (var jar = new JarOutputStream(Files.newOutputStream(evidence.resolve("classpath.jar")), manifest)) {}
            String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
            var command = List.of(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                    "-Xmx128m", "-XX:ActiveProcessorCount=2", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                    "-Dstderr.encoding=UTF-8", "-Djava.io.tmpdir=tmp", "-Djacoco-agent.destfile=coverage.exec", "-Dcatalina.home=.", "-cp", "classpath.jar",
                    "com.example.fixtures.CommandRecoveryHost", configuration.toUri().toASCIIString());
            child = new ProcessBuilder(command).directory(evidence.toFile()).redirectErrorStream(true)
                    .redirectOutput(evidence.resolve("host.log").toFile()).start();
            try (var ready = listener.accept(); var input = new DataInputStream(ready.getInputStream())) {
                ready.setSoTimeout(5000);
                assertThat(input.readUTF()).isEqualTo("ready"); assertThat(input.readLong()).isEqualTo(child.pid());
                base = "http://127.0.0.1:" + input.readInt();
                String facility = input.readUTF(); assertThat(facility).endsWith(".jar");
                record("ready pid=" + child.pid() + " startupMillis=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                        + " longLocks=" + longLocks + " facility=" + facility);
                listener.setSoTimeout(12000);
            }
            process = child; control = listener; client = http;
        } catch (Exception | Error failure) {
            if (child != null) {
                try { forceStop(child); awaitSessions(0); }
                catch (Exception | Error cleanup) { failure.addSuppressed(cleanup); }
            }
            if (listener != null) try { listener.close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            if (http != null) try { closeClient(http); } catch (Exception | Error cleanup) { failure.addSuppressed(cleanup); }
            failure.addSuppressed(new IllegalStateException("Child startup diagnostics: " + evidence)); throw failure;
        }
    }
    HttpResponse<String> send(String method, String path, String token, String key, String body) throws Exception {
        return client.send(request(method, path, token, key, body, null), HttpResponse.BodyHandlers.ofString());
    }
    CompletableFuture<HttpResponse<String>> start(String path, String token, String key, String body, String phase) {
        return client.sendAsync(request("POST", path, token, key, body, phase), HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> get(String path, String token) throws Exception { return send("GET", path, token, null, ""); }
    private HttpRequest request(String method, String path, String token, String key, String body, String phase) {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (key != null) request.header("Idempotency-Key", key);
        if (phase != null) request.header("X-Test-Command-Gate", secret).header("X-Test-Command-Phase", phase);
        return request.build();
    }
    Socket raw(String path, String token, String key, String body, String phase) throws Exception {
        var socket = new Socket();
        try {
            socket.connect(new InetSocketAddress("127.0.0.1", URI.create(base).getPort()), 2000); socket.setSoTimeout(5000);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            String headers = "POST " + path + " HTTP/1.1\r\nHost: 127.0.0.1\r\nAuthorization: Bearer " + token
                    + "\r\nContent-Type: application/json\r\nIdempotency-Key: " + key
                    + "\r\nX-Test-Command-Gate: " + secret + "\r\nX-Test-Command-Phase: " + phase
                    + "\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().write(bytes); socket.getOutputStream().flush(); return socket;
        } catch (Exception failure) { socket.close(); throw failure; }
    }
    Gate awaitGate(String phase) throws Exception {
        Socket socket = control.accept(); socket.setSoTimeout(5000);
        try {
            var input = new DataInputStream(socket.getInputStream());
            assertThat(input.readUTF()).isEqualTo(phase); assertThat(input.readLong()).isEqualTo(process.pid());
            record("gate=" + phase + " pid=" + process.pid()); return new Gate(socket);
        } catch (Exception | Error failure) { socket.close(); throw failure; }
    }
    final class Gate implements AutoCloseable {
        private final Socket socket;
        Gate(Socket socket) { this.socket = socket; }
        void release() throws Exception { socket.getOutputStream().write(1); socket.getOutputStream().flush(); record("gate released"); }
        @Override public void close() throws IOException { socket.close(); }
    }
    void kill() throws Exception {
        assertThat(process.isAlive()).as("owned child must be alive at the observed fault point").isTrue();
        long started = System.nanoTime(); killed = true; forceStop(process);
        awaitSessions(0); record("intentional kill confirmed; sessions=0 cleanupMillis=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    }
    long sessions() throws SQLException { return scalar("select count(*) from pg_stat_activity where application_name = '" + application + "'"); }
    void assertIdle() throws Exception {
        long sessions = sessions(); assertThat(sessions).isBetween(1L, 4L);
        awaitScalar("select count(*) from pg_stat_activity where application_name = '" + application + "' and state like 'idle in transaction%'", 0);
        record("idle check sessions=" + sessions + " poolBudget=4 idleTransactions=0");
    }
    void awaitSessions(long expected) throws Exception { awaitScalar("select count(*) from pg_stat_activity where application_name = '" + application + "'", expected); }
    static Connection admin() throws SQLException {
        var properties = new Properties(); properties.setProperty("user", "postgres"); properties.setProperty("password", "");
        properties.setProperty("connectTimeout", "2"); properties.setProperty("socketTimeout", "4"); properties.setProperty("cancelSignalTimeout", "1");
        return DriverManager.getConnection(Postgres.adminUrl(), properties);
    }
    static long scalar(String sql) throws SQLException {
        try (var connection = admin(); var statement = connection.createStatement()) {
            statement.setQueryTimeout(3); try (var rows = statement.executeQuery(sql)) { rows.next(); return rows.getLong(1); }
        }
    }
    static void awaitScalar(String sql, long expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5); long actual;
        do { actual = scalar(sql); if (actual == expected) return; TimeUnit.MILLISECONDS.sleep(20); } while (System.nanoTime() < deadline);
        assertThat(actual).as("observed database state before finite deadline: %s", sql).isEqualTo(expected);
    }
    void record(String event) throws IOException {
        Files.writeString(evidence.resolve("events.log"), java.time.Instant.now() + " " + event + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    @Override public void close() throws Exception {
        if (closed) return; closed = true;
        long started = System.nanoTime(); Throwable failure = null;
        try {
            if (!killed) {
                assertThat(process.isAlive()).as("unexpected child exit; %s", evidence).isTrue();
                process.getOutputStream().write('\n'); process.getOutputStream().flush();
                if (!process.waitFor(15, TimeUnit.SECONDS))
                    throw new AssertionError("Graceful child shutdown exceeded 15s: " + evidence);
                assertThat(process.exitValue()).as("child exit; %s", evidence).isZero();
            }
        } catch (Exception | Error primary) { failure = primary; }
        // Even an exception writing stdin must not leave this known owned process running.
        try { if (process.isAlive()) forceStop(process); }
        catch (Exception | Error cleanup) { failure = append(failure, cleanup); }
        if (!process.isAlive()) try {
            awaitSessions(0); record("closed pid=" + process.pid() + " exitConfirmed=true sessions=0 cleanupMillis="
                    + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            // Killed JVMs cannot run deleteOnExit. All their temp files remain inside this owned evidence directory.
            long bytes = 0; int files = 0;
            try (var paths = Files.walk(evidence)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) { bytes += Files.size(path); files++; }
            }
            assertThat(files).as("owned child evidence file budget").isLessThanOrEqualTo(128);
            assertThat(bytes).as("owned child evidence byte budget").isLessThanOrEqualTo(16L * 1024 * 1024);
            record("retainedEvidenceFiles=" + files + " retainedEvidenceBytes=" + bytes + " budget=128files/16MiB");
        } catch (Exception | Error cleanup) { failure = append(failure, cleanup); }
        try { control.close(); } catch (Exception cleanup) { failure = append(failure, cleanup); }
        try { closeClient(client); } catch (Exception | Error cleanup) { failure = append(failure, cleanup); }
        if (failure instanceof Exception exception) throw exception;
        if (failure instanceof Error error) throw error;
    }
    private static void forceStop(Process child) throws Exception {
        if (child.isAlive()) child.destroyForcibly();
        if (!child.waitFor(5, TimeUnit.SECONDS))
            throw new AssertionError("Owned child exit remains UNCONFIRMED after kill deadline; pid=" + child.pid());
    }
    private static void closeClient(HttpClient client) throws Exception {
        client.shutdown();
        if (!client.awaitTermination(Duration.ofSeconds(3))) {
            client.shutdownNow();
            if (!client.awaitTermination(Duration.ofSeconds(2))) throw new AssertionError("Owned HTTP client did not terminate within 5s");
        }
    }
    private static Throwable append(Throwable primary, Throwable cleanup) {
        if (primary == null) return cleanup;
        primary.addSuppressed(cleanup); return primary;
    }
}
