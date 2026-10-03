package cn.code91.facility.web.filter;

import cn.code91.facility.autoconfigure.FacilityWebAutoConfiguration;
import cn.code91.facility.web.session.SessionUserHolder;
import cn.code91.facility.web.test.EmbeddedServletApplication;
import cn.code91.facility.web.util.RequestUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.*;
import java.net.http.*;
import java.nio.file.Path;
import java.security.Principal;
import java.time.Duration;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

@org.junit.jupiter.api.Timeout(45)
class RequestBoundaryHttpTest {
    @TempDir Path directory;
    record Exit(String user, String trace, String thread) {}
    static class Probe {
        volatile boolean workerObservation;
        final BlockingQueue<Throwable> servletFailures = new LinkedBlockingQueue<>();
        final BlockingQueue<Exit> exits = new LinkedBlockingQueue<>();
        final BlockingQueue<Exit> workerExits = new LinkedBlockingQueue<>();
        final java.util.concurrent.atomic.AtomicReference<org.springframework.web.context.request.async.DeferredResult<String>> deferred = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicLong streamed = new java.util.concurrent.atomic.AtomicLong();
        final java.util.concurrent.atomic.AtomicBoolean writeFailed = new java.util.concurrent.atomic.AtomicBoolean();
        final CountDownLatch streaming = new CountDownLatch(1);
        final CountDownLatch interrupted = new CountDownLatch(1), releaseTimedOutWorker = new CountDownLatch(1);
        final CountDownLatch deferredReady = new CountDownLatch(1);
        final CountDownLatch workerStarted = new CountDownLatch(1), releaseWorker = new CountDownLatch(1);
    }

    @Test void authenticatedThenAnonymousAndShortCircuitLeaveNoIdentityOnTheReusedServletThread() throws Exception {
        try (var app = app(); var client = HttpClient.newHttpClient()) {
            var first = get(client, app, "/who", "Fixture-User", "alice", "X-Trace-Id", "trace-a");
            assertThat(first.body()).isEqualTo("alice|trace-a|127.0.0.1");
            Exit a = exit(app); assertThat(a.user()).isNull(); assertThat(a.trace()).isNull();
            assertThat(get(client, app, "/short").body()).isEqualTo("short");
            Exit shorted = exit(app); assertThat(shorted.user()).isNull();
            assertThat(get(client, app, "/who", "X-Forwarded-For", "192.0.2.77", "X-User", "forged").body())
                    .startsWith("anonymous|").endsWith("|127.0.0.1");
            Exit b = exit(app); assertThat(b.user()).isNull(); assertThat(b.trace()).isNull();
            assertThat(b.thread()).isEqualTo(a.thread()).isEqualTo(shorted.thread());
        }
    }

    @Test void explicitProxyConfigurationIsUsedAtTheHttpBoundary() throws Exception {
        try (var app = app("facility.web.proxy.trusted-proxies[0]=127.0.0.1/32", "facility.web.proxy.trusted-proxies[1]=10.0.0.0/8");
             var client = HttpClient.newHttpClient()) {
            assertThat(get(client, app, "/who", "X-Forwarded-For", "192.0.2.99, 198.51.100.4, 10.1.1.1").body())
                    .startsWith("anonymous|").endsWith("|198.51.100.4");
            assertThat(get(client, app, "/who", "X-Forwarded-For", "1.1.1.1", "X-Forwarded-For", "2.2.2.2").body())
                    .endsWith("|127.0.0.1");
        }
    }

    @Test void callableInstallsItsRequestOnActualWorkerAndCleansBothThreadsBeforeReuse() throws Exception {
        try (var app = app(); var client = HttpClient.newHttpClient()) {
            var probe = app.context().getBean(Probe.class);
            var future = client.sendAsync(HttpRequest.newBuilder(app.uri("/callable")).timeout(Duration.ofSeconds(8))
                    .header("Fixture-User", "alice").header("X-Trace-Id", "callable-a").GET().build(), HttpResponse.BodyHandlers.ofString());
            try {
                assertThat(probe.workerStarted.await(5, TimeUnit.SECONDS)).isTrue();
                Exit initial = exit(app); assertThat(initial.user()).isNull(); assertThat(initial.trace()).isNull();
            } finally { probe.releaseWorker.countDown(); }
            var response = future.get(5, TimeUnit.SECONDS);
            assertThat(response.body()).isEqualTo("alice|callable-a");
            assertThat(response.headers().firstValue("Fixture-Dispatch")).contains("ASYNC");
            assertThat(response.headers().firstValue("Fixture-Context")).contains("alice|callable-a");
            Exit worker = probe.workerExits.poll(5, TimeUnit.SECONDS);
            assertThat(worker).isNotNull(); assertThat(worker.user()).isNull(); assertThat(worker.trace()).isNull();
            Exit dispatched = exit(app); assertThat(dispatched.user()).isNull(); assertThat(dispatched.trace()).isNull();
            assertThat(get(client, app, "/callable", "X-Trace-Id", "callable-b").body()).isEqualTo("anonymous|callable-b");
            Exit reused = probe.workerExits.poll(5, TimeUnit.SECONDS);
            assertThat(reused).isNotNull(); assertThat(reused.thread()).isEqualTo(worker.thread()); assertThat(reused.user()).isNull();
        }
    }

    @Test void principalEstablishedByHostFilterIsAdaptedAtMvcBoundary() throws Exception {
        try (var app = app(); var client = HttpClient.newHttpClient()) {
            assertThat(get(client, app, "/who", "Fixture-Authenticated-Name", "host-authenticated").body()).startsWith("host-authenticated|");
            assertThat(exit(app).user()).isNull();
        }
    }

    @Test void deferredProducerRemainsIndependentWhileAsyncDispatchRestoresRequest() throws Exception {
        try (var app = app(); var client = HttpClient.newHttpClient()) {
            var probe = app.context().getBean(Probe.class);
            var future = client.sendAsync(HttpRequest.newBuilder(app.uri("/deferred")).timeout(Duration.ofSeconds(8))
                    .header("Fixture-User", "alice").header("X-Trace-Id", "deferred-a").GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(probe.deferredReady.await(5, TimeUnit.SECONDS)).isTrue();
            Exit initial = exit(app); assertThat(initial.user()).isNull(); assertThat(initial.trace()).isNull();
            try (var producer = Executors.newSingleThreadExecutor()) {
                assertThat(producer.submit(() -> { String before = who(); probe.deferred.get().setResult(before); return who(); }).get(5, TimeUnit.SECONDS))
                        .isEqualTo("anonymous|null");
            }
            var response = future.get(5, TimeUnit.SECONDS);
            assertThat(response.body()).isEqualTo("anonymous|null");
            assertThat(response.headers().firstValue("Fixture-Dispatch")).contains("ASYNC");
            assertThat(response.headers().firstValue("Fixture-Context")).contains("alice|deferred-a");
            Exit completed = exit(app); assertThat(completed.user()).isNull(); assertThat(completed.trace()).isNull();
        }
    }

    @Test void deferredTimeoutAndContainerErrorKeepCorrelationAndClearReusedThreads() throws Exception {
        try (var app = app(); var client = HttpClient.newHttpClient()) {
            for (String path : new String[]{"/deferred-timeout", "/send-error"}) {
                var response = get(client, app, path, "Fixture-User", "alice", "X-Trace-Id", "error-a");
                assertThat(response.statusCode()).isEqualTo(path.equals("/send-error") ? 502 : 503);
                assertThat(response.body()).contains("\"traceId\":\"error-a\"").doesNotContain("alice", "PRIVATE-ERROR");
                Exit initial = exit(app); assertThat(initial.user()).isNull(); assertThat(initial.trace()).isNull();
                if (!path.equals("/send-error")) {
                    Exit dispatched = exit(app); assertThat(dispatched.user()).isNull(); assertThat(dispatched.trace()).isNull();
                } else {
                    assertThat(response.headers().firstValue("Fixture-Boundary-Dispatch")).contains("ERROR");
                    assertThat(response.headers().firstValue("Fixture-Boundary-Context")).contains("alice|error-a");
                }
                assertThat(get(client, app, "/who", "X-Trace-Id", "anonymous-b").body()).isEqualTo("anonymous|anonymous-b|127.0.0.1");
                assertThat(exit(app).user()).isNull();
            }
        }
    }

    @Test void callableTimeoutCancelsTheRealWorkerAndItsNextAnonymousTaskIsClean() throws Exception {
        try (var app = app(); var client = HttpClient.newHttpClient()) {
            var probe = app.context().getBean(Probe.class);
            try {
                var response = get(client, app, "/callable-timeout", "Fixture-User", "alice", "X-Trace-Id", "timeout-a");
                assertThat(response.statusCode()).isEqualTo(503); assertThat(response.body()).contains("timeout-a");
                assertThat(probe.interrupted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(probe.workerExits).isEmpty();
            } finally { probe.releaseTimedOutWorker.countDown(); }
            Exit worker = probe.workerExits.poll(5, TimeUnit.SECONDS);
            assertThat(worker).isNotNull(); assertThat(worker.user()).isNull(); assertThat(worker.trace()).isNull();
            probe.releaseWorker.countDown();
            assertThat(get(client, app, "/callable", "X-Trace-Id", "after-timeout").body()).isEqualTo("anonymous|after-timeout");
            Exit reused = probe.workerExits.poll(5, TimeUnit.SECONDS); assertThat(reused).isNotNull();
            assertThat(reused.thread()).isEqualTo(worker.thread()); assertThat(reused.user()).isNull();
        }
    }

    @Test void realDisconnectStopsCallableAndClearsIdentityBeforeWorkerReuse() throws Exception {
        try (var app = app(); var client = HttpClient.newHttpClient()) {
            var probe = app.context().getBean(Probe.class);
            try (var socket = new java.net.Socket()) {
                socket.setReceiveBufferSize(1024); socket.setSoTimeout(5000);
                socket.connect(new java.net.InetSocketAddress("127.0.0.1", app.uri("/").getPort()), 5000);
                socket.getOutputStream().write(("GET /stream HTTP/1.1\r\nHost: localhost\r\nFixture-User: alice\r\nX-Trace-Id: disconnect-a\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                assertThat(probe.streaming.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(socket.getInputStream().readNBytes(64)).hasSize(64);
                socket.setSoLinger(true, 0);
            }
            Exit worker = probe.workerExits.poll(8, TimeUnit.SECONDS);
            assertThat(worker).isNotNull(); assertThat(worker.user()).isNull(); assertThat(worker.trace()).isNull();
            assertThat(probe.writeFailed.get()).isTrue(); assertThat(probe.streamed.get()).isLessThan(256L * 1024 * 1024);
            probe.releaseWorker.countDown();
            assertThat(get(client, app, "/callable", "X-Trace-Id", "after-disconnect").body()).isEqualTo("anonymous|after-disconnect");
            Exit reused = probe.workerExits.poll(5, TimeUnit.SECONDS); assertThat(reused).isNotNull();
            assertThat(reused.thread()).isEqualTo(worker.thread()); assertThat(reused.user()).isNull();
        }
    }

    @Test void hostObservationEstablishedInsideTheChainIsCapturedForCallable() throws Exception {
        try (var app = app(); var client = HttpClient.newHttpClient()) {
            app.context().getBean(Probe.class).releaseWorker.countDown();
            var response = get(client, app, "/callable", "Fixture-User", "alice", "X-Trace-Id", "inbound", "Fixture-Inner-Trace", "native-observation");
            assertThat(response.body()).isEqualTo("alice|native-observation");
            assertThat(response.headers().firstValue("X-Trace-Id")).contains("native-observation");
            Exit worker = app.context().getBean(Probe.class).workerExits.poll(5, TimeUnit.SECONDS);
            assertThat(worker).isNotNull(); assertThat(worker.trace()).isNull();
        }
    }

    @Test void hostIpPolicyReplacesTheDefaultAndOnlyTheOwningBoundaryIsRegistered() throws Exception {
        try (var app = EmbeddedServletApplication.start(directory, new Class<?>[]{PolicyOverride.class, Config.class}); var client = HttpClient.newHttpClient()) {
            assertThat(get(client, app, "/who", "X-Trace-Id", "custom-policy").body()).isEqualTo("anonymous|custom-policy|198.51.100.8");
            var names = app.context().getServletContext().getFilterRegistrations().keySet();
            assertThat(names).contains("facilityRequestContextFilter").doesNotContain("traceIdFilter", "hostTrace");
        }
    }

    @Test void disablingTraceStillClearsCompatibilityIdentityOnShortCircuits() throws Exception {
        try (var app = app("facility.web.trace.enabled=false"); var client = HttpClient.newHttpClient()) {
            assertThat(get(client, app, "/short").statusCode()).isEqualTo(200); assertThat(exit(app).user()).isNull();
            var response = get(client, app, "/who", "X-Trace-Id", "ignored");
            assertThat(response.body()).isEqualTo("anonymous|null|127.0.0.1");
            assertThat(response.headers().firstValue("X-Trace-Id")).isEmpty();
            Exit after = exit(app); assertThat(after.user()).isNull(); assertThat(after.trace()).isNull();
        }
    }

    @Configuration(proxyBeanMethods = false) static class PolicyOverride {
        @Bean cn.code91.facility.web.util.ClientIpPolicy hostIpPolicy() {
            return new cn.code91.facility.web.util.ClientIpPolicy(java.util.List.of()) {
                @Override public String resolve(HttpServletRequest request) { return "198.51.100.8"; }
            };
        }
    }

    @Test void failingHostOriginPolicyUsesSafeContainerErrorWithoutRecursivePolicyFailure() throws Exception {
        try (var app = EmbeddedServletApplication.start(directory, new Class<?>[]{BrokenPolicy.class, Config.class}); var client = HttpClient.newHttpClient()) {
            var response = get(client, app, "/who", "Fixture-User", "alice");
            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(response.headers().firstValue("Fixture-Boundary-Dispatch")).contains("ERROR");
            assertThat(response.body()).contains("Internal server error").doesNotContain("PRIVATE-POLICY", "IllegalStateException");
            Exit after = exit(app); assertThat(after.user()).isNull(); assertThat(after.trace()).isNull();
        }
    }
    @Configuration(proxyBeanMethods = false) static class BrokenPolicy {
        @Bean cn.code91.facility.web.util.ClientIpPolicy hostIpPolicy() {
            return new cn.code91.facility.web.util.ClientIpPolicy(java.util.List.of()) {
                @Override public String resolve(HttpServletRequest request) { throw new IllegalStateException("PRIVATE-POLICY"); }
            };
        }
    }

    @Test void workerHostObservationHasPriorityAndIsRestoredBeforeItsOwnerCleansIt() throws Exception {
        try (var app = app(); var client = HttpClient.newHttpClient()) {
            var probe = app.context().getBean(Probe.class); probe.workerObservation = true; probe.releaseWorker.countDown();
            assertThat(get(client, app, "/callable", "Fixture-User", "alice", "X-Trace-Id", "request-a").body()).isEqualTo("alice|worker-host");
            Exit worker = probe.workerExits.poll(5, TimeUnit.SECONDS); assertThat(worker).isNotNull();
            assertThat(worker.trace()).isEqualTo("worker-host"); assertThat(worker.user()).isNull();
            probe.workerObservation = false;
            assertThat(get(client, app, "/callable", "X-Trace-Id", "request-b").body()).isEqualTo("anonymous|request-b");
            Exit reused = probe.workerExits.poll(5, TimeUnit.SECONDS); assertThat(reused).isNotNull();
            assertThat(reused.thread()).isEqualTo(worker.thread()); assertThat(reused.trace()).isNull();
        }
    }

    @Test void explicitHostTraceWithDefaultDisabledIsStillRegisteredOnlyInsideTheBoundary() throws Exception {
        try (var app = EmbeddedServletApplication.start(directory, new Class<?>[]{ExplicitTrace.class, Config.class}, "facility.web.trace.enabled=false");
             var client = HttpClient.newHttpClient()) {
            assertThat(get(client, app, "/who", "X-Trace-Id", "host-owned").body()).isEqualTo("anonymous|host-owned|127.0.0.1");
            assertThat(app.context().getServletContext().getFilterRegistrations().keySet())
                    .contains("facilityRequestContextFilter").doesNotContain("explicitTrace", "traceIdFilter");
        }
    }
    @Configuration(proxyBeanMethods = false) static class ExplicitTrace {
        @Bean TraceIdFilter explicitTrace() { return new TraceIdFilter(new FacilityWebTraceProperties()); }
    }

    private EmbeddedServletApplication app(String... properties) { return EmbeddedServletApplication.start(directory, new Class<?>[]{Config.class}, properties); }
    private static Exit exit(EmbeddedServletApplication app) throws Exception {
        Exit value = app.context().getBean(Probe.class).exits.poll(5, TimeUnit.SECONDS);
        assertThat(value).isNotNull(); return value;
    }
    private static HttpResponse<String> get(HttpClient client, EmbeddedServletApplication app, String path, String... headers) throws Exception {
        var builder = HttpRequest.newBuilder(app.uri(path)).timeout(Duration.ofSeconds(8)).GET();
        if (headers.length > 0) builder.headers(headers);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private static String who() {
        Object user = SessionUserHolder.getUser(Object.class).orElse(null);
        return (user instanceof Principal p ? p.getName() : user == null ? "anonymous" : user.toString()) + "|" + MDC.get("traceId");
    }

    @Configuration(proxyBeanMethods = false) @EnableWebMvc
    @Import(Endpoints.class)
    @org.springframework.boot.autoconfigure.ImportAutoConfiguration({FacilityWebAutoConfiguration.class,
            org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration.class})
    static class Config implements WebMvcConfigurer {
        @org.springframework.beans.factory.annotation.Autowired org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor mvcExecutor;
        @Override public void configureAsyncSupport(AsyncSupportConfigurer configurer) { configurer.setTaskExecutor(mvcExecutor).setDefaultTimeout(1000); }
        @Bean static org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor mvcExecutor(Probe probe) {
            var executor = new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1); executor.setMaxPoolSize(1); executor.setQueueCapacity(4); executor.setThreadNamePrefix("request-contract-");
            executor.setTaskDecorator(task -> () -> {
                String previousTrace = MDC.get("traceId");
                if (probe.workerObservation) MDC.put("traceId", "worker-host");
                try { task.run(); } finally {
                    probe.workerExits.add(new Exit(SessionUserHolder.getUser(Object.class).map(Object::toString).orElse(null), MDC.get("traceId"), Thread.currentThread().getName()));
                    if (previousTrace == null) MDC.remove("traceId"); else MDC.put("traceId", previousTrace);
                }
            });
            return executor;
        }
        @Bean @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "facility.web.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
        TraceIdFilter hostTrace(FacilityWebTraceProperties properties, Probe probe) {
            return new TraceIdFilter(properties) {
                @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                        throws ServletException, java.io.IOException {
                    super.doFilterInternal(request, response, (rq, rs) -> {
                        if (request.getDispatcherType() == DispatcherType.ERROR && request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable failure)
                            probe.servletFailures.add(failure);
                        response.setHeader("Fixture-Boundary-Dispatch", request.getDispatcherType().name());
                        response.setHeader("Fixture-Boundary-Context", who());
                        chain.doFilter(rq, rs);
                    });
                }
            };
        }
        @Bean Probe probe() { return new Probe(); }
        @Bean org.springframework.web.servlet.DispatcherServlet dispatcherServlet() { return new org.springframework.web.servlet.DispatcherServlet(); }
        @Bean org.springframework.boot.webmvc.autoconfigure.DispatcherServletRegistrationBean dispatcherRegistration(org.springframework.web.servlet.DispatcherServlet servlet) {
            var bean = new org.springframework.boot.webmvc.autoconfigure.DispatcherServletRegistrationBean(servlet, "/");
            bean.setAsyncSupported(true); return bean;
        }
        @Bean org.springframework.boot.web.server.WebServerFactoryCustomizer<org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory> servletThreads(Probe probe) {
            return factory -> {
                factory.addConnectorCustomizers(connector -> {
                    var protocol = (org.apache.coyote.AbstractProtocol<?>) connector.getProtocolHandler();
                    protocol.setMaxThreads(1); protocol.setMinSpareThreads(1);
                });
                factory.addContextValves(new org.apache.catalina.valves.ValveBase(true) {
                    @Override public void invoke(org.apache.catalina.connector.Request request, org.apache.catalina.connector.Response response)
                            throws java.io.IOException, ServletException {
                        String name = request.getHeader("Fixture-User");
                        if (name != null) request.setUserPrincipal(() -> name);
                        try { getNext().invoke(request, response); }
                        finally { probe.exits.add(new Exit(SessionUserHolder.getUser(Object.class).map(Object::toString).orElse(null), MDC.get("traceId"), Thread.currentThread().getName())); }
                    }
                });
            };
        }
        @Bean FilterRegistrationBean<Filter> shortCircuit() {
            var bean = new FilterRegistrationBean<Filter>((request, response, chain) -> {
                String previousTrace = MDC.get("traceId");
                String observation = ((HttpServletRequest) request).getHeader("Fixture-Inner-Trace");
                if (observation != null) MDC.put("traceId", observation);
                try {
                ((HttpServletResponse) response).setHeader("Fixture-Dispatch", request.getDispatcherType().name());
                ((HttpServletResponse) response).setHeader("Fixture-Context", who());
                if (((HttpServletRequest) request).getRequestURI().equals("/short")) {
                    SessionUserHolder.setUser("short-user"); response.getWriter().write("short");
                } else if (((HttpServletRequest) request).getHeader("Fixture-Authenticated-Name") != null) {
                    String authenticatedName = ((HttpServletRequest) request).getHeader("Fixture-Authenticated-Name");
                    chain.doFilter(new HttpServletRequestWrapper((HttpServletRequest) request) {
                        @Override public Principal getUserPrincipal() { return () -> authenticatedName; }
                    }, response);
                } else chain.doFilter(request, response);
                } finally { if (previousTrace == null) MDC.remove("traceId"); else MDC.put("traceId", previousTrace); }
            });
            bean.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 10); bean.setAsyncSupported(true);
            bean.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR); return bean;
        }
    }
    @RestController static class Endpoints {
        @org.springframework.beans.factory.annotation.Autowired Probe probe;
        @GetMapping("/stream") Callable<Void> stream(HttpServletResponse response) {
            return () -> {
                response.setContentType("application/octet-stream");
                byte[] chunk = new byte[8192];
                try {
                    var output = response.getOutputStream(); output.write(chunk); output.flush(); probe.streaming.countDown();
                    while (probe.streamed.get() < 256L * 1024 * 1024) { output.write(chunk); output.flush(); probe.streamed.addAndGet(chunk.length); }
                } catch (java.io.IOException disconnected) { probe.writeFailed.set(true); }
                return null;
            };
        }
        @GetMapping("/deferred") org.springframework.web.context.request.async.DeferredResult<String> deferred() {
            var result = new org.springframework.web.context.request.async.DeferredResult<String>(5000L);
            probe.deferred.set(result); probe.deferredReady.countDown(); return result;
        }
        @GetMapping("/deferred-timeout") org.springframework.web.context.request.async.DeferredResult<String> timeout() {
            return new org.springframework.web.context.request.async.DeferredResult<String>(100L);
        }
        @GetMapping("/send-error") void error(HttpServletResponse response) throws Exception { response.sendError(502, "PRIVATE-ERROR"); }
        @GetMapping("/callable-timeout") Callable<String> timeoutCallable() {
            return () -> {
                try { new CountDownLatch(1).await(20, TimeUnit.SECONDS); }
                catch (InterruptedException cancelled) { probe.interrupted.countDown(); probe.releaseTimedOutWorker.await(5, TimeUnit.SECONDS); }
                return who();
            };
        }
        @GetMapping("/callable") Callable<String> callable() {
            return () -> { probe.workerStarted.countDown(); if (!probe.releaseWorker.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout"); return who(); };
        }
        @GetMapping("/who") String current(HttpServletRequest request) { return who() + "|" + RequestUtil.getClientIp(request); }
    }
}
