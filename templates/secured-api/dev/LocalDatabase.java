import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Loopback-only trust-authenticated development database. Never use this recipe for production. */
class LocalDatabase {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Supply an owned local state directory (ASCII path)");
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        if (!StandardCharsets.US_ASCII.newEncoder().canEncode(root.toString())) throw new IllegalArgumentException("Native PostgreSQL state directory must use an ASCII path");
        String configured = System.getenv("PG_BIN");
        if (configured == null || configured.isBlank()) throw new IllegalStateException("PG_BIN must point to PostgreSQL 18.6 tools");
        Path tools = Path.of(configured).toAbsolutePath();
        if (Files.exists(root)) {
            if (!Files.isRegularFile(root.resolve("owned-local-database"))) throw new IllegalArgumentException("Refusing an existing directory without the local database marker");
        } else { Files.createDirectory(root); Files.writeString(root.resolve("owned-local-database"), "secured-api local database v1\n"); }
        Path data = root.resolve("data");
        if (Files.exists(data.resolve("postmaster.pid"))) throw new IllegalStateException("Database is already running or needs explicit recovery");
        run(tools, root, "version.log", "postgres", "--version");
        if (!Files.readString(root.resolve("version.log")).contains("18.6")) throw new IllegalStateException("Expected PostgreSQL 18.6");
        if (!Files.exists(data)) run(tools, root, "initdb.log", "initdb", "-D", data.toString(), "-U", "postgres", "--auth=trust", "--encoding=UTF8", "--locale=C");
        int port; try (var socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) { port = socket.getLocalPort(); }
        Path properties = root.resolve("database.properties"); Files.deleteIfExists(properties);
        var database = new OwnedDatabase(tools, root, data, properties);
        Thread emergency = new Thread(() -> {
            try { database.close(); } catch (Exception failure) { System.err.println("Local database emergency cleanup failed: " + failure); }
        }, "local-postgres-shutdown");
        Runtime.getRuntime().addShutdownHook(emergency);
        Throwable primary = null;
        try {
            database.start(port);
            System.out.println("LOCAL_DATABASE_READY " + properties.toUri().toASCIIString());
            // Enter stops this foreground owner; EOF also stops it in the verification consumer.
            System.in.read();
        } catch (Exception | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            try {
                database.close();
            } catch (Exception | Error cleanup) {
                if (primary != null) primary.addSuppressed(cleanup);
                else throw cleanup;
            } finally {
                Runtime.getRuntime().removeShutdownHook(emergency);
            }
        }
    }
    /** pg_ctl owns native startup/readiness and the Windows restricted-token transition. */
    static final class OwnedDatabase implements AutoCloseable {
        private final Path tools, root, data, properties;
        private boolean closed;
        OwnedDatabase(Path tools, Path root, Path data, Path properties) {
            this.tools = tools; this.root = root; this.data = data; this.properties = properties;
        }
        synchronized void start(int port) throws Exception {
            if (closed) throw new IllegalStateException("Local database owner is already closed");
            run(tools, root, "start.log", "pg_ctl", "-D", data.toString(), "-l", root.resolve("postgres.log").toString(),
                    "-o", "-h 127.0.0.1 -p " + port + " -N 32", "-w", "-t", "15", "start");
            Files.writeString(properties, "spring.datasource.url=jdbc:postgresql://127.0.0.1:" + port + "/postgres\nspring.datasource.username=postgres\nspring.datasource.password=\n");
        }
        public synchronized void close() throws Exception {
            if (closed) return;
            Files.deleteIfExists(properties);
            if (Files.exists(data.resolve("postmaster.pid"))) {
                // Match the native test fixture: Windows fsync of its many databases was measured at 31s.
                run(tools, root, "stop.log", "pg_ctl", "-D", data.toString(), "-m", "fast", "-w", "-t", "60", "stop");
            }
            closed = true;
        }
    }
    static List<String> command(Path tools, String tool, String... args) {
        var command = new ArrayList<String>(); command.add(tools.resolve(tool + (System.getProperty("os.name").startsWith("Windows") ? ".exe" : "")).toString()); command.addAll(List.of(args)); return command;
    }
    static void run(Path tools, Path root, String log, String tool, String... args) throws Exception {
        var process = new ProcessBuilder(command(tools, tool, args)).redirectErrorStream(true).redirectOutput(root.resolve(log).toFile()).start();
        int seconds = List.of(args).contains("stop") ? 70 : 30;
        try {
            if (!process.waitFor(seconds, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException(tool + " failed; " + root.resolve(log) + "\n" + tail(root.resolve(log))
                        + (tool.equals("pg_ctl") ? "\nPostgreSQL: " + tail(root.resolve("postgres.log")) : ""));
            }
        }
        finally { if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); } }
    }
    static String tail(Path log) throws Exception {
        if (!Files.isRegularFile(log)) return "(native log absent)";
        try (var input = Files.newByteChannel(log)) {
            input.position(Math.max(0, input.size() - 8192));
            var bytes = java.nio.ByteBuffer.allocate(8192);
            while (bytes.hasRemaining() && input.read(bytes) > 0) { }
            return new String(bytes.array(), 0, bytes.position(), StandardCharsets.UTF_8);
        }
    }
}
