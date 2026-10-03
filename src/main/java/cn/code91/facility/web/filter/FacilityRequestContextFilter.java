package cn.code91.facility.web.filter;

import cn.code91.facility.web.session.SessionUserHolder;
import cn.code91.facility.web.util.ClientIpPolicy;
import org.slf4j.MDC;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.context.request.async.DeferredResultProcessingInterceptor;
import org.springframework.web.context.request.async.WebAsyncUtils;
import java.util.Objects;
import java.util.concurrent.Callable;
import jakarta.annotation.Nullable;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/**
 * Owns IP snapshots and compatibility identity across REQUEST/ASYNC/ERROR, including non-MVC short circuits.
 * Top-level Servlet and Callable scopes remove compatibility identity on exit; nested dispatch restores its parent.
 * A host Principal is adapted, never authenticated here. External DeferredResult producers and AsyncContext.start
 * retain their host-owned execution context. This filter creates no executor and never closes a host executor.
 */
public class FacilityRequestContextFilter extends OncePerRequestFilter {
    private final @Nullable TraceIdFilter trace;
    private final ClientIpPolicy ips;
    public FacilityRequestContextFilter(@Nullable TraceIdFilter trace, ClientIpPolicy ips) {
        this.trace = trace; this.ips = Objects.requireNonNull(ips, "ips");
    }

    private final ThreadLocal<Boolean> active = new ThreadLocal<>();
    private static final String STATE = FacilityRequestContextFilter.class.getName();
    private static final class State { volatile @Nullable Object user; }
    private record WorkerScope(@Nullable String previousTrace) {}

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean nested = Boolean.TRUE.equals(active.get());
        Object previous = SessionUserHolder.getUser(Object.class).orElse(null);
        active.set(true);
        SessionUserHolder.clear();
        State state = null;
        try {
            if (request.getAttribute(ClientIpPolicy.class.getName()) == null) {
                // A host policy failure must not be retried recursively by the container's ERROR dispatch.
                request.setAttribute(ClientIpPolicy.class.getName(), "unknown");
                String resolved = ips.resolve(request);
                if (resolved != null) request.setAttribute(ClientIpPolicy.class.getName(), resolved);
            }
            state = (State) request.getAttribute(STATE);
            if (state == null) {
                state = new State(); state.user = request.getUserPrincipal(); request.setAttribute(STATE, state);
                registerAsyncCallbacks(request, state);
            }
            SessionUserHolder.setUser(state.user);
            if (trace == null) chain.doFilter(request, response);
            else trace.doFilter(request, response, chain);
        } finally {
            if (state != null) state.user = SessionUserHolder.getUser(Object.class).orElse(null);
            SessionUserHolder.clear();
            if (nested) SessionUserHolder.setUser(previous);
            else active.remove();
        }
    }

    private void registerAsyncCallbacks(HttpServletRequest request, State state) {
        WebAsyncUtils.getAsyncManager(request).registerDeferredResultInterceptor(STATE,
                new DeferredResultProcessingInterceptor() {
                    @Override public <T> void beforeConcurrentHandling(NativeWebRequest web,
                            DeferredResult<T> result) {
                        state.user = SessionUserHolder.getUser(Object.class).orElse(null);
                        if (trace != null) trace.capture(request);
                    }
                });
        WebAsyncUtils.getAsyncManager(request).registerCallableInterceptor(STATE,
                new CallableProcessingInterceptor() {
                    private final ThreadLocal<WorkerScope> workers = new ThreadLocal<>();
                    @Override public <T> void beforeConcurrentHandling(NativeWebRequest web,
                            Callable<T> task) {
                        state.user = SessionUserHolder.getUser(Object.class).orElse(null);
                        if (trace != null) trace.capture(request);
                    }
                    @Override public <T> void preProcess(NativeWebRequest web, Callable<T> task) {
                        SessionUserHolder.clear();
                        String previousTrace = null;
                        boolean captured = false;
                        try {
                            SessionUserHolder.setUser(state.user);
                            if (trace != null) {
                                previousTrace = MDC.get(trace.mdcKey());
                                captured = true;
                                String selected = trace.workerTrace(request, previousTrace);
                                if (selected == null) MDC.remove(trace.mdcKey()); else MDC.put(trace.mdcKey(), selected);
                            }
                            workers.set(new WorkerScope(previousTrace));
                        } catch (RuntimeException | Error failure) {
                            // Spring need not call postProcess for an interceptor whose preProcess failed.
                            SessionUserHolder.clear(); workers.remove();
                            if (captured) restoreTrace(previousTrace, failure);
                            throw failure;
                        }
                    }
                    @Override public <T> void postProcess(NativeWebRequest web, Callable<T> task, @Nullable Object result) {
                        WorkerScope scope = workers.get(); workers.remove();
                        SessionUserHolder.clear();
                        if (scope != null) restoreTrace(scope.previousTrace(), result instanceof Throwable failure ? failure : null);
                    }
                });
    }

    private void restoreTrace(@Nullable String previous, @Nullable Throwable primary) {
        if (trace != null) trace.restore(previous, primary);
    }

    @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }
    @Override protected boolean shouldNotFilterErrorDispatch() { return false; }
    @Override protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException { doFilterInternal(request, response, chain); }
}
