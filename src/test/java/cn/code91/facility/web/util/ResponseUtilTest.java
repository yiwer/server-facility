package cn.code91.facility.web.util;

import cn.code91.facility.web.response.BaseResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ResponseUtil - JSON 写出/下载头/禁缓存头")
class ResponseUtilTest {

    @Test
    @DisplayName("writeJson 默认 200:contentType JSON、UTF-8、body JSON 往返")
    void writeJson_defaults200_jsonRoundTrip() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        var result = ResponseUtil.writeJson(resp, BaseResponse.ok("hi", "greet"));

        assertThat(result.isOk()).isTrue();
        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(resp.getContentType()).contains("application/json");
        assertThat(resp.getCharacterEncoding()).isEqualTo("UTF-8");

        JsonNode node = tools.jackson.databind.json.JsonMapper.builder().build().readTree(resp.getContentAsString());
        assertThat(node.get("code").asInt()).isEqualTo(200);
        assertThat(node.get("message").asString()).isEqualTo("greet");
        assertThat(node.get("data").asString()).isEqualTo("hi");
    }

    @Test
    @DisplayName("writeJson 指定 HTTP 状态码:status 与 envelope code 各自独立")
    void writeJson_customStatus() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        var result = ResponseUtil.writeJson(resp, 401, BaseResponse.err(401, "未认证"));

        assertThat(result.isOk()).isTrue();
        assertThat(resp.getStatus()).isEqualTo(401);
        JsonNode node = tools.jackson.databind.json.JsonMapper.builder().build().readTree(resp.getContentAsString());
        assertThat(node.get("code").asInt()).isEqualTo(401);
        assertThat(node.get("message").asString()).isEqualTo("未认证");
    }

    @Test
    @DisplayName("setDownloadHeaders:空格 + → %20,中文百分号编码,双 filename 头齐全")
    void setDownloadHeaders_encodesSpaceAndChinese() {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        ResponseUtil.setDownloadHeaders(resp, "报告 表.xlsx");

        // URLEncoder 输出 + 被替为 %20(探针实证);注意本方法 filename*=UTF-8''(大写,
        // 与 HttpFileResponses 的小写 utf-8'' 不同——两处行为均如实锁定)
        assertThat(resp.getHeader("Content-Disposition")).isEqualTo(
                "attachment; filename=\"%E6%8A%A5%E5%91%8A%20%E8%A1%A8.xlsx\"; "
                        + "filename*=UTF-8''%E6%8A%A5%E5%91%8A%20%E8%A1%A8.xlsx");
        assertThat(resp.getHeader("Content-Disposition")).doesNotContain("+");
        assertThat(resp.getContentType()).isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("setNoCacheHeaders:Cache-Control/Pragma/Expires 三头")
    void setNoCacheHeaders_threeHeaders() {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        ResponseUtil.setNoCacheHeaders(resp);

        assertThat(resp.getHeader("Cache-Control")).isEqualTo("no-cache, no-store, must-revalidate");
        assertThat(resp.getHeader("Pragma")).isEqualTo("no-cache");
        assertThat(resp.getHeader("Expires")).isEqualTo("Thu, 01 Jan 1970 00:00:00 GMT"); // dateHeader 0
    }
}
