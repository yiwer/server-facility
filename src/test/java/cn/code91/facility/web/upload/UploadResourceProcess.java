package cn.code91.facility.web.upload;

import cn.code91.facility.hash.Hashing;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;

/** Finite process workload: maximum one 256 MiB file, 96 MiB heap, 16 MiB direct memory. */
public final class UploadResourceProcess {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        if (args.length > 1 && args[1].startsWith("cold-interrupt-")) {
            var file = new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{65});
            Thread.currentThread().interrupt();
            try {
                if (args[1].endsWith("bytes")) {
                    try {
                        cn.code91.facility.mime.MimeTyping.detect(new byte[]{65});
                        throw new AssertionError("cold cancellation became success");
                    } catch (java.io.UncheckedIOException expected) {
                        if (!(expected.getCause() instanceof java.io.InterruptedIOException)) throw expected;
                    }
                } else {
                    var result = SafeUpload.detectMime(file);
                    if (!result.isErr() || !(result.getErr().getException() instanceof java.io.InterruptedIOException))
                        throw new AssertionError("lost cold cancellation");
                }
                if (!Thread.currentThread().isInterrupted()) throw new AssertionError("lost interrupt flag");
            } finally { Thread.interrupted(); }
            if (!SafeUpload.detectMime(file).get().equals("text/plain")) throw new AssertionError("poisoned MIME class");
            System.out.println("COLD_INTERRUPT_OK nextCallWorks=true");
            return;
        }
        if (args.length > 1) {
            var file = new MockMultipartFile("file", "a.txt", "text/plain", "abc".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Files.delete(SafeUpload.saveFile(file, root.toString()).get());
            try {
                SafeUpload.saveFile(file, root, 5, Set.of("text/plain"));
                throw new AssertionError("missing detector silently accepted a type policy");
            } catch (NoClassDefFoundError expected) {
                System.out.println("MISSING_TIKA_CAUSE " + expected.getMessage());
            }
            Files.delete(SafeUpload.saveFile(file, root.toString()).get());
            try (var files = Files.list(root)) { if (files.findAny().isPresent()) throw new AssertionError("staging leak"); }
            System.out.println("MISSING_TIKA_REJECTED stageFiles=0 rawSaveWorks=true");
            return;
        }
        upload(root, 8L * 1024 * 1024, null);
        long baseline = retained();
        int threads = ManagementFactory.getThreadMXBean().getThreadCount();
        long maximum = baseline;
        // Independent expected vectors generated with CPython hashlib over ASCII 'a', 2026-10-04.
        String[] hashes = {"fae972222d455a2eaee1661ad9625502ec3bfc5ec38b87a6eec5afd5107331b5",
                "b4a0226ee3f9b159ac06a86332dca0d90a04adef7f88934aa2a75be2a011d504"};
        long[] sizes = {64L * 1024 * 1024, 256L * 1024 * 1024};
        for (int i = 0; i < sizes.length; i++) {
            upload(root, sizes[i], hashes[i]);
            long used = retained(); maximum = Math.max(maximum, used);
            if (used - baseline > 16L * 1024 * 1024) throw new AssertionError("retained heap growth " + (used - baseline));
            System.out.println("UPLOAD_RESOURCE_SAMPLE bytes=" + sizes[i] + " retained=" + used);
        }
        for (int i = 0; i < 100; i++) {
            int iteration = i;
            var file = new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{1}) {
                @Override public InputStream getInputStream() {
                    return new InputStream() {
                        private int reads;
                        @Override public int read() throws IOException {
                            if (++reads == 1) return 'a';
                            if ((iteration & 1) == 0) throw new IOException("finite source failure");
                            throw new IllegalStateException("finite source bug");
                        }
                    };
                }
            };
            try {
                if (SafeUpload.toTempFile(file).isOk()) throw new AssertionError("failure became success");
            } catch (IllegalStateException expected) {
                if ((i & 1) == 0) throw expected;
            }
        }
        try (var files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            if (files.findAny().isPresent()) throw new AssertionError("temporary leak");
        }
        int afterThreads = ManagementFactory.getThreadMXBean().getThreadCount();
        if (afterThreads > threads + 2) throw new AssertionError("thread growth " + threads + " -> " + afterThreads);
        long finalUsed = retained();
        if (finalUsed - baseline > 16L * 1024 * 1024) throw new AssertionError("failure retention growth");
        System.out.println("UPLOAD_RESOURCE_OK heapMax=" + Runtime.getRuntime().maxMemory() + " baseline=" + baseline
                + " largestRetained=" + maximum + " finalRetained=" + finalUsed + " threads=" + threads + "->" + afterThreads
                + " failures=100 temporaryFiles=0 growthLimit=16777216");
    }

    private static void upload(Path root, long size, String expectedHash) throws Exception {
        var file = new MockMultipartFile("file", "untrusted.pdf", "application/pdf", new byte[]{1}) {
            @Override public long getSize() { return -1; }
            @Override public InputStream getInputStream() {
                return new InputStream() {
                    private long remaining = size;
                    @Override public int read() { if (remaining == 0) return -1; remaining--; return 'a'; }
                    @Override public int read(byte[] b, int off, int len) {
                        if (remaining == 0) return -1;
                        int count = (int) Math.min(remaining, len);
                        Arrays.fill(b, off, off + count, (byte) 'a'); remaining -= count; return count;
                    }
                };
            }
        };
        var result = SafeUpload.saveFile(file, root, size, Set.of("text/plain"));
        if (result.isErr()) throw new AssertionError(result.getErr());
        try {
            if (Files.size(result.get()) != size) throw new AssertionError("truncated upload");
            if (expectedHash != null && !Hashing.sha256(result.get().toFile()).get().equals(expectedHash)) {
                throw new AssertionError("independent digest mismatch");
            }
        } finally { Files.delete(result.get()); }
        try (var files = Files.list(root)) { if (files.findAny().isPresent()) throw new AssertionError("staging leak"); }
    }

    private static long retained() {
        System.gc();
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }
}
