package cn.code91.facility.web.interceptor;

import cn.code91.facility.web.session.SessionUserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Compatibility adapter for a Principal established by a host authentication filter before MVC.
 * The request filter owns cleanup, including async handoff and non-MVC failures. When used standalone,
 * this interceptor retains its old afterCompletion cleanup; standalone use does not cover async/filter paths.
 * This class does not authenticate a Principal and does not install a Spring Security context.
 */
public class SessionUserClearInterceptor implements HandlerInterceptor {

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getUserPrincipal() != null) SessionUserHolder.setUser(request.getUserPrincipal());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        if (request.getAttribute(cn.code91.facility.web.filter.FacilityRequestContextFilter.class.getName()) == null)
            SessionUserHolder.clear();
    }
}
