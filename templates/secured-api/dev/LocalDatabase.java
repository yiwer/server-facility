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
        Process postgres = new ProcessBuilder(command(tools, "postgres", "-D", data.toString(), "-h", "127.0.0.1", "-p", Integer.toString(port), "-N", "32"))
                .redirectErrorStream(true).redirectOutput(root.resolve("postgres.log").toFile()).start();
        Thread emergency = new Thread(() -> { if (postgres.isAlive()) postgres.destroy(); }, "local-postgres-shutdown");
        Runtime.getRuntime().addShutdownHook(emergency);
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15); boolean ready = false;
            while (postgres.isAlive() && System.nanoTime() < deadline) {
                // The pinned minimal distribution has initdb/pg_ctl/postgres, not pg_isready.
                // initdb --locale=C fixes this server-owned readiness message, independent of the caller locale.
                if (Files.readString(root.resolve("postgres.log")).contains("database system is ready to accept connections")) { ready = true; break; }
                Thread.sleep(50);
            }
            if (!ready) throw new IllegalStateException("Native database did not become ready; " + root.resolve("postgres.log"));
            Files.writeString(properties, "spring.datasource.url=jdbc:postgresql://127.0.0.1:" + port + "/postgres\nspring.datasource.username=postgres\nspring.datasource.password=\n");
            System.out.println("LOCAL_DATABASE_READY " + properties.toUri().toASCIIString());
            // Enter stops this foreground owner; EOF also stops it in the verification consumer.
            System.in.read();
        } finally {
            Files.deleteIfExists(properties);
            try {
                if (postgres.isAlive()) run(tools, root, "stop.log", "pg_ctl", "-D", data.toString(), "-m", "fast", "-w", "-t", "15", "stop");
                if (!postgres.waitFor(5, TimeUnit.SECONDS)) throw new IllegalStateException("Native database did not stop");
            } finally {
                if (postgres.isAlive()) { postgres.destroyForcibly(); postgres.waitFor(5, TimeUnit.SECONDS); }
                Runtime.getRuntime().removeShutdownHook(emergency);
            }
        }
    }
    static List<String> command(Path tools, String tool, String... args) {
        var command = new ArrayList<String>(); command.add(tools.resolve(tool + (System.getProperty("os.name").startsWith("Windows") ? ".exe" : "")).toString()); command.addAll(List.of(args)); return command;
    }
    static void run(Path tools, Path root, String log, String tool, String... args) throws Exception {
        var process = new ProcessBuilder(command(tools, tool, args)).redirectErrorStream(true).redirectOutput(root.resolve(log).toFile()).start();
        try { if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) throw new IllegalStateException(tool + " failed; " + root.resolve(log)); }
        finally { if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); } }
    }
}
