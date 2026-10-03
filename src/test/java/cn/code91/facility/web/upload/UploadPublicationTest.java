package cn.code91.facility.web.upload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class UploadPublicationTest {
    @TempDir Path root;

    @Test
    void displayNameDoesNotOverwriteExistingFileOrDetermineStorageKey() throws Exception {
        Path existing = Files.writeString(root.resolve("a.txt"), "original");
        var result = SafeUpload.saveFile(new MockMultipartFile("file", "a.txt", "text/plain", "new".getBytes()), root.toString());
        assertThat(result.isOk()).isTrue();
        assertThat(result.get()).isNotEqualTo(existing);
        assertThat(Files.readString(existing)).isEqualTo("original");
        assertThat(Files.readString(result.get())).isEqualTo("new");
    }

    @Test
    void concurrentSameDisplayNamesProduceTwoCompleteDistinctFiles() throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> saveAfterBarrier("one", barrier));
            var second = executor.submit(() -> saveAfterBarrier("two", barrier));
            Path a = first.get(5, TimeUnit.SECONDS);
            Path b = second.get(5, TimeUnit.SECONDS);
            assertThat(a).isNotEqualTo(b);
            assertThat(Files.readString(a)).isEqualTo("one");
            assertThat(Files.readString(b)).isEqualTo("two");
        }
    }

    @Test
    void sourceCloseFailureDoesNotPublishACompletedLookingFile() throws Exception {
        var failure = new IOException("source close failed");
        var source = new ByteArrayInputStream("abc".getBytes()) {
            @Override public void close() throws IOException { throw failure; }
        };
        var result = SafeUpload.saveFile(UploadByteBudgetTest.streamFile(source), root.toString());
        assertThat(result.isErr()).isTrue();
        assertThat(result.getErr().getException()).isSameAs(failure);
        try (var paths = Files.list(root)) { assertThat(paths).isEmpty(); }
    }

    private Path saveAfterBarrier(String body, CyclicBarrier barrier) {
        var file = new MockMultipartFile("file", "a.txt", "text/plain", body.getBytes()) {
            @Override public ByteArrayInputStream getInputStream() throws IOException {
                try { barrier.await(3, TimeUnit.SECONDS); } catch (Exception e) { throw new IOException(e); }
                return new ByteArrayInputStream(body.getBytes());
            }
        };
        return SafeUpload.saveFile(file, root.toString()).get();
    }
}
