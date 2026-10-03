package com.example.api.configuration;

import cn.code91.facility.web.filter.FacilityWebTraceProperties;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.context.integration.Slf4jThreadLocalAccessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.slf4j.MDC;

/** Context propagation belongs to this application's managed executor, never a global registry. */
@Configuration(proxyBeanMethods = false)
public class RequestExecutionConfiguration {
    @Bean TaskDecorator requestTaskDecorator(FacilityWebTraceProperties trace) {
        String key = trace.getMdcKey();
        var registry = new ContextRegistry()
                .registerThreadLocalAccessor(new Slf4jThreadLocalAccessor(key));
        TaskDecorator tracing = new ContextPropagatingTaskDecorator(ContextSnapshotFactory.builder()
                .contextRegistry(registry).clearMissing(true).build());
        return task -> {
            Runnable traced = tracing.decorate(task);
            return new DelegatingSecurityContextRunnable(() -> {
                String previous = MDC.get(key);
                Throwable primary = null;
                try {
                    traced.run();
                } catch (RuntimeException | Error failure) {
                    primary = failure;
                    throw failure;
                } finally {
                    // Micrometer 1.2.1 does not roll back an accessor that throws during installation.
                    try {
                        if (previous == null) MDC.remove(key);
                        else MDC.put(key, previous);
                    } catch (RuntimeException | Error cleanup) {
                        if (primary == null) throw cleanup;
                        if (cleanup != primary) primary.addSuppressed(cleanup);
                    }
                }
            });
        };
    }
}
