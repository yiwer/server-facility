import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

/** Pinned native development/test tools; no server starts here. Requires the OS tar program. */
class PreparePostgres {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Supply a NEW absolute tools directory (or file URI)");
        Path output = (args[0].startsWith("file:") ? Path.of(URI.create(args[0])) : Path.of(args[0])).toAbsolutePath().normalize();
        if (Files.exists(output)) throw new IllegalArgumentException("Tools directory must be new");
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        if (!(windows || System.getProperty("os.name").equals("Linux")) || !Set.of("amd64", "x86_64").contains(System.getProperty("os.arch")))
            throw new IllegalArgumentException("This pinned recipe supports Windows/Linux x86_64 only");
        String platform = windows ? "windows" : "linux";
        String expected = windows ? "a5586812fc713b536e4e0294a8de355a4aae2d9687203b1b48e53ba7a814373cef2ad73bc38c82ea347c7f33d6c7ed4597ba7adafeeed7e7f081e83c084dcd1e"
                : "5161552dfeb81330df7e37938f92579aac8caa004bc4a7264511627c4a167863a6cc64347aeb1934caf0191be3bed291cea8c21e26665efbacb0cf204838e41e";
        Files.createDirectories(output);
        Path jar = output.resolve("native-tools.jar"), archive = output.resolve("postgres.txz");
        String url = "https://repo.maven.apache.org/maven2/io/zonky/test/postgres/embedded-postgres-binaries-" + platform
                + "-amd64/18.6.0/embedded-postgres-binaries-" + platform + "-amd64-18.6.0.jar";
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            var pending = client.sendAsync(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(90)).build(), HttpResponse.BodyHandlers.ofFile(jar));
            try {
                var response = pending.get(95, TimeUnit.SECONDS);
                if (response.statusCode() != 200 || Files.size(jar) > 32L * 1024 * 1024) throw new IllegalStateException("Native tools download failed or exceeded budget");
            } finally { pending.cancel(true); }
        }
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(Files.readAllBytes(jar)));
        if (!actual.equals(expected)) throw new IllegalStateException("Native PostgreSQL SHA-512 mismatch");
        try (var zip = new ZipFile(jar.toFile())) {
            var entry = zip.getEntry("postgres-" + platform + "-x86_64.txz");
            if (entry == null) throw new IllegalStateException("Expected native archive missing");
            try (var input = zip.getInputStream(entry)) { Files.copy(input, archive); }
        }
        run(output, "extract.log", List.of("tar", "-xJf", archive.toString(), "-C", output.toString()));
        run(output, "version.log", List.of(output.resolve("bin/postgres" + (windows ? ".exe" : "")).toString(), "--version"));
        if (!Files.readString(output.resolve("version.log")).contains("18.6")) throw new IllegalStateException("Expected PostgreSQL 18.6");
        Files.writeString(output.resolve("provenance.txt"), "url=" + url + "\nsha512=" + actual + "\n" + Files.readString(output.resolve("version.log")));
        System.out.println("PG_BIN=" + output.resolve("bin"));
    }
    static void run(Path directory, String log, List<String> command) throws Exception {
        var process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).redirectOutput(directory.resolve(log).toFile()).start();
        try { if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) throw new IllegalStateException("Native tools step failed: " + directory.resolve(log)); }
        finally { if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); } }
    }
}
