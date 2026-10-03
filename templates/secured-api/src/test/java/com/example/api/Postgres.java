package com.example.api;

import java.net.ServerSocket;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** One owned native PostgreSQL cluster per test JVM. A missing prerequisite is a failure, never a skip. */
final class Postgres {
    private static volatile Postgres started;
    private static final class Holder { static final Postgres INSTANCE = new Postgres(); }
    private final Path tools, root, data;
    private final int port;
    private int sequence;
    private boolean closed;
    private Postgres() {
        try {
            String configured = System.getenv("PG_BIN");
            if (configured == null || configured.isBlank()) throw new IllegalStateException("PG_BIN must point to PostgreSQL 18.6 native tools; database tests cannot be skipped");
            tools = Path.of(configured).toAbsolutePath().normalize();
            root = Files.createTempDirectory("secured-api-postgres-").toRealPath(); data = root.resolve("data");
            try (var socket = new ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress())) { port = socket.getLocalPort(); }
            started = this;
            run("initdb", "-D", data.toString(), "-U", "postgres", "--auth=trust", "--encoding=UTF8", "--locale=C");
            run("pg_ctl", "-D", data.toString(), "-l", root.resolve("postgres.log").toString(), "-o", "-h 127.0.0.1 -p " + port + " -N 32", "-w", "-t", "15", "start");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { close(); } catch (Exception failure) { System.err.println("Emergency PostgreSQL cleanup failed: " + failure); }
            }, "owned-postgres-stop"));
            try (var connection = connect(url("postgres")); var statement = connection.createStatement(); var result = statement.executeQuery("show server_version")) {
                result.next(); if (!result.getString(1).startsWith("18.6")) throw new IllegalStateException("Expected PostgreSQL 18.6, got " + result.getString(1));
            }
            execute(url("postgres"), "create database app_shared");
        } catch (Exception failure) {
            try { closeIfStarted(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw new IllegalStateException("Native PostgreSQL fixture could not start", failure);
        }
    }
    static String sharedUrl() { return Holder.INSTANCE.url("app_shared"); }
    static String adminUrl() { return Holder.INSTANCE.url("postgres"); }
    static String freshUrl() throws SQLException {
        var cluster = Holder.INSTANCE; String name = "app_" + UUID.randomUUID().toString().replace("-", "");
        execute(cluster.url("postgres"), "create database " + name); return cluster.url(name);
    }
    private String url(String database) { return "jdbc:postgresql://127.0.0.1:" + port + "/" + database; }
    static Connection connect(String url) throws SQLException { return DriverManager.getConnection(url, "postgres", ""); }
    static void execute(String url, String sql) throws SQLException {
        try (var connection = connect(url); var statement = connection.createStatement()) { statement.execute(sql); }
    }
    private synchronized void run(String tool, String... arguments) throws Exception {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        var command = new ArrayList<String>(); command.add(tools.resolve(tool + (windows ? ".exe" : "")).toString()); command.addAll(List.of(arguments));
        Path log = root.resolve(++sequence + "-" + tool + ".log");
        var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        int seconds = List.of(arguments).contains("stop") ? 70 : 30;
        if (!process.waitFor(seconds, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException(tool + " exceeded " + seconds + "s; " + log); }
        if (process.exitValue() != 0) throw new IllegalStateException(tool + " failed; " + log + ": " + Files.readString(log));
    }
    static void closeIfStarted() throws Exception { if (started != null) started.close(); }
    private synchronized void close() throws Exception {
            if (closed) return;
            // The full suite checkpoints many isolated databases; actual Windows fsync took 31s.
            if (Files.exists(data.resolve("postmaster.pid"))) run("pg_ctl", "-D", data.toString(), "-m", "fast", "-w", "-t", "60", "stop");
            Path evidence = Files.createDirectories(Path.of("target", root.getFileName().toString()));
            try (var files = Files.list(root)) { for (Path log : files.filter(Files::isRegularFile).toList()) Files.copy(log, evidence.resolve(log.getFileName()), StandardCopyOption.REPLACE_EXISTING); }
            if (!root.getParent().equals(Path.of(System.getProperty("java.io.tmpdir")).toRealPath()) || !root.getFileName().toString().startsWith("secured-api-postgres-")) throw new IllegalStateException("Unexpected owned cluster path");
            try (var files = Files.walk(root)) { for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file); }
            closed = true;
    }
}
