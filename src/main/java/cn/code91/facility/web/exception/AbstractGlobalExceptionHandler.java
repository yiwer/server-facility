package cn.code91.facility.web.exception;

import cn.code91.facility.locale.LocaleUtil;
import cn.code91.facility.log.LogUtil;
import cn.code91.facility.web.ratelimit.RateLimitExceededException;
import cn.code91.facility.web.response.BaseResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
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




/**
 * MVC adapter for the application-owned {@link FacilityHttpErrors} policy.
 * Host advice with higher priority can override individual exceptions. A subclass bean replaces the default advice.
 * New subclasses should inject FacilityHttpErrors and call {@code super(errors)}.
 */
@RestControllerAdvice
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE)
public abstract class AbstractGlobalExceptionHandler {
    private final FacilityHttpErrors errors;
    private static final int MAX_TRACE_DEPTH = 10;

    protected AbstractGlobalExceptionHandler(FacilityHttpErrors errors) {
        this.errors = java.util.Objects.requireNonNull(errors, "errors");
    }

    /** @deprecated Inject the application policy to honor the host mapper and MessageSource. */
    @Deprecated
    protected AbstractGlobalExceptionHandler(FacilityWebExceptionProperties props, Environment ignoredEnvironment) {
        var messages = new org.springframework.context.support.ResourceBundleMessageSource();
        messages.setBasename("i18n/facility-messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        errors = new FacilityHttpErrors(props == null ? new FacilityWebExceptionProperties() : props,
                messages, new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter().getMapper());
    }

    @ExceptionHandler({BusinessException.class, SystemException.class})
    public Object handleFacilityException(FacilityException e, WebRequest request) {
        return errors.response((Exception) e, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleMethodArgumentNotValidException(MethodArgumentNotValidException e, WebRequest request) {
        return errors.response(e, request);
    }

    @ExceptionHandler(BindException.class)
    public Object handleBindException(BindException e, WebRequest request) {
        return errors.response(e, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public Object handleHandlerMethodValidationException(HandlerMethodValidationException e, WebRequest request) {
        return errors.response(e, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Object handleConstraintViolationException(ConstraintViolationException e, WebRequest request) {
        return errors.response(e, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Object handleHttpMessageNotReadableException(HttpMessageNotReadableException e, WebRequest request) {
        return errors.response(e, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Object handleMissingServletRequestParameterException(MissingServletRequestParameterException e, WebRequest request) {
        return errors.response(e, request);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public Object handleMissingServletRequestPartException(MissingServletRequestPartException e, WebRequest request) {
        return errors.response(e, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Object handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e, WebRequest request) {
        return errors.response(e, request);
    }

    @ExceptionHandler(TypeMismatchException.class)
    public Object handleTypeMismatchException(TypeMismatchException e, WebRequest request) {
        return errors.response(e, request);
    }

    @ExceptionHandler(ConversionNotSupportedException.class)
    public Object handleConversionNotSupportedException(ConversionNotSupportedException e, WebRequest request) {
        return errors.response(e, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e, WebRequest request) {
        return errors.response(e, request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public Object handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e, WebRequest request) {
        return errors.response(e, request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public Object handleHttpMediaTypeNotAcceptableException(HttpMediaTypeNotAcceptableException e, WebRequest request) {
        return errors.response(e, request);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public Object handleNotFound(Exception e, WebRequest request) {
        return errors.response(e, request);
    }

    @ExceptionHandler(MultipartException.class)
    public Object handleMultipartException(MultipartException e, WebRequest request) {
        return errors.response(e, request);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public Object handleRateLimitExceeded(RateLimitExceededException e, WebRequest request) {
        return errors.response(e, request);
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public Object handleAsyncRequestTimeout(AsyncRequestTimeoutException e, WebRequest request) {
        return errors.response(e, request);
    }

    @ExceptionHandler(ErrorResponseException.class)
    public Object handleErrorResponseException(ErrorResponseException e, WebRequest request) {
        return errors.response(e, request);
    }

    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, WebRequest request) {
        return errors.response(e, request);
    }

    /** @deprecated HTTP responses never include diagnostic stack traces, in any profile. */
    @Deprecated
    protected boolean shouldIncludeTrace() { return false; }

    /** @deprecated Compatibility helper for host-owned messages; default handling uses the injected policy. */
    @Deprecated
    protected String resolveErrorMessage(String key, Object[] args, String fallback) {
        return LocaleUtil.translateMessageWithFallback(key, args, fallback, LocaleUtil.getLocale());
    }

    /** @deprecated Compatibility envelope for an explicitly selected host-owned message. Never appends a trace. */
    @Deprecated
    protected BaseResponse<Void> buildResponse(int code, String message, Exception ignoredFailure) {
        return BaseResponse.err(code, message);
    }

    /** @deprecated Diagnostic formatting only; never pass this value to an HTTP response. */
    @Deprecated
    protected String formatObjectError(ObjectError error) {
        return error instanceof FieldError field ? field.getField() + ": " + field.getDefaultMessage() : error.getDefaultMessage();
    }

    /** @deprecated Diagnostic request description only; the policy uses an opaque instance URI. */
    @Deprecated
    protected String getRequestURI(WebRequest request) {
        return request instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : request.getDescription(false);
    }

    /** @deprecated Diagnostic formatting only; never pass this value to an HTTP response. */
    @Deprecated
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


    /** @deprecated Use FacilityHttpErrors.response; this source-compatible helper always returns a safe ProblemDetail. */
    @Deprecated
    protected ResponseEntity<ProblemDetail> buildProblemDetail(Throwable ignoredFailure, HttpStatus status, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(status,
                status.is5xxServerError() ? "Internal server error" : status.getReasonPhrase());
        problem.setInstance(safeInstanceUri(request));
        return ResponseEntity.status(status).contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    /** @deprecated Instance values are opaque and never reflect potentially secret request input. */
    @Deprecated
    protected java.net.URI safeInstanceUri(WebRequest ignoredRequest) {
        return java.net.URI.create("urn:facility:error:" + java.util.UUID.randomUUID());
    }
}
