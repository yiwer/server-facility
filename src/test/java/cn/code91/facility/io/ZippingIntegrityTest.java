package cn.code91.facility.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.nio.file.attribute.BasicFileAttributes;
import java.io.InputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.StandardOpenOption;
import java.nio.file.FileSystems;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.Map;
import com.sun.nio.file.ExtendedOpenOption;
import cn.code91.facility.result.Result;
import cn.code91.facility.error.WrappedError;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

class ZippingIntegrityTest {
    @TempDir Path root;

    @Test
    void concurrentPublishersHaveOneCompleteWinnerWithoutClobbering() throws Exception {
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var outcomes = new java.util.concurrent.CopyOnWriteArrayList<Result<Path, WrappedError>>();
        var workers = new java.util.ArrayList<Thread>();
        Path output = root.resolve("winner.zip");
        try {
            for (int value : new int[]{'A', 'B'}) {
                Path source = faultingSource(new InputStream() {
                    boolean done;
                    public int read() throws IOException {
                        if (done) return -1;
                        entered.countDown();
                        try {
                            if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("fixture barrier timeout");
                        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new java.io.InterruptedIOException(); }
                        done = true;
                        return value;
                    }
                });
                workers.add(Thread.ofPlatform().start(() -> outcomes.add(Zipping.zipFiles(List.of(source), output))));
            }
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(output).doesNotExist();
            release.countDown();
            for (Thread worker : workers) { worker.join(3000); assertThat(worker.isAlive()).isFalse(); }
            assertThat(outcomes).hasSize(2);
            assertThat(outcomes.stream().filter(Result::isOk)).hasSize(1);
            assertThat(outcomes.stream().filter(Result::isErr)).hasSize(1);
            try (var zip = new java.util.zip.ZipFile(output.toFile()); var body = zip.getInputStream(zip.getEntry("source.bin"))) {
                assertThat(zip.size()).isEqualTo(1);
                assertThat(new String(body.readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII)).isIn("A", "B");
            }
            try (var files = Files.list(root)) { assertThat(files).hasSize(2); }
        } finally {
            release.countDown();
            for (Thread worker : workers) { worker.interrupt(); worker.join(3000); }
        }
    }

    @Test
    void zeroLengthReadsMakeBoundedProgressWithoutLosingThePayload() throws Exception {
        Path source = faultingSource(new InputStream() {
            boolean done;
            public int read(byte[] bytes, int offset, int length) { return done ? -1 : 0; }
            public int read() { done = true; return 'x'; }
        });
        Path output = root.resolve("output.zip");
        assertThat(Zipping.zipFiles(List.of(source), output, new Zipping.Limits(1, 1, 4096, 1)).isOk()).isTrue();
        try (var zip = new java.util.zip.ZipFile(output.toFile()); var body = zip.getInputStream(zip.getEntry("source.bin"))) {
            assertThat(body.readAllBytes()).containsExactly((byte) 'x');
        }
    }

    @Test
    void disappearingInputAfterMetadataValidationIsNotPublished() throws Exception {
        Path source = faultingSource(new InputStream() {
            public int read() throws IOException {
                Files.delete(root.resolve("source.bin"));
                throw new java.nio.file.NoSuchFileException("source disappeared after metadata validation");
            }
        });
        var result = Zipping.zipFiles(List.of(source), root.resolve("output.zip"));
        assertThat(result.getErr().getException()).isInstanceOf(java.nio.file.NoSuchFileException.class);
        try (var files = Files.list(root)) { assertThat(files).isEmpty(); }
    }

    @Test
    void cancellationAtTheCloseBoundaryIsObservedBeforePublication() throws Exception {
        Path source = Files.writeString(root.resolve("source.txt"), "payload");
        try (var boundary = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            boundary.when(() -> Files.newOutputStream(any(Path.class))).thenAnswer(call -> {
                Path output = call.getArgument(0);
                return new java.io.FileOutputStream(output.toFile()) {
                    public void close() throws IOException { super.close(); Thread.currentThread().interrupt(); }
                };
            });
            try {
                var result = Zipping.zipFiles(List.of(source), root.resolve("output.zip"));
                assertThat(result.isErr()).isTrue();
                assertThat(result.getErr().getException()).isInstanceOf(java.io.InterruptedIOException.class);
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally { Thread.interrupted(); }
        }
        try (var files = Files.list(root)) { assertThat(files).containsExactly(source); }
    }

    @Test
    void readFailureHasPriorityOverCloseFailureAndSourceErrorsStillPropagate() throws Exception {
        var original = new IOException("first read failed");
        var close = new IOException("source close failed");
        Path source = faultingSource(new InputStream() {
            public int read() throws IOException { throw original; }
            public void close() throws IOException { throw close; }
        });
        var result = Zipping.zipFiles(List.of(source), root.resolve("output.zip"));
        assertThat(result.getErr().getException()).isSameAs(original);
        assertThat(original.getSuppressed()).contains(close);
        var error = new AssertionError("source error");
        Path broken = faultingSource(new InputStream() { public int read() { throw error; } });
        assertThatThrownBy(() -> Zipping.zipFiles(List.of(broken), root.resolve("error.zip"))).isSameAs(error);
        try (var files = Files.list(root)) { assertThat(files.map(p -> p.getFileName().toString())).containsExactly("source.bin"); }
    }

    @Test
    void cooperativeCancellationClosesTheSourceAndDoesNotPublishTheArchive() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var closed = new AtomicBoolean();
        var interrupted = new AtomicBoolean();
        var result = new AtomicReference<Result<Path, WrappedError>>();
        Path source = faultingSource(new InputStream() {
            public int read() throws IOException {
                entered.countDown();
                try { release.await(); return -1; }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new java.io.InterruptedIOException("source cancelled"); }
            }
            public void close() { closed.set(true); }
        });
        Path output = root.resolve("cancelled.zip");
        Thread worker = Thread.ofPlatform().start(() -> {
            result.set(Zipping.zipFiles(List.of(source), output));
            interrupted.set(Thread.currentThread().isInterrupted());
        });
        try {
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(output).doesNotExist();
            worker.interrupt(); worker.join(3000);
            assertThat(worker.isAlive()).isFalse();
            assertThat(result.get().getErr().getException()).isInstanceOf(java.io.InterruptedIOException.class);
            assertThat(interrupted).isTrue();
            assertThat(closed).isTrue();
            try (var files = Files.list(root)) { assertThat(files.map(p -> p.getFileName().toString())).containsExactly("source.bin"); }
        } finally { release.countDown(); worker.interrupt(); worker.join(3000); }
    }

    @Test
    void aRealCleanupDenialKeepsTheOriginalReadFailureAndReportsTheResidue() throws Exception {
        var original = new IOException("first read failure");
        var stage = new AtomicReference<Path>();
        var handle = new AtomicReference<SeekableByteChannel>();
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        Set<PosixFilePermission> permissions = windows ? null : Files.getPosixFilePermissions(root);
        Path source = faultingSource(new InputStream() {
            public int read() throws IOException {
                try (var files = Files.list(root)) {
                    stage.set(files.filter(p -> p.getFileName().toString().startsWith(".facility-zip-")).findFirst().orElseThrow());
                }
                if (windows) handle.set(Files.newByteChannel(stage.get(), Set.of(StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)));
                else Files.setPosixFilePermissions(root, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
                throw original;
            }
        });
        try {
            var result = Zipping.zipFiles(List.of(source), root.resolve("output.zip"));
            assertThat(result.getErr().getException()).isSameAs(original);
            assertThat(original.getSuppressed()).isNotEmpty().allMatch(e -> e instanceof IOException);
            assertThat(root.resolve("output.zip")).doesNotExist();
            assertThat(stage.get()).exists();
        } finally {
            if (handle.get() != null) handle.get().close();
            if (permissions != null) Files.setPosixFilePermissions(root, permissions);
            if (stage.get() != null) Files.deleteIfExists(stage.get());
        }
    }

    @Test
    void unsupportedPublicationFailsWithoutFallingBackToAVisibleCopy() throws Exception {
        Path source = Files.writeString(root.resolve("source.txt"), "payload");
        try (var filesystem = FileSystems.newFileSystem(root.resolve("storage.zip"), Map.of("create", "true"))) {
            Path output = filesystem.getPath("/output.zip");
            var result = Zipping.zipFiles(List.of(source), output);
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr().getException().getCause()).isInstanceOf(UnsupportedOperationException.class);
            try (var files = Files.list(filesystem.getPath("/"))) { assertThat(files).isEmpty(); }
        }
    }

    @Test
    void outputWriteOrCloseFailureNeverPublishesAnArchive() throws Exception {
        Path source = Files.writeString(root.resolve("source.txt"), "payload");
        for (boolean failOnClose : new boolean[]{false, true}) {
            var original = new IOException(failOnClose ? "close failure" : "write failure");
            try (var boundary = mockStatic(Files.class, CALLS_REAL_METHODS)) {
                boundary.when(() -> Files.newOutputStream(any(Path.class))).thenAnswer(call -> {
                    Path output = call.getArgument(0);
                    return new java.io.FileOutputStream(output.toFile()) {
                        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                            if (!failOnClose) throw original;
                            super.write(bytes, offset, length);
                        }
                        @Override public void close() throws IOException {
                            super.close();
                            if (failOnClose) throw original;
                        }
                    };
                });
                var result = Zipping.zipFiles(List.of(source), root.resolve("output.zip"));
                assertThat(result.getErr().getException()).isSameAs(original);
            }
            try (var files = Files.list(root)) { assertThat(files).containsExactly(source); }
        }
    }

    @Test
    void missingOutputIsAnExplicitInputFailure() throws Exception {
        Path source = Files.writeString(root.resolve("source.txt"), "payload");
        assertThat(Zipping.zipFiles(List.of(source), null).isErr()).isTrue();
        assertThat(Zipping.zipDirectory(root, null).isErr()).isTrue();
    }

    @Test
    void programmingFailureInTheSourcePropagatesAfterOwnedResourcesAreClosed() throws Exception {
        for (RuntimeException failure : List.of(new IllegalStateException("source bug"), new UnsupportedOperationException("source bug"))) {
            var closed = new AtomicBoolean();
            Path input = faultingSource(new InputStream() {
                public int read() { throw failure; }
                public void close() { closed.set(true); }
            });
            assertThatThrownBy(() -> Zipping.zipFiles(List.of(input), root.resolve("output.zip"))).isSameAs(failure);
            assertThat(closed).isTrue();
            try (var children = Files.list(root)) { assertThat(children.map(p -> p.getFileName().toString())).containsExactly("source.bin"); }
        }
    }

    @Test
    void duplicateBasenamesFailWithoutPublishingAnIncompleteArchive() throws Exception {
        Path left = Files.createDirectories(root.resolve("left")).resolve("same.txt");
        Path right = Files.createDirectories(root.resolve("right")).resolve("same.txt");
        Files.writeString(left, "left payload");
        Files.writeString(right, "right payload");
        Path output = root.resolve("complete.zip");
        var result = Zipping.zipFiles(List.of(left, right), output);
        assertThat(result.isErr()).as("duplicate name must not claim complete success").isTrue();
        assertThat(output).doesNotExist();
    }

    @Test
    void aMissingInputFailsTheWholeOperationAndLeavesNoArchive() throws Exception {
        Path present = Files.writeString(root.resolve("present.txt"), "included");
        Path missing = root.resolve("missing.txt");
        Path output = root.resolve("complete.zip");
        assertThat(Zipping.zipFiles(List.of(present, missing), output).isErr()).isTrue();
        assertThat(output).doesNotExist();
        try (var children = Files.list(root)) {
            assertThat(children.map(p -> p.getFileName().toString())).containsExactly("present.txt");
        }
    }

    @Test
    void midReadFailureNeverPublishesTheTargetAndKeepsTheOriginalFailure() throws Exception {
        Path output = root.resolve("complete.zip");
        var original = new IOException("owned test read failed");
        var visibleDuringRead = new AtomicBoolean();
        var reachedRead = new AtomicBoolean();
        var input = faultingSource(new InputStream() {
            @Override public int read() throws IOException {
                reachedRead.set(true);
                visibleDuringRead.set(Files.exists(output));
                throw original;
            }
        });
        var result = Zipping.zipFiles(List.of(input), output);
        assertThat(reachedRead).isTrue();
        assertThat(result.isErr()).isTrue();
        assertThat(result.getErr().getException()).isSameAs(original);
        assertThat(visibleDuringRead).isFalse();
        assertThat(output).doesNotExist();
        try (var children = Files.list(root)) {
            assertThat(children.map(p -> p.getFileName().toString())).containsExactly("source.bin");
        }
    }

    @Test
    void outputInsideTheInputTreeIsRejectedBeforeCreatingAnyStage() throws Exception {
        Path source = Files.createDirectory(root.resolve("source"));
        assertThat(Zipping.zipDirectory(source, source.resolve("archive.zip")).isErr()).isTrue();
        try (var children = Files.list(source)) { assertThat(children).isEmpty(); }
    }

    @Test
    void anExistingTargetIsNeverOverwritten() throws Exception {
        Path source = Files.writeString(root.resolve("source.txt"), "source");
        Path output = Files.writeString(root.resolve("existing.zip"), "untouched sentinel");
        assertThat(Zipping.zipFiles(List.of(source), output).isErr()).isTrue();
        assertThat(Files.readString(output)).isEqualTo("untouched sentinel");
        try (var children = Files.list(root)) { assertThat(children).hasSize(2); }
    }

    @Test
    void directoryLinksCannotRedirectSourcesOrOutputsOutsideTheNamedTree() throws Exception {
        Path outside = Files.createDirectory(root.resolve("outside"));
        Path source = Files.writeString(outside.resolve("source.txt"), "outside marker");
        Path linked = root.resolve("linked");
        if (System.getProperty("os.name").startsWith("Windows")) {
            var builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                    "New-Item -ItemType Junction -Path $env:ZIP_LINK -Target $env:ZIP_TARGET | Out-Null");
            builder.environment().put("ZIP_LINK", linked.toString());
            builder.environment().put("ZIP_TARGET", outside.toString());
            var process = builder.redirectErrorStream(true).start();
            try {
                assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
                assertThat(process.exitValue()).as(new String(process.getInputStream().readAllBytes())).isZero();
            } finally { if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); } }
        } else { Files.createSymbolicLink(linked, outside); }
        try {
            assertThat(Zipping.zipFiles(List.of(linked.resolve("source.txt")), root.resolve("out.zip")).isErr()).isTrue();
            assertThat(Zipping.zipFiles(List.of(source), linked.resolve("out.zip")).isErr()).isTrue();
            assertThat(Zipping.zipDirectory(outside, linked.resolve("new/out.zip")).isErr()).isTrue();
            assertThat(PathIo.directorySize(linked).isErr()).as("linked root cannot claim complete zero").isTrue();
            assertThat(PathIo.deleteDirectory(linked).isErr()).isTrue();
            assertThat(PathIo.directorySize(linked.resolve("source.txt")).isErr()).isTrue();
            assertThat(PathIo.deleteDirectory(linked.resolve("source.txt")).isErr()).isTrue();
            assertThat(root.resolve("out.zip")).doesNotExist();
            try (var children = Files.list(outside)) { assertThat(children).containsExactly(source); }
        } finally { Files.delete(linked); }
    }

    private Path faultingSource(InputStream stream) throws Exception {
        Path real = Files.writeString(root.resolve("source.bin"), "real filesystem metadata");
        Path path = mock(Path.class, delegatesTo(real));
        var filesystem = mock(FileSystem.class);
        var provider = mock(FileSystemProvider.class, CALLS_REAL_METHODS);
        doReturn(filesystem).when(path).getFileSystem();
        doReturn(path).when(path).toAbsolutePath();
        doReturn(path).when(path).normalize();
        doReturn(path).when(path).toRealPath(any(LinkOption[].class));
        when(filesystem.provider()).thenReturn(provider);
        when(provider.readAttributes(eq(path), eq(BasicFileAttributes.class), any(LinkOption[].class)))
                .thenReturn(Files.readAttributes(real, BasicFileAttributes.class));
        doReturn(stream).when(provider).newInputStream(eq(path), any(OpenOption[].class));
        return path;
    }
}
