import cn.code91.facility.io.PathIo;
import cn.code91.facility.io.Zipping;

import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;

/** Ordinary-jar consumer: independent ZIP reader, fixed seed and bounded process resources. */
public class IoConsumer {
    public static void main(String[] args) throws Exception {
        for (String type : List.of("org.springframework.context.ApplicationContext", "org.slf4j.Logger")) {
            try { Class.forName(type); throw new AssertionError("Unexpected dependency " + type); }
            catch (ClassNotFoundException expected) { }
        }
        Path root = Files.createDirectories(Path.of(args[0]));
        int threadsBefore = ManagementFactory.getThreadMXBean().getThreadCount();
        long started = System.nanoTime();
        try {
            properties(root);
            for (int mib : new int[]{32, 128}) largeArchive(root, mib);
            Path source = Files.write(root.resolve("source.bin"), new byte[]{1, 2});
            for (int i = 0; i < 20; i++) failure(root, source);
            long heapBefore = usedHeap();
            for (int i = 0; i < 200; i++) failure(root, source);
            long heapAfter = usedHeap();
            int threadsAfter = ManagementFactory.getThreadMXBean().getThreadCount();
            check(heapAfter < 40L * 1024 * 1024 && heapAfter <= heapBefore + 8L * 1024 * 1024, "retained heap budget");
            check(threadsAfter <= threadsBefore, "no library background threads");
            check(PathIo.deleteDirectory(root).isOk(), "all file handles released for deletion");
            check(!Files.exists(root), "no residue after successful cleanup");
            System.out.println("IO_RESOURCES heap-before=" + heapBefore + " heap-after=" + heapAfter
                    + " threads-before=" + threadsBefore + " threads-after=" + threadsAfter
                    + " elapsed-ms=" + (System.nanoTime() - started) / 1_000_000);
            System.out.println("IO_CONSUMER_PASS seed=140037 archives=64 max-input-mib=128 failures=200 framework=absent");
        } finally {
            if (Files.exists(root)) {
                try (var files = Files.walk(root)) {
                    for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                }
            }
        }
    }

    static void properties(Path root) throws Exception {
        var random = new Random(140037);
        for (int round = 0; round < 64; round++) {
            Path tree = Files.createDirectory(root.resolve("tree"));
            Files.createDirectory(tree.resolve("empty"));
            var expected = new LinkedHashMap<String, byte[]>();
            int count = 1 + random.nextInt(8);
            long total = 0;
            for (int i = 0; i < count; i++) {
                byte[] bytes = new byte[random.nextInt(8193)];
                random.nextBytes(bytes);
                String name = "文档-" + i + ".bin";
                Files.write(tree.resolve(name), bytes);
                expected.put(name, bytes);
                total += bytes.length;
            }
            Path archive = root.resolve("archive.zip");
            var outcome = Zipping.zipDirectory(tree, archive);
            check(outcome.isOk(), "seed140037 round=" + round + " complete publication");
            check(PathIo.directorySize(tree).get() == total, "complete logical size");
            try (var reader = new ZipFile(archive.toFile())) {
                check(reader.size() == expected.size() + 1, "independent ZIP entry count");
                check(reader.getEntry("empty/").isDirectory(), "empty directory survives");
                for (var entry : expected.entrySet()) {
                    try (var input = reader.getInputStream(reader.getEntry(entry.getKey()))) {
                        check(Arrays.equals(input.readAllBytes(), entry.getValue()), "independent bytes " + entry.getKey());
                    }
                }
            }
            check(PathIo.deleteDirectory(tree).isOk(), "tree deletion");
            Files.delete(archive);
        }
    }

    static void largeArchive(Path root, int mib) throws Exception {
        Path source = root.resolve("large.bin");
        byte[] block = new byte[8192];
        new Random(140037).nextBytes(block);
        try (var output = Files.newOutputStream(source)) {
            for (int i = 0; i < mib * 128; i++) output.write(block);
        }
        byte[] expected;
        try (var input = Files.newInputStream(source)) { expected = digest(input); }
        Path output = root.resolve("large.zip");
        check(Zipping.zipFiles(List.of(source), output).isOk(), "large archive under 64 MiB heap");
        try (var reader = new ZipFile(output.toFile()); var input = reader.getInputStream(reader.getEntry("large.bin"))) {
            check(reader.getEntry("large.bin").getSize() == (long) mib * 1024 * 1024, "independent large size");
            check(Arrays.equals(digest(input), expected), "independent large content digest");
        }
        System.out.println("IO_LARGE input-mib=" + mib + " archive-bytes=" + Files.size(output) + " live-heap=" + usedHeap());
        Files.delete(source);
        Files.delete(output);
    }

    static byte[] digest(InputStream input) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        for (int n; (n = input.read(buffer)) != -1;) digest.update(buffer, 0, n);
        return digest.digest();
    }

    static void failure(Path root, Path source) throws Exception {
        var result = Zipping.zipFiles(List.of(source), root.resolve("rejected.zip"), new Zipping.Limits(1, 1, 4096, 1));
        check(result.isErr(), "over-budget archive rejected");
        try (var children = Files.list(root)) { check(children.toList().equals(List.of(source)), "failure releases stage and streams"); }
    }

    static long usedHeap() {
        System.gc();
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
