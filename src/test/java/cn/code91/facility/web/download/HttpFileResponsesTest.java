package cn.code91.facility.web.download;

import cn.code91.facility.error.FacilityErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HttpFileResponses - 下载/预览(RFC 5987 中文名编码)")
class HttpFileResponsesTest {

    @TempDir
    Path tempDir;

    private File file(String name, String content) throws Exception {
        Path p = tempDir.resolve(name);
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
        return p.toFile();
    }

    @Test
    @DisplayName("download 中文名:Content-Disposition = attachment + filename*=UTF-8'' 百分号编码")
    void download_chineseName_contentDisposition() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        var result = HttpFileResponses.download(resp, file("data.bin", "hello world"), "报告.pdf");

        assertThat(result.isOk()).isTrue();
        // UriUtils.encode("报告.pdf", UTF_8) 实证值:%E6%8A%A5%E5%91%8A.pdf(legacy filename= 参数同样用编码值)
        assertThat(resp.getHeader("Content-Disposition")).isEqualTo(
                "attachment; filename=\"%E6%8A%A5%E5%91%8A.pdf\"; filename*=UTF-8''%E6%8A%A5%E5%91%8A.pdf");
    }

    @Test
    @DisplayName("download:body 字节与文件一致 + Content-Length + Content-Type 魔数推断")
    void download_bodyAndContentLength() throws Exception {
        File f = file("data.bin", "hello world");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertThat(HttpFileResponses.download(resp, f).isOk()).isTrue();

        assertThat(resp.getContentAsByteArray()).isEqualTo(Files.readAllBytes(f.toPath()));
        assertThat(resp.getContentLength()).isEqualTo(11);
        assertThat(resp.getContentType()).isEqualTo("text/plain"); // tika 魔数探测,非扩展名
    }

    @Test
    @DisplayName("preview:Content-Disposition = inline")
    void preview_inline() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        var result = HttpFileResponses.preview(resp, file("page.bin", "hi"));

        assertThat(result.isOk()).isTrue();
        assertThat(resp.getHeader("Content-Disposition")).isEqualTo("inline");
        assertThat(resp.getContentAsString()).isEqualTo("hi");
    }

    @Test
    @DisplayName("download 不存在文件 → FILE_NOT_FOUND")
    void download_missingFile_fileNotFound() {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        var result = HttpFileResponses.download(resp, tempDir.resolve("ghost.bin").toFile());
        assertThat(result.getErr().getErrorType()).isEqualTo(FacilityErrorType.FILE_NOT_FOUND);
    }

    @Test
    @DisplayName("preview 不存在文件 → FILE_NOT_FOUND")
    void preview_missingFile_fileNotFound() {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        var result = HttpFileResponses.preview(resp, tempDir.resolve("ghost.bin").toFile());
        assertThat(result.getErr().getErrorType()).isEqualTo(FacilityErrorType.FILE_NOT_FOUND);
    }

    @Test
    @DisplayName("downloadBytes 空数组 / null → FILE_READ_ERROR")
    void downloadBytes_emptyOrNull_fileReadError() {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertThat(HttpFileResponses.downloadBytes(resp, new byte[0], "a.txt", "text/plain").getErr()
                .isErrorType(FacilityErrorType.FILE_READ_ERROR)).isTrue();
        assertThat(HttpFileResponses.downloadBytes(resp, null, "a.txt", "text/plain").getErr()
                .isErrorType(FacilityErrorType.FILE_READ_ERROR)).isTrue();
    }

    @Test
    @DisplayName("downloadBytes:空格编码 %20、contentType null 回退 octet-stream、长度与 body")
    void downloadBytes_headersAndBody() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        var result = HttpFileResponses.downloadBytes(
                resp, "abc".getBytes(StandardCharsets.UTF_8), "a b.txt", null);

        assertThat(result.isOk()).isTrue();
        assertThat(resp.getHeader("Content-Disposition")).isEqualTo(
                "attachment; filename=\"a%20b.txt\"; filename*=UTF-8''a%20b.txt");
        assertThat(resp.getContentType()).isEqualTo("application/octet-stream"); // MimeTyping.FALLBACK
        assertThat(resp.getContentLength()).isEqualTo(3);
        assertThat(resp.getContentAsString()).isEqualTo("abc");
    }
}
