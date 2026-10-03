package com.example.fixtures;

import cn.code91.facility.web.filter.FacilityWebTraceProperties;
import com.example.api.configuration.RequestExecutionConfiguration;
import org.slf4j.MDC;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.task.TaskDecorator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;

public final class DecoratorFaultProcess {
    public static void main(String[] args) throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(FacilityWebTraceProperties.class); context.register(RequestExecutionConfiguration.class); context.refresh();
            var decorator = context.getBean(TaskDecorator.class);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice", "unused", List.of()));
            MDC.put("traceId", "request-A");
            Runnable task = decorator.decorate(() -> { throw new AssertionError("partially installed context must not execute task"); });
            SecurityContextHolder.clearContext(); MDC.clear();
            try (var worker = java.util.concurrent.Executors.newSingleThreadExecutor(Thread.ofPlatform().name("scope-worker").factory())) {
                worker.submit(() -> {
                    var host = SecurityContextHolder.createEmptyContext();
                    host.setAuthentication(new UsernamePasswordAuthenticationToken("host", "unused", List.of()));
                    SecurityContextHolder.setContext(host); MDC.put("traceId", "host-trace");
                    Throwable actual = null;
                    try { task.run(); } catch (Throwable failure) { actual = failure; }
                    if (actual != FaultingMdcProvider.PRIMARY) throw new AssertionError("original installation failure was lost", actual);
                    if (SecurityContextHolder.getContext() != host) throw new AssertionError("host SecurityContext was not restored");
                    if (!"host-trace".equals(MDC.get("traceId"))) throw new AssertionError("partial trace installation leaked");
                    if (Boolean.getBoolean("probe.rollbackFailure") && !List.of(actual.getSuppressed()).contains(FaultingMdcProvider.SECONDARY)) {
                        throw new AssertionError("cleanup failure must be suppressed on original failure");
                    }
                    SecurityContextHolder.clearContext(); MDC.clear();
                }).get(5, java.util.concurrent.TimeUnit.SECONDS);
            }
        }
        System.out.println("DECORATOR_FAILURE_RECOVERY_PASS");
    }
}
