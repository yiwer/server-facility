package cn.code91.facility.web.idempotency;

import cn.code91.facility.idempotency.IdempotencyRecord;
import cn.code91.facility.idempotency.IdempotencyStore;
import cn.code91.facility.log.LogUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.annotation.Nullable;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Optional;

/**
 * <b>方法级幂等拦截器</b>
 * <p>
 * 读取 Controller 方法上的 {@link Idempotent} 注解，委托构造注入的 {@code IdempotencyStore}
 * 提供旧 HTTP 响应重放适配：同一 key 可返回已保存的状态、Content-Type 与正文。
 * 这不是事务或跨身份的 exactly-once 保证。未标注 {@link Idempotent} 的方法、非 {@link HandlerMethod} 的 handler（如静态资源）
 * 一律放行。
 * </p>
 *
 * <h3>{@link #preHandle} 状态机：</h3>
 * <ul>
 *     <li>缺失/空白 key 请求头 —— HTTP 400，方法体不执行；</li>
 *     <li>key 已有 {@link IdempotencyRecord.State#DONE} 记录 —— 直接写回首次响应
 *     （状态码/Content-Type/body），方法体不执行；</li>
 *     <li>key 已有 {@link IdempotencyRecord.State#PROCESSING} 记录，或
 *     {@code IdempotencyStore#tryBegin} 竞态落败,或存储容量 fail-closed 拒绝(F8) —— HTTP 409，方法体不执行；</li>
 *     <li>其余情况 —— 占位成功，放行方法体执行，key 记入请求属性供 {@link #afterCompletion}
 *     使用。</li>
 * </ul>
 * <p>
 * 只有由 {@link IdempotencyFilter} 在现有 claim 成功后开启的有界捕获才可写入终态。
 * 超限、写失败、Servlet 异步移交或 MVC 已解析的异常均不保存不完整副本。
 * 捕获不延迟发送；未装配 filter 时记 WARN 并跳过保存，旧的手工无界 wrapper 不再作为旁路。
 * 未保存结果的旧 claim 保留至原 TTL，这不证明到期重试安全；授权、保存资格与恢复政策由业务适配负责。
 * </p>
 *
 * @author yvvb
 * @see Idempotent
 * @see IdempotencyFilter
 * @since 1.0.0
 */
public class IdempotencyInterceptor implements HandlerInterceptor {

    private static final String ATTR_KEY = "facility.idempotency.key";
    private static final String ATTR_TTL = "facility.idempotency.ttl";

    private final IdempotencyStore store;
    private final long defaultTtlMillis;

    /**
     * @param store            幂等存储 SPI
     * @param defaultTtlMillis {@link Idempotent#ttlSeconds()} 为 0 时使用的默认 TTL（毫秒）
     */
    public IdempotencyInterceptor(IdempotencyStore store, long defaultTtlMillis) {
        this.store = java.util.Objects.requireNonNull(store, "store");
        this.defaultTtlMillis = defaultTtlMillis;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        Idempotent ann = hm.getMethodAnnotation(Idempotent.class);
        if (ann == null) {
            return true;
        }
        String key = request.getHeader(ann.headerName());
        if (key == null || key.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing idempotency key header: " + ann.headerName());
            return false;
        }
        Optional<IdempotencyRecord> found = store.find(key);
        if (found.isPresent()) {
            IdempotencyRecord record = found.get();
            if (record.state() == IdempotencyRecord.State.DONE) {
                writeCached(response, record);
                return false;
            }
            response.sendError(HttpServletResponse.SC_CONFLICT, "Duplicate request in progress");
            return false;
        }
        long ttl = ann.ttlSeconds() > 0 ? ann.ttlSeconds() * 1000 : defaultTtlMillis;
        if (!store.tryBegin(key, ttl)) {
            response.sendError(HttpServletResponse.SC_CONFLICT, "Duplicate request in progress");
            return false;
        }
        request.setAttribute(ATTR_KEY, key);
        request.setAttribute(ATTR_TTL, ttl);   // afterCompletion 写 DONE 记录时复用同一 ttl(含 @Idempotent.ttlSeconds 覆盖)
        var capture = org.springframework.web.util.WebUtils.getNativeResponse(response, BoundedResponseCapture.class);
        if (capture != null) capture.start();
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex)
            throws IOException {
        String key = (String) request.getAttribute(ATTR_KEY);
        if (key == null) {
            return;
        }
        if (ex != null || request.getAttribute(org.springframework.web.servlet.DispatcherServlet.EXCEPTION_ATTRIBUTE) != null) {
            return;
        }
        var capture = org.springframework.web.util.WebUtils.getNativeResponse(response, BoundedResponseCapture.class);
        if (capture != null) {
            var body = capture.body();
            if (body.isPresent()) {
                long ttl = (long) request.getAttribute(ATTR_TTL);
                store.complete(key, IdempotencyRecord.done(response.getStatus(), response.getContentType(), body.get(),
                        System.currentTimeMillis() + ttl));
            }
        } else {
            // 配置故障信号:IdempotencyFilter 未装配或顺序错乱,响应未被包装——无法捕获响应体,
            // 本次结果不落 DONE 记录;占位 PROCESSING 存续至 TTL 到期(期间同 key 一律 409)。
            LogUtil.warn("[Idempotency] response has no bounded response capture "
                    + "(IdempotencyFilter missing or misordered); key={} left PROCESSING until TTL expiry, "
                    + "response not cached", key);
        }
    }

    private void writeCached(HttpServletResponse response, IdempotencyRecord record) throws IOException {
        response.setStatus(record.statusCode());
        if (record.contentType() != null) {
            response.setContentType(record.contentType());
        }
        response.getOutputStream().write(record.body());
        response.flushBuffer();
    }
}
