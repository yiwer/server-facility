import java.net.URI;
import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** External JDK-only client. The application is launched exclusively from its independently built executable jar. */
class WorkflowConsumer {
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
            Path staging=Files.createDirectory(evidence.resolve("staging"));
            Files.writeString(staging.resolve("unrelated.txt"),"untouched");
            AtomicInteger admissions=new AtomicInteger();AtomicReference<String> trace=new AtomicReference<>(),auth=new AtomicReference<>();
            HttpServer upstream=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            var upstreamWorkers=Executors.newVirtualThreadPerTaskExecutor();upstream.setExecutor(upstreamWorkers);
            upstream.createContext("/inventory/",exchange->{
                admissions.incrementAndGet();trace.set(exchange.getRequestHeaders().getFirst("traceparent"));auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
                String sku=exchange.getRequestURI().getPath().substring("/inventory/".length());
                byte[] bytes=("{\"sku\":\""+sku+"\",\"available\":23}"+(sku.equals("LARGE")?" ".repeat(8192):"")).getBytes(StandardCharsets.UTF_8);
                try(exchange){exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,0);exchange.getResponseBody().write(bytes);}
            });upstream.start();
            try {
            for (boolean virtual : new boolean[]{false, true}) {
                Path log = evidence.resolve("packaged-" + virtual + ".log");
                Process running = launch(app, jar, log, "--spring.config.additional-location=" + state.resolve("local.properties").toUri().toASCIIString()
                                + "," + databaseState.resolve("database.properties").toUri().toASCIIString(),
                        "--spring.threads.virtual.enabled=" + virtual,"--management.tracing.sampling.probability=1",
                        "--bench.upstream.base-url=http://127.0.0.1:"+upstream.getAddress().getPort(),
                        "--bench.upstream.credential=workflow-upstream-only","--bench.staging-directory="+staging);
                try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
                    String base = awaitServer(running, log);
                    check(get(client,base,"/health",null).statusCode()==200,"health");
                    check(get(client,base,"/api/bench/hello",null).statusCode()==401,"anonymous");
                    check(get(client,base,"/api/bench/hello",denied).statusCode()==403,"scope");
                    check(get(client,base,"/api/bench/hello",token).body().equals("{\"message\":\"ready\"}"),"hello literal");
                    var quote=send(client,base,"POST","/api/bench/quotes",token,"{\"sku\":\"SKU-42\",\"quantity\":3}");
                    check(quote.statusCode()==201 && quote.body().equals("{\"sku\":\"SKU-42\",\"quantity\":3,\"unitPriceCents\":125,\"totalCents\":375}"),"quote literal");
                    check(send(client,base,"POST","/api/bench/quotes",token,"{\"sku\":\"SKU-42\",\"quantity\":101}").statusCode()==400,"quote bounds");
                    var conflict=get(client,base,"/api/bench/failure?kind=conflict",token);
                    check(conflict.statusCode()==409 && conflict.body().contains("inventory_unavailable"),"conflict");
                    var internal=get(client,base,"/api/bench/failure?kind=internal",token);
                    check(internal.statusCode()==500 && internal.body().contains("internal_error") && !internal.body().contains("BENCH_PRIVATE_FAILURE"),"safe internal");
                    int before=admissions.get();
                    check(get(client,base,"/api/bench/stock?sku=A%20B",token).statusCode()==400 && admissions.get()==before,"invalid SKU admission");
                    var request=HttpRequest.newBuilder(URI.create(base+"/api/bench/stock?sku=OK")).timeout(Duration.ofSeconds(5))
                        .header("Authorization","Bearer "+token).header("traceparent","00-0123456789abcdef0123456789abcdef-1234567890abcdef-01").build();
                    var stock=client.send(request,HttpResponse.BodyHandlers.ofString());
                    check(stock.statusCode()==200 && stock.body().equals("{\"sku\":\"OK\",\"available\":23}"),"stock literal");
                    check("Bearer workflow-upstream-only".equals(auth.get()),"credential separation");
                    check(trace.get()!=null && trace.get().matches("00-0123456789abcdef0123456789abcdef-[0-9a-f]{16}-01") && !trace.get().contains("-1234567890abcdef-"),"outbound trace");
                    check(get(client,base,"/api/bench/stock?sku=LARGE",token).statusCode()==502,"body budget");
                    check(get(client,base,"/api/bench/stock?sku=OK",token).statusCode()==200 && admissions.get()==before+3,"reuse/no retry");
                    String csv="name,quantity\nfirst,2\nsecond,3\n";
                    var imported=upload(client,base,token,csv,false);
                    check(imported.statusCode()==200 && imported.body().equals("{\"rows\":2,\"totalQuantity\":5,\"items\":[{\"name\":\"first\",\"quantity\":2},{\"name\":\"second\",\"quantity\":3}]}"),"CSV literal");
                    check(upload(client,base,token,csv,true).statusCode()==400,"mixed named parts");
                    check(upload(client,base,token,"x".repeat(4097),false).statusCode()==413,"upload budget");
                    check(upload(client,base,token,"name,quantity\nbad,0\n",false).statusCode()==400,"CSV business bound");
                    check(upload(client,base,token,csv,false).statusCode()==200,"import reuse");
                    try(var paths=Files.list(staging)){check(paths.map(p->p.getFileName().toString()).toList().equals(List.of("unrelated.txt")),"staging cleanup");}
                    check(Files.readString(staging.resolve("unrelated.txt")).equals("untouched"),"unrelated bytes");
                    Files.writeString(evidence.resolve("packaged-"+virtual+"-result.txt"),"WORKFLOW_PACKAGED_PASS virtual="+virtual+" executableSha256="+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)))+"\n");
                } finally { stop(running); }
            }
            } finally {upstream.stop(0);upstreamWorkers.shutdownNow();check(upstreamWorkers.awaitTermination(3,TimeUnit.SECONDS),"upstream workers remained");}
            System.out.println("PACKAGED_WORKFLOW_PASS platform/virtual; final executable jar; actual HTTP/trace/budgets/cleanup");
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
    private static HttpResponse<String> upload(HttpClient client,String base,String token,String csv,boolean mixed)throws Exception {
        String boundary="workflow-boundary";
        String body="--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"input.csv\"\r\nContent-Type: text/csv\r\n\r\n"+csv+"\r\n";
        if(mixed)body+="--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"\r\n\r\nextra\r\n";
        body+="--"+boundary+"--\r\n";
        return client.send(HttpRequest.newBuilder(URI.create(base+"/api/bench/import")).timeout(Duration.ofSeconds(5))
                .header("Authorization","Bearer "+token).header("Content-Type","multipart/form-data; boundary="+boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
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
        if (process.isAlive()) process.destroy();
        if (!process.waitFor(10, TimeUnit.SECONDS)) { process.destroyForcibly(); check(process.waitFor(10, TimeUnit.SECONDS), "child process did not stop"); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
