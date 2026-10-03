import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Public development CLI and external consumer failure contracts; no application test classpath. */
class DatabaseProcessContract {
    static final String JAVA = Path.of(System.getProperty("java.home"), "bin",
            System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
    public static void main(String[] args) throws Exception {
        Path app = Path.of(URI.create(args[0])), evidence = Files.createDirectories(Path.of(URI.create(args[1])));
        if (args[2].equals("diagnostics")) {
            Path log = evidence.resolve("failure.log"), captured = evidence.resolve("failure-evidence");
            var builder = new ProcessBuilder(JAVA, "-Xmx96m", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", args[3], app.toUri().toASCIIString(), captured.toUri().toASCIIString())
                    .directory(app.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
            builder.environment().put("PG_BIN", evidence.resolve("missing-native-tools").toString());
            var child = builder.start();
            try {
                check(child.waitFor(40, TimeUnit.SECONDS), "failure consumer did not terminate");
                String failure = Files.readString(log);
                check(child.exitValue() != 0, "missing native prerequisite accepted");
                check(failure.startsWith("Exception in thread \"main\" java.lang.AssertionError: local fixture failed:"),
                        "cleanup replaced the startup failure: " + failure);
                check(Files.isRegularFile(captured.resolve("postgres/owned-local-database")), "failed startup diagnostics not archived");
            } finally { if (child.isAlive()) { child.destroyForcibly(); child.waitFor(10, TimeUnit.SECONDS); } }
            System.out.println("DATABASE_FAILURE_DIAGNOSTICS_PASS original failure preserved; cleanup suppressed; native state archived");
            return;
        }
        Path root = Files.createTempDirectory("facility-database-contract-").toRealPath(), state = root.resolve("cluster");
        try {
            for (int cycle = 0; cycle < 2; cycle++) {
                Path log = evidence.resolve("lifecycle-" + cycle + ".log");
                var child = start(app, state, log);
                try {
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
                    while (!Files.isRegularFile(state.resolve("database.properties")) && child.isAlive() && System.nanoTime() < deadline) Thread.sleep(50);
                    check(Files.isRegularFile(state.resolve("database.properties")), "CLI startup failed: " + Files.readString(log));
                    check(Files.isRegularFile(state.resolve("data/postmaster.pid")), "ready without owned database");
                    child.getOutputStream().close();
                    check(child.waitFor(85, TimeUnit.SECONDS) && child.exitValue() == 0, "CLI shutdown failed: " + Files.readString(log));
                    check(!Files.exists(state.resolve("database.properties")) && !Files.exists(state.resolve("data/postmaster.pid")), "CLI left readiness or running database");
                } finally { if (child.isAlive()) { child.destroy(); child.waitFor(85, TimeUnit.SECONDS); } }
            }
            // -C prints one configuration setting and exits; it never starts a server.
            // On hosted Windows this distinguishes the direct executable's admin rejection from pg_ctl's restricted token.
            Path directLog = evidence.resolve("direct-postgres-policy.log");
            boolean windows = System.getProperty("os.name").startsWith("Windows");
            Path postgres = Path.of(System.getenv("PG_BIN"), windows ? "postgres.exe" : "postgres");
            var direct = new ProcessBuilder(postgres.toString(), "-D", state.resolve("data").toString(), "-C", "port")
                    .redirectErrorStream(true).redirectOutput(directLog.toFile()).start();
            try {
                check(direct.waitFor(10, TimeUnit.SECONDS), "direct native configuration probe hung");
                String output = Files.readString(directLog);
                boolean adminRefusal = windows && direct.exitValue() != 0 && output.contains("administrative permissions");
                check(direct.exitValue() == 0 || adminRefusal, "unexpected direct executable failure: " + output);
                System.out.println("DIRECT_POSTGRES_POLICY " + (adminRefusal ? "administrative-refusal; pg_ctl lifecycle succeeded" : "non-admin configuration read succeeded"));
            } finally { if (direct.isAlive()) { direct.destroyForcibly(); direct.waitFor(5, TimeUnit.SECONDS); } }
            Files.writeString(state.resolve("data/postgresql.conf"), "\nticket28_invalid_native_setting = true\n", StandardOpenOption.APPEND);
            Path failed = evidence.resolve("invalid-configuration.log");
            var child = start(app, state, failed);
            try {
                check(child.waitFor(40, TimeUnit.SECONDS) && child.exitValue() != 0, "invalid native configuration accepted or hung");
                check(Files.readString(failed).contains("unrecognized configuration parameter \"ticket28_invalid_native_setting\""),
                        "native startup cause was lost: " + Files.readString(failed));
                check(!Files.exists(state.resolve("database.properties")) && !Files.exists(state.resolve("data/postmaster.pid")), "failed startup left readiness or running database");
            } finally { if (child.isAlive()) { child.destroy(); child.waitFor(85, TimeUnit.SECONDS); } }
            System.out.println("DATABASE_LIFECYCLE_PASS two native starts/stops; existing cluster restart; real PostgreSQL configuration failure preserved");
        } finally {
            if (Files.isDirectory(state)) try (var files = Files.list(state)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) Files.copy(file, evidence.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
            check(!Files.exists(state.resolve("data/postmaster.pid")), "refusing to delete active database");
            check(root.getParent().equals(Path.of(System.getProperty("java.io.tmpdir")).toRealPath())
                    && root.getFileName().toString().startsWith("facility-database-contract-"), "unexpected owned directory");
            try (var files = Files.walk(root)) { for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file); }
        }
    }
    static Process start(Path app, Path state, Path log) throws Exception {
        return new ProcessBuilder(JAVA, "-Xmx64m", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "dev/LocalDatabase.java", state.toString())
                .directory(app.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
    }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
