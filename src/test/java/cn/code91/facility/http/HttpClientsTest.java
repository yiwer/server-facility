package cn.code91.facility.http;

import cn.code91.facility.context.SpringContextHolder;
import cn.code91.facility.context.SpringContextHolderTestSupport;
import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * RED→GREEN 覆盖 {@link HttpClients}:2xx 反序列化、4xx/5xx 状态错误、请求体序列化、
 * 自定义 headers 透传、无 bean 时 {@code RestClient.create()} 默认路径 + 网络异常映射、
 * 私有构造契约。
 */
@DisplayName("HttpClients - HTTP client 门面(RestClient 委托 + 4xx/5xx/网络异常 → Result.err)")
class HttpClientsTest {

    @AfterEach
    void cleanup() {
        // 毒化清理:refresh 过的 GenericApplicationContext 若不清理会串到后续测试类
        // (P6-T5 事故根因),经 context 包测试桥调用包私有 clear()。
        SpringContextHolderTestSupport.reset();
    }

    /** 绑定 MockRestServiceServer 到一个 RestClient,并把该 client 注册为容器 bean 供 HttpClients.restClient() 取用。 */
    private MockRestServiceServer bindMockServerAndRegisterClient() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();

        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.getBeanFactory().registerSingleton("restClient", client);
        ctx.refresh();
        SpringContextHolder.setApplicationContextManually(ctx);

        return server;
    }

    @Test
    @DisplayName("GET 2xx → Result.ok 反序列化对象")
    void get_2xx_returnsOk() {
        MockRestServiceServer server = bindMockServerAndRegisterClient();
        server.expect(requestTo("http://localhost/users/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":1,\"name\":\"Alice\"}", MediaType.APPLICATION_JSON));

        Result<TestUser, WrappedError> result = HttpClients.get("http://localhost/users/1", TestUser.class);

        assertThat(result.isOk()).isTrue();
        assertThat(result.get().id()).isEqualTo(1);
        assertThat(result.get().name()).isEqualTo("Alice");
        server.verify();
    }

    @Test
    @DisplayName("GET 404 → Result.err,WrappedError 携带状态码 404")
    void get_4xx_returnsErrWithStatus() {
        MockRestServiceServer server = bindMockServerAndRegisterClient();
        server.expect(requestTo("http://localhost/users/999"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        Result<TestUser, WrappedError> result = HttpClients.get("http://localhost/users/999", TestUser.class);

        assertThat(result.isErr()).isTrue();
        WrappedError error = result.getErr();
        assertThat(error.getErrorType()).isEqualTo(FacilityErrorType.HTTP_STATUS_ERROR);
        assertThat(error.getArgs()[0]).isEqualTo(404);
        server.verify();
    }

    @Test
    @DisplayName("GET 500 → Result.err,WrappedError 携带状态码 500")
    void get_5xx_returnsErr() {
        MockRestServiceServer server = bindMockServerAndRegisterClient();
        server.expect(requestTo("http://localhost/boom"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        Result<TestUser, WrappedError> result = HttpClients.get("http://localhost/boom", TestUser.class);

        assertThat(result.isErr()).isTrue();
        WrappedError error = result.getErr();
        assertThat(error.getErrorType()).isEqualTo(FacilityErrorType.HTTP_STATUS_ERROR);
        assertThat(error.getArgs()[0]).isEqualTo(500);
        server.verify();
    }

    @Test
    @DisplayName("POST 2xx → 序列化请求体并返回反序列化对象")
    void post_2xx_serializesBodyAndReturnsOk() {
        MockRestServiceServer server = bindMockServerAndRegisterClient();
        server.expect(requestTo("http://localhost/users"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"id\":2,\"name\":\"Bob\"}"))
                .andRespond(withSuccess("{\"id\":2,\"name\":\"Bob\"}", MediaType.APPLICATION_JSON));

        Result<TestUser, WrappedError> result =
                HttpClients.post("http://localhost/users", new TestUser(2, "Bob"), TestUser.class);

        assertThat(result.isOk()).isTrue();
        assertThat(result.get().name()).isEqualTo("Bob");
        server.verify();
    }

    @Test
    @DisplayName("PUT 2xx → 序列化请求体并返回反序列化对象")
    void put_2xx_serializesBodyAndReturnsOk() {
        MockRestServiceServer server = bindMockServerAndRegisterClient();
        server.expect(requestTo("http://localhost/users/3"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"id\":3,\"name\":\"Carol\"}"))
                .andRespond(withSuccess("{\"id\":3,\"name\":\"Carol\"}", MediaType.APPLICATION_JSON));

        Result<TestUser, WrappedError> result =
                HttpClients.put("http://localhost/users/3", new TestUser(3, "Carol"), TestUser.class);

        assertThat(result.isOk()).isTrue();
        assertThat(result.get().name()).isEqualTo("Carol");
        server.verify();
    }

    @Test
    @DisplayName("DELETE 2xx → Result.ok(无值)")
    void delete_2xx_returnsOk() {
        MockRestServiceServer server = bindMockServerAndRegisterClient();
        server.expect(requestTo("http://localhost/users/4"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        Result<Void, WrappedError> result = HttpClients.delete("http://localhost/users/4");

        assertThat(result.isOk()).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("GET 带自定义 headers → 请求携带该 header")
    void get_withHeaders_sendsHeaders() {
        MockRestServiceServer server = bindMockServerAndRegisterClient();
        server.expect(requestTo("http://localhost/secure"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Trace-Id", "abc-123"))
                .andRespond(withSuccess("{\"id\":5,\"name\":\"Dave\"}", MediaType.APPLICATION_JSON));

        Result<TestUser, WrappedError> result =
                HttpClients.get("http://localhost/secure", Map.of("X-Trace-Id", "abc-123"), TestUser.class);

        assertThat(result.isOk()).isTrue();
        assertThat(result.get().name()).isEqualTo("Dave");
        server.verify();
    }

    @Test
    @DisplayName("无 RestClient bean → restClient() 走 RestClient.create() 默认路径;目标不可达 → HTTP_SEND_AND_PARSE_ERROR")
    void noBean_networkUnreachable_returnsErrWithSendParseError() {
        // 不注册任何 bean:restClient() 内部 SpringContextHolder.getBean 未命中,走 orElseGet(RestClient::create)。
        // 指向 loopback 上一个必然拒绝连接的端口,快速触发非 RestClientResponseException 的网络异常。
        String unreachableUrl = "http://127.0.0.1:1/";

        Result<TestUser, WrappedError> result = HttpClients.get(unreachableUrl, TestUser.class);

        assertThat(result.isErr()).isTrue();
        WrappedError error = result.getErr();
        assertThat(error.getErrorType()).isEqualTo(FacilityErrorType.HTTP_SEND_AND_PARSE_ERROR);
        assertThat(error.getArgs()[0]).isEqualTo(unreachableUrl);
    }

    @Test
    @DisplayName("私有构造器不可实例化(工具类契约)")
    void privateConstructor_throws() throws Exception {
        var ctor = HttpClients.class.getDeclaredConstructor();
        ctor.setAccessible(true);

        assertThatThrownBy(ctor::newInstance)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    private record TestUser(int id, String name) {
    }
}
