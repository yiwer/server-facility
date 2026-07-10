package cn.code91.facility.web.exception;

import cn.code91.facility.context.SpringContextHolder;
import cn.code91.facility.context.SpringContextHolderTestSupport;
import cn.code91.facility.error.ErrorTypeInterface;
import cn.code91.facility.web.ratelimit.RateLimitExceededException;
import cn.code91.facility.web.response.BaseResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    /**
     * 构造一个 {@code getMessage()} 返回给定值的 {@link ConstraintViolation} mock，
     * 避免依赖真实 hibernate-validator 校验流程（无需 EL 依赖，断言值完全可控）。
     */
    @SuppressWarnings("unchecked")
    private static ConstraintViolation<Object> mockViolation(String message) {
        ConstraintViolation<Object> violation = Mockito.mock(ConstraintViolation.class);
        Mockito.when(violation.getMessage()).thenReturn(message);
        return violation;
    }

    /**
     * 构造一个真实的 {@link HandlerMethodValidationException}，其唯一
     * {@link ParameterValidationResult} 携带一条 {@code defaultMessage} 可控的
     * {@link DefaultMessageSourceResolvable}。
     */
    private static HandlerMethodValidationException newHandlerMethodValidationException(String defaultMessage)
            throws NoSuchMethodException {
        Method method = Object.class.getMethod("toString");
        MethodParameter param = new MethodParameter(method, -1);
        DefaultMessageSourceResolvable resolvable =
                new DefaultMessageSourceResolvable(new String[]{"test.code"}, defaultMessage);
        ParameterValidationResult pvr = new ParameterValidationResult(
                param, "bad-value", List.of(resolvable), null, null, null, (error, type) -> null);
        MethodValidationResult validationResult =
                MethodValidationResult.create(new Object(), method, List.of(pvr));
        return new HandlerMethodValidationException(validationResult);
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
            // 无 Spring Context 时经 localize 回退 defaultMessage 模板渲染(含参数),而非泄漏裸 messageKey
            assertThat(response.getMessage()).isEqualTo("业务错误: 订单不存在");
            assertThat(response.getData()).isNull();
        }

        @Test
        @DisplayName("业务异常无参数：回退 defaultMessage 模板原文(无参不经 MessageFormat)")
        void shouldReturnDefaultMessageWhenNoArgs() {
            BusinessException ex = BusinessException.of(TestErrorType.TEST_BIZ_ERROR);

            Object result = handler.handleFacilityException(ex, webRequest);

            assertThat(result).isInstanceOf(BaseResponse.class);
            @SuppressWarnings("unchecked")
            BaseResponse<Void> response = (BaseResponse<Void>) result;
            assertThat(response.getCode()).isEqualTo(900001);
            // 无参:renderFallback 返回模板原文(占位符 {0} 保留),仍不泄漏裸 messageKey
            assertThat(response.getMessage()).isEqualTo("业务错误: {0}");
        }
    }

    // ==================== FacilityException messageKey 缺失(契约守恒) ====================

    @Nested
    @DisplayName("handleFacilityException - messageKey 缺失时不穿透 NoSuchMessageException")
    class HandleFacilityExceptionMissingKeyTests {

        @BeforeEach
        @AfterEach
        void resetHolder() {
            // SpringContextHolder 是全局静态,装/拆必须成对——否则毒化其余无 context 用例(见其类 javadoc)
            SpringContextHolderTestSupport.reset();
        }

        /**
         * 装一个"在场但不含任何键"的 MessageSource:任何 messageKey 解析都会抛
         * {@link org.springframework.context.NoSuchMessageException}——复现消费方 error type
         * 忘记登记 i18n 键的生产场景。
         */
        private void installEmptyMessageSourceContext() {
            StaticMessageSource ms = new StaticMessageSource();
            GenericApplicationContext ctx = new GenericApplicationContext();
            ctx.getBeanFactory().registerSingleton("messageSource", ms);
            ctx.refresh();
            SpringContextHolder.setApplicationContextManually(ctx);
        }

        private static ErrorTypeInterface missingKeyType() {
            return new ErrorTypeInterface() {
                @Override public int getCode() { return 200001; }
                @Override public String getMessageKey() { return "app.order.absent_key"; }
                @Override public String getDefaultMessage() { return "订单 {0} 不存在"; }
            };
        }

        @Test
        @DisplayName("默认包络:messageKey 未命中回退 defaultMessage 模板,返回 BaseResponse 而非抛出")
        void missingKey_defaultEnvelope_fallsBackToTemplate() {
            installEmptyMessageSourceContext();
            BusinessException ex = BusinessException.of(missingKeyType(), "A-100");

            Object responseObj = handler.handleFacilityException(ex, webRequest);

            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            @SuppressWarnings("unchecked")
            BaseResponse<Void> response = (BaseResponse<Void>) responseObj;
            assertThat(response.getCode()).isEqualTo(200001);
            assertThat(response.getMessage()).isEqualTo("订单 A-100 不存在");
        }

        @Test
        @DisplayName("problemDetail 模式:messageKey 未命中不抛,返回 ProblemDetail status=400")
        void missingKey_problemDetail_doesNotThrow() {
            installEmptyMessageSourceContext();
            FacilityWebExceptionProperties pdProps = new FacilityWebExceptionProperties();
            pdProps.setUseProblemDetail(true);
            DefaultGlobalExceptionHandler pdHandler =
                    new DefaultGlobalExceptionHandler(pdProps, new MockEnvironment());
            BusinessException ex = BusinessException.of(missingKeyType(), "A-100");

            Object responseObj = pdHandler.handleFacilityException(ex, webRequest);

            assertThat(responseObj).isInstanceOf(ResponseEntity.class);
            @SuppressWarnings("unchecked")
            ResponseEntity<ProblemDetail> re = (ResponseEntity<ProblemDetail>) responseObj;
            assertThat(re.getStatusCode().value()).isEqualTo(400);
        }
    }

    // ==================== 固定键缺失(消费方自带 messageSource)契约守恒 ====================

    @Nested
    @DisplayName("固定 facility.web.error.* 键缺失 - 不穿透 NoSuchMessageException(A2)")
    class FixedKeyMissingMessageSourceTests {

        @BeforeEach
        @AfterEach
        void resetHolder() {
            // SpringContextHolder 是全局静态,装/拆必须成对——否则毒化其余无 context 用例
            SpringContextHolderTestSupport.reset();
        }

        /**
         * 消费方自带 {@code @Bean messageSource}(facility 聚合链
         * {@code @ConditionalOnMissingBean(name="messageSource")} 退让)且未含 facility 键
         * 的受支持生产配置:任何 facility.web.error.* 键解析均抛 NoSuchMessageException。
         */
        private void installEmptyMessageSourceContext() {
            StaticMessageSource ms = new StaticMessageSource();
            GenericApplicationContext ctx = new GenericApplicationContext();
            ctx.getBeanFactory().registerSingleton("messageSource", ms);
            ctx.refresh();
            SpringContextHolder.setApplicationContextManually(ctx);
        }

        @Test
        @DisplayName("兜底 handleException:键缺失回退内置默认文案,500 契约不破(兜底无退路场景)")
        void fallbackHandler_missingKey_stillReturnsEnvelope() {
            installEmptyMessageSourceContext();

            Object responseObj = handler.handleException(new RuntimeException("boom"), webRequest);

            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            BaseResponse<?> response = (BaseResponse<?>) responseObj;
            assertThat(response.getCode()).isEqualTo(500);
            assertThat(response.getMessage()).isEqualTo("Internal server error");
        }

        @Test
        @DisplayName("带参键 missing_parameter:键缺失回退默认模板并渲染参数")
        void argsHandler_missingKey_rendersDefaultTemplate() {
            installEmptyMessageSourceContext();
            MissingServletRequestParameterException ex =
                    new MissingServletRequestParameterException("userId", "String");

            Object responseObj = handler.handleMissingServletRequestParameterException(ex, webRequest);

            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            BaseResponse<?> response = (BaseResponse<?>) responseObj;
            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getMessage()).isEqualTo("Missing request parameter userId");
        }

        @Test
        @DisplayName("handleNotFound:键缺失回退内置默认文案,404 契约不破")
        void notFoundHandler_missingKey_returnsDefaultText() {
            installEmptyMessageSourceContext();
            NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/x");

            Object responseObj = handler.handleNotFound(ex, webRequest);

            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            BaseResponse<?> response = (BaseResponse<?>) responseObj;
            assertThat(response.getCode()).isEqualTo(404);
            assertThat(response.getMessage()).isEqualTo("Requested resource not found");
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
            // 无 Spring Context 时回退内置默认文案(与基座 bundle en 同文,A2)
            assertThat(response.getMessage()).isEqualTo("Request method DELETE not supported");
        }
    }

    // ==================== 未匹配路由测试(404) ====================

    @Nested
    @DisplayName("handleNotFound - 处理未匹配路由(404)")
    class HandleNotFoundTests {

        @Test
        @DisplayName("NoResourceFoundException：默认包络返回 404")
        void noResourceFound_returns404() {
            NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/api/unknown");

            Object responseObj = handler.handleNotFound(ex, webRequest);
            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            @SuppressWarnings("unchecked")
            BaseResponse<Void> response = (BaseResponse<Void>) responseObj;

            assertThat(response.getCode()).isEqualTo(404);
            // 无 Spring Context 时回退内置默认文案(与基座 bundle en 同文,A2)
            assertThat(response.getMessage()).isEqualTo("Requested resource not found");
            assertThat(response.getData()).isNull();
        }

        @Test
        @DisplayName("NoHandlerFoundException：默认包络返回 404")
        void noHandlerFound_returns404() {
            NoHandlerFoundException ex =
                    new NoHandlerFoundException("GET", "/api/unknown", HttpHeaders.EMPTY);

            Object responseObj = handler.handleNotFound(ex, webRequest);
            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            @SuppressWarnings("unchecked")
            BaseResponse<Void> response = (BaseResponse<Void>) responseObj;

            assertThat(response.getCode()).isEqualTo(404);
            assertThat(response.getMessage()).isEqualTo("Requested resource not found");
        }

        @Test
        @DisplayName("useProblemDetail=true：返回 ProblemDetail，status=404")
        void notFound_problemDetail404() {
            FacilityWebExceptionProperties pdProps = new FacilityWebExceptionProperties();
            pdProps.setUseProblemDetail(true);
            DefaultGlobalExceptionHandler pdHandler =
                    new DefaultGlobalExceptionHandler(pdProps, new MockEnvironment());
            NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/api/unknown");

            Object responseObj = pdHandler.handleNotFound(ex, webRequest);

            assertThat(responseObj).isInstanceOf(ResponseEntity.class);
            @SuppressWarnings("unchecked")
            ResponseEntity<ProblemDetail> re = (ResponseEntity<ProblemDetail>) responseObj;
            assertThat(re.getStatusCode().value()).isEqualTo(404);
            assertThat(re.getBody()).isNotNull();
            assertThat(re.getBody().getTitle()).isEqualTo(HttpStatus.NOT_FOUND.getReasonPhrase());
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
            // 无 Spring Context 时回退内置默认文案(与基座 bundle en 同文,A2)
            assertThat(response.getMessage()).isEqualTo("Internal server error");
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
            assertThat(response.getMessage()).isEqualTo("Internal server error");
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
            assertThat(response.getMessage()).isEqualTo("Internal server error");
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

        @Test
        @DisplayName("BindException 返回 ProblemDetail，status=400")
        void bindExceptionReturnsProblemDetail400() {
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "form");
            bindingResult.addError(new FieldError("form", "email", "格式不正确"));
            BindException ex = new BindException(bindingResult);

            Object responseObj = pdHandler.handleBindException(ex, webRequest);

            assertThat(responseObj).isInstanceOf(ResponseEntity.class);
            @SuppressWarnings("unchecked")
            ResponseEntity<ProblemDetail> re = (ResponseEntity<ProblemDetail>) responseObj;
            assertThat(re.getStatusCode().value()).isEqualTo(400);
            assertThat(re.getBody()).isNotNull();
        }

        @Test
        @DisplayName("ConstraintViolationException 返回 ProblemDetail，status=400")
        void constraintViolationExceptionReturnsProblemDetail400() {
            Set<ConstraintViolation<?>> violations = new LinkedHashSet<>();
            violations.add(mockViolation("不能为空"));
            ConstraintViolationException ex = new ConstraintViolationException(violations);

            Object responseObj = pdHandler.handleConstraintViolationException(ex, webRequest);

            assertThat(responseObj).isInstanceOf(ResponseEntity.class);
            @SuppressWarnings("unchecked")
            ResponseEntity<ProblemDetail> re = (ResponseEntity<ProblemDetail>) responseObj;
            assertThat(re.getStatusCode().value()).isEqualTo(400);
            assertThat(re.getBody()).isNotNull();
        }

        @Test
        @DisplayName("HttpMessageNotReadableException 返回 ProblemDetail，status=400，detail 为异常消息")
        void httpMessageNotReadableExceptionReturnsProblemDetail400() {
            HttpMessageNotReadableException ex =
                    new HttpMessageNotReadableException("malformed json", new MockHttpInputMessage(new byte[0]));

            Object responseObj = pdHandler.handleHttpMessageNotReadableException(ex, webRequest);

            assertThat(responseObj).isInstanceOf(ResponseEntity.class);
            @SuppressWarnings("unchecked")
            ResponseEntity<ProblemDetail> re = (ResponseEntity<ProblemDetail>) responseObj;
            assertThat(re.getStatusCode().value()).isEqualTo(400);
            assertThat(re.getBody()).isNotNull();
            assertThat(re.getBody().getDetail()).isEqualTo("malformed json");
        }

        @Test
        @DisplayName("MissingServletRequestParameterException 返回 ProblemDetail，status=400")
        void missingServletRequestParameterExceptionReturnsProblemDetail400() {
            MissingServletRequestParameterException ex =
                    new MissingServletRequestParameterException("userId", "String");

            Object responseObj = pdHandler.handleMissingServletRequestParameterException(ex, webRequest);

            assertThat(responseObj).isInstanceOf(ResponseEntity.class);
            @SuppressWarnings("unchecked")
            ResponseEntity<ProblemDetail> re = (ResponseEntity<ProblemDetail>) responseObj;
            assertThat(re.getStatusCode().value()).isEqualTo(400);
            assertThat(re.getBody()).isNotNull();
        }

        @Test
        @DisplayName("MissingServletRequestPartException 返回 ProblemDetail，status=400")
        void missingServletRequestPartExceptionReturnsProblemDetail400() {
            MissingServletRequestPartException ex = new MissingServletRequestPartException("file");

            Object responseObj = pdHandler.handleMissingServletRequestPartException(ex, webRequest);

            assertThat(responseObj).isInstanceOf(ResponseEntity.class);
            @SuppressWarnings("unchecked")
            ResponseEntity<ProblemDetail> re = (ResponseEntity<ProblemDetail>) responseObj;
            assertThat(re.getStatusCode().value()).isEqualTo(400);
            assertThat(re.getBody()).isNotNull();
        }

        @Test
        @DisplayName("HandlerMethodValidationException 返回 ProblemDetail，status=400")
        void handlerMethodValidationExceptionReturnsProblemDetail400() throws NoSuchMethodException {
            HandlerMethodValidationException ex = newHandlerMethodValidationException("必须为正数");

            Object responseObj = pdHandler.handleHandlerMethodValidationException(ex, webRequest);

            assertThat(responseObj).isInstanceOf(ResponseEntity.class);
            @SuppressWarnings("unchecked")
            ResponseEntity<ProblemDetail> re = (ResponseEntity<ProblemDetail>) responseObj;
            assertThat(re.getStatusCode().value()).isEqualTo(400);
            assertThat(re.getBody()).isNotNull();
        }
    }

    // ==================== ConstraintViolationException 测试 ====================

    @Nested
    @DisplayName("handleConstraintViolationException - 处理约束校验异常")
    class HandleConstraintViolationExceptionTests {

        @Test
        @DisplayName("多个违规：消息用分号拼接")
        void shouldJoinMultipleViolationMessages() {
            Set<ConstraintViolation<?>> violations = new LinkedHashSet<>();
            violations.add(mockViolation("不能为空"));
            violations.add(mockViolation("必须大于0"));
            ConstraintViolationException ex = new ConstraintViolationException(violations);

            Object responseObj = handler.handleConstraintViolationException(ex, webRequest);
            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            @SuppressWarnings("unchecked")
            BaseResponse<Void> response = (BaseResponse<Void>) responseObj;

            // ConstraintViolationException 构造函数内部会把传入的 Set 拷贝进自有集合，
            // 不保证保留调用方的插入顺序（探针实测：确曾出现反序），故按子串而非整串断言。
            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getMessage()).contains("不能为空");
            assertThat(response.getMessage()).contains("必须大于0");
            assertThat(response.getMessage()).contains("; ");
        }
    }

    // ==================== HttpMessageNotReadableException 测试 ====================

    @Nested
    @DisplayName("handleHttpMessageNotReadableException - 处理请求体解析异常")
    class HandleHttpMessageNotReadableExceptionTests {

        @Test
        @DisplayName("解析失败：返回 400 与内置默认文案(缺 bundle/缺 context 回退,A2)")
        void shouldReturn400WithTranslatedKey() {
            HttpMessageNotReadableException ex =
                    new HttpMessageNotReadableException("malformed json", new MockHttpInputMessage(new byte[0]));

            Object responseObj = handler.handleHttpMessageNotReadableException(ex, webRequest);
            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            @SuppressWarnings("unchecked")
            BaseResponse<Void> response = (BaseResponse<Void>) responseObj;

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getMessage()).isEqualTo("Malformed request body");
        }
    }

    // ==================== MissingServletRequestParameterException 测试 ====================

    @Nested
    @DisplayName("handleMissingServletRequestParameterException - 处理缺失请求参数异常")
    class HandleMissingServletRequestParameterExceptionTests {

        @Test
        @DisplayName("缺少参数：返回 400 与内置默认文案(缺 bundle/缺 context 回退,A2)")
        void shouldReturn400WithTranslatedKey() {
            MissingServletRequestParameterException ex =
                    new MissingServletRequestParameterException("userId", "String");

            Object responseObj = handler.handleMissingServletRequestParameterException(ex, webRequest);
            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            @SuppressWarnings("unchecked")
            BaseResponse<Void> response = (BaseResponse<Void>) responseObj;

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getMessage()).isEqualTo("Missing request parameter userId");
        }
    }

    // ==================== MissingServletRequestPartException 测试 ====================

    @Nested
    @DisplayName("handleMissingServletRequestPartException - 处理缺失请求部分异常")
    class HandleMissingServletRequestPartExceptionTests {

        @Test
        @DisplayName("缺少部分：返回 400 与内置默认文案(缺 bundle/缺 context 回退,A2)")
        void shouldReturn400WithTranslatedKey() {
            MissingServletRequestPartException ex = new MissingServletRequestPartException("file");

            Object responseObj = handler.handleMissingServletRequestPartException(ex, webRequest);
            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            @SuppressWarnings("unchecked")
            BaseResponse<Void> response = (BaseResponse<Void>) responseObj;

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getMessage()).isEqualTo("Missing request part file");
        }
    }

    // ==================== HandlerMethodValidationException 测试 ====================

    @Nested
    @DisplayName("handleHandlerMethodValidationException - 处理方法参数校验异常")
    class HandleHandlerMethodValidationExceptionTests {

        @Test
        @DisplayName("方法参数校验失败：返回 400 与错误信息")
        void shouldReturn400WithResolvedErrors() throws NoSuchMethodException {
            HandlerMethodValidationException ex = newHandlerMethodValidationException("必须为正数");

            Object responseObj = handler.handleHandlerMethodValidationException(ex, webRequest);
            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            @SuppressWarnings("unchecked")
            BaseResponse<Void> response = (BaseResponse<Void>) responseObj;

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getMessage()).isEqualTo("必须为正数");
        }
    }

    // ==================== HttpMediaTypeNotSupportedException 默认路径测试 ====================

    @Nested
    @DisplayName("handleHttpMediaTypeNotSupportedException - 默认 BaseResponse 包络")
    class HandleMediaTypeNotSupportedDefaultTests {

        @Test
        @DisplayName("媒体类型不支持：返回 415 与内置默认文案(缺 bundle/缺 context 回退,A2)")
        void shouldReturn415WithTranslatedKey() {
            HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(
                    MediaType.APPLICATION_XML, List.of(MediaType.APPLICATION_JSON));

            Object responseObj = handler.handleHttpMediaTypeNotSupportedException(ex, webRequest);
            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            @SuppressWarnings("unchecked")
            BaseResponse<Void> response = (BaseResponse<Void>) responseObj;

            assertThat(response.getCode()).isEqualTo(415);
            assertThat(response.getMessage()).isEqualTo("Media type application/xml not supported");
        }
    }

    // ==================== MultipartException 默认路径测试 ====================

    @Nested
    @DisplayName("handleMultipartException - 默认 BaseResponse 包络")
    class HandleMultipartExceptionDefaultTests {

        @Test
        @DisplayName("文件上传异常：返回 400 与内置默认文案(缺 bundle/缺 context 回退,A2)")
        void shouldReturn400WithTranslatedKey() {
            MultipartException ex = new MultipartException("upload failed");

            Object responseObj = handler.handleMultipartException(ex, webRequest);
            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            @SuppressWarnings("unchecked")
            BaseResponse<Void> response = (BaseResponse<Void>) responseObj;

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getMessage()).isEqualTo("File upload failed");
        }
    }

    // ==================== MethodArgumentTypeMismatchException 测试(400,B1) ====================

    @Nested
    @DisplayName("handleMethodArgumentTypeMismatchException - 参数类型不匹配(400,此前落兜底 500)")
    class HandleMethodArgumentTypeMismatchTests {

        private MethodArgumentTypeMismatchException newTypeMismatch() throws NoSuchMethodException {
            MethodParameter param = new MethodParameter(Object.class.getMethod("toString"), -1);
            return new MethodArgumentTypeMismatchException("abc", Integer.class, "age", param,
                    new NumberFormatException("For input string: \"abc\""));
        }

        @Test
        @DisplayName("默认包络:返回 400,消息含参数名")
        void typeMismatch_returns400() throws NoSuchMethodException {
            Object responseObj = handler.handleMethodArgumentTypeMismatchException(newTypeMismatch(), webRequest);

            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            BaseResponse<?> response = (BaseResponse<?>) responseObj;
            assertThat(response.getCode()).isEqualTo(400);
            // 无 Spring Context 时回退内置默认模板渲染
            assertThat(response.getMessage()).isEqualTo("Invalid value for parameter age");
        }

        @Test
        @DisplayName("problemDetail 模式:status=400")
        void typeMismatch_problemDetail400() throws NoSuchMethodException {
            FacilityWebExceptionProperties pdProps = new FacilityWebExceptionProperties();
            pdProps.setUseProblemDetail(true);
            DefaultGlobalExceptionHandler pdHandler =
                    new DefaultGlobalExceptionHandler(pdProps, new MockEnvironment());

            Object responseObj = pdHandler.handleMethodArgumentTypeMismatchException(newTypeMismatch(), webRequest);

            assertThat(responseObj).isInstanceOf(ResponseEntity.class);
            ResponseEntity<?> re = (ResponseEntity<?>) responseObj;
            assertThat(re.getStatusCode().value()).isEqualTo(400);
        }
    }

    // ==================== 裸 TypeMismatchException(400)与 ConversionNotSupported(500) ====================

    @Nested
    @DisplayName("handleTypeMismatchException - 裸 TypeMismatch(400)/ConversionNotSupported 保 500")
    class HandleTypeMismatchTests {

        @Test
        @DisplayName("裸 TypeMismatchException:默认包络返回 400(此前落兜底 500)")
        void rawTypeMismatch_returns400() {
            TypeMismatchException ex = new TypeMismatchException("abc", Integer.class);

            Object responseObj = handler.handleTypeMismatchException(ex, webRequest);

            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            BaseResponse<?> response = (BaseResponse<?>) responseObj;
            assertThat(response.getCode()).isEqualTo(400);
            // propertyName 为 null 时渲染 "null"——与 media_type null 既有约定一致(罕见路径,如实锁定)
            assertThat(response.getMessage()).isEqualTo("Invalid value for parameter null");
        }

        @Test
        @DisplayName("problemDetail 模式:status=400")
        void rawTypeMismatch_problemDetail400() {
            FacilityWebExceptionProperties pdProps = new FacilityWebExceptionProperties();
            pdProps.setUseProblemDetail(true);
            DefaultGlobalExceptionHandler pdHandler =
                    new DefaultGlobalExceptionHandler(pdProps, new MockEnvironment());
            TypeMismatchException ex = new TypeMismatchException("abc", Integer.class);

            Object responseObj = pdHandler.handleTypeMismatchException(ex, webRequest);

            assertThat(responseObj).isInstanceOf(ResponseEntity.class);
            ResponseEntity<?> re = (ResponseEntity<?>) responseObj;
            assertThat(re.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        @DisplayName("ConversionNotSupportedException(服务端转换器问题):保持 500,不被父类 handler 误判 400")
        void conversionNotSupported_stays500() {
            ConversionNotSupportedException ex = new ConversionNotSupportedException(
                    "v", String.class, new IllegalStateException("no converter"));

            Object responseObj = handler.handleConversionNotSupportedException(ex, webRequest);

            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            BaseResponse<?> response = (BaseResponse<?>) responseObj;
            assertThat(response.getCode()).isEqualTo(500);
            assertThat(response.getMessage()).isEqualTo("Internal server error");
        }
    }

    // ==================== HttpMediaTypeNotAcceptableException 测试(406,B2) ====================

    @Nested
    @DisplayName("handleHttpMediaTypeNotAcceptableException - 媒体类型不可接受(406,此前落兜底 500)")
    class HandleMediaTypeNotAcceptableTests {

        @Test
        @DisplayName("默认包络:返回 406 与内置默认文案")
        void notAcceptable_returns406() {
            HttpMediaTypeNotAcceptableException ex =
                    new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON));

            Object responseObj = handler.handleHttpMediaTypeNotAcceptableException(ex, webRequest);

            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            BaseResponse<?> response = (BaseResponse<?>) responseObj;
            assertThat(response.getCode()).isEqualTo(406);
            assertThat(response.getMessage()).isEqualTo("Requested media type not acceptable");
        }

        @Test
        @DisplayName("problemDetail 模式:status=406")
        void notAcceptable_problemDetail406() {
            FacilityWebExceptionProperties pdProps = new FacilityWebExceptionProperties();
            pdProps.setUseProblemDetail(true);
            DefaultGlobalExceptionHandler pdHandler =
                    new DefaultGlobalExceptionHandler(pdProps, new MockEnvironment());
            HttpMediaTypeNotAcceptableException ex =
                    new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON));

            Object responseObj = pdHandler.handleHttpMediaTypeNotAcceptableException(ex, webRequest);

            assertThat(responseObj).isInstanceOf(ResponseEntity.class);
            @SuppressWarnings("unchecked")
            ResponseEntity<ProblemDetail> re = (ResponseEntity<ProblemDetail>) responseObj;
            assertThat(re.getStatusCode().value()).isEqualTo(406);
            assertThat(re.getBody()).isNotNull();
            assertThat(re.getBody().getTitle()).isEqualTo(HttpStatus.NOT_ACCEPTABLE.getReasonPhrase());
        }
    }

    // ==================== ErrorResponseException 状态透传测试(B3) ====================

    @Nested
    @DisplayName("handleErrorResponseException - 带状态异常透传(此前落兜底 500 丢弃预期状态)")
    class HandleErrorResponseExceptionTests {

        @Test
        @DisplayName("默认包络:ResponseStatusException(409,reason) → code=409,message=reason")
        void responseStatus_unifiedEnvelope_carriesStatusAndReason() {
            ResponseStatusException ex = new ResponseStatusException(HttpStatus.CONFLICT, "资源已存在");

            Object responseObj = handler.handleErrorResponseException(ex, webRequest);

            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            BaseResponse<?> response = (BaseResponse<?>) responseObj;
            assertThat(response.getCode()).isEqualTo(409);
            assertThat(response.getMessage()).isEqualTo("资源已存在");
        }

        @Test
        @DisplayName("默认包络:reason 缺省回退 title(reason phrase)")
        void responseStatus_noReason_fallsBackToTitle() {
            ResponseStatusException ex = new ResponseStatusException(HttpStatus.CONFLICT);

            Object responseObj = handler.handleErrorResponseException(ex, webRequest);

            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            BaseResponse<?> response = (BaseResponse<?>) responseObj;
            assertThat(response.getCode()).isEqualTo(409);
            assertThat(response.getMessage()).isEqualTo("Conflict");
        }

        @Test
        @DisplayName("problemDetail 模式:honor 异常自带 status 与 body detail")
        void responseStatus_problemDetail_honorsStatusAndBody() {
            FacilityWebExceptionProperties pdProps = new FacilityWebExceptionProperties();
            pdProps.setUseProblemDetail(true);
            DefaultGlobalExceptionHandler pdHandler =
                    new DefaultGlobalExceptionHandler(pdProps, new MockEnvironment());
            ResponseStatusException ex = new ResponseStatusException(HttpStatus.CONFLICT, "资源已存在");

            Object responseObj = pdHandler.handleErrorResponseException(ex, webRequest);

            assertThat(responseObj).isInstanceOf(ResponseEntity.class);
            ResponseEntity<?> re = (ResponseEntity<?>) responseObj;
            assertThat(re.getStatusCode().value()).isEqualTo(409);
            assertThat(re.getBody()).isInstanceOf(ProblemDetail.class);
            ProblemDetail pd = (ProblemDetail) re.getBody();
            assertThat(pd.getDetail()).isEqualTo("资源已存在");
        }
    }

    // ==================== RateLimitExceededException 测试(429) ====================

    @Nested
    @DisplayName("handleRateLimitExceeded - 处理限流超限异常(429)")
    class HandleRateLimitExceededTests {

        @Test
        @DisplayName("默认包络：返回 ResponseEntity 429，Retry-After 头，body 为 BaseResponse code 429")
        void handleRateLimitExceeded_returns429WithRetryAfter() {
            RateLimitExceededException ex = new RateLimitExceededException("k", 3000);

            Object responseObj = handler.handleRateLimitExceeded(ex, webRequest);

            assertThat(responseObj).isInstanceOf(ResponseEntity.class);
            ResponseEntity<?> re = (ResponseEntity<?>) responseObj;
            assertThat(re.getStatusCode().value()).isEqualTo(429);
            assertThat(re.getHeaders().getFirst("Retry-After")).isEqualTo("3");
            assertThat(re.getBody()).isInstanceOf(BaseResponse.class);
            BaseResponse<?> body = (BaseResponse<?>) re.getBody();
            assertThat(body.getCode()).isEqualTo(429);
        }

        @Test
        @DisplayName("useProblemDetail=true：返回 ProblemDetail，status=429，含 Retry-After 头")
        void handleRateLimitExceeded_problemDetail() {
            FacilityWebExceptionProperties props = new FacilityWebExceptionProperties();
            props.setUseProblemDetail(true);
            DefaultGlobalExceptionHandler pdHandler =
                    new DefaultGlobalExceptionHandler(props, new MockEnvironment());
            RateLimitExceededException ex = new RateLimitExceededException("k", 3000);

            Object responseObj = pdHandler.handleRateLimitExceeded(ex, webRequest);

            assertThat(responseObj).isInstanceOf(ResponseEntity.class);
            ResponseEntity<?> re = (ResponseEntity<?>) responseObj;
            assertThat(re.getStatusCode().value()).isEqualTo(429);
            assertThat(re.getHeaders().getFirst("Retry-After")).isEqualTo("3");
            assertThat(re.getBody()).isInstanceOf(ProblemDetail.class);
            ProblemDetail pd = (ProblemDetail) re.getBody();
            assertThat(pd.getTitle()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
        }
    }

    // ==================== AsyncRequestTimeoutException 测试(503) ====================

    @Nested
    @DisplayName("handleAsyncRequestTimeout - 异步请求超时(503,此前落兜底 500)")
    class HandleAsyncRequestTimeoutTests {

        @Test
        @DisplayName("默认包络:返回 503 与内置默认文案")
        void asyncTimeout_returns503() {
            AsyncRequestTimeoutException ex = new AsyncRequestTimeoutException();

            Object responseObj = handler.handleAsyncRequestTimeout(ex, webRequest);

            assertThat(responseObj).isInstanceOf(BaseResponse.class);
            BaseResponse<?> response = (BaseResponse<?>) responseObj;
            assertThat(response.getCode()).isEqualTo(503);
            assertThat(response.getMessage()).isEqualTo("Request processing timed out");
        }

        @Test
        @DisplayName("problemDetail 模式:status=503,title=Service Unavailable")
        void asyncTimeout_problemDetail503() {
            FacilityWebExceptionProperties pdProps = new FacilityWebExceptionProperties();
            pdProps.setUseProblemDetail(true);
            DefaultGlobalExceptionHandler pdHandler =
                    new DefaultGlobalExceptionHandler(pdProps, new MockEnvironment());
            AsyncRequestTimeoutException ex = new AsyncRequestTimeoutException();

            Object responseObj = pdHandler.handleAsyncRequestTimeout(ex, webRequest);

            assertThat(responseObj).isInstanceOf(ResponseEntity.class);
            @SuppressWarnings("unchecked")
            ResponseEntity<ProblemDetail> re = (ResponseEntity<ProblemDetail>) responseObj;
            assertThat(re.getStatusCode().value()).isEqualTo(503);
            assertThat(re.getBody()).isNotNull();
            assertThat(re.getBody().getTitle()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());
        }
    }

    // ==================== 受保护工具方法 - 直接调用测试 ====================

    @Nested
    @DisplayName("protected 工具方法 - 直接调用覆盖分支")
    class ProtectedHelperMethodsTests {

        @Test
        @DisplayName("formatObjectError - FieldError 返回 field: message 格式")
        void formatObjectError_fieldError_prefixesFieldName() {
            FieldError err = new FieldError("target", "name", "不能为空");
            assertThat(handler.formatObjectError(err)).isEqualTo("name: 不能为空");
        }

        @Test
        @DisplayName("formatObjectError - 全局 ObjectError 仅返回消息")
        void formatObjectError_globalObjectError_returnsMessageOnly() {
            ObjectError err = new ObjectError("target", "全局校验失败");
            assertThat(handler.formatObjectError(err)).isEqualTo("全局校验失败");
        }

        @Test
        @DisplayName("getRequestURI - 非 ServletWebRequest 时使用 request.getDescription(false)")
        void getRequestURI_nonServletWebRequest_usesDescription() {
            WebRequest customRequest = Mockito.mock(WebRequest.class);
            Mockito.when(customRequest.getDescription(false)).thenReturn("uri=/custom/path");

            assertThat(handler.getRequestURI(customRequest)).isEqualTo("uri=/custom/path");
        }

        @Test
        @DisplayName("formatTrace - 空堆栈时仅返回类名与消息")
        void formatTrace_emptyStackTrace_returnsClassAndMessageOnly() {
            RuntimeException ex = new RuntimeException("boom");
            ex.setStackTrace(new StackTraceElement[0]);

            assertThat(handler.formatTrace(ex)).isEqualTo("java.lang.RuntimeException: boom");
        }

        @Test
        @DisplayName("formatTrace - 超过最大深度时截断并汇总剩余帧数")
        void formatTrace_deepStackTrace_truncatesAndSummarizesRemainder() {
            RuntimeException ex = new RuntimeException("deep");
            StackTraceElement[] fakeTrace = new StackTraceElement[15];
            for (int i = 0; i < fakeTrace.length; i++) {
                fakeTrace[i] = new StackTraceElement("Class" + i, "method" + i, "Class" + i + ".java", i);
            }
            ex.setStackTrace(fakeTrace);

            String trace = handler.formatTrace(ex);

            assertThat(trace).startsWith("java.lang.RuntimeException: deep\n");
            assertThat(trace).contains("... 5 more");
            long atLines = trace.lines().filter(l -> l.trim().startsWith("at ")).count();
            assertThat(atLines).isEqualTo(10);
        }

        @Test
        @DisplayName("buildProblemDetail - 畸形请求路径不抛 IAE,instance 省略(A1)")
        void buildProblemDetail_malformedUri_omitsInstance() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            // 容器 relaxedPathChars 等配置下原始路径可含 URI 非法字符(如花括号)——错误响应
            // 本身不得因 URI.create 抛 IllegalArgumentException 而失败
            req.setRequestURI("/api/{bad}");
            ServletWebRequest badRequest = new ServletWebRequest(req);

            ResponseEntity<ProblemDetail> re = handler.buildProblemDetail(
                    new RuntimeException("x"), HttpStatus.INTERNAL_SERVER_ERROR, badRequest);

            assertThat(re.getStatusCode().value()).isEqualTo(500);
            assertThat(re.getBody()).isNotNull();
            assertThat(re.getBody().getInstance()).isNull();
        }

        @Test
        @DisplayName("buildProblemDetail - 异常消息为 null 时 detail 使用 status reason phrase")
        void buildProblemDetail_nullMessage_usesReasonPhraseAsDetail() {
            Exception ex = new RuntimeException();

            ResponseEntity<ProblemDetail> re =
                    handler.buildProblemDetail(ex, HttpStatus.INTERNAL_SERVER_ERROR, webRequest);

            assertThat(re.getBody()).isNotNull();
            assertThat(re.getBody().getDetail()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        }

        @Test
        @DisplayName("shouldIncludeTrace - env 为 null 时返回 false")
        void shouldIncludeTrace_falseWhenEnvNull() {
            DefaultGlobalExceptionHandler h =
                    new DefaultGlobalExceptionHandler(new FacilityWebExceptionProperties(), null);
            assertThat(h.shouldIncludeTrace()).isFalse();
        }

        @Test
        @DisplayName("shouldIncludeTrace - props 为 null 时返回 false")
        void shouldIncludeTrace_falseWhenPropsNull() {
            DefaultGlobalExceptionHandler h =
                    new DefaultGlobalExceptionHandler(null, new MockEnvironment());
            assertThat(h.shouldIncludeTrace()).isFalse();
        }

        @Test
        @DisplayName("shouldIncludeTrace - 白名单为空时返回 false")
        void shouldIncludeTrace_falseWhenWhitelistEmpty() {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("dev");
            FacilityWebExceptionProperties props = new FacilityWebExceptionProperties();
            props.setIncludeTraceProfiles(List.of());
            DefaultGlobalExceptionHandler h = new DefaultGlobalExceptionHandler(props, env);
            assertThat(h.shouldIncludeTrace()).isFalse();
        }
    }
}
