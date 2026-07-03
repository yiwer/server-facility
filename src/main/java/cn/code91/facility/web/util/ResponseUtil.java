package cn.code91.facility.web.util;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.json.JsonUtil;
import cn.code91.facility.result.Result;
import cn.code91.facility.web.response.BaseResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.experimental.UtilityClass;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * <b>HTTP响应工具类</b>
 * <p>
 * 提供直接向 {@link HttpServletResponse} 写入JSON、设置下载头、禁用缓存等工具方法。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 在 Filter 中直接写出JSON响应
 * ResponseUtil.writeJson(response, R.err(401, "未认证"));
 *
 * // 设置文件下载头
 * ResponseUtil.setDownloadHeaders(response, "报表.xlsx");
 * }</pre>
 *
 * @author yvvb
 * @since 2.0.0
 */
@UtilityClass
public class ResponseUtil {

    // ==================== JSON写入 ====================

    /**
     * 向响应写入JSON（HTTP 200）
     *
     * @param response     HTTP响应
     * @param baseResponse 响应对象
     * @return 写入结果
     */
    public Result<Void, WrappedError> writeJson(HttpServletResponse response, BaseResponse<?> baseResponse) {
        return writeJson(response, HttpServletResponse.SC_OK, baseResponse);
    }

    /**
     * 向响应写入JSON（指定HTTP状态码）
     *
     * @param response     HTTP响应
     * @param httpStatus   HTTP状态码
     * @param baseResponse 响应对象
     * @return 写入结果
     */
    public Result<Void, WrappedError> writeJson(HttpServletResponse response, int httpStatus, BaseResponse<?> baseResponse) {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        return JsonUtil.serialize(baseResponse).flatMap(json -> {
            try {
                response.getWriter().write(json);
                response.getWriter().flush();
                return Result.ok(null);
            } catch (IOException e) {
                return Result.err(WrappedError.of(
                        FacilityErrorType.WEB_RESPONSE_WRITE_ERROR, e
                ));
            }
        });
    }

    // ==================== 下载头 ====================

    /**
     * 设置文件下载响应头（支持中文文件名，RFC 5987）
     *
     * @param response HTTP响应
     * @param filename 文件名
     */
    public void setDownloadHeaders(HttpServletResponse response, String filename) {
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename);
    }

    // ==================== 缓存控制 ====================

    /**
     * 设置禁用缓存响应头
     *
     * @param response HTTP响应
     */
    public void setNoCacheHeaders(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setDateHeader(HttpHeaders.EXPIRES, 0);
    }
}
