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
 * 构造注入本应用RateLimiter、默认容量/速率与显式failOpen政策，不依赖properties。
 * 三参数兼容构造器默认设施不可用时拒绝；自动装配在默认幂等拦截器前扣入口额度。
 * </p>
 * <p>
 * scope选择IP来源、宿主Principal或全局操作额度；DEFAULT保留旧空key/IP、固定key/GLOBAL选择。
 * 相同请求/操作/身份/政策的ASYNC完成复用扣费收据。详见 {@link RateLimit#scope()} 和ADR-0032。
 * </p>
 *
 * @author yvvb
 * @see RateLimit
 * @see RateLimiter
 * @since 1.0.0
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private final @jakarta.annotation.Nullable RateLimiter rateLimiter;
    private final long defaultCapacity;
    private final double defaultPermitsPerSecond;
    private final boolean failOpen;
    private final String receiptAttribute = RateLimitInterceptor.class.getName() + ".receipt." + java.util.UUID.randomUUID();
    private record Receipt(java.lang.reflect.Method method, String key, int cost, long capacity, double rate) { }


    /**
     * @param rateLimiter             限流器
     * @param defaultCapacity         {@link RateLimit#capacity()} 为 0 时使用的默认桶容量
     * @param defaultPermitsPerSecond {@link RateLimit#permitsPerSecond()} 为 0 时使用的默认填充速率
     */
    public RateLimitInterceptor(@jakarta.annotation.Nullable RateLimiter rateLimiter, long defaultCapacity, double defaultPermitsPerSecond) {
        this(rateLimiter, defaultCapacity, defaultPermitsPerSecond, false);
    }

    /**
     * @param rateLimiter host adapter, nullable so required annotation guards can reject absence
     * @param defaultCapacity positive default capacity
     * @param defaultPermitsPerSecond finite positive default refill rate
     * @param failOpen explicit infrastructure fallback; never hides invalid policy or quota rejection
     */
    public RateLimitInterceptor(@jakarta.annotation.Nullable RateLimiter rateLimiter, long defaultCapacity, double defaultPermitsPerSecond,
                                boolean failOpen) {
        if (defaultCapacity <= 0 || !(defaultPermitsPerSecond > 0) || !Double.isFinite(defaultPermitsPerSecond))
            throw new IllegalArgumentException("Invalid default rate-limit policy");
        this.rateLimiter = rateLimiter;
        this.defaultCapacity = defaultCapacity;
        this.defaultPermitsPerSecond = defaultPermitsPerSecond;
        this.failOpen = failOpen;
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
        String key = quotaKey(request, hm, ann);
        long capacity = ann.capacity() == 0 ? defaultCapacity : ann.capacity();
        double permitsPerSecond = ann.permitsPerSecond() == 0 ? defaultPermitsPerSecond : ann.permitsPerSecond();
        if (key.isBlank() || key.length() > 512 || key.chars().anyMatch(Character::isISOControl)
                || capacity <= 0 || ann.permits() <= 0 || ann.permits() > capacity
                || !(permitsPerSecond > 0) || !Double.isFinite(permitsPerSecond))
            throw new IllegalArgumentException("Invalid @RateLimit policy");
        Receipt receipt = new Receipt(hm.getMethod(), key, ann.permits(), capacity, permitsPerSecond);
        if (request.getDispatcherType() == jakarta.servlet.DispatcherType.ASYNC
                && receipt.equals(request.getAttribute(receiptAttribute))) return true;
        if (rateLimiter == null) {
            if (failOpen) return true;
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Rate limiter unavailable");
        }
        RateLimitResult result;
        try {
            result = java.util.Objects.requireNonNull(rateLimiter.acquire(key, ann.permits(), capacity, permitsPerSecond),
                    "Rate limiter returned no decision");
        } catch (IllegalArgumentException invalidPolicy) { throw invalidPolicy; }
        catch (RuntimeException unavailable) {
            if (failOpen) return true;
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Rate limiter unavailable", unavailable);
        }
        if (!result.allowed()) {
            throw new RateLimitExceededException(key, result.retryAfterMillis());
        }
        request.setAttribute(receiptAttribute, receipt);
        return true;
    }

    private static String quotaKey(HttpServletRequest request, HandlerMethod handler, RateLimit annotation) {
        if (!annotation.key().isEmpty() && annotation.key().isBlank())
            throw new IllegalArgumentException("Invalid @RateLimit operation alias");
        String operation = annotation.key().isEmpty()
                ? "m:" + org.springframework.util.ClassUtils.getUserClass(handler.getBeanType()).getName() + "#"
                    + handler.getMethod().getName() + "(" + java.util.Arrays.stream(handler.getMethod().getParameterTypes())
                    .map(Class::getName).collect(java.util.stream.Collectors.joining(",")) + ")"
                : "a:" + annotation.key();
        RateLimit.Scope scope = annotation.scope() == RateLimit.Scope.DEFAULT
                ? (annotation.key().isEmpty() ? RateLimit.Scope.IP : RateLimit.Scope.GLOBAL) : annotation.scope();
        String subject = switch (scope) {
            case PRINCIPAL -> {
                java.security.Principal principal = request.getUserPrincipal();
                String name = principal == null ? null : principal.getName();
                if (name == null || name.isBlank() || name.length() > 128 || name.chars().anyMatch(Character::isISOControl))
                    throw new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.FORBIDDEN, "Verified quota identity required");
                yield name;
            }
            case IP -> RequestUtil.getClientIp(request);
            case GLOBAL -> "";
            default -> throw new IllegalStateException("Unresolved rate-limit scope");
        };
        // Length delimiting prevents operation/identity delimiters from aliasing another quota.
        return scope.name() + "|" + operation.length() + ":" + operation + "|" + subject;
    }

}
