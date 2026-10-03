package com.example.fixtures;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.MDC;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

/** Host-added HTTP operations exercise the production application chain and executor. */
@TestConfiguration(proxyBeanMethods = false)
public class LifecycleFixture {
    public record Cleanup(String path, jakarta.servlet.DispatcherType dispatch, Object authentication) {}
    @Bean public java.util.concurrent.BlockingQueue<Cleanup> cleanupEvents() { return new java.util.concurrent.LinkedBlockingQueue<>(); }
    @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> errorDispatchObserver(
            java.util.concurrent.BlockingQueue<Cleanup> events) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter>((request, response, chain) -> {
            ((jakarta.servlet.http.HttpServletResponse) response).setHeader("X-Actual-Dispatch", request.getDispatcherType().name());
            try { chain.doFilter(request, response); }
            finally { events.add(new Cleanup(((jakarta.servlet.http.HttpServletRequest) request).getRequestURI(),
                    request.getDispatcherType(), SecurityContextHolder.getContext().getAuthentication())); }
        });
        registration.setOrder(Integer.MIN_VALUE); registration.setDispatcherTypes(jakarta.servlet.DispatcherType.ERROR);
        return registration;
    }
    @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> securityObserver(
            java.util.concurrent.BlockingQueue<Cleanup> events) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter>((request, response, chain) -> {
            try { chain.doFilter(request, response); }
            finally { events.add(new Cleanup(((jakarta.servlet.http.HttpServletRequest) request).getRequestURI(),
                    request.getDispatcherType(), SecurityContextHolder.getContext().getAuthentication())); }
        });
        registration.setOrder(-101); registration.setAsyncSupported(true);
        registration.setDispatcherTypes(jakarta.servlet.DispatcherType.REQUEST, jakarta.servlet.DispatcherType.ASYNC, jakarta.servlet.DispatcherType.ERROR);
        return registration;
    }
    @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> failingFilter() {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter>((request, response, chain) -> {
            if (((jakarta.servlet.http.HttpServletRequest) request).getRequestURI().endsWith("/filter-error"))
                throw new IllegalStateException("FILTER_SECRET");
            chain.doFilter(request, response);
        });
        registration.setOrder(0); registration.setAsyncSupported(true); return registration;
    }
    @RestController
    public static class ProbeController {
        final AsyncTaskExecutor executor;
        public volatile CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), exited = new CountDownLatch(1);
        ProbeController(AsyncTaskExecutor executor) { this.executor = executor; }
        static Map<String, Object> identity() {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            return Map.of("subject", authentication == null ? "none" : authentication.getName(),
                    "trace", java.util.Objects.requireNonNullElse(MDC.get("traceId"), "none"),
                    "virtual", Thread.currentThread().isVirtual());
        }
        @GetMapping("/api/greeting/probe") public Map<String, Object> sync() { return identity(); }
        @GetMapping("/api/greeting/origin") public Map<String, String> origin(jakarta.servlet.http.HttpServletRequest request) {
            return Map.of("subject", request.getUserPrincipal().getName(), "ip", cn.code91.facility.web.util.RequestUtil.getClientIp(request));
        }
        @GetMapping("/api/greeting/callable") public Callable<Map<String, Object>> callable() { return ProbeController::identity; }
        @GetMapping("/api/greeting/deferred") public DeferredResult<Map<String, Object>> deferred() {
            var result = new DeferredResult<Map<String, Object>>(2000L);
            executor.execute(() -> result.setResult(identity())); return result;
        }
        @GetMapping("/api/greeting/mvc-error") public void mvcError() { throw new IllegalStateException("MVC_SECRET"); }
        @GetMapping("/api/greeting/callable-error") public Callable<Void> callableError() {
            return () -> { throw new IllegalStateException("CALLABLE_SECRET"); };
        }
        @GetMapping("/api/greeting/timeout") public DeferredResult<Void> timeout() { return new DeferredResult<>(30L); }
        @GetMapping("/api/greeting/servlet-error") public void servletError(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
            response.sendError(418, "SERVLET_SECRET");
        }
        @GetMapping("/api/greeting/disconnect") public Callable<Void> disconnect(jakarta.servlet.http.HttpServletResponse response) {
            return () -> {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture deadline");
                    response.getOutputStream().write(new byte[1_048_576]); response.flushBuffer(); return null;
                } finally { exited.countDown(); }
            };
        }
    }
}
