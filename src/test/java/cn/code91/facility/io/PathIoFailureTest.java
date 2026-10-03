package cn.code91.facility.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.spi.FileSystemProvider;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

class PathIoFailureTest {
    @TempDir Path root;

    @Test
    void extremeFilesystemSizesCannotWrapOrBypassTheLogicalByteBudget() throws Exception {
        Path real = Files.writeString(root.resolve("virtual-size"), "metadata fixture");
        var filesystem = mock(FileSystem.class);
        var provider = mock(FileSystemProvider.class, CALLS_REAL_METHODS);
        Path path = mock(Path.class, delegatesTo(real));
        doReturn(filesystem).when(path).getFileSystem();
        when(filesystem.provider()).thenReturn(provider);
        var attrs = mock(BasicFileAttributes.class);
        when(attrs.isRegularFile()).thenReturn(true);
        when(attrs.size()).thenReturn(Long.MAX_VALUE);
        when(provider.readAttributes(eq(path), eq(BasicFileAttributes.class), any(LinkOption[].class))).thenReturn(attrs);
        assertThat(PathIo.directorySize(path, new PathIo.Limits(1, Long.MAX_VALUE, 1)).get()).isEqualTo(Long.MAX_VALUE);
        assertThat(PathIo.directorySize(path, new PathIo.Limits(1, Long.MAX_VALUE - 1, 1)).isErr()).isTrue();
        when(attrs.size()).thenReturn(-1L);
        assertThat(PathIo.directorySize(path, new PathIo.Limits(1, Long.MAX_VALUE, 1)).isErr()).isTrue();
    }

    @Test
    void inaccessibleDirectoryIsAnObservedFailureRatherThanACompleteZeroOrMissingPath() throws Exception {
        var inaccessible = new IOException("owned fixture access denied");
        var filesystem = mock(FileSystem.class);
        var provider = mock(FileSystemProvider.class, CALLS_REAL_METHODS);
        Path path = mock(Path.class, delegatesTo(root));
        doReturn(filesystem).when(path).getFileSystem();
        when(filesystem.provider()).thenReturn(provider);
        when(provider.readAttributes(eq(path), eq(BasicFileAttributes.class), any(LinkOption[].class)))
                .thenThrow(inaccessible);
        var result = PathIo.directorySize(path);
        assertThat(result.isErr()).isTrue();
        assertThat(result.getErr().getException()).isSameAs(inaccessible);
    }

    @Test
    void traversalFailureIsRetainedAndTheParentIsNotDeletedAfterAfailedWalk() throws Exception {
        Path real = Files.createDirectory(root.resolve("directory"));
        var traversalFailure = new IOException("owned fixture directory iteration failed");
        var filesystem = mock(FileSystem.class);
        var provider = mock(FileSystemProvider.class, CALLS_REAL_METHODS);
        Path path = mock(Path.class, delegatesTo(real));
        doReturn(filesystem).when(path).getFileSystem();
        when(filesystem.provider()).thenReturn(provider);
        when(provider.readAttributes(eq(path), eq(BasicFileAttributes.class), any(LinkOption[].class)))
                .thenReturn(Files.readAttributes(real, BasicFileAttributes.class));
        when(provider.newDirectoryStream(eq(path), any())).thenReturn(new DirectoryStream<>() {
            @Override public Iterator<Path> iterator() {
                return new Iterator<>() {
                    public boolean hasNext() { throw new DirectoryIteratorException(traversalFailure); }
                    public Path next() { throw new AssertionError("no entry can be yielded"); }
                };
            }
            @Override public void close() { }
        });
        doAnswer(invocation -> { Files.delete(real); return null; }).when(provider).delete(path);
        var result = PathIo.deleteDirectory(path);
        assertThat(result.isErr()).isTrue();
        assertThat(result.getErr().getException()).isSameAs(traversalFailure);
        assertThat(real).isDirectory();
    }
}
