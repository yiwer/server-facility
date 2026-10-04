package cn.code91.facility.web.interceptor;

import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Emits one standard SLF4J event using method, matched route template, status and duration.
 * Raw URI, query, headers, body, client address and exception details are not log fields.
 * This diagnostic event is not an audit record. Hosts own the logging backend and sinks.
 */
public class AccessLogInterceptor implements HandlerInterceptor {
    private static final Logger LOG = LoggerFactory.getLogger(AccessLogInterceptor.class);
    private static final String ATTR_START_TIME = "accessLog_startTime";
    private static final java.util.Set<String> METHODS = java.util.Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE", "CONNECT");
    private final FacilityWebAccessLogProperties props;

    public AccessLogInterceptor(FacilityWebAccessLogProperties props) {
        this.props = java.util.Objects.requireNonNull(props, "props");
    }

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(ATTR_START_TIME, System.currentTimeMillis());
        return true;
    }

    @Override public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) {
        Object started = request.getAttribute(ATTR_START_TIME);
        long duration = started instanceof Long time ? Math.max(0, System.currentTimeMillis() - time) : -1;
        String method = METHODS.contains(java.util.Objects.toString(request.getMethod(), "")) ? request.getMethod() : "UNKNOWN";
        Object matched = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = matched instanceof String value && value.length() <= 256
                && value.codePoints().noneMatch(Character::isISOControl) ? value : "unmatched";
        long slowThreshold = props.getSlowThresholdMillis();
        try {
            if (slowThreshold > 0 && duration >= slowThreshold) {
                LOG.warn("[ACCESS] {} {} {} {}ms slow", method, route, response.getStatus(), duration);
            } else {
                LOG.info("[ACCESS] {} {} {} {}ms", method, route, response.getStatus(), duration);
            }
        } catch (RuntimeException ignored) {
            // The response/business failure remains authoritative when its diagnostic sink is unavailable.
        }
    }
}
