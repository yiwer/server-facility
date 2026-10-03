package cn.code91.facility.web.exception;

import cn.code91.facility.error.ErrorTypeInterface;
import cn.code91.facility.web.ratelimit.RateLimitExceededException;
import cn.code91.facility.web.response.BaseResponse;
import jakarta.validation.Validation;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.*;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.*;
import org.springframework.web.bind.*;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.*;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/** Source-compatible public handler adapters; Servlet behavior is independently tested over HTTP. */
class GlobalExceptionHandlerTest {
    private static final String SECRET = "SECRET-INPUT";
    private static final ErrorTypeInterface BUSINESS = new ErrorTypeInterface() {
        @Override public int getCode() { return 900001; }
        @Override public String getMessageKey() { return "test.business"; }
        @Override public String getDefaultMessage() { return SECRET + " {0}"; }
    };
    record Input(@jakarta.validation.constraints.Size(max = 3, message = SECRET) String password) {}
    public void endpoint(Input input) {}
    record Scenario(String name, int status, int code,
                    BiFunction<DefaultGlobalExceptionHandler, WebRequest, Object> invoke) {
        Scenario(String name, int status, BiFunction<DefaultGlobalExceptionHandler, WebRequest, Object> invoke) {
            this(name, status, status, invoke);
        }
    }

    @TestFactory
    Stream<DynamicTest> publicHandlersPreserveSafeStatusAndExplicitLegacyShape() throws Exception {
        var parameter = new MethodParameter(getClass().getMethod("endpoint", Input.class), 0);
        var binding = new BeanPropertyBindingResult(new Input(SECRET), "input");
        binding.addError(new FieldError("input", "password", SECRET, false, null, null, SECRET));
        var validation = new HandlerMethodValidationException(MethodValidationResult.create(this, parameter.getMethod(),
                List.of(new ParameterValidationResult(parameter, new Input(SECRET),
                        List.of(new DefaultMessageSourceResolvable(new String[]{"input"}, SECRET)),
                        null, null, null, (error, type) -> error))));
        jakarta.validation.ConstraintViolationException constraint;
        try (var factory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator())
                .buildValidatorFactory()) {
            constraint = new jakarta.validation.ConstraintViolationException(factory.getValidator().validate(new Input(SECRET)));
        }
        var scenarios = List.of(
                new Scenario("business", 400, 900001, (h,r) -> h.handleFacilityException(BusinessException.of(BUSINESS, SECRET), r)),
                new Scenario("system", 500, 900001, (h,r) -> h.handleFacilityException(SystemException.of(BUSINESS, new IllegalStateException(SECRET), SECRET), r)),
                new Scenario("body validation", 400, (h,r) -> h.handleMethodArgumentNotValidException(new MethodArgumentNotValidException(parameter, binding), r)),
                new Scenario("binding", 400, (h,r) -> h.handleBindException(new BindException(binding), r)),
                new Scenario("method validation", 400, (h,r) -> h.handleHandlerMethodValidationException(validation, r)),
                new Scenario("constraint violation", 400, (h,r) -> h.handleConstraintViolationException(constraint, r)),
                new Scenario("malformed body", 400, (h,r) -> h.handleHttpMessageNotReadableException(new HttpMessageNotReadableException(SECRET, new MockHttpInputMessage(new byte[0])), r)),
                new Scenario("missing parameter", 400, (h,r) -> h.handleMissingServletRequestParameterException(new MissingServletRequestParameterException(SECRET, "String"), r)),
                new Scenario("missing part", 400, (h,r) -> h.handleMissingServletRequestPartException(new MissingServletRequestPartException(SECRET), r)),
                new Scenario("argument mismatch", 400, (h,r) -> h.handleMethodArgumentTypeMismatchException(new MethodArgumentTypeMismatchException(SECRET, Integer.class, "number", parameter, null), r)),
                new Scenario("type mismatch", 400, (h,r) -> h.handleTypeMismatchException(new org.springframework.beans.TypeMismatchException(SECRET, Integer.class), r)),
                new Scenario("conversion unavailable", 500, (h,r) -> h.handleConversionNotSupportedException(new org.springframework.beans.ConversionNotSupportedException(SECRET, Integer.class, null), r)),
                new Scenario("method", 405, (h,r) -> h.handleHttpRequestMethodNotSupportedException(new HttpRequestMethodNotSupportedException("POST", List.of("GET")), r)),
                new Scenario("media input", 415, (h,r) -> h.handleHttpMediaTypeNotSupportedException(new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)), r)),
                new Scenario("media output", 406, (h,r) -> h.handleHttpMediaTypeNotAcceptableException(new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)), r)),
                new Scenario("no resource", 404, (h,r) -> h.handleNotFound(new NoResourceFoundException(HttpMethod.GET, SECRET, SECRET), r)),
                new Scenario("no handler", 404, (h,r) -> h.handleNotFound(new NoHandlerFoundException("GET", SECRET, new HttpHeaders()), r)),
                new Scenario("bad multipart", 400, (h,r) -> h.handleMultipartException(new MultipartException(SECRET), r)),
                new Scenario("upload limit", 413, (h,r) -> h.handleMultipartException(new MaxUploadSizeExceededException(64), r)),
                new Scenario("rate limit", 429, (h,r) -> h.handleRateLimitExceeded(new RateLimitExceededException(SECRET, 1501), r)),
                new Scenario("async timeout", 503, (h,r) -> h.handleAsyncRequestTimeout(new AsyncRequestTimeoutException(), r)),
                new Scenario("status reason", 422, (h,r) -> h.handleErrorResponseException(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, SECRET), r)),
                new Scenario("checked failure", 500, (h,r) -> h.handleException(new Exception(SECRET), r)),
                new Scenario("null message", 500, (h,r) -> h.handleException(new NullPointerException(), r)));
        return scenarios.stream().flatMap(scenario -> Stream.of(true, false).map(problem -> DynamicTest.dynamicTest(
                scenario.name() + (problem ? " ProblemDetail" : " legacy"), () -> {
                    var properties = new FacilityWebExceptionProperties();
                    properties.setUseProblemDetail(problem);
                    var handler = new DefaultGlobalExceptionHandler(properties, new MockEnvironment().withProperty("spring.profiles.active", "dev"));
                    var response = (ResponseEntity<?>) scenario.invoke().apply(handler,
                            new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()));
                    assertThat(response.getStatusCode().value()).isEqualTo(problem || scenario.status() == 429 ? scenario.status() : 200);
                    if (problem) {
                        var body = (ProblemDetail) response.getBody();
                        assertThat(body.getStatus()).isEqualTo(scenario.status());
                        assertThat(body.getProperties()).containsEntry("code", scenario.code());
                        assertThat(body.getInstance().toString()).startsWith("urn:facility:error:");
                    } else {
                        var body = (BaseResponse<?>) response.getBody();
                        assertThat(body.getCode()).isEqualTo(scenario.code());
                        assertThat(body.getDescription()).isEmpty();
                    }
                    assertThat(new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter().getMapper().writeValueAsString(response.getBody()))
                            .doesNotContain(SECRET, "stackTrace", "rejectedValue");
                })));
    }

    @Test
    void retryAfterCeilingHandlesZeroNegativeExactAndOverflowBoundaries() {
        var values = Map.of(-1L, "1", 0L, "1", 1L, "1", 999L, "1", 1000L, "1", 1001L, "2", Long.MAX_VALUE, "9223372036854776");
        values.forEach((millis, seconds) -> assertThat(policy().response(new RateLimitExceededException(SECRET, millis), request())
                .getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo(seconds));
    }

    @Test
    void validationBudgetsNormalizeUntrustedMapKeysAndBoundOutput() {
        for (int size : new int[]{31, 32, 33}) {
            var binding = new BeanPropertyBindingResult(new Object(), "input");
            for (int i = 0; i < size; i++) binding.addError(new FieldError("input", "field" + i + "[" + SECRET + "]", SECRET));
            var body = (ProblemDetail) policy().response(new BindException(binding), request()).getBody();
            var errors = (List<?>) body.getProperties().get("errors");
            assertThat(errors).hasSize(Math.min(size, 32));
            assertThat(errors.toString()).doesNotContain(SECRET).contains("[]");
        }
    }

    @Test
    void invalidAndLongFieldMetadataAreBoundedWithoutReflectingInput() {
        for (int length : new int[]{119, 120, 121}) {
            var boundary = new BeanPropertyBindingResult(new Object(), "input");
            boundary.addError(new FieldError("input", "x".repeat(length), SECRET));
            var problem = (ProblemDetail) policy().response(new BindException(boundary), request()).getBody();
            var first = (Map<?, ?>) ((List<?>) problem.getProperties().get("errors")).getFirst();
            assertThat(first.get("field")).isEqualTo(length <= 120 ? "x".repeat(length) : "request");
        }
        var binding = new BeanPropertyBindingResult(new Object(), "input");
        for (String field : List.of("x".repeat(121), "password[SECRET-INPUT]", "\u5bc6\u7801", "bad/SECRET-INPUT")) {
            binding.addError(new FieldError("input", field, SECRET));
        }
        var body = (ProblemDetail) policy().response(new BindException(binding), request()).getBody();
        assertThat(body.getProperties().get("errors").toString()).doesNotContain(SECRET, "x".repeat(121), "\u5bc6\u7801")
                .contains("password[]", "request");
    }

    @Test
    void invalidTraceAndMalformedUriNeverBecomeErrorInput() throws Exception {
        var servletRequest = new MockHttpServletRequest();
        servletRequest.setRequestURI("/SECRET-INPUT/{bad}%");
        var servletResponse = new MockHttpServletResponse();
        servletResponse.setHeader("X-Trace-Id", "invalid value".repeat(100));
        policy().write(servletRequest, servletResponse, new Exception(SECRET));
        var tree = new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter().getMapper().readTree(servletResponse.getContentAsString());
        assertThat(tree.path("traceId").asString()).matches("[0-9A-Za-z_-]{1,64}");
        assertThat(tree.path("instance").asString()).isEqualTo("urn:facility:error:" + tree.path("traceId").asString());
        assertThat(servletResponse.getContentAsString()).doesNotContain(SECRET, "invalid value");
    }

    @Test
    void committedPublicWriterAndResolverLeaveTheOriginalResponseUntouched() throws Exception {
        var response = new MockHttpServletResponse();
        response.getWriter().write("original");
        response.flushBuffer();
        var request = new MockHttpServletRequest();
        assertThat(policy().response(new Exception(SECRET), new ServletWebRequest(request, response))).isNull();
        policy().write(request, response, new Exception(SECRET));
        assertThat(response.getContentAsString()).isEqualTo("original");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void compatibilitySubclassCannotOptInToAutomaticTraceExposure() {
        class ConsumerAdvice extends DefaultGlobalExceptionHandler {
            ConsumerAdvice() { super(new FacilityWebExceptionProperties(), new MockEnvironment().withProperty("spring.profiles.active", "dev")); }
            BaseResponse<Void> appResponse() { return buildResponse(409, "Safe business rejection", new Exception(SECRET)); }
            boolean exposesTrace() { return shouldIncludeTrace(); }
        }
        var handler = new ConsumerAdvice();
        assertThat(handler.appResponse().getDescription()).isEmpty();
        assertThat(handler.exposesTrace()).isFalse();
    }

    @jakarta.validation.constraints.NotNull public Object serviceResult() { return null; }

    @Test
    void serviceReturnConstraintIsInternalFailureAndNeverAnInputError() throws Exception {
        try (var factory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator()).buildValidatorFactory()) {
            var violations = factory.getValidator().forExecutables().validateReturnValue(this, getClass().getMethod("serviceResult"), null);
            var result = policy().response(new jakarta.validation.ConstraintViolationException(violations), request());
            assertThat(result.getStatusCode().value()).isEqualTo(500);
            assertThat(((ProblemDetail) result.getBody()).getProperties().get("errors")).isEqualTo(List.of());
        }
    }

    @Test
    void messageSourceFailureFallsBackWithoutExposingItsDiagnostic() {
        var messages = new org.springframework.context.MessageSource() {
            @Override public String getMessage(String code, Object[] args, String fallback, java.util.Locale locale) {
                throw new IllegalStateException(SECRET);
            }
            @Override public String getMessage(String code, Object[] args, java.util.Locale locale) {
                throw new IllegalStateException(SECRET);
            }
            @Override public String getMessage(org.springframework.context.MessageSourceResolvable resolvable, java.util.Locale locale) {
                throw new IllegalStateException(SECRET);
            }
        };
        var policy = new FacilityHttpErrors(new FacilityWebExceptionProperties(), messages, new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter().getMapper());
        var result = policy.response(new Exception(SECRET), request());
        assertThat(((ProblemDetail) result.getBody()).getDetail()).isEqualTo("Internal server error");
    }

    @Test
    void repeatedResolutionRetainsTraceInBothBodyAndNewResponseHeader() throws Exception {
        var request = new MockHttpServletRequest();
        var policy = policy();
        var first = (ProblemDetail) policy.response(new Exception(SECRET), new ServletWebRequest(request)).getBody();
        var response = new MockHttpServletResponse();
        policy.write(request, response, new Exception(SECRET));
        assertThat(response.getHeader("X-Trace-Id")).isNull();
        assertThat(new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter().getMapper()
                .readTree(response.getContentAsByteArray()).path("traceId").asString())
                .isEqualTo(first.getProperties().get("traceId"));
    }

    @Test
    void servletCauseCyclesHaveSafe500WithoutRecursion() {
        var first = new jakarta.servlet.ServletException(SECRET);
        var second = new jakarta.servlet.ServletException(SECRET);
        first.initCause(second);
        second.initCause(first);
        var response = policy().response(first, request());
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(((ProblemDetail) response.getBody()).getDetail()).isEqualTo("Internal server error");
        assertThat(first.getCause()).isSameAs(second);
        assertThat(second.getCause()).isSameAs(first);
    }

    @Test
    void servletUnwrappingBudgetHandlesBoundaryAndDeepInput() {
        for (int count : new int[]{63, 64, 65, 10_000}) {
            Exception failure = new ResponseStatusException(HttpStatus.CONFLICT, SECRET);
            for (int i = 0; i < count; i++) failure = new jakarta.servlet.ServletException(SECRET, failure);
            var response = policy().response(failure, request());
            assertThat(response.getStatusCode().value()).as("nested causes %s", count).isEqualTo(count <= 64 ? 409 : 500);
            assertThat(((ProblemDetail) response.getBody()).getDetail()).doesNotContain(SECRET);
        }
    }

    private FacilityHttpErrors policy() {
        return new FacilityHttpErrors(new FacilityWebExceptionProperties(), new StaticMessageSource(), new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter().getMapper());
    }
    private ServletWebRequest request() { return new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()); }
}
