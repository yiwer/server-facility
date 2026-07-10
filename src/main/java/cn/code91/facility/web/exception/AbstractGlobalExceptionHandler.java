package cn.code91.facility.web.exception;

import cn.code91.facility.locale.LocaleUtil;
import cn.code91.facility.log.LogUtil;
import cn.code91.facility.web.ratelimit.RateLimitExceededException;
import cn.code91.facility.web.response.BaseResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <b>全局异常处理器 — 抽象基类</b>
 * <p>
 * 提供统一异常处理的默认实现。所有 {@code @ExceptionHandler} 方法均可被子类覆盖。
 * </p>
 *
 * <h3>扩展方式</h3>
 * <p>
 * 若应用不提供自定义实现，facility 将自动注册 {@link DefaultGlobalExceptionHandler}。
 * 应用可以通过继承本类并声明为 {@code @RestControllerAdvice} 来替换默认行为：
 * </p>
 * <pre>{@code
 * @RestControllerAdvice
 * public class AppExceptionHandler extends AbstractGlobalExceptionHandler {
 *
 *     public AppExceptionHandler(FacilityWebExceptionProperties props, Environment environment) {
 *         super(props, environment);
 *     }
 *
 *     // 覆盖统一 facility 异常处理器（含 BusinessException + SystemException）
 *     @Override
 *     public Object handleFacilityException(FacilityException e, WebRequest request) {
 *         // 自定义逻辑
 *     }
 *
 *     // 新增应用级处理器
 *     @ExceptionHandler(DuplicateKeyException.class)
 *     public BaseResponse<Void> handleDuplicateKey(DuplicateKeyException e, WebRequest request) {
 *         return buildResponse(409, "数据已存在", e);
 *     }
 * }
 * }</pre>
 *
 * @author yvvb
 * @see DefaultGlobalExceptionHandler
 * @since 2.0.0
 */
@RestControllerAdvice
public abstract class AbstractGlobalExceptionHandler {

    private static final int MAX_TRACE_DEPTH = 10;

    private final FacilityWebExceptionProperties props;
    private final Environment env;

    protected AbstractGlobalExceptionHandler(FacilityWebExceptionProperties props, Environment env) {
        this.props = props;
        this.env = env;
    }

    /**
     * Returns true when the current active profiles include at least one from
     * {@link FacilityWebExceptionProperties#getIncludeTraceProfiles()} (whitelist).
     * Empty whitelist means never expose.
     */
    protected boolean shouldIncludeTrace() {
        if (env == null || props == null) return false;
        String[] active = env.getActiveProfiles();
        List<String> allowed = props.getIncludeTraceProfiles();
        if (allowed == null || allowed.isEmpty()) return false;
        for (String a : active) {
            if (allowed.contains(a)) return true;
        }
        return false;
    }

    /**
     * <b>统一 facility 异常处理</b>
     * <p>处理 {@link BusinessException} + {@link SystemException}（均 implements
     * {@link FacilityException}）。详见 docs/adr/0004-rp-07-facility-exception-interface.md</p>
     *
     * @since phase-3
     */
    @ExceptionHandler({BusinessException.class, SystemException.class})
    public Object handleFacilityException(FacilityException e, WebRequest request) {
        // 经 C1 边界本地化入口 localize(ADR-0010):MessageSource 未命中 messageKey 时回退
        // errorType.getDefaultMessage() 模板渲染,而非让 NoSuchMessageException 穿透 @ExceptionHandler
        // 击穿统一响应契约(消费方 error type 忘记登记 i18n 键是常见场景)。
        String message = LocaleUtil.localize(e.getErrorType(), e.getArgs());
        LogUtil.warn("Facility 异常: code={}, message={}, path={}",
                e.getCode(), message, getRequestURI(request));
        if (props.isUseProblemDetail()) {
            HttpStatus status = (e instanceof BusinessException)
                    ? HttpStatus.BAD_REQUEST
                    : HttpStatus.INTERNAL_SERVER_ERROR;
            return buildProblemDetail((Throwable) e, status, request);
        }
        return buildResponse(e.getCode(), message, (Exception) e);
    }

    // ==================== 参数校验异常 ====================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleMethodArgumentNotValidException(MethodArgumentNotValidException e, WebRequest request) {
        String message = e.getBindingResult().getAllErrors().stream()
                .map(this::formatObjectError)
                .collect(Collectors.joining("; "));
        LogUtil.warn("参数校验失败: {}, path={}", message, getRequestURI(request));
        if (props.isUseProblemDetail()) {
            return buildProblemDetail(e, HttpStatus.BAD_REQUEST, request);
        }
        return buildResponse(400, message, e);
    }

    @ExceptionHandler(BindException.class)
    public Object handleBindException(BindException e, WebRequest request) {
        String message = e.getBindingResult().getAllErrors().stream()
                .map(this::formatObjectError)
                .collect(Collectors.joining("; "));
        LogUtil.warn("参数绑定失败: {}, path={}", message, getRequestURI(request));
        if (props.isUseProblemDetail()) {
            return buildProblemDetail(e, HttpStatus.BAD_REQUEST, request);
        }
        return buildResponse(400, message, e);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public Object handleHandlerMethodValidationException(HandlerMethodValidationException e, WebRequest request) {
        String message = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .map(MessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining("; "));
        LogUtil.warn("方法参数校验失败: {}, path={}", message, getRequestURI(request));
        if (props.isUseProblemDetail()) {
            return buildProblemDetail(e, HttpStatus.BAD_REQUEST, request);
        }
        return buildResponse(400, message, e);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Object handleConstraintViolationException(ConstraintViolationException e, WebRequest request) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        LogUtil.warn("约束校验失败: {}, path={}", message, getRequestURI(request));
        if (props.isUseProblemDetail()) {
            return buildProblemDetail(e, HttpStatus.BAD_REQUEST, request);
        }
        return buildResponse(400, message, e);
    }

    // ==================== 请求解析异常 ====================

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Object handleHttpMessageNotReadableException(HttpMessageNotReadableException e, WebRequest request) {
        LogUtil.warn("请求体解析失败: {}, path={}", e.getMessage(), getRequestURI(request));
        if (props.isUseProblemDetail()) {
            return buildProblemDetail(e, HttpStatus.BAD_REQUEST, request);
        }
        return buildResponse(400, resolveErrorMessage(
                "facility.web.error.message_not_readable", null, "Malformed request body"), e);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Object handleMissingServletRequestParameterException(MissingServletRequestParameterException e, WebRequest request) {
        String message = resolveErrorMessage("facility.web.error.missing_parameter",
                new Object[]{e.getParameterName()}, "Missing request parameter {0}");
        LogUtil.warn("缺少请求参数: {}, path={}", e.getParameterName(), getRequestURI(request));
        if (props.isUseProblemDetail()) {
            return buildProblemDetail(e, HttpStatus.BAD_REQUEST, request);
        }
        return buildResponse(400, message, e);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public Object handleMissingServletRequestPartException(MissingServletRequestPartException e, WebRequest request) {
        String message = resolveErrorMessage("facility.web.error.missing_part",
                new Object[]{e.getRequestPartName()}, "Missing request part {0}");
        LogUtil.warn("缺少请求部分: {}, path={}", e.getRequestPartName(), getRequestURI(request));
        if (props.isUseProblemDetail()) {
            return buildProblemDetail(e, HttpStatus.BAD_REQUEST, request);
        }
        return buildResponse(400, message, e);
    }

    /**
     * <b>参数类型不匹配(400,B1)</b>
     * <p>如 {@code ?age=abc} 转换 int 失败。此前无专门 handler 落兜底被误判 500 + ERROR 日志,
     * 实为客户端错误。</p>
     *
     * @since phase-error-audit
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Object handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e, WebRequest request) {
        String message = resolveErrorMessage("facility.web.error.type_mismatch",
                new Object[]{e.getName()}, "Invalid value for parameter {0}");
        LogUtil.warn("参数类型不匹配: {}, path={}", e.getName(), getRequestURI(request));
        if (props.isUseProblemDetail()) {
            return buildProblemDetail(e, HttpStatus.BAD_REQUEST, request);
        }
        return buildResponse(400, message, e);
    }

    // ==================== HTTP 协议异常 ====================

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e, WebRequest request) {
        String message = resolveErrorMessage("facility.web.error.method_not_supported",
                new Object[]{e.getMethod()}, "Request method {0} not supported");
        LogUtil.warn("请求方法不支持: {}, path={}", e.getMethod(), getRequestURI(request));
        if (props.isUseProblemDetail()) {
            return buildProblemDetail(e, HttpStatus.METHOD_NOT_ALLOWED, request);
        }
        return buildResponse(405, message, e);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public Object handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e, WebRequest request) {
        String message = resolveErrorMessage("facility.web.error.media_type_not_supported",
                new Object[]{e.getContentType()}, "Media type {0} not supported");
        LogUtil.warn("媒体类型不支持: {}, path={}", e.getContentType(), getRequestURI(request));
        if (props.isUseProblemDetail()) {
            return buildProblemDetail(e, HttpStatus.UNSUPPORTED_MEDIA_TYPE, request);
        }
        return buildResponse(415, message, e);
    }

    /**
     * <b>媒体类型不可接受(406,B2)</b>
     * <p>{@code Accept} 头与可产出类型不匹配。此前落兜底被误判 500 + ERROR 日志。注意:统一包络
     * 的 JSON 响应体对完全排斥 JSON 的 Accept 仍可能写不出(Spring 回落容器 406 空体),但常见
     * 浏览器 Accept 带 {@code *}{@code /}{@code *} 可达,且日志语义已修正为 WARN。</p>
     *
     * @since phase-error-audit
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public Object handleHttpMediaTypeNotAcceptableException(HttpMediaTypeNotAcceptableException e, WebRequest request) {
        String message = resolveErrorMessage("facility.web.error.not_acceptable",
                null, "Requested media type not acceptable");
        LogUtil.warn("媒体类型不可接受: {}, path={}", e.getMessage(), getRequestURI(request));
        if (props.isUseProblemDetail()) {
            return buildProblemDetail(e, HttpStatus.NOT_ACCEPTABLE, request);
        }
        return buildResponse(406, message, e);
    }

    // ==================== 路由未匹配(404) ====================

    /**
     * <b>未匹配路由处理(404)</b>
     * <p>处理 {@link NoResourceFoundException}(Spring 6.1+ 起,未匹配路由落到静态资源
     * 处理器 {@code /**} 时默认抛出)与 {@link NoHandlerFoundException}(配置
     * {@code spring.mvc.throw-exception-if-no-handler-found=true} 时抛出)。两者语义均为
     * "请求的资源/路由不存在",归 404。</p>
     * <p>若不显式拦截,二者会落入兜底 {@link #handleException} 被误判为 500,且以 ERROR 级
     * 记日志——扫描器/探测/拼错 URL 都会污染错误日志、可能误触告警。此处按真实语义返回 404
     * 并以 WARN 记录。</p>
     *
     * @since phase-web-404
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public Object handleNotFound(Exception e, WebRequest request) {
        LogUtil.warn("未匹配路由: {}, path={}", e.getMessage(), getRequestURI(request));
        if (props.isUseProblemDetail()) {
            return buildProblemDetail(e, HttpStatus.NOT_FOUND, request);
        }
        return buildResponse(404, resolveErrorMessage(
                "facility.web.error.not_found", null, "Requested resource not found"), e);
    }

    // ==================== 文件上传异常 ====================

    @ExceptionHandler(MultipartException.class)
    public Object handleMultipartException(MultipartException e, WebRequest request) {
        LogUtil.warn("文件上传异常: {}, path={}", e.getMessage(), getRequestURI(request));
        if (props.isUseProblemDetail()) {
            return buildProblemDetail(e, HttpStatus.PAYLOAD_TOO_LARGE, request);
        }
        return buildResponse(400, resolveErrorMessage(
                "facility.web.error.multipart", null, "File upload failed"), e);
    }

    // ==================== 限流异常 ====================

    /**
     * <b>限流超限处理(429)</b>
     * <p>处理 {@link RateLimitExceededException}(由 {@code RateLimitInterceptor}
     * 在 {@code @RateLimit} 超限时抛出),返回 HTTP 429 并附带 {@code Retry-After} 头
     * (单位:秒,至少 1 秒)。</p>
     *
     * @since phase-ratelimit
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public Object handleRateLimitExceeded(RateLimitExceededException e, WebRequest request) {
        LogUtil.warn("限流触发: {}, retryAfter={}ms, path={}", e.getMessage(), e.getRetryAfterMillis(), getRequestURI(request));
        long retryAfterSeconds = Math.max(1, e.getRetryAfterMillis() / 1000);
        if (props.isUseProblemDetail()) {
            ResponseEntity<ProblemDetail> pd = buildProblemDetail(e, HttpStatus.TOO_MANY_REQUESTS, request);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(retryAfterSeconds))
                    .body(pd.getBody());
        }
        BaseResponse<Void> body = buildResponse(429, resolveErrorMessage(
                "facility.web.error.rate_limited", null, "Too many requests"), e);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(retryAfterSeconds))
                .body(body);
    }

    // ==================== 异步请求超时(503) ====================

    /**
     * <b>异步请求超时(503)</b>
     * <p>处理 {@link AsyncRequestTimeoutException}(Callable/DeferredResult/WebAsyncTask 超时)。
     * 它实现 {@link org.springframework.web.ErrorResponse}(自带 503)但<b>不继承</b>
     * {@code ErrorResponseException},不被下方状态透传 handler 覆盖;若不显式拦截会落兜底被误判
     * 500 + ERROR。按真实语义归 503(Service Unavailable),WARN 记录(容量/时延信号,非代码缺陷)。</p>
     *
     * @since phase-error-audit
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public Object handleAsyncRequestTimeout(AsyncRequestTimeoutException e, WebRequest request) {
        LogUtil.warn("异步请求超时: path={}", getRequestURI(request));
        if (props.isUseProblemDetail()) {
            return buildProblemDetail(e, HttpStatus.SERVICE_UNAVAILABLE, request);
        }
        return buildResponse(503, resolveErrorMessage(
                "facility.web.error.async_timeout", null, "Request processing timed out"), e);
    }

    // ==================== 带状态异常透传(B3) ====================

    /**
     * <b>带状态异常透传</b>
     * <p>处理 {@link ErrorResponseException} 及其子类(含
     * {@link org.springframework.web.server.ResponseStatusException}——消费方/Spring 组件显式
     * 携带 HTTP 状态抛出)。若不显式拦截会落入兜底 {@link #handleException} 被压成 500,丢弃异常
     * 自带的预期状态(与 404 遮蔽同型:兜底 {@code Exception.class} 先于 Spring 默认解析器匹配)。</p>
     * <p>统一包络保持 HTTP 200 + body {@code code}=预期状态值;problemDetail 模式 honor 异常自带
     * status/headers/body(RFC 7807 instance 为可选项,保留异常自带值)。日志 4xx WARN / 5xx ERROR。</p>
     *
     * @since phase-error-audit
     */
    @ExceptionHandler(ErrorResponseException.class)
    public Object handleErrorResponseException(ErrorResponseException e, WebRequest request) {
        HttpStatusCode status = e.getStatusCode();
        String message = e.getBody().getDetail() != null ? e.getBody().getDetail() : e.getBody().getTitle();
        if (message == null) {
            message = String.valueOf(status.value());
        }
        if (status.is5xxServerError()) {
            LogUtil.error("带状态异常: status={}, path={}", e, status.value(), getRequestURI(request));
        } else {
            LogUtil.warn("带状态异常: status={}, message={}, path={}", status.value(), message, getRequestURI(request));
        }
        if (props.isUseProblemDetail()) {
            return ResponseEntity.status(status).headers(e.getHeaders()).body(e.getBody());
        }
        return buildResponse(status.value(), message, e);
    }

    // ==================== 兜底异常 ====================

    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, WebRequest request) {
        LogUtil.error("系统异常: path={}", e, getRequestURI(request));
        if (props.isUseProblemDetail()) {
            return buildProblemDetail(e, HttpStatus.INTERNAL_SERVER_ERROR, request);
        }
        return buildResponse(500, resolveErrorMessage(
                "facility.web.error.system", null, "Internal server error"), e);
    }

    // ==================== 可覆盖的工具方法 ====================

    /**
     * <b>解析 handler 固定 i18n 键(facility.web.error.*)</b>
     * <p>经 {@link LocaleUtil#translateMessageWithFallback}:MessageSource 未命中时回退
     * {@code defaultPattern}(与基座 bundle 英文文案同文)。消费方自带 {@code messageSource}
     * bean 时 facility 聚合链退让({@code @ConditionalOnMissingBean(name="messageSource")}),
     * facility 键不在其中是<b>受支持配置</b>——不得让 NoSuchMessageException 逃出
     * {@code @ExceptionHandler} 击穿统一响应契约;兜底 {@link #handleException} 也必须经此,
     * 否则连最后防线一并失守(A2,同 handleFacilityException 的 localize 修复族)。</p>
     */
    protected String resolveErrorMessage(String messageKey, Object[] args, String defaultPattern) {
        return LocaleUtil.translateMessageWithFallback(messageKey, args, defaultPattern, LocaleUtil.getLocale());
    }

    /**
     * 构建错误响应，白名单中的 profile 附带堆栈摘要
     */
    protected BaseResponse<Void> buildResponse(int code, String message, Exception e) {
        BaseResponse<Void> response = BaseResponse.err(code, message);
        if (shouldIncludeTrace()) {
            response.setDescription(formatTrace(e));
        }
        return response;
    }

    /**
     * 格式化校验错误信息：FieldError 带字段名前缀，全局 ObjectError 仅消息
     */
    protected String formatObjectError(ObjectError error) {
        if (error instanceof FieldError fieldError) {
            return fieldError.getField() + ": " + fieldError.getDefaultMessage();
        }
        return error.getDefaultMessage();
    }

    /**
     * 提取请求 URI
     */
    protected String getRequestURI(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            return servletRequest.getRequest().getRequestURI();
        }
        return request.getDescription(false);
    }

    /**
     * 格式化异常堆栈摘要（最多 {@value MAX_TRACE_DEPTH} 层）
     */
    protected String formatTrace(Exception e) {
        StackTraceElement[] trace = e.getStackTrace();
        if (trace == null || trace.length == 0) {
            return e.getClass().getName() + ": " + e.getMessage();
        }
        StringBuilder sb = new StringBuilder();
        sb.append(e.getClass().getName()).append(": ").append(e.getMessage()).append('\n');
        int depth = Math.min(trace.length, MAX_TRACE_DEPTH);
        for (int i = 0; i < depth; i++) {
            sb.append("  at ").append(trace[i]).append('\n');
        }
        if (trace.length > depth) {
            sb.append("  ... ").append(trace.length - depth).append(" more\n");
        }
        return sb.toString();
    }

    /**
     * <b>构建 RFC 7807 ProblemDetail 响应</b>
     * <p>当 {@code facility.web.exception.useProblemDetail=true} 时由各
     * handler 调用。详见 docs/adr/0003-rp-06-rfc-7807-problem-details.md</p>
     *
     * @since phase-3
     */
    protected ResponseEntity<ProblemDetail> buildProblemDetail(
            Throwable ex, HttpStatus status, WebRequest request) {
        String detail = ex.getMessage() != null ? ex.getMessage() : status.getReasonPhrase();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(safeInstanceUri(request));
        return ResponseEntity.status(status).body(problem);
    }

    /**
     * 请求路径 → ProblemDetail instance URI。畸形原始路径(容器 relaxedPathChars 等配置下可含
     * URI 非法字符)不得使错误响应本身抛 IllegalArgumentException 失败(A1 defense-in-depth):
     * 解析失败返回 null,instance 省略(RFC 7807 中为可选项),detail/title 仍完整。
     */
    protected java.net.URI safeInstanceUri(WebRequest request) {
        String uri = getRequestURI(request);
        try {
            return java.net.URI.create(uri);
        } catch (IllegalArgumentException e) {
            LogUtil.debug("请求路径无法构造 URI,ProblemDetail instance 省略: {}", e.getMessage());
            return null;
        }
    }
}
