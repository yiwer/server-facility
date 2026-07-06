package cn.code91.facility.web.interceptor;

import cn.code91.facility.log.LogUtil;
import cn.code91.facility.web.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * <b>访问日志拦截器</b>
 * <p>
 * 记录每个请求的方法、URI、状态码、耗时和客户端IP。
 * 通过 {@link cn.code91.facility.autoconfigure.FacilityWebAutoConfiguration} 注册。
 * </p>
 *
 * <h3>日志输出示例：</h3>
 * <pre>
 * [ACCESS] GET /api/users 200 35ms 192.168.1.100
 * </pre>
 *
 * <p>耗时 ≥ {@code slow-threshold-millis}(>0 生效,默认 1000)时升 WARN 并追加 {@code slow} 标记;
 * 0 = 禁用。</p>
 *
 * <p><b>⚠️ 安全:</b>日志中的客户端 IP 来自 {@link RequestUtil#getClientIp},该方法无条件信任
 * 可被客户端伪造的 {@code X-Forwarded-For} / {@code X-Real-IP} 代理头——公网直连(前面没有
 * 覆写 XFF 的受信反代)部署下,访问日志中的 IP 不可作为审计/取证依据。</p>
 *
 * @author yvvb
 * @since 2.0.0
 */
public class AccessLogInterceptor implements HandlerInterceptor {

    private static final String ATTR_START_TIME = "accessLog_startTime";

    private final FacilityWebAccessLogProperties props;

    /**
     * @param props access-log configuration knobs
     */
    public AccessLogInterceptor(FacilityWebAccessLogProperties props) {
        this.props = props;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        request.setAttribute(ATTR_START_TIME, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute(ATTR_START_TIME);
        long duration = startTime != null ? System.currentTimeMillis() - startTime : -1;
        String clientIp = RequestUtil.getClientIp(request);

        long slowThreshold = props.getSlowThresholdMillis();
        if (slowThreshold > 0 && duration >= slowThreshold) {
            LogUtil.warn("[ACCESS] {} {} {} {}ms {} slow",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), duration, clientIp);
        } else {
            LogUtil.info("[ACCESS] {} {} {} {}ms {}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), duration, clientIp);
        }
    }
}
