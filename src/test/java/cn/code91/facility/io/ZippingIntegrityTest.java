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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

class ZippingIntegrityTest {
    @TempDir Path root;

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
