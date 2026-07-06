package cn.code91.facility.web.idempotency;

import cn.code91.facility.idempotency.IdempotencyRecord;
import cn.code91.facility.idempotency.IdempotencyStore;
import cn.code91.facility.log.LogUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Optional;

/**
 * <b>方法级幂等拦截器</b>
 * <p>
 * 读取 Controller 方法上的 {@link Idempotent} 注解，委托构造注入的 {@code IdempotencyStore}
 * 实现完整幂等语义：同一 key 重复提交返回首次处理的响应本身（而非仅做"防重复提交拒绝"式的
 * 409）。未标注 {@link Idempotent} 的方法、非 {@link HandlerMethod} 的 handler（如静态资源）
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
 * {@link #afterCompletion} 仅在方法体正常完成（未抛异常）且响应为
 * {@link ContentCachingResponseWrapper}（由 {@code IdempotencyFilter} 包装，可读取完整响应体）
 * 时才写入终态记录；异常场景刻意不缓存——占位记录到期后允许重试，而非把一次失败永久固化为
 * "首次响应"。
 * 响应未被包装(Filter 未装配/顺序错)时记 WARN 并跳过缓存——该场景是配置故障信号。
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
        this.store = store;
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
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws IOException {
        String key = (String) request.getAttribute(ATTR_KEY);
        if (key == null) {
            return;
        }
        if (ex != null) {
            return;
        }
        if (response instanceof ContentCachingResponseWrapper wrapper) {
            byte[] body = wrapper.getContentAsByteArray();
            long ttl = (long) request.getAttribute(ATTR_TTL);   // 与 preHandle 占位同一 ttl,尊重 @Idempotent.ttlSeconds
            store.complete(key, IdempotencyRecord.done(wrapper.getStatus(), wrapper.getContentType(), body,
                    System.currentTimeMillis() + ttl));
        } else {
            // 配置故障信号:IdempotencyFilter 未装配或顺序错乱,响应未被包装——无法捕获响应体,
            // 本次结果不落 DONE 记录;占位 PROCESSING 存续至 TTL 到期(期间同 key 一律 409)。
            LogUtil.warn("[Idempotency] response is not ContentCachingResponseWrapper "
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
