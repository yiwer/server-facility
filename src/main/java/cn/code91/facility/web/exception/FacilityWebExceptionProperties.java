package cn.code91.facility.web.exception;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Whitelist of profiles in which stacktrace details are included in error responses.
 * <p>
 * Active profile must be IN this list for stacktrace to be exposed.
 * Empty list = never expose.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "facility.web.exception")
public class FacilityWebExceptionProperties {
    private List<String> includeTraceProfiles = List.of("dev", "test", "local");

    /**
     * 是否启用 RFC 7807 ProblemDetail 响应格式。
     * <p>默认 false 保留现有 BaseResponse + HTTP 200 行为；
     * 开启后 GlobalExceptionHandler 返回 ResponseEntity&lt;ProblemDetail&gt;，
     * Content-Type application/problem+json，含异常类型映射的 HTTP status。</p>
     *
     * <p>详见 docs/adr/0003-rp-06-rfc-7807-problem-details.md</p>
     *
     * @since phase-3
     */
    private boolean useProblemDetail = false;
}
