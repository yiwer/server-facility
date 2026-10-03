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
 * the first response is not drained again at filter exit, and no retry is performed here.
 * A qualified replay is emitted once after successful inner filter completion (ADR-0035).
 */
public class IdempotencyFilter extends OncePerRequestFilter {
    private final int maxCaptureBytes;
    private final int maxRequestBytes;

    public IdempotencyFilter() { this(1024 * 1024); }

    /** Positive per-response capture budget; ordinary responses allocate no body buffer. */
    public IdempotencyFilter(int maxCaptureBytes) {
        this(maxCaptureBytes, 1024 * 1024);
    }

    /** Independent positive budgets; neither body is eagerly captured on ordinary requests. */
    public IdempotencyFilter(int maxCaptureBytes, int maxRequestBytes) {
        if (maxCaptureBytes <= 0) throw new IllegalArgumentException("maxCaptureBytes must be positive");
        if (maxRequestBytes <= 0) throw new IllegalArgumentException("maxRequestBytes must be positive");
        this.maxCaptureBytes = maxCaptureBytes;
        this.maxRequestBytes = maxRequestBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (org.springframework.web.util.WebUtils.getNativeRequest(request, ReplayRequest.class) == null)
            request = new ReplayRequest(request, maxRequestBytes);
        if (org.springframework.web.util.WebUtils.getNativeResponse(response, BoundedResponseCapture.class) != null) {
            chain.doFilter(request, response);
            return;
        }
        var capture = new BoundedResponseCapture(response, maxCaptureBytes);
        boolean successful = false;
        Throwable primary = null;
        try {
            chain.doFilter(request, capture);
            if (!request.isAsyncStarted()
                    && request.getAttribute(org.springframework.web.servlet.DispatcherServlet.EXCEPTION_ATTRIBUTE) == null) {
                capture.complete();
            }
            successful = !request.isAsyncStarted();
        } catch (IOException | ServletException | RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            try { IdempotencyInterceptor.finishRequest(request, capture, successful); }
            catch (IOException | RuntimeException | Error completion) {
                if (primary == null) throw completion;
                if (completion != primary) primary.addSuppressed(completion);
            } finally { capture.discard(); }
        }
    }
}
