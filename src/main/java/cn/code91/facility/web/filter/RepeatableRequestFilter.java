package cn.code91.facility.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;
import java.util.Objects;

/**
 * Opt-in synchronous request buffering. Selection uses media types and path patterns.
 * Actual body bytes, including chunked requests, are bounded. Local overflow becomes
 * a standard 413 exception for the enclosing HTTP error filter; downstream exceptions
 * and input I/O failures retain their original meaning. Container streams are borrowed.
 */
public class RepeatableRequestFilter extends OncePerRequestFilter {
    private final FacilityWebRepeatableRequestProperties props;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RepeatableRequestFilter(FacilityWebRepeatableRequestProperties props) {
        this.props = Objects.requireNonNull(props, "props");
        if (props.getMaxBodyBytes() <= 0) throw new IllegalArgumentException("maxBodyBytes must be positive; disable repeatable-request instead");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (WebUtils.getNativeRequest(request, RepeatableRequestWrapper.class) != null || !shouldWrap(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        RepeatableRequestWrapper wrapped;
        try {
            wrapped = new RepeatableRequestWrapper(request, props.getMaxBodyBytes());
        } catch (PayloadTooLargeException failure) {
            throw new ErrorResponseException(HttpStatus.PAYLOAD_TOO_LARGE, failure);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException failure) {
            throw new ErrorResponseException(HttpStatus.BAD_REQUEST, failure);
        }
        filterChain.doFilter(wrapped, response);
    }

    private boolean shouldWrap(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (matches(props.getExcludePaths(), uri)) return false;
        if (!matches(props.getIncludePaths(), uri)) return false;
        String contentType = request.getContentType();
        if (contentType == null) return false;
        MediaType actual;
        try { actual = MediaType.parseMediaType(contentType); }
        catch (InvalidMediaTypeException failure) { throw new ErrorResponseException(HttpStatus.BAD_REQUEST, failure); }
        var types = props.getIncludeContentTypes();
        if (types == null || types.isEmpty()) return true;
        for (String type : types) {
            // Preserve historical text/ configuration while rejecting subtype prefix lookalikes.
            MediaType selected = MediaType.parseMediaType(type.endsWith("/") ? type + "*" : type);
            if (selected.includes(actual)) return true;
        }
        return false;
    }

    private boolean matches(List<String> patterns, String uri) {
        if (patterns == null) return false;
        for (String pattern : patterns) if (pathMatcher.match(pattern, uri)) return true;
        return false;
    }
}
