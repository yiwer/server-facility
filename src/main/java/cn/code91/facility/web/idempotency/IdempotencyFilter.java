package cn.code91.facility.web.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Response capture never delays ordinary downloads or SSE (ADR-0028). */
public class IdempotencyFilter extends OncePerRequestFilter {
    private final int maxCaptureBytes;

    public IdempotencyFilter() { this(1024 * 1024); }

    /** Positive per-response capture budget; ordinary responses allocate no body buffer. */
    public IdempotencyFilter(int maxCaptureBytes) {
        if (maxCaptureBytes <= 0) throw new IllegalArgumentException("maxCaptureBytes must be positive");
        this.maxCaptureBytes = maxCaptureBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (org.springframework.web.util.WebUtils.getNativeResponse(response, BoundedResponseCapture.class) != null) {
            chain.doFilter(request, response);
            return;
        }
        var capture = new BoundedResponseCapture(response, maxCaptureBytes);
        try {
            chain.doFilter(request, capture);
            capture.finish();
        } finally {
            capture.discard();
        }
    }
}
