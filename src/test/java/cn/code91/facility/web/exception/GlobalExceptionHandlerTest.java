package cn.code91.facility.web.exception;

import cn.code91.facility.error.ErrorTypeInterface;
import cn.code91.facility.web.response.BaseResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DefaultGlobalExceptionHandler - 全局异常处理器")
class GlobalExceptionHandlerTest {

    private DefaultGlobalExceptionHandler handler;
    private WebRequest webRequest;

    // ==================== 测试用错误类型 ====================

    enum TestErrorType implements ErrorTypeInterface {
        TEST_BIZ_ERROR(900001, "test.biz_error", "业务错误: {0}");

        private final int code;
        private final String messageKey;
        private final String defaultMessage;

        TestErrorType(int code, String messageKey, String defaultMessage) {
            this.code = code;
            this.messageKey = messageKey;
            this.defaultMessage = defaultMessage;
        }

        @Override
        public int getCode() {
            return code;
        }

        @Override
        public String getMessageKey() {
            return messageKey;
        }

        @Override
        public String getDefaultMessage() {
            return defaultMessage;
        }
    }

    @BeforeEach
    void setUp() {
        handler = new DefaultGlobalExceptionHandler(new FacilityWebExceptionProperties(), new MockEnvironment());
        webRequest = new ServletWebRequest(new MockHttpServletRequest());
    }

    // ==================== FacilityException 测试（useProblemDetail=false 默认行为） ====================

    @Nested
    @DisplayName("handleFacilityException - 处理 facility 异常（默认 BaseResponse 包络）")
    class HandleFacilityExceptionDefaultEnvelopeTests {

        @Test
        @DisplayName("业务异常：返回对应错误码")
        void shouldReturnCorrectCodeAndMessage() {
            BusinessException ex = BusinessException.of(TestErrorType.TEST_BIZ_ERROR, "订单不存在");

            Object result = handler.handleFacilityException(ex, webRequest);

            assertThat(result).isInstanceOf(BaseResponse.class);
            @SuppressWarnings("unchecked")
            BaseResponse<Void> response = (BaseResponse<Void>) result;
            assertThat(response.getCode()).isEqualTo(900001);
            // 无 Spring Context 时 LocaleUtil 回退为 messageKey
            assertThat(response.getMessage()).isEqualTo("test.biz_error");
            assertThat(response.getData()).isNull();
        }

        @Test
        @DisplayName("业务异常无参数：返回 messageKey 作为回退消息")
        void shouldReturnDefaultMessageWhenNoArgs() {
            BusinessException ex = BusinessException.of(TestErrorType.TEST_BIZ_ERROR);

            Object result = handler.handleFacilityException(ex, webRequest);

            assertThat(result).isInstanceOf(BaseResponse.class);
            @SuppressWarnings("unchecked")
            BaseResponse<Void> response = (BaseResponse<Void>) result;
            assertThat(response.getCode()).isEqualTo(900001);
            assertThat(response.getMessage()).isEqualTo("test.biz_error");
        }
    }

    // ==================== MethodArgumentNotValidException 测试 ====================

    @Nested
    @DisplayName("handleMethodArgumentNotValidException - 处理参数校验异常")
    class HandleMethodArgumentNotValidExceptionTests {

        private MethodParameter dummyParam;

        @BeforeEach
        void setUp() throws NoSuchMethodException {
            dummyParam = new MethodParameter(Object.class.getMethod("toString"), -1);
        }

        @Test
        @DisplayName("单字段校验失败：返回 400 和字段错误信息")
        void shouldReturn400WithFieldError() {
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
            bindingResult.addError(new FieldError("target", "name", "不能为空"));
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(dummyParam, bindingResult);

            Object responseObj = handler.handleMethodArgumentNotValidException(ex, webRequest);
            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            @SuppressWarnings("unchecked")
            BaseResponse<Void> response = (BaseResponse<Void>) responseObj;

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getMessage()).contains("name");
            assertThat(response.getMessage()).contains("不能为空");
        }

        @Test
        @DisplayName("多字段校验失败：错误信息用分号拼接")
        void shouldJoinMultipleFieldErrors() {
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
            bindingResult.addError(new FieldError("target", "name", "不能为空"));
            bindingResult.addError(new FieldError("target", "age", "必须大于0"));
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(dummyParam, bindingResult);

            Object responseObj = handler.handleMethodArgumentNotValidException(ex, webRequest);
            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            @SuppressWarnings("unchecked")
            BaseResponse<Void> response = (BaseResponse<Void>) responseObj;

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getMessage()).contains("name: 不能为空");
            assertThat(response.getMessage()).contains("age: 必须大于0");
            assertThat(response.getMessage()).contains("; ");
        }
    }

    // ==================== BindException 测试 ====================

    @Nested
    @DisplayName("handleBindException - 处理参数绑定异常")
    class HandleBindExceptionTests {

        @Test
        @DisplayName("绑定异常：返回 400 和字段错误信息")
        void shouldReturn400WithBindError() {
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "form");
            bindingResult.addError(new FieldError("form", "email", "格式不正确"));
            BindException ex = new BindException(bindingResult);

            Object responseObj = handler.handleBindException(ex, webRequest);
            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            @SuppressWarnings("unchecked")
            BaseResponse<Void> response = (BaseResponse<Void>) responseObj;

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getMessage()).isEqualTo("email: 格式不正确");
        }
    }

    // ==================== HttpRequestMethodNotSupportedException 测试 ====================

    @Nested
    @DisplayName("handleHttpRequestMethodNotSupportedException - 处理请求方法不支持异常")
    class HandleMethodNotSupportedTests {

        @Test
        @DisplayName("方法不支持：返回 405")
        void shouldReturn405WithMethodName() {
            HttpRequestMethodNotSupportedException ex =
                    new HttpRequestMethodNotSupportedException("DELETE");

            Object responseObj = handler.handleHttpRequestMethodNotSupportedException(ex, webRequest);
            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            @SuppressWarnings("unchecked")
            BaseResponse<Void> response = (BaseResponse<Void>) responseObj;

            assertThat(response.getCode()).isEqualTo(405);
            // 无 Spring Context 时回退为 messageKey
            assertThat(response.getMessage()).isEqualTo("error.method_not_supported");
        }
    }

    // ==================== 兜底异常测试 ====================

    @Nested
    @DisplayName("handleException - 处理未知异常（兜底）")
    class HandleGenericExceptionTests {

        @Test
        @DisplayName("RuntimeException：返回 500")
        void shouldReturn500ForRuntimeException() {
            RuntimeException ex = new RuntimeException("unexpected error");

            Object responseObj = handler.handleException(ex, webRequest);
            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            @SuppressWarnings("unchecked")
            BaseResponse<Void> response = (BaseResponse<Void>) responseObj;

            assertThat(response.getCode()).isEqualTo(500);
            // 无 Spring Context 时回退为 messageKey
            assertThat(response.getMessage()).isEqualTo("error.system");
            assertThat(response.getData()).isNull();
        }

        @Test
        @DisplayName("NullPointerException：返回 500")
        void shouldReturn500ForNullPointerException() {
            NullPointerException ex = new NullPointerException("null ref");

            Object responseObj = handler.handleException(ex, webRequest);
            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            @SuppressWarnings("unchecked")
            BaseResponse<Void> response = (BaseResponse<Void>) responseObj;

            assertThat(response.getCode()).isEqualTo(500);
            assertThat(response.getMessage()).isEqualTo("error.system");
        }

        @Test
        @DisplayName("受检异常：返回 500")
        void shouldReturn500ForCheckedException() {
            Exception ex = new Exception("checked exception");

            Object responseObj = handler.handleException(ex, webRequest);
            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            @SuppressWarnings("unchecked")
            BaseResponse<Void> response = (BaseResponse<Void>) responseObj;

            assertThat(response.getCode()).isEqualTo(500);
            assertThat(response.getMessage()).isEqualTo("error.system");
        }
    }

    // ==================== 堆栈信息控制测试 ====================

    @Nested
    @DisplayName("includeTrace - 堆栈信息控制")
    class IncludeTraceTests {

        @Test
        @DisplayName("非生产环境：响应包含堆栈摘要")
        void shouldIncludeTraceInNonProdEnvironment() {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("dev");
            DefaultGlobalExceptionHandler devHandler = new DefaultGlobalExceptionHandler(
                new FacilityWebExceptionProperties(), env);

            RuntimeException ex = new RuntimeException("test error");
            Object responseObj = devHandler.handleException(ex, webRequest);
            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            @SuppressWarnings("unchecked")
            BaseResponse<Void> response = (BaseResponse<Void>) responseObj;

            assertThat(response.getDescription()).isNotNull().isNotEmpty();
            assertThat(response.getDescription()).contains("RuntimeException");
        }

        @Test
        @DisplayName("生产环境：响应不包含堆栈摘要")
        void shouldExcludeTraceInProdEnvironment() {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("prod");
            DefaultGlobalExceptionHandler prodHandler = new DefaultGlobalExceptionHandler(
                new FacilityWebExceptionProperties(), env);

            RuntimeException ex = new RuntimeException("test error");
            Object responseObj = prodHandler.handleException(ex, webRequest);
            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            @SuppressWarnings("unchecked")
            BaseResponse<Void> response = (BaseResponse<Void>) responseObj;

            // prod is not in the whitelist (dev/test/local) → no trace
            assertThat(response.getDescription()).isEmpty();
        }

        @Test
        @DisplayName("includesTraceInDevProfile - whitelist contains dev")
        void includesTraceInDevProfile() {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("dev");
            FacilityWebExceptionProperties props = new FacilityWebExceptionProperties();
            // default whitelist includes "dev"
            DefaultGlobalExceptionHandler h = new DefaultGlobalExceptionHandler(props, env);

            Object responseObj = h.handleException(new RuntimeException("boom"), webRequest);
            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            @SuppressWarnings("unchecked")
            BaseResponse<Void> response = (BaseResponse<Void>) responseObj;
            assertThat(response.getDescription()).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("hidesTraceInProdProfile - prod not in whitelist")
        void hidesTraceInProdProfile() {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("prod");
            FacilityWebExceptionProperties props = new FacilityWebExceptionProperties();
            DefaultGlobalExceptionHandler h = new DefaultGlobalExceptionHandler(props, env);

            Object responseObj = h.handleException(new RuntimeException("boom"), webRequest);
            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            @SuppressWarnings("unchecked")
            BaseResponse<Void> response = (BaseResponse<Void>) responseObj;
            assertThat(response.getDescription()).isEmpty();
        }
    }

    // ==================== RP-06 useProblemDetail=true 测试 ====================

    @Nested
    @DisplayName("useProblemDetail=true 场景 (RP-06)")
    class UseProblemDetailTests {

        private DefaultGlobalExceptionHandler pdHandler;

        @BeforeEach
        void setUp() {
            FacilityWebExceptionProperties props = new FacilityWebExceptionProperties();
            props.setUseProblemDetail(true);
            pdHandler = new DefaultGlobalExceptionHandler(props, new MockEnvironment());
        }

        @Test
        @DisplayName("BusinessException 返回 ResponseEntity<ProblemDetail>，status=400")
        void businessExceptionReturnsProblemDetail400() {
            BusinessException ex = BusinessException.of(TestErrorType.TEST_BIZ_ERROR, "订单不存在");

            Object responseObj = pdHandler.handleFacilityException(ex, webRequest);

            assertThat(responseObj).isInstanceOf(ResponseEntity.class);
            @SuppressWarnings("unchecked")
            ResponseEntity<ProblemDetail> re = (ResponseEntity<ProblemDetail>) responseObj;
            assertThat(re.getStatusCode().value()).isEqualTo(400);
            assertThat(re.getBody()).isNotNull();
            assertThat(re.getBody().getTitle()).isEqualTo(HttpStatus.BAD_REQUEST.getReasonPhrase());
        }

        @Test
        @DisplayName("SystemException 返回 ResponseEntity<ProblemDetail>，status=500")
        void systemExceptionReturnsProblemDetail500() {
            SystemException ex = SystemException.of(TestErrorType.TEST_BIZ_ERROR);

            Object responseObj = pdHandler.handleFacilityException(ex, webRequest);

            assertThat(responseObj).isInstanceOf(ResponseEntity.class);
            @SuppressWarnings("unchecked")
            ResponseEntity<ProblemDetail> re = (ResponseEntity<ProblemDetail>) responseObj;
            assertThat(re.getStatusCode().value()).isEqualTo(500);
            assertThat(re.getBody()).isNotNull();
            assertThat(re.getBody().getTitle()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        }

        @Test
        @DisplayName("MethodArgumentNotValidException 返回 ProblemDetail，status=400")
        void methodArgumentNotValidExceptionReturnsProblemDetail400() throws NoSuchMethodException {
            MethodParameter dummyParam = new MethodParameter(Object.class.getMethod("toString"), -1);
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
            bindingResult.addError(new FieldError("target", "name", "不能为空"));
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(dummyParam, bindingResult);

            Object responseObj = pdHandler.handleMethodArgumentNotValidException(ex, webRequest);

            assertThat(responseObj).isInstanceOf(ResponseEntity.class);
            @SuppressWarnings("unchecked")
            ResponseEntity<ProblemDetail> re = (ResponseEntity<ProblemDetail>) responseObj;
            assertThat(re.getStatusCode().value()).isEqualTo(400);
            assertThat(re.getBody()).isNotNull();
        }

        @Test
        @DisplayName("HttpRequestMethodNotSupportedException 返回 ProblemDetail，status=405")
        void methodNotSupportedReturnsProblemDetail405() {
            HttpRequestMethodNotSupportedException ex =
                    new HttpRequestMethodNotSupportedException("DELETE");

            Object responseObj = pdHandler.handleHttpRequestMethodNotSupportedException(ex, webRequest);

            assertThat(responseObj).isInstanceOf(ResponseEntity.class);
            @SuppressWarnings("unchecked")
            ResponseEntity<ProblemDetail> re = (ResponseEntity<ProblemDetail>) responseObj;
            assertThat(re.getStatusCode().value()).isEqualTo(405);
            assertThat(re.getBody()).isNotNull();
            assertThat(re.getBody().getTitle()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase());
        }

        @Test
        @DisplayName("HttpMediaTypeNotSupportedException 返回 ProblemDetail，status=415")
        void mediaTypeNotSupportedReturnsProblemDetail415() {
            HttpMediaTypeNotSupportedException ex =
                    new HttpMediaTypeNotSupportedException("application/xml");

            Object responseObj = pdHandler.handleHttpMediaTypeNotSupportedException(ex, webRequest);

            assertThat(responseObj).isInstanceOf(ResponseEntity.class);
            @SuppressWarnings("unchecked")
            ResponseEntity<ProblemDetail> re = (ResponseEntity<ProblemDetail>) responseObj;
            assertThat(re.getStatusCode().value()).isEqualTo(415);
            assertThat(re.getBody()).isNotNull();
            assertThat(re.getBody().getTitle()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE.getReasonPhrase());
        }

        @Test
        @DisplayName("MultipartException 返回 ProblemDetail，status=413")
        void multipartExceptionReturnsProblemDetail413() {
            MultipartException ex = new MultipartException("file too large");

            Object responseObj = pdHandler.handleMultipartException(ex, webRequest);

            assertThat(responseObj).isInstanceOf(ResponseEntity.class);
            @SuppressWarnings("unchecked")
            ResponseEntity<ProblemDetail> re = (ResponseEntity<ProblemDetail>) responseObj;
            assertThat(re.getStatusCode().value()).isEqualTo(413);
            assertThat(re.getBody()).isNotNull();
            assertThat(re.getBody().getTitle()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.getReasonPhrase());
        }

        @Test
        @DisplayName("兜底 Exception 返回 ProblemDetail，status=500")
        void genericExceptionReturnsProblemDetail500() {
            Exception ex = new RuntimeException("unexpected");

            Object responseObj = pdHandler.handleException(ex, webRequest);

            assertThat(responseObj).isInstanceOf(ResponseEntity.class);
            @SuppressWarnings("unchecked")
            ResponseEntity<ProblemDetail> re = (ResponseEntity<ProblemDetail>) responseObj;
            assertThat(re.getStatusCode().value()).isEqualTo(500);
            assertThat(re.getBody()).isNotNull();
            assertThat(re.getBody().getTitle()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        }
    }
}
