package cn.code91.facility.web.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Streaming response adapter (ADR-0028). Ordinary downloads, SSE and async responses
 * are passed through. The interceptor may select a finite synchronous response before
 * output is accessed, retaining up to the positive budget while every write goes to the container.
 * Overflow, I/O failure or async handoff discards the copy. No container stream is closed,
 * no body is copied again at filter exit, and no retry is performed here.
 */
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
            if (!request.isAsyncStarted()
                    && request.getAttribute(org.springframework.web.servlet.DispatcherServlet.EXCEPTION_ATTRIBUTE) == null) {
                capture.complete();
            }
        } finally {
            capture.discard();
        }
    }
}
