package cn.code91.facility.web.download;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.log.LogUtil;
import cn.code91.facility.mime.MimeTyping;
import cn.code91.facility.result.Result;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.util.UriUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * <b>HTTP 文件下载与预览</b>
 * <p>统一处理中文文件名编码（RFC 5987）、Content-Type 推断、Content-Length 设置。</p>
 */
public final class HttpFileResponses {

    private HttpFileResponses() { throw new UnsupportedOperationException(); }

    public static Result<Void, WrappedError> download(HttpServletResponse response, File file) {
        return download(response, file, file.getName());
    }

    public static Result<Void, WrappedError> download(HttpServletResponse response, File file, String downloadName) {
        if (!file.exists()) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.FILE_NOT_FOUND, null, new Object[]{file.getPath()}));
        }
        String mimeType = MimeTyping.detect(file)
                .orElseGet(() -> MediaTypeFactory.getMediaType(downloadName)
                        .orElse(MediaType.APPLICATION_OCTET_STREAM).toString());

        response.setContentType(mimeType);
        response.setContentLengthLong(file.length());

        String encodedFileName = UriUtils.encode(downloadName, StandardCharsets.UTF_8);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName);

        try (InputStream in = new FileInputStream(file);
             ServletOutputStream out = response.getOutputStream()) {
            FileCopyUtils.copy(in, out);
            out.flush();
            return Result.ok();
        } catch (IOException e) {
            LogUtil.debug("Download canceled/failed: {}", e.getMessage());
            return Result.err(WrappedError.of(
                    FacilityErrorType.FILE_READ_ERROR, e, new Object[]{file.getName()}));
        }
    }

    public static Result<Void, WrappedError> preview(HttpServletResponse response, File file) {
        if (!file.exists()) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.FILE_NOT_FOUND, null, new Object[]{file.getPath()}));
        }
        String mimeType = MimeTyping.detect(file)
                .orElseGet(() -> MediaTypeFactory.getMediaType(file.getName())
                        .orElse(MediaType.APPLICATION_OCTET_STREAM).toString());

        response.setContentType(mimeType);
        response.setContentLengthLong(file.length());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline");

        try (InputStream in = new FileInputStream(file);
             ServletOutputStream out = response.getOutputStream()) {
            FileCopyUtils.copy(in, out);
            out.flush();
            return Result.ok();
        } catch (IOException e) {
            LogUtil.debug("Preview failed: {}", e.getMessage());
            return Result.err(WrappedError.of(
                    FacilityErrorType.FILE_READ_ERROR, e, new Object[]{file.getName()}));
        }
    }

    public static Result<Void, WrappedError> downloadBytes(
            HttpServletResponse response, byte[] data, String fileName, String contentType) {
        if (data == null || data.length == 0) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_READ_ERROR));
        }
        response.setContentType(contentType != null ? contentType : MimeTyping.FALLBACK);
        response.setContentLength(data.length);

        String encodedFileName = UriUtils.encode(fileName, StandardCharsets.UTF_8);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName);

        try (ServletOutputStream out = response.getOutputStream()) {
            out.write(data);
            out.flush();
            return Result.ok();
        } catch (IOException e) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.FILE_WRITE_ERROR, e, new Object[]{fileName}));
        }
    }
}
