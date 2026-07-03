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
 * @author yvvb
 * @since 2.0.0
 */
public class AccessLogInterceptor implements HandlerInterceptor {

    private static final String ATTR_START_TIME = "accessLog_startTime";

    @SuppressWarnings("unused") // stored for future use (slow-request threshold, header logging)
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

        LogUtil.info("[ACCESS] {} {} {} {}ms {}",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                duration,
                clientIp);
    }
}
