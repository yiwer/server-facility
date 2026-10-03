package cn.code91.facility.web.upload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import com.sun.nio.file.ExtendedOpenOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import cn.code91.facility.result.Result;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.error.FacilityErrorType;
import org.springframework.mock.web.MockMultipartFile;
import static org.mockito.Mockito.*;

import static org.assertj.core.api.Assertions.*;

class UploadFailureContractTest {
    @TempDir Path root;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("runtimeFailures")
    void runtimeSourceFailurePropagatesAfterRemovingItsStage(RuntimeException failure) throws Exception {
        assertThatThrownBy(() -> SafeUpload.saveFile(
                UploadByteBudgetTest.streamFile(failsAfterPrefix(failure)), root.toString())).isSameAs(failure);
        try (var files = Files.list(root)) { assertThat(files).isEmpty(); }
    }

    static java.util.stream.Stream<RuntimeException> runtimeFailures() {
        return java.util.stream.Stream.of(new IllegalStateException("source bug"), new UnsupportedOperationException("source bug"));
    }

    @Test
    void sourceErrorPropagatesAfterRemovingItsStage() throws Exception {
        var failure = new AssertionError("source assertion");
        assertThatThrownBy(() -> SafeUpload.saveFile(
                UploadByteBudgetTest.streamFile(failsAfterPrefix(failure)), root.toString())).isSameAs(failure);
        try (var files = Files.list(root)) { assertThat(files).isEmpty(); }
    }

    @Test
    void detectionReadFailureKeepsPriorityWhenSourceCloseAlsoFails() {
        var first = new IOException("first read failed");
        var closing = new IOException("close also failed");
        var file = UploadByteBudgetTest.streamFile(new InputStream() {
            @Override public int read() throws IOException { throw first; }
            @Override public void close() throws IOException { throw closing; }
        });
        var result = SafeUpload.saveFile(file, root, 5, Set.of("application/octet-stream"));
        assertThat(result.getErr().getException()).isSameAs(first);
        assertThat(first.getSuppressed()).contains(closing);
    }

    @Test
    void interruptedBlockingSourceStopsAndReleasesItsPrivateStage() throws Exception {
        var entered = new CountDownLatch(1);
        var neverReleased = new CountDownLatch(1);
        var closed = new AtomicInteger();
        var reads = new AtomicInteger();
        var result = new AtomicReference<Result<Path, WrappedError>>();
        var interruptKept = new AtomicBoolean();
        var file = UploadByteBudgetTest.streamFile(new InputStream() {
            @Override public int read() throws IOException {
                reads.incrementAndGet(); entered.countDown();
                try { neverReleased.await(); return -1; }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new java.io.InterruptedIOException("cooperative source interrupted");
                }
            }
            @Override public void close() { closed.incrementAndGet(); }
        });
        Thread worker = Thread.ofPlatform().start(() -> {
            result.set(SafeUpload.saveFile(file, root.toString()));
            interruptKept.set(Thread.currentThread().isInterrupted());
        });
        try {
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(result).hasNullValue();
            try (var paths = Files.list(root)) {
                assertThat(paths).noneMatch(path -> path.getFileName().toString().endsWith(".upload"));
            }
            worker.interrupt(); worker.join(3000);
            assertThat(worker.isAlive()).isFalse();
            assertThat(result.get().getErr().getException()).isInstanceOf(java.io.InterruptedIOException.class);
            assertThat(interruptKept).isTrue();
            assertThat(closed).hasValue(1);
            assertThat(reads).hasValue(1);
            try (var files = Files.list(root)) { assertThat(files).isEmpty(); }
        } finally { neverReleased.countDown(); worker.interrupt(); worker.join(3000); }
    }

    @Test
    void aRealCleanupDenialIsSuppressedWithoutReplacingTheReadFailure() throws Exception {
        var first = new IOException("read failed before cleanup");
        var locked = new AtomicReference<SeekableByteChannel>();
        var stage = new AtomicReference<Path>();
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        Set<PosixFilePermission> permissions = windows ? null : Files.getPosixFilePermissions(root);
        var file = UploadByteBudgetTest.streamFile(new InputStream() {
            private boolean prefix;
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                if (length == 0) return 0;
                int value = read();
                if (value == -1) return -1;
                bytes[offset] = (byte) value;
                return 1;
            }
            @Override public int read() throws IOException {
                if (!prefix) { prefix = true; return 'a'; }
                try (var paths = Files.list(root)) { stage.set(paths.findFirst().orElseThrow()); }
                if (windows) locked.set(Files.newByteChannel(stage.get(), Set.of(StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)));
                else Files.setPosixFilePermissions(root, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
                throw first;
            }
        });
        try {
            var result = SafeUpload.saveFile(file, root.toString());
            assertThat(result.getErr().getException()).isSameAs(first);
            assertThat(first.getSuppressed()).isNotEmpty().allMatch(e -> e instanceof IOException);
            assertThat(stage.get()).exists();
        } finally {
            if (locked.get() != null) locked.get().close();
            if (permissions != null) Files.setPosixFilePermissions(root, permissions);
            if (stage.get() != null) Files.deleteIfExists(stage.get());
        }
        try (var files = Files.list(root)) { assertThat(files).isEmpty(); }
    }

    @Test
    void partialWriteFailureDoesNotPublishOrLeaveItsStage() throws Exception {
        var failure = new IOException("injected storage write failure");
        var file = new MockMultipartFile("file", "a.txt", "text/plain", new byte[16384]);
        try (var filesystem = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            filesystem.when(() -> Files.newOutputStream(any(Path.class))).thenAnswer(call -> {
                Path path = call.getArgument(0);
                return new java.io.FilterOutputStream(new java.io.FileOutputStream(path.toFile())) {
                    private boolean first = true;
                    @Override public void write(byte[] b, int off, int len) throws IOException {
                        if (!first) throw failure;
                        first = false; out.write(b, off, len);
                    }
                };
            });
            var result = SafeUpload.saveFile(file, root.toString());
            assertThat(result.getErr().getErrorType()).isEqualTo(FacilityErrorType.FILE_WRITE_ERROR);
            assertThat(result.getErr().getException()).isSameAs(failure);
        }
        try (var files = Files.list(root)) { assertThat(files).isEmpty(); }
    }

    @Test
    void outputCloseFailureRemainsAWriteFailureAndPreventsPublication() throws Exception {
        var failure = new IOException("output close failure");
        var file = new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{1});
        try (var filesystem = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            filesystem.when(() -> Files.newOutputStream(any(Path.class))).thenAnswer(call -> {
                Path path = call.getArgument(0);
                return new java.io.FileOutputStream(path.toFile()) {
                    @Override public void close() throws IOException { super.close(); throw failure; }
                };
            });
            var result = SafeUpload.saveFile(file, root.toString());
            assertThat(result.getErr().getErrorType()).isEqualTo(FacilityErrorType.FILE_WRITE_ERROR);
            assertThat(result.getErr().getException()).isSameAs(failure);
        }
        try (var files = Files.list(root)) { assertThat(files).isEmpty(); }
    }

    private static InputStream failsAfterPrefix(Throwable failure) {
        return new InputStream() {
            private boolean read;
            @Override public int read(byte[] b, int off, int len) {
                if (!read) { read = true; b[off] = 'a'; return 1; }
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw (Error) failure;
            }
            @Override public int read() { throw new AssertionError("bulk reader expected"); }
        };
    }
}
