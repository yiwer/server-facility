import workflow.fixture.CooperativeSource;
import cn.code91.facility.web.upload.SafeUpload;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Qualifies the fixture at the ordinary upload API; the application join is verified separately. */
class UploadInterruptionProbe {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Files.createDirectory(root);
        byte[] sentinel = "unrelated-owned-by-another-request\n".getBytes(StandardCharsets.UTF_8);
        Path unrelated = root.resolve("UNRELATED.ticket31");
        Files.write(unrelated, sentinel, StandardOpenOption.CREATE_NEW);
        byte[] prefix = "name,quantity\npartial,".getBytes(StandardCharsets.UTF_8);
        for (boolean virtual : new boolean[]{false, true}) {
            Path request = Files.createDirectory(root.resolve("request-" + virtual));
            var source = new CooperativeSource(prefix);
            MultipartFile file = new MultipartFile() {
                public String getName() { return "file"; }
                public String getOriginalFilename() { return "import.csv"; }
                public String getContentType() { return "text/csv"; }
                public boolean isEmpty() { return false; }
                public long getSize() { return prefix.length + 1; }
                public byte[] getBytes() { throw new AssertionError("Fixture must be consumed as a stream"); }
                public InputStream getInputStream() { return source; }
                public void transferTo(File destination) { throw new AssertionError("Fixture must use the upload API"); }
            };
            var outcome = new AtomicReference<String>();
            var failure = new AtomicReference<Throwable>();
            Runnable action = () -> {
                try {
                    var result = SafeUpload.saveFile(file, request, 4096, null);
                    check(result.isErr() && result.getErr().getException() instanceof InterruptedIOException,
                            "interrupted source was not reported as interrupted input failure");
                    outcome.set("interrupted:" + Thread.currentThread().isInterrupted());
                } catch (Throwable unexpected) { failure.set(unexpected); }
            };
            Thread worker = virtual ? Thread.ofVirtual().start(action) : Thread.ofPlatform().start(action);
            try {
                check(source.awaitBlocked(3, TimeUnit.SECONDS), "upload did not reach the controlled read");
                check(worker.isAlive() && outcome.get() == null, "upload completed before interruption");
                try (var paths = Files.walk(request)) {
                    var staged = paths.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).toList();
                    check(staged.size() == 1, "expected one real owned partial upload");
                    check(Arrays.equals(Files.readAllBytes(staged.getFirst()), prefix), "partial upload did not contain the emitted prefix");
                }
                check(Arrays.equals(Files.readAllBytes(unrelated), sentinel), "upload changed unrelated bytes before interruption");
                worker.interrupt(); worker.join(3000);
                check(!worker.isAlive(), "upload worker did not leave within three seconds");
                check(failure.get() == null, "unexpected upload failure: " + failure.get());
                check("interrupted:true".equals(outcome.get()) && source.isClosed(), "upload lost interruption or kept its input open");
                try (var paths = Files.list(request)) { check(paths.findAny().isEmpty(), "upload left a partial file"); }
                check(Arrays.equals(Files.readAllBytes(unrelated), sentinel), "upload changed unrelated bytes after interruption");
                System.out.println("UPLOAD_SOURCE_PROBE_PASS virtual=" + virtual + " partialBytes=" + prefix.length + " mode=size-budget-only");
            } finally {
                source.close(); worker.interrupt(); worker.join(3000);
                check(!worker.isAlive(), "probe cleanup left a worker alive");
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
