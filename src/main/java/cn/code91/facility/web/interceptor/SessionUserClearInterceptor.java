package cn.code91.facility.web.interceptor;

import cn.code91.facility.web.session.SessionUserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * <b>请求结束清理 {@link SessionUserHolder} ThreadLocal</b>（phase-13，RV2-08）
 *
 * <p>线程池复用下，若不清理 {@code ThreadLocal} 会导致用户身份在请求间串号 + 内存泄漏。
 * 本 interceptor 在 {@code afterCompletion}（无论是否异常）兜底清理。由
 * {@code FacilityWebAutoConfiguration} 注册。</p>
 *
 * @since phase-13
 */
public class SessionUserClearInterceptor implements HandlerInterceptor {

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        SessionUserHolder.clear();
    }
}
