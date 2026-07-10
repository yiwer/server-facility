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
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
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
        String message = LocaleUtil.translateMessageWithArgs(
                e.getErrorType().getMessageKey(), e.getArgs());
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
        return buildResponse(400, LocaleUtil.translateMessage("facility.web.error.message_not_readable"), e);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Object handleMissingServletRequestParameterException(MissingServletRequestParameterException e, WebRequest request) {
        String message = LocaleUtil.translateMessageWithArgs(
                "facility.web.error.missing_parameter", new Object[]{e.getParameterName()});
        LogUtil.warn("缺少请求参数: {}, path={}", e.getParameterName(), getRequestURI(request));
        if (props.isUseProblemDetail()) {
            return buildProblemDetail(e, HttpStatus.BAD_REQUEST, request);
        }
        return buildResponse(400, message, e);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public Object handleMissingServletRequestPartException(MissingServletRequestPartException e, WebRequest request) {
        String message = LocaleUtil.translateMessageWithArgs(
                "facility.web.error.missing_part", new Object[]{e.getRequestPartName()});
        LogUtil.warn("缺少请求部分: {}, path={}", e.getRequestPartName(), getRequestURI(request));
        if (props.isUseProblemDetail()) {
            return buildProblemDetail(e, HttpStatus.BAD_REQUEST, request);
        }
        return buildResponse(400, message, e);
    }

    // ==================== HTTP 协议异常 ====================

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e, WebRequest request) {
        String message = LocaleUtil.translateMessageWithArgs(
                "facility.web.error.method_not_supported", new Object[]{e.getMethod()});
        LogUtil.warn("请求方法不支持: {}, path={}", e.getMethod(), getRequestURI(request));
        if (props.isUseProblemDetail()) {
            return buildProblemDetail(e, HttpStatus.METHOD_NOT_ALLOWED, request);
        }
        return buildResponse(405, message, e);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public Object handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e, WebRequest request) {
        String message = LocaleUtil.translateMessageWithArgs(
                "facility.web.error.media_type_not_supported", new Object[]{e.getContentType()});
        LogUtil.warn("媒体类型不支持: {}, path={}", e.getContentType(), getRequestURI(request));
        if (props.isUseProblemDetail()) {
            return buildProblemDetail(e, HttpStatus.UNSUPPORTED_MEDIA_TYPE, request);
        }
        return buildResponse(415, message, e);
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
        return buildResponse(404, LocaleUtil.translateMessage("facility.web.error.not_found"), e);
    }

    // ==================== 文件上传异常 ====================

    @ExceptionHandler(MultipartException.class)
    public Object handleMultipartException(MultipartException e, WebRequest request) {
        LogUtil.warn("文件上传异常: {}, path={}", e.getMessage(), getRequestURI(request));
        if (props.isUseProblemDetail()) {
            return buildProblemDetail(e, HttpStatus.PAYLOAD_TOO_LARGE, request);
        }
        return buildResponse(400, LocaleUtil.translateMessage("facility.web.error.multipart"), e);
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
        BaseResponse<Void> body = buildResponse(429, LocaleUtil.translateMessage("facility.web.error.rate_limited"), e);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(retryAfterSeconds))
                .body(body);
    }

    // ==================== 兜底异常 ====================

    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, WebRequest request) {
        LogUtil.error("系统异常: path={}", e, getRequestURI(request));
        if (props.isUseProblemDetail()) {
            return buildProblemDetail(e, HttpStatus.INTERNAL_SERVER_ERROR, request);
        }
        return buildResponse(500, LocaleUtil.translateMessage("facility.web.error.system"), e);
    }

    // ==================== 可覆盖的工具方法 ====================

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
        problem.setInstance(java.net.URI.create(getRequestURI(request)));
        return ResponseEntity.status(status).body(problem);
    }
}
