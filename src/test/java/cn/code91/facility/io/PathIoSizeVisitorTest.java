package cn.code91.facility.io;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PathIo.SizeVisitor - 累加大小 + 读失败跳过 (RV2-09)")
class PathIoSizeVisitorTest {

    @Test @DisplayName("visitFile 累加 size")
    void visitFileAccumulates() throws IOException {
        PathIo.SizeVisitor v = new PathIo.SizeVisitor();
        BasicFileAttributes attrs = Mockito.mock(BasicFileAttributes.class);
        Mockito.when(attrs.size()).thenReturn(100L);
        v.visitFile(Mockito.mock(Path.class), attrs);
        v.visitFile(Mockito.mock(Path.class), attrs);
        assertThat(v.total()).isEqualTo(200L);
    }

    @Test @DisplayName("visitFileFailed 返回 CONTINUE（跳过不可读文件，不 rethrow）")
    void visitFileFailedContinues() throws IOException {
        PathIo.SizeVisitor v = new PathIo.SizeVisitor();
        FileVisitResult r = v.visitFileFailed(Mockito.mock(Path.class), new IOException("denied"));
        assertThat(r).isEqualTo(FileVisitResult.CONTINUE);
    }
}
