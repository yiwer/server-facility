package cn.code91.facility.web.exception;

import cn.code91.facility.web.ratelimit.RateLimitExceededException;
import cn.code91.facility.web.response.BaseResponse;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.io.IOException;
import java.util.Objects;

/** Application-owned HTTP error policy shared by MVC, Servlet filters and Security adapters. */
public class FacilityHttpErrors {
    private final FrameworkResolver framework = new FrameworkResolver();
    private static final Logger log = LoggerFactory.getLogger(FacilityHttpErrors.class);
    private static final int MAX_CAUSE_DEPTH = 64;
    private final FacilityWebExceptionProperties properties;
    private final MessageSource messages;
    private final ObjectMapper mapper;
    private final cn.code91.facility.web.filter.FacilityWebTraceProperties trace;
    private static final String TRACE_ATTRIBUTE = FacilityHttpErrors.class.getName() + ".traceId";

    public FacilityHttpErrors(FacilityWebExceptionProperties properties, MessageSource messages, ObjectMapper mapper) {
        this(properties, messages, mapper, new cn.code91.facility.web.filter.FacilityWebTraceProperties());
    }

    public FacilityHttpErrors(FacilityWebExceptionProperties properties, MessageSource messages, ObjectMapper mapper,
                              cn.code91.facility.web.filter.FacilityWebTraceProperties trace) {
        this.trace = Objects.requireNonNull(trace, "trace");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /** Resolves standard Spring status and headers while replacing diagnostic exception bodies. */
    public ResponseEntity<Object> response(Exception failure, WebRequest request) {
        if (request instanceof ServletWebRequest servlet && servlet.getResponse() != null
                && servlet.getResponse().isCommitted()) return null;
        Exception original = failure;
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Exception, Boolean>());
        int depth = 0;
        while (failure instanceof ServletException servlet && servlet.getCause() instanceof Exception cause) {
            if (depth++ >= MAX_CAUSE_DEPTH || !seen.add(failure)) {
                return render(original, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
            }
            failure = cause;
        }
        try {
            return framework.handleException(failure, request);
        } catch (Exception unhandled) {
            HttpStatusCode status = HttpStatus.INTERNAL_SERVER_ERROR;
            HttpHeaders headers = new HttpHeaders();
            if (failure instanceof ErrorResponse error) {
                status = error.getStatusCode();
                headers.putAll(error.getHeaders());
            } else if (failure instanceof ConstraintViolationException validation) {
                boolean returnValue = validation.getConstraintViolations().stream().anyMatch(violation -> {
                    for (var node : violation.getPropertyPath()) if (node.getKind() == jakarta.validation.ElementKind.RETURN_VALUE) return true;
                    return false;
                });
                status = returnValue ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_REQUEST;
            } else if (failure instanceof BusinessException || failure instanceof BindException || failure instanceof MultipartException) {
                status = HttpStatus.BAD_REQUEST;
            } else if (failure instanceof RateLimitExceededException limited) {
                status = HttpStatus.TOO_MANY_REQUESTS;
                headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, limited.getRetryAfterMillis() / 1000 + (limited.getRetryAfterMillis() % 1000 > 0 ? 1 : 0))));
            }
            return render(failure, headers, status, request);
        }
    }

    private final class FrameworkResolver extends ResponseEntityExceptionHandler {
        @Override
        protected ResponseEntity<Object> handleExceptionInternal(Exception failure, Object body, HttpHeaders headers,
                                                                 HttpStatusCode status, WebRequest request) {
            return render(failure, headers, status, request);
        }
    }

    private ResponseEntity<Object> render(Exception failure, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (request instanceof ServletWebRequest servlet && servlet.getResponse() != null) {
            if (servlet.getResponse().isCommitted()) return null;
            resetForError(servlet.getResponse());
        }
        String traceId = traceId(request);
        if (status.is5xxServerError()) logFailure("HTTP request failed with status " + status.value()
                + " (incidentId=" + traceId + ")", failure);
        String detail = status.value() == 500
                ? message("facility.web.error.system", "Internal server error", locale(request))
                : Objects.requireNonNullElse(HttpStatus.resolve(status.value()), HttpStatus.INTERNAL_SERVER_ERROR).getReasonPhrase();
        if (status.is4xxClientError() && failure instanceof BusinessException business) {
            String key = business.getErrorType().getMessageKey();
            // A reviewed host bundle supplies public text. Exception defaults and arguments are diagnostics.
            if (key != null && key.length() <= 200 && key.matches("[A-Za-z0-9_.-]+"))
                detail = message(key, detail, locale(request));
        }
        int code = failure instanceof FacilityException facility ? facility.getCode() : status.value();
        if (!properties.isUseProblemDetail()) {
            return ResponseEntity.status(status.value() == 429 ? status.value() : 200).headers(headers)
                    .body(BaseResponse.err(code, detail));
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        // Spring 7 leaves type unset; retain the published HTTP error protocol explicitly.
        problem.setType(java.net.URI.create("about:blank"));
        problem.setInstance(java.net.URI.create("urn:facility:error:" + traceId));
        problem.setProperty("code", code);
        problem.setProperty("traceId", traceId);
        problem.setProperty("errors", fieldErrors(failure, status, request));
        return ResponseEntity.status(status).headers(headers).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private static void resetForError(HttpServletResponse response) {
        var entityHeaders = java.util.Set.of("content-type", "content-length", "content-encoding", "content-disposition",
                "etag", "last-modified", "content-range", "accept-ranges", "cache-control", "expires");
        var retained = new java.util.LinkedHashMap<String, java.util.List<String>>();
        for (String name : response.getHeaderNames()) {
            if (!entityHeaders.contains(name.toLowerCase(java.util.Locale.ROOT))) {
                retained.put(name, java.util.List.copyOf(response.getHeaders(name)));
            }
        }
        // Unlike resetBuffer(), reset() also releases the previous Writer/OutputStream selection.
        response.reset();
        retained.forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    private java.util.List<java.util.Map<String, String>> fieldErrors(Exception failure, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) return java.util.List.of();
        java.util.stream.Stream<String> fields;
        if (failure instanceof BindException binding) {
            fields = binding.getFieldErrors().stream().map(org.springframework.validation.FieldError::getField);
        } else if (failure instanceof org.springframework.web.method.annotation.HandlerMethodValidationException validation) {
            fields = validation.getParameterValidationResults().stream()
                    .map(result -> result.getMethodParameter().getParameterName());
        } else if (failure instanceof ConstraintViolationException validation) {
            fields = validation.getConstraintViolations().stream().map(result -> result.getPropertyPath().toString());
        } else return java.util.List.of();
        String message = message("facility.web.error.invalid_value", "Invalid value", locale(request));
        return fields.map(FacilityHttpErrors::safeField).distinct().sorted().limit(32)
                .map(field -> java.util.Map.of("field", field, "code", "invalid", "message", message)).toList();
    }

    private static String safeField(String field) {
        if (field == null) return "request";
        // Map keys and collection indices are input, not field metadata.
        String normalized = field.replaceAll("\\[[^\\]]*\\]", "[]");
        return normalized.length() <= 120 && normalized.matches("[A-Za-z_$][A-Za-z0-9_$.\\[\\]]*")
                ? normalized : "request";
    }

    private static void logFailure(String message, Throwable failure) {
        // Throwable messages, suppressed exceptions and stack frames can contain request data.
        // Emit only application-independent metadata; the host may supply its own reviewed diagnostic policy.
        String kind = failure.getClass().getName();
        if (kind.length() > 200) kind = kind.substring(0, 200);
        try { log.error("{} (failureType={})", message, kind); }
        catch (RuntimeException ignored) {
            // Diagnostics are best effort; never recursively log a broken logging backend.
        }
    }

    private String message(String key, String fallback, java.util.Locale locale) {
        try {
            return messages.getMessage(key, null, fallback, locale);
        } catch (RuntimeException failure) {
            logFailure("HTTP error message lookup failed", failure);
            return fallback;
        }
    }

    private static java.util.Locale locale(WebRequest request) {
        return request instanceof ServletWebRequest servlet
                ? org.springframework.web.servlet.support.RequestContextUtils.getLocale(servlet.getRequest())
                : LocaleContextHolder.getLocale();
    }

    private String traceId(WebRequest request) {
        Object previous = request.getAttribute(TRACE_ATTRIBUTE, WebRequest.SCOPE_REQUEST);
        String value = previous instanceof String saved ? saved : null;
        if (value == null && trace.isEnabled() && request instanceof ServletWebRequest servlet && servlet.getResponse() != null) {
            value = servlet.getResponse().getHeader(trace.getHeaderName());
        }
        if (value == null && !trace.isEnabled()) {
            try {
                String observed = org.slf4j.MDC.get("traceId");
                if (observed != null && observed.matches("(?:[0-9a-f]{16}|[0-9a-f]{32})")
                        && !observed.matches("0+")) value = observed;
            } catch (RuntimeException ignored) { /* A broken MDC backend cannot change the HTTP result. */ }
        }
        // Without an active host span this is only an incident reference, not a newly created trace.
        if (value == null || !value.matches("[0-9A-Za-z_-]{1,64}")) value = java.util.UUID.randomUUID().toString();
        request.setAttribute(TRACE_ATTRIBUTE, value, WebRequest.SCOPE_REQUEST);
        if (trace.isEnabled() && request instanceof ServletWebRequest servlet && servlet.getResponse() != null) {
            servlet.getResponse().setHeader(trace.getHeaderName(), value);
        }
        return value;
    }

    /** Writes through the application's mapper; a committed response is left untouched. */
    public void write(HttpServletRequest request, HttpServletResponse response, Exception failure) throws IOException {
        if (response.isCommitted()) return;
        ResponseEntity<Object> resolved = response(failure, new ServletWebRequest(request, response));
        if (resolved == null) return;
        byte[] bytes;
        try {
            bytes = mapper.writeValueAsBytes(resolved.getBody());
        } catch (Exception serializationFailure) {
            logFailure("HTTP error serialization failed", serializationFailure);
            // This fixed fallback cannot invoke the failed application serializer again.
            String traceId = traceId(new ServletWebRequest(request, response));
            bytes = ("{\"type\":\"about:blank\",\"title\":\"Internal Server Error\",\"status\":500,"
                    + "\"detail\":\"Internal server error\",\"code\":500,\"errors\":[],\"traceId\":\"" + traceId
                    + "\",\"instance\":\"urn:facility:error:" + traceId + "\"}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            resolved = ResponseEntity.status(500).contentType(MediaType.APPLICATION_PROBLEM_JSON).build();
        }
        response.resetBuffer();
        response.setHeader(HttpHeaders.CONTENT_LENGTH, null);
        response.setHeader(HttpHeaders.CONTENT_ENCODING, null);
        response.setStatus(resolved.getStatusCode().value());
        resolved.getHeaders().forEach((name, values) -> {
            response.setHeader(name, null);
            values.forEach(value -> response.addHeader(name, value));
        });
        if (!resolved.getHeaders().containsHeader(HttpHeaders.CONTENT_TYPE)) response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getOutputStream().write(bytes);
    }
}
