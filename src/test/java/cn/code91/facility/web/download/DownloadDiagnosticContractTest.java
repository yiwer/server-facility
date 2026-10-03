package cn.code91.facility.web.download;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletResponse;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import static org.assertj.core.api.Assertions.assertThat;

class DownloadDiagnosticContractTest {
    @TempDir Path directory;
    @Test void resultCarriesTheOriginalIoFailureAndLeavesLoggingToTheCaller() throws Exception {
        var file = Files.writeString(directory.resolve("upload-secret.txt"), "content-secret").toFile();
        var logger = (Logger) LoggerFactory.getLogger(HttpFileResponses.class);
        var level = logger.getLevel();
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender); logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        try {
            for (boolean preview : new boolean[]{false, true}) {
                var failure = new IOException("SQL_SECRET TOKEN_SECRET UPLOAD_SECRET");
                var response = new MockHttpServletResponse() {
                    @Override public jakarta.servlet.ServletOutputStream getOutputStream() {
                        return new jakarta.servlet.ServletOutputStream() {
                            @Override public boolean isReady() { return true; }
                            @Override public void setWriteListener(jakarta.servlet.WriteListener listener) {}
                            @Override public void write(int value) throws IOException { throw failure; }
                        };
                    }
                };
                var result = preview ? HttpFileResponses.preview(response, file) : HttpFileResponses.download(response, file);
                assertThat(result.isErr()).isTrue();
                assertThat(result.getErr().getException()).isSameAs(failure);
                assertThat(appender.list).isEmpty();
            }
        } finally { logger.detachAppender(appender); logger.setLevel(level); appender.stop(); }
    }
}
