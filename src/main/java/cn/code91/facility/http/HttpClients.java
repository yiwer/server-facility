package cn.code91.facility.http;

import cn.code91.facility.context.SpringContextHolder;
import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.function.Supplier;

/**
 * <b>HTTP client 静态门面</b>
 * <p>
 * 委托 {@link SpringContextHolder#getBean(Class)} 查找容器中的 {@link RestClient} bean 并转发调用；
 * 容器中不存在 {@code RestClient} bean 时降级为默认的 {@link RestClient#create()} 实例。
 * 每个方法返回 {@link Result}，而非抛出异常：
 * </p>
 * <ul>
 *     <li>{@link RestClientResponseException}（4xx/5xx 响应状态）→
 *         {@link FacilityErrorType#HTTP_STATUS_ERROR}，第一个参数为 HTTP 状态码</li>
 *     <li>其他异常（网络故障、超时、序列化异常等）→
 *         {@link FacilityErrorType#HTTP_SEND_AND_PARSE_ERROR}，第一个参数为请求 URL</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * Result<User, WrappedError> result = HttpClients.get("https://api.example.com/users/1", User.class);
 * result.ifOk(user -> System.out.println(user.name()));
 *
 * Result<Void, WrappedError> deleted = HttpClients.delete("https://api.example.com/users/1");
 * }</pre>
 *
 * @author yvvb
 * @since 1.0.0
 * @deprecated New application code should inject the Boot-managed RestClient.Builder
 * into typed adapters. This compatibility facade retains its historical static lookup,
 * unconfigured fallback and coarse failure mapping; it is not the bounded adapter policy.
 */
@Deprecated(since = "0.1.0", forRemoval = false)
public final class HttpClients {

    private HttpClients() {
        throw new UnsupportedOperationException();
    }

    /**
     * 解析容器中的 {@link RestClient} bean；不存在时降级为默认实例。
     *
     * @return 容器管理的 {@link RestClient} bean，或 {@link RestClient#create()} 默认实例
     */
    private static RestClient restClient() {
        return SpringContextHolder.getBean(RestClient.class).orElseGet(RestClient::create);
    }

    /**
     * 统一执行 + 异常映射：4xx/5xx 映射为 {@link FacilityErrorType#HTTP_STATUS_ERROR}（携带状态码），
     * 其他异常映射为 {@link FacilityErrorType#HTTP_SEND_AND_PARSE_ERROR}（携带请求 URL）。
     */
    private static <T> Result<T, WrappedError> execute(String url, Supplier<T> action) {
        try {
            return Result.ok(action.get());
        } catch (RestClientResponseException e) {
            return Result.err(WrappedError.of(FacilityErrorType.HTTP_STATUS_ERROR, e, e.getStatusCode().value()));
        } catch (Exception e) {
            return Result.err(WrappedError.of(FacilityErrorType.HTTP_SEND_AND_PARSE_ERROR, e, url));
        }
    }

    /**
     * 发送 GET 请求
     *
     * @param url  请求地址
     * @param type 期望的响应体类型
     * @param <T>  响应体类型
     * @return 反序列化后的响应体；4xx/5xx 或其他异常时返回 {@link Result#err}
     */
    public static <T> Result<T, WrappedError> get(String url, Class<T> type) {
        return execute(url, () -> restClient().get().uri(url).retrieve().body(type));
    }

    /**
     * 发送带自定义请求头的 GET 请求
     *
     * @param url     请求地址
     * @param headers 追加的请求头（name → value）
     * @param type    期望的响应体类型
     * @param <T>     响应体类型
     * @return 反序列化后的响应体；4xx/5xx 或其他异常时返回 {@link Result#err}
     */
    public static <T> Result<T, WrappedError> get(String url, Map<String, String> headers, Class<T> type) {
        return execute(url, () -> restClient().get().uri(url).headers(h -> headers.forEach(h::add)).retrieve().body(type));
    }

    /**
     * 发送 POST 请求
     *
     * @param url  请求地址
     * @param body 请求体（由容器 {@link RestClient} 的消息转换器序列化）
     * @param type 期望的响应体类型
     * @param <T>  响应体类型
     * @return 反序列化后的响应体；4xx/5xx 或其他异常时返回 {@link Result#err}
     */
    public static <T> Result<T, WrappedError> post(String url, Object body, Class<T> type) {
        return execute(url, () -> restClient().post().uri(url).body(body).retrieve().body(type));
    }

    /**
     * 发送 PUT 请求
     *
     * @param url  请求地址
     * @param body 请求体（由容器 {@link RestClient} 的消息转换器序列化）
     * @param type 期望的响应体类型
     * @param <T>  响应体类型
     * @return 反序列化后的响应体；4xx/5xx 或其他异常时返回 {@link Result#err}
     */
    public static <T> Result<T, WrappedError> put(String url, Object body, Class<T> type) {
        return execute(url, () -> restClient().put().uri(url).body(body).retrieve().body(type));
    }

    /**
     * 发送 DELETE 请求
     *
     * @param url 请求地址
     * @return 成功时为无值的 {@link Result#ok()}；4xx/5xx 或其他异常时返回 {@link Result#err}
     */
    public static Result<Void, WrappedError> delete(String url) {
        return execute(url, () -> {
            restClient().delete().uri(url).retrieve().toBodilessEntity();
            return null;
        });
    }
}
