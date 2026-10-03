package cn.code91.facility.web.exception;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/** Converts failures before MVC to the same application-owned HTTP policy. */
public final class FacilityHttpErrorFilter extends OncePerRequestFilter {
    private final FacilityHttpErrors errors;

    public FacilityHttpErrorFilter(FacilityHttpErrors errors) {
        this.errors = Objects.requireNonNull(errors, "errors");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getDispatcherType() == jakarta.servlet.DispatcherType.ERROR) {
            renderDispatch(request, response);
            return;
        }
        try {
            chain.doFilter(request, response);
        } catch (Exception failure) {
            if (response.isCommitted()) {
                if (failure instanceof IOException io) throw io;
                if (failure instanceof ServletException servlet) throw servlet;
                throw new ServletException(failure);
            }
            errors.write(request, response, failure);
        }
    }
    @Override protected boolean shouldNotFilterErrorDispatch() { return false; }
    @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }

    @Override protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response,
                                                         FilterChain chain) throws IOException {
        renderDispatch(request, response);
    }

    private void renderDispatch(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object cause = request.getAttribute(jakarta.servlet.RequestDispatcher.ERROR_EXCEPTION);
        Object code = request.getAttribute(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE);
        int status = code instanceof Integer value && value >= 400 && value <= 599 ? value : 500;
        Exception failure = cause instanceof Exception exception ? exception
                : new org.springframework.web.ErrorResponseException(org.springframework.http.HttpStatusCode.valueOf(status));
        errors.write(request, response, failure);
    }

}
