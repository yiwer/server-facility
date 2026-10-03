package cn.code91.facility.web.ratelimit;

import cn.code91.facility.ratelimit.RateLimitResult;
import cn.code91.facility.ratelimit.RateLimiter;
import cn.code91.facility.web.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * <b>方法级限流拦截器</b>
 * <p>
 * 读取 Controller 方法上的 {@link RateLimit} 注解，委托构造注入的 {@link RateLimiter}
 * 执行限流判定；未标注 {@link RateLimit} 的方法、非 {@link HandlerMethod} 的 handler
 * （如静态资源）一律放行。超限时抛出 {@link RateLimitExceededException}，交由上层
 * （如 {@code AbstractGlobalExceptionHandler}）转换为 HTTP 429 响应。
 * </p>
 * <p>
 * 构造仅注入 {@link RateLimiter} 与两个默认值（{@code defaultCapacity} /
 * {@code defaultPermitsPerSecond}），不直接依赖 properties 类——装配层
 * （{@code FacilityRateLimitAutoConfiguration}）从 {@code FacilityRateLimitProperties}
 * 取值后传入这两个默认值。
 * </p>
 * <p>
 * 空 {@link RateLimit#key()} 时 key 含 {@code RequestUtil.getClientIp} 的来源快照；
 * 默认连接 peer，显式可信代理配置才解析转发链。该值不是认证身份，详见 {@link RateLimit#key()}。
 * </p>
 *
 * @author yvvb
 * @see RateLimit
 * @see RateLimiter
 * @since 1.0.0
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;
    private final long defaultCapacity;
    private final double defaultPermitsPerSecond;

    /**
     * @param rateLimiter             限流器
     * @param defaultCapacity         {@link RateLimit#capacity()} 为 0 时使用的默认桶容量
     * @param defaultPermitsPerSecond {@link RateLimit#permitsPerSecond()} 为 0 时使用的默认填充速率
     */
    public RateLimitInterceptor(RateLimiter rateLimiter, long defaultCapacity, double defaultPermitsPerSecond) {
        this.rateLimiter = rateLimiter;
        this.defaultCapacity = defaultCapacity;
        this.defaultPermitsPerSecond = defaultPermitsPerSecond;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        RateLimit ann = hm.getMethodAnnotation(RateLimit.class);
        if (ann == null) {
            return true;
        }
        String key = ann.key().isEmpty()
                ? hm.getBeanType().getSimpleName() + "#" + hm.getMethod().getName() + "#" + RequestUtil.getClientIp(request)
                : ann.key();
        long capacity = ann.capacity() > 0 ? ann.capacity() : defaultCapacity;
        double permitsPerSecond = ann.permitsPerSecond() > 0 ? ann.permitsPerSecond() : defaultPermitsPerSecond;
        RateLimitResult result = rateLimiter.acquire(key, ann.permits(), capacity, permitsPerSecond);
        if (!result.allowed()) {
            throw new RateLimitExceededException(key, result.retryAfterMillis());
        }
        return true;
    }
}
